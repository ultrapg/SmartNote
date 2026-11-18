package io.github.ultrapg.smartnote.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.BuildConfig
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
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
                    subtitle = "Master AI switch, Auto-Titles, SmartTags, Offline OCR, Duplicates & Semantic Search",
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
    SettingsSubScaffold(title = "AI & Smart Features", onBack = onBack) {
        SettingsSwitchRow(
            title = "Enable All AI Features",
            subtitle = "Master switch for Auto-Title Generation, SmartTags, Offline OCR, Duplicate Detection, and LiteRT Semantic Search",
            checked = settings.aiFeaturesEnabled,
            highlighted = true,
            onCheckedChange = { enabled ->
                scope.launch { settingsRepository.setAiFeaturesEnabled(enabled) }
                if (!enabled) onDisableAiUnloadModel()
            }
        )
        SectionLabel("Individual AI Features")
        SettingsSwitchRow(
            title = "Auto-Title Generation",
            subtitle = "Generate concise offline titles for untitled notes and shared screenshots",
            checked = settings.autoTitleEnabled,
            enabled = settings.aiFeaturesEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setAutoTitleEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "SmartTags",
            subtitle = "Suggest tags automatically using local rules, TF-IDF, and your personal tag vocabulary",
            checked = settings.smartTagsEnabled,
            enabled = settings.aiFeaturesEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setSmartTagsEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Offline OCR",
            subtitle = "Extract searchable text from shared screenshots and images on-device via bundled ML Kit",
            checked = settings.ocrEnabled,
            enabled = settings.aiFeaturesEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setOcrEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Duplicate Detection",
            subtitle = "Flag exact or near-duplicate notes and attachments for manual review",
            checked = settings.duplicateDetectionEnabled,
            enabled = settings.aiFeaturesEnabled,
            onCheckedChange = { scope.launch { settingsRepository.setDuplicateDetectionEnabled(it) } }
        )
        SettingsSwitchRow(
            title = "Hybrid Semantic Search (LiteRT)",
            subtitle = "Blend local on-device text embedding similarity into SmartSearch results",
            checked = settings.semanticRerankEnabled,
            enabled = settings.aiFeaturesEnabled,
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
    LaunchedEffect(Unit) { onRefreshDiagnostics() }

    SettingsSubScaffold(title = "AI Hardware & Diagnostics", onBack = onBack) {
        if (!settings.aiFeaturesEnabled) {
            SettingsSwitchRow(
                title = "All AI Features Disabled",
                subtitle = "Turn on the master AI switch to enable LiteRT semantic search, Auto-Titles, SmartTags, OCR, and duplicate detection",
                checked = false,
                highlighted = true,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setAiFeaturesEnabled(enabled) }
                }
            )
        }
        SectionLabel("Hardware Acceleration Preference")
        AiBackendPreference.entries.forEach { pref ->
            val title = when (pref) {
                AiBackendPreference.AUTO -> "Automatic (Recommended)"
                AiBackendPreference.PREFER_NPU -> "Prefer NPU"
                AiBackendPreference.PREFER_GPU -> "Prefer GPU"
                AiBackendPreference.CPU_ONLY -> "CPU Only"
            }
            val desc = when (pref) {
                AiBackendPreference.AUTO -> "Uses native ML Kit for OCR, SQLite CPU for FTS5, and verified NPU/GPU/CPU for LiteRT"
                AiBackendPreference.PREFER_NPU -> "Attempts LiteRT NPU dispatch when hardware runtime is present; falls back cleanly"
                AiBackendPreference.PREFER_GPU -> "Prefers LiteRT OpenCL/GLES GPU execution with CPU fallback"
                AiBackendPreference.CPU_ONLY -> "Forces CPU execution strictly; disables NPU and GPU delegates"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            settingsRepository.setAiBackendPreference(pref)
                            onRefreshDiagnostics()
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.aiBackendPreference == pref,
                    onClick = {
                        scope.launch {
                            settingsRepository.setAiBackendPreference(pref)
                            onRefreshDiagnostics()
                        }
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SettingsSwitchRow(
            title = "Energy-Efficient Processing",
            subtitle = "Step down to CPU when Battery Saver or elevated thermal throttling is active",
            checked = settings.energyEfficientAi,
            onCheckedChange = { scope.launch { settingsRepository.setEnergyEfficientAi(it) } }
        )

        SettingsSwitchRow(
            title = "Hybrid Semantic Re-Ranking (LiteRT)",
            subtitle = "Optional: blend local compact text-embedding similarity into FTS5 SmartSearch results",
            checked = settings.semanticRerankEnabled,
            enabled = settings.aiFeaturesEnabled,
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
            Button(onClick = onRunSelfTest, modifier = Modifier.weight(1f)) {
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                DiagRow("Exact Alarm Access", if (canScheduleExactAlarms) "Granted" else "Fallback window (5 min)")
                DiagRow(
                    "Android 16 Live Updates / Now Bar",
                    when {
                        Build.VERSION.SDK_INT < 36 -> "Requires Android 16+ (using standard notifications)"
                        canPostLiveUpdates -> "Available for active due/snoozed reminders"
                        else -> "Disabled in system settings (using standard notifications)"
                    }
                )
                if (!canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                title = "Live Updates for Active Reminders",
                subtitle = "Promotes only due or snoozed reminder countdowns on Android 16+ / One UI 8+ Now Bar",
                checked = settings.liveUpdateRemindersEnabled,
                onCheckedChange = { scope.launch { settingsRepository.setLiveUpdateRemindersEnabled(it) } }
            )
        }
    }
}

@Composable
fun IntegrationsSettingsScreen(
    onBack: () -> Unit
) {
    SettingsSubScaffold(title = "System Integrations", onBack = onBack) {
        SectionLabel("System Shell Features")
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
                DiagRow("Quick Settings Tile", "Registered (Quick Note)")
                DiagRow("App Shortcuts", "New Note, New Checklist, SmartSearch + Pinned")
                DiagRow("Home Screen Widgets", "3 Glance M3 Widgets (Quick, Pinned, Overview)")
                DiagRow("Android Sharesheet", "Text, URLs, Screenshots & Multi-Image")
            }
        }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
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
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = smartNoteCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        shape = shape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = smartNoteCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
