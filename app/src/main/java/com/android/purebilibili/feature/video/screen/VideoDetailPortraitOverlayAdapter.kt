@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.data.repository.StoryRepository
import com.android.purebilibili.feature.story.storyItemToRelatedVideo
import com.android.purebilibili.feature.video.ui.pager.PortraitVideoPager
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import kotlin.math.roundToInt

@Composable
internal fun VideoDetailPortraitOverlayAdapter(
    uiState: VideoPlaybackUiState,
    portraitExperienceEnabled: Boolean,
    isPortraitFullscreen: Boolean,
    /** 系统小窗：竖屏流只保留播放面，装饰层（标题 / 互动 / 进度）全部让位给系统 PiP。 */
    isInPipMode: Boolean = false,
    useOfficialInlinePortraitDetailExperience: Boolean,
    isLandscape: Boolean,
    shouldAnimatePortraitPager: Boolean,
    motionSpec: StandalonePortraitPagerMotionSpec,
    initialBvidOverride: String?,
    initialStartPositionMs: Long,
    entryCoverUrl: String = "",
    playbackViewModel: VideoPlaybackViewModel,
    engagementViewModel: VideoEngagementViewModel,
    sharedPlayer: ExoPlayer?,
    useTextureSurfaceForNavigation: Boolean,
    isPlayerSurfaceReady: Boolean = true,
    onExitFullscreen: () -> Unit,
    onHomeClick: () -> Unit,
    onVideoChange: (String) -> Unit,
    onPlaybackIdentityChange: (String, Long, String) -> Unit,
    onProgressUpdate: (String, Long, Long, String) -> Unit,
    onExitSnapshot: (String, Long, Long, String) -> Unit,
    onSearchClick: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val showPortraitFullscreen = shouldShowStandalonePortraitPager(
        portraitExperienceEnabled = portraitExperienceEnabled,
        isPortraitFullscreen = isPortraitFullscreen,
        useOfficialInlinePortraitDetailExperience = useOfficialInlinePortraitDetailExperience,
        hasPlayableState = uiState is VideoPlaybackUiState.Success || uiState is VideoPlaybackUiState.Loading,
    )
    var cachedSuccess by remember { mutableStateOf<VideoPlaybackUiState.Success?>(null) }
    LaunchedEffect(uiState) {
        if (uiState is VideoPlaybackUiState.Success) cachedSuccess = uiState
    }
    val success = when {
        uiState is VideoPlaybackUiState.Success -> uiState
        uiState is VideoPlaybackUiState.Loading -> cachedSuccess
        else -> null
    }
    val showEntryCoverPlaceholder = shouldShowPortraitEntryCoverPlaceholder(
        showPortraitFullscreen = showPortraitFullscreen,
        hasPlayableSuccess = success != null,
        entryCoverUrl = entryCoverUrl,
    )
    LaunchedEffect(isPortraitFullscreen, showPortraitFullscreen, success, isLandscape) {
        com.android.purebilibili.core.util.Logger.d(
            "VideoDetailScreen",
            "Portrait Mode Check: requested=$isPortraitFullscreen, shown=$showPortraitFullscreen, " +
                "success=${success != null}, isLandscape=$isLandscape",
        )
    }
    if (showEntryCoverPlaceholder) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = entryCoverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
    // 内嵌进入直接更新全屏布局并复用播放器，退出保留原有缩放与位移。
    val portraitExitTransition = remember(motionSpec) {
        val exitSpec = emphasizedExitTween<Float>(motionSpec.exitDurationMillis)
        fadeOut(exitSpec) + scaleOut(
            targetScale = motionSpec.exitScaleTarget,
            animationSpec = exitSpec,
            transformOrigin = TransformOrigin(0.5f, 0f),
        ) + slideOutVertically(
            animationSpec = emphasizedExitTween(motionSpec.exitDurationMillis),
            targetOffsetY = { -(it * motionSpec.exitTranslateUpFraction).roundToInt() },
        )
    }
    val portraitOnlyVerticalRecommendations by com.android.purebilibili.core.store.SettingsManager
        .getPortraitOnlyVerticalRecommendations(context)
        .collectAsStateWithLifecycle(initialValue = false)
    AnimatedVisibility(
        visible = showPortraitFullscreen && success != null,
        enter = if (shouldAnimatePortraitPager) {
            fadeIn(emphasizedEnterTween(motionSpec.enterDurationMillis))
        } else {
            EnterTransition.None
        },
        exit = portraitExitTransition,
        modifier = Modifier.fillMaxSize(),
    ) {
        success?.let { playableState ->
            val info = playableState.info
            // 竖屏流推荐:优先用 Story 短视频流(按当前视频请求,内容多样化),
            // 避免此前直接使用同主题相关推荐导致首屏全是相似视频/同一 UP 主;
            // Story 流不可用时才回退相关推荐。
            var portraitRecommendations by remember(info.bvid) {
                mutableStateOf(playableState.related)
            }
            LaunchedEffect(showPortraitFullscreen, info.bvid) {
                if (!showPortraitFullscreen) return@LaunchedEffect
                val storyItems = StoryRepository.getStoryFeed(
                    aid = info.aid,
                    bvid = info.bvid,
                ).getOrNull().orEmpty()
                val storyRecommendations = storyItems
                    .mapNotNull(::storyItemToRelatedVideo)
                    .filter { it.bvid.isNotBlank() && it.bvid != info.bvid }
                if (storyRecommendations.isNotEmpty()) {
                    portraitRecommendations = storyRecommendations
                }
            }
            PortraitVideoPager(
                initialBvid = initialBvidOverride ?: info.bvid,
                initialInfo = info,
                recommendations = portraitRecommendations,
                onlyVerticalRecommendations = portraitOnlyVerticalRecommendations,
                isInPipMode = isInPipMode,
                onBack = onExitFullscreen,
                onHomeClick = onHomeClick,
                onVideoChange = onVideoChange,
                onPlaybackIdentityChange = onPlaybackIdentityChange,
                viewModel = playbackViewModel,
                engagementViewModel = engagementViewModel,
                sharedPlayer = sharedPlayer,
                useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
                isPlayerSurfaceReady = isPlayerSurfaceReady,
                initialStartPositionMs = initialStartPositionMs,
                onProgressUpdate = onProgressUpdate,
                onExitSnapshot = onExitSnapshot,
                onSearchClick = onSearchClick,
                onUserClick = onUserClick,
                onExitFullscreen = onExitFullscreen,
            )
        }
    }
}

internal fun shouldShowPortraitEntryCoverPlaceholder(
    showPortraitFullscreen: Boolean,
    hasPlayableSuccess: Boolean,
    entryCoverUrl: String,
): Boolean {
    // 竖屏进入时压制封面占位，由黑色播放器直接起播，避免点击进入时看见封面闪烁
    return false
}
