package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.SystemDocument
import io.openlist.client.core.model.SystemDocumentOpenMode
import io.openlist.client.core.model.SystemDocumentRoot
import io.openlist.client.core.model.SystemWriteTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Provider-facing contract for v1.4. Implementations own transaction
 * composition; DocumentsProvider must never orchestrate remote mutations.
 */
interface SystemDocumentsRepository {
    fun observeRoots(): Flow<List<SystemDocumentRoot>>
    suspend fun getDocument(documentId: String): ApiResult<SystemDocument>
    suspend fun listChildren(parentDocumentId: String): ApiResult<List<SystemDocument>>
    suspend fun createDocument(parentDocumentId: String, displayName: String, mimeType: String): ApiResult<SystemDocument>
    suspend fun createDirectory(parentDocumentId: String, displayName: String): ApiResult<SystemDocument>
    suspend fun deleteDocument(documentId: String): ApiResult<Unit>
    suspend fun renameDocument(documentId: String, displayName: String): ApiResult<SystemDocument>
    suspend fun moveDocument(documentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): ApiResult<SystemDocument>
    suspend fun copyDocument(documentId: String, targetParentDocumentId: String): ApiResult<SystemDocument>
    suspend fun isChildDocument(parentDocumentId: String, childDocumentId: String): Boolean
    suspend fun openRead(documentId: String): ApiResult<SystemDocumentReadHandle>
    suspend fun openWrite(documentId: String, mode: SystemDocumentOpenMode): ApiResult<SystemDocumentWriteHandle>
    fun observeRecoverableTransactions(instanceId: String): Flow<List<SystemWriteTransaction>>
    suspend fun retrySave(transactionId: String): ApiResult<Unit>
    suspend fun exportDraft(transactionId: String, destinationUri: String): ApiResult<Unit>
    suspend fun deleteDraft(transactionId: String): ApiResult<Unit>
    suspend fun runRecovery(instanceId: String? = null): ApiResult<Unit>
}

interface SystemDocumentReadHandle : AutoCloseable {
    val sizeBytes: Long
    suspend fun read(offset: Long, size: Int): ByteArray
    override fun close()
}

interface SystemDocumentWriteHandle : AutoCloseable {
    val sizeBytes: Long
    suspend fun read(offset: Long, size: Int): ByteArray
    suspend fun write(offset: Long, bytes: ByteArray): Int
    suspend fun truncate(size: Long)
    suspend fun fsync(): ApiResult<Unit>
    override fun close()
}
