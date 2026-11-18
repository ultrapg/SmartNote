package io.github.ultrapg.smartnote.domain.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

object TextHashing {

    private val whitespaceRegex = Regex("\\s+")
    private val wordRegex = Regex("[\\p{L}\\p{N}_+#-]{2,}")

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun normalizeForHash(title: String, fullPlainText: String): String {
        val combined = "$title\n$fullPlainText"
            .lowercase(Locale.ROOT)
            .replace(whitespaceRegex, " ")
            .trim()
        return combined
    }

    fun tokenizeWords(text: String): List<String> {
        return wordRegex.findAll(text.lowercase(Locale.ROOT))
            .map { it.value.trim('_', '+', '#', '-') }
            .filter { it.length >= 2 }
            .toList()
    }

    fun computeSimHash64(text: String): Long {
        val tokens = tokenizeWords(text)
        if (tokens.isEmpty()) return 0L

        val features = ArrayList<String>(tokens.size * 2)
        features.addAll(tokens)
        for (i in 0 until tokens.size - 1) {
            features.add("${tokens[i]}_${tokens[i + 1]}")
        }

        val bitWeights = IntArray(64)
        for (feature in features) {
            val h = fnv1a64(feature)
            for (bit in 0 until 64) {
                if (((h ushr bit) and 1L) == 1L) {
                    bitWeights[bit] += 1
                } else {
                    bitWeights[bit] -= 1
                }
            }
        }

        var fingerprint = 0L
        for (bit in 0 until 64) {
            if (bitWeights[bit] > 0) {
                fingerprint = fingerprint or (1L shl bit)
            }
        }
        return fingerprint
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun jaccardSimilarity(tokensA: Set<String>, tokensB: Set<String>): Float {
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0f
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        var intersection = 0
        val smaller = if (tokensA.size <= tokensB.size) tokensA else tokensB
        val larger = if (tokensA.size <= tokensB.size) tokensB else tokensA
        for (t in smaller) {
            if (t in larger) intersection++
        }
        val union = tokensA.size + tokensB.size - intersection
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }

    private fun fnv1a64(input: String): Long {
        var hash = -0x340d631b7bdddcdbL // 14695981039346656037UL signed
        val prime = 0x100000001b3L
        for (ch in input) {
            hash = hash xor (ch.code.toLong() and 0xFFFFL)
            hash *= prime
        }
        return hash
    }
}
