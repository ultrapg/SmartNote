package io.github.ultrapg.smartnote.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import io.github.ultrapg.smartnote.data.local.ReminderEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import java.time.ZoneId
import java.util.UUID

class ReminderScheduler(
    private val context: Context,
    private val database: SmartNoteDatabase,
    private val notificationBuilder: ReminderNotificationBuilder
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return AlarmManagerCompat.canScheduleExactAlarms(manager)
    }

    suspend fun scheduleOrUpdateReminder(
        noteId: String,
        triggerAtMs: Long,
        repeatRule: RepeatRule,
        useLiveUpdate: Boolean = true,
        repeatDaysMask: Int = 0,
        repeatInterval: Int = 1,
        preAlertMinutes: Int = 0,
        isImportant: Boolean = false
    ) {
        val zone = ZoneId.systemDefault().id
        val localIso = ReminderCalculator.formatLocalIso(triggerAtMs, zone)
        val existing = database.reminderDao().getReminderForNote(noteId)
        val entity = ReminderEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            noteId = noteId,
            triggerAtMs = triggerAtMs,
            zoneId = zone,
            localDateTimeIso = localIso,
            repeatRule = repeatRule.name,
            isCompleted = false,
            completedAtMs = null,
            snoozeCount = existing?.snoozeCount ?: 0,
            useLiveUpdate = useLiveUpdate,
            repeatDaysMask = repeatDaysMask and 0x7F,
            repeatInterval = repeatInterval.coerceIn(1, 365),
            preAlertMinutes = preAlertMinutes.coerceAtLeast(0),
            isImportant = isImportant
        )
        database.reminderDao().upsertReminder(entity)
        notificationBuilder.cancelReminderNotification(noteId)
        schedulePlatformAlarm(entity)
        syncTodayNowBarForReminder(entity)
    }

    suspend fun cancelReminderForNote(noteId: String, deleteFromDb: Boolean = true) {
        val existing = database.reminderDao().getReminderForNote(noteId)
        if (existing != null) {
            cancelPlatformAlarm(existing.id, noteId)
            notificationBuilder.cancelReminderNotification(noteId)
            if (deleteFromDb) {
                database.reminderDao().deleteReminderForNote(noteId)
            }
        }
    }

    suspend fun markReminderCompleted(noteId: String, nowMs: Long = System.currentTimeMillis()) {
        val reminder = database.reminderDao().getReminderForNote(noteId) ?: return
        notificationBuilder.cancelReminderNotification(noteId)
        cancelPlatformAlarm(reminder.id, noteId)

        val rule = runCatching { RepeatRule.valueOf(reminder.repeatRule) }.getOrDefault(RepeatRule.NONE)
        val nextOccurrence = ReminderCalculator.computeNextOccurrence(
            currentTriggerMs = reminder.triggerAtMs,
            zoneId = reminder.zoneId,
            repeatRule = rule,
            nowMs = nowMs,
            repeatDaysMask = reminder.repeatDaysMask,
            repeatInterval = reminder.repeatInterval
        )

        if (nextOccurrence != null) {
            val updated = reminder.copy(
                triggerAtMs = nextOccurrence.first,
                localDateTimeIso = nextOccurrence.second,
                isCompleted = false,
                completedAtMs = nowMs,
                snoozeCount = 0
            )
            database.reminderDao().upsertReminder(updated)
            schedulePlatformAlarm(updated)
            syncTodayNowBarForReminder(updated, nowMs)
        } else {
            database.reminderDao().upsertReminder(
                reminder.copy(isCompleted = true, completedAtMs = nowMs)
            )
        }
    }

    suspend fun handleNotificationDismissed(noteId: String, nowMs: Long = System.currentTimeMillis()) {
        val reminder = database.reminderDao().getReminderForNote(noteId) ?: return
        val rule = runCatching { RepeatRule.valueOf(reminder.repeatRule) }.getOrDefault(RepeatRule.NONE)
        if (rule == RepeatRule.NONE || reminder.isCompleted || reminder.triggerAtMs > nowMs) return
        markReminderCompleted(noteId, nowMs)
    }

    suspend fun snoozeReminder(noteId: String, snoozeMinutes: Int, nowMs: Long = System.currentTimeMillis()) {
        val reminder = database.reminderDao().getReminderForNote(noteId) ?: return
        val newTriggerMs = ReminderCalculator.computeSnoozeTriggerMs(nowMs, snoozeMinutes)
        val zone = ZoneId.systemDefault().id
        val updated = reminder.copy(
            triggerAtMs = newTriggerMs,
            zoneId = zone,
            localDateTimeIso = ReminderCalculator.formatLocalIso(newTriggerMs, zone),
            isCompleted = false,
            snoozeCount = reminder.snoozeCount + 1
        )
        database.reminderDao().upsertReminder(updated)
        schedulePlatformAlarm(updated)

        val note = database.noteDao().getNoteById(noteId)
        if (note != null && !note.isDeleted) {
            notificationBuilder.postSnoozeCountdownNotification(
                noteId = noteId,
                noteTitle = note.title.ifBlank { "Note reminder" },
                notePreview = note.plainTextPreview,
                targetTriggerAtMs = newTriggerMs,
                useLiveUpdate = updated.useLiveUpdate,
                isImportant = updated.isImportant
            )
        }
    }

    suspend fun rescheduleAllActiveReminders(recomputeTimeZone: Boolean = false) {
        val active = database.reminderDao().getAllActiveReminders()
        val currentZone = ZoneId.systemDefault().id
        val now = System.currentTimeMillis()
        for (reminder in active) {
            var updated = reminder
            if (recomputeTimeZone && reminder.zoneId != currentZone) {
                val recomputed = ReminderCalculator.recomputeForCurrentZone(
                    reminder.localDateTimeIso,
                    currentZone
                )
                updated = reminder.copy(triggerAtMs = recomputed, zoneId = currentZone)
                database.reminderDao().upsertReminder(updated)
            }
            if (updated.triggerAtMs <= now - 5 * 60_000L) {
                val note = database.noteDao().getNoteById(updated.noteId)
                val rule = runCatching { RepeatRule.valueOf(updated.repeatRule) }.getOrDefault(RepeatRule.NONE)
                if (note != null && !note.isDeleted) {
                    notificationBuilder.postDueReminderNotification(
                        noteId = updated.noteId,
                        noteTitle = note.title.ifBlank { "Note reminder" },
                        notePreview = note.plainTextPreview,
                        triggerAtMs = updated.triggerAtMs,
                        useLiveUpdate = updated.useLiveUpdate,
                        isImportant = updated.isImportant,
                        repeatRule = rule
                    )
                }
            } else {
                schedulePlatformAlarm(updated)
                syncTodayNowBarForReminder(updated, now)
            }
        }
    }

    suspend fun syncTodayNowBarForReminder(
        reminder: ReminderEntity,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!reminder.isCompleted &&
            reminder.useLiveUpdate &&
            reminder.triggerAtMs > nowMs &&
            ReminderCalculator.isSameLocalDay(reminder.triggerAtMs, nowMs)
        ) {
            val note = database.noteDao().getNoteById(reminder.noteId)
            if (note != null && !note.isDeleted) {
                notificationBuilder.postTodayNowBarNotification(
                    noteId = reminder.noteId,
                    noteTitle = note.title.ifBlank { "SmartNote Reminder" },
                    notePreview = note.plainTextPreview,
                    triggerAtMs = reminder.triggerAtMs,
                    useLiveUpdate = reminder.useLiveUpdate
                )
            } else {
                notificationBuilder.cancelTodayNowBarNotification(reminder.noteId)
            }
        } else {
            notificationBuilder.cancelTodayNowBarNotification(reminder.noteId)
        }
    }

    suspend fun syncTodayNowBarReminders(nowMs: Long = System.currentTimeMillis()) {
        val active = database.reminderDao().getAllActiveReminders()
        for (reminder in active) {
            syncTodayNowBarForReminder(reminder, nowMs)
        }
    }

    suspend fun clearAllTodayNowBarReminders() {
        val active = database.reminderDao().getAllActiveReminders()
        notificationBuilder.cancelAllTodayNowBarNotifications(active.map { it.noteId })
    }

    internal fun schedulePlatformAlarm(reminder: ReminderEntity) {
        val manager = alarmManager ?: return
        val now = System.currentTimeMillis()

        // Main reminder trigger alarm
        val mainPending = buildAlarmPendingIntent(
            reminderId = reminder.id,
            noteId = reminder.noteId,
            action = ReminderBroadcastReceiver.ACTION_REMINDER_TRIGGER,
            requestOffset = 0
        )
        val mainTriggerMs = reminder.triggerAtMs.coerceAtLeast(now + 1_000L)
        scheduleSingleAlarm(manager, mainTriggerMs, mainPending)

        // Optional early pre-alert alarm
        val preAlertPending = buildAlarmPendingIntent(
            reminderId = reminder.id,
            noteId = reminder.noteId,
            action = ReminderBroadcastReceiver.ACTION_REMINDER_PRE_ALERT,
            requestOffset = PRE_ALERT_REQUEST_OFFSET
        )
        manager.cancel(preAlertPending)
        if (reminder.preAlertMinutes > 0) {
            val preAlertMs = reminder.triggerAtMs - reminder.preAlertMinutes.toLong() * 60_000L
            if (preAlertMs > now + 5_000L) {
                scheduleSingleAlarm(manager, preAlertMs, preAlertPending)
            } else {
                preAlertPending.cancel()
            }
        } else {
            preAlertPending.cancel()
        }

        // Day-start sync alarm for future-day reminders so they appear discreetly in Today Now Bar at 00:01
        val todaySyncPending = buildAlarmPendingIntent(
            reminderId = reminder.id,
            noteId = reminder.noteId,
            action = ReminderBroadcastReceiver.ACTION_REMINDER_TODAY_SYNC,
            requestOffset = TODAY_SYNC_REQUEST_OFFSET
        )
        manager.cancel(todaySyncPending)
        val dayStartMs = ReminderCalculator.startOfReminderLocalDayMs(reminder.triggerAtMs)
        if (reminder.useLiveUpdate &&
            !ReminderCalculator.isSameLocalDay(reminder.triggerAtMs, now) &&
            dayStartMs > now + 5_000L
        ) {
            scheduleSingleAlarm(manager, dayStartMs, todaySyncPending)
        } else {
            todaySyncPending.cancel()
        }
    }

    internal fun cancelPlatformAlarm(reminderId: String, noteId: String) {
        val manager = alarmManager ?: return
        val mainPending = buildAlarmPendingIntent(
            reminderId = reminderId,
            noteId = noteId,
            action = ReminderBroadcastReceiver.ACTION_REMINDER_TRIGGER,
            requestOffset = 0
        )
        manager.cancel(mainPending)
        mainPending.cancel()

        val preAlertPending = buildAlarmPendingIntent(
            reminderId = reminderId,
            noteId = noteId,
            action = ReminderBroadcastReceiver.ACTION_REMINDER_PRE_ALERT,
            requestOffset = PRE_ALERT_REQUEST_OFFSET
        )
        manager.cancel(preAlertPending)
        preAlertPending.cancel()

        val todaySyncPending = buildAlarmPendingIntent(
            reminderId = reminderId,
            noteId = noteId,
            action = ReminderBroadcastReceiver.ACTION_REMINDER_TODAY_SYNC,
            requestOffset = TODAY_SYNC_REQUEST_OFFSET
        )
        manager.cancel(todaySyncPending)
        todaySyncPending.cancel()
    }

    private fun scheduleSingleAlarm(
        manager: AlarmManager,
        triggerMs: Long,
        pendingIntent: PendingIntent
    ) {
        if (AlarmManagerCompat.canScheduleExactAlarms(manager)) {
            runCatching {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }.onFailure {
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 5 * 60_000L, pendingIntent)
            }
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 5 * 60_000L, pendingIntent)
        }
    }

    private fun buildAlarmPendingIntent(
        reminderId: String,
        noteId: String,
        action: String,
        requestOffset: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val PRE_ALERT_REQUEST_OFFSET = 10_000
        const val TODAY_SYNC_REQUEST_OFFSET = 20_000
    }
}
