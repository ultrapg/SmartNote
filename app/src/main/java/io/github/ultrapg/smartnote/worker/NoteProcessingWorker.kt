package io.github.ultrapg.smartnote.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.data.local.OcrResultEntity
import io.github.ultrapg.smartnote.data.local.ProcessingJobEntity
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import kotlinx.coroutines.flow.first

class NoteProcessingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getString(KEY_NOTE_ID) ?: return Result.failure()
        val app = applicationContext as? SmartNoteApplication ?: return Result.failure()
        val container = app.container

        val detail = container.noteRepository.getNoteDetail(noteId) ?: return Result.success()
        if (detail.isDeleted) return Result.success()

        val settings = container.settingsRepository.settingsFlow.first()
        if (!settings.backgroundAnalysisEnabled) {
            container.searchIndexer.indexNote(noteId)
            return Result.success()
        }

        val targetRevision = detail.contentRevision
        val now = System.currentTimeMillis()
        container.database.searchAndMaintenanceDao().upsertProcessingJob(
            ProcessingJobEntity(
                noteId = noteId,
                jobType = "FULL_PIPELINE",
                contentRevision = targetRevision,
                status = "RUNNING",
                attemptCount = runAttemptCount + 1,
                updatedAtMs = now
            )
        )

        return runCatching {
            // 1. Offline OCR for attachments (SHA-256 cached first)
            val ocrTexts = mutableListOf<String>()
            var hasScreenshot = false
            for (att in detail.attachments) {
                if (att.isScreenshot) hasScreenshot = true
                val alreadyCompleted = att.ocrStatus == OcrStatus.COMPLETED || att.ocrStatus == OcrStatus.EMPTY
                if (alreadyCompleted) {
                    att.ocrText?.takeIf { it.isNotBlank() }?.let(ocrTexts::add)
                    continue
                }
                if (!settings.ocrEnabled) continue

                val cached = container.database.attachmentDao().findCachedOcrBySha256(att.sha256)
                if (cached != null) {
                    container.database.attachmentDao().upsertOcrResult(
                        OcrResultEntity(
                            attachmentId = att.id,
                            recognizedText = cached.recognizedText,
                            languageCode = cached.languageCode,
                            avgConfidence = cached.avgConfidence,
                            status = cached.status,
                            processedAtMs = System.currentTimeMillis()
                        )
                    )
                    if (cached.recognizedText.isNotBlank()) ocrTexts += cached.recognizedText
                    continue
                }

                val file = container.attachmentStorage.resolvePrivateFile(att.relativeFilePath)
                val outcome = container.ocrEngine.recognizeFile(file)
                val status = when {
                    !outcome.succeeded -> OcrStatus.FAILED
                    outcome.text.isBlank() -> OcrStatus.EMPTY
                    else -> OcrStatus.COMPLETED
                }
                container.database.attachmentDao().upsertOcrResult(
                    OcrResultEntity(
                        attachmentId = att.id,
                        recognizedText = outcome.text,
                        avgConfidence = outcome.confidence,
                        status = status.name,
                        processedAtMs = System.currentTimeMillis()
                    )
                )
                if (outcome.text.isNotBlank()) ocrTexts += outcome.text
            }

            // Check if note was superseded by newer user edits
            val currentRevision = container.database.noteDao().getNoteById(noteId)?.contentRevision ?: targetRevision
            if (currentRevision != targetRevision) return Result.success()

            // 2. Opt-in network Link Metadata Preview (strictly skipped when fetchLinkPreviews == false)
            if (settings.fetchLinkPreviews && detail.links.isNotEmpty()) {
                val updatedLinks = detail.links.mapNotNull { link ->
                    if (link.fetchStatus == LinkFetchStatus.FETCHED) return@mapNotNull null
                    val meta = container.linkMetadataFetcher.fetchMetadataIfAllowed(link.rawUrl)
                    if (meta.blockedByPrivacySetting) return@mapNotNull null
                    val entity = container.database.linkPreviewDao().getLinksForNote(noteId)
                        .firstOrNull { it.id == link.id } ?: return@mapNotNull null
                    entity.copy(
                        pageTitle = meta.pageTitle ?: entity.pageTitle,
                        description = meta.description ?: entity.description,
                        fetchStatus = if (meta.succeeded) LinkFetchStatus.FETCHED.name else LinkFetchStatus.FAILED.name,
                        updatedAtMs = System.currentTimeMillis()
                    )
                }
                if (updatedLinks.isNotEmpty()) {
                    container.database.linkPreviewDao().upsertLinks(updatedLinks)
                }
            }

            // 3. SmartTags suggestion update (respecting user tags & dismissed tags)
            val detectedLinks = buildList {
                detail.blocks.forEach { block ->
                    when (block.type) {
                        NoteBlockType.TEXT, NoteBlockType.CHECKLIST -> addAll(SmartLinkDetector.extractLinks(block.textContent))
                        NoteBlockType.LINK -> block.linkUrl?.let { SmartLinkDetector.parseSingleUrl(it)?.let(::add) }
                        NoteBlockType.IMAGE -> Unit
                    }
                }
            }.distinctBy { it.canonicalUrl }

            if (settings.smartTagsEnabled) {
                val existingUserVocab = container.tagRepository.getAllTagsSnapshot().map { it.name }
                val suggestions = container.smartTagsEngine.suggestTags(
                    title = detail.title,
                    blocks = detail.blocks,
                    ocrTexts = ocrTexts,
                    links = detectedLinks,
                    hasScreenshot = hasScreenshot,
                    existingUserVocabulary = existingUserVocab
                )
                container.tagRepository.applySuggestedTags(
                    noteId = noteId,
                    suggestions = suggestions.map { it.name to it.confidence }
                )
            }

            // 4. Full FTS5 + trigram + vocab refresh (including OCR text, tags, link titles)
            container.searchIndexer.indexNote(noteId)

            // 5. Optional neural embedding update when semantic re-ranking is enabled
            if (settings.semanticRerankEnabled) {
                val combined = buildString {
                    append(detail.title).append('\n')
                    detail.blocks.forEach { append(it.textContent).append('\n') }
                    ocrTexts.forEach { append(it).append('\n') }
                }
                val hash = TextHashing.sha256Hex(combined)
                container.aiEngine.updateNoteEmbeddingIfNeeded(noteId, hash, combined)
            }

            container.database.searchAndMaintenanceDao().upsertProcessingJob(
                ProcessingJobEntity(
                    noteId = noteId,
                    jobType = "FULL_PIPELINE",
                    contentRevision = targetRevision,
                    status = "COMPLETED",
                    attemptCount = runAttemptCount + 1,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            Result.success()
        }.getOrElse { err ->
            container.database.searchAndMaintenanceDao().upsertProcessingJob(
                ProcessingJobEntity(
                    noteId = noteId,
                    jobType = "FULL_PIPELINE",
                    contentRevision = targetRevision,
                    status = "FAILED",
                    attemptCount = runAttemptCount + 1,
                    lastError = err.message,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"

        fun enqueueForNote(context: Context, noteId: String) {
            val request = OneTimeWorkRequestBuilder<NoteProcessingWorker>()
                .setInputData(workDataOf(KEY_NOTE_ID to noteId))
                .addTag("smartnote_note_processing")
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("note_process_$noteId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
