package io.openlist.client.core.network

import io.openlist.client.core.network.dto.AddOfflineDownloadReq
import io.openlist.client.core.network.dto.AddOfflineDownloadResp
import io.openlist.client.core.network.dto.AdminDriverInfoMap
import io.openlist.client.core.network.dto.AdminIndexProgressDto
import io.openlist.client.core.network.dto.AdminIndexUpdateReq
import io.openlist.client.core.network.dto.AdminSettingDto
import io.openlist.client.core.network.dto.AdminStorageDto
import io.openlist.client.core.network.dto.AdminStoragePageDto
import io.openlist.client.core.network.dto.AdminUserDto
import io.openlist.client.core.network.dto.AdminUserPageDto
import io.openlist.client.core.network.dto.ApiResponse
import io.openlist.client.core.network.dto.FsGetReq
import io.openlist.client.core.network.dto.FsGetResp
import io.openlist.client.core.network.dto.FsListReq
import io.openlist.client.core.network.dto.FsListResp
import io.openlist.client.core.network.dto.LoginHashReq
import io.openlist.client.core.network.dto.LoginReq
import io.openlist.client.core.network.dto.LoginResp
import io.openlist.client.core.network.dto.MkdirReq
import io.openlist.client.core.network.dto.MoveCopyReq
import io.openlist.client.core.network.dto.RemoveReq
import io.openlist.client.core.network.dto.RenameReq
import io.openlist.client.core.network.dto.SearchPageResp
import io.openlist.client.core.network.dto.SearchReq
import io.openlist.client.core.network.dto.SharePageResp
import io.openlist.client.core.network.dto.SharingResp
import io.openlist.client.core.network.dto.TaskInfoDto
import io.openlist.client.core.network.dto.UpdateSharingReq
import io.openlist.client.core.network.dto.UserResp
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OpenList REST endpoints used in v0.1. Paths are relative (no leading '/') so
 * they resolve against an instance base URL that may include a deploy sub-path
 * (conf.URL.Path on the server side).
 *
 * Reserved endpoints (search / fs mutations / upload / share / task / admin) are
 * added in their respective sprints together with their DTOs — see
 * v0.1_EXECUTION_PLAN.md §8.3.
 */
interface OpenListApi {

    /** Connectivity probe — returns the plain text "pong". */
    @GET("ping")
    suspend fun ping(): Response<ResponseBody>

    /** Public settings map — fallback reachability / instance-type check. */
    @GET("api/public/settings")
    suspend fun publicSettings(): ApiResponse<Map<String, String>>

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginReq): ApiResponse<LoginResp>

    /**
     * LDAP login (v1.0_PRD §4.2.B.1; v1.0_EXECUTION_PLAN.md V-601). Reuses
     * [LoginReq]'s username/password fields — `otpCode` is never populated for
     * this call since the server's LDAP path has no OTP branch (source-
     * confirmed absence in `ldap_login.go`). Failures are always plain HTTP
     * 403 (LDAP disabled / this account not allowed) or 400 (bad credentials)
     * or 429 (rate-limited), never an in-envelope error code.
     */
    @POST("api/auth/login/ldap")
    suspend fun loginLdap(@Body req: LoginReq): ApiResponse<LoginResp>

    /** Reserved: login with client-side hashed password. Not wired to UI in v0.1. */
    @POST("api/auth/login/hash")
    suspend fun loginHash(@Body req: LoginHashReq): ApiResponse<LoginResp>

    /** Current user; returns the guest user when the Authorization header is absent. */
    @GET("api/me")
    suspend fun me(): ApiResponse<UserResp>

    /**
     * Same endpoint, with an explicit token bypassing [interceptor.AuthInterceptor].
     * Used only to validate a freshly-obtained token/site-token *before* it is
     * persisted (AuthInterceptor can only attach what's already saved, so the
     * bootstrap login/token-login flows must pass the value directly once).
     */
    @GET("api/me")
    suspend fun meWithToken(@Header("Authorization") token: String): ApiResponse<UserResp>

    @POST("api/fs/list")
    suspend fun fsList(@Body req: FsListReq): ApiResponse<FsListResp>

    @POST("api/fs/get")
    suspend fun fsGet(@Body req: FsGetReq): ApiResponse<FsGetResp>

    /** `data` is always null on success (server calls `common.SuccessResp(c)` with no payload). */
    @POST("api/fs/mkdir")
    suspend fun fsMkdir(@Body req: MkdirReq): ApiResponse<JsonElement?>

    /** `data` is always null on success. */
    @POST("api/fs/rename")
    suspend fun fsRename(@Body req: RenameReq): ApiResponse<JsonElement?>

    /** `data` is always null on success. */
    @POST("api/fs/remove")
    suspend fun fsRemove(@Body req: RemoveReq): ApiResponse<JsonElement?>

    /** `data` carries `{message, tasks?}` on success; v0.2 only cares that the
     * request was accepted (P7), so the payload shape isn't modeled further. */
    @POST("api/fs/move")
    suspend fun fsMove(@Body req: MoveCopyReq): ApiResponse<JsonElement?>

    /** `data` carries `{message, tasks?}` on success; see [fsMove]. */
    @POST("api/fs/copy")
    suspend fun fsCopy(@Body req: MoveCopyReq): ApiResponse<JsonElement?>

    /** `AuthNotGuest` — 403 for guest users (v0.3_EXECUTION_PLAN.md §6.1). */
    @GET("api/share/list")
    suspend fun shareList(@Query("page") page: Int, @Query("per_page") perPage: Int): ApiResponse<SharePageResp>

    @GET("api/share/get")
    suspend fun shareGet(@Query("id") id: String): ApiResponse<SharingResp>

    @POST("api/share/create")
    suspend fun shareCreate(@Body req: UpdateSharingReq): ApiResponse<SharingResp>

    @POST("api/share/update")
    suspend fun shareUpdate(@Body req: UpdateSharingReq): ApiResponse<SharingResp>

    @POST("api/share/enable")
    suspend fun shareEnable(@Query("id") id: String): ApiResponse<JsonElement?>

    @POST("api/share/disable")
    suspend fun shareDisable(@Query("id") id: String): ApiResponse<JsonElement?>

    /** Delete is single-id only — the backend does not support batch delete. */
    @POST("api/share/delete")
    suspend fun shareDelete(@Query("id") id: String): ApiResponse<JsonElement?>

    /** Requires the backend's `SearchIndex` middleware/index to be built (V-04). */
    @POST("api/fs/search")
    suspend fun fsSearch(@Body req: SearchReq): ApiResponse<SearchPageResp>

    /** Permission-gated by `CanAddOfflineDownloadTasks`, not by `AuthNotGuest`. */
    @POST("api/fs/add_offline_download")
    suspend fun addOfflineDownload(@Body req: AddOfflineDownloadReq): ApiResponse<AddOfflineDownloadResp>

    /** No auth required (server: `api/public` group). */
    @GET("api/public/offline_download_tools")
    suspend fun offlineDownloadTools(): ApiResponse<List<String>>

    /** [type] is one of the 7 backend task-type path segments (v0.3 polls the
     * 4 named in P7: offline_download / offline_download_transfer / copy / move). */
    @GET("api/task/{type}/undone")
    suspend fun taskUndone(@Path("type") type: String): ApiResponse<List<TaskInfoDto>>

    @GET("api/task/{type}/done")
    suspend fun taskDone(@Path("type") type: String): ApiResponse<List<TaskInfoDto>>

    @POST("api/task/{type}/cancel")
    suspend fun taskCancel(@Path("type") type: String, @Query("tid") tid: String): ApiResponse<JsonElement?>

    // ---- Admin (v0.5) ----
    // All admin/* paths sit behind the server's `AuthAdmin` middleware
    // (403 "You are not an admin" for non-admin callers, confirmed against
    // `openlist-ref/server/middlewares/auth.go`); AdminGateRepository (S2) is
    // the sole gate that must run before any of these are called from UI.

    /** [page]/[perPage] — `model.PageReq` (`page`/`per_page` query params). */
    @GET("api/admin/user/list")
    suspend fun adminUserList(@Query("page") page: Int, @Query("per_page") perPage: Int): ApiResponse<AdminUserPageDto>

    @GET("api/admin/user/get")
    suspend fun adminUserGet(@Query("id") id: Int): ApiResponse<AdminUserDto>

    @GET("api/admin/storage/list")
    suspend fun adminStorageList(@Query("page") page: Int, @Query("per_page") perPage: Int): ApiResponse<AdminStoragePageDto>

    @GET("api/admin/storage/get")
    suspend fun adminStorageGet(@Query("id") id: Int): ApiResponse<AdminStorageDto>

    /** `data` is empty/absent on success (`common.SuccessResp(c)`, no payload). */
    @POST("api/admin/storage/enable")
    suspend fun adminStorageEnable(@Query("id") id: Int): ApiResponse<JsonElement?>

    @POST("api/admin/storage/disable")
    suspend fun adminStorageDisable(@Query("id") id: Int): ApiResponse<JsonElement?>

    /** Asynchronous: the server responds 200 immediately and continues
     * reloading storages on a background goroutine (`LoadAllStorages`,
     * `openlist-ref/server/handles/storage.go`) — callers must present this as
     * "reload submitted", not "reload completed" (PRD §13.2). */
    @POST("api/admin/storage/load_all")
    suspend fun adminStorageLoadAll(): ApiResponse<JsonElement?>

    /** `op.GetDriverInfoMap()` — driver name -> `driver.Info` metadata tree. */
    @GET("api/admin/driver/list")
    suspend fun adminDriverList(): ApiResponse<Map<String, JsonElement>>

    @GET("api/admin/driver/names")
    suspend fun adminDriverNames(): ApiResponse<List<String>>

    @GET("api/admin/driver/info")
    suspend fun adminDriverInfo(@Query("driver") driver: String): ApiResponse<AdminDriverInfoMap>

    // Admin task endpoints reuse the same 7 backend path segments as the
    // non-admin api/task/{type}/* group (`SetupTaskRoute` mounts identically
    // at both `auth.Group("/task", AuthNotGuest)` and `admin.Group("/task")`,
    // `openlist-ref/server/router.go`); the admin mount additionally returns
    // every user's tasks, not just the caller's own. `info`/`cancel`/`retry`/
    // `delete` are POST with a `tid` query param (confirmed against
    // `openlist-ref/server/handles/task.go` `taskRoute`), not the PRD table's
    // assumed GET for `info`.

    @GET("api/admin/task/{type}/undone")
    suspend fun adminTaskUndone(@Path("type") type: String): ApiResponse<List<TaskInfoDto>>

    @GET("api/admin/task/{type}/done")
    suspend fun adminTaskDone(@Path("type") type: String): ApiResponse<List<TaskInfoDto>>

    @POST("api/admin/task/{type}/info")
    suspend fun adminTaskInfo(@Path("type") type: String, @Query("tid") tid: String): ApiResponse<TaskInfoDto>

    @POST("api/admin/task/{type}/cancel")
    suspend fun adminTaskCancel(@Path("type") type: String, @Query("tid") tid: String): ApiResponse<JsonElement?>

    @POST("api/admin/task/{type}/retry")
    suspend fun adminTaskRetry(@Path("type") type: String, @Query("tid") tid: String): ApiResponse<JsonElement?>

    @POST("api/admin/task/{type}/delete")
    suspend fun adminTaskDelete(@Path("type") type: String, @Query("tid") tid: String): ApiResponse<JsonElement?>

    /** Full-rebuild; async like [adminStorageLoadAll] (server spawns a goroutine
     * and responds immediately). */
    @POST("api/admin/index/build")
    suspend fun adminIndexBuild(): ApiResponse<JsonElement?>

    @POST("api/admin/index/update")
    suspend fun adminIndexUpdate(@Body req: AdminIndexUpdateReq): ApiResponse<JsonElement?>

    @POST("api/admin/index/stop")
    suspend fun adminIndexStop(): ApiResponse<JsonElement?>

    @POST("api/admin/index/clear")
    suspend fun adminIndexClear(): ApiResponse<JsonElement?>

    @GET("api/admin/index/progress")
    suspend fun adminIndexProgress(): ApiResponse<AdminIndexProgressDto>

    /** [group]/[groups] are mutually exclusive per `ListSettings`
     * (`openlist-ref/server/handles/setting.go`): [groups] is a comma-joined
     * list of int group ids, [group] a single one; both null means "all". */
    @GET("api/admin/setting/list")
    suspend fun adminSettingList(
        @Query("group") group: Int? = null,
        @Query("groups") groups: String? = null,
    ): ApiResponse<List<AdminSettingDto>>

    /**
     * [key]/[keys] are mutually exclusive per `GetSetting`
     * (`openlist-ref/server/handles/setting.go`) — **but the response shape
     * differs**: with [key] the server returns one `SettingItem` object; with
     * [keys] (comma-joined) it returns a JSON array. This single-object
     * signature only covers the [key] case (the one S7's `getSettings`/
     * `getDefaultSettings` actually need per PRD §10.7); a [keys]-array
     * variant can be added alongside the real S7 implementation if a caller
     * needs it.
     */
    @GET("api/admin/setting/get")
    suspend fun adminSettingGet(@Query("key") key: String): ApiResponse<AdminSettingDto>

    /**
     * Confirmed **POST**, not GET, against `openlist-ref/server/router.go`
     * (`setting.POST("/default", handles.DefaultSettings)`) — the S0 brief's
     * "GET, not POST per PRD" note had the correction backwards; the actual
     * source shows POST, matching the PRD table after all. Recorded here as a
     * deviation from this task's own brief, not from the PRD.
     */
    @POST("api/admin/setting/default")
    suspend fun adminSettingDefault(
        @Query("group") group: Int? = null,
        @Query("groups") groups: String? = null,
    ): ApiResponse<List<AdminSettingDto>>
}
