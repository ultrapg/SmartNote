package io.github.ultrapg.smartnote.links

import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LinkMetadataResult(
    val pageTitle: String?,
    val description: String?,
    val succeeded: Boolean,
    val blockedByPrivacySetting: Boolean = false
)

class LinkMetadataFetcher(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun fetchMetadataIfAllowed(rawUrl: String): LinkMetadataResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.fetchLinkPreviews) {
            return@withContext LinkMetadataResult(
                pageTitle = null,
                description = null,
                succeeded = false,
                blockedByPrivacySetting = true
            )
        }
        val parsed = SmartLinkDetector.parseSingleUrl(rawUrl)
            ?: return@withContext LinkMetadataResult(null, null, succeeded = false)

        if (!isPublicHttpTarget(parsed.canonicalUrl)) {
            return@withContext LinkMetadataResult(null, null, succeeded = false)
        }

        runCatching {
            val request = Request.Builder()
                .url(parsed.canonicalUrl)
                .header("User-Agent", "SmartNote/1.0 (LinkPreview; +offline-first)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use LinkMetadataResult(null, null, succeeded = false)
                }
                val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                if (!contentType.contains("text/html") && !contentType.contains("application/xhtml")) {
                    return@use LinkMetadataResult(null, null, succeeded = false)
                }
                val body = response.body ?: return@use LinkMetadataResult(null, null, succeeded = false)
                val limitedBytes = body.source().peek().run {
                    request(MAX_HTML_BYTES)
                    readByteArray(minOf(buffer.size, MAX_HTML_BYTES))
                }
                val html = limitedBytes.toString(Charsets.UTF_8)
                val title = extractMetaContent(html, "og:title")
                    ?: extractMetaContent(html, "twitter:title")
                    ?: extractTitleTag(html)
                val desc = extractMetaContent(html, "og:description")
                    ?: extractMetaContent(html, "description")
                LinkMetadataResult(
                    pageTitle = title?.take(180),
                    description = desc?.take(320),
                    succeeded = title != null || desc != null
                )
            }
        }.getOrElse {
            LinkMetadataResult(null, null, succeeded = false)
        }
    }

    companion object {
        private const val MAX_HTML_BYTES = 128L * 1024L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        internal fun isPublicHttpTarget(url: String): Boolean = runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return false
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            if (host == "localhost" || host.endsWith(".local")) return false
            val addresses = InetAddress.getAllByName(host)
            addresses.all { addr ->
                !addr.isAnyLocalAddress &&
                    !addr.isLoopbackAddress &&
                    !addr.isLinkLocalAddress &&
                    !addr.isSiteLocalAddress &&
                    !addr.isMulticastAddress
            }
        }.getOrDefault(false)

        private val titleTagRegex = Regex("(?is)<title[^>]*>(.*?)</title>")

        internal fun extractTitleTag(html: String): String? {
            val raw = titleTagRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
            return decodeHtmlEntities(raw).trim().takeIf { it.isNotBlank() }
        }

        internal fun extractMetaContent(html: String, key: String): String? {
            val escaped = Regex.escape(key)
            val pattern1 = Regex(
                """(?is)<meta[^>]+(?:property|name)\s*=\s*["']$escaped["'][^>]+content\s*=\s*["'](.*?)["'][^>]*>"""
            )
            val pattern2 = Regex(
                """(?is)<meta[^>]+content\s*=\s*["'](.*?)["'][^>]+(?:property|name)\s*=\s*["']$escaped["'][^>]*>"""
            )
            val raw = pattern1.find(html)?.groupValues?.getOrNull(1)
                ?: pattern2.find(html)?.groupValues?.getOrNull(1)
                ?: return null
            return decodeHtmlEntities(raw).trim().takeIf { it.isNotBlank() }
        }

        private fun decodeHtmlEntities(input: String): String {
            return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace(Regex("\\s+"), " ")
        }
    }
}
