package com.android.purebilibili.feature.video.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.bottomSheetContentEnterTransition
import com.android.purebilibili.core.ui.bottomSheetContentExitTransition
import com.android.purebilibili.core.ui.bottomSheetScrimEnterTransition
import com.android.purebilibili.core.ui.bottomSheetScrimExitTransition
import com.android.purebilibili.core.ui.InteractiveOverlayProgressVisual
import com.android.purebilibili.core.ui.InteractiveOverlaySurfaceType
import com.android.purebilibili.core.ui.resolveAdaptiveBottomSheetMotionSpec
import com.android.purebilibili.core.ui.resolveInteractiveOverlayProgressVisual
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.resolveCommentFraudLightMessage
import com.android.purebilibili.data.repository.shouldShowCommentFraudResultDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.video.screen.CommentUrlNavigationTarget
import com.android.purebilibili.feature.video.screen.resolveCommentUrlNavigationTarget
import com.android.purebilibili.feature.video.ui.pager.resolveVideoSubReplySheetMaxHeightFraction
import com.android.purebilibili.feature.video.ui.pager.resolveVideoSubReplySheetScrimAlpha
import com.android.purebilibili.feature.video.ui.pager.resolvePortraitCommentVisibilityProgress
import com.android.purebilibili.feature.video.ui.pager.shouldDismissPortraitCommentSheetByDrag
import com.android.purebilibili.feature.video.ui.pager.shouldOpenPortraitCommentReplyComposer
import com.android.purebilibili.feature.video.ui.pager.shouldOpenPortraitCommentThreadDetail
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

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
    mainSheetVisible: Boolean,
    hostContent: VideoCommentSheetHostContent = VideoCommentSheetHostContent.MAIN_LIST
): Float {
    return 0f
}

internal fun shouldApplyVideoCommentThreadStatusBarPadding(
    mainSheetVisible: Boolean,
    topReservedPx: Int = 0
): Boolean {
    return !mainSheetVisible && topReservedPx <= 0
}

internal fun shouldInitializeVideoCommentSheetHost(
    mainSheetVisible: Boolean,
    forceInitialize: Boolean
): Boolean {
    return mainSheetVisible || forceInitialize
}

internal fun shouldDismissVideoCommentSheetHostOnBackdropTap(
    mainSheetVisible: Boolean,
    hostContent: VideoCommentSheetHostContent = VideoCommentSheetHostContent.MAIN_LIST
): Boolean {
    return mainSheetVisible && hostContent != VideoCommentSheetHostContent.THREAD_DETAIL
}

internal fun shouldInterceptVideoCommentSheetHostBackdropTap(
    mainSheetVisible: Boolean,
    hostContent: VideoCommentSheetHostContent = VideoCommentSheetHostContent.MAIN_LIST
): Boolean = mainSheetVisible && hostContent != VideoCommentSheetHostContent.THREAD_DETAIL

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
    hostContent: VideoCommentSheetHostContent = VideoCommentSheetHostContent.MAIN_LIST
): InteractiveOverlayProgressVisual {
    return resolveInteractiveOverlayProgressVisual(
        presentationProgress = presentationProgress,
        surfaceType = InteractiveOverlaySurfaceType.BOTTOM_SHEET,
        blurActive = mainSheetVisible,
        maxScrimAlpha = resolveVideoCommentSheetHostScrimAlpha(mainSheetVisible, hostContent)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun VideoCommentSheetHost(
    mainSheetVisible: Boolean,
    onDismiss: () -> Unit,
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
    screenHeightPx: Int = 0,
    topReservedPx: Int = 0,
    onTimestampClick: ((Long) -> Unit)? = null,
    maxTimestampMs: Long? = null,
    onImagePreview: ((List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit)? = null,
    forceInitialize: Boolean = false,
    handleFraudEvents: Boolean = true
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
    val hostVisible = hostContent != VideoCommentSheetHostContent.HIDDEN
    val scrimAlpha = resolveVideoCommentSheetHostScrimAlpha(mainSheetVisible = mainSheetVisible)
    val dismissOnBackdropTap = shouldDismissVideoCommentSheetHostOnBackdropTap(
        mainSheetVisible = mainSheetVisible,
        hostContent = hostContent
    )
    val applyThreadStatusBarPadding = shouldApplyVideoCommentThreadStatusBarPadding(
        mainSheetVisible = mainSheetVisible,
        topReservedPx = topReservedPx
    )
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val motionSpec = remember(uiPreset, androidNativeVariant) {
        resolveAdaptiveBottomSheetMotionSpec(uiPreset, androidNativeVariant)
    }
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
        animationSpec = tween(
            durationMillis = when {
                isDraggingSheet -> 0
                isDismissDragSettling -> motionSpec.contentExitFadeDurationMillis.coerceAtLeast(180)
                else -> 180
            }
        ),
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
    val overlayVisual = remember(mainSheetVisible, mainSheetVisibilityProgress, hostContent) {
        resolveVideoCommentSheetHostOverlayVisual(
            mainSheetVisible = mainSheetVisible,
            presentationProgress = mainSheetVisibilityProgress,
            hostContent = hostContent
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
    var fallbackPreviewSourceRect by remember { mutableStateOf<Rect?>(null) }
    var fallbackPreviewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }

    val previewCallback: (List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit =
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
            sourceRect = fallbackPreviewSourceRect,
            textContent = fallbackPreviewTextContent,
            onDismiss = {
                fallbackPreviewVisible = false
                fallbackPreviewTextContent = null
            }
        )
    }

    BackHandler(enabled = hostVisible) {
        if (subReplyState.visible) {
            commentViewModel.closeSubReply()
        } else {
            onDismiss()
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

    var fraudDialogStatus by remember { mutableStateOf<CommentFraudStatus?>(null) }
    LaunchedEffect(handleFraudEvents) {
        if (!handleFraudEvents) return@LaunchedEffect
        commentViewModel.fraudEvent.collect { status ->
            val lightMessage = resolveCommentFraudLightMessage(status)
            if (lightMessage != null) {
                Toast.makeText(context, lightMessage, Toast.LENGTH_SHORT).show()
            } else if (shouldShowCommentFraudResultDialog(status)) {
                fraudDialogStatus = status
            }
        }
    }

    fraudDialogStatus?.let { status ->
        CommentFraudResultDialog(
            status = status,
            onDismiss = {
                fraudDialogStatus = null
                commentViewModel.dismissFraudResult()
            },
            onDeleteComment = if (status == CommentFraudStatus.SHADOW_BANNED) {
                {
                    val rpid = commentViewModel.commentState.value.fraudDetectRpid
                    if (rpid > 0) {
                        commentViewModel.startDissolve(rpid)
                    }
                }
            } else null
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
        enter = bottomSheetScrimEnterTransition(uiPreset, androidNativeVariant),
        exit = bottomSheetScrimExitTransition(uiPreset, androidNativeVariant)
    ) {
        val interceptBackdropTap = shouldInterceptVideoCommentSheetHostBackdropTap(
            mainSheetVisible = mainSheetVisible,
            hostContent = hostContent
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
                visible = hostVisible,
                enter = bottomSheetContentEnterTransition(uiPreset, androidNativeVariant),
                exit = bottomSheetContentExitTransition(uiPreset, androidNativeVariant),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight)
                        .onSizeChanged { size ->
                            mainSheetMeasuredHeightPx = size.height.toFloat()
                        }
                        .offset { IntOffset(x = 0, y = sheetDragOffsetPx.roundToInt()) }
                        .pointerInput(mainSheetVisible, hostContent, mainSheetMeasuredHeightPx) {
                            if (hostContent == VideoCommentSheetHostContent.HIDDEN) {
                                return@pointerInput
                            }
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDraggingSheet = true
                                    sheetDragTargetOffsetPx = resolveVideoCommentSheetDragStartOffset(
                                        renderedOffsetPx = latestSheetDragOffsetPx.value,
                                        targetOffsetPx = sheetDragTargetOffsetPx
                                    )
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (
                                        shouldHandleVideoCommentSheetVerticalDrag(
                                            dragAmountPx = dragAmount,
                                            currentOffsetPx = sheetDragTargetOffsetPx
                                        )
                                    ) {
                                        change.consume()
                                        sheetDragTargetOffsetPx = resolveVideoCommentSheetDragTargetOffset(
                                            currentOffsetPx = sheetDragTargetOffsetPx,
                                            dragAmountPx = dragAmount
                                        )
                                    }
                                },
                                onDragEnd = {
                                    isDraggingSheet = false
                                    if (
                                        shouldDismissPortraitCommentSheetByDrag(
                                            sheetOffsetPx = sheetDragTargetOffsetPx,
                                            sheetHeightPx = mainSheetMeasuredHeightPx
                                        )
                                    ) {
                                        isDismissDragSettling = true
                                        isDragDismissExitPending = true
                                        sheetDragTargetOffsetPx =
                                            resolvePortraitCommentDismissDragTargetOffset(
                                                sheetHeightPx = mainSheetMeasuredHeightPx
                                            )
                                    } else {
                                        sheetDragTargetOffsetPx = 0f
                                    }
                                },
                                onDragCancel = {
                                    isDraggingSheet = false
                                    sheetDragTargetOffsetPx = 0f
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    color = appearance.panelColor.copy(
                        alpha = appearance.panelColor.alpha * overlayVisual.surfaceAlphaMultiplier
                    )
                ) {
                    AnimatedContent(
                        targetState = hostContent,
                        transitionSpec = {
                            val opensThreadDetail =
                                initialState == VideoCommentSheetHostContent.MAIN_LIST &&
                                    targetState == VideoCommentSheetHostContent.THREAD_DETAIL
                            val closesThreadDetail =
                                initialState == VideoCommentSheetHostContent.THREAD_DETAIL &&
                                    targetState == VideoCommentSheetHostContent.MAIN_LIST
                            val direction = when {
                                opensThreadDetail -> 1
                                closesThreadDetail -> -1
                                else -> 0
                            }
                            val enter = fadeIn(animationSpec = tween(220)) +
                                slideInHorizontally(animationSpec = tween(260)) { width ->
                                    when {
                                        direction > 0 -> width / 2
                                        direction < 0 -> -width / 2
                                        else -> 0
                                    }
                                }
                            val exit = fadeOut(animationSpec = tween(200)) +
                                slideOutHorizontally(animationSpec = tween(240)) { width ->
                                    when {
                                        direction > 0 -> -width / 3
                                        direction < 0 -> width / 3
                                        else -> 0
                                    }
                                }
                            enter togetherWith exit using SizeTransform(clip = false)
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "video_comment_host_content"
                    ) { targetContent ->
                        when (targetContent) {
                            VideoCommentSheetHostContent.MAIN_LIST -> {
                                VideoCommentMainList(
                                    viewModel = commentViewModel,
                                    showIdentityDecorations = commentMemberDecorationsEnabled,
                                    onRootCommentClick = onRootCommentClick,
                                    onReplyClick = onReplyClick,
                                    onUserClick = onUserClick,
                                    onCommentUrlClick = openCommentUrl,
                                    onTimestampClick = onTimestampClick,
                                    maxTimestampMs = maxTimestampMs,
                                    onImagePreview = previewCallback
                                )
                            }

                            VideoCommentSheetHostContent.THREAD_DETAIL -> {
                                val rootReply = subReplyState.rootReply
                                if (rootReply != null) {
                                    SubReplyDetailContent(
                                        rootReply = rootReply,
                                        subReplies = subReplyState.items,
                                        remoteReplyCount = subReplyState.totalCount,
                                        isLoading = subReplyState.isLoading,
                                        isEnd = subReplyState.isEnd,
                                        emoteMap = emoteMap,
                                        onLoadMore = { commentViewModel.loadMoreSubReplies() },
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
                                        dissolvingIds = subReplyState.dissolvingIds,
                                        currentMid = commentState.currentMid,
                                        onDissolveStart = { rpid -> commentViewModel.startSubDissolve(rpid) },
                                        onDeleteComment = { rpid -> commentViewModel.deleteSubComment(rpid) },
                                        onCommentLike = commentViewModel::likeComment,
                                        onReportComment = commentViewModel::reportComment,
                                        likedComments = commentState.likedComments,
                                        onUrlClick = openCommentUrl,
                                        onAvatarClick = { mid ->
                                            mid.toLongOrNull()?.let(onUserClick)
                                        },
                                        maxTimestampMs = maxTimestampMs,
                                        targetReplyId = subReplyState.targetReplyId
                                    )
                                }
                            }

                            VideoCommentSheetHostContent.HIDDEN -> Unit
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
    onImagePreview: (List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit
) {
    val state by viewModel.commentState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearance = rememberVideoCommentAppearance()
    val commentChromeBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val shouldShowBackToTop by remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CommentSortFilterBar(
            count = state.replyCount,
            sortMode = state.sortMode,
            onSortModeChange = { mode ->
                viewModel.setSortMode(mode)
                scope.launch {
                    SettingsManager.setCommentDefaultSortMode(context, mode.apiMode)
                }
            },
            upOnly = state.upOnlyFilter,
            onUpOnlyToggle = { viewModel.toggleUpOnly() },
            backdrop = commentChromeBackdrop
        )

        CommentFraudDetectingBanner(isDetecting = state.isDetectingFraud)

        if (state.isRepliesLoading && state.replies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AdaptiveLoadingIndicator()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(commentChromeBackdrop),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            color = appearance.composerHintBackgroundColor,
                            shape = RoundedCornerShape(16.dp),
                            onClick = onRootCommentClick
                        ) {
                            Text(
                                text = "说点什么，直接评论 UP 主和大家",
                                color = appearance.secondaryTextColor,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    items(
                        items = state.replies,
                        key = { it.rpid },
                        contentType = { resolveReplyItemContentType(it) }
                    ) { reply ->
                        ReplyItemView(
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
                        if (!state.isRepliesEnd) {
                            LaunchedEffect(Unit) {
                                viewModel.loadComments()
                            }
                            LoadingFooter()
                        } else {
                            NoMoreFooter()
                        }
                    }
                }

                VideoCommentBackToTopButton(
                    visible = shouldShowBackToTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoCommentBackToTopButton(
    visible: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f),
        exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.92f)
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = rememberAppChevronUpIcon(),
                contentDescription = "回到顶部"
            )
        }
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
        CircularProgressIndicator(strokeWidth = 2.dp)
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
        Text(
            text = "没有更多了",
            color = appearance.secondaryTextColor,
            fontWeight = FontWeight.Normal
        )
    }
}
