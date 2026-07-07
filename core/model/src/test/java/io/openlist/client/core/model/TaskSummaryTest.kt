package io.openlist.client.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSummaryTest {

    @Test
    fun emptyTasks_returnsZeroSummary() {
        val summary = summarizeTasks(emptyList())

        assertEquals(0, summary.runningCount)
        assertEquals(0, summary.pendingCount)
        assertEquals(0, summary.failedCount)
        assertEquals(0, summary.completedCount)
        assertEquals(0, summary.unknownCount)
        assertEquals(0, summary.activeCount)
        assertEquals(0, summary.totalCount)
        assertFalse(summary.hasFailures)
    }

    @Test
    fun summarizeTasks_countsEveryStatusBucket() {
        val summary = summarizeTasks(
            listOf(
                task("running-1", UnifiedTaskStatus.RUNNING),
                task("running-2", UnifiedTaskStatus.RUNNING),
                task("pending", UnifiedTaskStatus.PENDING),
                task("failed", UnifiedTaskStatus.FAILED),
                task("success", UnifiedTaskStatus.SUCCESS),
                task("cancelled", UnifiedTaskStatus.CANCELLED),
                task("unknown", UnifiedTaskStatus.UNKNOWN),
            ),
        )

        assertEquals(2, summary.runningCount)
        assertEquals(1, summary.pendingCount)
        assertEquals(1, summary.failedCount)
        assertEquals(2, summary.completedCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(3, summary.activeCount)
        assertEquals(7, summary.totalCount)
        assertTrue(summary.hasFailures)
    }

    private fun task(id: String, status: UnifiedTaskStatus) = UnifiedTask(
        id = id,
        instanceId = "instance-1",
        source = TaskSource.LOCAL_UPLOAD,
        type = TaskType.UPLOAD,
        title = id,
        status = status,
        progress = null,
        path = "/",
        localUri = null,
        errorMessage = null,
        createdAt = 1L,
        updatedAt = 2L,
    )
}
