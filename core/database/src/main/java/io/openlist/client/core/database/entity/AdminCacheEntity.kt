package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * General-purpose local cache row for the v0.5 admin console (PRD §11.6,
 * v0.5_EXECUTION_PLAN.md §8.3). Only the 3 slow-changing admin lists (users,
 * storages, settings) are cached here — task lists and index progress are
 * never persisted (real-time/poll-only semantics, B-503/§13.1).
 *
 * [rawJson] stores the **serialized domain model**, never the raw backend
 * response (P-507): by the time a row is written here, sensitive fields have
 * already been filtered out (users: no password/hash/salt/otp) or masked
 * (settings: private values blanked) at the DTO->domain mapping boundary, so
 * a compromised/inspected cache row can't leak them even in principle.
 *
 * [scope] is a short string enum-like discriminator (e.g. "users"/
 * "storages"/"settings") rather than a Room-level enum column, matching this
 * project's existing precedent of storing enum-shaped data as plain TEXT
 * (see [io.openlist.client.core.database.entity.UploadTaskEntity.status]).
 * [cacheKey] carries whatever finer-grained identity a scope needs (e.g. a
 * settings group id, or a fixed constant for a scope with only one row) —
 * same "synthetic id + separately-addressable columns" shape as
 * [PreviewCacheEntity].
 */
@Entity(
    tableName = "admin_cache",
    indices = [Index(value = ["instanceId", "scope", "cacheKey"], unique = true)],
)
data class AdminCacheEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val scope: String,
    val cacheKey: String,
    val rawJson: String,
    val cachedAt: Long,
)
