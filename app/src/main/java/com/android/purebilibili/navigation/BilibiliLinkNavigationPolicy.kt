package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.BilibiliUrlParser
import java.net.URI

internal sealed interface BilibiliLinkNavigationAction {
    data class NativeTarget(val target: BilibiliNavigationTarget) : BilibiliLinkNavigationAction
    data class InAppWeb(val url: String) : BilibiliLinkNavigationAction
    data class External(val url: String) : BilibiliLinkNavigationAction
    data object None : BilibiliLinkNavigationAction
}

internal fun resolveBilibiliLinkNavigationAction(rawLink: String): BilibiliLinkNavigationAction {
    val trimmed = rawLink.trim()
    if (trimmed.isEmpty()) return BilibiliLinkNavigationAction.None

    BilibiliNavigationTargetParser.parse(trimmed)?.let {
        return BilibiliLinkNavigationAction.NativeTarget(it)
    }

    BilibiliNavigationTargetParser.unwrapBrowserUrl(trimmed)?.let { destination ->
        val url = normalizeBilibiliLinkCandidate(destination)
        if (url != null) return BilibiliLinkNavigationAction.InAppWeb(url)
    }

    val url = normalizeBilibiliLinkCandidate(trimmed)
        ?: BilibiliUrlParser.extractUrls(trimmed).firstNotNullOfOrNull(::normalizeBilibiliLinkCandidate)
        ?: return BilibiliLinkNavigationAction.None

    return if (isBilibiliWebLink(url)) {
        BilibiliLinkNavigationAction.InAppWeb(url)
    } else {
        BilibiliLinkNavigationAction.External(url)
    }
}

internal fun isBilibiliShortWebLink(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    return com.android.purebilibili.core.util.isBilibiliShortHost(host)
}

private fun normalizeBilibiliLinkCandidate(rawLink: String): String? {
    val trimmed = rawLink.trim()
    if (trimmed.isEmpty()) return null
    val candidate = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("b23.tv/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("www.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("m.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("space.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("live.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("search.bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("bilibili.com/", ignoreCase = true) -> "https://$trimmed"
        else -> return null
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase().orEmpty()
    return candidate.takeIf { (scheme == "http" || scheme == "https") && uri.host != null }
}

private fun isBilibiliWebLink(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    return com.android.purebilibili.core.util.isBilibiliWebHost(host)
}
