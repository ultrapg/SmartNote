package io.github.ultrapg.smartnote.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class VocabTermRow(
    val term: String,
    @ColumnInfo(name = "doc_freq")
    val docFreq: Int
)

data class NoteTagWithInfo(
    val noteId: String,
    val tagId: String,
    val tagName: String,
    val source: String,
    val confidence: Float,
    val isDismissed: Boolean,
    val colorSeed: Int
)

data class TagSummaryRow(
    val id: String,
    val name: String,
    val normalizedName: String,
    val colorSeed: Int,
    val createdAtMs: Long,
    val noteCount: Int,
    val suggestedCount: Int
)

data class AttachmentWithOcrRow(
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "relative_file_path")
    val relativeFilePath: String,
    @ColumnInfo(name = "thumbnail_relative_path")
    val thumbnailRelativePath: String?,
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
    val createdAtMs: Long,
    val recognizedText: String?,
    val ocrStatus: String?
)

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE is_deleted = 0 AND is_archived = 0
        ORDER BY is_pinned DESC, updated_at_ms DESC
        """
    )
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE is_deleted = 0 AND is_archived = 1
        ORDER BY updated_at_ms DESC
        """
    )
    fun observeArchivedNotes(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE is_deleted = 1
        ORDER BY deleted_at_ms DESC, updated_at_ms DESC
        """
    )
    fun observeDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    fun observeNoteById(noteId: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE is_deleted = 0")
    suspend fun getAllNonDeletedNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSnapshot(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE is_deleted = 0 AND is_archived = 0 AND content_sha256 = :sha256 AND id != :excludeId LIMIT 20")
    suspend fun findByContentSha256(sha256: String, excludeId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE is_deleted = 0 AND is_archived = 0")
    suspend fun getAllActiveNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE is_deleted = 1 AND deleted_at_ms IS NOT NULL AND deleted_at_ms < :cutoffMs")
    suspend fun getExpiredDeletedNotes(cutoffMs: Long): List<NoteEntity>

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Upsert
    suspend fun upsertNotes(notes: List<NoteEntity>)

    @Query("UPDATE notes SET is_pinned = :pinned, updated_at_ms = :updatedAtMs WHERE id = :noteId")
    suspend fun setPinned(noteId: String, pinned: Boolean, updatedAtMs: Long)

    @Query("UPDATE notes SET is_archived = :archived, is_pinned = CASE WHEN :archived = 1 THEN 0 ELSE is_pinned END, updated_at_ms = :updatedAtMs WHERE id = :noteId")
    suspend fun setArchived(noteId: String, archived: Boolean, updatedAtMs: Long)

    @Query("UPDATE notes SET is_deleted = 1, is_pinned = 0, deleted_at_ms = :deletedAtMs, updated_at_ms = :deletedAtMs WHERE id = :noteId")
    suspend fun moveToRecycleBin(noteId: String, deletedAtMs: Long)

    @Query("UPDATE notes SET is_deleted = 0, deleted_at_ms = NULL, updated_at_ms = :updatedAtMs WHERE id = :noteId")
    suspend fun restoreFromRecycleBin(noteId: String, updatedAtMs: Long)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNotePermanently(noteId: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("SELECT COUNT(*) FROM notes WHERE is_deleted = 0 AND is_archived = 0")
    suspend fun countActiveNotes(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE is_deleted = 0 AND is_archived = 1")
    suspend fun countArchivedNotes(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE is_deleted = 1")
    suspend fun countDeletedNotes(): Int
}

@Dao
interface NoteBlockDao {
    @Query("SELECT * FROM note_blocks WHERE note_id = :noteId ORDER BY position ASC")
    fun observeBlocksForNote(noteId: String): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE note_id = :noteId ORDER BY position ASC")
    suspend fun getBlocksForNote(noteId: String): List<NoteBlockEntity>

    @Query("SELECT * FROM note_blocks WHERE note_id IN (:noteIds) ORDER BY position ASC")
    suspend fun getBlocksForNotes(noteIds: List<String>): List<NoteBlockEntity>

    @Query("SELECT * FROM note_blocks ORDER BY note_id, position ASC")
    fun observeAllBlocks(): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks ORDER BY note_id, position ASC")
    suspend fun getAllBlocksSnapshot(): List<NoteBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<NoteBlockEntity>)

    @Query("DELETE FROM note_blocks WHERE note_id = :noteId")
    suspend fun deleteBlocksForNote(noteId: String)

    @Transaction
    suspend fun replaceBlocksForNote(noteId: String, blocks: List<NoteBlockEntity>) {
        deleteBlocksForNote(noteId)
        if (blocks.isNotEmpty()) {
            insertBlocks(blocks)
        }
    }
}

@Dao
interface TagDao {
    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.normalized_name AS normalizedName,
               t.color_seed AS colorSeed, t.created_at_ms AS createdAtMs,
               COALESCE(SUM(CASE WHEN n.id IS NOT NULL AND n.is_deleted = 0 AND r.is_dismissed = 0 THEN 1 ELSE 0 END), 0) AS noteCount,
               COALESCE(SUM(CASE WHEN n.id IS NOT NULL AND n.is_deleted = 0 AND r.is_dismissed = 0 AND r.source = 'SUGGESTED' THEN 1 ELSE 0 END), 0) AS suggestedCount
        FROM tags t
        LEFT JOIN note_tag_cross_ref r ON t.id = r.tag_id
        LEFT JOIN notes n ON r.note_id = n.id
        GROUP BY t.id
        ORDER BY noteCount DESC, t.name COLLATE NOCASE ASC
        """
    )
    fun observeAllTagsWithCounts(): Flow<List<TagSummaryRow>>

    @Query(
        """
        SELECT r.note_id AS noteId, t.id AS tagId, t.name AS tagName,
               r.source AS source, r.confidence AS confidence, r.is_dismissed AS isDismissed,
               t.color_seed AS colorSeed
        FROM note_tag_cross_ref r
        INNER JOIN tags t ON r.tag_id = t.id
        WHERE r.is_dismissed = 0
        ORDER BY CASE WHEN r.source = 'USER' THEN 0 ELSE 1 END, r.confidence DESC, t.name COLLATE NOCASE ASC
        """
    )
    fun observeAllActiveNoteTags(): Flow<List<NoteTagWithInfo>>

    @Query(
        """
        SELECT r.note_id AS noteId, t.id AS tagId, t.name AS tagName,
               r.source AS source, r.confidence AS confidence, r.is_dismissed AS isDismissed,
               t.color_seed AS colorSeed
        FROM note_tag_cross_ref r
        INNER JOIN tags t ON r.tag_id = t.id
        WHERE r.note_id = :noteId AND r.is_dismissed = 0
        ORDER BY CASE WHEN r.source = 'USER' THEN 0 ELSE 1 END, r.confidence DESC, t.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForNote(noteId: String): Flow<List<NoteTagWithInfo>>

    @Query(
        """
        SELECT r.note_id AS noteId, t.id AS tagId, t.name AS tagName,
               r.source AS source, r.confidence AS confidence, r.is_dismissed AS isDismissed,
               t.color_seed AS colorSeed
        FROM note_tag_cross_ref r
        INNER JOIN tags t ON r.tag_id = t.id
        WHERE r.note_id = :noteId AND r.is_dismissed = 0
        """
    )
    suspend fun getActiveTagsForNote(noteId: String): List<NoteTagWithInfo>

    @Query("SELECT * FROM note_tag_cross_ref WHERE note_id = :noteId")
    suspend fun getAllCrossRefsForNote(noteId: String): List<NoteTagCrossRefEntity>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllTagsSnapshot(): List<TagEntity>

    @Query("SELECT * FROM note_tag_cross_ref")
    suspend fun getAllCrossRefsSnapshot(): List<NoteTagCrossRefEntity>

    @Query("SELECT * FROM tags WHERE normalized_name = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getTagById(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagIfAbsent(tag: TagEntity): Long

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Upsert
    suspend fun upsertTags(tags: List<TagEntity>)

    @Upsert
    suspend fun upsertCrossRef(ref: NoteTagCrossRefEntity)

    @Upsert
    suspend fun upsertCrossRefs(refs: List<NoteTagCrossRefEntity>)

    @Query("UPDATE tags SET name = :newName, normalized_name = :normalizedName WHERE id = :tagId")
    suspend fun renameTag(tagId: String, newName: String, normalizedName: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: String)

    @Query("DELETE FROM note_tag_cross_ref WHERE note_id = :noteId AND tag_id = :tagId")
    suspend fun deleteCrossRef(noteId: String, tagId: String)

    @Query("UPDATE note_tag_cross_ref SET is_dismissed = 1, updated_at_ms = :nowMs WHERE note_id = :noteId AND tag_id = :tagId")
    suspend fun dismissCrossRef(noteId: String, tagId: String, nowMs: Long)

    @Query("DELETE FROM note_tag_cross_ref WHERE note_id = :noteId AND source = 'SUGGESTED' AND is_dismissed = 0")
    suspend fun clearPendingSuggestions(noteId: String)

    @Query("SELECT note_id FROM note_tag_cross_ref WHERE tag_id = :tagId AND is_dismissed = 0")
    suspend fun getNoteIdsForTag(tagId: String): List<String>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()
}

@Dao
interface AttachmentDao {
    @Query(
        """
        SELECT a.*, o.recognized_text AS recognizedText, o.status AS ocrStatus
        FROM attachments a
        LEFT JOIN ocr_results o ON a.id = o.attachment_id
        ORDER BY a.created_at_ms ASC
        """
    )
    fun observeAllAttachmentsWithOcr(): Flow<List<AttachmentWithOcrRow>>

    @Query(
        """
        SELECT a.*, o.recognized_text AS recognizedText, o.status AS ocrStatus
        FROM attachments a
        LEFT JOIN ocr_results o ON a.id = o.attachment_id
        WHERE a.note_id = :noteId
        ORDER BY a.created_at_ms ASC
        """
    )
    fun observeAttachmentsForNote(noteId: String): Flow<List<AttachmentWithOcrRow>>

    @Query(
        """
        SELECT a.*, o.recognized_text AS recognizedText, o.status AS ocrStatus
        FROM attachments a
        LEFT JOIN ocr_results o ON a.id = o.attachment_id
        WHERE a.note_id = :noteId
        ORDER BY a.created_at_ms ASC
        """
    )
    suspend fun getAttachmentsForNote(noteId: String): List<AttachmentWithOcrRow>

    @Query("SELECT * FROM attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun getAttachmentById(attachmentId: String): AttachmentEntity?

    @Query("SELECT * FROM attachments")
    suspend fun getAllAttachmentsSnapshot(): List<AttachmentEntity>

    @Query("SELECT * FROM ocr_results")
    suspend fun getAllOcrResultsSnapshot(): List<OcrResultEntity>

    @Query(
        """
        SELECT o.* FROM ocr_results o
        INNER JOIN attachments a ON o.attachment_id = a.id
        WHERE a.sha256 = :sha256 AND o.status IN ('COMPLETED', 'EMPTY')
        LIMIT 1
        """
    )
    suspend fun findCachedOcrBySha256(sha256: String): OcrResultEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE relative_file_path = :relativePath AND note_id != :excludingNoteId")
    suspend fun countOtherReferencesToPath(relativePath: String, excludingNoteId: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE sha256 = :sha256 AND note_id != :excludingNoteId")
    suspend fun countNotesWithAttachmentSha256(sha256: String, excludingNoteId: String): Int

    @Query("SELECT note_id FROM attachments WHERE sha256 = :sha256 AND note_id != :excludingNoteId")
    suspend fun findNoteIdsWithAttachmentSha256(sha256: String, excludingNoteId: String): List<String>

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertAttachments(attachments: List<AttachmentEntity>)

    @Upsert
    suspend fun upsertOcrResult(ocr: OcrResultEntity)

    @Upsert
    suspend fun upsertOcrResults(ocrs: List<OcrResultEntity>)

    @Query("DELETE FROM attachments WHERE id = :attachmentId")
    suspend fun deleteAttachment(attachmentId: String)

    @Query("DELETE FROM attachments")
    suspend fun deleteAllAttachments()
}

@Dao
interface LinkPreviewDao {
    @Query("SELECT * FROM link_previews ORDER BY updated_at_ms DESC")
    fun observeAllLinkPreviews(): Flow<List<LinkPreviewEntity>>

    @Query("SELECT * FROM link_previews WHERE note_id = :noteId ORDER BY updated_at_ms DESC")
    fun observeLinksForNote(noteId: String): Flow<List<LinkPreviewEntity>>

    @Query("SELECT * FROM link_previews WHERE note_id = :noteId")
    suspend fun getLinksForNote(noteId: String): List<LinkPreviewEntity>

    @Query("SELECT * FROM link_previews")
    suspend fun getAllLinksSnapshot(): List<LinkPreviewEntity>

    @Query("SELECT DISTINCT note_id FROM link_previews WHERE canonical_url = :canonicalUrl AND note_id != :excludingNoteId")
    suspend fun findNoteIdsByCanonicalUrl(canonicalUrl: String, excludingNoteId: String): List<String>

    @Upsert
    suspend fun upsertLinks(links: List<LinkPreviewEntity>)

    @Upsert
    suspend fun upsertLink(link: LinkPreviewEntity)

    @Query("DELETE FROM link_previews WHERE note_id = :noteId")
    suspend fun deleteLinksForNote(noteId: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE note_id = :noteId LIMIT 1")
    fun observeReminderForNote(noteId: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE note_id = :noteId LIMIT 1")
    suspend fun getReminderForNote(noteId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: String): ReminderEntity?

    @Query(
        """
        SELECT r.* FROM reminders r
        INNER JOIN notes n ON r.note_id = n.id
        WHERE r.is_completed = 0 AND n.is_deleted = 0
        ORDER BY r.trigger_at_ms ASC
        """
    )
    suspend fun getAllActiveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersSnapshot(): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Upsert
    suspend fun upsertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE note_id = :noteId")
    suspend fun deleteReminderForNote(noteId: String)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)
}

@Dao
interface SearchAndMaintenanceDao {
    @Query("SELECT * FROM recent_searches ORDER BY searched_at_ms DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(entry: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProcessingJob(job: ProcessingJobEntity)

    @Query("SELECT * FROM processing_jobs WHERE note_id = :noteId")
    suspend fun getJobsForNote(noteId: String): List<ProcessingJobEntity>

    @Query("SELECT term, doc_freq FROM search_vocab WHERE term_length BETWEEN :minLen AND :maxLen")
    suspend fun getVocabByLengthRange(minLen: Int, maxLen: Int): List<VocabTermRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVocab(entries: List<SearchVocabEntity>)

    @Query("DELETE FROM search_vocab")
    suspend fun clearVocab()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismissDuplicatePair(pair: DismissedDuplicateEntity)

    @Query("SELECT pair_key FROM dismissed_duplicates")
    fun observeDismissedDuplicateKeys(): Flow<List<String>>

    @Query("SELECT pair_key FROM dismissed_duplicates")
    suspend fun getDismissedDuplicateKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: NoteEmbeddingEntity)

    @Query("SELECT * FROM note_embeddings WHERE note_id = :noteId LIMIT 1")
    suspend fun getEmbedding(noteId: String): NoteEmbeddingEntity?

    @Query("SELECT * FROM note_embeddings WHERE note_id IN (:noteIds)")
    suspend fun getEmbeddings(noteIds: List<String>): List<NoteEmbeddingEntity>
}
