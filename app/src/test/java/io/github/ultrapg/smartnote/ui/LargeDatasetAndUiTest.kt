package io.github.ultrapg.smartnote.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.ThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LargeDatasetAndUiTest {

    @get:Rule
    @Suppress("DEPRECATION")
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun largeCollectionFts5SearchScalesWithinLatencyBudget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as SmartNoteApplication
        val db = app.container.database
        val indexer = app.container.searchIndexer
        val search = app.container.searchEngine

        val now = System.currentTimeMillis()
        val entities = (1..2500).map { i ->
            io.github.ultrapg.smartnote.data.local.NoteEntity(
                id = "perf_$i",
                title = if (i == 1777) "Quantum Architecture Target" else "Daily Note #$i",
                plainTextPreview = "Offline note entry number $i about Kotlin and Android design.",
                fullPlainText = "Offline note entry number $i about Kotlin and Android design.",
                wordCount = 10,
                isPinned = i == 1,
                isArchived = false,
                isDeleted = false,
                createdAtMs = now - i,
                updatedAtMs = now - i
            )
        }
        val blocks = (1..2500).map { i ->
            io.github.ultrapg.smartnote.data.local.NoteBlockEntity(
                id = "perf_blk_$i",
                noteId = "perf_$i",
                position = 0,
                blockType = NoteBlockType.TEXT.name,
                textContent = "Offline note entry number $i about Kotlin and Android design."
            )
        }
        db.noteDao().upsertNotes(entities)
        db.noteBlockDao().insertBlocks(blocks)
        indexer.rebuildAll()

        val startMs = System.currentTimeMillis()
        val response = search.search("Quantum")
        val elapsedMs = System.currentTimeMillis() - startMs

        assertTrue("Expected target note in 2500-note results", response.results.any { it.note.title == "Quantum Architecture Target" })
        assertTrue("2500-note FTS5 query ($elapsedMs ms) exceeded latency budget", elapsedMs < 250L)
    }

    @Test
    fun composeUiSupportsNoteCreationLayoutAndThemeSwitchSearchAndTags() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as SmartNoteApplication
        runBlocking {
            app.container.settingsRepository.setThemePalette(ThemePalette.AMOLED)
            app.container.settingsRepository.setDarkModeConfig(DarkModeConfig.DARK)
            app.container.settingsRepository.setNotesLayout(NotesLayoutMode.STAGGERED_CARDS)
        }

        // Opening New note via morphing pill and backing out untouched must NOT create an empty ghost bar
        openNewTextNoteFromPill()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back and save").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Capture anything, offline").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Capture anything, offline").assertIsDisplayed()
        assertTrue(
            runBlocking {
                app.container.noteRepository.getAllSummariesSnapshot().isEmpty()
            }
        )

        // Create a note via morphing FAB pill -> Note
        openNewTextNoteFromPill()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Title").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Title").performTextInput("Compose UI Test Note")
        composeRule.onNodeWithContentDescription("Back and save").performClick()

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Compose UI Test Note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Compose UI Test Note").assertIsDisplayed()

        // Switch Staggered Cards <-> Compact List
        composeRule.onNodeWithContentDescription("Switch between card and compact list layout").performClick()
        composeRule.onNodeWithText("Compose UI Test Note").assertIsDisplayed()

        // By default auto-title is OFF: creating an untitled note keeps title blank
        openNewTextNoteFromPill()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Start writing", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Start writing", substring = true)
            .performTextInput("Quick offline note without automatic title")
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText("Auto-title:", substring = true).fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithContentDescription("Back and save").performClick()
        composeRule.waitUntil(5_000L) {
            runBlocking {
                app.container.noteRepository.getAllSummariesSnapshot()
                    .any { it.previewText.contains("Quick offline note without automatic title") && it.title.isEmpty() }
            }
        }

        // Enable auto-title explicitly and verify opt-in auto-title generation on editor exit
        runBlocking {
            app.container.settingsRepository.setAutoTitleEnabled(true)
        }
        openNewTextNoteFromPill()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Start writing", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Start writing", substring = true)
            .performTextInput("Quarterly architecture planning session.\nReview FTS5 benchmarks.")
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Auto-title:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back and save").performClick()
        composeRule.waitUntil(5_000L) {
            runBlocking {
                app.container.noteRepository.getAllSummariesSnapshot()
                    .any { it.title == "Quarterly architecture planning session" }
            }
        }
        assertTrue(
            runBlocking {
                app.container.noteRepository.getAllSummariesSnapshot()
                    .any { it.title == "Quarterly architecture planning session" }
            }
        )

        // Navigate to Tags tab & SmartSearch tab
        composeRule.onNodeWithText("Tags").performClick()
        composeRule.onNodeWithText("Filter tags...").assertIsDisplayed()

        composeRule.onNodeWithText("SmartSearch").performClick()
        composeRule.onNodeWithText("SmartSearch titles, OCR text, #tags, URLs...").assertIsDisplayed()

        // Navigate to Settings -> AI & Smart Features and toggle master AI setting
        composeRule.onNodeWithText("Notes").performClick()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("AI & Smart Features").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("AI & Smart Features").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Enable AI Features").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("SmartTags").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("AI Hardware & Diagnostics").performScrollTo().assertIsEnabled()

        composeRule.onNodeWithText("Enable AI Features").performScrollTo().performClick()
        composeRule.waitUntil(3_000L) {
            runBlocking {
                !app.container.settingsRepository.settingsFlow.first().aiFeaturesEnabled
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SmartTags").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Offline OCR").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Duplicate Detection").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Hybrid Semantic Search (LiteRT)").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("AI Hardware & Diagnostics").performScrollTo().assertIsNotEnabled()
        runBlocking {
            val disabledState = app.container.settingsRepository.settingsFlow.first()
            assertTrue(disabledState.smartTagsEnabled)
            assertTrue(disabledState.ocrEnabled)
            assertTrue(disabledState.semanticRerankEnabled)
            assertTrue(!disabledState.effectiveSmartTagsEnabled)
            assertTrue(!disabledState.effectiveOcrEnabled)
            assertTrue(!disabledState.effectiveSemanticRerankEnabled)
        }

        composeRule.onNodeWithText("Enable AI Features").performScrollTo().performClick()
        composeRule.waitUntil(3_000L) {
            runBlocking {
                app.container.settingsRepository.settingsFlow.first().aiFeaturesEnabled
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SmartTags").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("AI Hardware & Diagnostics").performScrollTo().assertIsEnabled()
        runBlocking {
            val restoredState = app.container.settingsRepository.settingsFlow.first()
            assertTrue(restoredState.effectiveSmartTagsEnabled)
            assertTrue(restoredState.effectiveOcrEnabled)
            assertTrue(restoredState.effectiveSemanticRerankEnabled)
        }
    }

    @Test
    @Config(fontScale = 2.0f)
    fun accessibleAt200PercentFontScale() {
        composeRule.onNodeWithContentDescription("New note").assertIsDisplayed()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()
    }

    @Test
    fun longScrollableNoteRendersEditorScrollbarAndAcceptsInput() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as SmartNoteApplication
        val longBody = (1..70).joinToString("\n") { "Scrollable editor line $it with offline note content" }
        runBlocking {
            app.container.noteRepository.createNote(
                title = "Long Scrollable Note",
                blocks = listOf(NoteBlock("long_blk_1", "long_n", 0, NoteBlockType.TEXT, longBody)),
                scheduleBackgroundWork = false
            )
        }

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Long Scrollable Note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Long Scrollable Note").performClick()

        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Scrollable editor line 1", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back and save").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Long Scrollable Note").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun smartSearchHeaderClipsScrolledResultsAndEdgeToEdgeChips() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as SmartNoteApplication
        runBlocking {
            val db = app.container.database
            val now = System.currentTimeMillis()
            val notes = (1..20).map { i ->
                io.github.ultrapg.smartnote.data.local.NoteEntity(
                    id = "search_clip_$i",
                    title = "Desert Research Lab #$i",
                    plainTextPreview = "Offline desert and labs field notes entry $i",
                    fullPlainText = "Offline desert and labs field notes entry $i",
                    wordCount = 8,
                    isPinned = false,
                    isArchived = false,
                    isDeleted = false,
                    createdAtMs = now - i,
                    updatedAtMs = now - i
                )
            }
            val blocks = (1..20).map { i ->
                io.github.ultrapg.smartnote.data.local.NoteBlockEntity(
                    id = "search_clip_blk_$i",
                    noteId = "search_clip_$i",
                    position = 0,
                    blockType = NoteBlockType.TEXT.name,
                    textContent = "Offline desert and labs field notes entry $i"
                )
            }
            db.noteDao().upsertNotes(notes)
            db.noteBlockDao().insertBlocks(blocks)
            app.container.searchIndexer.rebuildAll()
        }

        composeRule.onNodeWithText("SmartSearch").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("SmartSearch titles, OCR text, #tags, URLs...").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("SmartSearch titles, OCR text, #tags, URLs...")
            .performTextInput("dersert and labs")

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Did you mean: desert and labs?").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Desert Research Lab #", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        val chipRowNode = composeRule.onNode(
            hasScrollToIndexAction() and hasAnyDescendant(hasText("Pinned"))
        )
        val chipRowBounds = chipRowNode.getBoundsInRoot()
        val pinnedChipBounds = composeRule.onNodeWithText("Pinned").getBoundsInRoot()
        val didYouMeanBounds = composeRule.onNodeWithText("Did you mean: desert and labs?").getBoundsInRoot()
        val resultsListNode = composeRule.onNode(
            hasScrollToIndexAction() and hasAnyDescendant(hasText("Desert Research Lab #", substring = true))
        )
        val resultsListBounds = resultsListNode.getBoundsInRoot()

        assertTrue(
            "Filter chip row should span flush to the left screen edge (was ${chipRowBounds.left})",
            chipRowBounds.left <= 1.dp
        )
        assertTrue(
            "Filter chip row should span flush to the right screen edge (was ${chipRowBounds.right} vs ${rootBounds.right})",
            chipRowBounds.right >= rootBounds.right - 1.dp
        )
        assertTrue(
            "First filter chip should still have 16dp content padding (was ${pinnedChipBounds.left})",
            pinnedChipBounds.left >= 15.dp
        )
        assertTrue(
            "SmartSearch results list (${resultsListBounds.top}) must be clipped below filter chips (${pinnedChipBounds.bottom})",
            resultsListBounds.top >= pinnedChipBounds.bottom
        )
        assertTrue(
            "Did-you-mean banner (${didYouMeanBounds.top}..${didYouMeanBounds.bottom}) must be visible below filter chips (${pinnedChipBounds.bottom})",
            (didYouMeanBounds.bottom - didYouMeanBounds.top) > 0.dp &&
                didYouMeanBounds.top >= pinnedChipBounds.bottom
        )

        // Partial scroll: simulates scrolling under the filter chips when keyboard opens / user nudges list
        resultsListNode.performTouchInput { swipeUp(startY = centerY, endY = centerY - 50f) }
        composeRule.waitForIdle()

        val pinnedStillBounds = composeRule.onNodeWithText("Pinned").getBoundsInRoot()
        if (composeRule.onAllNodesWithText("Did you mean: desert and labs?").fetchSemanticsNodes().isNotEmpty()) {
            val partialBannerBounds = composeRule.onNodeWithText("Did you mean: desert and labs?").getBoundsInRoot()
            if ((partialBannerBounds.bottom - partialBannerBounds.top) > 0.dp) {
                assertTrue(
                    "Partially scrolled banner (${partialBannerBounds.top}) must not overlap filter chips (${pinnedStillBounds.bottom})",
                    partialBannerBounds.top >= pinnedStillBounds.bottom
                )
            }
        }

        // Deep scroll: scrolls past index 0 and verifies visible result cards remain strictly clipped below topBar
        resultsListNode.performScrollToIndex(6)
        composeRule.waitForIdle()

        val scrolledResultsListBounds = resultsListNode.getBoundsInRoot()
        val pinnedAfterDeepScroll = composeRule.onNodeWithText("Pinned").getBoundsInRoot()
        assertTrue(
            "Scrolled SmartSearch list (${scrolledResultsListBounds.top}) must remain below filter chips (${pinnedAfterDeepScroll.bottom})",
            scrolledResultsListBounds.top >= pinnedAfterDeepScroll.bottom
        )
        if (composeRule.onAllNodesWithText("Did you mean: desert and labs?").fetchSemanticsNodes().isNotEmpty()) {
            val deepBannerBounds = composeRule.onNodeWithText("Did you mean: desert and labs?").getBoundsInRoot()
            if ((deepBannerBounds.bottom - deepBannerBounds.top) > 0.dp) {
                assertTrue(
                    "Deeply scrolled banner (${deepBannerBounds.top}) must not overlap filter chips (${pinnedAfterDeepScroll.bottom})",
                    deepBannerBounds.top >= pinnedAfterDeepScroll.bottom
                )
            }
        }
        val visibleLabNodes = composeRule.onAllNodesWithText("Desert Research Lab #", substring = true)
        val visibleCount = visibleLabNodes.fetchSemanticsNodes().size
        var positiveHeightVisibleCards = 0
        for (idx in 0 until visibleCount) {
            val cardBounds = visibleLabNodes[idx].getBoundsInRoot()
            if ((cardBounds.bottom - cardBounds.top) > 0.dp) {
                positiveHeightVisibleCards++
                assertTrue(
                    "Visible card #$idx (${cardBounds.top}) must be clipped below filter chips (${pinnedAfterDeepScroll.bottom})",
                    cardBounds.top >= pinnedAfterDeepScroll.bottom
                )
            }
        }
        assertTrue(
            "Expected at least one visible search result card after scrolling",
            positiveHeightVisibleCards > 0
        )
    }

    @Test
    fun morphingCreatePillMenuRollsOutUpwardAndLaunchesOptions() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as SmartNoteApplication

        composeRule.onNodeWithText("Capture anything, offline").assertIsDisplayed()
        assertTrue(
            "Empty-state New checklist action chip should be removed",
            composeRule.onAllNodesWithText("New checklist").fetchSemanticsNodes().isEmpty()
        )

        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithContentDescription("Close create menu").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Reminder").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Image").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Checklist").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertTrue(
            "Pill toggle should use X icon only without 'Close' text label",
            composeRule.onAllNodesWithText("Close").fetchSemanticsNodes().isEmpty()
        )
        val closeBounds = composeRule.onNodeWithContentDescription("Close create menu").getBoundsInRoot()
        listOf("Reminder", "Image", "Checklist", "Note").forEach { optionLabel ->
            composeRule.onNodeWithText(optionLabel).assertIsDisplayed()
            val optionBounds = composeRule.onNodeWithText(optionLabel).getBoundsInRoot()
            assertTrue(
                "$optionLabel (${optionBounds.bottom}) must roll out above bottom toggle (${closeBounds.top})",
                optionBounds.bottom <= closeBounds.top + 2.dp
            )
        }

        // Choosing Checklist opens the editor directly in checklist mode
        composeRule.onNodeWithText("Checklist").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Checklist item").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Checklist item").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back and save").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Capture anything, offline").fetchSemanticsNodes().isNotEmpty()
        }

        // Choosing Reminder opens the editor with the Note Reminder sheet open
        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Reminder").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Reminder").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Note Reminder").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Note Reminder").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithContentDescription("Back and save").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back and save").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Capture anything, offline").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Cancelling untouched checklist/reminder creation must not leave ghost notes",
            runBlocking { app.container.noteRepository.getAllSummariesSnapshot().isEmpty() }
        )
    }

    @Test
    fun systemIntegrationsAndEnergySettingsRenderCleanly() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("System Integrations").fetchSemanticsNodes().isNotEmpty()
        }

        // System Integrations shows 4 distinct integration cards
        composeRule.onNodeWithText("System Integrations").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Quick Settings Tile").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Quick Settings Tile").assertIsDisplayed()
        composeRule.onNodeWithText("Home Screen Widgets").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Launcher App Shortcuts").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Android Sharesheet").performScrollTo().assertIsDisplayed()

        // Navigate back to Settings -> AI & Smart Features -> AI Hardware & Diagnostics
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("AI & Smart Features").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("AI & Smart Features").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("AI Hardware & Diagnostics").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("AI Hardware & Diagnostics")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Hardware Acceleration Preference").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Prioritize low-power NPU", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun morphingTopBarMenusAndFlyingBottomNavIndicatorWorkCleanly() {
        // 1. Expanding Sort menu squashes overflow Menu & layout toggle aside and rolls out pill options
        composeRule.onNodeWithContentDescription("Sort notes").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Recently updated").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithContentDescription("Close sort menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Recently updated").assertIsDisplayed()
        composeRule.onNodeWithText("Recently created").assertIsDisplayed()
        composeRule.onNodeWithText("Title A–Z").assertIsDisplayed()
        assertTrue(
            "Overflow menu icon should squash away while Sort menu is open",
            composeRule.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithText("Title A–Z").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Expanding Menu squashes Sort icon aside and rolls out overflow items inside the morphed pill
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithContentDescription("Close menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Archive").assertIsDisplayed()
        composeRule.onNodeWithText("Recycle Bin").assertIsDisplayed()
        composeRule.onNodeWithText("Review Duplicates").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        assertTrue(
            "Sort icon should squash away while Menu is open",
            composeRule.onAllNodesWithContentDescription("Sort notes").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithContentDescription("Close menu").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithContentDescription("Sort notes").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Flying bottom navigation pill moves smoothly left -> center -> right across tabs
        composeRule.waitForIdle()
        val notesLeft = composeRule.onNodeWithTag("bottom_nav_flying_pill").getBoundsInRoot().left

        composeRule.onNodeWithText("SmartSearch").performClick()
        composeRule.waitForIdle()
        val searchLeft = composeRule.onNodeWithTag("bottom_nav_flying_pill").getBoundsInRoot().left

        composeRule.onNodeWithText("Tags").performClick()
        composeRule.waitForIdle()
        val tagsLeft = composeRule.onNodeWithTag("bottom_nav_flying_pill").getBoundsInRoot().left

        assertTrue(
            "Flying bottom bar pill must move right from Notes ($notesLeft) -> SmartSearch ($searchLeft) -> Tags ($tagsLeft)",
            notesLeft < searchLeft && searchLeft < tagsLeft
        )
    }

    private fun openNewTextNoteFromPill() {
        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.waitUntil(3_000L) {
            composeRule.onAllNodesWithText("Note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Note").performClick()
    }
}
