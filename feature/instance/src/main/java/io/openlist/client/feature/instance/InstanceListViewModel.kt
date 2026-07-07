package io.openlist.client.feature.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.RecentPathRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.model.AdminEntryVisibility
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.RecentPath
import io.openlist.client.core.model.Session
import io.openlist.client.core.model.TaskSummary
import io.openlist.client.core.model.resolveAdminEntryVisibility
import io.openlist.client.core.model.summarizeTasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val instances: List<Instance> = emptyList(),
    val currentInstance: Instance? = null,
    val sessionsByInstanceId: Map<String, Session> = emptyMap(),
    val currentSession: Session? = null,
    val adminEntryVisibility: AdminEntryVisibility = AdminEntryVisibility.HIDDEN,
    val recentPaths: List<RecentPath> = emptyList(),
    val taskSummary: TaskSummary = TaskSummary(
        runningCount = 0,
        pendingCount = 0,
        failedCount = 0,
        completedCount = 0,
        unknownCount = 0,
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InstanceListViewModel @Inject constructor(
    private val instanceRepository: InstanceRepository,
    authRepository: AuthRepository,
    private val taskAggregationRepository: TaskAggregationRepository,
    recentPathRepository: RecentPathRepository,
) : ViewModel() {

    val instances: StateFlow<List<Instance>> = instanceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessionsByInstanceId: StateFlow<Map<String, Session>> = authRepository.observeAllSessions()
        .map { sessions -> sessions.associateBy { it.instanceId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _connectionStatus = MutableStateFlow<Map<String, ConnectionCheck>>(emptyMap())
    val connectionStatus: StateFlow<Map<String, ConnectionCheck>> = _connectionStatus.asStateFlow()

    private val currentInstance: StateFlow<Instance?> = instances
        .map { list -> list.firstOrNull { it.isCurrent } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val taskSummary: StateFlow<TaskSummary> = currentInstance
        .flatMapLatest { instance ->
            if (instance == null) flowOf(emptyList())
            else taskAggregationRepository.observeAllTasks(instance.id)
        }
        .map(::summarizeTasks)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            summarizeTasks(emptyList()),
        )

    private val recentPaths: StateFlow<List<RecentPath>> = recentPathRepository.observeAll()
        .map { recents -> recents.take(HOME_RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeUiState: StateFlow<HomeUiState> = combine(
        instances,
        currentInstance,
        sessionsByInstanceId,
        taskSummary,
        recentPaths,
    ) { instances, currentInstance, sessionsByInstanceId, taskSummary, recentPaths ->
        val currentSession = currentInstance?.let { sessionsByInstanceId[it.id] }
        HomeUiState(
            instances = instances,
            currentInstance = currentInstance,
            sessionsByInstanceId = sessionsByInstanceId,
            currentSession = currentSession,
            adminEntryVisibility = resolveAdminEntryVisibility(
                hasCurrentInstance = currentInstance != null,
                session = currentSession,
            ),
            recentPaths = recentPaths,
            taskSummary = taskSummary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val refreshedTaskInstances = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            currentInstance
                .map { it?.id }
                .distinctUntilChanged()
                .collect { instanceId ->
                    if (instanceId != null && refreshedTaskInstances.add(instanceId)) {
                        taskAggregationRepository.refreshDownloadStatuses(instanceId)
                        taskAggregationRepository.refreshRemoteTasks(instanceId)
                    }
                }
        }
    }

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

    private companion object {
        const val HOME_RECENT_LIMIT = 3
    }
}
