package io.github.ultrapg.smartnote.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val app = context.applicationContext as? SmartNoteApplication ?: return
        val container = app.container
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMINDER_TRIGGER -> {
                        val note = container.database.noteDao().getNoteById(noteId)
                        val reminder = container.database.reminderDao().getReminderForNote(noteId)
                        if (note != null && !note.isDeleted && reminder != null && !reminder.isCompleted) {
                            val rule = runCatching { RepeatRule.valueOf(reminder.repeatRule) }
                                .getOrDefault(RepeatRule.NONE)
                            container.reminderNotificationBuilder.postDueReminderNotification(
                                noteId = noteId,
                                noteTitle = note.title.ifBlank { "SmartNote Reminder" },
                                notePreview = note.plainTextPreview,
                                triggerAtMs = reminder.triggerAtMs,
                                useLiveUpdate = reminder.useLiveUpdate,
                                isImportant = reminder.isImportant,
                                repeatRule = rule
                            )
                        }
                    }
                    ACTION_REMINDER_PRE_ALERT -> {
                        val note = container.database.noteDao().getNoteById(noteId)
                        val reminder = container.database.reminderDao().getReminderForNote(noteId)
                        if (note != null && !note.isDeleted && reminder != null && !reminder.isCompleted &&
                            reminder.preAlertMinutes > 0
                        ) {
                            container.reminderNotificationBuilder.postPreAlertNotification(
                                noteId = noteId,
                                noteTitle = note.title.ifBlank { "SmartNote Reminder" },
                                notePreview = note.plainTextPreview,
                                triggerAtMs = reminder.triggerAtMs,
                                preAlertMinutes = reminder.preAlertMinutes,
                                useLiveUpdate = reminder.useLiveUpdate,
                                isImportant = reminder.isImportant
                            )
                        }
                    }
                    ACTION_REMINDER_COMPLETE -> {
                        container.reminderScheduler.markReminderCompleted(noteId)
                    }
                    ACTION_REMINDER_DISMISSED -> {
                        container.reminderScheduler.handleNotificationDismissed(noteId)
                    }
                    ACTION_REMINDER_SNOOZE -> {
                        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                        container.reminderScheduler.snoozeReminder(noteId, minutes)
                    }
                    ACTION_REMINDER_TODAY_SYNC -> {
                        val reminder = container.database.reminderDao().getReminderForNote(noteId)
                        if (reminder != null) {
                            container.reminderScheduler.syncTodayNowBarForReminder(reminder)
                        }
                    }
                    ACTION_REMINDER_WEAR_APPEND -> {
                        val replyText = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_WEAR_NOTE_REPLY)
                            ?.toString()
                            ?.trim()
                        if (!replyText.isNullOrBlank()) {
                            container.noteRepository.appendQuickLineToNote(noteId, replyText)
                        }
                        runCatching {
                            NotificationManagerCompat.from(context)
                                .cancel(container.reminderNotificationBuilder.notificationIdFor(noteId))
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER_TRIGGER = "io.github.ultrapg.smartnote.action.REMINDER_TRIGGER"
        const val ACTION_REMINDER_PRE_ALERT = "io.github.ultrapg.smartnote.action.REMINDER_PRE_ALERT"
        const val ACTION_REMINDER_COMPLETE = "io.github.ultrapg.smartnote.action.REMINDER_COMPLETE"
        const val ACTION_REMINDER_DISMISSED = "io.github.ultrapg.smartnote.action.REMINDER_DISMISSED"
        const val ACTION_REMINDER_SNOOZE = "io.github.ultrapg.smartnote.action.REMINDER_SNOOZE"
        const val ACTION_REMINDER_TODAY_SYNC = "io.github.ultrapg.smartnote.action.REMINDER_TODAY_SYNC"
        const val ACTION_REMINDER_WEAR_APPEND = "io.github.ultrapg.smartnote.action.REMINDER_WEAR_APPEND"
        const val KEY_WEAR_NOTE_REPLY = "wear_note_reply"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }
}
