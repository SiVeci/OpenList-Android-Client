package io.openlist.client.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.toUserMessage
import io.openlist.client.core.domain.AdminGateRepository
import io.openlist.client.core.domain.AdminIndexRepository
import io.openlist.client.core.domain.AdminStorageRepository
import io.openlist.client.core.domain.AdminTaskRepository
import io.openlist.client.core.domain.AdminWebFallbackRepository
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.model.AdminAccessState
import io.openlist.client.core.model.UnifiedTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The 7 admin console sections (PRD §12/§9.2). Only [OVERVIEW] has real
 * content in S2 — the rest render a "即将上线" placeholder until S3-S7. */
enum class AdminTab(val label: String) {
    OVERVIEW("概览"),
    USERS("用户"),
    STORAGES("存储"),
    TASKS("任务"),
    INDEX("索引"),
    SETTINGS("设置"),
    ADVANCED("高级"),
}

/** One independently-loadable overview summary card (PRD §16.3.4/P-512: a
 * failure in one card must never block the others or the instance-info block
 * from rendering). [Loading]/[Loaded]/[Failed] mirror the shape used
 * elsewhere in the app for per-section async state (e.g. Preview's
 * `PreviewBodyState`), kept local to this file since each card's payload
 * shape differs. */
sealed class AdminCardState<out T> {
    data object Loading : AdminCardState<Nothing>()
    data class Loaded<T>(val data: T) : AdminCardState<T>()
    data class Failed(val message: String) : AdminCardState<Nothing>()
}

data class AdminStorageSummaryCard(val enabledCount: Int, val disabledCount: Int)
data class AdminTaskSummaryCard(val runningCount: Int)
data class AdminIndexSummaryCard(val isRunning: Boolean, val objCount: Long)

data class AdminInstanceInfo(
    val instanceName: String,
    val baseUrl: String,
    val adminUsername: String?,
)

data class AdminUiState(
    val accessState: AdminAccessState = AdminAccessState.CHECKING,
    val selectedTab: AdminTab = AdminTab.OVERVIEW,
    val instanceInfo: AdminInstanceInfo? = null,
    val storageSummary: AdminCardState<AdminStorageSummaryCard> = AdminCardState.Loading,
    val taskSummary: AdminCardState<AdminTaskSummaryCard> = AdminCardState.Loading,
    val indexSummary: AdminCardState<AdminIndexSummaryCard> = AdminCardState.Loading,
)

/**
 * Host ViewModel for the `admin/{instanceId}?tab={tab}` route
 * (v0.5_EXECUTION_PLAN.md §11 S2-T3/T4). Reads [instanceId]/[initialTab] from
 * [SavedStateHandle] the same way [io.openlist.client.feature.preview.PreviewViewModel]
 * reads its nav args.
 *
 * Gate correctness (the S2 "earliest runnable node" exit criterion, §13):
 * [AdminGateRepository.checkAccess] is called once on init (hard requirement,
 * PRD "进入 ADMIN 路由即 checkAccess"), and [AdminGateRepository.observeAccess]
 * is additionally collected for the whole ViewModel lifetime so a 401 that
 * invalidates the session while the console is already open (e.g. from some
 * other Admin* repository call in a later Sprint) demotes [AdminUiState
 * .accessState] away from ALLOWED reactively. The overview summary loads
 * (`refreshOverviewCards`) are only ever triggered from the ALLOWED branch in
 * [AdminHostScreen] — this ViewModel itself does not gate the *call sites*
 * of those loads on accessState because Compose's `when` on accessState in
 * the screen is what actually decides whether the Tab content composables
 * (and therefore these load functions) are ever invoked at all. That "gate by
 * never mounting the composable" structure is what makes "DENIED_* states
 * make zero admin API calls" true by construction, not by an extra runtime
 * check here.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val adminGateRepository: AdminGateRepository,
    private val adminStorageRepository: AdminStorageRepository,
    private val adminTaskRepository: AdminTaskRepository,
    private val adminIndexRepository: AdminIndexRepository,
    private val adminWebFallbackRepository: AdminWebFallbackRepository,
    private val instanceRepository: InstanceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])
    private val initialTab: String? = savedStateHandle["tab"]

    private val _uiState = MutableStateFlow(
        AdminUiState(selectedTab = AdminTab.entries.firstOrNull { it.name.equals(initialTab, ignoreCase = true) } ?: AdminTab.OVERVIEW),
    )
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        // Hard requirement: checkAccess on entry (not just observeAccess),
        // since observeAccess's CHECKING->derived-state transition is purely
        // local/reactive and would never itself force a fresh /api/me call.
        viewModelScope.launch {
            when (val result = adminGateRepository.checkAccess(instanceId)) {
                is ApiResult.Success -> _uiState.update { it.copy(accessState = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(accessState = AdminAccessState.ERROR) }
            }
        }
        // Reacts to a later invalidation (e.g. a 401 from some other Admin*
        // call) while the console stays open. Since this starts from
        // CHECKING itself, the first emission is intentionally superseded by
        // the checkAccess call above in practice (whichever completes last
        // wins) -- both converge on the same true state once the session
        // Flow catches up, so no separate reconciliation logic is needed.
        adminGateRepository.observeAccess(instanceId)
            .onEach { state -> _uiState.update { it.copy(accessState = state) } }
            .launchIn(viewModelScope)

        loadInstanceInfo()
    }

    fun selectTab(tab: AdminTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** Zero-network instance-info block (PRD §12.2/§9.2 "本地即有，零请求"):
     * instance name/baseUrl come from [InstanceRepository], the admin
     * username from the [io.openlist.client.core.model.Session] already
     * fetched during gating -- neither requires a fresh call here. */
    private fun loadInstanceInfo() {
        viewModelScope.launch {
            val instance = instanceRepository.getById(instanceId)
            val session = authRepository.getSession(instanceId)
            if (instance != null) {
                _uiState.update {
                    it.copy(
                        instanceInfo = AdminInstanceInfo(
                            instanceName = instance.name,
                            baseUrl = instance.baseUrl,
                            adminUsername = session?.username,
                        ),
                    )
                }
            }
        }
    }

    /** Loads all three overview summary cards independently -- a failure in
     * one never blocks the others (each has its own `launch`/its own slice of
     * [AdminUiState]). Only ever called from the ALLOWED-gated Overview tab
     * composable (see [AdminHostScreen]/[AdminOverviewTab]), never from
     * [init], so DENIED_ and CHECKING states never trigger it. */
    fun loadOverviewCardsIfNeeded() {
        val state = _uiState.value
        if (state.storageSummary is AdminCardState.Loading || state.storageSummary is AdminCardState.Failed) {
            refreshStorageSummary()
        }
        if (state.taskSummary is AdminCardState.Loading || state.taskSummary is AdminCardState.Failed) {
            refreshTaskSummary()
        }
        if (state.indexSummary is AdminCardState.Loading || state.indexSummary is AdminCardState.Failed) {
            refreshIndexSummary()
        }
    }

    fun refreshStorageSummary() {
        _uiState.update { it.copy(storageSummary = AdminCardState.Loading) }
        viewModelScope.launch {
            when (val result = adminStorageRepository.getStorages(instanceId)) {
                is ApiResult.Success -> {
                    val enabled = result.data.storages.count { !it.disabled }
                    val disabled = result.data.storages.count { it.disabled }
                    _uiState.update { it.copy(storageSummary = AdminCardState.Loaded(AdminStorageSummaryCard(enabled, disabled))) }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(storageSummary = AdminCardState.Failed(result.error.toUserMessage()))
                }
            }
        }
    }

    fun refreshTaskSummary() {
        _uiState.update { it.copy(taskSummary = AdminCardState.Loading) }
        viewModelScope.launch {
            when (val result = adminTaskRepository.refreshUndone(instanceId)) {
                is ApiResult.Success -> {
                    // A single synchronous read of the just-refreshed in-memory
                    // flow is enough for a summary count -- observeAdminTasks
                    // only just finished being populated by the refreshUndone
                    // call above, so `.first()` reflects its result without
                    // needing to keep collecting for the rest of this
                    // ViewModel's lifetime (unlike the Tasks Tab itself, which
                    // does stay subscribed for live updates/polling).
                    val tasks = adminTaskRepository.observeAdminTasks(instanceId).first()
                    val runningCount = tasks.count {
                        !it.isDone && (it.state == UnifiedTaskStatus.RUNNING || it.state == UnifiedTaskStatus.PENDING)
                    }
                    _uiState.update { it.copy(taskSummary = AdminCardState.Loaded(AdminTaskSummaryCard(runningCount))) }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(taskSummary = AdminCardState.Failed(result.error.toUserMessage()))
                }
            }
        }
    }

    fun refreshIndexSummary() {
        _uiState.update { it.copy(indexSummary = AdminCardState.Loading) }
        viewModelScope.launch {
            when (val result = adminIndexRepository.getProgress(instanceId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(indexSummary = AdminCardState.Loaded(AdminIndexSummaryCard(result.data.isRunning, result.data.objCount)))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(indexSummary = AdminCardState.Failed(result.error.toUserMessage()))
                }
            }
        }
    }
}
