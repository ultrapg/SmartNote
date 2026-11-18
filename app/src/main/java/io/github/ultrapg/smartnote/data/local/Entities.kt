package io.github.ultrapg.smartnote.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["is_deleted", "is_archived", "is_pinned", "updated_at_ms"]),
        Index(value = ["content_sha256"]),
        Index(value = ["deleted_at_ms"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "plain_text_preview")
    val plainTextPreview: String,
    @ColumnInfo(name = "full_plain_text")
    val fullPlainText: String,
    @ColumnInfo(name = "word_count")
    val wordCount: Int,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean,
    @ColumnInfo(name = "deleted_at_ms")
    val deletedAtMs: Long? = null,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
    @ColumnInfo(name = "content_revision")
    val contentRevision: Long = 1L,
    @ColumnInfo(name = "has_checklist")
    val hasChecklist: Boolean = false,
    @ColumnInfo(name = "checklist_checked_count")
    val checklistCheckedCount: Int = 0,
    @ColumnInfo(name = "checklist_total_count")
    val checklistTotalCount: Int = 0,
    @ColumnInfo(name = "primary_attachment_id")
    val primaryAttachmentId: String? = null,
    @ColumnInfo(name = "content_sha256")
    val contentSha256: String = "",
    @ColumnInfo(name = "simhash64")
    val simhash64: Long = 0L,
    @ColumnInfo(name = "source_type")
    val sourceType: String = "MANUAL"
)

@Entity(
    tableName = "note_blocks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["note_id", "position"]),
        Index(value = ["attachment_id"])
    ]
)
data class NoteBlockEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    val position: Int,
    @ColumnInfo(name = "block_type")
    val blockType: String,
    @ColumnInfo(name = "text_content")
    val textContent: String = "",
    @ColumnInfo(name = "is_checked")
    val isChecked: Boolean = false,
    @ColumnInfo(name = "indent_level")
    val indentLevel: Int = 0,
    @ColumnInfo(name = "attachment_id")
    val attachmentId: String? = null,
    @ColumnInfo(name = "link_url")
    val linkUrl: String? = null
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["normalized_name"], unique = true)
    ]
)
data class TagEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "color_seed")
    val colorSeed: Int = 0,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long
)

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tag_id", "is_dismissed"]),
        Index(value = ["note_id", "is_dismissed"])
    ]
)
data class NoteTagCrossRefEntity(
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "tag_id")
    val tagId: String,
    val source: String,
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "is_dismissed")
    val isDismissed: Boolean = false,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["note_id"]),
        Index(value = ["sha256"]),
        Index(value = ["relative_file_path"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "relative_file_path")
    val relativeFilePath: String,
    @ColumnInfo(name = "thumbnail_relative_path")
    val thumbnailRelativePath: String? = null,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "original_display_name")
    val originalDisplayName: String,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    @ColumnInfo(name = "width_px")
    val widthPx: Int,
    @ColumnInfo(name = "height_px")
    val heightPx: Int,
    val sha256: String,
    @ColumnInfo(name = "is_screenshot")
    val isScreenshot: Boolean,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long
)

@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = AttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["status"])]
)
data class OcrResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "attachment_id")
    val attachmentId: String,
    @ColumnInfo(name = "recognized_text")
    val recognizedText: String,
    @ColumnInfo(name = "language_code")
    val languageCode: String = "la",
    @ColumnInfo(name = "avg_confidence")
    val avgConfidence: Float = 0f,
    val status: String,
    @ColumnInfo(name = "processed_at_ms")
    val processedAtMs: Long,
    @ColumnInfo(name = "engine_name")
    val engineName: String = "MLKit-Latin-v2-Bundled"
)

@Entity(
    tableName = "link_previews",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["note_id"]),
        Index(value = ["canonical_url"])
    ]
)
data class LinkPreviewEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "raw_url")
    val rawUrl: String,
    @ColumnInfo(name = "canonical_url")
    val canonicalUrl: String,
    val domain: String,
    @ColumnInfo(name = "page_title")
    val pageTitle: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "preview_image_path")
    val previewImagePath: String? = null,
    @ColumnInfo(name = "fetch_status")
    val fetchStatus: String = "LOCAL_ONLY",
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["note_id"], unique = true),
        Index(value = ["is_completed", "trigger_at_ms"])
    ]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "trigger_at_ms")
    val triggerAtMs: Long,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "local_date_time_iso")
    val localDateTimeIso: String,
    @ColumnInfo(name = "repeat_rule")
    val repeatRule: String = "NONE",
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at_ms")
    val completedAtMs: Long? = null,
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int = 0,
    @ColumnInfo(name = "use_live_update")
    val useLiveUpdate: Boolean = true
)

@Entity(
    tableName = "processing_jobs",
    primaryKeys = ["note_id", "job_type"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProcessingJobEntity(
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "job_type")
    val jobType: String,
    @ColumnInfo(name = "content_revision")
    val contentRevision: Long,
    val status: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey
    val query: String,
    @ColumnInfo(name = "searched_at_ms")
    val searchedAtMs: Long
)

@Entity(
    tableName = "search_vocab",
    indices = [Index(value = ["term_length"])]
)
data class SearchVocabEntity(
    @PrimaryKey
    val term: String,
    @ColumnInfo(name = "term_length")
    val termLength: Int,
    @ColumnInfo(name = "doc_freq")
    val docFreq: Int
)

@Entity(tableName = "dismissed_duplicates")
data class DismissedDuplicateEntity(
    @PrimaryKey
    @ColumnInfo(name = "pair_key")
    val pairKey: String,
    @ColumnInfo(name = "dismissed_at_ms")
    val dismissedAtMs: Long
)

@Entity(
    tableName = "note_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteEmbeddingEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "model_id")
    val modelId: String,
    @ColumnInfo(name = "content_sha256")
    val contentSha256: String,
    val dims: Int,
    @ColumnInfo(name = "vector_blob", typeAffinity = ColumnInfo.BLOB)
    val vectorBlob: ByteArray,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEmbeddingEntity) return false
        return noteId == other.noteId &&
            modelId == other.modelId &&
            contentSha256 == other.contentSha256 &&
            dims == other.dims &&
            vectorBlob.contentEquals(other.vectorBlob) &&
            updatedAtMs == other.updatedAtMs
    }

    override fun hashCode(): Int {
        var result = noteId.hashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + contentSha256.hashCode()
        result = 31 * result + dims
        result = 31 * result + vectorBlob.contentHashCode()
        result = 31 * result + updatedAtMs.hashCode()
        return result
    }
}
