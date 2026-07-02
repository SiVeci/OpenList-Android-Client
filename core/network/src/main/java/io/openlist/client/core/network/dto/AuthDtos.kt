package io.openlist.client.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/auth/login — password sent plaintext (server applies StaticHash).
 * v0.1 uses this over HTTPS. `otp_code` only needed when 2FA is enabled (HTTP 402).
 * Source: server/handles/auth.go LoginReq.
 */
@Serializable
data class LoginReq(
    val username: String,
    val password: String,
    @SerialName("otp_code") val otpCode: String = "",
)

/** POST /api/auth/login/hash — password pre-hashed with StaticHash by client. Reserved for v0.1. */
@Serializable
data class LoginHashReq(
    val username: String,
    val password: String,
    @SerialName("otp_code") val otpCode: String = "",
)

/** data payload of a successful login. */
@Serializable
data class LoginResp(
    val token: String,
)

/**
 * GET /api/me — returns the current user, or the guest user when no/invalid token
 * (the endpoint never 401s for a missing token; guest is resolved by middleware).
 * Source: server/handles/auth.go UserResp = model.User + otp.
 * role: OpenList user role int (guest/general/admin); base_path: user's root;
 * permission: bitmask; disabled: whether the (guest) account is disabled.
 */
@Serializable
data class UserResp(
    val id: Int = 0,
    val username: String = "",
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = 0,
    val disabled: Boolean = false,
    val permission: Int = 0,
    @SerialName("sso_id") val ssoId: String = "",
    val otp: Boolean = false,
)
