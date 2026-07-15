package io.openlist.client.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real published-schema migration test. It deliberately starts from 10.json,
 * seeds a shipped table, migrates with the handwritten SQL, then validates the
 * Room-generated 11 schema and both newly added tables.
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(OpenListDatabase::class.java.canonicalName),
    )

    @Test
    fun migrate10To11_preservesExistingRowsAndCreatesSystemDocumentTables() {
        val databaseName = "migration-10-11-test"
        helper.createDatabase(databaseName, 10).use { db ->
            seedPublishedData(db)
        }

        helper.runMigrationsAndValidate(databaseName, 11, true, MIGRATION_10_11).use { db ->
            legacyTables.forEach { tableName ->
                db.query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("legacy row lost from $tableName", 1, cursor.getInt(0))
                }
            }
            assertTableExists(db, "system_documents")
            assertTableExists(db, "system_write_transactions")
        }
    }

    private fun seedPublishedData(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO instances VALUES ('instance-10', 'v1.3 instance', 'https://example.test', 1, 1, 1, 1, NULL)")
        db.execSQL("INSERT INTO sessions VALUES ('instance-10', 'PASSWORD', 'tester', 'ciphertext', 0, 0, 0, 1, 1)")
        db.execSQL("INSERT INTO file_cache VALUES ('instance-10', '/old.txt', '/', 'old.txt', 0, 1, 1, '', '', 0, 1)")
        db.execSQL("INSERT INTO download_tasks VALUES ('download-10', 'instance-10', '/old.txt', 'old.txt', NULL, NULL, NULL, 'SUCCESS', NULL, NULL, 1, 1)")
        db.execSQL("INSERT INTO upload_tasks VALUES ('upload-10', 'instance-10', '/', 'content://test/upload', 'upload.txt', NULL, NULL, 0, 'SUCCESS', NULL, NULL, 0, 1, 1)")
        db.execSQL("INSERT INTO shares VALUES ('share-10', 'instance-10', '[]', '/old.txt', NULL, NULL, NULL, 1, NULL, 0, 0, NULL, '{}', NULL, NULL, 1)")
        db.execSQL("INSERT INTO search_history VALUES ('search-10', 'instance-10', 'old', NULL, 1)")
        db.execSQL("INSERT INTO remote_tasks VALUES ('remote-10', 'instance-10', 'copy', 'copy old', 0, 'SUCCESS', NULL, NULL, NULL, NULL, '{}', NULL, NULL, 1)")
        db.execSQL("INSERT INTO preview_cache VALUES ('preview-10', 'instance-10', '/old.txt', 'TEXT', NULL, NULL, 'key', 'cache.txt', 1, NULL, NULL, 1)")
        db.execSQL("INSERT INTO admin_cache VALUES ('admin-10', 'instance-10', 'users', 'key', '{}', 1)")
        db.execSQL("INSERT INTO recent_paths VALUES ('instance-10', '/', '根目录', 1)")
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, tableName: String) {
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            assertTrue("missing table: $tableName", cursor.moveToFirst())
        }
    }

    private companion object {
        val legacyTables = listOf(
            "instances", "sessions", "file_cache", "download_tasks", "upload_tasks", "shares",
            "search_history", "remote_tasks", "preview_cache", "admin_cache", "recent_paths",
        )
    }
}
