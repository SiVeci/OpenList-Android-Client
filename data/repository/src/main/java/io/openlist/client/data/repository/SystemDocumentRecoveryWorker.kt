package io.openlist.client.data.repository

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * P4 recovery worker has no remote side effect. It can only expire or expose
 * interrupted local drafts; P5 later adds fact-checked remote compensation.
 */
@HiltWorker
class SystemDocumentRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recoveryCoordinator: SystemDocumentRecoveryCoordinator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        recoveryCoordinator.recoverLocalDrafts()
        return Result.success()
    }
}
