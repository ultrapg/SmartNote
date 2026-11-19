package io.github.ultrapg.smartnote.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
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

    @Test
    fun migratesVersion1And2DatabasesCleanlyToVersion3DroppingRemindersTable() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_v1_v3_${System.nanoTime()}.db")
        try {
            val driver = BundledSQLiteDriver()
            val conn = driver.open(dbFile.absolutePath)
            try {
                conn.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `plain_text_preview` TEXT NOT NULL, `full_plain_text` TEXT NOT NULL, `word_count` INTEGER NOT NULL, `is_pinned` INTEGER NOT NULL, `is_archived` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `deleted_at_ms` INTEGER, `created_at_ms` INTEGER NOT NULL, `updated_at_ms` INTEGER NOT NULL, `content_revision` INTEGER NOT NULL, `has_checklist` INTEGER NOT NULL, `checklist_checked_count` INTEGER NOT NULL, `checklist_total_count` INTEGER NOT NULL, `primary_attachment_id` TEXT, `content_sha256` TEXT NOT NULL, `simhash64` INTEGER NOT NULL, `source_type` TEXT NOT NULL, PRIMARY KEY(`id`))")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_is_deleted_is_archived_is_pinned_updated_at_ms` ON `notes` (`is_deleted`, `is_archived`, `is_pinned`, `updated_at_ms`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_content_sha256` ON `notes` (`content_sha256`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_deleted_at_ms` ON `notes` (`deleted_at_ms`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `note_blocks` (`id` TEXT NOT NULL, `note_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `block_type` TEXT NOT NULL, `text_content` TEXT NOT NULL, `is_checked` INTEGER NOT NULL, `indent_level` INTEGER NOT NULL, `attachment_id` TEXT, `link_url` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_note_blocks_note_id_position` ON `note_blocks` (`note_id`, `position`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_note_blocks_attachment_id` ON `note_blocks` (`attachment_id`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `color_seed` INTEGER NOT NULL, `created_at_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                conn.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalized_name` ON `tags` (`normalized_name`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `note_tag_cross_ref` (`note_id` TEXT NOT NULL, `tag_id` TEXT NOT NULL, `source` TEXT NOT NULL, `confidence` REAL NOT NULL, `is_dismissed` INTEGER NOT NULL, `updated_at_ms` INTEGER NOT NULL, PRIMARY KEY(`note_id`, `tag_id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_cross_ref_tag_id_is_dismissed` ON `note_tag_cross_ref` (`tag_id`, `is_dismissed`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_cross_ref_note_id_is_dismissed` ON `note_tag_cross_ref` (`note_id`, `is_dismissed`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `note_id` TEXT NOT NULL, `relative_file_path` TEXT NOT NULL, `thumbnail_relative_path` TEXT, `mime_type` TEXT NOT NULL, `original_display_name` TEXT NOT NULL, `byte_size` INTEGER NOT NULL, `width_px` INTEGER NOT NULL, `height_px` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `is_screenshot` INTEGER NOT NULL, `created_at_ms` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_note_id` ON `attachments` (`note_id`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_sha256` ON `attachments` (`sha256`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_relative_file_path` ON `attachments` (`relative_file_path`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `ocr_results` (`attachment_id` TEXT NOT NULL, `recognized_text` TEXT NOT NULL, `language_code` TEXT NOT NULL, `avg_confidence` REAL NOT NULL, `status` TEXT NOT NULL, `processed_at_ms` INTEGER NOT NULL, `engine_name` TEXT NOT NULL, PRIMARY KEY(`attachment_id`), FOREIGN KEY(`attachment_id`) REFERENCES `attachments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_results_status` ON `ocr_results` (`status`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `link_previews` (`id` TEXT NOT NULL, `note_id` TEXT NOT NULL, `raw_url` TEXT NOT NULL, `canonical_url` TEXT NOT NULL, `domain` TEXT NOT NULL, `page_title` TEXT, `description` TEXT, `preview_image_path` TEXT, `fetch_status` TEXT NOT NULL, `updated_at_ms` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_link_previews_note_id` ON `link_previews` (`note_id`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_link_previews_canonical_url` ON `link_previews` (`canonical_url`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `note_id` TEXT NOT NULL, `trigger_at_ms` INTEGER NOT NULL, `zone_id` TEXT NOT NULL, `local_date_time_iso` TEXT NOT NULL, `repeat_rule` TEXT NOT NULL, `is_completed` INTEGER NOT NULL, `completed_at_ms` INTEGER, `snooze_count` INTEGER NOT NULL, `use_live_update` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_note_id` ON `reminders` (`note_id`)")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_is_completed_trigger_at_ms` ON `reminders` (`is_completed`, `trigger_at_ms`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `processing_jobs` (`note_id` TEXT NOT NULL, `job_type` TEXT NOT NULL, `content_revision` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, `last_error` TEXT, `updated_at_ms` INTEGER NOT NULL, PRIMARY KEY(`note_id`, `job_type`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `recent_searches` (`query` TEXT NOT NULL, `searched_at_ms` INTEGER NOT NULL, PRIMARY KEY(`query`))")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `search_vocab` (`term` TEXT NOT NULL, `term_length` INTEGER NOT NULL, `doc_freq` INTEGER NOT NULL, PRIMARY KEY(`term`))")
                conn.execSQL("CREATE INDEX IF NOT EXISTS `index_search_vocab_term_length` ON `search_vocab` (`term_length`)")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `dismissed_duplicates` (`pair_key` TEXT NOT NULL, `dismissed_at_ms` INTEGER NOT NULL, PRIMARY KEY(`pair_key`))")
                conn.execSQL("CREATE TABLE IF NOT EXISTS `note_embeddings` (`note_id` TEXT NOT NULL, `model_id` TEXT NOT NULL, `content_sha256` TEXT NOT NULL, `dims` INTEGER NOT NULL, `vector_blob` BLOB NOT NULL, `updated_at_ms` INTEGER NOT NULL, PRIMARY KEY(`note_id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                conn.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                conn.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c18462504b3d166fa8b7034d8e4c5ab8')")
                conn.execSQL("INSERT INTO notes VALUES ('n-v1','V1 Note','','',0,0,0,0,NULL,1000,1000,1,0,0,0,NULL,'sha',0,'MANUAL')")
                conn.execSQL("INSERT INTO reminders VALUES ('r-v1','n-v1',9999999999999,'UTC','2027-01-01T09:00:00','DAILY',0,NULL,2,1)")
                conn.execSQL("PRAGMA user_version = 1")
            } finally {
                conn.close()
            }

            val migratedDb = Room.databaseBuilder<SmartNoteDatabase>(
                context = context.applicationContext,
                name = dbFile.absolutePath
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(SmartNoteDatabase.MIGRATION_1_2, SmartNoteDatabase.MIGRATION_2_3)
                .build()
            try {
                val note = migratedDb.noteDao().getNoteById("n-v1")
                assertNotNull(note)
                assertEquals("V1 Note", note!!.title)

                val remindersTableExists = migratedDb.useReaderConnection { reader ->
                    reader.usePrepared(
                        "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='reminders'"
                    ) { stmt ->
                        stmt.step() && stmt.getLong(0) > 0L
                    }
                }
                assertFalse(remindersTableExists)
            } finally {
                migratedDb.close()
            }
        } finally {
            dbFile.delete()
        }
    }
}
