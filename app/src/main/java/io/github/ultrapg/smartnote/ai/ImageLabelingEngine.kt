package io.github.ultrapg.smartnote.ai

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import io.github.ultrapg.smartnote.ocr.SafeOcrBitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

data class DetectedImageLabel(
    val text: String,
    val confidence: Float
)

interface ImageLabelingEngine {
    suspend fun labelImageFile(file: File): List<DetectedImageLabel>
}

class MlKitImageLabelingEngine : ImageLabelingEngine {

    private val labeler: ImageLabeler? by lazy {
        runCatching {
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_LABEL_CONFIDENCE)
                .build()
            ImageLabeling.getClient(options)
        }.getOrNull()
    }

    override suspend fun labelImageFile(file: File): List<DetectedImageLabel> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val client = labeler ?: return@withContext emptyList()
        val bitmap = SafeOcrBitmapLoader.decodeDownsampledBitmap(file, maxDimPx = MAX_LABEL_DIM_PX)
            ?: return@withContext emptyList()
        val rotation = SafeOcrBitmapLoader.readExifRotationDegrees(file)

        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    val input = InputImage.fromBitmap(bitmap, rotation)
                    val labels = client.process(input).await()
                    labels
                        .asSequence()
                        .filter { it.confidence >= MIN_LABEL_CONFIDENCE && it.text.isNotBlank() }
                        .sortedByDescending { it.confidence }
                        .distinctBy { it.text.trim().lowercase(Locale.ROOT) }
                        .take(MAX_LABELS_PER_IMAGE)
                        .map { DetectedImageLabel(text = it.text.trim(), confidence = it.confidence) }
                        .toList()
                }.getOrDefault(emptyList())
            }.orEmpty()
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        const val MIN_LABEL_CONFIDENCE = 0.68f
        const val MAX_LABEL_DIM_PX = 640
        const val MAX_LABELS_PER_IMAGE = 6
        const val TIMEOUT_MS = 2_500L
    }
}
