package io.openlist.client.core.model

/**
 * Maps the backend's raw numeric `tache.State` (server/handles/task.go
 * TaskInfo.State) to [UnifiedTaskStatus]. The backend groups Pending/
 * Running/Canceling/Errored/Failing/WaitingRetry/BeforeRetry as "undone"
 * and Canceled/Failed/Succeeded as "done" (confirmed from
 * server/handles/task.go); the exact integer values of each are a
 * best-effort guess pending real-device verification
 * (v0.3_EXECUTION_PLAN.md V-02) — an unrecognized value degrades to
 * [UnifiedTaskStatus.UNKNOWN] rather than crashing.
 */
object TaskStateMapper {
    fun map(stateRaw: Int): UnifiedTaskStatus = when (stateRaw) {
        0 -> UnifiedTaskStatus.PENDING // Pending
        1 -> UnifiedTaskStatus.RUNNING // Running
        2 -> UnifiedTaskStatus.SUCCESS // Succeeded
        3 -> UnifiedTaskStatus.RUNNING // Canceling — still undone, not yet CANCELLED
        4 -> UnifiedTaskStatus.CANCELLED // Canceled
        5 -> UnifiedTaskStatus.FAILED // Errored
        6 -> UnifiedTaskStatus.FAILED // Failing
        7 -> UnifiedTaskStatus.FAILED // Failed
        8, 9 -> UnifiedTaskStatus.PENDING // WaitingRetry / BeforeRetry
        else -> UnifiedTaskStatus.UNKNOWN
    }
}
