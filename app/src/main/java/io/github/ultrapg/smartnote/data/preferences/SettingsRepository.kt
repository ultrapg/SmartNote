package io.github.ultrapg.smartnote.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val DARK_MODE_CONFIG = stringPreferencesKey("dark_mode_config")
        val NOTES_LAYOUT = stringPreferencesKey("notes_layout")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SMART_TAGS_ENABLED = booleanPreferencesKey("smart_tags_enabled")
        val OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
        val DUPLICATE_DETECTION_ENABLED = booleanPreferencesKey("duplicate_detection_enabled")
        val FETCH_LINK_PREVIEWS = booleanPreferencesKey("fetch_link_previews")
        val RECENT_SEARCHES_ENABLED = booleanPreferencesKey("recent_searches_enabled")
        val SEMANTIC_RERANK_ENABLED = booleanPreferencesKey("semantic_rerank_enabled")
        val AI_BACKEND_PREFERENCE = stringPreferencesKey("ai_backend_preference")
        val ENERGY_EFFICIENT_AI = booleanPreferencesKey("energy_efficient_ai")
        val BACKGROUND_ANALYSIS_ENABLED = booleanPreferencesKey("background_analysis_enabled")
        val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val LIVE_UPDATE_REMINDERS_ENABLED = booleanPreferencesKey("live_update_reminders_enabled")
        val GEMINI_INTEGRATION_ENABLED = booleanPreferencesKey("gemini_integration_enabled")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                themePalette = enumOrDefault(prefs[Keys.THEME_PALETTE], ThemePalette.DYNAMIC),
                darkModeConfig = enumOrDefault(prefs[Keys.DARK_MODE_CONFIG], DarkModeConfig.SYSTEM),
                notesLayout = enumOrDefault(prefs[Keys.NOTES_LAYOUT], NotesLayoutMode.STAGGERED_CARDS),
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                smartTagsEnabled = prefs[Keys.SMART_TAGS_ENABLED] ?: true,
                ocrEnabled = prefs[Keys.OCR_ENABLED] ?: true,
                duplicateDetectionEnabled = prefs[Keys.DUPLICATE_DETECTION_ENABLED] ?: true,
                fetchLinkPreviews = prefs[Keys.FETCH_LINK_PREVIEWS] ?: false,
                recentSearchesEnabled = prefs[Keys.REDUCED_MOTION]?.let { prefs[Keys.RECENT_SEARCHES_ENABLED] }
                    ?: (prefs[Keys.RECENT_SEARCHES_ENABLED] ?: true),
                semanticRerankEnabled = prefs[Keys.SEMANTIC_RERANK_ENABLED] ?: false,
                aiBackendPreference = enumOrDefault(
                    prefs[Keys.AI_BACKEND_PREFERENCE],
                    AiBackendPreference.AUTO
                ),
                energyEfficientAi = prefs[Keys.ENERGY_EFFICIENT_AI] ?: true,
                backgroundAnalysisEnabled = prefs[Keys.BACKGROUND_ANALYSIS_ENABLED] ?: true,
                defaultSnoozeMinutes = (prefs[Keys.DEFAULT_SNOOZE_MINUTES] ?: 10).coerceIn(1, 120),
                liveUpdateRemindersEnabled = prefs[Keys.LIVE_UPDATE_REMINDERS_ENABLED] ?: true,
                geminiIntegrationEnabled = prefs[Keys.GEMINI_INTEGRATION_ENABLED] ?: true
            )
        }
        .distinctUntilChanged()

    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { it[Keys.THEME_PALETTE] = palette.name }
    }

    suspend fun setDarkModeConfig(config: DarkModeConfig) {
        dataStore.edit { it[Keys.DARK_MODE_CONFIG] = config.name }
    }

    suspend fun setNotesLayout(layout: NotesLayoutMode) {
        dataStore.edit { it[Keys.NOTES_LAYOUT] = layout.name }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setSmartTagsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SMART_TAGS_ENABLED] = enabled }
    }

    suspend fun setOcrEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.OCR_ENABLED] = enabled }
    }

    suspend fun setDuplicateDetectionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DUPLICATE_DETECTION_ENABLED] = enabled }
    }

    suspend fun setFetchLinkPreviews(enabled: Boolean) {
        dataStore.edit { it[Keys.FETCH_LINK_PREVIEWS] = enabled }
    }

    suspend fun setRecentSearchesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.RECENT_SEARCHES_ENABLED] = enabled }
    }

    suspend fun setSemanticRerankEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SEMANTIC_RERANK_ENABLED] = enabled }
    }

    suspend fun setAiBackendPreference(preference: AiBackendPreference) {
        dataStore.edit { it[Keys.AI_BACKEND_PREFERENCE] = preference.name }
    }

    suspend fun setEnergyEfficientAi(enabled: Boolean) {
        dataStore.edit { it[Keys.ENERGY_EFFICIENT_AI] = enabled }
    }

    suspend fun setBackgroundAnalysisEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_ANALYSIS_ENABLED] = enabled }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DEFAULT_SNOOZE_MINUTES] = minutes.coerceIn(1, 120) }
    }

    suspend fun setLiveUpdateRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.LIVE_UPDATE_REMINDERS_ENABLED] = enabled }
    }

    suspend fun setGeminiIntegrationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GEMINI_INTEGRATION_ENABLED] = enabled }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
