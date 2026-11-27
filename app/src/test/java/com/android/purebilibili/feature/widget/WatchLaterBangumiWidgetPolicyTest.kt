package com.android.purebilibili.feature.widget

import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.data.model.response.TimelineEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLaterBangumiWidgetPolicyTest {

    @Test
    fun mapsTodayBangumiEpisodesToRoutableWidgetItems() {
        val days = listOf(
            TimelineDay(
                isToday = 1,
                episodes = listOf(
                    TimelineEpisode(seasonId = 1001L, episodeId = 2002L, title = "示例番剧", pubTime = "21:30", pubIndex = "第5话"),
                    TimelineEpisode(seasonId = 0L, episodeId = 0L, title = "无效条目"),
                ),
            )
        )
        val items = mapBangumiTimelineToWidgetItems(days, maxItems = 5)
        assertEquals(1, items.size)
        assertEquals(2002L, items[0].id)
        assertEquals("示例番剧", items[0].title)
        assertEquals("21:30 · 第5话", items[0].subtitle)
        assertTrue(items[0].targetRoute.contains("1001"))
    }

    @Test
    fun emptyStateMessagePrioritizesPrivacyLockAndLoginStateForWatchLater() {
        assertEquals(
            "已开启隐私验证，请在应用内查看",
            resolveWidgetEmptyStateMessage(
                tab = WatchLaterBangumiWidgetTab.WATCH_LATER,
                libraryProtected = true,
                isLoggedIn = false,
                loadFailed = true,
            ),
        )
        assertEquals(
            "登录后即可查看稍后再看",
            resolveWidgetEmptyStateMessage(
                tab = WatchLaterBangumiWidgetTab.WATCH_LATER,
                libraryProtected = false,
                isLoggedIn = false,
                loadFailed = false,
            ),
        )
        assertEquals(
            "今日暂无番剧更新",
            resolveWidgetEmptyStateMessage(
                tab = WatchLaterBangumiWidgetTab.BANGUMI,
                libraryProtected = true,
                isLoggedIn = false,
                loadFailed = false,
            ),
        )
    }

    @Test
    fun publishDecisionDropsStaleGenerationsAndGuardsMidFlightPrivacyOrAccountChanges() {
        val started = WidgetPublishSnapshot(
            tab = WatchLaterBangumiWidgetTab.WATCH_LATER,
            libraryProtected = false,
            isLoggedIn = true,
            accountIdentity = 1001L,
        )
        assertEquals(
            WidgetPublishDecision.DROP_STALE,
            resolveWidgetPublishDecision(
                requestGeneration = 1L,
                latestGeneration = 2L,
                started = started,
                current = started,
            ),
        )
        assertEquals(
            WidgetPublishDecision.APPLY_EMPTY_PROTECTED,
            resolveWidgetPublishDecision(
                requestGeneration = 2L,
                latestGeneration = 2L,
                started = started,
                current = started.copy(libraryProtected = true),
            ),
        )
        assertEquals(
            WidgetPublishDecision.DROP_STALE,
            resolveWidgetPublishDecision(
                requestGeneration = 2L,
                latestGeneration = 2L,
                started = started,
                current = started.copy(accountIdentity = 2002L),
            ),
        )
        assertEquals(
            WidgetPublishDecision.DROP_STALE,
            resolveWidgetPublishDecision(
                requestGeneration = 2L,
                latestGeneration = 2L,
                started = started,
                current = started.copy(tab = WatchLaterBangumiWidgetTab.BANGUMI),
            ),
        )
        assertEquals(
            WidgetPublishDecision.APPLY_FETCHED,
            resolveWidgetPublishDecision(
                requestGeneration = 2L,
                latestGeneration = 2L,
                started = started,
                current = started,
            ),
        )
    }
}
