package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * admin API DTOs (v0.5_EXECUTION_PLAN.md §8.2/S1-T3). Field lists are a
 * deliberate whitelist cross-checked against the real backend source
 * (`openlist-ref/internal/model/user.go`, `.../storage.go`, `.../search.go`,
 * `openlist-ref/server/handles/{user,storage,driver,setting,index}.go`) rather
 * than guessed: every sensitive `model.User` field (`PwdHash`/`PwdTS`/`Salt`/
 * `OtpSecret`/`Authn`) is tagged `json:"-"` server-side, so it never appears
 * in the JSON body at all — [ignoreUnknownKeys] (NetworkModule) is defense in
 * depth, not the primary guard. `password` IS present in the JSON
 * (`model.User.Password string `json:"password"``) but the backend always
 * zero-fills it before responding to list/get (only ever non-blank right after
 * a client sends a create/update `password` field back to itself, which admin
 * user list/get never does) — [AdminUserDto.password] is declared so decoding
 * doesn't fail on an unexpected non-blank value, but the domain mapper
 * (`data:repository`, S3) must never surface it.
 *
 * Task DTOs are intentionally NOT added here — the admin task endpoints
 * reuses the existing [TaskInfoDto] (`TaskDtos.kt`) verbatim (confirmed
 * identical shape via `openlist-ref/server/handles/task.go` `TaskInfo`).
 */

// ---- Users (admin/user/list, admin/user/get) ----

/**
 * One user row. Source: `internal/model/user.go` `User` struct's exported
 * (non `json:"-"`) fields. [password] is always blank on read paths (see file
 * doc) but declared to avoid a decode failure if that ever changes;
 * `data:repository`'s domain mapper must not surface it (v0.5 hard
 * requirement, PRD §11.1/§14.1).
 */
@Serializable
data class AdminUserDto(
    val id: Int = 0,
    val username: String = "",
    val password: String = "",
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = 0,
    val disabled: Boolean = false,
    val permission: Int = 0,
    @SerialName("sso_id") val ssoId: String = "",
    @SerialName("allow_ldap") val allowLdap: Boolean = true,
    /** Not an actual backend field name confirmed for 2FA status in this
     * checkout (no `otp`/`otp_enabled` field found on `model.User` — 2FA is
     * tracked via the `json:"-"` `OtpSecret` alone); kept optional/defaulted
     * so a future backend addition decodes without a crash, but the domain
     * layer must derive `otpEnabled` conservatively (e.g. always false/unknown)
     * until a real field is confirmed — see `AdminUserSummary.otpEnabled` KDoc. */
    val otp: Boolean = false,
)

/** data payload of `GET /api/admin/user/list`. Source: `server/common.PageResp`
 * (same envelope shape as [SharePageResp]). */
@Serializable
data class AdminUserPageDto(
    val content: List<AdminUserDto> = emptyList(),
    val total: Long = 0,
)

// ---- Storages (admin/storage/list, admin/storage/get) ----

/**
 * One storage row. Source: `internal/model/storage.go` `Storage` struct
 * (embeds `Sort`/`Proxy`, only the fields PRD §11.2/§9.3.3 need are modeled
 * here — the rest fall through `ignoreUnknownKeys`). [mountDetails] is
 * `omitempty`d server-side when null/absent (`StorageResp.MountDetails
 * *model.StorageDetails json:"mount_details,omitempty"`) and is computed with
 * a 3s timeout server-side (`makeStorageResp`), so it is always nullable here.
 */
@Serializable
data class AdminStorageDto(
    val id: Int = 0,
    @SerialName("mount_path") val mountPath: String = "",
    val order: Int = 0,
    val driver: String = "",
    @SerialName("cache_expiration") val cacheExpiration: Int = 0,
    @SerialName("custom_cache_policies") val customCachePolicies: String = "",
    val status: String = "",
    val remark: String = "",
    val modified: String? = null,
    val disabled: Boolean = false,
    @SerialName("disable_index") val disableIndex: Boolean = false,
    @SerialName("enable_sign") val enableSign: Boolean = false,
    @SerialName("mount_details") val mountDetails: AdminStorageDetailsDto? = null,
)

/** `model.StorageDetails` embeds `DiskUsage{TotalSpace, UsedSpace int64}` with
 * no json tags — Go's default field-name-as-key applies, so the JSON keys are
 * the Go field names verbatim (`TotalSpace`/`UsedSpace`), not snake_case. */
@Serializable
data class AdminStorageDetailsDto(
    @SerialName("TotalSpace") val totalSpace: Long = 0,
    @SerialName("UsedSpace") val usedSpace: Long = 0,
)

/** data payload of `GET /api/admin/storage/list`. */
@Serializable
data class AdminStoragePageDto(
    val content: List<AdminStorageDto> = emptyList(),
    val total: Long = 0,
)

// ---- Drivers (admin/driver/list, names, info) ----
// `ListDriverInfo`/`GetDriverInfo` return `driver.Info` (Common/Additional/Config
// item-metadata trees), a dynamic shape not worth a fixed data class for a
// read-only display (PRD §9.3: "结构复杂时只展示驱动名+关键摘要"). Modeled as
// a generic JSON map so the UI can walk it defensively without a decode
// failure; `ListDriverNames` is a plain `List<String>` (no DTO needed, wired
// directly on the OpenListApi method below).
typealias AdminDriverInfoMap = Map<String, JsonElement>

// ---- Index (admin/index/progress) ----

/** Source: `internal/model/search.go` `IndexProgress`. [lastDoneTime] is Go's
 * `*time.Time` (RFC3339 string or null), never a bare epoch number. */
@Serializable
data class AdminIndexProgressDto(
    @SerialName("obj_count") val objCount: Long = 0,
    @SerialName("is_done") val isDone: Boolean = false,
    @SerialName("last_done_time") val lastDoneTime: String? = null,
    val error: String = "",
)

/** `POST /api/admin/index/update` request body. Source:
 * `server/handles/index.go` `UpdateIndexReq`. [maxDepth] default here is just
 * this DTO's Go-zero-value fallback if a caller somehow skips the argument --
 * `AdminIndexRepository.updateIndex`'s own default (`-1`, DEC-504/S6) is what
 * actually reaches the backend in practice; see that interface's KDoc for why
 * `0` (this DTO's default) is a *depth-0/no-recursion* request, not "no
 * limit". */
@Serializable
data class AdminIndexUpdateReq(
    val paths: List<String> = listOf("/"),
    @SerialName("max_depth") val maxDepth: Int = 0,
)

// ---- Settings (admin/setting/list, get, default) ----

/** Source: `internal/model/setting.go` `SettingItem`. `Flag` semantics:
 * PUBLIC=0/PRIVATE=1/READONLY=2/DEPRECATED=3 (confirmed by the `model`
 * constants block). */
@Serializable
data class AdminSettingDto(
    val key: String = "",
    val value: String = "",
    val help: String = "",
    val type: String = "",
    val options: String = "",
    val group: Int = 0,
    val flag: Int = 0,
    val index: Int = 0,
)
