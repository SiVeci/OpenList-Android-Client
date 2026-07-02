package io.openlist.client.core.model

/** Domain model for a directory entry (v0.1_PRD §5.3.3). [sign] is the query
 * param OpenList expects on /d //p download URLs for signed storages. */
data class FileNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedAt: Long?,
    val sign: String,
    val thumb: String,
    val type: Int,
)
