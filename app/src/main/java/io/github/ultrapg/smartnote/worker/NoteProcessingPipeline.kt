package io.github.ultrapg.smartnote.worker

import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.ImageLabelingEngine
import io.github.ultrapg.smartnote.ai.SmartNoteAiEngine
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.ai.SystemEntityAnalyzer
import io.github.ultrapg.smartnote.data.local.OcrResultEntity
import io.github.ultrapg.smartnote.data.local.ProcessingJobEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.links.DetectedLink
import io.github.ultrapg.smartnote.links.LinkMetadataFetcher
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.ocr.SafeOcrBitmapLoader
import io.github.ultrapg.smartnote.ocr.SmartOcrCoordinator
import io.github.ultrapg.smartnote.search.SearchIndexer
import io.github.ultrapg.smartnote.tags.SmartTaggingCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class NoteProcessingOutcome {
    COMPLETED,
    SKIPPED_ALREADY_CURRENT,
    SKIPPED_SUPERSEDED,
    SKIPPED_UNAVAILABLE,
    FAILED
}

class NoteProcessingPipeline(
    private val database: SmartNoteDatabase,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
    private val attachmentStorage: AttachmentStorage,
    private val searchIndexer: SearchIndexer,
    private val smartOcrCoordinator: SmartOcrCoordinator,
    private val imageLabelingEngine: ImageLabelingEngine,
    private val systemEntityAnalyzer: SystemEntityAnalyzer,
    private val linkMetadataFetcher: LinkMetadataFetcher,
    private val smartTitleGenerator: SmartTitleGenerator,
    private val smartTaggingCoordinator: SmartTaggingCoordinator,
    private val aiEngine: SmartNoteAiEngine,
    private val applicationScope: CoroutineScope
) {
    private val noteMutexes = ConcurrentHashMap<String, Mutex>()

    fun launchImmediate(noteId: String) {
        applicationScope.launch {
            processNote(noteId = noteId, runAttemptCount = 0)
        }
    }

    suspend fun processNote(
        noteId: String,
        runAttemptCount: Int = 0
    ): NoteProcessingOutcome {
        val mutex = noteMutexes.computeIfAbsent(noteId) { Mutex() }
        return mutex.withLock {
            val detail = noteRepository.getNoteDetail(noteId)
                ?: return@withLock NoteProcessingOutcome.SKIPPED_UNAVAILABLE
            if (detail.isDeleted) return@withLock NoteProcessingOutcome.SKIPPED_UNAVAILABLE

            val settings = settingsRepository.settingsFlow.first()
            if (!settings.backgroundAnalysisEnabled) {
                searchIndexer.indexNote(noteId)
                return@withLock NoteProcessingOutcome.COMPLETED
            }

            val targetRevision = detail.contentRevision
            val maintenanceDao = database.searchAndMaintenanceDao()
            val existingJob = maintenanceDao.getJobsForNote(noteId)
                .firstOrNull { it.jobType == JOB_TYPE_FULL_PIPELINE }
            if (existingJob != null &&
                existingJob.status == STATUS_COMPLETED &&
                existingJob.contentRevision == targetRevision
            ) {
                return@withLock NoteProcessingOutcome.SKIPPED_ALREADY_CURRENT
            }

            maintenanceDao.upsertProcessingJob(
                ProcessingJobEntity(
                    noteId = noteId,
                    jobType = JOB_TYPE_FULL_PIPELINE,
                    contentRevision = targetRevision,
                    status = STATUS_RUNNING,
                    attemptCount = runAttemptCount + 1,
                    updatedAtMs = System.currentTimeMillis()
                )
            )

            try {
                // 1. Offline OCR for attachments (SHA-256 cached first)
                val ocrTexts = mutableListOf<String>()
                for (att in detail.attachments) {
                    val alreadyCompleted = att.ocrStatus == OcrStatus.COMPLETED || att.ocrStatus == OcrStatus.EMPTY
                    if (alreadyCompleted) {
                        att.ocrText?.takeIf { it.isNotBlank() }?.let(ocrTexts::add)
                        continue
                    }
                    if (!settings.effectiveOcrEnabled) continue

                    val cached = database.attachmentDao().findCachedOcrBySha256(att.sha256)
                        ?.takeIf { it.status == OcrStatus.COMPLETED.name || !settings.effectiveUseGeminiNanoForOcr }
                    if (cached != null) {
                        database.attachmentDao().upsertOcrResult(
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

                    val file = attachmentStorage.resolvePrivateFile(att.relativeFilePath)
                    val outcome = smartOcrCoordinator.recognizeFileWithOptionalAi(
                        file = file,
                        settings = settings,
                        forceAiAssist = false
                    )
                    val status = when {
                        !outcome.succeeded -> OcrStatus.FAILED
                        outcome.text.isBlank() -> OcrStatus.EMPTY
                        else -> OcrStatus.COMPLETED
                    }
                    database.attachmentDao().upsertOcrResult(
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
                val currentRevision = database.noteDao().getNoteById(noteId)?.contentRevision ?: targetRevision
                if (currentRevision != targetRevision) {
                    return@withLock NoteProcessingOutcome.SKIPPED_SUPERSEDED
                }

                // 2. Opt-in network Link Metadata Preview (strictly skipped when fetchLinkPreviews == false)
                if (settings.fetchLinkPreviews && detail.links.isNotEmpty()) {
                    val storedLinks = database.linkPreviewDao().getLinksForNote(noteId).associateBy { it.id }
                    val updatedLinks = detail.links.mapNotNull { link ->
                        if (link.fetchStatus == LinkFetchStatus.FETCHED) return@mapNotNull null
                        val meta = linkMetadataFetcher.fetchMetadataIfAllowed(link.rawUrl)
                        if (meta.blockedByPrivacySetting) return@mapNotNull null
                        val entity = storedLinks[link.id] ?: return@mapNotNull null
                        entity.copy(
                            pageTitle = meta.pageTitle ?: entity.pageTitle,
                            description = meta.description ?: entity.description,
                            previewImagePath = meta.localThumbnailPath ?: entity.previewImagePath,
                            fetchStatus = if (meta.succeeded) LinkFetchStatus.FETCHED.name else LinkFetchStatus.FAILED.name,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                    if (updatedLinks.isNotEmpty()) {
                        database.linkPreviewDao().upsertLinks(updatedLinks)
                    }
                }

                val refreshedLinks = noteRepository.getNoteDetail(noteId)?.links ?: detail.links
                val linkContextTexts = refreshedLinks.flatMap { link ->
                    listOfNotNull(
                        link.pageTitle?.trim()?.takeIf { it.isNotBlank() },
                        link.description?.trim()?.takeIf { it.isNotBlank() }
                    )
                }.distinct()

                // 3. ML Kit Image Labeling (auto-skipped if disabled or unavailable)
                val imageLabels = if (
                    settings.effectiveUseImageLabeling &&
                    detail.attachments.isNotEmpty() &&
                    imageLabelingEngine.checkAvailability() != ImageLabelingAvailability.UNAVAILABLE
                ) {
                    val mergedLabels = LinkedHashMap<String, DetectedImageLabel>()
                    for (att in detail.attachments.take(2)) {
                        val file = attachmentStorage.resolvePrivateFile(att.relativeFilePath)
                        imageLabelingEngine.labelImageFile(file).forEach { lbl ->
                            val key = lbl.text.lowercase(Locale.ROOT)
                            val prev = mergedLabels[key]
                            if (prev == null || lbl.confidence > prev.confidence) {
                                mergedLabels[key] = lbl
                            }
                        }
                    }
                    mergedLabels.values.sortedByDescending { it.confidence }.take(6)
                } else {
                    emptyList()
                }

                // 4. Android System TextClassifier entities (including link preview metadata)
                val combinedForEntities = buildString {
                    if (detail.title.isNotBlank()) append(detail.title).append('\n')
                    detail.blocks.forEach { if (it.textContent.isNotBlank()) append(it.textContent).append('\n') }
                    ocrTexts.forEach { if (it.isNotBlank()) append(it).append('\n') }
                    linkContextTexts.forEach { append(it).append('\n') }
                }.trim()
                val systemEntities = if (
                    settings.effectiveUseSystemEntities &&
                    combinedForEntities.isNotBlank() &&
                    systemEntityAnalyzer.checkAvailability() == SystemEntityAvailability.AVAILABLE
                ) {
                    systemEntityAnalyzer.extractEntities(combinedForEntities)
                } else {
                    emptyList()
                }

                // 5. Auto-title generation after OCR & link metadata when blank or generic share placeholder
                if (settings.effectiveAutoTitleEnabled && SmartTitleGenerator.isPlaceholderOrBlankTitle(detail.title)) {
                    val userProseChars = detail.blocks.sumOf { it.textContent.trim().length }
                    val primaryAttachment = detail.blocks
                        .firstOrNull { it.type == NoteBlockType.IMAGE }
                        ?.attachmentId
                        ?.let { attId -> detail.attachments.firstOrNull { it.id == attId } }
                        ?: detail.attachments.firstOrNull()
                    val titlePolicy = settings.effectiveGeminiNanoTitlesPolicy
                    val allowTitleNano = titlePolicy != GeminiNanoFeaturePolicy.OFF
                    val forceTitleNano = titlePolicy == GeminiNanoFeaturePolicy.FORCE
                    val primaryBitmap = if (
                        allowTitleNano &&
                        primaryAttachment != null &&
                        (forceTitleNano || userProseChars < 160)
                    ) {
                        SafeOcrBitmapLoader.decodeUprightDownsampledBitmap(
                            attachmentStorage.resolvePrivateFile(primaryAttachment.relativeFilePath),
                            maxDimPx = 768
                        )
                    } else {
                        null
                    }
                    val generatedTitle = try {
                        smartTitleGenerator.generateSmartTitle(
                            blocks = detail.blocks,
                            ocrTexts = ocrTexts,
                            links = refreshedLinks,
                            allowNeuralScoring = settings.aiFeaturesEnabled,
                            systemEntities = systemEntities,
                            imageLabels = imageLabels.map { it.text },
                            primaryImageBitmap = primaryBitmap,
                            allowGeminiNano = allowTitleNano,
                            forceGeminiNano = forceTitleNano,
                            preferFastGemini = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST,
                            maxTitleLength = settings.effectiveMaxTitleLength
                        )
                    } finally {
                        if (primaryBitmap != null && !primaryBitmap.isRecycled) {
                            primaryBitmap.recycle()
                        }
                    }
                    if (generatedTitle.isNotBlank()) {
                        noteRepository.applyGeneratedTitleIfUnmodified(
                            noteId = noteId,
                            expectedRevision = targetRevision,
                            generatedTitle = generatedTitle
                        )
                    }
                }

                // 6. SmartTags suggestion update (respecting user tags & dismissed tags)
                val currentTitleForTags = database.noteDao().getNoteById(noteId)?.title ?: detail.title
                val detectedLinks = buildList<DetectedLink> {
                    refreshedLinks.forEach { add(DetectedLink(it.rawUrl, it.canonicalUrl, it.domain)) }
                    detail.blocks.forEach { block ->
                        when (block.type) {
                            NoteBlockType.TEXT, NoteBlockType.CHECKLIST -> addAll(SmartLinkDetector.extractLinks(block.textContent))
                            NoteBlockType.LINK -> block.linkUrl?.let { SmartLinkDetector.parseSingleUrl(it)?.let(::add) }
                            NoteBlockType.IMAGE -> Unit
                        }
                    }
                }.distinctBy { it.canonicalUrl }

                val combinedForEmbedding = buildString {
                    if (currentTitleForTags.isNotBlank()) append(currentTitleForTags).append('\n')
                    detail.blocks.forEach { if (it.textContent.isNotBlank()) append(it.textContent).append('\n') }
                    ocrTexts.forEach { if (it.isNotBlank()) append(it).append('\n') }
                    linkContextTexts.forEach { append(it).append('\n') }
                }.trim()

                if (settings.effectiveSmartTagsEnabled) {
                    val existingUserVocab = tagRepository.getAllTagsSnapshot().map { it.name }
                    val existingNoteTags = detail.tags.mapTo(HashSet()) { it.name }
                    val dismissedNames = tagRepository.getDismissedTagNamesForNote(noteId)
                    val allSummaries = noteRepository.getAllSummariesSnapshot(includeArchived = true)
                    val tagOutcome = smartTaggingCoordinator.generateTagsForNote(
                        noteId = noteId,
                        title = currentTitleForTags,
                        blocks = detail.blocks,
                        ocrTexts = ocrTexts,
                        links = detectedLinks,
                        attachments = detail.attachments,
                        existingNoteTags = existingNoteTags,
                        dismissedTagNames = dismissedNames,
                        existingUserVocabulary = existingUserVocab,
                        allSummaries = allSummaries,
                        settings = settings,
                        isExplicitManualTrigger = false,
                        extraContextTexts = linkContextTexts
                    )
                    tagRepository.applySuggestedTags(
                        noteId = noteId,
                        suggestions = tagOutcome.suggestions.map { it.name to it.confidence },
                        maxTags = settings.effectiveMaxPredictedTags
                    )
                }

                // 7. Full FTS5 + trigram + vocab refresh (including OCR text, generated title, tags, link titles)
                searchIndexer.indexNote(noteId)

                // 8. Optional neural embedding update when semantic re-ranking is enabled
                if (settings.effectiveSemanticRerankEnabled && combinedForEmbedding.isNotBlank()) {
                    val hash = TextHashing.sha256Hex(combinedForEmbedding)
                    aiEngine.updateNoteEmbeddingIfNeeded(noteId, hash, combinedForEmbedding)
                }

                maintenanceDao.upsertProcessingJob(
                    ProcessingJobEntity(
                        noteId = noteId,
                        jobType = JOB_TYPE_FULL_PIPELINE,
                        contentRevision = targetRevision,
                        status = STATUS_COMPLETED,
                        attemptCount = runAttemptCount + 1,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                NoteProcessingOutcome.COMPLETED
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                maintenanceDao.upsertProcessingJob(
                    ProcessingJobEntity(
                        noteId = noteId,
                        jobType = JOB_TYPE_FULL_PIPELINE,
                        contentRevision = targetRevision,
                        status = STATUS_FAILED,
                        attemptCount = runAttemptCount + 1,
                        lastError = err.message,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                NoteProcessingOutcome.FAILED
            }
        }
    }

    private companion object {
        const val JOB_TYPE_FULL_PIPELINE = "FULL_PIPELINE"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }
}
