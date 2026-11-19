package io.github.ultrapg.smartnote.ai

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
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

interface OnDeviceGenAiAssistant {
    suspend fun checkAvailability(): GeminiNanoAvailability
    fun triggerBackgroundDownloadIfNeeded()
    suspend fun generateTitleCandidates(
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>,
        primaryImageBitmap: Bitmap? = null
    ): List<String>
    suspend fun generateTags(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>
    ): List<SmartTagSuggestion>
    fun close()
}

class MlKitGeminiNanoAssistant(
    private val applicationScope: CoroutineScope
) : OnDeviceGenAiAssistant {

    private val downloadInFlight = AtomicBoolean(false)

    private val client: GenerativeModel? by lazy {
        runCatching { Generation.getClient() }.getOrNull()
    }

    override suspend fun checkAvailability(): GeminiNanoAvailability = withContext(Dispatchers.IO) {
        val model = client ?: return@withContext GeminiNanoAvailability.UNAVAILABLE
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
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

    override fun triggerBackgroundDownloadIfNeeded() {
        val model = client ?: return
        if (!downloadInFlight.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (model.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                    model.download().collect { status ->
                        if (status is DownloadStatus.DownloadCompleted || status is DownloadStatus.DownloadFailed) {
                            downloadInFlight.set(false)
                        }
                    }
                }
            } catch (_: Throwable) {
                downloadInFlight.set(false)
            } finally {
                if (runCatching { model.checkStatus() }.getOrNull() != FeatureStatus.DOWNLOADING) {
                    downloadInFlight.set(false)
                }
            }
        }
    }

    override suspend fun generateTitleCandidates(
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        linkHints: List<String>,
        primaryImageBitmap: Bitmap?
    ): List<String> = withContext(Dispatchers.IO) {
        val model = client ?: return@withContext emptyList()
        val ready = runCatching {
            withTimeoutOrNull(STATUS_TIMEOUT_MS) { model.checkStatus() == FeatureStatus.AVAILABLE }
        }.getOrDefault(false) ?: false
        if (!ready) return@withContext emptyList()

        val contextBlock = buildNoteContext(
            title = "",
            noteText = noteText,
            ocrTexts = ocrTexts,
            imageLabels = imageLabels,
            linkHints = linkHints
        )
        val hasSparseText = noteText.trim().length < 20 && ocrTexts.all { it.isBlank() }

        val rawResponse = withTimeoutOrNull(GENERATE_TIMEOUT_MS) {
            runCatching {
                val request = if (primaryImageBitmap != null && !primaryImageBitmap.isRecycled && hasSparseText) {
                    val prompt = buildString {
                        append("Write 2 concise note titles (each 2 to 6 words, same language as text if present, no quotes, no numbering, one per line) describing this attached image and note:\n")
                        if (contextBlock.isNotBlank()) append(contextBlock)
                    }
                    GenerateContentRequest.Builder(ImagePart(primaryImageBitmap), TextPart(prompt)).apply {
                        temperature = 0.2f
                        topK = 16
                        maxOutputTokens = 48
                        candidateCount = 1
                    }.build()
                } else {
                    if (contextBlock.isBlank()) return@runCatching null
                    val prompt =
                        "Write 2 concise note titles (each 2 to 6 words, same language as the note, no quotes, no numbering, one per line) for this note:\n$contextBlock"
                    GenerateContentRequest.Builder(TextPart(prompt)).apply {
                        temperature = 0.2f
                        topK = 16
                        maxOutputTokens = 48
                        candidateCount = 1
                    }.build()
                }
                model.generateContent(request).candidates.firstOrNull()?.text
            }.getOrNull()
        } ?: return@withContext emptyList()

        parseTitleLines(rawResponse)
    }

    override suspend fun generateTags(
        title: String,
        noteText: String,
        ocrTexts: List<String>,
        imageLabels: List<String>,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>
    ): List<SmartTagSuggestion> = withContext(Dispatchers.IO) {
        val model = client ?: return@withContext emptyList()
        val ready = runCatching {
            withTimeoutOrNull(STATUS_TIMEOUT_MS) { model.checkStatus() == FeatureStatus.AVAILABLE }
        }.getOrDefault(false) ?: false
        if (!ready) return@withContext emptyList()

        val contextBlock = buildNoteContext(
            title = title,
            noteText = noteText,
            ocrTexts = ocrTexts,
            imageLabels = imageLabels,
            linkHints = emptyList()
        )
        if (contextBlock.isBlank()) return@withContext emptyList()

        val vocabHint = existingVocabulary.take(24).joinToString(", ")
        val prompt = buildString {
            append("Suggest 1 to 3 concise tags (comma-separated, 1 to 2 words each, no #")
            if (vocabHint.isNotBlank()) {
                append(", prefer matching these existing tags when relevant: ").append(vocabHint)
            }
            append(") for this note:\n").append(contextBlock)
        }

        val rawResponse = withTimeoutOrNull(GENERATE_TIMEOUT_MS) {
            runCatching {
                val request = GenerateContentRequest.Builder(TextPart(prompt)).apply {
                    temperature = 0.2f
                    topK = 16
                    maxOutputTokens = 32
                    candidateCount = 1
                }.build()
                model.generateContent(request).candidates.firstOrNull()?.text
            }.getOrNull()
        } ?: return@withContext emptyList()

        parseTagTokens(rawResponse, existingVocabulary, existingNoteTags)
    }

    override fun close() {
        runCatching { client?.close() }
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

    private fun parseTitleLines(raw: String): List<String> {
        val seen = HashSet<String>()
        return raw.lineSequence()
            .map { line ->
                line.trim()
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .replace(LEADING_TITLE_LABEL_REGEX, "")
                    .trim('"', '\'', '“', '”', '*', '`', ' ')
            }
            .filter { candidate ->
                candidate.length in 3..64 &&
                    !looksLikeRefusalOrChatter(candidate)
            }
            .mapNotNull { SmartTitleGenerator.formatTitle(it).takeIf(String::isNotBlank) }
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
            .take(2)
            .toList()
    }

    private fun parseTagTokens(
        raw: String,
        existingVocabulary: List<String>,
        existingNoteTags: Set<String>
    ): List<SmartTagSuggestion> {
        val vocabByLower = existingVocabulary.associateBy { it.lowercase(Locale.ROOT) }
        val existingLower = existingNoteTags.map { it.lowercase(Locale.ROOT) }.toSet()
        val seen = HashSet<String>()

        return raw.split(',', '\n', ';')
            .map { tok ->
                tok.trim()
                    .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
                    .removePrefix("#")
                    .trim('"', '\'', '.', ':', '!', '*', '`', ' ')
            }
            .filter { tok ->
                tok.length in 2..24 &&
                    tok.split(Regex("\\s+")).size <= 2 &&
                    !looksLikeRefusalOrChatter(tok) &&
                    tok.lowercase(Locale.ROOT) !in REFUSAL_OR_GENERIC_TOKENS &&
                    tok.any { it.isLetter() }
            }
            .map { cleanTok ->
                val lower = cleanTok.lowercase(Locale.ROOT)
                vocabByLower[lower] ?: cleanTok.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                }
            }
            .filter { finalTag ->
                val lower = finalTag.lowercase(Locale.ROOT)
                lower !in existingLower && seen.add(lower)
            }
            .take(3)
            .map { finalTag ->
                SmartTagSuggestion(
                    name = finalTag,
                    confidence = 0.91f,
                    reason = "Gemini Nano"
                )
            }
    }

    private fun looksLikeRefusalOrChatter(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return REFUSAL_PREFIXES.any { lower.startsWith(it) || "i cannot" in lower || "i'm sorry" in lower || "as an ai" in lower }
    }

    private companion object {
        const val STATUS_TIMEOUT_MS = 1_500L
        const val GENERATE_TIMEOUT_MS = 4_000L
        val LEADING_BULLET_OR_NUMBER_REGEX = Regex("""^(?:[-*•]\s*|\d+[.)]\s*)""")
        val LEADING_TITLE_LABEL_REGEX = Regex("""^(?:title|titles|titel|suggestion|tags)\s*:\s*""", RegexOption.IGNORE_CASE)
        val REFUSAL_PREFIXES = listOf("sure,", "sure!", "here are", "here is", "sorry", "hier sind", "natürlich")
        val REFUSAL_OR_GENERIC_TOKENS = setOf("tag", "tags", "note", "notes", "notiz", "none", "n/a")
    }
}
