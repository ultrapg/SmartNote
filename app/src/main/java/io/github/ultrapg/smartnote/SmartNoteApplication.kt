package io.github.ultrapg.smartnote

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.github.ultrapg.smartnote.di.AppContainer
import io.github.ultrapg.smartnote.worker.MaintenanceCleanupWorker
import kotlinx.coroutines.launch

class SmartNoteApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .allowHardware(false)
        .allowRgb565(true)
        .crossfade(false)
        .build()

    override fun onCreate() {
        super.onCreate()
        container.reminderNotificationBuilder.ensureChannel()
        runCatching { MaintenanceCleanupWorker.schedulePeriodic(this) }
        val launchMs = System.currentTimeMillis()
        container.applicationScope.launch {
            runCatching { container.noteRepository.pruneBlankUntouchedNotes(olderThanOrEqualMs = launchMs) }
            runCatching { container.reminderScheduler.syncTodayNowBarReminders() }
        }
    }
}
