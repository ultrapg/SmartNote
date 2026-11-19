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
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

class ReminderNotificationBuilder(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
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
        if (manager.getNotificationChannel(CHANNEL_REMINDERS_URGENT) == null) {
            val urgentChannel = NotificationChannel(
                CHANNEL_REMINDERS_URGENT,
                "Urgent Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority offline reminder alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 250L, 140L, 250L)
            }
            manager.createNotificationChannel(urgentChannel)
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
        isSnoozeCountdown: Boolean = false,
        isPreAlert: Boolean = false,
        preAlertMinutes: Int = 0,
        isImportant: Boolean = false,
        repeatRule: RepeatRule = RepeatRule.NONE
    ): Notification {
        ensureChannel()
        val settings = settingsRepository.settingsFlow.first()
        val allowPromotion = Build.VERSION.SDK_INT >= 36 &&
            useLiveUpdate &&
            settings.liveUpdateRemindersEnabled &&
            canPromoteLiveUpdates()

        val timeFormatted = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(triggerAtMs))
        val safeTitle = noteTitle.ifBlank { "SmartNote Reminder" }
        val leadDuration = ReminderCalculator.formatPreAlertLabel(preAlertMinutes).removeSuffix(" before")
        val bodyText = buildString {
            when {
                isPreAlert -> append("Upcoming in ").append(leadDuration).append(" (").append(timeFormatted).append(")")
                isSnoozeCountdown -> append("Snoozed until ").append(timeFormatted)
                else -> append("Due ").append(timeFormatted)
            }
            if (isImportant && !isPreAlert) {
                append(" • Urgent")
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

        val defaultSnoozeMins = settings.defaultSnoozeMinutes
        val snoozeDefaultPending = buildSnoozePendingIntent(noteId, defaultSnoozeMins, requestOffset = 3)
        val secondarySnoozeMins = if (defaultSnoozeMins == 60) 30 else 60
        val secondarySnoozeLabel = if (secondarySnoozeMins == 60) "Snooze 1h" else "Snooze 30m"
        val snoozeSecondaryPending = buildSnoozePendingIntent(noteId, secondarySnoozeMins, requestOffset = 4)

        val channelId = if (isImportant && !isPreAlert) CHANNEL_REMINDERS_URGENT else CHANNEL_REMINDERS
        val category = if (isImportant && !isPreAlert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER
        val priority = if (isImportant && !isPreAlert) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(if (isImportant && !isPreAlert) "Important: $safeTitle" else safeTitle)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setCategory(category)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPending)
            .setWhen(triggerAtMs)
            .setShowWhen(true)
            .addAction(0, "Complete", completePending)
            .addAction(0, "Snooze ${defaultSnoozeMins}m", snoozeDefaultPending)

        if (!isPreAlert) {
            builder.addAction(0, secondarySnoozeLabel, snoozeSecondaryPending)
        }

        if (!isPreAlert && repeatRule != RepeatRule.NONE) {
            val dismissedIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ReminderBroadcastReceiver.ACTION_REMINDER_DISMISSED
                putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            }
            val dismissedPending = PendingIntent.getBroadcast(
                context,
                noteId.hashCode() + 5,
                dismissedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setDeleteIntent(dismissedPending)
        }

        if (allowPromotion) {
            // Strictly compliant with Android 16+ Live Updates & Samsung One UI 8+ Now Bar rules:
            // ongoing = true, requestPromotedOngoing = true, no RemoteViews, no colorization, non-min channel
            builder.setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(
                    when {
                        isPreAlert || isSnoozeCountdown -> timeFormatted
                        else -> "Due"
                    }
                )
            if (isSnoozeCountdown || isPreAlert) {
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
        useLiveUpdate: Boolean,
        isImportant: Boolean = false,
        repeatRule: RepeatRule = RepeatRule.NONE
    ) {
        if (!canPostNotifications()) return
        val notif = buildDueReminderNotification(
            noteId = noteId,
            noteTitle = noteTitle,
            notePreview = notePreview,
            triggerAtMs = triggerAtMs,
            useLiveUpdate = useLiveUpdate,
            isSnoozeCountdown = false,
            isPreAlert = false,
            isImportant = isImportant,
            repeatRule = repeatRule
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(noteId), notif)
        }
    }

    suspend fun postPreAlertNotification(
        noteId: String,
        noteTitle: String,
        notePreview: String,
        triggerAtMs: Long,
        preAlertMinutes: Int,
        useLiveUpdate: Boolean,
        isImportant: Boolean = false
    ) {
        if (!canPostNotifications()) return
        val notif = buildDueReminderNotification(
            noteId = noteId,
            noteTitle = noteTitle,
            notePreview = notePreview,
            triggerAtMs = triggerAtMs,
            useLiveUpdate = useLiveUpdate,
            isSnoozeCountdown = false,
            isPreAlert = true,
            preAlertMinutes = preAlertMinutes,
            isImportant = isImportant
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
        useLiveUpdate: Boolean,
        isImportant: Boolean = false
    ) {
        if (!canPostNotifications()) return
        val notif = buildDueReminderNotification(
            noteId = noteId,
            noteTitle = noteTitle,
            notePreview = notePreview,
            triggerAtMs = targetTriggerAtMs,
            useLiveUpdate = useLiveUpdate,
            isSnoozeCountdown = true,
            isPreAlert = false,
            isImportant = isImportant
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(noteId), notif)
        }
    }

    fun cancelReminderNotification(noteId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(noteId))
    }

    fun notificationIdFor(noteId: String): Int = 40_000 + (noteId.hashCode() and 0x0FFF_FFFF)

    private fun buildSnoozePendingIntent(noteId: String, snoozeMinutes: Int, requestOffset: Int): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_REMINDER_SNOOZE
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderBroadcastReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "smartnote_reminders_high"
        const val CHANNEL_REMINDERS_URGENT = "smartnote_reminders_urgent"
    }
}
