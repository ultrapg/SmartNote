package io.github.ultrapg.smartnote.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AppLockTimeout
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.ThemePalette
import io.github.ultrapg.smartnote.security.AppLockSessionTracker
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
        assertTrue(defaults.semanticRerankEnabled)
        assertTrue(defaults.effectiveSemanticRerankEnabled)
        assertTrue(defaults.allowNewTopicTags)
        assertTrue(defaults.useGeminiNanoForOcr)
        assertTrue(defaults.effectiveUseGeminiNanoForOcr)
        assertTrue(defaults.useGeminiNanoForSearch)
        assertTrue(defaults.effectiveUseGeminiNanoForSearch)
        assertEquals(GeminiNanoFeaturePolicy.AUTO, defaults.effectiveGeminiNanoTagsPolicy)
        assertEquals(GeminiNanoFeaturePolicy.AUTO, defaults.effectiveGeminiNanoTitlesPolicy)
        assertEquals(GeminiNanoFeaturePolicy.AUTO, defaults.effectiveGeminiNanoSearchPolicy)
        assertEquals(GeminiNanoFeaturePolicy.AUTO, defaults.effectiveGeminiNanoOcrPolicy)
        assertEquals(AiBackendPreference.AUTO, defaults.aiBackendPreference)

        repo.setThemePalette(ThemePalette.AMOLED)
        repo.setDarkModeConfig(DarkModeConfig.DARK)
        repo.setNotesLayout(NotesLayoutMode.COMPACT_LIST)
        repo.setReducedMotion(true)
        repo.setAiBackendPreference(AiBackendPreference.PREFER_NPU)
        repo.setSemanticRerankEnabled(false)
        assertFalse(repo.settingsFlow.first().semanticRerankEnabled)
        assertFalse(repo.settingsFlow.first().effectiveSemanticRerankEnabled)
        repo.setSemanticRerankEnabled(true)
        repo.setAutoTitleEnabled(true)
        repo.setAiFeaturesEnabled(false)

        val masterOff = repo.settingsFlow.first()
        assertEquals(ThemePalette.AMOLED, masterOff.themePalette)
        assertEquals(DarkModeConfig.DARK, masterOff.darkModeConfig)
        assertEquals(NotesLayoutMode.COMPACT_LIST, masterOff.notesLayout)
        assertTrue(masterOff.reducedMotion)
        assertEquals(AiBackendPreference.PREFER_NPU, masterOff.aiBackendPreference)
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
        repo.setAllowNewTopicTags(false)
        repo.setUseGeminiNanoForOcr(false)
        repo.setUseGeminiNanoForSearch(false)
        val restored = repo.settingsFlow.first()
        assertTrue(restored.aiFeaturesEnabled)
        assertTrue(restored.effectiveSmartTagsEnabled)
        assertTrue(restored.effectiveOcrEnabled)
        assertTrue(restored.effectiveDuplicateDetectionEnabled)
        assertTrue(restored.effectiveSemanticRerankEnabled)
        assertFalse(restored.autoTitleEnabled)
        assertFalse(restored.effectiveAutoTitleEnabled)
        assertFalse(restored.allowNewTopicTags)
        assertFalse(restored.useGeminiNanoForOcr)
        assertFalse(restored.effectiveUseGeminiNanoForOcr)
        assertFalse(restored.useGeminiNanoForSearch)
        assertFalse(restored.effectiveUseGeminiNanoForSearch)
        assertEquals(GeminiNanoFeaturePolicy.OFF, restored.effectiveGeminiNanoSearchPolicy)
        assertEquals(GeminiNanoFeaturePolicy.OFF, restored.effectiveGeminiNanoOcrPolicy)

        repo.setGeminiNanoTagsPolicy(GeminiNanoFeaturePolicy.FORCE)
        repo.setGeminiNanoTitlesPolicy(GeminiNanoFeaturePolicy.FORCE)
        repo.setGeminiNanoSearchPolicy(GeminiNanoFeaturePolicy.FORCE)
        repo.setGeminiNanoOcrPolicy(GeminiNanoFeaturePolicy.AUTO)
        val forced = repo.settingsFlow.first()
        assertEquals(GeminiNanoFeaturePolicy.FORCE, forced.effectiveGeminiNanoTagsPolicy)
        assertEquals(GeminiNanoFeaturePolicy.FORCE, forced.effectiveGeminiNanoTitlesPolicy)
        assertEquals(GeminiNanoFeaturePolicy.FORCE, forced.effectiveGeminiNanoSearchPolicy)
        assertEquals(GeminiNanoFeaturePolicy.AUTO, forced.effectiveGeminiNanoOcrPolicy)
        assertTrue(forced.effectiveUseGeminiNanoForTitles)
        assertTrue(forced.effectiveUseGeminiNanoForSearch)
        assertTrue(forced.effectiveUseGeminiNanoForOcr)

        // App Lock settings defaults & persistence
        assertFalse(defaults.appLockEnabled)
        assertEquals(AppLockTimeout.IMMEDIATE, defaults.appLockTimeout)
        assertTrue(defaults.hideRecentsWhenLocked)

        repo.setAppLockEnabled(true)
        repo.setAppLockTimeout(AppLockTimeout.MINUTE_1)
        repo.setHideRecentsWhenLocked(false)
        val secured = repo.settingsFlow.first()
        assertTrue(secured.appLockEnabled)
        assertEquals(AppLockTimeout.MINUTE_1, secured.appLockTimeout)
        assertFalse(secured.hideRecentsWhenLocked)

        // Session tracker enforces cold-start lock, grace periods, and auth-prompt immunity
        val tracker = AppLockSessionTracker()
        val immediateSettings = AppSettings(appLockEnabled = true, appLockTimeout = AppLockTimeout.IMMEDIATE)
        assertTrue(tracker.isLocked(immediateSettings))
        tracker.markUnlocked()
        assertFalse(tracker.isLocked(immediateSettings))

        // While system biometric/PIN prompt is active, backgrounding does not relock
        tracker.setAuthPromptInFlight(true)
        tracker.onBackgrounded(immediateSettings, elapsedRealtimeMs = 1_000L)
        assertFalse(tracker.onForegrounded(immediateSettings, elapsedRealtimeMs = 2_000L))
        tracker.setAuthPromptInFlight(false)

        // Grace-period timeout check (1 min)
        val minuteSettings = AppSettings(appLockEnabled = true, appLockTimeout = AppLockTimeout.MINUTE_1)
        tracker.onBackgrounded(minuteSettings, elapsedRealtimeMs = 10_000L)
        assertFalse(tracker.onForegrounded(minuteSettings, elapsedRealtimeMs = 25_000L))
        tracker.onBackgrounded(minuteSettings, elapsedRealtimeMs = 30_000L)
        assertTrue(tracker.onForegrounded(minuteSettings, elapsedRealtimeMs = 95_000L))
        assertTrue(tracker.isLocked(minuteSettings))
    }
}
