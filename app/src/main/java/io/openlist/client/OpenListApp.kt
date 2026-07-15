package io.openlist.client

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.openlist.client.data.repository.SystemDocumentRecoveryScheduler
import io.openlist.client.data.repository.SystemDocumentSpaceManager
import javax.inject.Inject

@HiltAndroidApp
class OpenListApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var systemDocumentRecoveryScheduler: SystemDocumentRecoveryScheduler

    @Inject
    lateinit var systemDocumentSpaceManager: SystemDocumentSpaceManager

    override fun onCreate() {
        super.onCreate()
        systemDocumentSpaceManager.cleanOrphanedReadCache()
        // This schedules a read-only empty-journal scan in P1.  It never
        // promotes, uploads, or retries a FAILED_DRAFT automatically.
        systemDocumentRecoveryScheduler.scheduleStartupRecovery()
        systemDocumentRecoveryScheduler.scheduleTtlCleanup()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
