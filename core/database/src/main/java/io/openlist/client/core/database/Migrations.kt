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
