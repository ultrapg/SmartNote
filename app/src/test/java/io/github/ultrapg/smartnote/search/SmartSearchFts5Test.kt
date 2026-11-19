package io.github.ultrapg.smartnote.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.ai.MlKitGeminiNanoAssistant
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
import org.junit.Assert.assertFalse
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

    @Test
    fun supportsBilingualSynonymsAndGeminiNanoQueryExpansionWithLexicalPriority() = runTest {
        val idDoctor = noteRepository.createNote(
            title = "Doctor Checkup",
            blocks = listOf(NoteBlock("d1", "doc", 0, NoteBlockType.TEXT, "Bring vaccination booklet")),
            scheduleBackgroundWork = false
        )
        val idRecipe = noteRepository.createNote(
            title = "Pasta Dinner",
            blocks = listOf(NoteBlock("r1", "rec", 0, NoteBlockType.TEXT, "Simple Italian recipe with tomatoes")),
            scheduleBackgroundWork = false
        )
        val idFlight = noteRepository.createNote(
            title = "Paris Flight & Hotel",
            blocks = listOf(NoteBlock("f1", "flt", 0, NoteBlockType.TEXT, "Departure gate 14 and hotel check-in details")),
            scheduleBackgroundWork = false
        )
        val idExactGetaway = noteRepository.createNote(
            title = "Weekend Getaway Checklist",
            blocks = listOf(NoteBlock("g1", "get", 0, NoteBlockType.TEXT, "Pack chargers and adapters")),
            scheduleBackgroundWork = false
        )
        searchIndexer.rebuildAll()

        // 1. Local bilingual DE -> EN synonym fallback (without Gemini Nano)
        assertTrue(searchEngine.search("arzt").results.any { it.note.id == idDoctor })
        assertTrue(searchEngine.search("rezept").results.any { it.note.id == idRecipe })

        // 2. Gemini Nano query parser strips refusals, duplicates, and multi-word noise
        val parsedTerms = MlKitGeminiNanoAssistant.Parsers.parseSearchExpansionTerms(
            rawResponse = "Sure, here are keywords: flight, hotel, travel, weekend, too many words here, #packing, flight",
            originalQuery = "weekend getaway"
        )
        assertEquals(listOf("flight", "hotel", "travel", "packing"), parsedTerms)

        // 3. AI-expanded search finds related travel note while keeping exact lexical match #1
        var expanderCalls = 0
        val aiSearchEngine = SmartSearchEngine(
            database = database,
            noteRepository = noteRepository,
            aiQueryExpander = { query, _ ->
                expanderCalls++
                assertEquals("weekend getaway", query)
                listOf("flight", "hotel", "travel")
            }
        )
        val expandedResponse = aiSearchEngine.search(
            rawQuery = "weekend getaway",
            enableAiQueryExpansion = true
        )
        assertEquals(1, expanderCalls)
        assertEquals(listOf("flight", "hotel", "travel"), expandedResponse.aiExpandedTerms)
        assertEquals(idExactGetaway, expandedResponse.results.first().note.id)
        assertTrue(expandedResponse.results.any { it.note.id == idFlight })

        // 4. Failing / unsupported expander falls back gracefully without crashing
        val failingSearchEngine = SmartSearchEngine(
            database = database,
            noteRepository = noteRepository,
            aiQueryExpander = { _, _ -> throw IllegalStateException("AICore unavailable") }
        )
        val fallbackResponse = failingSearchEngine.search(
            rawQuery = "weekend getaway",
            enableAiQueryExpansion = true
        )
        assertFalse(fallbackResponse.results.isEmpty())
        assertEquals(idExactGetaway, fallbackResponse.results.first().note.id)
        assertTrue(fallbackResponse.aiExpandedTerms.isEmpty())

        // 5. Zero-result conceptual query (even short 3-char query) is rescued by Gemini Nano semantic note matcher
        val idGym = noteRepository.createNote(
            title = "Barbell Push Pull Split",
            blocks = listOf(NoteBlock("gym1", "gym", 0, NoteBlockType.TEXT, "Squats 3x5, bench press, deadlift progression")),
            scheduleBackgroundWork = false
        )
        searchIndexer.rebuildAll()

        assertEquals(
            listOf(2, 5, 1),
            MlKitGeminiNanoAssistant.Parsers.parseMatchedNoteIndices("2, 5, 99, 1, 2", maxIndex = 6)
        )
        assertTrue(MlKitGeminiNanoAssistant.Parsers.parseMatchedNoteIndices("NONE", maxIndex = 6).isEmpty())

        var matcherCalls = 0
        val rescuingEngine = SmartSearchEngine(
            database = database,
            noteRepository = noteRepository,
            aiQueryExpander = { _, _ -> emptyList() },
            aiNoteMatcher = { query, candidates ->
                matcherCalls++
                if (query.equals("gym", ignoreCase = true) || query.contains("leg day")) {
                    candidates.filter { it.noteId == idGym }.map { it.noteId }
                } else {
                    emptyList()
                }
            }
        )
        val zeroResultBaseline = rescuingEngine.search("leg day", enableAiQueryExpansion = false)
        assertTrue(zeroResultBaseline.results.isEmpty())

        val rescuedShort = rescuingEngine.search("gym", enableAiQueryExpansion = true)
        assertEquals(1, matcherCalls)
        assertTrue(rescuedShort.usedGeminiNanoMatch)
        assertEquals(listOf(idGym), rescuedShort.results.map { it.note.id })

        // 6. forceAiSearch runs BOTH expander and matcher even when lexical results already exist
        val forcedRes = rescuingEngine.search(
            rawQuery = "Barbell leg day",
            enableAiQueryExpansion = true,
            forceAiSearch = true
        )
        assertEquals(2, matcherCalls)
        assertTrue(forcedRes.usedGeminiNanoMatch)
        assertTrue(forcedRes.results.any { it.note.id == idGym })
    }
}
