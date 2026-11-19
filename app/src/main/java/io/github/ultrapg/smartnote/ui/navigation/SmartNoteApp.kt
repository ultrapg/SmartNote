package io.github.ultrapg.smartnote.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import io.github.ultrapg.smartnote.domain.model.NoteCreationOption
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
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

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
        initial = container.settingsRepository.currentSnapshot
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
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = imeBottom > navBottom + with(density) { 40.dp.roundToPx() }
    val isTopLevel = currentDest.isTopLevelTab()
    val hideBottomOnIme = currentDest?.hasRoute<SmartSearchRoute>() == true ||
        currentDest?.hasRoute<TagsRoute>() == true
    val showBottomBar = !isWideScreen && isTopLevel && (!hideBottomOnIme || !isImeVisible)

    val notesSelected = currentDest?.hasRoute<NotesOverviewRoute>() == true
    val searchSelected = currentDest?.hasRoute<SmartSearchRoute>() == true
    val tagsSelected = currentDest?.hasRoute<TagsRoute>() == true

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val navHostContent: @Composable (Modifier) -> Unit = { hostModifier ->
            NavHost(
                navController = navController,
                startDestination = NotesOverviewRoute,
                modifier = hostModifier.fillMaxSize(),
                enterTransition = {
                    val isTabSwitch = initialState.destination.isTopLevelTab() &&
                        targetState.destination.isTopLevelTab()
                    when {
                        reducedMotion && isTabSwitch -> EnterTransition.None
                        reducedMotion -> fadeIn(tween(90))
                        isTabSwitch -> fadeIn(tween(110, easing = LinearOutSlowInEasing))
                        else -> fadeIn(tween(140, easing = LinearOutSlowInEasing)) +
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(170, easing = FastOutSlowInEasing),
                                initialOffset = { it / 36 }
                            )
                    }
                },
                exitTransition = {
                    val isTabSwitch = initialState.destination.isTopLevelTab() &&
                        targetState.destination.isTopLevelTab()
                    when {
                        reducedMotion && isTabSwitch -> ExitTransition.None
                        reducedMotion -> fadeOut(tween(durationMillis = 1, delayMillis = 90))
                        isTabSwitch -> fadeOut(tween(durationMillis = 1, delayMillis = 110))
                        else -> fadeOut(tween(durationMillis = 1, delayMillis = 170))
                    }
                },
                popEnterTransition = {
                    EnterTransition.None
                },
                popExitTransition = {
                    val isTabSwitch = initialState.destination.isTopLevelTab() &&
                        targetState.destination.isTopLevelTab()
                    when {
                        reducedMotion && isTabSwitch -> ExitTransition.None
                        reducedMotion -> fadeOut(tween(90))
                        isTabSwitch -> fadeOut(tween(110, easing = LinearOutSlowInEasing))
                        else -> fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(140, easing = FastOutSlowInEasing),
                                targetOffset = { it / 36 }
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
                    NotesOverviewScreen(
                        uiState = uiState,
                        onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                        onCreateNewNote = { option ->
                            navController.navigate(
                                NoteEditorRoute(
                                    noteId = null,
                                    startChecklist = option == NoteCreationOption.CHECKLIST,
                                    autoPickImage = option == NoteCreationOption.IMAGE,
                                    autoOpenReminder = option == NoteCreationOption.REMINDER
                                )
                            )
                        },
                        onOpenSearch = {
                            navigateTopLevelTab(navController, SmartSearchRoute)
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
                        bottomBar = {
                            if (!isWideScreen) {
                                TopLevelBottomBarSpacer()
                            }
                        }
                    )
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
                        bottomBar = {
                            if (!isWideScreen && !isImeVisible) {
                                TopLevelBottomBarSpacer()
                            }
                        }
                    )
                }

                composable<TagsRoute> {
                    val vm: TagsViewModel = viewModel(
                        factory = TagsViewModel.Factory(container.tagRepository)
                    )
                    val uiState by vm.uiState.collectAsState()
                    TagsScreen(
                        uiState = uiState,
                        onFilterChange = vm::updateFilter,
                        onCreateTag = vm::createTag,
                        onRenameTag = vm::renameTag,
                        onDeleteTag = vm::deleteTag,
                        onOpenTagNotes = { tag ->
                            navController.navigate(TagNotesRoute(tagId = tag.id, tagName = tag.name))
                        },
                        bottomBar = {
                            if (!isWideScreen && !isImeVisible) {
                                TopLevelBottomBarSpacer()
                            }
                        }
                    )
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
                        onCreateNewNote = { option ->
                            navController.navigate(
                                NoteEditorRoute(
                                    noteId = null,
                                    startChecklist = option == NoteCreationOption.CHECKLIST,
                                    autoPickImage = option == NoteCreationOption.IMAGE,
                                    autoOpenReminder = option == NoteCreationOption.REMINDER
                                )
                            )
                        },
                        onOpenSearch = { navigateTopLevelTab(navController, SmartSearchRoute) },
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
                            smartTitleGenerator = container.smartTitleGenerator,
                            smartTagsEngine = container.smartTagsEngine
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
                        onApplyAutoTagsNow = vm::applyAutoTagsNow,
                        onConfirmSuggestedTag = vm::confirmSuggestedTag,
                        onDismissOrRemoveTag = vm::dismissOrRemoveTag,
                        onSaveReminder = vm::saveReminder,
                        onCompleteReminder = vm::completeReminder,
                        onSnoozeReminder = vm::snoozeReminder,
                        onRemoveReminder = vm::removeReminder,
                        onDismissDuplicate = vm::dismissDuplicatePair,
                        onOpenDuplicateNote = { otherId ->
                            navController.navigate(NoteEditorRoute(noteId = otherId))
                        },
                        onFlushAutosave = vm::flushPendingEditsOnly,
                        onExitEditor = vm::finishEditorSession,
                        initialPickImage = route.autoPickImage,
                        initialOpenReminder = route.autoOpenReminder,
                        onEnsureTrailingEditableBlock = vm::ensureTrailingEditableBlock,
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

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = isTopLevel,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(90))
                ) {
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
                }
                navHostContent(Modifier.weight(1f))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                navHostContent(Modifier)
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(120)),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(90)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SmartNoteBottomBar(
                        tabs = listOf(
                            BottomNavTab(
                                label = "Notes",
                                icon = Icons.Rounded.Description,
                                selected = notesSelected,
                                onClick = { navigateTopLevelTab(navController, NotesOverviewRoute) }
                            ),
                            BottomNavTab(
                                label = "SmartSearch",
                                icon = Icons.Rounded.Search,
                                selected = searchSelected,
                                onClick = { navigateTopLevelTab(navController, SmartSearchRoute) }
                            ),
                            BottomNavTab(
                                label = "Tags",
                                icon = Icons.AutoMirrored.Rounded.Label,
                                selected = tagsSelected,
                                onClick = { navigateTopLevelTab(navController, TagsRoute) }
                            )
                        ),
                        reducedMotion = reducedMotion
                    )
                }
            }
        }
    }
}

private data class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun SmartNoteBottomBar(
    tabs: List<BottomNavTab>,
    reducedMotion: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
    val animatedPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (reducedMotion) {
            tween(durationMillis = 80)
        } else {
            spring(dampingRatio = 0.88f, stiffness = 420f)
        },
        label = "bottom_nav_flying_pill"
    )
    val fractionalFlight = animatedPosition - floor(animatedPosition.toDouble()).toFloat()
    val inFlightStretch = if (reducedMotion) {
        0f
    } else {
        sin(fractionalFlight * PI).toFloat().coerceIn(0f, 1f)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp)
                .padding(horizontal = 8.dp)
        ) {
            val slotWidth = maxWidth / tabs.size.coerceAtLeast(1)
            val pillWidth = (64.dp + (18.dp * inFlightStretch)).coerceAtMost(slotWidth - 8.dp)
            val pillLeftX = slotWidth * (animatedPosition + 0.5f) - (pillWidth / 2f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(pillLeftX.roundToPx(), 12.dp.roundToPx()) }
                    .size(width = pillWidth, height = 32.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .testTag("bottom_nav_flying_pill")
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val iconTint by animateColorAsState(
                        targetValue = if (tab.selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = if (reducedMotion) 60 else 140),
                        label = "bottom_nav_icon_tint"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (tab.selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = if (reducedMotion) 60 else 140),
                        label = "bottom_nav_label_tint"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics(mergeDescendants = true) {
                                selected = tab.selected
                                role = Role.Tab
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (!tab.selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    tab.onClick()
                                }
                            )
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Box(
                            modifier = Modifier.height(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopLevelBottomBarSpacer() {
    Spacer(
        modifier = Modifier
            .navigationBarsPadding()
            .height(80.dp)
    )
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
