package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.data.model.response.BangumiIndexConditionData
import com.android.purebilibili.data.model.response.BangumiIndexConditionFilter
import com.android.purebilibili.data.model.response.BangumiIndexConditionOrder
import com.android.purebilibili.data.model.response.BangumiIndexConditionValue
import com.android.purebilibili.data.model.response.BangumiSearchItem
import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.data.model.response.TimelineEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiHubPolicyTest {
    @Test
    fun `initial types map to the two BiliPai channels`() {
        assertEquals(BangumiChannel.BANGUMI, resolveBangumiChannel(1))
        assertEquals(BangumiChannel.BANGUMI, resolveBangumiChannel(4))
        assertEquals(BangumiChannel.CINEMA, resolveBangumiChannel(2))
        assertEquals(BangumiChannel.CINEMA, resolveBangumiChannel(7))
    }

    @Test
    fun `back handling prioritizes selection and nested pages`() {
        assertEquals(
            BangumiBackAction.CLEAR_SELECTION,
            resolveBangumiBackAction(BangumiHubPage.FOLLOW, hasSelection = true),
        )
        assertEquals(
            BangumiBackAction.CLOSE_SEARCH,
            resolveBangumiBackAction(BangumiHubPage.SEARCH, hasSelection = false),
        )
        assertEquals(
            BangumiBackAction.SHOW_HOME,
            resolveBangumiBackAction(BangumiHubPage.INDEX, hasSelection = false),
        )
        assertEquals(
            BangumiBackAction.EXIT_SCREEN,
            resolveBangumiBackAction(BangumiHubPage.HOME, hasSelection = false),
        )
    }

    @Test
    fun `server conditions produce default index parameters`() {
        val groups = buildBangumiIndexFilterGroups(
            BangumiIndexConditionData(
                order = listOf(BangumiIndexConditionOrder(field = "3", name = "追番人数", sort = "0")),
                filter = listOf(
                    BangumiIndexConditionFilter(
                        field = "style_id",
                        name = "风格",
                        values = listOf(
                            BangumiIndexConditionValue(keyword = "-1", name = "全部"),
                            BangumiIndexConditionValue(keyword = "25", name = "历史"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf("order", "style_id"), groups.map { it.field })
        assertEquals(mapOf("order" to "3", "sort" to "0", "style_id" to "-1"), buildDefaultBangumiIndexParams(groups))
        assertEquals(
            "25",
            updateBangumiIndexParams(
                current = buildDefaultBangumiIndexParams(groups),
                group = groups[1],
                choice = groups[1].choices[1],
            )["style_id"],
        )
    }

    @Test
    fun `bangumi and guochuang timelines merge by date and deduplicate episodes`() {
        val shared = TimelineEpisode(episodeId = 2, seasonId = 20, pubTs = 20)
        val result = mergeBangumiTimelineDays(
            bangumiDays = listOf(
                TimelineDay(
                    date = "2026-08-10",
                    dateTs = 10,
                    dayOfWeek = 1,
                    isToday = 1,
                    episodes = listOf(TimelineEpisode(episodeId = 1, seasonId = 10, pubTs = 10), shared),
                ),
            ),
            guochuangDays = listOf(
                TimelineDay(
                    date = "2026-08-10",
                    dateTs = 10,
                    dayOfWeek = 1,
                    episodes = listOf(shared, TimelineEpisode(episodeId = 3, seasonId = 30, pubTs = 30)),
                ),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(1, result.single().isToday)
        assertEquals(listOf(1L, 2L, 3L), result.single().episodes.orEmpty().map { it.episodeId })
    }

    @Test
    fun `timeline labels include visible dates and today marker`() {
        assertEquals(
            "8-8 周五",
            resolveBangumiTimelineDayLabel(
                TimelineDay(date = "2026-08-08", dayOfWeek = 5),
            ),
        )
        assertEquals(
            "8-10 今天",
            resolveBangumiTimelineDayLabel(
                TimelineDay(date = "2026-08-10", dayOfWeek = 7, isToday = 1),
            ),
        )
    }

    @Test
    fun `timeline episode metadata prefers the matching cover and update state`() {
        val episode = TimelineEpisode(
            cover = "season-cover",
            squareCover = "square-cover",
            episodeCover = "episode-cover",
            pubIndex = "第 8 话",
            pubTime = "20:00",
            published = 0,
        )

        assertEquals("season-cover", resolveTimelineEpisodeCover(episode, preferEpisodeCover = false))
        assertEquals("episode-cover", resolveTimelineEpisodeCover(episode, preferEpisodeCover = true))
        assertEquals("第 8 话", resolveTimelineEpisodeUpdateLabel(episode))
        assertEquals("预计 20:00", resolveTimelineEpisodeScheduleLabel(episode))

        val delayed = episode.copy(
            delay = 1,
            delayIndex = "第 9 话",
            delayReason = "本周停播",
        )
        assertEquals("第 9 话", resolveTimelineEpisodeUpdateLabel(delayed))
        assertEquals("本周停播", resolveTimelineEpisodeScheduleLabel(delayed))
    }

    @Test
    fun `selection toggles valid ids only`() {
        assertEquals(setOf(9L), updateBangumiSelection(emptySet(), 9L))
        assertTrue(updateBangumiSelection(setOf(9L), 9L).isEmpty())
        assertEquals(setOf(9L), updateBangumiSelection(setOf(9L), 0L))
    }

    @Test
    fun `index categories produce BiliPai query targets`() {
        assertEquals(
            BangumiIndexQueryTarget(seasonType = null, indexType = 102),
            resolveBangumiIndexQueryTarget(BangumiIndexCategory.CINEMA_ALL),
        )
        assertEquals(
            BangumiIndexQueryTarget(seasonType = null, indexType = 2),
            resolveBangumiIndexQueryTarget(BangumiIndexCategory.MOVIE),
        )
        assertEquals(
            BangumiIndexQueryTarget(seasonType = 1, indexType = null),
            resolveBangumiIndexQueryTarget(BangumiIndexCategory.BANGUMI),
        )
        assertEquals(
            BangumiIndexQueryTarget(seasonType = 4, indexType = null),
            resolveBangumiIndexQueryTarget(BangumiIndexCategory.GUOCHUANG),
        )
        assertEquals(
            listOf(BangumiIndexCategory.BANGUMI),
            bangumiIndexCategoriesForChannel(BangumiChannel.BANGUMI),
        )
    }

    @Test
    fun `search categories preserve channel scope and filter exact season types`() {
        assertEquals(
            BangumiIndexCategory.GUOCHUANG,
            resolveDefaultBangumiSearchCategory(
                channel = BangumiChannel.BANGUMI,
                preferred = BangumiIndexCategory.GUOCHUANG,
            ),
        )
        assertEquals(
            BangumiIndexCategory.CINEMA_ALL,
            resolveDefaultBangumiSearchCategory(
                channel = BangumiChannel.CINEMA,
                preferred = BangumiIndexCategory.GUOCHUANG,
            ),
        )

        val results = listOf(
            BangumiSearchItem(seasonId = 1, seasonType = 1, seasonTypeName = "番剧"),
            BangumiSearchItem(seasonId = 2, seasonType = 4, seasonTypeName = "国创"),
            BangumiSearchItem(seasonId = 3, seasonType = 2, seasonTypeName = "电影"),
        )
        assertEquals(
            listOf(2L),
            filterBangumiSearchItems(results, BangumiIndexCategory.GUOCHUANG).map { it.seasonId },
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            filterBangumiSearchItems(results, BangumiIndexCategory.CINEMA_ALL).map { it.seasonId },
        )
    }

    @Test
    fun `pagination de-duplicates and reset drops old page`() {
        assertEquals(
            listOf(1, 2, 3),
            mergeBangumiPagedItems(listOf(1, 2), listOf(2, 3), reset = false) { it },
        )
        assertEquals(
            listOf(2, 3),
            mergeBangumiPagedItems(listOf(1), listOf(2, 2, 3), reset = true) { it },
        )
    }
}
