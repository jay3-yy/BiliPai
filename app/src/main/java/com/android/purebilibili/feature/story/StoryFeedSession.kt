package com.android.purebilibili.feature.story

import com.android.purebilibili.data.model.response.StoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class StoryFeedSource { STORY, HOME }

internal data class StoryFeedContinuation(
    val items: List<StoryItem> = emptyList(),
    val watchedKeys: List<String> = emptyList(),
    val source: StoryFeedSource = StoryFeedSource.STORY,
    val nextDisplayId: Int = 1,
    val nextHomePage: Int = 0
)

/** One process-local, account-scoped continuation, not a persistent watch history. */
internal class StoryContinuationStore(
    private val now: () -> Long = System::currentTimeMillis
) {
    private var account = ""
    private var revision = 0L
    private var savedAt = 0L
    private var saved: StoryFeedContinuation? = null

    fun claim(accountKey: String): Pair<Long, StoryFeedContinuation?> {
        val continuation = saved.takeIf {
            account == accountKey && now() - savedAt in 0..MAX_AGE_MS
        }
        account = accountKey
        revision += 1
        saved = null
        return revision to continuation
    }

    fun save(lease: Long, continuation: StoryFeedContinuation) {
        if (lease != revision) return
        saved = continuation.copy(
            items = continuation.items.take(MAX_UNREAD_ITEMS),
            watchedKeys = continuation.watchedKeys.takeLast(MAX_WATCHED_KEYS)
        )
        savedAt = now()
    }

    companion object {
        const val MAX_UNREAD_ITEMS = 60
        // Each video has at most three aliases (aid, bvid, Story id).
        const val MAX_WATCHED_KEYS = 900
        private const val MAX_AGE_MS = 30 * 60 * 1000L
        val shared = StoryContinuationStore()
    }
}

/**
 * Keeps the playing queue unchanged during pagination. Re-entry resumes the forward unread
 * tail (at most 60 items), never the consumed prefix; both source cursors survive.
 * Explicit video entries always honor their seed and never consume the home continuation.
 */
internal class StoryFeedSession(
    private val scope: CoroutineScope,
    private val loadStory: suspend (String, Int) -> Result<List<StoryItem>>,
    private val loadHome: suspend (Int) -> Result<List<StoryItem>>,
    private val store: StoryContinuationStore,
    private val accountKey: () -> String
) {
    private val mutableState = MutableStateFlow(StoryUiState(isLoading = true))
    val state = mutableState.asStateFlow()
    private var active = false
    private var seed = ""
    private var account = ""
    private var lease = 0L
    private var source = StoryFeedSource.STORY
    private var nextDisplayId = 1
    private var nextHomePage = 0
    private var generation = 0L
    private var furthestVisitedIndex = -1
    private var loadJob: Job? = null
    private val watchedKeys = linkedSetOf<String>()

    fun startFeed(bvid: String = "") {
        val normalized = bvid.trim()
        if (active && seed == normalized && account == accountKey()) return
        stopFeed()
        seed = normalized
        account = accountKey()
        active = true
        generation += 1
        val restored = if (seed.isEmpty()) {
            val (newLease, continuation) = store.claim(account)
            lease = newLease
            continuation
        } else null
        source = restored?.source ?: StoryFeedSource.STORY
        nextDisplayId = restored?.nextDisplayId ?: 1
        nextHomePage = restored?.nextHomePage ?: 0
        furthestVisitedIndex = -1
        watchedKeys.clear()
        watchedKeys.addAll(restored?.watchedKeys.orEmpty())
        mutableState.value = StoryUiState(items = restored?.items.orEmpty(), sessionId = generation)
        if (mutableState.value.items.isEmpty()) loadMoreStories()
    }

    fun stopFeed() {
        if (!active) return
        generation += 1
        loadJob?.cancel()
        active = false
        if (seed.isEmpty() && account == accountKey()) {
            store.save(lease, StoryFeedContinuation(
                items = mutableState.value.items.drop(furthestVisitedIndex + 1).filterNot(::wasWatched),
                watchedKeys = watchedKeys.toList(),
                source = source,
                nextDisplayId = nextDisplayId,
                nextHomePage = nextHomePage
            ))
        }
        mutableState.value = StoryUiState(isLoading = true)
    }

    /** Retry from the current cursor, never replay page one or discard the active queue. */
    fun refresh() {
        if (!active) return
        generation += 1
        loadJob?.cancel()
        mutableState.value = mutableState.value.copy(isLoading = false, error = null)
        loadMoreStories()
    }

    fun loadMoreStories() {
        if (!active) return
        if (account != accountKey()) {
            startFeed(seed)
            return
        }
        if (mutableState.value.isLoading) return
        val requestGeneration = generation
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        loadJob = scope.launch {
            var emptyPages = 0
            while (true) {
                val result = if (source == StoryFeedSource.STORY) {
                    loadStory(seed, nextDisplayId)
                } else loadHome(nextHomePage)
                currentCoroutineContext().ensureActive()

                if (!active || requestGeneration != generation) return@launch
                if (account != accountKey()) {
                    startFeed(seed)
                    return@launch
                }
                // A failed/unavailable Story endpoint falls back once, without resetting HOME.
                if (result.isFailure) {
                    if (source == StoryFeedSource.STORY) {
                        source = StoryFeedSource.HOME
                        emptyPages = 0
                        continue
                    }
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "推荐加载失败"
                    )
                    return@launch
                }
                if (source == StoryFeedSource.STORY) nextDisplayId += 1 else nextHomePage += 1
                val incoming = result.getOrThrow()
                    .filter { storyItemToRelatedVideo(it) != null && !wasWatched(it) }
                val previous = mutableState.value.items
                val merged = mergeStoryFeedItems(previous, incoming)
                if (merged.size > previous.size) {
                    mutableState.value = mutableState.value.copy(items = merged, isLoading = false, error = null)
                    return@launch
                }
                emptyPages += 1
                // Bound requests even when an endpoint repeats the same successful response.
                if (source == StoryFeedSource.STORY && (result.getOrThrow().isEmpty() || emptyPages >= MAX_EMPTY_PAGES)) {
                    source = StoryFeedSource.HOME
                    emptyPages = 0
                } else if (emptyPages >= MAX_EMPTY_PAGES) {
                    mutableState.value = mutableState.value.copy(isLoading = false, error = "暂时没有新的推荐视频，请重试")
                    return@launch
                }
            }
        }
    }

    fun updateCurrentVideo(bvid: String) {
        if (!active) return
        if (account != accountKey()) {
            startFeed(seed)
            return
        }
        val index = resolveStoryPortraitIndexForBvid(bvid, mutableState.value.items)
        val item = mutableState.value.items.getOrNull(index)
        furthestVisitedIndex = maxOf(furthestVisitedIndex, index)
        val keys = if (item != null) storyItemIdentityKeys(item) else {
            if (bvid.trim() != seed) return
            listOf("bvid:${seed}")
        }
        keys.forEach { key ->
            watchedKeys.remove(key)
            watchedKeys.add(key)
        }
        while (watchedKeys.size > StoryContinuationStore.MAX_WATCHED_KEYS) {
            watchedKeys.remove(watchedKeys.first())
        }
        mutableState.value = mutableState.value.copy(currentIndex = index.coerceAtLeast(0))
    }

    private fun wasWatched(item: StoryItem): Boolean =
        storyItemIdentityKeys(item).any { it in watchedKeys }

    private companion object {
        const val MAX_EMPTY_PAGES = 3
    }
}
