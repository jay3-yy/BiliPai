// 文件路径: feature/video/screen/VideoDetailScreen.kt
package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress


import com.android.purebilibili.feature.video.ambient.PlayerAmbientLayout
import coil3.request.crossfade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.refresh.HistoryRefreshSuppression
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.toAdaptiveStrategySnapshot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.resolveCommentFraudLightMessage
import com.android.purebilibili.data.repository.shouldShowCommentFraudResultDialog
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.store.PortraitPlayerCollapseMode
import com.android.purebilibili.feature.video.player.shouldReclaimVideoPlaybackSession
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppWindowSystemUiController
import com.android.purebilibili.core.ui.setWindowNavigationBarColor
import com.android.purebilibili.core.ui.setWindowStatusBarColor
//  已改用 AppContentColors.accent()

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
// Refactored UI components
import com.android.purebilibili.feature.video.ui.section.VideoTitleSection
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.DescriptionSection
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.ActionButton
import com.android.purebilibili.feature.video.ui.components.RelatedVideosHeader
import com.android.purebilibili.feature.video.ui.components.RelatedVideoItem
import com.android.purebilibili.feature.video.ui.components.CoinDialog
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
// Imports for moved classes
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoComposerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementEvent
import com.android.purebilibili.feature.video.viewmodel.VideoSupplementViewModel
import com.android.purebilibili.feature.video.viewmodel.toEngagementSeed
import com.android.purebilibili.feature.video.viewmodel.toSupplementSeed
import com.android.purebilibili.feature.video.viewmodel.QualitySwitchFailureDialogState
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.state.rememberVideoPlayerState
import com.android.purebilibili.feature.video.state.shouldReuseMiniPlayerAtEntry
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionActions
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionState
import com.android.purebilibili.feature.video.ui.section.resolveAllowLivePlayerSharedElementForMorph
import com.android.purebilibili.feature.video.ui.section.resolveNavigationLiveSurfaceTextureEnabled
import com.android.purebilibili.feature.video.ui.section.shouldKeepVideoPlaybackAwake
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureBlockState
import com.android.purebilibili.feature.video.ui.components.ReplyHeader
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.CommentFraudResultDialog
import com.android.purebilibili.feature.video.ui.components.VideoCommentSheetHost
import com.android.purebilibili.feature.video.ui.components.VideoInlineSubReplyDetailContent

import com.android.purebilibili.feature.video.viewmodel.CommentSortMode  //  新增
import com.android.purebilibili.feature.video.ui.components.LikeBurstAnimation
import com.android.purebilibili.feature.video.ui.components.TripleSuccessAnimation
import com.android.purebilibili.feature.video.ui.components.VideoDetailSkeleton
import com.android.purebilibili.feature.video.ui.components.VideoActionFeedbackHost
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayModePreference
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.policy.reduceVideoDetailPostScroll
import com.android.purebilibili.feature.video.policy.reduceVideoDetailPreScroll
import com.android.purebilibili.feature.video.policy.resolveVideoDetailCollapseProgress
import com.android.purebilibili.feature.video.policy.shouldSkipGesturePlayerCollapseForLayout
import com.android.purebilibili.feature.video.policy.shouldTrackVideoDetailCollapseMotion
import com.android.purebilibili.feature.video.subtitle.resolveSubtitlePreferenceSession
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppButton
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.LocalVideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.resolveVideoDetailShellOverlayCornerDp
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionEnterEasing
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionReturnEasing
import com.android.purebilibili.core.ui.transition.resolveVideoCardDetailChromeAlpha
import com.android.purebilibili.core.ui.transition.resolveVideoCardSecondaryContentVisualFrame
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionSourceCornerDp
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionVisualSpec
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldForceCoverOnlyForReturnOwnership
import com.android.purebilibili.core.ui.transition.shouldTreatLiveSurfaceRenderableForReturnMorph
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellContainerTransform
import com.android.purebilibili.core.ui.transition.VideoCardShellSharedBoundsRole
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.core.ui.transition.videoSharedElementBoundsTransformSpec
import com.android.purebilibili.core.ui.rememberAppCollectionIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppMusicIcon
import com.android.purebilibili.core.ui.rememberAppPhotoIcon
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlaybackService
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.player.PlaylistUiState
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
// 📱 [新增] 竖屏全屏
import com.android.purebilibili.feature.video.ui.overlay.PortraitFullscreenOverlay
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.core.util.layoutHinges
import com.android.purebilibili.core.util.resolvePlayerWindowOrientationPolicy

import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.feature.video.ui.components.DanmakuContextMenu
import com.android.purebilibili.feature.video.ui.components.DanmakuBlockActionTarget
import com.android.purebilibili.feature.video.ui.components.resolveDanmakuBlockActionFeedbackMessage
import com.android.purebilibili.feature.video.danmaku.appendDanmakuKeywordBlockRule
import com.android.purebilibili.feature.video.danmaku.appendDanmakuUserHashBlockRule
import com.android.purebilibili.feature.video.ui.components.InteractiveChoiceOverlay
import com.android.purebilibili.feature.video.ui.feedback.VideoFeedbackAnchor
import com.android.purebilibili.feature.video.ui.feedback.TripleCelebrationPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveQualityReminderPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveTripleCelebrationPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoFeedbackPlacement
import com.android.purebilibili.feature.video.ui.section.resolveForcedReturnCoverSharedElementSourceRoute
import com.android.purebilibili.feature.video.viewmodel.PlayerToastPresentation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 评论区「一键回顶」恢复播放器事件:评论区 host 与竖屏播放器处于不同
 * 代码块/作用域,通过共享事件桥接,播放器侧观察到后恢复全尺寸。
 */
private val commentBackToTopRestoreFlow =
    kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

private const val CONTINUOUS_PLAYER_MORPH_DURATION_MILLIS = 280

private const val VIDEO_DETAIL_COLLAPSE_SIGNAL_IDLE_TIMEOUT_MS = 120L

@Composable
private fun CollapsedPlayerNavigationBar(
    scrollRatio: Float,
    topInset: Dp,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    onPlayClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (scrollRatio > 0f) {
        val useMiuixNonGlassChrome = isMiuixNonGlassEnabled()
        val mediaScrimAlpha = resolveCollapsedPlayerMediaScrimAlpha(scrollRatio)
        val toolbarAlpha = resolveCollapsedPlayerToolbarAlpha(scrollRatio)
        val toolbarSurface = MaterialTheme.colorScheme.surface
        Box(
            modifier = modifier.drawBehind {
                drawRect(
                    color = Color.Black,
                    alpha = mediaScrimAlpha,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = toolbarAlpha },
            ) {
                // PiliPlus: status-bar app bar and kToolbarHeight video header are two siblings.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topInset)
                        .background(toolbarSurface)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(toolbarSurface)
                        .clickable(onClick = onPlayClick),
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (useMiuixNonGlassChrome) {
                            AppIconButton(
                                onClick = onBack,
                                modifier = Modifier.size(AppChromeSizeTokens.MinimumTouchTarget),
                            ) {
                                AppIcon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = AppContentColors.primaryText(),
                                )
                            }
                        } else {
                            IconButton(onClick = onBack, modifier = Modifier.size(width = 42.dp, height = 34.dp).appElasticPress()) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = AppContentColors.primaryText(),
                                )
                            }
                        }
                        if (useMiuixNonGlassChrome) {
                            AppIconButton(
                                onClick = onHomeClick,
                                modifier = Modifier.size(AppChromeSizeTokens.MinimumTouchTarget),
                            ) {
                                AppIcon(
                                    Icons.Filled.Home,
                                    contentDescription = "首页",
                                    tint = AppContentColors.primaryText(),
                                )
                            }
                        } else {
                            IconButton(onClick = onHomeClick, modifier = Modifier.size(width = 42.dp, height = 34.dp).appElasticPress()) {
                                Icon(
                                    Icons.Filled.Home,
                                    contentDescription = "首页",
                                    tint = AppContentColors.primaryText(),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (useMiuixNonGlassChrome) {
                            AppIcon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                            )
                            AppText("立即播放", color = AppContentColors.accent())
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                            )
                            Text("立即播放", color = AppContentColors.accent())
                        }
                    }
                    if (useMiuixNonGlassChrome) {
                        AppIconButton(
                            onClick = onMoreClick,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(AppChromeSizeTokens.MinimumTouchTarget),
                        ) {
                            AppIcon(
                                Icons.Filled.MoreVert,
                                contentDescription = "更多设置",
                                tint = AppContentColors.primaryText(),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onMoreClick,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(width = 42.dp, height = 34.dp).appElasticPress(),
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "更多设置",
                                tint = AppContentColors.primaryText(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun VideoDetailScreenStateHolder(
    bvid: String,
    cid: Long = 0L,
    coverUrl: String = "",
    startInFullscreen: Boolean = false,
    startAudioFromRoute: Boolean = false,
    autoEnterPortraitFromRoute: Boolean = false,
    initialVerticalFromRoute: Boolean = false,
    directPortraitEntryFromRoute: Boolean = false,
    resumePositionMsFromRoute: Long = 0L,
    openCommentRootRpidFromRoute: Long = 0L,
    openCommentTargetRpidFromRoute: Long = 0L,
    sourceRouteForSharedElement: String? = null,
    keepLoadedContentForBackPreview: Boolean = false,
    bindLivePlayerForBackPreview: Boolean = keepLoadedContentForBackPreview,
    predictiveBackCancelRecoveryGeneration: Int = 0,
    isReturningFromDetail: Boolean = false,
    isQuickReturningFromDetail: Boolean = false,
    onMarkReturningFromDetail: () -> Unit = {},
    onClearReturningFromDetail: () -> Unit = {},
    transitionEnabled: Boolean = false,
    transitionEnterDurationMillis: Int = 320,
    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onNavigateToAudioMode: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onSearchKeywordClick: (String) -> Unit = {},
    onOpenBilibiliLink: ((String) -> Unit)? = null,
    onVideoClick: (String, android.os.Bundle?) -> Unit,
    onReplaceVideoDetail: (String, Long, String, Long) -> Boolean = { _, _, _, _ -> false },
    onUpClick: (Long) -> Unit = {},
    onUpClickWithVideo: ((Long, String) -> Unit)? = null,
    miniPlayerManager: MiniPlayerManager? = null,
    isInPipMode: Boolean = false,
    isVisible: Boolean = true,
    isPlaybackSessionActive: Boolean = isVisible,
    onImmersivePlaybackChanged: (Boolean) -> Unit = {},
    viewModel: VideoPlaybackViewModel = viewModel(),
    engagementViewModel: VideoEngagementViewModel = viewModel(),
    composerViewModel: VideoComposerViewModel = viewModel(),
    supplementViewModel: VideoSupplementViewModel = viewModel(),
    commentViewModel: VideoCommentViewModel = viewModel(),
    onBgmClick: (BgmInfo) -> Unit = {}
) {
    // 详情页打开期间抑制历史/收藏列表刷新:播放心跳会持续触发
    // HistoryRefreshBus,若父级列表在预测性返回手势动画中重新加载,
    // 元素位置中途平移会导致返回表现异常。退出详情页(含返回完成)
    // 后恢复,抑制期间遗漏的刷新在恢复时补发一次。
    DisposableEffect(Unit) {
        HistoryRefreshSuppression.suppress()
        onDispose {
            HistoryRefreshSuppression.resume()
        }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val directPortraitEntryEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAutoPortraitFullscreen(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeUpBadgesVisible by com.android.purebilibili.core.store.SettingsManager
        .getHomeUpBadgesVisible(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    // Keep unknown distinct from false: the first PlayerView must use the persisted surface type.
    // The remembered flow never emits null, so lifecycle restarts retain the last known value.
    val liveSurfaceCardTransitionPreference by remember(context) {
        com.android.purebilibili.core.store.SettingsManager
            .getLiveSurfaceCardTransitionEnabled(context)
            .map<Boolean, Boolean?> { it }
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlayerSurfaceReady = liveSurfaceCardTransitionPreference != null
    val liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionPreference == true
    // SDR live morph TextureView only when both master transition + live-surface switch are on.
    // HDR still forces SurfaceView inside shouldUseTextureSurfaceForFlip (no quality sacrifice).
    val useTextureSurfaceForNavigation = remember(
        transitionEnabled,
        liveSurfaceCardTransitionEnabled,
    ) {
        resolveNavigationLiveSurfaceTextureEnabled(
            cardTransitionEnabled = transitionEnabled,
            liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
        )
    }
    val videoTransitionAdaptiveInfo = LocalVideoTransitionAdaptiveInfo.current
    val allowLivePlayerSharedElement = remember(
        transitionEnabled,
        liveSurfaceCardTransitionEnabled,
        videoTransitionAdaptiveInfo.foldPosture,
    ) {
        shouldUseVideoDetailSharedElementMorph(videoTransitionAdaptiveInfo.foldPosture) &&
            resolveAllowLivePlayerSharedElementForMorph(
            cardTransitionEnabled = transitionEnabled,
            liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
        )
    }
    val motionSpec = remember(transitionEnterDurationMillis) {
        resolveVideoDetailMotionSpec(transitionEnterDurationMillis)
    }
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val homeSharedTransitionMotionSpec = remember(
        sourceRouteForSharedElement,
        transitionEnabled,
        sharedTransitionSpeedSettings,
        isQuickReturningFromDetail,
        videoTransitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRouteForSharedElement,
            transitionEnabled = transitionEnabled,
            speedSettings = sharedTransitionSpeedSettings,
            isQuickReturn = isQuickReturningFromDetail,
            adaptiveInfo = videoTransitionAdaptiveInfo,
        )
    }
    val videoCardDepthBackgroundState = LocalVideoCardTransitionBackgroundState.current
    val scopedHeroDriver = com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState.current
    val entryHeroDriver = scopedHeroDriver
        .takeIf {
            isVideoDetailEntryActiveMiuixTransitionSource(
                sourceRouteForSharedElement, videoCardDepthBackgroundState.sourceRouteProvider(),
                miuixTransitionEnabled = it.enabled,
            )
        }
    val frozenTransitionSourceCornerDp =
        videoCardDepthBackgroundState.sourceCornerDpProvider()
    val sharedTransitionSourceCornerDp = remember(
        sourceRouteForSharedElement,
        frozenTransitionSourceCornerDp,
    ) {
        frozenTransitionSourceCornerDp
            ?: resolveVideoSharedTransitionSourceCornerDp(sourceRouteForSharedElement)
    }
    val autoPlayOnOpenEnabled by com.android.purebilibili.core.store.SettingsManager
        .getClickToPlay(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(context)
        )
    val videoSharedPlaybackIntent = remember(autoPlayOnOpenEnabled, startAudioFromRoute) {
        resolveVideoSharedTransitionPlaybackIntent(
            clickToPlayEnabled = autoPlayOnOpenEnabled,
            forceImmediatePlayback = startAudioFromRoute
        )
    }
    val routeSheetMotion = remember(sourceRouteForSharedElement, transitionEnabled) {
        resolveVideoDetailRouteSheetMotion(
            sourceRoute = sourceRouteForSharedElement,
            transitionEnabled = transitionEnabled
        )
    }
    val presentationState = rememberVideoDetailPresentationState(
        routeBvid = bvid,
        initialCid = 0L,
        initialPortraitFullscreen = shouldStartInPortraitFullscreenFromRouteHint(
            autoEnterPortraitFromRoute = autoEnterPortraitFromRoute,
            startAudioFromRoute = startAudioFromRoute,
            initialVerticalFromRoute = initialVerticalFromRoute,
            directPortraitEntryFromRoute = directPortraitEntryFromRoute,
        ),
        initialPipMode = isInPipMode,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val subjectSnapshot by viewModel.subjectSnapshot.collectAsStateWithLifecycle()
    val engagementState by engagementViewModel.uiState.collectAsStateWithLifecycle()
    val favoriteFolderSaveEvent by viewModel.favoriteFolderSaveEvent.collectAsStateWithLifecycle()
    val favoriteQuickSaveDefaultFolder by com.android.purebilibili.core.store.FavoriteInteractionSettingsStore
        .getQuickSaveDefaultFolder(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val latestFavoriteQuickSaveDefaultFolder = rememberUpdatedState(favoriteQuickSaveDefaultFolder)
    DisposableEffect(viewModel, presentationState) {
        viewModel.setPageIdentityCommitListener(presentationState::syncPlaybackIdentity)
        onDispose {
            viewModel.setPageIdentityCommitListener(null)
        }
    }
    val playbackActions = remember(viewModel, context, presentationState) {
        VideoDetailPlaybackActions(
            resolveNextWatch = viewModel::resolveNextWatchSuggestion,
            playNextWatch = viewModel::playNextWatchSuggestion,
            changeQuality = viewModel::changeQuality,
            reloadVideo = viewModel::reloadVideo,
            retryPlayback = viewModel::retry,
            switchCdn = viewModel::switchCdn,
            switchCdnTo = viewModel::switchCdnTo,
            probeCdnCandidates = viewModel::probeCurrentCdnCandidates,
            setAudioMode = viewModel::setAudioMode,
            setSleepTimer = viewModel::setSleepTimer,
            switchPage = { pageIndex -> viewModel.switchPage(pageIndex) },
            openDownloadDialog = viewModel::openDownloadDialog,
            showDanmakuSendDialog = viewModel::showDanmakuSendDialog,
            skipSponsorSegment = viewModel::skipCurrentSponsorSegment,
            dismissSponsorSkipButton = viewModel::dismissSponsorSkipButton,
            voteSponsorSegment = viewModel::voteCurrentSponsorSegment,
            markSponsorContributionBoundary = viewModel::markSponsorContributionBoundary,
            markWholeVideoAsSponsor = viewModel::markWholeVideoAsSponsor,
            setSponsorContributionCategory = viewModel::setSponsorContributionCategory,
            setSponsorContributionActionType = viewModel::setSponsorContributionActionType,
            submitSponsorContribution = viewModel::submitSponsorContribution,
            cancelSponsorContribution = viewModel::cancelSponsorContribution,
            notifyExplicitSeek = viewModel::notifyPluginsOfExplicitSeek,
            setVideoCodec = viewModel::setVideoCodec,
            setVideoSecondCodec = viewModel::setVideoSecondCodec,
            setAudioQuality = viewModel::setAudioQuality,
            applyPlaybackSpeed = viewModel::applyPlaybackSpeedFromUi,
            changeAudioLanguage = viewModel::changeAudioLanguage,
            saveCover = { viewModel.saveCover(context) },
            downloadAudio = { viewModel.downloadAudio(context) },
            selectSubtitleTrack = viewModel::selectSubtitleTrack,
            showFavoriteFolderDialog = viewModel::showFavoriteFolderDialog,
            toggleFavoriteFolderSelection = viewModel::toggleFavoriteFolderSelection,
            saveFavoriteFolderSelection = viewModel::saveFavoriteFolderSelection,
            dismissFavoriteFolderDialog = viewModel::dismissFavoriteFolderDialog,
            createFavoriteFolder = viewModel::createFavoriteFolder,
            retryAiSummary = viewModel::retryAiSummary,
            createVideoNoteDraftFromAiSummary = viewModel::createVideoNoteDraftFromAiSummary,
            openVideoNoteEditor = viewModel::openVideoNoteEditor,
            closeVideoNoteEditor = viewModel::closeVideoNoteEditor,
            updateVideoNoteEditorDocument = viewModel::updateVideoNoteEditorDocument,
            currentVideoNoteTimestamp = viewModel::currentVideoNoteTimestamp,
            seekTo = viewModel::seekTo,
            saveVideoNote = viewModel::saveVideoNote,
            deleteVideoNote = viewModel::deleteVideoNote,
            retryVideoNote = viewModel::retryVideoNote,
            loadMorePublicVideoNotes = viewModel::loadMorePublicVideoNotes,
            openRootCommentComposer = viewModel::openRootCommentComposer,
            replyTo = {
                viewModel.setReplyingTo(it)
                viewModel.showCommentInputDialog()
            },
            markVideoNotInterested = viewModel::markVideoNotInterested,
            likeDanmaku = { viewModel.likeDanmaku(it) },
            likeDanmakuToggle = { dmid, like -> viewModel.likeDanmaku(dmid, like) },
            likedDanmakuIds = viewModel.likedDanmakuIds,
            reportDanmaku = { dmid, reason -> viewModel.reportDanmaku(dmid, reason) },
            recallDanmaku = { viewModel.recallDanmaku(it) }
        )
    }
    val engagementActions = remember(engagementViewModel, viewModel) {
        VideoDetailEngagementActions(
            toggleFollow = engagementViewModel::toggleFollow,
            followOwner = engagementViewModel::followOwner,
            toggleFavorite = engagementViewModel::toggleFavorite,
            onFavoriteAction = { isLongPress ->
                when (
                    resolveVideoFavoriteAction(
                        entryPoint = VideoFavoriteEntryPoint.DetailActionRow,
                        isLongPress = isLongPress,
                        quickSaveDefaultFolder = latestFavoriteQuickSaveDefaultFolder.value,
                    )
                ) {
                    VideoFavoriteAction.ToggleFavorite -> engagementViewModel.toggleFavorite()
                    VideoFavoriteAction.OpenFavoriteFolders -> viewModel.showFavoriteFolderDialog()
                }
            },
            toggleLike = engagementViewModel::toggleLike,
            toggleDislike = engagementViewModel::toggleDislike,
            openCoinDialog = engagementViewModel::openCoinDialog,
            doTripleAction = engagementViewModel::doTripleAction,
            toggleWatchLater = engagementViewModel::toggleWatchLater
        )
    }
    val commentActions = remember(commentViewModel) {
        VideoDetailCommentActions(
            loadComments = commentViewModel::loadComments,
            refreshComments = commentViewModel::refreshComments,
            setSortMode = commentViewModel::setSortMode,
            deleteComment = commentViewModel::deleteComment,
            startDissolve = commentViewModel::startDissolve,
            loadMoreSubReplies = commentViewModel::loadMoreSubReplies,
            refreshSubReplies = commentViewModel::refreshSubReplies,
            setSubReplySortMode = commentViewModel::setSubReplySortMode,
            openSubReply = commentViewModel::openSubReply,
            openSubReplyConversation = commentViewModel::openSubReplyConversation,
            closeSubReplyConversation = commentViewModel::closeSubReplyConversation,
            closeSubReply = commentViewModel::closeSubReply,
            startSubDissolve = commentViewModel::startSubDissolve,
            deleteSubComment = commentViewModel::deleteSubComment,
            likeComment = commentViewModel::likeComment,
            hateComment = commentViewModel::hateComment,
            reportComment = { rpid, reason -> commentViewModel.reportComment(rpid, reason) },
            toggleTopComment = commentViewModel::toggleTopComment,
            checkCommentFraud = commentViewModel::checkCommentFraud
        )
    }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var isNavigatingToVideo by presentationState.navigatingToVideoState
    // `isNavigatingToVideo` 仅覆盖共享元素动画，动画结束会提前复位；NavHost 旧 entry
    // 仍可能再存活几帧。弹幕主机离开态必须保持到旧 entry 真正销毁。
    var hasCommittedRelatedVideoNavigation by remember(bvid) { mutableStateOf(false) }
    var isNavigatingToAudioMode by presentationState.navigatingToAudioModeState
    var isNavigatingToMiniMode by presentationState.navigatingToMiniModeState
    var hasAutoEnteredAudioMode by rememberSaveable { mutableStateOf(false) }
    var hasAutoEnteredPortraitFromRoute by rememberSaveable(bvid) { mutableStateOf(false) }
    LaunchedEffect(isVisible, isNavigatingToAudioMode) {
        if (isVisible && isNavigatingToAudioMode) {
            // The retained detail entry is active again after audio mode popped. Do not leave
            // later navigation/disposal permanently classified as an audio hand-off.
            presentationState.clearNavigatingToAudioMode()
        }
    }
    // 路由要求直达竖屏全屏时，立刻盖过可能被 saveable 复写的详情态。
    LaunchedEffect(
        bvid,
        autoEnterPortraitFromRoute,
        initialVerticalFromRoute,
        directPortraitEntryFromRoute,
        startAudioFromRoute,
    ) {
        if (
            shouldStartInPortraitFullscreenFromRouteHint(
                autoEnterPortraitFromRoute = autoEnterPortraitFromRoute,
                startAudioFromRoute = startAudioFromRoute,
                initialVerticalFromRoute = initialVerticalFromRoute,
                directPortraitEntryFromRoute = directPortraitEntryFromRoute,
            )
        ) {
            presentationState.setPortraitFullscreen(true)
            hasAutoEnteredPortraitFromRoute = true
        }
    }
    var hasHandledCommentRootFromRoute by rememberSaveable(
        bvid,
        openCommentRootRpidFromRoute,
        openCommentTargetRpidFromRoute
    ) { mutableStateOf(false) }
    // 🔄 [Seamless Playback] Internal BVID state to support seamless switching in portrait mode
    var currentBvid by presentationState.currentBvidState
    var currentBvidCid by presentationState.currentCidState
    var landscapeCommentPanelVisible by rememberSaveable(currentBvid) { mutableStateOf(false) }
    var landscapeCommentPanelOnLeft by rememberSaveable(currentBvid) { mutableStateOf(false) }
    val landscapeCommentCloseProgress = remember(currentBvid) { mutableFloatStateOf(1f) }
    // Episode cover captured at collection/playlist in-page switch (survives Loading.Initial).
    var pendingInPageSwitchCoverUrl by rememberSaveable { mutableStateOf("") }
    var isPipMode by presentationState.pipModeState
    var isPortraitFullscreen by presentationState.portraitFullscreenState
    var selectedVideoContentTabIndex by presentationState.selectedTabIndexState
    val playbackTargetCid = resolveVideoDetailPlaybackTargetCid(
        routeBvid = bvid,
        routeCid = cid,
        currentBvid = currentBvid,
        currentBvidCid = currentBvidCid
    )
    val introListState = rememberSaveable(currentBvid, saver = LazyListState.Saver) {
        LazyListState()
    }
    val detailContentState = remember(uiState, detailState, currentBvid, playbackTargetCid) {
        detailState.contentSnapshot(uiState, currentBvid, playbackTargetCid)
    }
    VideoDetailDomainEffects(
        context = context,
        isVisible = isVisible,
        uiState = detailContentState ?: uiState,
        subjectSnapshot = subjectSnapshot,
        favoriteFolderSaveEvent = favoriteFolderSaveEvent,
        playbackViewModel = viewModel,
        engagementViewModel = engagementViewModel,
        composerViewModel = composerViewModel,
        supplementViewModel = supplementViewModel,
    )
    val commentListState = rememberSaveable(currentBvid, saver = LazyListState.Saver) {
        LazyListState()
    }
    // 横屏评论面板必须用独立列表状态：与竖屏共用时，转全屏途中横屏列表被销毁
    // 会让共享 state 的滚动互斥锁卡死，回竖屏后评论区划不动。
    val landscapeCommentListState = remember(currentBvid) { LazyListState() }
    val videoContentPagerState: PagerState = key(currentBvid) {
        rememberPagerState(pageCount = { 2 })
    }
    var relatedParentUiSnapshot by remember(currentBvid) {
        mutableStateOf<VideoDetailParentUiSnapshot?>(null)
    }
    var relatedParentWasCovered by remember(currentBvid) { mutableStateOf(false) }

    val entryRootAnimatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val entryRootSharedTransitionScope = LocalSharedTransitionScope.current
    val entryNavigationTransitionScope = LocalVideoDetailNavigationTransitionScope.current
    val detailShellSharedBoundsEnabledForEntry = shouldUseVideoCardShellContainerTransform(
        sourceRoute = sourceRouteForSharedElement,
        transitionEnabled = transitionEnabled,
        hasSharedTransitionScope = entryRootSharedTransitionScope != null,
        hasAnimatedVisibilityScope = entryRootAnimatedVisibilityScope != null
    )
    // 有列表来源时默认按 shell morph 进场：即使首帧 AVS 尚未挂上，也先 defer 重内容。
    val shellSharedBoundsLikely =
        transitionEnabled && !sourceRouteForSharedElement.isNullOrBlank()
    val reuseFromMiniPlayerAtEntry = remember(currentBvid, playbackTargetCid, miniPlayerManager) {
        val manager = miniPlayerManager
        if (manager == null) {
            false
        } else {
            shouldReuseMiniPlayerAtEntry(
                isMiniPlayerActive = manager.isActive,
                miniPlayerBvid = manager.currentBvid,
                miniPlayerCid = manager.currentCid,
                hasMiniPlayerInstance = manager.player != null,
                requestBvid = currentBvid,
                requestCid = playbackTargetCid
            )
        }
    }
    val deferVideoDetailEntryLoad = shouldDeferVideoDetailLoadUntilEntryTransitionFinished(
        transitionEnabled = transitionEnabled,
        detailShellSharedBoundsEnabled = detailShellSharedBoundsEnabledForEntry ||
            shellSharedBoundsLikely,
        reuseFromMiniPlayerAtEntry = reuseFromMiniPlayerAtEntry,
        isReturningFromDetail = isReturningFromDetail,
        hasNavigationTransitionScope = entryNavigationTransitionScope != null,
    )
    val entryTransitionFinished = rememberVideoDetailEntryTransitionFinished(
        deferLoad = deferVideoDetailEntryLoad,
        sharedTransitionScope = entryRootSharedTransitionScope,
        animatedVisibilityScope = entryRootAnimatedVisibilityScope,
        fallbackDurationMillis = homeSharedTransitionMotionSpec.durationMillis,
        heroDriver = entryHeroDriver,
        navigationTransitionScope = entryNavigationTransitionScope,
    )
    // 网络请求仍立即启动；只将媒体准备移出导航动画，封面继续承担首帧交接。
    val entryPlaybackReady = entryTransitionFinished

    fun markSecondaryNavigationLeave(expectedBvid: String = currentBvid) {
        miniPlayerManager?.markLeavingByNavigation(expectedBvid = expectedBvid)
    }

    var hasStartedCurrentVideoPlayback by rememberSaveable(currentBvid) {
        mutableStateOf(false)
    }
    val navigateToUserSpaceFromVideo: (Long) -> Unit = { mid ->
        val locateBvid = currentBvid.takeIf { hasStartedCurrentVideoPlayback }.orEmpty()
        onUpClickWithVideo?.invoke(mid, locateBvid) ?: onUpClick(mid)
    }

    val navigateToSearchFromVideo: () -> Unit = {
        onNavigateToSearch()
    }

    val navigateToSearchKeywordFromVideo: (String) -> Unit = { keyword ->
        onSearchKeywordClick(keyword)
    }

    // 与播放器/Overlay 共用当前 bvid Session；同页切集/相关推荐 push 前清掉旧弹幕会话，
    // 避免新页 DanmakuView 绑定时把旧片缓存闪上去或卡在未重放状态。
    val sharedDanmakuManager = rememberDanmakuManager(currentBvid)

    fun switchVideoInCurrentDetailPage(
        targetBvid: String,
        targetCid: Long,
        autoPlay: Boolean = true
    ) {
        val normalizedBvid = targetBvid.trim()
        if (normalizedBvid.isBlank()) return
        val safeCid = targetCid.coerceAtLeast(0L)
        val success = uiState as? VideoPlaybackUiState.Success
        if (success?.info?.bvid == normalizedBvid && (safeCid <= 0L || success.info.cid == safeCid)) {
            return
        }
        // Capture episode cover before Success is replaced by Loading.
        val switchedCover = resolveUgcSeasonEpisodeCoverUrl(
            ugcSeason = success?.info?.ugc_season,
            targetBvid = normalizedBvid,
            targetCid = safeCid
        )
        if (switchedCover.isNotBlank()) {
            pendingInPageSwitchCoverUrl = switchedCover
        }
        sharedDanmakuManager.clearForVideoChange()
        presentationState.switchVideo(normalizedBvid, safeCid)
        viewModel.loadVideo(
            bvid = normalizedBvid,
            cid = safeCid,
            autoPlay = autoPlay
        )
    }

    // 导航 lambda 需要读到「点击那刻」的全屏状态，而 isFullscreenMode 在下方才计算，
    // 用 state 桥接（由 isFullscreenMode 的 LaunchedEffect 同步）。
    val relatedNavStartInFullscreen = remember { mutableStateOf(false) }
    val navigateToRelatedVideo = remember(
        onVideoClick,
        miniPlayerManager,
        uiState,
        currentBvid,
        sharedDanmakuManager,
        introListState,
        commentListState,
        selectedVideoContentTabIndex,
    ) {
        { targetBvid: String, options: android.os.Bundle? ->
            com.android.purebilibili.core.util.Logger.d("VideoInputTrace") {
                "related_click committed=$hasCommittedRelatedVideoNavigation visible=$isVisible"
            }
            val success = uiState as? VideoPlaybackUiState.Success
            val explicitCid = options?.getLong(VIDEO_NAV_TARGET_CID_KEY) ?: 0L
            val resolvedCid = resolveNavigationTargetCid(
                targetBvid = targetBvid,
                explicitCid = explicitCid,
                relatedVideos = success?.related.orEmpty(),
                ugcSeason = success?.info?.ugc_season
            )
            com.android.purebilibili.core.util.Logger.d(
                "VideoDetailScreen",
                "navigateToRelatedVideo: current=${success?.info?.bvid ?: "unknown"} target=$targetBvid explicitCid=$explicitCid resolvedCid=$resolvedCid"
            )
            if (
                shouldSwitchCollectionVideoInsideCurrentDetailPage(
                    targetBvid = targetBvid,
                    currentBvid = success?.info?.bvid ?: currentBvid,
                    ugcSeason = success?.info?.ugc_season
                )
            ) {
                miniPlayerManager?.isNavigatingToVideo = false
                switchVideoInCurrentDetailPage(
                    targetBvid = targetBvid,
                    targetCid = resolvedCid,
                    autoPlay = true
                )
            } else {
                if (!hasCommittedRelatedVideoNavigation) {
                    relatedParentWasCovered = false
                    relatedParentUiSnapshot = captureVideoDetailParentUiSnapshot(
                        selectedTabIndex = selectedVideoContentTabIndex,
                        introListState = introListState,
                        commentListState = commentListState,
                    )
                    val parentBvid = success?.info?.bvid ?: currentBvid
                    // 全屏中跳下一个视频：新详情页继承全屏意图与目标封面，避免中途掉出全屏、
                    // 也避免首帧前无封面承接。同页切集由上面的分支处理，不受这里影响。
                    val navOptions = buildVideoNavigationOptions(
                        base = options,
                        targetCid = resolvedCid,
                        coverUrl = resolveNavigationTargetCoverUrl(
                            targetBvid = targetBvid,
                            relatedVideos = success?.related.orEmpty(),
                            ugcSeason = success?.info?.ugc_season,
                            targetCid = resolvedCid,
                        ),
                        startInFullscreen = relatedNavStartInFullscreen.value,
                    )

                    // 先摘掉父详情壳 sharedBounds，再 push，避免相关卡嵌套在父壳内吃不到 morph。
                    // 只保留轻量 UI 位置快照；整屏位图捕获会把导航提交阻塞数百毫秒。
                    sharedDanmakuManager.clearForVideoChange()
                    hasCommittedRelatedVideoNavigation = true
                    presentationState.markNavigatingToVideo()
                    miniPlayerManager?.isNavigatingToVideo = true
                    markSecondaryNavigationLeave(expectedBvid = parentBvid)
                    onVideoClick(targetBvid, navOptions)
                }
                Unit
            }
        }
    }

    LaunchedEffect(
        hasCommittedRelatedVideoNavigation,
        isVisible,
        relatedParentWasCovered,
        relatedParentUiSnapshot,
        videoCardDepthBackgroundState,
        transitionEnterDurationMillis,
    ) {
        if (!hasCommittedRelatedVideoNavigation) return@LaunchedEffect
        if (!isVisible) {
            // Restore while the child fully covers this parent. The next back preview therefore
            // starts from the click-time list geometry instead of revealing a reset live layout
            // underneath the retained navigation snapshot.
            if (!relatedParentWasCovered) {
                relatedParentUiSnapshot?.let { parentUi ->
                    presentationState.selectTab(parentUi.selectedTabIndex)
                    restoreVideoDetailParentUiSnapshot(
                        snapshot = parentUi,
                        introListState = introListState,
                        commentListState = commentListState,
                        pagerState = videoContentPagerState,
                    )
                }
            }
            relatedParentWasCovered = true
            return@LaunchedEffect
        }
        if (!relatedParentWasCovered) {
            // Navigation may be rejected after the click; release the pending parent state.
            kotlinx.coroutines.delay(transitionEnterDurationMillis.coerceAtLeast(0).toLong() + 96L)
            if (isVisible && !relatedParentWasCovered) {
                hasCommittedRelatedVideoNavigation = false
                relatedParentUiSnapshot = null
            }
            return@LaunchedEffect
        }

        // The parent can become the top key before Miuix has finished the return morph. Keep the
        // navigation-leave guards until the flying layer has completely parked.
        snapshotFlow {
            canReleaseRelatedVideoNavigation(
                phase = videoCardDepthBackgroundState.phaseProvider(),
                gestureInProgress = videoCardDepthBackgroundState.isReturnGestureInProgressProvider(),
                gestureRestoreInProgress = videoCardDepthBackgroundState.isGestureRestoreInProgressProvider(),
            )
        }.first { it }

        // Let the already-restored parent produce one live frame before reactivating its player.
        withFrameNanos { }
        if (isVisible) {
            hasCommittedRelatedVideoNavigation = false
            relatedParentWasCovered = false
            relatedParentUiSnapshot = null
        }
    }

    LaunchedEffect(bvid, cid) {
        com.android.purebilibili.core.util.Logger.d(
            "VideoDetailScreen",
            "SUB_DBG screen entry args: bvid=$bvid, cid=$cid"
        )
    }

    val openCommentUrl: (String) -> Unit = openCommentUrl@{ rawUrl ->
        val url = rawUrl.trim()
        if (url.isEmpty()) return@openCommentUrl
        if (onOpenBilibiliLink != null) {
            onOpenBilibiliLink(url)
            return@openCommentUrl
        }

        when (val target = resolveCommentUrlNavigationTarget(url)) {
            is CommentUrlNavigationTarget.Video -> {
                navigateToRelatedVideo(target.videoId, null)
                return@openCommentUrl
            }

            is CommentUrlNavigationTarget.Search -> {
                navigateToSearchKeywordFromVideo(target.keyword)
                return@openCommentUrl
            }

            is CommentUrlNavigationTarget.Space -> {
                navigateToUserSpaceFromVideo(target.mid)
                return@openCommentUrl
            }

            null -> Unit
        }

        runCatching { uriHandler.openUri(url) }
    }

    // 进场以动画为主：导航/shell morph 期间不挂载手机正文等重型内容。
    // loadVideo 立即发起按需网络请求，媒体 prepare 才等待 entryTransitionFinished。
    val entryVisualEnabled = transitionEnabled && !deferVideoDetailEntryLoad
    // entry 生命周期内只允许完成一次；返回标志或 defer 变化不能重建状态并隐藏已加载正文。
    var isTransitionFinished by remember {
        mutableStateOf(
            when {
                deferVideoDetailEntryLoad -> false
                !transitionEnabled -> true
                else -> false
            }
        )
    }
    val entryVisualProgress = remember(entryVisualEnabled) {
        Animatable(if (entryVisualEnabled) 0f else 1f)
    }
    val detailInfoRevealProgress = remember(transitionEnabled) {
        Animatable(if (transitionEnabled) 0f else 1f)
    }

    LaunchedEffect(transitionEnabled, motionSpec.entryPhaseDurationMillis, entryHeroDriver) {
        if (!transitionEnabled || entryHeroDriver != null) {
            detailInfoRevealProgress.snapTo(1f)
        } else {
            detailInfoRevealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = motionSpec.entryPhaseDurationMillis.coerceAtLeast(1),
                    delayMillis = 48,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    LaunchedEffect(
        deferVideoDetailEntryLoad,
        entryTransitionFinished,
        entryVisualEnabled,
        motionSpec.entryPhaseDurationMillis,
        entryHeroDriver,
        transitionEnabled
    ) {
        when {
            entryHeroDriver != null -> {
                if (entryTransitionFinished) {
                    entryVisualProgress.snapTo(1f)
                    isTransitionFinished = true
                }
            }
            deferVideoDetailEntryLoad -> {
                // 只允许 true 锁存：相关推荐返回的二次 morph 不得把内容区重新藏起来。
                if (entryTransitionFinished) {
                    entryVisualProgress.snapTo(1f)
                    isTransitionFinished = true
                } else if (!isTransitionFinished) {
                    entryVisualProgress.snapTo(0f)
                }
            }

            !transitionEnabled -> {
                entryVisualProgress.snapTo(1f)
                isTransitionFinished = true
            }

            else -> {
                if (isTransitionFinished) {
                    entryVisualProgress.snapTo(1f)
                    return@LaunchedEffect
                }
                entryVisualProgress.snapTo(0f)
                entryVisualProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = motionSpec.entryPhaseDurationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
                isTransitionFinished = true
            }
        }
    }

    //  监听评论状态
    val commentState by commentViewModel.commentState.collectAsStateWithLifecycle()
    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    var subReplyCoveredBlurProgress by remember { mutableFloatStateOf(0f) }
    // 楼中楼是正文的兄弟覆盖层，必须显式复用播放器滚动连接；其他弹层不参与。
    var commentThreadPlayerScrollConnection by remember {
        mutableStateOf<NestedScrollConnection?>(null)
    }

    LaunchedEffect(
        openCommentRootRpidFromRoute,
        openCommentTargetRpidFromRoute,
        commentState.replies,
        commentState.isRepliesLoading,
        subReplyState.visible
    ) {
        if (openCommentRootRpidFromRoute <= 0L || hasHandledCommentRootFromRoute || subReplyState.visible) {
            return@LaunchedEffect
        }

        val rootReply = commentState.replies.firstOrNull { it.rpid == openCommentRootRpidFromRoute }
        if (rootReply != null) {
            commentViewModel.openSubReply(rootReply, openCommentTargetRpidFromRoute)
            hasHandledCommentRootFromRoute = true
        } else if (!commentState.isRepliesLoading) {
            val openStarted = commentViewModel.openSubReplyFromRoute(
                rootReplyId = openCommentRootRpidFromRoute,
                targetReplyId = openCommentTargetRpidFromRoute
            )
            if (openStarted) {
                hasHandledCommentRootFromRoute = true
            }
        }
    }
    val commentDefaultSortMode by com.android.purebilibili.core.store.SettingsManager
        .getCommentDefaultSortMode(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.SettingsManager.getCommentDefaultSortModeSync(context),
            lifecycle = lifecycleOwner.lifecycle
        )
    val commentFraudDetectionEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCommentFraudDetectionEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = true,
            lifecycle = lifecycleOwner.lifecycle
        )
    val commentMemberDecorationsEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCommentMemberDecorationsEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = false,
            lifecycle = lifecycleOwner.lifecycle
        )
    val homeSettings by com.android.purebilibili.core.store.SettingsManager
        .getHomeSettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.HomeSettings(),
            lifecycle = lifecycleOwner.lifecycle
        )
    val tabletCommentPanelWidthPreset by com.android.purebilibili.core.store.SettingsManager
        .getTabletCommentPanelWidthPreset(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.TabletCommentPanelWidthPreset.STANDARD,
            lifecycle = lifecycleOwner.lifecycle
        )
    val videoAiSummaryEntryEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoAiSummaryEntryEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true, lifecycle = lifecycleOwner.lifecycle)
    val videoNoteEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoNoteEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true, lifecycle = lifecycleOwner.lifecycle)
    val videoNoteDefaultCollapsed by com.android.purebilibili.core.store.SettingsManager
        .getVideoNoteDefaultCollapsed(context)
        .collectAsStateWithLifecycle(initialValue = true, lifecycle = lifecycleOwner.lifecycle)
    val preferredCommentSortMode = remember(commentDefaultSortMode) {
        CommentSortMode.fromApiMode(commentDefaultSortMode)
    }
    VideoDetailCommentFraudOverlayAdapter(
        context = context,
        playbackViewModel = viewModel,
        commentViewModel = commentViewModel,
        aid = detailContentState?.info?.aid,
        fraudDetectionEnabled = commentFraudDetectionEnabled,
    )
    val sortPreferenceScope = rememberCoroutineScope()
    val danmakuEnabledForDetail by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuEnabled(
            context,
            com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT
        )
        .collectAsStateWithLifecycle(
            initialValue = true,
            lifecycle = lifecycleOwner.lifecycle
        )
    val showFavoriteFolderDialog by viewModel.favoriteFolderDialogVisible.collectAsStateWithLifecycle()
    val showCommentInput by viewModel.showCommentDialog.collectAsStateWithLifecycle()
    // [Blur] Haze State
    val hazeState = rememberRecoverableHazeState()

    val sponsorSegment by viewModel.currentSponsorSegment.collectAsStateWithLifecycle()
    val showSponsorSkipButton by viewModel.showSkipButton.collectAsStateWithLifecycle()
    val sponsorContributionState by viewModel.sponsorContributionUiState.collectAsStateWithLifecycle()

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isFullscreenPlayerLocked = AppScreenshotGestureBlockState.fullscreenPlayerLocked

    val interactiveChoicePanel by viewModel.interactiveChoicePanel.collectAsStateWithLifecycle()

    // 📐 大屏与分离式折叠姿态使用分栏；紧凑高度强制回退单栏。
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val appWindowAdaptiveInfo =
        com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current
    val displayContext = appWindowAdaptiveInfo.displayContext
    val horizontalAdaptationEnabled by com.android.purebilibili.core.store.SettingsManager
        .getHorizontalAdaptationEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = windowSizeClass.isTabletDevice || displayContext.isKnownFoldableDevice,
            lifecycle = lifecycleOwner.lifecycle
        )
    val immersiveVideoPageStatusBar by com.android.purebilibili.core.store.SettingsManager
        .getHideVideoPageStatusBar(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.SettingsManager
                .getHideVideoPageStatusBarSync(context),
            lifecycle = lifecycleOwner.lifecycle
        )
    val useTabletLayout = appWindowAdaptiveInfo.shouldAvoidHinge || (
        horizontalAdaptationEnabled && shouldUseLargeScreenVideoLayout(
            windowWidthDp = configuration.screenWidthDp.toFloat(),
            windowHeightDp = configuration.screenHeightDp.toFloat(),
            horizontalAdaptationEnabled = true,
            isFoldableCoverWindow = displayContext.isFoldableCoverWindow,
        )
    )

    val activity = remember { context.findActivity() }
    val isActivityInMultiWindowMode = activity?.let {
        isActivityInMultiWindowOrFloatingMode(
            activity = it,
            displayContext = displayContext,
        )
    } ?: displayContext.isInMultiWindowMode

    // 🔧 [修复] 追踪用户是否主动请求全屏（点击全屏按钮）
    // 使用 rememberSaveable 确保状态在横竖屏切换时保持
    // 系统小窗 / 分屏也是普通详情入口；窗口模式不代表用户请求了全屏。
    // 按详情路由会话（bvid）保存手动全屏意图：同页切集（下一集 / 合集 / 队列）仍属于同一次
    // 全屏观看，意图必须跨 currentBvid 变化保留——否则方向请求会从「手动全屏」回落到
    // SCREEN_ORIENTATION_USER，被系统/重力转回竖屏，表现为播放中「退出全屏」。
    // push 到新的详情路由才重置，保持「每次进入新视频页默认非全屏」的语义。
    var userRequestedFullscreen by rememberSaveable(bvid) {
        mutableStateOf(false)
    }
    var hasHandledStartFullscreenRequest by rememberSaveable(bvid, startInFullscreen) {
        mutableStateOf(false)
    }
    var manualPortraitHoldActive by rememberSaveable { mutableStateOf(false) }
    var preserveCurrentFrameOnFullscreenChange by remember { mutableStateOf(false) }
    var pendingFullscreenPositionRestoreMs by remember { mutableLongStateOf(-1L) }
    val playerWindowOrientationPolicy = remember(displayContext) {
        com.android.purebilibili.core.util.resolvePlayerWindowOrientationPolicy(displayContext)
    }
    val usesInWindowFullscreen = playerWindowOrientationPolicy.usesInWindowFullscreen

    // 📐 全屏模式逻辑：
    // - 紧凑窗口：横放时自动进入全屏
    // - 平板/展开态折叠屏：旋转时保留分栏，仅用户主动切换全屏
    val fullscreenMode by com.android.purebilibili.core.store.SettingsManager
        .getFullscreenMode(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.FullscreenMode.AUTO,
            lifecycle = lifecycleOwner.lifecycle
        )
    val prefersManualFullscreenMode = remember(fullscreenMode) {
        fullscreenMode == com.android.purebilibili.core.store.FullscreenMode.NONE ||
            fullscreenMode == com.android.purebilibili.core.store.FullscreenMode.VERTICAL
    }
    val playerPresentation = remember(
        displayContext,
        isLandscape,
        userRequestedFullscreen,
        prefersManualFullscreenMode,
        isActivityInMultiWindowMode,
    ) {
        com.android.purebilibili.core.util.resolvePlayerPresentationPolicy(
            displayContext = displayContext,
            isLandscape = isLandscape,
            userFullscreenIntent = userRequestedFullscreen,
            prefersManualFullscreen = prefersManualFullscreenMode,
            isInMultiWindowMode = isActivityInMultiWindowMode,
        )
    }
    val orientationPolicyDevice = playerPresentation.isOrientationDriven
    val isOrientationDrivenFullscreen = playerPresentation.isOrientationDriven
    val isFullscreenMode = resolveVideoDetailFullscreenMode(
        isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
        isLandscape = isLandscape,
        userRequestedFullscreen = userRequestedFullscreen,
        isInMultiWindowMode = isActivityInMultiWindowMode,
        manualPortraitHoldActive = manualPortraitHoldActive,
    )

    // 「下一集 / 相关视频」的导航 lambda 定义在 isFullscreenMode 之前，用 state 桥接，
    // 让它在点击时刻读到最新全屏状态：全屏中 push 新详情页必须继承全屏意图才不会掉出全屏。
    LaunchedEffect(isFullscreenMode) {
        relatedNavStartInFullscreen.value = isFullscreenMode
    }

    var previousDisplayRole by remember {
        mutableStateOf(displayContext.foldableDisplayRole)
    }
    LaunchedEffect(activity, displayContext.foldableDisplayRole, usesInWindowFullscreen) {
        val shouldReleaseLock =
            com.android.purebilibili.core.util.shouldReleaseOrientationLockOnDisplayRoleChange(
                previousRole = previousDisplayRole,
                nextRole = displayContext.foldableDisplayRole,
            )
        if (shouldReleaseLock || usesInWindowFullscreen) {
            manualPortraitHoldActive = false
            activity?.applyPlayerRequestedOrientation(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                displayContext = displayContext,
            )
        }
        previousDisplayRole = displayContext.foldableDisplayRole
    }
    LaunchedEffect(appWindowAdaptiveInfo, playerPresentation, isFullscreenMode) {
        com.android.purebilibili.core.util.Logger.d(
            "VideoDetailScreen",
            com.android.purebilibili.core.util.formatAppAdaptiveStrategySnapshot(
                appWindowAdaptiveInfo.toAdaptiveStrategySnapshot(
                    playerPresentation = if (playerPresentation.usesInWindowFullscreen) {
                        "in-window(user=${playerPresentation.userFullscreenIntent}," +
                            "fullscreen=$isFullscreenMode)"
                    } else if (
                        isFullscreenMode && playerPresentation.orientationGeneratedFullscreen
                    ) {
                        "orientation-generated"
                    } else if (isFullscreenMode) {
                        "user-fullscreen"
                    } else {
                        "inline"
                    },
                )
            ),
        )
    }
    val canShowLandscapeComments = isFullscreenMode && isLandscape && !isPipMode && !isPortraitFullscreen
    LaunchedEffect(canShowLandscapeComments) {
        if (!canShowLandscapeComments) landscapeCommentPanelVisible = false
    }
    // The navigation rail may retain its slot for card return geometry, but immersive playback
    // must receive the entire window. Pair the route-owned request with disposal cleanup.
    val isImmersivePlayback = isFullscreenMode || isPortraitFullscreen || isPipMode
    DisposableEffect(onImmersivePlaybackChanged, isImmersivePlayback) {
        onImmersivePlaybackChanged(isImmersivePlayback)
        onDispose { onImmersivePlaybackChanged(false) }
    }
    ManualFullscreenRequestLifecycleEffect(
        manualFullscreenRequested = userRequestedFullscreen,
        isFullscreenMode = isFullscreenMode,
        requestEnvironmentKey = isOrientationDrivenFullscreen,
        onReleaseManualFullscreenRequest = { userRequestedFullscreen = false }
    )
    LaunchedEffect(isFullscreenMode) {
        if (preserveCurrentFrameOnFullscreenChange) {
            preserveCurrentFrameOnFullscreenChange = false
        }
    }
    var previousPipMode by remember { mutableStateOf(isInPipMode) }
    LaunchedEffect(isInPipMode) { presentationState.syncPipMode(isInPipMode) }
    LaunchedEffect(isPipMode, subReplyState.visible) {
        val shouldDismissThreadDetail = shouldDismissCommentThreadDetailForPip(
            wasInPipMode = previousPipMode,
            isInPipMode = isPipMode,
            subReplyVisible = subReplyState.visible
        )
        previousPipMode = isPipMode
        if (shouldDismissThreadDetail) {
            commentViewModel.closeSubReply()
        }
    }
    val openFavoriteFolders: (VideoFavoriteEntryPoint, Boolean) -> Unit = { entryPoint, isLongPress ->
        when (
            resolveVideoFavoriteAction(
                entryPoint = entryPoint,
                isLongPress = isLongPress,
                quickSaveDefaultFolder = favoriteQuickSaveDefaultFolder,
            )
        ) {
            VideoFavoriteAction.ToggleFavorite -> engagementViewModel.toggleFavorite()
            VideoFavoriteAction.OpenFavoriteFolders -> viewModel.showFavoriteFolderDialog()
        }
    }

    //  [新增] 监听定时关闭状态
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()

    // 📖 [新增] 监听视频章节数据
    // 📖 [新增] 监听视频章节数据
    val viewPoints by viewModel.viewPoints.collectAsStateWithLifecycle()
    val pbpProgressData by viewModel.pbpProgressData.collectAsStateWithLifecycle()
    val progressPeakDanmakuEnabled by com.android.purebilibili.core.store.SettingsManager
        .getProgressPeakDanmakuEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val visiblePbpProgressData = pbpProgressData.takeIf { progressPeakDanmakuEnabled }
    val sponsorProgressMarkers by viewModel.sponsorProgressMarkers.collectAsStateWithLifecycle()

    // [New] Codec & Audio Preferences
    val codecPreference by viewModel.videoCodecPreference.collectAsStateWithLifecycle()
    val secondCodecPreference by viewModel.videoSecondCodecPreference.collectAsStateWithLifecycle()
    val audioQualityPreference by viewModel.audioQualityPreference.collectAsStateWithLifecycle()

    //  [PiP修复] 记录视频播放器在屏幕上的位置，用于PiP窗口只显示视频区域
    var videoPlayerBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var videoPlayerRootBottomPx by remember { mutableIntStateOf(0) }
    val returnMediaGeometry = remember(currentBvid) { VideoDetailReturnMediaGeometry() }

    // 📱 [优化] isPortraitFullscreen 和 isVerticalVideo 现在从 playerState 获取（见 playerState 定义后）

    // 🔁 [优化] 合并播放队列状态订阅，减少同帧多次重组
    val playlistUiState by PlaylistManager.uiState.collectAsStateWithLifecycle(
        initialValue = PlaylistUiState(),
        lifecycle = lifecycleOwner.lifecycle
    )
    val currentPlayMode = playlistUiState.playMode
    val playlistItems = playlistUiState.playlist
    val playlistCurrentIndex = playlistUiState.currentIndex
    val isExternalPlaylist = playlistUiState.isExternalPlaylist
    val externalPlaylistSource = playlistUiState.externalPlaylistSource
    val shouldShowExternalPlaylistQueueBar = shouldShowExternalPlaylistQueueBarByPolicy(
        isExternalPlaylist = isExternalPlaylist,
        externalPlaylistSource = externalPlaylistSource,
        playlistSize = playlistItems.size
    )
    val externalPlaylistQueueTitle = resolveExternalPlaylistQueueTitle(externalPlaylistSource)
    var showExternalPlaylistQueueSheet by rememberSaveable { mutableStateOf(false) }
    val externalPlaylistQueueSheetPresentation = remember {
        resolveExternalPlaylistQueueSheetPresentation(requireRealtimeHaze = true)
    }

    LaunchedEffect(shouldShowExternalPlaylistQueueBar) {
        if (!shouldShowExternalPlaylistQueueBar) {
            showExternalPlaylistQueueSheet = false
        }
    }

    LaunchedEffect(startAudioFromRoute, hasAutoEnteredAudioMode, uiState) {
        if (shouldAutoEnterAudioModeFromRoute(
                startAudioFromRoute = startAudioFromRoute,
                hasAutoEnteredAudioMode = hasAutoEnteredAudioMode,
                isVideoLoadSuccess = uiState is VideoPlaybackUiState.Success
            )
        ) {
            hasAutoEnteredAudioMode = true
            presentationState.markNavigatingToAudioMode()
            viewModel.setAudioMode(true)
            onNavigateToAudioMode()
        }
    }

    //  路由请求只负责本次视频入口的初始方向。横屏切回竖屏后不能再次把它当成
    //  新请求，否则 startInFullscreen 会和用户的退出操作互相触发方向切换。
    //  守卫与 effect 都按详情路由会话（bvid）键控：同页切集不该重放入口的方向请求。
    LaunchedEffect(
        startInFullscreen,
        bvid,
    ) {
        if (!startInFullscreen || hasHandledStartFullscreenRequest) return@LaunchedEffect
        if (!isOrientationDrivenFullscreen) {
            userRequestedFullscreen = true
            hasHandledStartFullscreenRequest = true
        } else {
            context.findActivity()?.let { activity ->
                if (!shouldApplyStartFullscreenOrientationRequest(
                        startInFullscreen = startInFullscreen,
                        isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
                        isLandscape = isLandscape
                    )
                ) {
                    // 已经处于横屏（含分屏 / 小窗横屏）：方向无需再请求，但这次全屏意图必须记下来，
                    // 否则方向策略会回落 SCREEN_ORIENTATION_USER 把页面转回竖屏。
                    userRequestedFullscreen = true
                    hasHandledStartFullscreenRequest = true
                    return@let
                }
                activity.applyPlayerRequestedOrientation(
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    displayContext = displayContext,
                )
                hasHandledStartFullscreenRequest = true
            }
        }
    }

    //  用于跟踪组件是否正在退出，防止 SideEffect 覆盖恢复操作
    var isScreenActive by rememberSaveable(currentBvid) { mutableStateOf(true) }

    //  [关键] 保存进入前的状态栏配置（在 DisposableEffect 外部定义以便复用）
    val window = remember { activity?.window }
    var entryRequestedOrientation by rememberSaveable {
        mutableIntStateOf(
            resolveVideoDetailEntryOrientationSnapshot(
                currentRequestedOrientation = activity?.requestedOrientation
            )
        )
    }
    val insetsController = remember {
        if (window != null && activity != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null
    }
    var originalSystemBarsSnapshot by remember(window, insetsController) {
        mutableStateOf(
            resolveVideoDetailSystemBarsSnapshot(
                statusBarColor = window?.statusBarColor,
                navigationBarColor = window?.navigationBarColor,
                lightStatusBars = insetsController?.isAppearanceLightStatusBars,
                lightNavigationBars = insetsController?.isAppearanceLightNavigationBars,
                systemBarsBehavior = insetsController?.systemBarsBehavior,
                fallbackColor = android.graphics.Color.TRANSPARENT,
                fallbackLightBars = true,
                fallbackSystemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            )
        )
    }
    //  [修复] 系统栏可见期间持续刷新快照：此前只在首次组合捕获一次，若用户在
    //  沉浸播放期间切换深浅主题，退出时会恢复到陈旧的图标明暗/栏色。
    //  仅在系统栏可见时刷新，避免把沉浸期间被本页隐藏的状态当作"原始状态"。
    SideEffect {
        val controller = insetsController
        val hostWindow = window
        if (controller == null || hostWindow == null) return@SideEffect
        val systemBarsVisible = ViewCompat.getRootWindowInsets(hostWindow.decorView)
            ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
        if (!systemBarsVisible) return@SideEffect
        originalSystemBarsSnapshot = resolveVideoDetailSystemBarsSnapshot(
            statusBarColor = hostWindow.statusBarColor,
            navigationBarColor = hostWindow.navigationBarColor,
            lightStatusBars = controller.isAppearanceLightStatusBars,
            lightNavigationBars = controller.isAppearanceLightNavigationBars,
            systemBarsBehavior = controller.systemBarsBehavior,
            fallbackColor = android.graphics.Color.TRANSPARENT,
            fallbackLightBars = true,
            fallbackSystemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        )
    }

    //  [新增] 恢复状态栏的函数（可复用）
    val restoreStatusBar = remember {
        {
            if (window != null && insetsController != null) {
                if (shouldShowSystemBarsOnVideoDetailExit()) {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
                insetsController.systemBarsBehavior = originalSystemBarsSnapshot.systemBarsBehavior
                insetsController.isAppearanceLightStatusBars = originalSystemBarsSnapshot.lightStatusBars
                insetsController.isAppearanceLightNavigationBars = originalSystemBarsSnapshot.lightNavigationBars
                setWindowStatusBarColor(window, originalSystemBarsSnapshot.statusBarColor)
                setWindowNavigationBarColor(window, originalSystemBarsSnapshot.navigationBarColor)
            }
        }
    }

    //  [修复] 包装的 onBack，在导航之前立即恢复状态栏并通知小窗管理器
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnHomeClick by rememberUpdatedState(onHomeClick)
    val latestOnMarkReturningFromDetail by rememberUpdatedState(onMarkReturningFromDetail)
    val topBarActionHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var pendingTopBarActionRunnable by remember { mutableStateOf<Runnable?>(null) }
    var isActuallyLeaving by rememberSaveable(currentBvid) { mutableStateOf(false) }
    var forceCoverOnlyOnReturn by remember { mutableStateOf(false) }
    var systemBarsReapplyGeneration by remember(currentBvid) { mutableIntStateOf(0) }
    val transitionState = rememberVideoDetailTransitionState(
        bvid = bvid,
        sourceRoute = sourceRouteForSharedElement,
        transitionEnabled = transitionEnabled,
        keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
        motionSpec = homeSharedTransitionMotionSpec,
        routeSheetMotion = routeSheetMotion,
    )
    val rootAnimatedVisibilityScope = transitionState.animatedVisibilityScope
    val rootSharedTransitionScope = transitionState.sharedTransitionScope
    val entryOwnsMiuixCardTransition = transitionState.entryOwnsMiuixCardTransition
    val isExitTransitionInProgress = transitionState.isExitTransitionInProgress
    val detailShellSharedBoundsEnabled = transitionState.detailShellSharedBoundsEnabled
    val suppressEnterFadeAfterBackPreview = transitionState.suppressEnterFadeAfterBackPreview
    val detailTransitionProgress = transitionState.progress
    val detailChildTransitionEnabled = transitionState.detailChildTransitionEnabled
    val coverSharedBoundsActive = transitionState.coverSharedBoundsActive
    val sharedBoundsActive = transitionState.sharedBoundsActive
    val routeSheetFrameProvider = transitionState.routeSheetFrameProvider
    val detailShellShape = remember(sharedTransitionSourceCornerDp) {
        RoundedCornerShape(sharedTransitionSourceCornerDp.dp)
    }
    LaunchedEffect(isNavigatingToVideo, homeSharedTransitionMotionSpec.durationMillis, scopedHeroDriver) {
        if (!isNavigatingToVideo) return@LaunchedEffect
        // 进场 morph 结束后恢复父壳，避免长期禁用导致再回列表时丢 shell。
        if (scopedHeroDriver.enabled) {
            snapshotFlow {
                videoCardDepthBackgroundState.phaseProvider() == VideoCardTransitionBackgroundPhase.IDLE ||
                    (videoCardDepthBackgroundState.phaseProvider() == VideoCardTransitionBackgroundPhase.HELD &&
                        !videoCardDepthBackgroundState.isGestureRestoreInProgressProvider() &&
                        !scopedHeroDriver.isGestureInProgressProvider() && scopedHeroDriver.progressProvider() >= .999f)
            }.first { it }
        } else {
            kotlinx.coroutines.delay(homeSharedTransitionMotionSpec.durationMillis.toLong() + 48L)
        }
        if (isNavigatingToVideo) {
            presentationState.clearNavigatingToVideo()
        }
    }
    val detailShellModifier = Modifier.videoCardShellSharedBoundsOrEmpty(
        enabled = detailShellSharedBoundsEnabled &&
            !isNavigatingToVideo,
        sharedTransitionScope = rootSharedTransitionScope,
        animatedVisibilityScope = rootAnimatedVisibilityScope,
        bvid = bvid,
        sourceRoute = sourceRouteForSharedElement,
        motionSpec = homeSharedTransitionMotionSpec,
        clipShape = detailShellShape,
        role = VideoCardShellSharedBoundsRole.DetailShell,
        // 竖屏全屏（点赞/关注/发弹幕那套）：整卡展开到全屏，不要按顶部横屏播放器 TopCenter 落点。
        fillFullscreenShell = isPortraitFullscreen ||
            shouldStartInPortraitFullscreenFromRouteHint(
                autoEnterPortraitFromRoute = autoEnterPortraitFromRoute,
                startAudioFromRoute = startAudioFromRoute,
                initialVerticalFromRoute = initialVerticalFromRoute,
                directPortraitEntryFromRoute = directPortraitEntryFromRoute,
            ),
    )
    val coverTakeoverBeforeBackDelayMillis = remember {
        resolveCoverTakeoverDelayBeforeBackNavigationMillis()
    }
    // 仅当详情页自身正在回收到来源卡片时进入离开态。详情页作为上层页面的
    // 直接返回目标时必须保留已加载的播放器与内容，避免预测返回预览变成封面占位。
    val isCardReturnExitInProgress = shouldTreatVideoDetailCardExitAsReturning(
        isExitTransitionInProgress = isExitTransitionInProgress,
        sharedBoundsActive = sharedBoundsActive,
        keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
        // Nested detail entries keep their own AnimatedVisibility scope. The retained parent can
        // therefore still report PostExit for one frame after the child is popped; only the entry
        // whose source route owns the active card session may interpret that as a card return.
        entryOwnsCardTransition = entryOwnsMiuixCardTransition,
    )
    val forceCoverOnlyForReturn = resolveForceCoverOnlyForReturn(
        forceCoverOnlyOnReturn = forceCoverOnlyOnReturn,
        transitionEnabled = transitionEnabled,
        isCardReturnExitInProgress = isCardReturnExitInProgress
    )
    // 离开态：次要内容淡出等。ImmediatePlayback live morph 时不把视觉交给常驻封面。
    // 注意：预测 seek 中 isCardReturnExitInProgress 会为 true，但这不是「已提交」。
    val isSessionReturningToCard = shouldConsumeMiuixReturnSessionForVideoDetailEntry(
        entryOwnsMiuixCardTransition = entryOwnsMiuixCardTransition,
        isReturningFromDetail = isReturningFromDetail,
        transitionEnabled = transitionEnabled,
        sharedBoundsActive = sharedBoundsActive,
        keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
    )
    val useReturningVideoDetailVisualState = shouldUseReturningVideoDetailVisualState(
        forceCoverOnlyForReturn = forceCoverOnlyForReturn,
        isCardReturnExitInProgress = isCardReturnExitInProgress,
        isSessionReturningToCard = isSessionReturningToCard,
    )
    // 已提交信号继续驱动正文等次要内容；封面/播放器交接还会直接读取
    // Miuix 预测返回手势，因此 seek 期间就能随进度切换到封面。
    val isCommittedCardReturn = shouldTreatVideoDetailCardReturnAsCommitted(
        isActuallyLeaving = isActuallyLeaving,
        isSessionReturningToCard = isSessionReturningToCard,
    )
    val miuixVisualAssetsActive = shouldConsumeMiuixTransitionVisualAssets(
        entryOwnsMiuixCardTransition = entryOwnsMiuixCardTransition,
        phase = videoCardDepthBackgroundState.phaseProvider(),
        isReturnGestureInProgress =
            videoCardDepthBackgroundState.isReturnGestureInProgressProvider(),
        isGestureRestoreInProgress =
            videoCardDepthBackgroundState.isGestureRestoreInProgressProvider(),
    )
    val miuixCoverSnapshot =
        com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState.current
            .sourceChromeSnapshot
            .takeIf { miuixVisualAssetsActive }
    val clickCoverSnapshot =
        com.android.purebilibili.core.util.CardPositionManager.lastClickedVideoSourceChromeSnapshot
            .takeIf { miuixVisualAssetsActive }
    val homePrefetchCover = remember(bvid) {
        com.android.purebilibili.core.util.HomeCoverReturnPrefetchRegistry.snapshot()
            .firstOrNull { it.bvid == bvid.trim() }
    }
    val residentCoverSource = remember(
        miuixCoverSnapshot,
        clickCoverSnapshot,
        homePrefetchCover,
        coverUrl,
        bvid,
    ) {
        resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = miuixCoverSnapshot,
            clickChromeSnapshot = clickCoverSnapshot,
            prefetchUrl = homePrefetchCover?.url,
            prefetchCacheKey = homePrefetchCover?.cacheKey,
            prefetchDecodeWidthPx = homePrefetchCover?.decodeWidthPx ?: 0,
            prefetchDecodeHeightPx = homePrefetchCover?.decodeHeightPx ?: 0,
            routeCoverUrl = coverUrl,
            bvid = bvid,
        )
    }
    val hasResidentReturnCover = residentCoverSource != null
    var hasDecodedResidentReturnCover by remember(residentCoverSource) {
        mutableStateOf(false)
    }
    val detailContentReadyForLiveReturnMorph = shouldTreatVideoDetailContentReadyForLiveReturnMorph(
        hasSuccessfulDetailContent = uiState is VideoPlaybackUiState.Success,
    )
    // liveReturnMorph / ownership 依赖 player 首帧，见 playerState 定义之后。

    val handleTopBarAction = remember(
        miniPlayerManager,
        currentBvid,
        coverTakeoverBeforeBackDelayMillis,
        topBarActionHandler,
        detailShellSharedBoundsEnabled,
    ) {
        action@{ action: VideoDetailTopBarAction ->
            if (isActuallyLeaving) return@action
            isActuallyLeaving = true // 标记确实是用户通过点击或返回键离开
            isScreenActive = false  // 标记页面正在退出
            latestOnMarkReturningFromDetail()
            // 🎯 通知小窗管理器这是用户主动导航离开。
            // 有 shell sharedBounds 时延后停播，避免一镜到底落位前 surface 被掐掉。
            miniPlayerManager?.markLeavingByNavigation(
                expectedBvid = currentBvid,
                deferPlaybackStop = shouldDeferPlaybackStopForSharedLiveReturn(
                    cardTransitionEnabled = detailShellSharedBoundsEnabled,
                    hasSourceRoute = true,
                ),
            )

            restoreStatusBar() // 立即恢复状态栏（动画开始前）
            pendingTopBarActionRunnable?.let(topBarActionHandler::removeCallbacks)
            val navigationRunnable = Runnable {
                pendingTopBarActionRunnable = null
                when (action) {
                    VideoDetailTopBarAction.BACK -> latestOnBack()
                    VideoDetailTopBarAction.HOME -> latestOnHomeClick()
                }
            }
            pendingTopBarActionRunnable = navigationRunnable
            if (coverTakeoverBeforeBackDelayMillis > 0L) {
                topBarActionHandler.postDelayed(navigationRunnable, coverTakeoverBeforeBackDelayMillis)
            } else {
                navigationRunnable.run()
            }
        }
    }
    val handleBack = remember(handleTopBarAction) {
        {
            handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = false))
        }
    }

    LaunchedEffect(isExitTransitionInProgress, isActuallyLeaving) {
        if (shouldRestoreSystemBarsDuringVideoDetailExitTransition(
                isExitTransitionInProgress = isExitTransitionInProgress,
                isActuallyLeaving = isActuallyLeaving
            )
        ) {
            isScreenActive = false
            restoreStatusBar()
            return@LaunchedEffect
        }
        if (
            shouldReactivateVideoDetailSystemBarsAfterCancelledExit(
                isExitTransitionInProgress = isExitTransitionInProgress,
                isActuallyLeaving = isActuallyLeaving,
                isScreenActive = isScreenActive,
            )
        ) {
            isScreenActive = true
            systemBarsReapplyGeneration += 1
        }
    }

    var wasKeepLoadedContentForBackPreview by remember(currentBvid) {
        mutableStateOf(keepLoadedContentForBackPreview)
    }
    LaunchedEffect(keepLoadedContentForBackPreview, isActuallyLeaving) {
        if (
            shouldReapplyVideoDetailSystemBarsAfterBecomingTop(
                wasKeepLoadedContentForBackPreview = wasKeepLoadedContentForBackPreview,
                keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
                isActuallyLeaving = isActuallyLeaving,
            )
        ) {
            isScreenActive = true
            systemBarsReapplyGeneration += 1
        }
        wasKeepLoadedContentForBackPreview = keepLoadedContentForBackPreview
    }

    LaunchedEffect(predictiveBackCancelRecoveryGeneration) {
        if (predictiveBackCancelRecoveryGeneration <= 0) return@LaunchedEffect
        isScreenActive = true
        systemBarsReapplyGeneration += 1
    }

    LaunchedEffect(currentBvid) {
        pendingTopBarActionRunnable?.let(topBarActionHandler::removeCallbacks)
        pendingTopBarActionRunnable = null
        isScreenActive = true
        forceCoverOnlyOnReturn = false
        if (shouldClearStaleReturningStateOnVideoDetailEnter(isReturningFromDetail)) {
            onClearReturningFromDetail()
        }
    }

    DisposableEffect(lifecycleOwner, currentBvid) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                pendingTopBarActionRunnable?.let(topBarActionHandler::removeCallbacks)
                pendingTopBarActionRunnable = null
                isScreenActive = true
                forceCoverOnlyOnReturn = false
                if (shouldClearStaleReturningStateOnVideoDetailEnter(isReturningFromDetail)) {
                    onClearReturningFromDetail()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 🔄 自动横竖屏：紧凑窗口驱动全屏，大屏只自适应旋转并保留分栏。
    val autoRotateEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAutoRotateEnabled(context).collectAsStateWithLifecycle(
            initialValue = false,
            lifecycle = lifecycleOwner.lifecycle
        )
    val rotationResolver = context.applicationContext.contentResolver
    var systemAutoRotateEnabled by remember(rotationResolver) {
        mutableStateOf(
            Settings.System.getInt(rotationResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
        )
    }
    DisposableEffect(rotationResolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemAutoRotateEnabled = Settings.System.getInt(
                    rotationResolver, Settings.System.ACCELEROMETER_ROTATION, 0
                ) != 0
            }
        }
        rotationResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer
        )
        observer.onChange(false)
        onDispose { rotationResolver.unregisterContentObserver(observer) }
    }
    val sensorAutoRotateEnabled = autoRotateEnabled && systemAutoRotateEnabled

    DisposableEffect(Unit) {
        //  [沉浸式] 启用边到边显示，让内容延伸到状态栏下方
        if (window != null) {
            AppWindowSystemUiController.ensureEdgeToEdge(window)
        }

        onDispose {
            pendingTopBarActionRunnable?.let(topBarActionHandler::removeCallbacks)
            pendingTopBarActionRunnable = null
            //  [关键] 标记页面正在退出，防止 SideEffect 覆盖
            isScreenActive = false

            // ⚡ [性能优化] Phase 1: 同步执行 — 仅保留影响视觉的关键操作
            val layoutParams = window?.attributes
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window?.attributes = layoutParams
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            restoreStatusBar()

            // ⚡ [性能优化] Phase 1b: CardPositionManager 状态（影响首页卡片动画，必须同步）
            val shouldHandleAsNavigationExit = shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = isNavigatingToAudioMode,
                isNavigatingToMiniMode = isNavigatingToMiniMode,
                isMiniModeActive = miniPlayerManager?.isMiniMode == true,
                isChangingConfigurations = activity?.isChangingConfigurations == true,
                isNavigatingToVideo = resolveIsNavigatingToVideoDuringDispose(
                    localNavigatingToVideo = isNavigatingToVideo,
                    managerNavigatingToVideo = miniPlayerManager?.isNavigatingToVideo == true
                )
            )
            if (shouldMarkReturningStateOnVideoDetailDispose(shouldHandleAsNavigationExit)) {
                onMarkReturningFromDetail()
            } else {
                onClearReturningFromDetail()
            }

            // ⚡ [性能优化] Phase 2: 延迟执行 — 非视觉的系统调用推迟到下一帧
            // 通知清理、Service 停止、屏幕方向恢复等操作不影响退出动画
            // 将它们 post 到主线程 Handler，在导航转场动画完成后再执行
            val deferredActivity = activity
            val deferredContext = context
            val deferredShouldHandleAsNavExit = shouldHandleAsNavigationExit
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // 🔕 通知清理 + Service 停止
                if (deferredShouldHandleAsNavExit && miniPlayerManager?.isActive != true) {
                    val notificationManager = deferredContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                    notificationManager.cancel(1001)
                    notificationManager.cancel(PlaybackService.NOTIFICATION_ID)
                    try {
                        deferredContext.startService(
                            android.content.Intent(deferredContext, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_STOP_FOREGROUND
                            }
                        )
                    } catch (_: Exception) {}
                }

                // 恢复进入详情页前的方向请求，避免平板误横屏后退不回去。
                deferredActivity?.applyPlayerRequestedOrientation(
                    resolveVideoDetailExitRequestedOrientation(
                        originalRequestedOrientation = entryRequestedOrientation
                    )
                )
            }
        }
    }

    val playbackEventState = rememberVideoDetailPlaybackEventState(currentBvid)
    VideoDetailPlaybackEventEffects(
        context = context,
        viewModel = viewModel,
        state = playbackEventState,
    )
    val danmakuManager = playbackEventState.danmakuManager

    //  [PiP修复] 当视频播放器位置更新时，同步更新PiP参数
    //  [修复] 只有支持系统 PiP 的模式才启用自动进入 PiP
    val pipModeEnabled = remember {
        com.android.purebilibili.core.store.SettingsManager.getMiniPlayerModeSync(context)
            .supportsSystemPip
    }
    // Card entrance motion is independent of action feedback (and defaults off).
    val isReducedActionMotion = com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()

    // Only the visible detail owns activity PiP parameters. Covered pages must not
    // disable the current mini/audio session when their player pauses or disposes.
    if (isVisible && miniPlayerManager?.isMiniMode != true && !isNavigatingToAudioMode) {
        VideoDetailPipParamsEffect(
            context = context,
            activity = activity,
            playerBounds = videoPlayerBounds,
            pipModeEnabled = pipModeEnabled,
            player = miniPlayerManager?.player,
        )
    }

    // 📱 [修复] 提升竖屏全屏状态到 Screen 级别，防止 VideoPlayerState 重建时状态丢失
    val useSharedPortraitPlayer = shouldUseSharedPlayerForPortraitFullscreen()
    val portraitPagerMotionSpec = remember {
        resolveStandalonePortraitPagerMotionSpec()
    }
    val shouldAnimatePortraitPager = remember(useSharedPortraitPlayer, directPortraitEntryFromRoute) {
        shouldAnimateStandalonePortraitPager(
            useSharedPlayer = useSharedPortraitPlayer,
            directPortraitEntry = directPortraitEntryFromRoute
        )
    }
    val inlineReturnAnimMs = if (shouldAnimatePortraitPager) {
        portraitPagerMotionSpec.inlineReturnDurationMillis
    } else {
        0
    }
    val inlinePlayerAlpha = animateFloatAsState(
        targetValue = if (isPortraitFullscreen) 0f else 1f,
        animationSpec = tween(
            durationMillis = inlineReturnAnimMs,
            easing = FastOutSlowInEasing
        ),
        label = "inline-player-alpha"
    )
    val inlinePlayerScale = animateFloatAsState(
        targetValue = if (isPortraitFullscreen) {
            portraitPagerMotionSpec.inlineReturnInitialScale
        } else {
            1f
        },
        animationSpec = tween(
            durationMillis = inlineReturnAnimMs,
            easing = FastOutSlowInEasing
        ),
        label = "inline-player-return-scale"
    )
    var portraitSyncSnapshotBvid by rememberSaveable { mutableStateOf<String?>(null) }
    var portraitSyncSnapshotCid by remember { mutableLongStateOf(0L) }
    var portraitSyncSnapshotPositionMs by remember { mutableLongStateOf(0L) }
    var hasPendingPortraitSync by remember { mutableStateOf(false) }
    var hasDeferredPortraitRestoreAfterExternalNavigation by rememberSaveable { mutableStateOf(false) }
    var pendingMainReloadBvidAfterPortrait by rememberSaveable { mutableStateOf<String?>(null) }
    var portraitPendingSelectionBvid by rememberSaveable { mutableStateOf<String?>(null) }
    // 返回 morph 中栈已 pop 时 isVisible=false，仍须保活 surface，避免壳缩前半段黑掉。
    val playbackSessionActiveForMorph = shouldKeepPlaybackSessionActiveForSharedReturnMorph(
        isVisible = isPlaybackSessionActive,
        sharedBoundsActive = sharedBoundsActive,
        isExitTransitionInProgress = isExitTransitionInProgress,
    )
    // 初始化播放器状态
    val playerState = rememberVideoPlayerState(
        context = context,
        viewModel = viewModel,
        bvid = currentBvid,
        cid = playbackTargetCid,
        fallbackResumePositionMs = resumePositionMsFromRoute,
        startPaused = isPortraitFullscreen && !useSharedPortraitPlayer,
        entryTransitionFinished = entryPlaybackReady,
        playbackSessionActive = playbackSessionActiveForMorph,
    )
    LaunchedEffect(isFullscreenMode) {
        val restorePositionMs = pendingFullscreenPositionRestoreMs
        if (restorePositionMs <= 0L) return@LaunchedEffect

        val player = playerState.player
        kotlinx.coroutines.withTimeoutOrNull(3_000L) {
            while (isActive) {
                if (
                    player.mediaItemCount > 0 &&
                    player.playbackState == Player.STATE_READY
                ) {
                    val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                    if (currentPositionMs + 750L < restorePositionMs) {
                        player.seekTo(restorePositionMs)
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoDetailScreen",
                            "Restored playback position after fullscreen change: " +
                                "${currentPositionMs}ms -> ${restorePositionMs}ms"
                        )
                    }
                    break
                }
                kotlinx.coroutines.delay(32L)
            }
        }
        pendingFullscreenPositionRestoreMs = -1L
    }
    VideoDetailKeepScreenOnEffect(
        window = window,
        player = playerState.player,
    )
    val isVideoPlaying by produceState(
        initialValue = playerState.player.isPlaying,
        key1 = playerState.player
    ) {
        val player = playerState.player
        value = player.isPlaying
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                value = player.isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                value = player.isPlaying
            }
        }
        player.addListener(listener)
        awaitDispose {
            player.removeListener(listener)
        }
    }
    LaunchedEffect(currentBvid, isVideoPlaying) {
        if (isVideoPlaying) {
            hasStartedCurrentVideoPlayback = true
        }
    }
    // 返回动效只需要首帧信号；带宽、丢帧等诊断更新不应重组整个详情页。
    val renderedFirstFrameFlow = remember(playerState) {
        playerState.debugInfo
            .map { it.firstFrame.equals("rendered", ignoreCase = true) }
            .distinctUntilChanged()
    }
    val hasRenderedFirstFrameForReturn by renderedFirstFrameFlow.collectAsStateWithLifecycle(
        initialValue = playerState.debugInfo.value.firstFrame.equals("rendered", ignoreCase = true),
        lifecycle = lifecycleOwner.lifecycle,
    )
    // 全量 Success 但无首帧 / 强制封面 UI 时禁止 LIVE，避免黑壳缩回。
    val hasRenderableLiveFrameForReturn = shouldTreatLiveSurfaceRenderableForReturnMorph(
        hasRenderedFirstFrame = hasRenderedFirstFrameForReturn,
        forceCoverUi = forceCoverOnlyForReturn,
    )
    val returnPlaybackIntent = resolveVideoDetailReturnPlaybackIntent(
        entryPlaybackIntent = videoSharedPlaybackIntent,
        hasRenderableLiveFrame = hasRenderableLiveFrameForReturn,
    )
    val currentLandingState =
        com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState.current
    val effectiveSourceLayout = if (currentLandingState.enabled) {
        currentLandingState.sourceLayout
    } else {
        CardPositionManager.lastClickedVideoSourceLayout
    }
    val candidateReturnCoverOwnership = resolveVideoDetailReturnCoverOwnership(
        transitionEnabled = transitionEnabled,
        sharedBoundsActive = sharedBoundsActive,
        keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
        playbackIntent = returnPlaybackIntent,
        detailContentReady = detailContentReadyForLiveReturnMorph,
        hasResidentCover = hasResidentReturnCover,
        hasRenderableLiveFrame = hasRenderableLiveFrameForReturn,
        liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
        sourceLayout = effectiveSourceLayout,
    )
    // 返回会话 ownership：可升 LIVE（保实时画面），禁止 LIVE 降级（防闪）。
    var lockedReturnCoverOwnership by remember(bvid) {
        mutableStateOf<com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership?>(null)
    }
    val isReturnCoverOwnershipSessionActive =
        useReturningVideoDetailVisualState || isCardReturnExitInProgress
    val (nextLockedReturnCoverOwnership, returnCoverOwnership) =
        resolveVideoDetailReturnSessionLockedOwnership(
            lockedOwnership = lockedReturnCoverOwnership,
            isReturnSessionActive = isReturnCoverOwnershipSessionActive,
            candidateOwnership = candidateReturnCoverOwnership,
        )
    SideEffect {
        if (lockedReturnCoverOwnership != nextLockedReturnCoverOwnership) {
            lockedReturnCoverOwnership = nextLockedReturnCoverOwnership
        }
        if (entryOwnsMiuixCardTransition) {
            com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics
                .onOwnershipChanged(returnCoverOwnership, liveSurfaceCardTransitionEnabled)
        }
    }
    val liveReturnMorph = isLiveReturnMorphFromOwnership(returnCoverOwnership)
    // Android 15+ avoids the Android 14 SurfaceView/Compose interop recovery path.
    // Keep the existing surface type (including HDR); only the upper cover fades.
    val progressiveResidentCoverReturn = shouldUseProgressiveResidentCoverReturn(
        sdkInt = android.os.Build.VERSION.SDK_INT,
        ownsInlineCardTransition = entryOwnsMiuixCardTransition && !useTabletLayout &&
            !isFullscreenMode && !isPortraitFullscreen,
        hasRenderedFirstFrame = hasRenderedFirstFrameForReturn,
        hasDecodedResidentCover = hasDecodedResidentReturnCover,
        liveReturnMorph = liveReturnMorph,
        forceCoverOnly = forceCoverOnlyOnReturn,
        reduceMotion = videoCardDepthBackgroundState.motionTierProvider() ==
            com.android.purebilibili.core.ui.adaptive.MotionTier.Reduced,
        followProgressEnabled = videoCardDepthBackgroundState
            .returnContentFollowProgressEnabledProvider(),
    )
    val useResidentCoverForCommittedReturn = shouldHandResidentCoverFromOwnership(
        ownership = returnCoverOwnership,
        useReturningVisualState = isCommittedCardReturn,
        hasResidentCover = hasResidentReturnCover,
    )
    // Progressive resident return keeps reveal state intact for cancellation. Older
    // SurfaceView paths still enter cover-only immediately to avoid surface leakage.
    val forceCoverOnlyForLiveSafeReturn = !progressiveResidentCoverReturn &&
        shouldForceCoverOnlyForReturnOwnership(
            ownership = returnCoverOwnership,
            useReturningVisualState = useReturningVideoDetailVisualState,
            forceCoverOnlyOnReturn = forceCoverOnlyForReturn,
            isCommittedCardReturn = isCommittedCardReturn,
        )
    val videoCardTransitionDensity = LocalDensity.current
    val videoCardDetailChromeAlphaProvider = remember(
        videoCardDepthBackgroundState,
        entryOwnsMiuixCardTransition,
        isCommittedCardReturn,
        scopedHeroDriver.sourceLayout,
        scopedHeroDriver.sourceChromeSnapshot?.isNowPlayingBar,
    ) {
        {
            if (!entryOwnsMiuixCardTransition) {
                1f
            } else {
                resolveVideoCardDetailChromeAlpha(
                    morphDepthProgress = videoCardDepthBackgroundState.progressProvider(),
                    phase = videoCardDepthBackgroundState.phaseProvider(),
                    isReturnGestureInProgress =
                        videoCardDepthBackgroundState.isReturnGestureInProgressProvider() ||
                            videoCardDepthBackgroundState.isGestureRestoreInProgressProvider(),
                    isCommittedCardReturn = isCommittedCardReturn,
                    sourceLayout = scopedHeroDriver.sourceLayout.takeUnless {
                        scopedHeroDriver.sourceChromeSnapshot?.isNowPlayingBar == true
                    },
                )
            }
        }
    }
    // Budget follows coarse return/restore flags, never frame-rate morph progress.
    // Ownership is the resolved strategy, even when a resident return has a decoded video frame.
    val returnSessionPhase = resolveVideoDetailReturnSessionPhase(
        isCommittedCardReturn = isCommittedCardReturn,
        isExitTransitionInProgress = isCardReturnExitInProgress,
        isReturnGestureInProgress = entryOwnsMiuixCardTransition &&
            videoCardDepthBackgroundState.isReturnGestureInProgressProvider(),
        isGestureRestoreInProgress = entryOwnsMiuixCardTransition &&
            videoCardDepthBackgroundState.isGestureRestoreInProgressProvider(),
        settleProgress = when {
            !isCommittedCardReturn -> 0f
            // 退出过渡进行中：按 Morph 预算（弹幕/控制层减负，不停播）。
            isCardReturnExitInProgress -> 0.4f
            // 提交后过渡信号已结束：Settle，允许停播收尾。
            else -> 1f
        },
    )
    // Read frame-rate state behind a threshold: only the visible/invisible boundary
    // invalidates composition. Never detach a body that is still fading inside the card.
    val returnBodyAlphaIsInvisible by remember(
        isCommittedCardReturn, liveReturnMorph, detailShellSharedBoundsEnabled,
        videoCardDepthBackgroundState, currentLandingState.sourceLayout,
        detailTransitionProgress, scopedHeroDriver.enabled,
    ) {
        derivedStateOf {
            if (!isCommittedCardReturn || liveReturnMorph) {
                false
            } else {
                val alpha = if (detailShellSharedBoundsEnabled) {
                    resolveVideoCardSecondaryContentVisualFrame(
                        morphDepthProgress = videoCardDepthBackgroundState.progressProvider(),
                        phase = videoCardDepthBackgroundState.phaseProvider(),
                        isReturnGestureInProgress =
                            videoCardDepthBackgroundState.isReturnGestureInProgressProvider() ||
                                videoCardDepthBackgroundState.isGestureRestoreInProgressProvider(),
                        motionTier = videoCardDepthBackgroundState.motionTierProvider(),
                        sourceLayout = currentLandingState.sourceLayout,
                        followProgressEnabled = videoCardDepthBackgroundState
                            .returnContentFollowProgressEnabledProvider(),
                        isCommittedCardReturn = isCommittedCardReturn,
                        preserveNativeViewport = scopedHeroDriver.enabled,
                    ).alpha
                } else {
                    resolveVideoDetailReturnContentAlpha(
                        transitionProgress = detailTransitionProgress.value,
                        isCommittedCardReturn = true,
                    )
                }
                alpha <= 0.02f
            }
        }
    }
    val returnVisualBudget = resolveVideoDetailReturnVisualBudget(
        phase = returnSessionPhase,
        hasRenderableLiveFrame = hasRenderableLiveFrameForReturn,
        ownership = returnCoverOwnership,
        reduceMotion = videoCardDepthBackgroundState.motionTierProvider() ==
            com.android.purebilibili.core.ui.adaptive.MotionTier.Reduced,
        secondaryContentAlpha = if (returnBodyAlphaIsInvisible) 0f else 1f,
    )
    // Budget describes actual ownership; frame readiness alone cannot select LiveMorph.
    val effectiveDanmakuEnabledForDetail =
        danmakuEnabledForDetail && !shouldPauseHideDanmakuForReturnBudget(returnVisualBudget)
    val detachSecondaryContentForReturn =
        shouldDetachSecondaryContentForReturnBudget(returnVisualBudget)
    val suppressOverlayControlsForReturn =
        shouldSuppressOverlayControlsForReturnBudget(returnVisualBudget)
    val routedCommentInteractionActive =
        openCommentRootRpidFromRoute > 0L &&
            (subReplyState.visible || subReplyState.isLoading)
    val commentInteractionActive =
        routedCommentInteractionActive || subReplyState.visible || showCommentInput
    LaunchedEffect(commentInteractionActive) {
        viewModel.setCommentInteractionActive(commentInteractionActive)
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setCommentInteractionActive(false)
        }
    }
    var hasAutoPausedForRoutedComment by rememberSaveable(
        bvid,
        openCommentRootRpidFromRoute,
        openCommentTargetRpidFromRoute
    ) {
        mutableStateOf(false)
    }
    LaunchedEffect(
        openCommentRootRpidFromRoute,
        videoSharedPlaybackIntent,
        isVideoPlaying,
        hasAutoPausedForRoutedComment
    ) {
        val shouldAutoPause =
            openCommentRootRpidFromRoute > 0L &&
                videoSharedPlaybackIntent == VideoSharedTransitionPlaybackIntent.ImmediatePlayback &&
                isVideoPlaying &&
                !hasAutoPausedForRoutedComment
        if (shouldAutoPause) {
            com.android.purebilibili.feature.video.usecase.pausePlayerFromUserAction(playerState.player)
            hasAutoPausedForRoutedComment = true
        }
    }
    val isPlaybackPaused by produceState(
        initialValue = resolveIsPlaybackPausedForCollapse(
            playWhenReady = playerState.player.playWhenReady,
            playbackState = playerState.player.playbackState
        ),
        key1 = playerState.player,
        key2 = currentBvid,
        key3 = currentBvidCid
    ) {
        val player = playerState.player

        fun updatePausedState() {
            value = resolveIsPlaybackPausedForCollapse(
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState
            )
        }

        updatePausedState()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePausedState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePausedState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updatePausedState()
            }
        }
        player.addListener(listener)
        awaitDispose {
            player.removeListener(listener)
        }
    }
    val subtitleAutoPreference by com.android.purebilibili.core.store.SettingsManager
        .getSubtitleAutoPreference(context)
        .collectAsStateWithLifecycle(
            initialValue = SubtitleAutoPreference.OFF,
            lifecycle = lifecycleOwner.lifecycle
        )
    val subtitleAudioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val subtitleAutoModeMuted = remember(playerState.player, subtitleAudioManager, currentBvid) {
        val systemMuted = runCatching {
            subtitleAudioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) <= 0
        }.getOrDefault(false)
        systemMuted || playerState.player.volume <= 0f
    }
    val subtitlePreferenceSession = remember(uiState, currentBvid, subtitleAutoPreference, subtitleAutoModeMuted) {
        val success = uiState as? VideoPlaybackUiState.Success
        if (success == null) {
            resolveSubtitlePreferenceSession(
                bvid = currentBvid,
                cid = 0L,
                primaryLanguage = null,
                secondaryLanguage = null,
                primaryTrackLikelyAi = false,
                secondaryTrackLikelyAi = false,
                hasPrimaryTrack = false,
                hasSecondaryTrack = false,
                preference = subtitleAutoPreference,
                isMuted = subtitleAutoModeMuted
            )
        } else {
            val subtitleBelongsToCurrentVideo =
                success.subtitleOwnerBvid == success.info.bvid &&
                    success.subtitleOwnerCid == success.info.cid &&
                    success.info.cid > 0L
            val hasPrimaryTrack = subtitleBelongsToCurrentVideo &&
                (!success.subtitlePrimaryTrackKey.isNullOrBlank() || !success.subtitlePrimaryLanguage.isNullOrBlank())
            val hasSecondaryTrack = subtitleBelongsToCurrentVideo &&
                (!success.subtitleSecondaryTrackKey.isNullOrBlank() || !success.subtitleSecondaryLanguage.isNullOrBlank())
            resolveSubtitlePreferenceSession(
                bvid = success.info.bvid,
                cid = success.info.cid,
                primaryLanguage = success.subtitlePrimaryLanguage,
                secondaryLanguage = success.subtitleSecondaryLanguage,
                primaryTrackLikelyAi = subtitleBelongsToCurrentVideo && success.subtitlePrimaryLikelyAi,
                secondaryTrackLikelyAi = subtitleBelongsToCurrentVideo && success.subtitleSecondaryLikelyAi,
                hasPrimaryTrack = hasPrimaryTrack,
                hasSecondaryTrack = hasSecondaryTrack,
                preference = subtitleAutoPreference,
                isMuted = subtitleAutoModeMuted
            )
        }
    }
    var subtitlePreferenceSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var subtitleDisplayModeOverride by rememberSaveable { mutableStateOf(SubtitleDisplayMode.OFF) }
    LaunchedEffect(subtitlePreferenceSession.key) {
        subtitleDisplayModeOverride = resolveSubtitleDisplayModePreference(
            previousSessionKey = subtitlePreferenceSessionKey,
            nextSessionKey = subtitlePreferenceSession.key,
            previousMode = subtitleDisplayModeOverride,
            nextInitialMode = subtitlePreferenceSession.initialMode
        )
        subtitlePreferenceSessionKey = subtitlePreferenceSession.key
    }

    var hasAppliedInitialPageSwitch by remember(currentBvid, playbackTargetCid) { mutableStateOf(false) }
    LaunchedEffect(uiState, currentBvid, playbackTargetCid, hasAppliedInitialPageSwitch) {
        if (hasAppliedInitialPageSwitch) return@LaunchedEffect
        val success = uiState as? VideoPlaybackUiState.Success ?: return@LaunchedEffect
        if (success.info.bvid != currentBvid) return@LaunchedEffect

        val targetPageIndex = resolveInitialPageIndex(
            requestedCid = playbackTargetCid,
            currentCid = success.info.cid,
            pages = success.info.pages
        )
        hasAppliedInitialPageSwitch = true
        if (targetPageIndex != null) {
            playbackActions.switchPage(targetPageIndex)
        }
    }

    // 🎯 [修复] 确保在 VideoPlayerState 销毁之前通知 MiniPlayerManager 页面退出
    // 必须在 playerState 之后声明此 Effect，这样它会在 playerState.onDispose 之前执行（LIFO 顺序）
    DisposableEffect(playerState) {
        onDispose {
            // 标记页面正在退出
            // 配置切换不标记离开；音频模式/小窗模式为主动保活场景，也不标记离开。
            val isChangingConfigurations = activity?.isChangingConfigurations == true
            val shouldHandleAsNavigationExit = shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = isNavigatingToAudioMode,
                isNavigatingToMiniMode = isNavigatingToMiniMode,
                isMiniModeActive = miniPlayerManager?.isMiniMode == true,
                isChangingConfigurations = isChangingConfigurations,
                isNavigatingToVideo = resolveIsNavigatingToVideoDuringDispose(
                    localNavigatingToVideo = isNavigatingToVideo,
                    managerNavigatingToVideo = miniPlayerManager?.isNavigatingToVideo == true
                )
            )
            if (shouldHandleAsNavigationExit) {
                com.android.purebilibili.core.util.Logger.d(
                    "VideoDetailScreen",
                    "🛑 Disposing screen as navigation exit, notifying MiniPlayerManager"
                )
                miniPlayerManager?.markLeavingByNavigation(expectedBvid = currentBvid)
            } else {
                com.android.purebilibili.core.util.Logger.d(
                    "VideoDetailScreen",
                    "💤 Screen disposed without navigation-exit mark (audioMode=$isNavigatingToAudioMode, miniMode=$isNavigatingToMiniMode, changingConfig=$isChangingConfigurations)"
                )
            }
        }
    }

    //  [性能优化] 生命周期感知：进入后台时暂停播放，返回前台时继续
    //  [修复] 此处逻辑已移至 VideoPlayerState.kt 统一处理
    // 删除冗余的暂停逻辑，避免与 VideoPlayerState 中的生命周期处理冲突
    // VideoPlayerState 会检查 PiP/小窗模式来决定是否暂停

    // 📱 [优化] 综合合并路由初值、API 信息与 PlayerState 维度，确保第 0 帧即可识别竖屏
    val isVerticalFromPlayer by playerState.isVerticalVideo.collectAsStateWithLifecycle()
    val isVerticalFromInfo = (uiState as? VideoPlaybackUiState.Success)?.info?.dimension?.isVertical == true
    val isVerticalVideo = initialVerticalFromRoute || isVerticalFromPlayer || isVerticalFromInfo
    val continuousFullscreenTransitionEnabled = transitionEnabled &&
        isOrientationDrivenFullscreen &&
        orientationPolicyDevice &&
        !isActivityInMultiWindowMode &&
        !isVerticalVideo
    var continuousPlayerPhase by rememberSaveable(currentBvid) {
        mutableStateOf(
            if (isFullscreenMode) {
                ContinuousPlayerTransitionPhase.Fullscreen
            } else {
                ContinuousPlayerTransitionPhase.Inline
            }
        )
    }
    val continuousPlayerProgress = remember(currentBvid) {
        Animatable(if (isFullscreenMode) 1f else 0f)
    }
    val isContinuousPlayerMorphing = continuousFullscreenTransitionEnabled &&
        continuousPlayerPhase == ContinuousPlayerTransitionPhase.Collapsing

    fun applyContinuousPlayerDecision(decision: ContinuousPlayerTransitionDecision) {
        continuousPlayerPhase = decision.phase
        when (decision.orientationRequest) {
            ContinuousPlayerOrientationRequest.None -> Unit
            ContinuousPlayerOrientationRequest.Landscape -> {
                userRequestedFullscreen = true
                manualPortraitHoldActive = false
                activity?.applyPlayerRequestedOrientation(
                    requestedOrientation = resolvePhoneFullscreenEnterOrientation(
                        fullscreenMode = fullscreenMode,
                        isVerticalVideo = false,
                    ) ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    displayContext = displayContext,
                )
            }
            ContinuousPlayerOrientationRequest.Portrait -> {
                userRequestedFullscreen = false
                manualPortraitHoldActive = true
                activity?.applyPlayerRequestedOrientation(
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    displayContext = displayContext,
                )
            }
        }
    }

    LaunchedEffect(
        continuousFullscreenTransitionEnabled,
        continuousPlayerPhase,
        isLandscape,
        isFullscreenMode,
    ) {
        // 关闭 continuous morph 时同步到真实全屏层，避免折叠/展开后继承旧方向相位。
        if (!continuousFullscreenTransitionEnabled) {
            val target = if (isFullscreenMode) 1f else 0f
            if (kotlin.math.abs(continuousPlayerProgress.value - target) > 0.001f) {
                continuousPlayerProgress.snapTo(target)
            }
            continuousPlayerPhase = if (isFullscreenMode) {
                ContinuousPlayerTransitionPhase.Fullscreen
            } else {
                ContinuousPlayerTransitionPhase.Inline
            }
            return@LaunchedEffect
        }
        when {
            // 系统旋进横屏：从 inline/半途相位直接落到全屏高度。
            isLandscape &&
                continuousPlayerPhase != ContinuousPlayerTransitionPhase.Fullscreen &&
                continuousPlayerPhase != ContinuousPlayerTransitionPhase.AwaitingPortrait -> {
                continuousPlayerProgress.snapTo(1f)
                continuousPlayerPhase = ContinuousPlayerTransitionPhase.Fullscreen
            }
            // 仅清理「已处于 Fullscreen 但窗口已回竖屏」的陈旧进度。
            // AwaitingLandscape 已请求旋转，但系统窗口方向可能尚未更新，不能提前清理。
            !isLandscape &&
                continuousPlayerPhase == ContinuousPlayerTransitionPhase.Fullscreen -> {
                continuousPlayerProgress.snapTo(0f)
                continuousPlayerPhase = ContinuousPlayerTransitionPhase.Inline
            }
            // 等待横屏期间保留当前播放器尺寸，不再先播放竖屏铺满动画。
            // 初始竖屏观测不代表用户退出，不能误派发成「回竖屏」事件。
            shouldKeepContinuousPlayerEnterPhaseWhilePortrait(
                phase = continuousPlayerPhase,
                isLandscape = isLandscape,
            ) -> Unit
            else -> applyContinuousPlayerDecision(
                reduceContinuousPlayerTransition(
                    phase = continuousPlayerPhase,
                    event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape),
                )
            )
        }
    }

    LaunchedEffect(continuousFullscreenTransitionEnabled, continuousPlayerPhase) {
        if (!continuousFullscreenTransitionEnabled) return@LaunchedEffect
        when (continuousPlayerPhase) {
            ContinuousPlayerTransitionPhase.Collapsing -> {
                val remaining = continuousPlayerProgress.value.coerceIn(0f, 1f)
                continuousPlayerProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = (CONTINUOUS_PLAYER_MORPH_DURATION_MILLIS * remaining)
                            .roundToInt()
                            .coerceAtLeast(1),
                        easing = FastOutSlowInEasing,
                    ),
                )
                applyContinuousPlayerDecision(
                    reduceContinuousPlayerTransition(
                        continuousPlayerPhase,
                        ContinuousPlayerTransitionEvent.CollapseFinished,
                    )
                )
            }
            else -> Unit
        }
    }
    val activeVideoSharedTransitionVisualSpec = remember(
        sourceRouteForSharedElement,
        sharedTransitionSourceCornerDp,
        videoSharedPlaybackIntent,
        startInFullscreen,
        autoEnterPortraitFromRoute,
        initialVerticalFromRoute,
        isVerticalVideo,
        useReturningVideoDetailVisualState,
        videoTransitionAdaptiveInfo,
    ) {
        resolveVideoSharedTransitionVisualSpec(
            sourceRoute = sourceRouteForSharedElement,
            sourceCornerDp = sharedTransitionSourceCornerDp,
            playbackIntent = videoSharedPlaybackIntent,
            fullscreen = startInFullscreen,
            autoPortrait = autoEnterPortraitFromRoute,
            initialVertical = initialVerticalFromRoute,
            isVerticalVideo = isVerticalVideo,
            isReturning = useReturningVideoDetailVisualState,
            adaptiveInfo = videoTransitionAdaptiveInfo,
        )
    }
    var lastPhoneAutoRotateLandscapeAppliedAtMs by remember { mutableStateOf<Long?>(null) }
    var lastPhoneAutoRotatePortraitAppliedAtMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(
        autoRotateEnabled,
        systemAutoRotateEnabled,
        fullscreenMode,
        useTabletLayout,
        isOrientationDrivenFullscreen,
        isFullscreenMode,
        orientationPolicyDevice,
        isActivityInMultiWindowMode,
        isPipMode,
        userRequestedFullscreen,
        manualPortraitHoldActive,
        isVerticalVideo,
        isPortraitFullscreen,
        displayContext,
        isLandscape,
        isFullscreenPlayerLocked,
    ) {
        if (isFullscreenPlayerLocked) return@LaunchedEffect
        if (usesInWindowFullscreen) return@LaunchedEffect
        val requestedOrientation = resolvePhoneVideoRequestedOrientation(
            autoRotateEnabled = sensorAutoRotateEnabled,
            systemAutoRotateEnabled = systemAutoRotateEnabled,
            fullscreenMode = fullscreenMode,
            isCompactDevice = orientationPolicyDevice,
            isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
            isFullscreenMode = isFullscreenMode,
            manualFullscreenRequested = userRequestedFullscreen,
            manualPortraitHoldActive = manualPortraitHoldActive,
            isVerticalVideo = isVerticalVideo,
            isPortraitFullscreen = isPortraitFullscreen,
            currentRequestedOrientation = activity?.requestedOrientation,
            isInMultiWindowMode = isActivityInMultiWindowMode,
            isInPictureInPictureMode = isPipMode,
            // 展开态折叠屏也应遵循用户选择的默认全屏方向。
            preferPortraitForFlatFoldable = false,
            preserveExactLandscapeSide = orientationPolicyDevice,
            isCurrentlyLandscape = isLandscape,
        ) ?: return@LaunchedEffect

        val nowMs = SystemClock.elapsedRealtime()
        val targetToApply = resolvePhoneAutoRotateTargetToApply(
            candidateOrientation = requestedOrientation,
            lastLandscapeAppliedAtMs = lastPhoneAutoRotateLandscapeAppliedAtMs,
            nowMs = nowMs,
            lastPortraitAppliedAtMs = lastPhoneAutoRotatePortraitAppliedAtMs,
        ) ?: return@LaunchedEffect

        activity?.applyPlayerRequestedOrientation(
            requestedOrientation = targetToApply,
            displayContext = displayContext,
        )
        if (targetToApply == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            lastPhoneAutoRotatePortraitAppliedAtMs = nowMs
            lastPhoneAutoRotateLandscapeAppliedAtMs = null
        } else if (isLandscapeRequestedOrientation(targetToApply)) {
            lastPhoneAutoRotateLandscapeAppliedAtMs = nowMs
            lastPhoneAutoRotatePortraitAppliedAtMs = null
        }
        com.android.purebilibili.core.util.Logger.d(
            "VideoDetailScreen",
            "🔄 Auto-rotate: enabled=$autoRotateEnabled, hold=$manualPortraitHoldActive, mode=$fullscreenMode, horizontal=$horizontalAdaptationEnabled, requested=$targetToApply, fullscreen=$isFullscreenMode, portraitFs=$isPortraitFullscreen, verticalVideo=$isVerticalVideo, isCompactDevice=${windowSizeClass.isCompactDevice}, multiWindow=$isActivityInMultiWindowMode, pip=$isPipMode"
        )
    }

    LaunchedEffect(
        autoRotateEnabled,
        systemAutoRotateEnabled,
        isFullscreenMode,
        orientationPolicyDevice,
        isOrientationDrivenFullscreen,
        fullscreenMode,
        manualPortraitHoldActive,
        isActivityInMultiWindowMode,
        isPipMode,
        isPortraitFullscreen,
        displayContext,
        isFullscreenPlayerLocked,
        userRequestedFullscreen,
    ) {
        if (isFullscreenPlayerLocked) {
            lastPhoneAutoRotateLandscapeAppliedAtMs = null
            lastPhoneAutoRotatePortraitAppliedAtMs = null
            return@LaunchedEffect
        }
        if (!shouldObservePhoneAutoRotate(
                autoRotateEnabled = sensorAutoRotateEnabled,
                isCompactDevice = orientationPolicyDevice,
                isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
                fullscreenMode = fullscreenMode,
                manualPortraitHoldActive = manualPortraitHoldActive,
                isInMultiWindowMode = isActivityInMultiWindowMode,
                isInPictureInPictureMode = isPipMode,
                isPortraitFullscreen = isPortraitFullscreen,
                observeWhenAutoRotateDisabled = displayContext.isFoldableCoverWindow,
                isFullscreenMode = isFullscreenMode,
                manualFullscreenRequested = userRequestedFullscreen,
                systemAutoRotateEnabled = systemAutoRotateEnabled,
            )
        ) {
            lastPhoneAutoRotateLandscapeAppliedAtMs = null
            lastPhoneAutoRotatePortraitAppliedAtMs = null
        }
    }

    DisposableEffect(
        activity,
        autoRotateEnabled,
        systemAutoRotateEnabled,
        isFullscreenMode,
        fullscreenMode,
        useTabletLayout,
        orientationPolicyDevice,
        isOrientationDrivenFullscreen,
        manualPortraitHoldActive,
        isActivityInMultiWindowMode,
        isPipMode,
        isPortraitFullscreen,
        displayContext,
        isFullscreenPlayerLocked,
        userRequestedFullscreen,
    ) {
        val hostActivity = activity
        if (
            hostActivity == null ||
            isFullscreenPlayerLocked ||
            !shouldObservePhoneAutoRotate(
                autoRotateEnabled = sensorAutoRotateEnabled,
                isCompactDevice = orientationPolicyDevice,
                isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
                fullscreenMode = fullscreenMode,
                manualPortraitHoldActive = manualPortraitHoldActive,
                isInMultiWindowMode = isActivityInMultiWindowMode,
                isInPictureInPictureMode = isPipMode,
                isPortraitFullscreen = isPortraitFullscreen,
                observeWhenAutoRotateDisabled = displayContext.isFoldableCoverWindow,
                isFullscreenMode = isFullscreenMode,
                manualFullscreenRequested = userRequestedFullscreen,
                systemAutoRotateEnabled = systemAutoRotateEnabled,
            ) ||
            !isOrientationDrivenFullscreen
        ) {
            return@DisposableEffect onDispose {}
        }

        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (manualPortraitHoldActive) {
                    if (shouldReleasePhoneManualPortraitHold(orientation)) {
                        manualPortraitHoldActive = false
                    }
                    return
                }
                // 关闭自动切换时仅允许手动全屏跟随左右横屏；旧配置中的横屏状态
                // 不能重新触发横屏请求，与上面的竖屏锁定策略保持一致。
                if (!sensorAutoRotateEnabled && !userRequestedFullscreen) return
                val isCurrentlyLandscape =
                    hostActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val targetOrientation = resolvePhoneAutoRotateRequestedOrientation(
                    orientationDegrees = orientation,
                    isCurrentlyLandscape = isCurrentlyLandscape,
                    useExactLandscapeSide = orientationPolicyDevice,
                    // A manual fullscreen press can happen while the device is still upright.
                    // Wait for one physical landscape observation before allowing the sensor
                    // to treat portrait as an explicit rotate-back gesture.
                    allowPortraitTransitions = shouldAllowPhoneSensorPortraitTransition(
                        autoRotateEnabled = sensorAutoRotateEnabled,
                        manualFullscreenRequested = userRequestedFullscreen,
                    ),
                )
                val nowMs = SystemClock.elapsedRealtime()
                val targetToApply = resolvePhoneAutoRotateTargetToApply(
                    candidateOrientation = targetOrientation,
                    lastLandscapeAppliedAtMs = lastPhoneAutoRotateLandscapeAppliedAtMs,
                    nowMs = nowMs,
                    lastPortraitAppliedAtMs = lastPhoneAutoRotatePortraitAppliedAtMs,
                ) ?: return
                hostActivity.applyPlayerRequestedOrientation(
                    requestedOrientation = targetToApply,
                    displayContext = displayContext,
                )
                if (targetToApply == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    userRequestedFullscreen = false
                    lastPhoneAutoRotatePortraitAppliedAtMs = nowMs
                    lastPhoneAutoRotateLandscapeAppliedAtMs = null
                } else if (isLandscapeRequestedOrientation(targetToApply)) {
                    if (shouldReleaseManualFullscreenRequestAfterSensorTarget(
                            manualFullscreenRequested = userRequestedFullscreen,
                            sensorTargetOrientation = targetToApply,
                            autoRotateEnabled = sensorAutoRotateEnabled,
                        )
                    ) {
                        userRequestedFullscreen = false
                    }
                    lastPhoneAutoRotateLandscapeAppliedAtMs = nowMs
                    lastPhoneAutoRotatePortraitAppliedAtMs = null
                }
            }
        }

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }

        onDispose {
            orientationListener.disable()
            // Fullscreen/configuration changes can immediately register a new listener.
            // Keep the settle guard across that handoff; the effect above clears it
            // when orientation observation actually stops.
        }
    }
    val portraitExperienceEnabled = shouldEnablePortraitExperience()
    val useOfficialInlinePortraitDetailExperience = shouldUseOfficialInlinePortraitDetailExperience(
        useTabletLayout = useTabletLayout,
        isVerticalVideo = isVerticalVideo,
        portraitExperienceEnabled = portraitExperienceEnabled,
        directPortraitEntry = directPortraitEntryFromRoute
    )
    val allowStandalonePortraitExperience = portraitExperienceEnabled &&
        !useOfficialInlinePortraitDetailExperience
    // Direct morph: hide phone intro/comment body under the full-bleed shell so only
    // card→fullscreen motion + entry cover / portrait pager are visible.
    val suppressPhoneDetailBodyForDirectPortrait =
        shouldSuppressPhoneDetailBodyForDirectPortraitEntry(
            directPortraitEntry = directPortraitEntryFromRoute,
            isPortraitFullscreen = isPortraitFullscreen
        ) || shouldSuppressPhoneDetailBodyUnderStandalonePortraitPager(
            portraitExperienceEnabled = portraitExperienceEnabled,
            isPortraitFullscreen = isPortraitFullscreen,
            hasPlayableState = uiState is VideoPlaybackUiState.Success ||
                uiState is VideoPlaybackUiState.Loading,
        )
    val isCurrentRouteVideoLoaded = remember(uiState, currentBvid) {
        val success = uiState as? VideoPlaybackUiState.Success
        success?.info?.bvid == currentBvid
    }
    val enterPortraitFullscreen = {
        if (shouldActivatePortraitFullscreenState(portraitExperienceEnabled)) {
            portraitSyncSnapshotBvid = (uiState as? VideoPlaybackUiState.Success)?.info?.bvid
            portraitSyncSnapshotCid = (uiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
            portraitSyncSnapshotPositionMs = playerState.player.currentPosition.coerceAtLeast(0L)
            hasPendingPortraitSync = false
            presentationState.setPortraitFullscreen(true)
        }
    }
    LaunchedEffect(
        autoEnterPortraitFromRoute,
        startAudioFromRoute,
        portraitExperienceEnabled,
        useOfficialInlinePortraitDetailExperience,
        windowSizeClass.widthSizeClass,
        isCurrentRouteVideoLoaded,
        isVerticalVideo,
        isPortraitFullscreen,
        hasAutoEnteredPortraitFromRoute,
        initialVerticalFromRoute,
        directPortraitEntryFromRoute,
        directPortraitEntryEnabled,
    ) {
        if (
            shouldAutoEnterPortraitFullscreenFromRoute(
                autoEnterPortraitFromRoute = autoEnterPortraitFromRoute,
                startAudioFromRoute = startAudioFromRoute,
                portraitExperienceEnabled = portraitExperienceEnabled,
                useOfficialInlinePortraitDetailExperience = useOfficialInlinePortraitDetailExperience,
                allowStandalonePortraitAutoEnter = windowSizeClass.widthSizeClass ==
                    com.android.purebilibili.core.util.WindowWidthSizeClass.Compact,
                isCurrentRouteVideoLoaded = isCurrentRouteVideoLoaded,
                isVerticalVideo = isVerticalVideo,
                isPortraitFullscreen = isPortraitFullscreen,
                hasAutoEnteredPortraitFromRoute = hasAutoEnteredPortraitFromRoute,
                initialVerticalFromRoute = initialVerticalFromRoute,
                directPortraitEntryFromRoute = directPortraitEntryFromRoute,
                directPortraitEntryEnabled = directPortraitEntryEnabled,
            )
        ) {
            enterPortraitFullscreen()
            hasAutoEnteredPortraitFromRoute = true
        }
    }
    val shouldMirrorPortraitProgressToMainPlayer = com.android.purebilibili.feature.video.ui.pager
        .shouldMirrorPortraitProgressToMainPlayer(useSharedPlayer = useSharedPortraitPlayer)
    val latestOnReplaceVideoDetail by rememberUpdatedState(onReplaceVideoDetail)

    val tryApplyPortraitProgressSync = remember(playerState, viewModel) {
        { snapshotBvid: String?, snapshotPositionMs: Long ->
            val currentSuccess = viewModel.uiState.value as? VideoPlaybackUiState.Success
            val currentBvid = currentSuccess?.info?.bvid
            val currentCid = currentSuccess?.info?.cid ?: 0L
            if (!com.android.purebilibili.feature.video.ui.pager.shouldApplyPortraitProgressSync(
                    snapshotBvid = snapshotBvid,
                    snapshotCid = portraitSyncSnapshotCid,
                    currentBvid = currentBvid,
                    currentCid = currentCid
                )
            ) {
                false
            } else {
                playerState.player.seekTo(snapshotPositionMs.coerceAtLeast(0L))
                true
            }
        }
    }

    fun applyPortraitExitRestore() {
        val target = com.android.purebilibili.feature.video.ui.pager.resolvePortraitExitRestoreTarget(
            pendingMainReloadBvidAfterPortrait = pendingMainReloadBvidAfterPortrait,
            portraitPendingSelectionBvid = portraitPendingSelectionBvid,
            portraitSyncSnapshotBvid = portraitSyncSnapshotBvid,
            portraitSyncSnapshotCid = portraitSyncSnapshotCid,
        ) ?: return
        if (com.android.purebilibili.feature.video.ui.pager
                .shouldReplaceVideoDetailRouteAfterPortraitExit(
                    routeBvid = bvid,
                    portraitBvid = target.bvid,
                )
        ) {
            // The inline detail state is scoped to the route key. Mutating it in place leaves
            // Navigation 3 pointing at the entry video and the old route wins again on re-entry.
            // Replace the destination so player, metadata and saved state share one identity.
            presentationState.markNavigatingToVideo()
            miniPlayerManager?.isNavigatingToVideo = true
            val routeReplaced = latestOnReplaceVideoDetail(
                target.bvid,
                target.cid,
                pendingInPageSwitchCoverUrl,
                portraitSyncSnapshotPositionMs.coerceAtLeast(0L),
            )
            if (routeReplaced) return
            miniPlayerManager?.isNavigatingToVideo = false
        }
        val loaded = viewModel.uiState.value as? VideoPlaybackUiState.Success
        presentationState.switchVideo(target.bvid, target.cid)
        if (com.android.purebilibili.feature.video.ui.pager.shouldReloadMainPlayerAfterPortraitExit(
                snapshotBvid = target.bvid,
                snapshotCid = target.cid,
                currentBvid = loaded?.info?.bvid,
                currentCid = loaded?.info?.cid ?: 0L,
            )
        ) {
            // Commit the detail subject together with the portrait exit. Relying on the later
            // currentBvid observer leaves a frame-order race with AnimatedVisibility disposal:
            // the player/cover can already point at page N while title/description still expose
            // the route's first VideoInfo.
            viewModel.loadVideo(
                bvid = target.bvid,
                cid = target.cid.takeIf { it > 0L } ?: 0L,
                autoPlay = resolveAutoPlayOverrideForInternalBvidSync(forceAutoPlay = false),
            )
        }
    }



    // 同步状态到 playerState (可选，用于日志或内部逻辑)
    LaunchedEffect(isPortraitFullscreen) {
        playerState.setPortraitFullscreen(isPortraitFullscreen)
        viewModel.setPortraitPlaybackSessionActive(isPortraitFullscreen)
        val shouldPauseMainPlayer = com.android.purebilibili.feature.video.ui.pager
            .shouldPauseMainPlayerOnPortraitEnter(useSharedPlayer = useSharedPortraitPlayer)
        if (isPortraitFullscreen) {
            if (shouldPauseMainPlayer) {
                playerState.player.pause()
                playerState.player.volume = 0f
                playerState.player.playWhenReady = false
            }
            portraitSyncSnapshotBvid = (uiState as? VideoPlaybackUiState.Success)?.info?.bvid
            portraitSyncSnapshotCid = (uiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
            portraitSyncSnapshotPositionMs = playerState.player.currentPosition.coerceAtLeast(0L)
            hasPendingPortraitSync = shouldPauseMainPlayer
        } else {
             if (shouldPauseMainPlayer) {
                 // 退出时恢复音量 (不自动播放，等待用户操作或 onResume)
                 com.android.purebilibili.core.player.PlayerVolumeController
                     .applyPreferredVolume(playerState.player)
             }
            if (!com.android.purebilibili.feature.video.ui.pager
                    .shouldApplyDeferredPortraitRestoreOnResume(
                        hasDeferredRestore = hasDeferredPortraitRestoreAfterExternalNavigation,
                        isPortraitFullscreen = isPortraitFullscreen
                    )
            ) {
                applyPortraitExitRestore()
                pendingMainReloadBvidAfterPortrait = null
                portraitPendingSelectionBvid = null
            }
        }
    }

    DisposableEffect(
        lifecycleOwner,
        isPortraitFullscreen,
        hasDeferredPortraitRestoreAfterExternalNavigation,
        pendingMainReloadBvidAfterPortrait,
        portraitPendingSelectionBvid,
        portraitSyncSnapshotBvid,
        portraitSyncSnapshotCid,
    ) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (!com.android.purebilibili.feature.video.ui.pager
                    .shouldApplyDeferredPortraitRestoreOnResume(
                        hasDeferredRestore = hasDeferredPortraitRestoreAfterExternalNavigation,
                        isPortraitFullscreen = isPortraitFullscreen
                    )
            ) {
                return@LifecycleEventObserver
            }
            applyPortraitExitRestore()
            pendingMainReloadBvidAfterPortrait = null
            portraitPendingSelectionBvid = null
            hasDeferredPortraitRestoreAfterExternalNavigation = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setPortraitPlaybackSessionActive(false)
        }
    }

    LaunchedEffect(
        uiState,
        hasPendingPortraitSync,
        portraitSyncSnapshotBvid,
        portraitSyncSnapshotPositionMs
    ) {
        if (hasPendingPortraitSync && tryApplyPortraitProgressSync(
                portraitSyncSnapshotBvid,
                portraitSyncSnapshotPositionMs
            )
        ) {
            hasPendingPortraitSync = false
        }
    }

    LaunchedEffect(uiState, currentBvid, currentBvidCid, isPortraitFullscreen, bvid, isVisible) {
        if (!isVisible) return@LaunchedEffect
        val success = uiState as? VideoPlaybackUiState.Success ?: return@LaunchedEffect
        val managerBvid = miniPlayerManager?.currentBvid.orEmpty()
        val managerCid = miniPlayerManager?.currentCid ?: 0L
        if (
            miniPlayerManager?.isActive == true &&
            managerBvid.isNotBlank() &&
            managerBvid == success.info.bvid &&
            (managerCid <= 0L || managerCid == success.info.cid) &&
            (currentBvid != managerBvid ||
                (managerCid > 0L && currentBvidCid != managerCid))
        ) {
            // Playback may advance while lifecycle-aware UI collection is stopped. Adopt the
            // manager's live identity before the route/internal-id guard can reload the old item.
            presentationState.switchVideo(managerBvid, managerCid)
            return@LaunchedEffect
        }
        if (
            shouldSkipInternalBvidSyncForPlayerInitiatedAdvance(
                loadedBvid = success.info.bvid,
                inPageInitiatedBvid = viewModel.peekInPageInitiatedPlaybackIdentityBvid()
            )
        ) {
            // 播放器内部已自动推进（合集/队列下一集等）：身份错位是预期状态。
            // 既不能像“presentation 领先”那样重载旧视频，也不能把 presentation 同步到新集数——
            // 后者会中途改写 bvid 键控的 UI 状态（封面揭示/控制层），而此时没有新的首帧事件可恢复。
            // 保持 presentation 原样与直开场景的行为一致。
            return@LaunchedEffect
        }
        if (!shouldSyncMainPlayerToInternalBvid(
                isPortraitFullscreen = isPortraitFullscreen,
                routeBvid = bvid,
                currentBvid = currentBvid,
                currentBvidCid = currentBvidCid,
                loadedBvid = success.info.bvid,
                loadedCid = success.info.cid
            )
        ) {
            return@LaunchedEffect
        }
        viewModel.loadVideo(
            bvid = currentBvid,
            cid = currentBvidCid.takeIf { it > 0L } ?: 0L,
            autoPlay = resolveAutoPlayOverrideForInternalBvidSync(forceAutoPlay = false)
        )
    }

    // 📲 小窗模式（手机/平板统一逻辑）
    val handlePipClick = {
        // 使用 MiniPlayerManager 进入应用内小窗模式
        miniPlayerManager?.let { manager ->
            val stopPlaybackOnExit = com.android.purebilibili.core.store.SettingsManager
                .getStopPlaybackOnExitSync(context)
            if (stopPlaybackOnExit) {
                com.android.purebilibili.core.util.Logger.d(
                    "VideoDetailScreen",
                    "Stop-on-exit enabled, skip mini mode and leave page directly"
                )
                manager.markLeavingByNavigation(expectedBvid = currentBvid)
                onBack()
                return@let
            }

            //  [埋点] PiP 进入事件
            com.android.purebilibili.core.util.AnalyticsHelper.logPictureInPicture(
                videoId = currentBvid,
                action = "enter_mini"
            )

            // 1. 将当前播放器信息传递给小窗管理器
            val info = uiState as? VideoPlaybackUiState.Success
            manager.setVideoInfo(
                bvid = currentBvid,
                title = info?.info?.title ?: "",
                cover = info?.info?.pic ?: "",
                owner = info?.info?.owner?.name ?: "",
                cid = info?.info?.cid ?: 0L,
                aid = info?.info?.aid ?: 0L,
                externalPlayer = playerState.player
            )

            // 2. 进入小窗模式（强制，不管当前模式设置）
            manager.enterMiniMode(forced = true)

            // 3. 返回上一页（首页）
            presentationState.markNavigatingToMiniMode()
            onBack()
        } ?: run {
            // 如果 miniPlayerManager 不存在，直接返回
            com.android.purebilibili.core.util.Logger.w("VideoDetailScreen", "⚠️ miniPlayerManager 为 null，无法进入小窗")
            onBack()
        }
    }


    LaunchedEffect(detailContentState?.info?.aid, preferredCommentSortMode, isVisible, entryTransitionFinished) {
        if (!isVisible || !entryTransitionFinished) return@LaunchedEffect
        val info = detailContentState?.info ?: return@LaunchedEffect
        commentViewModel.init(
            aid = info.aid,
            upMid = info.owner.mid,
            preferredSortMode = preferredCommentSortMode,
            expectedReplyCount = info.stat.reply
        )
        if (openCommentRootRpidFromRoute > 0L) {
            selectedVideoContentTabIndex = 1
            if (!hasHandledCommentRootFromRoute) {
                hasHandledCommentRootFromRoute = commentViewModel.openSubReplyFromRoute(
                    rootReplyId = openCommentRootRpidFromRoute,
                    targetReplyId = openCommentTargetRpidFromRoute
                )
            }
        }
    }

    //  核心修改：初始化媒体中心信息
    LaunchedEffect(uiState, isVisible, playerState.player) {
        if (uiState is VideoPlaybackUiState.Success) {
            val info = (uiState as VideoPlaybackUiState.Success).info
            val success = uiState as VideoPlaybackUiState.Success

            // 📱 [双重验证] 从 API dimension 字段设置预判断值
            info.dimension?.let { dim ->
                playerState.setApiDimension(dim.width, dim.height, dim.rotate)
            }

            if (miniPlayerManager != null && isVisible) {
                // Returning to a retained page must reclaim the session even when its BVID
                // has not changed. Cache deduplication must never gate player ownership.
                val shouldReclaimSession = shouldReclaimVideoPlaybackSession(
                    isVisible = isVisible,
                    isSessionActive = miniPlayerManager.isActive,
                    currentPlayer = miniPlayerManager.player,
                    currentBvid = miniPlayerManager.currentBvid,
                    currentCid = miniPlayerManager.currentCid,
                    pagePlayer = playerState.player,
                    pageBvid = currentBvid,
                    pageCid = info.cid,
                )
                if (shouldReclaimSession) {
                    miniPlayerManager.setVideoInfo(
                        bvid = currentBvid,
                        title = info.title,
                        cover = info.pic,
                        owner = info.owner.name,
                        cid = info.cid,
                        aid = info.aid,
                        externalPlayer = playerState.player,
                        fromLeft = com.android.purebilibili.core.util.CardPositionManager.isCardOnLeft,
                    )
                }
                // This only assigns the state reference; no background serialization is needed.
                // Keep cache, metadata and player identity in the same main-thread handoff.
                miniPlayerManager.syncCurrentVideoInfo(success, expectedPlayer = playerState.player)
            } else if (miniPlayerManager == null) {
                android.util.Log.w("VideoDetailScreen", "miniPlayerManager is null")
            }
        } else if (uiState is VideoPlaybackUiState.Loading) {
            playerState.updateMediaMetadata(
                title = "加载中...",
                artist = "",
                coverUrl = coverUrl
            )
        }
    }

    //  弹幕加载逻辑已移至 VideoPlayerState 内部处理
    // 避免在此处重复消耗 InputStream

    // 辅助函数：切换全屏状态
    val toggleFullscreen = {
        val activity = context.findActivity()
        com.android.purebilibili.core.util.Logger.d("VideoInputTrace") {
            "fullscreen_toggle fullscreen=$isFullscreenMode portraitFs=$isPortraitFullscreen " +
                "landscape=$isLandscape phase=$continuousPlayerPhase requested=${activity?.requestedOrientation}"
        }
        val currentPositionMs = playerState.player.currentPosition.coerceAtLeast(0L)
        val shouldPreserveCurrentFrame = activity != null &&
            (!isVerticalVideo || isFullscreenMode) &&
            playerState.player.playWhenReady &&
            currentPositionMs > 0L
        preserveCurrentFrameOnFullscreenChange = shouldPreserveCurrentFrame
        if (shouldPreserveCurrentFrame) {
            pendingFullscreenPositionRestoreMs = currentPositionMs
        } else {
            pendingFullscreenPositionRestoreMs = -1L
        }
        if (continuousFullscreenTransitionEnabled) {
            applyContinuousPlayerDecision(
                reduceContinuousPlayerTransition(
                    phase = continuousPlayerPhase,
                    event = ContinuousPlayerTransitionEvent.Toggle,
                )
            )
        } else toggleVideoDetailFullscreen(
            activity = activity,
            isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
            isLandscape = isLandscape,
            isFullscreenMode = isFullscreenMode,
            isCompactDevice = orientationPolicyDevice,
            fullscreenMode = fullscreenMode,
            isVerticalVideo = isVerticalVideo,
            preferPortraitForFlatFoldable = false,
            displayContext = displayContext,
            portraitExperienceEnabled = portraitExperienceEnabled,
            onEnterPortraitFullscreen = { enterPortraitFullscreen() },
            onUserRequestedFullscreenChange = { requested -> userRequestedFullscreen = requested },
            onManualPortraitHoldActiveChange = { active -> manualPortraitHoldActive = active }
        )
    }

    // Fullscreen removes the inline scrollable nodes while retaining their states. A scroll
    // mutation from the old attachment must not suppress clicks/scrolls after reattachment.
    // stopScroll cancels the old mutation without changing the saved item/page position.
    var inlineContentWasFullscreen by remember(currentBvid) {
        mutableStateOf(isFullscreenMode || isPortraitFullscreen)
    }
    LaunchedEffect(currentBvid, isFullscreenMode, isPortraitFullscreen) {
        val fullscreenNow = isFullscreenMode || isPortraitFullscreen
        val returningToInline = inlineContentWasFullscreen && !fullscreenNow
        inlineContentWasFullscreen = fullscreenNow
        com.android.purebilibili.core.util.Logger.d("VideoInputTrace") {
            "presentation_changed fullscreen=$fullscreenNow returning=$returningToInline " +
                "introScrolling=${introListState.isScrollInProgress} commentScrolling=${commentListState.isScrollInProgress} " +
                "pagerScrolling=${videoContentPagerState.isScrollInProgress} phase=$continuousPlayerPhase"
        }
        if (returningToInline) {
            stopVideoDetailInlineScrolls(
                introListState = introListState,
                commentListState = commentListState,
                pagerState = videoContentPagerState,
            )
            com.android.purebilibili.core.util.Logger.d(
                "VideoDetailScreen",
                "Inline input restored after fullscreen: introScrolling=${introListState.isScrollInProgress} " +
                    "commentScrolling=${commentListState.isScrollInProgress} pagerScrolling=${videoContentPagerState.isScrollInProgress}",
            )
        }
    }

    val localBackTarget = resolveVideoDetailLocalBackTarget(
        isLandscapeFullscreen = isFullscreenMode,
        isPortraitFullscreen = isPortraitFullscreen,
        directPortraitEntry = directPortraitEntryFromRoute,
    )
    val localBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = localBackEventState,
        isBackEnabled = localBackTarget != VideoDetailLocalBackTarget.NAVIGATE_BACK,
        onBackCompleted = {
            com.android.purebilibili.core.util.Logger.d("VideoInputTrace") {
                "local_back_completed target=$localBackTarget fullscreen=$isFullscreenMode portraitFs=$isPortraitFullscreen"
            }
            when (localBackTarget) {
                VideoDetailLocalBackTarget.EXIT_PORTRAIT_FULLSCREEN -> presentationState.setPortraitFullscreen(false)
                VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN -> toggleFullscreen()
                VideoDetailLocalBackTarget.NAVIGATE_BACK -> Unit
            }
        },
    )

    // 以下 BackHandler 会阻止 Compose Navigation 的返回路由动画，由根导航统一处理。
    // 显式点击返回时由 handleBack 提前标记 returning，系统路径仍由 onDispose 兜底标记。
    // BackHandler(enabled = !isFullscreenMode && !isPortraitFullscreen, onBack = handleBack)


    // 清理逻辑（markLeavingByNavigation、restoreStatusBar）已移至 DisposableEffect.onDispose

    // 沉浸式状态栏控制
    val backgroundColor = AppSurfaceTokens.background()
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }
    // 进/出全屏瞬间系统栏图标明暗必须同帧切换；若经 LaunchedEffect 复位会晚一拍，
    // 所以滚动折叠值只做原始状态，全屏/平板场景直接在派生处压制。
    var playerChromeCollapsedByScroll by remember(currentBvid) { mutableStateOf(false) }
    val useCollapsedPlayerChromeAppearance = playerChromeCollapsedByScroll &&
        !(useTabletLayout || isLandscape || isFullscreenMode || isPortraitFullscreen)
    val systemBarsVisibilityPolicy = remember(
        isFullscreenMode,
        isPortraitFullscreen,
        immersiveVideoPageStatusBar,
        isPipMode,
        isScreenActive
    ) {
        resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = isFullscreenMode,
            hideVideoPageStatusBar = immersiveVideoPageStatusBar,
            isInPipMode = isPipMode,
            isScreenActive = isScreenActive,
            isPortraitFullscreen = isPortraitFullscreen
        )
    }
    val systemBarsApplySpec = remember(
        systemBarsVisibilityPolicy,
        useTabletLayout,
        isLightBackground,
        backgroundColor,
        useCollapsedPlayerChromeAppearance,
    ) {
        resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = systemBarsVisibilityPolicy,
            useTabletLayout = useTabletLayout,
            isLightBackground = isLightBackground,
            useCollapsedPlayerChromeAppearance = useCollapsedPlayerChromeAppearance,
            backgroundColor = backgroundColor.toArgb(),
            transparentColor = Color.Transparent.toArgb(),
            blackColor = Color.Black.toArgb(),
            transientBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        )
    }

    VideoDetailSystemBarsEffect(
        view = view,
        window = window,
        insetsController = insetsController,
        isScreenActive = isScreenActive,
        spec = systemBarsApplySpec,
        reapplyGeneration = systemBarsReapplyGeneration,
    )

    val uiSuccessState = uiState as? VideoPlaybackUiState.Success
    LaunchedEffect(uiSuccessState?.info?.bvid, currentBvid) {
        // Loaded target matched; drop transitional cover.
        if (
            pendingInPageSwitchCoverUrl.isNotBlank() &&
            uiSuccessState?.info?.bvid == currentBvid
        ) {
            pendingInPageSwitchCoverUrl = ""
        }
    }
    val videoPlayerSectionTarget = remember(
        bvid,
        coverUrl,
        currentBvid,
        currentBvidCid,
        pendingInPageSwitchCoverUrl,
        uiSuccessState?.info?.ugc_season,
        uiSuccessState?.info?.pic,
        uiSuccessState?.info?.bvid
    ) {
        val successCover = uiSuccessState
            ?.takeIf { it.info.bvid == currentBvid }
            ?.info
            ?.pic
            .orEmpty()
        val seasonCover = resolveUgcSeasonEpisodeCoverUrl(
            ugcSeason = uiSuccessState?.info?.ugc_season,
            targetBvid = currentBvid,
            targetCid = currentBvidCid
        )
        resolveVideoPlayerSectionTarget(
            routeBvid = bvid,
            routeCoverUrl = coverUrl,
            currentBvid = currentBvid,
            switchedCoverUrl = listOf(
                pendingInPageSwitchCoverUrl,
                seasonCover,
                successCover
            ).firstOrNull { it.isNotBlank() }.orEmpty()
        )
    }
    val shouldSuppressSubtitleOverlay = suppressOverlayControlsForReturn || (
        useSharedPortraitPlayer &&
        !isPortraitFullscreen &&
        pendingMainReloadBvidAfterPortrait != null &&
        (
            pendingMainReloadBvidAfterPortrait != uiSuccessState?.info?.bvid ||
                (portraitSyncSnapshotCid > 0L && portraitSyncSnapshotCid != (uiSuccessState?.info?.cid ?: 0L))
            )
        )
    val showDanmakuDialog by viewModel.showDanmakuDialog.collectAsStateWithLifecycle()
    val isSendingDanmaku by viewModel.isSendingDanmaku.collectAsStateWithLifecycle()
    val composerDrafts by viewModel.composerDrafts.collectAsStateWithLifecycle()
    val danmakuSendPreferenceScope = rememberCoroutineScope()
    val rememberedDanmakuSendColor by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuSendColor(context)
        .collectAsStateWithLifecycle(initialValue = 16777215)
    val rememberedDanmakuSendMode by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuSendMode(context)
        .collectAsStateWithLifecycle(initialValue = 1)
    val rememberedDanmakuSendFontSize by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuSendFontSize(context)
        .collectAsStateWithLifecycle(initialValue = 25)
    val continuousPlayerUnitState = remember { mutableFloatStateOf(1f) }
    val continuousPlayerRenderer = rememberUpdatedState<@Composable (ContinuousPlayerHostLayout) -> Unit> { layout ->
        // 竖屏全屏 pager 接管共享播放器后，内联 host 必须退出 composition。
        if (!suppressPhoneDetailBodyForDirectPortrait && !isPortraitFullscreen) {
            PortraitInlineVideoPlayerHost(
            modifier = layout.modifier,
            animatedViewportWidth = layout.viewportWidth,
            contentTopInset = layout.contentTopInset,
            inlinePlayerAlpha = layout.alpha,
            inlinePlayerScale = layout.scale,
            isFullscreen = layout.isFullscreen,
            playerState = playerState,
            uiState = uiState,
            engagementState = engagementState,
            engagementActions = engagementActions,
            isPipMode = isPipMode,
            transitionEnabled = detailChildTransitionEnabled,
            transitionChromeAlphaProvider = videoCardDetailChromeAlphaProvider,
            danmakuHostActive = !hasCommittedRelatedVideoNavigation,
            suppressTransientOverlaysForTransition = suppressOverlayControlsForReturn,
            showPlaybackErrorNotice = detailContentState != null,
            onToggleFullscreen = { toggleFullscreen() },
            playbackActions = playbackActions,
            onDoubleTapLike = engagementViewModel::toggleLike,
            onBack = if (layout.isFullscreen) ({ toggleFullscreen() }) else handleBack,
            onHomeClick = {
                handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = true))
            },
            videoPlayerSectionTarget = videoPlayerSectionTarget,
            residentCoverSource = residentCoverSource,
            sponsorSegment = sponsorSegment,
            showSponsorSkipButton = showSponsorSkipButton,
            sponsorContributionState = sponsorContributionState,
            sleepTimerMinutes = sleepTimerMinutes,
            viewPoints = viewPoints,
            pbpProgressData = visiblePbpProgressData,
            sponsorProgressMarkers = sponsorProgressMarkers,
            isVerticalVideo = isVerticalVideo,
            onPortraitFullscreen = { enterPortraitFullscreen() },
            isPortraitFullscreen = isPortraitFullscreen,
            onPipClick = handlePipClick,
            codecPreference = codecPreference,
            secondCodecPreference = secondCodecPreference,
            audioQualityPreference = audioQualityPreference,
            onNavigateToAudioMode = {
                viewModel.setAudioMode(true)
                presentationState.markNavigatingToAudioMode()
                onNavigateToAudioMode()
            },
            forceCoverOnly = forceCoverOnlyForLiveSafeReturn ||
                shouldForceBackPreviewPlayerCover(
                    keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
                    bindLivePlayerForBackPreview = bindLivePlayerForBackPreview,
                ),
            preserveCurrentFrameOnFullscreenChange = preserveCurrentFrameOnFullscreenChange,
            liveBackPreview = bindLivePlayerForBackPreview,
            useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
            isPlayerSurfaceReady = isPlayerSurfaceReady,
            predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
            allowLivePlayerSharedElement = allowLivePlayerSharedElement,
            sourceRouteForSharedElement = sourceRouteForSharedElement,
            preserveSourceCardCornerDuringSharedReturn =
                detailShellSharedBoundsEnabled && useReturningVideoDetailVisualState,
            suppressSubtitleOverlay = shouldSuppressSubtitleOverlay,
            subtitleDisplayModePreferenceOverride = subtitleDisplayModeOverride,
            onSubtitleDisplayModePreferenceOverrideChange = { subtitleDisplayModeOverride = it },
            fullscreenExtras = ContinuousPlayerFullscreenExtras(
                danmakuComposerVisible = showDanmakuDialog,
                onDismissDanmakuComposer = viewModel::hideDanmakuSendDialog,
                onSendDanmakuComposer = viewModel::sendDanmaku,
                isSendingDanmakuComposer = isSendingDanmaku,
                danmakuComposerInitialText = composerDrafts.danmaku.text,
                danmakuComposerInitialAttentionCommand =
                    composerDrafts.danmaku.attentionCommand,
                danmakuComposerInitialColor = rememberedDanmakuSendColor,
                danmakuComposerInitialMode = rememberedDanmakuSendMode,
                danmakuComposerInitialFontSize = rememberedDanmakuSendFontSize,
                onDanmakuComposerDraftChange = viewModel::updateDanmakuDraft,
                onDanmakuComposerSelectionChange = { color, mode, fontSize ->
                    danmakuSendPreferenceScope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setDanmakuSendColor(context, color)
                        com.android.purebilibili.core.store.SettingsManager
                            .setDanmakuSendMode(context, mode)
                        com.android.purebilibili.core.store.SettingsManager
                            .setDanmakuSendFontSize(context, fontSize)
                    }
                },
                currentPlayMode = currentPlayMode,
                onPlayModeClick = { PlaylistManager.togglePlayMode() },
                onSaveCover = { viewModel.saveCover(context) },
                onDownloadAudio = { viewModel.downloadAudio(context) },
                relatedVideos = uiSuccessState?.related.orEmpty(),
                ugcSeason = uiSuccessState?.info?.ugc_season,
                isFollowed = engagementState.isFollowing,
                followStatusKnown = engagementState.followStatusKnown,
                isFollowSubmitting = engagementState.isFollowSubmitting,
                isTripleSubmitting = engagementState.isTripleSubmitting,
                isLiked = engagementState.isLiked,
                likeStatusKnown = engagementState.likeStatusKnown,
                isCoined = engagementState.coinCount > 0,
                isFavorited = engagementState.isFavorited,
                onToggleFollow = engagementViewModel::toggleFollow,
                onFollow = engagementActions.followOwner,
                onToggleLike = engagementViewModel::toggleLike,
                onDislike = viewModel::markVideoNotInterested,
                onCoin = engagementViewModel::openCoinDialog,
                onToggleFavorite = {
                    openFavoriteFolders(VideoFavoriteEntryPoint.FullscreenOverlay, false)
                },
                onTriple = engagementViewModel::doTripleAction,
                onRelatedVideoClick = navigateToRelatedVideo,
                onPageSelect = playbackActions.switchPage,
                hasFavoritePlaylist = isExternalPlaylist &&
                    externalPlaylistSource == ExternalPlaylistSource.FAVORITE &&
                    playlistItems.size > 1,
                onFavoritePlaylistClick = { showExternalPlaylistQueueSheet = true },
                onLandscapeCommentClick = {
                    landscapeCommentPanelVisible = !landscapeCommentPanelVisible
                },
                landscapeCommentPanelVisible = canShowLandscapeComments && landscapeCommentPanelVisible,
                landscapeCommentPanelOnLeft = landscapeCommentPanelOnLeft,
                landscapeCommentCloseProgressProvider = { landscapeCommentCloseProgress.floatValue },
            ),
        )
        }
    }
    val continuousPlayerContent = remember {
        movableContentOf<ContinuousPlayerHostLayout> { layout ->
            continuousPlayerRenderer.value(layout)
        }
    }
    // Android 16 ART 曾拒绝校验由 VideoDetailRouteSheetHost 尾随 lambda 生成的超大合成方法
    // （VerifyError: VideoDetailScreen$lambda$N(...BoxScope, Composer, int) 参数过多）。
    // 主布局与覆盖层必须使用两个内容槽，单个局部函数仍会捕获全部状态并生成百参数方法。
    // 分槽后 Compose 编译器会分别生成合成方法，避免一个方法聚合整页状态。
    @Composable
    fun BoxScope.VideoDetailRouteSheetMainContent() {
            // 📐 [平板适配] 全屏模式过渡动画（只有手机横屏才进入全屏）
        // 全屏没有二级内容：仅物理遮挡铰链才把媒体钳进首个安全 pane；
        // 软折痕（FOLD）允许跨整窗，否则半开姿态下下半屏整块留黑。
        val fullscreenOccludingHingePresent = isFullscreenMode &&
            appWindowAdaptiveInfo.foldingFeature.layoutHinges().any { it.isOccluding }
        // 系统小窗只承接播放面：PiP 窗口尺寸与详情页排版无关，内联竖屏页、平板分栏和竖屏流
        // 装饰塞进小窗都会错位，因此与横屏全屏共用同一层播放面。
        // 竖屏全屏由 PortraitVideoPager 持有播放面，保持挂载、由 pager 自己隐藏装饰层。
        val pipPlayerOnlyLayer = isPipMode && !isPortraitFullscreen
        if (isFullscreenMode || pipPlayerOnlyLayer) {
                val useInlineDanmakuComposer =
                    com.android.purebilibili.feature.video.ui.components.shouldUseInlineDanmakuComposer(
                        isFullscreenMode = isFullscreenMode
                    )
                if (continuousFullscreenTransitionEnabled) {
                    // 全屏＋物理遮挡铰链：媒体收进首个安全区，不跨缝；软折痕跨整窗。
                    if (fullscreenOccludingHingePresent) {
                        com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            primaryContent = {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    continuousPlayerContent(
                                        ContinuousPlayerHostLayout(
                                            modifier = Modifier.fillMaxSize(),
                                            viewportWidth = maxWidth,
                                            alpha = continuousPlayerUnitState,
                                            scale = continuousPlayerUnitState,
                                            isFullscreen = true,
                                        )
                                    )
                                }
                            },
                        )
                    } else {
                        continuousPlayerContent(
                            ContinuousPlayerHostLayout(
                                modifier = Modifier.fillMaxSize(),
                                viewportWidth = configuration.screenWidthDp.dp,
                                alpha = continuousPlayerUnitState,
                                scale = continuousPlayerUnitState,
                                isFullscreen = true,
                            )
                        )
                    }
                    val success = uiState as? VideoPlaybackUiState.Success
                    if (canShowLandscapeComments && landscapeCommentPanelVisible && success != null) {
                        LandscapeCommentPanel(
                            info = success.info, listState = landscapeCommentListState,
                            replies = commentState.replies, replyCount = commentState.replyCount,
                            emoteMap = success.emoteMap, isRepliesLoading = commentState.isRepliesLoading,
                            isRepliesRefreshing = commentState.isRepliesRefreshing,
                            repliesError = commentState.repliesError,
                            isRepliesEnd = commentState.isRepliesEnd, videoTags = success.videoTags,
                            voteCard = commentState.voteCard,
                            sortMode = commentState.sortMode,
                            currentMid = commentState.currentMid, showUpFlag = commentState.showUpFlag,
                            showIdentityDecorations = commentMemberDecorationsEnabled,
                            dissolvingIds = commentState.dissolvingIds, likedComments = commentState.likedComments,
                            onSortModeChange = commentActions.setSortMode,
                            onUpClick = navigateToUserSpaceFromVideo,
                            onSubReplyClick = commentActions.openSubReply,
                            onCommentReplyClick = playbackActions.replyTo, onLoadMoreReplies = commentActions.loadComments,
                            onRefreshReplies = commentActions.refreshComments,
                            onDeleteComment = commentActions.deleteComment, onDissolveStart = commentActions.startDissolve,
                            onCommentLike = commentActions.likeComment,
                            onCommentHate = commentActions.hateComment,
                            hatedComments = commentState.hatedComments,
                            onCommentUrlClick = openCommentUrl,
                            onReportComment = commentActions.reportComment,
                            onToggleTopComment = commentActions.toggleTopComment,
                            onCheckCommentFraud = commentActions.checkCommentFraud,
                            onTimestampClick = { position -> seekPlayerFromUserAction(playerState.player, position) },
                            onDismiss = {
                                commentActions.closeSubReply()
                                landscapeCommentPanelVisible = false
                            },
                            onSwitchSide = { landscapeCommentPanelOnLeft = !landscapeCommentPanelOnLeft },
                            isOnLeft = landscapeCommentPanelOnLeft,
                            closeProgress = landscapeCommentCloseProgress,
                            drawerWidth = com.android.purebilibili.feature.video.ui.overlay
                                .resolveLandscapeEndDrawerLayoutPolicy(configuration.screenWidthDp)
                                .drawerWidthDp.dp,
                            threadContent = if (subReplyState.visible && subReplyState.rootReply != null) {
                                { onImagePreview ->
                                    VideoInlineSubReplyDetailContent(
                                        state = subReplyState,
                                        commentState = commentState,
                                        emoteMap = success.emoteMap,
                                        maxTimestampMs = success.videoDurationMs.takeIf { it > 0L },
                                        onLoadMore = commentActions.loadMoreSubReplies,
                                        onRefresh = commentActions.refreshSubReplies,
                                        onSortModeChange = commentActions.setSubReplySortMode,
                                        onDismiss = commentActions.closeSubReply,
                                        onRootCommentClick = playbackActions.openRootCommentComposer,
                                        onTimestampClick = { positionMs ->
                                            seekPlayerFromUserAction(playerState.player, positionMs)
                                            commentActions.closeSubReply()
                                        },
                                        onImagePreview = onImagePreview,
                                        onReplyClick = playbackActions.replyTo,
                                        onConversationClick = commentActions.openSubReplyConversation,
                                        onConversationBack = commentActions.closeSubReplyConversation,
                                        onDissolveStart = commentActions.startSubDissolve,
                                        onDeleteComment = commentActions.deleteSubComment,
                                        onCheckCommentFraud = commentActions.checkCommentFraud,
                                        onCommentLike = commentActions.likeComment,
                                        onCommentHate = commentActions.hateComment,
                                        onReportComment = commentActions.reportComment,
                                        onUrlClick = openCommentUrl,
                                        showIdentityDecorations = commentMemberDecorationsEnabled,
                                        onAvatarClick = { mid ->
                                            mid.toLongOrNull()?.let(navigateToUserSpaceFromVideo) ?: Unit
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .align(if (landscapeCommentPanelOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    val fullscreenPlayerSection: @Composable () -> Unit = {
                        VideoPlayerSection(
                    state = VideoPlayerSectionState(
                        playerState = playerState,
                        uiState = uiState,
                        isFullscreen = true,
                        isInPipMode = isPipMode,
                        danmakuHostActive = !hasCommittedRelatedVideoNavigation,
                        suppressTransientOverlaysForTransition = suppressOverlayControlsForReturn,
                        transitionEnabled = detailChildTransitionEnabled,
                        landscapeCommentPanelVisible = canShowLandscapeComments && landscapeCommentPanelVisible,
                        landscapeCommentPanelOnLeft = landscapeCommentPanelOnLeft,
                landscapeCommentCloseProgressProvider = { landscapeCommentCloseProgress.floatValue },
                        danmakuComposerVisible = showDanmakuDialog && useInlineDanmakuComposer,
                        isSendingDanmakuComposer = isSendingDanmaku,
                        danmakuComposerInitialText = composerDrafts.danmaku.text,
                        danmakuComposerInitialAttentionCommand = composerDrafts.danmaku.attentionCommand,
                        danmakuComposerInitialColor = rememberedDanmakuSendColor,
                        danmakuComposerInitialMode = rememberedDanmakuSendMode,
                        danmakuComposerInitialFontSize = rememberedDanmakuSendFontSize,
                        bvid = videoPlayerSectionTarget.bvid,
                        coverUrl = videoPlayerSectionTarget.entryCoverUrl,
                        stationaryListCoverUrl = residentCoverSource?.url.orEmpty(),
                        stationaryListCoverCacheKey = residentCoverSource?.cacheKey.orEmpty(),
                        stationaryListCoverDecodeWidthPx = residentCoverSource?.decodeWidthPx ?: 0,
                        stationaryListCoverDecodeHeightPx = residentCoverSource?.decodeHeightPx ?: 0,
                        sharedElementBvid = videoPlayerSectionTarget.sharedElementBvid,
                        sponsorSegment = sponsorSegment,
                        showSponsorSkipButton = showSponsorSkipButton,
                        sponsorContributionState = sponsorContributionState,
                        cdnCount = (uiState as? VideoPlaybackUiState.Success)?.cdnCount ?: 1,
                        cdnLineDiagnostics = (uiState as? VideoPlaybackUiState.Success)
                            ?.cdnLineDiagnostics.orEmpty(),
                        isCdnProbing = (uiState as? VideoPlaybackUiState.Success)?.isCdnProbing ?: false,
                        isAudioOnly = false,
                        sleepTimerMinutes = sleepTimerMinutes,
                        videoshotData = (uiState as? VideoPlaybackUiState.Success)?.videoshotData,
                        viewPoints = viewPoints,
                        pbpProgressData = visiblePbpProgressData,
                        sponsorMarkers = sponsorProgressMarkers,
                        isVerticalVideo = isVerticalVideo,
                        isPortraitFullscreen = isPortraitFullscreen,
                        currentPlayMode = currentPlayMode,
                        relatedVideos = (uiState as? VideoPlaybackUiState.Success)?.related
                            ?: emptyList(),
                        ugcSeason = (uiState as? VideoPlaybackUiState.Success)?.info?.ugc_season,
                        isFollowed = engagementState.isFollowing,
                        followStatusKnown = engagementState.followStatusKnown,
                        isFollowSubmitting = engagementState.isFollowSubmitting,
                        isTripleSubmitting = engagementState.isTripleSubmitting,
                        isLiked = engagementState.isLiked,
                        likeStatusKnown = engagementState.likeStatusKnown,
                        isCoined = engagementState.coinCount > 0,
                        isFavorited = engagementState.isFavorited,
                        hasFavoritePlaylist = isExternalPlaylist &&
                            externalPlaylistSource == ExternalPlaylistSource.FAVORITE &&
                            playlistItems.size > 1,
                        forceCoverOnly = forceCoverOnlyForLiveSafeReturn,
                        preserveCurrentFrameOnFullscreenChange = preserveCurrentFrameOnFullscreenChange,
                        useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
                        isPlayerSurfaceReady = isPlayerSurfaceReady,
                        predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                        allowLivePlayerSharedElement = allowLivePlayerSharedElement,
                        sourceRouteForSharedElement = sourceRouteForSharedElement,
                        suppressSubtitleOverlay = shouldSuppressSubtitleOverlay,
                        subtitleDisplayModePreferenceOverride = subtitleDisplayModeOverride,
                        currentCodec = codecPreference,
                        currentSecondCodec = secondCodecPreference,
                        currentAudioQuality = audioQualityPreference,
                    ),
                    actions = VideoPlayerSectionActions(
                        resolveNextWatch = viewModel::resolveNextWatchSuggestion,
                        playNextWatch = viewModel::playNextWatchSuggestion,
                        onToggleFullscreen = { toggleFullscreen() },
                        onQualityChange = { qid -> viewModel.changeQuality(qid) },
                        onBack = { toggleFullscreen() },
                        onHomeClick = {
                            handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = true))
                        },
                        onLandscapeCommentClick = {
                            landscapeCommentPanelVisible = !landscapeCommentPanelVisible
                        },
                        onDanmakuInputClick = { viewModel.showDanmakuSendDialog() },
                        onDismissDanmakuComposer = { viewModel.hideDanmakuSendDialog() },
                        onSendDanmakuComposer = { message, color, mode, fontSize, encourage ->
                            viewModel.sendDanmaku(message, color, mode, fontSize, encourage)
                        },
                        onDanmakuComposerDraftChange = viewModel::updateDanmakuDraft,
                        onDanmakuComposerSelectionChange = { color, mode, fontSize ->
                            danmakuSendPreferenceScope.launch {
                                com.android.purebilibili.core.store.SettingsManager.setDanmakuSendColor(context, color)
                                com.android.purebilibili.core.store.SettingsManager.setDanmakuSendMode(context, mode)
                                com.android.purebilibili.core.store.SettingsManager.setDanmakuSendFontSize(context, fontSize)
                            }
                        },
                        onDoubleTapLike = { engagementViewModel.toggleLike() },
                        onSponsorSkip = { viewModel.skipCurrentSponsorSegment() },
                        onSponsorDismiss = { viewModel.dismissSponsorSkipButton() },
                        onSponsorVote = viewModel::voteCurrentSponsorSegment,
                        onSponsorContributionMarkBoundary = viewModel::markSponsorContributionBoundary,
                        onSponsorContributionMarkWholeVideo = viewModel::markWholeVideoAsSponsor,
                        onSponsorContributionCategoryChange = viewModel::setSponsorContributionCategory,
                        onSponsorContributionActionTypeChange = viewModel::setSponsorContributionActionType,
                        onSponsorContributionSubmit = viewModel::submitSponsorContribution,
                        onSponsorContributionCancel = viewModel::cancelSponsorContribution,
                        onReloadVideo = { viewModel.reloadVideo() },
                        onSwitchCdn = { viewModel.switchCdn() },
                        onSwitchCdnTo = { viewModel.switchCdnTo(it) },
                        onProbeCdnCandidates = { viewModel.probeCurrentCdnCandidates() },
                        onCodecChange = { viewModel.setVideoCodec(it) },
                        onSecondCodecChange = { viewModel.setVideoSecondCodec(it) },
                        onAudioQualityChange = { viewModel.setAudioQuality(it) },
                        onPlaybackSpeedChange = { viewModel.applyPlaybackSpeedFromUi(it) },
                        onAudioLangChange = { viewModel.changeAudioLanguage(it) },
                        onAudioOnlyToggle = {
                            viewModel.setAudioMode(true)
                            presentationState.markNavigatingToAudioMode()
                            onNavigateToAudioMode()
                        },
                        onSleepTimerChange = { viewModel.setSleepTimer(it) },
                        onUserSeek = { position -> viewModel.notifyPluginsOfExplicitSeek(position) },
                        onPortraitFullscreen = {
                            if (allowStandalonePortraitExperience) {
                                if (!isPortraitFullscreen) {
                                    if (isFullscreenMode) {
                                        toggleFullscreen()
                                    }
                                    enterPortraitFullscreen()
                                } else {
                                    presentationState.setPortraitFullscreen(false)
                                }
                            }
                        },
                        onPlayModeClick = {
                            com.android.purebilibili.feature.video.player.PlaylistManager.togglePlayMode()
                        },
                        onSaveCover = { viewModel.saveCover(context) },
                        onDownloadAudio = { viewModel.downloadAudio(context) },
                        onToggleFollow = { engagementViewModel.toggleFollow() },
                        onFollow = engagementActions.followOwner,
                        onToggleLike = { engagementViewModel.toggleLike() },
                        onDislike = { viewModel.markVideoNotInterested() },
                        onCoin = { engagementViewModel.openCoinDialog() },
                        onToggleFavorite = {
                            openFavoriteFolders(VideoFavoriteEntryPoint.FullscreenOverlay, false)
                        },
                        onTriple = { engagementViewModel.doTripleAction() },
                        onRelatedVideoClick = navigateToRelatedVideo,
                        onPageSelect = playbackActions.switchPage,
                        onFavoritePlaylistClick = {
                            showExternalPlaylistQueueSheet = true
                        },
                        onSubtitleDisplayModePreferenceOverrideChange = {
                            subtitleDisplayModeOverride = it
                        },
                        onSubtitleTrackSelected = viewModel::selectSubtitleTrack,
                    ),
                    )
                    }
                    // 全屏＋物理遮挡铰链：媒体收进首个安全区，不跨缝；软折痕跨整窗。
                    if (fullscreenOccludingHingePresent) {
                        com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            primaryContent = fullscreenPlayerSection,
                        )
                    } else {
                        fullscreenPlayerSection()
                    }
                    val success = uiState as? VideoPlaybackUiState.Success
                    if (canShowLandscapeComments && landscapeCommentPanelVisible && success != null) {
                        LandscapeCommentPanel(
                            info = success.info,
                            listState = landscapeCommentListState,
                            replies = commentState.replies,
                            replyCount = commentState.replyCount,
                            emoteMap = success.emoteMap,
                            isRepliesLoading = commentState.isRepliesLoading,
                            isRepliesRefreshing = commentState.isRepliesRefreshing,
                            repliesError = commentState.repliesError,
                            isRepliesEnd = commentState.isRepliesEnd,
                            voteCard = commentState.voteCard,
                            videoTags = success.videoTags,
                            sortMode = commentState.sortMode,
                            currentMid = commentState.currentMid,
                            showUpFlag = commentState.showUpFlag,
                            showIdentityDecorations = commentMemberDecorationsEnabled,
                            dissolvingIds = commentState.dissolvingIds,
                            likedComments = commentState.likedComments,
                            onSortModeChange = commentActions.setSortMode,
                            onUpClick = navigateToUserSpaceFromVideo,
                            onSubReplyClick = commentActions.openSubReply,
                            onCommentReplyClick = playbackActions.replyTo,
                            onLoadMoreReplies = commentActions.loadComments,
                            onRefreshReplies = commentActions.refreshComments,
                            onDeleteComment = commentActions.deleteComment,
                            onDissolveStart = commentActions.startDissolve,
                            onCommentLike = commentActions.likeComment,
                            onCommentHate = commentActions.hateComment,
                            hatedComments = commentState.hatedComments,
                            onCommentUrlClick = openCommentUrl,
                            onReportComment = commentActions.reportComment,
                            onToggleTopComment = commentActions.toggleTopComment,
                            onCheckCommentFraud = commentActions.checkCommentFraud,
                            onTimestampClick = { position -> seekPlayerFromUserAction(playerState.player, position) },
                            onDismiss = {
                                commentActions.closeSubReply()
                                landscapeCommentPanelVisible = false
                            },
                            onSwitchSide = { landscapeCommentPanelOnLeft = !landscapeCommentPanelOnLeft },
                            isOnLeft = landscapeCommentPanelOnLeft,
                            closeProgress = landscapeCommentCloseProgress,
                            drawerWidth = com.android.purebilibili.feature.video.ui.overlay
                                .resolveLandscapeEndDrawerLayoutPolicy(configuration.screenWidthDp)
                                .drawerWidthDp.dp,
                            threadContent = if (subReplyState.visible && subReplyState.rootReply != null) {
                                { onImagePreview ->
                                    VideoInlineSubReplyDetailContent(
                                        state = subReplyState,
                                        commentState = commentState,
                                        emoteMap = success.emoteMap,
                                        maxTimestampMs = success.videoDurationMs.takeIf { it > 0L },
                                        onLoadMore = commentActions.loadMoreSubReplies,
                                        onRefresh = commentActions.refreshSubReplies,
                                        onSortModeChange = commentActions.setSubReplySortMode,
                                        onDismiss = commentActions.closeSubReply,
                                        onRootCommentClick = playbackActions.openRootCommentComposer,
                                        onTimestampClick = { positionMs ->
                                            seekPlayerFromUserAction(playerState.player, positionMs)
                                            commentActions.closeSubReply()
                                        },
                                        onImagePreview = onImagePreview,
                                        onReplyClick = playbackActions.replyTo,
                                        onConversationClick = commentActions.openSubReplyConversation,
                                        onConversationBack = commentActions.closeSubReplyConversation,
                                        onDissolveStart = commentActions.startSubDissolve,
                                        onDeleteComment = commentActions.deleteSubComment,
                                        onCheckCommentFraud = commentActions.checkCommentFraud,
                                        onCommentLike = commentActions.likeComment,
                                        onCommentHate = commentActions.hateComment,
                                        onReportComment = commentActions.reportComment,
                                        onUrlClick = openCommentUrl,
                                        showIdentityDecorations = commentMemberDecorationsEnabled,
                                        onAvatarClick = { mid ->
                                            mid.toLongOrNull()?.let(navigateToUserSpaceFromVideo) ?: Unit
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .align(
                                    if (landscapeCommentPanelOnLeft) Alignment.CenterStart else Alignment.CenterEnd
                                )
                                .fillMaxHeight(),
                        )
                    }
                }

            } else {
                    //  沉浸式布局：视频延伸到状态栏 + 内容区域
                    //  📐 [大屏适配] 仅 Expanded 使用分栏布局

                    // Full-viewport host for Miuix landing chrome (stacked home + side-by-side
                    // related). Anchors are entry-space; never nest under the player column.
                    val largeScreenGeometry = remember { LargeScreenVideoTransitionGeometry() }
                    val largeScreenTransition = com.android.purebilibili.core.ui.transition
                        .LocalMiuixVideoCardTransitionState.current
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned(largeScreenGeometry::recordRoot)) {
                    // Shared movable player content for tablet and large screen.
                    // onPortraitFullscreen = { enterPortraitFullscreen() }
                    val continuousPlayerSlot: @Composable (Modifier) -> Unit = remember(
                        continuousPlayerContent, continuousPlayerUnitState,
                        largeScreenGeometry, largeScreenTransition, videoCardDepthBackgroundState,
                        returnMediaGeometry,
                        entryOwnsMiuixCardTransition, isCommittedCardReturn, residentCoverSource,
                        uiState is VideoPlaybackUiState.Loading, hasRenderedFirstFrameForReturn,
                    ) {
                        { playerModifier ->
                            LargeScreenVideoPlayerTransitionHost(
                                geometry = largeScreenGeometry,
                                mediaGeometry = returnMediaGeometry,
                                transition = largeScreenTransition.copy(
                                    enabled = largeScreenTransition.enabled && entryOwnsMiuixCardTransition,
                                ),
                                phaseProvider = videoCardDepthBackgroundState.phaseProvider,
                                isReturningProvider = {
                                    videoCardDepthBackgroundState.isReturnGestureInProgressProvider() ||
                                        videoCardDepthBackgroundState.isGestureRestoreInProgressProvider()
                                },
                                isCommittedReturn = isCommittedCardReturn,
                                coverSource = residentCoverSource,
                                hasRenderedFirstFrame = hasRenderedFirstFrameForReturn,
                                detailContentLoading = uiState is VideoPlaybackUiState.Loading,
                                followProgressEnabledProvider = videoCardDepthBackgroundState
                                    .returnContentFollowProgressEnabledProvider,
                                modifier = playerModifier,
                            ) { viewportWidth ->
                                continuousPlayerContent(
                                    ContinuousPlayerHostLayout(
                                        modifier = Modifier.fillMaxSize(),
                                        viewportWidth = viewportWidth,
                                        alpha = continuousPlayerUnitState,
                                        scale = continuousPlayerUnitState,
                                        isFullscreen = false,
                                        contentTopInset = 0.dp,
                                    )
                                )
                            }
                        }
                    }
                    //  📐 [大屏适配] 根据设备类型选择布局
                    if (useTabletLayout) {
                        if (appWindowAdaptiveInfo.shouldAvoidHinge) {
                            // 半开折痕和展开后的实体铰链都按实际安全区域切分。
                            TabletVideoLayout(
                                playerState = playerState,
                                uiState = uiState,
                                detailContentState = detailContentState,
                                commentState = commentState,
                                engagementState = engagementState,
                                subReplyState = subReplyState,
                                downloadProgress = downloadProgress,
                                commentMemberDecorationsEnabled = commentMemberDecorationsEnabled,
                                playbackActions = playbackActions,
                                engagementActions = engagementActions,
                                commentActions = commentActions,
                                configuration = configuration,
                                isVerticalVideo = isVerticalVideo,
                                sleepTimerMinutes = sleepTimerMinutes,
                                viewPoints = viewPoints,
                                pbpProgressData = visiblePbpProgressData,
                                bvid = bvid,
                                danmakuManager = sharedDanmakuManager,
                                coverUrl = coverUrl,
                                onBack = { handleBack() },
                                onUpClick = navigateToUserSpaceFromVideo,
                                onNavigateToAudioMode = {
                                    presentationState.markNavigatingToAudioMode()
                                    onNavigateToAudioMode()
                                },
                                onToggleFullscreen = { toggleFullscreen() },
                                onPortraitFullscreen = {
                                    if (allowStandalonePortraitExperience) {
                                        if (!isPortraitFullscreen) {
                                            if (isFullscreenMode) toggleFullscreen()
                                            enterPortraitFullscreen()
                                        } else {
                                            presentationState.setPortraitFullscreen(false)
                                        }
                                    }
                                },
                                isInPipMode = isPipMode,
                                onPipClick = handlePipClick,
                                isPortraitFullscreen = isPortraitFullscreen,
                                onHomeClick = {
                                    handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = true))
                                },
                                currentCodec = codecPreference,
                                onCodecChange = { viewModel.setVideoCodec(it) },
                                currentSecondCodec = secondCodecPreference,
                                onSecondCodecChange = { viewModel.setVideoSecondCodec(it) },
                                currentAudioQuality = audioQualityPreference,
                                onAudioQualityChange = { viewModel.setAudioQuality(it) },
                                transitionEnabled = detailChildTransitionEnabled,
                                onRelatedVideoClick = navigateToRelatedVideo,
                                onBgmClick = onBgmClick,
                                showUpBadge = homeUpBadgesVisible,
                                onSearchKeywordClick = navigateToSearchKeywordFromVideo,
                                onOpenBilibiliLink = onOpenBilibiliLink,
                                currentPlayMode = currentPlayMode,
                                onPlayModeClick = {
                                    com.android.purebilibili.feature.video.player.PlaylistManager.togglePlayMode()
                                },
                                forceCoverOnlyOnReturn = forceCoverOnlyForLiveSafeReturn,
                                predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                                liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                                isPlayerSurfaceReady = isPlayerSurfaceReady,
                                paneControlsEnabled = isTransitionFinished,
                                videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
                                videoNoteEnabled = videoNoteEnabled,
                                videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
                                playerContent = continuousPlayerSlot,
                                transitionChromeAlphaProvider = videoCardDetailChromeAlphaProvider,
                            )
                        } else {
                            LargeScreenVideoLayout(
                            playerState = playerState,
                            uiState = uiState,
                            detailContentState = detailContentState,
                            commentState = commentState,
                            engagementState = engagementState,
                            subReplyState = subReplyState,
                            downloadProgress = downloadProgress,
                            commentMemberDecorationsEnabled = commentMemberDecorationsEnabled,
                            playbackActions = playbackActions,
                            engagementActions = engagementActions,
                            commentActions = commentActions,
                            configuration = configuration,
                            isVerticalVideo = isVerticalVideo,
                            sleepTimerMinutes = sleepTimerMinutes,
                            viewPoints = viewPoints,
                            pbpProgressData = visiblePbpProgressData,
                            bvid = bvid,
                            danmakuManager = sharedDanmakuManager,
                            coverUrl = coverUrl,
                            onBack = {
                                com.android.purebilibili.core.util.Logger.d(
                                    "VideoDetailScreen",
                                    "📱 Calling handleBack()"
                                )
                                handleBack()
                            },
                            onUpClick = navigateToUserSpaceFromVideo,
                            onBgmClick = onBgmClick,
                            onNavigateToAudioMode = {
                                presentationState.markNavigatingToAudioMode()
                                onNavigateToAudioMode()
                            },
                            onToggleFullscreen = { toggleFullscreen() },  // 📺 平板全屏切换
                            onPortraitFullscreen = {
                                if (allowStandalonePortraitExperience) {
                                    if (!isPortraitFullscreen) {
                                        if (isFullscreenMode) toggleFullscreen()
                                        enterPortraitFullscreen()
                                    } else {
                                        presentationState.setPortraitFullscreen(false)
                                    }
                                }
                            },
                            isInPipMode = isPipMode,
                            onPipClick = handlePipClick,
                            isPortraitFullscreen = isPortraitFullscreen,
                            onHomeClick = {
                                handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = true))
                            },

                            transitionEnabled = detailChildTransitionEnabled,
                            currentCodec = codecPreference,
                            onCodecChange = { viewModel.setVideoCodec(it) },
                            currentSecondCodec = secondCodecPreference,
                            onSecondCodecChange = { viewModel.setVideoSecondCodec(it) },
                            currentAudioQuality = audioQualityPreference,
                            onAudioQualityChange = { viewModel.setAudioQuality(it) },
                            onRelatedVideoClick = navigateToRelatedVideo,
                            showUpBadge = homeUpBadgesVisible,
                            onSearchKeywordClick = navigateToSearchKeywordFromVideo,
                            onOpenBilibiliLink = onOpenBilibiliLink,
                            // 🔁 [新增] 播放模式
                            currentPlayMode = currentPlayMode,
                            onPlayModeClick = { com.android.purebilibili.feature.video.player.PlaylistManager.togglePlayMode() },
                            forceCoverOnlyOnReturn = forceCoverOnlyForLiveSafeReturn,
                            predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                            liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                            isPlayerSurfaceReady = isPlayerSurfaceReady,
                            paneControlsEnabled = isTransitionFinished,
                            videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
                            videoNoteEnabled = videoNoteEnabled,
                            videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
                            playerContent = continuousPlayerSlot,
                            transitionChromeAlphaProvider = videoCardDetailChromeAlphaProvider,
                            )
                        }
                    } else {
                        // 📱 手机竖屏：原有单列布局
                        val stableStatusBarHeight = resolveVideoDetailStableStatusBarHeightDp(
                            visibleStatusBarHeightDp = WindowInsets.statusBars
                                .asPaddingValues()
                                .calculateTopPadding()
                                .value,
                            statusBarIgnoringVisibilityHeightDp = WindowInsets.statusBarsIgnoringVisibility
                                .asPaddingValues()
                                .calculateTopPadding()
                                .value,
                            hideStatusBars = systemBarsVisibilityPolicy.hideStatusBars
                        ).dp
                        val playerTopInset = resolveVideoDetailPortraitPlayerTopInsetDp(
                            stableStatusBarHeightDp = stableStatusBarHeight.value,
                            hideStatusBars = systemBarsVisibilityPolicy.hideStatusBars,
                            immersiveStatusBarBackdropEnabled = true,
                            isSharedCardTransition = detailShellSharedBoundsEnabled,
                        ).dp
                        val screenWidthDp = configuration.screenWidthDp.dp
                        val screenHeightDp = configuration.screenHeightDp.dp
                        val videoHeight = resolvePhoneInlineVideoViewportHeightDp(
                            windowWidthDp = configuration.screenWidthDp.toFloat(),
                            windowHeightDp = configuration.screenHeightDp.toFloat(),
                            isFoldableCoverWindow = displayContext.isFoldableCoverWindow,
                        ).dp
                        val playerChromeProfile = rememberAppPlayerChromeProfile()
                        val liquidGlassTabMotion = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
                        val videoContentTabSwitchAnimationSpec = remember(playerChromeProfile.tabPresentation, liquidGlassTabMotion) {
                            resolveVideoContentTabSwitchAnimationSpec(playerChromeProfile.tabPresentation, liquidGlassTabMotion)
                        }

                        //  读取竖屏播放器滚动缩小模式
                        val portraitPlayerCollapseMode by com.android.purebilibili.core.store.SettingsManager
                            .getPortraitPlayerCollapseMode(context)
                            .collectAsStateWithLifecycle(initialValue = PortraitPlayerCollapseMode.OFF
            )
                        var autoPausedByPlayerCollapse by remember(currentBvid) { mutableStateOf(false) }
                        DisposableEffect(playerState.player, currentBvid) {
                            val player = playerState.player
                            val listener = object : Player.Listener {
                                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                                    // A later manual pause must regain the normal paused layout.
                                    if (playWhenReady) autoPausedByPlayerCollapse = false
                                }
                            }
                            player.addListener(listener)
                            onDispose { player.removeListener(listener) }
                        }
                        // 自动暂停只改变播放状态，不切换手势范围、评论隐藏终点或紧凑布局。
                        val isPlaybackPausedForInlineLayout = resolveIsPlaybackPausedForInlineLayout(
                            isPlaybackPaused = isPlaybackPaused,
                            wasAutoPausedByCollapse = autoPausedByPlayerCollapse,
                        )
                        val hidePausedPlayerForComments = !isPortraitFullscreen &&
                            shouldHidePausedInlinePlayerForComments(
                                selectedTabIndex = selectedVideoContentTabIndex,
                                isPlaybackPaused = isPlaybackPausedForInlineLayout,
                                isCommentThreadVisible = subReplyState.visible,
                            )
                        val inlinePortraitScrollEnabled = shouldEnableInlinePortraitScrollTransform(
                            collapseMode = portraitPlayerCollapseMode,
                            selectedTabIndex = selectedVideoContentTabIndex,
                            isVerticalVideo = isVerticalVideo,
                            isPlaybackPaused = isPlaybackPausedForInlineLayout,
                            isCommentThreadVisible = subReplyState.visible,
                        )
                        val inlinePlayerCollapseState = rememberInlinePortraitPlayerCollapseState(currentBvid)
                        // 评论区「一键回顶」时恢复被压缩的播放器(共享事件)。
                        LaunchedEffect(Unit) {
                            commentBackToTopRestoreFlow.collect {
                                inlinePlayerCollapseState.restore()
                            }
                        }
                        val expandedPortraitInlineSpec = remember(
                            configuration.screenWidthDp,
                            configuration.screenHeightDp,
                            displayContext.isFoldableCoverWindow,
                        ) {
                            resolvePortraitInlinePlayerLayoutSpec(
                                screenWidthDp = configuration.screenWidthDp.toFloat(),
                                screenHeightDp = configuration.screenHeightDp.toFloat(),
                                isCollapsed = false,
                                isFoldableCoverWindow = displayContext.isFoldableCoverWindow,
                            )
                        }
                        val compactInlinePlayerForCommentTab =
                            shouldUseCompactInlinePortraitPlayerForCommentTab(
                                useOfficialInlinePortraitDetailExperience = useOfficialInlinePortraitDetailExperience,
                                selectedTabIndex = selectedVideoContentTabIndex,
                                isPortraitFullscreen = isPortraitFullscreen,
                                isCommentThreadVisible = subReplyState.visible,
                                collapseMode = portraitPlayerCollapseMode,
                                isVerticalVideo = isVerticalVideo,
                                isPlaybackPaused = isPlaybackPausedForInlineLayout,
                            )
                        // 📏 [Collapsing Player] 上滑隐藏播放器逻辑
                        val collapsedPortraitInlineSpec = remember(
                            configuration.screenWidthDp,
                            configuration.screenHeightDp,
                            displayContext.isFoldableCoverWindow,
                        ) {
                            resolvePortraitInlinePlayerLayoutSpec(
                                screenWidthDp = configuration.screenWidthDp.toFloat(),
                                screenHeightDp = configuration.screenHeightDp.toFloat(),
                                isCollapsed = true,
                                isFoldableCoverWindow = displayContext.isFoldableCoverWindow,
                            )
                        }
                        val expandedViewportHeight = when {
                            suppressPhoneDetailBodyForDirectPortrait -> screenHeightDp
                            useOfficialInlinePortraitDetailExperience -> expandedPortraitInlineSpec.heightDp.dp
                            else -> videoHeight
                        }
                        val standardCollapsedViewportHeight = when {
                            useOfficialInlinePortraitDetailExperience -> collapsedPortraitInlineSpec.heightDp.dp
                            portraitPlayerCollapseMode != PortraitPlayerCollapseMode.OFF -> 56.dp
                            else -> 0.dp
                        }
                        val collapsedViewportHeight = resolveInlinePlayerCollapsedViewportHeightDp(
                            standardCollapsedHeightDp = standardCollapsedViewportHeight.value,
                            hidePausedPlayerForComments = hidePausedPlayerForComments,
                        ).dp
                        // 两种竖屏内联体验共用同一套位移语义：手势范围始终等于
                        // “展开高度 - 当前折叠终点”，高度才能只由共享位移推导且保持连续。
                        val collapseRangePx = with(LocalDensity.current) {
                            (expandedViewportHeight - collapsedViewportHeight)
                                .toPx()
                                .coerceAtLeast(0f)
                        }
                        // Hiding increases the gesture range, not the auto-pause threshold;
                        // otherwise an automatic pause would look like an expansion and resume itself.
                        val autoPauseCollapseRangePx = with(LocalDensity.current) {
                            (expandedViewportHeight - standardCollapsedViewportHeight)
                                .toPx()
                                .coerceAtLeast(0f)
                        }
                        // 简介与评论共享手势高度；横向切页和列表历史位置不得改写折叠位移。
                        // 关闭折叠时动画展开；仅因页签的折叠终点变小而超界的位移只做收回，
                        // 由高度动画过渡，不再整段还原成展开态。
                        LaunchedEffect(inlinePlayerCollapseState, inlinePortraitScrollEnabled, collapseRangePx) {
                            inlinePlayerCollapseState.reconcileLayout(
                                collapseEnabled = inlinePortraitScrollEnabled,
                                collapseRangePx = collapseRangePx,
                            )
                        }
                        var previousTrackedCollapseOffsetPx by remember(currentBvid) {
                            mutableFloatStateOf(inlinePlayerCollapseState.offsetPx)
                        }
                        var collapseMotionSignalActive by remember(currentBvid) {
                            mutableStateOf(false)
                        }
                        LaunchedEffect(inlinePortraitScrollEnabled, inlinePlayerCollapseState.offsetPx) {
                            val currentOffsetPx = inlinePlayerCollapseState.offsetPx
                            val offsetMoved = shouldTrackVideoDetailCollapseMotion(
                                inlinePortraitScrollEnabled = inlinePortraitScrollEnabled,
                                previousOffsetPx = previousTrackedCollapseOffsetPx,
                                currentOffsetPx = currentOffsetPx,
                            )
                            previousTrackedCollapseOffsetPx = currentOffsetPx
                            collapseMotionSignalActive = offsetMoved
                            if (offsetMoved) {
                                kotlinx.coroutines.delay(VIDEO_DETAIL_COLLAPSE_SIGNAL_IDLE_TIMEOUT_MS)
                                collapseMotionSignalActive = false
                            }
                        }
                        TrackJankStateFlag(
                            stateName = "video_detail:player_swipe_collapse",
                            isActive = collapseMotionSignalActive,
                        )
                        // 暂停隐藏播放器时视口高度为 0，但折叠位移不会走完全程；
                        // 顶部 chrome（渐进模糊/纯色底）需要按"已折叠"处理，否则评论区顶栏透明。
                        val isPlayerCollapsed by remember(
                            inlinePortraitScrollEnabled,
                            collapseRangePx,
                            hidePausedPlayerForComments,
                        ) {
                            derivedStateOf {
                                hidePausedPlayerForComments || resolveIsPlayerCollapsed(
                                    swipeHidePlayerEnabled = inlinePortraitScrollEnabled,
                                    playerHeightOffsetPx = inlinePlayerCollapseState.offsetPx,
                                    videoHeightPx = collapseRangePx
                                )
                            }
                        }

                        // 折叠完全由 nestedScroll 接管，切页不再按简介列表阈值强制同步高度。
                        val skipGesturePlayerCollapse = shouldSkipGesturePlayerCollapseForLayout(
                            compactForIntroScroll = false,
                            compactForCommentTab = compactInlinePlayerForCommentTab,
                            hidePausedPlayerForComments = hidePausedPlayerForComments,
                            restoreRequested = inlinePlayerCollapseState.restoreRequested,
                        )

                        // 缩小浏览相关/评论时自动暂停（设置默认开启）；仅恢复本功能触发的暂停。
                        val pauseOnPlayerCollapseEnabled by com.android.purebilibili.core.store.SettingsManager
                            .getPauseOnPlayerCollapseEnabled(context)
                            .collectAsStateWithLifecycle(initialValue = true)
                        val playerCollapsedForAutoPause by remember(
                            inlinePortraitScrollEnabled,
                            autoPauseCollapseRangePx,
                            compactInlinePlayerForCommentTab,
                        ) {
                            derivedStateOf {
                                // 暂停会改变手势消费规则，但不会解除评论紧凑布局；两者不能共用判定。
                                resolvePlayerCollapsedForAutoPause(
                                    swipeHidePlayerEnabled = inlinePortraitScrollEnabled,
                                    playerHeightOffsetPx = inlinePlayerCollapseState.offsetPx,
                                    videoHeightPx = autoPauseCollapseRangePx,
                                    compactForCommentTab = compactInlinePlayerForCommentTab,
                                    restoreRequested = inlinePlayerCollapseState.restoreRequested,
                                )
                            }
                        }
                        LaunchedEffect(
                            playerCollapsedForAutoPause,
                            pauseOnPlayerCollapseEnabled,
                            isPortraitFullscreen,
                            currentBvid,
                        ) {
                            val player = playerState.player
                            if (shouldAutoPauseOnPlayerCollapse(
                                    autoPauseEnabled = pauseOnPlayerCollapseEnabled,
                                    isPlayerCollapsed = playerCollapsedForAutoPause,
                                    isPlaying = player.isPlaying,
                                    isPortraitFullscreen = isPortraitFullscreen,
                                )
                            ) {
                                autoPausedByPlayerCollapse = true
                                player.pause()
                                player.playWhenReady = false
                                com.android.purebilibili.core.util.Logger.d(
                                    "VideoDetailScreen",
                                    "⏸️ 折叠播放器自动暂停 bvid=$currentBvid"
                                )
                            } else if (
                                shouldAutoResumeOnPlayerExpand(
                                    autoPauseEnabled = pauseOnPlayerCollapseEnabled,
                                    isPlayerCollapsed = playerCollapsedForAutoPause,
                                    wasAutoPausedByCollapse = autoPausedByPlayerCollapse,
                                    isPortraitFullscreen = isPortraitFullscreen,
                                )
                            ) {
                                autoPausedByPlayerCollapse = false
                                player.play()
                                com.android.purebilibili.core.util.Logger.d(
                                    "VideoDetailScreen",
                                    "▶️ 折叠播放器展开后自动继续播放 bvid=$currentBvid"
                                )
                            } else if (!playerCollapsedForAutoPause) {
                                autoPausedByPlayerCollapse = false
                            }
                        }
                        val nestedScrollConnection = remember(
                            inlinePortraitScrollEnabled,
                            isPortraitFullscreen,
                            inlinePlayerCollapseState,
                            skipGesturePlayerCollapse,
                            collapseRangePx,
                        ) {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    val scrollUpdate = reduceVideoDetailPreScroll(
                                        currentOffsetPx = inlinePlayerCollapseState.offsetPx,
                                        deltaPx = available.y,
                                        // 切页后位移可能暂时超出新范围；先允许它从当前位置
                                        // 平滑回到合法范围，避免手势第一帧被硬夹到新终点。
                                        minOffsetPx = minOf(
                                            -collapseRangePx,
                                            inlinePlayerCollapseState.offsetPx,
                                        ),
                                        inlinePortraitScrollEnabled = inlinePortraitScrollEnabled,
                                        isPortraitFullscreen = isPortraitFullscreen,
                                        layoutAlreadyCollapsed = skipGesturePlayerCollapse,
                                    )
                                    return Offset(0f, inlinePlayerCollapseState.consumeScroll(scrollUpdate))
                                }

                                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                    val scrollUpdate = reduceVideoDetailPostScroll(
                                        currentOffsetPx = inlinePlayerCollapseState.offsetPx,
                                        deltaPx = available.y,
                                        minOffsetPx = minOf(
                                            -collapseRangePx,
                                            inlinePlayerCollapseState.offsetPx,
                                        ),
                                        inlinePortraitScrollEnabled = inlinePortraitScrollEnabled,
                                        isPortraitFullscreen = isPortraitFullscreen,
                                        layoutAlreadyCollapsed = skipGesturePlayerCollapse,
                                    )
                                    return Offset(0f, inlinePlayerCollapseState.consumeScroll(scrollUpdate))
                                }
                            }
                        }
                        DisposableEffect(nestedScrollConnection) {
                            commentThreadPlayerScrollConnection = nestedScrollConnection
                            onDispose {
                                if (commentThreadPlayerScrollConnection === nestedScrollConnection) {
                                    commentThreadPlayerScrollConnection = null
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                        ) {

                        //  播放器隐藏状态（用于动画）
                        //  播放器隐藏状态（用于动画）
                        //  当 playerHeightOffsetPx 为 -videoHeightPx 时，高度只剩 statusBarHeight
                        //  [Fix] 竖屏全屏模式下强制高度不受偏移影响
                        val playerHeightOffset = if (isPortraitFullscreen) {
                            0f
                        } else {
                            inlinePlayerCollapseState.offsetPx
                        }
                        val collapseProgress = resolveVideoDetailCollapseProgress(
                            playerHeightOffsetPx = playerHeightOffset,
                            collapseRangePx = collapseRangePx,
                            isPortraitFullscreen = isPortraitFullscreen
                        )
                        val commentTabCollapseProgress by animateFloatAsState(
                            targetValue = if (compactInlinePlayerForCommentTab) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = resolveInlinePortraitPlayerCommentCollapseDurationMillis(
                                    videoContentTabSwitchAnimationSpec
                                ),
                                easing = FastOutSlowInEasing
                            ),
                            label = "inline_portrait_comment_tab_collapse"
                        )
                        val effectiveCollapseProgress = resolveInlinePortraitPlayerCollapseProgress(
                            manualCollapseProgress = collapseProgress,
                            compactForCommentTabProgress = commentTabCollapseProgress,
                            restoreRequested = inlinePlayerCollapseState.restoreRequested,
                            hidePausedPlayerForComments = hidePausedPlayerForComments,
                        )
                        // Drag/scroll collapse stays directly coupled to the finger. Only an
                        // An explicit restore (for example comment back-to-top) eases the player from
                        // the compact viewport back to its full size instead of jumping in one frame.
                        val animatedCollapseProgress by animateFloatAsState(
                            targetValue = effectiveCollapseProgress,
                            animationSpec = if (
                                inlinePlayerCollapseState.restoreRequested ||
                                    inlinePlayerCollapseState.layoutReconciled
                            ) {
                                tween(
                                    durationMillis = resolveInlinePortraitPlayerCommentCollapseDurationMillis(
                                        videoContentTabSwitchAnimationSpec
                                    ),
                                    easing = FastOutSlowInEasing
                                )
                            } else {
                                snap()
                            },
                            label = "inline_portrait_player_restore"
                        )
                        // 下滑折叠后返回：布局必须先展开完整封面，否则 Crop 只显示一截。
                        // 读手势/phase 用 derivedStateOf，预测返回首帧就能展开，不依赖整页重组。
                        val miuixReturnState =
                            com.android.purebilibili.core.ui.transition
                                .LocalMiuixVideoCardTransitionState.current
                        val expandPlayerForSharedReturn by remember(
                            isExitTransitionInProgress,
                            detailShellSharedBoundsEnabled,
                            sharedBoundsActive,
                            liveReturnMorph,
                            entryOwnsMiuixCardTransition,
                            miuixReturnState,
                            videoCardDepthBackgroundState,
                        ) {
                            derivedStateOf {
                                val miuixGesture =
                                    entryOwnsMiuixCardTransition &&
                                        miuixReturnState.enabled &&
                                        miuixReturnState.isGestureInProgressProvider()
                                val phase = if (entryOwnsMiuixCardTransition) {
                                    videoCardDepthBackgroundState.phaseProvider()
                                } else {
                                    VideoCardTransitionBackgroundPhase.IDLE
                                }
                                shouldExpandPlayerViewportForSharedReturn(
                                    isExitTransitionInProgress = isExitTransitionInProgress ||
                                        miuixGesture ||
                                        phase == VideoCardTransitionBackgroundPhase.RETURNING,
                                    isReturnGestureInProgress =
                                        (
                                            entryOwnsMiuixCardTransition &&
                                                videoCardDepthBackgroundState
                                                    .isReturnGestureInProgressProvider()
                                        ) || miuixGesture,
                                    isGestureRestoreInProgress =
                                        entryOwnsMiuixCardTransition &&
                                            videoCardDepthBackgroundState
                                                .isGestureRestoreInProgressProvider(),
                                    sharedReturnLikely = detailShellSharedBoundsEnabled ||
                                        sharedBoundsActive ||
                                        liveReturnMorph ||
                                        (entryOwnsMiuixCardTransition &&
                                            miuixReturnState.enabled),
                                )
                            }
                        }
                        val layoutCollapseProgress = resolvePlayerCollapseProgressForLayout(
                            manualOrCompactCollapseProgress = animatedCollapseProgress,
                            expandForSharedReturn = expandPlayerForSharedReturn,
                        )
                        SideEffect {
                            playerChromeCollapsedByScroll = layoutCollapseProgress >= 0.98f
                        }
                        LaunchedEffect(expandPlayerForSharedReturn) {
                            if (expandPlayerForSharedReturn) {
                                // 清掉折叠 offset，避免 skipGesture 路径下一帧又压扁。
                                inlinePlayerCollapseState.reset()
                            }
                        }
                        // 高度只由简介/评论共享的折叠位移决定，而不是由当前页签的折叠终点
                        // 重新插值；这样暂停评论页隐藏到 0 后切到简介不会在一帧内弹回。
                        // 范围收缩属于布局校正，交给 tween；跟手拖动仍用 snap。
                        val viewportHeightDensity = LocalDensity.current
                        val targetInlineViewportHeight = if (
                            expandPlayerForSharedReturn || isPortraitFullscreen
                        ) {
                            expandedViewportHeight
                        } else {
                            (expandedViewportHeight + with(viewportHeightDensity) {
                                playerHeightOffset.toDp()
                            }).coerceIn(0.dp, expandedViewportHeight)
                        }
                        val animatedInlineViewportHeight by animateFloatAsState(
                            targetValue = targetInlineViewportHeight.value,
                            animationSpec = if (
                                inlinePlayerCollapseState.restoreRequested ||
                                    inlinePlayerCollapseState.layoutReconciled
                            ) {
                                tween(
                                    durationMillis = resolveInlinePortraitPlayerCommentCollapseDurationMillis(
                                        videoContentTabSwitchAnimationSpec
                                    ),
                                    easing = FastOutSlowInEasing
                                )
                            } else {
                                snap()
                            },
                            label = "inline_portrait_player_viewport_height"
                        )
                        // 两种内联体验都从共享位移推导高度，横屏视频详情也不再按页签
                        // 重算折叠终点；手势范围已同步为“展开高度 - 折叠终点”。
                        val inlineViewportHeight = animatedInlineViewportHeight.dp
                        val expandedViewportWidth = if (useOfficialInlinePortraitDetailExperience) {
                            expandedPortraitInlineSpec.widthDp.dp
                        } else {
                            screenWidthDp
                        }
                        val collapsedViewportWidth = if (useOfficialInlinePortraitDetailExperience) {
                            collapsedPortraitInlineSpec.widthDp.dp
                        } else {
                            screenWidthDp
                        }
                        val inlineViewportWidth = lerp(
                            expandedViewportWidth,
                            collapsedViewportWidth,
                            layoutCollapseProgress
                        )
                        // PiliPlus 使用 MediaQuery.viewPadding.top 作为独立 AppBar 高度。
                        // Android 侧读取根窗口 ignoringVisibility inset，并用系统 dimen 兜底。
                        val insetDensity = LocalDensity.current
                        val collapsedSystemBarInset = remember(view, configuration.densityDpi) {
                            val rootInsetPx = ViewCompat.getRootWindowInsets(view)
                                ?.getInsetsIgnoringVisibility(
                                    WindowInsetsCompat.Type.statusBars() or
                                        WindowInsetsCompat.Type.displayCutout()
                                )
                                ?.top
                                ?: 0
                            val statusBarResourceId = context.resources.getIdentifier(
                                "status_bar_height",
                                "dimen",
                                "android",
                            )
                            val resourceInsetPx = if (statusBarResourceId != 0) {
                                context.resources.getDimensionPixelSize(statusBarResourceId)
                            } else {
                                0
                            }
                            with(insetDensity) { maxOf(rootInsetPx, resourceInsetPx).toDp() }
                        }
                        val inlinePlayerHeight = inlineViewportHeight + collapsedSystemBarInset
                        val fullscreenPlayerHeight = screenHeightDp.coerceAtLeast(1.dp)

                        //  视频播放器区域：状态栏可见时避让，隐藏时让画面沉浸到顶部。
                        //  尝试获取共享元素作用域
                        val sharedTransitionScope = LocalSharedTransitionScope.current
                        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                        val coverSharedElementSourceRoute = resolveForcedReturnCoverSharedElementSourceRoute(
                            sourceRouteForSharedElement
                        )

                        //  为播放器容器添加共享元素标记（封面 ↔ 播放器区域映射）
                        // shell 已接管时禁止再挂 cover sharedBounds（默认 Center 会往屏幕中心飞）。
                        val isFullscreenTarget = activeVideoSharedTransitionVisualSpec.fillTargetViewport
                        val attachPlayerCoverSharedBounds =
                            com.android.purebilibili.core.ui.transition.shouldAttachVideoDetailCoverSharedBounds(
                                coverSharedBoundsEnabled =
                                    shouldEnableVideoCoverSharedTransition(
                                        transitionEnabled = detailChildTransitionEnabled,
                                        hasSharedTransitionScope = sharedTransitionScope != null,
                                        hasAnimatedVisibilityScope = animatedVisibilityScope != null
                                    ) &&
                                        activeVideoSharedTransitionVisualSpec.useCoverSharedBounds,
                                detailShellSharedBoundsEnabled = detailShellSharedBoundsEnabled,
                                immediatePlayback = videoSharedPlaybackIntent ==
                                    VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                                forceCoverOnlyForReturn = forceCoverOnlyForReturn,
                            )
                        val playerContainerModifier = if (attachPlayerCoverSharedBounds) {
                            with(requireNotNull(sharedTransitionScope)) {
                                Modifier
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            key = com.android.purebilibili.core.ui.transition.videoCoverSharedElementKey(
                                                bvid,
                                                sourceRoute = coverSharedElementSourceRoute
                                            )
                                        ),
                                        animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                        boundsTransform = { initialBounds, targetBounds ->
                                            val duration = if (
                                                homeSharedTransitionMotionSpec.enabled && isFullscreenTarget
                                            ) {
                                                homeSharedTransitionMotionSpec.fullscreenDurationMillis
                                            } else {
                                                homeSharedTransitionMotionSpec.durationMillis
                                            }
                                            videoSharedElementBoundsTransformSpec(
                                                motion = homeSharedTransitionMotionSpec,
                                                initialBounds = initialBounds,
                                                targetBounds = targetBounds,
                                                durationMillis = duration
                                            )
                                        },
                                        resizeMode = com.android.purebilibili.core.ui.transition
                                            .resolveVideoCardSharedBoundsResizeMode(
                                                fillFullscreenShell = isFullscreenTarget,
                                            ),
                                        clipInOverlayDuringTransition = OverlayClip(
                                            RoundedCornerShape(
                                                resolveVideoDetailShellOverlayCornerDp(
                                                    visualSpec = activeVideoSharedTransitionVisualSpec,
                                                    liveReturnMorph = liveReturnMorph,
                                                    isReturningVisualState =
                                                        useReturningVideoDetailVisualState,
                                                ).dp
                                            )
                                        )
                                    )
                            }
                        } else {
                            Modifier
                        }

                        // isLeaving：离开态（正文让位等）；封面/播放器 handoff 用 isCommittedCardReturn。
                        val isLeaving = useReturningVideoDetailVisualState
                        // Exact stationary list cover request (URL + key + size). Never fixImageUrl.
                        val residentCoverImageRequest = remember(
                            context,
                            isVerticalVideo,
                            residentCoverSource?.url,
                            residentCoverSource?.cacheKey,
                            residentCoverSource?.decodeWidthPx,
                            residentCoverSource?.decodeHeightPx,
                        ) {
                            val source = residentCoverSource ?: return@remember null
                            coil3.request.ImageRequest.Builder(context)
                                .data(source.url)
                                .apply {
                                    if (source.decodeWidthPx > 0 && source.decodeHeightPx > 0) {
                                        size(source.decodeWidthPx, source.decodeHeightPx)
                                    }
                                }
                                .placeholderMemoryCacheKey(source.cacheKey)
                                .crossfade(false)
                                .memoryCacheKey(source.cacheKey)
                                .diskCacheKey(source.cacheKey)
                                .build()
                        }

                        //  播放器容器按当前顶部避让高度计算，避免隐藏状态栏后留下黑边。
                        //  [修复] 始终保持播放器在 Composition 中，避免隐藏时重新创建导致重载
                        // Landing: remeasure media to the list cover box so Crop pixels and cover
                        // edges already match before the flying entry hands off to the home card.
                        val miuixLandingState =
                            com.android.purebilibili.core.ui.transition
                                .LocalMiuixVideoCardTransitionState.current
                        val floatingPlayerTopInsetProvider: () -> Dp = {
                            if (entryOwnsMiuixCardTransition && miuixLandingState.enabled) {
                                playerTopInset * resolveVideoDetailCardTopInsetFraction(
                                    miuixLandingState.progressProvider(),
                                )
                            } else playerTopInset
                        }
                        val removedPlayerTopInsetProvider: () -> Dp = {
                            playerTopInset - floatingPlayerTopInsetProvider()
                        }
                        val landingLayoutForMedia = run {
                            if (
                                !entryOwnsMiuixCardTransition ||
                                !miuixLandingState.enabled
                            ) return@run null
                            val viewportW = miuixLandingState.layoutWidthProvider()
                                .takeIf { it > 1f }
                                ?: with(videoCardTransitionDensity) {
                                    configuration.screenWidthDp.dp.toPx()
                                }
                            resolveVideoDetailReturnSourceCardLayout(
                                viewportWidthPx = viewportW,
                                sourceBounds = miuixLandingState.sourceBoundsProvider(),
                                sourceCoverBounds = miuixLandingState.sourceCoverBoundsProvider(),
                                sourceLayout = miuixLandingState.sourceLayout,
                            ).takeIf { it.canRender }
                        }
                        // A frozen now-playing snapshot contains the whole capsule, not just
                        // its cover. Land it in the complete source bounds for either bar layout.
                        val nativeSnapshotTargetBoundsProvider: (() -> Rect?)? =
                            if (
                                entryOwnsMiuixCardTransition && miuixLandingState.enabled &&
                                (miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true ||
                                    (CardPositionManager.lastClickedNativeCardBitmap != null &&
                                        miuixLandingState.sourceLayout == VideoCardSourceLayout.COVER_ONLY))
                            ) {
                                { miuixLandingState.sourceBoundsProvider() }
                            } else {
                                null
                            }
                        // Only the source-card endpoint is rounded; the detail player fills its viewport.
                        // The complete native capsule already has one navigation-shell clip.
                        // A second media-cover radius exposes the live layer around its baked corners.
                        val returnMediaClipCornerDp = if (nativeSnapshotTargetBoundsProvider != null) {
                            0.dp
                        } else AppShapes.mediaCoverCornerDp()
                        val returnMediaInverseScaleProvider: () ->
                            com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardInverseScale = {
                            val landing = landingLayoutForMedia
                            val frozenX = landing?.sourceScale ?: 1f
                            if (!entryOwnsMiuixCardTransition || !miuixLandingState.enabled) {
                                com.android.purebilibili.navigation3.predictiveback
                                    .MiuixVideoCardInverseScale(1f / frozenX, 1f / frozenX)
                            } else {
                                val viewportH = miuixLandingState.layoutHeightProvider()
                                    .takeIf { it > 1f }
                                    ?: with(videoCardTransitionDensity) {
                                        configuration.screenHeightDp.dp.toPx()
                                    }
                                val sourceH = miuixLandingState.sourceBoundsProvider()
                                    ?.height
                                    ?: landing?.cardHeightPx
                                    ?: viewportH
                                val viewportW = miuixLandingState.layoutWidthProvider()
                                    .takeIf { it > 1f }
                                    ?: with(videoCardTransitionDensity) { configuration.screenWidthDp.dp.toPx() }
                                val sourceScaleX = landing?.sourceScale
                                    ?: ((miuixLandingState.sourceBoundsProvider()?.width ?: viewportW) / viewportW)
                                val sourceScaleY = (sourceH / viewportH.coerceAtLeast(1f))
                                    .coerceIn(0.01f, 1f)
                                com.android.purebilibili.navigation3.predictiveback
                                    .resolveMiuixVideoCardInverseScaleForDepth(
                                        sourceScaleX = sourceScaleX,
                                        sourceScaleY = sourceScaleY,
                                        depth = miuixLandingState.progressProvider(),
                                    )
                            }
                        }
                        val flyingSourceChromeAlphaProvider: () -> Float = {
                            resolveVideoDetailFlyingSourceChromeAlpha(
                                morphDepthProgress = miuixLandingState.progressProvider(),
                                phase = videoCardDepthBackgroundState.phaseProvider(),
                                isReturnGestureInProgress =
                                    videoCardDepthBackgroundState
                                        .isReturnGestureInProgressProvider() ||
                                        videoCardDepthBackgroundState
                                            .isGestureRestoreInProgressProvider(),
                                sourceLayout = landingLayoutForMedia?.layout
                                    ?: miuixLandingState.sourceLayout,
                                followProgressEnabled = videoCardDepthBackgroundState
                                    .returnContentFollowProgressEnabledProvider(),
                                detailContentLoading = uiState is VideoPlaybackUiState.Loading,
                                isNowPlayingBar =
                                    miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true,
                                isCommittedCardReturn = isCommittedCardReturn,
                            )
                        }
                        val returnMediaHandoffProgressProvider: () -> Float = {
                            if (!entryOwnsMiuixCardTransition || !miuixLandingState.enabled) {
                                0f
                            } else if (isCommittedCardReturn &&
                                !videoCardDepthBackgroundState.isReturnGestureInProgressProvider() &&
                                !videoCardDepthBackgroundState.isGestureRestoreInProgressProvider() &&
                                videoCardDepthBackgroundState.phaseProvider() ==
                                    VideoCardTransitionBackgroundPhase.IDLE
                            ) {
                                // Keep every media path at its source endpoint until the entry leaves.
                                1f
                            } else {
                                if (miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true) {
                                    com.android.purebilibili.core.ui.transition
                                        .resolveNowPlayingBarMediaLayoutHandoff(
                                            miuixLandingState.progressProvider(),
                                        )
                                } else if (nativeSnapshotTargetBoundsProvider != null) {
                                    com.android.purebilibili.core.ui.transition
                                        .resolveVideoCardReturnSettleFromMorphDepth(
                                            miuixLandingState.progressProvider(),
                                        )
                                } else {
                                    com.android.purebilibili.core.ui.transition
                                        .resolveVideoDetailMediaLayoutHandoffProgress(
                                            morphDepthProgress = miuixLandingState.progressProvider(),
                                            phase = videoCardDepthBackgroundState.phaseProvider(),
                                            isReturnGestureInProgress =
                                                videoCardDepthBackgroundState
                                                    .isReturnGestureInProgressProvider() ||
                                                    videoCardDepthBackgroundState
                                                        .isGestureRestoreInProgressProvider(),
                                            sourceLayout = landingLayoutForMedia?.layout
                                                ?: miuixLandingState.sourceLayout,
                                            sourceChromeAlpha = flyingSourceChromeAlphaProvider(),
                                            isCommittedCardReturn = isCommittedCardReturn,
                                        )
                                }
                            }
                        }
                        val residentCoverContentScale = VideoDetailCoverContentScale
                        val nowPlayingMediaAlphaProvider: () -> Float = {
                            if (!entryOwnsMiuixCardTransition || !miuixLandingState.enabled ||
                                miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar != true
                            ) {
                                1f
                            } else if (isCommittedCardReturn &&
                                videoCardDepthBackgroundState.phaseProvider() ==
                                    VideoCardTransitionBackgroundPhase.IDLE
                            ) {
                                0f
                            } else {
                                1f - flyingSourceChromeAlphaProvider()
                            }
                        }
                        val returnMediaFrameProvider: () -> VideoDetailReturnMediaFrame = {
                            val returnGestureInProgress =
                                videoCardDepthBackgroundState.isReturnGestureInProgressProvider() ||
                                    videoCardDepthBackgroundState
                                        .isGestureRestoreInProgressProvider()
                            resolveVideoDetailReturnMediaFrame(
                                transitionProgress = resolveVideoDetailReturnVisualProgress(
                                    animatedVisibilityProgress = detailTransitionProgress.value,
                                    morphDepthProgress =
                                        videoCardDepthBackgroundState.progressProvider(),
                                    liveReturnMorph = liveReturnMorph || entryOwnsMiuixCardTransition,
                                ),
                                isCommittedCardReturn = isCommittedCardReturn,
                                hasResidentCover = hasResidentReturnCover,
                                liveReturnMorph = liveReturnMorph,
                                progressiveResidentCover = progressiveResidentCoverReturn,
                                followProgressEnabled = videoCardDepthBackgroundState
                                    .returnContentFollowProgressEnabledProvider(),
                                isReturnGestureInProgress = returnGestureInProgress,
                                showResidentCoverUntilFirstFrame =
                                    entryOwnsMiuixCardTransition &&
                                        currentBvid == bvid &&
                                        !hasRenderedFirstFrameForReturn,
                            )
                        }
                        returnMediaGeometry.frameProvider = {
                            val viewport = returnMediaGeometry.viewportSize.value
                            if (viewport.width <= 0 || viewport.height <= 0) null else {
                                val inverse = returnMediaInverseScaleProvider()
                                resolveVideoDetailReturnMediaLayoutFrame(
                                    containerWidthPx = viewport.width,
                                    containerHeightPx = viewport.height,
                                    landingLayout = landingLayoutForMedia,
                                    handoffProgress = returnMediaHandoffProgressProvider(),
                                    inverseScaleX = inverse.scaleX,
                                    inverseScaleY = inverse.scaleY,
                                    contentTopInsetPx = with(videoCardTransitionDensity) {
                                        floatingPlayerTopInsetProvider().roundToPx()
                                    },
                                    nativeSnapshotBounds = nativeSnapshotTargetBoundsProvider?.invoke(),
                                )
                            }
                        }
                        PlayerAmbientLayout(
                            modifier = Modifier.fillMaxWidth(),
                            fullscreen = isFullscreenMode,
                            playerModifier = playerContainerModifier
                                .fillMaxWidth()
                                .then(
                                    if (hidePausedPlayerForComments && !expandPlayerForSharedReturn) {
                                        Modifier.clipToBounds()
                                    } else {
                                        Modifier
                                    }
                                )
                                .continuousPlayerViewportHeight(
                                    progressProvider = { continuousPlayerProgress.value },
                                    inlineHeight = inlinePlayerHeight,
                                    fullscreenHeight = fullscreenPlayerHeight,
                                    enabled = continuousFullscreenTransitionEnabled,
                                    // Loading cover and playback share 16:9 geometry; the first frame must not resize the viewport.
                                    preferLayoutWidthInline = !useOfficialInlinePortraitDetailExperience &&
                                        !suppressPhoneDetailBodyForDirectPortrait,
                                    inlineTopInset = if (!useOfficialInlinePortraitDetailExperience) {
                                        playerTopInset
                                    } else {
                                        0.dp
                                    },
                                    removedInlineTopInsetProvider = removedPlayerTopInsetProvider,
                                )
                                .onSizeChanged { returnMediaGeometry.viewportSize.value = it }
                                .drawBehind {
                                    // The compact source is translucent in both plain and glass modes.
                                    // Do not leave the opaque player backing under its return snapshot.
                                    val backingAlpha = if (
                                        miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true
                                    ) {
                                        nowPlayingMediaAlphaProvider()
                                    } else {
                                        1f
                                    }
                                    // The media shrinks inside this still detail-sized viewport.
                                    // Keep its backing in the same slot so it cannot darken the
                                    // source title/info band while that band fades in.
                                    val handoffProgress = returnMediaHandoffProgressProvider()
                                    val inverseScale = returnMediaInverseScaleProvider()
                                    val backingFrame = resolveVideoDetailReturnMediaLayoutFrame(
                                        containerWidthPx = size.width.roundToInt(),
                                        containerHeightPx = size.height.roundToInt(),
                                        landingLayout = landingLayoutForMedia,
                                        handoffProgress = handoffProgress,
                                        inverseScaleX = inverseScale.scaleX,
                                        inverseScaleY = inverseScale.scaleY,
                                        nativeSnapshotBounds = nativeSnapshotTargetBoundsProvider?.invoke(),
                                    )
                                    val hasTargetGeometry = landingLayoutForMedia?.canRender == true ||
                                        nativeSnapshotTargetBoundsProvider?.invoke()
                                            ?.let { it.width > 1f && it.height > 1f } == true
                                    val radius = if (hasTargetGeometry) {
                                        returnMediaClipCornerDp.toPx() * handoffProgress.coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = backingAlpha),
                                        topLeft = androidx.compose.ui.geometry.Offset(
                                            backingFrame.offsetXPx.toFloat(),
                                            backingFrame.offsetYPx.toFloat(),
                                        ),
                                        size = androidx.compose.ui.geometry.Size(
                                            backingFrame.widthPx.toFloat(),
                                            backingFrame.heightPx.toFloat(),
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                                    )
                                }
                                //  [PiP修复] 捕获视频播放器在屏幕上的位置
                                .onSettledVideoPlayerPositioned(
                                    freezeProvider = {
                                        shouldFreezeVideoDetailPlayerBounds(
                                            continuousPlayerMorphing = isContinuousPlayerMorphing,
                                            ownsCardTransition = entryOwnsMiuixCardTransition,
                                            committedCardReturn = isCommittedCardReturn,
                                            exposure = videoCardDepthBackgroundState.exposureProvider(),
                                        )
                                    },
                                ) { layoutCoordinates ->
                                    val position = layoutCoordinates.positionInWindow()
                                    val rootPosition = layoutCoordinates.positionInRoot()
                                    val size = layoutCoordinates.size
                                    val nextBounds = android.graphics.Rect(
                                        position.x.toInt(),
                                        position.y.toInt(),
                                        position.x.toInt() + size.width,
                                        position.y.toInt() + size.height
                                    )
                                    val nextRootBottomPx = (rootPosition.y + size.height).roundToInt()
                                    if (
                                        videoPlayerRootBottomPx == 0 ||
                                        abs(videoPlayerRootBottomPx - nextRootBottomPx) > 3
                                    ) {
                                        videoPlayerRootBottomPx = nextRootBottomPx
                                    }
                                    if (!hasMeaningfulVideoPlayerBoundsChange(videoPlayerBounds, nextBounds)) {
                                        return@onSettledVideoPlayerPositioned
                                    }
                                    videoPlayerBounds = nextBounds
                                }
                        ) {
                            // 媒体只有两个显式层：上层常驻封面、下层播放器。返回 seek 时两者
                            // 共享同一帧进度，不再依赖导航 entry alpha 遮挡平台视频 surface。
                            if (residentCoverImageRequest != null) {
                                VideoDetailReturnCoverImage(
                                    request = residentCoverImageRequest,
                                    contentDescription = "cover",
                                    onDecoded = { hasDecodedResidentReturnCover = true },
                                    landingLayout = landingLayoutForMedia,
                                    contentTopInsetProvider = floatingPlayerTopInsetProvider,
                                    nativeSnapshotBoundsProvider = nativeSnapshotTargetBoundsProvider,
                                    handoffProgressProvider = returnMediaHandoffProgressProvider,
                                    inverseScaleXProvider = { returnMediaInverseScaleProvider().scaleX },
                                    inverseScaleYProvider = { returnMediaInverseScaleProvider().scaleY },
                                    clipCornerDp = returnMediaClipCornerDp,
                                    alphaProvider = {
                                        returnMediaFrameProvider().coverAlpha * nowPlayingMediaAlphaProvider()
                                    },
                                    backingColor = Color.Black,
                                    modifier = Modifier.fillMaxSize().zIndex(1f),
                                    contentScale = if (isVerticalVideo) {
                                        ContentScale.Crop
                                    } else {
                                        residentCoverContentScale
                                    }
                                )
                            }
                            var collapsedPlayerMoreRequestKey by remember(currentBvid) {
                                mutableIntStateOf(0)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .videoDetailReturnPlayerLayout(
                                        landingLayout = landingLayoutForMedia,
                                        handoffProgressProvider =
                                            returnMediaHandoffProgressProvider,
                                        inverseScaleXProvider = {
                                            returnMediaInverseScaleProvider().scaleX
                                        },
                                        inverseScaleYProvider = {
                                            returnMediaInverseScaleProvider().scaleY
                                        },
                                        clipCornerDp = returnMediaClipCornerDp,
                                        nativeSnapshotBoundsProvider = nativeSnapshotTargetBoundsProvider,
                                    )
                                    .videoDetailRemoveFloatingTopInset(removedPlayerTopInsetProvider)
                                    .zIndex(0f)
                                    .graphicsLayer {
                                        alpha = returnMediaFrameProvider().playerAlpha * nowPlayingMediaAlphaProvider()
                                    }
                            ) {
                                continuousPlayerContent(
                                    ContinuousPlayerHostLayout(
                                        modifier = Modifier.align(Alignment.TopCenter),
                                        viewportWidth = if (continuousFullscreenTransitionEnabled) screenWidthDp else inlineViewportWidth,
                                        alpha = inlinePlayerAlpha,
                                        scale = inlinePlayerScale,
                                        isFullscreen = false,
                                        contentTopInset = playerTopInset,
                                    )
                                )
                            }
                            if (
                                miuixVisualAssetsActive &&
                                shouldDrawFlyingReconstructedSourceChrome(
                                    phase = videoCardDepthBackgroundState.phaseProvider(),
                                    isReturnGestureInProgress =
                                        videoCardDepthBackgroundState
                                            .isReturnGestureInProgressProvider(),
                                    isGestureRestoreInProgress =
                                        videoCardDepthBackgroundState
                                            .isGestureRestoreInProgressProvider(),
                                )
                            ) {
                                VideoDetailReturnCoverChrome(
                                    sourceChromeSnapshot = miuixLandingState.sourceChromeSnapshot,
                                    sourceScale = landingLayoutForMedia?.sourceScale ?: 1f,
                                    inverseScaleXProvider = {
                                        returnMediaInverseScaleProvider().scaleX
                                    },
                                    inverseScaleYProvider = {
                                        returnMediaInverseScaleProvider().scaleY
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .videoDetailReturnMediaLayout(
                                            landingLayout = landingLayoutForMedia,
                                            // A complete capsule snapshot has its own stable aspect
                                            // ratio; never interpolate it from the player rectangle.
                                            handoffProgressProvider = {
                                                if (miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true) {
                                                    1f
                                                } else returnMediaHandoffProgressProvider()
                                            },
                                            inverseScaleXProvider = {
                                                returnMediaInverseScaleProvider().scaleX
                                            },
                                            inverseScaleYProvider = {
                                                if (miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true) {
                                                    returnMediaInverseScaleProvider().scaleX
                                                } else returnMediaInverseScaleProvider().scaleY
                                            },
                                            nativeSnapshotBoundsProvider =
                                                nativeSnapshotTargetBoundsProvider,
                                        )
                                        .zIndex(1.5f)
                                        .graphicsLayer {
                                            val liveHandoff = if (miuixLandingState.sourceChromeSnapshot?.isNowPlayingBar == true) {
                                                com.android.purebilibili.core.ui.transition
                                                    .resolveNowPlayingBarLiveHandoff(miuixLandingState.progressProvider())
                                            } else 0f
                                            alpha = flyingSourceChromeAlphaProvider() * (1f - liveHandoff)
                                        },
                                )
                            }
                            if (!isVerticalVideo) {
                                CollapsedPlayerNavigationBar(
                                    scrollRatio = layoutCollapseProgress,
                                    topInset = collapsedSystemBarInset,
                                    onBack = handleBack,
                                    onHomeClick = {
                                        handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = true))
                                    },
                                    onPlayClick = {
                                        inlinePlayerCollapseState.restore()
                                        playPlayerFromUserAction(playerState.player)
                                    },
                                    onMoreClick = { collapsedPlayerMoreRequestKey += 1 },
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(2f),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (suppressPhoneDetailBodyForDirectPortrait) {
                                        Color.Black
                                    } else {
                                        AppSurfaceTokens.background()
                                    }
                                )
                                .drawWithContent {
                                    val reveal = if (entryHeroDriver != null &&
                                        videoCardDepthBackgroundState.phaseProvider() == VideoCardTransitionBackgroundPhase.OPENING
                                    ) resolveVideoCardDetailChromeAlpha(
                                        entryHeroDriver.progressProvider(),
                                        VideoCardTransitionBackgroundPhase.OPENING, false,
                                        sourceLayout = scopedHeroDriver.sourceLayout.takeUnless {
                                            scopedHeroDriver.sourceChromeSnapshot?.isNowPlayingBar == true
                                        },
                                    ) else detailInfoRevealProgress.value.coerceIn(0f, 1f)
                                    clipRect(
                                        left = 0f,
                                        top = 0f,
                                        right = size.width,
                                        bottom = size.height * reveal,
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                    val collapseShadowAlpha =
                                        resolveVideoDetailCollapseShadowAlpha(layoutCollapseProgress)
                                    if (collapseShadowAlpha > 0f) {
                                        val shadowHeight = 20.dp.toPx()
                                        clipRect(bottom = shadowHeight) {
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = collapseShadowAlpha),
                                                        Color.Transparent,
                                                    ),
                                                    endY = shadowHeight,
                                                )
                                            )
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    val reveal = if (entryHeroDriver != null &&
                                        videoCardDepthBackgroundState.phaseProvider() == VideoCardTransitionBackgroundPhase.OPENING
                                    ) resolveVideoCardDetailChromeAlpha(
                                        entryHeroDriver.progressProvider(),
                                        VideoCardTransitionBackgroundPhase.OPENING, false,
                                        sourceLayout = scopedHeroDriver.sourceLayout.takeUnless {
                                            scopedHeroDriver.sourceChromeSnapshot?.isNowPlayingBar == true
                                        },
                                    ) else detailInfoRevealProgress.value.coerceIn(0f, 1f)
                                    val holdFullyOpaque =
                                        suppressEnterFadeAfterBackPreview &&
                                            !useReturningVideoDetailVisualState
                                    // Miuix flying entry: always complement source-card chrome with
                                    // morph depth (not only LIVE surface). Cover-first related/home
                                    // returns need the same gradual player-below ↔ cover-below handoff.
                                    val useMorphSecondaryYield =
                                        (if (scopedHeroDriver.enabled) entryOwnsMiuixCardTransition
                                        else liveReturnMorph || detailShellSharedBoundsEnabled) &&
                                            !holdFullyOpaque
                                    if (useMorphSecondaryYield) {
                                        val frame = resolveVideoCardSecondaryContentVisualFrame(
                                            morphDepthProgress =
                                                videoCardDepthBackgroundState.progressProvider(),
                                            phase = videoCardDepthBackgroundState.phaseProvider(),
                                            isReturnGestureInProgress =
                                                videoCardDepthBackgroundState
                                                    .isReturnGestureInProgressProvider() ||
                                                    videoCardDepthBackgroundState
                                                        .isGestureRestoreInProgressProvider(),
                                            motionTier =
                                                videoCardDepthBackgroundState.motionTierProvider(),
                                            sourceLayout = miuixLandingState.sourceLayout,
                                            followProgressEnabled = videoCardDepthBackgroundState
                                                .returnContentFollowProgressEnabledProvider(),
                                            isCommittedCardReturn = isCommittedCardReturn,
                                            preserveNativeViewport = scopedHeroDriver.enabled,
                                        )
                                        alpha = frame.alpha
                                        // Native entry already owns geometry. Keep only its content
                                        // handoff alpha; the legacy path retains its internal transform.
                                        scaleX = if (scopedHeroDriver.enabled) 1f else frame.scale
                                        scaleY = if (scopedHeroDriver.enabled) 1f else frame.scale
                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                        clip = frame.handoffProgress > 0.001f
                                        translationY = with(videoCardTransitionDensity) {
                                            ((if (scopedHeroDriver.enabled) 0f else frame.translationYDp) +
                                                (1f - reveal) * 12f).dp.toPx()
                                        }
                                    } else {
                                        alpha = resolveVideoDetailReturnContentAlpha(
                                            transitionProgress = detailTransitionProgress.value,
                                            isCommittedCardReturn = isCommittedCardReturn,
                                            holdFullyOpaqueAfterBackPreview = holdFullyOpaque,
                                            liveReturnMorph = false,
                                            isQuickReturn = isQuickReturningFromDetail,
                                            morphDepthProgress =
                                                videoCardDepthBackgroundState.progressProvider(),
                                        )
                                        translationY = with(videoCardTransitionDensity) {
                                            ((1f - reveal) * 12f).dp.toPx()
                                        }
                                    }
                                }
                                // .nestedScroll(nestedScrollConnection) // [Remove] 移除嵌套滚动，确保 Tabs 正常滑动
                        ) {
                            // 「竖屏直达」morph 期间不绘制详情 body，避免先露出简介/评论再跳竖全屏。
                            // 错误态仍展示，避免黑屏无法重试。
                            when {
                                suppressPhoneDetailBodyForDirectPortrait &&
                                    uiState !is VideoPlaybackUiState.Error -> Unit
                                // 封面接管且正文已不可见才允许卸载；LiveMorph 内容必须保持
                                // composition，并在 Miuix 飞行 entry 内让位给来源卡文字。
                                detachSecondaryContentForReturn &&
                                    uiState !is VideoPlaybackUiState.Error -> Unit
                                uiState is VideoPlaybackUiState.Loading && detailContentState == null -> {
                                    val loadingState = uiState as VideoPlaybackUiState.Loading
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        //  显示重试进度
                                        if (loadingState.retryAttempt > 0) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    AdaptiveLoadingIndicator()
                                                    Spacer(Modifier.height(16.dp))
                                                    AppText(
                                                        text = "正在重试 ${loadingState.retryAttempt}/${loadingState.maxAttempts}...",
                                                        color = AppContentColors.secondaryText(),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        } else {
                                            VideoDetailSkeleton(
                                                // Loading feedback is independent of the card-transition switch.
                                                animated = !transitionEnabled || entryTransitionFinished,
                                            )
                                        }
                                    }
                                }

                                detailContentState != null -> {
                                    val success = detailContentState
                                    VideoDetailPhoneSuccessContentLayer(
                                        success = success,
                                        introListState = introListState,
                                        commentListState = commentListState,
                                        videoContentPagerState = videoContentPagerState,
                                        commentState = commentState,
                                        engagementState = engagementState,
                                        androidNativeLiquidGlassEnabled =
                                            homeSettings.androidNativeLiquidGlassEnabled,
                                        commentMemberDecorationsEnabled = commentMemberDecorationsEnabled,
                                        playbackActions = playbackActions,
                                        engagementActions = engagementActions,
                                        commentActions = commentActions,
                                        context = context,
                                        sortPreferenceScope = sortPreferenceScope,
                                        playerState = playerState,
                                        motionSpec = motionSpec,
                                        hazeState = hazeState,
                                        isTransitionFinished = isTransitionFinished ||
                                            keepLoadedContentForBackPreview ||
                                            suppressEnterFadeAfterBackPreview,
                                        isLeaving = isLeaving,
                                        rootTransitionOwnsContentAlpha = detailShellSharedBoundsEnabled,
                                        keepContentVisibleAfterBackPreview =
                                            suppressEnterFadeAfterBackPreview ||
                                                keepLoadedContentForBackPreview,
                                        shouldShowExternalPlaylistQueueBar = shouldShowExternalPlaylistQueueBar,
                                        selectedVideoContentTabIndex = selectedVideoContentTabIndex,
                                        useTabletLayout = useTabletLayout,
                                        isFullscreenMode = isFullscreenMode,
                                        isPortraitFullscreen = isPortraitFullscreen,
                                        showCommentInput = showCommentInput,
                                        isCommentThreadVisible = subReplyState.visible,
                                        commentThreadCoveredBlurProgressProvider = { subReplyCoveredBlurProgress },
                                        showFavoriteFolderDialog = showFavoriteFolderDialog,
                                        downloadProgress = downloadProgress,
                                        danmakuEnabledForDetail = effectiveDanmakuEnabledForDetail,
                                        // 与播放器/Overlay 共用同一弹幕会话：正文层不再自行 acquire。
                                        danmakuManager = sharedDanmakuManager,
                                        isQuickReturnLimitedForSharedElements =
                                            isReturningFromDetail && isQuickReturningFromDetail,
                                        transitionEnabled = detailChildTransitionEnabled,
                                        sourceRouteForSharedElement = sourceRouteForSharedElement,
                                        isPlayerCollapsed = isPlayerCollapsed,
                                        commentPullToRefreshEnabled = shouldEnableCommentPullToRefresh(layoutCollapseProgress),
                                        onBgmClick = onBgmClick,
                                        homeUpBadgesVisible = homeUpBadgesVisible,
                                        isVideoPlaying = isVideoPlaying,
                                        onSelectedTabChange = presentationState::selectTab,
                                        openFavoriteFolders = openFavoriteFolders,
                                        navigateToUserSpaceFromVideo = navigateToUserSpaceFromVideo,
                                        navigateToRelatedVideo = navigateToRelatedVideo,
                                        openCommentUrl = openCommentUrl,
                                        onSearchKeywordClick = navigateToSearchKeywordFromVideo,
                                        onOpenBilibiliLink = onOpenBilibiliLink,
                                        externalPlaylistQueueTitle = externalPlaylistQueueTitle,
                                        playlistItems = playlistItems,
                                        onShowExternalPlaylistQueueSheet = {
                                            showExternalPlaylistQueueSheet = true
                                        }
                                    )
                                } // End of Success block

                                uiState is VideoPlaybackUiState.Error -> {
                                    val errorState = uiState as VideoPlaybackUiState.Error
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(32.dp)
                                        ) {
                                            //  根据错误类型显示不同图标
                                            AppText(
                                                text = when (errorState.error) {
                                                    is com.android.purebilibili.data.model.VideoLoadError.NetworkError -> "📡"
                                                    is com.android.purebilibili.data.model.VideoLoadError.VideoNotFound -> "🔍"
                                                    is com.android.purebilibili.data.model.VideoLoadError.RegionRestricted -> "🌐"
                                                    is com.android.purebilibili.data.model.VideoLoadError.RateLimited -> "⏳"
                                                    is com.android.purebilibili.data.model.VideoLoadError.GlobalCooldown -> ""
                                                    is com.android.purebilibili.data.model.VideoLoadError.PlayUrlEmpty -> "⚡"
                                                    else -> ""
                                                },
                                                style = MaterialTheme.typography.displayMedium
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            AppText(
                                                text = errorState.msg,
                                                color = AppContentColors.primaryText(),
                                                style = MaterialTheme.typography.titleMedium,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )

                                            //  针对风控错误显示额外建议
                                            when (errorState.error) {
                                                is com.android.purebilibili.data.model.VideoLoadError.GlobalCooldown,
                                                is com.android.purebilibili.data.model.VideoLoadError.PlayUrlEmpty -> {
                                                    Spacer(Modifier.height(8.dp))
                                                    AppText(
                                                        text = " 建议：切换 WiFi/移动数据 或 清除缓存后重试",
                                                        color = AppContentColors.secondaryText(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                                is com.android.purebilibili.data.model.VideoLoadError.RateLimited -> {
                                                    Spacer(Modifier.height(8.dp))
                                                    AppText(
                                                        text = " 该视频可能暂时不可用，请尝试其他视频",
                                                        color = AppContentColors.secondaryText(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                                else -> {}
                                            }

                                            //  只有可重试的错误才显示重试按钮（或者风控错误允许强制重试）
                                            val showRetryButton = errorState.canRetry ||
                                                errorState.error is com.android.purebilibili.data.model.VideoLoadError.RateLimited ||
                                                errorState.error is com.android.purebilibili.data.model.VideoLoadError.PlayUrlEmpty
                                            if (showRetryButton) {
                                                Spacer(Modifier.height(24.dp))
                                                AppButton(
                                                    onClick = { viewModel.retry() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                                                        contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                                                    )
                                                ) {
                                                    AppText(
                                                        text = when (errorState.error) {
                                                            is com.android.purebilibili.data.model.VideoLoadError.RateLimited -> "强制重试"
                                                            is com.android.purebilibili.data.model.VideoLoadError.GlobalCooldown -> "清除冷却并重试"
                                                            else -> "重试"
                                                        }
                                                    )
                                                }
                                            }

                                            //  🎬 [引导] 大会员受限时，提示用已保存的大会员账号播放
                                            val isVipRequiredError =
                                                errorState.error is com.android.purebilibili.data.model.VideoLoadError.VipRequired ||
                                                    (errorState.error as? com.android.purebilibili.data.model.VideoLoadError.ApiError)?.code == -10403 ||
                                                    (errorState.error as? com.android.purebilibili.data.model.VideoLoadError.UnknownError)
                                                        ?.throwable?.message?.contains("大会员") == true
                                            if (isVipRequiredError) {
                                                val vipPlaybackCandidates = remember(errorState.error) {
                                                    val currentPlaybackMid = com.android.purebilibili.core.network.NetworkModule
                                                        .playbackAccount()?.mid
                                                    com.android.purebilibili.core.store.AccountSessionStore
                                                        .getAccounts(context)
                                                        .filter { account ->
                                                            account.isVip &&
                                                                account.sessData.isNotBlank() &&
                                                                account.mid != currentPlaybackMid
                                                        }
                                                }
                                                val vipCandidate = vipPlaybackCandidates.firstOrNull()
                                                if (vipCandidate != null) {
                                                    Spacer(Modifier.height(16.dp))
                                                    AppButton(
                                                        onClick = {
                                                            com.android.purebilibili.core.store.AccountSessionStore
                                                                .setPlaybackAccountMid(context, vipCandidate.mid)
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "已使用「${vipCandidate.name.ifBlank { "UID ${vipCandidate.mid}" }}」的大会员播放",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                            viewModel.retry()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.tertiary
                                                        )
                                                    ) {
                                                        AppText("使用「${vipCandidate.name.ifBlank { "UID ${vipCandidate.mid}" }}」的大会员播放")
                                                    }
                                                    Spacer(Modifier.height(8.dp))
                                                    AppText(
                                                        text = "可在「我的 - 账号与播放」中更换播放账号",
                                                        color = AppContentColors.secondaryText(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                    }  // Detail body
                    }  // 📱 手机竖屏布局结束（Column）
                    }  // phone portrait branch of useTabletLayout
                    // Keep the flying source facade mounted through opening and return.
                    // Its draw-phase alpha hands pixels back to the list at the endpoint.
                    val miuixCardTransitionState =
                        com.android.purebilibili.core.ui.transition
                            .LocalMiuixVideoCardTransitionState.current
                    val sourceCardInfo = (uiState as? VideoPlaybackUiState.Success)?.info
                    if (
                        // Nested detail entries share the host CompositionLocals. Only the
                        // outgoing child owns this session; the retained parent must not draw
                        // the child's frozen cover/chrome over its related-video list.
                        miuixVisualAssetsActive &&
                        miuixCardTransitionState.enabled &&
                        !suppressPhoneDetailBodyForDirectPortrait &&
                        shouldDrawFlyingReconstructedSourceChrome(
                            phase = videoCardDepthBackgroundState.phaseProvider(),
                            isReturnGestureInProgress =
                                videoCardDepthBackgroundState
                                    .isReturnGestureInProgressProvider(),
                            isGestureRestoreInProgress =
                                videoCardDepthBackgroundState
                                    .isGestureRestoreInProgressProvider(),
                        ) &&
                        (sourceCardInfo != null ||
                            miuixCardTransitionState.sourceChromeSnapshot != null)
                    ) {
                        VideoDetailReturnSourceCardChrome(
                            info = sourceCardInfo,
                            horizontalMediaHandoffEnabled = true,
                            mediaFrameProvider = { returnMediaGeometry.frameProvider() },
                            sourceChromeSnapshot = miuixCardTransitionState.sourceChromeSnapshot,
                            detailContentLoading = uiState is VideoPlaybackUiState.Loading,
                            sourceLayout = miuixCardTransitionState.sourceLayout,
                            sourceBounds = miuixCardTransitionState.sourceBoundsProvider(),
                            sourceCoverBounds =
                                miuixCardTransitionState.sourceCoverBoundsProvider(),
                            sourceInfoBounds =
                                miuixCardTransitionState.sourceInfoBoundsProvider(),
                            morphDepthProgressProvider =
                                miuixCardTransitionState.progressProvider,
                            phaseProvider = videoCardDepthBackgroundState.phaseProvider,
                            followProgressEnabledProvider = videoCardDepthBackgroundState
                                .returnContentFollowProgressEnabledProvider,
                            isReturnGestureInProgressProvider = {
                                videoCardDepthBackgroundState
                                    .isReturnGestureInProgressProvider() ||
                                    videoCardDepthBackgroundState
                                        .isGestureRestoreInProgressProvider()
                            },
                            isCommittedCardReturn = isCommittedCardReturn,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                    }  // Full-viewport source-card chrome host (phone + tablet)
                }  // else shouldUseSplitLayout / immersive parent
            }  // else targetIsLandscape
    }

    @Composable
    fun BoxScope.VideoDetailRouteSheetOverlayContent() {
        VideoDetailPortraitOverlayAdapter(
            uiState = uiState,
            portraitExperienceEnabled = portraitExperienceEnabled,
            isPortraitFullscreen = isPortraitFullscreen,
            // 竖屏全屏持有时进入系统小窗：保持 pager 挂载以承接播放面，由 pager 隐藏装饰层。
            isInPipMode = isPipMode,
            useOfficialInlinePortraitDetailExperience = useOfficialInlinePortraitDetailExperience,
            isLandscape = isLandscape,
            shouldAnimatePortraitPager = shouldAnimatePortraitPager,
            motionSpec = portraitPagerMotionSpec,
            initialBvidOverride = pendingMainReloadBvidAfterPortrait,
            initialStartPositionMs = portraitSyncSnapshotPositionMs,
            entryCoverUrl = coverUrl,
            playbackViewModel = viewModel,
            engagementViewModel = engagementViewModel,
            sharedPlayer = if (useSharedPortraitPlayer) playerState.player else null,
            useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
            isPlayerSurfaceReady = isPlayerSurfaceReady,
            onBack = {
                if (directPortraitEntryFromRoute) {
                    handleBack()
                } else {
                    presentationState.setPortraitFullscreen(false)
                }
            },
            onHomeClick = {
                presentationState.setPortraitFullscreen(false)
                handleTopBarAction(resolveVideoDetailTopBarAction(isHomeButton = true))
            },
            onVideoChange = { portraitPendingSelectionBvid = it },
            onPlaybackIdentityChange = { updatedBvid, updatedCid, updatedCoverUrl ->
                // This callback follows the shared player's media identity, including the
                // early-switch path used while a swipe is still settling. Keep bvid/cid as one
                // snapshot so system back cannot combine the new video with the old detail cid.
                portraitPendingSelectionBvid = updatedBvid
                portraitSyncSnapshotBvid = updatedBvid
                portraitSyncSnapshotCid = updatedCid
                if (updatedCoverUrl.isNotBlank()) {
                    pendingInPageSwitchCoverUrl = updatedCoverUrl
                }
            },
            onProgressUpdate = { updatedBvid, positionMs, updatedCid, updatedCoverUrl ->
                // 竖屏轮询很密；只有身份变化或进度跨过阈值时才写回巨型详情页状态，
                // 避免每 200ms 重组 VideoDetailScreenStateHolder 把主线程打到 ANR。
                val shouldCommit = com.android.purebilibili.feature.video.ui.pager
                    .shouldCommitPortraitProgressToDetailState(
                        previousBvid = portraitSyncSnapshotBvid,
                        previousCid = portraitSyncSnapshotCid,
                        previousPositionMs = portraitSyncSnapshotPositionMs,
                        nextBvid = updatedBvid,
                        nextCid = updatedCid,
                        nextPositionMs = positionMs,
                    )
                if (shouldCommit) {
                    portraitPendingSelectionBvid = updatedBvid
                    portraitSyncSnapshotBvid = updatedBvid
                    portraitSyncSnapshotCid = updatedCid
                    portraitSyncSnapshotPositionMs = positionMs.coerceAtLeast(0L)
                    // 竖屏滑到第 N 个视频时冻结其封面，切回横屏时勿回落到路由首个视频封面。
                    if (updatedCoverUrl.isNotBlank()) {
                        pendingInPageSwitchCoverUrl = updatedCoverUrl
                    }
                    if (shouldMirrorPortraitProgressToMainPlayer) {
                        hasPendingPortraitSync = true
                        if (tryApplyPortraitProgressSync(updatedBvid, portraitSyncSnapshotPositionMs)) {
                            hasPendingPortraitSync = false
                        }
                    }
                }
            },
            onExitSnapshot = { updatedBvid, positionMs, updatedCid, updatedCoverUrl ->
                presentationState.switchVideo(updatedBvid, updatedCid)
                portraitPendingSelectionBvid = updatedBvid
                portraitSyncSnapshotBvid = updatedBvid
                portraitSyncSnapshotCid = updatedCid
                portraitSyncSnapshotPositionMs = positionMs.coerceAtLeast(0L)
                pendingMainReloadBvidAfterPortrait = updatedBvid
                if (updatedCoverUrl.isNotBlank()) {
                    pendingInPageSwitchCoverUrl = updatedCoverUrl
                }
                if (shouldMirrorPortraitProgressToMainPlayer) {
                    hasPendingPortraitSync = true
                    if (tryApplyPortraitProgressSync(updatedBvid, portraitSyncSnapshotPositionMs)) {
                        hasPendingPortraitSync = false
                    }
                }
            },
            onSearchClick = {
                hasDeferredPortraitRestoreAfterExternalNavigation =
                    com.android.purebilibili.feature.video.ui.pager
                        .shouldDeferPortraitRestoreUntilForegroundResume(
                            isPortraitFullscreen = isPortraitFullscreen,
                            isExternalNavigation = true,
                        )
                if (com.android.purebilibili.feature.video.ui.pager
                        .shouldExitPortraitForExternalNavigation(isPortraitFullscreen)
                ) {
                    presentationState.setPortraitFullscreen(false)
                }
                navigateToSearchFromVideo()
            },
            onUserClick = { mid ->
                val anchorBvid = portraitPendingSelectionBvid
                    ?: pendingMainReloadBvidAfterPortrait
                    ?: portraitSyncSnapshotBvid
                    ?: (uiState as? VideoPlaybackUiState.Success)?.info?.bvid
                if (!anchorBvid.isNullOrBlank()) {
                    val anchorCid = if (anchorBvid == portraitSyncSnapshotBvid) {
                        portraitSyncSnapshotCid
                    } else {
                        0L
                    }
                    presentationState.switchVideo(anchorBvid, anchorCid)
                    pendingMainReloadBvidAfterPortrait = anchorBvid
                }
                hasDeferredPortraitRestoreAfterExternalNavigation =
                    com.android.purebilibili.feature.video.ui.pager
                        .shouldDeferPortraitRestoreUntilForegroundResume(
                            isPortraitFullscreen = isPortraitFullscreen,
                            isExternalNavigation = true,
                        )
                if (com.android.purebilibili.feature.video.ui.pager
                        .shouldExitPortraitForUserSpaceNavigation(isPortraitFullscreen)
                ) {
                    presentationState.setPortraitFullscreen(false)
                }
                navigateToUserSpaceFromVideo(mid)
            },
            onRotateToLandscape = {
                presentationState.setPortraitFullscreen(false)
                val hostActivity = context.findActivity()
                val targetOrientation = resolvePortraitRotateTargetOrientation(
                    isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
                    manualPortraitHoldActive = manualPortraitHoldActive,
                )
                if (
                    hostActivity != null &&
                    targetOrientation != null &&
                    !isActivityInMultiWindowOrFloatingMode(
                        activity = hostActivity,
                        displayContext = displayContext,
                    )
                ) {
                    userRequestedFullscreen = true
                    manualPortraitHoldActive = false
                    hostActivity.applyPlayerRequestedOrientation(
                        requestedOrientation = targetOrientation,
                        displayContext = displayContext,
                    )
                } else {
                    toggleFullscreen()
                }
            },
        )

        VideoDetailCommonOverlayAdapter(
            interactiveChoicePanel = interactiveChoicePanel,
            engagementState = engagementState,
            playbackViewModel = viewModel,
            engagementViewModel = engagementViewModel,
            queueVisible = shouldShowExternalPlaylistQueueBar && showExternalPlaylistQueueSheet,
            queueTitle = externalPlaylistQueueTitle,
            playlist = playlistItems,
            playlistCurrentIndex = playlistCurrentIndex,
            hazeState = hazeState,
            queuePresentation = externalPlaylistQueueSheetPresentation,
            player = playerState.player,
            onDismissQueue = { showExternalPlaylistQueueSheet = false },
            onVideoSelected = { index, item ->
                PlaylistManager.playAt(index)
                showExternalPlaylistQueueSheet = false
                switchVideoInCurrentDetailPage(
                    targetBvid = item.bvid,
                    targetCid = 0L,
                    autoPlay = true,
                )
            },
        )

        val inputOverlayLayoutInfo = VideoDetailInputOverlayAdapter(
            context = context,
            configuration = configuration,
            viewModel = viewModel,
            commentState = commentState,
            showCommentInput = showCommentInput,
            isLandscape = isLandscape,
            isFullscreenMode = isFullscreenMode,
            isPortraitFullscreen = isPortraitFullscreen,
            videoPlayerRootBottomPx = videoPlayerRootBottomPx,
            hideStatusBars = systemBarsVisibilityPolicy.hideStatusBars,
            immersiveStatusBarBackdropEnabled = true,
            currentVideoPositionMsProvider = {
                playerState.player.currentPosition.coerceAtLeast(0L)
            },
        )
        val screenHeightPx = inputOverlayLayoutInfo.screenHeightPx
        val danmakuDialogTopReservePx = inputOverlayLayoutInfo.topReservedPx

        VideoDetailDownloadOverlayAdapter(
            viewModel = viewModel,
            uiState = uiState,
        )

        val successState = uiState as? VideoPlaybackUiState.Success
        DetachedVideoCommentThreadHost(
            threadListModifier = commentThreadPlayerScrollConnection?.let {
                Modifier.nestedScroll(it)
            } ?: Modifier,
            visible = shouldShowDetachedVideoCommentThreadHost(useTabletLayout = useTabletLayout) &&
                !(canShowLandscapeComments && landscapeCommentPanelVisible),
            successState = successState,
            commentState = commentState,
            commentViewModel = commentViewModel,
            forceInitialize = shouldForceInitializeDetachedCommentThreadHostForRoute(
                routeCommentRootRpid = openCommentRootRpidFromRoute,
                aid = successState?.info?.aid ?: 0L,
                hasHandledRouteComment = hasHandledCommentRootFromRoute
            ),
            viewModel = viewModel,
            onUpClick = navigateToUserSpaceFromVideo,
            onNavigateToRelatedVideo = { targetVideoId ->
                navigateToRelatedVideo(targetVideoId, null)
            },
            onSearchKeywordClick = navigateToSearchKeywordFromVideo,
            onOpenBilibiliLink = onOpenBilibiliLink,
            screenHeightPx = screenHeightPx,
            topReservedPx = danmakuDialogTopReservePx,
            onTimestampClick = { positionMs ->
                seekPlayerFromUserAction(playerState.player, positionMs)
                commentViewModel.closeSubReply()
            },
            onBackToTop = {
                // 评论区下滑缩小播放器后,一键回顶同时恢复播放器全尺寸。
                commentBackToTopRestoreFlow.tryEmit(Unit)
            },
            onCoveredBlurProgressChange = { progress ->
                subReplyCoveredBlurProgress = progress
            }
        )

        VideoDetailFavoriteFolderOverlayAdapter(
            visible = showFavoriteFolderDialog,
            viewModel = viewModel,
        )

        VideoDetailFeedbackOverlayAdapter(
            playbackViewModel = viewModel,
            engagementViewModel = engagementViewModel,
            engagementState = engagementState,
            playbackEventState = playbackEventState,
            hazeState = hazeState,
            isFullscreenMode = isFullscreenMode,
            isLandscape = isLandscape,
            reducedMotion = isReducedActionMotion,
        )

        VideoDetailPlayerSettingsOverlayAdapter(
            context = context,
            viewModel = viewModel,
            isFullscreenMode = isFullscreenMode,
            isPortraitFullscreen = isPortraitFullscreen,
            danmakuManager = danmakuManager,
        )
    }

    // 分屏 / 系统自由小窗的边界由 Android 窗口管理器控制。仅旋转 Compose
    // 根图层不会把任务窗口变成横向矩形，并会让播放器 Surface 与 UI 变换不一致，
    // 导致视频被拉伸；因此窗口内全屏仍使用正常布局，由播放器按视频比例适配窗口。
    val rootCardTransition = com.android.purebilibili.core.ui.transition
        .LocalMiuixVideoCardTransitionState.current
    VideoDetailScreenContent(
        transitionState = transitionState,
        routeSheetMotion = routeSheetMotion,
        // 系统小窗整窗就是播放面，底板与全屏一致用黑色，避免窗口边缘露出页面底色。
        isFullscreenMode = isFullscreenMode || isPipMode,
        backgroundColor = AppSurfaceTokens.background(),
        backgroundAlphaProvider = {
            if (!rootCardTransition.enabled || rootCardTransition.sourceChromeSnapshot?.isNowPlayingBar != true) {
                1f
            } else if (isCommittedCardReturn &&
                videoCardDepthBackgroundState.phaseProvider() == VideoCardTransitionBackgroundPhase.IDLE
            ) {
                0f
            } else {
                1f - resolveVideoDetailFlyingSourceChromeAlpha(
                    morphDepthProgress = rootCardTransition.progressProvider(),
                    phase = videoCardDepthBackgroundState.phaseProvider(),
                    isReturnGestureInProgress = videoCardDepthBackgroundState.isReturnGestureInProgressProvider() ||
                        videoCardDepthBackgroundState.isGestureRestoreInProgressProvider(),
                    sourceLayout = rootCardTransition.sourceLayout,
                    isNowPlayingBar = true,
                    isCommittedCardReturn = isCommittedCardReturn,
                    followProgressEnabled = videoCardDepthBackgroundState.returnContentFollowProgressEnabledProvider(),
                )
            }
        },
        modifier = detailShellModifier.videoInputDiagnostics("detail_root") {
            "fullscreen=$isFullscreenMode portraitFs=$isPortraitFullscreen landscape=$isLandscape " +
                "visible=$isVisible leaving=$useReturningVideoDetailVisualState phase=$continuousPlayerPhase " +
                "tab=${videoContentPagerState.currentPage} introScrolling=${introListState.isScrollInProgress} " +
                "commentScrolling=${commentListState.isScrollInProgress} pagerScrolling=${videoContentPagerState.isScrollInProgress} " +
                "playerBounds=$videoPlayerBounds"
        },
        mainContent = { VideoDetailRouteSheetMainContent() },
        overlayContent = { VideoDetailRouteSheetOverlayContent() }
    )
}
