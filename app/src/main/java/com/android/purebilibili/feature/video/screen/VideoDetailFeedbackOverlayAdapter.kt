package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.feature.video.share.VideoShareFeedbackEvents
import com.android.purebilibili.feature.video.viewmodel.VideoMaidAction
import com.android.purebilibili.core.ui.BlueSnowMaidAnimation
import com.android.purebilibili.core.ui.MaidAnimation
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.feedback.LikeBurstAnchorRegistry
import com.android.purebilibili.feature.video.ui.components.LikeBurstAnimation
import com.android.purebilibili.feature.video.ui.components.TripleSuccessAnimation
import com.android.purebilibili.feature.video.ui.components.VideoActionFeedbackHost
import com.android.purebilibili.feature.video.ui.feedback.TripleCelebrationPlacement
import com.android.purebilibili.feature.video.ui.feedback.VideoFeedbackAnchor
import com.android.purebilibili.feature.video.ui.feedback.resolveQualityReminderPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveLikeBurstPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveTripleCelebrationPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoFeedbackPlacement
import com.android.purebilibili.feature.video.viewmodel.PlayerToastPresentation
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect

@Composable
internal fun BoxScope.VideoDetailFeedbackOverlayAdapter(
    playbackViewModel: VideoPlaybackViewModel,
    engagementViewModel: VideoEngagementViewModel,
    engagementState: VideoEngagementUiState,
    playbackEventState: VideoDetailPlaybackEventState,
    hazeState: HazeState,
    isFullscreenMode: Boolean,
    isLandscape: Boolean,
    reducedMotion: Boolean,
) {
    val feedbackBottomInsetDp = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
        .value
        .roundToInt() + if (isFullscreenMode) 24 else 20
    val feedbackPlacement = resolveVideoFeedbackPlacement(
        isFullscreen = isFullscreenMode,
        isLandscape = isLandscape,
        bottomInsetDp = feedbackBottomInsetDp,
    )
    val safePadding = WindowInsets.safeDrawing.union(WindowInsets.ime).asPaddingValues()
    val safeEndPadding = safePadding.calculateEndPadding(LocalLayoutDirection.current)
    val likeBurstPlacement = resolveLikeBurstPlacement(safePadding.calculateBottomPadding().value.roundToInt())
    val window = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.windowSizeClass
    val compactCelebration = isFullscreenMode || isLandscape || window.heightDp < 480.dp
    val availableHeight = window.heightDp.value - safePadding.calculateTopPadding().value -
        likeBurstPlacement.bottomInsetDp
    val availableWidth = window.widthDp.value - safePadding.calculateLeftPadding(LocalLayoutDirection.current).value -
        safePadding.calculateRightPadding(LocalLayoutDirection.current).value - 32f
    val hasFeedbackSpace = availableHeight >= 96f && availableWidth >= 96f
    val celebrationSize = minOf(
        availableHeight - 32f, availableWidth,
        if (compactCelebration) 180f else 220f
    ).coerceAtLeast(1f).dp
    val likeSize = minOf(availableHeight, availableWidth, 144f).coerceAtLeast(1f).dp
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, engagementViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            VideoShareFeedbackEvents.events.collect { engagementViewModel.showShareFeedback(it) }
        }
    }
    LaunchedEffect(hasFeedbackSpace, engagementState.likeBurstVisible, engagementState.tripleCelebrationId, engagementState.maidActionId) {
        if (!hasFeedbackSpace) {
            if (engagementState.likeBurstVisible) engagementViewModel.dismissLikeBurst()
            engagementViewModel.dismissMaidAction(engagementState.maidActionId)
            engagementViewModel.cancelTripleCelebration(engagementState.tripleCelebrationId)
        }
    }
    if ((engagementState.likeBurstVisible || engagementState.maidAction != null) && hasFeedbackSpace) {
        // 锚定点赞图标：女仆中心对齐图标右缘、底边压在图标顶部，形成站在图标右上角的姿态；
        // 图标位置缺失时退回旧的右下角静态 inset 布局。
        val likeIconBounds = LikeBurstAnchorRegistry.likeIcon.bounds
        val density = LocalDensity.current
        val maidSizePx = with(density) { likeSize.toPx() }
        val configuration = LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val topSafePx = with(density) { safePadding.calculateTopPadding().toPx() }
        val bottomSafePx = with(density) { safePadding.calculateBottomPadding().toPx() }
        val sideSafePx = with(density) { safeEndPadding.toPx() }
        val horizontalLeadPx = with(density) { 4.dp.toPx() }
        val verticalOverlapPx = with(density) { 12.dp.toPx() }
        val anchoredOffset: IntOffset? = likeIconBounds?.let { bounds ->
            val x = bounds.right - maidSizePx / 2f + horizontalLeadPx
            val y = bounds.top - maidSizePx + verticalOverlapPx
            IntOffset(
                x.roundToInt()
                    .coerceIn(
                        sideSafePx.roundToInt(),
                        (screenWidthPx - sideSafePx - maidSizePx).roundToInt().coerceAtLeast(sideSafePx.roundToInt())
                    ),
                y.roundToInt()
                    .coerceAtLeast(topSafePx.roundToInt())
                    .coerceAtMost((screenHeightPx - bottomSafePx - maidSizePx).roundToInt())
            )
        }
        Box(
            modifier = if (anchoredOffset != null) {
                Modifier
                    .align(Alignment.TopStart)
                    .offset { anchoredOffset }
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = safeEndPadding + likeBurstPlacement.sideInsetDp.dp,
                        bottom = likeBurstPlacement.bottomInsetDp.dp
                    )
            },
        ) {
            // 以事件 ID 为 key：连续点赞时（visible 保持 true）也强制重挂、重播动画，
            // 同时旧实例的延迟 onFinished 不会误关新一次动画。
            val action = engagementState.maidAction
            key(action, engagementState.maidActionId, engagementState.likeBurstId) {
                if (action == null) {
                    LikeBurstAnimation(
                        visible = true,
                        reducedMotion = reducedMotion,
                        size = likeSize,
                        onAnimationEnd = { engagementViewModel.dismissLikeBurst(engagementState.likeBurstId) },
                    )
                } else {
                    BlueSnowMaidAnimation(
                        animation = when (action) {
                            VideoMaidAction.DISLIKE -> MaidAnimation.DISLIKE_CONFIRMED
                            VideoMaidAction.SHARE -> MaidAnimation.SHARE_READY
                            VideoMaidAction.COIN -> MaidAnimation.COIN_SUCCESS
                        },
                        modifier = Modifier.size(likeSize),
                        reducedMotion = reducedMotion,
                        staticDisplayDurationMs = 1_000L,
                        completionHoldDurationMs = if (action == VideoMaidAction.DISLIKE) 400L else 500L,
                        onFinished = { engagementViewModel.dismissMaidAction(engagementState.maidActionId) }
                    )
                }
            }
        }
    }

    val tripleCelebrationPlacement = resolveTripleCelebrationPlacement(
        isFullscreen = isFullscreenMode,
        isLandscape = isLandscape,
    )
    if (engagementState.tripleCelebrationVisible && hasFeedbackSpace) {
        Box(
            modifier = Modifier
                .align(
                    when (tripleCelebrationPlacement) {
                        TripleCelebrationPlacement.CenterOverlay -> Alignment.Center
                        TripleCelebrationPlacement.BottomTrailing -> Alignment.BottomEnd
                    },
                )
                .padding(
                    end = safeEndPadding + 16.dp,
                    bottom = likeBurstPlacement.bottomInsetDp.dp
                ),
        ) {
            key(engagementState.tripleCelebrationId) {
                TripleSuccessAnimation(
                    visible = true,
                    isCompact = compactCelebration,
                    size = celebrationSize,
                    reducedMotion = reducedMotion,
                    onAnimationEnd = {
                        engagementViewModel.completeTripleCelebration(engagementState.tripleCelebrationId)
                    },
                )
            }
        }
    }

    val popupMessage = playbackEventState.popupMessage
    val activeFeedbackPlacement = if (
        popupMessage?.presentation == PlayerToastPresentation.CenteredHighlight
    ) {
        resolveQualityReminderPlacement()
    } else {
        feedbackPlacement
    }
    val feedbackContext = androidx.compose.ui.platform.LocalContext.current
    val feedbackHintScale by com.android.purebilibili.core.store.SettingsManager
        .getLongPressSpeedHintScale(feedbackContext)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.SettingsManager
                .getLongPressSpeedHintScaleSync(feedbackContext)
        )
    val feedbackHintAlpha by com.android.purebilibili.core.store.SettingsManager
        .getLongPressSpeedHintAlpha(feedbackContext)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.SettingsManager
                .getLongPressSpeedHintAlphaSync(feedbackContext)
        )
    VideoActionFeedbackHost(
        message = popupMessage?.message,
        visible = popupMessage != null,
        placement = activeFeedbackPlacement,
        hazeState = hazeState,
        scale = feedbackHintScale,
        backgroundAlphaOverride = feedbackHintAlpha,
    )

    val coverSavedSnackbarHostState = androidx.compose.runtime.remember {
        androidx.compose.material3.SnackbarHostState()
    }
    LaunchedEffect(playbackViewModel) {
        playbackViewModel.coverSavedShareEvents.collect { (title, coverUrl) ->
            coverSavedSnackbarHostState.currentSnackbarData?.dismiss()
            val result = coverSavedSnackbarHostState.showSnackbar(
                message = "封面已保存到相册",
                actionLabel = "分享",
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                    putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$coverUrl")
                }
                feedbackContext.startActivity(
                    android.content.Intent.createChooser(shareIntent, "分享视频封面")
                )
            }
        }
    }
    com.android.purebilibili.core.ui.components.AppSnackbarHost(
        hostState = coverSavedSnackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = feedbackBottomInsetDp.dp),
    )

    val resumePlaybackSuggestion by playbackViewModel.resumePlaybackSuggestion.collectAsStateWithLifecycle()
    resumePlaybackSuggestion?.let { suggestion ->
        AppAlertDialog(
            onDismissRequest = playbackViewModel::dismissResumePlaybackSuggestion,
            title = { AppText("继续播放") },
            text = {
                AppText(
                    text = "检测到上次播放到 ${suggestion.targetLabel}（${FormatUtils.formatDuration(suggestion.positionMs)}），是否跳转继续播放？",
                )
            },
            confirmButton = {
                AppTextButton(onClick = playbackViewModel::continueResumePlaybackSuggestion) {
                    AppText("跳转")
                }
            },
            dismissButton = {
                AppTextButton(onClick = playbackViewModel::dismissResumePlaybackSuggestion) {
                    AppText("稍后")
                }
            },
        )
    }
}
