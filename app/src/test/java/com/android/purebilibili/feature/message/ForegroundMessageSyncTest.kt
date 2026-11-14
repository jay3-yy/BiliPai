package com.android.purebilibili.feature.message

import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.model.response.SessionMessage
import com.android.purebilibili.data.repository.MessageCenterUnread
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundMessageSyncTest {
    private fun session(key: Long, talker: Long = 2L) = SessionItem(
        talker_id = talker, unread_count = 1,
        last_msg = SessionMessage(msg_key = key, sender_uid = talker),
    )

    @Test
    fun firstSuccessfulSessionListEstablishesBaselineWithoutHints() {
        val tracker = ForegroundPrivateMessageTracker()
        assertTrue(tracker.observe(listOf(session(10)), selfMid = 1L).isEmpty())
        assertTrue(tracker.observe(listOf(session(10)), selfMid = 1L).isEmpty())
    }

    @Test
    fun incomingMessageIsDetectedEvenWithUnchangedUnreadCounts() {
        val tracker = ForegroundPrivateMessageTracker()
        tracker.observe(listOf(session(10)), selfMid = 1L)
        assertEquals(listOf(session(11)), tracker.observe(listOf(session(11)), selfMid = 1L))
        assertTrue(tracker.observe(listOf(session(11)), selfMid = 1L).isEmpty())
    }

    @Test
    fun temporarilyMissingSessionDoesNotReplayItsOldUnreadMessage() {
        val tracker = ForegroundPrivateMessageTracker()
        tracker.observe(listOf(session(10)), selfMid = 1L)
        tracker.observe(emptyList(), selfMid = 1L)
        assertTrue(tracker.observe(listOf(session(10)), selfMid = 1L).isEmpty())
    }

    @Test
    fun oldMutedAndOutgoingMessagesAreRememberedWithoutLaterReplay() {
        val tracker = ForegroundPrivateMessageTracker()
        tracker.observe(emptyList(), selfMid = 1L)
        val muted = session(10).copy(is_dnd = 1)
        val outgoing = session(20, talker = 3L).copy(last_msg = SessionMessage(msg_key = 20, sender_uid = 1L))
        assertTrue(tracker.observe(listOf(muted, outgoing), selfMid = 1L).isEmpty())
        assertTrue(tracker.observe(listOf(muted.copy(is_dnd = 0), outgoing), selfMid = 1L).isEmpty())
        assertEquals(listOf(session(11)), tracker.observe(listOf(session(11), outgoing), selfMid = 1L))
    }

    @Test
    fun interactionIncreaseTriggersEvenWhenOtherReadMessagesKeepTotalUnchanged() {
        val before = MessageCenterUnread(total = 3, reply = 1, at = 0, like = 0, sysMsg = 0)
        assertTrue(hasNewForegroundInteractions(before, before.copy(reply = 2)))
        assertFalse(hasNewForegroundInteractions(before, before))
        assertFalse(hasNewForegroundInteractions(before, before.copy(total = 1, reply = 0)))
        // 私信总数增长仍须由会话查询确认，以排除免打扰、拦截和自己发送的消息。
        assertFalse(hasNewForegroundInteractions(before, before.copy(total = 4)))
    }
}
