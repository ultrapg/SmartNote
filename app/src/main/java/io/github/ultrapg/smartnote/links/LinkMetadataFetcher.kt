package io.github.ultrapg.smartnote.links

import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.domain.util.TextHashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LinkMetadataResult(
    val pageTitle: String?,
    val description: String?,
    val succeeded: Boolean,
    val blockedByPrivacySetting: Boolean = false,
    val siteName: String? = null,
    val imageUrl: String? = null,
    val localThumbnailPath: String? = null
)

class LinkMetadataFetcher(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient = defaultClient(),
    private val attachmentStorage: AttachmentStorage? = null,
    private val targetValidator: (String) -> Boolean = ::isPublicHttpTarget
) {
    private data class FetchedPayload(
        val finalUrl: String,
        val contentType: String,
        val charset: Charset?,
        val bytes: ByteArray
    )

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

        if (!targetValidator(parsed.canonicalUrl)) {
            return@withContext LinkMetadataResult(null, null, succeeded = false)
        }

        val htmlPayload = fetchWithSafeRedirects(
            startUrl = parsed.canonicalUrl,
            acceptHeader = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
            maxBytes = MAX_HTML_BYTES
        ) ?: return@withContext LinkMetadataResult(null, null, succeeded = false)

        val contentTypeLower = htmlPayload.contentType.lowercase(Locale.ROOT)
        if (!contentTypeLower.contains("text/html") && !contentTypeLower.contains("application/xhtml")) {
            return@withContext LinkMetadataResult(null, null, succeeded = false)
        }

        val html = decodeHtmlBytes(htmlPayload.bytes, htmlPayload.charset)
        val title = extractMetaContent(html, "og:title")
            ?: extractMetaContent(html, "twitter:title")
            ?: extractTitleTag(html)
        val desc = extractMetaContent(html, "og:description")
            ?: extractMetaContent(html, "twitter:description")
            ?: extractMetaContent(html, "description")
        val siteName = extractMetaContent(html, "og:site_name")
            ?: extractMetaContent(html, "application-name")
        val rawImage = extractMetaContent(html, "og:image:secure_url")
            ?: extractMetaContent(html, "og:image")
            ?: extractMetaContent(html, "twitter:image")
            ?: extractMetaContent(html, "twitter:image:src")
        val resolvedImageUrl = resolvePreviewImageUrl(htmlPayload.finalUrl, rawImage)
            ?.takeIf { targetValidator(it) }

        val storage = attachmentStorage
        val localThumb = if (storage != null && resolvedImageUrl != null) {
            fetchThumbnailSafely(resolvedImageUrl, parsed.canonicalUrl, storage)
        } else {
            null
        }

        LinkMetadataResult(
            pageTitle = title?.take(180),
            description = desc?.take(320),
            succeeded = title != null || desc != null || localThumb != null,
            siteName = siteName?.take(80),
            imageUrl = resolvedImageUrl,
            localThumbnailPath = localThumb
        )
    }

    private fun fetchThumbnailSafely(
        imageUrl: String,
        canonicalPageUrl: String,
        storage: AttachmentStorage
    ): String? {
        val imagePayload = fetchWithSafeRedirects(
            startUrl = imageUrl,
            acceptHeader = "image/webp,image/png,image/jpeg,image/*;q=0.8",
            maxBytes = MAX_IMAGE_BYTES
        ) ?: return null
        val typeLower = imagePayload.contentType.lowercase(Locale.ROOT)
        if (!typeLower.startsWith("image/") || typeLower.contains("svg")) return null
        val urlKey = TextHashing.sha256Hex(canonicalPageUrl).take(16)
        return storage.saveLinkPreviewThumbnail(imagePayload.bytes, urlKey)
    }

    private fun fetchWithSafeRedirects(
        startUrl: String,
        acceptHeader: String,
        maxBytes: Long
    ): FetchedPayload? = runCatching {
        var currentUrl = startUrl
        for (hop in 0..MAX_REDIRECTS) {
            if (!targetValidator(currentUrl)) return@runCatching null
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", acceptHeader)
                .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (hop >= MAX_REDIRECTS) return@runCatching null
                    val location = response.header("Location")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@runCatching null
                    val resolvedNext = runCatching { URI(currentUrl).resolve(location).toString() }.getOrNull()
                        ?: return@runCatching null
                    currentUrl = resolvedNext
                } else {
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body ?: return@runCatching null
                    val mediaType = body.contentType()
                    val contentTypeHeader = response.header("Content-Type")
                        ?: mediaType?.toString()
                        .orEmpty()
                    val source = body.source()
                    source.request(maxBytes)
                    val bytes = source.readByteArray(minOf(source.buffer.size, maxBytes))
                    return@runCatching FetchedPayload(
                        finalUrl = response.request.url.toString(),
                        contentType = contentTypeHeader,
                        charset = mediaType?.charset(),
                        bytes = bytes
                    )
                }
            }
        }
        null
    }.getOrNull()

    companion object {
        private const val MAX_HTML_BYTES = 256L * 1024L
        private const val MAX_IMAGE_BYTES = 512L * 1024L
        private const val MAX_REDIRECTS = 4
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) SmartNote/1.0"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
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

        internal fun resolvePreviewImageUrl(pageUrl: String, rawImageUrl: String?): String? {
            val clean = rawImageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val lower = clean.lowercase(Locale.ROOT)
            if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("blob:")) {
                return null
            }
            return runCatching {
                val resolved = URI(pageUrl).resolve(clean).toString()
                val scheme = URI(resolved).scheme?.lowercase(Locale.ROOT)
                resolved.takeIf { scheme == "http" || scheme == "https" }
            }.getOrNull()
        }

        internal fun decodeHtmlBytes(bytes: ByteArray, declaredCharset: Charset?): String {
            if (declaredCharset != null) {
                return bytes.toString(declaredCharset)
            }
            val headAscii = bytes.copyOf(minOf(bytes.size, 4096)).toString(Charsets.ISO_8859_1)
            val metaCharsetName = metaCharsetRegex.find(headAscii)?.groupValues?.getOrNull(1)?.trim()
            val resolvedCharset = metaCharsetName?.let { name ->
                runCatching { Charset.forName(name) }.getOrNull()
            } ?: Charsets.UTF_8
            return bytes.toString(resolvedCharset)
        }

        private val metaCharsetRegex = Regex(
            """(?is)<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9._-]+)"""
        )
        private val titleTagRegex = Regex("(?is)<title[^>]*>(.*?)</title>")
        private val decimalEntityRegex = Regex("&#(\\d{2,7});")
        private val hexEntityRegex = Regex("&#[xX]([0-9a-fA-F]{2,6});")

        private val namedEntities = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&#39;" to "'",
            "&nbsp;" to " ",
            "&ndash;" to "–",
            "&mdash;" to "—",
            "&lsquo;" to "‘",
            "&rsquo;" to "’",
            "&ldquo;" to "“",
            "&rdquo;" to "”",
            "&hellip;" to "…",
            "&copy;" to "©",
            "&reg;" to "®",
            "&trade;" to "™",
            "&euro;" to "€",
            "&auml;" to "ä",
            "&ouml;" to "ö",
            "&uuml;" to "ü",
            "&Auml;" to "Ä",
            "&Ouml;" to "Ö",
            "&Uuml;" to "Ü",
            "&szlig;" to "ß"
        )

        internal fun extractTitleTag(html: String): String? {
            val raw = titleTagRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
            return decodeHtmlEntities(raw).takeIf { it.isNotBlank() }
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
            return decodeHtmlEntities(raw).takeIf { it.isNotBlank() }
        }

        internal fun decodeHtmlEntities(input: String): String {
            var decoded = input
            for ((entity, replacement) in namedEntities) {
                if (entity in decoded) {
                    decoded = decoded.replace(entity, replacement)
                }
            }
            decoded = decimalEntityRegex.replace(decoded) { match ->
                val codePoint = match.groupValues[1].toIntOrNull()
                if (codePoint != null && codePoint in 0x20..0x10FFFF && Character.isValidCodePoint(codePoint)) {
                    String(Character.toChars(codePoint))
                } else {
                    match.value
                }
            }
            decoded = hexEntityRegex.replace(decoded) { match ->
                val codePoint = match.groupValues[1].toIntOrNull(16)
                if (codePoint != null && codePoint in 0x20..0x10FFFF && Character.isValidCodePoint(codePoint)) {
                    String(Character.toChars(codePoint))
                } else {
                    match.value
                }
            }
            return decoded.replace(Regex("\\s+"), " ").trim()
        }
    }
}
