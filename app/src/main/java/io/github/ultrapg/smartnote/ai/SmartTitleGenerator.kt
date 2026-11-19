package io.github.ultrapg.smartnote.ai

import android.graphics.Bitmap
import android.view.textclassifier.TextClassifier
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.util.TextHashing
import java.net.URI
import java.util.Locale

/**
 * Generates concise, meaningful note titles on-device from Markdown headings,
 * optional Gemini Nano synthesis, checklists, link titles/URL slugs, system entities,
 * prose clauses, screenshot OCR text, and image labels.
 */
class SmartTitleGenerator(
    private val aiEngine: SmartNoteAiEngine? = null,
    private val genAiAssistant: OnDeviceGenAiAssistant? = null
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
        rawSubject: String = "",
        allowNeuralScoring: Boolean = true,
        systemEntities: List<ClassifiedTextEntity> = emptyList(),
        imageLabels: List<String> = emptyList(),
        primaryImageBitmap: Bitmap? = null,
        allowGeminiNano: Boolean = true,
        preferFastGemini: Boolean = true,
        maxTitleLength: Int = MAX_TITLE_LEN
    ): String = generateTitleCandidates(
        blocks = blocks,
        ocrTexts = ocrTexts,
        links = links,
        rawSubject = rawSubject,
        allowNeuralScoring = allowNeuralScoring,
        systemEntities = systemEntities,
        imageLabels = imageLabels,
        primaryImageBitmap = primaryImageBitmap,
        allowGeminiNano = allowGeminiNano,
        preferFastGemini = preferFastGemini,
        maxTitleLength = maxTitleLength
    ).firstOrNull().orEmpty()

    suspend fun generateTitleCandidates(
        blocks: List<NoteBlock>,
        ocrTexts: List<String> = emptyList(),
        links: List<LinkPreviewInfo> = emptyList(),
        rawSubject: String = "",
        allowNeuralScoring: Boolean = true,
        systemEntities: List<ClassifiedTextEntity> = emptyList(),
        imageLabels: List<String> = emptyList(),
        primaryImageBitmap: Bitmap? = null,
        allowGeminiNano: Boolean = true,
        preferFastGemini: Boolean = true,
        maxTitleLength: Int = MAX_TITLE_LEN
    ): List<String> {
        val cleanSubject = cleanTextCandidate(rawSubject)
        if (cleanSubject.isNotBlank() && !isBareUrl(cleanSubject)) {
            val formatted = formatTitle(distillClauseToTitle(cleanSubject).ifBlank { cleanSubject }, maxLen = maxTitleLength)
            return if (formatted.isNotBlank()) listOf(formatted) else emptyList()
        }

        val results = ArrayList<String>(4)
        val seenLower = HashSet<String>()
        fun addUnique(rawCandidate: String) {
            val formatted = stripTrailingFunctionWords(formatTitle(rawCandidate, maxLen = maxTitleLength))
            if (formatted.isBlank()) return
            if (seenLower.add(formatted.lowercase(Locale.ROOT))) {
                results += formatted
            }
        }

        val textBlocks = blocks.filter { it.type == NoteBlockType.TEXT && it.textContent.isNotBlank() }
        val explicitHeadings = buildList {
            for (block in textBlocks) {
                for (line in block.textContent.lineSequence()) {
                    val trimmed = line.trim()
                    val headingMatch = MARKDOWN_HEADING_REGEX.matchEntire(trimmed)
                    if (headingMatch != null) {
                        val heading = cleanMarkdownInline(headingMatch.groupValues[1])
                        if (heading.length >= 2 && !isBareUrl(heading)) {
                            add(heading)
                        }
                    }
                }
            }
        }
        // Keep explicit author Markdown # heading first
        explicitHeadings.firstOrNull()?.let { addUnique(it) }

        val checklistBlocks = blocks.filter { it.type == NoteBlockType.CHECKLIST && it.textContent.isNotBlank() }
        val combinedBodyText = buildString {
            textBlocks.forEach { append(it.textContent).append('\n') }
            checklistBlocks.forEach { append(it.textContent).append('\n') }
        }.trim()

        val assistant = genAiAssistant
        if (allowNeuralScoring && allowGeminiNano && assistant != null) {
            val linkHints = buildList {
                links.forEach { l -> add(l.pageTitle?.takeIf(String::isNotBlank) ?: l.domain) }
                blocks.filter { it.type == NoteBlockType.LINK }.mapNotNullTo(this) { it.linkUrl }
            }
            val nanoTitles = runCatching {
                assistant.generateTitleCandidates(
                    noteText = combinedBodyText,
                    ocrTexts = ocrTexts,
                    imageLabels = imageLabels,
                    linkHints = linkHints,
                    primaryImageBitmap = primaryImageBitmap,
                    preferFast = preferFastGemini,
                    maxTitleLength = maxTitleLength
                )
            }.getOrDefault(emptyList())
            nanoTitles.forEach { addUnique(it) }
        }

        explicitHeadings.drop(1).forEach { addUnique(it) }

        val flightEntity = systemEntities.firstOrNull { it.type == TextClassifier.TYPE_FLIGHT_NUMBER }
        val proseChars = textBlocks.sumOf { it.textContent.trim().length }
        if (flightEntity != null && explicitHeadings.isEmpty() && proseChars <= flightEntity.matchedText.length + 4) {
            addUnique("Flight ${flightEntity.matchedText}")
        }
        if (checklistBlocks.isNotEmpty() && proseChars < 24) {
            val synthesized = synthesizeChecklistTitle(checklistBlocks.map { it.textContent.trim() })
            if (synthesized.isNotBlank()) addUnique(synthesized)
        }

        val candidates = collectScoredCandidates(textBlocks, ocrTexts, systemEntities)
        if (candidates.isNotEmpty()) {
            val engine = aiEngine
            val rankedCandidates = if (allowNeuralScoring && candidates.size >= 2 && engine != null) {
                val combinedDoc = buildString {
                    textBlocks.forEach { append(it.textContent).append('\n') }
                    ocrTexts.forEach { append(it).append('\n') }
                }.trim()
                val docVec = runCatching {
                    if (combinedDoc.isNotBlank()) engine.embedText(combinedDoc.take(2500)) else null
                }.getOrNull()

                if (docVec != null) {
                    candidates.take(8).map { cand ->
                        val candVec = runCatching { engine.embedText(cand.text) }.getOrNull()
                        val sim = if (candVec != null) {
                            TextFeatureVectorizer.cosineSimilarity(docVec, candVec).coerceAtLeast(0f).toDouble()
                        } else {
                            0.0
                        }
                        cand to (cand.baseScore + (3.2 * sim))
                    }.sortedByDescending { it.second }.map { it.first }
                } else {
                    candidates.sortedByDescending { it.baseScore }
                }
            } else {
                candidates.sortedByDescending { it.baseScore }
            }

            rankedCandidates.take(4).forEach { cand -> addUnique(cand.text) }
        }

        if (flightEntity != null && results.none { it.contains(flightEntity.matchedText, ignoreCase = true) }) {
            addUnique("Flight ${flightEntity.matchedText}")
        }

        val urlTitle = extractBestLinkTitle(links, blocks)
        if (urlTitle.isNotBlank()) addUnique(urlTitle)

        if (checklistBlocks.isNotEmpty()) {
            val synthesized = synthesizeChecklistTitle(checklistBlocks.map { it.textContent.trim() })
            if (synthesized.isNotBlank()) addUnique(synthesized)
        }

        val visualTitle = synthesizeImageTitle(imageLabels)
        if (visualTitle.isNotBlank()) {
            addUnique(visualTitle)
        }

        val keyphraseTitle = synthesizeKeyphraseTitle(textBlocks, checklistBlocks, ocrTexts)
        if (keyphraseTitle.isNotBlank()) {
            addUnique(keyphraseTitle)
        }

        return results.take(4)
    }

    private fun synthesizeChecklistTitle(rawItems: List<String>): String {
        val items = rawItems
            .map { cleanMarkdownInline(it) }
            .filter { it.length >= 2 }
        if (items.isEmpty()) return ""

        val allLower = items.joinToString(" ").lowercase(Locale.ROOT)
        val hasShoppingCue = SHOPPING_HINTS.any { cue -> cue in allLower }
        val hasPackingCue = PACKING_HINTS.any { cue -> cue in allLower }
        val hasWorkoutCue = WORKOUT_HINTS.any { cue -> cue in allLower }
        val hasWatchCue = WATCH_HINTS.any { cue -> cue in allLower }

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
            hasWorkoutCue -> {
                val preview = items.take(2).joinToString(", ")
                "Workout: $preview"
            }
            hasWatchCue -> {
                val preview = items.take(2).joinToString(", ")
                "Watchlist: $preview"
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
        ocrTexts: List<String>,
        systemEntities: List<ClassifiedTextEntity> = emptyList()
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

        val flightCodes = systemEntities
            .filter { it.type == TextClassifier.TYPE_FLIGHT_NUMBER }
            .map { it.matchedText }

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
                val cleanedRaw = cleanTextCandidate(rawClause)
                if (cleanedRaw.length < 3 || isNoiseLine(cleanedRaw, isOcr)) return@forEachIndexed

                val labelMatch = TOPIC_LABEL_PREFIX_REGEX.matchAt(cleanedRaw, 0)
                val withoutLabel = if (labelMatch != null) {
                    cleanedRaw.removeRange(labelMatch.range).trim()
                } else {
                    cleanedRaw
                }
                val distilled = distillClauseToTitle(withoutLabel)
                val clause = distilled.ifBlank { cleanTextCandidate(withoutLabel) }
                if (clause.length < 3 || isNoiseLine(clause, isOcr)) return@forEachIndexed

                val words = clause.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty()) return@forEachIndexed
                if (words.size == 1 && words.first().length < 4) return@forEachIndexed

                var score = if (isOcr) 0.8 else 2.2
                // Lead-line preference
                val posPenalty = (lineIndex * 0.28 + clauseIdx * 0.4).coerceAtMost(2.5)
                score -= posPenalty

                // Explicit labeled topic lines ("Subject:", "Betreff:") or action-first key-value prefixes
                if (labelMatch != null) {
                    score += 2.0
                }
                if (isMetaOrGreetingLine(cleanedRaw) || isMetaOrGreetingLine(clause)) {
                    score -= 3.8
                }

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
                if (flightCodes.any { clause.contains(it, ignoreCase = true) }) {
                    score += 1.6
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

    private fun distillClauseToTitle(raw: String): String {
        var working = raw.trim()
        // Iteratively strip conversational filler openings in EN & DE
        repeat(2) {
            val lower = working.lowercase(Locale.ROOT)
            val prefix = FILLER_OPENINGS.firstOrNull { lower.startsWith(it) } ?: return@repeat
            val remainder = working.substring(prefix.length).trimStart(',', ':', '-', '–', '—', ' ')
            if (remainder.count { it.isLetter() } >= 3) {
                working = remainder
            }
        }

        // Compress overly long clauses on clean subordinate or conjunction boundaries
        if (working.length > 50) {
            val subSplit = SUBORDINATE_SPLIT_REGEX.split(working, limit = 2)
            val firstPart = subSplit.firstOrNull()?.trim().orEmpty()
            val firstWords = firstPart.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (subSplit.size >= 2 && firstPart.length >= 14 && firstWords.size >= 2) {
                working = firstPart
            }
        }
        if (working.length > 54) {
            val coordSplit = COORDINATING_SPLIT_REGEX.split(working, limit = 2)
            val firstPart = coordSplit.firstOrNull()?.trim().orEmpty()
            val firstWords = firstPart.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (coordSplit.size >= 2 && firstPart.length >= 18 && firstWords.size >= 3) {
                working = firstPart
            }
        }

        working = formatTitle(working)
        return stripTrailingFunctionWords(working)
    }

    private fun synthesizeKeyphraseTitle(
        textBlocks: List<NoteBlock>,
        checklistBlocks: List<NoteBlock>,
        ocrTexts: List<String>
    ): String {
        val sampleLines = buildList {
            textBlocks.forEach { b -> b.textContent.lineSequence().forEach { add(cleanTextCandidate(it)) } }
            checklistBlocks.forEach { add(cleanTextCandidate(it.textContent)) }
            ocrTexts.forEach { o -> o.lineSequence().forEach { add(cleanTextCandidate(it)) } }
        }.filter { it.isNotBlank() && !isBareUrl(it) }
        if (sampleLines.isEmpty()) return ""

        val termScores = LinkedHashMap<String, Double>()
        val originalForms = HashMap<String, String>()
        val rawWordRegex = Regex("""[\p{L}][\p{L}\p{N}-]{2,24}""")

        sampleLines.take(12).forEachIndexed { lineIdx, line ->
            val weight = (2.0 - (lineIdx * 0.14)).coerceAtLeast(0.7)
            rawWordRegex.findAll(line).forEach { m ->
                val rawTok = m.value
                val lower = rawTok.lowercase(Locale.ROOT)
                if (lower.length < 3 || lower in STOP_WORDS || lower in TRAILING_FUNCTION_WORDS || lower in META_GENERIC_LINES) {
                    return@forEach
                }
                termScores[lower] = (termScores[lower] ?: 0.0) + weight + if (rawTok.first().isUpperCase()) 0.35 else 0.0
                originalForms.putIfAbsent(
                    lower,
                    if (rawTok.any { it.isUpperCase() }) rawTok else rawTok.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
                )
            }
        }

        val topTerms = termScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .mapNotNull { originalForms[it.key] }
        return if (topTerms.size >= 2) topTerms.joinToString(" ") else ""
    }

    private fun synthesizeImageTitle(rawLabels: List<String>): String {
        val meaningful = rawLabels
            .map { it.trim() }
            .filter { lbl -> lbl.length >= 3 && lbl.lowercase(Locale.ROOT) !in GENERIC_VISUAL_LABELS }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(2)
        return when {
            meaningful.isEmpty() -> ""
            meaningful.size == 1 -> "${meaningful.first().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }} photo"
            else -> "${meaningful[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }} & ${meaningful[1]}"
        }
    }

    private fun isMetaOrGreetingLine(raw: String): Boolean {
        val clean = raw.trim().trimEnd('.', '!', ':', ',')
        val lower = clean.lowercase(Locale.ROOT)
        if (lower in META_GENERIC_LINES) return true
        if (DATE_ONLY_REGEX.matches(clean)) return true
        if (GREETING_PREFIX_REGEX.containsMatchIn(lower) && clean.split(Regex("\\s+")).size <= 3) return true
        return false
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
        val cleaned = cleanMarkdownInline(raw)
            .replace(URL_EXTRACT_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val withoutHashtags = cleaned
            .replace(INLINE_HASHTAG_STRIP_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (withoutHashtags.count { it.isLetter() } >= 3) {
            withoutHashtags
        } else {
            cleaned.replace("#", "").trim()
        }
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
        private val INLINE_HASHTAG_STRIP_REGEX = Regex("(?:^|\\s)#[\\p{L}][\\p{L}\\p{N}_-]{1,30}")
        private val TIME_OR_STATUS_REGEX = Regex("^\\d{1,2}:\\d{2}(\\s?(AM|PM))?$", RegexOption.IGNORE_CASE)
        private val PERCENT_OR_NUMBER_REGEX = Regex("^[\\d.,%$€£:+\\-/\\s]+$")
        private val TOPIC_LABEL_PREFIX_REGEX = Regex("^(subject|topic|title|betreff|thema)\\s*:\\s*", RegexOption.IGNORE_CASE)
        private val SUBORDINATE_SPLIT_REGEX = Regex(
            "(?:,\\s+|;\\s+|\\s+[—–-]\\s+|\\s+(?:because|so that|in order to|which|while|whereas|weil|damit|bevor|nachdem|obwohl|sodass)\\s+)",
            RegexOption.IGNORE_CASE
        )
        private val COORDINATING_SPLIT_REGEX = Regex("\\s+(?:and|und)\\s+", RegexOption.IGNORE_CASE)
        private val DATE_ONLY_REGEX = Regex("^\\d{1,4}[./-]\\d{1,2}(?:[./-]\\d{2,4})?$")
        private val GREETING_PREFIX_REGEX = Regex("^(hey|hi|hello|hallo|servus|moin|dear|liebe|lieber)\\b")

        private val FILLER_OPENINGS = listOf(
            "bitte nicht vergessen ", "do not forget to ", "don't forget to ",
            "note to self:", "note to self ", "wir müssen noch ", "wir muessen noch ",
            "nicht vergessen ", "daran denken ", "dran denken ", "ich möchte noch ",
            "ich moechte noch ", "ich sollte noch ", "ich muss noch ", "kurze notiz:",
            "remember to ", "make sure to ", "be sure to ", "erinnerung:", "quick note:",
            "we need to ", "i need to ", "i have to ", "i should ", "i want to ",
            "ich muss ", "ich sollte ", "muss noch ", "bitte noch ", "aufgabe:",
            "reminder:", "to-do:", "to do:", "todo:", "need to ", "have to ",
            "notiz:", "merke:", "note:", "please ", "bitte ", "hallo,", "hey,", "hi,"
        )

        private val TRAILING_FUNCTION_WORDS = setOf(
            "and", "or", "but", "the", "a", "an", "to", "for", "of", "with", "in", "on",
            "at", "by", "from", "about", "my", "our", "your", "und", "oder", "aber",
            "der", "die", "das", "ein", "eine", "einen", "einem", "zu", "zum", "zur",
            "für", "fuer", "mit", "von", "vom", "im", "am", "an", "auf", "bei",
            "über", "ueber", "mein", "meine", "noch"
        )

        private val META_GENERIC_LINES = setOf(
            "note", "notes", "quick note", "update", "updates", "today", "tomorrow",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "notiz", "notizen", "heute", "morgen", "montag", "dienstag", "mittwoch",
            "donnerstag", "freitag", "samstag", "sonntag", "draft", "entwurf", "info",
            "hey", "hi", "hello", "hallo"
        )

        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "from", "have", "your", "are",
            "was", "were", "will", "not", "but", "can", "into", "about", "just", "than",
            "then", "them", "they", "what", "when", "where", "which", "would", "could",
            "should", "there", "their", "been", "being", "also", "more", "some", "very",
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem",
            "einer", "und", "oder", "aber", "dass", "weil", "wenn", "mit", "von", "vom",
            "auf", "aus", "bei", "nach", "für", "fuer", "über", "ueber", "noch", "schon",
            "auch", "nur", "sehr", "mehr", "sind", "ist", "war", "werden", "wird",
            "haben", "hatte", "kann", "muss", "nicht", "kein", "keine", "ich", "wir"
        )

        private val SHOPPING_HINTS = setOf(
            "milk", "bread", "eggs", "coffee", "butter", "cheese", "apples", "bananas",
            "rice", "pasta", "sugar", "flour", "chicken", "soap", "shampoo", "grocery",
            "oat milk", "milch", "hafermilch", "brot", "eier", "kaffee", "käse", "kaese",
            "äpfel", "aepfel", "bananen", "tomaten", "zwiebeln", "joghurt", "reis",
            "nudeln", "mehl", "zucker", "seife", "einkauf"
        )

        private val PACKING_HINTS = setOf(
            "passport", "charger", "adapter", "tickets", "socks", "jacket", "toothbrush", "luggage",
            "reisepass", "ladekabel", "socken", "jacke", "zahnbürste", "zahnbuerste",
            "koffer", "handtuch", "sonnencreme", "powerbank"
        )

        private val WORKOUT_HINTS = setOf(
            "pushups", "squats", "bench", "deadlift", "pullups", "plank", "cardio",
            "stretching", "liegestütze", "liegestuetze", "kniebeugen", "klimmzüge", "klimmzuege"
        )

        private val WATCH_HINTS = setOf(
            "season", "episode", "movie", "movies", "trailer", "staffel", "folge"
        )

        private val GENERIC_URL_SEGMENTS = setOf(
            "index", "home", "page", "article", "post", "posts", "blog", "docs", "en", "us"
        )

        private val DOMAIN_CUE_WORDS = setOf(
            "invoice", "receipt", "order", "flight", "itinerary", "meeting", "agenda",
            "summary", "roadmap", "architecture", "exception", "error", "stacktrace",
            "recipe", "workout", "budget", "password", "booking", "ticket", "reminder",
            "rechnung", "quittung", "bestellung", "flug", "besprechung", "protokoll",
            "zusammenfassung", "rezept", "termin", "projekt", "vertrag", "steuer",
            "arzt", "zahnarzt", "werkstatt", "urlaub", "packliste", "einkaufsliste"
        )

        private val GENERIC_VISUAL_LABELS = setOf(
            "product", "font", "rectangle", "material property", "text", "line", "pattern",
            "symmetry", "screenshot", "brand", "logo", "event", "art", "design", "circle",
            "square", "color", "darkness", "light", "indoor", "outdoor", "thing", "object",
            "parallel", "space", "paper", "document"
        )

        private fun stripTrailingFunctionWords(text: String): String {
            val mutableWords = text.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
            var trimCount = 0
            while (trimCount < 2 && mutableWords.size >= 3) {
                val lastLower = mutableWords.last().trimEnd('.', ',', ':', ';', '-', '–', '—').lowercase(Locale.ROOT)
                if (lastLower in TRAILING_FUNCTION_WORDS) {
                    mutableWords.removeAt(mutableWords.lastIndex)
                    trimCount++
                } else {
                    break
                }
            }
            return mutableWords.joinToString(" ")
        }

        fun formatTitle(raw: String, maxLen: Int = MAX_TITLE_LEN): String {
            val clampedMax = maxLen.coerceIn(24, 96)
            val collapsed = raw
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.', ',', ':', ';', '-', '–', '—')
                .trim()
            if (collapsed.isBlank()) return ""

            val truncated = if (collapsed.length <= clampedMax) {
                collapsed
            } else {
                val slice = collapsed.take(clampedMax)
                val lastSpace = slice.lastIndexOf(' ')
                if (lastSpace >= clampedMax / 2) slice.substring(0, lastSpace) else slice
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
