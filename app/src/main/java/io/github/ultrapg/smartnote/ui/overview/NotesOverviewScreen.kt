package io.github.ultrapg.smartnote.ui.overview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import io.github.ultrapg.smartnote.domain.model.NoteCreationOption
import io.github.ultrapg.smartnote.domain.model.NoteFilterType
import io.github.ultrapg.smartnote.domain.model.NoteSortOrder
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveBadgeShapeKind
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveShapeBadge
import io.github.ultrapg.smartnote.ui.designsystem.components.expressivePressable
import io.github.ultrapg.smartnote.ui.designsystem.components.rememberBoundedImageRequest
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteCardBorder
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteTonalFilterChipColors
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class OverviewTopMenu {
    NONE,
    SORT,
    OVERFLOW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesOverviewScreen(
    uiState: NotesOverviewUiState,
    onOpenNote: (String) -> Unit,
    onCreateNewNote: (NoteCreationOption) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLayoutMode: () -> Unit,
    onSelectSort: (NoteSortOrder) -> Unit,
    onSelectFilter: (NoteFilterType) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onArchiveNote: (String) -> Unit,
    onUnarchiveNote: (String) -> Unit,
    onMoveToTrash: (String) -> Unit,
    onRestoreFromTrash: (String) -> Unit,
    onBatchPin: (Boolean) -> Unit,
    onBatchArchive: () -> Unit,
    onBatchTrash: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var activeTopMenu by remember { mutableStateOf(OverviewTopMenu.NONE) }
    var displayedTopMenu by remember { mutableStateOf(OverviewTopMenu.OVERFLOW) }
    var lastTransitionWasCrossSwitch by remember { mutableStateOf(false) }
    if (activeTopMenu != OverviewTopMenu.NONE) {
        displayedTopMenu = activeTopMenu
    }
    var isCreateMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val themeMeta = LocalSmartNoteThemeMeta.current
    val reducedMotion = themeMeta.isReducedMotion

    val inSelectionMode = uiState.selectedIds.isNotEmpty()
    val allDisplayedNotes = uiState.pinnedNotes + uiState.otherNotes

    BackHandler(enabled = isCreateMenuExpanded || activeTopMenu != OverviewTopMenu.NONE) {
        lastTransitionWasCrossSwitch = false
        isCreateMenuExpanded = false
        activeTopMenu = OverviewTopMenu.NONE
    }

    LaunchedEffect(inSelectionMode) {
        if (inSelectionMode) {
            lastTransitionWasCrossSwitch = false
            isCreateMenuExpanded = false
            activeTopMenu = OverviewTopMenu.NONE
        }
    }

    fun archiveWithUndo(noteId: String) {
        onArchiveNote(noteId)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "Note archived",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) onUnarchiveNote(noteId)
        }
    }

    fun trashWithUndo(noteId: String) {
        onMoveToTrash(noteId)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "Moved to Recycle Bin",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) onRestoreFromTrash(noteId)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        val anyUnpinned = allDisplayedNotes
                            .filter { it.id in uiState.selectedIds }
                            .any { !it.isPinned }
                        IconButton(onClick = { onBatchPin(anyUnpinned) }) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = if (anyUnpinned) "Pin selected" else "Unpin selected"
                            )
                        }
                        IconButton(onClick = onBatchArchive) {
                            Icon(Icons.Rounded.Archive, contentDescription = "Archive selected")
                        }
                        IconButton(onClick = onBatchTrash) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Move selected to Recycle Bin")
                        }
                    }
                )
            } else if (uiState.activeTagName != null) {
                TopAppBar(
                    title = { Text("#${uiState.activeTagName}") },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleLayoutMode) {
                            Icon(
                                imageVector = if (uiState.layoutMode == NotesLayoutMode.STAGGERED_CARDS) {
                                    Icons.AutoMirrored.Rounded.ViewList
                                } else {
                                    Icons.Rounded.GridView
                                },
                                contentDescription = "Switch notes layout"
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                ) {
                    val isTopMenuOpen = activeTopMenu != OverviewTopMenu.NONE
                    val inactiveAlpha by animateFloatAsState(
                        targetValue = if (isTopMenuOpen) 0.42f else 1f,
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 60 else 180,
                            easing = FastOutSlowInEasing
                        ),
                        label = "top_bar_inactive_alpha"
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = smartNoteCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    lastTransitionWasCrossSwitch = false
                                    isCreateMenuExpanded = false
                                    activeTopMenu = OverviewTopMenu.NONE
                                    onOpenSearch()
                                },
                                modifier = Modifier.alpha(inactiveAlpha)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "SmartSearch",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .alpha(inactiveAlpha)
                                    .clickable {
                                        lastTransitionWasCrossSwitch = false
                                        isCreateMenuExpanded = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                        onOpenSearch()
                                    }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Search notes, tags...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = {
                                    lastTransitionWasCrossSwitch = false
                                    isCreateMenuExpanded = false
                                    activeTopMenu = OverviewTopMenu.NONE
                                    onToggleLayoutMode()
                                },
                                modifier = Modifier.alpha(inactiveAlpha)
                            ) {
                                Icon(
                                    imageVector = if (uiState.layoutMode == NotesLayoutMode.STAGGERED_CARDS) {
                                        Icons.AutoMirrored.Rounded.ViewList
                                    } else {
                                        Icons.Rounded.GridView
                                    },
                                    contentDescription = "Switch between card and compact list layout"
                                )
                            }

                            TopBarBubbleTriggerButton(
                                isOpen = activeTopMenu == OverviewTopMenu.SORT,
                                isOtherMenuOpen = isTopMenuOpen && activeTopMenu != OverviewTopMenu.SORT,
                                reducedMotion = reducedMotion,
                                openIcon = Icons.AutoMirrored.Rounded.Sort,
                                openContentDescription = "Sort notes",
                                closeContentDescription = "Close sort menu",
                                onClick = {
                                    isCreateMenuExpanded = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (activeTopMenu == OverviewTopMenu.SORT) {
                                        lastTransitionWasCrossSwitch = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                    } else {
                                        lastTransitionWasCrossSwitch = activeTopMenu == OverviewTopMenu.OVERFLOW
                                        activeTopMenu = OverviewTopMenu.SORT
                                    }
                                }
                            )

                            TopBarBubbleTriggerButton(
                                isOpen = activeTopMenu == OverviewTopMenu.OVERFLOW,
                                isOtherMenuOpen = isTopMenuOpen && activeTopMenu != OverviewTopMenu.OVERFLOW,
                                reducedMotion = reducedMotion,
                                openIcon = Icons.Rounded.MoreVert,
                                openContentDescription = "Menu",
                                closeContentDescription = "Close menu",
                                onClick = {
                                    isCreateMenuExpanded = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (activeTopMenu == OverviewTopMenu.OVERFLOW) {
                                        lastTransitionWasCrossSwitch = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                    } else {
                                        lastTransitionWasCrossSwitch = activeTopMenu == OverviewTopMenu.SORT
                                        activeTopMenu = OverviewTopMenu.OVERFLOW
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                ExpressiveMorphingCreateFab(
                    expanded = isCreateMenuExpanded,
                    onExpandedChange = { next ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (next) activeTopMenu = OverviewTopMenu.NONE
                        isCreateMenuExpanded = next
                    },
                    onSelectOption = { option ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isCreateMenuExpanded = false
                        onCreateNewNote(option)
                    },
                    reducedMotion = reducedMotion
                )
            }
        }
    ) { innerPadding ->
        val contentBottomPadding = innerPadding.calculateBottomPadding() + 96.dp
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NoteFilterType.entries, key = { it.name }) { filter ->
                    val label = when (filter) {
                        NoteFilterType.ALL -> "All"
                        NoteFilterType.PINNED -> "Pinned"
                        NoteFilterType.IMAGES -> "Images"
                        NoteFilterType.CHECKLISTS -> "Checklists"
                        NoteFilterType.LINKS -> "Links"
                    }
                    FilterChip(
                        selected = uiState.filterType == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(label) },
                        border = null,
                        colors = smartNoteTonalFilterChipColors()
                    )
                }
            }

            if (uiState.isLoading && allDisplayedNotes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (allDisplayedNotes.isEmpty()) {
                EmptyNotesPlaceholder(
                    bottomPadding = innerPadding.calculateBottomPadding() + 72.dp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.layoutMode) {
                        NotesLayoutMode.STAGGERED_CARDS -> {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
                                contentPadding = PaddingValues(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = 6.dp,
                                    bottom = contentBottomPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalItemSpacing = 10.dp,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (uiState.pinnedNotes.isNotEmpty()) {
                                    item(
                                        key = "hdr_pinned",
                                        span = StaggeredGridItemSpan.FullLine
                                    ) {
                                        SectionHeaderText("Pinned")
                                    }
                                    items(
                                        items = uiState.pinnedNotes,
                                        key = { it.id },
                                        contentType = { "note_card" }
                                    ) { note ->
                                        val isSelected = note.id in uiState.selectedIds
                                        NoteStaggeredCard(
                                            note = note,
                                            isSelected = isSelected,
                                            filesDir = context.filesDir,
                                            onClick = {
                                                if (inSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleSelection(note.id)
                                            },
                                            onPinToggle = { onTogglePin(note.id, note.isPinned) },
                                            onArchive = { archiveWithUndo(note.id) },
                                            onTrash = { trashWithUndo(note.id) },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                                                fadeOutSpec = null
                                            )
                                        )
                                    }
                                }

                                if (uiState.otherNotes.isNotEmpty()) {
                                    if (uiState.pinnedNotes.isNotEmpty()) {
                                        item(
                                            key = "hdr_others",
                                            span = StaggeredGridItemSpan.FullLine
                                        ) {
                                            SectionHeaderText("Notes")
                                        }
                                    }
                                    items(
                                        items = uiState.otherNotes,
                                        key = { it.id },
                                        contentType = { "note_card" }
                                    ) { note ->
                                        val isSelected = note.id in uiState.selectedIds
                                        NoteStaggeredCard(
                                            note = note,
                                            isSelected = isSelected,
                                            filesDir = context.filesDir,
                                            onClick = {
                                                if (inSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleSelection(note.id)
                                            },
                                            onPinToggle = { onTogglePin(note.id, note.isPinned) },
                                            onArchive = { archiveWithUndo(note.id) },
                                            onTrash = { trashWithUndo(note.id) },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                                                fadeOutSpec = null
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        NotesLayoutMode.COMPACT_LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = 6.dp,
                                    bottom = contentBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (uiState.pinnedNotes.isNotEmpty()) {
                                    item(key = "hdr_pinned_list") { SectionHeaderText("Pinned") }
                                    items(
                                        items = uiState.pinnedNotes,
                                        key = { it.id },
                                        contentType = { "note_row" }
                                    ) { note ->
                                        SwipeableCompactNoteRow(
                                            note = note,
                                            isSelected = note.id in uiState.selectedIds,
                                            filesDir = context.filesDir,
                                            onClick = {
                                                if (inSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleSelection(note.id)
                                            },
                                            onPinToggle = { onTogglePin(note.id, note.isPinned) },
                                            onArchive = { archiveWithUndo(note.id) },
                                            onTrash = { trashWithUndo(note.id) },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                                                fadeOutSpec = null
                                            )
                                        )
                                    }
                                }

                                if (uiState.otherNotes.isNotEmpty()) {
                                    if (uiState.pinnedNotes.isNotEmpty()) {
                                        item(key = "hdr_others_list") { SectionHeaderText("Notes") }
                                    }
                                    items(
                                        items = uiState.otherNotes,
                                        key = { it.id },
                                        contentType = { "note_row" }
                                    ) { note ->
                                        SwipeableCompactNoteRow(
                                            note = note,
                                            isSelected = note.id in uiState.selectedIds,
                                            filesDir = context.filesDir,
                                            onClick = {
                                                if (inSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleSelection(note.id)
                                            },
                                            onPinToggle = { onTogglePin(note.id, note.isPinned) },
                                            onArchive = { archiveWithUndo(note.id) },
                                            onTrash = { trashWithUndo(note.id) },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                                                fadeOutSpec = null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = isCreateMenuExpanded || activeTopMenu != OverviewTopMenu.NONE,
            enter = fadeIn(tween(durationMillis = if (reducedMotion) 70 else 200, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = if (reducedMotion) 60 else 150, easing = FastOutLinearInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (themeMeta.isDark) 0.36f else 0.14f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            lastTransitionWasCrossSwitch = false
                            isCreateMenuExpanded = false
                            activeTopMenu = OverviewTopMenu.NONE
                        }
                    )
            )
        }

        val showTopMenuBubble = activeTopMenu != OverviewTopMenu.NONE && !inSelectionMode && uiState.activeTagName == null
        val bubbleExtraEndShift by animateDpAsState(
            targetValue = if (displayedTopMenu == OverviewTopMenu.SORT) 44.dp else 0.dp,
            animationSpec = if (reducedMotion || !lastTransitionWasCrossSwitch) {
                snap()
            } else {
                SmartNoteMotion.refinedMenuMorphSpring()
            },
            label = "top_menu_bubble_shift"
        )
        val bubblePivotX = if (displayedTopMenu == OverviewTopMenu.SORT) 0.84f else 0.88f

        AnimatedVisibility(
            visible = showTopMenuBubble,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = innerPadding.calculateTopPadding() + 4.dp, end = 16.dp)
                .offset { IntOffset(-bubbleExtraEndShift.roundToPx(), 0) },
            enter = if (reducedMotion) {
                fadeIn(tween(80))
            } else {
                scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(bubblePivotX, 0f),
                    animationSpec = SmartNoteMotion.refinedMenuOpenSpring()
                ) + slideInVertically(
                    initialOffsetY = { -16 },
                    animationSpec = SmartNoteMotion.refinedMenuOpenSpring()
                ) + fadeIn(tween(140, easing = LinearOutSlowInEasing))
            },
            exit = if (reducedMotion) {
                fadeOut(tween(70))
            } else {
                scaleOut(
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(bubblePivotX, 0f),
                    animationSpec = tween(135, easing = FastOutSlowInEasing)
                ) + slideOutVertically(
                    targetOffsetY = { -12 },
                    animationSpec = tween(135, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(115, easing = FastOutLinearInEasing))
            }
        ) {
            Surface(
                modifier = Modifier
                    .width(224.dp)
                    .testTag("overview_top_menu_bubble"),
                shape = RoundedCornerShape(26.dp),
                color = if (themeMeta.isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (themeMeta.isDark) 0.28f else 0.35f)
                ),
                tonalElevation = 4.dp,
                shadowElevation = 12.dp
            ) {
                AnimatedContent(
                    targetState = displayedTopMenu,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(60)) togetherWith fadeOut(tween(50))
                        } else {
                            val movingRight = targetState == OverviewTopMenu.OVERFLOW
                            val slideIn = slideInHorizontally(
                                animationSpec = SmartNoteMotion.refinedMenuMorphSpring(),
                                initialOffsetX = { fullW -> if (movingRight) fullW / 7 else -fullW / 7 }
                            )
                            val slideOut = slideOutHorizontally(
                                animationSpec = tween(110, easing = FastOutLinearInEasing),
                                targetOffsetX = { fullW -> if (movingRight) -fullW / 9 else fullW / 9 }
                            )
                            (slideIn + fadeIn(tween(150, delayMillis = 30, easing = LinearOutSlowInEasing))) togetherWith
                                (slideOut + fadeOut(tween(85, easing = FastOutLinearInEasing))) using
                                SizeTransform(clip = true) { _, _ -> SmartNoteMotion.refinedMenuMorphSpring() }
                        }
                    },
                    label = "top_menu_bubble_content"
                ) { menu ->
                    val animateItems = !lastTransitionWasCrossSwitch
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        when (menu) {
                            OverviewTopMenu.SORT -> {
                                NoteSortOrder.entries.forEachIndexed { idx, order ->
                                    val (label, icon) = when (order) {
                                        NoteSortOrder.UPDATED_DESC -> "Recently updated" to Icons.Rounded.Schedule
                                        NoteSortOrder.CREATED_DESC -> "Recently created" to Icons.Rounded.History
                                        NoteSortOrder.TITLE_ASC -> "Title A–Z" to Icons.Rounded.SortByAlpha
                                    }
                                    PopOutBubbleMenuItem(
                                        icon = icon,
                                        label = label,
                                        selected = uiState.sortOrder == order,
                                        itemIndex = idx,
                                        animateEntrance = animateItems,
                                        reducedMotion = reducedMotion,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastTransitionWasCrossSwitch = false
                                            activeTopMenu = OverviewTopMenu.NONE
                                            onSelectSort(order)
                                        }
                                    )
                                }
                            }
                            OverviewTopMenu.OVERFLOW -> {
                                PopOutBubbleMenuItem(
                                    icon = Icons.Rounded.Archive,
                                    label = "Archive",
                                    itemIndex = 0,
                                    animateEntrance = animateItems,
                                    reducedMotion = reducedMotion,
                                    onClick = {
                                        lastTransitionWasCrossSwitch = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                        onOpenArchive()
                                    }
                                )
                                PopOutBubbleMenuItem(
                                    icon = Icons.Rounded.DeleteOutline,
                                    label = "Recycle Bin",
                                    itemIndex = 1,
                                    animateEntrance = animateItems,
                                    reducedMotion = reducedMotion,
                                    onClick = {
                                        lastTransitionWasCrossSwitch = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                        onOpenRecycleBin()
                                    }
                                )
                                PopOutBubbleMenuItem(
                                    icon = Icons.Rounded.ContentCopy,
                                    label = "Review Duplicates",
                                    itemIndex = 2,
                                    animateEntrance = animateItems,
                                    reducedMotion = reducedMotion,
                                    onClick = {
                                        lastTransitionWasCrossSwitch = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                        onOpenDuplicates()
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                PopOutBubbleMenuItem(
                                    icon = Icons.Rounded.Settings,
                                    label = "Settings",
                                    itemIndex = 3,
                                    animateEntrance = animateItems,
                                    reducedMotion = reducedMotion,
                                    onClick = {
                                        lastTransitionWasCrossSwitch = false
                                        activeTopMenu = OverviewTopMenu.NONE
                                        onOpenSettings()
                                    }
                                )
                            }
                            OverviewTopMenu.NONE -> Unit
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TopBarBubbleTriggerButton(
    isOpen: Boolean,
    isOtherMenuOpen: Boolean,
    reducedMotion: Boolean,
    openIcon: ImageVector,
    openContentDescription: String,
    closeContentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val openProgress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = if (reducedMotion) {
            tween(durationMillis = 60, easing = FastOutSlowInEasing)
        } else {
            SmartNoteMotion.refinedIconSpring()
        },
        label = "top_bar_trigger_open_progress"
    )
    val otherDimAlpha by animateFloatAsState(
        targetValue = if (isOtherMenuOpen) 0.50f else 1f,
        animationSpec = tween(durationMillis = if (reducedMotion) 60 else 160, easing = FastOutSlowInEasing),
        label = "top_bar_trigger_other_alpha"
    )
    val clampedProgress = openProgress.coerceIn(0f, 1f)

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .expressivePressable(interactionSource, pressedScale = 0.92f)
            .semantics {
                contentDescription = if (isOpen) closeContentDescription else openContentDescription
            }
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer { alpha = otherDimAlpha },
            contentAlignment = Alignment.Center
        ) {
            if (clampedProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val s = 0.62f + (0.38f * clampedProgress)
                            scaleX = s
                            scaleY = s
                            alpha = clampedProgress
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }
            if (clampedProgress < 0.65f) {
                Icon(
                    imageVector = openIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        val s = 1f - (0.22f * clampedProgress)
                        alpha = (1f - clampedProgress * 1.6f).coerceIn(0f, 1f)
                        rotationZ = 45f * clampedProgress
                        scaleX = s
                        scaleY = s
                    }
                )
            }
            if (clampedProgress > 0.15f) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.graphicsLayer {
                        val s = 0.76f + (0.24f * clampedProgress)
                        alpha = ((clampedProgress - 0.2f) * 1.35f).coerceIn(0f, 1f)
                        rotationZ = -45f * (1f - clampedProgress)
                        scaleX = s
                        scaleY = s
                    }
                )
            }
        }
    }
}

@Composable
private fun PopOutBubbleMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    itemIndex: Int = 0,
    animateEntrance: Boolean = true,
    reducedMotion: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val enterProgress = remember {
        Animatable(if (reducedMotion || !animateEntrance) 1f else 0f)
    }
    LaunchedEffect(Unit) {
        if (reducedMotion || !animateEntrance) {
            enterProgress.snapTo(1f)
        } else {
            val staggerMs = (itemIndex * 16L).coerceAtMost(56L)
            if (staggerMs > 0L) delay(staggerMs)
            enterProgress.animateTo(1f, animationSpec = SmartNoteMotion.refinedMenuOpenSpring())
        }
    }
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = if (reducedMotion) 60 else 150, easing = FastOutSlowInEasing),
        label = "bubble_item_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = if (reducedMotion) 60 else 150, easing = FastOutSlowInEasing),
        label = "bubble_item_fg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = if (reducedMotion) 60 else 150, easing = FastOutSlowInEasing),
        label = "bubble_item_icon"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .graphicsLayer {
                if (!reducedMotion) {
                    val p = enterProgress.value.coerceIn(0f, 1f)
                    alpha = p
                    translationY = (1f - enterProgress.value) * 6f * density
                }
            }
            .expressivePressable(interactionSource, pressedScale = 0.975f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpressiveMorphingCreateFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectOption: (NoteCreationOption) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = when {
            reducedMotion -> tween(
                durationMillis = if (expanded) 85 else 70,
                easing = FastOutSlowInEasing
            )
            expanded -> spring(
                dampingRatio = 0.90f,
                stiffness = 440f,
                visibilityThreshold = 0.002f
            )
            else -> spring(
                dampingRatio = 1.0f,
                stiffness = 600f,
                visibilityThreshold = 0.002f
            )
        },
        label = "create_pill_progress"
    )
    val clampedProgress = progress.coerceIn(0f, 1f)
    val cornerRadius = lerp(16.dp, 28.dp, clampedProgress)
    val tonalElevation = lerp(3.dp, 6.dp, clampedProgress)
    val shadowElevation = lerp(6.dp, 12.dp, clampedProgress)
    val showOptions = expanded || clampedProgress > 0.01f
    val toggleInteractionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    ) {
        Layout(
            content = {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .clickable(
                            interactionSource = toggleInteractionSource,
                            indication = androidx.compose.material3.ripple(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                            )
                        ) { onExpandedChange(!expanded) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (expanded) "Close create menu" else "New note"
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    rotationZ = 45f * clampedProgress
                                    val iconScale = 1f + (0.14f * clampedProgress)
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }
                }
                if (showOptions) {
                    Column(
                        modifier = Modifier
                            .width(184.dp)
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CreatePillOptionRow(
                            icon = Icons.Rounded.Image,
                            label = "Image",
                            rowIndex = 0,
                            pillProgress = clampedProgress,
                            isExpanding = expanded,
                            reducedMotion = reducedMotion,
                            onClick = { onSelectOption(NoteCreationOption.IMAGE) }
                        )
                        CreatePillOptionRow(
                            icon = Icons.Rounded.CheckBox,
                            label = "Checklist",
                            rowIndex = 1,
                            pillProgress = clampedProgress,
                            isExpanding = expanded,
                            reducedMotion = reducedMotion,
                            onClick = { onSelectOption(NoteCreationOption.CHECKLIST) }
                        )
                        CreatePillOptionRow(
                            icon = Icons.AutoMirrored.Rounded.NoteAdd,
                            label = "Note",
                            rowIndex = 2,
                            pillProgress = clampedProgress,
                            isExpanding = expanded,
                            reducedMotion = reducedMotion,
                            onClick = { onSelectOption(NoteCreationOption.TEXT_NOTE) }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val collapsedPx = 56.dp.roundToPx()
            val toggleMeasurable = measurables[0]
            if (measurables.size == 1) {
                val togglePlaceable = toggleMeasurable.measure(Constraints.fixed(collapsedPx, collapsedPx))
                layout(collapsedPx, collapsedPx) {
                    togglePlaceable.placeRelative(0, 0)
                }
            } else {
                val optionsPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val expandedW = maxOf(collapsedPx, optionsPlaceable.width)
                val expandedH = collapsedPx + optionsPlaceable.height
                val rawFraction = progress.coerceAtLeast(0f)
                val curW = (collapsedPx + (expandedW - collapsedPx) * rawFraction)
                    .roundToInt()
                    .coerceAtLeast(collapsedPx)
                val curH = (collapsedPx + (expandedH - collapsedPx) * rawFraction)
                    .roundToInt()
                    .coerceAtLeast(collapsedPx)
                val togglePlaceable = toggleMeasurable.measure(Constraints.fixed(curW, collapsedPx))
                layout(curW, curH) {
                    optionsPlaceable.placeRelative(
                        x = curW - expandedW,
                        y = curH - collapsedPx - optionsPlaceable.height
                    )
                    togglePlaceable.placeRelative(
                        x = 0,
                        y = curH - collapsedPx
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePillOptionRow(
    icon: ImageVector,
    label: String,
    rowIndex: Int,
    pillProgress: Float,
    isExpanding: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val itemPhase = when {
        reducedMotion -> pillProgress
        isExpanding -> {
            val start = when (rowIndex) {
                2 -> 0.10f
                1 -> 0.22f
                else -> 0.34f
            }
            val raw = ((pillProgress - start) / 0.60f).coerceIn(0f, 1f)
            val inv = 1f - raw
            1f - (inv * inv * inv)
        }
        else -> ((pillProgress - 0.42f) / 0.56f).coerceIn(0f, 1f)
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .graphicsLayer {
                if (reducedMotion) {
                    alpha = pillProgress
                } else {
                    alpha = itemPhase
                    translationY = (1f - itemPhase) * 8f * density
                    val s = 0.94f + (0.06f * itemPhase)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.85f, 1f)
                }
            }
            .expressivePressable(interactionSource, pressedScale = 0.96f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
                ),
                enabled = isExpanding,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SectionHeaderText(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteStaggeredCard(
    note: NoteSummary,
    isSelected: Boolean,
    filesDir: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinToggle: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(if (isSelected) 16.dp else 22.dp)
    val baseBorder = smartNoteCardBorder()
    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        baseBorder
    }

    val accessibleSummary = buildNoteAccessibilityLabel(note)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .expressivePressable(interactionSource)
            .clip(cardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleSummary
                customActions = listOf(
                    CustomAccessibilityAction(if (note.isPinned) "Unpin note" else "Pin note") {
                        onPinToggle()
                        true
                    },
                    CustomAccessibilityAction("Archive note") {
                        onArchive()
                        true
                    },
                    CustomAccessibilityAction("Move to Recycle Bin") {
                        onTrash()
                        true
                    }
                )
            },
        shape = cardShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = cardBorder
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!note.primaryThumbnailPath.isNullOrBlank()) {
                val thumbFile = File(filesDir, note.primaryThumbnailPath)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = rememberBoundedImageRequest(
                            file = thumbFile,
                            maxDimensionPx = 512
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (note.imageCount > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                        ) {
                            Text(
                                text = "+${note.imageCount - 1}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hasBodyPreview = !note.primaryThumbnailPath.isNullOrBlank() ||
                    (note.hasChecklist && note.checklistPreview.isNotEmpty()) ||
                    note.previewText.isNotBlank()
                val showUntitledFallback = note.title.isBlank() && !hasBodyPreview

                if (note.title.isNotBlank() || showUntitledFallback || note.isPinned || isSelected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = note.title.ifBlank { if (showUntitledFallback) "Untitled note" else "" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (note.title.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (note.isPinned) {
                            ExpressiveShapeBadge(
                                kind = ExpressiveBadgeShapeKind.SUNNY,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 24.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                if (note.hasChecklist && note.checklistPreview.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        note.checklistPreview.take(4).forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isChecked) {
                                        Icons.Rounded.CheckBox
                                    } else {
                                        Icons.Rounded.CheckBoxOutlineBlank
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (item.isChecked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                    color = if (item.isChecked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                        if (note.checklistTotalCount > 4) {
                            Text(
                                text = "+${note.checklistTotalCount - 4} more items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (note.previewText.isNotBlank()) {
                    Text(
                        text = note.previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (note.primaryLinkDomain != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(13.dp))
                            Text(
                                text = note.primaryLinkDomain,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (note.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        note.tags.take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (tag.source == TagSource.SUGGESTED) {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (note.tags.size > 3) {
                            Text(
                                text = "+${note.tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableCompactNoteRow(
    note: NoteSummary,
    isSelected: Boolean,
    filesDir: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinToggle: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onArchive()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTrash()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor = when (dir) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
            val align = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = align
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Rounded.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val rowShape = RoundedCornerShape(if (isSelected) 14.dp else 18.dp)
        val baseBorder = smartNoteCardBorder()
        val rowBorder = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            baseBorder
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .expressivePressable(interactionSource)
                .clip(rowShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = buildNoteAccessibilityLabel(note)
                    customActions = listOf(
                        CustomAccessibilityAction(if (note.isPinned) "Unpin note" else "Pin note") {
                            onPinToggle()
                            true
                        },
                        CustomAccessibilityAction("Archive note") {
                            onArchive()
                            true
                        },
                        CustomAccessibilityAction("Move to Recycle Bin") {
                            onTrash()
                            true
                        }
                    )
                },
            shape = rowShape,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = rowBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (note.isPinned) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = note.title.ifBlank { "Untitled note" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (note.previewText.isNotBlank()) {
                        Text(
                            text = note.previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(note.updatedAtMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        note.tags.take(2).forEach { t ->
                            Text(
                                text = "#${t.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (!note.primaryThumbnailPath.isNullOrBlank()) {
                    AsyncImage(
                        model = rememberBoundedImageRequest(
                            file = File(filesDir, note.primaryThumbnailPath),
                            maxDimensionPx = 512
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesPlaceholder(
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveShapeBadge(
            kind = ExpressiveBadgeShapeKind.COOKIE,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 76.dp
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.NoteAdd,
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Capture your thoughts",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Write notes and checklists, or share links and screenshots from other apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun buildNoteAccessibilityLabel(note: NoteSummary): String = buildString {
    if (note.isPinned) append("Pinned note: ")
    append(note.title.ifBlank { "Untitled note" })
    if (note.previewText.isNotBlank()) {
        append(". ").append(note.previewText.take(120))
    }
    if (note.hasChecklist) {
        append(". Checklist ${note.checklistCheckedCount} of ${note.checklistTotalCount} completed")
    }
    if (note.tags.isNotEmpty()) {
        append(". Tags: ").append(note.tags.joinToString(", ") { it.name })
    }
}
