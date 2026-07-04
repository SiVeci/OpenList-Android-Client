package io.openlist.client.core.network

import io.openlist.client.core.network.dto.AddOfflineDownloadReq
import io.openlist.client.core.network.dto.AddOfflineDownloadResp
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
}
