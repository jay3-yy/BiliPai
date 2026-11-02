package com.android.purebilibili.feature.space

import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.SpaceArticleItem

internal fun buildSpaceArticleStatsText(article: SpaceArticleItem): String {
    article.displayStats?.let { return if (it.isEmpty()) "图文" else it.joinToString(" · ") }
    return buildList {
        add(article.category?.name?.takeIf { it.isNotBlank() } ?: "图文")
        val viewCount = article.stats?.view ?: 0
        add(if (viewCount > 0) "${FormatUtils.formatStat(viewCount.toLong())}阅读" else "—阅读")
        add("${FormatUtils.formatStat(article.stats?.like?.toLong() ?: 0)}点赞")
    }.joinToString(" · ")
}
