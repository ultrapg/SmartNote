package io.github.ultrapg.smartnote.tags

import android.content.Context
import android.graphics.Bitmap
import android.view.textclassifier.TextClassifier
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.ai.ClassifiedTextEntity
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.GeminiNanoCapabilities
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.ImageLabelingEngine
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.ai.SystemEntityAnalyzer
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.TagPredictionSensitivity
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

    @Test
    fun smartTagsEliminatesFalsePositivesSupportsNewCategoriesAndSettings() = runTest {
        // 1. Everyday casual English & German sentences must NOT trigger false tags even with includeTopicKeywords = true
        val casualGerman = smartTagsEngine.suggestTags(
            title = "Samstag",
            blocks = listOf(
                NoteBlock(
                    "g1", "n1", 0, NoteBlockType.TEXT,
                    "Ich wollte heute einfach mal schauen ob wir später noch rausgehen oder zu Hause bleiben und entspannen."
                )
            ),
            ocrTexts = emptyList(),
            links = emptyList(),
            hasScreenshot = false,
            existingUserVocabulary = emptyList(),
            includeTopicKeywords = true
        )
        assertTrue("Casual German note must not produce noisy tags: $casualGerman", casualGerman.isEmpty())

        // 2. Ambiguous software/travel/medical disambiguations
        val autoFeatureNote = smartTagsEngine.suggestTags(
            title = "App feedback",
            blocks = listOf(NoteBlock("d1", "n2", 0, NoteBlockType.TEXT, "Auto-title and auto-tag clean the windows while running slow"))
            , ocrTexts = emptyList(), links = emptyList(), hasScreenshot = false, existingUserVocabulary = emptyList()
        ).map { it.name }
        assertFalse("Software 'auto-title' must not tag Car: $autoFeatureNote", "Car" in autoFeatureNote)
        assertFalse("'clean the windows' must not tag Tech: $autoFeatureNote", "Tech" in autoFeatureNote)
        assertFalse("'running slow' must not tag Fitness: $autoFeatureNote", "Fitness" in autoFeatureNote)

        val bookFlight = smartTagsEngine.suggestTags(
            title = "Weekend",
            blocks = listOf(NoteBlock("d2", "n3", 0, NoteBlockType.TEXT, "Book a flight for tomorrow morning"))
            , ocrTexts = emptyList(), links = emptyList(), hasScreenshot = false, existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Expected Travel for 'Book a flight', got $bookFlight", "Travel" in bookFlight)
        assertFalse("Reading must NOT trigger on 'book a flight': $bookFlight", "Reading" in bookFlight)

        val medicalRezept = smartTagsEngine.suggestTags(
            title = "Praxis",
            blocks = listOf(NoteBlock("d3", "n4", 0, NoteBlockType.TEXT, "Beim Arzt mein Rezept für Medikamente abholen und in die Apotheke"))
            , ocrTexts = emptyList(), links = emptyList(), hasScreenshot = false, existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Expected Health for German medical prescription, got $medicalRezept", "Health" in medicalRezept)
        assertFalse("Recipe must NOT trigger on medical 'Rezept': $medicalRezept", "Recipe" in medicalRezept)

        val noIdea = smartTagsEngine.suggestTags(
            title = "Hmm",
            blocks = listOf(NoteBlock("d4", "n5", 0, NoteBlockType.TEXT, "No idea what happened or keine Idee warum"))
            , ocrTexts = emptyList(), links = emptyList(), hasScreenshot = false, existingUserVocabulary = emptyList()
        ).map { it.name }
        assertFalse("Negated 'no idea' must not tag Ideas: $noIdea", "Ideas" in noIdea)

        // 3. New Food and Accounts categories
        val dinnerNote = smartTagsEngine.suggestTags(
            title = "Friday",
            blocks = listOf(NoteBlock("f1", "n6", 0, NoteBlockType.TEXT, "Dinner with friends at the Italian restaurant"))
            , ocrTexts = emptyList(), links = emptyList(), hasScreenshot = false, existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Expected Food category, got $dinnerNote", "Food" in dinnerNote)
        assertFalse("Eating out must not tag Recipe: $dinnerNote", "Recipe" in dinnerNote)

        val accountsNote = smartTagsEngine.suggestTags(
            title = "Router Setup",
            blocks = listOf(NoteBlock("a1", "n7", 0, NoteBlockType.TEXT, "WLAN Passwort und 2FA recovery codes gespeichert"))
            , ocrTexts = emptyList(), links = emptyList(), hasScreenshot = false, existingUserVocabulary = emptyList()
        ).map { it.name }
        assertTrue("Expected Accounts category, got $accountsNote", "Accounts" in accountsNote)

        // 4. Generated tags saved with confidence < 0.99f must NOT pollute BuildUserTagContext,
        // whereas explicit user tags (1.0f) ARE learned cleanly
        val nGenerated = noteRepository.createNote(
            title = "Generated check",
            blocks = listOf(NoteBlock("ng1", "ng", 0, NoteBlockType.TEXT, "Customtokenalpha discussion")),
            scheduleBackgroundWork = false
        )
        tagRepository.addGeneratedTagToNote(nGenerated, "NoisyGenTag", 0.78f)
        val nManual = noteRepository.createNote(
            title = "Manual check",
            blocks = listOf(NoteBlock("nm1", "nm", 0, NoteBlockType.TEXT, "HandmadeCeramicMug glazing batch")),
            scheduleBackgroundWork = false
        )
        tagRepository.addUserTagToNote(nManual, "Pottery")

        val summaries = noteRepository.getAllSummariesSnapshot(includeArchived = true)
        val (profiles, _) = SmartTagsEngine.buildUserTagContext(summaries, excludeNoteId = "other_note")
        assertFalse("Generated tag must not pollute learned profiles", profiles.containsKey("NoisyGenTag"))
        assertTrue("Manual user tag must be learned cleanly", profiles.containsKey("Pottery"))
    }

    @Test
    fun coordinatorRoutesHybridGeminiNanoFastRespectsSettingsAndAutoSkipsUnsupported() = runTest {
        var nanoCalls = 0
        var lastPreferFast: Boolean? = null
        var lastAllowNewTopics: Boolean? = null

        val fakeNanoFastOnly = object : OnDeviceGenAiAssistant {
            override suspend fun checkAvailability() = GeminiNanoAvailability.AVAILABLE
            override suspend fun probeCapabilities(forceRefresh: Boolean) = GeminiNanoCapabilities(
                fastAvailability = GeminiNanoAvailability.AVAILABLE,
                fullAvailability = GeminiNanoAvailability.UNAVAILABLE,
                baseModelName = "gemini-nano-v2-fast"
            )
            override fun triggerBackgroundDownloadIfNeeded() {}
            override suspend fun generateTitleCandidates(
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                linkHints: List<String>,
                primaryImageBitmap: Bitmap?
            ) = emptyList<String>()
            override suspend fun generateTags(
                title: String,
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                existingVocabulary: List<String>,
                existingNoteTags: Set<String>
            ) = emptyList<SmartTagSuggestion>()
            override suspend fun generateTags(
                title: String,
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                existingVocabulary: List<String>,
                existingNoteTags: Set<String>,
                maxTags: Int,
                preferFast: Boolean,
                allowNewTopicTags: Boolean,
                primaryImageBitmap: Bitmap?
            ): List<SmartTagSuggestion> {
                nanoCalls++
                lastPreferFast = preferFast
                lastAllowNewTopics = allowNewTopicTags
                return listOf(
                    SmartTagSuggestion("Personal", 0.89f, "Gemini Nano Fast"),
                    SmartTagSuggestion("Events", 0.89f, "Gemini Nano Fast")
                ).filter { it.name.lowercase() !in existingNoteTags }.take(maxTags)
            }
            override fun close() {}
        }

        val unavailableEntityAnalyzer = object : SystemEntityAnalyzer {
            override fun checkAvailability() = SystemEntityAvailability.UNAVAILABLE
            override suspend fun extractEntities(text: String): List<ClassifiedTextEntity> {
                error("Should not execute when UNAVAILABLE")
            }
        }
        val unavailableImageLabeler = object : ImageLabelingEngine {
            override suspend fun checkAvailability(forceRefresh: Boolean) = ImageLabelingAvailability.UNAVAILABLE
            override suspend fun labelImageFile(file: java.io.File): List<DetectedImageLabel> {
                error("Should not execute when UNAVAILABLE")
            }
        }

        val coordinator = SmartTaggingCoordinator(
            smartTagsEngine = smartTagsEngine,
            systemEntityAnalyzer = unavailableEntityAnalyzer,
            imageLabelingEngine = unavailableImageLabeler,
            geminiNanoAssistant = fakeNanoFastOnly
        )

        // 1. AUTO_HYBRID skips Gemini Nano when local engine is already certain
        val confidentOutcome = coordinator.generateTagsForNote(
            noteId = "c1",
            title = "Supermarket Run",
            blocks = listOf(NoteBlock("cb1", "c1", 0, NoteBlockType.CHECKLIST, "Buy groceries, milk, eggs, bread, butter, cheese")),
            ocrTexts = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            existingNoteTags = emptySet(),
            dismissedTagNames = emptySet(),
            existingUserVocabulary = listOf("Shopping"),
            allSummaries = emptyList(),
            settings = AppSettings(
                geminiNanoMode = GeminiNanoMode.AUTO_HYBRID,
                geminiNanoVariant = GeminiNanoVariant.FAST_FIRST,
                maxPredictedTags = 2
            ),
            isExplicitManualTrigger = true
        )
        assertEquals(0, nanoCalls)
        assertFalse(confidentOutcome.usedGeminiNano)
        assertTrue("Shopping" in confidentOutcome.suggestions.map { it.name })

        // 2. AUTO_HYBRID calls Gemini Nano Fast when local rules are unsure, honoring maxPredictedTags & dismissed tags
        val unsureOutcome = coordinator.generateTagsForNote(
            noteId = "c2",
            title = "Saturday evening",
            blocks = listOf(NoteBlock("cb2", "c2", 0, NoteBlockType.TEXT, "Catching up with Sarah and Tom for coffee and catching up")),
            ocrTexts = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            existingNoteTags = emptySet(),
            dismissedTagNames = setOf("Personal"),
            existingUserVocabulary = emptyList(),
            allSummaries = emptyList(),
            settings = AppSettings(
                geminiNanoMode = GeminiNanoMode.AUTO_HYBRID,
                geminiNanoVariant = GeminiNanoVariant.FAST_FIRST,
                allowNewTopicTags = false,
                maxPredictedTags = 1,
                tagSensitivity = TagPredictionSensitivity.BALANCED
            ),
            isExplicitManualTrigger = true
        )
        assertEquals(1, nanoCalls)
        assertTrue(unsureOutcome.usedGeminiNano)
        assertTrue(unsureOutcome.usedGeminiFast)
        assertEquals(true, lastPreferFast)
        assertEquals(false, lastAllowNewTopics)
        assertEquals(listOf("Events"), unsureOutcome.suggestions.map { it.name })

        // 3. GeminiNanoMode.OFF skips Gemini Nano even when unsure
        val nanoOffOutcome = coordinator.generateTagsForNote(
            noteId = "c3",
            title = "Random",
            blocks = listOf(NoteBlock("cb3", "c3", 0, NoteBlockType.TEXT, "Neutral sentence without category hints")),
            ocrTexts = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            existingNoteTags = emptySet(),
            dismissedTagNames = emptySet(),
            existingUserVocabulary = emptyList(),
            allSummaries = emptyList(),
            settings = AppSettings(
                geminiNanoMode = GeminiNanoMode.OFF,
                allowNewTopicTags = false
            ),
            isExplicitManualTrigger = true
        )
        assertEquals(1, nanoCalls)
        assertFalse(nanoOffOutcome.usedGeminiNano)
        assertTrue(nanoOffOutcome.suggestions.isEmpty())

        // 4. Per-feature FORCE calls Gemini Nano even when local engine is already certain
        val forcedOutcome = coordinator.generateTagsForNote(
            noteId = "c4",
            title = "Supermarket Run",
            blocks = listOf(NoteBlock("cb4", "c4", 0, NoteBlockType.CHECKLIST, "Buy groceries, milk, eggs, bread, butter, cheese")),
            ocrTexts = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            existingNoteTags = emptySet(),
            dismissedTagNames = emptySet(),
            existingUserVocabulary = listOf("Shopping"),
            allSummaries = emptyList(),
            settings = AppSettings(
                geminiNanoTagsPolicy = GeminiNanoFeaturePolicy.FORCE,
                geminiNanoVariant = GeminiNanoVariant.FAST_FIRST,
                maxPredictedTags = 3
            ),
            isExplicitManualTrigger = true
        )
        assertEquals(2, nanoCalls)
        assertTrue(forcedOutcome.usedGeminiNano)
        assertTrue("Personal" in forcedOutcome.suggestions.map { it.name })
    }
}
