package io.openlist.client.documents

import android.provider.DocumentsContract
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-process authority/manifest smoke test; no backend or credential is used. */
class OpenListDocumentsProviderSmokeTest {
    @Test
    fun authorityIsDiscoverableAndEmptyRootsQueryIsSafe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "${context.packageName}.documents"
        val provider = context.packageManager.resolveContentProvider(authority, 0)

        assertNotNull(provider)
        assertEquals("android.permission.MANAGE_DOCUMENTS", provider?.readPermission)
        assertTrue(provider?.grantUriPermissions == true)

        context.contentResolver.query(DocumentsContract.buildRootsUri(authority), null, null, null, null).use { cursor ->
            assertNotNull(cursor)
            // Fresh test installs have no configured OpenList instance. The
            // provider must still return a valid, empty cursor rather than
            // crashing or attempting any remote request.
            assertEquals(0, cursor?.count)
        }
    }

    @Test
    fun rootsAreStablePerInstanceAndDocumentFlagsStayReadOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "${context.packageName}.documents"
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath("openlist.db").path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            database.execSQL("DELETE FROM system_documents")
            database.execSQL("DELETE FROM instances")
            insertInstance(database, "fixture-a", "甲")
            insertInstance(database, "fixture-b", "乙")

            val first = queryRoots(context, authority)
            assertEquals(2, first.size)
            assertEquals(setOf("OpenList · 甲", "OpenList · 乙"), first.keys)

            database.execSQL("UPDATE instances SET name = '甲（改名）' WHERE id = 'fixture-a'")
            val afterRename = queryRoots(context, authority)
            assertEquals(first["OpenList · 甲"], afterRename["OpenList · 甲（改名）"])

            val rootDocumentId = afterRename.getValue("OpenList · 甲（改名）")
            context.contentResolver.query(
                DocumentsContract.buildDocumentUri(authority, rootDocumentId),
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null,
            ).use { cursor ->
                assertTrue(cursor?.moveToFirst() == true)
                assertEquals(0, cursor?.getInt(0))
            }
        } finally {
            database.execSQL("DELETE FROM system_documents")
            database.execSQL("DELETE FROM instances")
            database.close()
        }
    }

    private fun queryRoots(context: android.content.Context, authority: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        context.contentResolver.query(DocumentsContract.buildRootsUri(authority), null, null, null, null).use { cursor ->
            assertNotNull(cursor)
            val titleColumn = cursor!!.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_TITLE)
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) result[cursor.getString(titleColumn)] = cursor.getString(idColumn)
        }
        return result
    }

    private fun insertInstance(database: SQLiteDatabase, id: String, name: String) {
        database.execSQL(
            "INSERT INTO instances(id, name, baseUrl, createdAt, updatedAt, lastUsedAt, isCurrent, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(id, name, "https://$id.invalid", 1L, 1L, 1L, 0, null),
        )
    }
}
