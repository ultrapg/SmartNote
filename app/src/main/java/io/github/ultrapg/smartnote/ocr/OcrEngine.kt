package io.github.ultrapg.smartnote.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class OcrOutcome(
    val text: String,
    val confidence: Float,
    val succeeded: Boolean,
    val errorMessage: String? = null
)

interface OcrEngine {
    suspend fun recognizeFile(file: File): OcrOutcome
}

object SafeOcrBitmapLoader {
    const val TALL_THRESHOLD_PX = 3200
    const val BAND_HEIGHT_PX = 2800
    const val BAND_OVERLAP_PX = 160
    const val MAX_STANDARD_DIM_PX = 2048

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
            // Deduplicate up to 4 overlapping lines at slice boundaries
            var skipCount = 0
            val maxCheck = min(4, min(result.size, lines.size))
            for (k in maxCheck downTo 1) {
                val tail = result.subList(result.size - k, result.size)
                val head = lines.subList(0, k)
                if (tail == head) {
                    skipCount = k
                    break
                }
            }
            result.addAll(lines.drop(skipCount))
        }
        return result.joinToString("\n")
    }
}
