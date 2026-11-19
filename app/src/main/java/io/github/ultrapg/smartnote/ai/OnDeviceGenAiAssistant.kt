package io.github.ultrapg.smartnote.ai

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import io.github.ultrapg.smartnote.ocr.OcrTextPostProcessor
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Immutable
data class SearchNoteDigest(
    val noteId: String,
    val title: String,
    val tags: List<String>,
    val snippet: String
)

enum class GeminiNanoAvailability {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE
}

@Immutable
data class GeminiNanoCapabilities(
    val fastAvailability: GeminiNanoAvailability = GeminiNanoAvailability.UNAVAILABLE,
    val fullAvailability: GeminiNanoAvailability = GeminiNanoAvailability.UNAVAILABLE,
    val baseModelName: String? = null
) {
    val bestAvailability: GeminiNanoAvailability
        get() = listOf(fastAvailability, fullAvailability).minByOrNull { avail ->
            when (avail) {
                GeminiNanoAvailability.AVAILABLE -> 0
                GeminiNanoAvailability.DOWNLOADING -> 1
                GeminiNanoAvailability.DOWNLOADABLE -> 2
                GeminiNanoAvailability.UNAVAILABLE -> 3
            }
        } ?: GeminiNanoAvailability.UNAVAILABLE

    val isFastSupported: Boolean get() = fastAvailability != GeminiNanoAvailability.UNAVAILABLE
    val isFullSupported: Boolean get() = fullAvailability != GeminiNanoAvailability.UNAVAILABLE
    val isSupportedOnDevice: Boolean get() = isFastSupported || isFullSupported
}

interface OnDeviceGenAiAssistant {
    suspend fun checkAvailability(): GeminiNanoAvailability

    suspend fun probeCapabilities(forceRefresh: Boolean = false): GeminiNanoCapabilities {
        val status = checkAvailability()
        return GeminiNanoCapabilities(
            fastAvailability = status,
            fullAvailability = status
        )
    }

    fun triggerBackgroundDownloadIfNeeded()

    fun triggerBackgroundDownloadIfNeeded(preferFast: Boolean) {
        triggerBackgroundDownloadIfNeeded()
    }

    suspend fun generateTitleCandidates(
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>,
        primaryImageBitmap: Bitmap? = null
    ): List<String>

    suspend fun generateTitleCandidates(
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>,
        primaryImageBitmap: Bitmap? = null,
        preferFast: Boolean = true,
        maxTitleLength: Int = 56
    ): List<String> = generateTitleCandidates(
        noteText = noteText,
        ocrTexts = ocrTexts,
        imageLabels = imageLabels,
        linkHints = linkHints,
        primaryImageBitmap = primaryImageBitmap
    ).mapNotNull { SmartTitleGenerator.formatTitle(it, maxLen = maxTitleLength).takeIf(String::isNotBlank) }

    suspend fun generateTags(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>
    ): List<SmartTagSuggestion>

    suspend fun generateTags(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>,
        maxTags: Int = 3,
        preferFast: Boolean = true,
        allowNewTopicTags: Boolean = false,
        primaryImageBitmap: Bitmap? = null
    ): List<SmartTagSuggestion> = generateTags(
        title = title,
        noteText = noteText,
        ocrTexts = ocrTexts,
        imageLabels = imageLabels,
        existingVocabulary = existingVocabulary,
        existingNoteTags = existingNoteTags
    ).take(maxTags.coerceIn(1, 6))

    suspend fun enhanceOrExtractOcrText(
        imageBitmap: Bitmap?,
        rawOcrText: String,
        rawOcrConfidence: Float,
        preferFast: Boolean = true
    ): String? = null

    suspend fun expandSearchQuery(
        query: String,
        availableTags: List<String>,
        preferFast: Boolean = true
    ): List<String> = emptyList()

    suspend fun matchNotesForSearchQuery(
        query: String,
        candidates: List<SearchNoteDigest>,
        preferFast: Boolean = true
    ): List<String> = emptyList()

    fun close()
}

class MlKitGeminiNanoAssistant(
    private val applicationScope: CoroutineScope
) : OnDeviceGenAiAssistant {

    private val downloadInFlight = AtomicBoolean(false)

    @Volatile
    private var cachedCapabilities: GeminiNanoCapabilities? = null

    @Volatile
    private var cachedCapabilitiesAtMs: Long = 0L

    private val searchCacheLock = Any()
    private val searchExpansionCache = object : LinkedHashMap<String, List<String>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > MAX_SEARCH_CACHE_ENTRIES
    }
    private val searchMatchCache = object : LinkedHashMap<String, List<String>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > MAX_SEARCH_CACHE_ENTRIES
    }

    private val fastClient: GenerativeModel? by lazy {
        runCatching {
            Generation.getClient(
                generationConfig {
                    modelConfig = modelConfig {
                        preference = ModelPreference.FAST
                    }
                }
            )
        }.getOrNull()
    }

    private val fullClient: GenerativeModel? by lazy {
        runCatching {
            Generation.getClient(
                generationConfig {
                    modelConfig = modelConfig {
                        preference = ModelPreference.FULL
                    }
                }
            )
        }.getOrNull()
    }

    override suspend fun checkAvailability(): GeminiNanoAvailability =
        probeCapabilities(forceRefresh = false).bestAvailability

    override suspend fun probeCapabilities(forceRefresh: Boolean): GeminiNanoCapabilities =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cachedCapabilities
            val ttl = if (cached?.bestAvailability == GeminiNanoAvailability.DOWNLOADING) {
                DOWNLOADING_CACHE_TTL_MS
            } else {
                PROBE_CACHE_TTL_MS
            }
            if (!forceRefresh && cached != null && (now - cachedCapabilitiesAtMs) < ttl) {
                return@withContext cached
            }

            val fastAvail = probeModelStatus(fastClient)
            val fullAvail = probeModelStatus(fullClient)

            val readyModel = when {
                fastAvail == GeminiNanoAvailability.AVAILABLE -> fastClient
                fullAvail == GeminiNanoAvailability.AVAILABLE -> fullClient
                else -> null
            }
            val baseName = if (readyModel != null) {
                withTimeoutOrNull(STATUS_TIMEOUT_MS) {
                    runCatching { readyModel.getBaseModelName().takeIf(String::isNotBlank) }.getOrNull()
                }
            } else {
                null
            }

            GeminiNanoCapabilities(
                fastAvailability = fastAvail,
                fullAvailability = fullAvail,
                baseModelName = baseName
            ).also { resolved ->
                cachedCapabilities = resolved
                cachedCapabilitiesAtMs = System.currentTimeMillis()
            }
        }

    override fun triggerBackgroundDownloadIfNeeded() {
        triggerBackgroundDownloadIfNeeded(preferFast = true)
    }

    override fun triggerBackgroundDownloadIfNeeded(preferFast: Boolean) {
        val model = if (preferFast) {
            fastClient ?: fullClient
        } else {
            fullClient ?: fastClient
        } ?: return
        if (!downloadInFlight.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (model.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                    model.download().collect { status ->
                        if (status is DownloadStatus.DownloadCompleted || status is DownloadStatus.DownloadFailed) {
                            downloadInFlight.set(false)
                            cachedCapabilities = null
                        }
                    }
                }
            } catch (_: Throwable) {
                downloadInFlight.set(false)
            } finally {
                if (runCatching { model.checkStatus() }.getOrNull() != FeatureStatus.DOWNLOADING) {
                    downloadInFlight.set(false)
                }
                cachedCapabilities = null
            }
        }
    }

    override suspend fun generateTitleCandidates(
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>,
        primaryImageBitmap: Bitmap?
    ): List<String> = generateTitleCandidates(
        noteText = noteText,
        ocrTexts = ocrTexts,
        imageLabels = imageLabels,
        linkHints = linkHints,
        primaryImageBitmap = primaryImageBitmap,
        preferFast = true,
        maxTitleLength = 56
    )

    override suspend fun generateTitleCandidates(
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>,
        primaryImageBitmap: Bitmap?,
        preferFast: Boolean,
        maxTitleLength: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val (model, usedFast) = selectReadyModel(preferFast = preferFast) ?: return@withContext emptyList()

        val contextBlock = buildNoteContext(
            title = "",
            noteText = noteText,
            ocrTexts = ocrTexts,
            imageLabels = imageLabels,
            linkHints = linkHints
        )
        val hasImage = primaryImageBitmap != null && !primaryImageBitmap.isRecycled
        if (!hasImage && contextBlock.isBlank()) return@withContext emptyList()

        val wordLimitHint = if (maxTitleLength >= 58) "3 to 8 words" else "2 to 5 words"
        val sysRule = if (hasImage) {
            "Look at the image and any note details, then generate 2 concise, specific note titles ($wordLimitHint each, same language as the note or image text, no quotes, no numbering, one per line)."
        } else {
            "Generate 2 concise note titles ($wordLimitHint each, same language as note, no quotes, no numbering, one per line)."
        }
        val useSystem = supportsSystemPrompt(model)
        val textTimeoutMs = if (usedFast) FAST_GENERATE_TIMEOUT_MS else FULL_GENERATE_TIMEOUT_MS
        val multimodalTimeoutMs = if (usedFast) FAST_MULTIMODAL_TIMEOUT_MS else FULL_MULTIMODAL_TIMEOUT_MS

        if (hasImage) {
            val imageResponse = executeGeneration(
                model = model,
                systemRule = sysRule,
                userText = contextBlock.ifBlank { "Look at this image and generate 2 concise, specific note titles." },
                imageBitmap = primaryImageBitmap,
                useSystemPrompt = useSystem,
                temperature = 0.15f,
                topK = 12,
                maxOutputTokens = 44,
                timeoutMs = multimodalTimeoutMs
            )
            val multimodalTitles = imageResponse?.let { parseTitleLines(it, maxTitleLength = maxTitleLength) }.orEmpty()
            if (multimodalTitles.isNotEmpty() || contextBlock.isBlank()) {
                return@withContext multimodalTitles
            }
        }

        val textResponse = executeGeneration(
            model = model,
            systemRule = sysRule,
            userText = contextBlock,
            imageBitmap = null,
            useSystemPrompt = useSystem,
            temperature = 0.15f,
            topK = 12,
            maxOutputTokens = 44,
            timeoutMs = textTimeoutMs
        ) ?: return@withContext emptyList()

        parseTitleLines(textResponse, maxTitleLength = maxTitleLength)
    }

    override suspend fun generateTags(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>
    ): List<SmartTagSuggestion> = generateTags(
        title = title,
        noteText = noteText,
        ocrTexts = ocrTexts,
        imageLabels = imageLabels,
        existingVocabulary = existingVocabulary,
        existingNoteTags = existingNoteTags,
        maxTags = 3,
        preferFast = true,
        allowNewTopicTags = false,
        primaryImageBitmap = null
    )

    override suspend fun generateTags(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>,
        maxTags: Int,
        preferFast: Boolean,
        allowNewTopicTags: Boolean,
        primaryImageBitmap: Bitmap?
    ): List<SmartTagSuggestion> = withContext(Dispatchers.IO) {
        val cappedMax = maxTags.coerceIn(1, 6)
        val (model, usedFast) = selectReadyModel(preferFast = preferFast) ?: return@withContext emptyList()

        val contextBlock = buildNoteContext(
            title = title,
            noteText = noteText,
            ocrTexts = ocrTexts,
            imageLabels = imageLabels,
            linkHints = emptyList()
        )
        val canUseImage = primaryImageBitmap != null && !primaryImageBitmap.isRecycled &&
            noteText.trim().length < 160
        if (contextBlock.isBlank() && !canUseImage) return@withContext emptyList()

        val existingLower = existingNoteTags.map { it.lowercase(Locale.ROOT) }.toSet()
        val combinedAllowedVocab = buildList {
            val seenLower = HashSet<String>()
            (existingVocabulary + SmartTagsEngine.knownCategoryNames()).forEach { tag ->
                val clean = tag.trim()
                val lower = clean.lowercase(Locale.ROOT)
                if (clean.length in 2..24 && lower !in existingLower && seenLower.add(lower)) {
                    add(clean)
                }
            }
        }
        val vocabHint = combinedAllowedVocab.take(42).joinToString(", ")
        val sysRule = buildString {
            append("Assign 1 to $cappedMax concise comma-separated tags (1-2 words each, no # symbol, no explanations).")
            if (allowNewTopicTags) {
                append(" Prefer reusing existing categories/tags when accurate: $vocabHint.")
            } else {
                append(" Choose ONLY from this allowed list: $vocabHint.")
            }
            append(" Never output vague words (e.g. Note, Today, Misc, Stuff). If nothing fits clearly, output NONE.")
        }

        val useSystem = supportsSystemPrompt(model)
        val textTimeoutMs = if (usedFast) FAST_GENERATE_TIMEOUT_MS else FULL_GENERATE_TIMEOUT_MS
        val multimodalTimeoutMs = if (usedFast) FAST_MULTIMODAL_TIMEOUT_MS else FULL_MULTIMODAL_TIMEOUT_MS

        if (canUseImage) {
            val multimodalRaw = executeGeneration(
                model = model,
                systemRule = sysRule,
                userText = if (useSystem) {
                    contextBlock.ifBlank { "Classify the attached image with concise tags." }
                } else {
                    "Note:\n${contextBlock.ifBlank { "Classify the attached image." }}"
                },
                imageBitmap = primaryImageBitmap,
                useSystemPrompt = useSystem,
                temperature = 0.1f,
                topK = 8,
                maxOutputTokens = 44,
                timeoutMs = multimodalTimeoutMs
            )
            val parsedMultimodal = multimodalRaw?.let {
                parseTagTokens(
                    raw = it,
                    allowedVocabulary = combinedAllowedVocab,
                    existingNoteTags = existingLower,
                    maxTags = cappedMax,
                    allowNewTopicTags = allowNewTopicTags,
                    usedFast = usedFast
                )
            }.orEmpty()
            if (parsedMultimodal.isNotEmpty() || contextBlock.isBlank()) {
                return@withContext parsedMultimodal
            }
        }

        val rawResponse = executeGeneration(
            model = model,
            systemRule = sysRule,
            userText = if (useSystem) contextBlock else "Note:\n$contextBlock",
            imageBitmap = null,
            useSystemPrompt = useSystem,
            temperature = 0.1f,
            topK = 8,
            maxOutputTokens = 44,
            timeoutMs = textTimeoutMs
        ) ?: return@withContext emptyList()

        parseTagTokens(
            raw = rawResponse,
            allowedVocabulary = combinedAllowedVocab,
            existingNoteTags = existingLower,
            maxTags = cappedMax,
            allowNewTopicTags = allowNewTopicTags,
            usedFast = usedFast
        )
    }

    override suspend fun enhanceOrExtractOcrText(
        imageBitmap: Bitmap?,
        rawOcrText: String,
        rawOcrConfidence: Float,
        preferFast: Boolean
    ): String? = withContext(Dispatchers.IO) {
        val baseline = rawOcrText.trim()
        val hasImage = imageBitmap != null && !imageBitmap.isRecycled
        if (!hasImage && baseline.length < 20) return@withContext null

        val (model, usedFast) = selectReadyModel(preferFast = preferFast) ?: return@withContext null
        val useSystem = supportsSystemPrompt(model)
        val ocrTimeoutMs = if (usedFast) FAST_OCR_TIMEOUT_MS else FULL_OCR_TIMEOUT_MS

        if (hasImage) {
            val sysRule = if (baseline.isBlank()) {
                "Transcribe all readable text in this image verbatim, keeping exact numbers, dates, and line breaks. Output ONLY the extracted text with no commentary. If there is no readable text, output NONE."
            } else {
                "Compare this image with the rough OCR draft and output the corrected verbatim text. Fix misread characters, broken words, and reading order without inventing text. Output ONLY the clean text. If unreadable, output NONE."
            }
            val userText = if (baseline.isBlank()) {
                "Extract all readable text verbatim."
            } else {
                "Rough OCR draft:\n${baseline.take(1800)}"
            }
            val multimodalRaw = executeGeneration(
                model = model,
                systemRule = sysRule,
                userText = userText,
                imageBitmap = imageBitmap,
                useSystemPrompt = useSystem,
                temperature = 0.05f,
                topK = 8,
                maxOutputTokens = 384,
                timeoutMs = ocrTimeoutMs
            )
            val cleaned = multimodalRaw?.let { sanitizeOcrAssistantOutput(it, baseline) }
            if (!cleaned.isNullOrBlank()) return@withContext cleaned
        }

        if (baseline.length in 20..1800) {
            val textRule = "Fix obvious OCR character typos, broken hyphenations, and spacing in the text below without changing meaning, numbers, or language. Output ONLY the cleaned text."
            val textRaw = executeGeneration(
                model = model,
                systemRule = textRule,
                userText = baseline,
                imageBitmap = null,
                useSystemPrompt = useSystem,
                temperature = 0.05f,
                topK = 8,
                maxOutputTokens = 384,
                timeoutMs = if (usedFast) FAST_GENERATE_TIMEOUT_MS else FULL_GENERATE_TIMEOUT_MS
            )
            val cleaned = textRaw?.let { sanitizeOcrAssistantOutput(it, baseline) }
            if (!cleaned.isNullOrBlank()) return@withContext cleaned
        }

        null
    }

    override suspend fun expandSearchQuery(
        query: String,
        availableTags: List<String>,
        preferFast: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val tagSlice = availableTags.take(24)
        val cacheKey = "${cleanQuery.lowercase(Locale.ROOT)}|${tagSlice.joinToString(",")}|$preferFast"
        synchronized(searchCacheLock) {
            searchExpansionCache[cacheKey]?.let { return@withContext it }
        }

        val (model, usedFast) = selectReadyModel(preferFast = preferFast) ?: return@withContext emptyList()
        val useSystem = supportsSystemPrompt(model)
        val sysRule = "Given a note search query, return 2 to 5 single-word search keywords, synonyms, or matching tags (include English and German equivalents when useful). Comma-separated words only, no explanations. If none, output NONE."
        val userText = buildString {
            append("Query: ").append(cleanQuery)
            if (tagSlice.isNotEmpty()) {
                append("\nTags: ").append(tagSlice.joinToString(", "))
            }
        }
        val rawResponse = executeGeneration(
            model = model,
            systemRule = sysRule,
            userText = userText,
            imageBitmap = null,
            useSystemPrompt = useSystem,
            temperature = 0.1f,
            topK = 8,
            maxOutputTokens = 32,
            timeoutMs = if (usedFast) SEARCH_FAST_TIMEOUT_MS else SEARCH_FULL_TIMEOUT_MS
        ) ?: return@withContext emptyList()

        val expanded = parseSearchExpansionTerms(rawResponse, cleanQuery)
        synchronized(searchCacheLock) {
            searchExpansionCache[cacheKey] = expanded
        }
        expanded
    }

    override suspend fun matchNotesForSearchQuery(
        query: String,
        candidates: List<SearchNoteDigest>,
        preferFast: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val limitedCandidates = candidates.take(36)
        if (cleanQuery.length < 2 || limitedCandidates.isEmpty()) return@withContext emptyList()

        val fingerprint = limitedCandidates.joinToString(",") { it.noteId }.hashCode()
        val cacheKey = "${cleanQuery.lowercase(Locale.ROOT)}|${limitedCandidates.size}|$fingerprint|$preferFast"
        synchronized(searchCacheLock) {
            searchMatchCache[cacheKey]?.let { return@withContext it }
        }

        val (model, usedFast) = selectReadyModel(preferFast = preferFast) ?: return@withContext emptyList()
        val useSystem = supportsSystemPrompt(model)
        val sysRule = "Match the user's search query to the most relevant notes by meaning, topic, or language (English & German). Return ONLY the comma-separated numbers of the 1 to 5 best matching notes, most relevant first. If no note matches, output NONE."
        val catalogText = buildString {
            append("Query: ").append(cleanQuery).append("\nNotes:\n")
            limitedCandidates.forEachIndexed { idx, digest ->
                if (length >= 2_400) return@forEachIndexed
                val titlePart = digest.title.trim().ifBlank { "Untitled" }.take(32)
                val tagsPart = if (digest.tags.isNotEmpty()) {
                    " [${digest.tags.take(3).joinToString(",") { "#$it" }}]"
                } else {
                    ""
                }
                val snippetPart = digest.snippet.trim().replace(Regex("\\s+"), " ").take(55)
                append("${idx + 1}: $titlePart$tagsPart")
                if (snippetPart.isNotBlank()) append(" - $snippetPart")
                append('\n')
            }
        }.trim()

        val rawResponse = executeGeneration(
            model = model,
            systemRule = sysRule,
            userText = catalogText,
            imageBitmap = null,
            useSystemPrompt = useSystem,
            temperature = 0.05f,
            topK = 8,
            maxOutputTokens = 24,
            timeoutMs = if (usedFast) SEARCH_MATCH_FAST_TIMEOUT_MS else SEARCH_MATCH_FULL_TIMEOUT_MS
        ) ?: return@withContext emptyList()

        val indices = Parsers.parseMatchedNoteIndices(rawResponse, limitedCandidates.size)
        val matchedIds = indices.mapNotNull { oneBased -> limitedCandidates.getOrNull(oneBased - 1)?.noteId }
        synchronized(searchCacheLock) {
            searchMatchCache[cacheKey] = matchedIds
        }
        matchedIds
    }

    override fun close() {
        runCatching { fastClient?.close() }
        runCatching { fullClient?.close() }
    }

    private suspend fun supportsSystemPrompt(model: GenerativeModel): Boolean = runCatching {
        withTimeoutOrNull(SYSTEM_CHECK_TIMEOUT_MS) { Feature.SYSTEM_PROMPT in model.getAvailableFeatures() } == true
    }.getOrDefault(false)

    private suspend fun executeGeneration(
        model: GenerativeModel,
        systemRule: String,
        userText: String,
        imageBitmap: Bitmap?,
        useSystemPrompt: Boolean,
        temperature: Float,
        topK: Int,
        maxOutputTokens: Int,
        timeoutMs: Long
    ): String? = withTimeoutOrNull(timeoutMs) {
        runCatching {
            val hasImage = imageBitmap != null && !imageBitmap.isRecycled
            val finalBody = if (useSystemPrompt) userText else "$systemRule\n\n$userText"
            val builder = when {
                hasImage && useSystemPrompt -> GenerateContentRequest.Builder(
                    SystemInstruction(systemRule),
                    ImagePart(imageBitmap!!),
                    TextPart(finalBody)
                )
                hasImage -> GenerateContentRequest.Builder(
                    ImagePart(imageBitmap!!),
                    TextPart(finalBody)
                )
                useSystemPrompt -> GenerateContentRequest.Builder(
                    SystemInstruction(systemRule),
                    TextPart(finalBody)
                )
                else -> GenerateContentRequest.Builder(TextPart(finalBody))
            }
            val request = builder.apply {
                this.temperature = temperature
                this.topK = topK
                this.maxOutputTokens = maxOutputTokens
                candidateCount = 1
                enableThinking = false
            }.build()
            model.generateContent(request).candidates.firstOrNull()?.text
        }.getOrNull()
    }

    private suspend fun selectReadyModel(preferFast: Boolean): Pair<GenerativeModel, Boolean>? {
        val caps = probeCapabilities(forceRefresh = false)
        return when {
            preferFast && caps.fastAvailability == GeminiNanoAvailability.AVAILABLE && fastClient != null ->
                fastClient!! to true
            preferFast && caps.fullAvailability == GeminiNanoAvailability.AVAILABLE && fullClient != null ->
                fullClient!! to false
            !preferFast && caps.fullAvailability == GeminiNanoAvailability.AVAILABLE && fullClient != null ->
                fullClient!! to false
            !preferFast && caps.fastAvailability == GeminiNanoAvailability.AVAILABLE && fastClient != null ->
                fastClient!! to true
            else -> null
        }
    }

    private suspend fun probeModelStatus(model: GenerativeModel?): GeminiNanoAvailability {
        if (model == null) return GeminiNanoAvailability.UNAVAILABLE
        return withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            runCatching {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> GeminiNanoAvailability.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> GeminiNanoAvailability.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> GeminiNanoAvailability.DOWNLOADING
                    else -> GeminiNanoAvailability.UNAVAILABLE
                }
            }.getOrDefault(GeminiNanoAvailability.UNAVAILABLE)
        } ?: GeminiNanoAvailability.UNAVAILABLE
    }

    private fun buildNoteContext(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>
    ): String = buildString {
        if (title.isNotBlank()) append("Title: ").append(title.trim().take(120)).append('\n')
        if (noteText.isNotBlank()) append(noteText.trim().take(1200)).append('\n')
        val cleanOcr = ocrTexts.filter { it.isNotBlank() }.joinToString(" ").trim()
        if (cleanOcr.isNotBlank()) append("Image text: ").append(cleanOcr.take(600)).append('\n')
        if (imageLabels.isNotEmpty()) append("Image objects: ").append(imageLabels.take(6).joinToString(", ")).append('\n')
        if (linkHints.isNotEmpty()) append("Links: ").append(linkHints.take(4).joinToString(", ")).append('\n')
    }.trim()

    private fun parseTitleLines(raw: String, maxTitleLength: Int): List<String> {
        val seen = HashSet<String>()
        return raw.lineSequence()
            .map { line ->
                line.trim()
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .replace(LEADING_TITLE_LABEL_REGEX, "")
                    .trim('"', '\'', '“', '”', '*', '`', ' ')
            }
            .filter { candidate ->
                candidate.length in 3..72 &&
                    !looksLikeRefusalOrChatter(candidate)
            }
            .mapNotNull { SmartTitleGenerator.formatTitle(it, maxLen = maxTitleLength).takeIf(String::isNotBlank) }
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
            .take(2)
            .toList()
    }

    private fun parseTagTokens(
        raw: String,
        allowedVocabulary: List<String>,
        existingNoteTags: Set<String>,
        maxTags: Int,
        allowNewTopicTags: Boolean,
        usedFast: Boolean
    ): List<SmartTagSuggestion> {
        val normalizedRaw = raw.lineSequence()
            .joinToString(",") { line ->
                line.trim()
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .replace(LEADING_TITLE_LABEL_REGEX, "")
            }

        val vocabByLower = allowedVocabulary.associateBy { it.lowercase(Locale.ROOT) }
        val seen = HashSet<String>()
        val confidence = if (usedFast) 0.89f else 0.92f
        val reason = if (usedFast) "Gemini Nano Fast" else "Gemini Nano"

        return normalizedRaw.split(',', '\n', ';')
            .map { tok ->
                tok.trim()
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .removePrefix("#")
                    .trim('"', '\'', '.', ':', '!', '*', '`', ' ', '(', ')')
            }
            .filter { tok ->
                val lower = tok.lowercase(Locale.ROOT)
                tok.length in 2..24 &&
                    tok.split(Regex("\\s+")).size <= 2 &&
                    !looksLikeRefusalOrChatter(tok) &&
                    lower !in REFUSAL_OR_GENERIC_TOKENS &&
                    !SmartTagsEngine.isGenericStopword(lower) &&
                    tok.any { it.isLetter() }
            }
            .mapNotNull { cleanTok ->
                val lower = cleanTok.lowercase(Locale.ROOT)
                val matchedExisting = vocabByLower[lower]
                when {
                    matchedExisting != null -> matchedExisting
                    allowNewTopicTags -> cleanTok.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                    }
                    else -> null
                }
            }
            .filter { finalTag ->
                val lower = finalTag.lowercase(Locale.ROOT)
                lower !in existingNoteTags && seen.add(lower)
            }
            .take(maxTags)
            .map { finalTag ->
                SmartTagSuggestion(
                    name = finalTag,
                    confidence = confidence,
                    reason = reason
                )
            }
    }

    private fun looksLikeRefusalOrChatter(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return REFUSAL_PREFIXES.any {
            lower.startsWith(it) || "i cannot" in lower || "i'm sorry" in lower || "as an ai" in lower
        }
    }

    private companion object {
        const val MAX_SEARCH_CACHE_ENTRIES = 32
        const val STATUS_TIMEOUT_MS = 1_500L
        const val SYSTEM_CHECK_TIMEOUT_MS = 400L
        const val FAST_GENERATE_TIMEOUT_MS = 2_500L
        const val FULL_GENERATE_TIMEOUT_MS = 4_500L
        const val FAST_MULTIMODAL_TIMEOUT_MS = 3_500L
        const val FULL_MULTIMODAL_TIMEOUT_MS = 5_500L
        const val FAST_OCR_TIMEOUT_MS = 4_500L
        const val FULL_OCR_TIMEOUT_MS = 7_000L
        const val SEARCH_FAST_TIMEOUT_MS = 1_200L
        const val SEARCH_FULL_TIMEOUT_MS = 2_000L
        const val SEARCH_MATCH_FAST_TIMEOUT_MS = 2_200L
        const val SEARCH_MATCH_FULL_TIMEOUT_MS = 3_500L
        const val PROBE_CACHE_TTL_MS = 60_000L
        const val DOWNLOADING_CACHE_TTL_MS = 5_000L
        val LEADING_BULLET_OR_NUMBER_REGEX = Regex("""^(?:[-*•]\s*|\d+[.)]\s*)""")
        val LEADING_TITLE_LABEL_REGEX = Regex(
            """^(?:title|titles|titel|suggestion|suggested tags|tags|tag|kategorien|categories)\s*:\s*""",
            RegexOption.IGNORE_CASE
        )
        val LEADING_OCR_LABEL_REGEX = Regex(
            """^(?:extracted text|recognized text|transcribed text|cleaned text|corrected text|ocr text|ocr|text)\s*:\s*""",
            RegexOption.IGNORE_CASE
        )
        val LEADING_SEARCH_PREAMBLE_REGEX = Regex(
            """^(?:sure|certainly|here(?:\s+are|\s+is)|keywords|search\s+terms|synonyms|terms|tags|hier\s+sind)[^:\n]{0,40}:\s*""",
            RegexOption.IGNORE_CASE
        )
        val REFUSAL_PREFIXES = listOf("sure,", "sure!", "here are", "here is", "sorry", "hier sind", "natürlich")
        val OCR_DESCRIPTION_PREFIXES = listOf(
            "the image shows", "this image shows", "in this image", "i can see", "there is no",
            "no readable text", "das bild zeigt", "auf dem bild", "im bild sieht man"
        )
        val REFUSAL_OR_GENERIC_TOKENS = setOf(
            "tag", "tags", "note", "notes", "notiz", "none", "n/a", "no tags", "keine", "misc", "other", "general",
            "sure", "here", "okay", "certainly", "keywords", "synonyms", "terms", "hier", "natürlich", "gerne"
        )
        val EMPTY_OCR_MARKERS = setOf("none", "n/a", "na", "no text", "no readable text", "keine", "kein text")
        val CODE_FENCE_REGEX = Regex("""^```[a-zA-Z]*\s*\n?|\n?```$""")
    }

    internal object Parsers {
        fun sanitizeOcrAssistantOutput(rawResponse: String, baselineOcr: String): String? {
            var cleaned = rawResponse.trim()
                .replace(LEADING_OCR_LABEL_REGEX, "")
                .trim()
                .replace(CODE_FENCE_REGEX, "")
                .trim()
                .replace(LEADING_OCR_LABEL_REGEX, "")
                .trim()
            if (cleaned.isBlank()) return null

            val lower = cleaned.lowercase(Locale.ROOT)
            if (lower in EMPTY_OCR_MARKERS) return null
            if (REFUSAL_PREFIXES.any { lower.startsWith(it) || "i cannot" in lower || "i'm sorry" in lower || "as an ai" in lower }) {
                return null
            }
            if (OCR_DESCRIPTION_PREFIXES.any { lower.startsWith(it) }) return null

            cleaned = OcrTextPostProcessor.cleanRecognizedText(cleaned)
            if (cleaned.isBlank()) return null

            val baseClean = baselineOcr.trim()
            if (baseClean.length >= 40) {
                val maxAllowedLen = (baseClean.length * 1.65).toInt() + 80
                val minAllowedLen = (baseClean.length * 0.45).toInt()
                if (cleaned.length > maxAllowedLen || cleaned.length < minAllowedLen) {
                    return null
                }
            }
            return cleaned
        }

        fun parseSearchExpansionTerms(rawResponse: String, originalQuery: String): List<String> {
            val originalTokens = originalQuery.lowercase(Locale.ROOT)
                .split(Regex("\\s+"))
                .map { it.trim(',', '.', ';', ':', '?', '!') }
                .filter { it.isNotEmpty() }
                .toSet()

            val seen = HashSet<String>()
            val result = ArrayList<String>(5)
            val normalized = rawResponse.lineSequence().joinToString(",") { line ->
                line.trim()
                    .replace(LEADING_SEARCH_PREAMBLE_REGEX, "")
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .replace(LEADING_TITLE_LABEL_REGEX, "")
            }
            for (rawTok in normalized.split(',', '\n', ';')) {
                val clean = rawTok.trim()
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .removePrefix("#")
                    .trim('"', '\'', '.', ':', '!', '*', '`', ' ', '(', ')')
                val lower = clean.lowercase(Locale.ROOT)
                if (clean.length !in 3..22) continue
                if (clean.any { it.isWhitespace() }) continue
                if (!clean.any { it.isLetter() }) continue
                if (lower in originalTokens) continue
                if (lower in REFUSAL_OR_GENERIC_TOKENS) continue
                if (REFUSAL_PREFIXES.any { lower.startsWith(it) }) continue
                if (SmartTagsEngine.isGenericStopword(lower)) continue
                if (seen.add(lower)) {
                    result += clean
                    if (result.size >= 5) break
                }
            }
            return result
        }

        fun parseMatchedNoteIndices(rawResponse: String, maxIndex: Int): List<Int> {
            if (maxIndex <= 0) return emptyList()
            val cleaned = rawResponse.trim().lowercase(Locale.ROOT)
            if (cleaned.isBlank() || cleaned in EMPTY_OCR_MARKERS || cleaned in REFUSAL_OR_GENERIC_TOKENS) {
                return emptyList()
            }
            if (REFUSAL_PREFIXES.any { cleaned.startsWith(it) && cleaned.none { ch -> ch.isDigit() } }) {
                return emptyList()
            }
            val seen = HashSet<Int>()
            val result = ArrayList<Int>(5)
            for (match in Regex("""\b(\d{1,3})\b""").findAll(rawResponse)) {
                val idx = match.groupValues[1].toIntOrNull() ?: continue
                if (idx in 1..maxIndex && seen.add(idx)) {
                    result += idx
                    if (result.size >= 5) break
                }
            }
            return result
        }
    }

    private fun sanitizeOcrAssistantOutput(rawResponse: String, baselineOcr: String): String? =
        Parsers.sanitizeOcrAssistantOutput(rawResponse, baselineOcr)

    private fun parseSearchExpansionTerms(rawResponse: String, originalQuery: String): List<String> =
        Parsers.parseSearchExpansionTerms(rawResponse, originalQuery)
}
