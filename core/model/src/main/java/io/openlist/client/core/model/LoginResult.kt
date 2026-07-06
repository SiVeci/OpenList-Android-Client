package io.openlist.client.core.model

/**
 * Outcome of a password/LDAP login attempt (v1.0_PRD §9.2 Auth.3). Failure is
 * deliberately not a case here — it stays expressed as the existing
 * `ApiResult.Failure(DomainError)` at the Repository boundary so there is only
 * one error taxonomy in the app (v1.0_EXECUTION_PLAN.md §8: "Failure 包既有
 * DomainError 不另造错误体系").
 */
sealed class LoginResult {
    data class Success(val session: Session) : LoginResult()
    data class NeedOtp(val challenge: OtpChallenge) : LoginResult()
}

/**
 * In-memory-only state for the login page's OTP second step (v1.0_PRD §9.2
 * Auth.2 / §11.2). Never persisted (no Room entity, no SavedStateHandle
 * persistence), never logged — holds just enough to resubmit with an OTP code.
 * [method] is always [AuthType.PASSWORD] in practice: LDAP login has no OTP
 * branch on the server (v1.0_EXECUTION_PLAN.md V-601, source-confirmed absence
 * in `ldap_login.go`), but the field stays generic rather than hard-coding
 * that assumption into the type.
 */
data class OtpChallenge(
    val instanceId: String,
    val method: AuthType,
    val username: String,
)
