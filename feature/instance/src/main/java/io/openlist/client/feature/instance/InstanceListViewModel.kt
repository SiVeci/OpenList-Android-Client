package io.openlist.client.feature.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstanceListViewModel @Inject constructor(
    private val instanceRepository: InstanceRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val instances: StateFlow<List<Instance>> = instanceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessionsByInstanceId: StateFlow<Map<String, Session>> = authRepository.observeAllSessions()
        .map { sessions -> sessions.associateBy { it.instanceId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _connectionStatus = MutableStateFlow<Map<String, ConnectionCheck>>(emptyMap())
    val connectionStatus: StateFlow<Map<String, ConnectionCheck>> = _connectionStatus.asStateFlow()

    /** Marks [instance] current (v0.1_PRD §6.1 "点击进入该实例"); Login screen
     * decides whether that's enough to skip straight to the file page. */
    fun selectInstance(instance: Instance) {
        viewModelScope.launch { instanceRepository.setCurrent(instance.id) }
    }

    fun deleteInstance(instance: Instance) {
        viewModelScope.launch {
            instanceRepository.delete(instance.id)
            _connectionStatus.update { it - instance.id }
        }
    }

    fun testConnection(instance: Instance) {
        viewModelScope.launch {
            _connectionStatus.update { it + (instance.id to ConnectionCheck.Testing) }
            val result = instanceRepository.testConnection(instance.baseUrl)
            val status = when (result) {
                is ApiResult.Success -> ConnectionCheck.Reachable
                is ApiResult.Failure -> ConnectionCheck.Unreachable(result.error.toUserMessage())
            }
            _connectionStatus.update { it + (instance.id to status) }
        }
    }
}
