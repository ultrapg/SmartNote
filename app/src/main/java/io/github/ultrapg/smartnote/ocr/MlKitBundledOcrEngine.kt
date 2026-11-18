package io.github.ultrapg.smartnote.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

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
            if (bands.size > 1) {
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
                                val text = visionText.text.trim()
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

                    val merged = SafeOcrBitmapLoader.mergeTiledOcrTexts(bandTexts)
                    val meanConf = if (confidences.isEmpty()) 0f else confidences.average().toFloat()
                    return@withContext OcrOutcome(
                        text = merged,
                        confidence = meanConf,
                        succeeded = true
                    )
                }
            }

            // Standard or fallback: decode downsampled bitmap capped at MAX_STANDARD_DIM_PX
            val bitmap = SafeOcrBitmapLoader.decodeDownsampledBitmap(file)
                ?: return@withContext OcrOutcome(
                    text = "",
                    confidence = 0f,
                    succeeded = false,
                    errorMessage = "Unable to decode bitmap safely for OCR"
                )
            try {
                val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
                val visionText = recognizer.process(inputImage).await()
                val text = visionText.text.trim()
                val avgConf = averageBlockConfidence(visionText)
                OcrOutcome(
                    text = text,
                    confidence = avgConf,
                    succeeded = true
                )
            } finally {
                bitmap.recycle()
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

    private fun averageBlockConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
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
