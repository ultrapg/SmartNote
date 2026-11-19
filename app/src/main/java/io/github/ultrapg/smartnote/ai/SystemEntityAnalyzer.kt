package io.github.ultrapg.smartnote.ai

import android.content.Context
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

enum class SystemEntityAvailability {
    AVAILABLE,
    UNAVAILABLE
}

data class ClassifiedTextEntity(
    val type: String,
    val matchedText: String,
    val confidence: Float
)

interface SystemEntityAnalyzer {
    fun checkAvailability(): SystemEntityAvailability = SystemEntityAvailability.AVAILABLE
    suspend fun extractEntities(text: String): List<ClassifiedTextEntity>
}

class AndroidSystemEntityAnalyzer(
    private val context: Context
) : SystemEntityAnalyzer {

    override fun checkAvailability(): SystemEntityAvailability = runCatching {
        val manager = context.getSystemService(TextClassificationManager::class.java)
            ?: return@runCatching SystemEntityAvailability.UNAVAILABLE
        val classifier = manager.textClassifier
        if (classifier === TextClassifier.NO_OP) {
            SystemEntityAvailability.UNAVAILABLE
        } else {
            SystemEntityAvailability.AVAILABLE
        }
    }.getOrDefault(SystemEntityAvailability.UNAVAILABLE)

    override suspend fun extractEntities(text: String): List<ClassifiedTextEntity> {
        val sample = text.trim().take(MAX_TEXT_CHARS)
        if (sample.length < 4) return emptyList()

        return withContext(Dispatchers.Default) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    val manager = context.getSystemService(TextClassificationManager::class.java)
                        ?: return@runCatching emptyList()
                    val classifier = manager.textClassifier
                    if (classifier === TextClassifier.NO_OP) {
                        return@runCatching emptyList()
                    }
                    val request = TextLinks.Request.Builder(sample).build()
                    val links = classifier.generateLinks(request)

                    val seen = HashSet<String>()
                    buildList {
                        for (link in links.links) {
                            if (link.entityCount <= 0) continue
                            val entityType = link.getEntity(0) ?: continue
                            val confidence = link.getConfidenceScore(entityType)
                            if (confidence < MIN_CONFIDENCE) continue
                            if (link.start < 0 || link.end > sample.length || link.start >= link.end) continue
                            val matched = sample.substring(link.start, link.end).trim()
                            if (matched.isBlank()) continue
                            val key = "${entityType.lowercase(Locale.ROOT)}:${matched.lowercase(Locale.ROOT)}"
                            if (seen.add(key)) {
                                add(
                                    ClassifiedTextEntity(
                                        type = entityType,
                                        matchedText = matched,
                                        confidence = confidence
                                    )
                                )
                            }
                        }
                    }
                }.getOrDefault(emptyList())
            }.orEmpty()
        }
    }

    private companion object {
        const val MAX_TEXT_CHARS = 2000
        const val TIMEOUT_MS = 800L
        const val MIN_CONFIDENCE = 0.60f
    }
}
