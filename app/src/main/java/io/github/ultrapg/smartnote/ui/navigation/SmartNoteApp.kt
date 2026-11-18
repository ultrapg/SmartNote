package io.github.ultrapg.smartnote.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
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

@OptIn(ExperimentalSharedTransitionApi::class)
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

    val isTopLevel = currentDest == null ||
        currentDest.hasRoute<NotesOverviewRoute>() ||
        currentDest.hasRoute<SmartSearchRoute>() ||
        currentDest.hasRoute<TagsRoute>()

    val navContent: @Composable () -> Unit = {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = NotesOverviewRoute,
                modifier = Modifier.fillMaxSize()
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
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
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
                        onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) }
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
                        onNavigateBack = { navController.popBackStack() },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
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
                            duplicateDetector = container.duplicateDetector
                        )
                    )
                    val uiState by vm.uiState.collectAsState()
                    NoteEditorScreen(
                        uiState = uiState,
                        onTitleChange = vm::updateTitle,
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
                        onNavigateBack = { navController.popBackStack() },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
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
                        settings = settings,
                        settingsRepository = container.settingsRepository,
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
    }

    if (isTopLevel) {
        NavigationSuiteScaffold(
            modifier = modifier,
            navigationSuiteItems = {
                item(
                    selected = currentDest?.hasRoute<NotesOverviewRoute>() == true,
                    onClick = {
                        navController.navigate(NotesOverviewRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.Description, contentDescription = "Notes") },
                    label = { Text("Notes") }
                )
                item(
                    selected = currentDest?.hasRoute<SmartSearchRoute>() == true,
                    onClick = {
                        navController.navigate(SmartSearchRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "SmartSearch") },
                    label = { Text("SmartSearch") }
                )
                item(
                    selected = currentDest?.hasRoute<TagsRoute>() == true,
                    onClick = {
                        navController.navigate(TagsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = "Tags") },
                    label = { Text("Tags") }
                )
            }
        ) {
            navContent()
        }
    } else {
        navContent()
    }
}
