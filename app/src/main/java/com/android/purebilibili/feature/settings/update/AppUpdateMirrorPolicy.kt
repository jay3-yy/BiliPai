package com.android.purebilibili.feature.settings

import java.net.URI

internal enum class AppUpdateMirror(val label: String, val prefix: String? = null) {
    GHFAST("GHFast", "https://ghfast.top/"),
    GH_PROXY("GH-Proxy", "https://gh-proxy.org/"),
    // HiWiki's public GitHub Store uses this node to forward Release assets.
    HIWIKI("HiWiki", "https://gh.idayer.com/"),
    CUSTOM("自定义源");

    companion object {
        fun fromValue(value: String?): AppUpdateMirror = entries.firstOrNull { it.name == value } ?: GHFAST
    }
}

internal data class AppUpdateMirrorPreference(
    val source: AppUpdateMirror = AppUpdateMirror.GHFAST,
    val customPrefix: String = "",
)

internal fun normalizeAppUpdateMirrorPrefix(value: String): String? {
    val prefix = value.trim().trimEnd('/')
    if (prefix.length > 2048) return null
    val uri = runCatching { URI(prefix) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty() ||
        uri.userInfo != null || uri.query != null || uri.fragment != null ||
        (uri.port != -1 && uri.port !in 1..65535)
    ) return null
    val asciiPrefix = uri.toASCIIString().takeIf { it.length <= 2048 } ?: return null
    return "$asciiPrefix/"
}

internal fun resolveAppUpdateMirrorUrl(
    source: AppUpdateMirror,
    customPrefix: String,
    assetUrl: String,
): String? {
    if (!assetUrl.startsWith("https://github.com/jay3-yy/BiliPai/releases/download/")) return null
    val prefix = source.prefix ?: normalizeAppUpdateMirrorPrefix(customPrefix) ?: return null
    return prefix + assetUrl
}

internal fun isAppUpdateSha256(value: String?): Boolean =
    value != null && value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
