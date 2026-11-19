package io.github.ultrapg.smartnote.tags

import io.github.ultrapg.smartnote.ai.ClassifiedTextEntity
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.GeminiNanoCapabilities
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.ImageLabelingEngine
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.ai.SmartNoteAiEngine
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.ai.SystemEntityAnalyzer
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.AttachmentInfo
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.links.DetectedLink
import io.github.ultrapg.smartnote.ocr.SafeOcrBitmapLoader
import java.util.Locale

data class TagGenerationOutcome(
    val suggestions: List<SmartTagSuggestion>,
    val usedGeminiNano: Boolean = false,
    val usedGeminiFast: Boolean = false
)

class SmartTaggingCoordinator(
    private val smartTagsEngine: SmartTagsEngine,
    private val aiEngine: SmartNoteAiEngine? = null,
    private val systemEntityAnalyzer: SystemEntityAnalyzer? = null,
    private val imageLabelingEngine: ImageLabelingEngine? = null,
    private val geminiNanoAssistant: OnDeviceGenAiAssistant? = null,
    private val attachmentStorage: AttachmentStorage? = null
) {
    suspend fun generateTagsForNote(
        noteId: String,
        title: String,
        blocks: List<NoteBlock>,
        ocrTexts: List<String>,
        links: List<DetectedLink>,
        attachments: List<AttachmentInfo>,
        existingNoteTags: Set<String>,
        dismissedTagNames: Set<String>,
        existingUserVocabulary: List<String>,
        allSummaries: List<NoteSummary>,
        settings: AppSettings,
        isExplicitManualTrigger: Boolean
    ): TagGenerationOutcome {
        if (!settings.aiFeaturesEnabled) return TagGenerationOutcome(emptyList())

        val maxTags = settings.effectiveMaxPredictedTags
        val minConf = SmartTagsEngine.minConfidenceFor(settings.tagSensitivity)
        val existingLower = existingNoteTags.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        val dismissedLower = dismissedTagNames.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        val excludedLower = existingLower + dismissedLower

        val combinedText = buildString {
            if (title.isNotBlank()) append(title).append('\n')
            blocks.forEach { if (it.textContent.isNotBlank()) append(it.textContent).append('\n') }
            ocrTexts.forEach { if (it.isNotBlank()) append(it).append('\n') }
        }.trim()

        val noteContentLen = title.trim().length +
            blocks.sumOf { it.textContent.trim().length } +
            ocrTexts.sumOf { it.trim().length }
        val hasImages = attachments.isNotEmpty()
        val hasScreenshot = attachments.any { it.isScreenshot }

        // 1. Optional ML Kit Image Labeling (auto-skipped if disabled or unavailable)
        val labeler = imageLabelingEngine
        val storage = attachmentStorage
        val imageLabels: List<DetectedImageLabel> = if (
            settings.effectiveUseImageLabeling &&
            hasImages &&
            labeler != null &&
            storage != null
        ) {
            val availability = labeler.checkAvailability()
            if (availability != ImageLabelingAvailability.UNAVAILABLE) {
                val merged = LinkedHashMap<String, DetectedImageLabel>()
                for (att in attachments.take(2)) {
                    val file = storage.resolvePrivateFile(att.relativeFilePath)
                    labeler.labelImageFile(file).forEach { lbl ->
                        val key = lbl.text.lowercase(Locale.ROOT)
                        val prev = merged[key]
                        if (prev == null || lbl.confidence > prev.confidence) {
                            merged[key] = lbl
                        }
                    }
                }
                merged.values.sortedByDescending { it.confidence }.take(6)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        // 2. Optional Android System TextClassifier entities (auto-skipped if disabled or unavailable)
        val analyzer = systemEntityAnalyzer
        val systemEntities: List<ClassifiedTextEntity> = if (
            settings.effectiveUseSystemEntities &&
            combinedText.isNotBlank() &&
            analyzer != null &&
            analyzer.checkAvailability() == SystemEntityAvailability.AVAILABLE
        ) {
            analyzer.extractEntities(combinedText)
        } else {
            emptyList()
        }

        // 3. Personal tag profile context (learned strictly from user-confirmed tags when enabled)
        val (userTagProfiles, userTagNoteIds) = if (settings.learnFromUserTags) {
            SmartTagsEngine.buildUserTagContext(
                summaries = allSummaries,
                excludeNoteId = noteId
            )
        } else {
            emptyMap<String, Map<String, Float>>() to emptyMap()
        }

        // 4. Optional semantic embedding affinities
        val engine = aiEngine
        val semanticScores = if (
            settings.effectiveUseSemanticEmbeddingsForTags &&
            combinedText.isNotBlank() &&
            engine != null
        ) {
            runCatching {
                engine.computeEmbeddingAndTagAffinities(
                    noteText = combinedText,
                    categoryPrototypes = smartTagsEngine.categoryPrototypes(),
                    userTagNoteIds = userTagNoteIds
                ).second
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val engineRequestLimit = (maxTags + excludedLower.size).coerceIn(maxTags, 10)

        // 5. High-precision local pass (without noisy open-vocabulary Stage 4)
        val localRaw = smartTagsEngine.suggestTags(
            title = title,
            blocks = blocks,
            ocrTexts = ocrTexts,
            links = links,
            hasScreenshot = hasScreenshot,
            existingUserVocabulary = existingUserVocabulary,
            semanticTagScores = semanticScores,
            userTagProfiles = userTagProfiles,
            systemEntities = systemEntities,
            imageLabels = imageLabels,
            includeTopicKeywords = false,
            maxTags = engineRequestLimit,
            minConfidence = minConf,
            preferExistingTags = settings.preferExistingTags
        )
        val localFiltered = localRaw.filter { it.name.lowercase(Locale.ROOT) !in excludedLower }

        // 6. Hybrid Gemini Nano (Fast or Full) when supported & needed
        val assistant = geminiNanoAssistant
        val nanoCaps = if (settings.effectiveGeminiNanoEnabled && assistant != null) {
            assistant.probeCapabilities(forceRefresh = false)
        } else {
            GeminiNanoCapabilities()
        }
        val preferFast = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST
        val nanoReady = nanoCaps.fastAvailability == GeminiNanoAvailability.AVAILABLE ||
            nanoCaps.fullAvailability == GeminiNanoAvailability.AVAILABLE
        val willUseFast = when {
            !nanoReady -> false
            preferFast -> nanoCaps.fastAvailability == GeminiNanoAvailability.AVAILABLE
            else -> nanoCaps.fullAvailability != GeminiNanoAvailability.AVAILABLE &&
                nanoCaps.fastAvailability == GeminiNanoAvailability.AVAILABLE
        }

        if (!nanoReady &&
            settings.effectiveGeminiNanoEnabled &&
            isExplicitManualTrigger &&
            nanoCaps.bestAvailability == GeminiNanoAvailability.DOWNLOADABLE
        ) {
            assistant?.triggerBackgroundDownloadIfNeeded(preferFast)
        }

        val isLocalUnsure = SmartTagsEngine.isLocalResultUnsure(
            suggestions = localFiltered,
            targetCount = maxTags,
            noteContentLength = noteContentLen,
            hasImages = hasImages
        )
        val shouldInvokeNano = nanoReady && assistant != null && when (settings.geminiNanoMode) {
            GeminiNanoMode.OFF -> false
            GeminiNanoMode.ALWAYS_PREFER -> true
            GeminiNanoMode.AUTO_HYBRID -> isLocalUnsure
        }

        val nanoSuggestions = if (shouldInvokeNano) {
            val sparseText = blocks.sumOf { it.textContent.trim().length } < 24 && ocrTexts.all { it.isBlank() }
            val primaryBitmap = if (hasImages && sparseText && storage != null) {
                SafeOcrBitmapLoader.decodeDownsampledBitmap(
                    storage.resolvePrivateFile(attachments.first().relativeFilePath),
                    maxDimPx = 512
                )
            } else {
                null
            }
            try {
                assistant.generateTags(
                    title = title,
                    noteText = blocks.joinToString("\n") { it.textContent },
                    ocrTexts = ocrTexts,
                    imageLabels = imageLabels.map { it.text },
                    existingVocabulary = existingUserVocabulary,
                    existingNoteTags = excludedLower,
                    maxTags = maxTags,
                    preferFast = preferFast,
                    allowNewTopicTags = settings.allowNewTopicTags,
                    primaryImageBitmap = primaryBitmap
                ).filter { it.name.lowercase(Locale.ROOT) !in excludedLower }
            } finally {
                if (primaryBitmap != null && !primaryBitmap.isRecycled) {
                    primaryBitmap.recycle()
                }
            }
        } else {
            emptyList()
        }

        // 7. Merge local + Gemini Nano cleanly (boost dual-agreed tags)
        val mergedByLower = LinkedHashMap<String, SmartTagSuggestion>()
        for (local in localFiltered) {
            mergedByLower[local.name.lowercase(Locale.ROOT)] = local
        }
        for (nano in nanoSuggestions) {
            val key = nano.name.lowercase(Locale.ROOT)
            val existing = mergedByLower[key]
            if (existing != null) {
                mergedByLower[key] = SmartTagSuggestion(
                    name = existing.name,
                    confidence = (maxOf(existing.confidence, nano.confidence) + 0.08f).coerceAtMost(0.99f),
                    reason = "Verified by ${nano.reason}"
                )
            } else {
                mergedByLower[key] = nano
            }
        }

        var combinedList = mergedByLower.values
            .sortedByDescending { it.confidence }
            .take(maxTags)

        // 8. Strict Stage 4 open-vocabulary fallback ONLY when user opted into new topics,
        // manually pressed Generate tags, and neither local rules nor Gemini Nano found any tag
        if (combinedList.isEmpty() && isExplicitManualTrigger && settings.allowNewTopicTags) {
            combinedList = smartTagsEngine.suggestTags(
                title = title,
                blocks = blocks,
                ocrTexts = ocrTexts,
                links = links,
                hasScreenshot = hasScreenshot,
                existingUserVocabulary = existingUserVocabulary,
                semanticTagScores = semanticScores,
                userTagProfiles = userTagProfiles,
                systemEntities = systemEntities,
                imageLabels = imageLabels,
                includeTopicKeywords = true,
                maxTags = engineRequestLimit,
                minConfidence = minConf,
                preferExistingTags = settings.preferExistingTags
            ).filter { it.name.lowercase(Locale.ROOT) !in excludedLower }
                .take(maxTags)
        }

        return TagGenerationOutcome(
            suggestions = combinedList,
            usedGeminiNano = shouldInvokeNano,
            usedGeminiFast = shouldInvokeNano && willUseFast
        )
    }
}
