package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved search keyword (v0.3_EXECUTION_PLAN.md §11), isolated per instance
 * and per scope so the same keyword searched in different directories keeps
 * separate history rows. The unique index lets an upsert (OnConflict.REPLACE)
 * refresh [searchedAt] for a repeated (instanceId, keyword, scopePath) instead
 * of accumulating duplicates.
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["instanceId", "keyword", "scopePath"], unique = true)],
)
data class SearchHistoryEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val keyword: String,
    val scopePath: String?,
    val searchedAt: Long,
)
