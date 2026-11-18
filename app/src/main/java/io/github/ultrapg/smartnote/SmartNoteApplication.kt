package io.github.ultrapg.smartnote

import android.app.Application
import androidx.work.Configuration
import io.github.ultrapg.smartnote.di.AppContainer
import io.github.ultrapg.smartnote.worker.MaintenanceCleanupWorker

class SmartNoteApplication : Application(), Configuration.Provider {

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container.reminderNotificationBuilder.ensureChannel()
        runCatching { MaintenanceCleanupWorker.schedulePeriodic(this) }
    }
}
