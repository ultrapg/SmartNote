package io.github.ultrapg.smartnote.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.ultrapg.smartnote.LaunchDeepLink
import io.github.ultrapg.smartnote.di.AppContainer
import io.github.ultrapg.smartnote.domain.model.DuplicatePairInfo
import io.github.ultrapg.smartnote.domain.model.StorageStats
import io.github.ultrapg.smartnote.ui.archive.ArchiveScreen
import io.github.ultrapg.smartnote.ui.archive.DuplicateReviewScreen
import io.github.ultrapg.smartnote.ui.archive.RecycleBinScreen
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.editor.NoteEditorScreen
import io.github.ultrapg.smartnote.ui.editor.NoteEditorViewModel
import io.github.ultrapg.smartnote.ui.overview.NotesOverviewScreen
import io.github.ultrapg.smartnote.ui.overview.NotesOverviewViewModel
import io.github.ultrapg.smartnote.ui.search.SmartSearchScreen
import io.github.ultrapg.smartnote.ui.search.SmartSearchViewModel
import io.github.ultrapg.smartnote.ui.settings.AboutScreen
import io.github.ultrapg.smartnote.ui.settings.AiProcessingSettingsScreen
import io.github.ultrapg.smartnote.ui.settings.AppearanceSettingsScreen
import io.github.ultrapg.smartnote.ui.settings.DataSettingsScreen
import io.github.ultrapg.smartnote.ui.settings.IntegrationsSettingsScreen
import io.github.ultrapg.smartnote.ui.settings.ReminderSettingsScreen
import io.github.ultrapg.smartnote.ui.settings.SettingsScreen
import io.github.ultrapg.smartnote.ui.settings.SmartFeaturesSettingsScreen
import io.github.ultrapg.smartnote.ui.tags.TagsScreen
import io.github.ultrapg.smartnote.ui.tags.TagsViewModel
import kotlinx.coroutines.launch

@Composable
fun SmartNoteApp(
    container: AppContainer,
    deepLink: LaunchDeepLink,
    onDeepLinkConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val settings by container.settingsRepository.settingsFlow.collectAsState(
        initial = io.github.ultrapg.smartnote.domain.model.AppSettings()
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(deepLink) {
        when (deepLink) {
            LaunchDeepLink.Overview -> Unit
            is LaunchDeepLink.OpenNote -> {
                navController.navigate(NoteEditorRoute(noteId = deepLink.noteId)) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is LaunchDeepLink.NewNote -> {
                navController.navigate(
                    NoteEditorRoute(
                        noteId = null,
                        startChecklist = deepLink.startChecklist,
                        sourceQuickTile = deepLink.fromQuickTile
                    )
                )
                onDeepLinkConsumed()
            }
            LaunchDeepLink.OpenSearch -> {
                navController.navigate(SmartSearchRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                onDeepLinkConsumed()
            }
        }
    }

    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion

    NavHost(
        navController = navController,
        startDestination = NotesOverviewRoute,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            if (reducedMotion || (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab())) {
                fadeIn(tween(140))
            } else {
                fadeIn(tween(180)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(210, easing = FastOutSlowInEasing),
                        initialOffset = { it / 24 }
                    )
            }
        },
        exitTransition = {
            if (reducedMotion || (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab())) {
                fadeOut(tween(120))
            } else {
                fadeOut(tween(150))
            }
        },
        popEnterTransition = {
            if (reducedMotion || (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab())) {
                fadeIn(tween(140))
            } else {
                fadeIn(tween(160))
            }
        },
        popExitTransition = {
            if (reducedMotion || (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab())) {
                fadeOut(tween(120))
            } else {
                fadeOut(tween(140)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetOffset = { it / 24 }
                    )
            }
        }
    ) {
        composable<NotesOverviewRoute> {
            val vm: NotesOverviewViewModel = viewModel(
                factory = NotesOverviewViewModel.Factory(
                    noteRepository = container.noteRepository,
                    settingsRepository = container.settingsRepository
                )
            )
            val uiState by vm.uiState.collectAsState()
            TopLevelAdaptiveShell(
                navController = navController,
                currentDestination = currentDest,
                hideBottomBarOnIme = false
            ) { bottomBar ->
                NotesOverviewScreen(
                    uiState = uiState,
                    onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                    onCreateNewNote = { checklist ->
                        navController.navigate(NoteEditorRoute(noteId = null, startChecklist = checklist))
                    },
                    onOpenSearch = {
                        navController.navigate(SmartSearchRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenArchive = { navController.navigate(ArchiveRoute) },
                    onOpenRecycleBin = { navController.navigate(RecycleBinRoute) },
                    onOpenDuplicates = { navController.navigate(DuplicatesRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onToggleLayoutMode = vm::toggleLayoutMode,
                    onSelectSort = vm::setSortOrder,
                    onSelectFilter = vm::setFilterType,
                    onToggleSelection = vm::toggleSelection,
                    onClearSelection = vm::clearSelection,
                    onTogglePin = vm::togglePin,
                    onArchiveNote = vm::archiveNote,
                    onUnarchiveNote = vm::unarchiveNote,
                    onMoveToTrash = vm::moveToRecycleBin,
                    onRestoreFromTrash = vm::restoreFromRecycleBin,
                    onBatchPin = vm::batchSetPinned,
                    onBatchArchive = vm::batchArchive,
                    onBatchTrash = vm::batchMoveToRecycleBin,
                    bottomBar = bottomBar
                )
            }
        }

        composable<SmartSearchRoute> {
            val vm: SmartSearchViewModel = viewModel(
                factory = SmartSearchViewModel.Factory(
                    searchEngine = container.searchEngine,
                    tagRepository = container.tagRepository,
                    settingsRepository = container.settingsRepository
                )
            )
            val uiState by vm.uiState.collectAsState()
            TopLevelAdaptiveShell(
                navController = navController,
                currentDestination = currentDest,
                hideBottomBarOnIme = true
            ) { bottomBar ->
                SmartSearchScreen(
                    uiState = uiState,
                    onQueryChange = vm::updateQuery,
                    onSubmitSearch = vm::submitSearch,
                    onApplyRecentSearch = vm::applyRecentSearch,
                    onDeleteRecentSearch = vm::deleteRecentSearch,
                    onClearRecentSearches = vm::clearRecentSearches,
                    onApplyDidYouMean = vm::applyDidYouMean,
                    onToggleTagFilter = vm::toggleTagFilter,
                    onTogglePinnedFilter = vm::togglePinnedFilter,
                    onToggleImageFilter = vm::toggleImageFilter,
                    onToggleOcrFilter = vm::toggleOcrFilter,
                    onToggleChecklistFilter = vm::toggleChecklistFilter,
                    onToggleLinkFilter = vm::toggleLinkFilter,
                    onSelectDateFilter = vm::setDateFilter,
                    onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                    bottomBar = bottomBar
                )
            }
        }

        composable<TagsRoute> {
            val vm: TagsViewModel = viewModel(
                factory = TagsViewModel.Factory(container.tagRepository)
            )
            val uiState by vm.uiState.collectAsState()
            TopLevelAdaptiveShell(
                navController = navController,
                currentDestination = currentDest,
                hideBottomBarOnIme = true
            ) { bottomBar ->
                TagsScreen(
                    uiState = uiState,
                    onFilterChange = vm::updateFilter,
                    onCreateTag = vm::createTag,
                    onRenameTag = vm::renameTag,
                    onDeleteTag = vm::deleteTag,
                    onOpenTagNotes = { tag ->
                        navController.navigate(TagNotesRoute(tagId = tag.id, tagName = tag.name))
                    },
                    bottomBar = bottomBar
                )
            }
        }

        composable<TagNotesRoute> { entry ->
            val route = entry.toRoute<TagNotesRoute>()
            val vm: NotesOverviewViewModel = viewModel(
                key = "tag_notes_${route.tagId}",
                factory = NotesOverviewViewModel.Factory(
                    noteRepository = container.noteRepository,
                    settingsRepository = container.settingsRepository,
                    tagIdFilter = route.tagId,
                    tagNameFilter = route.tagName
                )
            )
            val uiState by vm.uiState.collectAsState()
            NotesOverviewScreen(
                uiState = uiState,
                onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                onCreateNewNote = { checklist ->
                    navController.navigate(NoteEditorRoute(noteId = null, startChecklist = checklist))
                },
                onOpenSearch = { navController.navigate(SmartSearchRoute) },
                onOpenArchive = { navController.navigate(ArchiveRoute) },
                onOpenRecycleBin = { navController.navigate(RecycleBinRoute) },
                onOpenDuplicates = { navController.navigate(DuplicatesRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onToggleLayoutMode = vm::toggleLayoutMode,
                onSelectSort = vm::setSortOrder,
                onSelectFilter = vm::setFilterType,
                onToggleSelection = vm::toggleSelection,
                onClearSelection = vm::clearSelection,
                onTogglePin = vm::togglePin,
                onArchiveNote = vm::archiveNote,
                onUnarchiveNote = vm::unarchiveNote,
                onMoveToTrash = vm::moveToRecycleBin,
                onRestoreFromTrash = vm::restoreFromRecycleBin,
                onBatchPin = vm::batchSetPinned,
                onBatchArchive = vm::batchArchive,
                onBatchTrash = vm::batchMoveToRecycleBin,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<NoteEditorRoute> { entry ->
            val route = entry.toRoute<NoteEditorRoute>()
            val vm: NoteEditorViewModel = viewModel(
                factory = NoteEditorViewModel.Factory(
                    initialNoteId = route.noteId,
                    startChecklist = route.startChecklist,
                    fromQuickTile = route.sourceQuickTile,
                    noteRepository = container.noteRepository,
                    tagRepository = container.tagRepository,
                    autosaveCoordinator = container.autosaveCoordinator,
                    attachmentImporter = container.attachmentImporter,
                    noteShareExporter = container.noteShareExporter,
                    reminderScheduler = container.reminderScheduler,
                    duplicateDetector = container.duplicateDetector,
                    settingsRepository = container.settingsRepository,
                    smartTitleGenerator = container.smartTitleGenerator
                )
            )
            val uiState by vm.uiState.collectAsState()
            NoteEditorScreen(
                uiState = uiState,
                onTitleChange = vm::updateTitle,
                onGenerateSmartTitle = vm::generateSmartTitleNow,
                onApplySuggestedTitle = vm::applySuggestedTitle,
                onBlockTextChange = vm::updateBlockText,
                onToggleChecklistChecked = vm::toggleChecklistChecked,
                onAddChecklistItemAfter = vm::insertChecklistItemAfter,
                onDeleteChecklistItem = vm::deleteChecklistItem,
                onMoveChecklistItem = vm::moveChecklistItem,
                onToggleChecklistMode = vm::convertSelectionOrToggleChecklistMode,
                onInsertMarkdown = vm::wrapOrInsertMarkdownSnippet,
                onImportImages = vm::importImages,
                onRemoveImageBlock = vm::removeImageBlock,
                onTogglePin = vm::togglePin,
                onToggleArchive = vm::toggleArchive,
                onDuplicateNote = {
                    vm.duplicateCurrentNote { copyId ->
                        navController.navigate(NoteEditorRoute(noteId = copyId))
                    }
                },
                onMoveToTrash = { vm.moveToRecycleBin { navController.popBackStack() } },
                onShareNote = vm::shareCurrentNote,
                onAddUserTag = vm::addUserTag,
                onConfirmSuggestedTag = vm::confirmSuggestedTag,
                onDismissOrRemoveTag = vm::dismissOrRemoveTag,
                onSaveReminder = vm::saveReminder,
                onRemoveReminder = vm::removeReminder,
                onDismissDuplicate = vm::dismissDuplicatePair,
                onOpenDuplicateNote = { otherId ->
                    navController.navigate(NoteEditorRoute(noteId = otherId))
                },
                onFlushAutosave = vm::flushAutosaveAsync,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<ArchiveRoute> {
            val archived by container.noteRepository.observeArchivedNotes()
                .collectAsState(initial = emptyList())
            ArchiveScreen(
                archivedNotes = archived,
                onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                onUnarchive = { id -> scope.launch { container.noteRepository.setArchived(id, false) } },
                onMoveToTrash = { id -> scope.launch { container.noteRepository.moveToRecycleBin(id) } },
                onBack = { navController.popBackStack() }
            )
        }

        composable<RecycleBinRoute> {
            val deleted by container.noteRepository.observeDeletedNotes()
                .collectAsState(initial = emptyList())
            RecycleBinScreen(
                deletedNotes = deleted,
                onRestore = { id -> scope.launch { container.noteRepository.restoreFromRecycleBin(id) } },
                onDeletePermanently = { id -> scope.launch { container.noteRepository.deletePermanently(id) } },
                onEmptyRecycleBin = { scope.launch { container.noteRepository.emptyRecycleBin() } },
                onBack = { navController.popBackStack() }
            )
        }

        composable<DuplicatesRoute> {
            var pairs by remember { mutableStateOf<List<DuplicatePairInfo>>(emptyList()) }
            LaunchedEffect(Unit) {
                pairs = container.duplicateDetector.findDuplicatesForAllNotes()
            }
            DuplicateReviewScreen(
                duplicates = pairs,
                onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                onDismissPair = { pair ->
                    scope.launch {
                        container.duplicateDetector.dismissDuplicatePair(pair.noteA.id, pair.noteB.id)
                        pairs = pairs.filterNot { it.pairKey == pair.pairKey }
                    }
                },
                onRecycleOlderCopy = { pair ->
                    scope.launch {
                        container.noteRepository.moveToRecycleBin(pair.noteB.id)
                        pairs = container.duplicateDetector.findDuplicatesForAllNotes()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onOpenAppearance = { navController.navigate(AppearanceSettingsRoute) },
                onOpenSmartFeatures = { navController.navigate(SmartFeaturesSettingsRoute) },
                onOpenReminders = { navController.navigate(ReminderSettingsRoute) },
                onOpenIntegrations = { navController.navigate(IntegrationsSettingsRoute) },
                onOpenData = { navController.navigate(DataSettingsRoute) },
                onOpenAbout = { navController.navigate(AboutSettingsRoute) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppearanceSettingsRoute> {
            AppearanceSettingsScreen(
                settings = settings,
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable<SmartFeaturesSettingsRoute> {
            SmartFeaturesSettingsScreen(
                settings = settings,
                settingsRepository = container.settingsRepository,
                onDisableAiUnloadModel = { scope.launch { container.aiEngine.unloadModel() } },
                onOpenAiProcessing = { navController.navigate(AiSettingsRoute) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AiSettingsRoute> {
            val diagnostics by container.aiEngine.diagnosticsFlow.collectAsState()
            AiProcessingSettingsScreen(
                settings = settings,
                diagnostics = diagnostics,
                settingsRepository = container.settingsRepository,
                onRefreshDiagnostics = { scope.launch { container.aiEngine.refreshDiagnostics() } },
                onRunSelfTest = {
                    scope.launch {
                        container.aiEngine.embedText("SmartNote hardware verification probe")
                    }
                },
                onUnloadModel = { scope.launch { container.aiEngine.unloadModel() } },
                onBack = { navController.popBackStack() }
            )
        }

        composable<ReminderSettingsRoute> {
            ReminderSettingsScreen(
                settings = settings,
                canScheduleExactAlarms = container.reminderScheduler.canScheduleExactAlarms(),
                canPostLiveUpdates = container.reminderNotificationBuilder.canPromoteLiveUpdates(),
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable<IntegrationsSettingsRoute> {
            IntegrationsSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<DataSettingsRoute> {
            var stats by remember { mutableStateOf<StorageStats?>(null) }
            DataSettingsScreen(
                backupManager = container.backupManager,
                storageStats = stats,
                onRefreshStats = {
                    scope.launch { stats = container.noteRepository.getStorageStats() }
                },
                onOpenDuplicates = { navController.navigate(DuplicatesRoute) },
                onOpenRecycleBin = { navController.navigate(RecycleBinRoute) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AboutSettingsRoute> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavDestination?.isTopLevelTab(): Boolean = this != null && (
    hasRoute<NotesOverviewRoute>() ||
        hasRoute<SmartSearchRoute>() ||
        hasRoute<TagsRoute>()
)

private fun navigateTopLevelTab(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TopLevelAdaptiveShell(
    navController: NavHostController,
    currentDestination: NavDestination?,
    hideBottomBarOnIme: Boolean = false,
    content: @Composable (bottomBar: @Composable () -> Unit) -> Unit
) {
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = imeBottom > navBottom + with(density) { 40.dp.roundToPx() }

    val notesSelected = currentDestination?.hasRoute<NotesOverviewRoute>() == true
    val searchSelected = currentDestination?.hasRoute<SmartSearchRoute>() == true
    val tagsSelected = currentDestination?.hasRoute<TagsRoute>() == true

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                NavigationRailItem(
                    selected = notesSelected,
                    onClick = { navigateTopLevelTab(navController, NotesOverviewRoute) },
                    icon = { Icon(Icons.Rounded.Description, contentDescription = "Notes") },
                    label = { Text("Notes") }
                )
                NavigationRailItem(
                    selected = searchSelected,
                    onClick = { navigateTopLevelTab(navController, SmartSearchRoute) },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "SmartSearch") },
                    label = { Text("SmartSearch") }
                )
                NavigationRailItem(
                    selected = tagsSelected,
                    onClick = { navigateTopLevelTab(navController, TagsRoute) },
                    icon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = "Tags") },
                    label = { Text("Tags") }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                content {}
            }
        }
    } else {
        content {
            if (!hideBottomBarOnIme || !isImeVisible) {
                NavigationBar {
                    NavigationBarItem(
                        selected = notesSelected,
                        onClick = { navigateTopLevelTab(navController, NotesOverviewRoute) },
                        icon = { Icon(Icons.Rounded.Description, contentDescription = "Notes") },
                        label = { Text("Notes") }
                    )
                    NavigationBarItem(
                        selected = searchSelected,
                        onClick = { navigateTopLevelTab(navController, SmartSearchRoute) },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = "SmartSearch") },
                        label = { Text("SmartSearch") }
                    )
                    NavigationBarItem(
                        selected = tagsSelected,
                        onClick = { navigateTopLevelTab(navController, TagsRoute) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = "Tags") },
                        label = { Text("Tags") }
                    )
                }
            }
        }
    }
}
