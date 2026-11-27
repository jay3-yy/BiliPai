package com.android.purebilibili.feature.appfunctions

import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.data.model.response.TimelineEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliPaiAppFunctionPolicyTest {

    @Test
    fun parsesValidAndInvalidFunctionRequests() {
        val search = parseBiliPaiAppFunctionRequest(APP_FUNCTION_SEARCH_CONTENT, "  鬼畜教程  ")
        assertEquals(AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.SearchContent("鬼畜教程")), search)

        val missingQuery = parseBiliPaiAppFunctionRequest(APP_FUNCTION_SEARCH_CONTENT, "   ")
        assertTrue(missingQuery is AppFunctionParseResult.InvalidArgument)

        val unknown = parseBiliPaiAppFunctionRequest("com.example.other", null)
        assertTrue(unknown is AppFunctionParseResult.UnknownFunction)

        assertEquals(
            AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.OpenWatchLater),
            parseBiliPaiAppFunctionRequest(APP_FUNCTION_OPEN_WATCH_LATER, null),
        )
        assertEquals(
            AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.ResumeBackgroundAudio),
            parseBiliPaiAppFunctionRequest(APP_FUNCTION_RESUME_AUDIO, null),
        )
        assertEquals(
            AppFunctionParseResult.Valid(BiliPaiAppFunctionAction.QueryTodayBangumi),
            parseBiliPaiAppFunctionRequest(APP_FUNCTION_QUERY_TODAY_BANGUMI, null),
        )
    }

    @Test
    fun formatsTodayBangumiSummaryPreferringIsTodayDay() {
        val days = listOf(
            TimelineDay(
                isToday = 0,
                episodes = listOf(TimelineEpisode(title = "昨日番", pubTime = "20:00", pubIndex = "第1话")),
            ),
            TimelineDay(
                isToday = 1,
                episodes = listOf(
                    TimelineEpisode(title = "今日新番A", pubTime = "22:00", pubIndex = "第8话"),
                    TimelineEpisode(title = "今日新番B", pubTime = "", pubIndex = "第3话"),
                ),
            ),
        )
        val items = formatTodayBangumiSummaryItems(days, maxItems = 5)
        assertEquals(listOf("22:00 · 今日新番A · 第8话", "今日新番B · 第3话"), items)
    }
}
