package io.github.ultrapg.smartnote.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.data.local.OcrResultEntity
import io.github.ultrapg.smartnote.data.local.ProcessingJobEntity
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
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
                if (!settings.effectiveOcrEnabled) continue

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

            // 3. Auto-title generation after OCR / optional link metadata when blank or generic share placeholder
            if (settings.effectiveAutoTitleEnabled && SmartTitleGenerator.isPlaceholderOrBlankTitle(detail.title)) {
                val refreshedLinks = container.noteRepository.getNoteDetail(noteId)?.links ?: detail.links
                val generatedTitle = container.smartTitleGenerator.generateSmartTitle(
                    blocks = detail.blocks,
                    ocrTexts = ocrTexts,
                    links = refreshedLinks,
                    allowNeuralScoring = settings.aiFeaturesEnabled
                )
                if (generatedTitle.isNotBlank()) {
                    container.noteRepository.applyGeneratedTitleIfUnmodified(
                        noteId = noteId,
                        expectedRevision = targetRevision,
                        generatedTitle = generatedTitle
                    )
                }
            }

            // 4. SmartTags suggestion update (respecting user tags & dismissed tags)
            val currentTitleForTags = container.database.noteDao().getNoteById(noteId)?.title ?: detail.title
            val detectedLinks = buildList {
                detail.blocks.forEach { block ->
                    when (block.type) {
                        NoteBlockType.TEXT, NoteBlockType.CHECKLIST -> addAll(SmartLinkDetector.extractLinks(block.textContent))
                        NoteBlockType.LINK -> block.linkUrl?.let { SmartLinkDetector.parseSingleUrl(it)?.let(::add) }
                        NoteBlockType.IMAGE -> Unit
                    }
                }
            }.distinctBy { it.canonicalUrl }

            val combinedForEmbedding = buildString {
                append(currentTitleForTags).append('\n')
                detail.blocks.forEach { append(it.textContent).append('\n') }
                ocrTexts.forEach { append(it).append('\n') }
            }
            var precomputedNoteVec: FloatArray? = null

            if (settings.effectiveSmartTagsEnabled) {
                val existingUserVocab = container.tagRepository.getAllTagsSnapshot().map { it.name }
                val (userTagProfiles, userTagNoteIds) = buildUserTagContext(
                    container = container,
                    excludeNoteId = noteId
                )
                val semanticTagScores = if (settings.aiFeaturesEnabled && combinedForEmbedding.isNotBlank()) {
                    val (vec, affinities) = container.aiEngine.computeEmbeddingAndTagAffinities(
                        noteText = combinedForEmbedding,
                        categoryPrototypes = container.smartTagsEngine.categoryPrototypes(),
                        userTagNoteIds = userTagNoteIds
                    )
                    precomputedNoteVec = vec
                    affinities
                } else {
                    emptyMap()
                }
                val suggestions = container.smartTagsEngine.suggestTags(
                    title = currentTitleForTags,
                    blocks = detail.blocks,
                    ocrTexts = ocrTexts,
                    links = detectedLinks,
                    hasScreenshot = hasScreenshot,
                    existingUserVocabulary = existingUserVocab,
                    semanticTagScores = semanticTagScores,
                    userTagProfiles = userTagProfiles
                )
                container.tagRepository.applySuggestedTags(
                    noteId = noteId,
                    suggestions = suggestions.map { it.name to it.confidence }
                )
            }

            // 5. Full FTS5 + trigram + vocab refresh (including OCR text, generated title, tags, link titles)
            container.searchIndexer.indexNote(noteId)

            // 6. Optional neural embedding update when semantic re-ranking is enabled
            if (settings.effectiveSemanticRerankEnabled && combinedForEmbedding.isNotBlank()) {
                val hash = TextHashing.sha256Hex(combinedForEmbedding)
                val vec = precomputedNoteVec
                if (vec != null && vec.isNotEmpty()) {
                    container.aiEngine.savePrecomputedEmbeddingIfNeeded(noteId, hash, vec)
                } else {
                    container.aiEngine.updateNoteEmbeddingIfNeeded(noteId, hash, combinedForEmbedding)
                }
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

    private suspend fun buildUserTagContext(
        container: io.github.ultrapg.smartnote.di.AppContainer,
        excludeNoteId: String
    ): Pair<Map<String, Map<String, Float>>, Map<String, List<String>>> {
        val summaries = container.noteRepository.getAllSummariesSnapshot(includeArchived = true)
        val rawTokenCounts = LinkedHashMap<String, MutableMap<String, Int>>()
        val tagNoteIds = LinkedHashMap<String, MutableList<String>>()
        val tagDocCounts = HashMap<String, Int>()

        for (summary in summaries) {
            if (summary.id == excludeNoteId || summary.isDeleted) continue
            val userTags = summary.tags.filter { it.source == TagSource.USER }
            if (userTags.isEmpty()) continue

            val noteTokens = TextHashing.tokenizeWords("${summary.title} ${summary.previewText}")
                .map { SmartTagsEngine.foldToAsciiLower(it) }
                .filter { it.length >= 3 && it !in USER_PROFILE_STOPWORDS }
                .distinct()

            for (tag in userTags) {
                val ids = tagNoteIds.getOrPut(tag.name) { mutableListOf() }
                if (ids.size < 16) ids += summary.id
                tagDocCounts[tag.name] = (tagDocCounts[tag.name] ?: 0) + 1
                val counts = rawTokenCounts.getOrPut(tag.name) { mutableMapOf() }
                for (tok in noteTokens) {
                    counts[tok] = (counts[tok] ?: 0) + 1
                }
            }
        }

        val profiles = LinkedHashMap<String, Map<String, Float>>()
        for ((tagName, counts) in rawTokenCounts) {
            val docCount = (tagDocCounts[tagName] ?: 1).coerceAtLeast(1)
            val weighted = counts.entries
                .sortedByDescending { it.value }
                .take(12)
                .associate { (tok, count) ->
                    val salience = (1.0f + (count.toFloat() / docCount.toFloat())).coerceIn(1.0f, 2.5f)
                    tok to salience
                }
            if (weighted.isNotEmpty()) {
                profiles[tagName] = weighted
            }
        }
        return profiles to tagNoteIds
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"

        private val USER_PROFILE_STOPWORDS = setOf(
            "the", "and", "for", "with", "from", "that", "this", "have", "are", "was", "were",
            "you", "your", "not", "but", "can", "will", "just", "about", "into", "more", "some",
            "der", "die", "das", "und", "fuer", "mit", "von", "auf", "ein", "eine", "einen",
            "ist", "sind", "war", "nicht", "auch", "noch", "nur", "oder", "aber", "als", "bei",
            "note", "notes", "notiz", "notizen", "http", "https", "www", "com"
        )

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
