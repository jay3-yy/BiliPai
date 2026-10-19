package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.SpaceAggregateArchive
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceAggregateArticleSection
import com.android.purebilibili.data.model.response.SpaceAggregateAudioSection
import com.android.purebilibili.data.model.response.SpaceAggregateCard
import com.android.purebilibili.data.model.response.SpaceAggregateData
import com.android.purebilibili.data.model.response.SpaceAggregateFavoriteItem
import com.android.purebilibili.data.model.response.SpaceAggregateFavoriteSection
import com.android.purebilibili.data.model.response.SpaceAggregateImages
import com.android.purebilibili.data.model.response.SpaceAggregateLevelInfo
import com.android.purebilibili.data.model.response.SpaceAggregateLikes
import com.android.purebilibili.data.model.response.SpaceAggregateRelation
import com.android.purebilibili.data.model.response.SpaceAggregateTab
import com.android.purebilibili.data.model.response.SpaceAggregateTabItem
import com.android.purebilibili.data.model.response.SpaceArticleAuthor
import com.android.purebilibili.data.model.response.SpaceArticleCategory
import com.android.purebilibili.data.model.response.SpaceArticleItem
import com.android.purebilibili.data.model.response.SpaceAudioItem
import com.android.purebilibili.data.model.response.SpaceLiveRoom
import com.android.purebilibili.data.model.response.SpaceOfficial
import com.android.purebilibili.data.model.response.SpaceVip
import com.android.purebilibili.data.model.response.SpaceVipLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpaceAggregatedModelMappingTest {

    @Test
    fun `resolveSpaceInitialSeedFromAggregate maps core header and seeded content`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                defaultTab = "video",
                card = SpaceAggregateCard(
                    mid = "42",
                    name = "不不不加点糖",
                    face = "https://i0.hdslb.com/bfs/face/demo.jpg",
                    sign = "这里是签名",
                    sex = "女",
                    attention = 28,
                    fans = 46,
                    officialVerify = SpaceOfficial(
                        type = 0,
                        title = "认证",
                        spliceTitle = "bilibili 知名游戏UP主",
                        desc = "官方认证"
                    ),
                    vip = SpaceVip(status = 1, type = 2, label = SpaceVipLabel(text = "年度大会员")),
                    levelInfo = SpaceAggregateLevelInfo(currentLevel = 5),
                    likes = SpaceAggregateLikes(likeNum = 422L),
                    relation = SpaceAggregateRelation(status = 1, isFollow = 1)
                ),
                images = SpaceAggregateImages(
                    imgUrl = "https://i0.hdslb.com/bfs/space/cover.jpg",
                    nightImgUrl = "https://i0.hdslb.com/bfs/space/night.jpg"
                ),
                live = SpaceLiveRoom(
                    roomStatus = 1,
                    liveStatus = 1,
                    title = "直播中",
                    url = "https://live.bilibili.com/6",
                    roomId = 6L
                ),
                archive = SpaceAggregateArchive(
                    count = 2,
                    item = listOf(
                        SpaceAggregateArchiveItem(
                            aid = 1001,
                            bvid = "BV1xx411c7mD",
                            title = "第一条视频",
                            cover = "https://i0.hdslb.com/bfs/archive/a.jpg",
                            author = "UP",
                            length = "03:21",
                            play = 114,
                            reply = 12,
                            ctime = 1_710_000_000,
                            tname = "动画"
                        ),
                        SpaceAggregateArchiveItem(
                            aid = 1002,
                            bvid = "BV1xx411c7mE",
                            title = "第二条视频",
                            cover = "https://i0.hdslb.com/bfs/archive/b.jpg",
                            author = "UP",
                            length = "05:43",
                            play = 514,
                            reply = 21,
                            ctime = 1_710_000_100,
                            tname = "动画"
                        )
                    )
                ),
                article = SpaceAggregateArticleSection(
                    count = 1,
                    item = listOf(
                        SpaceArticleItem(
                            id = 7L,
                            title = "一篇专栏",
                            summary = "摘要",
                            image_urls = listOf("https://i0.hdslb.com/bfs/article/a.jpg"),
                            category = SpaceArticleCategory(name = "专栏"),
                            author = SpaceArticleAuthor(name = "UP")
                        )
                    )
                ),
                audios = SpaceAggregateAudioSection(
                    count = 1,
                    item = listOf(
                        SpaceAudioItem(
                            id = 8L,
                            title = "一段音频",
                            cover = "https://i0.hdslb.com/bfs/audio/a.jpg",
                            bvid = "BV1audio",
                            duration = 189
                        )
                    )
                ),
                favourite2 = SpaceAggregateFavoriteSection(
                    count = 1,
                    item = listOf(
                        SpaceAggregateFavoriteItem(
                            id = 12L,
                            mediaId = 12L,
                            title = "收藏夹",
                            count = 18,
                            media_count = 18
                        )
                    )
                ),
                coinArchive = SpaceAggregateArchive(
                    count = 1,
                    item = listOf(
                        SpaceAggregateArchiveItem(
                            aid = 2001L,
                            bvid = "BVcoin1",
                            title = "最近投币",
                            cover = "https://i0.hdslb.com/bfs/archive/c.jpg"
                        )
                    )
                ),
                likeArchive = SpaceAggregateArchive(
                    count = 1,
                    item = listOf(
                        SpaceAggregateArchiveItem(
                            aid = 2002L,
                            bvid = "BVlike1",
                            title = "最近点赞",
                            cover = "https://i0.hdslb.com/bfs/archive/d.jpg"
                        )
                    )
                ),
                season = SpaceAggregateArchive(
                    count = 1,
                    item = listOf(
                        SpaceAggregateArchiveItem(
                            title = "追番预览",
                            cover = "https://i0.hdslb.com/bfs/bangumi/a.jpg",
                            goto = "bangumi",
                            param = "5566"
                        )
                    )
                ),
                comic = SpaceAggregateArchive(
                    count = 1,
                    item = listOf(
                        SpaceAggregateArchiveItem(
                            title = "漫画预览",
                            cover = "https://i0.hdslb.com/bfs/manga/a.jpg",
                            param = "7788"
                        )
                    )
                ),
                tab2 = listOf(
                    SpaceAggregateTab(title = "主页", param = "home"),
                    SpaceAggregateTab(title = "动态", param = "dynamic"),
                    SpaceAggregateTab(
                        title = "投稿",
                        param = "contribute",
                        items = listOf(
                            SpaceAggregateTabItem(title = "视频", param = "video"),
                            SpaceAggregateTabItem(title = "图文", param = "article"),
                            SpaceAggregateTabItem(title = "赛季合集", param = "season_video", seasonId = 88L)
                        )
                    )
                )
            )
        )

        assertNotNull(seed)
        assertEquals("bilibili 知名游戏UP主", seed.userInfo.official.spliceTitle)
        assertEquals(42L, seed.userInfo.mid)
        assertEquals("不不不加点糖", seed.userInfo.name)
        assertEquals("https://i0.hdslb.com/bfs/space/cover.jpg", seed.userInfo.topPhoto)
        assertTrue(seed.userInfo.isFollowed)
        assertEquals(28, seed.relationStat?.following)
        assertEquals(46, seed.relationStat?.follower)
        assertEquals(422L, seed.upStat?.likes)
        assertEquals(listOf("BV1xx411c7mD", "BV1xx411c7mE"), seed.videos.map { it.bvid })
        assertEquals(2, seed.totalVideos)
        assertEquals("一篇专栏", seed.articles.single().title)
        assertEquals("一段音频", seed.audios.single().title)
        assertEquals("收藏夹", seed.homeFavoriteFolders.single().title)
        assertEquals("BVcoin1", seed.homeCoinVideos.single().bvid)
        assertEquals("BVlike1", seed.homeLikeVideos.single().bvid)
        assertEquals("5566", seed.homeBangumiItems.single().param)
        assertEquals("7788", seed.homeComicItems.single().param)
        assertEquals(SpaceMainTab.HOME, seed.defaultMainTab)
        assertEquals(SpaceSubTab.VIDEO, seed.defaultSubTab)
        assertEquals(listOf("主页", "动态", "投稿", "合集", "收藏", "追番"), seed.mainTabs.map { it.title })
        assertEquals(listOf("视频", "图文", "赛季合集"), seed.contributionTabs.map { it.title })
        assertEquals(88L, seed.contributionTabs.last().seasonId)
    }

    @Test
    fun `resolveSpaceInitialSeedFromAggregate falls back to provided top photos`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                card = SpaceAggregateCard(
                    mid = "24",
                    name = "fallback",
                    face = "face",
                    levelInfo = SpaceAggregateLevelInfo(currentLevel = 3)
                ),
                images = SpaceAggregateImages(imgUrl = " ", nightImgUrl = "")
            ),
            cardLargePhoto = "https://i0.hdslb.com/bfs/space/card-large.jpg",
            cardSmallPhoto = "https://i0.hdslb.com/bfs/space/card-small.jpg"
        )

        assertEquals("https://i0.hdslb.com/bfs/space/card-large.jpg", seed?.userInfo?.topPhoto)
    }

    @Test
    fun `resolveSpaceInitialSeedFromAggregate adds article contribution tab when article content exists`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                card = SpaceAggregateCard(
                    mid = "24",
                    name = "article-up",
                    face = "face",
                    levelInfo = SpaceAggregateLevelInfo(currentLevel = 3)
                ),
                article = SpaceAggregateArticleSection(
                    count = 1,
                    item = listOf(SpaceArticleItem(id = 9L, title = "图文内容"))
                ),
                tab2 = listOf(
                    SpaceAggregateTab(
                        title = "投稿",
                        param = "contribute",
                        items = listOf(SpaceAggregateTabItem(title = "视频", param = "video"))
                    )
                )
            )
        )

        assertEquals(
            listOf("视频", "图文"),
            seed?.contributionTabs?.map { it.title }
        )
    }

    @Test
    fun `resolveSpaceInitialSeedFromAggregate returns null when card identity is missing`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                card = SpaceAggregateCard(mid = "", name = "", face = "")
            )
        )

        assertNull(seed)
    }
}
