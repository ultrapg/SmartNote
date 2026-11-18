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
        assertTrue(defaults.aiFeaturesEnabled)
        assertFalse(defaults.autoTitleEnabled)
        assertFalse(defaults.effectiveAutoTitleEnabled)
        assertTrue(defaults.effectiveSmartTagsEnabled)
        assertTrue(defaults.effectiveOcrEnabled)
        assertTrue(defaults.effectiveDuplicateDetectionEnabled)
        assertFalse(defaults.fetchLinkPreviews)
        assertFalse(defaults.semanticRerankEnabled)
        assertEquals(AiBackendPreference.AUTO, defaults.aiBackendPreference)

        repo.setThemePalette(ThemePalette.AMOLED)
        repo.setDarkModeConfig(DarkModeConfig.DARK)
        repo.setNotesLayout(NotesLayoutMode.COMPACT_LIST)
        repo.setReducedMotion(true)
        repo.setAiBackendPreference(AiBackendPreference.PREFER_NPU)
        repo.setDefaultSnoozeMinutes(15)
        repo.setSemanticRerankEnabled(true)
        repo.setAutoTitleEnabled(true)
        repo.setAiFeaturesEnabled(false)

        val masterOff = repo.settingsFlow.first()
        assertEquals(ThemePalette.AMOLED, masterOff.themePalette)
        assertEquals(DarkModeConfig.DARK, masterOff.darkModeConfig)
        assertEquals(NotesLayoutMode.COMPACT_LIST, masterOff.notesLayout)
        assertTrue(masterOff.reducedMotion)
        assertEquals(AiBackendPreference.PREFER_NPU, masterOff.aiBackendPreference)
        assertEquals(15, masterOff.defaultSnoozeMinutes)
        assertFalse(masterOff.aiFeaturesEnabled)
        assertFalse(masterOff.effectiveAutoTitleEnabled)
        assertFalse(masterOff.effectiveSmartTagsEnabled)
        assertFalse(masterOff.effectiveOcrEnabled)
        assertFalse(masterOff.effectiveDuplicateDetectionEnabled)
        assertFalse(masterOff.effectiveSemanticRerankEnabled)

        repo.setAiFeaturesEnabled(true)
        val enabledAgain = repo.settingsFlow.first()
        assertTrue(enabledAgain.autoTitleEnabled)
        assertTrue(enabledAgain.effectiveAutoTitleEnabled)

        repo.setAutoTitleEnabled(false)
        val restored = repo.settingsFlow.first()
        assertTrue(restored.aiFeaturesEnabled)
        assertTrue(restored.effectiveSmartTagsEnabled)
        assertTrue(restored.effectiveOcrEnabled)
        assertTrue(restored.effectiveDuplicateDetectionEnabled)
        assertTrue(restored.effectiveSemanticRerankEnabled)
        assertFalse(restored.autoTitleEnabled)
        assertFalse(restored.effectiveAutoTitleEnabled)
    }
}
