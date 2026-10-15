package com.android.purebilibili.feature.message.notification

import com.android.purebilibili.core.network.grpc.DynamicPersonalFeedPage
import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.DynamicTagModule
import com.android.purebilibili.data.model.response.FollowedLiveData
import com.android.purebilibili.data.model.response.FollowedLiveRoom
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.model.response.FollowingsData
import com.android.purebilibili.data.model.response.MessageFeedUnreadData
import com.android.purebilibili.data.model.response.PageInfo
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.model.response.SessionMessage
import com.android.purebilibili.data.model.response.SystemNoticeItem
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageNotificationPolicyTest {
    private val enabled = MessageNotificationSettings(enabled = true)
    private val dynamicOnly = enabled.copy(
        notifyPrivateMessages = false, notifyReplies = false, notifyAtMe = false,
        notifyLikes = false, notifySystemNotices = false, notifyLiveAlerts = false,
    )
    private val liveOnly = enabled.copy(
        notifyPrivateMessages = false, notifyReplies = false, notifyAtMe = false,
        notifyLikes = false, notifySystemNotices = false, notifyDynamicUpdates = false,
    )
    private val interactionsOnly = enabled.copy(
        notifyPrivateMessages = false, notifySystemNotices = false,
        notifyDynamicUpdates = false, notifyLiveAlerts = false,
    )

    @Test
    fun timelyModeShortensBothSchedulesWithoutBatteryConstraint() {
        val saving = resolveMessageNotificationTiming(MessageNotificationMode.POWER_SAVING)
        val timely = resolveMessageNotificationTiming(MessageNotificationMode.MORE_TIMELY)
        assertEquals(MessageNotificationTiming(30, 120, true), saving)
        assertEquals(MessageNotificationTiming(15, 60, false), timely)
    }

    @Test
    fun residentPollDropsToIdleFloorOnlyWhenScreenOffAndDischarging() {
        assertEquals(60_000L, resolveResidentPollDelayMs(MessageNotificationMode.MORE_TIMELY, screenOn = true, charging = false))
        assertEquals(60_000L, resolveResidentPollDelayMs(MessageNotificationMode.MORE_TIMELY, screenOn = false, charging = true))
        assertEquals(120_000L, resolveResidentPollDelayMs(MessageNotificationMode.POWER_SAVING, screenOn = true, charging = true))
        assertEquals(RESIDENT_IDLE_POLL_MS, resolveResidentPollDelayMs(MessageNotificationMode.MORE_TIMELY, screenOn = false, charging = false))
        assertEquals(RESIDENT_IDLE_POLL_MS, resolveResidentPollDelayMs(MessageNotificationMode.POWER_SAVING, screenOn = false, charging = false))
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
            unread = MessageFeedUnreadData(reply = 1, at = 2, like = 3)
            notices = listOf(SystemNoticeItem(cursor = 7, title = "notice"))
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))))
            rooms = mapOf(1 to FollowedLiveData(list = listOf(live(9, 100)), livingNum = 1))
        }
        val poller = MessageNotificationPoller(source)
        val initial = poller.check(enabled, AccountNotificationState(), 1)
        assertEquals(emptyList(), initial.notifications)
        assertEquals(mapOf(9L to "100"), initial.state.dynamicUpBaselines)
        assertEquals(emptyList(), poller.check(enabled, initial.state, 1).notifications)
    }

    @Test
    fun businessFailureDoesNotEstablishUnreadBaselinesAndRecoveryIsSilent() = runTest {
        val source = Source().apply { unreadError = IllegalStateException("not logged in") }
        val poller = MessageNotificationPoller(source)
        val first = poller.check(enabled, AccountNotificationState(), 1)
        assertEquals(null, first.state.replyUnreadCount)
        assertEquals(null, first.state.atUnreadCount)
        assertEquals(null, first.state.likeUnreadCount)
        assertFalse(first.shouldRetry)
        source.unreadError = null
        source.unread = MessageFeedUnreadData(reply = 10, at = 20)
        val next = poller.check(enabled, first.state, 1)
        assertEquals(emptyList(), next.notifications)
        assertEquals(10, next.state.replyUnreadCount)
        assertEquals(20, next.state.atUnreadCount)
    }

    @Test
    fun unreadCountsRebaselineLegacyStateThenNotifyOnlyIncreasesAndResetAfterReads() = runTest {
        val source = Source().apply { unread = MessageFeedUnreadData(reply = 5, at = 2, like = 1) }
        val poller = MessageNotificationPoller(source)
        val legacy = AccountNotificationState(initialized = setOf("reply", "at", "like"))
        val initial = poller.check(interactionsOnly, legacy, 1)
        assertEquals(emptyList(), initial.notifications)
        assertEquals(emptyList(), poller.check(interactionsOnly, initial.state, 1).notifications)

        source.unread = MessageFeedUnreadData(reply = 7, at = 4, like = 3)
        val increased = poller.check(interactionsOnly, initial.state, 1)
        assertEquals(listOf("reply", "at", "like"), increased.notifications.map { it.key })
        assertEquals(listOf(2, 2, 2), increased.notifications.map { it.text.filter(Char::isDigit).toInt() })
        assertEquals(emptyList(), poller.check(interactionsOnly, increased.state, 1).notifications)

        source.unread = MessageFeedUnreadData(at = 1)
        val read = poller.check(interactionsOnly, increased.state, 1)
        assertEquals(emptyList(), read.notifications)
        source.unread = MessageFeedUnreadData(reply = 1, at = 1)
        val next = poller.check(interactionsOnly, read.state, 1)
        assertEquals(listOf("reply"), next.notifications.map { it.key })
        assertEquals(1, next.notifications.single().text.filter(Char::isDigit).toInt())
    }

    @Test
    fun failedUnreadSnapshotPreservesBaselinesWhileSystemNoticesCommit() = runTest {
        val source = Source().apply {
            unreadError = IOException("network interrupted")
            notices = listOf(SystemNoticeItem(cursor = -3, title = "new"))
        }
        val settings = interactionsOnly.copy(notifySystemNotices = true)
        val previous = AccountNotificationState(
            initialized = setOf("sysmsg"), seenSystemCursors = listOf(8),
            replyUnreadCount = 5, atUnreadCount = 4, likeUnreadCount = 3,
        )
        val poller = MessageNotificationPoller(source)
        val failed = poller.check(settings, previous, 1)
        assertEquals(5, failed.state.replyUnreadCount)
        assertEquals(4, failed.state.atUnreadCount)
        assertEquals(3, failed.state.likeUnreadCount)
        assertEquals(listOf("sysmsg"), failed.notifications.map { it.key })
        assertTrue(failed.shouldRetry)

        source.unreadError = null
        source.unread = MessageFeedUnreadData(reply = 7, at = 5, like = 4)
        val recovered = poller.check(settings, failed.state, 1)
        assertEquals(listOf("reply", "at", "like"), recovered.notifications.map { it.key })
        assertEquals(listOf(2, 1, 1), recovered.notifications.map { it.text.filter(Char::isDigit).toInt() })
    }

    @Test
    fun enablingInteractionCategoriesEstablishesIndependentSilentBaselines() = runTest {
        val source = Source().apply { unread = MessageFeedUnreadData(reply = 6, at = 3, like = 2) }
        val poller = MessageNotificationPoller(source)
        val replyOnly = interactionsOnly.copy(notifyAtMe = false, notifyLikes = false)
        val first = poller.check(replyOnly, AccountNotificationState(replyUnreadCount = 5), 1)
        assertEquals(listOf("reply"), first.notifications.map { it.key })
        assertEquals(null, first.state.atUnreadCount)
        assertEquals(null, first.state.likeUnreadCount)
        val enabled = poller.check(interactionsOnly, first.state, 1)
        assertEquals(emptyList(), enabled.notifications)

        source.unread = MessageFeedUnreadData(reply = 6, at = 4, like = 3)
        val next = poller.check(interactionsOnly, enabled.state, 1)
        assertEquals(listOf("at", "like"), next.notifications.map { it.key })
        assertEquals(listOf(1, 1), next.notifications.map { it.text.filter(Char::isDigit).toInt() })
    }

    @Test
    fun initializedAuthorWithUnreadUpdatesNotifiesNewPublication() = runTest {
        val source = Source().apply {
            authorUpdates = mapOf(9L to true)
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110"), dynamic("100"))))
        }
        val previous = AccountNotificationState(dynamicUpBaselines = mapOf(9L to "100"))
        val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1)
        assertEquals(listOf("dynamic:110"), result.notifications.map { it.key })
        assertEquals(mapOf("110" to 9L), result.state.pendingDynamicIds)
        assertEquals(mapOf(9L to "110"), result.state.dynamicUpBaselines)
    }

    @Test
    fun failedPersonalSecondPageKeepsBaselineWhileOtherCategoryCommits() = runTest {
        val source = Source().apply {
            unread = MessageFeedUnreadData(reply = 10)
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(
                items = listOf(dynamic("120")), hasMore = true, offset = "next"))
            feedErrorOffset = "next"
        }
        val previous = AccountNotificationState(initialized = setOf("dynamic"),
            dynamicUpBaselines = mapOf(9L to "100"), replyUnreadCount = 0)
        val result = MessageNotificationPoller(source).check(enabled, previous, 1)
        assertEquals(mapOf(9L to "100"), result.state.dynamicUpBaselines)
        assertEquals(listOf("reply"), result.notifications.map { it.key })
        assertTrue(result.shouldRetry)
        source.feedErrorOffset = null
        source.pages = source.pages + ((9L to "next") to DynamicPersonalFeedPage(
            items = listOf(dynamic("110"), dynamic("100"))))
        val recovered = MessageNotificationPoller(source).check(enabled, result.state, 1)
        assertEquals("120", recovered.state.dynamicUpBaselines[9L])
        assertEquals(listOf("dynamic:120", "dynamic:110"), recovered.notifications.map { it.key })
        assertEquals(emptyList(), MessageNotificationPoller(source).check(enabled, recovered.state, 1).notifications)
    }

    @Test
    fun repeatedPersonalOffsetPreservesGapAndRecoverySummarizesNewestItems() = runTest {
        val source = Source().apply {
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = (109 downTo 102).map { dynamic("$it") },
                    hasMore = true, offset = "next"),
                (9L to "next") to DynamicPersonalFeedPage(items = listOf(dynamic("101")),
                    hasMore = true, offset = "next"),
            )
        }
        val previous = AccountNotificationState(initialized = setOf("dynamic"),
            dynamicUpBaselines = mapOf(9L to "100"))
        val poller = MessageNotificationPoller(source)
        val failed = poller.check(dynamicOnly, previous, 1)
        assertEquals(previous, failed.state)
        assertEquals(emptyList(), failed.notifications)
        source.pages = source.pages + ((9L to "next") to DynamicPersonalFeedPage(items = listOf(dynamic("101"))))
        val recovered = poller.check(dynamicOnly, failed.state, 1)
        assertEquals(listOf("dynamic:109", "dynamic:108", "dynamic:107", "dynamic:summary"),
            recovered.notifications.map { it.key })
        assertEquals(9, recovered.notifications.last().text.filter(Char::isDigit).toInt())
        assertEquals("109", recovered.state.dynamicUpBaselines[9L])
    }

    @Test
    fun newlyFollowedUpInitializesSilentlyWithoutAdvancingTheExistingProbe() = runTest {
        val source = Source().apply {
            defaultUpdateCount = 0
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))),
                (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("50", 10))),
            )
        }
        val previous = AccountNotificationState(
            dynamicProbeBaseline = "80", dynamicUpBaselines = mapOf(9L to "100"),
        )
        val initial = MessageNotificationPoller(source).check(dynamicOnly, previous, 1)
        assertEquals(emptyList(), initial.notifications)
        assertEquals(mapOf(9L to "100", 10L to "50"), initial.state.dynamicUpBaselines)
        source.updateCounts = mapOf("80" to 1, "100" to 0)
        source.pages = mapOf(
            (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))),
            (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110", 10))),
        )
        val next = MessageNotificationPoller(source).check(dynamicOnly, initial.state, 1)
        assertEquals(listOf("dynamic:110"), next.notifications.map { it.key })
    }

    @Test
    fun oneUpFailureDoesNotDiscardOtherUpsAndRecoveryOnlyNotifiesTheFailedUp() = runTest {
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110"))))
            feedErrorHost = 10L
        }
        val previous = AccountNotificationState(dynamicUpBaselines = mapOf(9L to "100", 10L to "90"))
        val poller = MessageNotificationPoller(source)
        val partial = poller.check(dynamicOnly, previous, 1)
        assertEquals(listOf("dynamic:110"), partial.notifications.map { it.key })
        assertEquals(mapOf(9L to "110", 10L to "90"), partial.state.dynamicUpBaselines)
        assertTrue(partial.shouldRetry)
        source.feedErrorHost = null
        source.pages = source.pages + ((10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120", 10))))
        val recovered = poller.check(dynamicOnly, partial.state, 1)
        assertEquals(listOf("dynamic:120"), recovered.notifications.map { it.key })
        assertEquals(mapOf(9L to "110", 10L to "120"), recovered.state.dynamicUpBaselines)
    }

    @Test
    fun followingPaginationIncludesLaterUpsAndIncompleteRefreshNeverPrunesState() = runTest {
        val source = Source().apply {
            followingPages = mapOf(
                1 to FollowingsData(list = (9L..58L).map { FollowingUser(mid = it) }, total = 51),
                2 to FollowingsData(list = listOf(FollowingUser(mid = 59)), total = 51),
            )
            pages = (9L..59L).associate { (it to "") to DynamicPersonalFeedPage(items = listOf(dynamic("$it", it))) } +
                ((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))))
        }
        val previous = AccountNotificationState(dynamicUpBaselines = mapOf(9L to "100"))
        val poller = MessageNotificationPoller(source)
        val initial = poller.check(dynamicOnly, previous, 1)
        assertEquals(emptyList(), initial.notifications)
        source.pages = source.pages + mapOf(
            (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))),
            (59L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110", 59))),
        )
        val updated = poller.check(dynamicOnly, initial.state, 1)
        assertEquals(listOf("dynamic:110"), updated.notifications.map { it.key })
        source.followingErrorPage = 2
        val failure = poller.check(dynamicOnly, updated.state, 1)
        assertEquals(updated.state, failure.state)
        assertEquals(emptyList(), failure.notifications)
        assertTrue(failure.shouldRetry)
    }

    @Test
    fun oldPinnedCardDoesNotHideNewItemsOnLaterPersonalPages() = runTest {
        val pinned = dynamic("50").copy(modules = dynamic("50").modules.copy(module_tag = DynamicTagModule("置顶")))
        val source = Source().apply {
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(pinned, dynamic("130")), offset = "next", hasMore = true),
                (9L to "next") to DynamicPersonalFeedPage(items = listOf(dynamic("120"), dynamic("100"))),
            )
        }
        val result = MessageNotificationPoller(source).check(dynamicOnly,
            AccountNotificationState(dynamicUpBaselines = mapOf(9L to "100")), 1)
        assertEquals(listOf("dynamic:130", "dynamic:120"), result.notifications.map { it.key })
        assertEquals("130", result.state.dynamicUpBaselines[9L])
    }

    @Test
    fun newRangeBeyondThreePagesIsCompletedBeforeAdvancingBaseline() = runTest {
        val source = Source().apply {
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("140")), offset = "p2", hasMore = true),
                (9L to "p2") to DynamicPersonalFeedPage(items = listOf(dynamic("130")), offset = "p3", hasMore = true),
                (9L to "p3") to DynamicPersonalFeedPage(items = listOf(dynamic("120")), offset = "p4", hasMore = true),
                (9L to "p4") to DynamicPersonalFeedPage(items = listOf(dynamic("110"), dynamic("100"))),
            )
        }
        val result = MessageNotificationPoller(source).check(dynamicOnly,
            AccountNotificationState(dynamicUpBaselines = mapOf(9L to "100")), 1)
        assertEquals(4, result.notifications.last().text.filter(Char::isDigit).toInt())
        assertEquals("140", result.state.dynamicUpBaselines[9L])
    }

    @Test
    fun successfullyEmptyPersonalFeedStillNotifiesItsFirstFutureDynamic() = runTest {
        val source = Source()
        val poller = MessageNotificationPoller(source)
        val initial = poller.check(dynamicOnly, AccountNotificationState(), 1)
        assertEquals("", initial.state.dynamicUpBaselines[9L])
        source.pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))))
        val next = poller.check(dynamicOnly, initial.state, 1)
        assertEquals(listOf("dynamic:100"), next.notifications.map { it.key })
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
        val source = Source().apply { unreadError = CancellationException("stop") }
        assertFailsWith<CancellationException> { MessageNotificationPoller(source).check(enabled, AccountNotificationState(), 1) }
        var checks = 0
        val changed = MessageNotificationPoller(Source()) {
            if (++checks == 2) throw CancellationException("account changed during request")
        }
        assertFailsWith<CancellationException> { changed.check(enabled, AccountNotificationState(), 1) }
    }
    @Test
    fun eachMessageCenterSubcategorySwitchGatesOnlyItsOwnCategory() = runTest {
        // Seed prior snapshots so every enabled category observes new messages.
        val source = Source().apply {
            sessions = listOf(SessionItem(talker_id = 90, unread_count = 1, last_msg = SessionMessage(sender_uid = 90, msg_key = 1)))
            unread = MessageFeedUnreadData(reply = 91, at = 92, like = 93, sysMsg = 1)
            notices = listOf(SystemNoticeItem(cursor = 99, title = "alert"))
        }
        val baseline = AccountNotificationState(
            initialized = setOf("dm", "sysmsg", "dynamic", "live"),
            sessionMsgKeys = mapOf("90_1" to 999L),
            replyUnreadCount = 1, atUnreadCount = 1, likeUnreadCount = 1,
            seenSystemCursors = listOf(1L), dynamicUpBaselines = mapOf(9L to "100"),
        )
        // Master enabled, all five subcategories on: every category reports one new item.
        val allOn = MessageNotificationPoller(source).check(enabled.copy(notifyDynamicUpdates = false, notifyLiveAlerts = false), baseline, 1)
        assertEquals(setOf("dm:90_1", "reply", "at", "like", "sysmsg"), allOn.notifications.map { it.key }.toSet())

        // Turning off exactly one subcategory switch removes only that category's notice.
        val dmOff = MessageNotificationPoller(source).check(enabled.copy(notifyPrivateMessages = false, notifyDynamicUpdates = false, notifyLiveAlerts = false), baseline, 1)
        assertFalse("dm:90_1" in dmOff.notifications.map { it.key })
        assertTrue(listOf("reply", "at", "like", "sysmsg").all { it in dmOff.notifications.map { n -> n.key } })

        val replyOff = MessageNotificationPoller(source).check(enabled.copy(notifyReplies = false, notifyDynamicUpdates = false, notifyLiveAlerts = false), baseline, 1)
        assertFalse("reply" in replyOff.notifications.map { it.key })
        assertTrue(listOf("dm:90_1", "at", "like", "sysmsg").all { it in replyOff.notifications.map { n -> n.key } })

        val atOff = MessageNotificationPoller(source).check(enabled.copy(notifyAtMe = false, notifyDynamicUpdates = false, notifyLiveAlerts = false), baseline, 1)
        assertFalse("at" in atOff.notifications.map { it.key })
        assertTrue(listOf("dm:90_1", "reply", "like", "sysmsg").all { it in atOff.notifications.map { n -> n.key } })

        val likeOff = MessageNotificationPoller(source).check(enabled.copy(notifyLikes = false, notifyDynamicUpdates = false, notifyLiveAlerts = false), baseline, 1)
        assertFalse("like" in likeOff.notifications.map { it.key })
        assertTrue(listOf("dm:90_1", "reply", "at", "sysmsg").all { it in likeOff.notifications.map { n -> n.key } })

        val sysmsgOff = MessageNotificationPoller(source).check(enabled.copy(notifySystemNotices = false, notifyDynamicUpdates = false, notifyLiveAlerts = false), baseline, 1)
        assertFalse("sysmsg" in sysmsgOff.notifications.map { it.key })
        assertTrue(listOf("dm:90_1", "reply", "at", "like").all { it in sysmsgOff.notifications.map { n -> n.key } })

        // All five off: no message-center notice at all.
        val allOff = MessageNotificationPoller(source).check(
            enabled.copy(notifyPrivateMessages = false, notifyReplies = false, notifyAtMe = false, notifyLikes = false, notifySystemNotices = false, notifyDynamicUpdates = false, notifyLiveAlerts = false),
            baseline, 1,
        )
        assertEquals(emptyList(), allOff.notifications)
    }

    @Test
    fun zeroUnreadCountsClearOnlyReadCategoriesAndDoNotClearPartiallyReadCategories() = runTest {
        val source = Source().apply { unread = MessageFeedUnreadData(at = 2) }
        val previous = AccountNotificationState(replyUnreadCount = 5, atUnreadCount = 4, likeUnreadCount = 3)
        val result = MessageNotificationPoller(source).check(
            interactionsOnly.copy(notifySystemNotices = true), previous, 1,
            setOf("reply", "at", "like", "sysmsg", "interactions:summary"),
        )
        assertEquals(setOf("reply", "like", "sysmsg"), result.cancelNotificationKeys)
        assertEquals(2, result.state.atUnreadCount)
        assertEquals(emptyList(), result.notifications)

        source.unreadError = IOException("unread unavailable")
        val failed = MessageNotificationPoller(source).check(
            interactionsOnly, previous, 1, setOf("reply", "at", "like"),
        )
        assertEquals(emptySet(), failed.cancelNotificationKeys)
        assertEquals(previous.replyUnreadCount, failed.state.replyUnreadCount)
    }

    @Test
    fun alreadyReadSystemNoticesDoNotReappearEvenWhenTheirCursorIsNew() = runTest {
        val source = Source().apply {
            unread = MessageFeedUnreadData()
            notices = listOf(SystemNoticeItem(cursor = 10, title = "read elsewhere"))
        }
        val result = MessageNotificationPoller(source).check(
            interactionsOnly.copy(notifySystemNotices = true),
            AccountNotificationState(initialized = setOf("sysmsg"), seenSystemCursors = listOf(9)), 1,
            setOf("sysmsg"),
        )
        assertEquals(setOf("sysmsg"), result.cancelNotificationKeys)
        assertEquals(emptyList(), result.notifications)
        assertEquals(listOf(9L, 10L), result.state.seenSystemCursors)
    }

    @Test
    fun privateReadSyncQueriesOlderNotifiedSessionsAndPreservesFailedOrUnreadSessions() = runTest {
        val source = Source().apply {
            sessions = listOf(SessionItem(talker_id = 9, unread_count = 0))
            sessionDetails = mapOf((10L to 1) to SessionItem(talker_id = 10, unread_count = 1))
            sessionDetailErrors = setOf(11L to 1)
        }
        val previous = AccountNotificationState(
            initialized = setOf("dm"), pendingPrivateSessionKeys = setOf("9_1", "10_1", "11_1"),
        )
        val privateOnly = interactionsOnly.copy(
            notifyPrivateMessages = true, notifyReplies = false, notifyAtMe = false, notifyLikes = false,
        )
        val active = setOf("dm:9_1", "dm:10_1", "dm:summary")
        val first = MessageNotificationPoller(source).check(privateOnly, previous, 1, active)
        assertEquals(setOf("dm:9_1"), first.cancelNotificationKeys)
        assertEquals(setOf("10_1", "11_1"), first.state.pendingPrivateSessionKeys)
        assertTrue(first.shouldRetry)

        source.sessionDetailErrors = emptySet()
        source.sessionDetails = mapOf(
            (10L to 1) to SessionItem(talker_id = 10, unread_count = 0),
            (11L to 1) to SessionItem(talker_id = 11, unread_count = 0),
        )
        val read = MessageNotificationPoller(source).check(
            privateOnly, first.state, 1, setOf("dm:10_1", "dm:summary"),
        )
        assertEquals(setOf("dm:10_1", "dm:11_1"), read.cancelNotificationKeys)
        assertEquals(emptySet(), read.state.pendingPrivateSessionKeys)
    }

    @Test
    fun zeroUpdateCountStillClearsOnlyTheReadAuthorsPendingNotices() = runTest {
        val source = Source().apply {
            defaultUpdateCount = 0
            feedErrorHost = 9L
            followingPages = mapOf(1 to FollowingsData(list = (9L..11L).map { FollowingUser(mid = it) }, total = 3))
            pages = (9L..11L).associate { (it to "") to DynamicPersonalFeedPage(items = listOf(dynamic("${it}0", it))) }
            authorUpdates = mapOf(9L to false, 10L to true)
        }
        val previous = AccountNotificationState(
            dynamicProbeBaseline = "110",
            dynamicUpBaselines = mapOf(9L to "90", 10L to "100", 11L to "110"),
            pendingDynamicIds = mapOf("90" to 9L, "100" to 10L, "110" to 11L),
        )
        val result = MessageNotificationPoller(source).check(
            dynamicOnly, previous, 1, setOf("dynamic:90", "dynamic:100", "dynamic:110", "dynamic:summary"),
        )
        assertEquals(setOf("dynamic:90"), result.cancelNotificationKeys)
        assertEquals(mapOf("100" to 10L, "110" to 11L), result.state.pendingDynamicIds)
        assertEquals(previous.dynamicUpBaselines, result.state.dynamicUpBaselines)
        assertEquals(emptyList(), result.notifications)
        assertFalse(result.shouldRetry)
    }

    @Test
    fun readAuthorSuppressesNewNoticesButAdvancesItsBaselineWithoutAffectingOtherAuthors() = runTest {
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120"), dynamic("110"), dynamic("100"))),
                (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("220", 10), dynamic("200", 10))),
            )
            authorUpdates = mapOf(9L to false, 10L to true)
        }
        val previous = AccountNotificationState(
            dynamicUpBaselines = mapOf(9L to "100", 10L to "200"),
            pendingDynamicIds = mapOf("105" to 9L, "205" to 10L),
        )
        val poller = MessageNotificationPoller(source)
        val result = poller.check(dynamicOnly, previous, 1, setOf("dynamic:105", "dynamic:205"))
        assertEquals(setOf("dynamic:105"), result.cancelNotificationKeys)
        assertEquals(listOf("dynamic:220"), result.notifications.map { it.key })
        assertEquals(mapOf("205" to 10L, "220" to 10L), result.state.pendingDynamicIds)
        assertEquals(mapOf(9L to "120", 10L to "220"), result.state.dynamicUpBaselines)

        source.authorUpdates = mapOf(9L to true, 10L to true)
        val unreadAgain = poller.check(dynamicOnly, result.state, 1, setOf("dynamic:205", "dynamic:220"))
        assertEquals(emptyList(), unreadAgain.notifications)
        assertEquals(emptySet(), unreadAgain.cancelNotificationKeys)
        assertEquals(result.state.pendingDynamicIds, unreadAgain.state.pendingDynamicIds)
    }

    @Test
    fun unknownAuthorStatePreservesPendingAndNotifiesNewPublications() = runTest {
        for (snapshot in listOf(emptyMap(), mapOf(10L to false))) {
            val source = Source().apply {
                authorUpdates = snapshot
                pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120"), dynamic("100"))))
            }
            val previous = AccountNotificationState(
                dynamicUpBaselines = mapOf(9L to "100"), pendingDynamicIds = mapOf("105" to 9L),
            )
            val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1, setOf("dynamic:105"))
            assertEquals(emptySet(), result.cancelNotificationKeys)
            assertEquals(listOf("dynamic:120"), result.notifications.map { it.key })
            assertEquals(mapOf("105" to 9L, "120" to 9L), result.state.pendingDynamicIds)
            assertEquals("120", result.state.dynamicUpBaselines[9L])
        }
    }

    @Test
    fun failedAuthorSnapshotDoesNotCancelOrRollBackSuccessfulPublicationScans() = runTest {
        for (failure in listOf(IOException("snapshot interrupted"), IllegalStateException("missing snapshot"))) {
            val source = Source().apply {
                authorUpdatesError = failure
                pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120"), dynamic("100"))))
            }
            val previous = AccountNotificationState(
                dynamicUpBaselines = mapOf(9L to "100"), pendingDynamicIds = mapOf("105" to 9L),
            )
            val poller = MessageNotificationPoller(source)
            val result = poller.check(dynamicOnly, previous, 1, setOf("dynamic:105"))
            assertEquals(emptySet(), result.cancelNotificationKeys)
            assertEquals(listOf("dynamic:120"), result.notifications.map { it.key })
            assertEquals(mapOf("105" to 9L, "120" to 9L), result.state.pendingDynamicIds)
            assertEquals("120", result.state.dynamicUpBaselines[9L])
            assertEquals(failure is IOException, result.shouldRetry)

            source.authorUpdatesError = null
            source.authorUpdates = mapOf(9L to true)
            val recovered = poller.check(dynamicOnly, result.state, 1, setOf("dynamic:105", "dynamic:120"))
            assertEquals(emptyList(), recovered.notifications)
            assertEquals(result.state.pendingDynamicIds, recovered.state.pendingDynamicIds)
        }
    }

    @Test
    fun newestPreviewsAreGlobalAndReadingOneAuthorRemovesItsOverflowToo() = runTest {
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = (104 downTo 100).map { dynamic("$it") }),
                (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("205", 10), dynamic("200", 10))),
            )
            authorUpdates = mapOf(9L to true, 10L to true)
        }
        val previous = AccountNotificationState(dynamicUpBaselines = mapOf(9L to "100", 10L to "200"))
        val poller = MessageNotificationPoller(source)
        val fresh = poller.check(dynamicOnly, previous, 1)
        assertEquals(listOf("dynamic:205", "dynamic:104", "dynamic:103", "dynamic:summary"),
            fresh.notifications.map { it.key })
        assertEquals(5, fresh.notifications.last().text.filter(Char::isDigit).toInt())
        assertEquals(mapOf("101" to 9L, "102" to 9L, "103" to 9L, "104" to 9L, "205" to 10L),
            fresh.state.pendingDynamicIds)

        source.authorUpdates = mapOf(9L to false, 10L to true)
        val read = poller.check(
            dynamicOnly, fresh.state, 1, setOf("dynamic:205", "dynamic:104", "dynamic:103", "dynamic:summary"),
        )
        assertEquals(setOf("dynamic:101", "dynamic:102", "dynamic:103", "dynamic:104"), read.cancelNotificationKeys)
        assertEquals(mapOf("205" to 10L), read.state.pendingDynamicIds)
        assertEquals(emptyList(), read.notifications)
    }

    @Test
    fun authorReadSnapshotCanClearPendingDespiteThatAuthorsFailedContentRange() = runTest {
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120")), offset = "next", hasMore = true),
                (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("220", 10), dynamic("200", 10))),
            )
            feedErrorOffset = "next"
            authorUpdates = mapOf(9L to false, 10L to true)
        }
        val previous = AccountNotificationState(
            dynamicUpBaselines = mapOf(9L to "100", 10L to "200"),
            pendingDynamicIds = mapOf("105" to 9L, "205" to 10L),
        )
        val result = MessageNotificationPoller(source).check(
            dynamicOnly, previous, 1, setOf("dynamic:105", "dynamic:205"),
        )
        assertEquals(setOf("dynamic:105"), result.cancelNotificationKeys)
        assertEquals(listOf("dynamic:220"), result.notifications.map { it.key })
        assertEquals(mapOf(9L to "100", 10L to "220"), result.state.dynamicUpBaselines)
        assertEquals(mapOf("205" to 10L, "220" to 10L), result.state.pendingDynamicIds)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun simultaneousContentAndAuthorSnapshotFailuresPreserveFailedAuthorAndCommitOtherAuthors() = runTest {
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            feedErrorHost = 9L
            authorUpdatesError = IOException("author snapshot interrupted")
            pages = mapOf((10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("220", 10), dynamic("200", 10))))
        }
        val previous = AccountNotificationState(
            dynamicUpBaselines = mapOf(9L to "100", 10L to "200"),
            pendingDynamicIds = mapOf("105" to 9L, "205" to 10L),
        )
        val result = MessageNotificationPoller(source).check(
            dynamicOnly, previous, 1, setOf("dynamic:105", "dynamic:205"),
        )
        assertEquals(emptySet(), result.cancelNotificationKeys)
        assertEquals(listOf("dynamic:220"), result.notifications.map { it.key })
        assertEquals(mapOf(9L to "100", 10L to "220"), result.state.dynamicUpBaselines)
        assertEquals(mapOf("105" to 9L, "205" to 10L, "220" to 10L), result.state.pendingDynamicIds)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun absentOrUnfollowedAuthorsAreNotTreatedAsRead() = runTest {
        for (followed in listOf(listOf(FollowingUser(mid = 9)), emptyList())) {
            val source = Source().apply {
                followingPages = mapOf(1 to FollowingsData(list = followed, total = followed.size))
                pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))))
                authorUpdates = emptyMap()
            }
            val previous = AccountNotificationState(
                dynamicUpBaselines = mapOf(9L to "100", 10L to "200"),
                pendingDynamicIds = mapOf("100" to 9L, "200" to 10L),
            )
            val result = MessageNotificationPoller(source).check(
                dynamicOnly, previous, 1, setOf("dynamic:100", "dynamic:200", "dynamic:summary"),
            )
            assertEquals(emptySet(), result.cancelNotificationKeys)
            assertEquals(previous.pendingDynamicIds, result.state.pendingDynamicIds)
            assertEquals(emptyList(), result.notifications)
        }
    }

    @Test
    fun authorBecomingUnreadWhileContentIsFetchedDoesNotLoseItsNewPublication() = runTest {
        val source = Source().apply {
            authorUpdates = mapOf(9L to false)
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120")), offset = "next", hasMore = true),
                (9L to "next") to DynamicPersonalFeedPage(items = listOf(dynamic("110"), dynamic("100"))),
            )
            onPersonalFeed = { _, offset ->
                if (offset == "next") authorUpdates = mapOf(9L to true)
            }
        }
        val previous = AccountNotificationState(
            dynamicUpBaselines = mapOf(9L to "100"), pendingDynamicIds = mapOf("105" to 9L),
        )
        val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1, setOf("dynamic:105"))
        assertEquals(listOf("dynamic:120", "dynamic:110"), result.notifications.map { it.key })
        assertEquals(emptySet(), result.cancelNotificationKeys)
        assertEquals(mapOf("105" to 9L, "110" to 9L, "120" to 9L), result.state.pendingDynamicIds)
        assertEquals("120", result.state.dynamicUpBaselines[9L])
    }

    @Test
    fun boundedConcurrentScansCompleteEveryAuthorsPagesBeforeQueryingReadState() = runTest {
        val mids = (9L until 9L + MAX_DYNAMIC_NOTIFICATION_CONCURRENCY * 2 + 1).toList()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        val completedPages = mutableSetOf<Pair<Long, String>>()
        var authorQueries = 0
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = mids.map { FollowingUser(mid = it) }, total = mids.size))
            pages = mids.flatMap { mid ->
                listOf(
                    (mid to "") to DynamicPersonalFeedPage(
                        items = listOf(dynamic("${mid + 100}", mid)), offset = "older", hasMore = true,
                    ),
                    (mid to "older") to DynamicPersonalFeedPage(items = listOf(dynamic("$mid", mid))),
                )
            }.toMap()
            onPersonalFeed = { mid, offset ->
                active++
                peak = maxOf(peak, active)
                try {
                    release.await()
                    completedPages += mid to offset
                } finally {
                    active--
                }
            }
            onAuthorUpdates = {
                authorQueries++
                assertEquals(0, active)
                assertEquals(pages.keys, completedPages)
            }
        }
        val previous = AccountNotificationState(dynamicUpBaselines = mids.associateWith { "$it" })
        val pending = async { MessageNotificationPoller(source).check(dynamicOnly, previous, 1) }
        runCurrent()
        assertEquals(MAX_DYNAMIC_NOTIFICATION_CONCURRENCY, active)
        assertEquals(0, authorQueries)
        assertFalse(pending.isCompleted)
        release.complete(Unit)
        advanceUntilIdle()
        val result = pending.await()
        assertEquals(MAX_DYNAMIC_NOTIFICATION_CONCURRENCY, peak)
        assertEquals(1, authorQueries)
        assertEquals(mids.associateWith { "${it + 100}" }, result.state.dynamicUpBaselines)
        assertEquals(mids.associate { "${it + 100}" to it }, result.state.pendingDynamicIds)
        assertEquals(
            mids.sortedDescending().take(3).map { "dynamic:${it + 100}" } + "dynamic:summary",
            result.notifications.map { it.key },
        )
        assertEquals(mids.size, result.notifications.last().text.filter(Char::isDigit).toInt())
        assertFalse(result.shouldRetry)
    }

    @Test
    fun idleWorkerScansQueuedAuthorsWhileOneAuthorsNextPageIsStillBlocked() = runTest {
        val mids = (9L until 9L + MAX_DYNAMIC_NOTIFICATION_CONCURRENCY * 2 + 1).toList()
        val slowMid = mids.first()
        val releaseSlowPage = CompletableDeferred<Unit>()
        val completedAuthors = mutableSetOf<Long>()
        var authorQueries = 0
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = mids.map { FollowingUser(mid = it) }, total = mids.size))
            pages = mids.flatMap { mid ->
                listOf(
                    (mid to "") to DynamicPersonalFeedPage(
                        items = listOf(dynamic("${mid + 100}", mid)), offset = "older", hasMore = true,
                    ),
                    (mid to "older") to DynamicPersonalFeedPage(items = listOf(dynamic("$mid", mid))),
                )
            }.toMap()
            authorUpdates = mids.associateWith { true }
            onPersonalFeed = { mid, offset ->
                if (offset == "older") {
                    if (mid == slowMid) releaseSlowPage.await()
                    completedAuthors += mid
                }
            }
            onAuthorUpdates = {
                authorQueries++
                assertEquals(mids.toSet(), completedAuthors)
                authorUpdates = authorUpdates + (slowMid to false)
            }
        }
        val previous = AccountNotificationState(
            dynamicUpBaselines = mids.associateWith { "$it" },
            pendingDynamicIds = mapOf("99" to slowMid),
        )
        val pending = async {
            MessageNotificationPoller(source).check(dynamicOnly, previous, 1, setOf("dynamic:99"))
        }
        runCurrent()
        assertEquals(mids.toSet() - slowMid, completedAuthors)
        assertEquals(0, authorQueries)
        assertFalse(pending.isCompleted)
        releaseSlowPage.complete(Unit)
        advanceUntilIdle()
        val result = pending.await()
        assertEquals(1, authorQueries)
        assertEquals(mids.associateWith { "${it + 100}" }, result.state.dynamicUpBaselines)
        assertEquals((mids - slowMid).associate { "${it + 100}" to it }, result.state.pendingDynamicIds)
        assertEquals(setOf("dynamic:99"), result.cancelNotificationKeys)
        assertEquals(
            mids.sortedDescending().take(3).map { "dynamic:${it + 100}" } + "dynamic:summary",
            result.notifications.map { it.key },
        )
        assertEquals(mids.size - 1, result.notifications.last().text.filter(Char::isDigit).toInt())
    }

    @Test
    fun concurrentSecondPageFailurePreservesOnlyThatAuthorsBaselineAndCandidates() = runTest {
        for (failure in listOf(IOException("interrupted"), IllegalStateException("unavailable"))) {
            val mids = (9L until 9L + MAX_DYNAMIC_NOTIFICATION_CONCURRENCY + 2).toList()
            val failedMid = mids.first()
            val releaseFailure = CompletableDeferred<Unit>()
            val completedAuthors = mutableSetOf<Long>()
            val source = Source().apply {
                followingPages = mapOf(1 to FollowingsData(list = mids.map { FollowingUser(mid = it) }, total = mids.size))
                pages = mids.flatMap { mid ->
                    listOf(
                        (mid to "") to DynamicPersonalFeedPage(
                            items = listOf(dynamic("${mid + 100}", mid)), offset = "older", hasMore = true,
                        ),
                        (mid to "older") to DynamicPersonalFeedPage(items = listOf(dynamic("$mid", mid))),
                    )
                }.toMap()
                onPersonalFeed = { mid, offset ->
                    if (offset == "older") {
                        if (mid == failedMid) {
                            releaseFailure.await()
                            throw failure
                        }
                        completedAuthors += mid
                    }
                }
            }
            val previous = AccountNotificationState(
                dynamicUpBaselines = mids.associateWith { "$it" },
                pendingDynamicIds = mapOf("99" to failedMid),
            )
            val pending = async {
                MessageNotificationPoller(source).check(dynamicOnly, previous, 1, setOf("dynamic:99"))
            }
            runCurrent()
            assertEquals(mids.toSet() - failedMid, completedAuthors)
            releaseFailure.complete(Unit)
            advanceUntilIdle()
            val result = pending.await()
            assertEquals(
                mids.associateWith { if (it == failedMid) "$it" else "${it + 100}" },
                result.state.dynamicUpBaselines,
            )
            assertEquals(
                (mids - failedMid).associate { "${it + 100}" to it } + ("99" to failedMid),
                result.state.pendingDynamicIds,
            )
            assertEquals(
                mids.sortedDescending().take(3).map { "dynamic:${it + 100}" } + "dynamic:summary",
                result.notifications.map { it.key },
            )
            assertEquals(mids.size - 1, result.notifications.last().text.filter(Char::isDigit).toInt())
            assertEquals(failure is IOException, result.shouldRetry)
        }
    }

    @Test
    fun cancellingConcurrentScanStopsActiveAndQueuedAuthorsWithoutReturningPartialResults() = runTest {
        for (cancelFromRequest in listOf(false, true)) {
            val mids = (9L until 9L + MAX_DYNAMIC_NOTIFICATION_CONCURRENCY * 2).toList()
            val releaseCancellation = CompletableDeferred<Unit>()
            val active = mutableSetOf<Long>()
            val started = mutableSetOf<Long>()
            val completedAuthors = mutableSetOf<Long>()
            var cancellingMid: Long? = null
            var authorQueries = 0
            var resultDelivered = false
            val source = Source().apply {
                followingPages = mapOf(1 to FollowingsData(list = mids.map { FollowingUser(mid = it) }, total = mids.size))
                pages = mids.associate { (it to "") to DynamicPersonalFeedPage(items = listOf(dynamic("${it + 100}", it))) }
                onPersonalFeed = { mid, _ ->
                    started += mid
                    if (mid == mids.first()) {
                        completedAuthors += mid
                    } else {
                        active += mid
                        try {
                            releaseCancellation.await()
                            if (mid == cancellingMid) throw CancellationException("request cancelled")
                            awaitCancellation()
                        } finally {
                            active -= mid
                        }
                    }
                }
                onAuthorUpdates = { authorQueries++ }
            }
            val previous = AccountNotificationState(dynamicUpBaselines = mids.associateWith { "$it" })
            val poller = MessageNotificationPoller(source)
            val pending = async {
                poller.check(dynamicOnly, previous, 1).also { resultDelivered = true }
            }
            runCurrent()
            assertEquals(MAX_DYNAMIC_NOTIFICATION_CONCURRENCY, active.size)
            assertEquals(setOf(mids.first()), completedAuthors)
            assertTrue(started.size < mids.size)
            val startedBeforeCancellation = started.toSet()
            if (cancelFromRequest) {
                cancellingMid = active.first()
                releaseCancellation.complete(Unit)
            } else {
                pending.cancel()
            }
            advanceUntilIdle()
            assertFailsWith<CancellationException> { pending.await() }
            assertEquals(emptySet(), active)
            assertEquals(startedBeforeCancellation, started)
            assertEquals(0, authorQueries)
            assertFalse(resultDelivered)
            source.onPersonalFeed = null
            val recovered = poller.check(dynamicOnly, previous, 1)
            assertEquals(mids.associateWith { "${it + 100}" }, recovered.state.dynamicUpBaselines)
            assertEquals(mids.associate { "${it + 100}" to it }, recovered.state.pendingDynamicIds)
            assertEquals(
                mids.sortedDescending().take(3).map { "dynamic:${it + 100}" } + "dynamic:summary",
                recovered.notifications.map { it.key },
            )
        }
    }

    @Test
    fun publicationDuringAnotherAuthorsScanIsFoundDespiteAZeroCountAtTheNewerGlobalId() = runTest {
        val firstAuthorFetched = CompletableDeferred<Unit>()
        val source = Source().apply {
            followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
            updateCounts = mapOf("100" to 1, "120" to 0)
            pages = mapOf(
                (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("100"))),
                (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120", 10), dynamic("90", 10))),
            )
            authorUpdates = mapOf(9L to true, 10L to true)
            onPersonalFeed = { mid, _ ->
                if (mid == 10L) {
                    firstAuthorFetched.await()
                    pages = pages + ((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110"), dynamic("100"))))
                }
            }
            onPersonalFeedResult = { mid, _ ->
                if (mid == 9L) firstAuthorFetched.complete(Unit)
            }
        }
        val poller = MessageNotificationPoller(source)
        val previous = AccountNotificationState(
            dynamicProbeBaseline = "100", dynamicUpBaselines = mapOf(9L to "100", 10L to "90"),
        )
        val first = poller.check(dynamicOnly, previous, 1)
        assertEquals(listOf("dynamic:120"), first.notifications.map { it.key })
        assertEquals(mapOf(9L to "100", 10L to "120"), first.state.dynamicUpBaselines)
        val second = poller.check(dynamicOnly, first.state, 1, setOf("dynamic:120"))
        assertEquals(listOf("dynamic:110"), second.notifications.map { it.key })
        assertEquals(mapOf(9L to "110", 10L to "120"), second.state.dynamicUpBaselines)
        assertEquals(mapOf("110" to 9L, "120" to 10L), second.state.pendingDynamicIds)
    }

    @Test
    fun missingProbeAfterUpgradeFindsNewPublicationsEvenWhenUpdateCountIsZero() = runTest {
        val source = Source().apply {
            defaultUpdateCount = 0
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110"), dynamic("100"))))
            authorUpdates = mapOf(9L to true)
        }
        val previous = Json { ignoreUnknownKeys = true }.decodeFromString<AccountNotificationState>(
            """{"initialized":["dynamic"],"dynamicBaseline":"120","dynamicUpBaselines":{"9":"100"}}"""
        )
        val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1)
        assertEquals(listOf("dynamic:110"), result.notifications.map { it.key })
        assertEquals(mapOf("110" to 9L), result.state.pendingDynamicIds)
        assertEquals(mapOf(9L to "110"), result.state.dynamicUpBaselines)
        assertFalse(result.shouldRetry)
    }

    @Test
    fun failedUpdateCountStillScansPublicationsAndSynchronizesAuthorReadState() = runTest {
        for (failure in listOf(IOException("count interrupted"), IllegalStateException("count unavailable"))) {
            val source = Source().apply {
                updateCountError = failure
                followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9), FollowingUser(mid = 10)), total = 2))
                pages = mapOf(
                    (9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("110"), dynamic("100"))),
                    (10L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("120", 10), dynamic("90", 10))),
                )
                authorUpdates = mapOf(9L to true, 10L to false)
            }
            val previous = AccountNotificationState(
                dynamicProbeBaseline = "100", dynamicUpBaselines = mapOf(9L to "100", 10L to "90"),
                pendingDynamicIds = mapOf("95" to 10L),
            )
            val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1, setOf("dynamic:95"))
            assertEquals(listOf("dynamic:110"), result.notifications.map { it.key })
            assertEquals(setOf("dynamic:95"), result.cancelNotificationKeys)
            assertEquals(mapOf("110" to 9L), result.state.pendingDynamicIds)
            assertEquals(mapOf(9L to "110", 10L to "120"), result.state.dynamicUpBaselines)
            assertEquals(failure is IOException, result.shouldRetry)
        }
    }

    @Test
    fun newAuthorInitializesSilentlyFromOnlyTheFirstPage() = runTest {
        val source = Source().apply {
            authorUpdates = mapOf(9L to true)
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(
                items = listOf(dynamic("120")), offset = "older", hasMore = true,
            ))
        }
        val poller = MessageNotificationPoller(source)
        val initial = poller.check(dynamicOnly, AccountNotificationState(), 1)
        assertEquals(mapOf(9L to "120"), initial.state.dynamicUpBaselines)
        assertEquals(emptyList(), initial.notifications)
        assertFalse(initial.shouldRetry)
        source.pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(dynamic("130"), dynamic("120"))))
        val next = poller.check(dynamicOnly, initial.state, 1)
        assertEquals(listOf("dynamic:130"), next.notifications.map { it.key })
    }

    @Test
    fun unsignedPublicationIdsKeepExactOrderingAndRealNotificationRoutes() = runTest {
        val source = Source().apply {
            pages = mapOf((9L to "") to DynamicPersonalFeedPage(items = listOf(
                dynamic("18446744073709551615"), dynamic("18446744073709551614"),
            )))
        }
        val previous = AccountNotificationState(dynamicUpBaselines = mapOf(9L to "18446744073709551614"))
        val result = MessageNotificationPoller(source).check(dynamicOnly, previous, 1)
        assertEquals(listOf("dynamic:18446744073709551615"), result.notifications.map { it.key })
        assertEquals("18446744073709551615", result.state.dynamicUpBaselines[9L])
        assertEquals("video_player/BV1xx411c7mD?cid=0&aid=0&commentRootRpid=0&commentTargetRpid=0",
            result.notifications.single().route)
    }

    private fun dynamic(id: String, mid: Long = 9L) = DynamicItem(
        id_str = id,
        type = "DYNAMIC_TYPE_AV",
        modules = DynamicModules(
            module_author = DynamicAuthorModule(mid = mid, name = "UP", following = true),
            module_dynamic = DynamicContentModule(major = DynamicMajor(archive = ArchiveMajor(bvid = "BV1xx411c7mD"))),
        ),
    )

    private fun live(uid: Long, time: Long) = FollowedLiveRoom(roomid = uid, uid = uid, liveStatus = 1, liveTime = time)

    private class Source : MessageNotificationSource {
        var sessions = emptyList<SessionItem>()
        var sessionDetails = emptyMap<Pair<Long, Int>, SessionItem>()
        var sessionDetailErrors = emptySet<Pair<Long, Int>>()
        var unread = MessageFeedUnreadData(sysMsg = 1)
        var notices = emptyList<SystemNoticeItem>()
        var unreadError: Exception? = null
        var defaultUpdateCount = 1
        var updateCounts = emptyMap<String, Int>()
        var updateCountError: Exception? = null
        var authorUpdates = emptyMap<Long, Boolean>()
        var authorUpdatesError: Exception? = null
        var onPersonalFeed: (suspend (Long, String) -> Unit)? = null
        var onPersonalFeedResult: ((Long, String) -> Unit)? = null
        var onAuthorUpdates: (suspend () -> Unit)? = null
        var followingPages = mapOf(1 to FollowingsData(list = listOf(FollowingUser(mid = 9, uname = "UP")), total = 1))
        var pages = mapOf((9L to "") to DynamicPersonalFeedPage())
        var rooms = emptyMap<Int, FollowedLiveData>()
        var feedErrorOffset: String? = null
        var feedErrorHost: Long? = null
        var followingErrorPage: Int? = null
        var liveErrorPage: Int? = null
        override suspend fun sessions() = sessions
        override suspend fun sessionDetail(talkerId: Long, sessionType: Int): SessionItem {
            if ((talkerId to sessionType) in sessionDetailErrors) throw IOException("session detail interrupted")
            return sessionDetails.getValue(talkerId to sessionType)
        }
        override suspend fun feedUnread(): MessageFeedUnreadData { unreadError?.let { throw it }; return unread }
        override suspend fun systemNotices() = notices
        override suspend fun dynamicUpdateCount(baseline: String): Int {
            updateCountError?.let { throw it }
            return updateCounts[baseline] ?: defaultUpdateCount
        }
        override suspend fun dynamicAuthorUpdates(): Map<Long, Boolean> {
            onAuthorUpdates?.invoke()
            authorUpdatesError?.let { throw it }
            return authorUpdates
        }
        override suspend fun followings(selfMid: Long, page: Int): FollowingsData {
            if (page == followingErrorPage) throw IOException("following list interrupted")
            return followingPages[page] ?: FollowingsData()
        }
        override suspend fun personalDynamicFeed(hostUid: Long, offset: String, page: Int): DynamicPersonalFeedPage {
            onPersonalFeed?.invoke(hostUid, offset)
            if (offset == feedErrorOffset || hostUid == feedErrorHost) throw IOException("network interrupted")
            return pages.getValue(hostUid to offset).also { onPersonalFeedResult?.invoke(hostUid, offset) }
        }
        override suspend fun followedLive(page: Int): FollowedLiveData {
            if (page == liveErrorPage) throw IOException("network interrupted")
            return rooms[page] ?: FollowedLiveData()
        }
    }
}
