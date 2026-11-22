package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.appPressHighlightPressSpec
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import com.android.purebilibili.core.ui.components.LocalElasticPressEnabled
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.animation.gl.ThanosEffectView
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import com.android.purebilibili.core.ui.findHostActivity
import com.android.purebilibili.core.ui.transition.captureNativeVideoCardImage
import com.android.purebilibili.core.ui.transition.captureNativeVideoCardBitmap
import com.android.purebilibili.core.ui.transition.recordNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import com.android.purebilibili.core.ui.transition.NowPlayingBarHandoffState
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.resolveNowPlayingBarReturnVisibility
import com.android.purebilibili.core.util.CardPositionManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.android.purebilibili.feature.audio.lyrics.halcyon.darken
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.resolveBiliPaiBottomBarShellColor
import com.android.purebilibili.feature.home.components.resolveBottomBarDarkTheme
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop

private const val NOW_PLAYING_CAPTURE_TIMEOUT_MILLIS = 500L

internal data class AudioNowPlayingBarState(
    val bvid: String = "",
    val title: String,
    val artist: String,
    val artistAvatarUrl: String = "",
    val cover: AudioNowPlayingCover,
    val isPlaying: Boolean,
    val playbackSpeed: Float = 1f
)

internal class AudioNowPlayingCover(val request: ImageRequest) {
    // 当前播放项只保留一张已解码的静态封面，供独立条和合并底栏交接。
    var bitmap: ImageBitmap? = null
}

@Composable
internal fun rememberAudioNowPlayingCover(bvid: String, url: String): AudioNowPlayingCover {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { resolveAudioNowPlayingCoverSizeDp(0f, 0f).dp.roundToPx() }
    return remember(context, bvid, url, sizePx) {
        AudioNowPlayingCover(
            ImageRequest.Builder(context).data(url).size(sizePx).scale(Scale.FILL).build()
        )
    }
}

@Composable
private fun AudioNowPlayingCoverImage(cover: AudioNowPlayingCover, modifier: Modifier) {
    // 本宿主继续展示自己已启动的请求，交接后的宿主才直接使用已解码结果。
    val bitmap = remember(cover) { cover.bitmap }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        AsyncImage(
            model = cover.request,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onSuccess = { success ->
                val image = success.result.image as? BitmapImage
                if (image != null && image.shareable) cover.bitmap = image.bitmap.asImageBitmap()
            },
        )
    }
}

@Composable
internal fun AudioNowPlayingBar(
    state: AudioNowPlayingBarState,
    showDeleteButton: Boolean,
    onExpand: (hasRecordedSource: Boolean) -> Unit,
    onCompactClick: (() -> Unit)? = null,
    isLayoutStable: Boolean = true,
    pauseArtworkRotation: Boolean = false,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCancelPlayback: () -> Unit,
    onDismiss: () -> Unit,
    expandDestinationLabel: String = "听视频",
    sourceRoute: String,
    handoff: NowPlayingBarHandoffState = NowPlayingBarHandoffState.Idle,
    glassEnabled: Boolean = LocalSettingsLiquidGlassEnabled.current,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassTuning: LiquidGlassTuning = LocalLiquidGlassRenderConfig.current.tuning,
    liftAboveBottomBar: Boolean = true,
    consumeNavigationBarsPadding: Boolean = true,
    bottomSpacing: Dp? = null,
    dockHosted: Boolean = false,
    dockMergeProgress: () -> Float = { 0f },
    iconOnlyProgress: () -> Float = { 0f },
    surfaceMergeProgress: () -> Float = dockMergeProgress,
    modifier: Modifier = Modifier
) {
    com.android.purebilibili.core.ui.components.ProvideAppElasticFeedback {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenWidthPx = remember(configuration.screenWidthDp, density) {
            with(density) { configuration.screenWidthDp.dp.toPx() }
        }
        val screenHeightPx = remember(configuration.screenHeightDp, density) {
            with(density) { configuration.screenHeightDp.dp.toPx() }
        }
        SideEffect {
            CardPositionManager.invalidateVideoSourceIfWindowChanged(screenWidthPx, screenHeightPx)
        }

        // 视频详情和听视频共用小横条的系统窗口来源。
        val nativeBarLayer = if (LocalVideoCardSourceTrackingEnabled.current) {
            rememberNativeVideoCardLayer()
        } else null
        val snapshotScope = key(state.bvid, sourceRoute) { rememberCoroutineScope() }
        val latestLayoutStable by rememberUpdatedState(isLayoutStable)
        var captureInProgress by remember(state.bvid, sourceRoute) { mutableStateOf(false) }
        var freezeSourceLayer by remember(state.bvid, sourceRoute) { mutableStateOf(false) }
        var pendingSourceFrame by remember(state.bvid, sourceRoute) {
            mutableStateOf<CompletableDeferred<Unit>?>(null)
        }
        val barCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val closeCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val coverCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val dissolveContext = LocalContext.current
        val artworkGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
        val reduceMotion = LocalSystemReduceMotion.current
        val cancelCaptureJob = remember { arrayOfNulls<Job>(1) }

        // 从窗口已绘制的像素截取横条，避免离屏重绘实时模糊/旋转图层。
        // 点按取消立即暂停；粒子首帧隐藏本体，消散完成后再关闭会话。
        val dismissAfterCancel by rememberUpdatedState(onDismiss)
        var cancelDissolving by remember(state.bvid) { mutableStateOf(false) }
        var dissolveContentHidden by remember(state.bvid) { mutableStateOf(false) }
        var dissolveEffectView by remember { mutableStateOf<ThanosEffectView?>(null) }

        fun finishCancelDissolveCleanup() {
            // Dock / AnimatedVisibility 保留本体直到退场结束。粒子结束后不能恢复
            // alpha，否则清理与宿主移除之间会重新露出横条，随后再淡出一次。
            dissolveContentHidden = true
            dissolveEffectView?.dispose()
            dissolveEffectView = null
            // 关闭过程保持锁定；退出组合（或切换视频）后才重置。
        }

        val handleCancelClick: () -> Unit = {
            if (!cancelDissolving) {
                cancelDissolving = true
                onCancelPlayback()
                val hostWindow = dissolveContext.findHostActivity()?.window
                if (hostWindow == null || !artworkGate.active || !isThanosEffectSupported(dissolveContext)) {
                    dismissAfterCancel()
                } else {
                    cancelCaptureJob[0] = snapshotScope.launch {
                        try {
                            withFrameNanos { }
                            val windowBounds = barCoordsRef[0]?.takeIf { it.isAttached }?.boundsInWindow()
                            val snapshot = if (windowBounds != null && !windowBounds.isEmpty) {
                                withTimeoutOrNull(NOW_PLAYING_CAPTURE_TIMEOUT_MILLIS) {
                                    captureAudioNowPlayingWindowSnapshot(hostWindow, windowBounds)
                                }
                            } else {
                                null
                            }
                            if (snapshot == null) {
                                finishCancelDissolveCleanup()
                                dismissAfterCancel()
                                return@launch
                            }
                            val bitmap = snapshot.bitmap
                            dissolveEffectView?.dispose()
                            dissolveEffectView = ThanosEffectView.attach(
                                window = hostWindow,
                                bitmap = bitmap,
                                windowBounds = android.graphics.RectF(snapshot.windowBounds),
                                sweepReversed = true,
                                originInWindow = closeCoordsRef[0]?.takeIf { it.isAttached }
                                    ?.boundsInWindow()?.center?.let { android.graphics.PointF(it.x, it.y) },
                                onFirstFrame = { dissolveContentHidden = true },
                                onComplete = {
                                    finishCancelDissolveCleanup()
                                    dismissAfterCancel()
                                },
                            )
                            if (dissolveEffectView == null) {
                                if (!bitmap.isRecycled) bitmap.recycle()
                                finishCancelDissolveCleanup()
                                dismissAfterCancel()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            com.android.purebilibili.core.util.Logger.w(
                                "AudioNowPlayingBar", "Cancel dissolve failed: ${error.message}",
                            )
                            finishCancelDissolveCleanup()
                            dismissAfterCancel()
                        }
                    }
                }
            }
        }

        LaunchedEffect(artworkGate.active, cancelDissolving, state.bvid) {
            // 关闭已开始而窗口不再呈现时，直接结束粒子和截图任务，不能在后台逐帧运行。
            if (!artworkGate.active && cancelDissolving) {
                cancelCaptureJob[0]?.cancel()
                finishCancelDissolveCleanup()
                dismissAfterCancel()
            }
        }
        DisposableEffect(state.bvid) {
            onDispose {
                cancelCaptureJob[0]?.cancel()
                dissolveEffectView?.dispose()
                dissolveEffectView = null
            }
        }

        suspend fun expandFromBar(layer: GraphicsLayer) {
            // 布局落位只保证坐标已更新。等录制层真正绘完同一尺寸后再冻结像素。
            val renderedFrame = CompletableDeferred<Unit>()
            pendingSourceFrame = renderedFrame
            val recorded = withTimeoutOrNull(NOW_PLAYING_CAPTURE_TIMEOUT_MILLIS) {
                renderedFrame.await()
            } != null
            if (recorded) yield()
            if (CardPositionManager.isVideoNavigationDebounceActive()) return
            if (!recorded) {
                onExpand(false)
                return
            }
            val bounds = barCoordsRef[0]?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()
            if (bounds == null ||
                !bounds.left.isFinite() || !bounds.top.isFinite() ||
                !bounds.right.isFinite() || !bounds.bottom.isFinite() || bounds.isEmpty
            ) {
                onExpand(false)
                return
            }
            CardPositionManager.recordVideoCardPosition(
                bvid = state.bvid,
                sourceRoute = sourceRoute,
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                density = density.density,
                sourceCornerDp = com.android.purebilibili.core.ui.transition
                    .resolveNowPlayingBarSourceCornerDp(bounds.width, bounds.height, density.density),
                coverBounds = coverCoordsRef[0]?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot(),
                sourceLayout = if (iconOnlyProgress() >= 0.99f) {
                    VideoCardSourceLayout.COVER_ONLY
                } else {
                    VideoCardSourceLayout.SIDE_BY_SIDE
                },
                sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                    title = state.title,
                    ownerName = state.artist,
                    ownerFaceUrl = state.artistAvatarUrl,
                    viewText = "",
                    danmakuText = "",
                    durationText = "",
                    followed = false,
                    isNowPlayingBar = true,
                ),
            )
            CardPositionManager.clearNativeVideoCardLayers()
            captureNativeVideoCardImage(layer)
            freezeSourceLayer = true
            val expectedSourceKey = CardPositionManager.lastClickedVideoSourceKey
            val sourceOwner = CardPositionManager.videoCardPixelOwnerFor(expectedSourceKey)
            try {
                captureNativeVideoCardBitmap(layer, expectedSourceKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                com.android.purebilibili.core.util.Logger.w(
                    "AudioNowPlayingBar", "Could not freeze return snapshot: ${error.message}",
                )
            }
            if (CardPositionManager.isActiveVideoCardPixelOwner(sourceOwner)) {
                onExpand(true)
            }
        }
        val handleExpand = nowPlayingBarExpand@{
            if (onCompactClick != null) {
                onCompactClick()
                return@nowPlayingBarExpand
            }
            if (captureInProgress || CardPositionManager.isVideoNavigationDebounceActive()) {
                return@nowPlayingBarExpand
            }
            if (nativeBarLayer == null || state.bvid.isBlank()) {
                onExpand(false)
                return@nowPlayingBarExpand
            }
            captureInProgress = true
            snapshotScope.launch {
                try {
                    expandFromBar(nativeBarLayer)
                } finally {
                    pendingSourceFrame = null
                    freezeSourceLayer = false
                    captureInProgress = false
                }
            }
        }
        val sourceInActiveReturn by remember(handoff, state.bvid) {
            derivedStateOf {
                resolveNowPlayingBarReturnVisibility(handoff = handoff, currentBvid = state.bvid) <= 0f
            }
        }

        val chrome = resolveMusicPlayerChromeSpec(
            uiStyle = LocalAppUiStyle.current,
            glassEnabled = glassEnabled
        )
        val shape = resolveSharedBottomBarCapsuleShape()
        val glassActive = glassEnabled && miuixBackdrop != null
        val immersiveBackdrop: Color? =
            com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
                .immersiveBackdropColor.collectAsState().value
        val defaultContainerColor = resolveBiliPaiBottomBarShellColor(
            containerColor = AppSurfaceTokens.surfaceContainer(),
            liquidGlassEnabled = glassEnabled,
            darkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background()),
            liquidGlassTuning = liquidGlassTuning,
        )
        // 悬浮在沉浸式音乐页上时，容器取封面主色的暗化版本，避免主题中性灰与
        // 封面氛围色冲突。玻璃材质保留外壳的半透明度，仅替换 RGB 为氛围色调。
        val containerColor = if (immersiveBackdrop != null) {
            val tint = immersiveBackdrop.darken(0.52f)
            if (glassActive) {
                tint.copy(alpha = defaultContainerColor.alpha)
            } else {
                androidx.compose.ui.graphics.lerp(defaultContainerColor, tint, 0.85f)
            }
        } else {
            defaultContainerColor
        }
        val immersiveContentColor = if (immersiveBackdrop != null) {
            Color.White
        } else {
            null
        }
        // 跑马灯仅在页面可见、布局稳定且没有卡片过渡时运行。
        val decorativeMotionAllowed = artworkGate.active && !reduceMotion && isLayoutStable &&
            !pauseArtworkRotation && !captureInProgress && !cancelDissolving && !sourceInActiveReturn
        val elasticState = rememberAppElasticPressState()
        val glassMotion = glassActive && miuixBackdrop != null
        val interactionEnabled = !sourceInActiveReturn && !captureInProgress && !cancelDissolving
        val swipeFeedbackEnabled = interactionEnabled && !reduceMotion && LocalComponentMotionEnabled.current &&
            LocalElasticPressEnabled.current && LocalAppThemeConfig.current.globalDragFeedbackEnabled
        val swipeOffset = remember(sourceRoute, swipeFeedbackEnabled) { mutableFloatStateOf(0f) }
        val latestSkipNext = rememberUpdatedState(onSkipNext)
        val latestSkipPrevious = rememberUpdatedState(onSkipPrevious)
        // 玻璃外壳与正文共用位移，折射采样跟着外壳；原生路径移动整条。
        val glassLayer: GraphicsLayerScope.() -> Unit = remember(elasticState, swipeOffset) {
            {
                translationX = 0f
                elasticState.layerBlock(this)
                translationX += swipeOffset.floatValue
            }
        }
        Box(
            modifier = modifier
                .then(artworkGate.modifier)
                .fillMaxWidth()
                .then(if (consumeNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    start = if (dockHosted) 0.dp else chrome.horizontalPaddingDp.dp,
                    end = if (dockHosted) 0.dp else chrome.horizontalPaddingDp.dp,
                    bottom = when {
                        dockHosted -> 0.dp
                        liftAboveBottomBar -> 72.dp
                        // 独立入口显式统一各材质间距；首页保留原有宿主布局和默认值。
                        bottomSpacing != null -> bottomSpacing
                        !glassActive && chrome.uiStyle == com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3 -> 16.dp
                        else -> 8.dp
                    }
                )
                .videoCardSourceAnchor(state.bvid, shape, enabled = nativeBarLayer != null) { coordinates ->
                    barCoordsRef[0] = coordinates
                }
                // 源锚点和录制层使用同一个未缩放的胶囊尺寸，松手回弹不会改变系统落点。
                .appElasticPress(
                    enabled = interactionEnabled,
                    state = elasticState,
                    transformInBackdrop = glassMotion,
                )
                .graphicsLayer {
                    // The transition host owns the source pixels during return; do not
                    // start a second settle animation when the real bar is revealed.
                    alpha = if (dissolveContentHidden) 0f else resolveNowPlayingBarReturnVisibility(
                        handoff = handoff,
                        currentBvid = state.bvid,
                    )
                }
                .recordNativeVideoCardLayer(
                    layer = nativeBarLayer,
                    // 小横条只在点击展开时录制一次来源像素。常驻录制会让每个静止帧
                    // 重新生成 GraphicsLayer display list，且返回交接期间已有冻结位图。
                    enabled = captureInProgress,
                    freezeProvider = {
                        nativeBarLayer != null && freezeSourceLayer &&
                            CardPositionManager.isNativeVideoCardLayerCurrentOwner(nativeBarLayer)
                    },
                    // Visibility is owned by the explicit now-playing handoff above.
                    sourceRoute = sourceRoute,
                    onRecorded = {
                        val frame = pendingSourceFrame
                        val coordinates = barCoordsRef[0]
                        if (frame != null && latestLayoutStable && coordinates?.isAttached == true &&
                            coordinates.size == nativeBarLayer?.size
                        ) {
                            frame.complete(Unit)
                        }
                    },
                )
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("删除听视频条") {
                        handleCancelClick()
                        true
                    })
                    contentDescription = if (onCompactClick != null) {
                        "当前视频：${state.title}，收起搜索并展开视频小横条"
                    } else {
                        "当前视频：${state.title}，打开$expandDestinationLabel"
                    }
                }
                .clickable(
                    enabled = interactionEnabled,
                    onClick = handleExpand,
                )
                .then(
                    if (interactionEnabled) {
                        Modifier.audioNowPlayingSkipGesture(
                            swipeOffset = swipeOffset.takeIf { swipeFeedbackEnabled },
                            onSkipNext = latestSkipNext,
                            onSkipPrevious = latestSkipPrevious,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(if (!glassMotion && swipeFeedbackEnabled) {
                    Modifier.graphicsLayer { translationX = swipeOffset.floatValue }
                } else Modifier)
                .then(if (sourceInActiveReturn) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            if (!dissolveContentHidden) Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = 1f - surfaceMergeProgress().coerceIn(0f, 1f) }
                    .biliPaiFloatingDockShell(
                        backdrop = miuixBackdrop,
                        interactionLayerBlock = glassLayer.takeIf { glassMotion },
                        interactionState = elasticState.takeIf { glassMotion },
                        containerColor = containerColor,
                        pressProgress = 0f,
                        shape = shape,
                        enabled = glassActive,
                        blurEnabled = blurEnabled,
                        hazeState = hazeState,
                        liquidGlassTuning = liquidGlassTuning,
                        // Use the app's current animated palette directly: a luminance
                        // threshold would flip this scrim halfway through a theme change.
                        readabilitySurfaceColor = AppSurfaceTokens.background(),
                    )
            )
            Box(
                modifier = if (glassMotion) Modifier.graphicsLayer(glassLayer) else Modifier,
            ) {
                CompositionLocalProvider(LocalElasticPressEnabled provides false) {
                    AudioNowPlayingBarContentRow(
                        modifier = Modifier.clip(shape),
                        mergeProgress = dockMergeProgress,
                        searchProgress = iconOnlyProgress,
                        showDeleteButton = showDeleteButton,
                        cover = {
                            AudioNowPlayingCoverImage(
                                cover = state.cover,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { coordinates ->
                                        coverCoordsRef[0] = coordinates
                                    }
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                        },
                        title = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = resolveAudioNowPlayingPrimaryProgress(iconOnlyProgress())
                                    },
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AppText(
                                    text = state.title,
                                    modifier = if (state.isPlaying && decorativeMotionAllowed) {
                                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                    } else {
                                        Modifier
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    softWrap = false,
                                    color = immersiveContentColor
                                        ?: AppContentColors.primaryText(),
                                    fontWeight = FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp)
                                )
                                Box(
                                    modifier = Modifier
                                        .audioNowPlayingArtistHeight(dockMergeProgress, iconOnlyProgress)
                                        .clipToBounds()
                                        .graphicsLayer {
                                            alpha = resolveAudioNowPlayingSupplementalAlpha(
                                                mergeProgress = dockMergeProgress(),
                                                searchProgress = iconOnlyProgress(),
                                            )
                                        },
                                ) {
                                    AppText(
                                        text = state.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                        color = immersiveContentColor?.copy(alpha = 0.72f)
                                            ?: AppContentColors.secondaryText()
                                    )
                                }
                            }
                        },
                        play = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .graphicsLayer {
                                        val primary = resolveAudioNowPlayingPrimaryProgress(iconOnlyProgress())
                                        alpha = resolveAudioNowPlayingPrimaryAlpha(iconOnlyProgress())
                                        scaleX = primary
                                        scaleY = primary
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                                    AppIcon(
                                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        modifier = Modifier.size(22.dp),
                                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                                        tint = immersiveContentColor ?: AppContentColors.primaryText()
                                    )
                                }
                            }
                        },
                        next = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .graphicsLayer {
                                        val supplemental = resolveAudioNowPlayingSupplementalProgress(
                                            mergeProgress = dockMergeProgress(),
                                            searchProgress = iconOnlyProgress(),
                                        )
                                        alpha = resolveAudioNowPlayingSupplementalAlpha(
                                            mergeProgress = dockMergeProgress(),
                                            searchProgress = iconOnlyProgress(),
                                        )
                                        scaleX = supplemental
                                        scaleY = supplemental
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                                    AppIcon(
                                        Icons.Filled.SkipNext,
                                        modifier = Modifier.size(24.dp),
                                        contentDescription = "下一首",
                                        tint = immersiveContentColor ?: AppContentColors.primaryText(),
                                    )
                                }
                            }
                        },
                    )
                }
                if (showDeleteButton && !dissolveContentHidden) {
                    AppIconButton(
                        onClick = handleCancelClick,
                        enabled = !sourceInActiveReturn && !captureInProgress && !cancelDissolving,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 2.dp)
                            .size(24.dp)
                            .onGloballyPositioned { closeCoordsRef[0] = it }
                            .layout { measurable, constraints ->
                                val child = measurable.measure(constraints)
                                layout(child.width, child.height) {
                                    if (iconOnlyProgress() < 0.5f) child.placeRelative(0, 0)
                                }
                            },
                    ) {
                        AppIcon(
                            Icons.Filled.Close,
                            contentDescription = "删除听视频条",
                            modifier = Modifier.size(14.dp),
                            tint = immersiveContentColor ?: AppContentColors.secondaryText(),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.audioNowPlayingSkipGesture(
    swipeOffset: MutableFloatState?,
    onSkipNext: State<() -> Unit>,
    onSkipPrevious: State<() -> Unit>,
): Modifier = pointerInput(swipeOffset) {
    coroutineScope {
        var totalDrag = 0f
        var startOffset = 0f
        var returnJob: Job? = null

        fun releaseOffset() {
            if (swipeOffset == null || swipeOffset.floatValue == 0f) return
            returnJob = launch {
                AnimationState(initialValue = swipeOffset.floatValue)
                    .animateTo(0f, appPressHighlightPressSpec()) {
                        swipeOffset.floatValue = value
                    }
            }
        }

        detectHorizontalDragGestures(
            onDragStart = {
                returnJob?.cancel()
                totalDrag = 0f
                startOffset = swipeOffset?.floatValue ?: 0f
            },
            onDragEnd = {
                releaseOffset()
                if (abs(totalDrag) > 64f) {
                    if (totalDrag < 0f) onSkipNext.value() else onSkipPrevious.value()
                }
            },
            onDragCancel = { releaseOffset() },
        ) { _, dragAmount ->
            totalDrag += dragAmount
            if (swipeOffset != null) {
                // 短距离近似直接跟手，长距离逐渐增加阻力，窄槽位的移动范围随宽度收敛。
                val limit = minOf(24.dp.toPx(), size.width * 0.25f)
                if (limit > 0f) {
                    val dragOffset = limit * tanh(totalDrag / limit)
                    swipeOffset.floatValue = (startOffset + dragOffset).coerceIn(-limit, limit)
                }
            }
        }
    }
}

@Composable
private fun AudioNowPlayingBarContentRow(
    modifier: Modifier = Modifier,
    mergeProgress: () -> Float,
    searchProgress: () -> Float,
    showDeleteButton: Boolean,
    cover: @Composable () -> Unit,
    title: @Composable () -> Unit,
    play: @Composable () -> Unit,
    next: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(content = { cover() })
            Box(content = { title() })
            Box(content = { play() })
            Box(content = { next() })
        },
    ) { measurables, constraints ->
        val merge = mergeProgress()
        val search = searchProgress()
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = (constraints.maxWidth - if (showDeleteButton) {
                (16.dp.toPx() * resolveAudioNowPlayingPrimaryProgress(search)).roundToInt()
            } else 0).coerceAtLeast(0),
            mergeProgress = merge,
            searchProgress = search,
            density = density,
        )
        val rowHeight = constraints.constrainHeight(metrics.controlHeightPx)
        val coverPlaceable = measurables[0].measure(
            Constraints.fixed(metrics.coverPx, metrics.coverPx)
        )
        val titlePlaceable = if (metrics.titleWidthPx > 0) measurables[1].measure(
            Constraints(
                minWidth = 0,
                maxWidth = metrics.titleWidthPx,
                minHeight = 0,
                maxHeight = rowHeight,
            )
        ) else null
        val playPlaceable = if (metrics.playWidthPx > 0) measurables[2].measure(
            Constraints.fixed(metrics.playWidthPx, metrics.controlHeightPx)
        ) else null
        val nextPlaceable = if (metrics.extraWidthPx > 0) measurables[3].measure(
            Constraints.fixed(metrics.extraWidthPx, metrics.controlHeightPx)
        ) else null
        layout(constraints.maxWidth, rowHeight) {
            var x = metrics.contentStartPx
            coverPlaceable.placeRelative(x, (rowHeight - coverPlaceable.height) / 2)
            x += metrics.coverPx + metrics.spacerPx
            titlePlaceable?.let {
                it.placeRelative(x, (rowHeight - it.height) / 2)
                x += it.width
            }
            val buttonY = (rowHeight - metrics.controlHeightPx) / 2
            playPlaceable?.placeRelative(x, buttonY)
            x += metrics.playWidthPx
            nextPlaceable?.placeRelative(x, buttonY)
        }
    }
}

private fun Modifier.audioNowPlayingArtistHeight(
    mergeProgress: () -> Float,
    searchProgress: () -> Float,
): Modifier = layout { measurable, constraints ->
    val height = (
        16f * resolveAudioNowPlayingSupplementalProgress(
            mergeProgress = mergeProgress(),
            searchProgress = searchProgress(),
        )
    ).dp.roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = height)
    )
    layout(placeable.width, height) {
        placeable.placeRelative(0, 0)
    }
}

/**
 * 独立挂载路径的小横条 presence 宿主：与 dock 路径共用同一套
 * presence 时长/曲线 token（几何向下收放 + alpha 窗口），
 * 保证两条挂载路径的出入场节奏一致。Reduced motion 下退化为短淡入淡出。
 */
@Composable
internal fun AudioNowPlayingBarPresenceHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    animatePresence: Boolean = true,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val reduceMotion = LocalSystemReduceMotion.current
    val parentVisualWorkAllowed = com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current
    val alphaEnterSpec = resolveAudioNowPlayingPresenceAnimationSpec(
        active = true,
        reduceMotion = reduceMotion,
    )
    val alphaExitSpec = resolveAudioNowPlayingPresenceAnimationSpec(
        active = false,
        reduceMotion = reduceMotion,
    )
    val geometryEnterSpec = resolveAudioNowPlayingPresenceEnterGeometrySpringSpec()
    val geometryExitSpec = resolveAudioNowPlayingPresenceGeometrySpec(
        active = false,
        reduceMotion = reduceMotion,
    )
    AnimatedVisibility(
        visible = visible,
        enter = if (!animatePresence) {
            androidx.compose.animation.EnterTransition.None
        } else if (reduceMotion) {
            fadeIn(alphaEnterSpec)
        } else {
            expandVertically(geometryEnterSpec, expandFrom = Alignment.Bottom) +
                fadeIn(alphaEnterSpec)
        },
        exit = if (!animatePresence) {
            androidx.compose.animation.ExitTransition.None
        } else if (reduceMotion) {
            fadeOut(alphaExitSpec)
        } else {
            shrinkVertically(geometryExitSpec, shrinkTowards = Alignment.Bottom) +
                fadeOut(alphaExitSpec)
        },
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            com.android.purebilibili.core.ui.LocalVisualWorkAllowed provides
                (parentVisualWorkAllowed && visible && !transition.isRunning),
        ) { content() }
    }
}
