package io.github.ultrapg.smartnote.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.GeminiNanoCapabilities
import io.github.ultrapg.smartnote.ai.MlKitGeminiNanoAssistant
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.links.LinkMetadataFetcher
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class OcrAndLinkPrivacyTest {

    @Test
    fun safeOcrBitmapLoaderTilesTallScrollingScreenshotsAndDeduplicatesBoundaries() {
        val normalRects = SafeOcrBitmapLoader.computeBandRects(1080, 2400)
        assertEquals(1, normalRects.size)

        val tallRects = SafeOcrBitmapLoader.computeBandRects(1080, 7600)
        assertTrue(tallRects.size >= 3)
        assertEquals(0, tallRects.first().top)
        assertEquals(7600, tallRects.last().bottom)

        val merged = SafeOcrBitmapLoader.mergeTiledOcrTexts(
            listOf(
                "Line 1\nLine 2\nOverlap A\nOverlap B",
                "Overlap A\nOverlap B\nLine 5\nLine 6"
            )
        )
        assertEquals("Line 1\nLine 2\nOverlap A\nOverlap B\nLine 5\nLine 6", merged)

        val largePng = File.createTempFile("ocr_large_", ".png")
        try {
            val largeBmp = Bitmap.createBitmap(4200, 3000, Bitmap.Config.ARGB_8888)
            FileOutputStream(largePng).use { largeBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            largeBmp.recycle()

            val decoded = SafeOcrBitmapLoader.decodeDownsampledBitmap(largePng)
            assertTrue(decoded != null)
            assertTrue(decoded!!.width <= SafeOcrBitmapLoader.MAX_STANDARD_DIM_PX)
            assertTrue(decoded.height <= SafeOcrBitmapLoader.MAX_STANDARD_DIM_PX)
            decoded.recycle()
        } finally {
            largePng.delete()
        }
    }

    @Test
    fun linkMetadataFetcherBlocksNetworkWhenPrivacyToggleOffAndBlocksSsrfHosts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = File(context.cacheDir, "link_privacy_${System.nanoTime()}.preferences_pb")
        val settingsRepo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { dsFile })
        )
        var networkCalled = false
        val failingClient = OkHttpClient.Builder()
            .addInterceptor {
                networkCalled = true
                throw AssertionError("Network must never be invoked when fetchLinkPreviews is OFF")
            }
            .build()

        val fetcher = LinkMetadataFetcher(settingsRepo, failingClient)
        val res = fetcher.fetchMetadataIfAllowed("https://example.com/article")
        assertTrue(res.blockedByPrivacySetting)
        assertFalse(res.succeeded)
        assertFalse(networkCalled)

        // Verify SSRF private/loopback IP blocking
        assertFalse(LinkMetadataFetcher.isPublicHttpTarget("http://127.0.0.1:8080/secret"))
        assertFalse(LinkMetadataFetcher.isPublicHttpTarget("http://192.168.1.1/admin"))
        assertFalse(LinkMetadataFetcher.isPublicHttpTarget("http://10.0.0.1/internal"))
        assertFalse(LinkMetadataFetcher.isPublicHttpTarget("http://localhost/health"))

        // Verify HTML title/meta extraction
        val html = """
            <html><head>
              <meta property="og:title" content="SmartNote &amp; Compose Guide" />
              <meta name="description" content="Offline first notes." />
            </head></html>
        """.trimIndent()
        assertEquals("SmartNote & Compose Guide", LinkMetadataFetcher.extractMetaContent(html, "og:title"))
        assertEquals("Offline first notes.", LinkMetadataFetcher.extractMetaContent(html, "description"))

        scope.cancel()
        dsFile.delete()
    }

    @Test
    fun ocrPostProcessorFormatsLayoutRemovesArtifactsAndCoordinatesGeminiNanoAssist() = runTest {
        // 1. Spatial reading order, status-bar artifact stripping, column spacing, and de-hyphenation
        val layoutLines = listOf(
            OcrLayoutLine("10:15", left = 40, top = 12, right = 130, bottom = 42),
            OcrLayoutLine("87%", left = 960, top = 12, right = 1040, bottom = 42),
            OcrLayoutLine("$14.50", left = 720, top = 220, right = 900, bottom = 260),
            OcrLayoutLine("Coffee Beans", left = 80, top = 222, right = 340, bottom = 262),
            OcrLayoutLine("Super-", left = 80, top = 360, right = 230, bottom = 400),
            OcrLayoutLine("market Receipt", left = 80, top = 408, right = 420, bottom = 448)
        )
        val formatted = OcrTextPostProcessor.orderAndFormatLines(layoutLines, imageHeightPx = 2000)
        assertFalse(formatted.contains("10:15"))
        assertFalse(formatted.contains("87%"))
        assertTrue(formatted.contains("Coffee Beans  $14.50"))
        assertTrue(formatted.contains("Supermarket Receipt"))

        // Small single-line/two-line images must NOT strip clock/percent text
        val smallReceipt = OcrTextPostProcessor.orderAndFormatLines(
            listOf(
                OcrLayoutLine("10:15", left = 10, top = 5, right = 80, bottom = 30),
                OcrLayoutLine("Total", left = 10, top = 45, right = 90, bottom = 70)
            ),
            imageHeightPx = 1000
        )
        assertTrue(smallReceipt.contains("10:15"))

        // Noise estimator separates clean text from garbled symbol bursts
        assertTrue(OcrTextPostProcessor.estimateTextNoiseScore("Invoice INV-2026 Total EUR 42.00") < 0.10f)
        assertTrue(OcrTextPostProcessor.estimateTextNoiseScore("x q z ~ ^ | \\ a b c m") > 0.30f)

        // Fuzzy boundary dedup across sliced tall screenshots
        val fuzzyMerged = SafeOcrBitmapLoader.mergeTiledOcrTexts(
            listOf(
                "Header\nOverlap   Line",
                "overlap line\nBottom"
            )
        )
        assertEquals("Header\nOverlap   Line\nBottom", fuzzyMerged)

        // 2. Gemini Nano OCR assistant sanity guards
        assertNull(
            MlKitGeminiNanoAssistant.Parsers.sanitizeOcrAssistantOutput(
                "The image shows a supermarket receipt on a wooden table.",
                baselineOcr = ""
            )
        )
        assertNull(
            MlKitGeminiNanoAssistant.Parsers.sanitizeOcrAssistantOutput(
                "NONE",
                baselineOcr = ""
            )
        )
        assertEquals(
            "Invoice #4821\nTotal: 49.90 EUR",
            MlKitGeminiNanoAssistant.Parsers.sanitizeOcrAssistantOutput(
                "Extracted text:\n```\nInvoice #4821\nTotal: 49.90 EUR\n```",
                baselineOcr = "Invoic #4821 Total 49.90"
            )
        )

        // 3. SmartOcrCoordinator routing & fallback
        val sampleFile = File.createTempFile("ocr_coord_", ".png")
        try {
            val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
            FileOutputStream(sampleFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            val weakBaseEngine = object : OcrEngine {
                override suspend fun recognizeFile(file: File) = OcrOutcome(
                    text = "Invo1ce ~ 49",
                    confidence = 0.58f,
                    succeeded = true
                )
            }
            var assistCalls = 0
            val readyNano = object : OnDeviceGenAiAssistant {
                override suspend fun checkAvailability() = GeminiNanoAvailability.AVAILABLE
                override suspend fun probeCapabilities(forceRefresh: Boolean) = GeminiNanoCapabilities(
                    fastAvailability = GeminiNanoAvailability.AVAILABLE,
                    fullAvailability = GeminiNanoAvailability.AVAILABLE
                )
                override fun triggerBackgroundDownloadIfNeeded() {}
                override suspend fun generateTitleCandidates(
                    noteText: String,
                    ocrTexts: List<String>,
                    imageLabels: List<String>,
                    linkHints: List<String>,
                    primaryImageBitmap: Bitmap?
                ) = emptyList<String>()
                override suspend fun generateTags(
                    title: String,
                    noteText: String,
                    ocrTexts: List<String>,
                    imageLabels: List<String>,
                    existingVocabulary: List<String>,
                    existingNoteTags: Set<String>
                ) = emptyList<SmartTagSuggestion>()
                override suspend fun enhanceOrExtractOcrText(
                    imageBitmap: Bitmap?,
                    rawOcrText: String,
                    rawOcrConfidence: Float,
                    preferFast: Boolean
                ): String {
                    assistCalls++
                    assertTrue(imageBitmap != null)
                    return "Invoice #49"
                }
                override fun close() {}
            }

            val coordinator = SmartOcrCoordinator(weakBaseEngine, readyNano)
            val enhancedOutcome = coordinator.recognizeFileWithOptionalAi(
                file = sampleFile,
                settings = AppSettings(geminiNanoMode = GeminiNanoMode.AUTO_HYBRID, useGeminiNanoForOcr = true)
            )
            assertEquals(1, assistCalls)
            assertTrue(enhancedOutcome.usedGeminiNano)
            assertEquals("Invoice #49", enhancedOutcome.text)

            // Disabled via useGeminiNanoForOcr = false falls back cleanly to base ML Kit result
            val localOnlyOutcome = coordinator.recognizeFileWithOptionalAi(
                file = sampleFile,
                settings = AppSettings(geminiNanoMode = GeminiNanoMode.AUTO_HYBRID, useGeminiNanoForOcr = false)
            )
            assertEquals(1, assistCalls)
            assertFalse(localOnlyOutcome.usedGeminiNano)
            assertEquals("Invo1ce ~ 49", localOnlyOutcome.text)

            // forceAiAssist runs assist when user explicitly taps Re-scan even if high-confidence
            val confidentEngine = object : OcrEngine {
                override suspend fun recognizeFile(file: File) = OcrOutcome(
                    text = "Long clear document text that exceeds twenty eight characters cleanly",
                    confidence = 0.96f,
                    succeeded = true
                )
            }
            val confidentCoord = SmartOcrCoordinator(confidentEngine, readyNano)
            val autoSkip = confidentCoord.recognizeFileWithOptionalAi(
                file = sampleFile,
                settings = AppSettings(geminiNanoMode = GeminiNanoMode.AUTO_HYBRID, useGeminiNanoForOcr = true),
                forceAiAssist = false
            )
            assertFalse(autoSkip.usedGeminiNano)
            assertEquals(1, assistCalls)

            val manualForce = confidentCoord.recognizeFileWithOptionalAi(
                file = sampleFile,
                settings = AppSettings(geminiNanoMode = GeminiNanoMode.AUTO_HYBRID, useGeminiNanoForOcr = true),
                forceAiAssist = true
            )
            assertTrue(manualForce.usedGeminiNano)
            assertEquals(2, assistCalls)
        } finally {
            sampleFile.delete()
        }
    }
}
