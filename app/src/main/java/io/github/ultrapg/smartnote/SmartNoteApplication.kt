package io.github.ultrapg.smartnote

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.work.Configuration
import io.github.ultrapg.smartnote.di.AppContainer
import io.github.ultrapg.smartnote.gemini.SmartNoteAppFunctions
import io.github.ultrapg.smartnote.worker.MaintenanceCleanupWorker

class SmartNoteApplication : Application(), Configuration.Provider, AppFunctionConfiguration.Provider {

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(SmartNoteAppFunctions::class.java) {
                SmartNoteAppFunctions(container.operationsApi)
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        container.reminderNotificationBuilder.ensureChannel()
        runCatching { MaintenanceCleanupWorker.schedulePeriodic(this) }
    }
}
