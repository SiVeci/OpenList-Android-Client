package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per instance (instanceId is the primary key — a 1:1 relationship).
 * A missing row means "never logged in / needs login". [tokenEncrypted] is the
 * Keystore-encrypted bearer token (null for guest sessions, since guests never
 * hold a token). "Session invalidated" (v0.1_PRD §5.2.4, e.g. a 401) is modeled
 * as deleting this row entirely — see SessionManager.invalidate — rather than a
 * separate flag, since a missing row already unambiguously means "go to login".
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val instanceId: String,
    val authType: String,
    val username: String?,
    val tokenEncrypted: String?,
    val role: Int,
    val isGuest: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
