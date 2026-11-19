package io.github.ultrapg.smartnote.worker

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ultrapg.smartnote.SmartNoteApplication

class NoteProcessingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getString(KEY_NOTE_ID) ?: return Result.failure()
        val app = applicationContext as? SmartNoteApplication ?: return Result.failure()
        return when (app.container.noteProcessingPipeline.processNote(noteId, runAttemptCount)) {
            NoteProcessingOutcome.FAILED -> if (runAttemptCount < 2) Result.retry() else Result.failure()
            NoteProcessingOutcome.COMPLETED,
            NoteProcessingOutcome.SKIPPED_ALREADY_CURRENT,
            NoteProcessingOutcome.SKIPPED_SUPERSEDED,
            NoteProcessingOutcome.SKIPPED_UNAVAILABLE -> Result.success()
        }
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"

        fun enqueueForNote(context: Context, noteId: String) {
            val builder = OneTimeWorkRequestBuilder<NoteProcessingWorker>()
                .setInputData(workDataOf(KEY_NOTE_ID to noteId))
                .addTag("smartnote_note_processing")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("note_process_$noteId", ExistingWorkPolicy.REPLACE, builder.build())
        }
    }
}
