package io.openlist.client.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.database.AppPreferences
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminEntryVisibility
import io.openlist.client.core.model.Instance
import io.openlist.client.core.model.Session
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
import java.io.File
import java.util.Locale
import kotlin.time.TimeSource
import javax.inject.Inject

sealed interface SettingsConnectionCheck {
    data object Reachable : SettingsConnectionCheck
    data class Unreachable(val message: String) : SettingsConnectionCheck
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val filesRepository: FilesRepository,
    private val instanceRepository: InstanceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val loggingEnabled: StateFlow<Boolean> = appPreferences.loggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Backs the settings page's secondary "任务中心" entry point
     * (v0.3_EXECUTION_PLAN.md §13) — null hides it, since this screen has no
     * per-instance nav arg of its own to fall back on. */
    val currentInstanceId: StateFlow<String?> = instanceRepository.observeAll()
        .map { instances -> instances.firstOrNull { it.isCurrent }?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val currentInstance: StateFlow<Instance?> = instanceRepository.observeAll()
        .map { instances -> instances.firstOrNull { it.isCurrent } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current instance's display name for the "管理台" row subtitle (PRD
     * §12.1 "入口必须显示当前实例名称，避免跨实例误操作") — null when there is no
     * current instance, same condition under which [adminEntryState] is
     * [AdminEntryState.HIDDEN]. */
    val currentInstanceName: StateFlow<String?> = currentInstance
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentInstanceBaseUrl: StateFlow<String?> = currentInstance
        .map { it?.baseUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentInstanceLastUsedAt: StateFlow<Long?> = currentInstance
        .map { it?.lastUsedAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentSession: StateFlow<Session?> = currentInstance
        .flatMapLatest { instance ->
            val id = instance?.id
            if (id == null) flowOf(null) else authRepository.observeSession(id)
        }
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

    private val _cacheSizeLabel = MutableStateFlow(formatBytes(cacheDirSize()))
    val cacheSizeLabel: StateFlow<String> = _cacheSizeLabel.asStateFlow()

    private val _connectionCheck = MutableStateFlow<SettingsConnectionCheck?>(null)
    val connectionCheck: StateFlow<SettingsConnectionCheck?> = _connectionCheck.asStateFlow()

    private val _connectionElapsedMillis = MutableStateFlow<Long?>(null)
    val connectionElapsedMillis: StateFlow<Long?> = _connectionElapsedMillis.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _loggedOutInstanceId = MutableStateFlow<String?>(null)
    val loggedOutInstanceId: StateFlow<String?> = _loggedOutInstanceId.asStateFlow()

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLoggingEnabled(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
            filesRepository.clearAllCache()
            refreshCacheSize()
            _cacheCleared.value = true
        }
    }

    fun acknowledgeCacheCleared() {
        _cacheCleared.value = false
    }

    fun testCurrentConnection() {
        val instance = currentInstance.value ?: return
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionCheck.value = null
            _connectionElapsedMillis.value = null
            val startedAt = TimeSource.Monotonic.markNow()
            val result = instanceRepository.testConnection(instance.baseUrl)
            _connectionElapsedMillis.value = startedAt.elapsedNow().inWholeMilliseconds
            _connectionCheck.value = when (result) {
                is ApiResult.Success -> SettingsConnectionCheck.Reachable
                is ApiResult.Failure -> SettingsConnectionCheck.Unreachable(result.error.toUserMessage())
            }
            _isTestingConnection.value = false
        }
    }

    fun logoutCurrentSession() {
        val instanceId = currentInstanceId.value ?: return
        viewModelScope.launch {
            authRepository.logout(instanceId)
            _loggedOutInstanceId.value = instanceId
        }
    }

    fun acknowledgeLoggedOut() {
        _loggedOutInstanceId.value = null
    }

    private fun refreshCacheSize() {
        _cacheSizeLabel.value = formatBytes(cacheDirSize())
    }

    private fun cacheDirSize(): Long = context.cacheDir.walkTopDown()
        .filter(File::isFile)
        .sumOf { it.length() }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes / 1024.0
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}
