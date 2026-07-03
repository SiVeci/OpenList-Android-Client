package io.openlist.client.core.network

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
import io.openlist.client.core.network.dto.UserResp
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

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
}
