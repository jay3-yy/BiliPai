package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.android.purebilibili.core.ui.InteractiveOverlayProgressVisual
import com.android.purebilibili.core.ui.InteractiveOverlaySurfaceType
import com.android.purebilibili.core.ui.resolveInteractiveOverlayProgressVisual
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.video.screen.CommentUrlNavigationTarget
import com.android.purebilibili.feature.video.screen.resolveCommentUrlNavigationTarget
import com.android.purebilibili.feature.video.ui.pager.resolveVideoSubReplySheetMaxHeightFraction
import com.android.purebilibili.feature.video.ui.pager.resolveVideoSubReplySheetScrimAlpha
import com.android.purebilibili.feature.video.ui.pager.resolvePortraitCommentVisibilityProgress
import com.android.purebilibili.feature.video.ui.pager.shouldDismissPortraitCommentSheetByDrag
import com.android.purebilibili.feature.video.ui.pager.shouldOpenPortraitCommentReplyComposer
import com.android.purebilibili.feature.video.ui.pager.shouldOpenPortraitCommentThreadDetail
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.FullCommentSearchUiState
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.resolveVideoCommentSkeletonSlotCount
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private const val MAIN_COMMENT_SHEET_HEIGHT_FRACTION = 0.60f
private const val MAIN_COMMENT_SHEET_SCRIM_ALPHA = 0.5f

internal enum class VideoCommentSheetHostContent {
    HIDDEN,
    MAIN_LIST,
    THREAD_DETAIL
}

internal fun resolveVideoCommentSheetHostContent(
    mainSheetVisible: Boolean,
    subReplyVisible: Boolean
): VideoCommentSheetHostContent {
    return when {
        subReplyVisible -> VideoCommentSheetHostContent.THREAD_DETAIL
        mainSheetVisible -> VideoCommentSheetHostContent.MAIN_LIST
        else -> VideoCommentSheetHostContent.HIDDEN
    }
}

internal fun resolveVideoCommentSheetHostHeightFraction(
    hostContent: VideoCommentSheetHostContent = VideoCommentSheetHostContent.MAIN_LIST,
    mainSheetVisible: Boolean,
    screenHeightPx: Int = 0,
    topReservedPx: Int = 0
): Float {
    return when (hostContent) {
        VideoCommentSheetHostContent.MAIN_LIST -> MAIN_COMMENT_SHEET_HEIGHT_FRACTION
        VideoCommentSheetHostContent.THREAD_DETAIL -> resolveVideoSubReplySheetMaxHeightFraction(
            screenHeightPx = screenHeightPx,
            topReservedPx = topReservedPx
        )
        VideoCommentSheetHostContent.HIDDEN -> 0f
    }
}

internal fun resolveVideoCommentSheetHostHeightPx(
    hostContent: VideoCommentSheetHostContent,
    hostHeightPx: Int,
    topReservedPx: Int
): Int {
    if (hostHeightPx <= 0) return 0
    return when (hostContent) {
        VideoCommentSheetHostContent.MAIN_LIST ->
            (hostHeightPx * MAIN_COMMENT_SHEET_HEIGHT_FRACTION)
                .roundToInt()
                .coerceIn(0, hostHeightPx)

        VideoCommentSheetHostContent.THREAD_DETAIL -> {
            val heightFraction = resolveVideoSubReplySheetMaxHeightFraction(
                screenHeightPx = hostHeightPx,
                topReservedPx = topReservedPx
            )
            (hostHeightPx * heightFraction)
                .roundToInt()
                .coerceIn(0, hostHeightPx)
        }

        VideoCommentSheetHostContent.HIDDEN -> 0
    }
}

internal fun resolveVideoCommentSheetHostScrimAlpha(
    mainSheetVisible: Boolean
): Float {
    return if (mainSheetVisible) {
        MAIN_COMMENT_SHEET_SCRIM_ALPHA
    } else {
        resolveVideoSubReplySheetScrimAlpha()
    }
}

internal fun shouldApplyVideoCommentThreadStatusBarPadding(
    mainSheetVisible: Boolean,
    topReservedPx: Int = 0
): Boolean {
    return !mainSheetVisible && topReservedPx <= 0
}

internal enum class VideoCommentPredictiveBackTarget {
    CLOSE_CONVERSATION,
    CLOSE_THREAD,
    DISMISS_SHEET
}

internal fun resolveVideoCommentPredictiveBackTarget(
    subReplyVisible: Boolean,
    conversationActive: Boolean
): VideoCommentPredictiveBackTarget {
    return when {
        subReplyVisible && conversationActive -> VideoCommentPredictiveBackTarget.CLOSE_CONVERSATION
        subReplyVisible -> VideoCommentPredictiveBackTarget.CLOSE_THREAD
        else -> VideoCommentPredictiveBackTarget.DISMISS_SHEET
    }
}

internal fun resolveVideoCommentPredictiveBackProgress(
    inProgress: Boolean,
    progress: Float
): Float {
    if (!inProgress) return 0f
    return progress.coerceIn(0f, 1f)
}

internal fun resolveCommentThreadPredictiveBackOffsetY(
    progress: Float,
    heightPx: Float,
): Float = progress.coerceIn(0f, 1f) * heightPx

internal fun resolveCommentThreadCoveredBlurProgress(threadBackProgress: Float): Float {
    val coveredDepth = (1f - threadBackProgress.coerceIn(0f, 1f))
    return coveredDepth * coveredDepth
}

internal fun shouldInitializeVideoCommentSheetHost(
    mainSheetVisible: Boolean,
    forceInitialize: Boolean
): Boolean {
    return mainSheetVisible || forceInitialize
}

internal fun shouldDismissVideoCommentSheetHostOnBackdropTap(
    mainSheetVisible: Boolean
): Boolean {
    return mainSheetVisible
}

internal fun shouldInterceptVideoCommentSheetHostBackdropTap(
    mainSheetVisible: Boolean
): Boolean = mainSheetVisible

internal fun shouldHandleVideoCommentSheetVerticalDrag(
    dragAmountPx: Float,
    currentOffsetPx: Float
): Boolean {
    return dragAmountPx > 0f || currentOffsetPx > 0f
}

internal fun resolveVideoCommentSheetDragTargetOffset(
    currentOffsetPx: Float,
    dragAmountPx: Float
): Float {
    return (currentOffsetPx + dragAmountPx).coerceAtLeast(0f)
}

internal fun resolveVideoCommentSheetDragStartOffset(
    renderedOffsetPx: Float,
    targetOffsetPx: Float
): Float {
    return max(renderedOffsetPx, targetOffsetPx).coerceAtLeast(0f)
}

internal fun resolvePortraitCommentDismissDragTargetOffset(sheetHeightPx: Float): Float {
    return sheetHeightPx.coerceAtLeast(0f)
}

internal fun shouldCompletePortraitCommentDismissDragSettling(
    sheetOffsetPx: Float,
    sheetHeightPx: Float
): Boolean {
    if (sheetHeightPx <= 0f) return true
    return sheetOffsetPx >= sheetHeightPx * 0.98f
}

internal fun resolveVideoCommentSheetDragVisibilityProgress(
    hostContent: VideoCommentSheetHostContent,
    mainSheetVisible: Boolean,
    isDismissDragSettling: Boolean,
    sheetOffsetPx: Float,
    sheetHeightPx: Float,
    hostVisibilityProgress: Float,
    isDragDismissExitPending: Boolean = false
): Float {
    return when {
        (hostContent != VideoCommentSheetHostContent.HIDDEN && mainSheetVisible) ||
            isDismissDragSettling ->
            resolvePortraitCommentVisibilityProgress(
                sheetOffsetPx = sheetOffsetPx,
                sheetHeightPx = sheetHeightPx
            )
        hostContent == VideoCommentSheetHostContent.THREAD_DETAIL &&
            !mainSheetVisible -> 1f
        hostContent == VideoCommentSheetHostContent.HIDDEN && hostVisibilityProgress > 0f ->
            if (isDragDismissExitPending) {
                0f
            } else {
                hostVisibilityProgress.coerceIn(0f, 1f)
            }
        else -> 0f
    }
}

internal fun resolveVideoCommentSheetPresentationProgress(
    hostVisibilityProgress: Float,
    dragVisibilityProgress: Float,
    preferDragProgress: Boolean = false
): Float {
    val hostProgress = hostVisibilityProgress.coerceIn(0f, 1f)
    val dragProgress = dragVisibilityProgress.coerceIn(0f, 1f)
    return when {
        preferDragProgress -> dragProgress
        dragProgress <= 0.001f -> 0f
        // 关闭评论区时 drag 会跟随 host 淡出；避免 host * host 造成视频缩放回弹。
        dragProgress + 0.001f >= hostProgress -> hostProgress
        else -> (hostProgress * dragProgress).coerceIn(0f, 1f)
    }
}

internal fun resolveVideoCommentSheetHostOverlayVisual(
    mainSheetVisible: Boolean,
    presentationProgress: Float,
    maxScrimAlphaOverride: Float? = null
): InteractiveOverlayProgressVisual {
    return resolveInteractiveOverlayProgressVisual(
        presentationProgress = presentationProgress,
        surfaceType = InteractiveOverlaySurfaceType.BOTTOM_SHEET,
        blurActive = mainSheetVisible,
        maxScrimAlpha = maxScrimAlphaOverride
            ?: resolveVideoCommentSheetHostScrimAlpha(mainSheetVisible)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun VideoCommentSheetHost(
    mainSheetVisible: Boolean,
    onDismiss: () -> Unit,
    /**
     * 覆盖全屏 scrim 的峰值透明度。
     *
     * 详情页楼中楼（嵌入呈现）场景传入 0f：打开子评论时不再盖住播放器上方的阴影，
     * 但 backdrop 点击拦截仍由 [mainSheetVisible] 控制，点背景关闭行为不受影响。
     */
    maxScrimAlphaOverride: Float? = null,
    onMainSheetVisibilityProgressChange: (Float) -> Unit = {},
    commentViewModel: VideoCommentViewModel,
    aid: Long,
    upMid: Long = 0,
    expectedReplyCount: Int = 0,
    emoteMap: Map<String, String> = emptyMap(),
    onRootCommentClick: () -> Unit = {},
    onReplyClick: (ReplyItem) -> Unit = {},
    onUserClick: (Long) -> Unit,
    onVideoClick: ((String) -> Unit)? = null,
    onSearchKeywordClick: ((String) -> Unit)? = null,
    onOpenBilibiliLink: ((String) -> Unit)? = null,
    onBackToTop: () -> Unit = {},
    screenHeightPx: Int = 0,
    topReservedPx: Int = 0,
    onTimestampClick: ((Long) -> Unit)? = null,
    maxTimestampMs: Long? = null,
    onImagePreview: ((List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit)? = null,
    forceInitialize: Boolean = false,
    handleFraudEvents: Boolean = true,
    onCoveredBlurProgressChange: ((Float) -> Unit)? = null,
    threadListModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val commentState by commentViewModel.commentState.collectAsStateWithLifecycle()
    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    val defaultSortMode by com.android.purebilibili.core.store.SettingsManager
        .getCommentDefaultSortMode(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager.getCommentDefaultSortModeSync(context),
            context = kotlin.coroutines.EmptyCoroutineContext
        )
    val commentMemberDecorationsEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCommentMemberDecorationsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val preferredSortMode = remember(defaultSortMode) {
        CommentSortMode.fromApiMode(defaultSortMode)
    }
    val hostContent = resolveVideoCommentSheetHostContent(
        mainSheetVisible = mainSheetVisible,
        subReplyVisible = subReplyState.visible
    )
    val mainCommentListState = rememberLazyListState()
    LaunchedEffect(aid) {
        if (mainCommentListState.firstVisibleItemIndex != 0 || mainCommentListState.firstVisibleItemScrollOffset != 0) {
            mainCommentListState.scrollToItem(0)
        }
    }
    val hostVisible = hostContent != VideoCommentSheetHostContent.HIDDEN
    // A host can be mounted already visible when opened from the inline list.
    // Start the sheet transition hidden so that first mount also slides upward.
    val sheetVisibility = remember { MutableTransitionState(false) }
    LaunchedEffect(hostVisible) {
        sheetVisibility.targetState = hostVisible
    }
    val scrimAlpha = maxScrimAlphaOverride
        ?: resolveVideoCommentSheetHostScrimAlpha(mainSheetVisible = mainSheetVisible)
    val dismissOnBackdropTap = shouldDismissVideoCommentSheetHostOnBackdropTap(
        mainSheetVisible = mainSheetVisible
    )
    val applyThreadStatusBarPadding = shouldApplyVideoCommentThreadStatusBarPadding(
        mainSheetVisible = mainSheetVisible,
        topReservedPx = topReservedPx
    )
    val motionSpec = rememberAppBottomSheetMotion()
    val appearance = rememberVideoCommentAppearance()
    var isDraggingSheet by remember { mutableStateOf(false) }
    var isDismissDragSettling by remember { mutableStateOf(false) }
    var isDragDismissExitPending by remember { mutableStateOf(false) }
    var sheetDragTargetOffsetPx by remember { mutableFloatStateOf(0f) }
    var mainSheetMeasuredHeightPx by remember { mutableFloatStateOf(0f) }
    val hostVisibilityProgress by animateFloatAsState(
        targetValue = if (hostVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (hostVisible) {
                motionSpec.contentEnterFadeDurationMillis
            } else {
                motionSpec.contentExitFadeDurationMillis
            }
        ),
        label = "video_comment_host_visibility_progress"
    )
    val sheetDragOffsetPx by animateFloatAsState(
        targetValue = sheetDragTargetOffsetPx,
        animationSpec = if (isDraggingSheet) tween(0) else motionSpec.dragSettleSpec,
        label = "video_comment_main_sheet_offset"
    )
    val latestSheetDragOffsetPx = rememberUpdatedState(sheetDragOffsetPx)
    val sheetDragVisibilityProgress = resolveVideoCommentSheetDragVisibilityProgress(
        hostContent = hostContent,
        mainSheetVisible = mainSheetVisible,
        isDismissDragSettling = isDismissDragSettling,
        sheetOffsetPx = sheetDragOffsetPx,
        sheetHeightPx = mainSheetMeasuredHeightPx,
        hostVisibilityProgress = hostVisibilityProgress,
        isDragDismissExitPending = isDragDismissExitPending
    )
    val mainSheetVisibilityProgress = resolveVideoCommentSheetPresentationProgress(
        hostVisibilityProgress = hostVisibilityProgress,
        dragVisibilityProgress = sheetDragVisibilityProgress,
        preferDragProgress = isDraggingSheet || isDismissDragSettling
    )

    SideEffect {
        onMainSheetVisibilityProgressChange(mainSheetVisibilityProgress)
    }

    LaunchedEffect(isDismissDragSettling, sheetDragOffsetPx, mainSheetMeasuredHeightPx, hostContent) {
        if (!isDismissDragSettling) return@LaunchedEffect
        if (
            shouldCompletePortraitCommentDismissDragSettling(
                sheetOffsetPx = sheetDragOffsetPx,
                sheetHeightPx = mainSheetMeasuredHeightPx
            )
        ) {
            isDismissDragSettling = false
            if (hostContent == VideoCommentSheetHostContent.THREAD_DETAIL) {
                commentViewModel.closeSubReply()
            } else {
                onDismiss()
            }
        }
    }
    val overlayVisual = remember(mainSheetVisible, mainSheetVisibilityProgress, maxScrimAlphaOverride) {
        resolveVideoCommentSheetHostOverlayVisual(
            mainSheetVisible = mainSheetVisible,
            presentationProgress = mainSheetVisibilityProgress,
            maxScrimAlphaOverride = maxScrimAlphaOverride
        )
    }

    LaunchedEffect(hostVisible, hostVisibilityProgress) {
        if (!hostVisible && hostVisibilityProgress <= 0f) {
            isDraggingSheet = false
            isDismissDragSettling = false
            isDragDismissExitPending = false
            sheetDragTargetOffsetPx = 0f
        }
    }

    var fallbackPreviewVisible by remember { mutableStateOf(false) }
    var fallbackPreviewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var fallbackPreviewIndex by remember { mutableIntStateOf(0) }
    var fallbackPreviewSourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var fallbackPreviewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }

    val previewCallback: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit =
        onImagePreview ?: { images, index, rect, textContent ->
            fallbackPreviewImages = images
            fallbackPreviewIndex = index
            fallbackPreviewSourceRect = rect
            fallbackPreviewTextContent = textContent
            fallbackPreviewVisible = true
        }

    if (fallbackPreviewVisible && fallbackPreviewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = fallbackPreviewImages,
            initialIndex = fallbackPreviewIndex,
            sourceRect = fallbackPreviewSourceRect?.rect,
            sourceRects = fallbackPreviewSourceRect?.galleryRects.orEmpty(),
            sourceCornerRadiusDp = fallbackPreviewSourceRect?.cornerRadiusDp
                ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
            textContent = fallbackPreviewTextContent,
            onDismiss = {
                fallbackPreviewVisible = false
                fallbackPreviewTextContent = null
            }
        )
    }

    val commentBackState = rememberNavigationEventState(NavigationEventInfo.None)
    var threadBackCompleted by remember { mutableStateOf(false) }
    val rawThreadBackProgress = resolveVideoCommentPredictiveBackProgress(
        inProgress = commentBackState.transitionState is NavigationEventTransitionState.InProgress &&
            hostContent == VideoCommentSheetHostContent.THREAD_DETAIL,
        progress = (commentBackState.transitionState as? NavigationEventTransitionState.InProgress)
            ?.latestEvent
            ?.progress
            ?: 0f
    )

    val threadBackProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = rawThreadBackProgress,
        animationSpec = if (commentBackState.transitionState is NavigationEventTransitionState.InProgress) {
            androidx.compose.animation.core.snap()
        } else {
            tween(180)
        },
        label = "comment_thread_predictive_back",
    )
    LaunchedEffect(subReplyState.visible) {
        if (subReplyState.visible) threadBackCompleted = false
    }
    NavigationBackHandler(
        state = commentBackState,
        isBackEnabled = hostVisible,
        onBackCompleted = {
            when (
                resolveVideoCommentPredictiveBackTarget(
                    subReplyVisible = subReplyState.visible,
                    conversationActive = subReplyState.conversationAnchor != null
                )
            ) {
                VideoCommentPredictiveBackTarget.CLOSE_CONVERSATION ->
                    commentViewModel.closeSubReplyConversation()
                VideoCommentPredictiveBackTarget.CLOSE_THREAD -> {
                    threadBackCompleted = rawThreadBackProgress > 0f
                    commentViewModel.closeSubReply()
                }
                VideoCommentPredictiveBackTarget.DISMISS_SHEET -> onDismiss()
            }
        },
    )
    val threadDrag = rememberCommentThreadDrag(
        visible = subReplyState.visible,
        rootReplyId = subReplyState.rootReply?.rpid,
        onDismiss = commentViewModel::closeSubReply,
    )
    val threadCoveredBlurProgress = if (
        hostContent == VideoCommentSheetHostContent.THREAD_DETAIL
    ) {
        resolveCommentThreadCoveredBlurProgress(maxOf(threadBackProgress, threadDrag.revealProgress))
    } else {
        0f
    }
    SideEffect {
        onCoveredBlurProgressChange?.invoke(threadCoveredBlurProgress)
    }
    DisposableEffect(Unit) {
        onDispose {
            onCoveredBlurProgressChange?.invoke(0f)
        }
    }

    LaunchedEffect(aid, mainSheetVisible, forceInitialize, preferredSortMode, upMid, expectedReplyCount) {
        if (shouldInitializeVideoCommentSheetHost(mainSheetVisible, forceInitialize)) {
            commentViewModel.init(
                aid = aid,
                upMid = upMid,
                preferredSortMode = preferredSortMode,
                expectedReplyCount = expectedReplyCount
            )
        }
    }

    CommentFraudFeedbackHost(commentViewModel = commentViewModel, enabled = handleFraudEvents)

    val openCommentUrl: (String) -> Unit = openCommentUrl@{ rawUrl ->
        val url = rawUrl.trim()
        if (url.isEmpty()) return@openCommentUrl
        if (onOpenBilibiliLink != null) {
            onOpenBilibiliLink(url)
            return@openCommentUrl
        }

        when (val target = resolveCommentUrlNavigationTarget(url)) {
            is CommentUrlNavigationTarget.Video -> {
                if (onVideoClick != null) {
                    onVideoClick(target.videoId)
                    return@openCommentUrl
                }
            }

            is CommentUrlNavigationTarget.Search -> {
                if (onSearchKeywordClick != null) {
                    onSearchKeywordClick(target.keyword)
                    return@openCommentUrl
                }
            }

            is CommentUrlNavigationTarget.Space -> {
                onUserClick(target.mid)
                return@openCommentUrl
            }

            null -> Unit
        }

        runCatching { uriHandler.openUri(url) }
    }

    AnimatedVisibility(
        visible = hostVisible,
        enter = motionSpec.scrimEnter,
        exit = motionSpec.scrimExit
    ) {
        val interceptBackdropTap = shouldInterceptVideoCommentSheetHostBackdropTap(
            mainSheetVisible = mainSheetVisible
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayVisual.scrimAlpha))
                .then(
                    if (interceptBackdropTap) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (dismissOnBackdropTap) {
                                    onDismiss()
                                }
                            }
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            val density = LocalDensity.current
            val hostHeightPx = with(density) { maxHeight.toPx().roundToInt() }
            val sheetHeightPx = remember(hostContent, hostHeightPx, topReservedPx) {
                resolveVideoCommentSheetHostHeightPx(
                    hostContent = hostContent,
                    hostHeightPx = hostHeightPx,
                    topReservedPx = topReservedPx
                )
            }
            val sheetHeight = with(density) { sheetHeightPx.toDp() }
            AnimatedVisibility(
                visibleState = sheetVisibility,
                enter = motionSpec.contentEnter,
                exit = motionSpec.contentExit,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight)
                        .onSizeChanged { size ->
                            mainSheetMeasuredHeightPx = size.height.toFloat()
                        }
                        .offset { IntOffset(x = 0, y = sheetDragOffsetPx.roundToInt()) }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    color = if (mainSheetVisible) {
                        appearance.panelColor.copy(
                            alpha = appearance.panelColor.alpha * overlayVisual.surfaceAlphaMultiplier
                        )
                    } else {
                        Color.Transparent
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (mainSheetVisible && hostContent != VideoCommentSheetHostContent.HIDDEN) {
                            val coveredBlurProgress = threadCoveredBlurProgress
                            val coveredBlurEffectCache = remember { BlurRenderEffectCache() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        renderEffect = null
                                        if (coveredBlurProgress > 0f &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                        ) {
                                            val blurFrame = resolvePredictiveBackBlurFrame(
                                                progress = coveredBlurProgress,
                                            )
                                            renderEffect = if (blurFrame.blurRadiusPx > 0.5f) {
                                                coveredBlurEffectCache.resolve(blurFrame.blurRadiusPx)
                                            } else {
                                                null
                                            }
                                        }
                                    },
                            ) {
                                VideoCommentMainList(
                                    viewModel = commentViewModel,
                                    showIdentityDecorations = commentMemberDecorationsEnabled,
                                    onRootCommentClick = onRootCommentClick,
                                    onReplyClick = onReplyClick,
                                    onUserClick = onUserClick,
                                    onCommentUrlClick = openCommentUrl,
                                    onTimestampClick = onTimestampClick,
                                    maxTimestampMs = maxTimestampMs,
                                    onImagePreview = previewCallback,
                                    onRefresh = commentViewModel::refreshComments,
                                    headerDragModifier = Modifier.pointerInput(
                                        mainSheetVisible,
                                        hostContent,
                                        mainSheetMeasuredHeightPx,
                                    ) {
                                        if (hostContent != VideoCommentSheetHostContent.MAIN_LIST) {
                                            return@pointerInput
                                        }
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                isDismissDragSettling = false
                                                isDragDismissExitPending = false
                                                isDraggingSheet = true
                                                sheetDragTargetOffsetPx = resolveVideoCommentSheetDragStartOffset(
                                                    renderedOffsetPx = latestSheetDragOffsetPx.value,
                                                    targetOffsetPx = sheetDragTargetOffsetPx,
                                                )
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                if (
                                                    shouldHandleVideoCommentSheetVerticalDrag(
                                                        dragAmountPx = dragAmount,
                                                        currentOffsetPx = sheetDragTargetOffsetPx,
                                                    )
                                                ) {
                                                    change.consume()
                                                    sheetDragTargetOffsetPx = resolveVideoCommentSheetDragTargetOffset(
                                                        currentOffsetPx = sheetDragTargetOffsetPx,
                                                        dragAmountPx = dragAmount,
                                                    )
                                                }
                                            },
                                            onDragEnd = {
                                                isDraggingSheet = false
                                                if (
                                                    shouldDismissPortraitCommentSheetByDrag(
                                                        sheetOffsetPx = sheetDragTargetOffsetPx,
                                                        sheetHeightPx = mainSheetMeasuredHeightPx,
                                                    )
                                                ) {
                                                    isDismissDragSettling = true
                                                    isDragDismissExitPending = true
                                                    sheetDragTargetOffsetPx =
                                                        resolvePortraitCommentDismissDragTargetOffset(
                                                            sheetHeightPx = mainSheetMeasuredHeightPx,
                                                        )
                                                } else {
                                                    sheetDragTargetOffsetPx = 0f
                                                }
                                            },
                                            onDragCancel = {
                                                isDraggingSheet = false
                                                sheetDragTargetOffsetPx = 0f
                                            },
                                        )
                                    },
                                    onBackToTop = onBackToTop,
                                    listState = mainCommentListState,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = hostContent == VideoCommentSheetHostContent.THREAD_DETAIL &&
                                subReplyState.rootReply != null,
                            enter = motionSpec.contentEnter,
                            exit = if (threadBackCompleted) androidx.compose.animation.ExitTransition.None else motionSpec.contentExit,
                        ) {
                            val rootReply = subReplyState.rootReply
                            if (rootReply != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(threadDrag.containerModifier)
                                        .graphicsLayer {
                                            translationY = threadDrag.offsetPx.value + resolveCommentThreadPredictiveBackOffsetY(
                                                progress = threadBackProgress,
                                                heightPx = size.height,
                                            )
                                        }
                                        .background(appearance.panelColor),
                                ) {
                                    SubReplyDetailContent(
                                        listModifier = threadListModifier,
                                        headerDragModifier = threadDrag.headerModifier,
                                        rootReply = rootReply,
                                        subReplies = subReplyState.items,
                                        sortMode = subReplyState.sortMode,
                                        error = subReplyState.error,
                                        onSortModeChange = commentViewModel::setSubReplySortMode,
                                        remoteReplyCount = subReplyState.totalCount,
                                        isLoading = subReplyState.isLoading,
                                        isEnd = subReplyState.isEnd,
                                        emoteMap = emoteMap,
                                        onLoadMore = { commentViewModel.loadMoreSubReplies() },
                                        onRefresh = commentViewModel::refreshSubReplies,
                                        isRefreshing = subReplyState.isRefreshing,
                                        onDismiss = { commentViewModel.closeSubReply() },
                                        applyStatusBarPadding = applyThreadStatusBarPadding,
                                        onRootCommentClick = onRootCommentClick,
                                        onTimestampClick = onTimestampClick,
                                        upMid = subReplyState.upMid,
                                        showUpFlag = commentState.showUpFlag,
                                        showIdentityDecorations = commentMemberDecorationsEnabled,
                                        onImagePreview = previewCallback,
                                        onReplyClick = onReplyClick,
                                        onConversationClick = commentViewModel::openSubReplyConversation,
                                        onConversationBack = commentViewModel::closeSubReplyConversation,
                                        isConversationMode = subReplyState.conversationAnchor != null,
                                        conversationAnchorId = subReplyState.conversationAnchor?.rpid ?: 0L,
                                        dissolvingIds = subReplyState.dissolvingIds,
                                        currentMid = commentState.currentMid,
                                        onDissolveStart = { rpid -> commentViewModel.startSubDissolve(rpid) },
                                        onDeleteComment = { rpid -> commentViewModel.deleteSubComment(rpid) },
                                        onCheckCommentFraud = commentViewModel::checkCommentFraud,
                                        onCommentLike = commentViewModel::likeComment,
                                        onCommentHate = commentViewModel::hateComment,
                                        onReportComment = commentViewModel::reportComment,
                                        likedComments = commentState.likedComments,
                                        hatedComments = commentState.hatedComments,
                                        onUrlClick = openCommentUrl,
                                        onAvatarClick = { mid ->
                                            mid.toLongOrNull()?.let(onUserClick)
                                        },
                                        maxTimestampMs = maxTimestampMs,
                                        targetReplyId = subReplyState.targetReplyId
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoCommentMainList(
    viewModel: VideoCommentViewModel,
    showIdentityDecorations: Boolean,
    onRootCommentClick: () -> Unit,
    onReplyClick: (ReplyItem) -> Unit,
    onUserClick: (Long) -> Unit,
    onCommentUrlClick: (String) -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    maxTimestampMs: Long?,
    onImagePreview: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit,
    onRefresh: () -> Unit,
    headerDragModifier: Modifier = Modifier,
    onBackToTop: () -> Unit = {},
    scrollToTopRequest: Int = 0,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val avatarPushEnabled = rememberCommentAvatarPushEnabled()
    val state by viewModel.commentState.collectAsStateWithLifecycle()
    val fullSearchState by viewModel.fullSearchState.collectAsStateWithLifecycle()
    val fullSearchReplies by viewModel.fullSearchReplies.collectAsStateWithLifecycle()
    val repliesError = state.repliesError
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearance = rememberVideoCommentAppearance()
    val commentChromeBackdrop = rememberLayerBackdrop()
    // 首屏骨架按视口高度铺够槽位：少铺会让列表在数据到达时被撑长、整体跳一下。
    val commentSkeletonSlotCount = resolveVideoCommentSkeletonSlotCount(
        LocalConfiguration.current.screenHeightDp
    )
    var showCommentSearchSheet by remember { mutableStateOf(false) }
    val latestOnBackToTop by rememberUpdatedState(onBackToTop)
    val shouldShowBackToTop by remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            )
        }
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest <= 0) return@LaunchedEffect
        listState.scrollToItem(0)
        latestOnBackToTop()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CommentFraudDetectingBanner(isDetecting = state.isDetectingFraud)

        CommentSortHeader(
            count = state.replyCount,
            sortMode = state.sortMode,
            onSortModeChange = { mode ->
                viewModel.setSortMode(mode)
                scope.launch { SettingsManager.setCommentDefaultSortMode(context, mode.apiMode) }
            },
            onSearchClick = { showCommentSearchSheet = true },
            compactTypography = true,
            modifier = Modifier
                .fillMaxWidth()
                .then(headerDragModifier),
        )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AdaptivePullToRefreshBox(
                    isRefreshing = state.isRepliesRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(commentChromeBackdrop),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues()
                    ) {
                    state.voteCard?.let { card ->
                        item(key = "comment_vote_${card.voteId}") {
                            VideoCommentVoteCard(
                                card = card,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item {
                        AppSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            color = appearance.composerHintBackgroundColor,
                            shape = AppShapes.container(ContainerLevel.Card),
                            onClick = onRootCommentClick
                        ) {
                            AppText(
                                text = "说点什么，直接评论 UP 主和大家",
                                color = appearance.secondaryTextColor,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                    if (repliesError != null) {
                        item(key = "comment_refresh_error") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(
                                    text = repliesError,
                                    color = AppContentColors.error(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                AppTextButton(onClick = onRefresh) {
                                    AppText("重试")
                                }
                            }
                        }
                    }
                    if (
                        state.isRepliesLoading &&
                        !state.isRepliesRefreshing &&
                        state.replies.isEmpty()
                    ) {
                        // 骨架拆成多个独立 lazy item（而不是 1 个 item 里塞 5 行）：数据到达时
                        // 是一个槽位换一个槽位，列表锚点不失效，不会整体跳一下。
                        videoCommentSkeletonItems(
                            slotCount = commentSkeletonSlotCount,
                            keyPrefix = "comment_skeleton_",
                        )
                    }

                    items(
                        items = state.replies,
                        key = { it.rpid },
                        contentType = { resolveReplyItemContentType(it) }
                    ) { reply ->
                        ReplyItemView(
                            modifier = videoCommentItemModifier(),
                            avatarListState = listState.takeIf { avatarPushEnabled },
                            item = reply,
                            upMid = state.upMid,
                            showUpFlag = state.showUpFlag,
                            showIdentityDecorations = showIdentityDecorations,
                            isPinned = reply.rpid in state.pinnedReplyIds,
                            onClick = {},
                            onSubClick = { parentReply, targetReplyId ->
                                if (shouldOpenPortraitCommentThreadDetail(useEmbeddedPresentation = true)) {
                                    viewModel.openSubReply(parentReply, targetReplyId)
                                }
                            },
                            onTimestampClick = onTimestampClick,
                            maxTimestampMs = maxTimestampMs,
                            onImagePreview = onImagePreview,
                            onLikeClick = { viewModel.likeComment(reply.rpid) },
                            onHateClick = { viewModel.hateComment(reply.rpid) },
                            isHated = reply.action == 2 || reply.rpid in state.hatedComments,
                            onReplyClick = {
                                if (shouldOpenPortraitCommentReplyComposer()) {
                                    onReplyClick(reply)
                                }
                            },
                            onReportClick = { reason -> viewModel.reportComment(reply.rpid, reason) },
                            canToggleTop = shouldShowReplyTopAction(
                                currentMid = state.currentMid,
                                upMid = state.upMid,
                                item = reply
                            ),
                            onToggleTopClick = { viewModel.toggleTopComment(reply) },
                            onUrlClick = onCommentUrlClick,
                            onAvatarClick = { mid -> mid.toLongOrNull()?.let(onUserClick) ?: Unit }
                        )
                    }

                    item {
                        when {
                            state.isRepliesRefreshing || repliesError != null -> Unit
                            state.isRepliesEnd -> NoMoreFooter()
                            else -> {
                                if (!state.isRepliesLoading) {
                                    LaunchedEffect(
                                        state.replies.size,
                                        state.isRepliesLoading,
                                        state.isRepliesRefreshing,
                                        repliesError,
                                        state.isRepliesEnd,
                                    ) {
                                        if (
                                            !state.isRepliesLoading &&
                                            !state.isRepliesRefreshing &&
                                            repliesError == null &&
                                            !state.isRepliesEnd
                                        ) {
                                            viewModel.loadComments()
                                        }
                                    }
                                }
                                if (!(state.isRepliesLoading && state.replies.isEmpty())) {
                                    LoadingFooter()
                                }
                            }
                        }
                    }
                }
                }

                AppLiquidGlassBackToTopButton(
                    visible = rememberBackToTopButtonEnabled() && shouldShowBackToTop,
                    backdrop = commentChromeBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                    onClick = {
                        // 回顶时通知父级(竖屏详情)恢复被评论区压缩的播放器。
                        onBackToTop()
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                )
            }
    }

    if (showCommentSearchSheet) {
        CommentSearchSheet(
            replies = state.replies,
            fullReplies = fullSearchReplies,
            fullSearchState = fullSearchState,
            onLoadAllComments = { viewModel.loadAllCommentsForSearch() },
            upMid = state.upMid,
            onCommentClick = { reply ->
                onReplyClick(reply)
            },
            onSubReplyClick = { rootReply ->
                viewModel.openSubReply(rootReply)
            },
            onDismiss = { showCommentSearchSheet = false },
            miuixBackdrop = commentChromeBackdrop,
        )
    }
}

@Composable
private fun LoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AdaptiveLoadingIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun NoMoreFooter() {
    val appearance = rememberVideoCommentAppearance()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = "没有更多了",
            color = appearance.secondaryTextColor,
            fontWeight = FontWeight.Normal
        )
    }
}
