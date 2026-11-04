package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.feature.video.usecase.TripleActionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoDomainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `watch later status arriving after binding updates the action`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions())
        val video = subject("BV1", generation = 1L)
        viewModel.bindSubject(video, VideoEngagementSeed())
        viewModel.bindSubject(video, VideoEngagementSeed(isInWatchLater = true))
        assertTrue(viewModel.uiState.value.isInWatchLater)
        viewModel.toggleWatchLater()
        runCurrent()
        assertFalse(viewModel.uiState.value.isInWatchLater)
        viewModel.sync(VideoEngagementSeed(isInWatchLater = true))
        assertFalse(viewModel.uiState.value.isInWatchLater)
    }

    @Test
    fun `unknown playback seed preserves confirmed relations but confirmed changes still apply`() {
        val viewModel = VideoEngagementViewModel()
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(
            isLiked = true, isFollowing = true, isFavorited = true, isDisliked = true,
            coinCount = 2, likeStatusKnown = true, followStatusKnown = true,
            relationshipStatusKnown = true,
        ))

        viewModel.sync(VideoEngagementSeed())
        val retained = viewModel.uiState.value
        assertTrue(retained.isLiked)
        assertTrue(retained.isFollowing)
        assertTrue(retained.isFavorited)
        assertTrue(retained.isDisliked)
        assertEquals(2, retained.coinCount)
        assertTrue(retained.likeStatusKnown)
        assertTrue(retained.followStatusKnown)

        viewModel.sync(VideoEngagementSeed(
            likeStatusKnown = true, followStatusKnown = true, relationshipStatusKnown = true,
        ))
        assertFalse(viewModel.uiState.value.isLiked)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFavorited)
        assertEquals(0, viewModel.uiState.value.coinCount)
    }

    @Test
    fun `engagement keeps transient state for same generation and resets for next video`() {
        val viewModel = VideoEngagementViewModel()
        val first = subject("BV1", generation = 1L)
        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = false))
        viewModel.setCoinDialogVisible(true)

        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = true))
        assertTrue(viewModel.uiState.value.coinDialogVisible)
        assertTrue(viewModel.uiState.value.isLiked)

        viewModel.bindSubject(subject("BV2", generation = 2L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        assertFalse(viewModel.uiState.value.coinDialogVisible)
        assertFalse(viewModel.uiState.value.isLiked)
    }

    @Test
    fun `engagement keeps successful local result when playback seed is stale`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions())
        val first = subject("BV1", generation = 1L)
        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = false, likeCount = 10))

        viewModel.toggleLike()
        runCurrent()
        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = false, likeCount = 10))

        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals(11, viewModel.uiState.value.likeCount)

        viewModel.bindSubject(subject("BV2", generation = 2L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, likeCount = 3))
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(3, viewModel.uiState.value.likeCount)
    }

    @Test
    fun `engagement discards an old request after subject generation changes`() = runTest(dispatcher) {
        val pendingLike = CompletableDeferred<Result<Boolean>>()
        val viewModel = VideoEngagementViewModel(
            actions = FakeEngagementActions(pendingLike = pendingLike)
        )
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, likeCount = 10))
        viewModel.toggleLike()
        runCurrent()

        viewModel.bindSubject(subject("BV2", generation = 2L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, likeCount = 3))
        pendingLike.complete(Result.success(true))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(3, viewModel.uiState.value.likeCount)
    }

    @Test
    fun `coin entry loads balance in engagement state`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(
            actions = FakeEngagementActions(),
            coinBalanceLoader = VideoCoinBalanceLoader { 8.5 }
        )
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))

        viewModel.openCoinDialog()
        runCurrent()

        assertTrue(viewModel.uiState.value.coinDialogVisible)
        assertEquals(8.5, viewModel.uiState.value.userCoinBalance ?: 0.0, 0.0)
    }

    @Test
    fun `repost video caps coin options at one`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(
            actions = actions,
            coinBalanceLoader = VideoCoinBalanceLoader { 8.5 }
        )
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isRepost = true))

        assertEquals(1, viewModel.uiState.value.coinLimit)

        viewModel.openCoinDialog()
        runCurrent()
        assertTrue(viewModel.uiState.value.coinDialogVisible)

        viewModel.doCoin(1, false)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.coinCount)

        // 转载已投满 1 币：不再打开面板，直接提示上限
        viewModel.openCoinDialog()
        runCurrent()
        assertFalse(viewModel.uiState.value.coinDialogVisible)

        viewModel.doTripleAction()
        runCurrent()
        assertEquals(1, actions.lastTripleCoinCount)
    }

    @Test
    fun `original video keeps two coin limit and triple coin count`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))

        assertEquals(2, viewModel.uiState.value.coinLimit)

        viewModel.doTripleAction()
        runCurrent()
        assertEquals(2, actions.lastTripleCoinCount)
        assertEquals(2, viewModel.uiState.value.coinCount)
    }

    @Test
    fun `composer drops drafts when subject generation changes`() {
        val viewModel = VideoComposerViewModel()
        val first = subject("BV1", generation = 1L)
        viewModel.bindSubject(first)
        viewModel.updateCommentDraft("draft")
        viewModel.bindSubject(first)
        assertEquals("draft", viewModel.uiState.value.commentDraft)

        viewModel.bindSubject(subject("BV2", generation = 2L))
        assertEquals("", viewModel.uiState.value.commentDraft)
    }

    @Test
    fun `supplement updates payload without replacing subject generation`() {
        val viewModel = VideoSupplementViewModel()
        val subject = subject("BV1", generation = 1L)
        viewModel.bindSubject(subject, VideoSupplementSeed(onlineCount = "1"))
        viewModel.bindSubject(subject, VideoSupplementSeed(onlineCount = "2"))

        assertEquals(subject, viewModel.uiState.value.subject)
        assertEquals("2", viewModel.uiState.value.onlineCount)
    }

    @Test
    fun `supplement discards deferred result after generation changes`() = runTest(dispatcher) {
        val viewModel = VideoSupplementViewModel(
            loader = VideoSupplementLoader { snapshot ->
                VideoSupplementSeed(onlineCount = snapshot.bvid)
            },
            startDelayMs = 100L
        )
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoSupplementSeed())
        viewModel.bindSubject(subject("BV2", generation = 2L), VideoSupplementSeed())

        advanceTimeBy(100L)
        runCurrent()

        assertEquals("BV2", viewModel.uiState.value.onlineCount)
        assertEquals(2L, viewModel.uiState.value.subject?.generation)
    }

    @Test
    fun `supplement cancels deferred task while page is invisible`() = runTest(dispatcher) {
        var loads = 0
        val viewModel = VideoSupplementViewModel(
            loader = VideoSupplementLoader {
                loads += 1
                VideoSupplementSeed(onlineCount = "loaded")
            },
            startDelayMs = 100L
        )
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoSupplementSeed())
        viewModel.setVisible(false)

        advanceTimeBy(100L)
        runCurrent()

        assertEquals(0, loads)
        assertEquals("", viewModel.uiState.value.onlineCount)
    }

    @Test
    fun `supplement default deferred loader preserves playback seed`() = runTest(dispatcher) {
        val viewModel = VideoSupplementViewModel(startDelayMs = 100L)
        viewModel.bindSubject(
            subject("BV1", generation = 1L),
            VideoSupplementSeed(onlineCount = "seeded", ownerFollowerCount = 12)
        )

        advanceTimeBy(100L)
        runCurrent()

        assertEquals("seeded", viewModel.uiState.value.onlineCount)
        assertEquals(12, viewModel.uiState.value.ownerFollowerCount)
    }

    @Test
    fun `composer buffered event survives a temporary collector stop`() = runTest(dispatcher) {
        val viewModel = VideoComposerViewModel()

        viewModel.notifyCommentSent()

        assertEquals(VideoComposerEvent.CommentSent, viewModel.events.first())
    }

    @Test
    fun `dislike clears local like state and keeps it against stale seeds`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions())
        val first = subject("BV1", generation = 1L)
        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = true, isDisliked = false, likeCount = 10))

        viewModel.toggleDislike()
        runCurrent()

        assertTrue(viewModel.uiState.value.isDisliked)
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(9, viewModel.uiState.value.likeCount)

        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = true, isDisliked = false, likeCount = 10))
        assertTrue(viewModel.uiState.value.isDisliked)
        assertFalse(viewModel.uiState.value.isLiked)
    }

    @Test
    fun `like clears local dislike state`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions())
        val first = subject("BV1", generation = 1L)
        viewModel.bindSubject(first, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isLiked = false, isDisliked = true))

        viewModel.toggleLike()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLiked)
        assertFalse(viewModel.uiState.value.isDisliked)
    }

    @Test
    fun `triple jump waits for animation completion instead of two seconds`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(
            actions = FakeEngagementActions(),
            tripleJumpEnabled = { true }
        )
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.doTripleAction()
        runCurrent()

        advanceTimeBy(2_200L)
        runCurrent()
        assertTrue(viewModel.uiState.value.tripleCelebrationVisible)
        assertTrue(events.none { it is VideoEngagementEvent.LoadVideo })

        viewModel.completeTripleCelebration(viewModel.uiState.value.tripleCelebrationId)
        runCurrent()
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertEquals(1, events.filterIsInstance<VideoEngagementEvent.LoadVideo>().size)
    }

    @Test
    fun `stale celebration completion cannot close or jump over a new celebration`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(
            actions = FakeEngagementActions(),
            tripleJumpEnabled = { true }
        )
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.doTripleAction()
        runCurrent()
        val previousId = viewModel.uiState.value.tripleCelebrationId
        viewModel.bindSubject(
            subject("BV2", generation = 2L),
            VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true)
        )
        viewModel.doTripleAction()
        runCurrent()
        val currentId = viewModel.uiState.value.tripleCelebrationId
        assertTrue(currentId > previousId)

        viewModel.completeTripleCelebration(previousId)
        runCurrent()
        assertTrue(viewModel.uiState.value.tripleCelebrationVisible)
        assertTrue(events.none { it is VideoEngagementEvent.LoadVideo })

        viewModel.completeTripleCelebration(currentId)
        runCurrent()
        assertEquals(1, events.filterIsInstance<VideoEngagementEvent.LoadVideo>().size)
    }

    @Test
    fun `unmounted celebration times out without a surprise jump`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(
            actions = FakeEngagementActions(),
            tripleJumpEnabled = { true }
        )
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.doTripleAction()
        runCurrent()
        advanceTimeBy(5_001L)
        runCurrent()

        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertTrue(events.none { it is VideoEngagementEvent.LoadVideo })
    }

    @Test
    fun `cancelled celebration does not trigger the enabled jump`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(
            actions = FakeEngagementActions(),
            tripleJumpEnabled = { true }
        )
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", generation = 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.doTripleAction()
        runCurrent()
        viewModel.cancelTripleCelebration(viewModel.uiState.value.tripleCelebrationId)
        runCurrent()

        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertFalse(viewModel.uiState.value.tripleCelebrationFinished)
        assertTrue(events.none { it is VideoEngagementEvent.LoadVideo })
    }

    @Test
    fun `old maid completion cannot dismiss feedback after video changes`() {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions())
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.showShareFeedback("BV1")
        val oldId = viewModel.uiState.value.maidActionId
        viewModel.bindSubject(subject("BV2", 2L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.showShareFeedback("BV1")
        assertEquals(null, viewModel.uiState.value.maidAction)
        viewModel.showShareFeedback("BV2")
        viewModel.dismissMaidAction(oldId)
        assertEquals(VideoMaidAction.SHARE, viewModel.uiState.value.maidAction)
        viewModel.dismissMaidAction(viewModel.uiState.value.maidActionId)
        assertEquals(null, viewModel.uiState.value.maidAction)
    }

    @Test
    fun `cancel dislike and failed coin do not show completion feedback`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions(failCoin = true))
        val events = mutableListOf<VideoEngagementEvent>()
        val eventJob = launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true, isDisliked = true))
        viewModel.toggleDislike()
        runCurrent()
        assertFalse(viewModel.uiState.value.isDisliked)
        assertEquals(null, viewModel.uiState.value.maidAction)
        viewModel.doCoin(1, false)
        runCurrent()
        assertEquals(null, viewModel.uiState.value.maidAction)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertTrue(events.none { it is VideoEngagementEvent.CoinSucceeded })
        eventJob.cancel()
    }

    @Test
    fun `coin with also like replaces like feedback with one coin animation`() = runTest(dispatcher) {
        val viewModel = VideoEngagementViewModel(actions = FakeEngagementActions())
        val events = mutableListOf<VideoEngagementEvent>()
        val eventJob = launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.toggleLike()
        runCurrent()
        assertTrue(viewModel.uiState.value.likeBurstVisible)
        viewModel.doCoin(1, true)
        runCurrent()
        assertFalse(viewModel.uiState.value.likeBurstVisible)
        assertEquals(VideoMaidAction.COIN, viewModel.uiState.value.maidAction)
        assertTrue(viewModel.uiState.value.isLiked)
        val coinEvents = events.filterIsInstance<VideoEngagementEvent.CoinSucceeded>()
        assertEquals(1, coinEvents.size)
        assertEquals(viewModel.uiState.value.subject, coinEvents.single().subject)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        runCurrent()
        assertEquals(1, events.filterIsInstance<VideoEngagementEvent.CoinSucceeded>().size)
        eventJob.cancel()
    }

    @Test
    fun `failed confirmations block toggles until a known seed arrives`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(actions = actions)
        val current = subject("BV1", 1L)
        viewModel.bindSubject(current, VideoEngagementSeed())
        assertFalse(viewModel.uiState.value.likeStatusKnown)
        assertFalse(viewModel.uiState.value.followStatusKnown)
        viewModel.setCoinDialogVisible(true)
        viewModel.toggleLike()
        viewModel.toggleFollow()
        viewModel.doTripleAction()
        viewModel.doCoin(1, true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLiked)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertEquals(null, actions.lastTripleCoinCount)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertTrue(viewModel.uiState.value.coinDialogVisible)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(actions.followRequests.isEmpty())
        viewModel.bindSubject(current, VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.toggleLike()
        viewModel.toggleFollow()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLiked)
        assertTrue(viewModel.uiState.value.isFollowing)
    }

    @Test
    fun `follow owner reserves pending before dispatch and never toggles successful follow off`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(followResponses = listOf(response))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(followStatusKnown = true))

        viewModel.followOwner()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        viewModel.followOwner()
        runCurrent()
        viewModel.followOwner()
        runCurrent()
        assertEquals(listOf(1L to false), actions.followRequests)
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertTrue(events.none { it is VideoEngagementEvent.OpenFollowGroups })

        response.complete(Result.success(true))
        runCurrent()
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertTrue(1L in viewModel.uiState.value.followingMids)
        assertEquals(listOf(VideoEngagementEvent.OpenFollowGroups(1L)), events.filterIsInstance<VideoEngagementEvent.OpenFollowGroups>())

        viewModel.followOwner()
        runCurrent()
        assertEquals(1, actions.followRequests.size)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertEquals(1, events.filterIsInstance<VideoEngagementEvent.OpenFollowGroups>().size)
    }

    @Test
    fun `queued author toggles keep follow pending until all requests complete`() = runTest(dispatcher) {
        val follow = CompletableDeferred<Result<Boolean>>()
        val unfollow = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(followResponses = listOf(follow, unfollow))
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(followStatusKnown = true))

        viewModel.toggleFollow()
        viewModel.toggleFollow()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        viewModel.followOwner()
        runCurrent()
        assertEquals(listOf(1L to false), actions.followRequests)

        follow.complete(Result.success(true))
        runCurrent()
        assertTrue(viewModel.uiState.value.isFollowing)
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        assertEquals(listOf(1L to false, 1L to true), actions.followRequests)

        unfollow.complete(Result.success(false))
        runCurrent()
        assertFalse(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertEquals(2, actions.followRequests.size)
    }

    @Test
    fun `follow owner failure releases pending without success feedback and permits retry`() = runTest(dispatcher) {
        val failure = CompletableDeferred<Result<Boolean>>()
        val success = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(followResponses = listOf(failure, success))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(followStatusKnown = true))

        viewModel.followOwner()
        runCurrent()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        failure.complete(Result.failure(IllegalStateException("follow failed")))
        runCurrent()
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertTrue(viewModel.uiState.value.followingMids.isEmpty())
        assertTrue(events.none { it is VideoEngagementEvent.OpenFollowGroups })

        viewModel.followOwner()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        runCurrent()
        assertEquals(listOf(1L to false, 1L to false), actions.followRequests)
        success.complete(Result.success(true))
        runCurrent()
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertEquals(1, events.filterIsInstance<VideoEngagementEvent.OpenFollowGroups>().size)
    }

    @Test
    fun `follow owner rejects unknown status and already followed author`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(actions = actions)
        val current = subject("BV1", 1L)
        viewModel.bindSubject(current, VideoEngagementSeed())
        viewModel.followOwner()
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        runCurrent()
        assertTrue(actions.followRequests.isEmpty())

        viewModel.bindSubject(current, VideoEngagementSeed(followStatusKnown = true, isFollowing = true))
        viewModel.followOwner()
        runCurrent()
        assertTrue(actions.followRequests.isEmpty())
        assertTrue(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
    }

    @Test
    fun `old follow result cannot alter new author or release its pending request`() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<Result<Boolean>>()
        val newResponse = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(followResponses = listOf(oldResponse, newResponse))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(followStatusKnown = true))
        viewModel.followOwner()
        runCurrent()

        val next = subject("BV2", 2L).copy(ownerMid = 2L)
        viewModel.bindSubject(next, VideoEngagementSeed(followStatusKnown = true))
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        viewModel.followOwner()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        runCurrent()
        oldResponse.complete(Result.success(true))
        runCurrent()
        assertEquals(next, viewModel.uiState.value.subject)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertTrue(viewModel.uiState.value.followingMids.isEmpty())
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(events.isEmpty())
        assertEquals(listOf(1L to false, 2L to false), actions.followRequests)

        viewModel.followOwner()
        runCurrent()
        assertEquals(2, actions.followRequests.size)
        newResponse.complete(Result.success(true))
        runCurrent()
        assertTrue(viewModel.uiState.value.isFollowing)
        assertEquals(setOf(2L), viewModel.uiState.value.followingMids)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertEquals(listOf(VideoEngagementEvent.OpenFollowGroups(2L)), events.filterIsInstance<VideoEngagementEvent.OpenFollowGroups>())
    }

    @Test
    fun `triple reserves pending synchronously and celebrates one actual completed request`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<TripleActionResult>>()
        val actions = FakeEngagementActions(tripleResponses = listOf(response))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, likeCount = 10, favoriteCount = 20))

        viewModel.doTripleAction()
        assertTrue(viewModel.uiState.value.isTripleSubmitting)
        viewModel.doTripleAction()
        runCurrent()
        viewModel.doTripleAction()
        runCurrent()
        assertEquals(listOf(1L to 2), actions.tripleRequests)
        assertTrue(viewModel.uiState.value.isTripleSubmitting)
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertFalse(viewModel.uiState.value.isFavorited)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)

        response.complete(Result.success(TripleActionResult(true, true, null, true)))
        runCurrent()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals(11, viewModel.uiState.value.likeCount)
        assertEquals(2, viewModel.uiState.value.coinCount)
        assertTrue(viewModel.uiState.value.isFavorited)
        assertEquals(21, viewModel.uiState.value.favoriteCount)
        assertTrue(viewModel.uiState.value.tripleCelebrationVisible)
        assertEquals(1, events.count { it is VideoEngagementEvent.InvalidateFavoriteFolders })
        val celebrationId = viewModel.uiState.value.tripleCelebrationId

        viewModel.doTripleAction()
        runCurrent()
        assertEquals(1, actions.tripleRequests.size)
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertEquals(celebrationId, viewModel.uiState.value.tripleCelebrationId)
        assertEquals(1, events.count { it is VideoEngagementEvent.InvalidateFavoriteFolders })
    }

    @Test
    fun `partial triple preserves actual successes and retries unfinished interactions`() = runTest(dispatcher) {
        val partial = CompletableDeferred<Result<TripleActionResult>>()
        val retry = CompletableDeferred<Result<TripleActionResult>>()
        val actions = FakeEngagementActions(tripleResponses = listOf(partial, retry))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, isDisliked = true, likeCount = 10, favoriteCount = 20))

        viewModel.doTripleAction()
        runCurrent()
        partial.complete(Result.success(TripleActionResult(true, false, "coin failed", true)))
        runCurrent()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertTrue(viewModel.uiState.value.isLiked)
        assertFalse(viewModel.uiState.value.isDisliked)
        assertEquals(11, viewModel.uiState.value.likeCount)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertTrue(viewModel.uiState.value.isFavorited)
        assertEquals(21, viewModel.uiState.value.favoriteCount)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertEquals(1, events.count { it is VideoEngagementEvent.InvalidateFavoriteFolders })

        viewModel.doTripleAction()
        assertTrue(viewModel.uiState.value.isTripleSubmitting)
        runCurrent()
        assertEquals(2, actions.tripleRequests.size)
        retry.complete(Result.success(TripleActionResult(false, true, null, false)))
        runCurrent()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals(11, viewModel.uiState.value.likeCount)
        assertEquals(2, viewModel.uiState.value.coinCount)
        assertTrue(viewModel.uiState.value.isFavorited)
        assertEquals(21, viewModel.uiState.value.favoriteCount)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertEquals(1, events.count { it is VideoEngagementEvent.InvalidateFavoriteFolders })
    }

    @Test
    fun `failed triple releases pending without fabricated success and permits retry`() = runTest(dispatcher) {
        val failure = CompletableDeferred<Result<TripleActionResult>>()
        val retry = CompletableDeferred<Result<TripleActionResult>>()
        val actions = FakeEngagementActions(tripleResponses = listOf(failure, retry))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, likeCount = 10, favoriteCount = 20))
        viewModel.doTripleAction()
        runCurrent()
        failure.complete(Result.failure(IllegalStateException("triple failed")))
        runCurrent()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(10, viewModel.uiState.value.likeCount)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertFalse(viewModel.uiState.value.isFavorited)
        assertEquals(20, viewModel.uiState.value.favoriteCount)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertTrue(events.none { it is VideoEngagementEvent.InvalidateFavoriteFolders })

        viewModel.doTripleAction()
        assertTrue(viewModel.uiState.value.isTripleSubmitting)
        runCurrent()
        assertEquals(2, actions.tripleRequests.size)
        retry.complete(Result.success(TripleActionResult(true, true, null, true)))
        runCurrent()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertTrue(viewModel.uiState.value.tripleCelebrationVisible)
    }

    @Test
    fun `triple rejects unknown like status and completed seeded interactions`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(actions = actions)
        val current = subject("BV1", 1L)
        viewModel.bindSubject(current, VideoEngagementSeed())
        viewModel.doTripleAction()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        runCurrent()
        assertTrue(actions.tripleRequests.isEmpty())

        viewModel.bindSubject(current, VideoEngagementSeed(likeStatusKnown = true, isLiked = true, coinCount = 2, isFavorited = true))
        viewModel.doTripleAction()
        runCurrent()
        assertTrue(actions.tripleRequests.isEmpty())
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)

        viewModel.bindSubject(subject("BV2", 2L), VideoEngagementSeed(likeStatusKnown = true, isLiked = true, coinCount = 1, isFavorited = true, isRepost = true))
        viewModel.doTripleAction()
        runCurrent()
        assertTrue(actions.tripleRequests.isEmpty())
    }

    @Test
    fun `old triple result cannot overwrite next video or release its pending request`() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<Result<TripleActionResult>>()
        val newResponse = CompletableDeferred<Result<TripleActionResult>>()
        val actions = FakeEngagementActions(tripleResponses = listOf(oldResponse, newResponse))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true))
        viewModel.doTripleAction()
        runCurrent()
        val next = subject("BV2", 2L)
        viewModel.bindSubject(next, VideoEngagementSeed(likeStatusKnown = true, likeCount = 30, favoriteCount = 40))
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        viewModel.doTripleAction()
        assertTrue(viewModel.uiState.value.isTripleSubmitting)
        runCurrent()
        events.clear()

        oldResponse.complete(Result.success(TripleActionResult(true, true, null, true)))
        runCurrent()
        assertEquals(next, viewModel.uiState.value.subject)
        assertTrue(viewModel.uiState.value.isTripleSubmitting)
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(30, viewModel.uiState.value.likeCount)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertFalse(viewModel.uiState.value.isFavorited)
        assertEquals(40, viewModel.uiState.value.favoriteCount)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertTrue(events.none { it is VideoEngagementEvent.InvalidateFavoriteFolders || it is VideoEngagementEvent.LoadVideo })
        assertEquals(listOf(1L to 2, 2L to 2), actions.tripleRequests)
        viewModel.doTripleAction()
        runCurrent()
        assertEquals(2, actions.tripleRequests.size)

        newResponse.complete(Result.success(TripleActionResult(true, true, null, true)))
        runCurrent()
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertEquals(31, viewModel.uiState.value.likeCount)
        assertEquals(2, viewModel.uiState.value.coinCount)
        assertEquals(41, viewModel.uiState.value.favoriteCount)
        assertTrue(viewModel.uiState.value.tripleCelebrationVisible)
        assertEquals(1, events.count { it is VideoEngagementEvent.InvalidateFavoriteFolders })
    }

    @Test
    fun `cancelled engagement requests release pending without completion feedback`() = runTest(dispatcher) {
        val followResponse = CompletableDeferred<Result<Boolean>>()
        val tripleResponse = CompletableDeferred<Result<TripleActionResult>>()
        val actions = FakeEngagementActions(
            followResponses = listOf(followResponse),
            tripleResponses = listOf(tripleResponse)
        )
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeStatusKnown = true, followStatusKnown = true))
        viewModel.followOwner()
        viewModel.doTripleAction()
        runCurrent()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(viewModel.uiState.value.isTripleSubmitting)

        followResponse.complete(Result.failure(CancellationException("follow cancelled")))
        tripleResponse.complete(Result.failure(CancellationException("triple cancelled")))
        runCurrent()
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(0, viewModel.uiState.value.coinCount)
        assertFalse(viewModel.uiState.value.isFavorited)
        assertFalse(viewModel.uiState.value.tripleCelebrationVisible)
        assertTrue(events.none { it is VideoEngagementEvent.OpenFollowGroups || it is VideoEngagementEvent.InvalidateFavoriteFolders })

        viewModel.followOwner()
        viewModel.doTripleAction()
        runCurrent()
        assertEquals(2, actions.followRequests.size)
        assertEquals(2, actions.tripleRequests.size)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertTrue(viewModel.uiState.value.tripleCelebrationVisible)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertFalse(viewModel.uiState.value.isTripleSubmitting)
    }

    @Test
    fun `unknown like queries actual state before toggling`() = runTest(dispatcher) {
        val actions = FakeEngagementActions(likeStatus = Result.success(true))
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed(likeCount = 10))
        viewModel.toggleLike()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.likeStatusKnown)
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(9, viewModel.uiState.value.likeCount)
        assertEquals(1, actions.likeToggleCalls)
    }

    @Test
    fun `failed like confirmation never submits a toggle`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())
        viewModel.toggleLike()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.likeStatusKnown)
        assertEquals(0, actions.likeToggleCalls)
    }

    @Test
    fun `late like confirmation cannot act on another video`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(pendingLikeStatus = pending)
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())
        viewModel.toggleLike()
        runCurrent()
        viewModel.bindSubject(subject("BV2", 2L), VideoEngagementSeed())
        pending.complete(Result.success(false))
        advanceUntilIdle()
        assertEquals("BV2", viewModel.uiState.value.subject?.bvid)
        assertFalse(viewModel.uiState.value.likeStatusKnown)
        assertEquals(0, actions.likeToggleCalls)
    }

    @Test
    fun `repeated taps while confirming submit only one like`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(pendingLikeStatus = pending)
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())
        viewModel.toggleLike()
        viewModel.toggleLike()
        runCurrent()
        pending.complete(Result.success(false))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals(1, actions.likeToggleCalls)
    }

    @Test
    fun `unknown follow confirms actual followed state before toggling off`() = runTest(dispatcher) {
        val actions = FakeEngagementActions(followStatus = Result.success(true))
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())

        viewModel.toggleFollow()
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        advanceUntilIdle()

        assertEquals(listOf(1L to true), actions.followRequests)
        assertTrue(viewModel.uiState.value.followStatusKnown)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(viewModel.uiState.value.followingMids.isEmpty())
    }

    @Test
    fun `failed follow confirmation preserves unknown state and permits another attempt`() = runTest(dispatcher) {
        val actions = FakeEngagementActions()
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())

        viewModel.toggleFollow()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.followStatusKnown)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(actions.followRequests.isEmpty())

        actions.followStatus = Result.success(false)
        viewModel.toggleFollow()
        advanceUntilIdle()
        assertEquals(listOf(1L to false), actions.followRequests)
        assertTrue(viewModel.uiState.value.followStatusKnown)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertEquals(setOf(1L), viewModel.uiState.value.followingMids)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
    }

    @Test
    fun `recovered follow status survives a failed toggle and stale playback seed`() = runTest(dispatcher) {
        val failure = CompletableDeferred(Result.failure<Boolean>(IllegalStateException("offline")))
        val actions = FakeEngagementActions(
            followStatus = Result.success(true),
            followResponses = listOf(failure),
        )
        val viewModel = VideoEngagementViewModel(actions = actions)
        val current = subject("BV1", 1L)
        viewModel.bindSubject(current, VideoEngagementSeed())

        viewModel.toggleFollow()
        advanceUntilIdle()
        viewModel.bindSubject(current, VideoEngagementSeed())

        assertEquals(listOf(1L to true), actions.followRequests)
        assertTrue(viewModel.uiState.value.followStatusKnown)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertEquals(setOf(1L), viewModel.uiState.value.followingMids)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
    }

    @Test
    fun `late follow confirmation cannot act on next author or release its pending request`() = runTest(dispatcher) {
        val pendingStatus = CompletableDeferred<Result<Boolean>>()
        val nextFollow = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(
            pendingFollowStatus = pendingStatus,
            followResponses = listOf(nextFollow),
        )
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())
        viewModel.toggleFollow()
        runCurrent()

        val next = subject("BV2", 2L).copy(ownerMid = 2L)
        viewModel.bindSubject(next, VideoEngagementSeed(followStatusKnown = true))
        viewModel.toggleFollow()
        pendingStatus.complete(Result.success(true))
        runCurrent()

        assertEquals(next, viewModel.uiState.value.subject)
        assertEquals(listOf(2L to false), actions.followRequests)
        assertFalse(viewModel.uiState.value.isFollowing)
        assertTrue(viewModel.uiState.value.isFollowSubmitting)
        nextFollow.complete(Result.success(true))
        advanceUntilIdle()
        assertEquals(setOf(2L), viewModel.uiState.value.followingMids)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
    }

    @Test
    fun `repeated taps during follow confirmation submit only one toggle`() = runTest(dispatcher) {
        val pendingStatus = CompletableDeferred<Result<Boolean>>()
        val actions = FakeEngagementActions(pendingFollowStatus = pendingStatus)
        val viewModel = VideoEngagementViewModel(actions = actions)
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())

        viewModel.toggleFollow()
        viewModel.toggleFollow()
        runCurrent()
        viewModel.toggleFollow()
        pendingStatus.complete(Result.success(false))
        advanceUntilIdle()

        assertEquals(listOf(1L to false), actions.followRequests)
        assertTrue(viewModel.uiState.value.isFollowing)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
    }

    @Test
    fun `cancelled follow confirmation releases pending without submitting a toggle`() = runTest(dispatcher) {
        val actions = FakeEngagementActions(followStatus = Result.failure(CancellationException("cancelled")))
        val viewModel = VideoEngagementViewModel(actions = actions)
        val events = mutableListOf<VideoEngagementEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.bindSubject(subject("BV1", 1L), VideoEngagementSeed())

        viewModel.toggleFollow()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.followStatusKnown)
        assertFalse(viewModel.uiState.value.isFollowSubmitting)
        assertTrue(actions.followRequests.isEmpty())
        assertTrue(events.isEmpty())
    }

    private fun subject(bvid: String, generation: Long) = VideoSubjectSnapshot(
        bvid = bvid,
        cid = generation,
        aid = generation,
        ownerMid = 1L,
        title = bvid,
        coverUrl = "",
        durationMs = 1_000L,
        generation = generation
    )

    private class FakeEngagementActions(
        private val pendingLike: CompletableDeferred<Result<Boolean>>? = null,
        private val failCoin: Boolean = false,
        private val likeStatus: Result<Boolean> = Result.failure(IllegalStateException("offline")),
        private val pendingLikeStatus: CompletableDeferred<Result<Boolean>>? = null,
        var followStatus: Result<Boolean> = Result.failure(IllegalStateException("offline")),
        private val pendingFollowStatus: CompletableDeferred<Result<Boolean>>? = null,
        private val followResponses: List<CompletableDeferred<Result<Boolean>>> = emptyList(),
        private val tripleResponses: List<CompletableDeferred<Result<TripleActionResult>>> = emptyList()
    ) : VideoEngagementActions {
        override suspend fun queryLikeStatus(aid: Long) = pendingLikeStatus?.await() ?: likeStatus
        override suspend fun queryFollowStatus(mid: Long) = pendingFollowStatus?.await() ?: followStatus

        var likeToggleCalls = 0
            private set

        val followRequests = mutableListOf<Pair<Long, Boolean>>()
        val tripleRequests = mutableListOf<Pair<Long, Int>>()

        override suspend fun toggleFollow(mid: Long, currentlyFollowing: Boolean): Result<Boolean> {
            val response = followResponses.getOrNull(followRequests.size)
            followRequests += mid to currentlyFollowing
            return response?.await() ?: Result.success(!currentlyFollowing)
        }

        override suspend fun toggleLike(aid: Long, currentlyLiked: Boolean, bvid: String): Result<Boolean> {
            likeToggleCalls++
            return pendingLike?.await() ?: Result.success(!currentlyLiked)
        }

        override suspend fun toggleDislike(aid: Long, currentlyDisliked: Boolean, bvid: String) =
            Result.success(!currentlyDisliked)

        override suspend fun toggleFavorite(aid: Long, currentlyFavorited: Boolean, bvid: String) =
            Result.success(!currentlyFavorited)

        override suspend fun toggleWatchLater(
            aid: Long,
            currentlyInWatchLater: Boolean,
            bvid: String
        ) = Result.success(!currentlyInWatchLater)

        override suspend fun doCoin(aid: Long, count: Int, alsoLike: Boolean, bvid: String) =
            if (failCoin) Result.failure(IllegalStateException("coin failed")) else Result.success(true)

        override suspend fun doTripleAction(aid: Long, coinCount: Int) = run {
            lastTripleCoinCount = coinCount
            val response = tripleResponses.getOrNull(tripleRequests.size)
            tripleRequests += aid to coinCount
            if (response != null) return@run response.await()
            Result.success(
                TripleActionResult(
                    likeSuccess = true,
                    coinSuccess = true,
                    coinMessage = null,
                    favoriteSuccess = true
                )
            )
        }

        var lastTripleCoinCount: Int? = null
            private set
    }
}
