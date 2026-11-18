package io.github.ultrapg.smartnote.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ultrapg.smartnote.SmartNoteApplication
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
                            container.reminderNotificationBuilder.postDueReminderNotification(
                                noteId = noteId,
                                noteTitle = note.title.ifBlank { "SmartNote Reminder" },
                                notePreview = note.plainTextPreview,
                                triggerAtMs = reminder.triggerAtMs,
                                useLiveUpdate = reminder.useLiveUpdate
                            )
                        }
                    }
                    ACTION_REMINDER_COMPLETE -> {
                        container.reminderScheduler.markReminderCompleted(noteId)
                    }
                    ACTION_REMINDER_SNOOZE -> {
                        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                        container.reminderScheduler.snoozeReminder(noteId, minutes)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER_TRIGGER = "io.github.ultrapg.smartnote.action.REMINDER_TRIGGER"
        const val ACTION_REMINDER_COMPLETE = "io.github.ultrapg.smartnote.action.REMINDER_COMPLETE"
        const val ACTION_REMINDER_SNOOZE = "io.github.ultrapg.smartnote.action.REMINDER_SNOOZE"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }
}
