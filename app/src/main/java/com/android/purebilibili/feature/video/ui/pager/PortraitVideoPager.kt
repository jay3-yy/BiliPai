package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.shouldResumePlaybackAfterUserSeek
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionDanmakuContainer
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionMaskStabilizer
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionModuleState
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionVisualMask
import com.android.purebilibili.feature.video.danmaku.checkFaceOcclusionModuleState
import com.android.purebilibili.feature.video.danmaku.createFaceOcclusionDetector
import com.android.purebilibili.feature.video.danmaku.detectFaceOcclusionRegions
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.playback.session.PlaybackSeekSessionState
import com.android.purebilibili.feature.video.playback.session.cancelPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.finishPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.shouldUsePlaybackSeekSessionPosition
import com.android.purebilibili.feature.video.playback.session.startPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.syncPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.updatePlaybackSeekInteraction
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.PortraitFullscreenOverlay
import com.android.purebilibili.feature.video.player.resolveHandleAudioFocusByPolicy
import com.android.purebilibili.feature.video.ui.section.resolveLongPressPlaybackParameters
import com.android.purebilibili.feature.video.ui.section.rebindPlayerSurfaceIfNeeded
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.viewmodel.PlayerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.resolvePlaybackEndAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class PortraitVideoInteractionOverride(
    val isLiked: Boolean? = null,
    val isFavorited: Boolean? = null,
    val likeCount: Int? = null,
    val favoriteCount: Int? = null
)

internal data class PortraitVideoInteractionUiState(
    val isLiked: Boolean,
    val isFavorited: Boolean,
    val likeCount: Int,
    val favoriteCount: Int
)

internal enum class PortraitFavoriteAction {
    OpenFavoriteFolders
}

/**
 * 竖屏无缝滑动播放页面 (TikTok Style)
 * 
 * @param initialBvid 初始视频 BVID
 * @param initialInfo 初始视频详情
 * @param recommendations 推荐视频列表
 * @param onBack 返回回调
 * @param onVideoChange 切换视频回调 (当滑动到新视频时通知外部)
 */

@UnstableApi
@Composable
fun PortraitVideoPager(
    initialBvid: String,
    initialInfo: ViewInfo,
    recommendations: List<RelatedVideo>,
    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onVideoChange: (String) -> Unit,
    viewModel: PlayerViewModel,
    commentViewModel: VideoCommentViewModel,
    sharedPlayer: ExoPlayer? = null,
    initialStartPositionMs: Long = 0L,
    onProgressUpdate: (String, Long, Long) -> Unit = { _, _, _ -> },
    onExitSnapshot: (String, Long, Long) -> Unit = { _, _, _ -> },
    onSearchClick: () -> Unit = {},
    onUserClick: (Long) -> Unit,
    onRotateToLandscape: () -> Unit
) {
    val context = LocalContext.current
    val useSharedPlayer = sharedPlayer != null
    val entryStartPositionMs = remember(initialBvid) { initialStartPositionMs.coerceAtLeast(0L) }
    val scope = rememberCoroutineScope()
    val danmakuManager = rememberDanmakuManager()
    val danmakuScope = com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT
    val danmakuSettings by SettingsManager
        .getDanmakuSettings(context, danmakuScope)
        .collectAsState(initial = com.android.purebilibili.core.store.DanmakuSettings())
    val danmakuEnabled = danmakuSettings.enabled
    val danmakuOpacity = danmakuSettings.opacity
    val danmakuFontScale = danmakuSettings.fontScale
    val danmakuSpeed = danmakuSettings.speed
    val danmakuDisplayArea = danmakuSettings.displayArea
    val danmakuMergeDuplicates = danmakuSettings.mergeDuplicates
    val danmakuAllowScroll = danmakuSettings.allowScroll
    val danmakuAllowTop = danmakuSettings.allowTop
    val danmakuAllowBottom = danmakuSettings.allowBottom
    val danmakuAllowColorful = danmakuSettings.allowColorful
    val danmakuAllowSpecial = danmakuSettings.allowSpecial
    val danmakuBlockRules = danmakuSettings.blockRules
    val danmakuSmartOcclusion = danmakuSettings.smartOcclusion
    val faceDetector = remember { createFaceOcclusionDetector() }
    var smartOcclusionModuleState by remember { mutableStateOf(FaceOcclusionModuleState.Checking) }
    DisposableEffect(faceDetector) {
        onDispose { faceDetector.close() }
    }
    LaunchedEffect(faceDetector, danmakuSmartOcclusion) {
        if (!danmakuSmartOcclusion) {
            smartOcclusionModuleState = FaceOcclusionModuleState.NotInstalled
            return@LaunchedEffect
        }
        smartOcclusionModuleState = FaceOcclusionModuleState.Checking
        smartOcclusionModuleState = checkFaceOcclusionModuleState(context, faceDetector)
    }
    val autoPlayEnabled by SettingsManager
        .getAutoPlay(context)
        .collectAsState(initial = true)
    val externalPlaylistAutoContinueEnabled by SettingsManager
        .getExternalPlaylistAutoContinue(context)
        .collectAsState(initial = true)
    val playbackCompletionBehavior by SettingsManager
        .getPlaybackCompletionBehavior(context)
        .collectAsState(initial = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC)
    val isExternalPlaylist by PlaylistManager.isExternalPlaylist.collectAsState()

    val baseRecommendations = remember(recommendations) {
        recommendations.distinctBy { it.bvid }
    }
    val initialPageIndex = remember(initialBvid, initialInfo.bvid, baseRecommendations) {
        resolvePortraitInitialPageIndex(
            initialBvid = initialBvid,
            initialInfoBvid = initialInfo.bvid,
            recommendations = baseRecommendations
        )
    }
    val recommendationItems = remember(initialInfo.bvid, baseRecommendations) {
        mutableStateListOf<RelatedVideo>().apply {
            addAll(baseRecommendations)
        }
    }
    val pageItems = remember(initialInfo.bvid, baseRecommendations) {
        mutableStateListOf<Any>().apply {
            add(initialInfo)
            addAll(baseRecommendations)
        }
    }
    val knownVideoAspectRatios = remember(initialInfo.bvid) {
        mutableStateMapOf<String, Float>().apply {
            resolveAspectRatioFromDimension(initialInfo.dimension)?.let { aspectRatio ->
                put(initialInfo.bvid, aspectRatio)
            }
        }
    }
    var watchLaterVideos by remember { mutableStateOf<List<RelatedVideo>>(emptyList()) }
    var isLoadingMoreRecommendations by remember { mutableStateOf(false) }
    val appendedRecommendationSeeds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        if (TokenManager.sessDataCache.isNullOrEmpty()) {
            watchLaterVideos = emptyList()
            return@LaunchedEffect
        }
        runCatching { NetworkModule.api.getWatchLaterList() }
            .onSuccess { response ->
                if (response.code == 0) {
                    watchLaterVideos = response.data?.list
                        .orEmpty()
                        .mapNotNull(::toRelatedVideoFromWatchLater)
                        .distinctBy { it.bvid }
                }
            }
            .onFailure {
                watchLaterVideos = emptyList()
            }
    }

    LaunchedEffect(watchLaterVideos) {
        if (watchLaterVideos.isEmpty()) return@LaunchedEffect
        val existingBvids = pageItems.mapNotNull {
            when (it) {
                is ViewInfo -> it.bvid
                is RelatedVideo -> it.bvid
                else -> null
            }
        }.toSet()
        val appendItems = watchLaterVideos.filter { it.bvid !in existingBvids }
        if (appendItems.isNotEmpty()) {
            pageItems.addAll(appendItems)
        }
    }
    
    val pagerState = rememberPagerState(initialPage = initialPageIndex) {
        pageItems.size
    }
    var currentPageScale by remember { mutableFloatStateOf(1f) }

    // [重构] 优先复用主播放器；仅在未传入 sharedPlayer 时才创建页内播放器
    val exoPlayer = sharedPlayer ?: remember(context) {
        val audioFocusEnabled = SettingsManager.getAudioFocusEnabledSync(context)
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                resolveHandleAudioFocusByPolicy(audioFocusEnabled = audioFocusEnabled)
            )
            .build()
            .apply {
                repeatMode = resolvePortraitPagerRepeatMode()
                volume = 1.0f
                setPlaybackSpeed(SettingsManager.getPreferredPlaybackSpeedSync(context))
            }
    }

    if (!useSharedPlayer) {
        DisposableEffect(exoPlayer) {
            onDispose {
                exoPlayer.release()
            }
        }
    }

    val sharedPlayerHasFrameAtEntry = remember(useSharedPlayer, exoPlayer) {
        useSharedPlayer &&
            exoPlayer.videoSize.width > 0 &&
            exoPlayer.videoSize.height > 0
    }

    val sharedPlayerShouldResumeAtEntry = remember(useSharedPlayer, exoPlayer) {
        useSharedPlayer && (exoPlayer.playWhenReady || exoPlayer.isPlaying)
    }

    // [状态] 当前播放的视频 URL
    var currentPlayingBvid by remember(initialBvid, useSharedPlayer) {
        mutableStateOf(
            resolvePortraitInitialPlayingBvid(
                useSharedPlayer = useSharedPlayer,
                initialBvid = initialBvid
            )
        )
    }
    var currentPlayingCid by remember(initialInfo.cid, useSharedPlayer) {
        mutableStateOf(if (useSharedPlayer) initialInfo.cid else 0L)
    }
    var currentPlayingAid by remember(initialInfo.aid, useSharedPlayer) {
        mutableStateOf(if (useSharedPlayer) initialInfo.aid else 0L)
    }
    var isLoading by remember { mutableStateOf(false) }
    var lastCommittedPage by remember(useSharedPlayer) {
        mutableIntStateOf(if (useSharedPlayer) 0 else -1)
    }
    var activeLoadGeneration by remember { mutableIntStateOf(0) }
    var hasConsumedInitialSeek by remember(useSharedPlayer) { mutableStateOf(useSharedPlayer) }
    var pendingAutoPlayGeneration by remember { mutableIntStateOf(-1) }
    var renderedFirstFrameGeneration by remember(useSharedPlayer, sharedPlayerHasFrameAtEntry) {
        mutableIntStateOf(
            resolvePortraitInitialRenderedFirstFrameGeneration(
                useSharedPlayer = useSharedPlayer,
                sharedPlayerHasFrameAtEntry = sharedPlayerHasFrameAtEntry
            )
        )
    }
    var lastAutoAdvancedBvid by remember { mutableStateOf<String?>(null) }
    var pendingUserSpaceNavigation by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(exoPlayer) {
        danmakuManager.attachPlayer(exoPlayer)
        onDispose { }
    }

    DisposableEffect(exoPlayer, activeLoadGeneration) {
        val autoPlayListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY &&
                    pendingAutoPlayGeneration == activeLoadGeneration &&
                    !exoPlayer.isPlaying
                ) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && pendingAutoPlayGeneration == activeLoadGeneration) {
                    pendingAutoPlayGeneration = -1
                }
            }

            override fun onRenderedFirstFrame() {
                renderedFirstFrameGeneration = activeLoadGeneration
                isLoading = false
            }
        }
        exoPlayer.addListener(autoPlayListener)
        onDispose {
            exoPlayer.removeListener(autoPlayListener)
        }
    }

    LaunchedEffect(useSharedPlayer, sharedPlayerShouldResumeAtEntry) {
        if (useSharedPlayer && sharedPlayerShouldResumeAtEntry) {
            exoPlayer.playWhenReady = true
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
        }
    }

    LaunchedEffect(pendingUserSpaceNavigation, pagerState.currentPage, pageItems.size) {
        if (!pendingUserSpaceNavigation) return@LaunchedEffect
        val item = pageItems.getOrNull(pagerState.currentPage)
        val expectedBvid = when (item) {
            is ViewInfo -> item.bvid
            is RelatedVideo -> item.bvid
            else -> ""
        }
        val shouldResync = shouldResyncPortraitPagerOnUserSpaceReturn(
            pendingUserSpaceNavigation = pendingUserSpaceNavigation,
            expectedBvid = expectedBvid,
            currentPlayingBvid = currentPlayingBvid,
            currentPlayerMediaId = exoPlayer.currentMediaItem?.mediaId
        )
        pendingUserSpaceNavigation = false
        if (!shouldResync) return@LaunchedEffect

        currentPlayingBvid = null
        currentPlayingCid = 0L
        currentPlayingAid = 0L
        lastCommittedPage = -1
        isLoading = true
    }

    DisposableEffect(lifecycleOwner, pagerState, pageItems, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver

            val item = pageItems.getOrNull(pagerState.currentPage)
            val expectedBvid = when (item) {
                is ViewInfo -> item.bvid
                is RelatedVideo -> item.bvid
                else -> ""
            }
            val shouldResync = shouldResyncPortraitPagerOnUserSpaceReturn(
                pendingUserSpaceNavigation = pendingUserSpaceNavigation,
                expectedBvid = expectedBvid,
                currentPlayingBvid = currentPlayingBvid,
                currentPlayerMediaId = exoPlayer.currentMediaItem?.mediaId
            )
            pendingUserSpaceNavigation = false
            if (!shouldResync) return@LifecycleEventObserver

            currentPlayingBvid = null
            currentPlayingCid = 0L
            currentPlayingAid = 0L
            lastCommittedPage = -1
            isLoading = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(
        exoPlayer,
        pagerState,
        pageItems.size,
        currentPlayingBvid,
        autoPlayEnabled,
        externalPlaylistAutoContinueEnabled,
        playbackCompletionBehavior,
        isExternalPlaylist
    ) {
        val autoAdvanceListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val playbackEndAction = resolvePlaybackEndAction(
                    behavior = playbackCompletionBehavior,
                    autoPlayEnabled = autoPlayEnabled,
                    isExternalPlaylist = isExternalPlaylist,
                    externalPlaylistAutoContinueEnabled = externalPlaylistAutoContinueEnabled
                )
                when (playbackEndAction) {
                    PlaybackEndAction.STOP -> return
                    PlaybackEndAction.REPEAT_CURRENT -> {
                        exoPlayer.seekTo(0)
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                    PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST,
                    PlaybackEndAction.AUTO_CONTINUE -> {
                        val playingBvid = currentPlayingBvid ?: return
                        if (lastAutoAdvancedBvid == playingBvid) return
                        lastAutoAdvancedBvid = playingBvid

                        val nextPage = resolveNextPortraitPageAfterPlaybackEnd(
                            action = playbackEndAction,
                            currentPage = pagerState.currentPage,
                            lastPage = pageItems.lastIndex
                        ) ?: return

                        scope.launch {
                            pagerState.animateScrollToPage(nextPage)
                        }
                    }
                    PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP -> {
                        val playingBvid = currentPlayingBvid ?: return
                        if (lastAutoAdvancedBvid == playingBvid) return
                        lastAutoAdvancedBvid = playingBvid

                        val nextPage = resolveNextPortraitPageAfterPlaybackEnd(
                            action = PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP,
                            currentPage = pagerState.currentPage,
                            lastPage = pageItems.lastIndex
                        ) ?: return
                        scope.launch {
                            pagerState.animateScrollToPage(nextPage)
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(autoAdvanceListener)
        onDispose {
            exoPlayer.removeListener(autoAdvanceListener)
        }
    }

    // [核心] 仅在页面 settle 后切流，避免拖动过程频繁切换导致卡顿与竞态
    LaunchedEffect(pagerState, pageItems) {
        snapshotFlow {
            resolveCommittedPage(
                isScrollInProgress = pagerState.isScrollInProgress,
                currentPage = pagerState.currentPage,
                lastCommittedPage = lastCommittedPage
            )
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { committedPage ->
                lastCommittedPage = committedPage
                val item = pageItems.getOrNull(committedPage) ?: return@collect

                val bvid = if (item is ViewInfo) item.bvid else (item as RelatedVideo).bvid
                val aid = if (item is ViewInfo) item.aid else (item as RelatedVideo).aid

                onVideoChange(bvid)

                if (
                    shouldLoadMorePortraitRecommendations(
                        committedPage = committedPage,
                        totalItemsCount = pageItems.size,
                        isLoadingMoreRecommendations = isLoadingMoreRecommendations
                    ) &&
                    bvid !in appendedRecommendationSeeds
                ) {
                    appendedRecommendationSeeds.add(bvid)
                    isLoadingMoreRecommendations = true
                    launch {
                        try {
                            val existingBvids = pageItems.mapNotNull { candidate ->
                                when (candidate) {
                                    is ViewInfo -> candidate.bvid
                                    is RelatedVideo -> candidate.bvid
                                    else -> null
                                }
                            }.toSet()
                            val appendItems = mergePortraitRecommendationAppendItems(
                                currentBvid = bvid,
                                existingBvids = existingBvids,
                                fetchedRecommendations = com.android.purebilibili.data.repository.VideoRepository
                                    .getRelatedVideos(bvid)
                            )
                            if (appendItems.isNotEmpty()) {
                                recommendationItems.addAll(appendItems)
                                pageItems.addAll(appendItems)
                            }
                        } finally {
                            isLoadingMoreRecommendations = false
                        }
                    }
                }

                if (shouldSkipPortraitReloadForCurrentMedia(
                        currentPlayingBvid = currentPlayingBvid,
                        targetBvid = bvid,
                        currentPlayerMediaId = exoPlayer.currentMediaItem?.mediaId
                    )
                ) {
                    isLoading = false
                    return@collect
                }

                activeLoadGeneration += 1
                val requestGeneration = activeLoadGeneration

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                danmakuManager.clear()
                isLoading = true
                currentPlayingBvid = bvid
                currentPlayingCid = 0L
                currentPlayingAid = aid
                pendingAutoPlayGeneration = requestGeneration
                renderedFirstFrameGeneration = -1

                launch {
                    try {
                        val result = com.android.purebilibili.data.repository.VideoRepository.getVideoDetails(
                            bvid = bvid,
                            aid = aid,
                            targetQuality = 64
                        )

                        result.fold(
                            onSuccess = { (info, playData) ->
                                val videoUrl = playData.dash?.video?.firstOrNull()?.baseUrl
                                    ?: playData.durl?.firstOrNull()?.url
                                val audioUrl = playData.dash?.audio?.firstOrNull()?.baseUrl

                                if (videoUrl.isNullOrEmpty()) {
                                    pendingAutoPlayGeneration = -1
                                    if (shouldApplyLoadResult(
                                            requestGeneration = requestGeneration,
                                            activeGeneration = activeLoadGeneration,
                                            expectedBvid = bvid,
                                            currentPlayingBvid = currentPlayingBvid
                                        )
                                    ) {
                                        isLoading = false
                                    }
                                    return@fold
                                }

                                val headers = mapOf(
                                    "Referer" to "https://www.bilibili.com",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                )
                                val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                    .setUserAgent(headers["User-Agent"])
                                    .setDefaultRequestProperties(headers)
                                val mediaSourceFactory = DefaultMediaSourceFactory(context)
                                    .setDataSourceFactory(dataSourceFactory)

                                val videoItem = MediaItem.Builder()
                                    .setUri(videoUrl)
                                    .setMediaId(bvid)
                                    .build()
                                val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                                val finalSource = if (!audioUrl.isNullOrEmpty()) {
                                    val audioItem = MediaItem.Builder()
                                        .setUri(audioUrl)
                                        .setMediaId("audio_$bvid")
                                        .build()
                                    val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                                    MergingMediaSource(videoSource, audioSource)
                                } else {
                                    videoSource
                                }

                                if (!shouldApplyLoadResult(
                                        requestGeneration = requestGeneration,
                                        activeGeneration = activeLoadGeneration,
                                        expectedBvid = bvid,
                                        currentPlayingBvid = currentPlayingBvid
                                    )
                                ) {
                                    com.android.purebilibili.core.util.Logger.d(
                                        "PortraitVideoPager",
                                        "Discarded stale video load for $bvid (request=$requestGeneration, active=$activeLoadGeneration, current=$currentPlayingBvid)"
                                    )
                                    return@fold
                                }

                                resolveAspectRatioFromDimension(info.dimension)?.let { aspectRatio ->
                                    knownVideoAspectRatios[bvid] = aspectRatio
                                }
                                currentPlayingCid = info.cid
                                currentPlayingAid = info.aid
                                exoPlayer.playWhenReady = true
                                exoPlayer.setMediaSource(finalSource)
                                exoPlayer.prepare()

                                if (committedPage == 0 && entryStartPositionMs > 0 && !hasConsumedInitialSeek) {
                                    exoPlayer.seekTo(entryStartPositionMs)
                                    hasConsumedInitialSeek = true
                                }

                                exoPlayer.play()
                            },
                            onFailure = {
                                pendingAutoPlayGeneration = -1
                                if (shouldApplyLoadResult(
                                        requestGeneration = requestGeneration,
                                        activeGeneration = activeLoadGeneration,
                                        expectedBvid = bvid,
                                        currentPlayingBvid = currentPlayingBvid
                                    )
                                ) {
                                    isLoading = false
                                }
                            }
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        pendingAutoPlayGeneration = -1
                        if (shouldApplyLoadResult(
                                requestGeneration = requestGeneration,
                                activeGeneration = activeLoadGeneration,
                                expectedBvid = bvid,
                                currentPlayingBvid = currentPlayingBvid
                            )
                        ) {
                            isLoading = false
                        }
                    }
                }
            }
    }

    LaunchedEffect(currentPlayingCid, currentPlayingAid, danmakuEnabled, exoPlayer) {
        if (currentPlayingCid > 0 && danmakuEnabled) {
            danmakuManager.isEnabled = true
            var durationMs = exoPlayer.duration
            var retries = 0
            while (durationMs <= 0 && retries < 50) {
                delay(100)
                durationMs = exoPlayer.duration
                retries++
            }
            danmakuManager.loadDanmaku(currentPlayingCid, currentPlayingAid, durationMs.coerceAtLeast(0L))
        } else {
            danmakuManager.isEnabled = false
        }
    }

    LaunchedEffect(
        danmakuOpacity,
        danmakuFontScale,
        danmakuSpeed,
        danmakuDisplayArea,
        danmakuMergeDuplicates,
        danmakuAllowScroll,
        danmakuAllowTop,
        danmakuAllowBottom,
        danmakuAllowColorful,
        danmakuAllowSpecial,
        danmakuBlockRules,
        danmakuSmartOcclusion
    ) {
        danmakuManager.updateSettings(
            opacity = danmakuOpacity,
            fontScale = danmakuFontScale,
            speed = danmakuSpeed,
            displayArea = danmakuDisplayArea,
            mergeDuplicates = danmakuMergeDuplicates,
            allowScroll = danmakuAllowScroll,
            allowTop = danmakuAllowTop,
            allowBottom = danmakuAllowBottom,
            allowColorful = danmakuAllowColorful,
            allowSpecial = danmakuAllowSpecial,
            blockedRules = danmakuBlockRules,
            // Mask-only mode: keep lane layout fixed, do not move danmaku tracks.
            smartOcclusion = false
        )
    }

    VerticalPager(
        state = pagerState,
        userScrollEnabled = shouldHandlePortraitTapGesture(scale = currentPageScale),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) { page ->
        val item = pageItems.getOrNull(page)
        val handleUserClick: (Long) -> Unit = { mid ->
            pendingUserSpaceNavigation = true
            onUserClick(mid)
        }
        
        if (item != null) {
            val itemBvid = when (item) {
                is ViewInfo -> item.bvid
                is RelatedVideo -> item.bvid
                else -> ""
            }
            VideoPageItem(
                item = item,
                isCurrentPage = page == pagerState.currentPage,
                onBack = onBack,
                onHomeClick = onHomeClick,
                viewModel = viewModel,
                commentViewModel = commentViewModel,
                exoPlayer = exoPlayer, // [核心] 传递共享播放器
                currentPlayingBvid = currentPlayingBvid, // [修复] 传递当前播放的 BVID 用于校验
                currentPlayingCid = currentPlayingCid,
                isLoading = if (page == pagerState.currentPage) isLoading else false, // 只有当前页显示 Loading
                danmakuManager = danmakuManager,
                danmakuEnabled = danmakuEnabled,
                danmakuSmartOcclusion = danmakuSmartOcclusion,
                faceDetector = faceDetector,
                smartOcclusionModuleState = smartOcclusionModuleState,
                onExitSnapshot = onExitSnapshot,
                onSearchClick = onSearchClick,
                onUserClick = handleUserClick,
                onRotateToLandscape = onRotateToLandscape,
                onProgressUpdate = onProgressUpdate,
                watchLaterVideos = watchLaterVideos,
                recommendationVideos = recommendationItems,
                knownVideoAspectRatio = knownVideoAspectRatios[itemBvid]
                    ?: (item as? ViewInfo)?.dimension?.let(::resolveAspectRatioFromDimension),
                hasRenderedFirstFrame = (renderedFirstFrameGeneration == activeLoadGeneration),
                initialProgressPositionMs = resolvePortraitInitialProgressPosition(
                    isFirstPage = page == 0,
                    initialStartPositionMs = entryStartPositionMs
                ),
                onCurrentPageScaleChange = { scale ->
                    if (page == pagerState.currentPage) {
                        currentPageScale = scale
                    }
                },
                onRequestVideoChange = { targetBvid ->
                    val targetIndex = pageItems.indexOfFirst { candidate ->
                        when (candidate) {
                            is ViewInfo -> candidate.bvid == targetBvid
                            is RelatedVideo -> candidate.bvid == targetBvid
                            else -> false
                        }
                    }
                    if (targetIndex >= 0) {
                        scope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    }
                }
            )
        }
    }
}

@UnstableApi
@Composable
private fun VideoPageItem(
    item: Any,
    isCurrentPage: Boolean,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    viewModel: PlayerViewModel,
    commentViewModel: VideoCommentViewModel,
    exoPlayer: ExoPlayer,
    currentPlayingBvid: String?, // [新增]
    currentPlayingCid: Long,
    isLoading: Boolean,
    danmakuManager: DanmakuManager,
    danmakuEnabled: Boolean,
    danmakuSmartOcclusion: Boolean,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    smartOcclusionModuleState: FaceOcclusionModuleState,
    onExitSnapshot: (String, Long, Long) -> Unit,
    onSearchClick: () -> Unit,
    onUserClick: (Long) -> Unit,
    onRotateToLandscape: () -> Unit,
    onProgressUpdate: (String, Long, Long) -> Unit,
    watchLaterVideos: List<RelatedVideo>,
    recommendationVideos: List<RelatedVideo>,
    knownVideoAspectRatio: Float?,
    hasRenderedFirstFrame: Boolean,
    initialProgressPositionMs: Long,
    onCurrentPageScaleChange: (Float) -> Unit,
    onRequestVideoChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var faceVisualMasks by remember { mutableStateOf(emptyList<FaceOcclusionVisualMask>()) }
    val faceMaskStabilizer = remember { FaceOcclusionMaskStabilizer() }
    val longPressSpeed by SettingsManager
        .getLongPressSpeed(context)
        .collectAsState(initial = 2.0f)
    val currentAudioQuality by viewModel.audioQualityPreference.collectAsState(initial = -1)
    val bvid = if (item is ViewInfo) item.bvid else (item as RelatedVideo).bvid
    val aid = if (item is ViewInfo) item.aid else (item as RelatedVideo).aid
    
    // [修复] 手动监听 ExoPlayer 播放状态，确保 UI 及时更新
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentVideoAspect by remember(bvid, currentPlayingBvid, knownVideoAspectRatio) {
        mutableFloatStateOf(
            resolvePortraitInitialVideoAspectRatio(
                itemBvid = bvid,
                currentPlayingBvid = currentPlayingBvid,
                playerVideoWidth = exoPlayer.videoSize.width,
                playerVideoHeight = exoPlayer.videoSize.height,
                knownVideoAspectRatio = knownVideoAspectRatio
            )
        )
    }
    
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying_: Boolean) {
                isPlaying = isPlaying_
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    currentVideoAspect = resolvePortraitRuntimeVideoAspectRatio(
                        knownVideoAspectRatio = knownVideoAspectRatio,
                        playerVideoWidth = videoSize.width,
                        playerVideoHeight = videoSize.height
                    )
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // [逻辑] 只有当播放器正在播放当前视频时，才显示 PlayerView
    val isPlayerReadyForThisVideo = bvid == currentPlayingBvid
    val snapshotCid = if (isPlayerReadyForThisVideo && currentPlayingCid > 0L) {
        currentPlayingCid
    } else {
        0L
    }

    LaunchedEffect(
        playerViewRef,
        isCurrentPage,
        isPlayerReadyForThisVideo,
        currentPlayingBvid,
        exoPlayer.videoSize
    ) {
        val view = playerViewRef ?: return@LaunchedEffect
        if (!shouldRebindSharedPlayerSurfaceOnAttach(
                isCurrentPage = isCurrentPage,
                isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                hasPlayerView = true,
                videoWidth = exoPlayer.videoSize.width,
                videoHeight = exoPlayer.videoSize.height
            )
        ) {
            return@LaunchedEffect
        }

        // Force the shared player to hand over its surface to the portrait pager view.
        rebindPlayerSurfaceIfNeeded(playerView = view, player = exoPlayer)
    }

    val title = if (item is ViewInfo) item.title else (item as RelatedVideo).title
    val cover = if (item is ViewInfo) item.pic else (item as RelatedVideo).pic
    val authorName = if (item is ViewInfo) item.owner.name else (item as RelatedVideo).owner.name
    val authorFace = if (item is ViewInfo) item.owner.face else (item as RelatedVideo).owner.face
    val authorMid = if (item is ViewInfo) item.owner.mid else (item as RelatedVideo).owner.mid

    // 提取时长
    val initialDuration = if (item is RelatedVideo) {
        item.duration * 1000L
    } else if (item is ViewInfo) {
        (item.pages.firstOrNull()?.duration ?: 0L) * 1000L
    } else {
        0L
    }

    // 互动状态
    var showCommentSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var detailSheetUpOnlyMode by remember { mutableStateOf(false) }
    var isOverlayVisible by remember { mutableStateOf(true) }

    // 进度状态 (从播放器获取)
    var progressState by remember(bvid, initialDuration, initialProgressPositionMs) {
        mutableStateOf(
            PlayerProgress(
                current = initialProgressPositionMs.coerceAtLeast(0L),
                duration = initialDuration,
                buffered = initialProgressPositionMs.coerceAtLeast(0L)
            )
        )
    }
    var seekSession by remember(bvid, initialProgressPositionMs) {
        mutableStateOf(
            syncPlaybackSeekSession(
                state = PlaybackSeekSessionState(),
                playbackPositionMs = initialProgressPositionMs.coerceAtLeast(0L)
            )
        )
    }
    
    // 如果是当前页，监听播放器进度
    LaunchedEffect(isCurrentPage, exoPlayer, hasRenderedFirstFrame) {
        if (isCurrentPage) {
            while (true) {
                if (isPlayerReadyForThisVideo) {
                    val playerPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val effectivePosition = if (
                        playerPosition == 0L &&
                        progressState.current > 0L &&
                        !hasRenderedFirstFrame
                    ) {
                        progressState.current
                    } else {
                        playerPosition
                    }
                    val realDuration = if (exoPlayer.duration > 0) exoPlayer.duration else initialDuration
                    seekSession = syncPlaybackSeekSession(
                        state = seekSession,
                        playbackPositionMs = effectivePosition
                    )
                    progressState = PlayerProgress(
                        current = seekSession.sliderPositionMs,
                        duration = realDuration,
                        buffered = exoPlayer.bufferedPosition
                    )
                    if (exoPlayer.isPlaying || effectivePosition > 0L) {
                        onProgressUpdate(bvid, effectivePosition, snapshotCid)
                    }
                }
                delay(200)
            }
        }
    }
    
    // 手势调整进度状态
    var isSeekGesture by remember { mutableStateOf(false) }
    var seekStartPosition by remember { mutableFloatStateOf(0f) }
    var seekTargetPosition by remember { mutableFloatStateOf(0f) }
    var isLongPressing by remember { mutableStateOf(false) }
    var longPressOriginPlaybackParameters by remember { mutableStateOf(PlaybackParameters.DEFAULT) }
    var effectiveLongPressSpeed by remember { mutableFloatStateOf(longPressSpeed) }
    var showLongPressSpeedFeedback by remember { mutableStateOf(false) }
    var scale by remember(bvid) { mutableFloatStateOf(1f) }
    var panX by remember(bvid) { mutableFloatStateOf(0f) }
    var panY by remember(bvid) { mutableFloatStateOf(0f) }

    fun resetViewportTransform() {
        scale = 1f
        panX = 0f
        panY = 0f
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            resetViewportTransform()
        }
    }

    LaunchedEffect(isCurrentPage, isLongPressing, longPressOriginPlaybackParameters) {
        if (shouldRestorePortraitLongPressSpeed(isLongPressing = isLongPressing, isCurrentPage = isCurrentPage)) {
            exoPlayer.playbackParameters = longPressOriginPlaybackParameters
            isLongPressing = false
            showLongPressSpeedFeedback = false
        }
    }

    val latestIsLongPressing by rememberUpdatedState(isLongPressing)
    val latestLongPressOriginPlaybackParameters by rememberUpdatedState(longPressOriginPlaybackParameters)
    DisposableEffect(exoPlayer) {
        onDispose {
            if (latestIsLongPressing) {
                exoPlayer.playbackParameters = latestLongPressOriginPlaybackParameters
            }
        }
    }

    LaunchedEffect(isCurrentPage, scale, onCurrentPageScaleChange) {
        onCurrentPageScaleChange(if (isCurrentPage) scale else 1f)
    }

    LaunchedEffect(
        playerViewRef,
        isCurrentPage,
        isPlayerReadyForThisVideo,
        danmakuEnabled,
        danmakuSmartOcclusion,
        smartOcclusionModuleState
    ) {
        if (
            !isCurrentPage ||
            !isPlayerReadyForThisVideo ||
            !danmakuEnabled ||
            !danmakuSmartOcclusion ||
            smartOcclusionModuleState != FaceOcclusionModuleState.Ready
        ) {
            faceMaskStabilizer.reset()
            faceVisualMasks = emptyList()
            return@LaunchedEffect
        }
        faceMaskStabilizer.reset()

        while (isActive) {
            val view = playerViewRef
            if (view == null || view.width <= 0 || view.height <= 0 || !exoPlayer.isPlaying) {
                delay(1200L)
                continue
            }

            val videoWidth = exoPlayer.videoSize.width
            val videoHeight = exoPlayer.videoSize.height
            val sampleWidth = 480
            val sampleHeight = when {
                videoWidth > 0 && videoHeight > 0 -> (sampleWidth * videoHeight / videoWidth).coerceIn(270, 960)
                else -> 270
            }

            val detection = withTimeoutOrNull(1_500L) {
                detectFaceOcclusionRegions(
                    playerView = view,
                    sampleWidth = sampleWidth,
                    sampleHeight = sampleHeight,
                    detector = faceDetector
                )
            } ?: com.android.purebilibili.feature.video.danmaku.FaceOcclusionDetectionResult(
                verticalRegions = emptyList(),
                maskRects = emptyList(),
                visualMasks = emptyList()
            )
            faceVisualMasks = faceMaskStabilizer.step(detection.visualMasks)
            delay(if (detection.visualMasks.isEmpty()) 1300L else 900L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isCurrentPage, bvid) {
                if (!isCurrentPage) return@pointerInput

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var observedMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) {
                            break
                        }
                        if (pressedCount < 2) {
                            if (observedMultiTouch) {
                                break
                            }
                            continue
                        }
                        observedMultiTouch = true

                        if (isLongPressing) {
                            exoPlayer.playbackParameters = longPressOriginPlaybackParameters
                            isLongPressing = false
                            showLongPressSpeedFeedback = false
                        }

                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()

                        if (zoom != 1f || pan != Offset.Zero) {
                            val updatedScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = updatedScale

                            if (updatedScale > 1f) {
                                val maxPanX = (size.width * updatedScale - size.width) / 2f
                                val maxPanY = (size.height * updatedScale - size.height) / 2f
                                panX = (panX + pan.x * updatedScale).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + pan.y * updatedScale).coerceIn(-maxPanY, maxPanY)
                                isOverlayVisible = false
                            } else {
                                panX = 0f
                                panY = 0f
                            }

                            event.changes.forEach { change ->
                                if (change.position != change.previousPosition) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(longPressSpeed, currentAudioQuality, isCurrentPage) {
                detectTapGestures(
                    onTap = {
                        if (!shouldHandlePortraitTapGesture(scale = scale)) {
                            return@detectTapGestures
                        }
                        isOverlayVisible = !isOverlayVisible
                    },
                    onDoubleTap = {
                        if (!shouldHandlePortraitTapGesture(scale = scale)) {
                            return@detectTapGestures
                        }
                        if (isCurrentPage) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    },
                    onLongPress = {
                        if (!shouldHandlePortraitLongPressGesture(scale = scale)) {
                            return@detectTapGestures
                        }
                        if (!isCurrentPage) return@detectTapGestures
                        longPressOriginPlaybackParameters = exoPlayer.playbackParameters
                        val longPressPlaybackParameters = resolveLongPressPlaybackParameters(
                            requestedSpeed = longPressSpeed,
                            currentAudioQuality = currentAudioQuality
                        )
                        effectiveLongPressSpeed = longPressPlaybackParameters.speed
                        exoPlayer.playbackParameters = longPressPlaybackParameters
                        isLongPressing = true
                        showLongPressSpeedFeedback = true
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isLongPressing) {
                            exoPlayer.playbackParameters = longPressOriginPlaybackParameters
                            isLongPressing = false
                            showLongPressSpeedFeedback = false
                        }
                    }
                )
            }
            // 进度调整手势
            .pointerInput(progressState.duration, scale, isCurrentPage) {
                detectHorizontalDragGestures(
                    onDragStart = { 
                        if (
                            isCurrentPage &&
                            progressState.duration > 0 &&
                            shouldHandlePortraitSeekGesture(scale = scale)
                        ) {
                            isSeekGesture = true
                            seekStartPosition = exoPlayer.currentPosition.toFloat()
                            seekTargetPosition = seekStartPosition
                        }
                    },
                    onDragEnd = {
                        if (isCurrentPage && isSeekGesture) {
                            val targetPosition = seekTargetPosition.toLong()
                            seekPlayerFromUserAction(exoPlayer, targetPosition)
                            danmakuManager.seekTo(targetPosition)
                            isSeekGesture = false
                        }
                    },
                    onDragCancel = { isSeekGesture = false },
                    onHorizontalDrag = { _, dragAmount ->
                        if (isCurrentPage && isSeekGesture && progressState.duration > 0) {
                            val seekDelta = (dragAmount / size.width) * progressState.duration * 0.75f
                            seekTargetPosition = (seekTargetPosition + seekDelta).coerceIn(0f, progressState.duration.toFloat())
                        }
                    }
                )
            }
    ) {
        // [核心逻辑]
        // 始终保留 AndroidView 以确保 Surface 准备就绪，但只有当播放器加载了当前视频时才将其绑定或显示
        // 否则显示封面
        
        if (isCurrentPage && isPlayerReadyForThisVideo) {
            PortraitVideoViewportContainer(
                currentVideoAspect = currentVideoAspect,
                modifier = Modifier.fillMaxSize()
            ) {
                key(currentPlayingBvid, bvid) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = panX
                                translationY = panY
                            }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    playerViewRef = this
                                    player = exoPlayer
                                    useController = false
                                    keepScreenOn = true
                                    resizeMode = VideoAspectRatio.FIT.playerResizeMode
                                    setKeepContentOnPlayerReset(true)
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                }
                            },
                            update = { view ->
                                playerViewRef = view
                                if (view.player != exoPlayer) {
                                    view.player = exoPlayer
                                }
                                if (view.resizeMode != VideoAspectRatio.FIT.playerResizeMode) {
                                    view.resizeMode = VideoAspectRatio.FIT.playerResizeMode
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (danmakuEnabled) {
                            AndroidView(
                                factory = { ctx ->
                                    FaceOcclusionDanmakuContainer(ctx).apply {
                                        setMasks(faceVisualMasks)
                                        setVideoViewport(
                                            videoWidth = exoPlayer.videoSize.width,
                                            videoHeight = exoPlayer.videoSize.height,
                                            resizeMode = VideoAspectRatio.FIT.resizeMode
                                        )
                                        danmakuManager.attachView(danmakuView())
                                    }
                                },
                                update = { container ->
                                    container.setMasks(faceVisualMasks)
                                    container.setVideoViewport(
                                        videoWidth = exoPlayer.videoSize.width,
                                        videoHeight = exoPlayer.videoSize.height,
                                        resizeMode = playerViewRef?.resizeMode ?: VideoAspectRatio.FIT.resizeMode
                                    )
                                    val view = container.danmakuView()
                                    if (view.width > 0 && view.height > 0) {
                                        val sizeTag = "${view.width}x${view.height}"
                                        if (view.tag != sizeTag) {
                                            view.tag = sizeTag
                                            danmakuManager.attachView(view)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 封面图 (在加载中、未匹配到视频、或未开始播放时显示)
        val showCover = shouldShowPortraitCover(
            isLoading = isLoading,
            isCurrentPage = isCurrentPage,
            isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
            hasRenderedFirstFrame = hasRenderedFirstFrame
        )
        
        if (showCover) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (shouldUseViewportBoundPortraitCover(
                        isCurrentPage = isCurrentPage,
                        isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                        hasRenderedFirstFrame = hasRenderedFirstFrame
                    )
                ) {
                    PortraitVideoViewportContainer(
                        currentVideoAspect = currentVideoAspect,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncImage(
                            model = FormatUtils.fixImageUrl(cover),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = resolvePortraitCoverContentScale()
                        )
                    }
                } else {
                    AsyncImage(
                        model = FormatUtils.fixImageUrl(cover),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = resolvePortraitCoverContentScale()
                    )
                }

                if (isLoading && isCurrentPage) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }
        }

        // 暂停图标 (仅当前页且暂停时显示)
        // [修复] 使用响应式的 isPlaying 状态
        val showPauseIcon = shouldShowPortraitPauseIcon(
            isCurrentPage = isCurrentPage,
            isPlaying = isPlaying,
            playWhenReady = exoPlayer.playWhenReady,
            isLoading = isLoading,
            isSeekGesture = isSeekGesture
        )
        if (showPauseIcon) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Pause",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(60.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // 滑动进度提示
        if (isSeekGesture && progressState.duration > 0) {
            val targetTimeText = FormatUtils.formatDuration(seekTargetPosition.toLong())
            val totalTimeText = FormatUtils.formatDuration(progressState.duration)
            val deltaMs = (seekTargetPosition - seekStartPosition).toLong()
            val deltaText = if (deltaMs >= 0) "+${FormatUtils.formatDuration(deltaMs)}" else "-${FormatUtils.formatDuration(-deltaMs)}"
            
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    text = "$targetTimeText / $totalTimeText",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = deltaText,
                    color = if (deltaMs >= 0) Color(0xFF66FF66) else Color(0xFFFF6666),
                    fontSize = 14.sp
                )
            }
        }

        // 长按倍速提示（透明背景 + 循环箭头动画，位于视频上方）
        AnimatedVisibility(
            visible = isLongPressing && isCurrentPage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it })
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "fast_forward_portrait")
            val arrow1Alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        1.0f at 300
                        0.3f at 600
                        0.3f at 900
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "arrow1"
            )
            val arrow2Alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        0.3f at 300
                        1.0f at 600
                        0.3f at 900
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "arrow2"
            )
            val arrow3Alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        0.3f at 600
                        1.0f at 900
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "arrow3"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val arrowAlphas = listOf(arrow1Alpha, arrow2Alpha, arrow3Alpha)
                arrowAlphas.forEach { alpha ->
                    Canvas(
                        modifier = Modifier.size(14.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = alpha)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${effectiveLongPressSpeed}x",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = isCurrentPage && scale > 1.05f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Button(
                onClick = {
                    resetViewportTransform()
                    isOverlayVisible = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "还原画面"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "还原画面",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlay & Interaction
    val currentUiState = viewModel.uiState.collectAsState().value
    val isCurrentModelVideo = (currentUiState as? PlayerUiState.Success)?.info?.bvid == bvid
    val currentSuccess = currentUiState as? PlayerUiState.Success
    var portraitInteractionOverride by remember(bvid) {
        mutableStateOf(PortraitVideoInteractionOverride())
    }
    val favoriteSaveEvent by viewModel.favoriteFolderSaveEvent.collectAsState()
    var consumedFavoriteSaveEventVersion by remember(bvid) { mutableLongStateOf(0L) }
    val stat = if (item is ViewInfo) item.stat else (item as RelatedVideo).stat
    val resolvedInteractionState = resolvePortraitVideoInteractionUiState(
        targetBvid = bvid,
        fallbackStat = stat,
        sharedState = currentSuccess,
        localOverride = portraitInteractionOverride
    )
    val isFollowing = (currentUiState as? PlayerUiState.Success)?.followingMids?.contains(authorMid) == true
    val fallbackDetailInfo = when (item) {
        is ViewInfo -> item
        is RelatedVideo -> toViewInfoForPortraitDetail(item)
        else -> null
    }
    val portraitDetailInfo = if (isCurrentModelVideo && currentSuccess != null) {
        currentSuccess.info
    } else {
        fallbackDetailInfo
    }
    val detailVideoList = remember(bvid, watchLaterVideos, recommendationVideos) {
        buildPortraitDetailVideoList(
            currentBvid = bvid,
            watchLaterVideos = watchLaterVideos,
            recommendationVideos = recommendationVideos
        )
    }
    val upOnlyVideos = remember(detailVideoList.videos, authorMid, bvid) {
        detailVideoList.videos.filter { candidate ->
            candidate.owner.mid == authorMid && candidate.bvid != bvid
        }
    }
    val detailSheetTitle = remember(detailSheetUpOnlyMode, recommendationVideos.size, upOnlyVideos.size) {
        if (detailSheetUpOnlyMode) {
            if (upOnlyVideos.isEmpty()) "该 UP 暂无可切换视频" else "UP 主视频"
        } else {
            detailVideoList.title
        }
    }
    val detailSheetVideos = remember(detailSheetUpOnlyMode, upOnlyVideos, detailVideoList.videos) {
        if (detailSheetUpOnlyMode) upOnlyVideos else detailVideoList.videos
    }
    val toggleDanmaku: () -> Unit = {
        val next = !danmakuEnabled
        danmakuManager.isEnabled = next
        scope.launch {
            SettingsManager.setDanmakuEnabled(
                context,
                next,
                com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT
            )
        }
        Unit
    }
    val canHandlePortraitInteraction = shouldHandlePortraitVideoInteraction(
        isCurrentPage = isCurrentPage,
        aid = aid,
        bvid = bvid
    )

    LaunchedEffect(favoriteSaveEvent?.version, aid) {
        val event = favoriteSaveEvent ?: return@LaunchedEffect
        if (event.aid != aid || event.version == consumedFavoriteSaveEventVersion) {
            return@LaunchedEffect
        }
        val nextFavoriteCount = (
            resolvedInteractionState.favoriteCount +
                when {
                    event.isFavorited == resolvedInteractionState.isFavorited -> 0
                    event.isFavorited -> 1
                    else -> -1
                }
            ).coerceAtLeast(0)
        portraitInteractionOverride = portraitInteractionOverride.copy(
            isFavorited = event.isFavorited,
            favoriteCount = nextFavoriteCount
        )
        consumedFavoriteSaveEventVersion = event.version
    }

    LaunchedEffect(isCurrentPage, authorMid) {
        if (isCurrentPage && authorMid > 0L) {
            viewModel.ensureFollowStatus(authorMid)
        }
    }

    PortraitFullscreenOverlay(
            title = title,
            authorName = authorName,
            authorFace = authorFace,
            isPlaying = if (isCurrentPage) isPlaying else false,
            progress = progressState,
            
            statView = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.view else stat.view,
            statLike = resolvedInteractionState.likeCount,
            statDanmaku = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.danmaku else stat.danmaku,
            statReply = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.reply else stat.reply,
            statFavorite = resolvedInteractionState.favoriteCount,
            statShare = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.share else stat.share,
            
            isLiked = resolvedInteractionState.isLiked,
            isCoined = false,
            isFavorited = resolvedInteractionState.isFavorited,
            
            isFollowing = isFollowing,
            onFollowClick = { 
                viewModel.toggleFollow(authorMid, isFollowing)
            },
            
            onDetailClick = {
                if (portraitDetailInfo != null) {
                    detailSheetUpOnlyMode = false
                    showDetailSheet = true
                }
            },
            onTitleClick = {
                if (portraitDetailInfo != null) {
                    detailSheetUpOnlyMode = false
                    showDetailSheet = true
                }
            },
            onAuthorClick = {
                if (portraitDetailInfo != null) {
                    detailSheetUpOnlyMode = true
                    showDetailSheet = true
                }
            },
            onLikeClick = {
                if (canHandlePortraitInteraction) {
                    val currentLikeState = resolvedInteractionState.isLiked
                    val currentLikeCount = resolvedInteractionState.likeCount
                    viewModel.toggleLikeForVideo(
                        aid = aid,
                        bvid = bvid,
                        currentlyLiked = currentLikeState
                    ) { liked ->
                        val nextLikeCount = (
                            currentLikeCount +
                                when {
                                    liked == currentLikeState -> 0
                                    liked -> 1
                                    else -> -1
                                }
                            ).coerceAtLeast(0)
                        portraitInteractionOverride = portraitInteractionOverride.copy(
                            isLiked = liked,
                            likeCount = nextLikeCount
                        )
                    }
                }
            },
            onCoinClick = { },
            onFavoriteClick = {
                if (canHandlePortraitInteraction) {
                    when (resolvePortraitFavoriteAction()) {
                        PortraitFavoriteAction.OpenFavoriteFolders -> {
                            viewModel.showFavoriteFolderDialog(aid)
                        }
                    }
                }
            },
            onCommentClick = { showCommentSheet = true },
            onShareClick = {
                val shareText = buildPortraitShareText(title = title, bvid = bvid)
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share too..."))
            },
            
            currentSpeed = 1.0f,
            currentQualityLabel = "高清",
            currentRatio = VideoAspectRatio.FIT,
            danmakuEnabled = danmakuEnabled,
            isStatusBarHidden = true,
            
            onBack = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid)
                onBack()
            },
            onHomeClick = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid)
                onHomeClick()
            },
            onPlayPause = {
                if (isCurrentPage) {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            },
            onSeek = {
                if (isCurrentPage) {
                    val resolvedDuration = progressState.duration
                        .takeIf { durationMs -> durationMs > 0L }
                        ?: exoPlayer.duration.coerceAtLeast(0L)
                    val targetPosition = resolvePortraitCommittedSeekPosition(
                        requestedPositionMs = it,
                        durationMs = resolvedDuration
                    )
                    val commitResult = finishPlaybackSeekInteraction(
                        updatePlaybackSeekInteraction(
                            state = seekSession,
                            positionMs = targetPosition
                        )
                    )
                    seekSession = commitResult.state
                    progressState = progressState.copy(current = commitResult.committedPositionMs)
                    seekPlayerFromUserAction(
                        player = exoPlayer,
                        positionMs = commitResult.committedPositionMs,
                        shouldResumePlaybackOverride = commitResult.shouldResumePlayback
                    )
                    danmakuManager.seekTo(commitResult.committedPositionMs)
                }
            },
            onSeekStart = { },
            seekPositionMs = seekSession.sliderPositionMs,
            isSeekScrubbing = seekSession.isSliderMoving,
            onSeekDragStart = { position ->
                seekSession = startPlaybackSeekInteraction(
                    state = seekSession,
                    positionMs = position,
                    shouldResumePlayback = shouldResumePlaybackAfterUserSeek(
                        playWhenReadyBeforeSeek = exoPlayer.playWhenReady,
                        playbackStateBeforeSeek = exoPlayer.playbackState
                    )
                )
            },
            onSeekDragUpdate = { position ->
                seekSession = updatePlaybackSeekInteraction(
                    state = seekSession,
                    positionMs = position
                )
            },
            onSeekDragCancel = {
                seekSession = cancelPlaybackSeekInteraction(seekSession)
            },
            onSpeedClick = { },
            onQualityClick = { },
            onRatioClick = { },
            onDanmakuToggle = toggleDanmaku,
            onDanmakuInputClick = {
                if (isCurrentPage) {
                    viewModel.showDanmakuSendDialog()
                }
            },
            onToggleStatusBar = { },
            onSearchClick = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid)
                onSearchClick()
            },
            onMoreClick = {
                showDetailSheet = true
            },
            onRotateToLandscape = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid)
                onRotateToLandscape()
            },
            
            showControls = isOverlayVisible && !showCommentSheet && !showDetailSheet
        )

        PortraitCommentSheet(
            visible = showCommentSheet,
            onDismiss = { showCommentSheet = false },
            commentViewModel = commentViewModel,
            aid = aid,
            upMid = authorMid,
            expectedReplyCount = if (isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.reply else stat.reply,
            emoteMap = currentSuccess?.emoteMap ?: emptyMap(),
            onRootCommentClick = { viewModel.openRootCommentComposer() },
            onReplyClick = { reply ->
                viewModel.setReplyingTo(reply)
                viewModel.showCommentInputDialog()
            },
            onUserClick = onUserClick
        )

        if (isCurrentPage) {
            val showCommentInput by viewModel.showCommentDialog.collectAsState()
            val isSendingComment by viewModel.isSendingComment.collectAsState()
            val replyingToComment by viewModel.replyingToComment.collectAsState()
            val emotePackages by viewModel.emotePackages.collectAsState()
            val commentState by commentViewModel.commentState.collectAsState()

            LaunchedEffect(aid) {
                viewModel.commentSentEvent.collect { reply ->
                    commentViewModel.onExternalCommentSent(aid = aid, newReply = reply)
                }
            }

            com.android.purebilibili.feature.video.ui.components.CommentInputDialog(
                visible = showCommentInput,
                onDismiss = { viewModel.hideCommentInputDialog() },
                isSending = isSendingComment,
                replyToName = replyingToComment?.member?.uname,
                inputHint = if (replyingToComment != null) commentState.childInputHint else commentState.rootInputHint,
                canUploadImage = commentState.canUploadImage,
                canInputComment = commentState.canInputComment,
                emotePackages = emotePackages,
                onSend = { message, imageUris ->
                    viewModel.sendComment(message, imageUris)
                    viewModel.hideCommentInputDialog()
                }
            )
        }
        
        PortraitDetailSheet(
            visible = showDetailSheet,
            onDismiss = {
                showDetailSheet = false
                detailSheetUpOnlyMode = false
            },
            info = portraitDetailInfo,
            recommendationTitle = detailSheetTitle,
            recommendations = detailSheetVideos,
            onRecommendationClick = { targetBvid ->
                showDetailSheet = false
                detailSheetUpOnlyMode = false
                onRequestVideoChange(targetBvid)
            },
            onAuthorClick = { mid ->
                showDetailSheet = false
                detailSheetUpOnlyMode = false
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid)
                onUserClick(mid)
            },
            danmakuEnabled = danmakuEnabled,
            onDanmakuToggle = toggleDanmaku
        )
    }
}

internal fun resolvePortraitInitialPageIndex(
    initialBvid: String,
    initialInfoBvid: String,
    recommendations: List<RelatedVideo>
): Int {
    if (initialBvid == initialInfoBvid) return 0
    val recommendationIndex = recommendations.indexOfFirst { it.bvid == initialBvid }
    if (recommendationIndex < 0) return 0
    return recommendationIndex + 1
}

internal fun shouldHandlePortraitVideoInteraction(
    isCurrentPage: Boolean,
    aid: Long,
    bvid: String
): Boolean {
    return isCurrentPage && aid > 0L && bvid.isNotBlank()
}

internal fun resolvePortraitFavoriteAction(): PortraitFavoriteAction {
    return PortraitFavoriteAction.OpenFavoriteFolders
}

internal fun resolvePortraitVideoInteractionUiState(
    targetBvid: String,
    fallbackStat: Stat,
    sharedState: PlayerUiState.Success?,
    localOverride: PortraitVideoInteractionOverride? = null
): PortraitVideoInteractionUiState {
    val shouldUseSharedState = sharedState?.info?.bvid == targetBvid
    return if (shouldUseSharedState && sharedState != null) {
        PortraitVideoInteractionUiState(
            isLiked = sharedState.isLiked,
            isFavorited = sharedState.isFavorited,
            likeCount = sharedState.info.stat.like,
            favoriteCount = sharedState.info.stat.favorite
        )
    } else {
        PortraitVideoInteractionUiState(
            isLiked = localOverride?.isLiked ?: false,
            isFavorited = localOverride?.isFavorited ?: false,
            likeCount = localOverride?.likeCount ?: fallbackStat.like,
            favoriteCount = localOverride?.favoriteCount ?: fallbackStat.favorite
        )
    }
}

internal fun resolvePortraitPagerRepeatMode(): Int = Player.REPEAT_MODE_OFF

@Composable
internal fun PortraitVideoViewportContainer(
    currentVideoAspect: Float,
    modifier: Modifier = Modifier,
    viewportModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val safeAspect = currentVideoAspect.coerceAtLeast(0.1f)
        val containerAspect = if (maxHeight.value > 0f) {
            maxWidth.value / maxHeight.value
        } else {
            safeAspect
        }
        val viewportHeight = if (safeAspect > containerAspect) {
            maxWidth / safeAspect
        } else {
            maxHeight
        }
        val viewportWidth = if (safeAspect > containerAspect) {
            maxWidth
        } else {
            maxHeight * safeAspect
        }

        Box(
            modifier = viewportModifier
                .size(
                    width = viewportWidth.coerceAtMost(maxWidth),
                    height = viewportHeight.coerceAtMost(maxHeight)
                )
                .align(Alignment.Center)
        ) {
            content()
        }
    }
}

internal fun resolvePortraitInitialVideoAspectRatio(
    itemBvid: String,
    currentPlayingBvid: String?,
    playerVideoWidth: Int,
    playerVideoHeight: Int,
    knownVideoAspectRatio: Float? = null
): Float {
    knownVideoAspectRatio
        ?.takeIf { it > 0f }
        ?.let { return it }
    val hasValidPlayerSize = playerVideoWidth > 0 && playerVideoHeight > 0
    return if (itemBvid == currentPlayingBvid && hasValidPlayerSize) {
        playerVideoWidth.toFloat() / playerVideoHeight.toFloat()
    } else {
        9f / 16f
    }
}

internal fun resolvePortraitRuntimeVideoAspectRatio(
    knownVideoAspectRatio: Float?,
    playerVideoWidth: Int,
    playerVideoHeight: Int
): Float {
    val safeKnownAspect = knownVideoAspectRatio?.takeIf { it > 0f }
    if (playerVideoWidth <= 0 || playerVideoHeight <= 0) {
        return safeKnownAspect ?: (9f / 16f)
    }
    val playerAspect = playerVideoWidth.toFloat() / playerVideoHeight.toFloat()
    if (safeKnownAspect == null) return playerAspect
    val knownPortrait = safeKnownAspect < 1f
    val playerPortrait = playerAspect < 1f
    return if (knownPortrait != playerPortrait) {
        safeKnownAspect
    } else {
        playerAspect
    }
}

internal fun resolvePortraitInitialRenderedFirstFrameGeneration(
    useSharedPlayer: Boolean,
    sharedPlayerHasFrameAtEntry: Boolean
): Int {
    return if (useSharedPlayer && sharedPlayerHasFrameAtEntry) 0 else -1
}

internal fun resolveAspectRatioFromDimension(
    dimension: com.android.purebilibili.data.model.response.Dimension?
): Float? {
    val source = dimension ?: return null
    if (source.width <= 0 || source.height <= 0) return null
    val normalizedRotate = ((source.rotate % 360) + 360) % 360
    val shouldSwap = normalizedRotate == 90 || normalizedRotate == 270
    val effectiveWidth = if (shouldSwap) source.height else source.width
    val effectiveHeight = if (shouldSwap) source.width else source.height
    if (effectiveWidth <= 0 || effectiveHeight <= 0) return null
    return effectiveWidth.toFloat() / effectiveHeight.toFloat()
}

internal fun shouldRebindSharedPlayerSurfaceOnAttach(
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
    hasPlayerView: Boolean,
    videoWidth: Int,
    videoHeight: Int
): Boolean {
    return isCurrentPage && isPlayerReadyForThisVideo && hasPlayerView
}
