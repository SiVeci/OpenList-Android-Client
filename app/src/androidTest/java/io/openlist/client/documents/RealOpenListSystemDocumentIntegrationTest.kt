package io.openlist.client.documents

import android.database.sqlite.SQLiteDatabase
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.model.SystemDocumentOpenMode
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Explicit opt-in real-backend smoke. It creates a random test file only when
 * both runner arguments are supplied, and deletes the file before it returns.
 */
class RealOpenListSystemDocumentIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, DocumentsProviderEntryPoint::class.java)
    private var instanceId: String? = null
    private var documentId: String? = null

    @Test
    fun guestStrongCommitThenRandomReadAndDelete() = runBlocking {
        val baseUrl = arguments.getString(ARG_BASE_URL).orEmpty()
        val destructiveAllowed = arguments.getString(ARG_ALLOW_DESTRUCTIVE) == "true"
        assumeTrue("requires explicit isolated-backend arguments", baseUrl.isNotBlank() && destructiveAllowed)

        val id = UUID.randomUUID().toString()
        seedGuestInstance(id, baseUrl)
        instanceId = id
        val repository = entryPoint.systemDocumentsRepository()
        val root = repository.observeRoots().first().first { it.instanceId == id }
        val authority = "${context.packageName}.documents"
        assertFlag(root.documentId, authority, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
        val document = when (val created = repository.createDocument(root.documentId, "v14-${UUID.randomUUID()}.bin", "application/octet-stream")) {
            is ApiResult.Success -> created.data
            is ApiResult.Failure -> {
                val code = (created.error as? DomainError.OpenListError)?.code
                error(
                    "remote test document was not created: ${created.error::class.simpleName}/$code " +
                        "journal=${latestJournalDiagnostic(id)}",
                )
            }
        }
        documentId = document.documentId
        assertFlag(document.documentId, authority, DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
        assertFlag(document.documentId, authority, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)
        assertFlag(document.documentId, authority, DocumentsContract.Document.FLAG_SUPPORTS_RENAME)
        assertFlag(document.documentId, authority, DocumentsContract.Document.FLAG_SUPPORTS_MOVE)
        assertFlag(document.documentId, authority, DocumentsContract.Document.FLAG_SUPPORTS_COPY)

        val expected = "v1.4-system-document".encodeToByteArray()
        val handle = (repository.openWrite(document.documentId, SystemDocumentOpenMode.WRITE_TRUNCATE) as? ApiResult.Success)?.data
            ?: error("write handle unavailable")
        check(handle.write(0, expected) == expected.size) { "draft write failed" }
        check(handle.fsync() is ApiResult.Success) { "remote save was not verified" }
        handle.close()

        val reader = (repository.openRead(document.documentId) as? ApiResult.Success)?.data
            ?: error("read handle unavailable")
        try {
            assertArrayEquals(expected, reader.read(0, expected.size))
        } finally {
            reader.close()
        }
        check(repository.deleteDocument(document.documentId) is ApiResult.Success) { "remote test document cleanup failed" }
        documentId = null
    }

    @After
    fun cleanup() {
        runBlocking { documentId?.let { entryPoint.systemDocumentsRepository().deleteDocument(it) } }
        instanceId?.let(::deleteSeededInstance)
    }

    private fun seedGuestInstance(id: String, baseUrl: String) {
        withDatabase { database ->
            database.execSQL(
                "INSERT INTO instances(id, name, baseUrl, createdAt, updatedAt, lastUsedAt, isCurrent, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(id, "v14 integration", baseUrl, 1L, 1L, 1L, 0, "instrumentation only"),
            )
            database.execSQL(
                "INSERT INTO sessions(instanceId, authType, username, tokenEncrypted, role, permission, isGuest, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                // The real guest is explicitly granted write/rename/move/copy/delete
                // in the isolated root. Mirror those server operation bits so the
                // production capability gate, rather than a test-only bypass, runs.
                arrayOf(id, "GUEST", null, null, 0, 248, 1, 1L, 1L),
            )
        }
    }

    private fun deleteSeededInstance(id: String) {
        withDatabase { database ->
            database.execSQL("DELETE FROM system_write_transactions WHERE instanceId = ?", arrayOf(id))
            database.execSQL("DELETE FROM system_documents WHERE instanceId = ?", arrayOf(id))
            database.execSQL("DELETE FROM sessions WHERE instanceId = ?", arrayOf(id))
            database.execSQL("DELETE FROM instances WHERE id = ?", arrayOf(id))
        }
    }

    /** Contains only state-machine labels; remote paths and error text stay private. */
    private fun latestJournalDiagnostic(id: String): String {
        val database = SQLiteDatabase.openDatabase(context.getDatabasePath("openlist.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            return database.rawQuery(
                "SELECT state, failureStage, errorCode FROM system_write_transactions WHERE instanceId = ? ORDER BY updatedAt DESC LIMIT 1",
                arrayOf(id),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use "NONE"
                val state = cursor.getString(0) ?: "NULL"
                val stage = cursor.getString(1) ?: "NULL"
                val errorCode = cursor.getString(2) ?: "NULL"
                "$state/$stage/$errorCode"
            }
        } finally {
            database.close()
        }
    }

    private fun assertFlag(documentId: String, authority: String, expectedFlag: Int) {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUri(authority, documentId),
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null,
            null,
            null,
        ).use { cursor ->
            check(cursor?.moveToFirst() == true) { "provider did not return flags" }
            assertTrue("expected provider capability flag", cursor!!.getInt(0) and expectedFlag != 0)
        }
    }

    private fun withDatabase(block: (SQLiteDatabase) -> Unit) {
        val database = SQLiteDatabase.openDatabase(context.getDatabasePath("openlist.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val ARG_BASE_URL = "v14OpenListBaseUrl"
        const val ARG_ALLOW_DESTRUCTIVE = "v14AllowDestructive"
    }
}
