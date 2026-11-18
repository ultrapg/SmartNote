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

enum class RepeatRule {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    WEEKDAYS
}

enum class NoteSortOrder {
    UPDATED_DESC,
    CREATED_DESC,
    TITLE_ASC
}

enum class NoteFilterType {
    ALL,
    PINNED,
    REMINDERS,
    IMAGES,
    CHECKLISTS,
    LINKS
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

@Immutable
data class AppSettings(
    val themePalette: ThemePalette = ThemePalette.DYNAMIC,
    val darkModeConfig: DarkModeConfig = DarkModeConfig.SYSTEM,
    val notesLayout: NotesLayoutMode = NotesLayoutMode.STAGGERED_CARDS,
    val reducedMotion: Boolean = false,
    val aiFeaturesEnabled: Boolean = true,
    val smartTagsEnabled: Boolean = true,
    val autoTitleEnabled: Boolean = true,
    val ocrEnabled: Boolean = true,
    val duplicateDetectionEnabled: Boolean = true,
    val fetchLinkPreviews: Boolean = false,
    val recentSearchesEnabled: Boolean = true,
    val semanticRerankEnabled: Boolean = false,
    val aiBackendPreference: AiBackendPreference = AiBackendPreference.AUTO,
    val energyEfficientAi: Boolean = true,
    val backgroundAnalysisEnabled: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val liveUpdateRemindersEnabled: Boolean = true
) {
    val effectiveSmartTagsEnabled: Boolean get() = aiFeaturesEnabled && smartTagsEnabled
    val effectiveAutoTitleEnabled: Boolean get() = aiFeaturesEnabled && autoTitleEnabled
    val effectiveOcrEnabled: Boolean get() = aiFeaturesEnabled && ocrEnabled
    val effectiveDuplicateDetectionEnabled: Boolean get() = aiFeaturesEnabled && duplicateDetectionEnabled
    val effectiveSemanticRerankEnabled: Boolean get() = aiFeaturesEnabled && semanticRerankEnabled
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
data class ReminderInfo(
    val id: String,
    val noteId: String,
    val triggerAtMs: Long,
    val zoneId: String,
    val localDateTimeIso: String,
    val repeatRule: RepeatRule,
    val isCompleted: Boolean,
    val completedAtMs: Long? = null,
    val snoozeCount: Int = 0,
    val useLiveUpdate: Boolean = true
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
    val reminder: ReminderInfo?,
    val hasLink: Boolean,
    val primaryLinkDomain: String?,
    val ocrSnippet: String? = null,
    val sourceType: NoteSourceType = NoteSourceType.MANUAL
)

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
    val links: List<LinkPreviewInfo>,
    val reminder: ReminderInfo?
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
