package io.github.ultrapg.smartnote.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

        // Opening New note and backing out untouched must NOT create an empty ghost bar
        composeRule.onNodeWithContentDescription("New note").performClick()
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

        // Create a note via prominent FAB
        composeRule.onNodeWithContentDescription("New note").performClick()
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

        // Create an untitled note and verify auto-title generation on editor exit
        composeRule.onNodeWithContentDescription("New note").performClick()
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
            composeRule.onAllNodesWithText("Enable All AI Features").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Enable All AI Features").performClick()
        composeRule.waitUntil(3_000L) {
            runBlocking {
                !app.container.settingsRepository.settingsFlow.first().aiFeaturesEnabled
            }
        }
        composeRule.onNodeWithText("Enable All AI Features").performClick()
        composeRule.waitUntil(3_000L) {
            runBlocking {
                app.container.settingsRepository.settingsFlow.first().aiFeaturesEnabled
            }
        }
    }

    @Test
    @Config(fontScale = 2.0f)
    fun accessibleAt200PercentFontScale() {
        composeRule.onNodeWithContentDescription("New note").assertIsDisplayed()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()
    }
}
