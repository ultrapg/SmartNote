package io.github.ultrapg.smartnote.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OcrOutcome(
    val text: String,
    val confidence: Float,
    val succeeded: Boolean,
    val errorMessage: String? = null,
    val usedGeminiNano: Boolean = false
)

data class OcrLayoutLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

interface OcrEngine {
    suspend fun recognizeFile(file: File): OcrOutcome
}

object SafeOcrBitmapLoader {
    const val TALL_THRESHOLD_PX = 3200
    const val BAND_HEIGHT_PX = 2800
    const val BAND_OVERLAP_PX = 160
    const val MAX_STANDARD_DIM_PX = 2560

    fun readExifRotationDegrees(file: File): Int = runCatching {
        val exif = ExifInterface(file.absolutePath)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    fun decodeDownsampledBitmap(file: File, maxDimPx: Int = MAX_STANDARD_DIM_PX): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            val longest = max(width, height)
            while (longest / sampleSize > maxDimPx) {
                sampleSize *= 2
            }

            var attemptSample = sampleSize
            repeat(3) {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = attemptSample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = try {
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                } catch (_: OutOfMemoryError) {
                    attemptSample *= 2
                    null
                }
                if (decoded != null) {
                    val curLongest = max(decoded.width, decoded.height)
                    if (curLongest <= maxDimPx) return decoded
                    val scale = maxDimPx.toFloat() / curLongest.toFloat()
                    val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                    return try {
                        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { scaled ->
                            if (scaled !== decoded) decoded.recycle()
                        }
                    } catch (_: OutOfMemoryError) {
                        decoded.recycle()
                        null
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes [file] within [maxDimPx] and rotates pixels upright according to EXIF orientation.
     */
    fun decodeUprightDownsampledBitmap(file: File, maxDimPx: Int = MAX_STANDARD_DIM_PX): Bitmap? {
        val decoded = decodeDownsampledBitmap(file, maxDimPx) ?: return null
        val rotationDegrees = readExifRotationDegrees(file)
        if (rotationDegrees == 0) return decoded
        return try {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) {
                decoded.recycle()
            }
            rotated
        } catch (_: Throwable) {
            decoded
        }
    }

    fun computeBandRects(width: Int, height: Int): List<Rect> {
        if (width <= 0 || height <= 0) return emptyList()
        if (height <= TALL_THRESHOLD_PX || height.toFloat() <= width.toFloat() * 2.5f) {
            return listOf(Rect(0, 0, width, height))
        }
        val rects = mutableListOf<Rect>()
        val step = (BAND_HEIGHT_PX - BAND_OVERLAP_PX).coerceAtLeast(256)
        var top = 0
        while (top < height) {
            val bottom = min(height, top + BAND_HEIGHT_PX)
            rects += Rect(0, top, width, bottom)
            if (bottom >= height) break
            top += step
        }
        return rects
    }

    fun decodeBandsSequentially(file: File, onBand: (Bitmap) -> Unit) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return

        val bands = computeBandRects(width, height)
        if (bands.size <= 1) {
            var sampleSize = 1
            val maxDim = max(width, height)
            while (maxDim / (sampleSize * 2) >= MAX_STANDARD_DIM_PX) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return
            try {
                onBand(bmp)
            } finally {
                bmp.recycle()
            }
            return
        }

        val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(file.absolutePath)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(file.absolutePath, false)
        }
        try {
            var sampleSize = 1
            while (width / (sampleSize * 2) >= MAX_STANDARD_DIM_PX) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            for (rect in bands) {
                val slice = decoder.decodeRegion(rect, opts) ?: continue
                try {
                    onBand(slice)
                } finally {
                    slice.recycle()
                }
            }
        } finally {
            decoder.recycle()
        }
    }

    fun mergeTiledOcrTexts(bandTexts: List<String>): String {
        val result = mutableListOf<String>()
        for (band in bandTexts) {
            val lines = band.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            if (result.isEmpty()) {
                result.addAll(lines)
                continue
            }
            // Deduplicate up to 4 overlapping lines at slice boundaries using normalized comparisons
            var skipCount = 0
            val maxCheck = min(4, min(result.size, lines.size))
            for (k in maxCheck downTo 1) {
                val tail = result.subList(result.size - k, result.size).map(::normalizeLineForMerge)
                val head = lines.subList(0, k).map(::normalizeLineForMerge)
                if (tail == head) {
                    skipCount = k
                    break
                }
            }
            result.addAll(lines.drop(skipCount))
        }
        return result.joinToString("\n")
    }

    private fun normalizeLineForMerge(line: String): String =
        line.lowercase(Locale.ROOT).replace(MULTI_SPACE_REGEX, " ").trim()

    private val MULTI_SPACE_REGEX = Regex("\\s+")
}

/**
 * Spatial layout ordering, cleanup, and quality scoring for recognized OCR text.
 */
object OcrTextPostProcessor {
    private val ZERO_WIDTH_REGEX = Regex("[\\u200B\\uFEFF\\u00AD]")
    private val HYPHEN_WRAP_REGEX = Regex("([\\p{L}]{2,})-\\s*\\n([\\p{Ll}]{2,})")
    private val EXCESS_HORIZONTAL_SPACES_REGEX = Regex("[\\t ]{3,}")
    private val EXCESS_NEWLINES_REGEX = Regex("\\n{3,}")
    private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")
    private val STATUS_BATTERY_REGEX = Regex("^\\d{1,3}%$")
    private val STATUS_RADIO_REGEX = Regex("^(?:5G|4G|3G|LTE|Wi-?Fi|VoLTE|NFC)$", RegexOption.IGNORE_CASE)
    private val STATUS_TIME_REGEX = Regex("^(?:[01]?\\d|2[0-3]):[0-5]\\d$")
    private val NON_ALNUM_ONLY_REGEX = Regex("^[^\\p{L}\\p{N}]+$")
    private val WEIRD_OCR_CHARS = setOf('~', '^', '|', '\\', '§', '\uFFFD', '_', '¢', '¦')

    fun orderAndFormatLines(lines: List<OcrLayoutLine>, imageHeightPx: Int = 0): String {
        val nonEmpty = lines.mapNotNull { line ->
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) null else line.copy(text = trimmed)
        }
        if (nonEmpty.isEmpty()) return ""

        val filtered = if (nonEmpty.size >= 3 && imageHeightPx > 0) {
            val topBandLimit = (imageHeightPx * 0.055f).toInt()
            nonEmpty.filterNot { line ->
                line.bottom <= topBandLimit && isLikelyStatusBarArtifact(line.text)
            }.ifEmpty { nonEmpty }
        } else {
            nonEmpty
        }

        val avgLineHeight = filtered
            .map { (it.bottom - it.top).coerceAtLeast(1) }
            .average()
            .toFloat()
            .coerceAtLeast(12f)

        val topSorted = filtered.sortedWith(compareBy<OcrLayoutLine> { it.top }.thenBy { it.left })
        val rows = ArrayList<MutableList<OcrLayoutLine>>()
        val rowBounds = ArrayList<Pair<Int, Int>>() // top, bottom

        for (line in topSorted) {
            val lineCenterY = (line.top + line.bottom) / 2f
            val targetIdx = rows.indices.firstOrNull { idx ->
                val (rTop, rBottom) = rowBounds[idx]
                val rCenterY = (rTop + rBottom) / 2f
                val overlap = min(rBottom, line.bottom) - max(rTop, line.top)
                val minH = min((rBottom - rTop).coerceAtLeast(1), (line.bottom - line.top).coerceAtLeast(1))
                abs(lineCenterY - rCenterY) <= avgLineHeight * 0.55f ||
                    (overlap.toFloat() / minH.toFloat()) >= 0.45f
            }
            if (targetIdx != null) {
                rows[targetIdx].add(line)
                val (prevTop, prevBottom) = rowBounds[targetIdx]
                rowBounds[targetIdx] = min(prevTop, line.top) to max(prevBottom, line.bottom)
            } else {
                rows.add(mutableListOf(line))
                rowBounds.add(line.top to line.bottom)
            }
        }

        val sb = StringBuilder()
        var prevRowBottom: Int? = null
        val orderedRowIndices = rows.indices.sortedBy { rowBounds[it].first }
        for (idx in orderedRowIndices) {
            val row = rows[idx].sortedBy { it.left }
            val (rowTop, rowBottom) = rowBounds[idx]
            if (prevRowBottom != null) {
                val verticalGap = rowTop - prevRowBottom
                sb.append(if (verticalGap > avgLineHeight * 0.9f) "\n\n" else "\n")
            }
            for (i in row.indices) {
                if (i > 0) {
                    val gap = row[i].left - row[i - 1].right
                    sb.append(if (gap > avgLineHeight * 1.8f) "  " else " ")
                }
                sb.append(row[i].text)
            }
            prevRowBottom = rowBottom
        }

        return cleanRecognizedText(sb.toString())
    }

    fun cleanRecognizedText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(ZERO_WIDTH_REGEX, "")
        text = text.lines().joinToString("\n") { line ->
            line.trimEnd().replace(EXCESS_HORIZONTAL_SPACES_REGEX, "  ")
        }
        text = text.replace(HYPHEN_WRAP_REGEX, "$1$2")
        text = text.replace(EXCESS_NEWLINES_REGEX, "\n\n")
        return text.trim()
    }

    fun estimateTextNoiseScore(text: String): Float {
        if (text.isBlank()) return 1f
        val nonWs = text.filterNot { it.isWhitespace() }
        if (nonWs.isEmpty()) return 1f
        val weirdCount = nonWs.count { it in WEIRD_OCR_CHARS }
        val weirdRatio = weirdCount.toFloat() / nonWs.length.toFloat()

        val tokens = text.trim().split(WHITESPACE_SPLIT_REGEX).filter { it.isNotEmpty() }
        val singleLetterCount = tokens.count { it.length == 1 && it[0].isLetter() }
        val fragmentPenalty = if (tokens.size >= 4 && singleLetterCount >= 3) {
            val ratio = singleLetterCount.toFloat() / tokens.size.toFloat()
            if (ratio > 0.35f) (ratio - 0.25f) * 0.9f else 0f
        } else {
            0f
        }
        return (weirdRatio * 2.2f + fragmentPenalty).coerceIn(0f, 1f)
    }

    private fun isLikelyStatusBarArtifact(text: String): Boolean {
        val clean = text.trim()
        return STATUS_BATTERY_REGEX.matches(clean) ||
            STATUS_RADIO_REGEX.matches(clean) ||
            STATUS_TIME_REGEX.matches(clean) ||
            NON_ALNUM_ONLY_REGEX.matches(clean)
    }
}
