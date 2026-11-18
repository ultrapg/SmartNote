package io.github.ultrapg.smartnote.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.links.LinkMetadataFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
