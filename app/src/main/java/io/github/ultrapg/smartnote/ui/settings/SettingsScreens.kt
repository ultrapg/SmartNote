package io.github.ultrapg.smartnote.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
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
import androidx.compose.material.icons.rounded.Watch
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import io.github.ultrapg.smartnote.backup.BackupManager
import io.github.ultrapg.smartnote.backup.RestoreMode
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.StorageStats
import io.github.ultrapg.smartnote.domain.model.ThemePalette
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
    onOpenReminders: () -> Unit,
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
                    subtitle = "Enable AI Features, Auto-Titles, SmartTags, Offline OCR, Duplicates & Semantic Search",
                    onClick = onOpenSmartFeatures
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Alarm,
                    title = "Reminders",
                    subtitle = "Notifications, exact alarms, snooze defaults, Live Updates",
                    onClick = onOpenReminders
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
                    subtitle = "SAF ZIP backup & restore, duplicate review, recycle bin, storage usage",
                    onClick = onOpenData
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Info,
                    title = "About",
                    subtitle = "SmartNote v${BuildConfig.VERSION_NAME}, offline privacy guarantee & licenses",
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

@Composable
fun SmartFeaturesSettingsScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    onDisableAiUnloadModel: () -> Unit = {},
    onOpenAiProcessing: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val aiEnabled = settings.aiFeaturesEnabled
    SettingsSubScaffold(title = "AI & Smart Features", onBack = onBack) {
        SettingsSwitchRow(
            title = "Enable AI Features",
            subtitle = "Master switch for Auto-Title Generation, SmartTags, Offline OCR, Duplicate Detection, and LiteRT Semantic Search",
            checked = aiEnabled,
            highlighted = true,
            onCheckedChange = { enabled ->
                scope.launch { settingsRepository.setAiFeaturesEnabled(enabled) }
                if (!enabled) onDisableAiUnloadModel()
            }
        )
        SectionLabel("Individual AI Features", enabled = aiEnabled)
        SettingsSwitchRow(
            title = "Auto-Title Generation",
            subtitle = "Automatically title untitled notes and shared screenshots (when off, use the on-demand Generate button in the editor)",
            checked = settings.effectiveAutoTitleEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setAutoTitleEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "SmartTags",
            subtitle = "Suggest tags automatically using local rules, TF-IDF, and your personal tag vocabulary",
            checked = settings.effectiveSmartTagsEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setSmartTagsEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Offline OCR",
            subtitle = "Extract searchable text from shared screenshots and images on-device via bundled ML Kit",
            checked = settings.effectiveOcrEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setOcrEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Duplicate Detection",
            subtitle = "Flag exact or near-duplicate notes and attachments for manual review",
            checked = settings.effectiveDuplicateDetectionEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setDuplicateDetectionEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Hybrid Semantic Search (LiteRT)",
            subtitle = "Blend local on-device text embedding similarity into SmartSearch results",
            checked = settings.effectiveSemanticRerankEnabled,
            enabled = aiEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setSemanticRerankEnabled(it) } }
        )

        SectionLabel("Search & Privacy")
        SettingsSwitchRow(
            title = "Save Recent Searches",
            subtitle = "Store recent SmartSearch queries locally on device",
            checked = settings.recentSearchesEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setRecentSearchesEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Fetch Link Previews (Network Opt-In)",
            subtitle = "Optional: fetch page titles ONLY for saved URLs. Never transmits private note text. Default OFF.",
            checked = settings.fetchLinkPreviews,
            onCheckedChange = { scope.launch { settingsRepository.setFetchLinkPreviews(it) } }
        )
        SettingsSwitchRow(
            title = "Background Analysis",
            subtitle = "Run indexing and enabled smart analysis asynchronously after saving",
            checked = settings.backgroundAnalysisEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setBackgroundAnalysisEnabled(it) } }
        )
        SettingsCategoryCard(
            icon = Icons.Rounded.Memory,
            title = "AI Hardware & Diagnostics",
            subtitle = "Configure NPU / GPU / CPU preferences, energy mode, and view active LiteRT backend",
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
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val aiEnabled = settings.aiFeaturesEnabled
    LaunchedEffect(Unit) { onRefreshDiagnostics() }

    SettingsSubScaffold(title = "AI Hardware & Diagnostics", onBack = onBack) {
        if (!aiEnabled) {
            SettingsSwitchRow(
                title = "Enable AI Features",
                subtitle = "AI features are currently off. Turn on to re-enable LiteRT semantic search, Auto-Titles, SmartTags, OCR, and duplicate detection",
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
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                AiBackendPreference.entries.forEach { pref ->
                    val title = when (pref) {
                        AiBackendPreference.AUTO -> "Automatic (Recommended)"
                        AiBackendPreference.PREFER_NPU -> "Prefer NPU"
                        AiBackendPreference.PREFER_GPU -> "Prefer GPU"
                        AiBackendPreference.CPU_ONLY -> "CPU Only"
                    }
                    val desc = when (pref) {
                        AiBackendPreference.AUTO -> "Prioritizes low-power NPU first, then GPU, then CPU for LiteRT; bundled ML Kit for OCR"
                        AiBackendPreference.PREFER_NPU -> "Prioritizes energy-efficient NPU hardware dispatch when ready; falls back cleanly"
                        AiBackendPreference.PREFER_GPU -> "Prefers OpenCL/GLES GPU execution (skipped when Energy-Efficient throttling is active)"
                        AiBackendPreference.CPU_ONLY -> "Forces CPU execution strictly; disables both NPU and GPU delegates"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = aiEnabled) {
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
                            enabled = aiEnabled,
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
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
            title = "Hybrid Semantic Re-Ranking (LiteRT)",
            subtitle = "Blend local compact text-embedding similarity into FTS5 SmartSearch results (enabled by default)",
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
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagRow("SoC / Platform", diagnostics.socDescription)
                DiagRow("OCR Engine", diagnostics.ocrBackend.name)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsScreen(
    settings: AppSettings,
    canScheduleExactAlarms: Boolean,
    canPostLiveUpdates: Boolean,
    settingsRepository: SettingsRepository,
    onLiveUpdateSettingChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkNotifGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    fun checkExactAlarmGranted(): Boolean {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return canScheduleExactAlarms
        return AlarmManagerCompat.canScheduleExactAlarms(mgr) || canScheduleExactAlarms
    }

    var hasNotifPermission by remember { mutableStateOf(checkNotifGranted()) }
    var effectiveExactAlarms by remember { mutableStateOf(checkExactAlarmGranted()) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || checkNotifGranted()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = checkNotifGranted()
                effectiveExactAlarms = checkExactAlarmGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSubScaffold(title = "Reminders", onBack = onBack) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = smartNoteCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DiagRow("Notifications", if (hasNotifPermission) "Granted" else "Not granted (alerts blocked)")
                DiagRow("Exact Alarm Access", if (effectiveExactAlarms) "Granted" else "Fallback window (5 min)")
                DiagRow(
                    "Android 16 Live Updates / Now Bar",
                    when {
                        Build.VERSION.SDK_INT < 36 -> "Requires Android 16+ (using standard alerts)"
                        canPostLiveUpdates -> "Available for today's reminders (silent until due)"
                        else -> "Disabled in system settings (using standard alerts)"
                    }
                )
                DiagRow("Wear OS Watch Sync", "Active (bridged alerts, wrist actions & voice reply)")
                if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    TextButton(
                        onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    ) { Text("Allow Notifications") }
                }
                if (!effectiveExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        }
                    ) { Text("Grant Exact Alarm Permission") }
                }
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    }
                ) { Text("Open System Notification Settings") }
            }
        }

        SectionLabel("Default Snooze Duration")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { mins ->
                FilterChip(
                    selected = settings.defaultSnoozeMinutes == mins,
                    onClick = { scope.launch { settingsRepository.setDefaultSnoozeMinutes(mins) } },
                    label = { Text("${mins}m") },
                    border = null,
                    colors = smartNoteTonalFilterChipColors()
                )
            }
        }

        if (Build.VERSION.SDK_INT >= 36) {
            SettingsSwitchRow(
                title = "Now Bar for Today's Reminders",
                subtitle = "Shows today's upcoming reminders discreetly in the Android 16 Live Update / Now Bar (silent until due)",
                checked = settings.liveUpdateRemindersEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.setLiveUpdateRemindersEnabled(enabled)
                        onLiveUpdateSettingChanged(enabled)
                    }
                }
            )
        }
    }
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
            description = "Share text, URLs, screenshots, or multiple images from any app straight into SmartNote for private storage, offline OCR, and SmartTags."
        )

        SystemIntegrationCard(
            icon = Icons.Rounded.Watch,
            title = "Wear OS Watch Bridging",
            badge = "No Watch App",
            description = "Due reminders bridge directly to your paired Wear OS watch with synchronized dismissal, wrist Complete/Snooze buttons, and voice-reply note additions."
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
                    subtitle = "Save versioned ZIP with SHA-256 checksums, notes, tags, OCR, reminders & attachments",
                    onClick = { exportLauncher.launch("smartnote-backup-$stamp.snbackup") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.Restore,
                    title = "Import / Restore Backup",
                    subtitle = "Choose Merge or Replace All after archive integrity verification",
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Review Duplicates",
                    subtitle = "Inspect exact text, URL, image, and SimHash near-duplicate pairs",
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
                SectionLabel("Local Storage Usage")
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
                        DiagRow("SQLite + FTS5 Database", formatBytes(storageStats?.databaseBytes ?: 0L))
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
                    text = "Offline-first native Android notes built with Kotlin, Jetpack Compose, and Material 3 Expressive.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                SectionLabel("Privacy Guarantee")
                Text(
                    text = "• Zero user accounts, zero cloud sync, zero ads or analytics.\n" +
                        "• Offline OCR via bundled ML Kit Text Recognition.\n" +
                        "• Local FTS5 + Trigram + Fuzzy SmartSearch inside Bundled SQLite.\n" +
                        "• Network access is used ONLY when you explicitly opt into Link Previews.",
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
