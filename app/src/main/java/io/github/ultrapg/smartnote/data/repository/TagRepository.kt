package io.github.ultrapg.smartnote.data.repository

import io.github.ultrapg.smartnote.data.local.NoteTagCrossRefEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.local.TagEntity
import io.github.ultrapg.smartnote.domain.model.NoteTag
import io.github.ultrapg.smartnote.domain.model.TagInfo
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.search.SearchIndexer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class TagRepository(
    private val database: SmartNoteDatabase,
    private val searchIndexer: SearchIndexer,
    private val onTagsChanged: () -> Unit = {}
) {
    private val tagDao = database.tagDao()

    fun observeAllTags(): Flow<List<TagInfo>> =
        tagDao.observeAllTagsWithCounts().map { rows ->
            rows.map { row ->
                TagInfo(
                    id = row.id,
                    name = row.name,
                    colorSeed = row.colorSeed,
                    noteCount = row.noteCount,
                    suggestedCount = row.suggestedCount,
                    createdAtMs = row.createdAtMs
                )
            }
        }

    fun observeTagsForNote(noteId: String): Flow<List<NoteTag>> =
        tagDao.observeTagsForNote(noteId).map { rows ->
            rows.map { row ->
                NoteTag(
                    tagId = row.tagId,
                    name = row.tagName,
                    source = enumValueOfOrDefault(row.source, TagSource.USER),
                    confidence = row.confidence,
                    colorSeed = row.colorSeed
                )
            }
        }

    suspend fun getAllTagsSnapshot(): List<TagEntity> = tagDao.getAllTagsSnapshot()

    suspend fun getOrCreateTag(rawName: String, nowMs: Long = System.currentTimeMillis()): TagEntity? {
        val cleaned = normalizeDisplayName(rawName)
        if (cleaned.isBlank()) return null
        val normalized = cleaned.lowercase(Locale.ROOT)
        val existing = tagDao.findByNormalizedName(normalized)
        if (existing != null) return existing
        val created = TagEntity(
            id = UUID.randomUUID().toString(),
            name = cleaned,
            normalizedName = normalized,
            colorSeed = abs(normalized.hashCode()),
            createdAtMs = nowMs
        )
        tagDao.insertTagIfAbsent(created)
        return tagDao.findByNormalizedName(normalized) ?: created
    }

    suspend fun getDismissedTagNamesForNote(noteId: String): Set<String> {
        val dismissedIds = tagDao.getAllCrossRefsForNote(noteId)
            .filter { it.isDismissed }
            .mapTo(HashSet()) { it.tagId }
        if (dismissedIds.isEmpty()) return emptySet()
        return tagDao.getAllTagsSnapshot()
            .filter { it.id in dismissedIds }
            .mapTo(HashSet()) { it.name }
    }

    suspend fun addUserTagToNote(noteId: String, rawName: String) {
        val now = System.currentTimeMillis()
        val tag = getOrCreateTag(rawName, now) ?: return
        tagDao.upsertCrossRef(
            NoteTagCrossRefEntity(
                noteId = noteId,
                tagId = tag.id,
                source = TagSource.USER.name,
                confidence = 1.0f,
                isDismissed = false,
                updatedAtMs = now
            )
        )
        searchIndexer.indexNote(noteId)
        onTagsChanged()
    }

    suspend fun addGeneratedTagToNote(noteId: String, rawName: String, confidence: Float) {
        val now = System.currentTimeMillis()
        val tag = getOrCreateTag(rawName, now) ?: return
        tagDao.upsertCrossRef(
            NoteTagCrossRefEntity(
                noteId = noteId,
                tagId = tag.id,
                source = TagSource.USER.name,
                confidence = confidence.coerceIn(0.50f, 0.95f),
                isDismissed = false,
                updatedAtMs = now
            )
        )
        searchIndexer.indexNote(noteId)
        onTagsChanged()
    }

    suspend fun confirmSuggestedTag(noteId: String, tagId: String) {
        val now = System.currentTimeMillis()
        tagDao.upsertCrossRef(
            NoteTagCrossRefEntity(
                noteId = noteId,
                tagId = tagId,
                source = TagSource.USER.name,
                confidence = 1.0f,
                isDismissed = false,
                updatedAtMs = now
            )
        )
        searchIndexer.indexNote(noteId)
        onTagsChanged()
    }

    suspend fun dismissOrRemoveTagFromNote(noteId: String, tagId: String) {
        val now = System.currentTimeMillis()
        tagDao.dismissCrossRef(noteId, tagId, now)
        searchIndexer.indexNote(noteId)
        onTagsChanged()
    }

    suspend fun applySuggestedTags(
        noteId: String,
        suggestions: List<Pair<String, Float>>,
        maxTags: Int = 4
    ) {
        val cappedMax = maxTags.coerceIn(1, 6)
        val now = System.currentTimeMillis()
        val existingRefs = tagDao.getAllCrossRefsForNote(noteId).associateBy { it.tagId }
        val userTagIds = existingRefs.values
            .filter { !it.isDismissed && it.source == TagSource.USER.name }
            .map { it.tagId }
            .toSet()
        val dismissedTagIds = existingRefs.values
            .filter { it.isDismissed }
            .map { it.tagId }
            .toSet()

        val resolvedSuggestions = mutableListOf<Pair<TagEntity, Float>>()
        for ((name, confidence) in suggestions) {
            if (resolvedSuggestions.size >= cappedMax) break
            val tag = getOrCreateTag(name, now) ?: continue
            if (tag.id in userTagIds || tag.id in dismissedTagIds) continue
            if (resolvedSuggestions.any { it.first.id == tag.id }) continue
            resolvedSuggestions += tag to confidence.coerceIn(0f, 1f)
        }

        database.inWriteTransaction {
            tagDao.clearPendingSuggestions(noteId)
            val newRefs = resolvedSuggestions.map { (tag, confidence) ->
                NoteTagCrossRefEntity(
                    noteId = noteId,
                    tagId = tag.id,
                    source = TagSource.SUGGESTED.name,
                    confidence = confidence,
                    isDismissed = false,
                    updatedAtMs = now
                )
            }
            if (newRefs.isNotEmpty()) {
                tagDao.upsertCrossRefs(newRefs)
            }
        }
        onTagsChanged()
    }

    suspend fun renameTag(tagId: String, newRawName: String): Boolean {
        val cleaned = normalizeDisplayName(newRawName)
        if (cleaned.isBlank()) return false
        val normalized = cleaned.lowercase(Locale.ROOT)
        val collision = tagDao.findByNormalizedName(normalized)
        if (collision != null && collision.id != tagId) {
            mergeTags(sourceTagId = tagId, targetTagId = collision.id)
            return true
        }
        val affectedNotes = tagDao.getNoteIdsForTag(tagId)
        tagDao.renameTag(tagId, cleaned, normalized)
        affectedNotes.forEach { searchIndexer.indexNote(it) }
        onTagsChanged()
        return true
    }

    suspend fun mergeTags(sourceTagId: String, targetTagId: String) {
        if (sourceTagId == targetTagId) return
        val now = System.currentTimeMillis()
        val affectedNotes = tagDao.getNoteIdsForTag(sourceTagId)
        database.inWriteTransaction {
            val allRefs = tagDao.getAllCrossRefsSnapshot()
            val targetNotes = allRefs.filter { it.tagId == targetTagId && !it.isDismissed }
                .map { it.noteId }
                .toSet()
            val toInsert = allRefs
                .filter { it.tagId == sourceTagId && !it.isDismissed && it.noteId !in targetNotes }
                .map { ref ->
                    ref.copy(tagId = targetTagId, updatedAtMs = now)
                }
            if (toInsert.isNotEmpty()) {
                tagDao.upsertCrossRefs(toInsert)
            }
            tagDao.deleteTag(sourceTagId)
        }
        affectedNotes.forEach { searchIndexer.indexNote(it) }
        onTagsChanged()
    }

    suspend fun deleteTag(tagId: String) {
        val affectedNotes = tagDao.getNoteIdsForTag(tagId)
        tagDao.deleteTag(tagId)
        affectedNotes.forEach { searchIndexer.indexNote(it) }
        onTagsChanged()
    }

    companion object {
        fun normalizeDisplayName(raw: String): String {
            return raw.trim()
                .removePrefix("#")
                .replace(Regex("\\s+"), " ")
                .take(36)
                .trim()
        }

        private inline fun <reified T : Enum<T>> enumValueOfOrDefault(raw: String?, fallback: T): T {
            if (raw.isNullOrBlank()) return fallback
            return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
        }
    }
}
