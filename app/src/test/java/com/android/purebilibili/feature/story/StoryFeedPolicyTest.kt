package com.android.purebilibili.feature.story

import com.android.purebilibili.data.model.response.Dimension
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.model.response.StoryOwner
import com.android.purebilibili.data.model.response.StoryPlayerArgs
import com.android.purebilibili.data.model.response.StoryStat
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoryFeedPolicyTest {

    @Test
    fun resolveNextStoryFeedAid_keepsPreviousCursorWhenTailAidIsMissing() {
        val nextAid = resolveNextStoryFeedAid(
            previousAid = 100L,
            items = listOf(
                storyItem(id = 1L, aid = 101L),
                storyItem(id = 2L, aid = 0L)
            )
        )

        assertEquals(101L, nextAid)
    }

    @Test
    fun mergeStoryFeedItems_skipsItemsAlreadySeenByAidBvidOrId() {
        val merged = mergeStoryFeedItems(
            existingItems = listOf(
                storyItem(id = 1L, aid = 100L, bvid = "BV_EXISTING"),
                storyItem(id = 2L, aid = 101L, bvid = "")
            ),
            newItems = listOf(
                storyItem(id = 3L, aid = 100L, bvid = "BV_DUP_AID"),
                storyItem(id = 4L, aid = 102L, bvid = "BV_EXISTING"),
                storyItem(id = 2L, aid = 0L, bvid = ""),
                storyItem(id = 5L, aid = 103L, bvid = "BV_FRESH")
            )
        )

        assertEquals(listOf(1L, 2L, 5L), merged.map { it.id })
    }

    @Test
    fun buildStoryPortraitFeed_usesFirstPlayableStoryAsInitialVideo() {
        val feed = buildStoryPortraitFeed(
            listOf(
                storyItem(id = 1L, aid = 0L, cid = 0L, bvid = ""),
                storyItem(id = 2L, aid = 200L, cid = 1200L, bvid = "")
            )
        )

        val portraitFeed = assertNotNull(feed)
        assertEquals("av200", portraitFeed.initialInfo.bvid)
        assertEquals(200L, portraitFeed.initialInfo.aid)
        assertEquals(1200L, portraitFeed.initialInfo.cid)
        assertEquals(1200L, portraitFeed.initialInfo.pages.firstOrNull()?.cid)
    }

    @Test
    fun buildStoryPortraitFeed_usesSeedVideoAsInitialEntry() {
        val feed = buildStoryPortraitFeed(
            items = listOf(
                storyItem(id = 1L, aid = 100L, cid = 1100L, bvid = "BV_OTHER")
            ),
            seed = StoryFeedSeed(
                bvid = "BV_SEED",
                cid = 2200L,
                cover = "https://img.test/seed.jpg",
                title = "Seed"
            )
        )

        val portraitFeed = assertNotNull(feed)
        assertEquals("BV_SEED", portraitFeed.initialInfo.bvid)
        assertEquals(2200L, portraitFeed.initialInfo.cid)
        assertEquals("https://img.test/seed.jpg", portraitFeed.initialInfo.pic)
        assertEquals(listOf("BV_OTHER"), portraitFeed.recommendations.map { it.bvid })
    }

    @Test
    fun resolveStoryPortraitIndexForBvid_mapsFeedAndSeedPositions() {
        val items = listOf(
            storyItem(id = 1L, aid = 100L, cid = 1L, bvid = "BV_A"),
            storyItem(id = 2L, aid = 200L, cid = 2L, bvid = "BV_B")
        )
        assertEquals(
            1,
            resolveStoryPortraitIndexForBvid(
                bvid = "BV_B",
                items = items,
                seedBvid = "BV_SEED"
            )
        )
        assertEquals(
            0,
            resolveStoryPortraitIndexForBvid(
                bvid = "BV_SEED",
                items = items,
                seedBvid = "BV_SEED"
            )
        )
        assertEquals(
            -1,
            resolveStoryPortraitIndexForBvid(
                bvid = "BV_MISSING",
                items = items,
                seedBvid = "BV_SEED"
            )
        )
    }

    @Test
    fun buildStoryPortraitFeed_mapsRemainingPlayableStoriesAsRecommendations() {
        val feed = buildStoryPortraitFeed(
            listOf(
                storyItem(id = 1L, aid = 100L, cid = 1100L, bvid = "BV_FIRST"),
                storyItem(id = 2L, aid = 101L, cid = 1101L, bvid = "BV_SECOND"),
                storyItem(id = 3L, aid = 0L, cid = 0L, bvid = ""),
                storyItem(id = 4L, aid = 102L, cid = 1102L, bvid = "")
            )
        )

        val portraitFeed = assertNotNull(feed)
        assertEquals("BV_FIRST", portraitFeed.initialInfo.bvid)
        assertEquals(listOf("BV_SECOND", "av102"), portraitFeed.recommendations.map { it.bvid })
        assertEquals(listOf(1101L, 1102L), portraitFeed.recommendations.map { it.cid })
    }

    @Test
    fun videoItemToStoryItem_mapsHomeRecommendIntoStoryShape() {
        val story = videoItemToStoryItem(
            VideoItem(
                id = 9L,
                bvid = "BV_HOME",
                aid = 900L,
                cid = 0L,
                title = "Home clip",
                pic = "https://img/home.jpg",
                owner = Owner(mid = 1L, name = "UP", face = "f"),
                stat = Stat(view = 12, danmaku = 3, like = 4),
                duration = 88,
            )
        )
        val mapped = assertNotNull(story)
        assertEquals("Home clip", mapped.title)
        assertEquals("BV_HOME", mapped.playerArgs?.bvid)
        assertEquals(900L, mapped.playerArgs?.aid)
        assertEquals(0L, mapped.playerArgs?.cid)

        val feed = buildStoryPortraitFeed(listOf(mapped))
        val portrait = assertNotNull(feed)
        assertEquals("BV_HOME", portrait.initialInfo.bvid)
    }

    @Test
    fun videoItemToStoryItem_rejectsItemsWithoutPlayableId() {
        assertNull(
            videoItemToStoryItem(
                VideoItem(bvid = "", aid = 0L, title = "x")
            )
        )
    }

    private fun storyItem(
        id: Long,
        aid: Long,
        bvid: String = "BV_$aid",
        cid: Long = aid + 1000L
    ): StoryItem {
        return StoryItem(
            id = id,
            title = "story $id",
            cover = "https://example.com/$id.jpg",
            duration = 60,
            owner = StoryOwner(mid = 10L + id, name = "up $id", face = "https://example.com/$id.png"),
            stat = StoryStat(view = 100, like = 20, reply = 3, favorite = 5, coin = 2, share = 1, danmaku = 4),
            playerArgs = StoryPlayerArgs(
                aid = aid,
                cid = cid,
                bvid = bvid
            )
        )
    }

    @Test
    fun `storyItemToRelatedVideo maps playable story items and skips unplayable`() {
        val playable = storyItemToRelatedVideo(storyItem(id = 1, aid = 100L, bvid = "BV_100", cid = 1100L))
        assertEquals("BV_100", playable?.bvid)
        assertEquals(100L, playable?.aid)
        assertEquals("up 1", playable?.owner?.name)

        val unplayable = storyItemToRelatedVideo(
            StoryItem(id = 2, title = "no args", playerArgs = null)
        )
        assertNull(unplayable)
    }

    @Test
    fun `filterVerticalStoryItems respects rotated dimensions and falls back to lookup when dimension missing`() = kotlinx.coroutines.test.runTest {
        val lookedUp = mutableListOf<String>()
        val items = listOf(
            storyItem(id = 1, aid = 101L, bvid = "BV_VERT").copy(dimension = Dimension(1080, 1920, rotate = 0)),
            storyItem(id = 2, aid = 102L, bvid = "BV_ROTATED_LANDSCAPE").copy(dimension = Dimension(1080, 1920, rotate = 1)),
            storyItem(id = 3, aid = 103L, bvid = "BV_ROTATED_PORTRAIT").copy(dimension = Dimension(1920, 1080, rotate = 1)),
            storyItem(id = 4, aid = 104L, bvid = "BV_UNKNOWN_VERT").copy(dimension = null),
            storyItem(id = 5, aid = 105L, bvid = "BV_ZERO_DIM_LAND").copy(dimension = Dimension(0, 0, rotate = 0)),
        )

        val result = filterVerticalStoryItems(items) { bvid, _ ->
            lookedUp += bvid
            bvid == "BV_UNKNOWN_VERT"
        }

        assertEquals(
            listOf("BV_VERT", "BV_ROTATED_PORTRAIT", "BV_UNKNOWN_VERT"),
            result.map { it.playerArgs?.bvid }
        )
        assertEquals(listOf("BV_UNKNOWN_VERT", "BV_ZERO_DIM_LAND"), lookedUp)
    }
}
