package io.github.ultrapg.smartnote.data.preferences

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    private val dataStore: DataStore<Preferences>,
    private val bootPrefs: SharedPreferences? = null
) {
    private object Keys {
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val DARK_MODE_CONFIG = stringPreferencesKey("dark_mode_config")
        val NOTES_LAYOUT = stringPreferencesKey("notes_layout")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val AI_FEATURES_ENABLED = booleanPreferencesKey("ai_features_enabled")
        val SMART_TAGS_ENABLED = booleanPreferencesKey("smart_tags_enabled")
        val AUTO_TITLE_ENABLED = booleanPreferencesKey("auto_title_enabled")
        val OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
        val DUPLICATE_DETECTION_ENABLED = booleanPreferencesKey("duplicate_detection_enabled")
        val FETCH_LINK_PREVIEWS = booleanPreferencesKey("fetch_link_previews")
        val RECENT_SEARCHES_ENABLED = booleanPreferencesKey("recent_searches_enabled")
        val SEMANTIC_RERANK_ENABLED = booleanPreferencesKey("semantic_rerank_enabled_v2")
        val AI_BACKEND_PREFERENCE = stringPreferencesKey("ai_backend_preference")
        val ENERGY_EFFICIENT_AI = booleanPreferencesKey("energy_efficient_ai")
        val BACKGROUND_ANALYSIS_ENABLED = booleanPreferencesKey("background_analysis_enabled")
    }

    @Volatile
    var currentSnapshot: AppSettings = loadInitialSnapshot()
        private set

    private fun loadInitialSnapshot(): AppSettings {
        val prefs = bootPrefs ?: return AppSettings()
        return AppSettings(
            themePalette = enumOrDefault(prefs.getString("theme_palette", null), ThemePalette.DYNAMIC),
            darkModeConfig = enumOrDefault(prefs.getString("dark_mode_config", null), DarkModeConfig.SYSTEM),
            notesLayout = enumOrDefault(prefs.getString("notes_layout", null), NotesLayoutMode.STAGGERED_CARDS),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
            aiFeaturesEnabled = prefs.getBoolean("ai_features_enabled", true),
            autoTitleEnabled = prefs.getBoolean("auto_title_enabled_v2", false),
            semanticRerankEnabled = prefs.getBoolean("semantic_rerank_enabled_v2", true)
        )
    }

    private fun persistBootCache(snapshot: AppSettings) {
        bootPrefs?.edit()
            ?.putString("theme_palette", snapshot.themePalette.name)
            ?.putString("dark_mode_config", snapshot.darkModeConfig.name)
            ?.putString("notes_layout", snapshot.notesLayout.name)
            ?.putBoolean("reduced_motion", snapshot.reducedMotion)
            ?.putBoolean("ai_features_enabled", snapshot.aiFeaturesEnabled)
            ?.putBoolean("auto_title_enabled_v2", snapshot.autoTitleEnabled)
            ?.putBoolean("semantic_rerank_enabled_v2", snapshot.semanticRerankEnabled)
            ?.apply()
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                themePalette = enumOrDefault(prefs[Keys.THEME_PALETTE], ThemePalette.DYNAMIC),
                darkModeConfig = enumOrDefault(prefs[Keys.DARK_MODE_CONFIG], DarkModeConfig.SYSTEM),
                notesLayout = enumOrDefault(prefs[Keys.NOTES_LAYOUT], NotesLayoutMode.STAGGERED_CARDS),
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                aiFeaturesEnabled = prefs[Keys.AI_FEATURES_ENABLED] ?: true,
                smartTagsEnabled = prefs[Keys.SMART_TAGS_ENABLED] ?: true,
                autoTitleEnabled = prefs[Keys.AUTO_TITLE_ENABLED] ?: false,
                ocrEnabled = prefs[Keys.OCR_ENABLED] ?: true,
                duplicateDetectionEnabled = prefs[Keys.DUPLICATE_DETECTION_ENABLED] ?: true,
                fetchLinkPreviews = prefs[Keys.FETCH_LINK_PREVIEWS] ?: false,
                recentSearchesEnabled = prefs[Keys.RECENT_SEARCHES_ENABLED] ?: true,
                semanticRerankEnabled = prefs[Keys.SEMANTIC_RERANK_ENABLED] ?: true,
                aiBackendPreference = enumOrDefault(
                    prefs[Keys.AI_BACKEND_PREFERENCE],
                    AiBackendPreference.AUTO
                ),
                energyEfficientAi = prefs[Keys.ENERGY_EFFICIENT_AI] ?: true,
                backgroundAnalysisEnabled = prefs[Keys.BACKGROUND_ANALYSIS_ENABLED] ?: true
            ).also { mapped ->
                currentSnapshot = mapped
                persistBootCache(mapped)
            }
        }
        .distinctUntilChanged()

    suspend fun setThemePalette(palette: ThemePalette) {
        currentSnapshot = currentSnapshot.copy(themePalette = palette)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.THEME_PALETTE] = palette.name }
    }

    suspend fun setDarkModeConfig(config: DarkModeConfig) {
        currentSnapshot = currentSnapshot.copy(darkModeConfig = config)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.DARK_MODE_CONFIG] = config.name }
    }

    suspend fun setNotesLayout(layout: NotesLayoutMode) {
        currentSnapshot = currentSnapshot.copy(notesLayout = layout)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.NOTES_LAYOUT] = layout.name }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(reducedMotion = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setAiFeaturesEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(aiFeaturesEnabled = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.AI_FEATURES_ENABLED] = enabled }
    }

    suspend fun setSmartTagsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SMART_TAGS_ENABLED] = enabled }
    }

    suspend fun setAutoTitleEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(autoTitleEnabled = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.AUTO_TITLE_ENABLED] = enabled }
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
        currentSnapshot = currentSnapshot.copy(semanticRerankEnabled = enabled)
        persistBootCache(currentSnapshot)
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

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
