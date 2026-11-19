package io.github.ultrapg.smartnote.ui.settings

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.BuildConfig
import io.github.ultrapg.smartnote.R
import io.github.ultrapg.smartnote.ai.AiDiagnosticsState
import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.backup.BackupManager
import io.github.ultrapg.smartnote.backup.RestoreMode
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AiHardwareBackend
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.StorageStats
import io.github.ultrapg.smartnote.domain.model.TagPredictionSensitivity
import io.github.ultrapg.smartnote.domain.model.ThemePalette
import io.github.ultrapg.smartnote.domain.model.TitleStylePreference
import io.github.ultrapg.smartnote.integration.QuickNoteTileService
import io.github.ultrapg.smartnote.integration.widgets.NotesOverviewWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.PinnedNoteWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.QuickNoteWidgetReceiver
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteCardBorder
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteTonalFilterChipColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenSmartFeatures: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = maxOf(innerPadding.calculateBottomPadding(), navBottom) + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.ColorLens,
                    title = "Appearance",
                    subtitle = "Theme palette, light/dark mode, card or compact layout, motion",
                    onClick = onOpenAppearance
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "AI & Smart Features",
                    subtitle = "Auto-titles, tags, text recognition, duplicate checks & search ranking",
                    onClick = onOpenSmartFeatures
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Extension,
                    title = "System Integrations",
                    subtitle = "Home screen widgets, Quick Settings tile, app shortcuts & Sharesheet",
                    onClick = onOpenIntegrations
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Storage,
                    title = "Data",
                    subtitle = "Backups, duplicate review, recycle bin & storage usage",
                    onClick = onOpenData
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Info,
                    title = "About",
                    subtitle = "Version v${BuildConfig.VERSION_NAME}, privacy details & licenses",
                    onClick = onOpenAbout
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    SettingsSubScaffold(title = "Appearance", onBack = onBack) {
        SectionLabel("Theme")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ThemePalette.entries.forEach { palette ->
                val label = when (palette) {
                    ThemePalette.DYNAMIC -> "Dynamic Color"
                    ThemePalette.VIOLET -> "SmartNote Violet"
                    ThemePalette.NEUTRAL -> "Neutral"
                    ThemePalette.AMOLED -> "AMOLED"
                }
                FilterChip(
                    selected = settings.themePalette == palette,
                    onClick = { scope.launch { settingsRepository.setThemePalette(palette) } },
                    label = { Text(label) },
                    border = null,
                    colors = smartNoteTonalFilterChipColors()
                )
            }
        }

        SectionLabel("Color Mode")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkModeConfig.entries.forEach { mode ->
                val label = when (mode) {
                    DarkModeConfig.SYSTEM -> "Follow system"
                    DarkModeConfig.LIGHT -> "Light"
                    DarkModeConfig.DARK -> "Dark"
                }
                FilterChip(
                    selected = settings.darkModeConfig == mode,
                    onClick = { scope.launch { settingsRepository.setDarkModeConfig(mode) } },
                    label = { Text(label) },
                    border = null,
                    colors = smartNoteTonalFilterChipColors()
                )
            }
        }

        SectionLabel("Notes Layout")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.notesLayout == NotesLayoutMode.STAGGERED_CARDS,
                onClick = { scope.launch { settingsRepository.setNotesLayout(NotesLayoutMode.STAGGERED_CARDS) } },
                label = { Text("Card Layout (Staggered)") },
                border = null,
                colors = smartNoteTonalFilterChipColors()
            )
            FilterChip(
                selected = settings.notesLayout == NotesLayoutMode.COMPACT_LIST,
                onClick = { scope.launch { settingsRepository.setNotesLayout(NotesLayoutMode.COMPACT_LIST) } },
                label = { Text("Compact List") },
                border = null,
                colors = smartNoteTonalFilterChipColors()
            )
        }

        SettingsSwitchRow(
            title = "Reduced Motion",
            subtitle = "Use gentle standard fades instead of expressive springs (also honors Android animator scale)",
            checked = settings.reducedMotion,
            onCheckedChange = { scope.launch { settingsRepository.setReducedMotion(it) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartFeaturesSettingsScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    diagnostics: AiDiagnosticsState = AiDiagnosticsState(),
    onRefreshDiagnostics: () -> Unit = {},
    onPrepareGeminiNano: () -> Unit = {},
    onDownloadImageLabeling: () -> Unit = {},
    onDisableAiUnloadModel: () -> Unit = {},
    onOpenAiProcessing: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val aiEnabled = settings.aiFeaturesEnabled
    val tagsConfigEnabled = aiEnabled
    LaunchedEffect(Unit) { onRefreshDiagnostics() }

    val nanoCaps = diagnostics.geminiNanoCapabilities
    val nanoBadgeText = remember(diagnostics.capabilitiesProbed, nanoCaps) {
        when {
            !diagnostics.capabilitiesProbed -> "Checking..."
            nanoCaps.fastAvailability == GeminiNanoAvailability.AVAILABLE &&
                nanoCaps.fullAvailability == GeminiNanoAvailability.AVAILABLE -> "Ready (Fast & Full)"
            nanoCaps.fastAvailability == GeminiNanoAvailability.AVAILABLE -> "Ready (Fast)"
            nanoCaps.fullAvailability == GeminiNanoAvailability.AVAILABLE -> "Ready (Full)"
            nanoCaps.bestAvailability == GeminiNanoAvailability.DOWNLOADING -> "Downloading..."
            nanoCaps.bestAvailability == GeminiNanoAvailability.DOWNLOADABLE -> "Downloadable"
            else -> "Not supported on this device"
        }
    }
    val imgAvail = diagnostics.imageLabelingAvailability
    val imgBadgeText = when {
        !diagnostics.capabilitiesProbed -> "Checking..."
        imgAvail == ImageLabelingAvailability.AVAILABLE -> "Ready"
        imgAvail == ImageLabelingAvailability.DOWNLOADABLE -> "Downloadable"
        else -> "Not supported on this device"
    }
    val entityAvail = diagnostics.systemEntityAvailability
    val entityBadgeText = when {
        !diagnostics.capabilitiesProbed -> "Checking..."
        entityAvail == SystemEntityAvailability.AVAILABLE -> "Ready"
        else -> "Not available on this device"
    }

    SettingsSubScaffold(title = "AI & Smart Features", onBack = onBack) {
        SettingsSwitchRow(
            title = "Enable AI Features",
            subtitle = "Master switch for Auto-Title Generation, SmartTags, Text Recognition, Duplicate Detection, and Semantic Search",
            checked = aiEnabled,
            highlighted = true,
            onCheckedChange = { enabled ->
                scope.launch { settingsRepository.setAiFeaturesEnabled(enabled) }
                if (!enabled) onDisableAiUnloadModel()
            }
        )

        SectionLabel("SmartTags Configuration", enabled = aiEnabled)
        SettingsSwitchRow(
            title = "SmartTags",
            subtitle = "Suggest tags automatically in the background (when off, use Generate tags in the editor)",
            checked = settings.effectiveSmartTagsEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setSmartTagsEnabled(it) } }
        )

        SettingsOptionGroupCard(
            title = "Max tags to predict",
            subtitle = "Predict up to ${settings.effectiveMaxPredictedTags} tag(s) per note (1–6)",
            enabled = tagsConfigEnabled
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..6).forEach { count ->
                    FilterChip(
                        selected = settings.effectiveMaxPredictedTags == count,
                        enabled = tagsConfigEnabled,
                        onClick = { scope.launch { settingsRepository.setMaxPredictedTags(count) } },
                        label = { Text("$count") },
                        border = null,
                        colors = smartNoteTonalFilterChipColors()
                    )
                }
            }
        }

        val strictnessDesc = when (settings.tagSensitivity) {
            TagPredictionSensitivity.CONSERVATIVE ->
                "Strict: only high-confidence categories and clear matches"
            TagPredictionSensitivity.BALANCED ->
                "Balanced: accurate categories with low noise (Recommended)"
            TagPredictionSensitivity.BROAD ->
                "Broad: includes weaker hints and borderline matches"
        }
        SettingsOptionGroupCard(
            title = "Tag strictness",
            subtitle = strictnessDesc,
            enabled = tagsConfigEnabled
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagPredictionSensitivity.entries.forEach { sensitivity ->
                    val label = when (sensitivity) {
                        TagPredictionSensitivity.CONSERVATIVE -> "Strict"
                        TagPredictionSensitivity.BALANCED -> "Balanced"
                        TagPredictionSensitivity.BROAD -> "Broad"
                    }
                    FilterChip(
                        selected = settings.tagSensitivity == sensitivity,
                        enabled = tagsConfigEnabled,
                        onClick = { scope.launch { settingsRepository.setTagSensitivity(sensitivity) } },
                        label = { Text(label) },
                        border = null,
                        colors = smartNoteTonalFilterChipColors()
                    )
                }
            }
        }

        SettingsSwitchRow(
            title = "Prefer your existing tags",
            subtitle = "Prioritize matching tags already in your library instead of introducing new categories",
            checked = tagsConfigEnabled && settings.preferExistingTags,
            enabled = tagsConfigEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setPreferExistingTags(it) } }
        )
        SettingsSwitchRow(
            title = "Learn from your tagged notes",
            subtitle = "Use patterns from notes you manually tagged to suggest matching personal tags",
            checked = tagsConfigEnabled && settings.learnFromUserTags,
            enabled = tagsConfigEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setLearnFromUserTags(it) } }
        )
        SettingsSwitchRow(
            title = "Allow new topic tags",
            subtitle = "Let SmartNote propose specific new topic tags when curated categories don't fully describe a note",
            checked = tagsConfigEnabled && settings.allowNewTopicTags,
            enabled = tagsConfigEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setAllowNewTopicTags(it) } }
        )
        SettingsSwitchRow(
            title = "Semantic tag matching",
            subtitle = "Use LiteRT embeddings to reinforce category and personal tag affinity",
            checked = tagsConfigEnabled && settings.useSemanticEmbeddingsForTags,
            enabled = tagsConfigEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setUseSemanticEmbeddingsForTags(it) } }
        )

        SectionLabel("Title Generation", enabled = aiEnabled)
        SettingsSwitchRow(
            title = "Auto-Title Generation",
            subtitle = "Automatically title untitled notes and shared screenshots (when off, use Generate in the editor)",
            checked = settings.effectiveAutoTitleEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setAutoTitleEnabled(it) } }
        )
        SettingsOptionGroupCard(
            title = "Title style",
            subtitle = if (settings.titleStyle == TitleStylePreference.CONCISE) {
                "Concise: tight headline up to 44 characters"
            } else {
                "Descriptive: fuller summary phrase up to 64 characters"
            },
            enabled = aiEnabled
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TitleStylePreference.entries.forEach { style ->
                    FilterChip(
                        selected = settings.titleStyle == style,
                        enabled = aiEnabled,
                        onClick = { scope.launch { settingsRepository.setTitleStyle(style) } },
                        label = {
                            Text(if (style == TitleStylePreference.CONCISE) "Concise" else "Descriptive")
                        },
                        border = null,
                        colors = smartNoteTonalFilterChipColors()
                    )
                }
            }
        }

        SectionLabel("On-Device Models & Engines (Auto-Detected)", enabled = aiEnabled)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (aiEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
            },
            border = smartNoteCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val contentAlpha = if (aiEnabled) 1f else 0.38f
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gemini Nano",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = nanoBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                when {
                    diagnostics.capabilitiesProbed && !nanoCaps.isSupportedOnDevice -> {
                        Text(
                            text = "Not supported on this device (Android AICore / Gemini Nano unavailable). SmartNote automatically uses its local classifier, LiteRT embeddings, and entity rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                    }
                    diagnostics.capabilitiesProbed && nanoCaps.bestAvailability == GeminiNanoAvailability.DOWNLOADABLE -> {
                        Text(
                            text = "Gemini Nano is supported by your device and ready to download via Android AICore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                        OutlinedButton(
                            enabled = aiEnabled,
                            onClick = {
                                onPrepareGeminiNano()
                                onRefreshDiagnostics()
                            }
                        ) {
                            Text("Download Gemini Nano")
                        }
                    }
                    diagnostics.capabilitiesProbed && nanoCaps.bestAvailability == GeminiNanoAvailability.DOWNLOADING -> {
                        Text(
                            text = "Downloading Gemini Nano model via Android AICore... Controls will activate automatically once ready.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                        OutlinedButton(
                            enabled = aiEnabled,
                            onClick = onRefreshDiagnostics
                        ) {
                            Text("Refresh status")
                        }
                    }
                    else -> {
                        val nanoModeDesc = when (settings.geminiNanoMode) {
                            GeminiNanoMode.AUTO_HYBRID ->
                                "Smart Hybrid: uses fast local rules first and asks Gemini Nano only when unsure"
                            GeminiNanoMode.ALWAYS_PREFER ->
                                "Prefer Nano: runs Gemini Nano together with local rules on notes"
                            GeminiNanoMode.OFF ->
                                "Off: disables Gemini Nano inference"
                        }
                        Text(
                            text = nanoModeDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GeminiNanoMode.entries.forEach { mode ->
                                val label = when (mode) {
                                    GeminiNanoMode.AUTO_HYBRID -> "Smart Hybrid"
                                    GeminiNanoMode.ALWAYS_PREFER -> "Prefer Nano"
                                    GeminiNanoMode.OFF -> "Off"
                                }
                                FilterChip(
                                    selected = settings.geminiNanoMode == mode,
                                    enabled = aiEnabled,
                                    onClick = { scope.launch { settingsRepository.setGeminiNanoMode(mode) } },
                                    label = { Text(label) },
                                    border = null,
                                    colors = smartNoteTonalFilterChipColors()
                                )
                            }
                        }

                        val variantEnabled = aiEnabled && settings.geminiNanoMode != GeminiNanoMode.OFF
                        Text(
                            text = "Model variant",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fastSupported = !diagnostics.capabilitiesProbed || nanoCaps.isFastSupported
                            val fullSupported = !diagnostics.capabilitiesProbed || nanoCaps.isFullSupported
                            FilterChip(
                                selected = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST,
                                enabled = variantEnabled && fastSupported,
                                onClick = {
                                    scope.launch { settingsRepository.setGeminiNanoVariant(GeminiNanoVariant.FAST_FIRST) }
                                },
                                label = {
                                    Text(if (fastSupported) "Fast (Recommended)" else "Fast (Unsupported)")
                                },
                                border = null,
                                colors = smartNoteTonalFilterChipColors()
                            )
                            FilterChip(
                                selected = settings.geminiNanoVariant == GeminiNanoVariant.FULL_QUALITY,
                                enabled = variantEnabled && fullSupported,
                                onClick = {
                                    scope.launch { settingsRepository.setGeminiNanoVariant(GeminiNanoVariant.FULL_QUALITY) }
                                },
                                label = {
                                    Text(if (fullSupported) "Full" else "Full (Unsupported)")
                                },
                                border = null,
                                colors = smartNoteTonalFilterChipColors()
                            )
                        }

                        NanoCardToggleRow(
                            title = "Use Gemini Nano for titles",
                            subtitle = "Synthesize titles from note text and attached images with Gemini Nano",
                            checked = settings.useGeminiNanoForTitles,
                            enabled = variantEnabled,
                            onCheckedChange = {
                                scope.launch { settingsRepository.setUseGeminiNanoForTitles(it) }
                            }
                        )
                        NanoCardToggleRow(
                            title = "Use Gemini Nano for OCR",
                            subtitle = "Read hard-to-scan images and refine ML Kit text recognition with Gemini Nano",
                            checked = settings.useGeminiNanoForOcr && settings.ocrEnabled,
                            enabled = variantEnabled && settings.ocrEnabled,
                            onCheckedChange = {
                                scope.launch { settingsRepository.setUseGeminiNanoForOcr(it) }
                            }
                        )
                        NanoCardToggleRow(
                            title = "Use Gemini Nano for Search",
                            subtitle = "Understand natural queries and synonyms with Gemini Nano, falling back to local search automatically",
                            checked = settings.useGeminiNanoForSearch,
                            enabled = variantEnabled,
                            onCheckedChange = {
                                scope.launch { settingsRepository.setUseGeminiNanoForSearch(it) }
                            }
                        )
                    }
                }
            }
        }

        val imageLabelingSupported = !diagnostics.capabilitiesProbed || imgAvail != ImageLabelingAvailability.UNAVAILABLE
        val imageLabelingToggleEnabled = aiEnabled && imageLabelingSupported
        SettingsBadgeSwitchRow(
            title = "Image Labeling (ML Kit)",
            subtitle = if (imageLabelingSupported) {
                "Detect visual objects and scenes in photos and screenshots for titles and tags"
            } else {
                "Not supported on this device (Play Services ML Kit module unavailable)"
            },
            badge = imgBadgeText,
            checked = settings.effectiveUseImageLabeling && imageLabelingSupported,
            enabled = imageLabelingToggleEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setUseImageLabelingForAi(it) } },
            action = if (imgAvail == ImageLabelingAvailability.DOWNLOADABLE && aiEnabled) {
                {
                    OutlinedButton(
                        onClick = {
                            onDownloadImageLabeling()
                            onRefreshDiagnostics()
                        }
                    ) {
                        Text("Download module")
                    }
                }
            } else {
                null
            }
        )

        val entitySupported = !diagnostics.capabilitiesProbed || entityAvail == SystemEntityAvailability.AVAILABLE
        SettingsBadgeSwitchRow(
            title = "System Entity Recognition",
            subtitle = if (entitySupported) {
                "Detect flights, tracking codes, addresses, and contact info via Android TextClassifier"
            } else {
                "Android TextClassifier is unavailable on this device"
            },
            badge = entityBadgeText,
            checked = settings.effectiveUseSystemEntities && entitySupported,
            enabled = aiEnabled && entitySupported,
            onCheckedChange = { scope.launch { settingsRepository.setUseSystemEntitiesForAi(it) } }
        )

        SettingsSwitchRow(
            title = "Text Recognition (OCR)",
            subtitle = "Extract searchable text from shared screenshots and photos",
            checked = settings.effectiveOcrEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setOcrEnabled(it) } }
        )

        SectionLabel("Other Smart Features", enabled = aiEnabled)
        SettingsSwitchRow(
            title = "Duplicate Detection",
            subtitle = "Highlight matching or near-duplicate notes and images for manual review",
            checked = settings.effectiveDuplicateDetectionEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setDuplicateDetectionEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Semantic Search",
            subtitle = "Understand meaning and related topics alongside exact keyword matches",
            checked = settings.effectiveSemanticRerankEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setSemanticRerankEnabled(it) } }
        )

        SectionLabel("Search & Privacy")
        SettingsSwitchRow(
            title = "Save Recent Searches",
            subtitle = "Keep a quick history of your recent searches",
            checked = settings.recentSearchesEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setRecentSearchesEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Fetch Link Previews (Network Opt-In)",
            subtitle = "Optional: fetch webpage titles only for links you save; note text stays private. Off by default.",
            checked = settings.fetchLinkPreviews,
            onCheckedChange = { scope.launch { settingsRepository.setFetchLinkPreviews(it) } }
        )
        SettingsSwitchRow(
            title = "Background Analysis",
            subtitle = "Update search indexing, tags, and text recognition in the background after saving",
            checked = settings.backgroundAnalysisEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setBackgroundAnalysisEnabled(it) } }
        )
        SettingsCategoryCard(
            icon = Icons.Rounded.Memory,
            title = "AI Hardware & Diagnostics",
            subtitle = "Choose NPU, GPU, or CPU execution and inspect live engine availability",
            enabled = aiEnabled,
            onClick = onOpenAiProcessing
        )
    }
}

@Composable
fun AiProcessingSettingsScreen(
    settings: AppSettings,
    diagnostics: AiDiagnosticsState,
    settingsRepository: SettingsRepository,
    onRefreshDiagnostics: () -> Unit,
    onRunSelfTest: () -> Unit,
    onUnloadModel: () -> Unit,
    onPrepareGeminiNano: () -> Unit = {},
    onDownloadImageLabeling: () -> Unit = {},
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val aiEnabled = settings.aiFeaturesEnabled
    LaunchedEffect(Unit) { onRefreshDiagnostics() }

    SettingsSubScaffold(title = "AI Hardware & Diagnostics", onBack = onBack) {
        if (!aiEnabled) {
            SettingsSwitchRow(
                title = "Enable AI Features",
                subtitle = "AI features are currently off. Turn on to re-enable Semantic Search, Auto-Titles, SmartTags, Text Recognition, and Duplicate Detection",
                checked = false,
                highlighted = true,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setAiFeaturesEnabled(enabled) }
                }
            )
        }
        SectionLabel("Hardware Acceleration Preference", enabled = aiEnabled)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (aiEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
            },
            border = smartNoteCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val contentAlpha = if (aiEnabled) 1f else 0.38f
            val npuSupported = !diagnostics.capabilitiesProbed || diagnostics.npuDeviceSupported
            val gpuSupported = !diagnostics.capabilitiesProbed || AiHardwareBackend.GPU in diagnostics.detectedAccelerators
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                AiBackendPreference.entries.forEach { pref ->
                    val optionSupported = when (pref) {
                        AiBackendPreference.PREFER_NPU -> npuSupported || settings.aiBackendPreference == pref
                        AiBackendPreference.PREFER_GPU -> gpuSupported || settings.aiBackendPreference == pref
                        AiBackendPreference.AUTO,
                        AiBackendPreference.CPU_ONLY -> true
                    }
                    val optionEnabled = aiEnabled && optionSupported
                    val optionAlpha = if (optionEnabled) contentAlpha else 0.38f
                    val title = when (pref) {
                        AiBackendPreference.AUTO -> "Automatic (Recommended)"
                        AiBackendPreference.PREFER_NPU ->
                            if (npuSupported) "Prefer NPU" else "Prefer NPU (Not supported)"
                        AiBackendPreference.PREFER_GPU ->
                            if (gpuSupported) "Prefer GPU" else "Prefer GPU (Not supported)"
                        AiBackendPreference.CPU_ONLY -> "CPU Only"
                    }
                    val desc = when (pref) {
                        AiBackendPreference.AUTO -> "Prioritizes low-power NPU first, then GPU, then CPU for semantic features"
                        AiBackendPreference.PREFER_NPU -> "Prioritizes energy-efficient NPU hardware dispatch when available"
                        AiBackendPreference.PREFER_GPU -> "Uses GPU acceleration (skipped when Energy-Efficient throttling is active)"
                        AiBackendPreference.CPU_ONLY -> "Forces CPU execution strictly; disables both NPU and GPU delegates"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = optionEnabled) {
                                scope.launch {
                                    settingsRepository.setAiBackendPreference(pref)
                                    onRefreshDiagnostics()
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.aiBackendPreference == pref,
                            enabled = optionEnabled,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setAiBackendPreference(pref)
                                    onRefreshDiagnostics()
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = optionAlpha)
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = optionAlpha)
                            )
                        }
                    }
                }
            }
        }

        SettingsSwitchRow(
            title = "Energy-Efficient Processing",
            subtitle = "Prioritize low-power NPU and skip power-hungry GPU dispatch during Battery Saver or elevated thermal load, plus unload idle models faster",
            checked = aiEnabled && settings.energyEfficientAi,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setEnergyEfficientAi(it) } }
        )

        SettingsSwitchRow(
            title = "Semantic Search Ranking",
            subtitle = "Blend meaning-based similarity into search results alongside keyword matching",
            checked = settings.effectiveSemanticRerankEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setSemanticRerankEnabled(it) } }
        )

        SectionLabel("Honest Hardware Diagnostics")
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = smartNoteCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val nanoCaps = diagnostics.geminiNanoCapabilities
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagRow("SoC / Platform", diagnostics.socDescription)
                DiagRow("OCR Engine", diagnostics.ocrBackend.name)
                DiagRow("Image Labeling", formatImageLabelDiag(diagnostics.capabilitiesProbed, diagnostics.imageLabelingAvailability))
                DiagRow("System Entity Recognition", formatEntityDiag(diagnostics.capabilitiesProbed, diagnostics.systemEntityAvailability))
                DiagRow("Gemini Nano (Fast)", formatNanoDiag(diagnostics.capabilitiesProbed, nanoCaps.fastAvailability))
                DiagRow("Gemini Nano (Full)", formatNanoDiag(diagnostics.capabilitiesProbed, nanoCaps.fullAvailability))
                nanoCaps.baseModelName?.let { baseName ->
                    DiagRow("Gemini Base Model", baseName)
                }
                DiagRow("Lexical Search", diagnostics.lexicalSearchBackend.name)
                DiagRow("Active LiteRT Backend", diagnostics.activeNeuralBackend.name)
                DiagRow(
                    "Detected Accelerators",
                    diagnostics.detectedAccelerators.joinToString(", ").ifBlank { "CPU" }
                )
                DiagRow(
                    "NPU Support / Ready",
                    "${diagnostics.npuDeviceSupported} / ${diagnostics.npuRuntimeReady}"
                )
                DiagRow(
                    "Energy-Saving Mode",
                    when {
                        !settings.energyEfficientAi -> "Off"
                        diagnostics.energySavingActive -> "Active (NPU prioritized, GPU skipped)"
                        else -> "Standby (NPU first; skips GPU on Battery Saver)"
                    }
                )
                DiagRow("Model Loaded", if (diagnostics.isModelLoaded) "Yes (${diagnostics.lastInferenceMs} ms)" else "Unloaded (lazy)")
                diagnostics.fallbackReason?.let { reason ->
                    Text(
                        text = "Note: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        if (diagnostics.geminiNanoCapabilities.bestAvailability == GeminiNanoAvailability.DOWNLOADABLE && aiEnabled) {
            OutlinedButton(
                onClick = {
                    onPrepareGeminiNano()
                    onRefreshDiagnostics()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Prepare Gemini Nano")
            }
        }
        if (diagnostics.imageLabelingAvailability == ImageLabelingAvailability.DOWNLOADABLE && aiEnabled) {
            OutlinedButton(
                onClick = {
                    onDownloadImageLabeling()
                    onRefreshDiagnostics()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download Image Labeling Module")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRunSelfTest,
                enabled = aiEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text("Verify Backend")
            }
            OutlinedButton(onClick = onUnloadModel, modifier = Modifier.weight(1f)) {
                Text("Unload Model")
            }
        }
    }
}

private fun formatNanoDiag(probed: Boolean, avail: GeminiNanoAvailability): String = when {
    !probed -> "Checking..."
    avail == GeminiNanoAvailability.AVAILABLE -> "Ready"
    avail == GeminiNanoAvailability.DOWNLOADABLE -> "Available to download"
    avail == GeminiNanoAvailability.DOWNLOADING -> "Downloading..."
    else -> "Not supported on this device"
}

private fun formatImageLabelDiag(probed: Boolean, avail: ImageLabelingAvailability): String = when {
    !probed -> "Checking..."
    avail == ImageLabelingAvailability.AVAILABLE -> "Ready (ML Kit)"
    avail == ImageLabelingAvailability.DOWNLOADABLE -> "Downloadable (Play Services)"
    else -> "Not supported on this device"
}

private fun formatEntityDiag(probed: Boolean, avail: SystemEntityAvailability): String = when {
    !probed -> "Checking..."
    avail == SystemEntityAvailability.AVAILABLE -> "Ready (Android TextClassifier)"
    else -> "Not available on this device"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntegrationsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appWidgetManager = remember(context) {
        runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
    }
    val canPinWidgets = remember(appWidgetManager) {
        runCatching { appWidgetManager?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)
    }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    SettingsSubScaffold(title = "System Integrations", onBack = onBack) {
        SectionLabel("System Shell Features")
        actionFeedback?.let { msg ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        SystemIntegrationCard(
            icon = Icons.Rounded.Bolt,
            title = "Quick Settings Tile",
            badge = "Active",
            description = "Add the Quick Note tile to your Android notification shade for 1-tap note creation from any screen.",
            actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val sbm = context.getSystemService(StatusBarManager::class.java)
                                sbm?.requestAddTileService(
                                    ComponentName(context, QuickNoteTileService::class.java),
                                    context.getString(R.string.qs_tile_label),
                                    AndroidIcon.createWithResource(context, R.drawable.ic_qs_new_note),
                                    context.mainExecutor
                                ) { status ->
                                    actionFeedback = when (status) {
                                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                                            "Quick Note tile added to Quick Settings"
                                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                                            "Quick Note tile is already in Quick Settings"
                                        else -> null
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Add to Quick Settings")
                    }
                }
            } else {
                null
            }
        )

        SystemIntegrationCard(
            icon = Icons.Rounded.Widgets,
            title = "Home Screen Widgets",
            badge = "3 Widgets",
            description = "Three Material 3 Glance widgets: Quick Capture, Pinned Note, and Notes Overview.",
            actions = if (canPinWidgets) {
                {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pinned = runCatching {
                                    appWidgetManager?.requestPinAppWidget(
                                        ComponentName(context, QuickNoteWidgetReceiver::class.java),
                                        null,
                                        null
                                    ) == true
                                }.getOrDefault(false)
                                if (!pinned) {
                                    actionFeedback = "Open your launcher's widget picker to place SmartNote widgets"
                                }
                            }
                        ) { Text("Pin Quick Capture") }
                        OutlinedButton(
                            onClick = {
                                val pinned = runCatching {
                                    appWidgetManager?.requestPinAppWidget(
                                        ComponentName(context, NotesOverviewWidgetReceiver::class.java),
                                        null,
                                        null
                                    ) == true
                                }.getOrDefault(false)
                                if (!pinned) {
                                    actionFeedback = "Open your launcher's widget picker to place SmartNote widgets"
                                }
                            }
                        ) { Text("Pin Overview") }
                        OutlinedButton(
                            onClick = {
                                val pinned = runCatching {
                                    appWidgetManager?.requestPinAppWidget(
                                        ComponentName(context, PinnedNoteWidgetReceiver::class.java),
                                        null,
                                        null
                                    ) == true
                                }.getOrDefault(false)
                                if (!pinned) {
                                    actionFeedback = "Open your launcher's widget picker to place SmartNote widgets"
                                }
                            }
                        ) { Text("Pin Pinned Note") }
                    }
                }
            } else {
                null
            }
        )

        SystemIntegrationCard(
            icon = Icons.Rounded.AppShortcut,
            title = "Launcher App Shortcuts",
            badge = "Active",
            description = "Long-press the SmartNote app icon on your home screen for New Note, New Checklist, SmartSearch, and dynamic shortcuts to your pinned notes."
        )

        SystemIntegrationCard(
            icon = Icons.Rounded.Share,
            title = "Android Sharesheet",
            badge = "Active",
            description = "Share text, links, screenshots, or multiple images from any app straight into SmartNote."
        )
    }
}

@Composable
fun DataSettingsScreen(
    backupManager: BackupManager,
    storageStats: StorageStats?,
    onRefreshStats: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmReplaceAll by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { onRefreshStats() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { backupManager.exportToUri(uri) }
                    .onSuccess { res ->
                        snackbarHostState.showSnackbar(
                            "Exported ${res.noteCount} note(s) and ${res.attachmentCount} attachment(s)"
                        )
                    }
                    .onFailure { err ->
                        snackbarHostState.showSnackbar("Backup export failed: ${err.message}")
                    }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Data & Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = maxOf(innerPadding.calculateBottomPadding(), navBottom) + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val stamp = remember {
                    SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                }
                SettingsCategoryCard(
                    icon = Icons.Rounded.Backup,
                    title = "Export Backup (.snbackup)",
                    subtitle = "Save a verified backup archive with notes, tags, and images",
                    onClick = { exportLauncher.launch("smartnote-backup-$stamp.snbackup") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Restore,
                    title = "Import / Restore Backup",
                    subtitle = "Merge with existing notes or restore from a saved archive",
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Review Duplicates",
                    subtitle = "Review matching or near-duplicate notes and attachments",
                    onClick = onOpenDuplicates
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Recycle Bin",
                    subtitle = "${storageStats?.recycleBinCount ?: 0} note(s) awaiting 30-day expiry",
                    onClick = onOpenRecycleBin
                )
            }
            item {
                SectionLabel("Storage Usage")
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = smartNoteCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagRow("Active Notes", "${storageStats?.noteCount ?: 0}")
                        DiagRow("Archived Notes", "${storageStats?.archivedCount ?: 0}")
                        DiagRow("Attachments (${storageStats?.attachmentCount ?: 0})", formatBytes(storageStats?.attachmentsBytes ?: 0L))
                        DiagRow("Thumbnails Cache", formatBytes(storageStats?.thumbnailsBytes ?: 0L))
                        DiagRow("Notes Database", formatBytes(storageStats?.databaseBytes ?: 0L))
                    }
                }
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Mode") },
            text = {
                Text("Merge adds backup notes while preserving your current notes. Replace All overwrites existing notes after verifying archive checksums.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        scope.launch {
                            runCatching { backupManager.restoreFromUri(uri, RestoreMode.MERGE) }
                                .onSuccess { res ->
                                    onRefreshStats()
                                    snackbarHostState.showSnackbar(
                                        "Merged: ${res.notesImported} imported, ${res.notesSkippedOrMerged} existing/resolved"
                                    )
                                }
                                .onFailure { err ->
                                    snackbarHostState.showSnackbar("Restore failed: ${err.message}")
                                }
                        }
                    }
                ) { Text("Merge (Safe)") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            pendingRestoreUri = null
                            confirmReplaceAll = uri
                        }
                    ) { Text("Replace All", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    confirmReplaceAll?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmReplaceAll = null },
            title = { Text("Confirm Replace All?") },
            text = { Text("This deletes your current local notes and replaces them with the selected backup.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplaceAll = null
                        scope.launch {
                            runCatching { backupManager.restoreFromUri(uri, RestoreMode.REPLACE_ALL) }
                                .onSuccess { res ->
                                    onRefreshStats()
                                    snackbarHostState.showSnackbar("Restored ${res.notesImported} notes (replaced)")
                                }
                                .onFailure { err ->
                                    snackbarHostState.showSnackbar("Replace failed: ${err.message}")
                                }
                        }
                    }
                ) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplaceAll = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsSubScaffold(title = "About SmartNote", onBack = onBack) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = smartNoteCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("SmartNote v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "A fast, private notes app for Android built with Kotlin, Jetpack Compose, and Material 3.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                SectionLabel("Privacy")
                Text(
                    text = "• No accounts, cloud sync, ads, or analytics.\n" +
                        "• Image text recognition, auto-tags, titles, and search run directly on your device.\n" +
                        "• Network access is used only if you opt into Link Previews for saved URLs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel("Open-Source Stack & Licenses")
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = smartNoteCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("• AndroidX Compose, Material 3 Expressive, Room, Bundled SQLite, DataStore, WorkManager, Glance (Apache-2.0)", style = MaterialTheme.typography.bodySmall)
                Text("• Google AI Edge LiteRT (Apache-2.0) & Google ML Kit Text Recognition (Google APIs ToS)", style = MaterialTheme.typography.bodySmall)
                Text("• Coil Image Loading & OkHttp (Apache-2.0)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = maxOf(innerPadding.calculateBottomPadding(), navBottom) + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        shape = shape,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        },
        border = smartNoteCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
    }
}

@Composable
private fun SettingsOptionGroupCard(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        shape = shape,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        },
        border = smartNoteCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
            content()
        }
    }
}

@Composable
private fun SettingsBadgeSwitchRow(
    title: String,
    subtitle: String,
    badge: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        shape = shape,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        },
        border = smartNoteCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                    )
                }
                Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        shape = shape,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
            highlighted && checked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = smartNoteCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun NanoCardToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    enabled: Boolean = true
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SystemIntegrationCard(
    icon: ImageVector,
    title: String,
    badge: String,
    description: String,
    actions: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = smartNoteCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actions?.invoke()
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.44f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.56f)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
