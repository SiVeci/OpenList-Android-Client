package io.openlist.client.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.LoginResult
import io.openlist.client.core.model.OtpChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The four login entries the page distinguishes (v1.0_PRD §4.2.B.3). Guest
 * stays a standalone action button (as in v0.1~v0.5) rather than a tab, since
 * it takes no credentials. */
enum class LoginMethod { PASSWORD, LDAP, TOKEN }

data class LoginUiState(
    val instanceName: String = "",
    val instanceBaseUrl: String = "",
    val checkingSession: Boolean = true,
    val method: LoginMethod = LoginMethod.PASSWORD,
    val username: String = "",
    val password: String = "",
    val tokenInput: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val autoProceedInstanceId: String? = null,
    /** Non-null while the inline OTP second step is showing (DEC-601). */
    val otpChallenge: OtpChallenge? = null,
    val otpCode: String = "",
) {
    val needsOtp: Boolean get() = otpChallenge != null
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val instanceRepository: InstanceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val instance = instanceRepository.getById(instanceId)
            _uiState.update { it.copy(instanceName = instance?.name.orEmpty(), instanceBaseUrl = instance?.baseUrl.orEmpty()) }

            if (authRepository.getSession(instanceId) == null) {
                _uiState.update { it.copy(checkingSession = false) }
                return@launch
            }
            // A saved session may be stale (e.g. server restarted, clearing its
            // in-memory token cache), so re-check it against /api/me before
            // trusting it enough to skip the login form.
            when (authRepository.refreshCurrentUser(instanceId)) {
                is ApiResult.Success -> _uiState.update { it.copy(checkingSession = false, autoProceedInstanceId = instanceId) }
                is ApiResult.Failure -> _uiState.update { it.copy(checkingSession = false) }
            }
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onTokenInputChange(value: String) = _uiState.update { it.copy(tokenInput = value, errorMessage = null) }
    fun onOtpCodeChange(value: String) = _uiState.update { it.copy(otpCode = value, errorMessage = null) }

    fun selectMethod(method: LoginMethod) = _uiState.update {
        it.copy(method = method, errorMessage = null, otpChallenge = null, otpCode = "")
    }

    /** Cancels the inline OTP step and returns to the credentials form
     * (e.g. the user realizes they picked the wrong instance/account). */
    fun cancelOtp() = _uiState.update { it.copy(otpChallenge = null, otpCode = "", errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (state.otpChallenge != null) {
            submitOtp()
            return
        }
        when (state.method) {
            LoginMethod.PASSWORD -> loginWithPassword()
            LoginMethod.LDAP -> loginWithLdap()
            LoginMethod.TOKEN -> loginWithToken()
        }
    }

    private fun loginWithPassword() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            handleLoginResult(authRepository.loginWithPassword(instanceId, state.username, state.password))
        }
    }

    private fun loginWithLdap() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            handleLoginResult(authRepository.loginWithLdap(instanceId, state.username, state.password))
        }
    }

    /** Resubmits with the original password plus the OTP code — the server has
     * no separate "verify code only" endpoint, `/api/auth/login` re-checks the
     * full credentials every time (v1.0_EXECUTION_PLAN.md V-602). */
    private fun submitOtp() {
        val state = _uiState.value
        val challenge = state.otpChallenge ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            handleLoginResult(
                authRepository.loginWithPassword(instanceId, challenge.username, state.password, otpCode = state.otpCode),
            )
        }
    }

    fun loginAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = authRepository.loginAsGuest(instanceId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSubmitting = false, autoProceedInstanceId = instanceId) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    private fun loginWithToken() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = authRepository.loginWithToken(instanceId, state.tokenInput)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSubmitting = false, autoProceedInstanceId = instanceId) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    private fun handleLoginResult(result: ApiResult<LoginResult>) {
        when (result) {
            is ApiResult.Success -> when (val loginResult = result.data) {
                is LoginResult.Success -> _uiState.update { it.copy(isSubmitting = false, autoProceedInstanceId = instanceId) }
                is LoginResult.NeedOtp -> _uiState.update {
                    it.copy(isSubmitting = false, otpChallenge = loginResult.challenge, otpCode = "")
                }
            }
            is ApiResult.Failure -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage()) }
        }
    }
}
