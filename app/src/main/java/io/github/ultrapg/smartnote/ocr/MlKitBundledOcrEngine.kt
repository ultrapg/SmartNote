package io.github.ultrapg.smartnote.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MlKitBundledOcrEngine(
    @Suppress("unused")
    private val context: Context
) : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeFile(file: File): OcrOutcome = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext OcrOutcome(
                text = "",
                confidence = 0f,
                succeeded = false,
                errorMessage = "Attachment file missing or empty"
            )
        }

        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) {
                return@withContext OcrOutcome(
                    text = "",
                    confidence = 0f,
                    succeeded = false,
                    errorMessage = "Unsupported or unreadable image dimensions"
                )
            }

            val rotationDegrees = SafeOcrBitmapLoader.readExifRotationDegrees(file)
            val bands = SafeOcrBitmapLoader.computeBandRects(width, height)
            if (bands.size > 1 && rotationDegrees == 0) {
                val decoder = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(file.absolutePath)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(file.absolutePath, false)
                    }
                }.getOrNull()

                if (decoder != null) {
                    val bandTexts = mutableListOf<String>()
                    val confidences = mutableListOf<Float>()
                    try {
                        var sampleSize = 1
                        while (width / (sampleSize * 2) >= SafeOcrBitmapLoader.MAX_STANDARD_DIM_PX) {
                            sampleSize *= 2
                        }
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        for (rect in bands) {
                            val slice = decoder.decodeRegion(rect, opts) ?: continue
                            try {
                                val visionText = recognizer.process(InputImage.fromBitmap(slice, 0)).await()
                                val text = formatVisionText(visionText, slice.height)
                                if (text.isNotEmpty()) {
                                    bandTexts += text
                                    confidences += averageBlockConfidence(visionText)
                                }
                            } finally {
                                slice.recycle()
                            }
                        }
                    } finally {
                        decoder.recycle()
                    }

                    val merged = OcrTextPostProcessor.cleanRecognizedText(
                        SafeOcrBitmapLoader.mergeTiledOcrTexts(bandTexts)
                    )
                    val meanConf = if (confidences.isEmpty()) 0f else confidences.average().toFloat()
                    return@withContext OcrOutcome(
                        text = merged,
                        confidence = meanConf,
                        succeeded = true
                    )
                }
            }

            // Standard or EXIF-rotated image: decode upright bitmap once and retry with contrast/upscale if weak
            val bitmap = SafeOcrBitmapLoader.decodeUprightDownsampledBitmap(
                file = file,
                maxDimPx = SafeOcrBitmapLoader.MAX_STANDARD_DIM_PX
            ) ?: return@withContext OcrOutcome(
                text = "",
                confidence = 0f,
                succeeded = false,
                errorMessage = "Unable to decode bitmap safely for OCR"
            )
            try {
                val primaryVision = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                var bestText = formatVisionText(primaryVision, bitmap.height)
                var bestConf = averageBlockConfidence(primaryVision)
                val primaryNoise = OcrTextPostProcessor.estimateTextNoiseScore(bestText)
                val needsRetry = bestText.length < 24 ||
                    bestConf < 0.72f ||
                    primaryNoise > 0.24f ||
                    max(bitmap.width, bitmap.height) < 1000

                if (needsRetry) {
                    val enhanced = createEnhancedBitmapForOcr(bitmap)
                    if (enhanced != null) {
                        try {
                            val retryVision = recognizer.process(InputImage.fromBitmap(enhanced, 0)).await()
                            val retryText = formatVisionText(retryVision, enhanced.height)
                            val retryConf = averageBlockConfidence(retryVision)
                            if (scoreOcrPass(retryText, retryConf) > scoreOcrPass(bestText, bestConf)) {
                                bestText = retryText
                                bestConf = retryConf
                            }
                        } finally {
                            if (!enhanced.isRecycled) enhanced.recycle()
                        }
                    }
                }

                OcrOutcome(
                    text = bestText,
                    confidence = bestConf,
                    succeeded = true
                )
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }.getOrElse { throwable ->
            OcrOutcome(
                text = "",
                confidence = 0f,
                succeeded = false,
                errorMessage = throwable.message ?: "OCR failed"
            )
        }
    }

    private fun formatVisionText(visionText: Text, imageHeightPx: Int): String {
        val layoutLines = visionText.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLayoutLine(
                    text = line.text,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom
                )
            }
        }
        return if (layoutLines.isNotEmpty()) {
            OcrTextPostProcessor.orderAndFormatLines(layoutLines, imageHeightPx = imageHeightPx)
        } else {
            OcrTextPostProcessor.cleanRecognizedText(visionText.text)
        }
    }

    private fun createEnhancedBitmapForOcr(source: Bitmap): Bitmap? = runCatching {
        val longest = max(source.width, source.height)
        val scale = if (longest in 1 until 1100) {
            min(1.6f, 2048f / longest.toFloat())
        } else {
            1f
        }
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val enhanced = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val c = 1.35f
        val t = (-0.5f * c + 0.5f) * 255f
        val contrastMatrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(contrastMatrix)
        }
        canvas.drawBitmap(source, null, Rect(0, 0, targetW, targetH), paint)
        enhanced
    }.getOrNull()

    private fun scoreOcrPass(text: String, confidence: Float): Float {
        if (text.isBlank()) return -100f
        val alnumCount = text.count { it.isLetterOrDigit() }.coerceAtMost(400)
        val noisePenalty = OcrTextPostProcessor.estimateTextNoiseScore(text) * 45f
        return (confidence * 100f) + (alnumCount * 0.25f) - noisePenalty
    }

    private fun averageBlockConfidence(visionText: Text): Float {
        val lineConfs = visionText.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line -> line.confidence.takeIf { it > 0f } }
        return if (lineConfs.isNotEmpty()) {
            lineConfs.average().toFloat()
        } else if (visionText.text.isNotBlank()) {
            0.85f
        } else {
            0f
        }
    }
}
