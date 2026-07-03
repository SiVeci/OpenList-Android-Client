package io.openlist.client.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v0.1.0 has shipped with real user data, so schema changes from here on are
 * hand-written Migrations registered in `di/DatabaseModule`, never a
 * destructive fallback (v0.2_EXECUTION_PLAN.md B3/P4).
 */

/** Adds the `/api/me` permission bitmask so write-permission gating (Sprint 3)
 * has a user-level fallback alongside the directory-level `FsListResp.write`. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN permission INTEGER NOT NULL DEFAULT 0")
    }
}

/** Adds `upload_tasks` (Sprint 6). Column order/types mirror UploadTaskEntity
 * exactly — cross-checked against Room's own generated createSql for the
 * other task tables in the exported schema JSON files, rather than guessed. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `upload_tasks` (" +
                "`id` TEXT NOT NULL, " +
                "`instanceId` TEXT NOT NULL, " +
                "`targetDir` TEXT NOT NULL, " +
                "`localUri` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, " +
                "`mimeType` TEXT, " +
                "`totalBytes` INTEGER, " +
                "`uploadedBytes` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`errorMessage` TEXT, " +
                "`workRequestId` TEXT, " +
                "`overwrite` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
