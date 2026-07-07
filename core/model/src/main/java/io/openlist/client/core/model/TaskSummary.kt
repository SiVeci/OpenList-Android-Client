package io.openlist.client.core.model

data class TaskSummary(
    val runningCount: Int,
    val pendingCount: Int,
    val failedCount: Int,
    val completedCount: Int,
    val unknownCount: Int,
) {
    val activeCount: Int get() = runningCount + pendingCount
    val totalCount: Int get() = runningCount + pendingCount + failedCount + completedCount + unknownCount
    val hasFailures: Boolean get() = failedCount > 0
}

fun summarizeTasks(tasks: List<UnifiedTask>): TaskSummary {
    var running = 0
    var pending = 0
    var failed = 0
    var completed = 0
    var unknown = 0

    tasks.forEach { task ->
        when (task.status) {
            UnifiedTaskStatus.RUNNING -> running += 1
            UnifiedTaskStatus.PENDING -> pending += 1
            UnifiedTaskStatus.FAILED -> failed += 1
            UnifiedTaskStatus.SUCCESS,
            UnifiedTaskStatus.CANCELLED -> completed += 1
            UnifiedTaskStatus.UNKNOWN -> unknown += 1
        }
    }

    return TaskSummary(
        runningCount = running,
        pendingCount = pending,
        failedCount = failed,
        completedCount = completed,
        unknownCount = unknown,
    )
}
