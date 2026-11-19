package io.github.ultrapg.smartnote.ai

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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

enum class ImageLabelingAvailability {
    AVAILABLE,
    DOWNLOADABLE,
    UNAVAILABLE
}

data class DetectedImageLabel(
    val text: String,
    val confidence: Float
)

interface ImageLabelingEngine {
    suspend fun checkAvailability(forceRefresh: Boolean = false): ImageLabelingAvailability =
        ImageLabelingAvailability.AVAILABLE

    fun requestModuleDownloadIfNeeded() {}

    suspend fun labelImageFile(file: File): List<DetectedImageLabel>
}

class MlKitImageLabelingEngine(
    private val context: Context
) : ImageLabelingEngine {

    @Volatile
    private var cachedAvailability: ImageLabelingAvailability? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private val labeler: ImageLabeler? by lazy {
        runCatching {
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_LABEL_CONFIDENCE)
                .build()
            ImageLabeling.getClient(options)
        }.getOrNull()
    }

    override suspend fun checkAvailability(forceRefresh: Boolean): ImageLabelingAvailability =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cachedAvailability
            if (!forceRefresh && cached != null && (now - cachedAtMs) < CACHE_TTL_MS) {
                return@withContext cached
            }

            val playStatus = runCatching {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            }.getOrDefault(ConnectionResult.SERVICE_MISSING)
            if (playStatus != ConnectionResult.SUCCESS) {
                return@withContext updateCache(ImageLabelingAvailability.UNAVAILABLE, now)
            }

            val client = labeler ?: return@withContext updateCache(ImageLabelingAvailability.UNAVAILABLE, now)
            val resolved = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                runCatching {
                    val response = ModuleInstall.getClient(context)
                        .areModulesAvailable(client)
                        .await()
                    if (response.areModulesAvailable()) {
                        ImageLabelingAvailability.AVAILABLE
                    } else {
                        ImageLabelingAvailability.DOWNLOADABLE
                    }
                }.getOrDefault(ImageLabelingAvailability.UNAVAILABLE)
            } ?: ImageLabelingAvailability.UNAVAILABLE

            updateCache(resolved, now)
        }

    override fun requestModuleDownloadIfNeeded() {
        val playStatus = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)
        if (playStatus != ConnectionResult.SUCCESS) return
        val client = labeler ?: return
        runCatching {
            val installClient = ModuleInstall.getClient(context)
            val req = ModuleInstallRequest.newBuilder().addApi(client).build()
            installClient.installModules(req)
        }.onFailure {
            runCatching { ModuleInstall.getClient(context).deferredInstall(client) }
        }
        cachedAvailability = null
    }

    override suspend fun labelImageFile(file: File): List<DetectedImageLabel> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val availability = checkAvailability()
        when (availability) {
            ImageLabelingAvailability.UNAVAILABLE -> return@withContext emptyList()
            ImageLabelingAvailability.DOWNLOADABLE -> {
                val client = labeler
                if (client != null) {
                    runCatching { ModuleInstall.getClient(context).deferredInstall(client) }
                }
                return@withContext emptyList()
            }
            ImageLabelingAvailability.AVAILABLE -> Unit
        }
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

    private fun updateCache(value: ImageLabelingAvailability, now: Long): ImageLabelingAvailability {
        cachedAvailability = value
        cachedAtMs = now
        return value
    }

    private companion object {
        const val MIN_LABEL_CONFIDENCE = 0.68f
        const val MAX_LABEL_DIM_PX = 640
        const val MAX_LABELS_PER_IMAGE = 6
        const val TIMEOUT_MS = 2_500L
        const val PROBE_TIMEOUT_MS = 1_500L
        const val CACHE_TTL_MS = 60_000L
    }
}
