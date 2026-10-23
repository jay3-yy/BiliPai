package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.feature.video.usecase.TripleActionResult
import com.android.purebilibili.feature.video.viewmodel.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortraitVideoEngagementSessionTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `foreign coin dialog waits for its real status and spends only that video`() = runTest(dispatcher) {
        val backend = Backend()
        val root = model(backend)
        root.bindSubject(subject("ORIGINAL", 10), readySeed().copy(coinCount = 2, isLiked = true, likeCount = 100))
        val pending = CompletableDeferred<Result<PortraitEngagementSnapshot>>()
        val account = ActionRepository.interactionSession()
        val store = PortraitVideoEngagementStore(backgroundScope, { _, _ -> },
            { root.recordExternalVideoEngagement(it, account) },
            PortraitEngagementLoader { _, _, _, _ -> pending.await() }, { model(backend) })
        try {
            val target = subject("FOREIGN", 20).copy(isRepost = true)
            val session = store.session(target, info(target), VideoEngagementSeed(isLoggedIn = true, isRepost = true, likeCount = 8))
            store.activate(Any(), session) { fail("unexpected navigation") }
            session.openCoinDialog()
            runCurrent()
            assertFalse(session.model.uiState.value.coinDialogVisible)
            assertTrue(backend.coins.isEmpty())
            pending.complete(Result.success(PortraitEngagementSnapshot(target, readySeed().copy(isRepost = true, likeCount = 8), info(target))))
            runCurrent()
            assertTrue(session.model.uiState.value.coinDialogVisible)
            assertEquals(1, session.model.uiState.value.coinLimit)
            assertEquals(0, session.model.uiState.value.coinCount)
            assertFalse(root.uiState.value.coinDialogVisible)
            session.confirmCoins(1, false)
            runCurrent()
            assertEquals(listOf(CoinRequest(20, 1, false, "FOREIGN")), backend.coins)
            assertEquals(1, session.model.uiState.value.coinCount)
            assertEquals("ORIGINAL", root.uiState.value.subject?.bvid)
            assertEquals(2, root.uiState.value.coinCount)
            assertTrue(root.uiState.value.isLiked)
            assertEquals(100, root.uiState.value.likeCount)
            root.bindSubject(target.copy(generation = 2), readySeed().copy(isRepost = true, likeCount = 8))
            assertEquals(1, root.uiState.value.coinCount)
            root.sync(readySeed().copy(isRepost = true, coinCount = 0, likeCount = 8))
            assertEquals(1, root.uiState.value.coinCount)
        } finally { store.close() }
    }

    @Test fun `parts share confirmed state without querying or replacing it with zero defaults`() = runTest(dispatcher) {
        val backend = Backend()
        var reads = 0
        val store = PortraitVideoEngagementStore(backgroundScope, { _, _ -> }, {},
            PortraitEngagementLoader { _, _, _, _ -> reads++; error("known state must not be fetched") }, { model(backend) })
        try {
            val target = subject("PARTS", 30)
            val first = store.session(target, info(target), readySeed().copy(isFavorited = true, coinCount = 1, likeCount = 12))
            first.toggleLike()
            runCurrent()
            assertTrue(first.model.uiState.value.isLiked)
            assertEquals(13, first.model.uiState.value.likeCount)
            val next = store.session(target.copy(cid = 999), info(target).copy(cid = 999), VideoEngagementSeed())
            next.prepare()
            runCurrent()
            assertSame(first, next)
            assertTrue(next.model.uiState.value.isFavorited)
            assertTrue(next.model.uiState.value.isLiked)
            assertEquals(1, next.model.uiState.value.coinCount)
            assertEquals(13, next.model.uiState.value.likeCount)
            assertEquals(0, reads)
        } finally { store.close() }
    }

    @Test fun `failed missing status does not invent an unspent video and a subsequent tap can recover`() = runTest(dispatcher) {
        val backend = Backend()
        val events = mutableListOf<VideoEngagementEvent>()
        var failRead = true
        val target = subject("NETWORK", 40)
        val store = PortraitVideoEngagementStore(backgroundScope, { _, event -> events += event }, {},
            PortraitEngagementLoader { _, _, _, _ ->
                if (failRead) Result.failure(IllegalStateException("relationship unavailable"))
                else Result.success(PortraitEngagementSnapshot(target, readySeed().copy(coinCount = 2, isLiked = true), info(target)))
            }, { model(backend) })
        try {
            val session = store.session(target, info(target), VideoEngagementSeed(
                isLoggedIn = true, isLiked = true, likeStatusKnown = true, likeCount = 9,
            ))
            store.activate(Any(), session) { fail("unexpected navigation") }
            session.openCoinDialog()
            runCurrent()
            assertFalse(session.isReady)
            assertTrue(session.model.uiState.value.isLiked)
            assertEquals(9, session.model.uiState.value.likeCount)
            assertFalse(session.model.uiState.value.coinDialogVisible)
            assertTrue(backend.coins.isEmpty())
            assertTrue(events.any { it is VideoEngagementEvent.Message && it.text == "relationship unavailable" })
            failRead = false
            session.openCoinDialog()
            runCurrent()
            assertTrue(session.isReady)
            assertEquals(2, session.model.uiState.value.coinCount)
            assertFalse(session.model.uiState.value.coinDialogVisible)
            assertTrue(backend.coins.isEmpty())
        } finally { store.close() }
    }

    @Test fun `late prior-video like changes only its cached owner not the current video`() = runTest(dispatcher) {
        val backend = Backend()
        val response = CompletableDeferred<Result<Boolean>>()
        backend.pendingLike = response
        val store = PortraitVideoEngagementStore(backgroundScope, { _, _ -> }, {}, modelFactory = { model(backend) })
        try {
            val a = subject("A", 50)
            val b = subject("B", 60)
            val first = store.session(a, info(a), readySeed().copy(likeCount = 5))
            store.activate(Any(), first) { fail("unexpected navigation") }
            first.toggleLike()
            runCurrent()
            val second = store.session(b, info(b), readySeed().copy(likeCount = 90, coinCount = 1))
            store.activate(Any(), second) { fail("unexpected navigation") }
            response.complete(Result.success(true))
            runCurrent()
            assertTrue(first.model.uiState.value.isLiked)
            assertEquals(6, first.model.uiState.value.likeCount)
            assertFalse(second.model.uiState.value.isLiked)
            assertEquals(90, second.model.uiState.value.likeCount)
            assertEquals(1, second.model.uiState.value.coinCount)
        } finally { store.close() }
    }

    @Test fun `closing account store cancels pending work without a late handoff`() = runTest(dispatcher) {
        val backend = Backend()
        val pending = CompletableDeferred<Result<Boolean>>()
        backend.pendingLike = pending
        val root = model(backend)
        val target = subject("CLOSE", 70)
        root.bindSubject(target, readySeed().copy(likeCount = 4))
        val account = ActionRepository.interactionSession()
        val store = PortraitVideoEngagementStore(backgroundScope, { _, _ -> },
            { root.recordExternalVideoEngagement(it, account) }, modelFactory = { model(backend) })
        val session = store.session(target, info(target), readySeed().copy(likeCount = 4))
        session.toggleLike()
        runCurrent()
        store.close()
        pending.complete(Result.success(true))
        runCurrent()
        assertFalse(root.uiState.value.isLiked)
        assertEquals(4, root.uiState.value.likeCount)
    }

    @Test fun `foreign explicit dislike cannot dispatch through an original-video model`() = runTest(dispatcher) {
        val backend = Backend()
        val root = model(backend)
        root.bindSubject(subject("ROOT", 80), readySeed().copy(isLiked = true, likeCount = 10))
        root.toggleDislike(aid = 81, bvid = "OTHER", currentlyDisliked = false)
        runCurrent()
        assertTrue(backend.dislikes.isEmpty())
        assertTrue(root.uiState.value.isLiked)
        assertFalse(root.uiState.value.isDisliked)
        assertEquals(10, root.uiState.value.likeCount)
    }

    @Test fun `old-account snapshots are rejected and account markers never print credentials`() {
        val backend = Backend()
        val root = model(backend)
        val target = subject("ACCOUNT", 90)
        root.bindSubject(target, readySeed())
        val oldScope = "old-account-fixture-token"
        val stale = root.uiState.value.copy(
            isLiked = true, coinCount = 2, accountSession = videoEngagementAccountScope(oldScope),
        )
        root.recordExternalVideoEngagement(stale, oldScope)
        assertFalse(root.uiState.value.isLiked)
        assertEquals(0, root.uiState.value.coinCount)
        assertFalse(stale.accountSession.toString().contains(oldScope))
        val staleSuccess = VideoPlaybackUiState.Success(
            info = info(target), playUrl = "", coinCount = 2,
            likeStatusKnown = true, followStatusKnown = true, relationshipStatusKnown = true,
            interactionAccountSession = videoEngagementAccountScope(oldScope),
        ).toEngagementSeed()
        assertFalse(staleSuccess.relationshipStatusKnown)
        assertFalse(staleSuccess.likeStatusKnown)
    }

    @Test fun `native detail mutation after handoff wins when another part binds`() = runTest(dispatcher) {
        val backend = Backend().apply { likeResult = false }
        val root = model(backend)
        val target = subject("RETURN", 100)
        root.bindSubject(target, readySeed().copy(likeCount = 10))
        root.recordExternalVideoEngagement(root.uiState.value.copy(isLiked = true, likeCount = 11), ActionRepository.interactionSession())
        root.toggleLike()
        runCurrent()
        assertFalse(root.uiState.value.isLiked)
        root.bindSubject(target.copy(cid = 222, generation = 2), readySeed().copy(isLiked = true, likeCount = 11))
        assertFalse(root.uiState.value.isLiked)
        assertEquals(10, root.uiState.value.likeCount)
    }

    @Test fun `like dislike favorite and triple share only the foreign video model`() = runTest(dispatcher) {
        val backend = Backend()
        val root = model(backend)
        root.bindSubject(subject("ORIGINAL", 110), readySeed().copy(isLiked = true, isFavorited = true, coinCount = 2, likeCount = 100))
        val account = ActionRepository.interactionSession()
        val store = PortraitVideoEngagementStore(backgroundScope, { _, _ -> },
            { root.recordExternalVideoEngagement(it, account) }, modelFactory = { model(backend) })
        try {
            val target = subject("FOREIGN", 120)
            val own = store.session(target, info(target), readySeed().copy(isDisliked = true, likeCount = 8, favoriteCount = 3))
            own.toggleLike()
            runCurrent()
            assertTrue(own.model.uiState.value.isLiked)
            assertFalse(own.model.uiState.value.isDisliked)
            assertEquals(9, own.model.uiState.value.likeCount)
            own.toggleDislike()
            runCurrent()
            assertFalse(own.model.uiState.value.isLiked)
            assertTrue(own.model.uiState.value.isDisliked)
            assertEquals(8, own.model.uiState.value.likeCount)
            own.toggleFavorite()
            runCurrent()
            assertTrue(own.model.uiState.value.isFavorited)
            assertEquals(4, own.model.uiState.value.favoriteCount)
            own.tripleAction()
            runCurrent()
            assertTrue(own.model.uiState.value.isLiked)
            assertFalse(own.model.uiState.value.isDisliked)
            assertEquals(2, own.model.uiState.value.coinCount)
            assertEquals(listOf(120L), backend.likes)
            assertEquals(listOf(120L), backend.dislikes)
            assertEquals(listOf(120L), backend.favorites)
            assertEquals(listOf(120L), backend.triples)
            assertEquals("ORIGINAL", root.uiState.value.subject?.bvid)
            assertEquals(100, root.uiState.value.likeCount)
            assertEquals(2, root.uiState.value.coinCount)
        } finally { store.close() }
    }

    @Test fun `one visible coin confirmation cannot spend twice`() = runTest(dispatcher) {
        val backend = Backend()
        val store = PortraitVideoEngagementStore(backgroundScope, { _, _ -> }, {}, modelFactory = { model(backend) })
        try {
            val target = subject("CONFIRM", 130)
            val own = store.session(target, info(target), readySeed())
            own.openCoinDialog()
            runCurrent()
            assertTrue(own.model.uiState.value.coinDialogVisible)
            own.confirmCoins(1, false)
            own.confirmCoins(1, false)
            runCurrent()
            assertEquals(listOf(CoinRequest(130, 1, false, "CONFIRM")), backend.coins)
            assertEquals(1, own.model.uiState.value.coinCount)
        } finally { store.close() }
    }

    private fun subject(bvid: String, aid: Long) = VideoSubjectSnapshot(
        bvid, cid = aid * 10, aid = aid, ownerMid = aid + 1,
        title = bvid, coverUrl = "", durationMs = 60_000, generation = 1,
    )
    private fun info(subject: VideoSubjectSnapshot) = ViewInfo(
        bvid = subject.bvid, aid = subject.aid, cid = subject.cid,
        owner = Owner(mid = subject.ownerMid), stat = Stat(like = 8, favorite = 3),
        copyright = if (subject.isRepost) 2 else 1,
    )
    private fun readySeed() = VideoEngagementSeed(
        isLoggedIn = true, likeStatusKnown = true, followStatusKnown = true, relationshipStatusKnown = true,
    )
    private fun model(backend: Backend) = VideoEngagementViewModel(
        actions = backend, coinBalanceLoader = VideoCoinBalanceLoader { 8.0 }, tripleJumpEnabled = { false },
    )
    private data class CoinRequest(val aid: Long, val count: Int, val alsoLike: Boolean, val bvid: String)
    private class Backend : VideoEngagementActions {
        val coins = mutableListOf<CoinRequest>()
        val dislikes = mutableListOf<Long>()
        val likes = mutableListOf<Long>()
        val favorites = mutableListOf<Long>()
        val triples = mutableListOf<Long>()
        var pendingLike: CompletableDeferred<Result<Boolean>>? = null
        var likeResult = true
        override suspend fun queryLikeStatus(aid: Long) = Result.success(false)
        override suspend fun queryFollowStatus(mid: Long) = Result.success(false)
        override suspend fun toggleFollow(mid: Long, currentlyFollowing: Boolean) = Result.success(true)
        override suspend fun toggleLike(aid: Long, currentlyLiked: Boolean, bvid: String): Result<Boolean> {
            likes += aid
            return pendingLike?.await() ?: Result.success(likeResult)
        }
        override suspend fun toggleDislike(aid: Long, currentlyDisliked: Boolean, bvid: String): Result<Boolean> {
            dislikes += aid
            return Result.success(true)
        }
        override suspend fun toggleFavorite(aid: Long, currentlyFavorited: Boolean, bvid: String): Result<Boolean> {
            favorites += aid
            return Result.success(true)
        }
        override suspend fun toggleWatchLater(aid: Long, currentlyInWatchLater: Boolean, bvid: String) = Result.success(true)
        override suspend fun doCoin(aid: Long, count: Int, alsoLike: Boolean, bvid: String): Result<Boolean> {
            coins += CoinRequest(aid, count, alsoLike, bvid)
            return Result.success(true)
        }
        override suspend fun doTripleAction(aid: Long, coinCount: Int): Result<TripleActionResult> {
            triples += aid
            return Result.success(TripleActionResult(true, true, null, true))
        }
    }
}
