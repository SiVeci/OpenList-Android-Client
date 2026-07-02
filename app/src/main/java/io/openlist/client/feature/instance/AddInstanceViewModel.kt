package io.openlist.client.feature.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.network.BaseUrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddInstanceUiState(
    val url: String = "",
    val name: String = "",
    val note: String = "",
    val isTesting: Boolean = false,
    val testResult: ConnectionCheck? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedInstanceId: String? = null,
)

@HiltViewModel
class AddInstanceViewModel @Inject constructor(
    private val repository: InstanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddInstanceUiState())
    val uiState: StateFlow<AddInstanceUiState> = _uiState.asStateFlow()

    fun onUrlChange(value: String) {
        _uiState.update { it.copy(url = value, testResult = null, saveError = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun testConnection() {
        val rawUrl = _uiState.value.url
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null, saveError = null) }
            val normalized = BaseUrlNormalizer.normalize(rawUrl)
            val status = when (normalized) {
                is ApiResult.Failure -> ConnectionCheck.Unreachable(normalized.error.toUserMessage())
                is ApiResult.Success -> when (val result = repository.testConnection(normalized.data.baseUrl)) {
                    is ApiResult.Success -> ConnectionCheck.Reachable
                    is ApiResult.Failure -> ConnectionCheck.Unreachable(result.error.toUserMessage())
                }
            }
            _uiState.update { it.copy(isTesting = false, testResult = status) }
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            when (val result = repository.addInstance(state.url, state.name.ifBlank { null }, state.note.ifBlank { null })) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSaving = false, savedInstanceId = result.data.id)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.error.toUserMessage())
                }
            }
        }
    }
}
