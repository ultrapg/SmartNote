package io.github.ultrapg.smartnote.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var scope: CoroutineScope
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        tempFile = File(context.cacheDir, "settings_test_${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFile }
        )
        repo = SettingsRepository(ds)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempFile.delete()
    }

    @Test
    fun defaultsAndUpdatesPersistAcrossReads() = runTest {
        val defaults = repo.settingsFlow.first()
        assertEquals(ThemePalette.DYNAMIC, defaults.themePalette)
        assertEquals(DarkModeConfig.SYSTEM, defaults.darkModeConfig)
        assertEquals(NotesLayoutMode.STAGGERED_CARDS, defaults.notesLayout)
        assertFalse(defaults.fetchLinkPreviews)
        assertFalse(defaults.semanticRerankEnabled)
        assertEquals(AiBackendPreference.AUTO, defaults.aiBackendPreference)

        repo.setThemePalette(ThemePalette.AMOLED)
        repo.setDarkModeConfig(DarkModeConfig.DARK)
        repo.setNotesLayout(NotesLayoutMode.COMPACT_LIST)
        repo.setReducedMotion(true)
        repo.setAiBackendPreference(AiBackendPreference.PREFER_NPU)
        repo.setDefaultSnoozeMinutes(15)

        val updated = repo.settingsFlow.first()
        assertEquals(ThemePalette.AMOLED, updated.themePalette)
        assertEquals(DarkModeConfig.DARK, updated.darkModeConfig)
        assertEquals(NotesLayoutMode.COMPACT_LIST, updated.notesLayout)
        assertTrue(updated.reducedMotion)
        assertEquals(AiBackendPreference.PREFER_NPU, updated.aiBackendPreference)
        assertEquals(15, updated.defaultSnoozeMinutes)
    }
}
