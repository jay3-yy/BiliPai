package com.android.purebilibili.navigation3

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiliPaiReturnSessionStateTest {

    @Test
    fun relatedSourceRestoreWaitsForObservedTransitionThenIdle() {
        val beforeTransition = resolveRelatedReturnSourceRestoreDecision(
            restorePending = true,
            transitionObserved = false,
            transitionAnimated = true,
            exposure = VideoCardTransitionExposure.Idle,
        )
        assertFalse(beforeTransition.transitionObserved)
        assertFalse(beforeTransition.shouldRestore)

        val returning = resolveRelatedReturnSourceRestoreDecision(
            restorePending = true,
            transitionObserved = false,
            transitionAnimated = true,
            exposure = VideoCardTransitionExposure.Returning,
        )
        assertTrue(returning.transitionObserved)
        assertFalse(returning.shouldRestore)

        val settled = resolveRelatedReturnSourceRestoreDecision(
            restorePending = true,
            transitionObserved = returning.transitionObserved,
            transitionAnimated = true,
            exposure = VideoCardTransitionExposure.Idle,
        )
        assertTrue(settled.shouldRestore)
    }

    @Test
    fun relatedSourceWithoutAnyNavigationAnimationRestoresImmediately() {
        val decision = resolveRelatedReturnSourceRestoreDecision(
            restorePending = true,
            transitionObserved = false,
            transitionAnimated = false,
            exposure = VideoCardTransitionExposure.Idle,
        )

        assertTrue(decision.shouldRestore)
    }

    @Test
    fun switchingCollectionVideoInPlaceKeepsTheNextRelatedCardsGeometryOwnedByItsEntry() {
        val parent = BiliPaiNavKey.VideoDetail(bvid = "BV_ENTRY")
        val recordedRoute = com.android.purebilibili.feature.video.ui.components.resolveRelatedVideoCardHostRoute(
            parent.toLegacyRoute(), displayedBvid = "BV_PLAYING_AFTER_SWITCH",
        )
        val source = resolveBiliPaiVideoSource(
            bvid = "BV_RELATED",
            explicitSourceRoute = "video/${parent.bvid}",
            currentKey = parent,
            previousSourceRoute = "home",
        )
        val bounds = Rect(12f, 300f, 360f, 470f)
        val session = VideoCardTransitionSession.create(
            bvid = "BV_RELATED",
            source = source,
            recordedSourceKey = "$recordedRoute:BV_RELATED",
            cardBounds = bounds,
            sourceCornerDp = 12,
            cardSourceDirection = BiliPaiNavCardSourceDirection.NONE,
            coverIdentity = "related-cover",
            cardFullyVisible = true,
            isSingleColumnCard = true,
            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
        )
        assertEquals("video/BV_ENTRY", session.sourceRoute)
        assertEquals(bounds, session.cardBounds)
        assertTrue(session.hasUsableSourceGeometry)
        assertEquals(BiliPaiVideoCardMorphMode.PRIMARY_WHOLE_CARD, resolveBiliPaiVideoCardMorphMode(
            cardTransitionEnabled = true,
            reduceMotion = false,
            sourceRoute = session.sourceRoute,
            hasUsableSourceBounds = session.cardBounds != null,
        ))
    }

    private fun transitionSession(
        bvid: String,
        sourceRoute: String,
        sourceKey: String,
        left: Float = 10f,
        sourceInstanceId: Long? = null,
    ) = VideoCardTransitionSession.create(
        bvid = bvid,
        source = BiliPaiVideoSource(route = sourceRoute, key = sourceKey),
        recordedSourceKey = sourceKey,
        sourcePixelOwner = sourceInstanceId?.let {
            com.android.purebilibili.core.util.VideoCardPixelOwner(sourceKey, it)
        },
        cardBounds = Rect(left, 20f, left + 100f, 180f),
        sourceCornerDp = 12,
        cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT,
        coverIdentity = "cover-$bvid",
        cardFullyVisible = true,
        isSingleColumnCard = false,
        sourceLayout = if (sourceRoute.startsWith("video/")) {
            VideoCardSourceLayout.SIDE_BY_SIDE
        } else {
            VideoCardSourceLayout.STACKED
        },
        sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
            title = "title-$bvid",
            ownerName = "owner-$bvid",
            viewText = "1万",
            danmakuText = "200",
            durationText = "01:23",
        ),
    )

    @Test
    fun videoSourceRouteIsStoredOutsideCardPositionManager() {
        val state = BiliPaiReturnSessionState()
            .recordVideoSource(
                BiliPaiVideoSource(
                    route = "search",
                    key = "search:BV1"
                )
            )

        assertEquals("search", state.lastVideoSourceRoute)
        assertEquals("search:BV1", state.lastVideoSourceKey)
        assertFalse(state.isReturningFromDetail)
        assertFalse(state.isQuickReturnFromDetail)
    }

    @Test
    fun returnSessionMarksQuickReturnFromElapsedTime() {
        val state = BiliPaiReturnSessionState()
            .markDetailEntered(nowMillis = 1_000L)
            .markReturning(nowMillis = 1_450L)

        assertTrue(state.isReturningFromDetail)
        assertTrue(state.isQuickReturnFromDetail)
    }

    @Test
    fun clearReturningKeepsSourceRouteForNextSharedElementMatch() {
        val state = BiliPaiReturnSessionState()
            .recordVideoSource(
                BiliPaiVideoSource(
                    route = "home",
                    key = "home:BV1"
                )
            )
            .markDetailEntered(nowMillis = 1_000L)
            .markReturning(nowMillis = 2_000L)
            .clearReturning()

        assertEquals("home", state.lastVideoSourceRoute)
        assertEquals("home:BV1", state.lastVideoSourceKey)
        assertFalse(state.isReturningFromDetail)
        assertFalse(state.isQuickReturnFromDetail)
    }

    @Test
    fun legacyRouteRecordingClearsPreviousSourceKey() {
        val state = BiliPaiReturnSessionState()
            .recordVideoSource(
                BiliPaiVideoSource(
                    route = "search",
                    key = "search:BV1"
                )
            )
            .recordVideoSourceRoute("history?from=tab")

        assertEquals("history", state.lastVideoSourceRoute)
        assertEquals(null, state.lastVideoSourceKey)
    }

    @Test
    fun relatedDetailSourcePreservesAndRestoresListSource() {
        val state = BiliPaiReturnSessionState()
            .recordVideoSource(
                BiliPaiVideoSource(
                    route = "home",
                    key = "home:BV_A"
                )
            )
            .recordVideoSource(
                BiliPaiVideoSource(
                    route = "video/BV_A",
                    key = "video/BV_A:BV_B"
                )
            )

        assertEquals("video/BV_A", state.lastVideoSourceRoute)
        assertEquals("video/BV_A:BV_B", state.lastVideoSourceKey)
        assertEquals("home", state.previousVideoSources.single().route)
        assertEquals("home:BV_A", state.previousVideoSources.single().key)

        val restored = state.restorePreviousVideoSourceAfterRelatedReturn()
        assertEquals("home", restored.lastVideoSourceRoute)
        assertEquals("home:BV_A", restored.lastVideoSourceKey)
        assertTrue(restored.previousVideoSources.isEmpty())
    }

    @Test
    fun immutableTransitionSessionKeepsClickGeometryAfterLiveSourceChanges() {
        val clicked = transitionSession(
            bvid = "BV_A",
            sourceRoute = "home",
            sourceKey = "home:BV_A",
            left = 20f,
        )
        val state = BiliPaiReturnSessionState().recordTransitionSession(clicked)

        val originalBounds = clicked.cardBounds
        val unrelatedLaterCard = transitionSession(
            bvid = "BV_B",
            sourceRoute = "home",
            sourceKey = "home:BV_B",
            left = 500f,
        )

        assertEquals(originalBounds, state.transitionSession?.cardBounds)
        assertFalse(state.transitionSession?.cardBounds == unrelatedLaterCard.cardBounds)
        assertEquals("cover-BV_A", state.transitionSession?.coverIdentity)
        assertEquals(12, state.transitionSession?.sourceCornerDp)
        assertEquals(VideoCardSourceLayout.STACKED, state.transitionSession?.sourceLayout)
        assertEquals("title-BV_A", state.transitionSession?.sourceChromeSnapshot?.title)
    }

    @Test
    fun transitionSessionFreezesNavigationHostOriginAtClick() {
        val origin = Offset(96f, 24f)
        val session = VideoCardTransitionSession.create(
            bvid = "BV_A",
            source = BiliPaiVideoSource(route = "home", key = "home:BV_A"),
            recordedSourceKey = "home:BV_A",
            cardBounds = Rect(120f, 80f, 420f, 360f),
            sourceCornerDp = 12,
            cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT,
            coverIdentity = "cover-BV_A",
            cardFullyVisible = true,
            isSingleColumnCard = false,
            hostOriginInRoot = origin,
        )

        assertEquals(origin, session.hostOriginInRoot)
    }

    @Test
    fun staleOrAdjacentCardGeometryIsRejectedByBvidOwnership() {
        val session = VideoCardTransitionSession.create(
            bvid = "BV_TARGET",
            source = BiliPaiVideoSource(route = "home", key = "home:BV_ADJACENT"),
            recordedSourceKey = "home:BV_ADJACENT",
            cardBounds = Rect(0f, 0f, 100f, 100f),
            sourceCornerDp = 12,
            cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_RIGHT,
            coverIdentity = "target-cover",
            cardFullyVisible = true,
            isSingleColumnCard = false,
        )

        assertEquals(null, session.cardBounds)
        assertEquals(null, session.sourceCornerDp)
        assertEquals(BiliPaiNavCardSourceDirection.NONE, session.cardSourceDirection)
        assertFalse(session.hasUsableSourceGeometry)
    }

    @Test
    fun commentLinkRejectsPreviousCardPixelsAndRestoresParentSession() {
        val parent = transitionSession("BV_A", "home", "home:BV_A")
        val child = VideoCardTransitionSession.create(
            bvid = "BV_B",
            source = BiliPaiVideoSource("video/BV_A", "video/BV_A:BV_B"),
            recordedSourceKey = "home:BV_A",
            cardBounds = parent.cardBounds,
            coverBounds = parent.cardBounds,
            sourceCornerDp = 12,
            cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT,
            coverIdentity = "cover-BV_B",
            cardFullyVisible = true,
            isSingleColumnCard = false,
            sourceChromeSnapshot = parent.sourceChromeSnapshot,
        )
        assertEquals(null, child.cardBounds)
        assertEquals(null, child.coverBounds)
        assertEquals(null, child.sourceChromeSnapshot)
        assertFalse(child.hasUsableSourceGeometry)
        val restored = BiliPaiReturnSessionState()
            .recordTransitionSession(parent)
            .recordTransitionSession(child, preserveCurrentSession = true)
            .restorePreviousVideoSourceAfterRelatedReturn()
        assertEquals(parent, restored.transitionSession)
    }

    @Test
    fun textLinkRejectsEvenMatchingStaleCardGeometry() {
        val child = VideoCardTransitionSession.create(
            bvid = "BV_B",
            source = BiliPaiVideoSource("video/BV_A", "video/BV_A:BV_B"),
            recordedSourceKey = null,
            cardBounds = Rect(0f, 0f, 100f, 100f),
            sourceCornerDp = 12,
            cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT,
            coverIdentity = null,
            cardFullyVisible = true,
            isSingleColumnCard = false,
        )
        assertFalse(child.hasUsableSourceGeometry)
        assertEquals(BiliPaiNavCardSourceDirection.NONE, child.cardSourceDirection)
    }

    @Test
    fun relatedReturnRestoresTheCompleteListTransitionSession() {
        val listSession = transitionSession("BV_A", "home", "home:BV_A")
        val relatedSession = transitionSession(
            "BV_B",
            "video/BV_A",
            "video/BV_A:BV_B",
            left = 300f,
        )
        val restored = BiliPaiReturnSessionState()
            .recordTransitionSession(listSession)
            .recordTransitionSession(relatedSession)
            .restorePreviousVideoSourceAfterRelatedReturn()

        assertEquals(listSession, restored.transitionSession)
        assertEquals("home", restored.lastVideoSourceRoute)
        assertEquals("home:BV_A", restored.lastVideoSourceKey)
        assertTrue(restored.previousTransitionSessions.isEmpty())
    }

    @Test
    fun multiHopRelatedReturnRestoresEveryIntermediateSourceSession() {
        val homeToA = transitionSession("BV_A", "home", "home:BV_A")
        val aToB = transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B")
        val bToC = transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C")
        val atC = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToA)
            .recordTransitionSession(aToB)
            .recordTransitionSession(bToC)

        assertEquals(listOf(homeToA, aToB), atC.previousTransitionSessions)

        val backAtB = atC.restorePreviousVideoSourceAfterRelatedReturn()
        assertEquals(aToB, backAtB.transitionSession)
        assertEquals("video/BV_A:BV_B", backAtB.lastVideoSourceKey)

        val backAtA = backAtB.restorePreviousVideoSourceAfterRelatedReturn()
        assertEquals(homeToA, backAtA.transitionSession)
        assertEquals("home:BV_A", backAtA.lastVideoSourceKey)
        assertTrue(backAtA.previousTransitionSessions.isEmpty())
    }

    @Test
    fun nestedDetailThroughSpacePreservesOriginalVideoReturnSession() {
        val homeToVideo = transitionSession("BV_A", "home", "home:BV_A")
        val spaceToSameVideo = transitionSession("BV_A", "space/123", "space:BV_A")

        val nested = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToVideo)
            .recordTransitionSession(
                session = spaceToSameVideo,
                preserveCurrentSession = true,
            )

        assertEquals(listOf(homeToVideo), nested.previousTransitionSessions)
        val restored = nested.restorePreviousVideoSourceAfterRelatedReturn()
        assertEquals(homeToVideo, restored.transitionSession)
        assertEquals("home", restored.lastVideoSourceRoute)
        assertEquals("home:BV_A", restored.lastVideoSourceKey)
    }

    @Test
    fun onlySuccessfulPrefixPopsConsumeSourcesIncludingReturnToRoot() {
        val a = BiliPaiNavKey.VideoDetail(bvid = "BV_A")
        val b = BiliPaiNavKey.VideoDetail(bvid = "BV_B")
        val c = BiliPaiNavKey.VideoDetail(bvid = "BV_C")
        val stack = listOf(BiliPaiNavKey.MainHost, a, b, c)
        assertEquals(1, countSuccessfulVideoDetailPops(stack, stack.dropLast(1)))
        assertEquals(2, countSuccessfulVideoDetailPops(stack, stack.dropLast(2)))
        assertEquals(3, countSuccessfulVideoDetailPops(stack, listOf(BiliPaiNavKey.MainHost)))
        assertEquals(0, countSuccessfulVideoDetailPops(stack, stack)) // Preview cancellation.
        assertEquals(0, countSuccessfulVideoDetailPops(stack, stack + BiliPaiNavKey.SearchTrending))
        assertEquals(0, countSuccessfulVideoDetailPops(stack, listOf(BiliPaiNavKey.MainHost, c)))
        assertEquals(0, countSuccessfulVideoDetailPops(stack, emptyList()))
        assertEquals(0, countSuccessfulVideoDetailPops(
            stack + BiliPaiNavKey.SearchTrending, stack,
        ))
    }

    @Test
    fun twoCommittedPopsInOneAnimationRestoreTwoSourcesExactlyOnce() {
        val homeToA = transitionSession("BV_A", "home", "home:BV_A")
        val aToB = transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B")
        val bToC = transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C")
        val pending = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToA)
            .recordTransitionSession(aToB)
            .recordTransitionSession(bToC)
            .recordSuccessfulVideoDetailPop()
            .recordSuccessfulVideoDetailPop()

        assertEquals(2, pending.pendingRelatedReturnCount)
        assertEquals(bToC, pending.transitionSession) // Outgoing geometry stays frozen.
        val restored = pending.restorePendingRelatedReturns(pending.sourceGeneration)
        assertEquals(homeToA, restored.transitionSession)
        assertEquals("home:BV_A", restored.lastVideoSourceKey)
        assertEquals(0, restored.pendingRelatedReturnCount)
        assertTrue(restored.previousTransitionSessions.isEmpty())
        assertTrue(restored.previousVideoSources.isEmpty())
        assertEquals(restored, restored.restorePendingRelatedReturns(pending.sourceGeneration))
    }

    @Test
    fun sequentialAnimatedAndUnanimatedPopsHaveTheSameParentResult() {
        val homeToA = transitionSession("BV_A", "home", "home:BV_A")
        val aToB = transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B")
        val bToC = transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C")
        val atC = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToA)
            .recordTransitionSession(aToB)
            .recordTransitionSession(bToC)
        val atB = atC.recordSuccessfulVideoDetailPop().restorePendingRelatedReturns()
        assertEquals(aToB, atB.transitionSession)
        assertEquals(0, atB.pendingRelatedReturnCount)
        val atA = atB.recordSuccessfulVideoDetailPop().restorePendingRelatedReturns()
        assertEquals(homeToA, atA.transitionSession)
        val combined = atC.recordSuccessfulVideoDetailPop().recordSuccessfulVideoDetailPop()
            .restorePendingRelatedReturns()
        assertEquals(atA, combined)
    }

    @Test
    fun pushDuringPendingReturnStartsFromActualParentAndRejectsOldCompletion() {
        val homeToA = transitionSession("BV_A", "home", "home:BV_A")
        val aToB = transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B")
        val bToC = transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C")
        val bToD = transitionSession("BV_D", "video/BV_B", "video/BV_B:BV_D")
        val pending = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToA)
            .recordTransitionSession(aToB)
            .recordTransitionSession(bToC)
            .recordSuccessfulVideoDetailPop()
        val newPush = pending.recordTransitionSession(bToD)
        assertEquals(listOf(homeToA, aToB), newPush.previousTransitionSessions)
        assertEquals(0, newPush.pendingRelatedReturnCount)
        assertEquals(bToD, newPush.transitionSession)
        val newerReturn = newPush.recordSuccessfulVideoDetailPop()
        assertEquals(newerReturn, newerReturn.restorePendingRelatedReturns(pending.sourceGeneration))
        assertEquals(aToB, newerReturn.restorePendingRelatedReturns().transitionSession)
    }

    @Test
    fun returnToRootCannotQueueMoreRestoresThanThereAreParentSources() {
        val homeToA = transitionSession("BV_A", "home", "home:BV_A")
        val aToB = transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B")
        val bToC = transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C")
        val pending = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToA)
            .recordTransitionSession(aToB)
            .recordTransitionSession(bToC)
            .recordSuccessfulVideoDetailPop()
            .recordSuccessfulVideoDetailPop()
            .recordSuccessfulVideoDetailPop()
        assertEquals(2, pending.pendingRelatedReturnCount)
        assertEquals(homeToA, pending.restorePendingRelatedReturns().transitionSession)
    }

    @Test
    fun sourceReplacementInvalidatesPendingCompletionEvenForTheSameBvid() {
        val pending = BiliPaiReturnSessionState()
            .recordTransitionSession(transitionSession("BV_A", "home", "home:BV_A"))
            .recordTransitionSession(transitionSession("BV_A", "space/123", "space:BV_A"), true)
            .recordSuccessfulVideoDetailPop()
        val replacement = pending.recordVideoSourceRoute("history")
        assertEquals(0, replacement.pendingRelatedReturnCount)
        assertEquals(replacement, replacement.restorePendingRelatedReturns(pending.sourceGeneration))
        assertEquals("history", replacement.lastVideoSourceRoute)
    }

    @Test
    fun sourceInstanceIdentityIsFrozenAndRestoredWithItsOwnSourceSession() {
        val home = transitionSession("BVsame", "home", "home:BVsame", sourceInstanceId = 101L)
        val space = transitionSession("BVsame", "space/123", "space/123:BVsame", sourceInstanceId = 202L)
        val nested = BiliPaiReturnSessionState()
            .recordTransitionSession(home)
            .recordTransitionSession(space, preserveCurrentSession = true)
        assertEquals(202L, nested.transitionSession?.sourceInstanceId)
        val restored = nested.recordSuccessfulVideoDetailPop().restorePendingRelatedReturns()
        assertEquals(101L, restored.transitionSession?.sourceInstanceId)
        assertEquals("home:BVsame", restored.lastVideoSourceKey)
    }

    @Test
    fun consecutiveImmediateReturnsKeepDistinctRequestsEvenWhenPendingCountRepeats() {
        val homeToA = transitionSession("BV_A", "home", "home:BV_A")
        val aToB = transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B")
        val bToC = transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C")
        val first = BiliPaiReturnSessionState()
            .recordTransitionSession(homeToA)
            .recordTransitionSession(aToB)
            .recordTransitionSession(bToC)
            .recordSuccessfulVideoDetailPop()
        val firstSettled = first.restorePendingRelatedReturns()
        val second = firstSettled.recordSuccessfulVideoDetailPop()
        assertEquals(first.pendingRelatedReturnCount, second.pendingRelatedReturnCount)
        assertTrue(first.relatedReturnRequestId != second.relatedReturnRequestId)
        assertEquals(second, second.restorePendingRelatedReturns(
            first.sourceGeneration, first.relatedReturnRequestId,
        ))
        assertEquals(homeToA, second.restorePendingRelatedReturns().transitionSession)
    }

    @Test
    fun olderIdleCompletionCannotConsumeANewerPopInTheSameSession() {
        val pending = BiliPaiReturnSessionState()
            .recordTransitionSession(transitionSession("BV_A", "home", "home:BV_A"))
            .recordTransitionSession(transitionSession("BV_B", "video/BV_A", "video/BV_A:BV_B"))
            .recordTransitionSession(transitionSession("BV_C", "video/BV_B", "video/BV_B:BV_C"))
            .recordSuccessfulVideoDetailPop()
        val next = pending.recordSuccessfulVideoDetailPop()
        assertEquals(next, next.restorePendingRelatedReturns(
            pending.sourceGeneration, pending.relatedReturnRequestId,
        ))
        assertEquals("home:BV_A", next.restorePendingRelatedReturns().lastVideoSourceKey)
    }

    @Test
    fun returningDetailAndCardMorphDestinationDetectionCoversVideoAndArticle() {
        val mainOnly = listOf(BiliPaiNavKey.MainHost)
        val withVideo = listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.VideoDetail(bvid = "BV1xx", sourceRoute = "home"))
        val withArticle = listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.ArticleDetail(articleId = 42L))

        assertFalse(hasReturningDetailDestination(mainOnly))
        assertTrue(hasReturningDetailDestination(withVideo))
        assertTrue(hasReturningDetailDestination(withArticle))

        assertFalse(hasCardMorphDestination(mainOnly))
        assertTrue(hasCardMorphDestination(withVideo))
        assertFalse(hasCardMorphDestination(withArticle))
    }

    @Test
    fun returnSessionReconciliationWaitsOnlyWhenDetailIsGoneAndTransitionsAreSettled() {
        assertEquals(
            ReturnSessionReconciliationAction.NONE,
            resolveReturnSessionReconciliationAction(
                isReturningFromDetail = false,
                hasReturningDetailOnStack = false,
                isNavigationSettled = true,
                isCardTransitionIdle = true,
                isCardGestureInProgress = false,
            )
        )
        assertEquals(
            ReturnSessionReconciliationAction.NONE,
            resolveReturnSessionReconciliationAction(
                isReturningFromDetail = true,
                hasReturningDetailOnStack = true,
                isNavigationSettled = true,
                isCardTransitionIdle = true,
                isCardGestureInProgress = false,
            )
        )
        assertEquals(
            ReturnSessionReconciliationAction.NONE,
            resolveReturnSessionReconciliationAction(
                isReturningFromDetail = true,
                hasReturningDetailOnStack = false,
                isNavigationSettled = true,
                isCardTransitionIdle = true,
                isCardGestureInProgress = true,
            )
        )
        assertEquals(
            ReturnSessionReconciliationAction.NONE,
            resolveReturnSessionReconciliationAction(
                isReturningFromDetail = true,
                hasReturningDetailOnStack = false,
                isNavigationSettled = false,
                isCardTransitionIdle = true,
                isCardGestureInProgress = false,
            )
        )
        assertEquals(
            ReturnSessionReconciliationAction.WAIT_FOR_SUPPRESSION_WINDOW,
            resolveReturnSessionReconciliationAction(
                isReturningFromDetail = true,
                hasReturningDetailOnStack = false,
                isNavigationSettled = true,
                isCardTransitionIdle = true,
                isCardGestureInProgress = false,
            )
        )
    }

    @Test
    fun stuckCardTransitionResetsWhenHostSettledOrTimedOutWithoutDestination() {
        assertFalse(
            shouldResetStuckCardTransition(
                hasCardMorphDestinationOnStack = true,
                isCardTransitionIdle = false,
                isCardGestureInProgress = false,
                isHostNavigationSettled = true,
            )
        )
        assertFalse(
            shouldResetStuckCardTransition(
                hasCardMorphDestinationOnStack = false,
                isCardTransitionIdle = false,
                isCardGestureInProgress = true,
                isHostNavigationSettled = true,
            )
        )
        assertFalse(
            shouldResetStuckCardTransition(
                hasCardMorphDestinationOnStack = false,
                isCardTransitionIdle = false,
                isCardGestureInProgress = false,
                isHostNavigationSettled = false,
            )
        )
        assertTrue(
            shouldResetStuckCardTransition(
                hasCardMorphDestinationOnStack = false,
                isCardTransitionIdle = false,
                isCardGestureInProgress = false,
                isHostNavigationSettled = true,
            )
        )
        assertTrue(
            shouldForceResetCardTransitionOnTimeout(
                hasCardMorphDestinationOnStack = false,
                isCardTransitionIdle = false,
                isCardGestureInProgress = false,
            )
        )
        assertFalse(
            shouldForceResetCardTransitionOnTimeout(
                hasCardMorphDestinationOnStack = true,
                isCardTransitionIdle = false,
                isCardGestureInProgress = false,
            )
        )
    }
}
