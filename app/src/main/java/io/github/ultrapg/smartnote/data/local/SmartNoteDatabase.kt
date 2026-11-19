package io.github.ultrapg.smartnote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
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
        ProcessingJobEntity::class,
        RecentSearchEntity::class,
        SearchVocabEntity::class,
        DismissedDuplicateEntity::class,
        NoteEmbeddingEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class SmartNoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteBlockDao(): NoteBlockDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun linkPreviewDao(): LinkPreviewDao
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(ftsCallback())
                .build()
        }

        fun createInMemory(context: Context): SmartNoteDatabase {
            return Room.inMemoryDatabaseBuilder<SmartNoteDatabase>(
                context = context.applicationContext
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(ftsCallback())
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE reminders ADD COLUMN repeat_days_mask INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE reminders ADD COLUMN repeat_interval INTEGER NOT NULL DEFAULT 1"
                )
                connection.execSQL(
                    "ALTER TABLE reminders ADD COLUMN pre_alert_minutes INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE reminders ADD COLUMN is_important INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("DROP TABLE IF EXISTS reminders")
            }
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
