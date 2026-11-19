package io.github.ultrapg.smartnote.domain.model

import androidx.compose.runtime.Immutable

enum class NoteSourceType {
    MANUAL,
    SHARE_TEXT,
    SHARE_IMAGE,
    SHARE_URL,
    QUICK_TILE
}

enum class NoteBlockType {
    TEXT,
    CHECKLIST,
    IMAGE,
    LINK
}

enum class TagSource {
    USER,
    SUGGESTED
}

enum class OcrStatus {
    PENDING,
    COMPLETED,
    EMPTY,
    FAILED
}

enum class LinkFetchStatus {
    LOCAL_ONLY,
    FETCHED,
    FAILED
}

enum class NoteSortOrder {
    UPDATED_DESC,
    CREATED_DESC,
    TITLE_ASC
}

enum class NoteFilterType {
    ALL,
    PINNED,
    IMAGES,
    CHECKLISTS,
    LINKS
}

enum class NoteCreationOption {
    TEXT_NOTE,
    CHECKLIST,
    IMAGE
}

enum class ThemePalette {
    DYNAMIC,
    VIOLET,
    NEUTRAL,
    AMOLED
}

enum class DarkModeConfig {
    SYSTEM,
    LIGHT,
    DARK
}

enum class NotesLayoutMode {
    STAGGERED_CARDS,
    COMPACT_LIST
}

enum class AiBackendPreference {
    AUTO,
    PREFER_NPU,
    PREFER_GPU,
    CPU_ONLY
}

enum class AiHardwareBackend {
    NPU,
    GPU,
    CPU,
    NATIVE_MLKIT,
    DIRECT_CPU_LEXICAL,
    UNINITIALIZED
}

enum class TagPredictionSensitivity {
    CONSERVATIVE,
    BALANCED,
    BROAD
}

enum class GeminiNanoMode {
    AUTO_HYBRID,
    ALWAYS_PREFER,
    OFF
}

enum class GeminiNanoVariant {
    FAST_FIRST,
    FULL_QUALITY
}

enum class GeminiNanoFeaturePolicy {
    OFF,
    AUTO,
    FORCE
}

enum class AppLockTimeout(val timeoutMs: Long, val label: String) {
    IMMEDIATE(0L, "Immediately"),
    SECONDS_30(30_000L, "After 30 sec"),
    MINUTE_1(60_000L, "After 1 min"),
    MINUTES_5(300_000L, "After 5 min")
}

enum class TitleStylePreference {
    CONCISE,
    DESCRIPTIVE
}

@Immutable
data class AppSettings(
    val themePalette: ThemePalette = ThemePalette.DYNAMIC,
    val darkModeConfig: DarkModeConfig = DarkModeConfig.SYSTEM,
    val notesLayout: NotesLayoutMode = NotesLayoutMode.STAGGERED_CARDS,
    val reducedMotion: Boolean = false,
    val aiFeaturesEnabled: Boolean = true,
    val smartTagsEnabled: Boolean = true,
    val autoTitleEnabled: Boolean = false,
    val ocrEnabled: Boolean = true,
    val duplicateDetectionEnabled: Boolean = true,
    val fetchLinkPreviews: Boolean = false,
    val recentSearchesEnabled: Boolean = true,
    val semanticRerankEnabled: Boolean = true,
    val aiBackendPreference: AiBackendPreference = AiBackendPreference.AUTO,
    val energyEfficientAi: Boolean = true,
    val backgroundAnalysisEnabled: Boolean = true,
    val maxPredictedTags: Int = 3,
    val tagSensitivity: TagPredictionSensitivity = TagPredictionSensitivity.BALANCED,
    val allowNewTopicTags: Boolean = true,
    val preferExistingTags: Boolean = true,
    val learnFromUserTags: Boolean = true,
    val useImageLabelingForAi: Boolean = true,
    val useSystemEntitiesForAi: Boolean = true,
    val useSemanticEmbeddingsForTags: Boolean = true,
    val geminiNanoMode: GeminiNanoMode = GeminiNanoMode.AUTO_HYBRID,
    val geminiNanoVariant: GeminiNanoVariant = GeminiNanoVariant.FAST_FIRST,
    val useGeminiNanoForTitles: Boolean = true,
    val useGeminiNanoForOcr: Boolean = true,
    val useGeminiNanoForSearch: Boolean = true,
    val geminiNanoTagsPolicy: GeminiNanoFeaturePolicy = GeminiNanoFeaturePolicy.AUTO,
    val geminiNanoTitlesPolicy: GeminiNanoFeaturePolicy = GeminiNanoFeaturePolicy.AUTO,
    val geminiNanoSearchPolicy: GeminiNanoFeaturePolicy = GeminiNanoFeaturePolicy.AUTO,
    val geminiNanoOcrPolicy: GeminiNanoFeaturePolicy = GeminiNanoFeaturePolicy.AUTO,
    val titleStyle: TitleStylePreference = TitleStylePreference.CONCISE,
    val appLockEnabled: Boolean = false,
    val appLockTimeout: AppLockTimeout = AppLockTimeout.IMMEDIATE,
    val hideRecentsWhenLocked: Boolean = true
) {
    val effectiveSmartTagsEnabled: Boolean get() = aiFeaturesEnabled && smartTagsEnabled
    val effectiveAutoTitleEnabled: Boolean get() = aiFeaturesEnabled && autoTitleEnabled
    val effectiveOcrEnabled: Boolean get() = aiFeaturesEnabled && ocrEnabled
    val effectiveDuplicateDetectionEnabled: Boolean get() = aiFeaturesEnabled && duplicateDetectionEnabled
    val effectiveSemanticRerankEnabled: Boolean get() = aiFeaturesEnabled && semanticRerankEnabled
    val effectiveMaxPredictedTags: Int get() = maxPredictedTags.coerceIn(1, 6)
    val effectiveUseImageLabeling: Boolean get() = aiFeaturesEnabled && useImageLabelingForAi
    val effectiveUseSystemEntities: Boolean get() = aiFeaturesEnabled && useSystemEntitiesForAi
    val effectiveUseSemanticEmbeddingsForTags: Boolean get() = aiFeaturesEnabled && useSemanticEmbeddingsForTags
    val effectiveGeminiNanoTagsPolicy: GeminiNanoFeaturePolicy
        get() = when {
            !aiFeaturesEnabled || geminiNanoMode == GeminiNanoMode.OFF -> GeminiNanoFeaturePolicy.OFF
            geminiNanoTagsPolicy != GeminiNanoFeaturePolicy.AUTO -> geminiNanoTagsPolicy
            geminiNanoMode == GeminiNanoMode.ALWAYS_PREFER -> GeminiNanoFeaturePolicy.FORCE
            else -> GeminiNanoFeaturePolicy.AUTO
        }
    val effectiveGeminiNanoTitlesPolicy: GeminiNanoFeaturePolicy
        get() = when {
            !aiFeaturesEnabled || geminiNanoMode == GeminiNanoMode.OFF || !useGeminiNanoForTitles ->
                GeminiNanoFeaturePolicy.OFF
            else -> geminiNanoTitlesPolicy
        }
    val effectiveGeminiNanoSearchPolicy: GeminiNanoFeaturePolicy
        get() = when {
            !aiFeaturesEnabled || geminiNanoMode == GeminiNanoMode.OFF || !useGeminiNanoForSearch ->
                GeminiNanoFeaturePolicy.OFF
            else -> geminiNanoSearchPolicy
        }
    val effectiveGeminiNanoOcrPolicy: GeminiNanoFeaturePolicy
        get() = when {
            !effectiveOcrEnabled || geminiNanoMode == GeminiNanoMode.OFF || !useGeminiNanoForOcr ->
                GeminiNanoFeaturePolicy.OFF
            geminiNanoOcrPolicy != GeminiNanoFeaturePolicy.AUTO -> geminiNanoOcrPolicy
            geminiNanoMode == GeminiNanoMode.ALWAYS_PREFER -> GeminiNanoFeaturePolicy.FORCE
            else -> GeminiNanoFeaturePolicy.AUTO
        }
    val effectiveGeminiNanoEnabled: Boolean
        get() = aiFeaturesEnabled && (
            effectiveGeminiNanoTagsPolicy != GeminiNanoFeaturePolicy.OFF ||
                effectiveGeminiNanoTitlesPolicy != GeminiNanoFeaturePolicy.OFF ||
                effectiveGeminiNanoSearchPolicy != GeminiNanoFeaturePolicy.OFF ||
                effectiveGeminiNanoOcrPolicy != GeminiNanoFeaturePolicy.OFF
            )
    val effectiveUseGeminiNanoForTitles: Boolean
        get() = effectiveGeminiNanoTitlesPolicy != GeminiNanoFeaturePolicy.OFF
    val effectiveUseGeminiNanoForOcr: Boolean
        get() = effectiveGeminiNanoOcrPolicy != GeminiNanoFeaturePolicy.OFF
    val effectiveUseGeminiNanoForSearch: Boolean
        get() = effectiveGeminiNanoSearchPolicy != GeminiNanoFeaturePolicy.OFF
    val effectiveMaxTitleLength: Int get() = if (titleStyle == TitleStylePreference.DESCRIPTIVE) 64 else 44
}

@Immutable
data class ChecklistPreviewItem(
    val id: String,
    val text: String,
    val isChecked: Boolean
)

@Immutable
data class NoteTag(
    val tagId: String,
    val name: String,
    val source: TagSource,
    val confidence: Float,
    val colorSeed: Int = 0
)

@Immutable
data class TagInfo(
    val id: String,
    val name: String,
    val colorSeed: Int,
    val noteCount: Int,
    val suggestedCount: Int,
    val createdAtMs: Long
)

@Immutable
data class AttachmentInfo(
    val id: String,
    val noteId: String,
    val relativeFilePath: String,
    val thumbnailRelativePath: String?,
    val mimeType: String,
    val originalDisplayName: String,
    val byteSize: Long,
    val widthPx: Int,
    val heightPx: Int,
    val sha256: String,
    val isScreenshot: Boolean,
    val createdAtMs: Long,
    val ocrText: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.PENDING
)

@Immutable
data class LinkPreviewInfo(
    val id: String,
    val noteId: String,
    val rawUrl: String,
    val canonicalUrl: String,
    val domain: String,
    val pageTitle: String? = null,
    val description: String? = null,
    val previewImagePath: String? = null,
    val fetchStatus: LinkFetchStatus = LinkFetchStatus.LOCAL_ONLY,
    val updatedAtMs: Long
)

@Immutable
data class NoteBlock(
    val id: String,
    val noteId: String,
    val position: Int,
    val type: NoteBlockType,
    val textContent: String = "",
    val isChecked: Boolean = false,
    val indentLevel: Int = 0,
    val attachmentId: String? = null,
    val linkUrl: String? = null
)

@Immutable
data class NoteSummary(
    val id: String,
    val title: String,
    val previewText: String,
    val wordCount: Int,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val deletedAtMs: Long?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val hasChecklist: Boolean,
    val checklistCheckedCount: Int,
    val checklistTotalCount: Int,
    val checklistPreview: List<ChecklistPreviewItem>,
    val primaryThumbnailPath: String?,
    val imageCount: Int,
    val tags: List<NoteTag>,
    val hasLink: Boolean,
    val primaryLinkDomain: String?,
    val ocrSnippet: String? = null,
    val sourceType: NoteSourceType = NoteSourceType.MANUAL
) {
    val isBlankUntouchedGhost: Boolean
        get() = !isPinned && !isArchived && !isDeleted &&
            title.isBlank() && previewText.isBlank() &&
            checklistPreview.isEmpty() && imageCount == 0 &&
            !hasLink && tags.isEmpty()
}

@Immutable
data class NoteDetail(
    val id: String,
    val title: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val deletedAtMs: Long?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val contentRevision: Long,
    val sourceType: NoteSourceType,
    val blocks: List<NoteBlock>,
    val tags: List<NoteTag>,
    val attachments: List<AttachmentInfo>,
    val links: List<LinkPreviewInfo>
)

@Immutable
data class DuplicatePairInfo(
    val pairKey: String,
    val noteA: NoteSummary,
    val noteB: NoteSummary,
    val matchReason: String,
    val similarityScore: Float
)

@Immutable
data class StorageStats(
    val noteCount: Int,
    val archivedCount: Int,
    val recycleBinCount: Int,
    val attachmentCount: Int,
    val attachmentsBytes: Long,
    val thumbnailsBytes: Long,
    val databaseBytes: Long
)
