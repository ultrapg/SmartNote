package io.github.ultrapg.smartnote.search

import androidx.compose.runtime.Immutable
import androidx.room.useReaderConnection
import io.github.ultrapg.smartnote.data.local.RecentSearchEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.exp
import kotlin.math.min

enum class SearchDateFilter {
    ANY,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS
}

enum class SearchMatchField {
    TITLE,
    CONTENT,
    CHECKLIST,
    TAG,
    OCR,
    URL
}

@Immutable
data class SearchFilterSpec(
    val tagNames: Set<String> = emptySet(),
    val pinnedOnly: Boolean = false,
    val hasImageOnly: Boolean = false,
    val hasChecklistOnly: Boolean = false,
    val hasLinkOnly: Boolean = false,
    val hasOcrOnly: Boolean = false,
    val dateFilter: SearchDateFilter = SearchDateFilter.ANY,
    val includeArchived: Boolean = false
)

@Immutable
data class SmartSearchResultItem(
    val note: NoteSummary,
    val score: Double,
    val matchedFields: Set<SearchMatchField>,
    val highlightTerms: List<String>,
    val ocrPreviewSnippet: String?
)

@Immutable
data class SmartSearchResponse(
    val results: List<SmartSearchResultItem>,
    val didYouMeanSuggestion: String?,
    val queryTokens: List<String>,
    val durationMs: Long
)

data class ParsedSearchQuery(
    val textTokens: List<String>,
    val inlineTags: Set<String>,
    val pinnedOnly: Boolean,
    val hasImageOnly: Boolean,
    val hasChecklistOnly: Boolean,
    val hasLinkOnly: Boolean,
    val afterEpochMs: Long?,
    val beforeEpochMs: Long?
)

object SearchQueryParser {

    private val tokenSplitRegex = Regex("\\s+")
    private val cleanTokenRegex = Regex("[\\p{L}\\p{N}_+#.-]+")

    fun parse(rawQuery: String): ParsedSearchQuery {
        val parts = rawQuery.trim().split(tokenSplitRegex).filter { it.isNotBlank() }
        val textTokens = mutableListOf<String>()
        val inlineTags = LinkedHashSet<String>()
        var pinned = false
        var hasImage = false
        var hasChecklist = false
        var hasLink = false
        var afterMs: Long? = null
        var beforeMs: Long? = null

        for (part in parts) {
            val lower = part.lowercase(Locale.ROOT)
            when {
                lower.startsWith("#") && lower.length > 1 -> {
                    inlineTags += lower.removePrefix("#")
                }
                lower == "is:pinned" -> pinned = true
                lower == "has:image" || lower == "has:screenshot" -> hasImage = true
                lower == "has:checklist" -> hasChecklist = true
                lower == "has:link" || lower == "has:url" -> hasLink = true
                lower.startsWith("after:") -> {
                    parseDateStartMs(lower.removePrefix("after:"))?.let { afterMs = it }
                }
                lower.startsWith("before:") -> {
                    parseDateEndMs(lower.removePrefix("before:"))?.let { beforeMs = it }
                }
                else -> {
                    val sanitized = sanitizeToken(part)
                    if (sanitized.isNotBlank()) {
                        textTokens += sanitized
                    }
                }
            }
        }
        return ParsedSearchQuery(
            textTokens = textTokens,
            inlineTags = inlineTags,
            pinnedOnly = pinned,
            hasImageOnly = hasImage,
            hasChecklistOnly = hasChecklist,
            hasLinkOnly = hasLink,
            afterEpochMs = afterMs,
            beforeEpochMs = beforeMs
        )
    }

    fun sanitizeToken(raw: String): String {
        val extracted = cleanTokenRegex.findAll(raw.lowercase(Locale.ROOT))
            .map { it.value.trim('.') }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return extracted.trim()
    }

    fun escapeFtsToken(token: String): String {
        val escaped = token.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun parseDateStartMs(raw: String): Long? = runCatching {
        LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parseDateEndMs(raw: String): Long? = runCatching {
        LocalDate.parse(raw).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L
    }.getOrNull()
}

object FuzzyVocabMatcher {

    fun damerauLevenshtein(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, dp[i - 2][j - 2] + cost)
                }
                dp[i][j] = best
            }
        }
        return dp[n][m]
    }

    fun findBestCorrection(
        token: String,
        candidates: List<Pair<String, Int>>
    ): String? {
        if (token.length < 4) return null
        val maxDist = if (token.length <= 6) 1 else 2
        var bestTerm: String? = null
        var bestDist = maxDist + 1
        var bestFreq = -1

        for ((cand, freq) in candidates) {
            if (cand == token) return null // Exact term already exists
            if (kotlin.math.abs(cand.length - token.length) > maxDist) continue
            val dist = damerauLevenshtein(token, cand)
            if (dist <= maxDist) {
                if (dist < bestDist || (dist == bestDist && freq > bestFreq)) {
                    bestDist = dist
                    bestFreq = freq
                    bestTerm = cand
                }
            }
        }
        return bestTerm
    }
}

class SmartSearchEngine(
    private val database: SmartNoteDatabase,
    private val noteRepository: NoteRepository,
    private val semanticScoreProvider: (suspend (query: String, candidateIds: List<String>) -> Map<String, Float>)? = null
) {
    private val synonyms: Map<String, List<String>> = mapOf(
        "bug" to listOf("error", "exception", "crash"),
        "error" to listOf("exception", "crash", "failure"),
        "crash" to listOf("exception", "error", "fatal"),
        "pic" to listOf("screenshot", "image", "photo"),
        "photo" to listOf("image", "screenshot"),
        "screenshot" to listOf("image", "capture"),
        "todo" to listOf("task", "checklist"),
        "task" to listOf("todo", "checklist"),
        "link" to listOf("url", "website"),
        "url" to listOf("link", "website"),
        "shop" to listOf("grocery", "shopping"),
        "buy" to listOf("shopping", "grocery"),
        "doc" to listOf("document", "notes"),
        "code" to listOf("programming", "kotlin", "android")
    )

    fun observeRecentSearches(): Flow<List<String>> =
        database.searchAndMaintenanceDao().observeRecentSearches().map { list ->
            list.map { it.query }
        }

    suspend fun recordRecentSearch(rawQuery: String) {
        val clean = rawQuery.trim()
        if (clean.length < 2) return
        database.searchAndMaintenanceDao().upsertRecentSearch(
            RecentSearchEntity(query = clean, searchedAtMs = System.currentTimeMillis())
        )
    }

    suspend fun deleteRecentSearch(query: String) {
        database.searchAndMaintenanceDao().deleteRecentSearch(query)
    }

    suspend fun clearRecentSearches() {
        database.searchAndMaintenanceDao().clearRecentSearches()
    }

    suspend fun search(
        rawQuery: String,
        filter: SearchFilterSpec = SearchFilterSpec(),
        enableSemanticRerank: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): SmartSearchResponse = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val parsed = SearchQueryParser.parse(rawQuery)
        val mergedTags = (filter.tagNames.map { it.lowercase(Locale.ROOT) } + parsed.inlineTags).toSet()
        val requirePinned = filter.pinnedOnly || parsed.pinnedOnly
        val requireImage = filter.hasImageOnly || parsed.hasImageOnly
        val requireChecklist = filter.hasChecklistOnly || parsed.hasChecklistOnly
        val requireLink = filter.hasLinkOnly || parsed.hasLinkOnly

        val dateAfterMs = parsed.afterEpochMs ?: when (filter.dateFilter) {
            SearchDateFilter.ANY -> null
            SearchDateFilter.TODAY -> nowMs - 24L * 60L * 60L * 1000L
            SearchDateFilter.LAST_7_DAYS -> nowMs - 7L * 24L * 60L * 60L * 1000L
            SearchDateFilter.LAST_30_DAYS -> nowMs - 30L * 24L * 60L * 60L * 1000L
        }
        val dateBeforeMs = parsed.beforeEpochMs

        val allSummaries = noteRepository.getAllSummariesSnapshot(includeArchived = filter.includeArchived)
            .filter { summary ->
                val matchesTags = mergedTags.isEmpty() || mergedTags.all { req ->
                    summary.tags.any { it.name.equals(req, ignoreCase = true) }
                }
                val matchesPinned = !requirePinned || summary.isPinned
                val matchesImage = !requireImage || summary.imageCount > 0
                val matchesChecklist = !requireChecklist || summary.hasChecklist
                val matchesLink = !requireLink || summary.hasLink
                val matchesOcr = !filter.hasOcrOnly || !summary.ocrSnippet.isNullOrBlank()
                val matchesAfter = dateAfterMs == null || summary.updatedAtMs >= dateAfterMs
                val matchesBefore = dateBeforeMs == null || summary.updatedAtMs <= dateBeforeMs
                matchesTags && matchesPinned && matchesImage && matchesChecklist &&
                    matchesLink && matchesOcr && matchesAfter && matchesBefore
            }

        val summaryMap = allSummaries.associateBy { it.id }
        val tokens = parsed.textTokens
        if (tokens.isEmpty()) {
            val ordered = allSummaries
                .sortedWith(compareByDescending<NoteSummary> { it.isPinned }.thenByDescending { it.updatedAtMs })
                .map { summary ->
                    SmartSearchResultItem(
                        note = summary,
                        score = if (summary.isPinned) 10.0 else 1.0,
                        matchedFields = if (mergedTags.isNotEmpty()) setOf(SearchMatchField.TAG) else emptySet(),
                        highlightTerms = emptyList(),
                        ocrPreviewSnippet = summary.ocrSnippet?.take(120)
                    )
                }
            val elapsed = (System.nanoTime() - startNs) / 1_000_000L
            return@withContext SmartSearchResponse(ordered, null, emptyList(), elapsed)
        }

        // 1. Primary FTS5 search with weighted BM25 (exact + prefix + synonyms)
        val ftsScores = HashMap<String, Double>()
        val ftsSnippets = HashMap<String, String>()
        val primaryFtsExpr = buildFtsMatchExpression(tokens, includeSynonyms = true)
        if (primaryFtsExpr != null) {
            queryFts5Bm25(primaryFtsExpr).forEach { (noteId, negBm25, snippet) ->
                if (noteId in summaryMap) {
                    ftsScores[noteId] = negBm25
                    if (snippet.isNotBlank()) ftsSnippets[noteId] = snippet
                }
            }
        }

        // 2. Trigram substring fallback for code identifiers / partial URLs / non-prefix matches
        val trigramHits = HashSet<String>()
        for (token in tokens) {
            if (token.length >= 3) {
                queryTrigram(token).forEach { noteId ->
                    if (noteId in summaryMap) trigramHits += noteId
                }
            }
        }

        // 3. Fuzzy Damerau-Levenshtein typo tolerance + "Did you mean" suggestion
        var didYouMeanParts: MutableList<String>? = null
        val fuzzyBoosts = HashMap<String, Double>()
        val correctedHighlightTerms = LinkedHashSet<String>()

        if (ftsScores.size < 3) {
            val vocabCache = database.searchAndMaintenanceDao()
                .getVocabByLengthRange(3, 32)
                .map { it.term to it.docFreq }
            val correctedTokens = mutableListOf<String>()
            var hadCorrection = false

            for (token in tokens) {
                val correction = FuzzyVocabMatcher.findBestCorrection(token, vocabCache)
                if (correction != null) {
                    hadCorrection = true
                    correctedTokens += correction
                    correctedHighlightTerms += correction
                } else {
                    correctedTokens += token
                }
            }

            if (hadCorrection) {
                didYouMeanParts = correctedTokens
                val fuzzyExpr = buildFtsMatchExpression(correctedTokens, includeSynonyms = false)
                if (fuzzyExpr != null) {
                    queryFts5Bm25(fuzzyExpr).forEach { (noteId, negBm25, snippet) ->
                        if (noteId in summaryMap && noteId !in ftsScores) {
                            fuzzyBoosts[noteId] = negBm25 * 0.65
                            if (snippet.isNotBlank()) ftsSnippets.putIfAbsent(noteId, snippet)
                        }
                    }
                }
            }
        }

        val candidateIds = (ftsScores.keys + trigramHits + fuzzyBoosts.keys).toSet()
        val semanticBonuses = if (enableSemanticRerank && semanticScoreProvider != null && candidateIds.isNotEmpty()) {
            semanticScoreProvider.invoke(tokens.joinToString(" "), candidateIds.take(50))
        } else {
            emptyMap()
        }

        val joinedQuery = tokens.joinToString(" ").lowercase(Locale.ROOT)
        val highlightList = (tokens + correctedHighlightTerms + tokens.flatMap { synonyms[it].orEmpty() })
            .distinct()

        val scoredResults = candidateIds.mapNotNull { noteId ->
            val summary = summaryMap[noteId] ?: return@mapNotNull null
            val lowerTitle = summary.title.lowercase(Locale.ROOT)
            val lowerPreview = summary.previewText.lowercase(Locale.ROOT)
            val lowerOcr = summary.ocrSnippet?.lowercase(Locale.ROOT).orEmpty()
            val lowerChecklist = summary.checklistPreview.joinToString(" ") { it.text }.lowercase(Locale.ROOT)

            val matchedFields = LinkedHashSet<SearchMatchField>()
            if (highlightList.any { lowerTitle.contains(it) }) {
                matchedFields += SearchMatchField.TITLE
            }
            if (highlightList.any { lowerPreview.contains(it) }) {
                matchedFields += SearchMatchField.CONTENT
            }
            if (highlightList.any { lowerChecklist.contains(it) }) {
                matchedFields += SearchMatchField.CHECKLIST
            }
            if (summary.tags.any { t -> highlightList.any { tok -> t.name.lowercase(Locale.ROOT).contains(tok) } }) {
                matchedFields += SearchMatchField.TAG
            }
            if (highlightList.any { lowerOcr.contains(it) }) {
                matchedFields += SearchMatchField.OCR
            }
            if (summary.primaryLinkDomain != null && highlightList.any { summary.primaryLinkDomain.lowercase(Locale.ROOT).contains(it) }) {
                matchedFields += SearchMatchField.URL
            }
            if (matchedFields.isEmpty()) matchedFields += SearchMatchField.CONTENT

            val exactTitleBonus = if (lowerTitle == joinedQuery || tokens.all { t ->
                    lowerTitle.split(Regex("\\s+")).contains(t)
                }
            ) 40.0 else 0.0
            val prefixTitleBonus = if (exactTitleBonus == 0.0 && tokens.any { lowerTitle.contains(it) }) 20.0 else 0.0
            val bm25Component = ftsScores[noteId] ?: fuzzyBoosts[noteId] ?: if (noteId in trigramHits) 4.0 else 0.0
            val tagMatchRatio = if (summary.tags.isEmpty()) 0.0 else {
                val hits = tokens.count { tok -> summary.tags.any { it.name.lowercase(Locale.ROOT).contains(tok) } }
                hits.toDouble() / tokens.size.toDouble()
            }
            val ocrPenaltyOrBoost = if (SearchMatchField.OCR in matchedFields && SearchMatchField.TITLE !in matchedFields && SearchMatchField.CONTENT !in matchedFields) {
                2.5
            } else {
                0.0
            }
            val pinnedBonus = if (summary.isPinned) 4.0 else 0.0
            val ageDays = ((nowMs - summary.updatedAtMs).coerceAtLeast(0L)) / (1000.0 * 60.0 * 60.0 * 24.0)
            val recencyBonus = 3.0 * exp(-ageDays / 30.0)
            val semanticBonus = (semanticBonuses[noteId] ?: 0f) * 8.0

            val totalScore = exactTitleBonus + prefixTitleBonus + bm25Component +
                (12.0 * tagMatchRatio) + ocrPenaltyOrBoost + pinnedBonus + recencyBonus + semanticBonus

            val ocrSnippet = if (SearchMatchField.OCR in matchedFields) {
                extractSnippetWindow(summary.ocrSnippet.orEmpty(), highlightList)
            } else {
                ftsSnippets[noteId]?.takeIf { it.isNotBlank() }
            }

            SmartSearchResultItem(
                note = summary,
                score = totalScore,
                matchedFields = matchedFields,
                highlightTerms = highlightList,
                ocrPreviewSnippet = ocrSnippet
            )
        }.sortedByDescending { it.score }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        SmartSearchResponse(
            results = scoredResults,
            didYouMeanSuggestion = didYouMeanParts?.joinToString(" "),
            queryTokens = tokens,
            durationMs = elapsedMs
        )
    }

    private fun buildFtsMatchExpression(tokens: List<String>, includeSynonyms: Boolean): String? {
        if (tokens.isEmpty()) return null
        val clauses = tokens.mapNotNull { raw ->
            val token = raw.trim()
            if (token.isBlank()) return@mapNotNull null
            val variants = linkedSetOf("${SearchQueryParser.escapeFtsToken(token)}*")
            if (includeSynonyms) {
                synonyms[token]?.forEach { syn ->
                    variants += "${SearchQueryParser.escapeFtsToken(syn)}*"
                }
            }
            if (variants.size == 1) variants.first() else "(${variants.joinToString(" OR ")})"
        }
        return if (clauses.isEmpty()) null else clauses.joinToString(" AND ")
    }

    private suspend fun queryFts5Bm25(matchExpression: String): List<Triple<String, Double, String>> {
        return runCatching {
            database.useReaderConnection { conn ->
                conn.usePrepared(
                    """
                    SELECT note_id,
                           -bm25(notes_fts, 12.0, 5.0, 6.0, 8.0, 3.5, 3.0) AS rank_score,
                           snippet(notes_fts, 4, '', '', '...', 12) AS ocr_snip
                    FROM notes_fts
                    WHERE notes_fts MATCH ?
                    ORDER BY rank_score DESC
                    LIMIT 250
                    """.trimIndent()
                ) { stmt ->
                    stmt.bindText(1, matchExpression)
                    buildList {
                        while (stmt.step()) {
                            val id = stmt.getText(0)
                            val score = stmt.getDouble(1).coerceAtLeast(0.1)
                            val snip = stmt.getText(2)
                            add(Triple(id, score, snip))
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun queryTrigram(token: String): List<String> {
        return runCatching {
            database.useReaderConnection { conn ->
                conn.usePrepared(
                    "SELECT note_id FROM notes_trigram_fts WHERE notes_trigram_fts MATCH ? LIMIT 200"
                ) { stmt ->
                    stmt.bindText(1, SearchQueryParser.escapeFtsToken(token))
                    buildList {
                        while (stmt.step()) {
                            add(stmt.getText(0))
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun extractSnippetWindow(text: String, terms: List<String>): String? {
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.ROOT)
        val idx = terms.firstNotNullOfOrNull { term ->
            val pos = lower.indexOf(term.lowercase(Locale.ROOT))
            if (pos >= 0) pos else null
        } ?: 0
        val start = (idx - 32).coerceAtLeast(0)
        val end = (idx + 96).coerceAtMost(text.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return "$prefix${text.substring(start, end).replace('\n', ' ')}$suffix"
    }
}
