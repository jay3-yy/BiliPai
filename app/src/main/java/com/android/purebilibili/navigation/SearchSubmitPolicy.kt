package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.BilibiliUrlParser

internal sealed interface SearchSubmitAction {
    data object Ignore : SearchSubmitAction
    data class OpenSearch(val keyword: String) : SearchSubmitAction
    data class OpenLink(val url: String) : SearchSubmitAction
    data class OpenNativeTarget(val target: BilibiliNavigationTarget) : SearchSubmitAction
}

private val sharedShortLink = Regex(
    "(?<![A-Za-z0-9./-])(?:https?://)?b23\\.tv/[A-Za-z0-9]+(?:[?][^\\s，。！；）】]*)?",
    RegexOption.IGNORE_CASE,
)

internal fun resolveSearchSubmitAction(rawKeyword: String): SearchSubmitAction {
    val keyword = rawKeyword.trim()
    if (keyword.isEmpty()) return SearchSubmitAction.Ignore

    // 分享标题里的 BV/av 字样不作为跳转依据，短链先展开到实际目标。
    val shortLink = sharedShortLink.find(keyword)?.value
    val linkCandidate = shortLink ?: BilibiliUrlParser.extractUrls(keyword).firstOrNull { url ->
        val action = resolveBilibiliLinkNavigationAction(url)
        action is BilibiliLinkNavigationAction.NativeTarget || action is BilibiliLinkNavigationAction.InAppWeb
    }
    val candidate = linkCandidate ?: keyword
    val linkAction = resolveBilibiliLinkNavigationAction(candidate)
    if (linkAction is BilibiliLinkNavigationAction.InAppWeb && isBilibiliShortWebLink(linkAction.url)) {
        return SearchSubmitAction.OpenLink(linkAction.url)
    }
    return when (val target = BilibiliNavigationTargetParser.parse(candidate)) {
        null -> SearchSubmitAction.OpenSearch(keyword)
        is BilibiliNavigationTarget.Search -> SearchSubmitAction.OpenSearch(target.keyword)
        else -> SearchSubmitAction.OpenNativeTarget(target)
    }
}
