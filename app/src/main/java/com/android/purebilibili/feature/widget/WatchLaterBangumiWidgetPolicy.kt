package com.android.purebilibili.feature.widget

import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.navigation.ScreenRoutes

internal enum class WatchLaterBangumiWidgetTab(val storageKey: String) {
    WATCH_LATER("watch_later"),
    BANGUMI("bangumi");

    companion object {
        fun fromKey(raw: String?): WatchLaterBangumiWidgetTab =
            entries.firstOrNull { it.storageKey == raw } ?: WATCH_LATER
    }
}

internal data class WidgetFeedItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val targetRoute: String,
)

internal fun mapBangumiTimelineToWidgetItems(
    days: List<TimelineDay>,
    maxItems: Int = 10,
): List<WidgetFeedItem> {
    val today = days.firstOrNull { it.isToday == 1 } ?: days.firstOrNull() ?: return emptyList()
    return today.episodes.orEmpty()
        .filter { it.seasonId > 0L && it.title.isNotBlank() }
        .take(maxItems.coerceAtLeast(1))
        .map { ep ->
            val sub = buildList {
                if (ep.pubTime.isNotBlank()) add(ep.pubTime.trim())
                if (ep.pubIndex.isNotBlank()) add(ep.pubIndex.trim())
            }.joinToString(" · ")
            WidgetFeedItem(
                id = if (ep.episodeId > 0L) ep.episodeId else ep.seasonId,
                title = ep.title.trim(),
                subtitle = sub.ifBlank { "今日更新" },
                targetRoute = ScreenRoutes.BangumiDetail.createRoute(
                    seasonId = ep.seasonId,
                    epId = ep.episodeId,
                ),
            )
        }
}

internal fun resolveWidgetEmptyStateMessage(
    tab: WatchLaterBangumiWidgetTab,
    libraryProtected: Boolean,
    isLoggedIn: Boolean,
    loadFailed: Boolean,
): String = when {
    tab == WatchLaterBangumiWidgetTab.WATCH_LATER && libraryProtected -> "已开启隐私验证，请在应用内查看"
    tab == WatchLaterBangumiWidgetTab.WATCH_LATER && !isLoggedIn -> "登录后即可查看稍后再看"
    loadFailed -> "加载失败，点击右上角重试"
    tab == WatchLaterBangumiWidgetTab.WATCH_LATER -> "稍后再看为空"
    else -> "今日暂无番剧更新"
}

internal data class WidgetPublishSnapshot(
    val tab: WatchLaterBangumiWidgetTab,
    val libraryProtected: Boolean,
    val isLoggedIn: Boolean,
    val accountIdentity: Any?,
)

internal enum class WidgetPublishDecision {
    APPLY_FETCHED,
    APPLY_EMPTY_PROTECTED,
    DROP_STALE,
}

/**
 * 防止并发刷新乱序或请求在途期间隐私锁/账号切换把过期标题写回桌面微件。
 */
internal fun resolveWidgetPublishDecision(
    requestGeneration: Long,
    latestGeneration: Long,
    started: WidgetPublishSnapshot,
    current: WidgetPublishSnapshot,
): WidgetPublishDecision {
    if (requestGeneration != latestGeneration) return WidgetPublishDecision.DROP_STALE
    if (current.tab != started.tab) return WidgetPublishDecision.DROP_STALE
    if (started.tab == WatchLaterBangumiWidgetTab.WATCH_LATER) {
        if (current.libraryProtected || !current.isLoggedIn) {
            return WidgetPublishDecision.APPLY_EMPTY_PROTECTED
        }
        if (current.accountIdentity != started.accountIdentity) {
            return WidgetPublishDecision.DROP_STALE
        }
    }
    return WidgetPublishDecision.APPLY_FETCHED
}
