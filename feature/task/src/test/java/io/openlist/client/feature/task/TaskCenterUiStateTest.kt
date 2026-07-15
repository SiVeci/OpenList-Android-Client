package io.openlist.client.feature.task

import io.openlist.client.core.model.TaskSource
import io.openlist.client.core.model.TaskType
import io.openlist.client.core.model.UnifiedTask
import io.openlist.client.core.model.UnifiedTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCenterUiStateTest {

    @Test
    fun `summary counts active failed and completed tasks across all tabs`() {
        val state = TaskCenterUiState(
            tasks = listOf(
                task("running", UnifiedTaskStatus.RUNNING),
                task("pending", UnifiedTaskStatus.PENDING),
                task("failed", UnifiedTaskStatus.FAILED),
                task("success", UnifiedTaskStatus.SUCCESS),
                task("cancelled", UnifiedTaskStatus.CANCELLED),
            ),
        )

        assertEquals(TaskSummary(activeCount = 2, failedCount = 1, completedCount = 1), state.summary)
    }

    @Test
    fun `failed tab shows failed tasks from every source`() {
        val failedUpload = task("upload-failed", UnifiedTaskStatus.FAILED, TaskSource.LOCAL_UPLOAD)
        val failedRemote = task("remote-failed", UnifiedTaskStatus.FAILED, TaskSource.REMOTE)
        val state = TaskCenterUiState(
            selectedTab = TaskTab.FAILED,
            tasks = listOf(
                task("upload-running", UnifiedTaskStatus.RUNNING, TaskSource.LOCAL_UPLOAD),
                failedUpload,
                task("download-success", UnifiedTaskStatus.SUCCESS, TaskSource.LOCAL_DOWNLOAD),
                failedRemote,
            ),
        )

        assertEquals(listOf(failedUpload, failedRemote), state.filteredTasks)
    }

    @Test
    fun `task groups are ordered running failed completed and omit empty groups`() {
        val running = task("running", UnifiedTaskStatus.RUNNING)
        val pending = task("pending", UnifiedTaskStatus.PENDING)
        val failed = task("failed", UnifiedTaskStatus.FAILED)
        val completed = task("completed", UnifiedTaskStatus.SUCCESS)

        val groups = groupTasks(listOf(completed, failed, pending, running))

        assertEquals(listOf(TaskGroupType.RUNNING, TaskGroupType.FAILED, TaskGroupType.COMPLETED), groups.map { it.type })
        assertEquals(listOf(pending, running), groups[0].tasks)
        assertEquals(listOf(failed), groups[1].tasks)
        assertEquals(listOf(completed), groups[2].tasks)
    }

    @Test
    fun `draft retention label rounds up and never reports a negative duration`() {
        assertEquals("草稿保留约 1分钟", formatDraftRetention(1L))
        assertEquals("草稿保留约 1小时1分钟", formatDraftRetention(3_600_001L))
        assertEquals("草稿即将清理", formatDraftRetention(0L))
    }

    private fun task(
        id: String,
        status: UnifiedTaskStatus,
        source: TaskSource = TaskSource.LOCAL_UPLOAD,
    ) = UnifiedTask(
        id = id,
        instanceId = "instance-1",
        source = source,
        type = when (source) {
            TaskSource.LOCAL_UPLOAD -> TaskType.UPLOAD
            TaskSource.LOCAL_DOWNLOAD -> TaskType.DOWNLOAD
            TaskSource.REMOTE -> TaskType.COPY
            TaskSource.SYSTEM_DOCUMENT -> TaskType.SYSTEM_SAVE
        },
        title = id,
        status = status,
        progress = null,
        path = "/",
        localUri = null,
        errorMessage = null,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
