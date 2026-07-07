package io.openlist.client.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.model.summarizeTasks
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class MainNavUiState(
    val currentInstanceId: String? = null,
    val hasInstances: Boolean = false,
    val activeTaskCount: Int = 0,
)

@HiltViewModel
class MainNavViewModel @Inject constructor(
    instanceRepository: InstanceRepository,
    taskAggregationRepository: TaskAggregationRepository,
) : ViewModel() {

    private val instances = instanceRepository.observeAll()

    private val currentInstanceId = instances
        .map { list -> list.firstOrNull { it.isCurrent }?.id ?: list.firstOrNull()?.id }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeTaskCount = currentInstanceId
        .flatMapLatest { instanceId ->
            if (instanceId == null) {
                flowOf(0)
            } else {
                taskAggregationRepository.observeAllTasks(instanceId)
                    .map { tasks -> summarizeTasks(tasks).activeCount }
                    .onStart { emit(0) }
            }
        }

    val uiState: StateFlow<MainNavUiState> = combine(
        instances,
        currentInstanceId,
        activeTaskCount,
    ) { list, instanceId, activeCount ->
        MainNavUiState(
            currentInstanceId = instanceId,
            hasInstances = list.isNotEmpty(),
            activeTaskCount = activeCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainNavUiState(),
    )
}
