package com.android.purebilibili.core.ui.adaptive

import android.content.ClipData
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.BilibiliUrlParser

internal sealed interface ExternalDropAction {
    data class OpenLink(val rawText: String) : ExternalDropAction
    data class Search(val keyword: String) : ExternalDropAction
}

internal const val EXTERNAL_DROP_MAX_SEARCH_LENGTH = 120

/** 从跨应用拖拽的 ClipData 中提取首个非空文本或 URI。 */
internal fun resolveExternalDroppedText(clipData: ClipData?): String? {
    if (clipData == null || clipData.itemCount <= 0) return null
    for (index in 0 until clipData.itemCount) {
        val item = clipData.getItemAt(index) ?: continue
        val text = item.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null) return text
        val uri = item.uri?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (uri != null) return uri
    }
    return null
}

/**
 * 解析跨应用拖入的文本意图：
 * - 包含 B 站可解析目标（BV/AV/番剧/直播/动态/空间/b23 等）或 URL 时走链接导航；
 * - 其余短文本作为搜索词打开搜索页。
 */
internal fun resolveExternalDropAction(rawText: String?): ExternalDropAction? {
    val trimmed = rawText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val hasNavigableLink = BilibiliNavigationTargetParser.parse(trimmed) != null ||
        BilibiliNavigationTargetParser.unwrapBrowserUrl(trimmed) != null ||
        BilibiliUrlParser.extractUrls(trimmed).isNotEmpty()
    if (hasNavigableLink) {
        return ExternalDropAction.OpenLink(trimmed)
    }
    val singleLine = trimmed.replace(Regex("\\s+"), " ").take(EXTERNAL_DROP_MAX_SEARCH_LENGTH)
    return singleLine.takeIf { it.isNotEmpty() }?.let(ExternalDropAction::Search)
}
