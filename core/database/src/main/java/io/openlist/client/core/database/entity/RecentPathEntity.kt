package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One recently opened directory path, isolated per instance. The composite
 * primary key lets revisiting a path refresh [visitedAt] instead of creating a
 * duplicate row.
 */
@Entity(
    tableName = "recent_paths",
    primaryKeys = ["instanceId", "path"],
    indices = [Index(value = ["instanceId", "visitedAt"])],
)
data class RecentPathEntity(
    val instanceId: String,
    val path: String,
    val displayName: String,
    val visitedAt: Long,
)
