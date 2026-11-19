package io.github.ultrapg.smartnote.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import io.github.ultrapg.smartnote.domain.model.AttachmentInfo
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
import io.github.ultrapg.smartnote.ui.designsystem.components.VerticalLazyScrollbar
import io.github.ultrapg.smartnote.ui.designsystem.components.rememberBoundedImageRequest
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteCardBorder
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun NoteEditorScreen(
    uiState: NoteEditorUiState,
    onTitleChange: (String) -> Unit,
    onGenerateSmartTitle: () -> Unit = {},
    onApplySuggestedTitle: () -> Unit = {},
    onBlockTextChange: (String, String) -> Unit,
    onToggleChecklistChecked: (String) -> Unit,
    onAddChecklistItemAfter: (String?) -> Unit,
    onDeleteChecklistItem: (String) -> Unit,
    onMoveChecklistItem: (String, Int) -> Unit,
    onToggleChecklistMode: () -> Unit,
    onInsertMarkdown: (String, String) -> Unit,
    onImportImages: (List<Uri>) -> Unit,
    onRemoveImageBlock: (String, String?) -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDuplicateNote: () -> Unit,
    onMoveToTrash: () -> Unit,
    onShareNote: () -> Unit,
    onAddUserTag: (String) -> Unit,
    onApplyAutoTagsNow: () -> Unit = {},
    onConfirmSuggestedTag: (String) -> Unit,
    onDismissOrRemoveTag: (String) -> Unit,
    onDismissDuplicate: (io.github.ultrapg.smartnote.domain.model.DuplicatePairInfo) -> Unit,
    onOpenDuplicateNote: (String) -> Unit,
    onFlushAutosave: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPickImage: Boolean = false,
    onExitEditor: () -> Unit = onFlushAutosave,
    onEnsureTrailingEditableBlock: () -> String = { uiState.blocks.lastOrNull()?.id.orEmpty() }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var didAutoLaunchImagePicker by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    var awaitingInitialImagePick by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    var fullscreenAttachment by remember { mutableStateOf<AttachmentInfo?>(null) }
    var ocrViewerAttachment by remember { mutableStateOf<AttachmentInfo?>(null) }
    val textFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val checklistFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var pendingFocusTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var focusSeq by remember { mutableIntStateOf(0) }
    var titleHeaderHeightPx by remember { mutableIntStateOf(0) }

    fun focusBottomEditor() {
        val targetId = onEnsureTrailingEditableBlock()
        if (targetId.isNotBlank()) {
            focusSeq++
            pendingFocusTarget = targetId to focusSeq
        }
    }

    LaunchedEffect(pendingFocusTarget) {
        val target = pendingFocusTarget ?: return@LaunchedEffect
        val targetId = target.first
        yield()
        val prefixItems = 1 +
            (if (uiState.tags.isNotEmpty()) 1 else 0) +
            (if (uiState.duplicateMatches.isNotEmpty()) 1 else 0)
        val blockIdx = uiState.blocks.indexOfFirst { it.id == targetId }
        if (blockIdx >= 0) {
            val listIdx = prefixItems + blockIdx
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIdx }
            if (!isVisible) {
                listState.scrollToItem(listIdx)
            }
        }
        val requester = textFocusRequesters[targetId] ?: checklistFocusRequesters[targetId]
        runCatching { requester?.requestFocus() }
        keyboardController?.show()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(8)
    ) { uris ->
        val wasInitialAutoPick = awaitingInitialImagePick
        awaitingInitialImagePick = false
        if (uris.isNotEmpty()) {
            onImportImages(uris)
        } else if (wasInitialAutoPick && uiState.title.isBlank() && uiState.attachments.isEmpty() &&
            uiState.blocks.all { it.textContent.isBlank() }
        ) {
            onExitEditor()
            onNavigateBack()
        }
    }

    LaunchedEffect(initialPickImage) {
        if (initialPickImage && !didAutoLaunchImagePicker) {
            didAutoLaunchImagePicker = true
            awaitingInitialImagePick = true
            runCatching {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }.onFailure {
                awaitingInitialImagePick = false
            }
        }
    }

    // Guaranteed flush on backgrounding / screen stop
    DisposableEffect(lifecycleOwner, uiState.noteId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onFlushAutosave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onFlushAutosave()
        }
    }

    BackHandler {
        onExitEditor()
        onNavigateBack()
    }

    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion

    val headingColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    val syntaxMarkerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val markdownTransformation = remember(headingColor, codeBackground, syntaxMarkerColor) {
        MarkdownVisualTransformation(
            codeBackground = codeBackground,
            syntaxMarkerColor = syntaxMarkerColor,
            headingColor = headingColor
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val statusLabel = when (uiState.autosaveStatus) {
                        AutosaveStatus.PENDING -> "Editing..."
                        AutosaveStatus.SAVING -> "Saving..."
                        AutosaveStatus.SAVED -> "Saved"
                        AutosaveStatus.ERROR -> "Save error"
                        AutosaveStatus.IDLE -> ""
                    }
                    if (statusLabel.isNotBlank()) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onExitEditor()
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back and save")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePin()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (uiState.isPinned) "Unpin note" else "Pin note",
                            tint = if (uiState.isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = onShareNote) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share note")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More note actions")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (uiState.isArchived) "Unarchive" else "Archive") },
                                leadingIcon = {
                                    Icon(
                                        if (uiState.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleArchive()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onDuplicateNote()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Recycle Bin") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onMoveToTrash()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onInsertMarkdown("**", "**") }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.FormatBold, contentDescription = "Bold")
                        }
                        IconButton(onClick = { onInsertMarkdown("*", "*") }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic")
                        }
                        IconButton(onClick = { onInsertMarkdown("`", "`") }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Code, contentDescription = "Inline code")
                        }
                        IconButton(onClick = { onInsertMarkdown("# ", "") }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Title, contentDescription = "Heading")
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleChecklistMode()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Rounded.CheckBox, contentDescription = "Toggle checklist")
                        }
                        IconButton(
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = "Insert image")
                        }
                        IconButton(onClick = { showAddTagDialog = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add tag")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val topContentPad = 8.dp
        val bottomContentPad = innerPadding.calculateBottomPadding() + 24.dp
        val showGenerateTagsButton = uiState.aiFeaturesEnabled && uiState.liveTagSuggestions.isNotEmpty()
        val isSingleCleanTextBlock = uiState.blocks.size == 1 &&
            uiState.blocks.first().type == NoteBlockType.TEXT &&
            uiState.tags.isEmpty() &&
            !showGenerateTagsButton &&
            uiState.duplicateMatches.isEmpty() &&
            uiState.links.isEmpty()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .pointerInput(Unit) {
                    detectTapGestures { focusBottomEditor() }
                }
        ) {
            val availableBodyHeight = (maxHeight - topContentPad - bottomContentPad).coerceAtLeast(180.dp)
            val titleHeaderHeightDp = with(density) { titleHeaderHeightPx.toDp() }
            val usedAboveBody = if (titleHeaderHeightPx > 0) titleHeaderHeightDp + 16.dp else 72.dp
            val singleTextMinHeight = (availableBodyHeight - usedAboveBody).coerceAtLeast(120.dp)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = topContentPad,
                    bottom = bottomContentPad
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "editor_title") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { titleHeaderHeightPx = it.height }
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BasicTextField(
                            value = uiState.title,
                            onValueChange = onTitleChange,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (uiState.title.isEmpty()) {
                                        Text(
                                            text = "Title",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        val hasTitleSourceContent = remember(uiState.blocks, uiState.attachments, uiState.links) {
                            uiState.blocks.any { it.textContent.isNotBlank() || !it.linkUrl.isNullOrBlank() } ||
                                uiState.attachments.values.any { !it.ocrText.isNullOrBlank() } ||
                                uiState.links.isNotEmpty()
                        }
                        if (uiState.aiFeaturesEnabled && hasTitleSourceContent) {
                            if (!uiState.autoTitleEnabled && uiState.title.isBlank()) {
                                Surface(
                                    onClick = onGenerateSmartTitle,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.semantics { contentDescription = "Generate title" }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Generate",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                IconButton(onClick = onGenerateSmartTitle) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Generate title",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.autoTitleEnabled && uiState.title.isBlank() && !uiState.suggestedTitle.isNullOrBlank(),
                        enter = if (reducedMotion) fadeIn(tween(90)) else fadeIn(tween(150)),
                        exit = if (reducedMotion) fadeOut(tween(90)) else fadeOut(tween(120))
                    ) {
                        AssistChip(
                            onClick = onApplySuggestedTitle,
                            label = { Text("Auto-title: ${uiState.suggestedTitle.orEmpty()}") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (uiState.tags.isNotEmpty() || showGenerateTagsButton) {
                item(key = "editor_meta_chips") {
                    FlowRow(
                        modifier = Modifier.animateContentSize(SmartNoteMotion.softSizeSpring()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.tags.forEach { tag ->
                            if (tag.source == TagSource.SUGGESTED) {
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onConfirmSuggestedTag(tag.tagId)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = "SmartTag suggestion",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${tag.name}?",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Confirm tag ${tag.name}",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onConfirmSuggestedTag(tag.tagId) }
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Dismiss tag ${tag.name}",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDismissOrRemoveTag(tag.tagId) }
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = { onDismissOrRemoveTag(tag.tagId) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "#${tag.name}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove tag ${tag.name}",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.tags.isNotEmpty()) {
                            Surface(
                                onClick = { showAddTagDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("Tag", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        if (showGenerateTagsButton) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onApplyAutoTagsNow()
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.semantics { contentDescription = "Generate tags" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Generate tags",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.duplicateMatches.isNotEmpty()) {
                item(key = "duplicate_notice") {
                    val pair = uiState.duplicateMatches.first()
                    val other = if (pair.noteA.id == uiState.noteId) pair.noteB else pair.noteA
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Possible duplicate: ${other.title.ifBlank { "Untitled" }}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = pair.matchReason,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onOpenDuplicateNote(other.id) }) {
                                Text("View")
                            }
                            IconButton(onClick = { onDismissDuplicate(pair) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss duplicate warning")
                            }
                        }
                    }
                }
            }

            items(
                items = uiState.blocks,
                key = { it.id },
                contentType = { it.type }
            ) { block ->
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                        fadeOutSpec = null
                    )
                ) {
                    when (block.type) {
                        NoteBlockType.TEXT -> {
                            val requester = textFocusRequesters.getOrPut(block.id) { FocusRequester() }
                            val textMinHeight = when {
                                isSingleCleanTextBlock -> singleTextMinHeight
                                block.id == uiState.blocks.lastOrNull()?.id -> 220.dp
                                else -> 120.dp
                            }
                            TextBlockEditorRow(
                                block = block,
                                visualTransformation = markdownTransformation,
                                focusRequester = requester,
                                minHeight = textMinHeight,
                                moveCursorToEndSignal = if (pendingFocusTarget?.first == block.id) {
                                    pendingFocusTarget?.second ?: 0
                                } else {
                                    0
                                },
                                onTextChange = { onBlockTextChange(block.id, it) }
                            )
                        }

                        NoteBlockType.CHECKLIST -> {
                            val requester = checklistFocusRequesters.getOrPut(block.id) { FocusRequester() }
                            ChecklistBlockEditorRow(
                                block = block,
                                focusRequester = requester,
                                onToggleChecked = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleChecklistChecked(block.id)
                                },
                                onTextChange = { onBlockTextChange(block.id, it) },
                                onEnterNext = { onAddChecklistItemAfter(block.id) },
                                onDelete = { onDeleteChecklistItem(block.id) },
                                onMoveUp = { onMoveChecklistItem(block.id, -1) },
                                onMoveDown = { onMoveChecklistItem(block.id, +1) }
                            )
                        }

                        NoteBlockType.IMAGE -> {
                            val att = block.attachmentId?.let { uiState.attachments[it] }
                            if (att != null) {
                                ImageBlockCard(
                                    attachment = att,
                                    filesDir = context.filesDir,
                                    onOpenFullScreen = { fullscreenAttachment = att },
                                    onOpenOcrText = { ocrViewerAttachment = att },
                                    onRemove = { onRemoveImageBlock(block.id, att.id) }
                                )
                            }
                        }

                        NoteBlockType.LINK -> {
                            val matchingLink = uiState.links.firstOrNull { it.rawUrl == block.linkUrl }
                            LinkBlockCard(
                                url = block.linkUrl.orEmpty(),
                                preview = matchingLink,
                                onOpenUrl = { rawUrl ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Extra clickable Link Cards for URLs detected inline inside prose blocks
            val explicitLinkUrls = uiState.blocks
                .filter { it.type == NoteBlockType.LINK }
                .mapNotNull { it.linkUrl }
                .toSet()
            val inlineLinks = uiState.links.filter { it.rawUrl !in explicitLinkUrls }
            items(
                items = inlineLinks,
                key = { "inline_link_${it.id}" }
            ) { link ->
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                        fadeOutSpec = null
                    )
                ) {
                    LinkBlockCard(
                        url = link.rawUrl,
                        preview = link,
                        onOpenUrl = { rawUrl ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)))
                            }
                        }
                    )
                }
            }

            if (uiState.blocks.isNotEmpty() && uiState.blocks.last().type != NoteBlockType.TEXT) {
                item(key = "editor_bottom_tap_zone") {
                    val tapZoneInteraction = remember { MutableInteractionSource() }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clickable(
                                interactionSource = tapZoneInteraction,
                                indication = null,
                                onClick = ::focusBottomEditor
                            )
                    )
                }
            }
            }

            VerticalLazyScrollbar(
                listState = listState,
                topPadding = 6.dp,
                bottomPadding = innerPadding.calculateBottomPadding() + 6.dp
            )
        }
    }

    if (showAddTagDialog) {
        ManageTagsSheet(
            aiFeaturesEnabled = uiState.aiFeaturesEnabled,
            liveSuggestions = uiState.liveTagSuggestions,
            existingNoteTags = uiState.tags.map { it.name },
            availableVocabulary = uiState.availableTags,
            onAddTag = { tagName ->
                showAddTagDialog = false
                onAddUserTag(tagName)
                scope.launch { listState.scrollToItem(0) }
            },
            onApplyAllAutoTags = {
                showAddTagDialog = false
                onApplyAutoTagsNow()
                scope.launch { listState.scrollToItem(0) }
            },
            onDismiss = { showAddTagDialog = false }
        )
    }

    fullscreenAttachment?.let { att ->
        Dialog(
            onDismissRequest = { fullscreenAttachment = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenAttachment = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = rememberBoundedImageRequest(
                        file = File(context.filesDir, att.relativeFilePath),
                        maxDimensionPx = 2048
                    ),
                    contentDescription = att.originalDisplayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullscreenAttachment = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .systemBarsPadding()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close viewer", tint = Color.White)
                }
            }
        }
    }

    ocrViewerAttachment?.let { att ->
        ModalBottomSheet(onDismissRequest = { ocrViewerAttachment = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Extracted Text", style = MaterialTheme.typography.titleLarge)
                    if (!att.ocrText.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("SmartNote OCR", att.ocrText))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied OCR text to clipboard")
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                    }
                }
                val textBody = when (att.ocrStatus) {
                    OcrStatus.PENDING -> "Recognition queued in background..."
                    OcrStatus.EMPTY -> "No readable text detected in this image."
                    OcrStatus.FAILED -> "Unable to recognize text in this image."
                    OcrStatus.COMPLETED -> att.ocrText.orEmpty()
                }
                Text(
                    text = textBody,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TextBlockEditorRow(
    block: NoteBlock,
    visualTransformation: MarkdownVisualTransformation,
    focusRequester: FocusRequester,
    minHeight: Dp,
    moveCursorToEndSignal: Int,
    onTextChange: (String) -> Unit
) {
    var textFieldValue by remember(block.id) {
        mutableStateOf(
            TextFieldValue(
                text = block.textContent,
                selection = TextRange(block.textContent.length)
            )
        )
    }
    val pendingLocalTexts = remember(block.id) { ArrayDeque<String>() }

    LaunchedEffect(block.id, block.textContent) {
        val pendingIdx = pendingLocalTexts.indexOfFirst { it == block.textContent }
        if (pendingIdx >= 0) {
            repeat(pendingIdx + 1) { pendingLocalTexts.removeFirst() }
        } else if (textFieldValue.text != block.textContent) {
            val clampedStart = textFieldValue.selection.start.coerceIn(0, block.textContent.length)
            val clampedEnd = textFieldValue.selection.end.coerceIn(0, block.textContent.length)
            textFieldValue = TextFieldValue(
                text = block.textContent,
                selection = TextRange(clampedStart, clampedEnd)
            )
        }
    }

    LaunchedEffect(moveCursorToEndSignal) {
        if (moveCursorToEndSignal > 0) {
            val end = textFieldValue.text.length
            textFieldValue = textFieldValue.copy(selection = TextRange(end))
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { next ->
            val textChanged = next.text != textFieldValue.text
            textFieldValue = next
            if (textChanged && next.text != block.textContent) {
                if (pendingLocalTexts.size >= 16) pendingLocalTexts.removeFirst()
                pendingLocalTexts.addLast(next.text)
                onTextChange(next.text)
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = "Start writing... (# heading, **bold**, *italic*, `code`)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ChecklistBlockEditorRow(
    block: NoteBlock,
    focusRequester: FocusRequester,
    onToggleChecked: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnterNext: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = block.isChecked,
            onCheckedChange = { onToggleChecked() }
        )
        BasicTextField(
            value = block.textContent,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (block.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (block.isChecked) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onEnterNext() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (block.textContent.isEmpty()) {
                        Text(
                            text = "Checklist item",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move item up", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move item down", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove checklist item", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ImageBlockCard(
    attachment: AttachmentInfo,
    filesDir: File,
    onOpenFullScreen: () -> Unit,
    onOpenOcrText: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = smartNoteCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            val imageFile = attachment.thumbnailRelativePath?.let { rel ->
                File(filesDir, rel).takeIf { it.exists() }
            } ?: File(filesDir, attachment.relativeFilePath)
            AsyncImage(
                model = rememberBoundedImageRequest(
                    file = imageFile,
                    maxDimensionPx = 1280
                ),
                contentDescription = attachment.originalDisplayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clickable(onClick = onOpenFullScreen)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onOpenOcrText) {
                    Icon(Icons.AutoMirrored.Rounded.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    val ocrLabel = when (attachment.ocrStatus) {
                        OcrStatus.COMPLETED -> "View OCR text"
                        OcrStatus.PENDING -> "OCR queued..."
                        OcrStatus.EMPTY -> "No text found"
                        OcrStatus.FAILED -> "OCR unavailable"
                    }
                    Text(ocrLabel)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove image")
                }
            }
        }
    }
}

@Composable
private fun LinkBlockCard(
    url: String,
    preview: LinkPreviewInfo?,
    onOpenUrl: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = smartNoteCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview?.pageTitle?.takeIf { it.isNotBlank() } ?: preview?.domain ?: url,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!preview?.description.isNullOrBlank()) {
                    Text(
                        text = preview?.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "Open link in browser",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ManageTagsSheet(
    aiFeaturesEnabled: Boolean,
    liveSuggestions: List<SmartTagSuggestion>,
    existingNoteTags: List<String>,
    availableVocabulary: List<String>,
    onAddTag: (String) -> Unit,
    onApplyAllAutoTags: () -> Unit,
    onDismiss: () -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val existingLower = remember(existingNoteTags) { existingNoteTags.map { it.lowercase() }.toSet() }
    val unusedVocab = remember(availableVocabulary, existingLower, liveSuggestions) {
        val suggestedLower = liveSuggestions.map { it.name.lowercase() }.toSet()
        availableVocabulary.filter {
            val l = it.lowercase()
            l !in existingLower && l !in suggestedLower
        }.take(16)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Manage Tags",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (aiFeaturesEnabled && liveSuggestions.isNotEmpty()) {
                    TextButton(onClick = onApplyAllAutoTags) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Generate tags")
                    }
                }
            }

            // Borderless tonal tag input bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (tagInput.isEmpty()) {
                                    Text(
                                        text = "Add custom tag...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
                Button(
                    enabled = tagInput.isNotBlank(),
                    onClick = { if (tagInput.isNotBlank()) onAddTag(tagInput) }
                ) {
                    Text("Add")
                }
            }

            if (aiFeaturesEnabled && liveSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Suggested for this note",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        liveSuggestions.forEach { sug ->
                            Surface(
                                onClick = { onAddTag(sug.name) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Column {
                                        Text(
                                            text = sug.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = sug.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                                        )
                                    }
                                    Icon(Icons.Rounded.Add, contentDescription = "Add ${sug.name}", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (unusedVocab.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your tags",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        unusedVocab.forEach { tagName ->
                            Surface(
                                onClick = { onAddTag(tagName) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Text(
                                    text = "#$tagName",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
