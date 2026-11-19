package io.github.ultrapg.smartnote.data.preferences

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AppLockTimeout
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.TagPredictionSensitivity
import io.github.ultrapg.smartnote.domain.model.ThemePalette
import io.github.ultrapg.smartnote.domain.model.TitleStylePreference
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
        val MAX_PREDICTED_TAGS = intPreferencesKey("max_predicted_tags")
        val TAG_SENSITIVITY = stringPreferencesKey("tag_sensitivity")
        val ALLOW_NEW_TOPIC_TAGS = booleanPreferencesKey("allow_new_topic_tags")
        val PREFER_EXISTING_TAGS = booleanPreferencesKey("prefer_existing_tags")
        val LEARN_FROM_USER_TAGS = booleanPreferencesKey("learn_from_user_tags")
        val USE_IMAGE_LABELING_FOR_AI = booleanPreferencesKey("use_image_labeling_for_ai")
        val USE_SYSTEM_ENTITIES_FOR_AI = booleanPreferencesKey("use_system_entities_for_ai")
        val USE_SEMANTIC_EMBEDDINGS_FOR_TAGS = booleanPreferencesKey("use_semantic_embeddings_for_tags")
        val GEMINI_NANO_MODE = stringPreferencesKey("gemini_nano_mode")
        val GEMINI_NANO_VARIANT = stringPreferencesKey("gemini_nano_variant")
        val USE_GEMINI_NANO_FOR_TITLES = booleanPreferencesKey("use_gemini_nano_for_titles")
        val USE_GEMINI_NANO_FOR_OCR = booleanPreferencesKey("use_gemini_nano_for_ocr")
        val USE_GEMINI_NANO_FOR_SEARCH = booleanPreferencesKey("use_gemini_nano_for_search")
        val GEMINI_NANO_TAGS_POLICY = stringPreferencesKey("gemini_nano_tags_policy")
        val GEMINI_NANO_TITLES_POLICY = stringPreferencesKey("gemini_nano_titles_policy")
        val GEMINI_NANO_SEARCH_POLICY = stringPreferencesKey("gemini_nano_search_policy")
        val GEMINI_NANO_OCR_POLICY = stringPreferencesKey("gemini_nano_ocr_policy")
        val TITLE_STYLE = stringPreferencesKey("title_style")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT = stringPreferencesKey("app_lock_timeout")
        val HIDE_RECENTS_WHEN_LOCKED = booleanPreferencesKey("hide_recents_when_locked")
    }

    @Volatile
    var currentSnapshot: AppSettings = loadInitialSnapshot()
        private set

    private fun loadInitialSnapshot(): AppSettings {
        val prefs = bootPrefs ?: return AppSettings()
        val legacyMode = enumOrDefault(prefs.getString("gemini_nano_mode", null), GeminiNanoMode.AUTO_HYBRID)
        val useTitles = prefs.getBoolean("use_gemini_nano_for_titles", true)
        val useOcr = prefs.getBoolean("use_gemini_nano_for_ocr", true)
        val useSearch = prefs.getBoolean("use_gemini_nano_for_search", true)
        return AppSettings(
            themePalette = enumOrDefault(prefs.getString("theme_palette", null), ThemePalette.DYNAMIC),
            darkModeConfig = enumOrDefault(prefs.getString("dark_mode_config", null), DarkModeConfig.SYSTEM),
            notesLayout = enumOrDefault(prefs.getString("notes_layout", null), NotesLayoutMode.STAGGERED_CARDS),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
            aiFeaturesEnabled = prefs.getBoolean("ai_features_enabled", true),
            autoTitleEnabled = prefs.getBoolean("auto_title_enabled_v2", false),
            semanticRerankEnabled = prefs.getBoolean("semantic_rerank_enabled_v2", true),
            maxPredictedTags = prefs.getInt("max_predicted_tags", 3).coerceIn(1, 6),
            tagSensitivity = enumOrDefault(prefs.getString("tag_sensitivity", null), TagPredictionSensitivity.BALANCED),
            allowNewTopicTags = prefs.getBoolean("allow_new_topic_tags", true),
            geminiNanoMode = legacyMode,
            geminiNanoVariant = enumOrDefault(prefs.getString("gemini_nano_variant", null), GeminiNanoVariant.FAST_FIRST),
            useGeminiNanoForTitles = useTitles,
            useGeminiNanoForOcr = useOcr,
            useGeminiNanoForSearch = useSearch,
            geminiNanoTagsPolicy = enumOrDefault(
                prefs.getString("gemini_nano_tags_policy", null),
                legacyModeToPolicy(legacyMode)
            ),
            geminiNanoTitlesPolicy = enumOrDefault(
                prefs.getString("gemini_nano_titles_policy", null),
                if (useTitles && legacyMode != GeminiNanoMode.OFF) GeminiNanoFeaturePolicy.AUTO else GeminiNanoFeaturePolicy.OFF
            ),
            geminiNanoSearchPolicy = enumOrDefault(
                prefs.getString("gemini_nano_search_policy", null),
                if (useSearch && legacyMode != GeminiNanoMode.OFF) GeminiNanoFeaturePolicy.AUTO else GeminiNanoFeaturePolicy.OFF
            ),
            geminiNanoOcrPolicy = enumOrDefault(
                prefs.getString("gemini_nano_ocr_policy", null),
                if (useOcr) legacyModeToPolicy(legacyMode) else GeminiNanoFeaturePolicy.OFF
            ),
            titleStyle = enumOrDefault(prefs.getString("title_style", null), TitleStylePreference.CONCISE),
            appLockEnabled = prefs.getBoolean("app_lock_enabled", false),
            appLockTimeout = enumOrDefault(prefs.getString("app_lock_timeout", null), AppLockTimeout.IMMEDIATE),
            hideRecentsWhenLocked = prefs.getBoolean("hide_recents_when_locked", true)
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
            ?.putInt("max_predicted_tags", snapshot.effectiveMaxPredictedTags)
            ?.putString("tag_sensitivity", snapshot.tagSensitivity.name)
            ?.putBoolean("allow_new_topic_tags", snapshot.allowNewTopicTags)
            ?.putString("gemini_nano_mode", snapshot.geminiNanoMode.name)
            ?.putString("gemini_nano_variant", snapshot.geminiNanoVariant.name)
            ?.putBoolean("use_gemini_nano_for_titles", snapshot.useGeminiNanoForTitles)
            ?.putBoolean("use_gemini_nano_for_ocr", snapshot.useGeminiNanoForOcr)
            ?.putBoolean("use_gemini_nano_for_search", snapshot.useGeminiNanoForSearch)
            ?.putString("gemini_nano_tags_policy", snapshot.geminiNanoTagsPolicy.name)
            ?.putString("gemini_nano_titles_policy", snapshot.geminiNanoTitlesPolicy.name)
            ?.putString("gemini_nano_search_policy", snapshot.geminiNanoSearchPolicy.name)
            ?.putString("gemini_nano_ocr_policy", snapshot.geminiNanoOcrPolicy.name)
            ?.putString("title_style", snapshot.titleStyle.name)
            ?.putBoolean("app_lock_enabled", snapshot.appLockEnabled)
            ?.putString("app_lock_timeout", snapshot.appLockTimeout.name)
            ?.putBoolean("hide_recents_when_locked", snapshot.hideRecentsWhenLocked)
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
                backgroundAnalysisEnabled = prefs[Keys.BACKGROUND_ANALYSIS_ENABLED] ?: true,
                maxPredictedTags = (prefs[Keys.MAX_PREDICTED_TAGS] ?: 3).coerceIn(1, 6),
                tagSensitivity = enumOrDefault(
                    prefs[Keys.TAG_SENSITIVITY],
                    TagPredictionSensitivity.BALANCED
                ),
                allowNewTopicTags = prefs[Keys.ALLOW_NEW_TOPIC_TAGS] ?: true,
                preferExistingTags = prefs[Keys.PREFER_EXISTING_TAGS] ?: true,
                learnFromUserTags = prefs[Keys.LEARN_FROM_USER_TAGS] ?: true,
                useImageLabelingForAi = prefs[Keys.USE_IMAGE_LABELING_FOR_AI] ?: true,
                useSystemEntitiesForAi = prefs[Keys.USE_SYSTEM_ENTITIES_FOR_AI] ?: true,
                useSemanticEmbeddingsForTags = prefs[Keys.USE_SEMANTIC_EMBEDDINGS_FOR_TAGS] ?: true,
                geminiNanoMode = enumOrDefault(
                    prefs[Keys.GEMINI_NANO_MODE],
                    GeminiNanoMode.AUTO_HYBRID
                ),
                geminiNanoVariant = enumOrDefault(
                    prefs[Keys.GEMINI_NANO_VARIANT],
                    GeminiNanoVariant.FAST_FIRST
                ),
                useGeminiNanoForTitles = prefs[Keys.USE_GEMINI_NANO_FOR_TITLES] ?: true,
                useGeminiNanoForOcr = prefs[Keys.USE_GEMINI_NANO_FOR_OCR] ?: true,
                useGeminiNanoForSearch = prefs[Keys.USE_GEMINI_NANO_FOR_SEARCH] ?: true,
                geminiNanoTagsPolicy = enumOrDefault(
                    prefs[Keys.GEMINI_NANO_TAGS_POLICY],
                    legacyModeToPolicy(
                        enumOrDefault(prefs[Keys.GEMINI_NANO_MODE], GeminiNanoMode.AUTO_HYBRID)
                    )
                ),
                geminiNanoTitlesPolicy = enumOrDefault(
                    prefs[Keys.GEMINI_NANO_TITLES_POLICY],
                    if ((prefs[Keys.USE_GEMINI_NANO_FOR_TITLES] ?: true) &&
                        enumOrDefault(prefs[Keys.GEMINI_NANO_MODE], GeminiNanoMode.AUTO_HYBRID) != GeminiNanoMode.OFF
                    ) {
                        GeminiNanoFeaturePolicy.AUTO
                    } else {
                        GeminiNanoFeaturePolicy.OFF
                    }
                ),
                geminiNanoSearchPolicy = enumOrDefault(
                    prefs[Keys.GEMINI_NANO_SEARCH_POLICY],
                    if ((prefs[Keys.USE_GEMINI_NANO_FOR_SEARCH] ?: true) &&
                        enumOrDefault(prefs[Keys.GEMINI_NANO_MODE], GeminiNanoMode.AUTO_HYBRID) != GeminiNanoMode.OFF
                    ) {
                        GeminiNanoFeaturePolicy.AUTO
                    } else {
                        GeminiNanoFeaturePolicy.OFF
                    }
                ),
                geminiNanoOcrPolicy = enumOrDefault(
                    prefs[Keys.GEMINI_NANO_OCR_POLICY],
                    if (prefs[Keys.USE_GEMINI_NANO_FOR_OCR] ?: true) {
                        legacyModeToPolicy(
                            enumOrDefault(prefs[Keys.GEMINI_NANO_MODE], GeminiNanoMode.AUTO_HYBRID)
                        )
                    } else {
                        GeminiNanoFeaturePolicy.OFF
                    }
                ),
                titleStyle = enumOrDefault(
                    prefs[Keys.TITLE_STYLE],
                    TitleStylePreference.CONCISE
                ),
                appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
                appLockTimeout = enumOrDefault(
                    prefs[Keys.APP_LOCK_TIMEOUT],
                    AppLockTimeout.IMMEDIATE
                ),
                hideRecentsWhenLocked = prefs[Keys.HIDE_RECENTS_WHEN_LOCKED] ?: true
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
        currentSnapshot = currentSnapshot.copy(smartTagsEnabled = enabled)
        dataStore.edit { it[Keys.SMART_TAGS_ENABLED] = enabled }
    }

    suspend fun setAutoTitleEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(autoTitleEnabled = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.AUTO_TITLE_ENABLED] = enabled }
    }

    suspend fun setOcrEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(ocrEnabled = enabled)
        dataStore.edit { it[Keys.OCR_ENABLED] = enabled }
    }

    suspend fun setDuplicateDetectionEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(duplicateDetectionEnabled = enabled)
        dataStore.edit { it[Keys.DUPLICATE_DETECTION_ENABLED] = enabled }
    }

    suspend fun setFetchLinkPreviews(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(fetchLinkPreviews = enabled)
        dataStore.edit { it[Keys.FETCH_LINK_PREVIEWS] = enabled }
    }

    suspend fun setRecentSearchesEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(recentSearchesEnabled = enabled)
        dataStore.edit { it[Keys.RECENT_SEARCHES_ENABLED] = enabled }
    }

    suspend fun setSemanticRerankEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(semanticRerankEnabled = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.SEMANTIC_RERANK_ENABLED] = enabled }
    }

    suspend fun setAiBackendPreference(preference: AiBackendPreference) {
        currentSnapshot = currentSnapshot.copy(aiBackendPreference = preference)
        dataStore.edit { it[Keys.AI_BACKEND_PREFERENCE] = preference.name }
    }

    suspend fun setEnergyEfficientAi(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(energyEfficientAi = enabled)
        dataStore.edit { it[Keys.ENERGY_EFFICIENT_AI] = enabled }
    }

    suspend fun setBackgroundAnalysisEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(backgroundAnalysisEnabled = enabled)
        dataStore.edit { it[Keys.BACKGROUND_ANALYSIS_ENABLED] = enabled }
    }

    suspend fun setMaxPredictedTags(count: Int) {
        val clamped = count.coerceIn(1, 6)
        currentSnapshot = currentSnapshot.copy(maxPredictedTags = clamped)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.MAX_PREDICTED_TAGS] = clamped }
    }

    suspend fun setTagSensitivity(sensitivity: TagPredictionSensitivity) {
        currentSnapshot = currentSnapshot.copy(tagSensitivity = sensitivity)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.TAG_SENSITIVITY] = sensitivity.name }
    }

    suspend fun setAllowNewTopicTags(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(allowNewTopicTags = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.ALLOW_NEW_TOPIC_TAGS] = enabled }
    }

    suspend fun setPreferExistingTags(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(preferExistingTags = enabled)
        dataStore.edit { it[Keys.PREFER_EXISTING_TAGS] = enabled }
    }

    suspend fun setLearnFromUserTags(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(learnFromUserTags = enabled)
        dataStore.edit { it[Keys.LEARN_FROM_USER_TAGS] = enabled }
    }

    suspend fun setUseImageLabelingForAi(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(useImageLabelingForAi = enabled)
        dataStore.edit { it[Keys.USE_IMAGE_LABELING_FOR_AI] = enabled }
    }

    suspend fun setUseSystemEntitiesForAi(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(useSystemEntitiesForAi = enabled)
        dataStore.edit { it[Keys.USE_SYSTEM_ENTITIES_FOR_AI] = enabled }
    }

    suspend fun setUseSemanticEmbeddingsForTags(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(useSemanticEmbeddingsForTags = enabled)
        dataStore.edit { it[Keys.USE_SEMANTIC_EMBEDDINGS_FOR_TAGS] = enabled }
    }

    suspend fun setGeminiNanoMode(mode: GeminiNanoMode) {
        val mapped = legacyModeToPolicy(mode)
        currentSnapshot = currentSnapshot.copy(
            geminiNanoMode = mode,
            geminiNanoTagsPolicy = mapped,
            geminiNanoOcrPolicy = if (currentSnapshot.useGeminiNanoForOcr) mapped else GeminiNanoFeaturePolicy.OFF
        )
        persistBootCache(currentSnapshot)
        dataStore.edit {
            it[Keys.GEMINI_NANO_MODE] = mode.name
            it[Keys.GEMINI_NANO_TAGS_POLICY] = mapped.name
        }
    }

    suspend fun setGeminiNanoVariant(variant: GeminiNanoVariant) {
        currentSnapshot = currentSnapshot.copy(geminiNanoVariant = variant)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.GEMINI_NANO_VARIANT] = variant.name }
    }

    suspend fun setUseGeminiNanoForTitles(enabled: Boolean) {
        setGeminiNanoTitlesPolicy(if (enabled) GeminiNanoFeaturePolicy.AUTO else GeminiNanoFeaturePolicy.OFF)
    }

    suspend fun setUseGeminiNanoForOcr(enabled: Boolean) {
        setGeminiNanoOcrPolicy(if (enabled) GeminiNanoFeaturePolicy.AUTO else GeminiNanoFeaturePolicy.OFF)
    }

    suspend fun setUseGeminiNanoForSearch(enabled: Boolean) {
        setGeminiNanoSearchPolicy(if (enabled) GeminiNanoFeaturePolicy.AUTO else GeminiNanoFeaturePolicy.OFF)
    }

    suspend fun setGeminiNanoTagsPolicy(policy: GeminiNanoFeaturePolicy) {
        updatePolicySnapshotAndStore(
            policy = policy,
            updateSnapshot = { snap, nextMode -> snap.copy(geminiNanoMode = nextMode, geminiNanoTagsPolicy = policy) },
            primaryKey = Keys.GEMINI_NANO_TAGS_POLICY,
            legacyToggleKey = null
        )
    }

    suspend fun setGeminiNanoTitlesPolicy(policy: GeminiNanoFeaturePolicy) {
        updatePolicySnapshotAndStore(
            policy = policy,
            updateSnapshot = { snap, nextMode ->
                snap.copy(
                    geminiNanoMode = nextMode,
                    geminiNanoTitlesPolicy = policy,
                    useGeminiNanoForTitles = policy != GeminiNanoFeaturePolicy.OFF
                )
            },
            primaryKey = Keys.GEMINI_NANO_TITLES_POLICY,
            legacyToggleKey = Keys.USE_GEMINI_NANO_FOR_TITLES
        )
    }

    suspend fun setGeminiNanoSearchPolicy(policy: GeminiNanoFeaturePolicy) {
        updatePolicySnapshotAndStore(
            policy = policy,
            updateSnapshot = { snap, nextMode ->
                snap.copy(
                    geminiNanoMode = nextMode,
                    geminiNanoSearchPolicy = policy,
                    useGeminiNanoForSearch = policy != GeminiNanoFeaturePolicy.OFF
                )
            },
            primaryKey = Keys.GEMINI_NANO_SEARCH_POLICY,
            legacyToggleKey = Keys.USE_GEMINI_NANO_FOR_SEARCH
        )
    }

    suspend fun setGeminiNanoOcrPolicy(policy: GeminiNanoFeaturePolicy) {
        updatePolicySnapshotAndStore(
            policy = policy,
            updateSnapshot = { snap, nextMode ->
                snap.copy(
                    geminiNanoMode = nextMode,
                    geminiNanoOcrPolicy = policy,
                    useGeminiNanoForOcr = policy != GeminiNanoFeaturePolicy.OFF
                )
            },
            primaryKey = Keys.GEMINI_NANO_OCR_POLICY,
            legacyToggleKey = Keys.USE_GEMINI_NANO_FOR_OCR
        )
    }

    private suspend fun updatePolicySnapshotAndStore(
        policy: GeminiNanoFeaturePolicy,
        updateSnapshot: (AppSettings, GeminiNanoMode) -> AppSettings,
        primaryKey: Preferences.Key<String>,
        legacyToggleKey: Preferences.Key<Boolean>?
    ) {
        val wasAllOff = currentSnapshot.geminiNanoMode == GeminiNanoMode.OFF
        val baseSnapshot = if (wasAllOff && policy != GeminiNanoFeaturePolicy.OFF) {
            currentSnapshot.copy(
                geminiNanoTagsPolicy = GeminiNanoFeaturePolicy.OFF,
                geminiNanoTitlesPolicy = GeminiNanoFeaturePolicy.OFF,
                geminiNanoSearchPolicy = GeminiNanoFeaturePolicy.OFF,
                geminiNanoOcrPolicy = GeminiNanoFeaturePolicy.OFF,
                useGeminiNanoForTitles = false,
                useGeminiNanoForOcr = false,
                useGeminiNanoForSearch = false
            )
        } else {
            currentSnapshot
        }
        val nextMode = if (policy != GeminiNanoFeaturePolicy.OFF && wasAllOff) {
            GeminiNanoMode.AUTO_HYBRID
        } else {
            currentSnapshot.geminiNanoMode
        }
        currentSnapshot = updateSnapshot(baseSnapshot, nextMode)
        persistBootCache(currentSnapshot)
        dataStore.edit { prefs ->
            if (wasAllOff && policy != GeminiNanoFeaturePolicy.OFF) {
                listOf(
                    Keys.GEMINI_NANO_TAGS_POLICY,
                    Keys.GEMINI_NANO_TITLES_POLICY,
                    Keys.GEMINI_NANO_SEARCH_POLICY,
                    Keys.GEMINI_NANO_OCR_POLICY
                ).forEach { key ->
                    if (key != primaryKey && prefs[key] == null) {
                        prefs[key] = GeminiNanoFeaturePolicy.OFF.name
                    }
                }
            }
            prefs[primaryKey] = policy.name
            if (legacyToggleKey != null) {
                prefs[legacyToggleKey] = policy != GeminiNanoFeaturePolicy.OFF
            }
            if (nextMode != enumOrDefault(prefs[Keys.GEMINI_NANO_MODE], GeminiNanoMode.AUTO_HYBRID)) {
                prefs[Keys.GEMINI_NANO_MODE] = nextMode.name
            }
        }
    }

    private fun legacyModeToPolicy(mode: GeminiNanoMode): GeminiNanoFeaturePolicy = when (mode) {
        GeminiNanoMode.OFF -> GeminiNanoFeaturePolicy.OFF
        GeminiNanoMode.AUTO_HYBRID -> GeminiNanoFeaturePolicy.AUTO
        GeminiNanoMode.ALWAYS_PREFER -> GeminiNanoFeaturePolicy.FORCE
    }

    suspend fun setTitleStyle(style: TitleStylePreference) {
        currentSnapshot = currentSnapshot.copy(titleStyle = style)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.TITLE_STYLE] = style.name }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(appLockEnabled = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockTimeout(timeout: AppLockTimeout) {
        currentSnapshot = currentSnapshot.copy(appLockTimeout = timeout)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.APP_LOCK_TIMEOUT] = timeout.name }
    }

    suspend fun setHideRecentsWhenLocked(enabled: Boolean) {
        currentSnapshot = currentSnapshot.copy(hideRecentsWhenLocked = enabled)
        persistBootCache(currentSnapshot)
        dataStore.edit { it[Keys.HIDE_RECENTS_WHEN_LOCKED] = enabled }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
