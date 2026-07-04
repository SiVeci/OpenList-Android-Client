package io.openlist.client.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.database.AppPreferences
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val filesRepository: FilesRepository,
    instanceRepository: InstanceRepository,
) : ViewModel() {

    val loggingEnabled: StateFlow<Boolean> = appPreferences.loggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Backs the settings page's secondary "任务中心" entry point
     * (v0.3_EXECUTION_PLAN.md §13) — null hides it, since this screen has no
     * per-instance nav arg of its own to fall back on. */
    val currentInstanceId: StateFlow<String?> = instanceRepository.observeAll()
        .map { instances -> instances.firstOrNull { it.isCurrent }?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
