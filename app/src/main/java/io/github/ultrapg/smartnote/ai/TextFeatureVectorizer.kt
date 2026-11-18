package io.github.ultrapg.smartnote.ai

import io.github.ultrapg.smartnote.domain.util.TextHashing
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

object TextFeatureVectorizer {
    const val INPUT_DIMS = 128
    const val OUTPUT_DIMS = 64

    fun vectorizeInput(text: String): FloatArray {
        val vec = FloatArray(INPUT_DIMS)
        val clean = text.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty()) return vec

        // 1. Word unigrams (weight 2.0)
        for (word in TextHashing.tokenizeWords(clean)) {
            val bucket = (fnv32(word) ushr 1) % INPUT_DIMS
            val sign = if ((fnv32("s_$word") and 1) == 0) 1f else -1f
            vec[bucket] += 2.0f * sign
        }

        // 2. Character 3-grams (weight 1.0) for morphology & typo resilience
        val compact = clean.replace(Regex("\\s+"), " ")
        for (i in 0..(compact.length - 3)) {
            val trigram = compact.substring(i, i + 3)
            val bucket = (fnv32("g_$trigram") ushr 1) % INPUT_DIMS
            vec[bucket] += 1.0f
        }

        return l2Normalize(vec)
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vector) {
            sumSq += (v * v)
        }
        val norm = sqrt(sumSq).toFloat()
        if (norm <= 1e-6f) return vector
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom <= 1e-8) 0f else (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    fun toByteArray(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun fromByteArray(bytes: ByteArray): FloatArray {
        val count = bytes.size / 4
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buffer.float }
    }

    private fun fnv32(input: String): Int {
        var hash = -0x7ee3623b // 2166136261U
        val prime = 0x01000193
        for (ch in input) {
            hash = hash xor (ch.code and 0xFF)
            hash *= prime
        }
        return hash
    }
}
