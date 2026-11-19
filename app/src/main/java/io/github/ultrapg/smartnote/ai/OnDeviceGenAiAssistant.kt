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
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
        val hasSparseText = noteText.trim().length < 20 && ocrTexts.all { it.isBlank() }
        val wordLimitHint = if (maxTitleLength >= 58) "3 to 8 words" else "2 to 5 words"
        val useSystem = runCatching {
            withTimeoutOrNull(SYSTEM_CHECK_TIMEOUT_MS) { Feature.SYSTEM_PROMPT in model.getAvailableFeatures() } == true
        }.getOrDefault(false)

        val sysRule = "Generate 2 concise note titles ($wordLimitHint each, same language as note, no quotes, no numbering, one per line)."
        val timeoutMs = if (usedFast) FAST_GENERATE_TIMEOUT_MS else FULL_GENERATE_TIMEOUT_MS

        val rawResponse = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val request = if (primaryImageBitmap != null && !primaryImageBitmap.isRecycled && hasSparseText) {
                    val userText = if (useSystem) {
                        contextBlock.ifBlank { "Describe this image as concise note titles." }
                    } else {
                        "$sysRule\n$contextBlock"
                    }
                    if (useSystem) {
                        GenerateContentRequest.Builder(
                            SystemInstruction(sysRule),
                            ImagePart(primaryImageBitmap),
                            TextPart(userText)
                        )
                    } else {
                        GenerateContentRequest.Builder(ImagePart(primaryImageBitmap), TextPart(userText))
                    }.apply {
                        temperature = 0.15f
                        topK = 12
                        maxOutputTokens = 44
                        candidateCount = 1
                        enableThinking = false
                    }.build()
                } else {
                    if (contextBlock.isBlank()) return@runCatching null
                    val userText = if (useSystem) contextBlock else "$sysRule\n$contextBlock"
                    if (useSystem) {
                        GenerateContentRequest.Builder(SystemInstruction(sysRule), TextPart(userText))
                    } else {
                        GenerateContentRequest.Builder(TextPart(userText))
                    }.apply {
                        temperature = 0.15f
                        topK = 12
                        maxOutputTokens = 44
                        candidateCount = 1
                        enableThinking = false
                    }.build()
                }
                model.generateContent(request).candidates.firstOrNull()?.text
            }.getOrNull()
        } ?: return@withContext emptyList()

        parseTitleLines(rawResponse, maxTitleLength = maxTitleLength)
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
            noteText.trim().length < 24 && ocrTexts.all { it.isBlank() }
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

        val useSystem = runCatching {
            withTimeoutOrNull(SYSTEM_CHECK_TIMEOUT_MS) { Feature.SYSTEM_PROMPT in model.getAvailableFeatures() } == true
        }.getOrDefault(false)
        val timeoutMs = if (usedFast) FAST_GENERATE_TIMEOUT_MS else FULL_GENERATE_TIMEOUT_MS

        val rawResponse = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val notePromptBody = if (useSystem) {
                    contextBlock.ifBlank { "Classify the attached image with concise tags." }
                } else {
                    "$sysRule\n\nNote:\n$contextBlock"
                }
                val requestBuilder = when {
                    canUseImage && useSystem -> GenerateContentRequest.Builder(
                        SystemInstruction(sysRule),
                        ImagePart(primaryImageBitmap!!),
                        TextPart(notePromptBody)
                    )
                    canUseImage -> GenerateContentRequest.Builder(
                        ImagePart(primaryImageBitmap!!),
                        TextPart(notePromptBody)
                    )
                    useSystem -> GenerateContentRequest.Builder(
                        SystemInstruction(sysRule),
                        TextPart(notePromptBody)
                    )
                    else -> GenerateContentRequest.Builder(TextPart(notePromptBody))
                }
                val request = requestBuilder.apply {
                    temperature = 0.1f
                    topK = 8
                    maxOutputTokens = 44
                    candidateCount = 1
                    enableThinking = false
                }.build()
                model.generateContent(request).candidates.firstOrNull()?.text
            }.getOrNull()
        } ?: return@withContext emptyList()

        parseTagTokens(
            raw = rawResponse,
            allowedVocabulary = combinedAllowedVocab,
            existingNoteTags = existingLower,
            maxTags = cappedMax,
            allowNewTopicTags = allowNewTopicTags,
            usedFast = usedFast
        )
    }

    override fun close() {
        runCatching { fastClient?.close() }
        runCatching { fullClient?.close() }
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
        const val STATUS_TIMEOUT_MS = 1_500L
        const val SYSTEM_CHECK_TIMEOUT_MS = 400L
        const val FAST_GENERATE_TIMEOUT_MS = 2_500L
        const val FULL_GENERATE_TIMEOUT_MS = 4_500L
        const val PROBE_CACHE_TTL_MS = 60_000L
        const val DOWNLOADING_CACHE_TTL_MS = 5_000L
        val LEADING_BULLET_OR_NUMBER_REGEX = Regex("""^(?:[-*•]\s*|\d+[.)]\s*)""")
        val LEADING_TITLE_LABEL_REGEX = Regex(
            """^(?:title|titles|titel|suggestion|suggested tags|tags|tag|kategorien|categories)\s*:\s*""",
            RegexOption.IGNORE_CASE
        )
        val REFUSAL_PREFIXES = listOf("sure,", "sure!", "here are", "here is", "sorry", "hier sind", "natürlich")
        val REFUSAL_OR_GENERIC_TOKENS = setOf(
            "tag", "tags", "note", "notes", "notiz", "none", "n/a", "no tags", "keine", "misc", "other", "general"
        )
    }
}
