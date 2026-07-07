package com.android.purebilibili.feature.search

import com.android.purebilibili.data.model.response.SearchType

enum class SearchEmptyStateReason {
    NONE,
    NO_RESULTS,
    FILTERED_OUT
}

data class SearchEmptyStateCopy(
    val title: String,
    val subtitle: String
)

internal fun resolveSearchEmptyStateReason(
    rawResultCount: Int,
    visibleResultCount: Int
): SearchEmptyStateReason {
    return when {
        visibleResultCount > 0 -> SearchEmptyStateReason.NONE
        rawResultCount > 0 -> SearchEmptyStateReason.FILTERED_OUT
        else -> SearchEmptyStateReason.NO_RESULTS
    }
}

internal fun resolveSearchEmptyStateCopy(
    reason: SearchEmptyStateReason,
    searchType: SearchType
): SearchEmptyStateCopy {
    if (reason == SearchEmptyStateReason.FILTERED_OUT) {
        return SearchEmptyStateCopy(
            title = "结果已被过滤",
            subtitle = "当前结果被屏蔽规则隐藏，请调整过滤设置后重试"
        )
    }

    val title = when (searchType) {
        SearchType.VIDEO -> "未找到相关视频"
        SearchType.UP -> "未找到相关UP主"
        SearchType.BANGUMI -> "未找到相关番剧"
        SearchType.MEDIA_FT -> "未找到相关影视"
        SearchType.LIVE -> "未找到相关直播间"
        SearchType.ARTICLE -> "未找到相关专栏"
        else -> "未找到相关内容"
    }
    return SearchEmptyStateCopy(
        title = title,
        subtitle = "试试其他关键词或调整筛选条件"
    )
}
