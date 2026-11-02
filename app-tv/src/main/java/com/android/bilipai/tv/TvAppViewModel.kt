package com.android.bilipai.tv

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.player.SharedPlaybackState
import com.android.purebilibili.core.player.resolvePlaybackResumePosition
import com.android.purebilibili.data.model.resolveVideoDisplayProgressState
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.HistoryCursor
import com.android.purebilibili.data.model.response.NavData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.CommentGrpcRepository
import com.android.purebilibili.data.repository.SubReplySortMode
import com.android.purebilibili.data.repository.isSortedSubReplyPageEnd
import com.android.purebilibili.data.repository.resolveCommentPageResolution
import com.android.purebilibili.data.repository.resolveSubReplyLoadedTotalCount
import com.android.purebilibili.data.repository.resolveSubReplyPageEnd
import com.android.purebilibili.data.repository.resolveSubReplyRemoteTotalCount
import com.android.purebilibili.data.repository.UserContentRepository
import com.android.purebilibili.data.repository.UserActionRepository
import com.android.purebilibili.data.repository.VideoCatalogRepository
import com.android.purebilibili.data.model.response.CreatorCardStats
import com.android.purebilibili.data.model.response.toVideoItem
import com.android.purebilibili.data.repository.ContentRequestException
import com.android.purebilibili.data.repository.asVideoItem
import com.android.purebilibili.core.player.RecentPlaybackStore
import com.android.purebilibili.core.ui.MaidAnimation
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.model.response.SpaceUserInfo
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.data.repository.HistoryRepository
import com.android.purebilibili.data.repository.QrLoginRepository
import com.android.purebilibili.data.repository.SearchRepository
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SessionRepository
import com.android.purebilibili.data.repository.SharedContentRepository
import com.android.purebilibili.data.repository.WatchLaterRepository
import com.android.bilipai.tv.ui.TvDanmakuSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class TvScreen { Home, Popular, Region, Following, Followings, Space, Search, History, Folders, Favorites, WatchLater, Settings, Login, Detail, Player, Comments }

@Serializable
data class TvRoute(
    val screen: TvScreen = TvScreen.Home, val bvid: String = "", val aid: Long = 0,
    val cid: Long = 0, val folderId: Long = 0, val label: String = "",
    val mid: Long = 0, val startPositionMs: Long? = null, val paused: Boolean = false, val speed: Float = 1f,
) {
    val key: String get() = "$screen:$bvid:$aid:$cid:$folderId:$mid"
}

data class TvCatalogState(
    val items: List<VideoItem> = emptyList(), val folders: List<FavFolder> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val page: Int = 0, val hasMore: Boolean = true,
    val focusedId: String? = null, val focusedIndex: Int = 0, val resetVersion: Long = 0, val firstVisibleIndex: Int = 0, val firstVisibleOffset: Int = 0,
    val historyCursor: HistoryCursor? = null,
    val offset: String = "", val users: List<FollowingUser> = emptyList(), val space: SpaceUserInfo? = null,
    val keyword: String = "", val viewed: Int = 0, val managing: Boolean = false,
    val searchOrder: SearchOrder = SearchOrder.TOTALRANK, val searchDuration: SearchDuration = SearchDuration.ALL,
    // 历史类型筛选（all/video/pgc/live/article，与移动端 HistoryContentFilter 同值）；收藏夹内排序（mtime/view/pubtime）
    val historyFilter: String = "all", val favoriteOrder: String = "mtime",
    // 热门子分类（popular/ranking/weekly/precious，与移动端 PopularSubCategory 同语义）；分区 tid
    val popularFilter: String = "popular", val regionTid: Int = 4,
)

enum class QrPhase { Loading, Waiting, Scanned, Expired, Success, Failed }
data class TvQrState(val phase: QrPhase = QrPhase.Loading, val bitmap: Bitmap? = null, val error: String? = null)

/** TV 评论阅读（只读第一阶段）：主评论分页、排序切换与楼中楼面板状态，按详情路由键缓存。 */
data class TvCommentsState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<ReplyItem> = emptyList(),
    val count: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val nextOffset: String = "",
    val sortMode: Int = CommentGrpcRepository.MODE_HOT,
    val focusedRpid: Long? = null,
    val sub: TvSubRepliesState? = null,
)

data class TvSubRepliesState(
    val root: ReplyItem,
    val items: List<ReplyItem> = emptyList(),
    val totalCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val isEnd: Boolean = false,
    val nextOffset: String? = null,
)

data class TvUiState(
    val route: TvRoute = TvRoute(), val rootScreen: TvScreen = TvScreen.Home, val catalog: TvCatalogState = TvCatalogState(),
    val detail: ViewInfo? = null, val detailLoading: Boolean = false, val detailError: String? = null,
    val detailResumePositionMs: Long = 0,
    val account: NavData? = null, val accountError: String? = null, val qr: TvQrState = TvQrState(),
    val query: String = "", val searchHistory: List<String> = emptyList(), val trending: List<String> = emptyList(),
    val suggest: List<String> = emptyList(),
    val quality: Int = 64, val autoContinue: Boolean = false, val privacyMode: Boolean = false,
    val danmakuEnabled: Boolean = true,
    val notice: String? = null,
    val continuing: List<VideoItem> = emptyList(), val reduceMotion: Boolean = false, val simpleEffects: Boolean = false,
    val dynamicColor: Boolean = false,
    val liked: Boolean? = null, val following: Boolean? = null, val actionBusy: Boolean = false,
    val favoriteFolders: List<FavFolder>? = null, val favoriteLoading: Boolean = false, val favoriteSaved: Boolean = false, val favoriteError: String? = null,
    val moveTargets: List<FavFolder>? = null, val moveCopy: Boolean = false, val moveAids: Set<Long> = emptySet(),
    val related: List<VideoItem> = emptyList(), val relatedLoading: Boolean = false, val creator: CreatorCardStats? = null,
    val brandFeedback: MaidAnimation? = null, val feedbackId: Int = 0, val resumeAction: String? = null,
    val update: TvUpdateState = TvUpdateState(),
    val danmakuSettings: TvDanmakuSettings = TvDanmakuSettings(),
    val gridDensity: Float = 1f,
    val comments: TvCommentsState = TvCommentsState(),
)

fun VideoItem.tvId(): String = bvid.takeIf { it.isNotBlank() } ?: "aid:${aid.takeIf { it > 0 } ?: id}"

class TvAppViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val preferences = TvPreferences(application)
    private val recent = RecentPlaybackStore(application)
    private val stack = runCatching {
        Json.decodeFromString<List<TvRoute>>(savedState.get<String>("routes") ?: "[]")
    }.getOrDefault(emptyList()).ifEmpty { listOf(TvRoute()) }.toMutableList()
    private val catalogs = mutableMapOf<String, TvCatalogState>()
    private val details = mutableMapOf<String, ViewInfo>()
    private val commentsStates = mutableMapOf<String, TvCommentsState>()
    private val mutableState = MutableStateFlow(TvUiState(route = stack.last(), rootScreen = stack.first().screen,
        query = savedState["query"] ?: "", searchHistory = preferences.searchHistory,
        quality = preferences.quality, autoContinue = preferences.autoContinue, privacyMode = preferences.privacyMode,
        danmakuEnabled = preferences.danmakuEnabled, reduceMotion = preferences.reduceMotion, simpleEffects = preferences.simpleEffects,
        dynamicColor = preferences.dynamicColor,
        danmakuSettings = TvDanmakuSettings(
            displayArea = preferences.danmakuArea, textSizeDp = preferences.danmakuTextSize,
            opacity = preferences.danmakuOpacity, speedScale = preferences.danmakuSpeed,
        ), gridDensity = preferences.gridDensity))
    val state = mutableState.asStateFlow()
    private var contentJob: Job? = null
    private var qrJob: Job? = null
    private var accountJob: Job? = null
    private var catalogOwner: Long? = TokenManager.midCache
    private var accountRevision = 0L
    private var started = false
    private var revision = 0L
    // 自动分页失败后阻断继续自动请求，避免滚动位置未变时形成重试风暴；刷新/重试后解除
    private var autoLoadBlocked = false
    private var continueJob: Job? = null
    private var actionJob: Job? = null
    private var commentsJob: Job? = null
    private var subRevision = 0L
    private var pendingAction: String? = savedState["pendingAction"]
    private var pendingOwner: Long? = savedState["pendingOwner"]


    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { TokenManager.awaitRestore() }
            refreshAccount().join()
            refreshContinueWatching()
            loadRoute()
            // 长按 OK 缩放列数是隐藏手势，首次使用给一次性提示。
            if (!preferences.gridZoomHintShown) {
                preferences.gridZoomHintShown = true
                feedback("长按 OK 键可调整网格列数")
            }
        }
    }

    fun navigate(route: TvRoute, root: Boolean = false) {
        if (route == mutableState.value.route) return
        contentJob?.cancel(); qrJob?.cancel(); commentsJob?.cancel(); revision++
        catalogs[mutableState.value.route.key] = mutableState.value.catalog.copy(loading = false)
        if (mutableState.value.route.screen == TvScreen.Comments) commentsStates[mutableState.value.route.key] = mutableState.value.comments
        if (root) { stack.clear(); stack.add(route) } else stack.add(route)
        savedState["routes"] = Json.encodeToString(stack.toList())
        mutableState.update { it.copy(route = route, rootScreen = stack.first().screen, catalog = catalogs[route.key] ?: TvCatalogState(),
            detail = details[route.key], detailLoading = false, detailError = null, detailResumePositionMs = 0, notice = null, favoriteFolders = null, favoriteLoading = false, favoriteError = null, moveTargets = null, moveAids = emptySet(), related = emptyList(), relatedLoading = false, creator = null, liked = null, following = null, resumeAction = null, brandFeedback = null,
            comments = commentsStates[route.key] ?: TvCommentsState()) }
        loadRoute()
    }

    /** Root sections return to recommendations; only Home lets the system leave. */
    fun back(): Boolean {
        if (mutableState.value.catalog.managing) { toggleManagement(); return true }
        if (mutableState.value.route.screen == TvScreen.Login) {
            pendingAction = null; pendingOwner = null; savedState["pendingAction"] = null; savedState["pendingOwner"] = null
        }
        if (stack.size <= 1) {
            if (stack.last().screen == TvScreen.Home) return false
            navigate(TvRoute(), root = true)
            return true
        }
        contentJob?.cancel(); qrJob?.cancel(); commentsJob?.cancel(); revision++
        catalogs[mutableState.value.route.key] = mutableState.value.catalog.copy(loading = false)
        if (mutableState.value.route.screen == TvScreen.Comments) commentsStates[mutableState.value.route.key] = mutableState.value.comments
        stack.removeAt(stack.lastIndex)
        val route = stack.last()
        savedState["routes"] = Json.encodeToString(stack.toList())
        mutableState.update { it.copy(route = route, rootScreen = stack.first().screen, catalog = catalogs[route.key] ?: TvCatalogState(),
            detail = details[route.key], detailLoading = false, detailError = null, detailResumePositionMs = 0, notice = null, favoriteFolders = null, favoriteLoading = false, favoriteError = null, moveTargets = null, moveAids = emptySet(), related = emptyList(), relatedLoading = false, creator = null, liked = null, following = null, resumeAction = null, brandFeedback = null,
            comments = commentsStates[route.key] ?: TvCommentsState()) }
        loadRoute()
        if (route.screen == TvScreen.Home) refreshContinueWatching()
        return true
    }

    fun openVideo(video: VideoItem) = navigate(TvRoute(TvScreen.Detail, video.bvid,
        video.aid.takeIf { it > 0 } ?: video.id, cid = video.cid))
    /** banner 主操作：推荐流带 cid 时直达播放器，否则走详情兜底。 */
    fun playItem(video: VideoItem) {
        val cid = video.cid.takeIf { it > 0 } ?: return openVideo(video)
        navigate(TvRoute(TvScreen.Player, video.bvid,
            video.aid.takeIf { it > 0 } ?: video.id, cid = cid, label = video.title))
    }
    fun play(cid: Long) {
        val info = mutableState.value.detail ?: return
        navigate(TvRoute(TvScreen.Player, info.bvid, info.aid, cid = cid, label = info.title))
    }

    /** 合集分集直达播放：分集未经详情页，直接携带 bvid/aid/cid 进播放器。 */
    fun playEpisode(bvid: String, aid: Long, cid: Long, label: String = "") {
        if (cid <= 0L || (bvid.isBlank() && aid <= 0L)) return
        navigate(TvRoute(TvScreen.Player, bvid, aid, cid = cid, label = label))
    }

    /** 详情页评论入口：按当前视频打开只读评论页。 */
    fun openComments() {
        val info = mutableState.value.detail ?: return
        navigate(TvRoute(TvScreen.Comments, bvid = info.bvid, aid = info.aid, label = info.title))
    }

    fun refreshComments() = loadCommentsList(reset = true)

    /** 排序切换（热门/最新）：重置分页与列表，保持路由键内的焦点记录失效。 */
    fun switchCommentSort(mode: Int) {
        val current = mutableState.value.comments
        if (current.sortMode == mode && current.items.isNotEmpty()) return
        mutableState.update { it.copy(comments = current.copy(
            sortMode = mode, items = emptyList(), page = 0, hasMore = true, nextOffset = "",
            loading = false, error = null, focusedRpid = null, sub = null)) }
        loadCommentsList(reset = true)
    }

    fun loadMoreComments() {
        val current = mutableState.value.comments
        if (current.loading || current.sub != null) return
        if (current.items.isEmpty() || !current.hasMore) return
        loadCommentsList(reset = false)
    }

    fun focusComment(rpid: Long) = mutableState.update { it.copy(comments = it.comments.copy(focusedRpid = rpid)) }

    private fun loadCommentsList(reset: Boolean) {
        val route = mutableState.value.route
        if (route.screen != TvScreen.Comments || route.aid <= 0) return
        contentJob?.cancel()
        val ticket = ++revision
        val previous = mutableState.value.comments
        val page = if (reset) 1 else previous.page + 1
        val sortMode = previous.sortMode
        mutableState.update { it.copy(comments = it.comments.copy(loading = true, error = null)) }
        contentJob = viewModelScope.launch {
            try {
                val data = CommentRepository.getCommentsForSubject(
                    oid = route.aid, type = 1, page = page, ps = 20, mode = sortMode,
                    paginationOffset = if (reset) null else previous.nextOffset.ifBlank { null },
                    fallbackOnMissingLocation = true,
                ).getOrThrow()
                if (ticket != revision) return@launch
                val topLevel = data.replies.orEmpty()
                // 首页合并置顶与热评（与手机评论面板同一合并顺序），后续页只追加普通评论。
                val previousItems = if (reset || page == 1) emptyList() else previous.items
                val merged = (previousItems + topLevel).distinctBy { it.rpid }
                val resolution = resolveCommentPageResolution(
                    data = data, pageToLoad = page,
                    previousRepliesSize = previousItems.size,
                    combinedRepliesSize = merged.size, newRepliesSize = topLevel.size,
                    fallbackCount = previous.count,
                )
                val nextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() } ?: ""
                val state = TvCommentsState(
                    loading = false, error = null, items = merged, count = resolution.totalCount,
                    page = page, hasMore = !resolution.isEnd, nextOffset = nextOffset,
                    sortMode = sortMode, focusedRpid = if (reset) null else previous.focusedRpid, sub = null,
                )
                commentsStates[route.key] = state
                mutableState.update { it.copy(comments = state) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket != revision) return@launch
                if (isAuthenticationFailure(error)) { requestLogin("comments"); return@launch }
                mutableState.update { it.copy(comments = it.comments.copy(loading = false, error = error.message ?: "评论加载失败")) }
            }
        }
    }

    fun openSubReplies(root: ReplyItem) {
        val route = mutableState.value.route
        if (route.screen != TvScreen.Comments) return
        subRevision++
        val ticket = subRevision
        mutableState.update { it.copy(comments = it.comments.copy(
            sub = TvSubRepliesState(root = root, loading = true, totalCount = root.rcount.takeIf { it > 0 } ?: root.count)) ) }
        commentsJob = viewModelScope.launch {
            try {
                val data = CommentRepository.getSortedSubCommentsForSubject(
                    oid = route.aid, type = 1, rootId = root.rpid, mode = SubReplySortMode.TIME.apiMode,
                ).getOrThrow()
                if (ticket != subRevision) return@launch
                val items = data.replies.orEmpty().distinctBy { it.rpid }
                val totalCount = resolveSubReplyLoadedTotalCount(
                    rootReply = root, loadedReplyCount = items.size,
                    remoteReplyCount = resolveSubReplyRemoteTotalCount(data, root),
                    previousTotalCount = root.rcount.takeIf { it > 0 } ?: root.count,
                )
                mutableState.update { it.copy(comments = it.comments.copy(sub = TvSubRepliesState(
                    root = root, items = items, totalCount = totalCount, loading = false,
                    isEnd = isSortedSubReplyPageEnd(data.cursor.isEnd, data.grpcNextOffset),
                    nextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() },
                ))) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket != subRevision) return@launch
                // 楼中楼遇到登录失效：收起面板回到评论列表，走统一扫码恢复，回来后可重开。
                if (isAuthenticationFailure(error)) { closeSubReplies(); requestLogin("comments"); return@launch }
                mutableState.update { it.copy(comments = it.comments.copy(sub = it.comments.sub?.copy(
                    loading = false, error = error.message ?: "回复加载失败"))) }
            }
        }
    }

    fun loadMoreSubReplies() {
        val sub = mutableState.value.comments.sub ?: return
        val route = mutableState.value.route
        if (sub.loading || sub.isEnd || route.screen != TvScreen.Comments) return
        subRevision++
        val ticket = subRevision
        val page = sub.page + 1
        mutableState.update { it.copy(comments = it.comments.copy(sub = sub.copy(loading = true, error = null))) }
        commentsJob = viewModelScope.launch {
            try {
                val data = CommentRepository.getSubCommentsForSubject(
                    oid = route.aid, type = 1, rootId = sub.root.rpid, page = page, ps = 20,
                    paginationOffset = sub.nextOffset,
                ).getOrThrow()
                if (ticket != subRevision) return@launch
                val fetched = data.replies.orEmpty()
                val items = (sub.items + fetched).distinctBy { it.rpid }
                val isEnd = resolveSubReplyPageEnd(
                    cursorIsEnd = data.cursor.isEnd, fetchedReplyCount = fetched.size,
                    loadedReplyCount = items.size, remoteReplyCount = sub.totalCount,
                    requestedPage = page, pageSize = 20, restPage = data.page,
                )
                mutableState.update { it.copy(comments = it.comments.copy(sub = sub.copy(
                    items = items, page = page, isEnd = isEnd, loading = false,
                    nextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() } ?: sub.nextOffset,
                ))) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket != subRevision) return@launch
                // 楼中楼分页遇到登录失效：收起面板走统一扫码恢复。
                if (isAuthenticationFailure(error)) { closeSubReplies(); requestLogin("comments"); return@launch }
                mutableState.update { it.copy(comments = it.comments.copy(sub = sub.copy(
                    loading = false, error = error.message ?: "回复加载失败"))) }
            }
        }
    }

    fun closeSubReplies() {
        subRevision++
        commentsJob?.cancel()
        mutableState.update { it.copy(comments = it.comments.copy(sub = null)) }
    }

    fun checkpointPlayback(snapshot: SharedPlaybackState) {
        val info = snapshot.info ?: return
        recent.save(TokenManager.midCache, info, snapshot.positionMs, snapshot.durationMs)
        preferences.rememberPart(info.bvid, info.aid, info.cid)
        mutableState.update { it.copy(continuing = recent.items(TokenManager.midCache)) }
        details.entries.forEach { entry ->
            if (entry.value.bvid == info.bvid) entry.setValue(entry.value.copy(cid = info.cid))
        }
        // Preserve the originating personal list and focus anchor while updating its watched position.
        catalogs.entries.forEach { entry ->
            if (entry.key.startsWith("History:") || entry.key.startsWith("WatchLater:")) entry.setValue(entry.value.copy(items = entry.value.items.map { item ->
                if (item.bvid == info.bvid) item.copy(
                    cid = info.cid,
                    progress = (snapshot.positionMs / 1000).toInt(),
                    duration = if (snapshot.durationMs > 0) (snapshot.durationMs / 1000).toInt() else item.duration,
                ) else item
            }))
        }
        viewModelScope.launch {
            if (!TokenManager.sessDataCache.isNullOrBlank()) HistoryRepository.reportPlayback(
                info.bvid, info.cid, snapshot.positionMs / 1000, snapshot.realPlayedMs / 1000,
                snapshot.startTsSec, info.aid)
        }
    }

    fun finishPlayback(snapshot: SharedPlaybackState) { checkpointPlayback(snapshot); back() }

    fun focusItem(id: String, routeKey: String = mutableState.value.route.key) = mutableState.update {
        if (it.route.key != routeKey) return@update it
        val index = if (id.startsWith("up:")) it.catalog.users.indexOfFirst { user -> "up:${user.mid}" == id }
            else if (id.startsWith("folder:")) it.catalog.folders.indexOfFirst { folder -> "folder:${folder.id}" == id }
            else it.catalog.items.indexOfFirst { item -> item.tvId() == id }
        it.copy(catalog = it.catalog.copy(focusedId = id, focusedIndex = index.coerceAtLeast(0)), resumeAction = null)
    }
    fun saveScroll(index: Int, offset: Int, routeKey: String = mutableState.value.route.key) = mutableState.update {
        if (it.route.key != routeKey) return@update it
        it.copy(catalog = it.catalog.copy(firstVisibleIndex = index, firstVisibleOffset = offset))
    }

    private fun loadRoute() {
        when (mutableState.value.route.screen) {
            TvScreen.Detail -> loadDetail()
            TvScreen.Comments -> if (mutableState.value.comments.items.isEmpty()) loadCommentsList(reset = true)
            TvScreen.Login -> {
                if (TokenManager.sessDataCache.isNullOrBlank()) refreshQr()
                else {
                    contentJob = viewModelScope.launch {
                        refreshAccount().join()
                        if (mutableState.value.route.screen == TvScreen.Login && mutableState.value.account == null) refreshQr()
                    }
                }
            }
            TvScreen.Settings, TvScreen.Player -> Unit
            TvScreen.Search -> {
                loadTrending()
                if (mutableState.value.query.isNotBlank() && mutableState.value.catalog.page == 0) loadCatalog()
            }
            else -> if (mutableState.value.catalog.page == 0) loadCatalog()
        }
    }

    fun refresh() {
        if (mutableState.value.route.screen == TvScreen.Detail) loadDetail(force = true)
        else if (mutableState.value.route.screen == TvScreen.Home) { refreshContinueWatching(); loadCatalog(reset = true) }
        else loadCatalog(reset = true)
    }

    fun search(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        preferences.recordSearch(clean); savedState["query"] = clean
        mutableState.update { it.copy(query = clean, searchHistory = preferences.searchHistory) }
        loadCatalog(reset = true)
    }

    fun loadMore() {
        if (autoLoadBlocked) return
        val catalog = mutableState.value.catalog
        if (!catalog.loading && catalog.hasMore) loadCatalog(reset = false)
    }

    private fun loadCatalog(reset: Boolean = false) {
        val route = mutableState.value.route
        if (route.screen !in setOf(TvScreen.Home, TvScreen.Following, TvScreen.Followings, TvScreen.Space, TvScreen.Search, TvScreen.History, TvScreen.Folders, TvScreen.Favorites, TvScreen.WatchLater)) return
        if (route.screen in setOf(TvScreen.Following, TvScreen.Followings, TvScreen.History, TvScreen.Folders, TvScreen.Favorites, TvScreen.WatchLater)
            && TokenManager.sessDataCache.isNullOrBlank()) {
            mutableState.update { it.copy(catalog = it.catalog.copy(error = "请先扫码登录", loading = false, hasMore = false)) }
            return
        }
        contentJob?.cancel()
        val ticket = ++revision
        val previous = mutableState.value.catalog
        val page = if (reset) 1 else previous.page + 1
        val query = mutableState.value.query
        if (reset) autoLoadBlocked = false
        mutableState.update { it.copy(catalog = it.catalog.copy(loading = true, error = null)) }
        contentJob = viewModelScope.launch {
            try {
                var hasMore = true
                var cursor = if (reset) null else previous.historyCursor
                var folders = emptyList<FavFolder>()
                var users = if (reset) emptyList() else previous.users
                var space = previous.space
                var offset = if (reset) "" else previous.offset
                val items = when (route.screen) {
                    TvScreen.Home -> SharedContentRepository.recommendations(page).getOrThrow()
                    TvScreen.Following -> UserContentRepository.followingVideos(offset).getOrThrow().let {
                        hasMore = it.second.has_more && it.second.offset.isNotBlank() && it.second.offset != offset
                        offset = it.second.offset; it.first
                    }
                    TvScreen.Followings -> UserContentRepository.followings(page).getOrThrow().let {
                        users = (users + it.list.orEmpty()).distinctBy { user -> user.mid }
                        hasMore = page * 50 < it.total; emptyList()
                    }
                    TvScreen.Space -> {
                        if (page == 1) space = UserContentRepository.space(route.mid).getOrThrow()
                        UserContentRepository.videos(route.mid, page).getOrThrow().let {
                            hasMore = it.page.pn * it.page.ps < it.page.count
                            it.list.vlist.map { video -> video.asVideoItem(route.mid) }
                        }
                    }
                    TvScreen.Popular -> when (previous.popularFilter) {
                        "ranking" -> VideoCatalogRepository.getRankingVideos().getOrThrow().also { hasMore = false }
                        "weekly" -> VideoCatalogRepository.getWeeklyMustWatchVideos().getOrThrow().also { hasMore = false }
                        "precious" -> VideoCatalogRepository.getPreciousVideos().getOrThrow().also { hasMore = false }
                        else -> VideoCatalogRepository.getPopularVideos(page).getOrThrow()
                            .also { hasMore = it.size >= 30 }
                    }
                    TvScreen.Region -> VideoCatalogRepository.getRegionVideos(tid = previous.regionTid, page = page).getOrThrow()
                        .also { hasMore = it.size >= 30 }
                    TvScreen.Search -> SearchRepository.search(query, order = previous.searchOrder,
                        duration = previous.searchDuration, page = page).getOrThrow().let {
                        hasMore = it.second.hasMore; it.first
                    }
                    TvScreen.History -> {
                        // 类型筛选与移动端 HistoryContentFilter 同值：video/live/article 传 type，
                        // 全部/番剧不传 type（番剧按 business 客户端过滤，与移动端一致）。
                        val filter = previous.historyFilter
                        val type = when (filter) { "video" -> "archive"; "live" -> "live"; "article" -> "article"; else -> null }
                        (if (previous.keyword.isNotBlank()) HistoryRepository.searchHistory(page, previous.keyword)
                         else HistoryRepository.getHistoryList(max = cursor?.max ?: 0,
                            viewAt = cursor?.view_at ?: 0, business = cursor?.business, type = type)).getOrThrow().let {
                            cursor = it.cursor; hasMore = it.list.isNotEmpty() && (previous.keyword.isNotBlank() || reset || cursor != previous.historyCursor)
                            it.list.filter { item ->
                                when (filter) {
                                    "video" -> item.history?.business == "archive"
                                    "pgc" -> item.history?.business == "pgc"
                                    "live" -> item.history?.business == "live"
                                    "article" -> item.history?.business == "article"
                                    else -> true
                                }
                            }.map { item -> item.toVideoItem() }.let { mergeLocalHistoryProgress(it) }
                        }
                    }
                    TvScreen.Folders -> {
                        val account = SessionRepository.account().getOrThrow() ?: throw ContentRequestException(-101, "")
                        folders = FavoriteRepository.getFavFolders(account.mid).getOrThrow(); hasMore = false; emptyList()
                    }
                    TvScreen.Favorites -> FavoriteRepository.getFavoriteList(mediaId = route.folderId, pn = page,
                        keyword = previous.keyword.takeIf { it.isNotBlank() }, order = previous.favoriteOrder).getOrThrow().let {
                        hasMore = it.has_more; it.medias.orEmpty().filter { item -> item.type == 2 }.map { item -> item.toVideoItem() }
                    }
                    TvScreen.WatchLater -> WatchLaterRepository.getPage(page, viewed = previous.viewed, keyword = previous.keyword, ascending = false).getOrThrow().let {
                        hasMore = it.hasMore; it.items
                    }
                    else -> emptyList()
                }
                if (ticket != revision) return@launch
                val validItems = items.filter { it.bvid.isNotBlank() || it.aid > 0 }
                mutableState.update { current -> current.copy(catalog = current.catalog.copy(
                    items = ((if (reset) emptyList() else previous.items) + validItems).distinctBy { it.tvId() },
                    folders = folders, users = users, space = space, offset = offset, loading = false, error = null, page = page,
                    hasMore = hasMore, historyCursor = cursor,
                    focusedId = if (reset) null else current.catalog.focusedId,
                    focusedIndex = if (reset) 0 else current.catalog.focusedIndex,
                    resetVersion = current.catalog.resetVersion + if (reset) 1 else 0,
                    firstVisibleIndex = if (reset) 0 else current.catalog.firstVisibleIndex,
                    firstVisibleOffset = if (reset) 0 else current.catalog.firstVisibleOffset,
                )) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket == revision) {
                    if (!reset) autoLoadBlocked = true
                    if (isAuthenticationFailure(error)) { requestLogin("catalog"); return@launch }
                    mutableState.update { it.copy(catalog = it.catalog.copy(loading = false, error = error.message ?: "加载失败")) }
                }
            }
        }
    }

    private fun loadDetail(force: Boolean = false) {
        val route = mutableState.value.route
        contentJob?.cancel()
        val ticket = ++revision
        val cached = details[route.key].takeUnless { force }
        if (cached != null) {
            contentJob = viewModelScope.launch {
                loadDetailActions(cached, ticket)
                val resumePosition = readDetailResumePosition(cached)
                if (ticket == revision) mutableState.update { it.copy(detailResumePositionMs = resumePosition) }
            }
            return
        }
        mutableState.update { it.copy(detailLoading = true, detailError = null) }
        contentJob = viewModelScope.launch {
            val requestedCid = recent.items(TokenManager.midCache).firstOrNull { it.bvid == route.bvid || it.aid == route.aid && route.aid > 0 }?.cid ?: route.cid
            val result = SharedContentRepository.detail(route.bvid, route.aid, requestedCid)
            val resumePosition = result.getOrNull()?.let { readDetailResumePosition(it) } ?: 0
            if (ticket != revision) return@launch
            result.fold(onSuccess = { info ->
                details[route.key] = info
                loadDetailActions(info, ticket)
                loadDetailExtras(info, ticket)
                mutableState.update { it.copy(detail = info, detailLoading = false, detailResumePositionMs = resumePosition) }
            }, onFailure = { error ->
                if (isAuthenticationFailure(error)) {
                    mutableState.update { it.copy(detailLoading = false) }
                    requestLogin("detail"); return@fold
                }
                mutableState.update { it.copy(detailLoading = false, detailError = error.message ?: "详情加载失败") }
            })
        }
    }

    /** 详情页扩展内容：相关推荐与 UP 主卡片（粉丝/投稿数），与详情并行加载、失败静默。 */
    private var detailExtrasJob: Job? = null
    private fun loadDetailExtras(info: ViewInfo, ticket: Long) {
        detailExtrasJob?.cancel()
        mutableState.update { it.copy(related = emptyList(), relatedLoading = true, creator = null) }
        detailExtrasJob = viewModelScope.launch {
            val related = withContext(Dispatchers.IO) {
                VideoCatalogRepository.getRelatedVideos(info.bvid).map { it.toVideoItem() }
            }
            if (ticket == revision) mutableState.update { it.copy(related = related, relatedLoading = false) }
        }
        viewModelScope.launch {
            val stats = VideoCatalogRepository.getCreatorCardStats(info.owner.mid).getOrNull()
            if (ticket == revision && stats != null) mutableState.update { it.copy(creator = stats) }
        }
    }

    // The same CID progress and completion policy used by SharedPlaybackSession.
    // Server progress is only known after streams load; unknown progress keeps the label as “播放”.
    private suspend fun readDetailResumePosition(info: ViewInfo): Long = withContext(Dispatchers.IO) {
        resolvePlaybackResumePosition(
            explicitMs = null,
            localMs = recent.items(TokenManager.midCache).firstOrNull { it.bvid == info.bvid && it.cid == info.cid }?.progress?.times(1000L) ?: 0,
            serverMs = null,
            durationMs = (info.pages.firstOrNull { it.cid == info.cid }?.duration ?: 0) * 1_000L,
        )
    }

    /**
     * 历史列表加载时合并本地持久化进度：与手机端 enrichHistoryProgress 消费同一共享规则
     * （resolveVideoDisplayProgressState，viewAt=1 表示条目必有观看记录）。
     * 仅合并 bvid + cid 一致的本地检查点，避免多 P 串位；source 为 RecentPlaybackStore，
     * 读取涉及 SharedPreferences，放到 IO 线程。
     */
    private suspend fun mergeLocalHistoryProgress(items: List<VideoItem>): List<VideoItem> {
        if (items.isEmpty()) return items
        val localItems = withContext(Dispatchers.IO) { recent.items(TokenManager.midCache) }
        if (localItems.isEmpty()) return items
        return items.map { video ->
            if (video.bvid.isBlank()) return@map video
            val local = localItems.firstOrNull {
                it.bvid == video.bvid && (video.cid <= 0L || it.cid == video.cid)
            } ?: return@map video
            val merged = resolveVideoDisplayProgressState(
                serverProgressSec = video.progress,
                durationSec = video.duration,
                localPositionMs = local.progress.takeIf { it > 0 }?.times(1000L) ?: 0L,
                viewAt = 1L,
            ).progressSec
            if (merged == video.progress) video else video.copy(progress = merged)
        }
    }

    private fun loadTrending() = viewModelScope.launch {
        val result = SearchRepository.getTrendingKeywords(12)
        result.getOrNull()?.let { bundle -> mutableState.update { it.copy(trending = bundle.allItems.map { item -> item.keyword }) } }
    }

    fun refreshAccount(): Job {
        accountJob?.cancel()
        val ticket = ++accountRevision
        return viewModelScope.launch {
            withContext(Dispatchers.IO) { TokenManager.awaitRestore() }
            val result = SessionRepository.account()
            if (ticket != accountRevision) return@launch
            result.fold(onSuccess = { account ->
                val previousMid = catalogOwner
                if (account != null || pendingAction == null) {
                    if (previousMid != account?.mid) { catalogs.clear(); revision++; contentJob?.cancel() }
                    catalogOwner = account?.mid
                }
                mutableState.update { it.copy(account = account, accountError = null, continuing = recent.items(account?.mid)) }
            }, onFailure = { error -> mutableState.update { it.copy(accountError = error.message) } })
        }.also { accountJob = it }
    }

    fun refreshQr() {
        qrJob?.cancel()
        mutableState.update { it.copy(qr = TvQrState()) }
        qrJob = viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { QrLoginRepository.generate() }
                check(response.code == 0) { response.message.ifBlank { "二维码申请失败" } }
                val data = response.data ?: error("二维码数据为空")
                val code = data.authCode?.takeIf { it.isNotBlank() } ?: error("二维码凭据为空")
                val bitmap = withContext(Dispatchers.Default) {
                    val matrix = QRCodeWriter().encode(data.url ?: error("二维码地址为空"), BarcodeFormat.QR_CODE, 360, 360)
                    val pixels = IntArray(360 * 360) { index -> if (matrix[index % 360, index / 360]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                    Bitmap.createBitmap(pixels, 360, 360, Bitmap.Config.ARGB_8888)
                }
                mutableState.update { it.copy(qr = TvQrState(QrPhase.Waiting, bitmap)) }
                val expiresAt = android.os.SystemClock.elapsedRealtime() + 180_000L
                while (android.os.SystemClock.elapsedRealtime() < expiresAt) {
                    delay(2_000)
                    val poll = withContext(Dispatchers.IO) { QrLoginRepository.poll(code) }
                    when (poll.code) {
                        0 -> {
                            SessionRepository.completeQrLogin(getApplication(), poll)
                            mutableState.update { it.copy(qr = TvQrState(QrPhase.Success)) }
                            refreshAccount().join()
                            val action = pendingAction
                            val sameAccount = pendingOwner == null || pendingOwner == mutableState.value.account?.mid
                            pendingAction = null; pendingOwner = null; autoLoadBlocked = false
                            savedState["pendingAction"] = null; savedState["pendingOwner"] = null
                            if (action != null && mutableState.value.route.screen == TvScreen.Login) {
                                back()
                                if (!sameAccount && mutableState.value.route.screen == TvScreen.Player) back()
                                if (action == "catalog" && mutableState.value.catalog.page > 0) loadCatalog()
                                // 登录成功回到原页面后立即重载触发登录时失败的内容，不要求用户再按一次重试。
                                if (action == "comments") refreshComments()
                                if (action == "detail") loadDetail(force = true)
                                mutableState.update { it.copy(resumeAction = action.takeIf { sameAccount },
                                    notice = if (sameAccount && action !in setOf("catalog", "player", "comments", "detail")) "登录成功，请再次确认操作" else if (!sameAccount) "已切换账号，请重新选择操作" else null) }
                            }
                            refreshContinueWatching()
                            return@launch
                        }
                        86039 -> Unit
                        86090 -> mutableState.update { it.copy(qr = it.qr.copy(phase = QrPhase.Scanned)) }
                        86038 -> break
                        else -> error(poll.message.ifBlank { "扫码登录失败（${poll.code}）" })
                    }
                }
                mutableState.update { it.copy(qr = TvQrState(QrPhase.Expired)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) { mutableState.update { it.copy(qr = TvQrState(QrPhase.Failed, error = error.message ?: "登录失败")) } }
        }
    }

    fun signOut() = viewModelScope.launch {
        accountJob?.cancel(); accountRevision++
        qrJob?.cancel()
        SessionRepository.signOut(getApplication()); catalogOwner = null; catalogs.clear(); details.clear(); continueJob?.cancel(); actionJob?.cancel(); pendingAction = null; savedState["pendingAction"] = null; savedState["pendingOwner"] = null
        mutableState.update { it.copy(account = null, accountError = null, qr = TvQrState(), continuing = recent.items(null), favoriteFolders = null, actionBusy = false, liked = null, following = null, brandFeedback = null) }
        navigate(TvRoute(), root = true)
        if (mutableState.value.route.screen == TvScreen.Home) loadCatalog(reset = true)
    }

    fun requestLogin(action: String = "catalog", snapshot: SharedPlaybackState? = null) {
        if (mutableState.value.route.screen == TvScreen.Login) return
        pendingAction = action; pendingOwner = TokenManager.midCache
        savedState["pendingAction"] = action; savedState["pendingOwner"] = pendingOwner
        snapshot?.info?.let { info ->
            checkpointPlayback(snapshot)
            stack[stack.lastIndex] = mutableState.value.route.copy(cid = info.cid,
                startPositionMs = snapshot.positionMs, paused = !snapshot.playing, speed = snapshot.speed)
        }
        navigate(TvRoute(TvScreen.Login))
        // Expired cookies must not make the login page render an obsolete account card.
        mutableState.update { it.copy(account = null) }
        refreshQr()
    }
    private fun isAuthenticationFailure(error: Throwable): Boolean =
        (error as? ContentRequestException)?.code == -101 ||
            (error as? com.android.purebilibili.data.repository.FavoriteRequestException)?.apiCode == -101

    private suspend fun loadDetailActions(info: ViewInfo, ticket: Long) {
        if (TokenManager.sessDataCache.isNullOrBlank()) return
        val liked = UserActionRepository.liked(info.aid).getOrNull()
        if (ticket == revision) mutableState.update { it.copy(liked = liked) }
    }
    fun refreshContinueWatching() {
        continueJob?.cancel()
        val mid = TokenManager.midCache
        mutableState.update { it.copy(continuing = recent.items(mid)) }
        if (mid == null) return
        continueJob = viewModelScope.launch {
            val history = HistoryRepository.getHistoryList(type = "archive").getOrNull()?.list.orEmpty()
                .filter { it.history?.business == "archive" }.map { it.toVideoItem() }
            if (mid != TokenManager.midCache) return@launch
            recent.mergeHistory(mid, history)
            mutableState.update { it.copy(continuing = recent.items(mid)) }
        }
    }
    private fun action(identity: String, operation: suspend () -> Result<*>, success: () -> Unit) {
        if (mutableState.value.actionBusy) return
        if (TokenManager.sessDataCache.isNullOrBlank()) { requestLogin(identity); return }
        val mid = TokenManager.midCache
        val routeKey = mutableState.value.route.key
        mutableState.update { it.copy(actionBusy = true, notice = null) }
        actionJob = viewModelScope.launch {
            try {
                val result = operation()
                if (mid != TokenManager.midCache) return@launch
                result.fold(onSuccess = {
                    catalogs.entries.removeAll { entry -> !entry.key.startsWith("Home:") && !entry.key.startsWith("Search:") }
                    if (mutableState.value.route.key == routeKey) success()
                }, onFailure = { error ->
                    if (mutableState.value.route.key != routeKey) return@fold
                    if (isAuthenticationFailure(error)) requestLogin(identity)
                    else mutableState.update { it.copy(notice = error.message ?: "操作失败") }
                })
            } finally { mutableState.update { it.copy(actionBusy = false) } }
        }
    }
    private fun feedback(message: String, animation: MaidAnimation? = null) = mutableState.update {
        it.copy(notice = message, brandFeedback = animation, feedbackId = it.feedbackId + 1)
    }
    fun consumeRestoredAction() = mutableState.update { it.copy(resumeAction = null) }
    fun clearNotice(message: String) = mutableState.update { if (it.notice == message) it.copy(notice = null) else it }
    fun consumeFeedback(id: Int) = mutableState.update { if (it.feedbackId == id) it.copy(brandFeedback = null) else it }
    fun addWatchLater() {
        val info = mutableState.value.detail ?: return
        action("later", { WatchLaterRepository.add(info.aid) }) {
            catalogs.entries.removeAll { it.key.startsWith("WatchLater:") }; feedback("已加入稍后再看")
        }
    }
    fun toggleLike() {
        val info = mutableState.value.detail ?: return
        val known = mutableState.value.liked
        if (known == null && !TokenManager.sessDataCache.isNullOrBlank()) {
            action("like", { UserActionRepository.liked(info.aid).onSuccess { liked -> mutableState.update { it.copy(liked = liked) } } }) {
                feedback("点赞状态已刷新，请再次确认")
            }
            return
        }
        val next = !(known ?: false)
        action("like", { UserActionRepository.like(info.aid, next) }) {
            mutableState.update { it.copy(liked = next) }; feedback(if (next) "已点赞" else "已取消点赞", MaidAnimation.LIKE_SUCCESS.takeIf { next })
        }
    }
    fun openSpace(mid: Long, name: String = "") = navigate(TvRoute(TvScreen.Space, mid = mid, label = name))
    fun toggleFollow() {
        val profile = mutableState.value.catalog.space ?: return
        val next = !profile.isFollowed
        action("follow", { UserActionRepository.follow(profile.mid, next) }) {
            catalogs.entries.removeAll { it.key.startsWith("Following:") || it.key.startsWith("Followings:") }
            mutableState.update { it.copy(catalog = it.catalog.copy(space = profile.copy(isFollowed = next))) }
            feedback(if (next) "已关注" else "已取关", if (next) MaidAnimation.FOLLOW_SUCCESS else MaidAnimation.UNFOLLOW_COMPLETE)
        }
    }
    fun chooseFavorites() {
        if (mutableState.value.favoriteLoading || mutableState.value.actionBusy) return
        val info = mutableState.value.detail ?: return
        if (TokenManager.sessDataCache.isNullOrBlank()) { requestLogin("favorite"); return }
        val mid = TokenManager.midCache
        val routeKey = mutableState.value.route.key
        mutableState.update { it.copy(favoriteLoading = true, favoriteError = null) }
        viewModelScope.launch {
            val result = UserActionRepository.folders(info.aid)
            if (mid != TokenManager.midCache || mutableState.value.route.key != routeKey) return@launch
            result.fold(onSuccess = { folders -> mutableState.update { it.copy(favoriteLoading = false, favoriteFolders = folders) } },
                onFailure = { error ->
                    mutableState.update { it.copy(favoriteLoading = false, favoriteError = error.message, notice = error.message) }
                    if (isAuthenticationFailure(error)) requestLogin("favorite")
                })
        }
    }
    fun dismissFavorites() = mutableState.update { it.copy(favoriteFolders = null, favoriteSaved = false) }
    fun saveFavorites(selected: Set<Long>) {
        val info = mutableState.value.detail ?: return
        val before = mutableState.value.favoriteFolders.orEmpty().filter { it.fav_state == 1 }.map { it.id }.toSet()
        val add = selected - before; val remove = before - selected
        if (add.isEmpty() && remove.isEmpty()) { mutableState.update { it.copy(favoriteSaved = true) }; return }
        action("favorite", { UserActionRepository.favorite(info.aid, add, remove) }) {
            catalogs.entries.removeAll { it.key.startsWith("Favorites:") || it.key.startsWith("Folders:") }
            mutableState.update { it.copy(favoriteSaved = true) }; feedback("收藏夹已更新", MaidAnimation.FAVORITE_SAVED.takeIf { add.isNotEmpty() })
        }
    }
    fun toggleManagement() = mutableState.update { it.copy(catalog = it.catalog.copy(managing = !it.catalog.managing)) }
    fun filterList(keyword: String = mutableState.value.catalog.keyword, viewed: Int = mutableState.value.catalog.viewed) {
        mutableState.update { it.copy(catalog = it.catalog.copy(keyword = keyword.trim(), viewed = viewed)) }; loadCatalog(reset = true)
    }
    fun removeItem(video: VideoItem) {
        val route = mutableState.value.route
        action("manage", { when (route.screen) {
            TvScreen.History -> HistoryRepository.deleteHistoryItem("archive_${video.aid.takeIf { it > 0 } ?: video.id}", TokenManager.csrfCache.orEmpty())
            TvScreen.WatchLater -> WatchLaterRepository.remove(video.aid.takeIf { it > 0 } ?: video.id)
            TvScreen.Favorites -> FavoriteRepository.removeResource(route.folderId, video.aid.takeIf { it > 0 } ?: video.id)
            else -> Result.failure<Unit>(IllegalStateException("当前列表不可移除"))
        } }) {
            if (route.screen == TvScreen.History) recent.remove(TokenManager.midCache, video.bvid)
            removeFromCatalogAndFocus(video)
            feedback("已移除")
        }
    }

    /** 移除或移动成功后的本地收敛：从当前目录移除条目，焦点按相邻条目规则恢复。 */
    private fun removeFromCatalogAndFocus(video: VideoItem) {
        val catalog = mutableState.value.catalog
        val index = catalog.items.indexOfFirst { it.tvId() == video.tvId() }.coerceAtLeast(0)
        val remaining = catalog.items.filterNot { it.tvId() == video.tvId() }
        val next = index.coerceAtMost((remaining.size - 1).coerceAtLeast(0))
        mutableState.update { it.copy(catalog = catalog.copy(items = remaining, focusedId = remaining.getOrNull(next)?.tvId(),
            focusedIndex = next, resetVersion = catalog.resetVersion + 1), continuing = recent.items(TokenManager.midCache)) }
    }

    /** 稍后再看移入/复制到收藏夹：先选目标收藏夹（与手机端同一仓库与语义）。 */
    fun requestMoveToFavorite(video: VideoItem, copy: Boolean) {
        val aid = video.aid.takeIf { it > 0 } ?: video.id
        if (aid <= 0) { feedback("该内容不支持移入收藏夹"); return }
        if (TokenManager.sessDataCache.isNullOrBlank()) { requestLogin("manage"); return }
        val mid = TokenManager.midCache
        if (mid == null) { feedback("请先登录"); return }
        mutableState.update { it.copy(moveCopy = copy, moveAids = setOf(aid), moveTargets = null, favoriteLoading = true, notice = null) }
        viewModelScope.launch {
            val result = FavoriteRepository.getFavFolders(mid)
            if (mid != TokenManager.midCache) return@launch
            result.fold(onSuccess = { folders ->
                if (folders.isEmpty()) {
                    mutableState.update { it.copy(favoriteLoading = false, moveAids = emptySet(), notice = "还没有可用的收藏夹") }
                } else {
                    mutableState.update { it.copy(favoriteLoading = false, moveTargets = folders) }
                }
            }, onFailure = { error ->
                mutableState.update { it.copy(favoriteLoading = false, moveTargets = null, moveAids = emptySet(), notice = error.message ?: "加载收藏夹失败") }
                if (isAuthenticationFailure(error)) requestLogin("manage")
            })
        }
    }

    fun dismissMoveTargets() = mutableState.update { it.copy(moveTargets = null, moveAids = emptySet(), favoriteLoading = false) }

    fun chooseMoveTarget(mediaId: Long) {
        val pending = mutableState.value
        val aids = pending.moveAids
        val copy = pending.moveCopy
        if (aids.isEmpty()) return
        dismissMoveTargets()
        action("manage", { WatchLaterRepository.copyOrMoveToFavorite(mediaId, aids, copy) }) {
            catalogs.entries.removeAll { it.key.startsWith("WatchLater:") }
            if (copy) {
                feedback("已复制到收藏夹")
            } else {
                // 移动语义：源条目不再保留在稍后再看，本地按移除规则收敛并恢复相邻焦点。
                mutableState.value.catalog.items.firstOrNull { (it.aid.takeIf { a -> a > 0 } ?: it.id) in aids }
                    ?.let { removeFromCatalogAndFocus(it) }
                feedback("已移动到收藏夹")
            }
        }
    }
    fun clearHistory() = action("manage", { HistoryRepository.clearHistory(TokenManager.csrfCache.orEmpty()) }) {
        recent.clear(TokenManager.midCache)
        mutableState.update { it.copy(catalog = it.catalog.copy(items = emptyList(), page = 1, hasMore = false), continuing = emptyList()) }
        feedback("观看历史已清空")
    }
    fun toggleReduceMotion() { preferences.reduceMotion = !preferences.reduceMotion; mutableState.update { it.copy(reduceMotion = preferences.reduceMotion) } }
    fun toggleSimpleEffects() { preferences.simpleEffects = !preferences.simpleEffects; mutableState.update { it.copy(simpleEffects = preferences.simpleEffects) } }

    /** 动态取色：跟随系统壁纸与当前封面提取强调色；关闭回品牌粉。 */
    fun toggleDynamicColor() {
        preferences.dynamicColor = !preferences.dynamicColor
        mutableState.update { it.copy(dynamicColor = preferences.dynamicColor) }
    }
    fun checkUpdate() {
        if (mutableState.value.update.loading) return
        mutableState.update { it.copy(update = it.update.copy(loading = true, message = null)) }
        viewModelScope.launch { val result = TvUpdateRepository.check(getApplication()); mutableState.update { it.copy(update = result) } }
    }

    fun updateQuality(value: Int) { preferences.quality = value; mutableState.update { it.copy(quality = value) } }
    fun toggleAutoContinue() { preferences.autoContinue = !preferences.autoContinue; mutableState.update { it.copy(autoContinue = preferences.autoContinue) } }
    fun toggleDanmaku() { preferences.danmakuEnabled = !preferences.danmakuEnabled; mutableState.update { it.copy(danmakuEnabled = preferences.danmakuEnabled) } }
    fun togglePrivacy() { preferences.privacyMode = !preferences.privacyMode; mutableState.update { it.copy(privacyMode = preferences.privacyMode) } }

    /** 弹幕档位更新：落偏好并热更新，播放中的覆盖层经 LaunchedEffect(settings) 立即生效。 */
    fun updateDanmakuSettings(settings: TvDanmakuSettings) {
        preferences.danmakuArea = settings.displayArea
        preferences.danmakuTextSize = settings.textSizeDp
        preferences.danmakuOpacity = settings.opacity
        preferences.danmakuSpeed = settings.speedScale
        mutableState.update { it.copy(danmakuSettings = settings) }
    }

    /** 详情页投币：走既有 action() 体系（busy/登录失效/路由校验），成功给品牌反馈。 */
    fun coin(count: Int, alsoLike: Boolean) {
        val info = mutableState.value.detail ?: return
        action("coin", { UserActionRepository.coin(info.aid, count, alsoLike) }) {
            feedback("投币成功", MaidAnimation.COIN_SUCCESS)
        }
    }

    /** 搜索筛选：仅搜索页生效，变更后重置分页重新搜索。 */
    fun updateSearchFilter(order: SearchOrder? = null, duration: SearchDuration? = null) {
        if (mutableState.value.route.screen != TvScreen.Search) return
        mutableState.update {
            val catalog = it.catalog
            it.copy(catalog = catalog.copy(
                searchOrder = order ?: catalog.searchOrder,
                searchDuration = duration ?: catalog.searchDuration,
            ))
        }
        loadCatalog(reset = true)
    }

    /** 搜索联想：防抖由调用方负责，这里取消上一请求避免乱序回写。 */
    private var suggestJob: Job? = null
    fun querySuggest(keyword: String) {
        suggestJob?.cancel()
        val clean = keyword.trim()
        if (clean.isBlank()) { mutableState.update { it.copy(suggest = emptyList()) }; return }
        suggestJob = viewModelScope.launch {
            val result = SearchRepository.getSuggest(clean).getOrNull().orEmpty()
                .map { tag -> tag.value.ifBlank { tag.term }.ifBlank { tag.name } }
                .filter { it.isNotBlank() }.distinct().take(8)
            mutableState.update { it.copy(suggest = result) }
        }
    }

    /** 历史类型筛选（与移动端 HistoryContentFilter 同值），变更后重置分页。 */
    fun updateHistoryFilter(filter: String) {
        if (mutableState.value.route.screen != TvScreen.History) return
        mutableState.update { it.copy(catalog = it.catalog.copy(historyFilter = filter)) }
        loadCatalog(reset = true)
    }

    /** 热门子分类（综合热门/排行榜/每周必看/入站必刷）：与移动端 PopularSubCategory 同语义。 */
    fun updatePopularFilter(filter: String) {
        if (mutableState.value.route.screen != TvScreen.Popular) return
        if (mutableState.value.catalog.popularFilter == filter) return
        mutableState.update { it.copy(catalog = it.catalog.copy(popularFilter = filter, items = emptyList())) }
        loadCatalog(reset = true)
    }

    /** 分区切换（游戏/知识/科技…）：tid 存目录状态，切区重置分页。 */
    fun updateRegionTid(tid: Int) {
        if (mutableState.value.route.screen != TvScreen.Region) return
        if (mutableState.value.catalog.regionTid == tid) return
        mutableState.update { it.copy(catalog = it.catalog.copy(regionTid = tid, items = emptyList())) }
        loadCatalog(reset = true)
    }

    /** 收藏夹内排序（mtime/view/pubtime），变更后重置分页。 */
    fun updateFavoriteOrder(order: String) {
        if (mutableState.value.route.screen != TvScreen.Favorites) return
        mutableState.update { it.copy(catalog = it.catalog.copy(favoriteOrder = order)) }
        loadCatalog(reset = true)
    }

    /** 网格密度（乘在最小卡宽上的系数），全局记忆；长按 OK 缩放与设置页档位共用。 */
    fun updateGridDensity(scale: Float) {
        preferences.gridDensity = scale
        mutableState.update { it.copy(gridDensity = scale) }
    }
    fun clearSearchHistory() { preferences.clearSearchHistory(); mutableState.update { it.copy(searchHistory = emptyList()) } }

    override fun onCleared() { continueJob?.cancel(); actionJob?.cancel(); qrJob?.cancel(); contentJob?.cancel(); accountJob?.cancel(); commentsJob?.cancel(); super.onCleared() }
}
