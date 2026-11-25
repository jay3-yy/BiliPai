package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.feature.video.policy.resolveFavoriteFolderMediaId
import com.android.purebilibili.feature.video.usecase.VideoInteractionUseCase
import com.android.purebilibili.feature.video.ui.components.FavoriteFolderSheet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.resolveVideoInfoLookupInput
import com.android.purebilibili.feature.video.ui.components.CoinDialog
import com.android.purebilibili.feature.video.ui.components.TripleSuccessAnimation
import com.android.purebilibili.feature.video.ui.components.PlayerNativeMenuTheme
import com.android.purebilibili.feature.video.usecase.queryVideoEngagementRelationship
import com.android.purebilibili.feature.video.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PortraitEngagementKey(val bvid: String, val aid: Long)
internal data class PortraitEngagementSnapshot(
    val subject: VideoSubjectSnapshot,
    val seed: VideoEngagementSeed,
    val info: ViewInfo?,
)

internal data class PortraitFavoriteFolders(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val folders: List<FavFolder> = emptyList(),
    val original: Set<Long> = emptySet(),
    val selected: Set<Long> = emptySet(),
)
internal fun interface PortraitEngagementLoader {
    suspend fun load(subject: VideoSubjectSnapshot, knownInfo: ViewInfo?, seed: VideoEngagementSeed, metadataKnown: Boolean): Result<PortraitEngagementSnapshot>
}

/** 按详情请求的 BV / AV 身份校验；保留页面标识供会话和事件路由使用。 */
internal fun resolvePortraitEngagementSubject(
    requested: VideoSubjectSnapshot,
    info: ViewInfo,
): VideoSubjectSnapshot? {
    val lookup = resolveVideoInfoLookupInput(requested.bvid, requested.aid) ?: return null
    val matchesLookup = if (lookup.bvid.isNotEmpty()) info.bvid == lookup.bvid
        else info.aid == lookup.aid
    if (!matchesLookup || info.aid <= 0L ||
        (requested.aid > 0L && info.aid != requested.aid)
    ) return null
    return requested.copy(
        aid = info.aid,
        ownerMid = info.owner.mid, title = info.title, coverUrl = info.pic,
        isRepost = info.isRepost,
        durationMs = info.pages.firstOrNull { it.cid == requested.cid }?.duration?.toLong()?.times(1000L)
            ?: requested.durationMs,
    )
}

private val DefaultPortraitEngagementLoader = PortraitEngagementLoader { requested, knownInfo, seed, metadataKnown ->
    val infoResult: Result<ViewInfo?> = when {
        knownInfo != null -> Result.success(knownInfo)
        metadataKnown -> Result.success(null)
        else -> VideoRepository.getVideoInfoOnly(bvid = requested.bvid, aid = requested.aid, requestedCid = requested.cid)
    }
    infoResult.fold(onSuccess = { info ->
        val resolvedSubject = info?.let { resolvePortraitEngagementSubject(requested, it) }
        if (info != null && resolvedSubject == null) {
            Result.failure(IllegalStateException("视频互动信息与当前视频不匹配"))
        } else {
            val subject = resolvedSubject ?: requested
            val metadataSeed = seed.copy(
                likeCount = if (knownInfo != null || info == null) seed.likeCount else info.stat.like,
                favoriteCount = if (knownInfo != null || info == null) seed.favoriteCount else info.stat.favorite,
                isRepost = info?.isRepost ?: seed.isRepost,
            )
            when {
                seed.relationshipStatusKnown -> Result.success(PortraitEngagementSnapshot(subject, metadataSeed, info))
                !seed.isLoggedIn -> Result.success(PortraitEngagementSnapshot(subject, metadataSeed.copy(
                    isLiked = false, likeStatusKnown = true, isDisliked = false,
                    isFollowing = false, followStatusKnown = true, isFavorited = false,
                    relationshipStatusKnown = true, coinCount = 0,
                ), info))
                else -> queryVideoEngagementRelationship(subject.aid, info?.bvid ?: subject.bvid).map { flags ->
                    PortraitEngagementSnapshot(subject, metadataSeed.copy(
                        isLiked = if (seed.likeStatusKnown) seed.isLiked else flags.like, likeStatusKnown = true,
                        isFollowing = if (seed.followStatusKnown) seed.isFollowing else flags.attention, followStatusKnown = true,
                        relationshipStatusKnown = true, isDisliked = flags.dislike,
                        isFavorited = flags.favorite, coinCount = flags.coin,
                        followingMids = if (flags.attention) seed.followingMids + subject.ownerMid
                            else seed.followingMids - subject.ownerMid,
                    ), info)
                }
            }
        }
    }, onFailure = { Result.failure(it) })
}

internal enum class PortraitEngagementAction { LIKE, FOLLOW, DISLIKE, FAVORITE, COIN, TRIPLE }

/** One account and one video (aid/bvid), deliberately not one part/cid or one pager position. */
internal class PortraitVideoEngagementSession(
    val model: VideoEngagementViewModel,
    private val subject: VideoSubjectSnapshot,
    initialInfo: ViewInfo?,
    initialSeed: VideoEngagementSeed,
    parentScope: CoroutineScope,
    private val loader: PortraitEngagementLoader = DefaultPortraitEngagementLoader,
    cachedSnapshot: VideoEngagementAuthoritativeSnapshot? = null,
    private val forceRelationshipRefresh: Boolean = false,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val requestMutex = Mutex()
    private var request: Deferred<Result<PortraitEngagementSnapshot>>? = null
    private var knownInfo = initialInfo
    private var metadataKnown = initialInfo != null || cachedSnapshot != null
    private var seed = (cachedSnapshot?.seed ?: initialSeed).let {
        if (forceRelationshipRefresh) it.copy(
            relationshipStatusKnown = false, likeStatusKnown = false, followStatusKnown = false,
        ) else it
    }
    private var closed = false
    private val folderActions = VideoInteractionUseCase()
    private val folderState = MutableStateFlow(PortraitFavoriteFolders())
    val favoriteFolders = folderState.asStateFlow()
    init {
        val bound = cachedSnapshot?.subject?.copy(
            cid = subject.cid, generation = subject.generation, isRepost = seed.isRepost,
        ) ?: subject
        model.bindSubject(bound, seed)
    }

    val isReady: Boolean get() = metadataKnown && model.uiState.value.relationshipStatusKnown
    val resolvedInfo: ViewInfo? get() = knownInfo

    fun updateKnownSeed(info: ViewInfo?, incoming: VideoEngagementSeed) {
        if (closed || isReady || info == null) return
        if (forceRelationshipRefresh) {
            knownInfo = info
            return
        }
        val hadMetadata = metadataKnown
        knownInfo = info
        metadataKnown = true
        val current = model.uiState.value
        seed = incoming.copy(
            isLiked = if (incoming.likeStatusKnown) incoming.isLiked else current.isLiked,
            likeStatusKnown = incoming.likeStatusKnown || current.likeStatusKnown,
            isFollowing = if (incoming.followStatusKnown) incoming.isFollowing else current.isFollowing,
            followStatusKnown = incoming.followStatusKnown || current.followStatusKnown,
            isFavorited = if (incoming.relationshipStatusKnown) incoming.isFavorited else incoming.isFavorited || current.isFavorited,
            coinCount = if (incoming.relationshipStatusKnown) incoming.coinCount else maxOf(incoming.coinCount, current.coinCount),
        )
        val bound = model.uiState.value.subject
        if (!hadMetadata || bound?.ownerMid != info.owner.mid) {
            model.bindSubject(subject.copy(
                ownerMid = info.owner.mid, title = info.title, coverUrl = info.pic,
                isRepost = info.isRepost, generation = subject.generation + 1L,
            ), seed)
        } else model.sync(seed)
    }

    private fun knownFor(action: PortraitEngagementAction): Boolean {
        if (!metadataKnown) return false
        val state = model.uiState.value
        return when (action) {
            PortraitEngagementAction.LIKE -> state.likeStatusKnown
            PortraitEngagementAction.FOLLOW -> state.followStatusKnown
            else -> state.relationshipStatusKnown && state.likeStatusKnown
        }
    }

    private suspend fun ensureLoaded(): Boolean {
        if (closed) return false
        val pending = requestMutex.withLock {
            if (isReady) return true
            request ?: scope.async(start = CoroutineStart.LAZY) {
                val loaded = try {
                    withTimeout(10_000L) {
                        loader.load(model.uiState.value.subject ?: subject, knownInfo, seed, metadataKnown)
                    }
                } catch (timeout: TimeoutCancellationException) {
                    Result.failure(IllegalStateException("加载互动状态超时，请重试", timeout))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
                currentCoroutineContext().ensureActive()
                requestMutex.withLock {
                    if (!closed && !isReady) loaded.onSuccess { snapshot ->
                        val current = model.uiState.value
                        val bound = current.subject
                        val confirmed = snapshot.seed.copy(
                            isLiked = if (current.likeStatusKnown) current.isLiked else snapshot.seed.isLiked,
                            likeCount = if (current.likeStatusKnown) current.likeCount else snapshot.seed.likeCount,
                            isDisliked = if (current.likeStatusKnown && current.isLiked) false else snapshot.seed.isDisliked,
                            isFollowing = if (current.followStatusKnown) current.isFollowing else snapshot.seed.isFollowing,
                            followingMids = if (current.followStatusKnown) current.followingMids else snapshot.seed.followingMids,
                        )
                        if (!metadataKnown || bound?.ownerMid != snapshot.subject.ownerMid ||
                            bound?.aid != snapshot.subject.aid
                        ) {
                            model.bindSubject(snapshot.subject.copy(generation = subject.generation + 1L), confirmed)
                        } else model.sync(confirmed)
                        knownInfo = snapshot.info
                        metadataKnown = true
                        seed = confirmed
                    }
                    request = null
                }
                if (!closed) loaded.onFailure { model.emitMessage(it.message ?: "加载互动状态失败") }
                loaded
            }.also { request = it; it.start() }
        }
        return pending.await().isSuccess && !closed
    }

    fun prepare() { scope.launch { ensureLoaded() } }
    private fun submit(action: PortraitEngagementAction, operation: () -> Unit) {
        scope.launch {
            if (!closed && (knownFor(action) || ensureLoaded())) {
                if (model.uiState.value.isLoggedIn) operation() else model.emitMessage("请先登录")
            }
        }
    }
    fun toggleLike() = submit(PortraitEngagementAction.LIKE) { model.toggleLike() }
    fun toggleFollow() = submit(PortraitEngagementAction.FOLLOW) { model.toggleFollow() }
    fun toggleDislike() = submit(PortraitEngagementAction.DISLIKE) { model.toggleDislike() }
    fun toggleFavorite() = submit(PortraitEngagementAction.FAVORITE) { model.toggleFavorite() }
    fun openCoinDialog() = submit(PortraitEngagementAction.COIN) { model.openCoinDialog() }
    fun confirmCoins(count: Int, alsoLike: Boolean) {
        if (!model.uiState.value.coinDialogVisible) return
        model.setCoinDialogVisible(false)
        submit(PortraitEngagementAction.COIN) { model.doCoin(count, alsoLike) }
    }
    fun tripleAction() = submit(PortraitEngagementAction.TRIPLE) { model.doTripleAction() }
    fun openFavoriteFolders() = submit(PortraitEngagementAction.FAVORITE) {
        folderState.update { it.copy(visible = true, loading = true) }
        scope.launch { loadFavoriteFolders() }
    }
    private suspend fun loadFavoriteFolders(keepSelection: Boolean = false) {
        val result = folderActions.getFavoriteFolders(subject.aid)
        currentCoroutineContext().ensureActive()
        result.onSuccess { folders ->
            val selected = folders.filter { it.fav_state == 1 }
                .map(::resolveFavoriteFolderMediaId).filter { it > 0L }.toSet()
            val validFolderIds = folders.map(::resolveFavoriteFolderMediaId).toSet()
            folderState.update { current ->
                current.copy(
                    folders = folders, original = selected,
                    selected = if (keepSelection) current.selected.intersect(validFolderIds) else selected,
                )
            }
            model.applyFavoriteFolderResult(subject.aid, selected.isNotEmpty())
        }.onFailure {
            if (it is CancellationException) throw it
            model.emitMessage(it.message ?: "加载收藏夹失败")
        }
        folderState.update { it.copy(loading = false) }
    }
    fun toggleFolder(folder: FavFolder) {
        val id = resolveFavoriteFolderMediaId(folder)
        if (id <= 0L) return
        folderState.update { current ->
            if (current.saving) current
            else current.copy(selected = if (id in current.selected) current.selected - id else current.selected + id)
        }
    }
    fun dismissFavoriteFolders() {
        folderState.update { current -> if (current.saving) current else current.copy(visible = false) }
    }
    fun hideDialogs() {
        model.setCoinDialogVisible(false)
        model.cancelTripleCelebration(model.uiState.value.tripleCelebrationId)
        folderState.update { it.copy(visible = false) }
    }
    fun saveFavoriteFolders() {
        var snapshot: PortraitFavoriteFolders? = null
        var shouldDismiss = false
        folderState.update { current ->
            if (current.saving || current.loading) current
            else {
                val mutation = resolveFavoriteFolderMutation(current.original, current.selected)
                if (mutation.addFolderIds.isEmpty() && mutation.removeFolderIds.isEmpty()) {
                    shouldDismiss = true
                    current.copy(visible = false)
                } else {
                    snapshot = current
                    current.copy(saving = true)
                }
            }
        }
        if (shouldDismiss) return
        val current = snapshot ?: return
        val mutation = resolveFavoriteFolderMutation(current.original, current.selected)
        scope.launch {
            try {
                val result = folderActions.updateFavoriteFolders(subject.aid, mutation.addFolderIds, mutation.removeFolderIds)
                currentCoroutineContext().ensureActive()
                result.onSuccess {
                    folderState.update { it.copy(visible = false, original = current.selected) }
                    model.applyFavoriteFolderResult(subject.aid, current.selected.isNotEmpty())
                    model.notifyFavoriteFoldersChanged()
                    model.emitMessage(if (current.selected.isEmpty()) "已取消收藏" else "收藏设置已保存")
                }.onFailure {
                    if (it is CancellationException) throw it
                    model.emitMessage(it.message ?: "收藏失败")
                }
            } finally {
                folderState.update { it.copy(saving = false) }
            }
        }
    }
    fun createFavoriteFolder(title: String, intro: String, isPrivate: Boolean) {
        scope.launch {
            val result = ActionRepository.createFavFolder(title, intro, isPrivate)
            currentCoroutineContext().ensureActive()
            result.onSuccess { loadFavoriteFolders(keepSelection = true) }
                .onFailure {
                    if (it is CancellationException) throw it
                    model.emitMessage(it.message ?: "创建收藏夹失败")
                }
        }
    }
    fun close() { closed = true; job.cancel() }
}

internal class PortraitVideoEngagementStore(
    parentScope: CoroutineScope,
    onEvent: (PortraitEngagementKey, VideoEngagementEvent) -> Unit,
    private val onState: (VideoEngagementUiState) -> Unit,
    private val loader: PortraitEngagementLoader = DefaultPortraitEngagementLoader,
    modelFactory: () -> VideoEngagementViewModel = { VideoEngagementViewModel() },
) {
    private data class Cached(
        val snapshot: VideoEngagementAuthoritativeSnapshot,
        val uncertain: Boolean,
    )
    private class Entry(
        val session: PortraitVideoEngagementSession,
        val models: ViewModelStore,
    ) {
        var disposed = false
    }
    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelFactory() as T
    }
    private val entries = linkedMapOf<PortraitEngagementKey, Entry>()
    private val snapshots = java.util.LinkedHashMap<PortraitEngagementKey, Cached>(16, 0.75f, true)
    private val accountSession = ActionRepository.interactionSession()
    private val ownerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + ownerJob)
    private var eventSink: ((PortraitEngagementKey, VideoEngagementEvent) -> Unit)? = onEvent
    private var retained: Set<PortraitEngagementKey>? = null
    private var activeToken: Any? = null
    private var activeKey: PortraitEngagementKey? = null
    private var navigate: ((String) -> Unit)? = null
    private var closed = false
    internal val residentControllerCount: Int get() = entries.size
    internal val snapshotCount: Int get() = snapshots.size

    private fun rememberSnapshot(key: PortraitEngagementKey, state: VideoEngagementUiState, uncertain: Boolean) {
        if (closed) return
        state.authoritativeSnapshot()?.let { snapshots[key] = Cached(it, uncertain) }
        if (snapshots.size > 256) snapshots.remove(snapshots.keys.first())
    }

    fun session(subject: VideoSubjectSnapshot, info: ViewInfo?, seed: VideoEngagementSeed): PortraitVideoEngagementSession {
        check(!closed)
        val key = PortraitEngagementKey(subject.bvid, subject.aid)
        val existing = entries[key]?.session
        if (existing != null) {
            existing.updateKnownSeed(info, seed)
            return existing
        }
        val cached = snapshots[key]
        val models = ViewModelStore()
        val model = ViewModelProvider(models, factory).get(VideoEngagementViewModel::class.java)
        val session = PortraitVideoEngagementSession(
            model, subject, info, seed, scope, loader,
            cachedSnapshot = cached?.snapshot, forceRelationshipRefresh = cached?.uncertain == true,
        )
        val entry = Entry(session, models)
        entries[key] = entry
        // These two observers live with the model, not the page. A confirmed coin request may
        // finish after route disposal; its last persistent result is handed back before clearing.
        model.viewModelScope.launch {
            model.uiState.collect { state ->
                if (!entry.disposed) {
                    rememberSnapshot(key, state, model.pendingMutations.value > 0)
                    onState(state)
                    if (closed && (state.coinDialogVisible || state.tripleCelebrationVisible)) session.hideDialogs()
                }
            }
        }
        model.viewModelScope.launch {
            var observedMutation = false
            model.pendingMutations.collect { pending ->
                if (pending > 0) observedMutation = true
                if (!entry.disposed && observedMutation) {
                    val state = model.uiState.value
                    rememberSnapshot(key, state, pending > 0)
                    if (pending == 0) onState(state)
                    pruneInactive()
                }
            }
        }
        scope.launch {
            model.events.collect { event ->
                if (event is VideoEngagementEvent.InvalidateFavoriteFolders) eventSink?.invoke(key, event)
                else if (activeKey == key) {
                    if (event is VideoEngagementEvent.LoadVideo) navigate?.invoke(event.bvid)
                    else eventSink?.invoke(key, event)
                }
            }
        }
        return session
    }

    fun retainVideos(keys: Set<PortraitEngagementKey>) {
        retained = keys.take(3).toSet()
        pruneInactive()
    }

    fun pruneInactive() {
        val candidates = entries.keys.toList()
        for (key in candidates) {
            val entry = entries[key] ?: continue
            val model = entry.session.model
            if (model.pendingMoneyMutations.value > 0 &&
                accountSession == ActionRepository.interactionSession()) continue
            val keep = !closed && (key == activeKey ||
                (retained?.contains(key) ?: (entries.size <= 3)))
            if (keep) continue
            val state = model.uiState.value
            rememberSnapshot(key, state, model.pendingMutations.value > 0)
            onState(state)
            entries.remove(key)
            entry.disposed = true
            entry.session.close()
            entry.models.clear()
        }
    }

    fun activate(token: Any, session: PortraitVideoEngagementSession, onNavigate: (String) -> Unit) {
        activeToken = token
        val subject = session.model.uiState.value.subject ?: return
        activeKey = PortraitEngagementKey(subject.bvid, subject.aid)
        navigate = onNavigate
        session.prepare()
    }
    fun deactivate(token: Any) {
        if (activeToken == token) { activeToken = null; activeKey = null; navigate = null }
    }
    fun close() {
        if (closed) return
        closed = true
        activeKey = null
        activeToken = null
        navigate = null
        eventSink = null
        entries.values.forEach { it.session.close() }
        ownerJob.cancel()
        snapshots.clear()
        pruneInactive()
    }
}

@Composable
internal fun PortraitVideoEngagementUi(
    session: PortraitVideoEngagementSession,
    visible: Boolean,
    onModalVisibilityChanged: (Boolean) -> Unit,
    favoriteFolderAnchor: com.android.purebilibili.feature.video.ui.components.FavoriteFolderMenuAnchor? = null,
) {
    val state by session.model.uiState.collectAsStateWithLifecycle()
    val folders by session.favoriteFolders.collectAsStateWithLifecycle()
    SideEffect { onModalVisibilityChanged(visible && (state.coinDialogVisible || folders.visible)) }
    LaunchedEffect(visible, state.coinDialogVisible, state.tripleCelebrationVisible, folders.visible) {
        if (!visible) {
            session.hideDialogs()
        }
    }
    if (visible) {
        PlayerNativeMenuTheme {
            CoinDialog(
                visible = state.coinDialogVisible,
                contentKey = state.subject to state.accountSession,
                currentCoinCount = state.coinCount,
                userBalance = state.userCoinBalance,
                maxCoins = state.coinLimit,
                onDismiss = { session.model.setCoinDialogVisible(false) },
                onConfirm = session::confirmCoins,
            )
            if (folders.visible) FavoriteFolderSheet(
                source = favoriteFolderAnchor?.source,
                folders = folders.folders, isLoading = folders.loading,
                selectedFolderIds = folders.selected, isSaving = folders.saving,
                onFolderToggle = session::toggleFolder,
                onSaveClick = session::saveFavoriteFolders,
                onDismissRequest = session::dismissFavoriteFolders,
                onCreateFolder = session::createFavoriteFolder,
            )
        }
        if (state.tripleCelebrationVisible) Box(Modifier.fillMaxSize()) {
            key(state.tripleCelebrationId) {
                TripleSuccessAnimation(
                    visible = true, isCompact = true, size = 180.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp),
                    onAnimationEnd = { session.model.completeTripleCelebration(state.tripleCelebrationId) },
                )
            }
        }
    }
}

