package io.openlist.client.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.database.AppPreferences
import io.openlist.client.core.domain.FilesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val filesRepository: FilesRepository,
) : ViewModel() {

    val loggingEnabled: StateFlow<Boolean> = appPreferences.loggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
