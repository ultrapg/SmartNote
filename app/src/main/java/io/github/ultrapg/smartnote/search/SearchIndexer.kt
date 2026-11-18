package io.github.ultrapg.smartnote.search

import androidx.room.PooledConnection
import androidx.room.useWriterConnection
import io.github.ultrapg.smartnote.data.local.SearchVocabEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.util.TextHashing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SearchIndexer(
    private val database: SmartNoteDatabase
) {
    private val writeMutex = Mutex()

    suspend fun indexNote(noteId: String) = writeMutex.withLock {
        val note = database.noteDao().getNoteById(noteId)
        if (note == null || note.isDeleted) {
            removeNoteInternal(noteId)
            return@withLock
        }

        val blocks = database.noteBlockDao().getBlocksForNote(noteId)
        val tags = database.tagDao().getActiveTagsForNote(noteId)
        val attachments = database.attachmentDao().getAttachmentsForNote(noteId)
        val links = database.linkPreviewDao().getLinksForNote(noteId)

        val bodyText = blocks
            .filter { it.blockType == NoteBlockType.TEXT.name }
            .joinToString("\n") { it.textContent }
        val checklistText = blocks
            .filter { it.blockType == NoteBlockType.CHECKLIST.name }
            .joinToString("\n") { it.textContent }
        val tagsText = tags.joinToString(" ") { it.tagName }
        val ocrText = attachments
            .mapNotNull { it.recognizedText?.takeIf(String::isNotBlank) }
            .joinToString("\n")
        val urlsText = buildSet {
            blocks.filter { it.blockType == NoteBlockType.LINK.name }
                .mapNotNullTo(this) { it.linkUrl?.takeIf(String::isNotBlank) }
            links.forEach { link ->
                add(link.rawUrl)
                add(link.domain)
                link.pageTitle?.takeIf(String::isNotBlank)?.let(::add)
                link.description?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" ")

        val combinedForTrigram = listOf(
            note.title,
            bodyText,
            checklistText,
            tagsText,
            ocrText,
            urlsText
        ).filter(String::isNotBlank).joinToString(" ")

        database.useWriterConnection { conn ->
            deleteFtsRows(conn, noteId)
            insertFtsRows(
                conn = conn,
                noteId = noteId,
                title = note.title,
                bodyText = bodyText,
                checklistText = checklistText,
                tagsText = tagsText,
                ocrText = ocrText,
                urlsText = urlsText,
                combined = combinedForTrigram
            )
        }

        val vocabTerms = TextHashing.tokenizeWords(
            "${note.title} $bodyText $checklistText $tagsText $ocrText"
        ).filter { it.length in 3..32 }.distinct()
        if (vocabTerms.isNotEmpty()) {
            val existing = database.searchAndMaintenanceDao()
                .getVocabByLengthRange(3, 32)
                .associateBy { it.term }
            val updated = vocabTerms.map { term ->
                val prev = existing[term]?.docFreq ?: 0
                SearchVocabEntity(
                    term = term,
                    termLength = term.length,
                    docFreq = (prev + 1).coerceAtLeast(1)
                )
            }
            database.searchAndMaintenanceDao().upsertVocab(updated)
        }
    }

    suspend fun removeNote(noteId: String) = writeMutex.withLock {
        removeNoteInternal(noteId)
    }

    suspend fun rebuildAll() = writeMutex.withLock {
        val activeNotes = database.noteDao().getAllNonDeletedNotes()
        database.useWriterConnection { conn ->
            conn.usePrepared("DELETE FROM notes_fts") { it.step() }
            conn.usePrepared("DELETE FROM notes_trigram_fts") { it.step() }
        }
        database.searchAndMaintenanceDao().clearVocab()

        val vocabFreq = HashMap<String, Int>()
        for (note in activeNotes) {
            val blocks = database.noteBlockDao().getBlocksForNote(note.id)
            val tags = database.tagDao().getActiveTagsForNote(note.id)
            val attachments = database.attachmentDao().getAttachmentsForNote(note.id)
            val links = database.linkPreviewDao().getLinksForNote(note.id)

            val bodyText = blocks.filter { it.blockType == NoteBlockType.TEXT.name }
                .joinToString("\n") { it.textContent }
            val checklistText = blocks.filter { it.blockType == NoteBlockType.CHECKLIST.name }
                .joinToString("\n") { it.textContent }
            val tagsText = tags.joinToString(" ") { it.tagName }
            val ocrText = attachments.mapNotNull { it.recognizedText?.takeIf(String::isNotBlank) }
                .joinToString("\n")
            val urlsText = buildSet {
                blocks.filter { it.blockType == NoteBlockType.LINK.name }
                    .mapNotNullTo(this) { it.linkUrl?.takeIf(String::isNotBlank) }
                links.forEach { link ->
                    add(link.rawUrl)
                    add(link.domain)
                    link.pageTitle?.takeIf(String::isNotBlank)?.let(::add)
                }
            }.joinToString(" ")
            val combined = listOf(note.title, bodyText, checklistText, tagsText, ocrText, urlsText)
                .filter(String::isNotBlank).joinToString(" ")

            database.useWriterConnection { conn ->
                insertFtsRows(
                    conn = conn,
                    noteId = note.id,
                    title = note.title,
                    bodyText = bodyText,
                    checklistText = checklistText,
                    tagsText = tagsText,
                    ocrText = ocrText,
                    urlsText = urlsText,
                    combined = combined
                )
            }

            TextHashing.tokenizeWords("${note.title} $bodyText $checklistText $tagsText $ocrText")
                .filter { it.length in 3..32 }
                .distinct()
                .forEach { token ->
                    vocabFreq[token] = (vocabFreq[token] ?: 0) + 1
                }
        }

        if (vocabFreq.isNotEmpty()) {
            database.searchAndMaintenanceDao().upsertVocab(
                vocabFreq.map { (term, freq) ->
                    SearchVocabEntity(term = term, termLength = term.length, docFreq = freq)
                }
            )
        }
    }

    private suspend fun removeNoteInternal(noteId: String) {
        database.useWriterConnection { conn ->
            deleteFtsRows(conn, noteId)
        }
    }

    private suspend fun deleteFtsRows(conn: PooledConnection, noteId: String) {
        conn.usePrepared("DELETE FROM notes_fts WHERE note_id = ?") { stmt ->
            stmt.bindText(1, noteId)
            stmt.step()
        }
        conn.usePrepared("DELETE FROM notes_trigram_fts WHERE note_id = ?") { stmt ->
            stmt.bindText(1, noteId)
            stmt.step()
        }
    }

    private suspend fun insertFtsRows(
        conn: PooledConnection,
        noteId: String,
        title: String,
        bodyText: String,
        checklistText: String,
        tagsText: String,
        ocrText: String,
        urlsText: String,
        combined: String
    ) {
        conn.usePrepared(
            """
            INSERT INTO notes_fts(note_id, title, body_text, checklist_text, tags_text, ocr_text, urls_text)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ) { stmt ->
            stmt.bindText(1, noteId)
            stmt.bindText(2, title)
            stmt.bindText(3, bodyText)
            stmt.bindText(4, checklistText)
            stmt.bindText(5, tagsText)
            stmt.bindText(6, ocrText)
            stmt.bindText(7, urlsText)
            stmt.step()
        }
        conn.usePrepared(
            "INSERT INTO notes_trigram_fts(note_id, combined_text) VALUES (?, ?)"
        ) { stmt ->
            stmt.bindText(1, noteId)
            stmt.bindText(2, combined)
            stmt.step()
        }
    }
}
