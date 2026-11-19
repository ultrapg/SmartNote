package io.github.ultrapg.smartnote.tags

import android.content.Context
import android.view.textclassifier.TextClassifier
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.ai.ClassifiedTextEntity
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.duplicates.SmartDuplicateDetector
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.search.SearchIndexer
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
class SmartTagsAndDuplicatesTest {

    private lateinit var database: SmartNoteDatabase
    private lateinit var tagRepository: TagRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var smartTagsEngine: SmartTagsEngine
    private lateinit var duplicateDetector: SmartDuplicateDetector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SmartNoteDatabase.createInMemory(context)
        val storage = AttachmentStorage(context)
        val indexer = SearchIndexer(database)
        tagRepository = TagRepository(database, indexer)
        noteRepository = NoteRepository(database, storage, indexer, tagRepository)
        smartTagsEngine = SmartTagsEngine()
        duplicateDetector = SmartDuplicateDetector(database, noteRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun smartTagsClassifiesNotesAndRespectsDismissedUserDecisions() = runTest {
        val links = SmartLinkDetector.extractLinks("https://developer.android.com/jetpack/compose?utm_source=x")
        val suggestions = smartTagsEngine.suggestTags(
            title = "Jetpack Compose StateFlow Guide",
            blocks = listOf(
                NoteBlock("b1", "n1", 0, NoteBlockType.TEXT, "Using Kotlin coroutines and WorkManager in Android.")
            ),
            ocrTexts = emptyList(),
            links = links,
            hasScreenshot = false,
            existingUserVocabulary = listOf("Architecture")
        ).map { it.name }

        assertTrue("Android" in suggestions)
        assertTrue("Kotlin" in suggestions)
        assertTrue("Development" in suggestions)

        val errSuggestions = smartTagsEngine.suggestTags(
            title = "Debug Log",
            blocks = emptyList(),
            ocrTexts = listOf("FATAL EXCEPTION: main\njava.lang.NullPointerException at MainActivity.kt:42"),
            links = emptyList(),
            hasScreenshot = true,
            existingUserVocabulary = emptyList()
        ).map { it.name }

        assertTrue("Screenshot" in errSuggestions)
        assertTrue("Error" in errSuggestions)
        assertTrue("Programming" in errSuggestions)

        // Verify dismissal memory in TagRepository
        val noteId = noteRepository.createNote(title = "Compose Note", scheduleBackgroundWork = false)
        tagRepository.applySuggestedTags(noteId, listOf("Android" to 0.9f, "Kotlin" to 0.85f))
        val tagsBefore = noteRepository.getNoteDetail(noteId)!!.tags
        assertEquals(2, tagsBefore.size)
        assertTrue(tagsBefore.all { it.source == TagSource.SUGGESTED })

        val androidTagId = tagsBefore.first { it.name == "Android" }.tagId
        val kotlinTagId = tagsBefore.first { it.name == "Kotlin" }.tagId
        tagRepository.confirmSuggestedTag(noteId, kotlinTagId)
        tagRepository.dismissOrRemoveTagFromNote(noteId, androidTagId)

        // Re-applying suggestions must NOT bring back dismissed "Android", and must keep USER "Kotlin"
        tagRepository.applySuggestedTags(noteId, listOf("Android" to 0.95f, "Kotlin" to 0.8f, "Development" to 0.78f))
        val tagsAfter = noteRepository.getNoteDetail(noteId)!!.tags
        assertFalse(tagsAfter.any { it.name == "Android" })
        assertEquals(TagSource.USER, tagsAfter.first { it.name == "Kotlin" }.source)
        assertEquals(TagSource.SUGGESTED, tagsAfter.first { it.name == "Development" }.source)
    }

    @Test
    fun duplicateDetectorFindsExactSimilarAndUrlMatchesWithoutAutoDeleting() = runTest {
        val n1 = noteRepository.createNote(
            title = "Weekly Shopping List",
            blocks = listOf(NoteBlock("1", "1", 0, NoteBlockType.TEXT, "Buy fresh organic milk, free range eggs, sourdough bread, roasted coffee beans")),
            scheduleBackgroundWork = false
        )
        val n2 = noteRepository.createNote(
            title = "Weekly Shopping List",
            blocks = listOf(NoteBlock("2", "2", 0, NoteBlockType.TEXT, "Buy fresh organic milk, free range eggs, sourdough bread, roasted coffee beans!")),
            scheduleBackgroundWork = false
        )
        val n3 = noteRepository.createNote(
            title = "Bookmark",
            blocks = listOf(NoteBlock("3", "3", 0, NoteBlockType.TEXT, "https://github.com/ultrapg/SmartNote?utm_source=twitter")),
            scheduleBackgroundWork = false
        )
        val n4 = noteRepository.createNote(
            title = "Repo link",
            blocks = listOf(NoteBlock("4", "4", 0, NoteBlockType.TEXT, "https://github.com/ultrapg/SmartNote/")),
            scheduleBackgroundWork = false
        )

        val duplicates = duplicateDetector.findDuplicatesForAllNotes()
        assertEquals(2, duplicates.size)
        assertTrue(duplicates.any { (it.noteA.id == n1 || it.noteB.id == n1) && (it.noteA.id == n2 || it.noteB.id == n2) })
        assertTrue(duplicates.any { (it.noteA.id == n3 || it.noteB.id == n3) && (it.noteA.id == n4 || it.noteB.id == n4) })

        // Dismissing keeps both notes safely intact
        duplicateDetector.dismissDuplicatePair(n1, n2)
        val remaining = duplicateDetector.findDuplicatesForAllNotes()
        assertEquals(1, remaining.size)
    }

    @Test
    fun smartTagsHandlesHashtagsGermanCompoundsPersonalProfilesAndSemanticAffinity() {
        // 1. Inline #hashtags work while Markdown # Heading and hex colors #FFAA00 are ignored
        val hashtagSuggestions = smartTagsEngine.suggestTags(
            title = "# Weekly Sync",
            blocks = listOf(
                NoteBlock(
                    "b1", "n1", 0, NoteBlockType.TEXT,
                    "Palette color is #FFAA00 and #fff, track #ProjectApollo tasks."
                )
            ),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("ProjectApollo" in hashtagSuggestions)
        assertFalse("Weekly" in hashtagSuggestions)
        assertFalse("FFAA00" in hashtagSuggestions)
        assertFalse("fff" in hashtagSuggestions)

        // 2. Short single-intent notes match category self-names & strong anchors cleanly
        val shortShopping = smartTagsEngine.suggestTags(
            title = "Shopping",
            blocks = emptyList(),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Shopping" in shortShopping)

        // 3. German compounds & umlaut folding
        val germanSuggestions = smartTagsEngine.suggestTags(
            title = "Notiz",
            blocks = listOf(
                NoteBlock(
                    "b2", "n2", 0, NoteBlockType.TEXT,
                    "Einkaufsliste schreiben, Arzttermin vereinbaren, Flugbuchung checken, Steuererklärung vorbereiten"
                )
            ),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Shopping" in germanSuggestions)
        assertTrue("Health" in germanSuggestions)
        assertTrue("Travel" in germanSuggestions)
        assertTrue("Finance" in germanSuggestions)

        // 4. Personal user-tag co-occurrence profiles + semantic affinity + weak-word noise guard
        val noisyOnly = smartTagsEngine.suggestTags(
            title = "Total notes change",
            blocks = listOf(NoteBlock("b3", "n3", 0, NoteBlockType.TEXT, "Just a quick review finish")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        )
        assertTrue("Weak generic words alone must not trigger noisy tags: $noisyOnly", noisyOnly.isEmpty())

        val learnedProfileSuggestions = smartTagsEngine.suggestTags(
            title = "Phoenix Q4 Rollout",
            blocks = listOf(NoteBlock("b4", "n4", 0, NoteBlockType.TEXT, "Prepared apollo invoice and delivery")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = listOf("ClientACME"),
            semanticTagScores = mapOf("Research" to 0.85f),
            userTagProfiles = mapOf("ClientACME" to mapOf("apollo" to 2.2f, "invoice" to 1.8f))
        ).map { it.name }
        assertTrue("ClientACME" in learnedProfileSuggestions)
        assertTrue("Research" in learnedProfileSuggestions)
    }

    @Test
    fun smartTagsSupportsNewCategoriesTopicsSystemEntitiesAndImageLabels() {
        val pets = smartTagsEngine.suggestTags(
            title = "Tierarzt",
            blocks = listOf(NoteBlock("p1", "n1", 0, NoteBlockType.TEXT, "Katzenfutter holen und mit Katzen zum Tierarzt")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Pets" in pets)

        val tech = smartTagsEngine.suggestTags(
            title = "Setup",
            blocks = listOf(NoteBlock("t1", "n2", 0, NoteBlockType.TEXT, "Mechanische Tastatur, Monitor und Linux Homelab NAS")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Tech" in tech)

        val photography = smartTagsEngine.suggestTags(
            title = "Kamera",
            blocks = listOf(NoteBlock("ph1", "n3", 0, NoteBlockType.TEXT, "Fujifilm Objektiv mit Stativ testen")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Photography" in photography)

        val personal = smartTagsEngine.suggestTags(
            title = "Wochenende",
            blocks = listOf(NoteBlock("pe1", "n4", 0, NoteBlockType.TEXT, "Mama anrufen und Familie besuchen")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Personal" in personal)

        val quotes = smartTagsEngine.suggestTags(
            title = "Zitat",
            blocks = listOf(NoteBlock("q1", "n5", 0, NoteBlockType.TEXT, "Ein schönes Gedicht und Motto merken")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Quotes" in quotes)

        // Open-vocabulary topic keywords work for explicit on-demand generation while still rejecting generic filler
        val topicTags = smartTagsEngine.suggestTags(
            title = "Sourdough fermentation schedule",
            blocks = listOf(NoteBlock("tp1", "n6", 0, NoteBlockType.TEXT, "Feed sourdough starter at 28C and stretch dough")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList(),
            includeTopicKeywords = true
        ).map { it.name }
        assertTrue("Expected Sourdough open-topic tag, got $topicTags", "Sourdough" in topicTags)

        val fillerOnly = smartTagsEngine.suggestTags(
            title = "Total notes change",
            blocks = listOf(NoteBlock("tp2", "n7", 0, NoteBlockType.TEXT, "Just a quick review finish today tomorrow")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList(),
            includeTopicKeywords = true
        )
        assertTrue("Generic filler must not trigger open-vocabulary topic tags: $fillerOnly", fillerOnly.isEmpty())

        // Android System TextClassifier entities & ML Kit Image Labeling signals
        val systemAndImageTags = smartTagsEngine.suggestTags(
            title = "Gate info",
            blocks = listOf(NoteBlock("se1", "n8", 0, NoteBlockType.TEXT, "Track package 1Z999AA101 before boarding LH402")),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList(),
            systemEntities = listOf(
                ClassifiedTextEntity(TextClassifier.TYPE_FLIGHT_NUMBER, "LH402", 0.92f),
                ClassifiedTextEntity("tracking_number", "1Z999AA101", 0.90f)
            ),
            imageLabels = listOf(
                DetectedImageLabel("Dog", 0.89f),
                DetectedImageLabel("Telescope", 0.91f)
            )
        ).map { it.name }
        assertTrue("Travel" in systemAndImageTags)
        assertTrue("Shopping" in systemAndImageTags)
        assertTrue("Pets" in systemAndImageTags)
        assertTrue("Telescope" in systemAndImageTags)
    }
}
