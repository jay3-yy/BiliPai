package com.android.purebilibili.feature.article

import com.android.purebilibili.data.model.response.ArticleStats
import com.android.purebilibili.core.util.FormatUtils

internal fun formatArticleActionCount(count: Long): String =
    FormatUtils.formatStat(count)

/** 互动接口未返回的计数继续使用详情种子，真实零值始终保留。 */
internal fun mergeArticleInteractionStats(primary: ArticleStats?, seed: ArticleStats?): ArticleStats? =
    primary?.copy(
        view = primary.view ?: seed?.view,
        like = primary.like ?: seed?.like,
        favorite = primary.favorite ?: seed?.favorite,
        reply = primary.reply ?: seed?.reply,
        share = primary.share ?: seed?.share,
    ) ?: seed
