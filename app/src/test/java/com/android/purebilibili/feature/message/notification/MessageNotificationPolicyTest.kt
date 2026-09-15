package com.android.purebilibili.feature.message.notification

import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicFeedData
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.FollowedLiveData
import com.android.purebilibili.data.model.response.FollowedLiveRoom
import com.android.purebilibili.data.model.response.PageInfo
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.model.response.SessionMessage
import com.android.purebilibili.data.model.response.SystemNoticeItem
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageNotificationPolicyTest {
    private val enabled = MessageNotificationSettings(enabled = true)
    private val dynamicOnly = enabled.copy(notifyMessageCenter = false, notifyLiveAlerts = false)
    private val liveOnly = enabled.copy(notifyMessageCenter = false, notifyDynamicUpdates = false)

    @Test
    fun timelyModeShortensBothSchedulesWithoutBatteryConstraint() {
        val saving = resolveMessageNotificationTiming(MessageNotificationMode.POWER_SAVING)
        val timely = resolveMessageNotificationTiming(MessageNotificationMode.MORE_TIMELY)
        assertEquals(MessageNotificationTiming(30, 120, true), saving)
        assertEquals(MessageNotificationTiming(15, 60, false), timely)
    }

    @Test
    fun privateMessagesRespectReadStateMutingSenderAndSessionIdentity() {
        val message = SessionItem(talker_id = 9, unread_count = 1, last_msg = SessionMessage(sender_uid = 9, msg_key = 100))
        val sessions = listOf(
            message,
            message.copy(session_type = 2),
            message.copy(talker_id = 10, unread_count = 0),
            message.copy(talker_id = 11, is_dnd = 1),
            message.copy(talker_id = 12, is_intercept = 1),
            message.copy(talker_id = 13, last_msg = message.last_msg?.copy(sender_uid = 1)),
            message.copy(talker_id = 14, last_msg = null),
            message.copy(talker_id = 15, last_msg = message.last_msg?.copy(msg_key = 0)),
            message.copy(talker_id = 0),
        )
        val result = filterNewPrivateSessions(sessions, 1, mapOf("9_1" to 100L))
        assertEquals(listOf("9_2"), result.map(::sessionNotificationKey))
        assertEquals(listOf("9_1", "9_2"), filterNewPrivateSessions(sessions, 1, emptyMap()).map(::sessionNotificationKey))
    }

    @Test
    fun boundedSeenRetainsNewestItemsEvenWithPageOverlap() {
        val seen = mergeNotificationSeen(listOf(1L, 2L, 3L), listOf(6L, 5L, 4L, 3L), 4)
        assertEquals(listOf(3L, 4L, 5L, 6L), seen)
        assertEquals(emptyList(), diffNewIds(listOf(6L, 5L, 4L, 3L), seen, true))
        assertEquals(listOf(7L), diffNewIds(listOf(7L, 7L, 6L, 0L), seen, true))
        assertEquals(emptyList(), diffNewIds(listOf(7L), seen, false))
    }

    @Test
    fun dynamicEligibilityExcludesAdsFoldedSelfAndUnfollowedAuthors() {
        val item = dynamic("1")
        assertTrue(shouldNotifyDynamicItem(item, 1))
        assertFalse(shouldNotifyDynamicItem(item.copy(type = "DYNAMIC_TYPE_AD"), 1))
        assertFalse(shouldNotifyDynamicItem(item.copy(type = "DYNAMIC_TYPE_BANNER"), 1))
        assertFalse(shouldNotifyDynamicItem(item.copy(visible = false), 1))
        assertFalse(shouldNotifyDynamicItem(item, 9))
        assertFalse(shouldNotifyDynamicItem(item.copy(modules = item.modules.copy(
            module_author = DynamicAuthorModule(mid = 9, following = false))), 1))
        assertFalse(shouldNotifyDynamicItem(DynamicItem(), 1))
        assertEquals("video_player/BV1xx411c7mD?cid=0&aid=0&commentRootRpid=0&commentTargetRpid=0", resolveDynamicNotificationRoute(item))
    }

    @Test
    fun liveSessionsOnlyNotifyForRealStartTransitions() {
        val room = live(9, 100)
        val initial = resolveLiveSessionTransitions(listOf(room, live(10, 1).copy(liveStatus = 2)), emptyMap(), false)
        assertEquals(emptyList(), initial.first)
        assertEquals(mapOf(9L to 100L), initial.second)
        assertEquals(emptyList(), resolveLiveSessionTransitions(listOf(room), initial.second, true).first)
        assertEquals(listOf(room.copy(liveTime = 200)), resolveLiveSessionTransitions(listOf(room.copy(liveTime = 200)), initial.second, true).first)
        val offline = resolveLiveSessionTransitions(emptyList(), initial.second, true)
        assertEquals(emptyMap(), offline.second)
        assertEquals(listOf(room), resolveLiveSessionTransitions(listOf(room), offline.second, true).first)
        val unknownTime = room.copy(liveTime = 0)
        assertEquals(emptyList(), resolveLiveSessionTransitions(listOf(unknownTime), mapOf(9L to 0L), true).first)
    }

    @Test
    fun firstScanSilentlyInitializesEveryCategoryThenUnchangedScanIsSilent() = runTest {
        val source = Source().apply {
            sessions = listOf(SessionItem(talker_id = 9, unread_count = 3, last_msg = SessionMessage(sender_uid = 9, msg_key = 1)))
            replies = listOf(1L)
            ats = listOf(2L)
            likes = listOf(3L)
            notices = listOf(SystemNoticeItem(cursor = 7, title = "notice"))
            pages = mapOf("" to DynamicFeedData(items = listOf(dynamic("d1")), update_baseline = "b1"))
            rooms = mapOf(1 to FollowedLiveData(list = listOf(live(9, 100)), livingNum = 1))
        }
        val poller = MessageNotificationPoller(source)
        val initial = poller.check(enabled, AccountNotificationState(), 1)
        assertEquals(emptyList(), initial.notifications)
        assertEquals(setOf("dm", "reply", "at", "like", "sysmsg", "dynamic", "live"), initial.state.initialized)
        assertEquals("b1", initial.state.dynamicBaseline)
        assertEquals(emptyList(), poller.check(enabled, initial.state, 1).notifications)
    }

    @Test
    fun businessFailureDoesNotInitializeCategoryAndLaterRecoveryIsSilent() = runTest {
        val source = Source().apply { replyError = IllegalStateException("not logged in") }
        val poller = MessageNotificationPoller(source)
        val first = poller.check(enabled, AccountNotificationState(), 1)
        assertFalse("reply" in first.state.initialized)
        assertFalse(first.shouldRetry)
        source.replyError = null
        source.replies = listOf(10L)
        source.ats = listOf(20L)
        val next = poller.check(enabled, first.state, 1)
        assertEquals(listOf("at"), next.notifications.map { it.key })
        assertTrue("reply" in next.state.initialized)
    }

    @Test
    fun dynamicNoUpdatesNeverFetchesFeedOrAdvancesBaseline() = runTest {
        val source = Source().apply { updates = 0 }
        val previous = AccountNotificationState(initialized = setOf("dynamic"), dynamicBaseline = "b0", seenDynamicIds = listOf("d0"))
        val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1)
        assertEquals(previous, result.state)
        assertEquals(emptyList(), source.feedOffsets)
        assertEquals(emptyList(), result.notifications)
    }

    @Test
    fun failedDynamicSecondPageKeepsBaselineAndSeenWhileOtherCategoryCommits() = runTest {
        val source = Source().apply {
            replies = listOf(10L)
            pages = mapOf("" to DynamicFeedData(items = listOf(dynamic("d1")), has_more = true, offset = "next", update_baseline = "b1"))
            feedErrorOffset = "next"
        }
        val previous = AccountNotificationState(initialized = setOf("dynamic", "reply"), dynamicBaseline = "b0", seenDynamicIds = listOf("d0"))
        val result = MessageNotificationPoller(source).check(enabled, previous, 1)
        assertEquals("b0", result.state.dynamicBaseline)
        assertEquals(listOf("d0"), result.state.seenDynamicIds)
        assertEquals(listOf("reply"), result.notifications.map { it.key })
        assertTrue(result.shouldRetry)
        source.feedErrorOffset = null
        source.pages = source.pages + ("next" to DynamicFeedData(items = listOf(dynamic("d2")), update_baseline = "wrong-page-baseline"))
        val recovered = MessageNotificationPoller(source).check(enabled, result.state, 1)
        assertEquals("b1", recovered.state.dynamicBaseline)
        assertEquals(listOf("dynamic:d1", "dynamic:d2"), recovered.notifications.map { it.key })
        assertEquals(emptyList(), MessageNotificationPoller(source).check(enabled, recovered.state, 1).notifications)
    }

    @Test
    fun repeatedDynamicOffsetAndLargeUpdateBatchAreBounded() = runTest {
        val source = Source().apply {
            pages = mapOf(
                "" to DynamicFeedData(items = (1..8).map { dynamic("d$it") }, has_more = true, offset = "next", update_baseline = "b1"),
                "next" to DynamicFeedData(items = listOf(dynamic("d9")), has_more = true, offset = "next"),
            )
        }
        val previous = AccountNotificationState(initialized = setOf("dynamic"), dynamicBaseline = "b0")
        val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1)
        assertEquals(listOf("", "next"), source.feedOffsets)
        assertEquals(listOf("dynamic:d1", "dynamic:d2", "dynamic:d3", "dynamic:summary"), result.notifications.map { it.key })
        assertEquals((1..9).map { "d$it" }.toSet(), result.state.seenDynamicIds.toSet())
    }

    @Test
    fun systemCursorIsOpaqueAndComparedOnlyByEquality() = runTest {
        val source = Source().apply { notices = listOf(SystemNoticeItem(cursor = -3, title = "new"), SystemNoticeItem(cursor = 8)) }
        val previous = AccountNotificationState(initialized = setOf("sysmsg"), seenSystemCursors = listOf(8))
        val result = MessageNotificationPoller(source).check(enabled.copy(notifyDynamicUpdates = false, notifyLiveAlerts = false), previous, 1)
        assertEquals(listOf("sysmsg"), result.notifications.map { it.key })
        assertEquals(listOf(8L, -3L), result.state.seenSystemCursors)
    }

    @Test
    fun failedLiveScanPreservesPriorSessionsAndCappedScanDoesNotInventOfflineTransitions() = runTest {
        val source = Source().apply {
            rooms = mapOf(1 to FollowedLiveData(listOf(live(9, 100)), livingNum = 20))
            liveErrorPage = 2
        }
        val previous = AccountNotificationState(initialized = setOf("live"), liveSessions = mapOf(99L to 200L))
        val failure = MessageNotificationPoller(source).check(liveOnly, previous, 1)
        assertEquals(previous, failure.state)
        assertEquals(emptyList(), failure.notifications)
        assertTrue(failure.shouldRetry)
        source.liveErrorPage = null
        source.rooms = (1..10).associateWith { FollowedLiveData(listOf(live(it.toLong(), 100)), livingNum = 20, pageinfo = PageInfo(total_page = 20)) }
        val capped = MessageNotificationPoller(source).check(liveOnly, previous, 1)
        assertEquals(200L, capped.state.liveSessions[99L])
        assertEquals((1..5).map { "live:$it" }, capped.notifications.map { it.key })
    }

    @Test
    fun cancellationAndAccountChangeAbortRatherThanCommitPartialCategories() = runTest {
        val source = Source().apply { replyError = CancellationException("stop") }
        assertFailsWith<CancellationException> { MessageNotificationPoller(source).check(enabled, AccountNotificationState(), 1) }
        var checks = 0
        val changed = MessageNotificationPoller(Source()) {
            if (++checks == 2) throw CancellationException("account changed during request")
        }
        assertFailsWith<CancellationException> { changed.check(enabled, AccountNotificationState(), 1) }
    }

    private fun dynamic(id: String) = DynamicItem(
        id_str = id,
        modules = DynamicModules(
            module_author = DynamicAuthorModule(mid = 9, name = "UP", following = true),
            module_dynamic = DynamicContentModule(major = DynamicMajor(archive = ArchiveMajor(bvid = "BV1xx411c7mD"))),
        ),
    )

    private fun live(uid: Long, time: Long) = FollowedLiveRoom(roomid = uid, uid = uid, liveStatus = 1, liveTime = time)

    private class Source : MessageNotificationSource {
        var sessions = emptyList<SessionItem>()
        var replies = emptyList<Long>()
        var ats = emptyList<Long>()
        var likes = emptyList<Long>()
        var notices = emptyList<SystemNoticeItem>()
        var replyError: Exception? = null
        var updates = 1
        var pages = mapOf("" to DynamicFeedData(update_baseline = "b0"))
        var rooms = emptyMap<Int, FollowedLiveData>()
        var feedErrorOffset: String? = null
        var liveErrorPage: Int? = null
        val feedOffsets = mutableListOf<String>()
        override suspend fun sessions() = sessions
        override suspend fun replyIds(): List<Long> { replyError?.let { throw it }; return replies }
        override suspend fun atIds() = ats
        override suspend fun likeIds() = likes
        override suspend fun systemNotices() = notices
        override suspend fun dynamicUpdateCount(baseline: String) = updates
        override suspend fun dynamicFeed(baseline: String, offset: String): DynamicFeedData {
            feedOffsets += offset
            if (offset == feedErrorOffset) throw IOException("network interrupted")
            return pages.getValue(offset)
        }
        override suspend fun followedLive(page: Int): FollowedLiveData {
            if (page == liveErrorPage) throw IOException("network interrupted")
            return rooms[page] ?: FollowedLiveData()
        }
    }
}
