package io.openlist.client.data.repository

import io.openlist.client.core.auth.TokenProvider
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.FileListResult
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.SystemDocument
import io.openlist.client.core.model.TaskStateMapper
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.SystemDocumentHttpClient
import io.openlist.client.core.network.buildScopedHttpHeaders
import io.openlist.client.core.network.dto.MoveCopyReq
import io.openlist.client.core.network.dto.MkdirReq
import io.openlist.client.core.network.dto.RemoveReq
import io.openlist.client.core.network.dto.RenameReq
import io.openlist.client.core.network.safeApiCall
import io.openlist.client.core.network.safeApiCallUnit
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SystemDocumentReadSource(
    val rawUrl: String,
    val instanceBaseUrl: String,
    val token: String?,
    val sizeBytes: Long,
)

/** A fact read from the backend, never an inference from an accepted request. */
data class SystemDocumentRemoteObject(
    val path: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val rawUrl: String,
    val hashInfo: String,
)

data class SystemDocumentRemoteNames(
    val stagePath: String,
    val backupPath: String,
    val stageName: String,
    val backupName: String,
)

/**
 * All OpenList side effects used by the system-document transaction flow.
 * Every mutating call is followed by a read-back check; HTTP 2xx and an
 * accepted background task are deliberately not represented as success here.
 */
@Singleton
class SystemDocumentRemoteGateway @Inject constructor(
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
    private val tokenProvider: TokenProvider,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
    private val httpClient: SystemDocumentHttpClient,
) {
    private val unsupportedOperations = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolveReadSource(document: SystemDocument, mappedPath: String): ApiResult<SystemDocumentReadSource> {
        if (document.isDirectory) return ApiResult.Failure(DomainError.NotFound)
        if (!OpenListPathCodec.isSafeNormalizedPath(mappedPath)) return ApiResult.Failure(DomainError.PathEncodeError)
        val instance = instanceRepository.getById(document.instanceId)
            ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return when (val detail = filesRepository.getFile(document.instanceId, mappedPath)) {
            is ApiResult.Failure -> detail
            is ApiResult.Success -> {
                val rawUrl = detail.data.rawUrl.takeIf { it.isNotBlank() }
                    ?: return ApiResult.Failure(DomainError.NotFound)
                ApiResult.Success(SystemDocumentReadSource(rawUrl, instance.baseUrl, tokenProvider.blockingTokenFor(document.instanceId), detail.data.size))
            }
        }
    }

    /** Names are deterministic once journaled and always stay beside the target. */
    fun namesFor(targetPath: String, transactionId: String, collisionSuffix: String = ""): SystemDocumentRemoteNames? {
        if (!OpenListPathCodec.isSafeNormalizedPath(targetPath) || transactionId.isBlank()) return null
        val parent = OpenListPathCodec.parent(targetPath)
        val suffix = collisionSuffix.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
        val stageName = ".openlist-android-$transactionId$suffix-stage"
        val backupName = ".openlist-android-$transactionId$suffix-backup"
        return SystemDocumentRemoteNames(
            stagePath = OpenListPathCodec.child(parent, stageName),
            backupPath = OpenListPathCodec.child(parent, backupName),
            stageName = stageName,
            backupName = backupName,
        )
    }

    fun localSha256(file: File): String = sha256(file)

    suspend fun findObject(instanceId: String, path: String): ApiResult<SystemDocumentRemoteObject?> {
        if (!OpenListPathCodec.isSafeNormalizedPath(path)) return ApiResult.Failure(DomainError.PathEncodeError)
        return when (val detail = filesRepository.getFile(instanceId, path)) {
            is ApiResult.Success -> ApiResult.Success(SystemDocumentRemoteObject(path, detail.data.size, detail.data.isDir, detail.data.rawUrl, detail.data.hashInfo))
            is ApiResult.Failure -> when (detail.error) {
                DomainError.NotFound -> ApiResult.Success(null)
                // Some OpenList storage drivers encode a missing fs/get target
                // as an envelope 5xx.  We never infer absence from that error:
                // a forced fresh parent listing must independently confirm it.
                DomainError.ServerError -> confirmAbsentByFreshListing(instanceId, path, detail.error)
                else -> detail
            }
        }
    }

    private suspend fun confirmAbsentByFreshListing(
        instanceId: String,
        path: String,
        originalError: DomainError,
    ): ApiResult<SystemDocumentRemoteObject?> {
        return when (val listing = filesRepository.listDirectory(
            instanceId = instanceId,
            path = OpenListPathCodec.parent(path),
            forceRefresh = true,
        ).first { it is FileListResult.Fresh || it is FileListResult.Error }) {
            is FileListResult.Fresh -> {
                if (listing.nodes.none { it.path == path }) ApiResult.Success(null)
                else ApiResult.Failure(originalError)
            }
            is FileListResult.Error -> ApiResult.Failure(listing.error)
            is FileListResult.Cached -> error("fresh-list selector cannot return a cached result")
        }
    }

    /**
     * Performs a synchronous non-overwriting upload, then verifies the exact
     * remote bytes. A pre-existing candidate is refused instead of guessed.
     */
    suspend fun uploadAndVerifyStage(instanceId: String, stagePath: String, localFile: File): ApiResult<SystemDocumentRemoteObject> {
        if (!localFile.isFile || !OpenListPathCodec.isSafeNormalizedPath(stagePath)) return ApiResult.Failure(DomainError.PathEncodeError)
        when (val existing = findObject(instanceId, stagePath)) {
            is ApiResult.Failure -> return existing
            is ApiResult.Success -> if (existing.data != null) return ApiResult.Failure(collisionError())
        }
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val expectedHash = sha256(localFile)
        val uploadUrl = "${instance.baseUrl.trimEnd('/')}/api/fs/put"
        val response = try {
            withContext(Dispatchers.IO) {
                localFile.inputStream().use { input ->
                    httpClient.uploadStage(
                        url = uploadUrl,
                        encodedRemotePath = OpenListPathCodec.encodePathForHeader(stagePath),
                        input = input,
                        contentLength = localFile.length(),
                        mimeType = "application/octet-stream",
                        headers = buildScopedHttpHeaders(uploadUrl, instance.baseUrl, tokenProvider.blockingTokenFor(instanceId)),
                    ).use { it.code to it.isSuccessful }
                }
            }
        } catch (error: IOException) {
            return ApiResult.Failure(DomainError.Unknown(error))
        }
        if (!response.second) return ApiResult.Failure(DomainError.OpenListError(response.first, "暂存上传未被后端确认"))
        return verifyObject(instanceId, stagePath, localFile.length(), expectedHash)
    }

    suspend fun verifyObject(instanceId: String, path: String, expectedSize: Long, expectedSha256: String): ApiResult<SystemDocumentRemoteObject> {
        val obj = when (val found = findObject(instanceId, path)) {
            is ApiResult.Failure -> return found
            is ApiResult.Success -> found.data ?: return ApiResult.Failure(DomainError.NotFound)
        }
        if (obj.sizeBytes != expectedSize || obj.rawUrl.isBlank()) return ApiResult.Failure(verificationError())
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        return when (val remoteHash = sha256Remote(obj.rawUrl, instance.baseUrl, tokenProvider.blockingTokenFor(instanceId))) {
            is ApiResult.Failure -> remoteHash
            is ApiResult.Success -> if (remoteHash.data.equals(expectedSha256, ignoreCase = true)) ApiResult.Success(obj) else ApiResult.Failure(verificationError())
        }
    }

    /** Same-directory rename plus destination and source facts. */
    suspend fun renameAndVerify(instanceId: String, sourcePath: String, destinationName: String): ApiResult<SystemDocumentRemoteObject> {
        if (!OpenListPathCodec.isSafeNormalizedPath(sourcePath) || !OpenListPathCodec.isSafeDocumentName(destinationName)) return ApiResult.Failure(DomainError.PathEncodeError)
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val destination = OpenListPathCodec.child(OpenListPathCodec.parent(sourcePath), destinationName)
        when (val result = safeApiCallUnit { api.fsRename(RenameReq(sourcePath, destinationName)) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> Unit
        }
        val destinationFact = when (val found = findObject(instanceId, destination)) {
            is ApiResult.Failure -> return found
            is ApiResult.Success -> found.data ?: return ApiResult.Failure(verificationError())
        }
        return when (val old = findObject(instanceId, sourcePath)) {
            is ApiResult.Success -> if (old.data == null) ApiResult.Success(destinationFact) else ApiResult.Failure(verificationError())
            is ApiResult.Failure -> old
        }
    }

    /** Removes only a known transaction object and proves it is absent afterwards. */
    suspend fun removeAndVerifyAbsent(instanceId: String, path: String): ApiResult<Unit> {
        if (!OpenListPathCodec.isSafeNormalizedPath(path)) return ApiResult.Failure(DomainError.PathEncodeError)
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val parent = OpenListPathCodec.parent(path)
        val name = OpenListPathCodec.name(path)
        when (val result = safeApiCallUnit { api.fsRemove(RemoveReq(parent, listOf(name))) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> Unit
        }
        return when (val after = findObject(instanceId, path)) {
            is ApiResult.Success -> if (after.data == null) ApiResult.Success(Unit) else ApiResult.Failure(verificationError())
            is ApiResult.Failure -> after
        }
    }

    suspend fun mkdirAndVerify(instanceId: String, path: String): ApiResult<SystemDocumentRemoteObject> {
        if (!OpenListPathCodec.isSafeNormalizedPath(path) || path == "/") return ApiResult.Failure(DomainError.PathEncodeError)
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        when (val result = safeApiCallUnit { api.fsMkdir(MkdirReq(path)) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> Unit
        }
        return when (val found = findObject(instanceId, path)) {
            is ApiResult.Failure -> found
            is ApiResult.Success -> found.data?.takeIf { it.isDirectory }?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(verificationError())
        }
    }

    suspend fun moveOrCopyAndVerify(
        instanceId: String,
        sourcePath: String,
        destinationDir: String,
        copy: Boolean,
        timeoutMillis: Long = DEFAULT_TASK_TIMEOUT_MILLIS,
    ): ApiResult<SystemDocumentRemoteObject> {
        if (!OpenListPathCodec.isSafeNormalizedPath(sourcePath) || !OpenListPathCodec.isSafeNormalizedPath(destinationDir)) return ApiResult.Failure(DomainError.PathEncodeError)
        val operation = if (copy) "copy" else "move"
        if (unsupportedOperations.contains("$instanceId:$operation")) return ApiResult.Failure(unsupportedError())
        val source = when (val found = findObject(instanceId, sourcePath)) {
            is ApiResult.Failure -> return found
            is ApiResult.Success -> found.data ?: return ApiResult.Failure(DomainError.NotFound)
        }
        val api = apiFor(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        val sourceDir = OpenListPathCodec.parent(sourcePath)
        val name = OpenListPathCodec.name(sourcePath)
        val submitted = if (copy) safeApiCall { api.fsCopyForSystemDocument(MoveCopyReq(sourceDir, destinationDir, listOf(name))) }
        else safeApiCall { api.fsMoveForSystemDocument(MoveCopyReq(sourceDir, destinationDir, listOf(name))) }
        val taskResponse = when (submitted) {
            is ApiResult.Failure -> {
                cacheUnsupportedIfExplicit(instanceId, operation, submitted.error)
                return submitted
            }
            is ApiResult.Success -> submitted.data
        }
        for (task in taskResponse.tasks) {
            if (task.id.isBlank()) return ApiResult.Failure(verificationError())
            when (val terminal = waitForTask(api, operation, task.id, timeoutMillis)) {
                is ApiResult.Failure -> return terminal
                is ApiResult.Success -> Unit
            }
        }
        val targetPath = OpenListPathCodec.child(destinationDir, name)
        val expectedHash = sha256Remote(source.rawUrl, instanceRepository.getById(instanceId)?.baseUrl ?: return ApiResult.Failure(DomainError.InvalidInstance), tokenProvider.blockingTokenFor(instanceId))
        val sourceHash = when (expectedHash) { is ApiResult.Success -> expectedHash.data; is ApiResult.Failure -> return expectedHash }
        val target = when (val verified = verifyObject(instanceId, targetPath, source.sizeBytes, sourceHash)) {
            is ApiResult.Success -> verified.data
            is ApiResult.Failure -> return verified
        }
        if (!copy) {
            when (val disappeared = findObject(instanceId, sourcePath)) {
                is ApiResult.Success -> if (disappeared.data != null) return ApiResult.Failure(verificationError())
                is ApiResult.Failure -> return disappeared
            }
        }
        return ApiResult.Success(target)
    }

    private suspend fun waitForTask(api: io.openlist.client.core.network.OpenListApi, type: String, taskId: String, timeoutMillis: Long): ApiResult<Unit> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val done = when (val result = safeApiCall { api.taskDone(type) }) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return result
            }
            val finished = done.firstOrNull { it.id == taskId }
            if (finished != null) {
                return when (TaskStateMapper.map(finished.state)) {
                    UnifiedTaskStatus.SUCCESS -> ApiResult.Success(Unit)
                    UnifiedTaskStatus.FAILED, UnifiedTaskStatus.CANCELLED -> ApiResult.Failure(DomainError.OpenListError(null, "远端任务未成功完成"))
                    else -> ApiResult.Failure(verificationError())
                }
            }
            val undone = when (val result = safeApiCall { api.taskUndone(type) }) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return result
            }
            val active = undone.firstOrNull { it.id == taskId }
            if (active == null) return ApiResult.Failure(verificationError())
            delay(TASK_POLL_INTERVAL_MILLIS)
        }
        // Cancellation is a best-effort cleanup only; timeout remains unknown and recoverable.
        safeApiCallUnit { api.taskCancel(type, taskId) }
        return ApiResult.Failure(DomainError.Timeout)
    }

    private suspend fun apiFor(instanceId: String): io.openlist.client.core.network.OpenListApi? {
        val instance = instanceRepository.getById(instanceId) ?: return null
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        return clientFactory.apiFor(instance.baseUrl)
    }

    private suspend fun sha256Remote(url: String, baseUrl: String, token: String?): ApiResult<String> = try {
        withContext(Dispatchers.IO) {
            httpClient.newContentCall(url, baseUrl, token).execute().use { response ->
                if (!response.isSuccessful) return@withContext ApiResult.Failure(DomainError.OpenListError(response.code, "远端内容校验未被后端确认"))
                val digest = MessageDigest.getInstance("SHA-256")
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(HASH_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) digest.update(buffer, 0, read)
                    }
                } ?: return@withContext ApiResult.Failure(verificationError())
                ApiResult.Success(digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
    } catch (error: IOException) {
        ApiResult.Failure(DomainError.Unknown(error))
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").also { digest ->
        file.inputStream().use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
    }.digest().joinToString("") { "%02x".format(it) }

    private fun cacheUnsupportedIfExplicit(instanceId: String, operation: String, error: DomainError) {
        val explicit = (error as? DomainError.OpenListError)?.code in setOf(404, 405, 501)
        if (explicit) unsupportedOperations += "$instanceId:$operation"
    }

    private fun collisionError() = DomainError.OpenListError(409, "远端暂存名称已被占用")
    private fun verificationError() = DomainError.OpenListError(null, "远端状态无法安全确认")
    private fun unsupportedError() = DomainError.OpenListError(405, "当前存储不支持此操作")

    private companion object {
        const val HASH_BUFFER_BYTES = 32 * 1024
        const val TASK_POLL_INTERVAL_MILLIS = 250L
        const val DEFAULT_TASK_TIMEOUT_MILLIS = 30_000L
    }
}
