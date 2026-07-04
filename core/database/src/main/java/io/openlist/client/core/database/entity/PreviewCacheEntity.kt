package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local metadata record for one previewed file's cached preview content
 * (v0.4_EXECUTION_PLAN.md §11 S1-T2, P-415). The content body itself is
 * never stored here — it lives under `context.cacheDir/preview/<instanceId>/`
 * (S3+); this row only tracks where it is and whether it's still fresh.
 *
 * [id] is a synthetic primary key (same precedent as [SearchHistoryEntity]:
 * a stable synthetic id plus a unique lookup index, rather than composing the
 * primary key itself out of instanceId/path/kind) — this keeps REPLACE-based
 * upserts simple (one row is either inserted or replaced by id) while the
 * unique index below is what actually enforces "one cache row per
 * (instanceId, path, kind)". [cacheKey] carries that same
 * instanceId+path+kind identity pre-joined into one string (matching how
 * callers already address a specific preview target), so callers can look a
 * row up either by [cacheKey] directly or by the three columns separately
 * (e.g. "every cache row under this instance" for cleanup).
 *
 * [mimeType] and [lastModified] are the execution plan's explicit §8.2/§11.4
 * fields: [mimeType] is kept alongside [kind] for later rendering/fallback
 * content-type decisions, and [lastModified] is what S3's cache-hit path
 * must compare against the backend's `fs/get` metadata before trusting a
 * cached body (§10.1: "命中前校验 lastModified/size，元信息不一致即失效") —
 * [sizeBytes] doubles as the other half of that same staleness check.
 */
@Entity(
    tableName = "preview_cache",
    indices = [Index(value = ["instanceId", "path", "kind"], unique = true)],
)
data class PreviewCacheEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val path: String,
    val kind: String,
    val mimeType: String?,
    val lastModified: Long?,
    val cacheKey: String,
    val localFilePath: String,
    val sizeBytes: Long,
    val etag: String?,
    val expiresAt: Long?,
    val cachedAt: Long,
)
