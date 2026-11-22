package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.OpusMajor
import com.android.purebilibili.data.model.response.ReplyControl
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyTop
import com.android.purebilibili.data.model.response.ReplyUpper
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentPayload
import com.android.purebilibili.feature.share.ReplyShareContext
import com.android.purebilibili.feature.share.articleReplyShareContext
import com.android.purebilibili.feature.share.bangumiReplyShareContext
import com.android.purebilibili.feature.share.bgmReplyShareContext
import com.android.purebilibili.feature.share.commentDetailReplyShareContext
import com.android.purebilibili.feature.share.dynamicReplyShareContext
import com.android.purebilibili.feature.share.matchReplyShareContext
import com.android.purebilibili.feature.share.videoReplyShareContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyMenuConsistencyTest {

    @Test
    fun `resolveReplyMenuBindings unifies pinned fraud report and top actions`() {
        var reported: Pair<ReplyItem, Int>? = null
        var checkedFraud: ReplyItem? = null
        var toggledTop: ReplyItem? = null
        val callbacks = ReplyMenuCallbacks(
            onReport = { item, reason -> reported = item to reason },
            onCheckFraud = { item -> checkedFraud = item },
            onToggleTop = { item -> toggledTop = item },
        )

        val ownRootByMemberMid = ReplyItem(
            rpid = 1001L,
            root = 0L,
            mid = 0L,
            member = ReplyMember(mid = "42", uname = "UP本人"),
        )
        val ownSubReply = ReplyItem(
            rpid = 1002L,
            root = 1001L,
            mid = 42L,
            member = ReplyMember(mid = "42", uname = "UP本人"),
        )
        val otherRootReply = ReplyItem(
            rpid = 1003L,
            root = 0L,
            mid = 99L,
            member = ReplyMember(mid = "99", uname = "路人"),
        )

        val upRootBindings = resolveReplyMenuBindings(
            item = ownRootByMemberMid,
            currentMid = 42L,
            upMid = 42L,
            pinnedReplyIds = setOf(1001L),
            callbacks = callbacks,
        )
        assertTrue(upRootBindings.isPinned)
        assertTrue(upRootBindings.canToggleTop)
        assertNotNull(upRootBindings.onCheckFraudClick)
        assertNotNull(upRootBindings.onReportClick)
        upRootBindings.onReportClick.invoke(4)
        upRootBindings.onCheckFraudClick.invoke()
        upRootBindings.onToggleTopClick.invoke()
        assertEquals(ownRootByMemberMid to 4, reported)
        assertEquals(ownRootByMemberMid, checkedFraud)
        assertEquals(ownRootByMemberMid, toggledTop)

        val ownSubBindings = resolveReplyMenuBindings(
            item = ownSubReply,
            currentMid = 42L,
            upMid = 42L,
            pinnedReplyIds = setOf(1001L),
            callbacks = callbacks,
        )
        assertFalse(ownSubBindings.isPinned)
        assertFalse(ownSubBindings.canToggleTop)
        assertNotNull(ownSubBindings.onCheckFraudClick)
        assertNotNull(ownSubBindings.onReportClick)

        val otherByNonUpBindings = resolveReplyMenuBindings(
            item = otherRootReply,
            currentMid = 77L,
            upMid = 42L,
            pinnedReplyIds = setOf(1001L),
            callbacks = callbacks,
        )
        assertFalse(otherByNonUpBindings.isPinned)
        assertFalse(otherByNonUpBindings.canToggleTop)
        assertNull(otherByNonUpBindings.onCheckFraudClick)
        assertNotNull(otherByNonUpBindings.onReportClick)

        val guestBindings = resolveReplyMenuBindings(
            item = otherRootReply,
            currentMid = 0L,
            upMid = 42L,
            pinnedReplyIds = emptySet(),
            callbacks = callbacks,
        )
        assertFalse(guestBindings.canToggleTop)
        assertNull(guestBindings.onCheckFraudClick)
        assertNotNull(guestBindings.onReportClick)
    }

    @Test
    fun `share context builders and match share url fallback produce canonical urls`() {
        assertEquals(
            ReplyShareContext(url = "https://www.bilibili.com/video/BV1abc", title = "标题", cover = "c", page = 2),
            videoReplyShareContext(bvid = "BV1abc", title = "标题", cover = "c", page = 2),
        )
        assertNull(videoReplyShareContext(bvid = "   "))

        assertEquals(
            ReplyShareContext(url = "https://www.bilibili.com/bangumi/play/ep12345", title = "番剧", cover = "c"),
            bangumiReplyShareContext(episodeId = 12345L, title = "番剧", cover = "c"),
        )
        assertNull(bangumiReplyShareContext(episodeId = 0L))

        val dynamicItem = DynamicItem(
            id_str = "987654",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(name = "作者"),
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(opus = OpusMajor(title = "图文动态")),
                ),
            ),
        )
        assertEquals(
            ReplyShareContext(url = "https://t.bilibili.com/987654", title = "图文动态", cover = ""),
            dynamicReplyShareContext(dynamicId = "987654", item = dynamicItem),
        )
        assertEquals(
            ReplyShareContext(url = "https://www.bilibili.com/read/cv321", title = "专栏", cover = "art"),
            articleReplyShareContext(articleId = 321L, title = "专栏", cover = "art"),
        )
        assertEquals(
            ReplyShareContext(
                url = "https://music.bilibili.com/h5/music-detail?music_id=MA%201",
                title = "BGM",
                cover = "bgm",
            ),
            bgmReplyShareContext(musicId = "MA 1", title = "BGM", cover = "bgm"),
        )
        assertEquals(
            ReplyShareContext(url = "https://www.bilibili.com/h5/match/data/detail/555", title = "赛事", cover = ""),
            matchReplyShareContext(cid = 555L, title = "赛事"),
        )
        assertEquals(
            ReplyShareContext(url = "https://www.bilibili.com/h5/match/data/detail/555", title = ""),
            commentDetailReplyShareContext(oid = 555L, type = 27, enterUri = ""),
        )

        val matchShareUrl = resolveReplyCommentShareUrl(
            item = ReplyItem(rpid = 88L, oid = 555L, replyType = 27),
            context = null,
        )
        assertTrue(matchShareUrl.startsWith("bilibili://comment/detail/27/555/88/?enterUri="))
        assertTrue(
            matchShareUrl.contains("https%3A%2F%2Fwww.bilibili.com%2Fh5%2Fmatch%2Fdata%2Fdetail%2F555")
        )
    }

    @Test
    fun `resolveDynamicCommentPayload populates pinnedReplyIds from top replies`() {
        val topReply = ReplyItem(rpid = 10L, replyControl = ReplyControl(isUpTop = true))
        val upperTop = ReplyItem(rpid = 20L)
        val normalReply = ReplyItem(rpid = 30L)
        val payload = resolveDynamicCommentPayload(
            data = ReplyData(
                top = ReplyTop(upper = topReply),
                upper = ReplyUpper(mid = 1L, top = upperTop),
                replies = listOf(normalReply),
            ),
            fallbackCount = 3,
        )

        assertEquals(setOf(10L, 20L), payload.pinnedReplyIds)
        assertEquals(listOf(10L, 20L, 30L), payload.replies.map { it.rpid })
    }
}
