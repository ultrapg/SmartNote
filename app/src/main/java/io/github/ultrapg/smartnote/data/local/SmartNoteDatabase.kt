package io.github.ultrapg.smartnote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        NoteEntity::class,
        NoteBlockEntity::class,
        TagEntity::class,
        NoteTagCrossRefEntity::class,
        AttachmentEntity::class,
        OcrResultEntity::class,
        LinkPreviewEntity::class,
        ReminderEntity::class,
        ProcessingJobEntity::class,
        RecentSearchEntity::class,
        SearchVocabEntity::class,
        DismissedDuplicateEntity::class,
        NoteEmbeddingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SmartNoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteBlockDao(): NoteBlockDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun linkPreviewDao(): LinkPreviewDao
    abstract fun reminderDao(): ReminderDao
    abstract fun searchAndMaintenanceDao(): SearchAndMaintenanceDao

    suspend fun <R> inWriteTransaction(block: suspend () -> R): R {
        return useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block()
            }
        }
    }

    companion object {
        const val DB_NAME = "smartnote.db"

        fun create(context: Context): SmartNoteDatabase {
            return Room.databaseBuilder<SmartNoteDatabase>(
                context = context.applicationContext,
                name = context.applicationContext.getDatabasePath(DB_NAME).absolutePath
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(ftsCallback())
                .build()
        }

        fun createInMemory(context: Context): SmartNoteDatabase {
            return Room.inMemoryDatabaseBuilder<SmartNoteDatabase>(
                context = context.applicationContext
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(ftsCallback())
                .build()
        }

        private fun ftsCallback(): Callback = object : Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                ensureFtsTables(connection)
            }

            override fun onOpen(connection: SQLiteConnection) {
                super.onOpen(connection)
                connection.execSQL("PRAGMA foreign_keys = ON")
                ensureFtsTables(connection)
            }
        }

        fun ensureFtsTables(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
                    note_id UNINDEXED,
                    title,
                    body_text,
                    checklist_text,
                    tags_text,
                    ocr_text,
                    urls_text,
                    tokenize = 'porter unicode61 remove_diacritics 2'
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS notes_trigram_fts USING fts5(
                    note_id UNINDEXED,
                    combined_text,
                    tokenize = 'trigram'
                )
                """.trimIndent()
            )
        }
    }
}
