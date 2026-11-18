package io.github.ultrapg.smartnote.tags

import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.links.DetectedLink
import java.util.Locale

data class SmartTagSuggestion(
    val name: String,
    val confidence: Float,
    val reason: String
)

class SmartTagsEngine {

    private data class CategorySpec(
        val tagName: String,
        val weightedTerms: Map<String, Float>
    )

    private val domainRules: Map<String, List<String>> = mapOf(
        "github.com" to listOf("Development", "Code"),
        "gitlab.com" to listOf("Development"),
        "stackoverflow.com" to listOf("Programming", "Troubleshooting"),
        "developer.android.com" to listOf("Android", "Development"),
        "kotlinlang.org" to listOf("Kotlin", "Development"),
        "arxiv.org" to listOf("Research", "Reading"),
        "scholar.google.com" to listOf("Research"),
        "figma.com" to listOf("Design"),
        "docs.google.com" to listOf("Work", "Documents"),
        "youtube.com" to listOf("Video", "Bookmark"),
        "youtu.be" to listOf("Video", "Bookmark"),
        "wikipedia.org" to listOf("Reference")
    )

    private val taxonomy: List<CategorySpec> = listOf(
        CategorySpec(
            "Android",
            mapOf(
                "android" to 2.2f, "jetpack" to 2.0f, "compose" to 1.8f, "activity" to 1.3f,
                "viewmodel" to 1.8f, "workmanager" to 2.0f, "gradle" to 1.8f, "adb" to 2.0f,
                "apk" to 2.0f, "manifest" to 1.5f, "material3" to 2.0f, "room" to 1.4f,
                "datastore" to 1.8f, "ndk" to 1.8f, "recyclerview" to 1.8f, "lifecycle" to 1.5f
            )
        ),
        CategorySpec(
            "Kotlin",
            mapOf(
                "kotlin" to 2.4f, "coroutine" to 2.0f, "coroutines" to 2.0f, "stateflow" to 2.2f,
                "suspend" to 1.8f, "sealed" to 1.5f, "dataclass" to 1.6f, "ksp" to 2.0f,
                "kotlinx" to 2.2f, "flow" to 1.3f, "lambda" to 1.4f
            )
        ),
        CategorySpec(
            "Development",
            mapOf(
                "api" to 1.4f, "sdk" to 1.6f, "git" to 1.6f, "commit" to 1.4f, "branch" to 1.2f,
                "compiler" to 1.8f, "refactor" to 1.7f, "architecture" to 1.4f, "backend" to 1.5f,
                "frontend" to 1.5f, "database" to 1.5f, "sqlite" to 1.8f, "fts5" to 2.0f,
                "json" to 1.5f, "yaml" to 1.5f, "docker" to 1.8f, "kubernetes" to 1.8f,
                "endpoint" to 1.5f, "repository" to 1.4f, "release" to 1.1f
            )
        ),
        CategorySpec(
            "Programming",
            mapOf(
                "function" to 1.3f, "class" to 1.2f, "interface" to 1.3f, "algorithm" to 1.7f,
                "python" to 2.0f, "rust" to 1.9f, "typescript" to 2.0f, "javascript" to 2.0f,
                "java" to 1.7f, "cpp" to 1.9f, "cargo" to 1.7f, "pytest" to 2.0f,
                "syntax" to 1.5f, "variable" to 1.4f, "recursion" to 1.7f, "debug" to 1.5f
            )
        ),
        CategorySpec(
            "Error",
            mapOf(
                "exception" to 2.1f, "crash" to 1.9f, "stacktrace" to 2.3f, "fatal" to 1.9f,
                "nullpointerexception" to 2.4f, "illegalstateexception" to 2.4f, "failed" to 1.4f,
                "failure" to 1.6f, "timeout" to 1.7f, "outofmemoryerror" to 2.4f, "bug" to 1.7f,
                "segfault" to 2.3f, "traceback" to 2.2f, "errno" to 2.0f, "404" to 1.5f, "500" to 1.4f
            )
        ),
        CategorySpec(
            "Work",
            mapOf(
                "sprint" to 1.7f, "standup" to 1.9f, "deadline" to 1.7f, "deliverable" to 1.8f,
                "roadmap" to 1.7f, "stakeholder" to 1.8f, "milestone" to 1.6f, "quarterly" to 1.6f,
                "jira" to 1.9f, "ticket" to 1.4f, "project" to 1.2f, "client" to 1.4f
            )
        ),
        CategorySpec(
            "Meeting",
            mapOf(
                "meeting" to 2.0f, "agenda" to 2.0f, "attendees" to 2.0f, "minutes" to 1.6f,
                "actionitems" to 2.0f, "followup" to 1.7f, "sync" to 1.4f, "notes" to 0.8f,
                "discussion" to 1.4f, "zoom" to 1.6f, "call" to 1.2f
            )
        ),
        CategorySpec(
            "Shopping",
            mapOf(
                "milk" to 1.8f, "eggs" to 1.8f, "bread" to 1.8f, "coffee" to 1.4f,
                "grocery" to 2.2f, "groceries" to 2.2f, "supermarket" to 2.0f, "buy" to 1.4f,
                "apples" to 1.7f, "bananas" to 1.7f, "cheese" to 1.7f, "butter" to 1.7f,
                "rice" to 1.6f, "pasta" to 1.6f, "detergent" to 1.9f, "toothpaste" to 1.9f
            )
        ),
        CategorySpec(
            "Finance",
            mapOf(
                "invoice" to 2.0f, "receipt" to 1.9f, "tax" to 1.8f, "budget" to 1.9f,
                "expense" to 1.8f, "payment" to 1.6f, "bank" to 1.7f, "salary" to 1.9f,
                "subtotal" to 2.0f, "total" to 1.1f, "usd" to 1.7f, "eur" to 1.7f, "refund" to 1.8f
            )
        ),
        CategorySpec(
            "Receipt",
            mapOf(
                "receipt" to 2.3f, "subtotal" to 2.2f, "vat" to 2.0f, "cashier" to 2.1f,
                "merchant" to 2.0f, "order" to 1.3f, "paid" to 1.5f, "visa" to 1.6f,
                "mastercard" to 1.8f, "change" to 1.1f, "qty" to 1.7f
            )
        ),
        CategorySpec(
            "Travel",
            mapOf(
                "flight" to 2.1f, "boarding" to 2.1f, "hotel" to 2.0f, "itinerary" to 2.2f,
                "passport" to 2.1f, "luggage" to 2.0f, "airport" to 2.0f, "terminal" to 1.5f,
                "train" to 1.6f, "reservation" to 1.7f, "checkin" to 1.9f, "booking" to 1.8f
            )
        ),
        CategorySpec(
            "Health",
            mapOf(
                "workout" to 2.0f, "doctor" to 2.0f, "appointment" to 1.6f, "prescription" to 2.2f,
                "vitamins" to 2.0f, "calories" to 1.9f, "cardio" to 2.0f, "sleep" to 1.5f,
                "symptom" to 2.0f, "pharmacy" to 2.1f, "dentist" to 2.1f, "hydration" to 1.8f
            )
        ),
        CategorySpec(
            "Recipe",
            mapOf(
                "ingredients" to 2.2f, "preheat" to 2.3f, "bake" to 1.9f, "simmer" to 2.2f,
                "tablespoon" to 2.2f, "teaspoon" to 2.2f, "tbsp" to 2.1f, "tsp" to 2.1f,
                "oven" to 1.8f, "chop" to 1.7f, "garlic" to 1.8f, "olive" to 1.5f, "flour" to 1.8f
            )
        ),
        CategorySpec(
            "Study",
            mapOf(
                "lecture" to 2.0f, "exam" to 2.1f, "homework" to 2.1f, "chapter" to 1.7f,
                "syllabus" to 2.2f, "theorem" to 2.1f, "quiz" to 1.9f, "flashcards" to 2.1f,
                "summary" to 1.3f, "study" to 1.8f, "course" to 1.5f
            )
        ),
        CategorySpec(
            "Ideas",
            mapOf(
                "idea" to 2.0f, "brainstorm" to 2.2f, "concept" to 1.7f, "prototype" to 1.6f,
                "hypothesis" to 1.8f, "inspiration" to 1.8f, "sketch" to 1.6f, "pitch" to 1.7f
            )
        ),
        CategorySpec(
            "Design",
            mapOf(
                "typography" to 2.1f, "palette" to 2.0f, "figma" to 2.1f, "wireframe" to 2.2f,
                "layout" to 1.6f, "spacing" to 1.6f, "elevation" to 1.7f, "contrast" to 1.6f,
                "animation" to 1.5f, "ux" to 1.9f, "ui" to 1.6f
            )
        ),
        CategorySpec(
            "Tasks",
            mapOf(
                "todo" to 2.0f, "reminder" to 1.5f, "finish" to 1.3f, "submit" to 1.5f,
                "review" to 1.3f, "prepare" to 1.4f, "schedule" to 1.4f, "urgent" to 1.7f
            )
        ),
        CategorySpec(
            "Research",
            mapOf(
                "paper" to 1.5f, "arxiv" to 2.2f, "benchmark" to 1.8f, "evaluation" to 1.6f,
                "dataset" to 1.8f, "citation" to 2.0f, "abstract" to 1.6f, "methodology" to 1.9f
            )
        )
    )

    private val stacktraceRegex = Regex(
        """(Exception|Error|Traceback|at\s+[\w.$]+\(|Caused by:|FATAL EXCEPTION|error\[E\d+\])"""
    )
    private val codeFenceOrKeywordsRegex = Regex(
        """(```|fun\s+\w+\(|class\s+\w+|import\s+[\w.]+|SELECT\s+.+\s+FROM|def\s+\w+\(|fn\s+\w+\()""",
        RegexOption.IGNORE_CASE
    )

    fun suggestTags(
        title: String,
        blocks: List<NoteBlock>,
        ocrTexts: List<String>,
        links: List<DetectedLink>,
        hasScreenshot: Boolean,
        existingUserVocabulary: List<String>,
        semanticTagScores: Map<String, Float> = emptyMap()
    ): List<SmartTagSuggestion> {
        val scores = LinkedHashMap<String, Pair<Float, String>>()

        fun addScore(tag: String, delta: Float, reason: String) {
            val clean = tag.trim()
            if (clean.isBlank()) return
            val existing = scores[clean]
            val updated = (existing?.first ?: 0f) + delta
            scores[clean] = updated to (existing?.second ?: reason)
        }

        val bodyText = blocks.filter { it.type == NoteBlockType.TEXT }.joinToString("\n") { it.textContent }
        val checklistItems = blocks.filter { it.type == NoteBlockType.CHECKLIST }.map { it.textContent }
        val checklistText = checklistItems.joinToString("\n")
        val combinedOcr = ocrTexts.joinToString("\n")
        val allText = listOf(title, bodyText, checklistText, combinedOcr).joinToString("\n")

        // Stage 1: Structural & pattern detectors
        if (hasScreenshot) {
            addScore("Screenshot", 0.88f, "Imported screenshot image")
        }

        if (stacktraceRegex.containsMatchIn(allText)) {
            addScore("Error", 0.86f, "Detected error or stacktrace pattern")
            addScore("Programming", 0.78f, "Detected technical diagnostic trace")
        }

        if (codeFenceOrKeywordsRegex.containsMatchIn(allText)) {
            addScore("Programming", 0.80f, "Detected source code pattern")
        }

        if (checklistItems.size >= 2) {
            addScore("Tasks", 0.68f, "Checklist note structure")
        }

        for (link in links) {
            val matchedDomainTags = domainRules[link.domain]
                ?: domainRules.entries.firstOrNull { link.domain.endsWith(".${it.key}") }?.value
            matchedDomainTags?.forEach { domainTag ->
                addScore(domainTag, 0.85f, "URL domain ${link.domain}")
            }
            if (matchedDomainTags == null) {
                addScore("Bookmark", 0.68f, "Saved web link")
            }
        }

        // Stage 2A: Token weighted frequencies across title (3x), urls (2x), body (1.5x), ocr (1.2x)
        val termWeights = HashMap<String, Float>()
        fun accumulateTokens(text: String, weight: Float) {
            if (text.isBlank()) return
            for (token in TextHashing.tokenizeWords(text)) {
                termWeights[token] = (termWeights[token] ?: 0f) + weight
            }
        }
        accumulateTokens(title, 3.0f)
        accumulateTokens(links.joinToString(" ") { "${it.domain} ${it.rawUrl}" }, 2.0f)
        accumulateTokens(bodyText, 1.5f)
        accumulateTokens(checklistText, 1.6f)
        accumulateTokens(combinedOcr, 1.2f)

        // User-vocabulary priority matching
        val loweredUserTags = existingUserVocabulary
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinctBy { it.lowercase(Locale.ROOT) }
        for (userTag in loweredUserTags) {
            val norm = userTag.lowercase(Locale.ROOT)
            val w = termWeights[norm]
            if (w != null && w >= 1.5f) {
                addScore(userTag, (0.62f + (w / 6.0f)).coerceAtMost(0.95f), "Matches your tag '$userTag'")
            } else if (" " in norm && allText.lowercase(Locale.ROOT).contains(norm)) {
                addScore(userTag, 0.82f, "Matches your tag '$userTag'")
            }
        }

        // Stage 2B: Curated weighted TF-IDF taxonomy
        for (category in taxonomy) {
            var rawSum = 0f
            var distinctHits = 0
            for ((term, termWeight) in category.weightedTerms) {
                val freqWeight = termWeights[term] ?: continue
                distinctHits++
                rawSum += (freqWeight.coerceAtMost(6f) * termWeight)
            }
            if (rawSum >= 2.6f && (distinctHits >= 2 || rawSum >= 3.8f)) {
                val normalizedConfidence = (0.55f + (rawSum / 14f)).coerceIn(0.60f, 0.96f)
                addScore(category.tagName, normalizedConfidence, "Keyword relevance")
            }
        }

        // Stage 3: Optional neural similarity scores
        for ((tagName, sim) in semanticTagScores) {
            if (sim >= 0.55f) {
                addScore(tagName, sim * 0.45f, "Semantic affinity")
            }
        }

        return scores.entries
            .map { (name, pair) ->
                SmartTagSuggestion(
                    name = name,
                    confidence = pair.first.coerceIn(0f, 0.99f),
                    reason = pair.second
                )
            }
            .filter { it.confidence >= 0.65f }
            .sortedByDescending { it.confidence }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .take(4)
    }
}
