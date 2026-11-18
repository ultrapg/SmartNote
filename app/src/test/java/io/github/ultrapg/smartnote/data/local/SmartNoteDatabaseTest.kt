package io.github.ultrapg.smartnote.data.local

import android.content.Context
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmartNoteDatabaseTest {

    private lateinit var database: SmartNoteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SmartNoteDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createsTablesCascadesForeignKeysAndExecutesRealBundledFts5Bm25() = runTest {
        val now = 1_760_000_000_000L
        val noteDao = database.noteDao()
        val blockDao = database.noteBlockDao()
        val tagDao = database.tagDao()

        val note = NoteEntity(
            id = "note-1",
            title = "Kotlin Coroutines Architecture",
            plainTextPreview = "Structured concurrency with Room and BundledSQLiteDriver.",
            fullPlainText = "Structured concurrency with Room and BundledSQLiteDriver.",
            wordCount = 7,
            isPinned = true,
            isArchived = false,
            isDeleted = false,
            createdAtMs = now,
            updatedAtMs = now,
            contentSha256 = "abc123",
            simhash64 = 42L
        )
        noteDao.upsertNote(note)
        blockDao.replaceBlocksForNote(
            "note-1",
            listOf(
                NoteBlockEntity(
                    id = "blk-1",
                    noteId = "note-1",
                    position = 0,
                    blockType = "TEXT",
                    textContent = "Structured concurrency with Room and BundledSQLiteDriver."
                )
            )
        )
        tagDao.upsertTag(
            TagEntity(
                id = "tag-kotlin",
                name = "Kotlin",
                normalizedName = "kotlin",
                createdAtMs = now
            )
        )
        tagDao.upsertCrossRef(
            NoteTagCrossRefEntity(
                noteId = "note-1",
                tagId = "tag-kotlin",
                source = "USER",
                confidence = 1.0f,
                isDismissed = false,
                updatedAtMs = now
            )
        )

        database.useWriterConnection { conn ->
            conn.usePrepared(
                """
                INSERT INTO notes_fts(note_id, title, body_text, checklist_text, tags_text, ocr_text, urls_text)
                VALUES (?, ?, ?, '', ?, '', '')
                """.trimIndent()
            ) { stmt ->
                stmt.bindText(1, "note-1")
                stmt.bindText(2, note.title)
                stmt.bindText(3, note.fullPlainText)
                stmt.bindText(4, "Kotlin")
                stmt.step()
            }
            conn.usePrepared(
                "INSERT INTO notes_trigram_fts(note_id, combined_text) VALUES (?, ?)"
            ) { stmt ->
                stmt.bindText(1, "note-1")
                stmt.bindText(2, "${note.title} ${note.fullPlainText}")
                stmt.step()
            }
        }

        val active = noteDao.observeActiveNotes().first()
        assertEquals(1, active.size)
        assertEquals("note-1", active.first().id)

        val ftsMatches = database.useReaderConnection { conn ->
            conn.usePrepared(
                """
                SELECT note_id, bm25(notes_fts, 12.0, 5.0, 6.0, 8.0, 3.5, 3.0)
                FROM notes_fts
                WHERE notes_fts MATCH ?
                """.trimIndent()
            ) { stmt ->
                stmt.bindText(1, "coroutine*")
                buildList {
                    while (stmt.step()) {
                        add(stmt.getText(0))
                    }
                }
            }
        }
        assertEquals(listOf("note-1"), ftsMatches)

        val trigramMatches = database.useReaderConnection { conn ->
            conn.usePrepared(
                "SELECT note_id FROM notes_trigram_fts WHERE notes_trigram_fts MATCH ?"
            ) { stmt ->
                stmt.bindText(1, "\"SQLiteDriver\"")
                buildList {
                    while (stmt.step()) {
                        add(stmt.getText(0))
                    }
                }
            }
        }
        assertEquals(listOf("note-1"), trigramMatches)

        noteDao.deleteNotePermanently("note-1")
        assertTrue(blockDao.getBlocksForNote("note-1").isEmpty())
        assertTrue(tagDao.getActiveTagsForNote("note-1").isEmpty())
    }
}
