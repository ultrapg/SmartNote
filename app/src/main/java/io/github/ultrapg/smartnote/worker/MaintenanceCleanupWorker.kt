package io.github.ultrapg.smartnote.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ultrapg.smartnote.SmartNoteApplication
import java.util.concurrent.TimeUnit

class MaintenanceCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SmartNoteApplication ?: return Result.success()
        val container = app.container
        container.noteRepository.purgeExpiredRecycleBin(retentionDays = 30)
        container.attachmentStorage.deleteStaleIncomingShares()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "smartnote_daily_maintenance"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<MaintenanceCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
