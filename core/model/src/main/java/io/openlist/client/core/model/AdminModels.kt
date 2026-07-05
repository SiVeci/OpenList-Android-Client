package io.openlist.client.core.model

import kotlinx.serialization.Serializable

/**
 * Domain models for the v0.5 lightweight admin console
 * (v0.5_EXECUTION_PLAN.md §8.1, PRD §11). None of these are Room entities —
 * the only Room-backed piece is `AdminCacheEntity` (`core:database`), which
 * stores a serialized form of a subset of these models, never a raw backend
 * response (§8.3/P-507).
 *
 * [AdminUserSummary]/[AdminUserPage]/[AdminStorageSummary]/[AdminStorageDetails]/
 * [AdminStoragePage] are `@Serializable` (S3-T1/T3) so `admin_cache.rawJson` can
 * hold their `Json.encodeToString` output directly — this is the *only* reason
 * `core:model` depends on kotlinx.serialization at all; nothing here is ever
 * decoded from a raw backend response (that happens one layer down, at the DTO
 * boundary in `core:network`/`data:repository`).
 */

/**
 * Client-side gating state produced by `AdminGateRepository` (PRD §10.2).
 * `ALLOWED` is a **local pre-check only** — every admin API call still
 * goes through the server's own `AuthAdmin` middleware, and a 401/403 from
 * any admin endpoint must be treated as authoritative even if this state was
 * `ALLOWED` a moment before (PRD §17.4.5 "本地误判管理员身份时，最终以后端结果为准").
 */
enum class AdminAccessState { CHECKING, ALLOWED, DENIED_NOT_ADMIN, DENIED_GUEST, SESSION_EXPIRED, ERROR }

/**
 * Read-only projection of `admin/user/list`/`get` (PRD §11.1). Deliberately
 * has **no** `password`/`pwdHash`/`salt`/`otpSecret` fields — the backend's
 * `model.User` never serializes the latter three (`json:"-"`) and
 * `AdminUserDto.password` (the one exposed-but-always-blank field) is
 * dropped at the DTO->domain mapping boundary in `data:repository` (S3), not
 * carried into this model, so nothing downstream (cache/UI/logs) can
 * accidentally leak it even by a future careless `copy()`.
 */
@Serializable
data class AdminUserSummary(
    val id: Int,
    val username: String,
    val role: Int,
    val roleLabel: String,
    val disabled: Boolean,
    val basePath: String?,
    val permission: Int?,
    /** See `AdminUserDto.otp` KDoc (`core:network`) — no confirmed backend
     * field for 2FA-enabled status was found in the source checkout, so this
     * is best-effort/provisional until verified against a real instance. */
    val otpEnabled: Boolean?,
)

@Serializable
data class AdminUserPage(
    val users: List<AdminUserSummary>,
    val total: Long,
)

/** Display-only storage state derived from the backend's free-form `status`
 * string field (`model.Storage.Status`) plus [AdminStorageSummary.disabled] —
 * exact string values are backend/driver-dependent (often "work" when
 * healthy, or a driver error message), so this enum only distinguishes the
 * client-actionable buckets rather than mirroring the raw string 1:1. */
@Serializable
enum class AdminStorageStatus { ENABLED, DISABLED, ERROR, UNKNOWN }

/** `model.StorageDetails` (embeds `DiskUsage`) — only present when the
 * backend's driver implements `driver.WithDetails` and responds within its
 * 3s collection window; absent otherwise (PRD §9.3.3). */
@Serializable
data class AdminStorageDetails(
    val totalSpace: Long,
    val usedSpace: Long,
)

@Serializable
data class AdminStorageSummary(
    val id: Int,
    val mountPath: String,
    val driver: String,
    val disabled: Boolean,
    val order: Int?,
    val remark: String?,
    val status: AdminStorageStatus,
    val mountDetails: AdminStorageDetails?,
)

@Serializable
data class AdminStoragePage(
    val storages: List<AdminStorageSummary>,
    val total: Long,
)

/**
 * Admin-视角 task row (PRD §11.3) — a projection of the existing
 * `TaskInfoDto` (`core:network`; not modeled again here — `core:model` has no
 * dependency on `core:network`, same as [RemoteTask]'s existing precedent,
 * so this is deliberately not a KDoc `[...]` link). Deliberately **not** a
 * reuse/extension of [RemoteTask]: that type's shape is tied to the
 * `remote_tasks` Room table (only 4 task types, persisted), whereas
 * [AdminTask] covers 7 task types, is admin-scoped (every user's tasks, not
 * just the caller's), and per v0.5_EXECUTION_PLAN.md §7.1/B-503 is never
 * persisted to Room (in-memory `StateFlow` only in `AdminTaskRepositoryImpl`).
 */
data class AdminTask(
    val id: String,
    val instanceId: String,
    val taskType: String,
    val name: String,
    val creator: String?,
    val creatorRole: Int?,
    val state: UnifiedTaskStatus,
    val statusText: String?,
    val progress: Float?,
    val totalBytes: Long?,
    val error: String?,
    val startTime: Long?,
    val endTime: Long?,
)

/**
 * `admin/index/progress` (PRD §11.4). [isRunning] is a **client-derived**
 * field, not a backend field: the backend's `search.Running()` state isn't
 * exposed directly on this endpoint, so it must be inferred from [isDone]
 * and [error] (e.g. `!isDone && error.isNullOrBlank()`) by the repository —
 * see `AdminIndexRepositoryImpl` (S6) for the exact derivation, refined
 * against real backend behavior (V-506).
 */
data class AdminIndexProgress(
    val objCount: Long,
    val isDone: Boolean,
    val lastDoneTime: Long?,
    val error: String?,
    val isRunning: Boolean,
)

/**
 * `admin/setting/{list,get,default}` row (PRD §11.5). [isPrivate] is
 * client-derived (§10.5/P-508): `flag == 1` (PRIVATE) OR the key matches a
 * token/secret/password/key keyword — defense in depth in case the backend
 * ever under-flags a sensitive setting. [value] must already be blanked by
 * the repository layer when [isPrivate] is true before this model is
 * constructed (never rely on UI to mask it after the fact).
 */
data class AdminSettingItem(
    val key: String,
    val value: String?,
    val type: String?,
    val group: Int?,
    val flag: Int?,
    val isPrivate: Boolean,
)

/**
 * Overview Tab aggregation (PRD §12.2/§11 "AdminOverview聚合模型"). Every
 * summary field is independently loadable/nullable so one slow/failed
 * section (e.g. mount_details) never blocks the others from rendering
 * (v0.5_EXECUTION_PLAN.md §9.1/§16.3.4 "首次打开不应阻塞在所有 Tab 数据全部加载完成").
 */
data class AdminOverview(
    val instanceId: String,
    val instanceName: String,
    val baseUrl: String,
    val adminUsername: String?,
    val storageEnabledCount: Int?,
    val storageDisabledCount: Int?,
    val runningTaskCount: Int?,
    val indexIsRunning: Boolean?,
    val indexObjCount: Long?,
)

/** Which admin section a Web-fallback deep link targets (PRD §12.8/§10.8);
 * used only to pick a label/URL fragment, never to gate native functionality. */
enum class AdminWebSection { HOME, USERS, STORAGES, SETTINGS, TASKS, INDEX }

/**
 * A constructed, validated Web-console fallback destination (PRD §10.8).
 * [url] is always prefixed by the current instance's base URL (including any
 * deploy sub-path) — `AdminWebFallbackRepository` (S7) must reject/refuse to
 * build a [WebFallbackTarget] for any other origin. No token is ever embedded
 * in [url] (PRD §15.3).
 */
data class WebFallbackTarget(
    val url: String,
    val sectionLabel: String,
    val requiresWebLogin: Boolean,
)
