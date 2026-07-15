package io.openlist.client.documents

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import dagger.hilt.android.EntryPointAccessors
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.domain.SystemDocumentsRepository
import io.openlist.client.core.model.SystemDocument
import io.openlist.client.core.model.SystemDocumentOpenMode
import io.openlist.client.core.model.SystemDocumentRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.util.UUID

/**
 * SAF bridge for v1.4. Flags come from fresh repository capability facts;
 * every mutating Binder call independently repeats the repository preflight.
 */
class OpenListDocumentsProvider : DocumentsProvider() {
    private lateinit var repository: SystemDocumentsRepository
    private lateinit var recoveryScope: CoroutineScope

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            DocumentsProviderEntryPoint::class.java,
        )
        repository = entryPoint.systemDocumentsRepository()
        recoveryScope = CoroutineScope(SupervisorJob() + entryPoint.dispatcherProvider().io)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = runBlocking {
        triggerRecovery()
        val columns = copyProjection(projection) ?: DEFAULT_ROOT_PROJECTION
        val cursor = MatrixCursor(columns)
        repository.observeRoots().first().forEach { root -> cursor.addRoot(root, columns) }
        cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val document = getDocumentOrThrow(documentId)
        val columns = copyProjection(projection) ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(columns).also { it.addDocument(document, columns) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = queryChildren(parentDocumentId, projection, null)

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (!isOpaqueId(parentDocumentId) || !isOpaqueId(documentId)) return false
        return runBlocking { repository.isChildDocument(parentDocumentId, documentId) }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (!isOpaqueId(parentDocumentId)) throw FileNotFoundException("Invalid parent documentId")
        val result = runBlocking {
            if (mimeType == Document.MIME_TYPE_DIR) repository.createDirectory(parentDocumentId, displayName)
            else repository.createDocument(parentDocumentId, displayName, mimeType)
        }
        return when (result) {
            is ApiResult.Success -> result.data.documentId
            is ApiResult.Failure -> throw FileNotFoundException("Remote create was not confirmed")
        }
    }

    override fun deleteDocument(documentId: String) {
        if (!isOpaqueId(documentId)) throw FileNotFoundException("Invalid documentId")
        when (runBlocking { repository.deleteDocument(documentId) }) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> throw FileNotFoundException("Remote delete was not confirmed")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (!isOpaqueId(documentId)) throw FileNotFoundException("Invalid documentId")
        return when (val result = runBlocking { repository.renameDocument(documentId, displayName) }) {
            is ApiResult.Success -> result.data.documentId
            is ApiResult.Failure -> throw FileNotFoundException("Remote rename was not confirmed")
        }
    }

    override fun moveDocument(documentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        if (!isOpaqueId(documentId) || !isOpaqueId(sourceParentDocumentId) || !isOpaqueId(targetParentDocumentId)) {
            throw FileNotFoundException("Invalid documentId")
        }
        return when (val result = runBlocking { repository.moveDocument(documentId, sourceParentDocumentId, targetParentDocumentId) }) {
            is ApiResult.Success -> result.data.documentId
            is ApiResult.Failure -> throw FileNotFoundException("Remote move was not confirmed")
        }
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        if (!isOpaqueId(sourceDocumentId) || !isOpaqueId(targetParentDocumentId)) throw FileNotFoundException("Invalid documentId")
        return when (val result = runBlocking { repository.copyDocument(sourceDocumentId, targetParentDocumentId) }) {
            is ApiResult.Success -> result.data.documentId
            is ApiResult.Failure -> throw FileNotFoundException("Remote copy was not confirmed")
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (!isOpaqueId(documentId)) throw FileNotFoundException("Invalid documentId")
        signal?.throwIfCanceled()
        val parsed = SystemDocumentOpenMode.parse(mode) ?: throw FileNotFoundException("Unsupported document mode")
        val callback = when (parsed) {
            SystemDocumentOpenMode.READ -> when (val result = runBlocking { repository.openRead(documentId) }) {
                is ApiResult.Success -> SystemDocumentProxyFileCallback(result.data)
                is ApiResult.Failure -> throw FileNotFoundException("Document is unavailable")
            }
            else -> when (val result = runBlocking { repository.openWrite(documentId, parsed) }) {
                is ApiResult.Success -> SystemDocumentProxyWriteCallback(result.data)
                is ApiResult.Failure -> throw FileNotFoundException("Local draft cannot be opened")
            }
        }
        signal?.setOnCancelListener {
            when (callback) {
                is SystemDocumentProxyFileCallback -> callback.releaseOnCancellation()
                is SystemDocumentProxyWriteCallback -> callback.onRelease()
            }
        }
        return try {
            (context ?: throw FileNotFoundException("Provider has no context"))
                .getSystemService(StorageManager::class.java)
                .openProxyFileDescriptor(
                if (parsed == SystemDocumentOpenMode.READ) ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_READ_WRITE,
                callback,
                Handler(Looper.getMainLooper()),
            )
        } catch (error: Throwable) {
            when (callback) {
                is SystemDocumentProxyFileCallback -> callback.releaseOnCancellation()
                is SystemDocumentProxyWriteCallback -> callback.onRelease()
            }
            throw FileNotFoundException("Unable to open document: ${error.message}")
        }
    }

    private fun queryChildren(
        parentDocumentId: String,
        projection: Array<out String>?,
        signal: CancellationSignal?,
    ): Cursor {
        if (!isOpaqueId(parentDocumentId)) throw FileNotFoundException("非法 documentId")
        signal?.throwIfCanceled()
        triggerRecovery()
        val columns = copyProjection(projection) ?: DEFAULT_DOCUMENT_PROJECTION
        val result = runBlocking { repository.listChildren(parentDocumentId) }
        signal?.throwIfCanceled()
        return MatrixCursor(columns).also { cursor ->
            if (result is ApiResult.Success) result.data.forEach { cursor.addDocument(it, columns) }
        }
    }

    private fun getDocumentOrThrow(documentId: String): SystemDocument {
        if (!isOpaqueId(documentId)) throw FileNotFoundException("非法 documentId")
        return when (val result = runBlocking { repository.getDocument(documentId) }) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> throw FileNotFoundException("文档不可用")
        }
    }

    private fun MatrixCursor.addRoot(root: SystemDocumentRoot, columns: Array<out String>) {
        val row = newRow()
        columns.forEach { column ->
            row.add(
                when (column) {
                    Root.COLUMN_ROOT_ID -> root.rootId
                    Root.COLUMN_DOCUMENT_ID -> root.documentId
                    Root.COLUMN_TITLE -> root.title
                    Root.COLUMN_SUMMARY -> if (root.isAuthenticated) null else "需要登录后访问"
                    Root.COLUMN_FLAGS -> Root.FLAG_SUPPORTS_IS_CHILD
                    Root.COLUMN_MIME_TYPES -> "*/*"
                    else -> null
                },
            )
        }
    }

    private fun MatrixCursor.addDocument(document: SystemDocument, columns: Array<out String>) {
        val row = newRow()
        columns.forEach { column ->
            row.add(
                when (column) {
                    Document.COLUMN_DOCUMENT_ID -> document.documentId
                    Document.COLUMN_DISPLAY_NAME -> document.displayName
                    Document.COLUMN_MIME_TYPE -> if (document.isDirectory) Document.MIME_TYPE_DIR else document.mimeType
                    Document.COLUMN_LAST_MODIFIED -> document.modifiedAt
                    Document.COLUMN_SIZE -> document.sizeBytes
                    Document.COLUMN_FLAGS -> documentFlags(document)
                    else -> null
                },
            )
        }
    }

    private fun isOpaqueId(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun documentFlags(document: SystemDocument): Int = buildList {
        val capability = document.capability
        if (capability.canWrite) add(Document.FLAG_SUPPORTS_WRITE)
        if (capability.canCreate) add(Document.FLAG_DIR_SUPPORTS_CREATE)
        if (capability.canDelete) add(Document.FLAG_SUPPORTS_DELETE)
        if (capability.canRename) add(Document.FLAG_SUPPORTS_RENAME)
        if (capability.canMove) add(Document.FLAG_SUPPORTS_MOVE)
        if (capability.canCopy) add(Document.FLAG_SUPPORTS_COPY)
    }.fold(0) { flags, flag -> flags or flag }

    /** Binder calls never wait for recovery; the coordinator serializes work. */
    private fun triggerRecovery() {
        recoveryScope.launch { repository.runRecovery() }
    }

    private fun copyProjection(projection: Array<out String>?): Array<String>? =
        projection?.let { source -> Array(source.size) { index -> source[index] } }

    private companion object {
        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
        )
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )
    }
}
