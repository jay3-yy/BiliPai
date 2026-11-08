package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.DynamicAdditionalReserve
import com.android.purebilibili.data.model.response.DynamicAdditionalText
import com.android.purebilibili.data.model.response.DynamicCardButton
import com.android.purebilibili.data.model.response.DynamicCardButtonStyle
import com.android.purebilibili.data.model.response.OpusLinkCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicReserveCardSessionTest {
    private fun card(id: Long = 7) = OpusLinkCard(
        type = LINK_CARD_TYPE_RESERVE, title = "直播预约", badgeText = "预约",
        reserve = DynamicAdditionalReserve(
            rid = id, title = "直播预约", reserve_total = 12,
            desc1 = DynamicAdditionalText("10月30日 20:00"),
            desc2 = DynamicAdditionalText("12人预约"),
            desc3 = DynamicAdditionalText("查看详情", "https://live.bilibili.com/123"),
            button = DynamicCardButton(type = 1, status = 0,
                check = DynamicCardButtonStyle(text = "已预约"),
                uncheck = DynamicCardButtonStyle(text = "预约")),
        ),
    )

    @Test
    fun `result keeps details and uses server count and button status`() {
        val session = DynamicReserveCardSession(card())
        assertTrue(session.begin())
        assertFalse(session.begin())
        assertTrue(session.complete(Result.success(DynamicReserveResult("0人预约", 0, 1))))
        assertFalse(session.submitting)
        assertEquals("已预约", session.shownCard.badgeText)
        assertEquals(0L, session.shownCard.reserve?.reserve_total)
        assertEquals("10月30日 20:00 · 0人预约 · 查看详情", session.shownCard.description)
        assertEquals("https://live.bilibili.com/123", session.shownCard.reserve?.desc3?.jump_url)
    }

    @Test
    fun `failure retains original status and permits retry without stale error`() {
        val original = card()
        val session = DynamicReserveCardSession(original)
        session.begin()
        session.complete(Result.failure(IllegalStateException("请先登录")))
        assertEquals(original, session.shownCard)
        assertEquals("请先登录", session.errorMessage)
        assertFalse(session.submitting)
        assertTrue(session.begin())
        assertNull(session.errorMessage)
        session.complete(Result.success(DynamicReserveResult("13人预约", 13, 1)))
        assertEquals("已预约", session.shownCard.badgeText)
    }

    @Test
    fun `cancelling submits the status and count returned by the reservation`() {
        val session = DynamicReserveCardSession(card())
        session.begin()
        session.complete(Result.success(DynamicReserveResult("13人预约", 13, 1)))
        val cancel = resolveDynamicReserveUi(session.shownCard, "dynamic-7")!!.action
        assertEquals(1, cancel.currentButtonStatus)
        assertEquals(13L, cancel.reserveTotal)
        session.begin()
        session.complete(Result.success(DynamicReserveResult("12人预约", 12, 0)))
        assertEquals("预约", session.shownCard.badgeText)
        assertEquals(0, resolveDynamicReserveUi(session.shownCard, "dynamic-7")!!.action.currentButtonStatus)
    }

    @Test
    fun `started reservation shows the jump label and keeps its action available`() {
        val source = card()
        val reserve = requireNotNull(source.reserve)
        val jumpCard = source.copy(reserve = reserve.copy(button = requireNotNull(reserve.button).copy(
            status = 1,
            jump_url = "https://live.bilibili.com/123",
            jump_style = DynamicCardButtonStyle(text = "去观看"),
            uncheck = DynamicCardButtonStyle(text = "预约", disable = 1),
        )))
        val shown = applyDynamicReserveResult(jumpCard, DynamicReserveResult("13人预约", 13, 1))
        assertEquals("去观看", shown.badgeText)
        val ui = resolveDynamicReserveUi(shown, "dynamic-7")!!
        assertTrue(ui.actionEnabled)
        assertEquals("https://live.bilibili.com/123", ui.actionJumpUrl)
    }

    @Test
    fun `callback after leaving the page cannot update a closed session`() {
        val original = card()
        val session = DynamicReserveCardSession(original)
        session.begin()
        session.close()
        assertFalse(session.complete(Result.success(DynamicReserveResult("13人预约", 13, 1))))
        assertFalse(session.complete(Result.failure(IllegalStateException("旧请求失败"))))
        assertEquals(original, session.shownCard)
        assertNull(session.errorMessage)
        assertFalse(session.begin())
    }

    @Test
    fun `identical titles on different reservations do not share results`() {
        val first = DynamicReserveCardSession(card(7))
        val second = DynamicReserveCardSession(card(8))
        first.begin()
        first.complete(Result.success(DynamicReserveResult("13人预约", 13, 1)))
        assertEquals("已预约", first.shownCard.badgeText)
        assertEquals("预约", second.shownCard.badgeText)
        assertEquals(12L, second.shownCard.reserve?.reserve_total)
    }

    @Test
    fun `reservation and cancellation use the action disable flag while jump links stay available`() {
        val source = card()
        val button = source.reserve!!.button!!
        fun withButton(updated: DynamicCardButton) = source.copy(reserve = source.reserve!!.copy(button = updated))
        val cancelAllowed = withButton(button.copy(status = 1, check = button.check!!.copy(disable = 1)))
        assertTrue(resolveReserveActionEnabled(cancelAllowed))
        val cannotCancel = withButton(button.copy(status = 1, uncheck = button.uncheck!!.copy(disable = 1)))
        assertFalse(resolveReserveActionEnabled(cannotCancel))
        assertTrue(resolveReserveActionEnabled(cannotCancel.copy(reserve = cannotCancel.reserve!!.copy(
            button = cannotCancel.reserve!!.button!!.copy(jump_url = "https://live.bilibili.com/123"),
        ))))
        assertFalse(resolveReserveActionEnabled(withButton(DynamicCardButton(
            uncheck = DynamicCardButtonStyle(disable = 1),
        ))))
    }
}
