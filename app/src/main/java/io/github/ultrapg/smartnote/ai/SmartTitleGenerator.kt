package io.github.ultrapg.smartnote.ai

import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.util.TextHashing
import java.net.URI
import java.util.Locale

/**
 * Generates concise, meaningful note titles 100% offline from Markdown headings,
 * checklists, link titles/URL slugs, prose clauses, and screenshot OCR text.
 *
 * When multiple prose/OCR candidates compete and [aiEngine] is available,
 * semantic cosine similarity against the document centroid improves title selection.
 */
class SmartTitleGenerator(
    private val aiEngine: SmartNoteAiEngine? = null
) {
    private data class CandidateClause(
        val text: String,
        val baseScore: Double,
        val isFromOcr: Boolean
    )

    fun generateFastTitle(
        blocks: List<NoteBlock>,
        ocrTexts: List<String> = emptyList(),
        links: List<LinkPreviewInfo> = emptyList(),
        rawSubject: String = ""
    ): String {
        val cleanSubject = cleanTextCandidate(rawSubject)
        if (cleanSubject.isNotBlank() && !isBareUrl(cleanSubject)) {
            return formatTitle(cleanSubject)
        }

        val textBlocks = blocks.filter { it.type == NoteBlockType.TEXT && it.textContent.isNotBlank() }
        val checklistBlocks = blocks.filter { it.type == NoteBlockType.CHECKLIST && it.textContent.isNotBlank() }

        // 1. Prefer explicit Markdown heading (#, ##, ###) in prose blocks
        for (block in textBlocks) {
            for (line in block.textContent.lineSequence()) {
                val trimmed = line.trim()
                val headingMatch = MARKDOWN_HEADING_REGEX.matchEntire(trimmed)
                if (headingMatch != null) {
                    val heading = cleanMarkdownInline(headingMatch.groupValues[1])
                    if (heading.length >= 2 && !isBareUrl(heading)) {
                        return formatTitle(heading)
                    }
                }
            }
        }

        // 2. Synthesize checklist-focused titles when checklists are the primary content
        val proseChars = textBlocks.sumOf { it.textContent.trim().length }
        if (checklistBlocks.isNotEmpty() && proseChars < 24) {
            val synthesized = synthesizeChecklistTitle(checklistBlocks.map { it.textContent.trim() })
            if (synthesized.isNotBlank()) return formatTitle(synthesized)
        }

        // 3. Extract scored candidates from prose + OCR text
        val candidates = collectScoredCandidates(textBlocks, ocrTexts)
        if (candidates.isNotEmpty()) {
            val best = candidates.maxByOrNull { it.baseScore }
            if (best != null && best.baseScore > 0.0) {
                return formatTitle(best.text)
            }
        }

        // 4. Fallback to fetched LinkPreview pageTitle or humanized URL slug + domain
        val urlTitle = extractBestLinkTitle(links, blocks)
        if (urlTitle.isNotBlank()) return formatTitle(urlTitle)

        // 5. Checklist fallback if mixed with sparse links
        if (checklistBlocks.isNotEmpty()) {
            val synthesized = synthesizeChecklistTitle(checklistBlocks.map { it.textContent.trim() })
            if (synthesized.isNotBlank()) return formatTitle(synthesized)
        }

        return ""
    }

    suspend fun generateSmartTitle(
        blocks: List<NoteBlock>,
        ocrTexts: List<String> = emptyList(),
        links: List<LinkPreviewInfo> = emptyList(),
        rawSubject: String = ""
    ): String {
        val cleanSubject = cleanTextCandidate(rawSubject)
        if (cleanSubject.isNotBlank() && !isBareUrl(cleanSubject)) {
            return formatTitle(cleanSubject)
        }

        val textBlocks = blocks.filter { it.type == NoteBlockType.TEXT && it.textContent.isNotBlank() }
        for (block in textBlocks) {
            for (line in block.textContent.lineSequence()) {
                val trimmed = line.trim()
                val headingMatch = MARKDOWN_HEADING_REGEX.matchEntire(trimmed)
                if (headingMatch != null) {
                    val heading = cleanMarkdownInline(headingMatch.groupValues[1])
                    if (heading.length >= 2 && !isBareUrl(heading)) {
                        return formatTitle(heading)
                    }
                }
            }
        }

        val checklistBlocks = blocks.filter { it.type == NoteBlockType.CHECKLIST && it.textContent.isNotBlank() }
        val proseChars = textBlocks.sumOf { it.textContent.trim().length }
        if (checklistBlocks.isNotEmpty() && proseChars < 24) {
            val synthesized = synthesizeChecklistTitle(checklistBlocks.map { it.textContent.trim() })
            if (synthesized.isNotBlank()) return formatTitle(synthesized)
        }

        val candidates = collectScoredCandidates(textBlocks, ocrTexts)
        if (candidates.isNotEmpty()) {
            val engine = aiEngine
            if (candidates.size >= 2 && engine != null) {
                val combinedDoc = buildString {
                    textBlocks.forEach { append(it.textContent).append('\n') }
                    ocrTexts.forEach { append(it).append('\n') }
                }.trim()
                val docVec = runCatching {
                    if (combinedDoc.isNotBlank()) engine.embedText(combinedDoc.take(2500)) else null
                }.getOrNull()

                if (docVec != null) {
                    var bestCandidate = candidates.first()
                    var bestCombinedScore = Double.NEGATIVE_INFINITY
                    for (cand in candidates.take(8)) {
                        val candVec = runCatching { engine.embedText(cand.text) }.getOrNull()
                        val sim = if (candVec != null) {
                            TextFeatureVectorizer.cosineSimilarity(docVec, candVec).coerceAtLeast(0f).toDouble()
                        } else {
                            0.0
                        }
                        val total = cand.baseScore + (3.2 * sim)
                        if (total > bestCombinedScore) {
                            bestCombinedScore = total
                            bestCandidate = cand
                        }
                    }
                    return formatTitle(bestCandidate.text)
                }
            }
            return formatTitle(candidates.maxBy { it.baseScore }.text)
        }

        val urlTitle = extractBestLinkTitle(links, blocks)
        if (urlTitle.isNotBlank()) return formatTitle(urlTitle)

        if (checklistBlocks.isNotEmpty()) {
            val synthesized = synthesizeChecklistTitle(checklistBlocks.map { it.textContent.trim() })
            if (synthesized.isNotBlank()) return formatTitle(synthesized)
        }

        return ""
    }

    private fun synthesizeChecklistTitle(rawItems: List<String>): String {
        val items = rawItems
            .map { cleanMarkdownInline(it) }
            .filter { it.length >= 2 }
        if (items.isEmpty()) return ""

        val allLower = items.joinToString(" ").lowercase(Locale.ROOT)
        val hasShoppingCue = SHOPPING_HINTS.any { cue -> cue in allLower }
        val hasPackingCue = PACKING_HINTS.any { cue -> cue in allLower }

        return when {
            items.size == 1 -> items.first()
            hasShoppingCue -> {
                val preview = items.take(2).joinToString(", ")
                if (items.size > 2) "Shopping: $preview (+${items.size - 2})" else "Shopping: $preview"
            }
            hasPackingCue -> {
                val preview = items.take(2).joinToString(", ")
                "Packing: $preview"
            }
            items.size == 2 -> "${shortenItem(items[0], 22)} & ${shortenItem(items[1], 22)}"
            else -> "Tasks: ${shortenItem(items[0], 20)}, ${shortenItem(items[1], 20)}"
        }
    }

    private fun shortenItem(item: String, maxLen: Int): String {
        if (item.length <= maxLen) return item
        val cut = item.take(maxLen).substringBeforeLast(' ')
        return cut.ifBlank { item.take(maxLen) }
    }

    private fun extractBestLinkTitle(links: List<LinkPreviewInfo>, blocks: List<NoteBlock>): String {
        for (link in links) {
            val fetchedTitle = link.pageTitle?.trim()
            if (!fetchedTitle.isNullOrBlank()) {
                val trimmedSuffix = fetchedTitle
                    .split(" | ", " — ", " - ")
                    .firstOrNull { it.trim().length >= 4 }
                    ?.trim()
                    ?: fetchedTitle
                return trimmedSuffix
            }
        }

        val candidateUrls = buildList {
            links.mapTo(this) { it.rawUrl }
            blocks.filter { it.type == NoteBlockType.LINK }.mapNotNullTo(this) { it.linkUrl }
            blocks.filter { it.type == NoteBlockType.TEXT || it.type == NoteBlockType.CHECKLIST }.forEach { b ->
                URL_EXTRACT_REGEX.findAll(b.textContent).forEach { add(it.value) }
            }
        }.distinct()

        for (raw in candidateUrls) {
            val uri = runCatching { URI(raw) }.getOrNull() ?: continue
            val host = uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: continue
            val pathSegments = uri.path
                ?.split('/')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it.length > 2 && !it.all(Char::isDigit) }
                .orEmpty()
            val meaningfulSegments = pathSegments
                .takeLast(2)
                .map { seg ->
                    seg.replace(Regex("\\.[a-zA-Z0-9]{2,5}$"), "")
                        .replace('-', ' ')
                        .replace('_', ' ')
                        .trim()
                }
                .filter { it.length >= 3 && it !in GENERIC_URL_SEGMENTS }
            if (meaningfulSegments.isNotEmpty()) {
                val slugTitle = meaningfulSegments.joinToString(" ")
                    .split(Regex("\\s+"))
                    .joinToString(" ") { w ->
                        w.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
                    }
                return "$slugTitle — $host"
            }
            return "Link from $host"
        }

        return ""
    }

    private fun collectScoredCandidates(
        textBlocks: List<NoteBlock>,
        ocrTexts: List<String>
    ): List<CandidateClause> {
        val rawLines = mutableListOf<Pair<String, Boolean>>()
        textBlocks.forEach { blk ->
            blk.textContent.lineSequence().forEach { line ->
                val trimmed = cleanMarkdownInline(line)
                if (trimmed.isNotBlank()) rawLines += trimmed to false
            }
        }
        ocrTexts.forEach { ocr ->
            ocr.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) rawLines += trimmed to true
            }
        }
        if (rawLines.isEmpty()) return emptyList()

        // Term frequency across all content for extractive salience
        val allText = rawLines.joinToString(" ") { it.first }
        val termFreq = HashMap<String, Int>()
        for (tok in TextHashing.tokenizeWords(allText)) {
            if (tok.length >= 3 && tok !in STOP_WORDS) {
                termFreq[tok] = (termFreq[tok] ?: 0) + 1
            }
        }

        val candidates = mutableListOf<CandidateClause>()
        rawLines.forEachIndexed { lineIndex, (lineText, isOcr) ->
            if (isNoiseLine(lineText, isOcr)) return@forEachIndexed

            val clauses = if (lineText.length > 64) {
                SENTENCE_SPLIT_REGEX.split(lineText)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            } else {
                listOf(lineText)
            }

            clauses.forEachIndexed { clauseIdx, rawClause ->
                val clause = cleanTextCandidate(rawClause)
                if (clause.length < 3 || isNoiseLine(clause, isOcr)) return@forEachIndexed

                val words = clause.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty()) return@forEachIndexed
                if (words.size == 1 && words.first().length < 4) return@forEachIndexed

                var score = if (isOcr) 0.8 else 2.2
                // Lead-line preference
                val posPenalty = (lineIndex * 0.28 + clauseIdx * 0.4).coerceAtMost(2.5)
                score -= posPenalty

                // Concise length fit
                score += when (words.size) {
                    in 3..7 -> 2.4
                    2, 8 -> 1.6
                    1 -> 0.4
                    else -> 0.9
                }

                // Keyword salience boost
                val clauseTokens = TextHashing.tokenizeWords(clause).filter { it !in STOP_WORDS }.distinct()
                val salience = clauseTokens.sumOf { (termFreq[it] ?: 1).coerceAtMost(4) * 0.45 }
                score += salience

                // Semantic domain / exception / receipt / meeting cues
                val lowerClause = clause.lowercase(Locale.ROOT)
                if (DOMAIN_CUE_WORDS.any { it in lowerClause }) {
                    score += 1.5
                }
                if (!isOcr && words.firstOrNull()?.firstOrNull()?.isUpperCase() == true) {
                    score += 0.35
                }

                candidates += CandidateClause(
                    text = clause,
                    baseScore = score,
                    isFromOcr = isOcr
                )
            }
        }
        return candidates
    }

    private fun isNoiseLine(raw: String, isOcr: Boolean): Boolean {
        val text = raw.trim()
        if (text.length < 3) return true
        if (isBareUrl(text)) return true
        if (TIME_OR_STATUS_REGEX.matches(text)) return true
        if (PERCENT_OR_NUMBER_REGEX.matches(text)) return true
        val letterCount = text.count { it.isLetter() }
        if (letterCount < 2) return true
        if (isOcr && text.length <= 4 && text.all { it.isDigit() || !it.isLetterOrDigit() }) return true
        if (isOcr && letterCount.toFloat() / text.length.toFloat() < 0.35f) return true
        return false
    }

    private fun cleanMarkdownInline(line: String): String {
        return line.trim()
            .replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^>\\s*"), "")
            .replace(Regex("^[-*+]\\s+"), "")
            .replace(Regex("^\\[[ xX]]\\s*"), "")
            .replace(Regex("^\\d+\\.\\s+"), "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("^\\*(?!\\s)|(?<!\\s)\\*$"), "")
            .trim()
    }

    private fun cleanTextCandidate(raw: String): String {
        return cleanMarkdownInline(raw)
            .replace(URL_EXTRACT_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isBareUrl(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true) ||
            t.startsWith("www.", ignoreCase = true)
    }

    companion object {
        private const val MAX_TITLE_LEN = 56
        private val MARKDOWN_HEADING_REGEX = Regex("^#{1,3}\\s+(.+)$")
        private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])\\s+|\\s+[—–|]\\s+")
        private val URL_EXTRACT_REGEX = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE)
        private val TIME_OR_STATUS_REGEX = Regex("^\\d{1,2}:\\d{2}(\\s?(AM|PM))?$", RegexOption.IGNORE_CASE)
        private val PERCENT_OR_NUMBER_REGEX = Regex("^[\\d.,%$€£:+\\-/\\s]+$")

        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "from", "have", "your", "are",
            "was", "were", "will", "not", "but", "can", "into", "about", "just", "than",
            "then", "them", "they", "what", "when", "where", "which", "would", "could",
            "should", "there", "their", "been", "being", "also", "more", "some", "very"
        )

        private val SHOPPING_HINTS = setOf(
            "milk", "bread", "eggs", "coffee", "butter", "cheese", "apples", "bananas",
            "rice", "pasta", "sugar", "flour", "chicken", "soap", "shampoo", "grocery"
        )

        private val PACKING_HINTS = setOf(
            "passport", "charger", "adapter", "tickets", "socks", "jacket", "toothbrush", "luggage"
        )

        private val GENERIC_URL_SEGMENTS = setOf(
            "index", "home", "page", "article", "post", "posts", "blog", "docs", "en", "us"
        )

        private val DOMAIN_CUE_WORDS = setOf(
            "invoice", "receipt", "order", "flight", "itinerary", "meeting", "agenda",
            "summary", "roadmap", "architecture", "exception", "error", "stacktrace",
            "recipe", "workout", "budget", "password", "booking", "ticket", "reminder"
        )

        fun formatTitle(raw: String): String {
            val collapsed = raw
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.', ',', ':', ';', '-', '–', '—')
                .trim()
            if (collapsed.isBlank()) return ""

            val truncated = if (collapsed.length <= MAX_TITLE_LEN) {
                collapsed
            } else {
                val slice = collapsed.take(MAX_TITLE_LEN)
                val lastSpace = slice.lastIndexOf(' ')
                if (lastSpace >= MAX_TITLE_LEN / 2) slice.substring(0, lastSpace) else slice
            }.trimEnd('.', ',', ':', ';', '-', '–', '—').trim()

            return truncated.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }
        }

        fun isPlaceholderOrBlankTitle(title: String): Boolean {
            val t = title.trim()
            if (t.isBlank()) return true
            if (t.equals("Untitled", ignoreCase = true) || t.equals("Untitled note", ignoreCase = true)) return true
            if (t.equals("Shared image", ignoreCase = true)) return true
            if (Regex("^\\d+ shared images$", RegexOption.IGNORE_CASE).matches(t)) return true
            if (t.startsWith("Link from ", ignoreCase = true)) return true
            return false
        }
    }
}
