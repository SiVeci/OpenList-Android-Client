package io.openlist.client.core.database.entity

import androidx.room.Entity

/**
 * One row per directory entry. [parentPath] is the listed directory (query key);
 * [path] is this entry's own full path (its own directory listing key if it's
 * itself a folder). (instanceId, path) is naturally unique, so no synthetic id.
 */
@Entity(tableName = "file_cache", primaryKeys = ["instanceId", "path"])
data class FileCacheEntity(
    val instanceId: String,
    val path: String,
    val parentPath: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedAt: Long?,
    val sign: String,
    val thumb: String,
    val type: Int,
    val cachedAt: Long,
)
