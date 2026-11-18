package io.github.ultrapg.smartnote.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        useLiveUpdate: Boolean = true
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
            useLiveUpdate = useLiveUpdate
        )
        database.reminderDao().upsertReminder(entity)
        schedulePlatformAlarm(entity)
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
            nowMs = nowMs
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
        } else {
            database.reminderDao().upsertReminder(
                reminder.copy(isCompleted = true, completedAtMs = nowMs)
            )
        }
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
                useLiveUpdate = updated.useLiveUpdate
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
                if (note != null && !note.isDeleted) {
                    notificationBuilder.postDueReminderNotification(
                        noteId = updated.noteId,
                        noteTitle = note.title.ifBlank { "Note reminder" },
                        notePreview = note.plainTextPreview,
                        triggerAtMs = updated.triggerAtMs,
                        useLiveUpdate = updated.useLiveUpdate
                    )
                }
            } else {
                schedulePlatformAlarm(updated)
            }
        }
    }

    internal fun schedulePlatformAlarm(reminder: ReminderEntity) {
        val manager = alarmManager ?: return
        val pendingIntent = buildTriggerPendingIntent(reminder.id, reminder.noteId)
        val triggerMs = reminder.triggerAtMs.coerceAtLeast(System.currentTimeMillis() + 1_000L)
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

    internal fun cancelPlatformAlarm(reminderId: String, noteId: String) {
        val manager = alarmManager ?: return
        val pi = buildTriggerPendingIntent(reminderId, noteId)
        manager.cancel(pi)
        pi.cancel()
    }

    private fun buildTriggerPendingIntent(reminderId: String, noteId: String): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_REMINDER_TRIGGER
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
