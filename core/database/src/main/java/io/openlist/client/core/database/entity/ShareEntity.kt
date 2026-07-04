package io.openlist.client.core.database.entity

import androidx.room.Entity

/**
 * Local cache of one OpenList share (v0.3_EXECUTION_PLAN.md §11). [filesJson]
 * is the raw JSON array of backend `files` paths (a share may cover multiple
 * paths when created from the Web UI); [primaryPath] is the first entry,
 * kept denormalized so list rows don't need to parse JSON to render.
 * [password] is stored in plaintext — the backend itself returns it in
 * plaintext and it is a user-distributed share secret, not an instance
 * credential (P3). [rawJson] is the full decoded response, excluding no
 * server-only secret (shares carry none), kept for forward-compatible fields.
 */
@Entity(tableName = "shares", primaryKeys = ["id", "instanceId"])
data class ShareEntity(
    val id: String,
    val instanceId: String,
    val filesJson: String,
    val primaryPath: String,
    val name: String?,
    val shareUrl: String?,
    val password: String?,
    val enabled: Boolean,
    val expiresAt: Long?,
    val accessed: Int,
    val maxAccessed: Int,
    val creator: String?,
    val rawJson: String,
    val createdAt: Long?,
    val updatedAt: Long?,
    val cachedAt: Long,
)
