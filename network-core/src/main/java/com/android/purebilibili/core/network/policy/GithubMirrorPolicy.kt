package com.android.purebilibili.core.network.policy

import java.net.URI

enum class GithubMirrorSource(val label: String, val prefix: String? = null) {
    GHFAST("GHFast", "https://ghfast.top/"),
    GH_PROXY("GH-Proxy", "https://gh-proxy.org/"),
    // HiWiki's public GitHub Store uses this node to forward Release assets.
    HIWIKI("HiWiki", "https://gh.idayer.com/"),
    CUSTOM("自定义源");

    companion object {
        fun fromValue(value: String?): GithubMirrorSource =
            entries.firstOrNull { it.name == value } ?: GHFAST
    }
}

data class GithubMirrorSettings(
    val enabled: Boolean = false,
    val source: GithubMirrorSource = GithubMirrorSource.GHFAST,
    val customPrefix: String = "",
)

fun normalizeGithubMirrorPrefix(value: String): String? {
    val prefix = value.trim().trimEnd('/')
    if (prefix.length > 2048) return null
    val uri = runCatching { URI(prefix) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrEmpty() ||
        uri.userInfo != null ||
        uri.query != null ||
        uri.fragment != null ||
        (uri.port != -1 && uri.port !in 1..65535)
    ) {
        return null
    }
    val asciiPrefix = uri.toASCIIString().takeIf { it.length <= 2048 } ?: return null
    return "$asciiPrefix/"
}

fun resolveGithubMirrorPrefix(
    source: GithubMirrorSource,
    customPrefix: String,
): String? = source.prefix ?: normalizeGithubMirrorPrefix(customPrefix)

fun isMirrorableGithubResourceUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase() ?: return false
    val path = uri.rawPath.orEmpty()
    return when (host) {
        "raw.githubusercontent.com", "gist.githubusercontent.com" -> {
            path.length > 1 && path.startsWith('/')
        }
        "github.com" -> {
            val segments = path.trimStart('/').split('/').filter { it.isNotEmpty() }
            when {
                segments.size >= 5 && segments[2] == "releases" && segments[3] == "download" -> true
                segments.size >= 6 &&
                    segments[2] == "releases" &&
                    segments[3] == "latest" &&
                    segments[4] == "download" -> true
                segments.size >= 4 && segments[2] == "raw" -> true
                segments.size >= 4 && segments[2] == "archive" -> true
                else -> false
            }
        }
        else -> false
    }
}

fun resolveGithubMirrorUrl(
    source: GithubMirrorSource,
    customPrefix: String,
    originalUrl: String,
): String? {
    if (!isMirrorableGithubResourceUrl(originalUrl)) return null
    val prefix = resolveGithubMirrorPrefix(source, customPrefix) ?: return null
    return prefix + originalUrl.trim()
}

fun resolveGithubMirrorUrl(
    settings: GithubMirrorSettings,
    originalUrl: String,
): String? {
    if (!settings.enabled) return null
    return resolveGithubMirrorUrl(settings.source, settings.customPrefix, originalUrl)
}
