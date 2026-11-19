package io.github.ultrapg.smartnote

import android.app.Application
import android.app.NotificationManager
import android.content.Context
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
        cleanupRetiredReminderNotifications()
        runCatching { MaintenanceCleanupWorker.schedulePeriodic(this) }
        val launchMs = System.currentTimeMillis()
        container.applicationScope.launch {
            runCatching { container.noteRepository.pruneBlankUntouchedNotes(olderThanOrEqualMs = launchMs) }
        }
    }

    private fun cleanupRetiredReminderNotifications() {
        runCatching {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            mgr.cancelAll()
            RETIRED_NOTIFICATION_CHANNELS.forEach { channelId ->
                runCatching { mgr.deleteNotificationChannel(channelId) }
            }
        }
    }

    private companion object {
        val RETIRED_NOTIFICATION_CHANNELS = listOf(
            "smartnote_reminders_high",
            "smartnote_reminders_urgent",
            "smartnote_reminders_due_v2",
            "smartnote_reminders_urgent_v2",
            "smartnote_nowbar_today_v1",
            "smartnote_nowbar_live_v2"
        )
    }
}
