package com.android.purebilibili.feature.story

import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.model.response.StoryPlayerArgs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryFeedSessionTest {
    @Test
    fun `home entry resumes only unvisited items with retained and recreated owners`() = runTest {
        val pages = mutableListOf<Int>()
        val store = StoryContinuationStore()
        fun session() = StoryFeedSession(
            this,
            loadStory = { _, page ->
                pages += page
                Result.success(when (page) {
                    1 -> clips(1, 2, 3)
                    2 -> clips(4, 5)
                    else -> clips(6)
                })
            },
            loadHome = { error("HOME should not be requested") },
            store = store,
            accountKey = { "account" }
        )
        val retained = session()
        retained.startFeed()
        runCurrent()
        retained.updateCurrentVideo("BV1")
        retained.updateCurrentVideo("BV2")
        retained.loadMoreStories()
        runCurrent()
        retained.updateCurrentVideo("BV3")
        // Pagination appends; it never changes the playing queue's first item or index.
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), retained.state.value.items.map { it.id })
        assertEquals(2, retained.state.value.currentIndex)
        retained.startFeed()
        assertEquals(listOf(1, 2), pages)
        retained.stopFeed()
        retained.startFeed()
        assertEquals(listOf(4L, 5L), retained.state.value.items.map { it.id })
        assertEquals(listOf(1, 2), pages)
        retained.updateCurrentVideo("BV4")
        retained.stopFeed()

        val recreated = session()
        recreated.startFeed()
        assertEquals(listOf(5L), recreated.state.value.items.map { it.id })
        recreated.updateCurrentVideo("BV5")
        recreated.stopFeed()
        val next = session()
        next.startFeed()
        runCurrent()
        assertEquals(listOf(6L), next.state.value.items.map { it.id })
        assertEquals(listOf(1, 2, 3), pages)
        next.stopFeed()
    }

    @Test
    fun `HOME fallback and its cursor survive reentry`() = runTest {
        var storyCalls = 0
        val homePages = mutableListOf<Int>()
        val store = StoryContinuationStore()
        fun session() = StoryFeedSession(
            this,
            loadStory = { _, _ -> storyCalls++; Result.failure(IllegalStateException("unavailable")) },
            loadHome = { page ->
                homePages += page
                Result.success(if (page == 0) clips(1, 2) else clips(3))
            },
            store = store,
            accountKey = { "account" }
        )
        val first = session()
        first.startFeed()
        runCurrent()
        first.updateCurrentVideo("BV1")
        first.stopFeed()
        val second = session()
        second.startFeed()
        assertEquals(listOf(2L), second.state.value.items.map { it.id })
        second.updateCurrentVideo("BV2")
        second.stopFeed()
        val third = session()
        third.startFeed()
        runCurrent()
        assertEquals(listOf(3L), third.state.value.items.map { it.id })
        assertEquals(1, storyCalls)
        assertEquals(listOf(0, 1), homePages)
        third.stopFeed()
    }

    @Test
    fun `repeated successful responses terminate and retry advances without replaying watched video`() = runTest {
        val storyPages = mutableListOf<Int>()
        val homePages = mutableListOf<Int>()
        val session = StoryFeedSession(
            this,
            loadStory = { _, page -> storyPages += page; Result.success(clips(1)) },
            loadHome = { page ->
                homePages += page
                Result.success(if (page < 3) clips(1) else clips(2))
            },
            store = StoryContinuationStore(),
            accountKey = { "account" }
        )
        session.startFeed()
        runCurrent()
        session.updateCurrentVideo("BV1")
        session.stopFeed()
        session.startFeed()
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4), storyPages)
        assertEquals(listOf(0, 1, 2), homePages)
        assertTrue(session.state.value.items.isEmpty())
        assertFalse(session.state.value.isLoading)
        assertNotNull(session.state.value.error)
        session.refresh()
        runCurrent()
        assertEquals(listOf(0, 1, 2, 3), homePages)
        assertEquals(listOf(2L), session.state.value.items.map { it.id })
        assertNull(session.state.value.error)
        session.stopFeed()
    }

    @Test
    fun `empty Story response falls back immediately and HOME failure is retryable at same cursor`() = runTest {
        val homePages = mutableListOf<Int>()
        val session = StoryFeedSession(
            this,
            loadStory = { _, _ -> Result.success(emptyList()) },
            loadHome = { page ->
                homePages += page
                if (homePages.size == 1) Result.failure(IllegalStateException("offline"))
                else Result.success(clips(1))
            },
            store = StoryContinuationStore(),
            accountKey = { "account" }
        )
        session.startFeed()
        runCurrent()
        assertEquals("offline", session.state.value.error)
        session.refresh()
        runCurrent()
        assertEquals(listOf(0, 0), homePages)
        assertEquals(listOf(1L), session.state.value.items.map { it.id })
        session.stopFeed()
    }

    @Test
    fun `seeded entry honors deliberate replay without replacing home continuation`() = runTest {
        val store = StoryContinuationStore()
        val requests = mutableListOf<Pair<String, Int>>()
        fun session() = StoryFeedSession(
            this,
            loadStory = { seed, page -> requests += seed to page; Result.success(clips(1, 2)) },
            loadHome = { error("HOME should not be requested") },
            store = store,
            accountKey = { "account" }
        )
        val home = session()
        home.startFeed()
        runCurrent()
        home.updateCurrentVideo("BV1")
        home.stopFeed()
        val seeded = session()
        repeat(2) {
            seeded.startFeed(" BV1 ")
            runCurrent()
            val feed = assertNotNull(buildStoryPortraitFeed(seeded.state.value.items, StoryFeedSeed("BV1", 1, "")))
            assertEquals("BV1", feed.initialInfo.bvid)
            seeded.updateCurrentVideo("BV1")
            seeded.stopFeed()
        }
        val resumed = session()
        resumed.startFeed()
        assertEquals(listOf(2L), resumed.state.value.items.map { it.id })
        assertEquals(listOf("" to 1, "BV1" to 1, "BV1" to 1), requests)
        resumed.stopFeed()
    }

    @Test
    fun `late canceled response cannot append items or advance new session cursor`() = runTest {
        val late = CompletableDeferred<Result<List<StoryItem>>>()
        val pages = mutableListOf<Int>()
        val session = StoryFeedSession(
            this,
            loadStory = { _, page ->
                pages += page
                if (pages.size == 1) withContext(NonCancellable) { late.await() }
                else Result.success(if (page == 1) clips(2) else clips(3))
            },
            loadHome = { error("HOME should not be requested") },
            store = StoryContinuationStore(),
            accountKey = { "account" }
        )
        session.startFeed()
        runCurrent()
        session.stopFeed()
        session.startFeed()
        runCurrent()
        late.complete(Result.success(clips(1)))
        runCurrent()
        assertEquals(listOf(2L), session.state.value.items.map { it.id })
        session.loadMoreStories()
        runCurrent()
        assertEquals(listOf(1, 1, 2), pages)
        assertEquals(listOf(2L, 3L), session.state.value.items.map { it.id })
        session.stopFeed()
    }

    @Test
    fun `refresh cancels pending preload without replacing playing queue`() = runTest {
        val late = CompletableDeferred<Result<List<StoryItem>>>()
        var calls = 0
        val session = StoryFeedSession(
            this,
            loadStory = { _, _ ->
                calls++
                when (calls) {
                    1 -> Result.success(clips(1, 2))
                    2 -> withContext(NonCancellable) { late.await() }
                    else -> Result.success(clips(3))
                }
            },
            loadHome = { error("HOME should not be requested") },
            store = StoryContinuationStore(),
            accountKey = { "account" }
        )
        session.startFeed()
        runCurrent()
        session.updateCurrentVideo("BV2")
        session.loadMoreStories()
        runCurrent()
        session.refresh()
        runCurrent()
        late.complete(Result.success(clips(99)))
        runCurrent()
        assertEquals(listOf(1L, 2L, 3L), session.state.value.items.map { it.id })
        assertEquals(1, session.state.value.currentIndex)
        assertFalse(session.state.value.isLoading)
        session.stopFeed()
    }

    @Test
    fun `continuation is bounded expiring and account scoped`() {
        var now = 0L
        val store = StoryContinuationStore { now }
        val first = store.claim("a").first
        store.save(first, StoryFeedContinuation(
            items = (1..100).map { clip(it) },
            watchedKeys = (1..1000).map { "bvid:BV$it" }
        ))
        val (second, saved) = store.claim("a")
        assertEquals(60, assertNotNull(saved).items.size)
        assertEquals(900, saved.watchedKeys.size)
        assertEquals("bvid:BV101", saved.watchedKeys.first())
        store.save(second, saved)
        assertNull(store.claim("b").second)
        val lease = store.claim("a").first
        store.save(lease, saved)
        now = 30 * 60 * 1000L + 1
        assertNull(store.claim("a").second)
    }

    @Test
    fun `watched aliases prevent same video with changed Story id from replaying`() = runTest {
        val session = StoryFeedSession(
            this,
            loadStory = { _, page ->
                Result.success(if (page == 1) clips(1) else listOf(clip(1).copy(id = 99), clip(2)))
            },
            loadHome = { error("HOME should not be requested") },
            store = StoryContinuationStore(),
            accountKey = { "account" }
        )
        session.startFeed()
        runCurrent()
        session.updateCurrentVideo("BV1")
        session.stopFeed()
        session.startFeed()
        runCurrent()
        assertEquals(listOf(2L), session.state.value.items.map { it.id })
        session.stopFeed()
    }

    @Test
    fun `obsolete owner cannot overwrite or restore a claimed continuation`() {
        val store = StoryContinuationStore()
        val oldLease = store.claim("account").first
        val currentLease = store.claim("account").first
        store.save(oldLease, StoryFeedContinuation(items = clips(1)))
        assertNull(store.claim("account").second)
        store.save(currentLease, StoryFeedContinuation(items = clips(2)))
        assertNull(store.claim("account").second)
    }

    @Test
    fun `long session does not restore consumed prefix after bounded history evicts old aliases`() = runTest {
        val session = StoryFeedSession(
            this,
            loadStory = { _, _ -> Result.success((1..450).map(::clip)) },
            loadHome = { error("HOME should not be requested") },
            store = StoryContinuationStore(),
            accountKey = { "account" }
        )
        session.startFeed()
        runCurrent()
        (1..400).forEach { session.updateCurrentVideo("BV$it") }
        session.updateCurrentVideo("BV399")
        session.stopFeed()
        session.startFeed()
        assertEquals((401L..450L).toList(), session.state.value.items.map { it.id })
        session.stopFeed()
    }

    @Test
    fun `account switch discards response and starts a clean account scoped feed`() = runTest {
        var account = "a"
        val late = CompletableDeferred<Result<List<StoryItem>>>()
        var calls = 0
        val session = StoryFeedSession(
            this,
            loadStory = { _, _ ->
                calls++
                if (calls == 1) late.await() else Result.success(clips(2))
            },
            loadHome = { error("HOME should not be requested") },
            store = StoryContinuationStore(),
            accountKey = { account }
        )
        session.startFeed()
        runCurrent()
        account = "b"
        late.complete(Result.success(clips(1)))
        runCurrent()
        assertEquals(listOf(2L), session.state.value.items.map { it.id })
        assertFalse(session.state.value.isLoading)
        session.stopFeed()
    }

    @Test
    fun `account switch starts new request without waiting for old preload`() = runTest {
        var account = "a"
        val late = CompletableDeferred<Result<List<StoryItem>>>()
        val requests = mutableListOf<Pair<String, Int>>()
        val session = StoryFeedSession(
            this,
            loadStory = { _, page ->
                val requestAccount = account
                requests += requestAccount to page
                if (requestAccount == "a") withContext(NonCancellable) { late.await() }
                else Result.success(clips(2, 3))
            },
            loadHome = { error("HOME should not be requested") },
            store = StoryContinuationStore(),
            accountKey = { account },
        )
        session.startFeed()
        runCurrent()
        account = "b"
        session.loadMoreStories()
        runCurrent()
        assertEquals(listOf("a" to 1, "b" to 1), requests)
        assertEquals(listOf(2L, 3L), session.state.value.items.map { it.id })
        assertFalse(session.state.value.isLoading)

        late.complete(Result.success(clips(1)))
        runCurrent()
        assertEquals(listOf(2L, 3L), session.state.value.items.map { it.id })
        session.stopFeed()
    }

    private fun clips(vararg ids: Int): List<StoryItem> = ids.map(::clip)

    private fun clip(id: Int) = StoryItem(
        id = id.toLong(),
        bvid = "BV$id",
        goto = "av",
        playerArgs = StoryPlayerArgs(aid = id.toLong(), bvid = "BV$id", cid = id.toLong())
    )
}
