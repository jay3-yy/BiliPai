package com.android.purebilibili.feature.appfunctions

import com.android.purebilibili.data.model.response.TimelineDay

internal const val APP_FUNCTION_SEARCH_CONTENT = "com.android.purebilibili.search_content"
internal const val APP_FUNCTION_OPEN_WATCH_LATER = "com.android.purebilibili.open_watch_later"
internal const val APP_FUNCTION_RESUME_AUDIO = "com.android.purebilibili.resume_background_audio"
internal const val APP_FUNCTION_QUERY_TODAY_BANGUMI = "com.android.purebilibili.query_today_bangumi"

internal sealed interface BiliPaiAppFunctionAction {
    data class SearchContent(val query: String) : BiliPaiAppFunctionAction
    data object OpenWatchLater : BiliPaiAppFunctionAction
    data object ResumeBackgroundAudio : BiliPaiAppFunctionAction
    data object QueryTodayBangumi : BiliPaiAppFunctionAction
}

internal sealed interface AppFunctionParseResult {
    data class Valid(val action: BiliPaiAppFunctionAction) : AppFunctionParseResult
    data class UnknownFunction(val functionId: String) : AppFunctionParseResult
    data class InvalidArgument(val reason: String) : AppFunctionParseResult
}

internal fun parseBiliPaiAppFunctionRequest(
    functionId: String,
    queryParam: String?,
): AppFunctionParseResult = when (functionId.trim()) {
    APP_FUNCTION_SEARCH_CONTENT -> {
        val cleanQuery = queryParam?.trim().orEmpty()
        if (cleanQuery.isEmpty()) {
            AppFunctionParseResult.InvalidArgument("缺少搜索关键词参数 query")
        } else {
            AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.SearchContent(cleanQuery.take(100)))
        }
    }
    APP_FUNCTION_OPEN_WATCH_LATER -> AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.OpenWatchLater)
    APP_FUNCTION_RESUME_AUDIO -> AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.ResumeBackgroundAudio)
    APP_FUNCTION_QUERY_TODAY_BANGUMI -> AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.QueryTodayBangumi)
    else -> AppFunctionParseResult.UnknownFunction(functionId)
}

internal fun formatTodayBangumiSummaryItems(
    days: List<TimelineDay>,
    maxItems: Int = 8,
): List<String> {
    val today = days.firstOrNull { it.isToday == 1 } ?: days.firstOrNull() ?: return emptyList()
    return today.episodes.orEmpty()
        .filter { it.title.isNotBlank() }
        .take(maxItems.coerceAtLeast(1))
        .map { ep ->
            buildList {
                if (ep.pubTime.isNotBlank()) add(ep.pubTime)
                add(ep.title.trim())
                if (ep.pubIndex.isNotBlank()) add(ep.pubIndex.trim())
            }.joinToString(" · ")
        }
}
