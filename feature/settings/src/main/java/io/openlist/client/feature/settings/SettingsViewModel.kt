package io.openlist.client.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.database.AppPreferences
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminEntryVisibility
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.resolveAdminEntryVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val filesRepository: FilesRepository,
    instanceRepository: InstanceRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val loggingEnabled: StateFlow<Boolean> = appPreferences.loggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Backs the settings page's secondary "任务中心" entry point
     * (v0.3_EXECUTION_PLAN.md §13) — null hides it, since this screen has no
     * per-instance nav arg of its own to fall back on. */
    val currentInstanceId: StateFlow<String?> = instanceRepository.observeAll()
        .map { instances -> instances.firstOrNull { it.isCurrent }?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val currentInstance: Flow<Instance?> = instanceRepository.observeAll()
        .map { instances -> instances.firstOrNull { it.isCurrent } }

    /** Current instance's display name for the "管理台" row subtitle (PRD
     * §12.1 "入口必须显示当前实例名称，避免跨实例误操作") — null when there is no
     * current instance, same condition under which [adminEntryState] is
     * [AdminEntryState.HIDDEN]. */
    val currentInstanceName: StateFlow<String?> = currentInstance
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Observes the current instance's [io.openlist.client.core.model.Session]
     * directly (not `AdminGateRepository`, per architecture: entry-row display
     * is a pre-check only) to derive which PRD §8 visual state the "管理台"
     * row should show. Switches session Flow whenever the current instance id
     * changes (`flatMapLatest`) so switching the active instance elsewhere in
     * the app updates this row without recomposing the whole screen.
     */
    val adminEntryState: StateFlow<AdminEntryVisibility> = currentInstance
        .flatMapLatest { instance ->
            val id = instance?.id
            if (id == null) flowOf(AdminEntryVisibility.HIDDEN)
            else authRepository.observeSession(id).map { session ->
                resolveAdminEntryVisibility(hasCurrentInstance = true, session = session)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminEntryVisibility.HIDDEN)

    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLoggingEnabled(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
            filesRepository.clearAllCache()
            _cacheCleared.value = true
        }
    }

    fun acknowledgeCacheCleared() {
        _cacheCleared.value = false
    }
}
