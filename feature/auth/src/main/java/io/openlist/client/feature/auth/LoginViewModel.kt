package io.openlist.client.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val instanceName: String = "",
    val instanceBaseUrl: String = "",
    val checkingSession: Boolean = true,
    val username: String = "",
    val password: String = "",
    val tokenInput: String = "",
    val isTokenMode: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val autoProceedInstanceId: String? = null,
)

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
    fun toggleTokenMode() = _uiState.update { it.copy(isTokenMode = !it.isTokenMode, errorMessage = null) }

    fun loginWithPassword() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            handleAuthResult(authRepository.loginWithPassword(instanceId, state.username, state.password))
        }
    }

    fun loginAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            handleAuthResult(authRepository.loginAsGuest(instanceId))
        }
    }

    fun loginWithToken() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            handleAuthResult(authRepository.loginWithToken(instanceId, state.tokenInput))
        }
    }

    private fun handleAuthResult(result: ApiResult<*>) {
        when (result) {
            is ApiResult.Success -> _uiState.update { it.copy(isSubmitting = false, autoProceedInstanceId = instanceId) }
            is ApiResult.Failure -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage()) }
        }
    }
}
