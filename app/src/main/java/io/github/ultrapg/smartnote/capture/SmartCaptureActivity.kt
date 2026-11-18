package io.github.ultrapg.smartnote.capture

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.links.DetectedLink
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveLoading
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveSplitSaveButton
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ParsedSharePayload(
    val subject: String,
    val sharedText: String,
    val imageUris: List<Uri>,
    val detectedLinks: List<DetectedLink>
)

class SmartCaptureActivity : ComponentActivity() {

    private var savedSuccessfully = false
    private lateinit var sessionId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sessionId = UUID.randomUUID().toString()
        val app = applicationContext as SmartNoteApplication
        val container = app.container
        val payload = extractSharePayload(intent)

        if (payload.sharedText.isBlank() && payload.imageUris.isEmpty()) {
            finish()
            return
        }

        setContent {
            val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
            var isStaging by remember { mutableStateOf(payload.imageUris.isNotEmpty()) }
            var stagedImages by remember { mutableStateOf<List<StagedAttachment>>(emptyList()) }
            var titleText by remember {
                mutableStateOf(
                    if (settings.autoTitleEnabled) {
                        suggestInitialTitle(
                            subject = payload.subject,
                            text = payload.sharedText,
                            links = payload.detectedLinks,
                            imageCount = payload.imageUris.size
                        )
                    } else {
                        payload.subject.take(100)
                    }
                )
            }
            var bodyText by remember { mutableStateOf(payload.sharedText) }
            val selectedTags = remember { mutableStateListOf<String>() }
            var suggestedTags by remember { mutableStateOf<List<String>>(emptyList()) }
            var isSaving by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val staged = if (payload.imageUris.isNotEmpty()) {
                    container.attachmentImporter.stageIncomingUris(sessionId, payload.imageUris)
                } else {
                    emptyList()
                }
                stagedImages = staged
                isStaging = false

                // Compute instant SmartTags preview
                val previewBlocks = listOf(
                    NoteBlock(
                        id = "preview",
                        noteId = "preview",
                        position = 0,
                        type = NoteBlockType.TEXT,
                        textContent = payload.sharedText
                    )
                )
                val existingTags = container.tagRepository.getAllTagsSnapshot().map { it.name }
                suggestedTags = container.smartTagsEngine.suggestTags(
                    title = titleText,
                    blocks = previewBlocks,
                    ocrTexts = emptyList(),
                    links = payload.detectedLinks,
                    hasScreenshot = staged.any { it.isScreenshot },
                    existingUserVocabulary = existingTags
                ).map { it.name }
            }

            fun cancelAndClose() {
                container.attachmentImporter.discardStagingSession(sessionId)
                finish()
            }

            fun commitNote(pinNote: Boolean, openInEditor: Boolean) {
                if (isSaving || isStaging) return
                isSaving = true
                scope.launch {
                    val noteId = UUID.randomUUID().toString()
                    val persistedAttachments = container.attachmentImporter.commitStagedAttachments(
                        noteId = noteId,
                        stagedList = stagedImages
                    )
                    val blocks = buildList {
                        if (bodyText.isNotBlank()) {
                            add(
                                NoteBlock(
                                    id = UUID.randomUUID().toString(),
                                    noteId = noteId,
                                    position = size,
                                    type = NoteBlockType.TEXT,
                                    textContent = bodyText.trim()
                                )
                            )
                        }
                        payload.detectedLinks.forEach { link ->
                            add(
                                NoteBlock(
                                    id = UUID.randomUUID().toString(),
                                    noteId = noteId,
                                    position = size,
                                    type = NoteBlockType.LINK,
                                    textContent = link.domain,
                                    linkUrl = link.rawUrl
                                )
                            )
                        }
                        persistedAttachments.forEach { att ->
                            add(
                                NoteBlock(
                                    id = UUID.randomUUID().toString(),
                                    noteId = noteId,
                                    position = size,
                                    type = NoteBlockType.IMAGE,
                                    attachmentId = att.id
                                )
                            )
                        }
                    }

                    val sourceType = when {
                        persistedAttachments.isNotEmpty() -> NoteSourceType.SHARE_IMAGE
                        payload.detectedLinks.isNotEmpty() -> NoteSourceType.SHARE_URL
                        else -> NoteSourceType.SHARE_TEXT
                    }

                    container.noteRepository.createNote(
                        id = noteId,
                        title = titleText.trim(),
                        blocks = blocks,
                        initialTags = selectedTags.toList(),
                        isPinned = pinNote,
                        sourceType = sourceType,
                        attachments = persistedAttachments,
                        scheduleBackgroundWork = true
                    )
                    savedSuccessfully = true
                    container.attachmentImporter.discardStagingSession(sessionId)

                    if (openInEditor) {
                        startActivity(
                            Intent(this@SmartCaptureActivity, MainActivity::class.java).apply {
                                action = MainActivity.ACTION_OPEN_NOTE
                                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                    }
                    finish()
                }
            }

            BackHandler { cancelAndClose() }

            SmartNoteTheme(
                palette = settings.themePalette,
                darkModeConfig = settings.darkModeConfig,
                reducedMotionPreference = settings.reducedMotion
            ) {
                SmartCaptureSheetContent(
                    isStaging = isStaging,
                    isSaving = isSaving,
                    title = titleText,
                    onTitleChange = { titleText = it },
                    body = bodyText,
                    onBodyChange = { bodyText = it },
                    stagedImages = stagedImages,
                    links = payload.detectedLinks,
                    suggestedTags = suggestedTags,
                    selectedTags = selectedTags,
                    onToggleSuggestedTag = { tag ->
                        if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                    },
                    onAddCustomTag = { tag ->
                        val clean = tag.trim().removePrefix("#")
                        if (clean.isNotBlank() && selectedTags.none { it.equals(clean, ignoreCase = true) }) {
                            selectedTags.add(clean)
                        }
                    },
                    onRemoveSelectedTag = { selectedTags.remove(it) },
                    onDismiss = ::cancelAndClose,
                    onQuickSave = { commitNote(pinNote = false, openInEditor = false) },
                    onSaveAndEdit = { commitNote(pinNote = false, openInEditor = true) },
                    onSaveAndPin = { commitNote(pinNote = true, openInEditor = false) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!savedSuccessfully && isFinishing) {
            (applicationContext as? SmartNoteApplication)
                ?.container
                ?.attachmentImporter
                ?.discardStagingSession(sessionId)
        }
    }

    companion object {
        fun extractSharePayload(intent: Intent?): ParsedSharePayload {
            if (intent == null) return ParsedSharePayload("", "", emptyList(), emptyList())
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: ""
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: ""

            val uris = LinkedHashSet<Uri>()
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    getParcelableUriExtra(intent, Intent.EXTRA_STREAM)?.let(uris::add)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    getParcelableUriListExtra(intent, Intent.EXTRA_STREAM).forEach(uris::add)
                }
            }
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let(uris::add)
                }
            }

            val links = SmartLinkDetector.extractLinks("$subject\n$text")
            return ParsedSharePayload(
                subject = subject.trim(),
                sharedText = text.trim(),
                imageUris = uris.toList(),
                detectedLinks = links
            )
        }

        @Suppress("DEPRECATION")
        private fun getParcelableUriExtra(intent: Intent, key: String): Uri? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(key, Uri::class.java)
            } else {
                intent.getParcelableExtra(key)
            }
        }

        @Suppress("DEPRECATION")
        private fun getParcelableUriListExtra(intent: Intent, key: String): List<Uri> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
            } else {
                intent.getParcelableArrayListExtra<Uri>(key).orEmpty()
            }
        }

        internal fun suggestInitialTitle(
            subject: String,
            text: String,
            links: List<DetectedLink>,
            imageCount: Int
        ): String {
            val blocks = if (text.isNotBlank()) {
                listOf(
                    NoteBlock(
                        id = "preview",
                        noteId = "preview",
                        position = 0,
                        type = NoteBlockType.TEXT,
                        textContent = text
                    )
                )
            } else {
                emptyList()
            }
            val linkPreviews = links.mapIndexed { idx, l ->
                LinkPreviewInfo(
                    id = "l$idx",
                    noteId = "preview",
                    rawUrl = l.rawUrl,
                    canonicalUrl = l.canonicalUrl,
                    domain = l.domain,
                    fetchStatus = LinkFetchStatus.LOCAL_ONLY,
                    updatedAtMs = 0L
                )
            }
            val smart = SmartTitleGenerator().generateFastTitle(
                blocks = blocks,
                links = linkPreviews,
                rawSubject = subject
            )
            if (smart.isNotBlank()) return smart
            if (imageCount == 1) return "Shared image"
            if (imageCount > 1) return "$imageCount shared images"
            return ""
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartCaptureSheetContent(
    isStaging: Boolean,
    isSaving: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    stagedImages: List<StagedAttachment>,
    links: List<DetectedLink>,
    suggestedTags: List<String>,
    selectedTags: List<String>,
    onToggleSuggestedTag: (String) -> Unit,
    onAddCustomTag: (String) -> Unit,
    onRemoveSelectedTag: (String) -> Unit,
    onDismiss: () -> Unit,
    onQuickSave: () -> Unit,
    onSaveAndEdit: () -> Unit,
    onSaveAndPin: () -> Unit
) {
    var customTagDraft by remember { mutableStateOf("") }
    var showSaveMenu by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    LaunchedEffect(Unit) { entered = true }
    val revealProgress by animateFloatAsState(
        targetValue = if (entered || reducedMotion) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 210f),
        label = "capture_sheet_reveal"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * revealProgress.coerceIn(0f, 1f)))
            .statusBarsPadding()
            .padding(top = 24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .graphicsLayer {
                    val progress = revealProgress
                    translationY = (1f - progress) * 140f
                    val s = 0.94f + (0.06f * progress)
                    scaleX = s
                    scaleY = s
                    alpha = progress.coerceIn(0f, 1f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SmartCapture",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (stagedImages.any { it.isScreenshot }) {
                                "Screenshot preserved offline • OCR runs in background"
                            } else {
                                "Saved locally • 100% offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel share")
                    }
                }

                if (isStaging) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveLoading(modifier = Modifier.size(32.dp))
                        Text("Copying shared media safely to local storage...")
                    }
                }

                if (stagedImages.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(stagedImages, key = { it.id }) { img ->
                            val previewFile: File = img.stagedFile
                            Box(
                                modifier = Modifier
                                    .size(width = 132.dp, height = 104.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = previewFile,
                                    contentDescription = img.originalDisplayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (img.isScreenshot) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Screenshot,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("OCR queued", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (links.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = links.first().domain,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = links.first().rawUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Note title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (body.isNotBlank() || stagedImages.isEmpty()) {
                    OutlinedTextField(
                        value = body,
                        onValueChange = onBodyChange,
                        label = { Text("Content") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (suggestedTags.isNotEmpty() || selectedTags.isNotEmpty()) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveSelectedTag(tag) },
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove $tag",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        suggestedTags
                            .filter { sug -> selectedTags.none { it.equals(sug, ignoreCase = true) } }
                            .forEach { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onToggleSuggestedTag(suggestion) },
                                    label = { Text("+ $suggestion") }
                                )
                            }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customTagDraft,
                        onValueChange = { customTagDraft = it },
                        label = { Text("Add tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    IconButton(
                        onClick = {
                            onAddCustomTag(customTagDraft)
                            customTagDraft = ""
                        },
                        enabled = customTagDraft.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add custom tag")
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Box {
                        ExpressiveSplitSaveButton(
                            primaryLabel = if (isSaving) "Saving..." else "Save Note",
                            onPrimaryClick = onQuickSave,
                            onMenuClick = { showSaveMenu = true },
                            menuContentDescription = "More save options",
                            enabled = !isStaging && !isSaving
                        )
                        DropdownMenu(
                            expanded = showSaveMenu,
                            onDismissRequest = { showSaveMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save & Open in SmartNote") },
                                leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                                onClick = {
                                    showSaveMenu = false
                                    onSaveAndEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save & Pin") },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                onClick = {
                                    showSaveMenu = false
                                    onSaveAndPin()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
