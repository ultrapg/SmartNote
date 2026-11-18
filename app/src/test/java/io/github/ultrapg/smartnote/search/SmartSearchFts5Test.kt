package io.github.ultrapg.smartnote.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.data.local.AttachmentEntity
import io.github.ultrapg.smartnote.data.local.OcrResultEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.OcrStatus
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
class SmartSearchFts5Test {

    private lateinit var database: SmartNoteDatabase
    private lateinit var noteRepository: NoteRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var searchIndexer: SearchIndexer
    private lateinit var searchEngine: SmartSearchEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SmartNoteDatabase.createInMemory(context)
        val storage = AttachmentStorage(context)
        searchIndexer = SearchIndexer(database)
        tagRepository = TagRepository(database, searchIndexer)
        noteRepository = NoteRepository(database, storage, searchIndexer, tagRepository)
        searchEngine = SmartSearchEngine(database, noteRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ranksExactTitleAboveBodyTagOcrAndSupportsTypoAndSynonymQueries() = runTest {
        val idTitle = noteRepository.createNote(
            title = "Kotlin Coroutines",
            blocks = listOf(NoteBlock("b1", "t", 0, NoteBlockType.TEXT, "Concurrency guide.")),
            scheduleBackgroundWork = false
        )
        val idBody = noteRepository.createNote(
            title = "Weekend Plans",
            blocks = listOf(NoteBlock("b2", "b", 0, NoteBlockType.TEXT, "Study kotlin structured concurrency and flows.")),
            scheduleBackgroundWork = false
        )
        val idOcr = noteRepository.createNote(
            title = "Error Screenshot",
            blocks = listOf(NoteBlock("b3", "o", 0, NoteBlockType.IMAGE, attachmentId = "att-ocr")),
            scheduleBackgroundWork = false
        )
        database.attachmentDao().upsertAttachment(
            AttachmentEntity(
                id = "att-ocr",
                noteId = idOcr,
                relativeFilePath = "attachments/att-ocr.png",
                mimeType = "image/png",
                originalDisplayName = "screen.png",
                byteSize = 1200L,
                widthPx = 1080,
                heightPx = 1920,
                sha256 = "ocrsha",
                isScreenshot = true,
                createdAtMs = System.currentTimeMillis()
            )
        )
        database.attachmentDao().upsertOcrResult(
            OcrResultEntity(
                attachmentId = "att-ocr",
                recognizedText = "Fatal NullPointerException in kotlin coroutine launcher",
                status = OcrStatus.COMPLETED.name,
                processedAtMs = System.currentTimeMillis()
            )
        )
        tagRepository.addUserTagToNote(idBody, "Android")
        searchIndexer.rebuildAll()

        // 1. Exact title match must rank #1
        val kotlinRes = searchEngine.search("Kotlin")
        assertTrue(kotlinRes.results.size >= 3)
        assertEquals(idTitle, kotlinRes.results.first().note.id)

        // 2. Synonym expansion ("bug" -> matches OCR "exception")
        val bugRes = searchEngine.search("bug")
        assertTrue(bugRes.results.any { it.note.id == idOcr })
        assertTrue(SearchMatchField.OCR in bugRes.results.first { it.note.id == idOcr }.matchedFields)

        // 3. Typo tolerance ("kotln" -> finds "kotlin" and suggests "Did you mean: kotlin")
        val typoRes = searchEngine.search("kotln")
        assertTrue(typoRes.results.isNotEmpty())
        assertEquals("kotlin", typoRes.didYouMeanSuggestion)

        // 4. Trigram substring search ("PointerException")
        val subRes = searchEngine.search("PointerException")
        assertEquals(listOf(idOcr), subRes.results.map { it.note.id })

        // 5. #tag operator + punctuation safety ("C++" shouldn't crash FTS5)
        val tagRes = searchEngine.search("#Android kotlin")
        assertEquals(listOf(idBody), tagRes.results.map { it.note.id })

        val punctRes = searchEngine.search("C++ @OptIn()")
        assertTrue(punctRes.results.isEmpty())

        // 6. Recent searches stored cleanly
        searchEngine.recordRecentSearch("Kotlin Coroutines")
        assertEquals(listOf("Kotlin Coroutines"), searchEngine.observeRecentSearches().first())
    }
}
