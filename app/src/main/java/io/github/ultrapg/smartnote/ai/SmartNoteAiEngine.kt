package io.github.ultrapg.smartnote.ai

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import io.github.ultrapg.smartnote.data.local.NoteEmbeddingEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AiHardwareBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@Immutable
data class AiHardwareCapabilities(
    val socDescription: String,
    val npuDeviceSupported: Boolean,
    val npuRuntimeReady: Boolean,
    val availableAccelerators: Set<AiHardwareBackend>,
    val isPowerSaveMode: Boolean = false,
    val thermalStatus: Int = 0
)

@Immutable
data class AiDiagnosticsState(
    val requestedPreference: AiBackendPreference = AiBackendPreference.AUTO,
    val activeNeuralBackend: AiHardwareBackend = AiHardwareBackend.UNINITIALIZED,
    val ocrBackend: AiHardwareBackend = AiHardwareBackend.NATIVE_MLKIT,
    val lexicalSearchBackend: AiHardwareBackend = AiHardwareBackend.DIRECT_CPU_LEXICAL,
    val socDescription: String = "Unknown SoC",
    val npuDeviceSupported: Boolean = false,
    val npuRuntimeReady: Boolean = false,
    val detectedAccelerators: Set<AiHardwareBackend> = emptySet(),
    val fallbackReason: String? = null,
    val isModelLoaded: Boolean = false,
    val lastInferenceMs: Long = 0L,
    val energySavingActive: Boolean = false
)

interface LiteRtRuntimeBridge {
    fun probeCapabilities(): AiHardwareCapabilities
    fun initialize(targetBackend: AiHardwareBackend): Result<AiHardwareBackend>
    fun runProjection(input128: FloatArray): Result<FloatArray>
    fun close()
}

class AndroidLiteRtRuntimeBridge(
    private val context: Context,
    private val modelAssetPath: String = "models/smartnote_text_embed_v1.tflite"
) : LiteRtRuntimeBridge {

    private var environment: Environment? = null
    private var compiledModel: CompiledModel? = null
    private var inputBuffers: List<TensorBuffer>? = null
    private var outputBuffers: List<TensorBuffer>? = null
    private var activeBackend: AiHardwareBackend = AiHardwareBackend.UNINITIALIZED

    override fun probeCapabilities(): AiHardwareCapabilities {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode ?: false
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: 0
        } else {
            0
        }
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOfNotNull(
                Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN },
                Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            ).joinToString(" ").ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

        val npuProvider = runCatching { BuiltinNpuAcceleratorProvider(context) }.getOrNull()
        val npuSupported = runCatching { npuProvider?.isDeviceSupported() == true }.getOrDefault(false)
        val npuReady = runCatching { npuProvider?.isLibraryReady() == true }.getOrDefault(false)

        val accelerators = LinkedHashSet<AiHardwareBackend>()
        runCatching {
            val env = if (npuProvider != null) {
                Environment.create(context, npuProvider)
            } else {
                Environment.create(context)
            }
            try {
                env.getAvailableAccelerators().forEach { acc ->
                    when (acc) {
                        Accelerator.NPU -> accelerators += AiHardwareBackend.NPU
                        Accelerator.GPU -> accelerators += AiHardwareBackend.GPU
                        Accelerator.CPU -> accelerators += AiHardwareBackend.CPU
                        Accelerator.NONE -> Unit
                    }
                }
            } finally {
                env.close()
            }
        }
        if (AiHardwareBackend.CPU !in accelerators) {
            accelerators += AiHardwareBackend.CPU
        }

        return AiHardwareCapabilities(
            socDescription = soc,
            npuDeviceSupported = npuSupported,
            npuRuntimeReady = npuReady,
            availableAccelerators = accelerators,
            isPowerSaveMode = isPowerSave,
            thermalStatus = thermal
        )
    }

    override fun initialize(targetBackend: AiHardwareBackend): Result<AiHardwareBackend> = runCatching {
        if (compiledModel != null && activeBackend == targetBackend) {
            return@runCatching activeBackend
        }
        close()

        val npuProvider = runCatching { BuiltinNpuAcceleratorProvider(context) }.getOrNull()
        val env = if (npuProvider != null) {
            Environment.create(context, npuProvider)
        } else {
            Environment.create(context)
        }
        val accel = when (targetBackend) {
            AiHardwareBackend.NPU -> Accelerator.NPU
            AiHardwareBackend.GPU -> Accelerator.GPU
            AiHardwareBackend.CPU,
            AiHardwareBackend.NATIVE_MLKIT,
            AiHardwareBackend.DIRECT_CPU_LEXICAL,
            AiHardwareBackend.UNINITIALIZED -> Accelerator.CPU
        }

        val model = CompiledModel.create(
            context.assets,
            modelAssetPath,
            CompiledModel.Options(accel),
            env
        )
        val inBufs = model.createInputBuffers()
        val outBufs = model.createOutputBuffers()
        // Run sanity check before confirming accelerator active
        inBufs[0].writeFloat(FloatArray(TextFeatureVectorizer.INPUT_DIMS))
        model.run(inBufs, outBufs)
        val probeOut = outBufs[0].readFloat()
        check(probeOut.size == TextFeatureVectorizer.OUTPUT_DIMS && probeOut.all { it.isFinite() }) {
            "Sanity inference returned invalid tensor shape or NaN"
        }

        environment = env
        compiledModel = model
        inputBuffers = inBufs
        outputBuffers = outBufs
        activeBackend = targetBackend
        targetBackend
    }.onFailure {
        close()
    }

    override fun runProjection(input128: FloatArray): Result<FloatArray> = runCatching {
        val model = checkNotNull(compiledModel) { "CompiledModel is not initialized" }
        val inBufs = checkNotNull(inputBuffers)
        val outBufs = checkNotNull(outputBuffers)
        inBufs[0].writeFloat(input128)
        model.run(inBufs, outBufs)
        outBufs[0].readFloat()
    }

    override fun close() {
        runCatching { inputBuffers?.forEach { it.close() } }
        runCatching { outputBuffers?.forEach { it.close() } }
        runCatching { compiledModel?.close() }
        runCatching { environment?.close() }
        inputBuffers = null
        outputBuffers = null
        compiledModel = null
        environment = null
        activeBackend = AiHardwareBackend.UNINITIALIZED
    }
}

class SmartNoteAiEngine(
    private val database: SmartNoteDatabase,
    private val settingsRepository: SettingsRepository,
    private val runtimeBridge: LiteRtRuntimeBridge,
    private val applicationScope: CoroutineScope,
    private val idleUnloadMs: Long = 60_000L
) {
    private val aiDispatcher: ExecutorCoroutineDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, "SmartNote-LiteRT-Worker").apply { isDaemon = true }
        }
        .asCoroutineDispatcher()

    private val _diagnostics = MutableStateFlow(AiDiagnosticsState())
    val diagnosticsFlow: StateFlow<AiDiagnosticsState> = _diagnostics.asStateFlow()

    private var idleUnloadJob: Job? = null

    suspend fun refreshDiagnostics(): AiDiagnosticsState {
        val settings = settingsRepository.settingsFlow.first()
        return withContext(aiDispatcher) {
            val caps = runtimeBridge.probeCapabilities()
            val cur = _diagnostics.value
            val updated = cur.copy(
                requestedPreference = settings.aiBackendPreference,
                socDescription = caps.socDescription,
                npuDeviceSupported = caps.npuDeviceSupported,
                npuRuntimeReady = caps.npuRuntimeReady,
                detectedAccelerators = caps.availableAccelerators,
                energySavingActive = settings.energyEfficientAi &&
                    (caps.isPowerSaveMode || caps.thermalStatus >= 3)
            )
            _diagnostics.value = updated
            updated
        }
    }

    suspend fun embedText(text: String): FloatArray {
        val features = TextFeatureVectorizer.vectorizeInput(text)
        val settings = settingsRepository.settingsFlow.first()
        return withContext(aiDispatcher) {
            val startNs = System.nanoTime()
            val caps = runtimeBridge.probeCapabilities()
            val energyThrottled = settings.energyEfficientAi &&
                (caps.isPowerSaveMode || caps.thermalStatus >= 3)

            val candidateOrder = buildAttemptOrder(
                preference = settings.aiBackendPreference,
                caps = caps,
                energyThrottled = energyThrottled
            )
            val fallbackNotes = mutableListOf<String>()

            if (settings.aiBackendPreference == AiBackendPreference.PREFER_NPU &&
                (!caps.npuDeviceSupported || !caps.npuRuntimeReady || AiHardwareBackend.NPU !in caps.availableAccelerators)
            ) {
                fallbackNotes += "NPU runtime unavailable on ${caps.socDescription}; falling back"
            }
            if (energyThrottled && settings.aiBackendPreference != AiBackendPreference.CPU_ONLY) {
                fallbackNotes += "Energy-efficient throttling active (power-save or thermal); using CPU"
            }

            var outputVector: FloatArray? = null
            var chosenBackend = AiHardwareBackend.UNINITIALIZED

            for (backend in candidateOrder) {
                val initResult = runtimeBridge.initialize(backend)
                if (initResult.isSuccess) {
                    val runResult = runtimeBridge.runProjection(features)
                    if (runResult.isSuccess) {
                        outputVector = runResult.getOrThrow()
                        chosenBackend = backend
                        break
                    } else {
                        fallbackNotes += "${backend.name} execution failed: ${runResult.exceptionOrNull()?.message}"
                        runtimeBridge.close()
                    }
                } else {
                    fallbackNotes += "${backend.name} init unavailable: ${initResult.exceptionOrNull()?.message}"
                }
            }

            if (outputVector == null) {
                // Deterministic CPU mathematical fallback (matches projection dimensionality cleanly)
                chosenBackend = AiHardwareBackend.CPU
                if (fallbackNotes.isEmpty()) {
                    fallbackNotes += "Using deterministic CPU projection"
                }
                outputVector = deterministicCpuFallbackProjection(features)
            }

            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            _diagnostics.value = AiDiagnosticsState(
                requestedPreference = settings.aiBackendPreference,
                activeNeuralBackend = chosenBackend,
                ocrBackend = AiHardwareBackend.NATIVE_MLKIT,
                lexicalSearchBackend = AiHardwareBackend.DIRECT_CPU_LEXICAL,
                socDescription = caps.socDescription,
                npuDeviceSupported = caps.npuDeviceSupported,
                npuRuntimeReady = caps.npuRuntimeReady,
                detectedAccelerators = caps.availableAccelerators,
                fallbackReason = fallbackNotes.takeIf { it.isNotEmpty() }?.joinToString("; "),
                isModelLoaded = chosenBackend != AiHardwareBackend.UNINITIALIZED,
                lastInferenceMs = elapsedMs,
                energySavingActive = energyThrottled
            )

            scheduleIdleUnload()
            outputVector
        }
    }

    suspend fun updateNoteEmbeddingIfNeeded(noteId: String, contentSha256: String, text: String) {
        if (text.isBlank()) return
        val existing = database.searchAndMaintenanceDao().getEmbedding(noteId)
        if (existing != null && existing.contentSha256 == contentSha256 && existing.modelId == MODEL_ID) {
            return
        }
        val vec = embedText(text.take(4000))
        database.searchAndMaintenanceDao().upsertEmbedding(
            NoteEmbeddingEntity(
                noteId = noteId,
                modelId = MODEL_ID,
                contentSha256 = contentSha256,
                dims = vec.size,
                vectorBlob = TextFeatureVectorizer.toByteArray(vec),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun scoreSemanticSimilarity(query: String, candidateNoteIds: List<String>): Map<String, Float> {
        if (query.isBlank() || candidateNoteIds.isEmpty()) return emptyMap()
        val queryVec = embedText(query)
        val embeddings = database.searchAndMaintenanceDao().getEmbeddings(candidateNoteIds)
        return embeddings.associate { entity ->
            val noteVec = TextFeatureVectorizer.fromByteArray(entity.vectorBlob)
            entity.noteId to TextFeatureVectorizer.cosineSimilarity(queryVec, noteVec).coerceAtLeast(0f)
        }
    }

    suspend fun unloadModel() {
        idleUnloadJob?.cancel()
        withContext(aiDispatcher) {
            runtimeBridge.close()
            _diagnostics.value = _diagnostics.value.copy(isModelLoaded = false)
        }
    }

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        if (idleUnloadMs <= 0L) return
        idleUnloadJob = applicationScope.launch(aiDispatcher) {
            delay(idleUnloadMs)
            runtimeBridge.close()
            _diagnostics.value = _diagnostics.value.copy(isModelLoaded = false)
        }
    }

    internal fun buildAttemptOrder(
        preference: AiBackendPreference,
        caps: AiHardwareCapabilities,
        energyThrottled: Boolean
    ): List<AiHardwareBackend> {
        if (preference == AiBackendPreference.CPU_ONLY || energyThrottled) {
            return listOf(AiHardwareBackend.CPU)
        }
        val order = LinkedHashSet<AiHardwareBackend>()
        val canTryNpu = caps.npuDeviceSupported &&
            caps.npuRuntimeReady &&
            AiHardwareBackend.NPU in caps.availableAccelerators
        val canTryGpu = AiHardwareBackend.GPU in caps.availableAccelerators

        when (preference) {
            AiBackendPreference.AUTO -> {
                if (canTryNpu) order += AiHardwareBackend.NPU
                if (canTryGpu) order += AiHardwareBackend.GPU
                order += AiHardwareBackend.CPU
            }
            AiBackendPreference.PREFER_NPU -> {
                if (canTryNpu) order += AiHardwareBackend.NPU
                if (canTryGpu) order += AiHardwareBackend.GPU
                order += AiHardwareBackend.CPU
            }
            AiBackendPreference.PREFER_GPU -> {
                if (canTryGpu) order += AiHardwareBackend.GPU
                if (canTryNpu) order += AiHardwareBackend.NPU
                order += AiHardwareBackend.CPU
            }
            AiBackendPreference.CPU_ONLY -> order += AiHardwareBackend.CPU
        }
        return order.toList()
    }

    private fun deterministicCpuFallbackProjection(features: FloatArray): FloatArray {
        val out = FloatArray(TextFeatureVectorizer.OUTPUT_DIMS)
        for (i in out.indices) {
            // Fold 128 hashed features into 64 dimensions with deterministic sign mixing
            val a = features[i]
            val b = features[i + TextFeatureVectorizer.OUTPUT_DIMS]
            out[i] = a + (if ((i and 1) == 0) b else -b)
        }
        return TextFeatureVectorizer.l2Normalize(out)
    }

    companion object {
        const val MODEL_ID = "smartnote_text_embed_v1"
    }
}
