package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryItem
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryNavigationPolicyTest {

    @Test
    fun `history lookup key prefers bvid for archive items`() {
        val key = resolveHistoryLookupKey(
            HistoryItem(
                videoItem = VideoItem(
                    bvid = "BV1archive",
                    id = 123L
                ),
                business = HistoryBusiness.ARCHIVE
            )
        )

        assertEquals("BV1archive", key)
    }

    @Test
    fun `history lookup key falls back to live render key when bvid is blank`() {
        val key = resolveHistoryLookupKey(
            HistoryItem(
                videoItem = VideoItem(
                    id = 9988L,
                    title = "直播回放"
                ),
                business = HistoryBusiness.LIVE,
                roomId = 445566L
            )
        )

        assertEquals("live_445566", key)
    }

    @Test
    fun `history navigation kind resolves article entries explicitly`() {
        val kind = resolveHistoryNavigationKind(
            HistoryItem(
                videoItem = VideoItem(
                    id = 334455L,
                    title = "专栏"
                ),
                business = HistoryBusiness.ARTICLE
            )
        )

        assertEquals(HistoryNavigationKind.ARTICLE, kind)
    }

    @Test
    fun `history navigation kind resolves cheese entries explicitly`() {
        val kind = resolveHistoryNavigationKind(
            HistoryItem(
                videoItem = VideoItem(
                    id = 667788L,
                    title = "精品课程"
                ),
                business = HistoryBusiness.CHEESE,
                seasonId = 1234L,
                epid = 5678L
            )
        )

        assertEquals(HistoryNavigationKind.CHEESE, kind)
    }

    @Test
    fun `pgc history card hides up badge and uses content type when owner is missing`() {
        val item = HistoryItem(
            videoItem = VideoItem(
                title = "名侦探柯南：犯人犯泽先生",
                owner = Owner()
            ),
            business = HistoryBusiness.PGC
        )

        val presentation = requireNotNull(resolveHistoryCardPresentation(item))

        assertEquals(false, presentation.showUpBadge)
        assertEquals("番剧", presentation.videoItem.owner.name)
    }

    @Test
    fun `archive history card follows global up badge visibility`() {
        val item = HistoryItem(
            videoItem = VideoItem(title = "普通视频"),
            business = HistoryBusiness.ARCHIVE
        )

        val presentation = requireNotNull(resolveHistoryCardPresentation(item))

        assertNull(presentation.showUpBadge)
    }

    @Test
    fun `pgc container opens player or detail using available episode identity`() {
        val item = HistoryItem(
            videoItem = VideoItem(title = "番剧"), business = HistoryBusiness.PGC,
            seasonId = 123L, epid = 456L,
        )
        assertEquals(
            com.android.purebilibili.navigation3.BiliPaiNavKey.BangumiPlayer(0L, 456L),
            resolveHistoryCardContainerTarget(item),
        )
        assertEquals(
            com.android.purebilibili.navigation3.BiliPaiNavKey.BangumiDetail(123L),
            resolveHistoryCardContainerTarget(item.copy(epid = 0L)),
        )
        assertNull(resolveHistoryCardContainerTarget(item.copy(seasonId = 0L, epid = 0L)))
    }

    @Test
    fun `course container keeps course playback and resume position`() {
        val item = HistoryItem(
            videoItem = VideoItem(), business = HistoryBusiness.CHEESE,
            seasonId = 123L, epid = 456L, progress = 42,
        )
        assertEquals(
            com.android.purebilibili.navigation3.BiliPaiNavKey.BangumiPlayer(
                seasonId = 123L, epId = 456L, resumePositionMs = 42_000L, isCourse = true,
            ),
            resolveHistoryCardContainerTarget(item),
        )
    }

    @Test
    fun `live container retains room title and host without treating room as video`() {
        val item = HistoryItem(
            videoItem = VideoItem(title = "直播", owner = Owner(name = "主播")),
            business = HistoryBusiness.LIVE, roomId = 9988L,
        )
        assertEquals(
            com.android.purebilibili.navigation3.BiliPaiNavKey.Live(roomId = "9988", title = "直播", uname = "主播"),
            resolveHistoryCardContainerTarget(item),
        )
        assertNull(resolveHistoryCardContainerTarget(item.copy(roomId = 0L)))
    }

    @Test
    fun `article container retains article and opus targets while video keeps video navigation`() {
        val article = HistoryItem(
            videoItem = VideoItem(id = 123L, title = "专栏"), business = HistoryBusiness.ARTICLE,
        )
        assertEquals(
            com.android.purebilibili.navigation3.BiliPaiNavKey.ArticleDetail(123L, "专栏"),
            resolveHistoryCardContainerTarget(article),
        )
        val opusId = 1_000_000_000_000_001L
        assertEquals(
            com.android.purebilibili.navigation3.BiliPaiNavKey.DynamicDetail(opusId.toString()),
            resolveHistoryCardContainerTarget(article.copy(videoItem = article.videoItem.copy(id = opusId))),
        )
        assertNull(resolveHistoryCardContainerTarget(article.copy(business = HistoryBusiness.ARCHIVE)))
        assertNull(resolveHistoryCardContainerTarget(article.copy(business = HistoryBusiness.UNKNOWN)))
    }

}
