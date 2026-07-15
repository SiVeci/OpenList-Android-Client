package io.openlist.client.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/** One scheduling boundary for app, provider/root, and TTL recovery triggers. */
@Singleton
class SystemDocumentRecoveryScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun scheduleStartupRecovery() = scheduleNetworkRecovery()

    /** Safe from binder entry points: WorkManager coalesces repeated root and directory access. */
    fun scheduleNetworkRecovery() = enqueue(RECOVERY_WORK_NAME, requiresNetwork = true)

    // P4 expiration deletes only an already-local no-backup draft and must
    // not wait indefinitely for a device that stays offline.
    fun scheduleTtlCleanup() = enqueue(TTL_WORK_NAME, requiresNetwork = false)

    private fun enqueue(uniqueName: String, requiresNetwork: Boolean) {
        val request = OneTimeWorkRequestBuilder<SystemDocumentRecoveryWorker>()
            // P5 may inspect remote state.  The P1 worker itself remains
            // read-only and never uploads a FAILED_DRAFT.
            .setConstraints(Constraints.Builder().apply {
                if (requiresNetwork) setRequiredNetworkType(NetworkType.CONNECTED)
            }.build())
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val RECOVERY_WORK_NAME = "system-document-recovery"
        const val TTL_WORK_NAME = "system-document-draft-ttl"
    }
}
