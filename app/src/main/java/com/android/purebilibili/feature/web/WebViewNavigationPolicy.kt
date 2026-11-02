package com.android.purebilibili.feature.web

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.hasCommentLocation

internal sealed interface WebViewNavigationAction {
    data object AllowWebLoad : WebViewNavigationAction
    data object Block : WebViewNavigationAction
    data class LoadInWebView(val url: String) : WebViewNavigationAction
    data class DispatchTarget(val target: BilibiliNavigationTarget) : WebViewNavigationAction
    data class OpenExternal(val url: String) : WebViewNavigationAction
}

internal fun resolveWebViewNavigationAction(
    urlString: String,
    hasUserGesture: Boolean
): WebViewNavigationAction {
    val trimmedUrl = urlString.trim()
    val normalizedUrl = trimmedUrl.lowercase()
    val isCustomScheme = normalizedUrl.startsWith("bilibili://") || normalizedUrl.startsWith("bili://")

    // Short shopping links can redirect off-site. Hand the destination to another app
    // before WebView renders it; checking the host boundary avoids lookalike domains.
    if (shouldOpenWebUrlExternally(trimmedUrl)) {
        return WebViewNavigationAction.OpenExternal(trimmedUrl)
    }

    if (isOfficialMusicDetailUrl(trimmedUrl)) {
        return WebViewNavigationAction.AllowWebLoad
    }

    // 空间子页包含网页投稿、充电等独立内容，不能丢掉路径后拦回 UP 主首页。
    if (isOfficialSpaceSubpageUrl(trimmedUrl)) {
        return WebViewNavigationAction.AllowWebLoad
    }

    if (isCustomScheme) {
        if (!hasUserGesture) {
            return WebViewNavigationAction.Block
        }

        val target = BilibiliNavigationTargetParser.parse(urlString)
        if (target != null && (target is BilibiliNavigationTarget.Dynamic || target.hasCommentLocation)) {
            return WebViewNavigationAction.DispatchTarget(target)
        }

        val convertedUrl = convertDeepLinkToWebUrl(urlString)
        return if (convertedUrl != null) {
            WebViewNavigationAction.LoadInWebView(convertedUrl)
        } else {
            WebViewNavigationAction.Block
        }
    }

    val target = BilibiliNavigationTargetParser.parse(urlString)
    // 显式网页入口的自动重定向保留在网页内，点击文集链接时才进入原生页面。
    if (target is BilibiliNavigationTarget.ArticleCollection && !hasUserGesture) {
        return WebViewNavigationAction.AllowWebLoad
    }
    return if (target != null) {
        WebViewNavigationAction.DispatchTarget(target)
    } else {
        WebViewNavigationAction.AllowWebLoad
    }
}

internal fun shouldOpenWebUrlExternally(urlString: String): Boolean {
    val uri = runCatching { java.net.URI(urlString.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)
    ) return false
    val host = uri.host?.lowercase() ?: return false
    return !com.android.purebilibili.core.util.isBilibiliWebHost(host)
}

internal fun isOfficialSpaceSubpageUrl(urlString: String): Boolean {
    val uri = runCatching { java.net.URI(urlString.trim()) }.getOrNull() ?: return false
    val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
    return uri.host.equals("space.bilibili.com", ignoreCase = true) && segments.size > 1 &&
        segments.first().toLongOrNull()?.let { it > 0L } == true
}

internal fun isOfficialMusicDetailUrl(urlString: String): Boolean {
    val uri = runCatching { java.net.URI(urlString.trim()) }.getOrNull() ?: return false
    val pathSegments = uri.path
        ?.split("/")
        ?.filter { it.isNotBlank() }
        ?: emptyList()
    return uri.host?.equals("music.bilibili.com", ignoreCase = true) == true &&
        pathSegments.contains("music-detail")
}

internal fun convertDeepLinkToWebUrl(rawUrl: String): String? {
    val normalized = rawUrl.trim()
    val withoutScheme = when {
        normalized.startsWith("bilibili://", ignoreCase = true) -> normalized.removePrefix("bilibili://")
        normalized.startsWith("bili://", ignoreCase = true) -> normalized.removePrefix("bili://")
        else -> return null
    }
    val pathWithoutQuery = withoutScheme.substringBefore("?").trim('/')
    if (pathWithoutQuery.isBlank()) return null

    val pathSegments = pathWithoutQuery.split("/").filter { it.isNotBlank() }
    if (pathSegments.isEmpty()) return null
    val host = pathSegments.first()

    android.util.Log.d("WebViewScreen", "🔗 Converting deep link: host=$host, segments=$pathSegments")

    return when {
        host == "video" -> {
            val videoId = pathSegments.getOrNull(1)
            if (videoId != null) {
                if (videoId.startsWith("BV")) {
                    "https://m.bilibili.com/video/$videoId"
                } else {
                    val numericId = videoId.toLongOrNull()
                    if (numericId != null && numericId > 10_000_000_000L) {
                        android.util.Log.w("WebViewScreen", "⚠️ Blocking invalid video ID: $numericId")
                        null
                    } else {
                        "https://m.bilibili.com/video/av$videoId"
                    }
                }
            } else {
                null
            }
        }

        host == "space" -> {
            val mid = pathSegments.getOrNull(1)
            if (mid != null) "https://space.bilibili.com/$mid" else null
        }

        host == "live" -> {
            val roomId = pathSegments.getOrNull(1)
            if (roomId != null) "https://live.bilibili.com/$roomId" else null
        }

        host == "bangumi" -> {
            if (pathSegments.getOrNull(1) == "season") {
                val ssid = pathSegments.getOrNull(2)
                if (ssid != null) "https://m.bilibili.com/bangumi/play/ss$ssid" else null
            } else null
        }

        else -> null
    }
}
