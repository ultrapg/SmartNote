package io.github.ultrapg.smartnote.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
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

        // Remove legacy channels created before explicit sound/vibration URIs were attached
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_REMINDERS) }
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_URGENT) }

        val notifSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val notifAudio = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

        if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Note Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Audible and vibrating alerts when scheduled reminders are due"
                setSound(notifSoundUri, notifAudio)
                enableVibration(true)
                vibrationPattern = STANDARD_VIBRATION_PATTERN
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val urgentSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: notifSoundUri
        val urgentAudio = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        if (manager.getNotificationChannel(CHANNEL_REMINDERS_URGENT) == null) {
            val urgentChannel = NotificationChannel(
                CHANNEL_REMINDERS_URGENT,
                "Urgent Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts with alarm tone and stronger vibration"
                setSound(urgentSoundUri, urgentAudio)
                enableVibration(true)
                vibrationPattern = URGENT_VIBRATION_PATTERN
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(urgentChannel)
        }

        if (manager.getNotificationChannel(CHANNEL_NOWBAR_TODAY) == null) {
            val nowBarChannel = NotificationChannel(
                CHANNEL_NOWBAR_TODAY,
                "Today's Reminders (Now Bar)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Discreet silent cards in Android 16 Now Bar / Live Updates for reminders due today"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(nowBarChannel)
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

    suspend fun buildTodayNowBarNotification(
        noteId: String,
        noteTitle: String,
        notePreview: String,
        triggerAtMs: Long,
        isSnoozeCountdown: Boolean = false,
        forcePromotedForTest: Boolean = false
    ): Notification {
        ensureChannel()
        val settings = settingsRepository.settingsFlow.first()
        val defaultSnoozeMins = settings.defaultSnoozeMinutes
        val timeFormatted = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(triggerAtMs))
        val safeTitle = noteTitle.ifBlank { "SmartNote Reminder" }
        val statusPrefix = if (isSnoozeCountdown) "Snoozed until $timeFormatted" else "Today at $timeFormatted"
        val bodyText = if (notePreview.isNotBlank()) {
            "$statusPrefix • ${notePreview.take(120)}"
        } else {
            statusPrefix
        }

        val openPending = buildOpenNotePendingIntent(noteId)
        val completePending = buildCompletePendingIntent(noteId)
        val snoozePending = buildSnoozePendingIntent(noteId, defaultSnoozeMins, requestOffset = 3)

        val builder = NotificationCompat.Builder(context, CHANNEL_NOWBAR_TODAY)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(safeTitle)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .setWhen(triggerAtMs)
            .setShowWhen(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            // Keep discreet upcoming Now Bar card on the phone until the reminder actually goes off
            .setLocalOnly(true)
            .addAction(0, "Complete", completePending)
            .addAction(0, "Snooze ${defaultSnoozeMins}m", snoozePending)

        if (Build.VERSION.SDK_INT >= 36 || forcePromotedForTest) {
            builder.setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(timeFormatted)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        return builder.build()
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

        val openPending = buildOpenNotePendingIntent(noteId)
        val completePending = buildCompletePendingIntent(noteId)
        val defaultSnoozeMins = settings.defaultSnoozeMinutes
        val snoozeDefaultPending = buildSnoozePendingIntent(noteId, defaultSnoozeMins, requestOffset = 3)
        val secondarySnoozeMins = if (defaultSnoozeMins == 60) 30 else 60
        val secondarySnoozeLabel = if (secondarySnoozeMins == 60) "Snooze 1h" else "Snooze 30m"
        val snoozeSecondaryPending = buildSnoozePendingIntent(noteId, secondarySnoozeMins, requestOffset = 4)

        val completeAction = NotificationCompat.Action.Builder(0, "Complete", completePending)
            .setShowsUserInterface(false)
            .extend(
                NotificationCompat.Action.WearableExtender()
                    .setHintLaunchesActivity(false)
                    .setHintDisplayActionInline(true)
            )
            .build()

        val snoozeDefaultAction = NotificationCompat.Action.Builder(0, "Snooze ${defaultSnoozeMins}m", snoozeDefaultPending)
            .setShowsUserInterface(false)
            .extend(
                NotificationCompat.Action.WearableExtender()
                    .setHintLaunchesActivity(false)
            )
            .build()

        val snoozeSecondaryAction = NotificationCompat.Action.Builder(0, secondarySnoozeLabel, snoozeSecondaryPending)
            .setShowsUserInterface(false)
            .extend(
                NotificationCompat.Action.WearableExtender()
                    .setHintLaunchesActivity(false)
            )
            .build()

        val wearRemoteInput = RemoteInput.Builder(ReminderBroadcastReceiver.KEY_WEAR_NOTE_REPLY)
            .setLabel("Add quick note...")
            .setChoices(arrayOf("Done", "Call back later", "Follow up tomorrow"))
            .build()

        val wearReplyAction = NotificationCompat.Action.Builder(
            0,
            "Add note",
            buildWearAppendPendingIntent(noteId)
        )
            .setShowsUserInterface(false)
            .addRemoteInput(wearRemoteInput)
            .build()

        val urgentAlert = isImportant && !isPreAlert
        val channelId = if (urgentAlert) CHANNEL_REMINDERS_URGENT else CHANNEL_REMINDERS
        val category = if (urgentAlert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER
        val priority = if (urgentAlert) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
        val soundUri = if (urgentAlert) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val wearableExtender = NotificationCompat.WearableExtender()
            .setBridgeTag(WEAR_BRIDGE_TAG)
            .setDismissalId("smartnote_rem_${noteId}_$triggerAtMs")
            .setHintContentIntentLaunchesActivity(true)
            .addAction(completeAction)
            .addAction(snoozeDefaultAction)
            .apply {
                if (!isPreAlert) addAction(snoozeSecondaryAction)
            }
            .addAction(wearReplyAction)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(if (urgentAlert) "Important: $safeTitle" else safeTitle)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setCategory(category)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .setWhen(triggerAtMs)
            .setShowWhen(true)
            .setSilent(false)
            .setOnlyAlertOnce(false)
            .setLocalOnly(false)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(if (urgentAlert) URGENT_VIBRATION_PATTERN else STANDARD_VIBRATION_PATTERN)
            .apply {
                if (soundUri != null) setSound(soundUri)
            }
            .addAction(completeAction)
            .addAction(snoozeDefaultAction)
            .apply {
                if (!isPreAlert) addAction(snoozeSecondaryAction)
            }
            .extend(wearableExtender)

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

        // Note: useLiveUpdate parameter kept cleanly for API compatibility; discreet Today Now Bar card
        // runs on CHANNEL_NOWBAR_TODAY while due alerts use sounding/vibrating high-priority alerts.
        @Suppress("UNUSED_VARIABLE")
        val unusedFlag = useLiveUpdate

        return builder.build()
    }

    suspend fun postTodayNowBarNotification(
        noteId: String,
        noteTitle: String,
        notePreview: String,
        triggerAtMs: Long,
        useLiveUpdate: Boolean,
        isSnoozeCountdown: Boolean = false
    ) {
        if (!canPostNotifications()) return
        val settings = settingsRepository.settingsFlow.first()
        val allowPromotion = Build.VERSION.SDK_INT >= 36 &&
            useLiveUpdate &&
            settings.liveUpdateRemindersEnabled &&
            canPromoteLiveUpdates()
        if (!allowPromotion) return

        val notif = buildTodayNowBarNotification(
            noteId = noteId,
            noteTitle = noteTitle,
            notePreview = notePreview,
            triggerAtMs = triggerAtMs,
            isSnoozeCountdown = isSnoozeCountdown
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(todayNowBarNotificationIdFor(noteId), notif)
        }
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
        // Remove any silent Today Now Bar card first so the due alert fires with fresh sound & vibration
        cancelTodayNowBarNotification(noteId)
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
        NotificationManagerCompat.from(context).cancel(notificationIdFor(noteId))
        if (useLiveUpdate && ReminderCalculator.isSameLocalDay(targetTriggerAtMs)) {
            postTodayNowBarNotification(
                noteId = noteId,
                noteTitle = noteTitle,
                notePreview = notePreview,
                triggerAtMs = targetTriggerAtMs,
                useLiveUpdate = useLiveUpdate,
                isSnoozeCountdown = true
            )
        } else {
            cancelTodayNowBarNotification(noteId)
        }
        @Suppress("UNUSED_VARIABLE")
        val unusedImportant = isImportant
    }

    fun cancelTodayNowBarNotification(noteId: String) {
        NotificationManagerCompat.from(context).cancel(todayNowBarNotificationIdFor(noteId))
    }

    fun cancelAllTodayNowBarNotifications(noteIds: List<String>) {
        val mgr = NotificationManagerCompat.from(context)
        noteIds.forEach { mgr.cancel(todayNowBarNotificationIdFor(it)) }
    }

    fun cancelReminderNotification(noteId: String) {
        val mgr = NotificationManagerCompat.from(context)
        mgr.cancel(notificationIdFor(noteId))
        mgr.cancel(todayNowBarNotificationIdFor(noteId))
    }

    fun notificationIdFor(noteId: String): Int = 40_000 + (noteId.hashCode() and 0x0FFF_FFFF)

    fun todayNowBarNotificationIdFor(noteId: String): Int = 50_000 + (noteId.hashCode() and 0x0FFF_FFFF)

    private fun buildOpenNotePendingIntent(noteId: String): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOTE
            putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            noteId.hashCode() + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCompletePendingIntent(noteId: String): PendingIntent {
        val completeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_REMINDER_COMPLETE
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 2,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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

    private fun buildWearAppendPendingIntent(noteId: String): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_REMINDER_WEAR_APPEND
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
        }
        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 6,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        )
    }

    companion object {
        private const val LEGACY_CHANNEL_REMINDERS = "smartnote_reminders_high"
        private const val LEGACY_CHANNEL_URGENT = "smartnote_reminders_urgent"

        const val CHANNEL_REMINDERS = "smartnote_reminders_due_v2"
        const val CHANNEL_REMINDERS_URGENT = "smartnote_reminders_urgent_v2"
        const val CHANNEL_NOWBAR_TODAY = "smartnote_nowbar_today_v1"
        const val WEAR_BRIDGE_TAG = "smartnote_reminder"

        val STANDARD_VIBRATION_PATTERN = longArrayOf(0L, 220L, 140L, 220L)
        val URGENT_VIBRATION_PATTERN = longArrayOf(0L, 350L, 150L, 350L, 150L, 350L)
    }
}
