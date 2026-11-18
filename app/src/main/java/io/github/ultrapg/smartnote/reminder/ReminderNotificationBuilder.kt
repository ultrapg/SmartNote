package io.github.ultrapg.smartnote.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.R
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

class ReminderNotificationBuilder(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_REMINDERS)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Note Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled offline reminders and snoozed note alerts"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canPromoteLiveUpdates(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            NotificationManagerCompat.from(context).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    suspend fun buildDueReminderNotification(
        noteId: String,
        noteTitle: String,
        notePreview: String,
        triggerAtMs: Long,
        useLiveUpdate: Boolean,
        isSnoozeCountdown: Boolean = false
    ): Notification {
        ensureChannel()
        val settings = settingsRepository.settingsFlow.first()
        val allowPromotion = Build.VERSION.SDK_INT >= 36 &&
            useLiveUpdate &&
            settings.liveUpdateRemindersEnabled &&
            canPromoteLiveUpdates()

        val timeFormatted = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(triggerAtMs))
        val safeTitle = noteTitle.ifBlank { "SmartNote Reminder" }
        val bodyText = buildString {
            if (isSnoozeCountdown) {
                append("Snoozed until ").append(timeFormatted)
            } else {
                append("Due ").append(timeFormatted)
            }
            if (notePreview.isNotBlank()) {
                append(" • ").append(notePreview.take(140))
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOTE
            putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            noteId.hashCode() + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_REMINDER_COMPLETE
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 2,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_REMINDER_SNOOZE
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderBroadcastReceiver.EXTRA_SNOOZE_MINUTES, settings.defaultSnoozeMinutes)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(safeTitle)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPending)
            .setWhen(triggerAtMs)
            .setShowWhen(true)
            .addAction(0, "Complete", completePending)
            .addAction(0, "Snooze ${settings.defaultSnoozeMinutes}m", snoozePending)

        if (allowPromotion) {
            // Strictly compliant with Android 16+ Live Updates & Samsung One UI 8+ Now Bar rules:
            // ongoing = true, requestPromotedOngoing = true, no RemoteViews, no colorization, non-min channel
            builder.setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(if (isSnoozeCountdown) timeFormatted else "Due")
            if (isSnoozeCountdown) {
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
        } else {
            builder.setAutoCancel(true)
        }

        return builder.build()
    }

    suspend fun postDueReminderNotification(
        noteId: String,
        noteTitle: String,
        notePreview: String,
        triggerAtMs: Long,
        useLiveUpdate: Boolean
    ) {
        if (!canPostNotifications()) return
        val notif = buildDueReminderNotification(
            noteId = noteId,
            noteTitle = noteTitle,
            notePreview = notePreview,
            triggerAtMs = triggerAtMs,
            useLiveUpdate = useLiveUpdate,
            isSnoozeCountdown = false
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(noteId), notif)
        }
    }

    suspend fun postSnoozeCountdownNotification(
        noteId: String,
        noteTitle: String,
        notePreview: String,
        targetTriggerAtMs: Long,
        useLiveUpdate: Boolean
    ) {
        if (!canPostNotifications()) return
        val notif = buildDueReminderNotification(
            noteId = noteId,
            noteTitle = noteTitle,
            notePreview = notePreview,
            triggerAtMs = targetTriggerAtMs,
            useLiveUpdate = useLiveUpdate,
            isSnoozeCountdown = true
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(noteId), notif)
        }
    }

    fun cancelReminderNotification(noteId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(noteId))
    }

    fun notificationIdFor(noteId: String): Int = 40_000 + (noteId.hashCode() and 0x0FFF_FFFF)

    companion object {
        const val CHANNEL_REMINDERS = "smartnote_reminders_high"
    }
}
