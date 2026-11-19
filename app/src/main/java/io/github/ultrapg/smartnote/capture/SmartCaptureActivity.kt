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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.domain.model.AttachmentInfo
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.links.DetectedLink
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.ocr.SafeOcrBitmapLoader
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveLoading
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveSplitSaveButton
import io.github.ultrapg.smartnote.ui.designsystem.components.rememberBoundedImageRequest
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
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
            val settings by container.settingsRepository.settingsFlow.collectAsState(
                initial = container.settingsRepository.currentSnapshot
            )
            var isStaging by remember { mutableStateOf(payload.imageUris.isNotEmpty()) }
            var stagedImages by remember { mutableStateOf<List<StagedAttachment>>(emptyList()) }
            var userEditedTitle by remember { mutableStateOf(false) }
            var titleText by remember {
                mutableStateOf(
                    sanitizeInitialSubject(
                        subject = payload.subject,
                        sharedText = payload.sharedText
                    )
                )
            }
            var bodyText by remember { mutableStateOf(payload.sharedText) }
            val selectedTags = remember { mutableStateListOf<String>() }
            var suggestedTags by remember { mutableStateOf<List<String>>(emptyList()) }
            var isGeneratingTagsPreview by remember { mutableStateOf(false) }
            var isGeneratingTitle by remember { mutableStateOf(false) }
            var previewTagJob by remember { mutableStateOf<Job?>(null) }
            var titleGenJob by remember { mutableStateOf<Job?>(null) }
            var cachedGeneratedTitles by remember { mutableStateOf<List<String>>(emptyList()) }
            var isSaving by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val liveLinks = remember(titleText, bodyText, payload.detectedLinks) {
                (SmartLinkDetector.extractLinks("$titleText\n$bodyText") + payload.detectedLinks)
                    .distinctBy { it.canonicalUrl }
            }

            val autoTitlePlaceholderHint = remember(
                settings.effectiveAutoTitleEnabled,
                titleText,
                bodyText,
                liveLinks,
                stagedImages.size
            ) {
                if (settings.effectiveAutoTitleEnabled && titleText.isBlank()) {
                    suggestInitialTitle(
                        subject = payload.subject,
                        text = bodyText,
                        links = liveLinks,
                        imageCount = stagedImages.size
                    ).ifBlank { null }
                } else {
                    null
                }
            }

            LaunchedEffect(Unit) {
                val staged = if (payload.imageUris.isNotEmpty()) {
                    container.attachmentImporter.stageIncomingUris(sessionId, payload.imageUris)
                } else {
                    emptyList()
                }
                stagedImages = staged
                isStaging = false
            }

            LaunchedEffect(
                isStaging,
                isSaving,
                titleText,
                bodyText,
                stagedImages,
                settings.aiFeaturesEnabled,
                settings.smartTagsEnabled,
                settings.effectiveMaxPredictedTags,
                settings.tagSensitivity,
                settings.preferExistingTags,
                settings.allowNewTopicTags,
                settings.effectiveGeminiNanoTagsPolicy,
                settings.geminiNanoVariant,
                settings.effectiveUseImageLabeling,
                settings.effectiveUseSystemEntities,
                settings.effectiveUseSemanticEmbeddingsForTags,
                settings.learnFromUserTags
            ) {
                previewTagJob = coroutineContext[Job]
                if (isStaging || isSaving || !settings.effectiveSmartTagsEnabled) {
                    isGeneratingTagsPreview = false
                    if (!settings.effectiveSmartTagsEnabled) {
                        suggestedTags = emptyList()
                    }
                    return@LaunchedEffect
                }

                val nowMs = System.currentTimeMillis()
                val previewBlocks = buildList {
                    if (bodyText.isNotBlank()) {
                        add(
                            NoteBlock(
                                id = "preview_text",
                                noteId = "share_preview",
                                position = size,
                                type = NoteBlockType.TEXT,
                                textContent = bodyText.trim()
                            )
                        )
                    }
                    liveLinks.forEachIndexed { idx, link ->
                        add(
                            NoteBlock(
                                id = "preview_link_$idx",
                                noteId = "share_preview",
                                position = size,
                                type = NoteBlockType.LINK,
                                textContent = link.domain,
                                linkUrl = link.rawUrl
                            )
                        )
                    }
                    stagedImages.forEachIndexed { idx, img ->
                        add(
                            NoteBlock(
                                id = "preview_img_$idx",
                                noteId = "share_preview",
                                position = size,
                                type = NoteBlockType.IMAGE,
                                attachmentId = img.id
                            )
                        )
                    }
                }
                val previewAttachments = stagedImages.map { img ->
                    AttachmentInfo(
                        id = img.id,
                        noteId = "share_preview",
                        relativeFilePath = "staged/${img.id}",
                        thumbnailRelativePath = img.thumbnailRelativePath,
                        mimeType = img.mimeType,
                        originalDisplayName = img.originalDisplayName,
                        byteSize = img.byteSize,
                        widthPx = img.widthPx,
                        heightPx = img.heightPx,
                        sha256 = img.sha256,
                        isScreenshot = img.isScreenshot,
                        createdAtMs = nowMs
                    )
                }
                val stagedFilesMap = stagedImages.associate { it.id to it.stagedFile }

                val appLocked = settings.appLockEnabled && container.appLockSession.isLocked(settings)
                val existingVocab = if (appLocked) {
                    emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        container.tagRepository.getAllTagsSnapshot().map { it.name }
                    }
                }
                val allSummaries = if (appLocked || !settings.learnFromUserTags) {
                    emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        container.noteRepository.getAllSummariesSnapshot(includeArchived = true)
                    }
                }

                // Pass 1: Immediate local rule preview
                val fastLocalTags = withContext(Dispatchers.Default) {
                    container.smartTagsEngine.suggestTags(
                        title = titleText,
                        blocks = previewBlocks,
                        ocrTexts = emptyList(),
                        links = liveLinks,
                        hasScreenshot = stagedImages.any { it.isScreenshot },
                        existingUserVocabulary = existingVocab,
                        maxTags = settings.effectiveMaxPredictedTags,
                        minConfidence = SmartTagsEngine.minConfidenceFor(settings.tagSensitivity),
                        preferExistingTags = settings.preferExistingTags
                    ).map { it.name }
                }
                if (!isActive || isSaving) return@LaunchedEffect
                suggestedTags = fastLocalTags

                // Pass 2: Full AI coordinator preview (Image Labeling, System Entities, LiteRT, Profiles, Gemini Nano)
                isGeneratingTagsPreview = true
                delay(220L)
                if (!isActive || isSaving) {
                    isGeneratingTagsPreview = false
                    return@LaunchedEffect
                }
                val fullOutcome = withContext(Dispatchers.IO) {
                    runCatching {
                        container.smartTaggingCoordinator.generateTagsForNote(
                            noteId = "share_preview",
                            title = titleText,
                            blocks = previewBlocks,
                            ocrTexts = emptyList(),
                            links = liveLinks,
                            attachments = previewAttachments,
                            existingNoteTags = emptySet(),
                            dismissedTagNames = emptySet(),
                            existingUserVocabulary = existingVocab,
                            allSummaries = allSummaries,
                            settings = settings,
                            isExplicitManualTrigger = false,
                            attachmentFilesOverride = stagedFilesMap
                        )
                    }.getOrNull()
                }
                if (isActive && !isSaving) {
                    if (fullOutcome != null && (fullOutcome.suggestions.isNotEmpty() || fastLocalTags.isEmpty())) {
                        suggestedTags = fullOutcome.suggestions.map { it.name }
                    }
                    isGeneratingTagsPreview = false
                }
            }

            fun cancelAndClose() {
                previewTagJob?.cancel()
                titleGenJob?.cancel()
                container.attachmentImporter.discardStagingSession(sessionId)
                finish()
            }

            fun generateTitleOnDemand() {
                if (!settings.aiFeaturesEnabled || isStaging || isSaving || isGeneratingTitle) return
                val currentClean = titleText.trim()
                if (cachedGeneratedTitles.isNotEmpty() && !SmartTitleGenerator.isPlaceholderOrBlankTitle(currentClean)) {
                    val idx = cachedGeneratedTitles.indexOfFirst { it.equals(currentClean, ignoreCase = true) }
                    val nextTitle = if (idx >= 0 && cachedGeneratedTitles.size > 1) {
                        cachedGeneratedTitles[(idx + 1) % cachedGeneratedTitles.size]
                    } else {
                        cachedGeneratedTitles.first()
                    }
                    userEditedTitle = true
                    titleText = nextTitle
                    return
                }
                isGeneratingTitle = true
                titleGenJob?.cancel()
                titleGenJob = scope.launch {
                    try {
                        val candidates = withContext(Dispatchers.IO) {
                            val stagedFiles = stagedImages.map { it.stagedFile }.filter { it.exists() }
                            val ocrTexts = if (settings.effectiveOcrEnabled && stagedFiles.isNotEmpty()) {
                                stagedFiles.take(2).mapNotNull { file ->
                                    runCatching {
                                        container.smartOcrCoordinator.recognizeFileWithOptionalAi(
                                            file = file,
                                            settings = settings,
                                            forceAiAssist = false
                                        ).text.takeIf { it.isNotBlank() }
                                    }.getOrNull()
                                }
                            } else {
                                emptyList()
                            }
                            val imageLabels = if (
                                settings.effectiveUseImageLabeling &&
                                stagedFiles.isNotEmpty() &&
                                container.imageLabelingEngine.checkAvailability() != ImageLabelingAvailability.UNAVAILABLE
                            ) {
                                val merged = LinkedHashMap<String, DetectedImageLabel>()
                                for (file in stagedFiles.take(2)) {
                                    container.imageLabelingEngine.labelImageFile(file).forEach { lbl ->
                                        val key = lbl.text.lowercase(Locale.ROOT)
                                        val prev = merged[key]
                                        if (prev == null || lbl.confidence > prev.confidence) merged[key] = lbl
                                    }
                                }
                                merged.values.sortedByDescending { it.confidence }.take(6).map { it.text }
                            } else {
                                emptyList()
                            }
                            val combinedForEntities = buildString {
                                if (bodyText.isNotBlank()) append(bodyText).append('\n')
                                ocrTexts.forEach { append(it).append('\n') }
                            }.trim()
                            val systemEntities = if (
                                settings.effectiveUseSystemEntities &&
                                combinedForEntities.isNotBlank() &&
                                container.systemEntityAnalyzer.checkAvailability() == SystemEntityAvailability.AVAILABLE
                            ) {
                                container.systemEntityAnalyzer.extractEntities(combinedForEntities)
                            } else {
                                emptyList()
                            }
                            val previewBlocks = buildList {
                                if (bodyText.isNotBlank()) {
                                    add(
                                        NoteBlock(
                                            id = "title_prev_text",
                                            noteId = "share_preview",
                                            position = size,
                                            type = NoteBlockType.TEXT,
                                            textContent = bodyText.trim()
                                        )
                                    )
                                }
                                liveLinks.forEachIndexed { idx, link ->
                                    add(
                                        NoteBlock(
                                            id = "title_prev_link_$idx",
                                            noteId = "share_preview",
                                            position = size,
                                            type = NoteBlockType.LINK,
                                            textContent = link.domain,
                                            linkUrl = link.rawUrl
                                        )
                                    )
                                }
                            }
                            val previewLinks = liveLinks.mapIndexed { idx, l ->
                                LinkPreviewInfo(
                                    id = "tl_$idx",
                                    noteId = "share_preview",
                                    rawUrl = l.rawUrl,
                                    canonicalUrl = l.canonicalUrl,
                                    domain = l.domain,
                                    fetchStatus = LinkFetchStatus.LOCAL_ONLY,
                                    updatedAtMs = 0L
                                )
                            }
                            val titlePolicy = settings.effectiveGeminiNanoTitlesPolicy
                            val allowTitleNano = titlePolicy != GeminiNanoFeaturePolicy.OFF
                            val forceTitleNano = titlePolicy == GeminiNanoFeaturePolicy.FORCE
                            val primaryBitmap = if (
                                allowTitleNano &&
                                stagedFiles.isNotEmpty() &&
                                (forceTitleNano || bodyText.trim().length < 160)
                            ) {
                                SafeOcrBitmapLoader.decodeUprightDownsampledBitmap(
                                    stagedFiles.first(),
                                    maxDimPx = 768
                                )
                            } else {
                                null
                            }
                            try {
                                container.smartTitleGenerator.generateTitleCandidates(
                                    blocks = previewBlocks,
                                    ocrTexts = ocrTexts,
                                    links = previewLinks,
                                    allowNeuralScoring = settings.aiFeaturesEnabled,
                                    systemEntities = systemEntities,
                                    imageLabels = imageLabels,
                                    primaryImageBitmap = primaryBitmap,
                                    allowGeminiNano = allowTitleNano,
                                    forceGeminiNano = forceTitleNano,
                                    preferFastGemini = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST,
                                    maxTitleLength = settings.effectiveMaxTitleLength
                                )
                            } finally {
                                if (primaryBitmap != null && !primaryBitmap.isRecycled) {
                                    primaryBitmap.recycle()
                                }
                            }
                        }
                        if (candidates.isNotEmpty()) {
                            cachedGeneratedTitles = candidates
                            val chosen = candidates.firstOrNull { !it.equals(titleText.trim(), ignoreCase = true) }
                                ?: candidates.first()
                            userEditedTitle = true
                            titleText = chosen
                        }
                    } finally {
                        isGeneratingTitle = false
                    }
                }
            }

            fun commitNote(pinNote: Boolean, openInEditor: Boolean) {
                if (isSaving || isStaging) return
                isSaving = true
                scope.launch {
                    previewTagJob?.cancelAndJoin()
                    titleGenJob?.cancelAndJoin()

                    val noteId = UUID.randomUUID().toString()
                    val persistedAttachments = container.attachmentImporter.commitStagedAttachments(
                        noteId = noteId,
                        stagedList = stagedImages
                    )
                    val finalLinks = (SmartLinkDetector.extractLinks("$titleText\n$bodyText") + payload.detectedLinks)
                        .distinctBy { it.canonicalUrl }
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
                        finalLinks.forEach { link ->
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
                        finalLinks.isNotEmpty() -> NoteSourceType.SHARE_URL
                        else -> NoteSourceType.SHARE_TEXT
                    }

                    val finalTitle = if (
                        !userEditedTitle &&
                        titleText.isBlank() &&
                        settings.effectiveAutoTitleEnabled &&
                        !settings.backgroundAnalysisEnabled
                    ) {
                        suggestInitialTitle(
                            subject = payload.subject,
                            text = bodyText,
                            links = finalLinks,
                            imageCount = persistedAttachments.size
                        )
                    } else {
                        titleText.trim()
                    }

                    container.noteRepository.createNote(
                        id = noteId,
                        title = finalTitle,
                        blocks = blocks,
                        initialTags = selectedTags.toList(),
                        isPinned = pinNote,
                        sourceType = sourceType,
                        attachments = persistedAttachments,
                        scheduleBackgroundWork = true
                    )
                    savedSuccessfully = true
                    container.attachmentImporter.discardStagingSession(sessionId)
                    container.noteProcessingPipeline.launchImmediate(noteId)

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
                    ocrEnabled = settings.effectiveOcrEnabled,
                    smartTagsEnabled = settings.effectiveSmartTagsEnabled,
                    autoTitleEnabled = settings.effectiveAutoTitleEnabled,
                    aiFeaturesEnabled = settings.aiFeaturesEnabled,
                    isGeneratingTagsPreview = isGeneratingTagsPreview,
                    isGeneratingTitle = isGeneratingTitle,
                    title = titleText,
                    titlePlaceholderHint = autoTitlePlaceholderHint,
                    onTitleChange = {
                        userEditedTitle = true
                        titleText = it
                    },
                    onGenerateTitleClick = ::generateTitleOnDemand,
                    body = bodyText,
                    onBodyChange = {
                        bodyText = it
                        cachedGeneratedTitles = emptyList()
                    },
                    stagedImages = stagedImages,
                    links = liveLinks,
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

        internal fun sanitizeInitialSubject(
            subject: String,
            sharedText: String
        ): String {
            val trimmed = subject.trim()
            if (trimmed.isBlank()) return ""
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true)
            ) {
                return ""
            }
            if (trimmed.equals(sharedText.trim(), ignoreCase = true)) return ""
            if (SmartTitleGenerator.isPlaceholderOrBlankTitle(trimmed)) return ""
            return trimmed.take(100)
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
    ocrEnabled: Boolean,
    smartTagsEnabled: Boolean,
    autoTitleEnabled: Boolean,
    aiFeaturesEnabled: Boolean,
    isGeneratingTagsPreview: Boolean,
    isGeneratingTitle: Boolean,
    title: String,
    titlePlaceholderHint: String?,
    onTitleChange: (String) -> Unit,
    onGenerateTitleClick: () -> Unit,
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
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 440f),
        label = "capture_sheet_reveal"
    )

    val subtitleText = when {
        stagedImages.isNotEmpty() && ocrEnabled && smartTagsEnabled && autoTitleEnabled ->
            "OCR, SmartTags & auto-title run on save"
        stagedImages.isNotEmpty() && ocrEnabled && smartTagsEnabled ->
            "OCR & SmartTags run automatically"
        stagedImages.any { it.isScreenshot } && ocrEnabled ->
            "Screenshot attached • Text extraction runs in the background"
        smartTagsEnabled && autoTitleEnabled ->
            "SmartTags & auto-title run on save"
        smartTagsEnabled ->
            "SmartTags run automatically on save"
        else ->
            "Ready to save to SmartNote"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f * revealProgress.coerceIn(0f, 1f)))
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
                .graphicsLayer {
                    val progress = revealProgress
                    translationY = (1f - progress) * 72f
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
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
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
                            text = subtitleText,
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
                        Text("Preparing shared media...")
                    }
                }

                if (stagedImages.isNotEmpty()) {
                    val appFilesDir = LocalContext.current.filesDir
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(stagedImages, key = { it.id }) { img ->
                            val previewFile: File = img.thumbnailRelativePath?.let { rel ->
                                runCatching { File(appFilesDir, rel).takeIf { it.exists() } }.getOrNull()
                            } ?: img.stagedFile
                            Box(
                                modifier = Modifier
                                    .size(width = 132.dp, height = 104.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = rememberBoundedImageRequest(
                                        file = previewFile,
                                        maxDimensionPx = 512
                                    ),
                                    contentDescription = img.originalDisplayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (img.isScreenshot && ocrEnabled) {
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

                val hasTitleSourceContent = body.isNotBlank() || links.isNotEmpty() || stagedImages.isNotEmpty()
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Note title") },
                    placeholder = if (titlePlaceholderHint != null) {
                        {
                            Text(
                                text = "Auto: $titlePlaceholderHint",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        null
                    },
                    trailingIcon = if (aiFeaturesEnabled && hasTitleSourceContent) {
                        {
                            IconButton(
                                onClick = onGenerateTitleClick,
                                enabled = !isStaging && !isSaving && !isGeneratingTitle
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Generate title",
                                    tint = if (isGeneratingTitle) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    } else {
                        null
                    },
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

                if (suggestedTags.isNotEmpty() || selectedTags.isNotEmpty() || isGeneratingTagsPreview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isGeneratingTagsPreview && suggestedTags.isEmpty()) {
                            Text(
                                text = "Finding tags...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (suggestedTags.isNotEmpty() || selectedTags.isNotEmpty()) {
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
