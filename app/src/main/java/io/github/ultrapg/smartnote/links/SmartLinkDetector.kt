package io.github.ultrapg.smartnote.links

import java.net.URI
import java.util.Locale

data class DetectedLink(
    val rawUrl: String,
    val canonicalUrl: String,
    val domain: String
)

object SmartLinkDetector {

    private val urlRegex = Regex(
        """https?://[^\s<>"'\]\)]+""",
        setOf(RegexOption.IGNORE_CASE)
    )

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "mc_cid", "mc_eid", "ref"
    )

    fun extractLinks(text: String): List<DetectedLink> {
        if (text.isBlank()) return emptyList()
        val seenCanonical = LinkedHashSet<String>()
        val results = mutableListOf<DetectedLink>()
        for (match in urlRegex.findAll(text)) {
            val cleaned = cleanTrailingPunctuation(match.value)
            val parsed = parseSafe(cleaned) ?: continue
            if (seenCanonical.add(parsed.canonicalUrl)) {
                results += parsed
            }
        }
        return results
    }

    fun parseSingleUrl(raw: String): DetectedLink? {
        val trimmed = cleanTrailingPunctuation(raw.trim())
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return parseSafe(trimmed)
    }

    private fun cleanTrailingPunctuation(url: String): String {
        var current = url
        while (current.isNotEmpty() && current.last() in charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')) {
            current = current.dropLast(1)
        }
        return current
    }

    private fun parseSafe(url: String): DetectedLink? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
        if (host.isBlank() || '.' !in host && host != "localhost") return null

        val path = (uri.rawPath ?: "").trimEnd('/')
        val query = uri.rawQuery
            ?.split('&')
            ?.filter { param ->
                val key = param.substringBefore('=').lowercase(Locale.ROOT)
                key.isNotBlank() && key !in trackingParams
            }
            ?.sorted()
            ?.joinToString("&")
            ?.takeIf { it.isNotBlank() }

        val portSuffix = if (uri.port != -1 &&
            !((scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443))
        ) {
            ":${uri.port}"
        } else {
            ""
        }

        val canonical = buildString {
            append(scheme).append("://").append(host).append(portSuffix).append(path)
            if (query != null) append('?').append(query)
        }
        DetectedLink(
            rawUrl = url,
            canonicalUrl = canonical,
            domain = host
        )
    }.getOrNull()
}
