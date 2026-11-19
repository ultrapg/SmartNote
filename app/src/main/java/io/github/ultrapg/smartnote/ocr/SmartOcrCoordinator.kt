package io.github.ultrapg.smartnote.ocr

import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import java.io.File

/**
 * Coordinates baseline ML Kit text recognition with optional Gemini Nano multimodal
 * transcription and OCR cleanup when local extraction is empty, noisy, or low-confidence.
 */
class SmartOcrCoordinator(
    private val ocrEngine: OcrEngine,
    private val geminiNanoAssistant: OnDeviceGenAiAssistant? = null
) {
    suspend fun recognizeFileWithOptionalAi(
        file: File,
        settings: AppSettings,
        forceAiAssist: Boolean = false
    ): OcrOutcome {
        val base = ocrEngine.recognizeFile(file)
        val assistant = geminiNanoAssistant ?: return base

        val nanoAllowed = settings.effectiveUseGeminiNanoForOcr ||
            (forceAiAssist && settings.effectiveGeminiNanoEnabled && settings.effectiveOcrEnabled)
        if (!nanoAllowed) return base

        val caps = assistant.probeCapabilities(forceRefresh = false)
        val nanoReady = caps.fastAvailability == GeminiNanoAvailability.AVAILABLE ||
            caps.fullAvailability == GeminiNanoAvailability.AVAILABLE
        if (!nanoReady) return base

        val cleanBase = base.text.trim()
        val noise = OcrTextPostProcessor.estimateTextNoiseScore(cleanBase)
        val shouldAssist = forceAiAssist ||
            settings.geminiNanoMode == GeminiNanoMode.ALWAYS_PREFER ||
            !base.succeeded ||
            cleanBase.length < 28 ||
            base.confidence < 0.82f ||
            noise >= 0.14f
        if (!shouldAssist) return base

        val preferFast = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST
        val uprightBitmap = SafeOcrBitmapLoader.decodeUprightDownsampledBitmap(
            file = file,
            maxDimPx = GEMINI_OCR_MAX_DIM_PX
        )
        val enhanced = try {
            assistant.enhanceOrExtractOcrText(
                imageBitmap = uprightBitmap,
                rawOcrText = cleanBase,
                rawOcrConfidence = base.confidence,
                preferFast = preferFast
            )?.trim()
        } finally {
            if (uprightBitmap != null && !uprightBitmap.isRecycled) {
                uprightBitmap.recycle()
            }
        }

        return if (!enhanced.isNullOrBlank()) {
            OcrOutcome(
                text = enhanced,
                confidence = maxOf(base.confidence, 0.88f),
                succeeded = true,
                usedGeminiNano = true
            )
        } else {
            base
        }
    }

    private companion object {
        const val GEMINI_OCR_MAX_DIM_PX = 1024
    }
}
