package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import com.android.purebilibili.core.ui.components.LocalElasticPressEnabled
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.animation.gl.ThanosEffectView
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import com.android.purebilibili.core.ui.findHostActivity
import com.android.purebilibili.core.ui.transition.captureNativeVideoCardImage
import com.android.purebilibili.core.ui.transition.captureNativeVideoCardBitmap
import com.android.purebilibili.core.ui.transition.recordNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.android.purebilibili.core.ui.transition.NowPlayingBarHandoffState
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.resolveNowPlayingBarReturnVisibility
import com.android.purebilibili.core.util.CardPositionManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.feature.audio.lyrics.halcyon.darken
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.resolveBiliPaiBottomBarShellColor
import com.android.purebilibili.feature.home.components.resolveBottomBarDarkTheme
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import kotlin.math.abs
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop

internal data class AudioNowPlayingBarState(
    val bvid: String = "",
    val title: String,
    val artist: String,
    val artistAvatarUrl: String = "",
    val coverUrl: String,
    val isPlaying: Boolean,
    val playbackSpeed: Float = 1f
)

@Composable
internal fun AudioNowPlayingBar(
    state: AudioNowPlayingBarState,
    onExpand: () -> Unit,
    onCompactClick: (() -> Unit)? = null,
    isLayoutStable: Boolean = true,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCancelPlayback: () -> Unit,
    onDismiss: () -> Unit,
    onManualHide: (() -> Unit)? = null,
    expandDestinationLabel: String = "听视频",
    sourceRoute: String? = null,
    handoff: NowPlayingBarHandoffState = NowPlayingBarHandoffState.Idle,
    glassEnabled: Boolean = LocalSettingsLiquidGlassEnabled.current,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassTuning: LiquidGlassTuning = LocalLiquidGlassRenderConfig.current.tuning,
    liftAboveBottomBar: Boolean = true,
    consumeNavigationBarsPadding: Boolean = true,
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

        val nativeBarLayer = rememberNativeVideoCardLayer()
        val snapshotScope = rememberCoroutineScope()
        var captureInProgress by remember(state.bvid) { mutableStateOf(false) }
        val barCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val coverCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val dissolveContext = LocalContext.current

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
                if (hostWindow == null || !isThanosEffectSupported(dissolveContext)) {
                    dismissAfterCancel()
                } else {
                    snapshotScope.launch {
                        try {
                            withFrameNanos { }
                            val windowBounds = barCoordsRef[0]?.takeIf { it.isAttached }?.boundsInWindow()
                            val snapshot = if (windowBounds != null && !windowBounds.isEmpty) {
                                withTimeoutOrNull(500L) {
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

        DisposableEffect(Unit) {
            onDispose {
                dissolveEffectView?.dispose()
                dissolveEffectView = null
            }
        }

        val handleExpand = {
            if (onCompactClick != null) {
                onCompactClick()
            } else if (!captureInProgress && canOpenAudioNowPlayingBarSource(isLayoutStable)) {
                barCoordsRef[0]?.takeIf { it.isAttached }?.boundsInRoot()?.let { bounds ->
                    val sourceCoverBounds = coverCoordsRef[0]?.takeIf { it.isAttached }?.boundsInRoot()
                    val effectiveSourceLayout = if (iconOnlyProgress() >= 0.99f) {
                        VideoCardSourceLayout.COVER_ONLY
                    } else {
                        VideoCardSourceLayout.SIDE_BY_SIDE
                    }
                    if (state.bvid.isNotBlank() &&
                        bounds.left.isFinite() && bounds.top.isFinite() &&
                        bounds.right.isFinite() && bounds.bottom.isFinite() &&
                        bounds.width > 0f && bounds.height > 0f
                    ) {
                        CardPositionManager.recordVideoCardPosition(
                            bvid = state.bvid,
                            sourceRoute = sourceRoute,
                            bounds = bounds,
                            screenWidth = screenWidthPx,
                            screenHeight = screenHeightPx,
                            density = density.density,
                            sourceCornerDp = com.android.purebilibili.core.ui.transition
                                .resolveNowPlayingBarSourceCornerDp(bounds.width, bounds.height, density.density),
                            coverBounds = sourceCoverBounds,
                            sourceLayout = effectiveSourceLayout,
                            sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                                title = state.title,
                                ownerName = state.artist,
                                ownerFaceUrl = state.artistAvatarUrl,
                                viewText = "",
                                danmakuText = "",
                                durationText = "",
                                followed = false,
                                isNowPlayingBar = true,
                            )
                        )
                    }
                }
                // Finish freezing before navigation removes the bar's graphics layer.
                CardPositionManager.clearNativeVideoCardLayers()
                captureNativeVideoCardImage(nativeBarLayer)
                val expectedSourceKey = CardPositionManager.lastClickedVideoSourceKey
                captureInProgress = true
                snapshotScope.launch {
                    try {
                        captureNativeVideoCardBitmap(nativeBarLayer, expectedSourceKey)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        com.android.purebilibili.core.util.Logger.w(
                            "AudioNowPlayingBar", "Could not freeze return snapshot: ${error.message}",
                        )
                    } finally {
                        captureInProgress = false
                    }
                    onExpand()
                }
            }
        }
        val reduceMotion = rememberSystemReduceMotion()
        val sourceInActiveReturn = resolveNowPlayingBarReturnVisibility(
            handoff = handoff,
            currentBvid = state.bvid,
        ) <= 0f

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
        // 迷你条封面旋转：播放时逐帧失效是预期开销（封面独占 graphicsLayer，
        // 不会连带模糊外壳层重绘）；暂停后 while 循环退出，帧率自然回落。
        val coverRotationDegrees = rememberMusicArtworkRotationDegrees(
            active = shouldRotateMusicArtwork(
                isPlaying = state.isPlaying,
                reduceMotion = reduceMotion
            ),
            contentKey = state.coverUrl,
            playbackSpeed = state.playbackSpeed
        )
        val elasticState = rememberAppElasticPressState()
        val glassMotion = glassActive && miuixBackdrop != null
        Box(
            modifier = modifier
                .appElasticPress(
                    enabled = !sourceInActiveReturn && !captureInProgress && !cancelDissolving,
                    state = elasticState,
                    transformInBackdrop = glassMotion,
                )
                .fillMaxWidth()
                .then(if (consumeNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    start = if (dockHosted) 0.dp else chrome.horizontalPaddingDp.dp,
                    end = if (dockHosted) 0.dp else chrome.horizontalPaddingDp.dp,
                    bottom = when {
                        dockHosted -> 0.dp
                        liftAboveBottomBar -> 72.dp
                        !glassActive && chrome.uiStyle == com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3 -> 16.dp
                        else -> 8.dp
                    }
                )
                .onGloballyPositioned { coordinates ->
                    barCoordsRef[0] = coordinates
                }
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
                    freezeProvider = {
                        (captureInProgress || sourceInActiveReturn) &&
                            CardPositionManager.isNativeVideoCardLayerCurrentOwner(nativeBarLayer)
                    },
                    // Visibility is owned by the explicit now-playing handoff above.
                    sourceRoute = sourceRoute,
                )
                .semantics {
                    contentDescription = if (onCompactClick != null) {
                        "当前视频：${state.title}，收起搜索并展开视频小横条"
                    } else {
                        "当前视频：${state.title}，打开$expandDestinationLabel"
                    }
                }
                .clickable(
                    enabled = !sourceInActiveReturn && !cancelDissolving,
                    onClick = handleExpand,
                )
                .then(
                    if (onManualHide != null && !sourceInActiveReturn && !cancelDissolving) {
                        Modifier.pointerInput(onManualHide) {
                            // 长按立即进入沉浸态，与自动沉浸共用同一把柄唤回通道。
                            detectTapGestures(onLongPress = { onManualHide() })
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (!sourceInActiveReturn && !cancelDissolving) {
                        Modifier.audioNowPlayingSkipGesture(
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(if (sourceInActiveReturn) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = 1f - surfaceMergeProgress().coerceIn(0f, 1f) }
                    .biliPaiFloatingDockShell(
                        backdrop = miuixBackdrop,
                        interactionLayerBlock = elasticState.layerBlock.takeIf { glassMotion },
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
            CompositionLocalProvider(LocalElasticPressEnabled provides false) {
                AudioNowPlayingBarContentRow(
                    modifier = if (glassMotion) Modifier.graphicsLayer(elasticState.layerBlock).clip(shape) else Modifier.clip(shape),
                    mergeProgress = dockMergeProgress,
                    searchProgress = iconOnlyProgress,
                    cover = {
                        AsyncImage(
                            model = state.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    coverCoordsRef[0] = coordinates
                                }
                                .graphicsLayer { rotationZ = coverRotationDegrees() }
                                .clip(
                                    if (chrome.coverShapeIsCircle) {
                                        CircleShape
                                    } else {
                                        AppShapes.container(ContainerLevel.Field)
                                    }
                                ),
                            contentScale = ContentScale.Crop
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
                                modifier = if (state.isPlaying && isLayoutStable) {
                                    Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                } else {
                                    Modifier
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                                color = immersiveContentColor
                                    ?: AppContentColors.primaryText(),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (state.artistAvatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = state.artistAvatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    AppText(
                                        text = state.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = immersiveContentColor?.copy(alpha = 0.72f)
                                            ?: AppContentColors.secondaryText()
                                    )
                                }
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
                                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                                    tint = immersiveContentColor ?: AppContentColors.primaryText()
                                )
                            }
                        }
                    },
                    queue = {
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
                            AppIconButton(onClick = handleExpand, modifier = Modifier.size(48.dp)) {
                                AppIcon(
                                    Icons.Outlined.QueueMusic,
                                    contentDescription = "打开$expandDestinationLabel",
                                    tint = immersiveContentColor ?: AppContentColors.primaryText(),
                                )
                            }
                        }
                    },
                    close = {
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
                            AppIconButton(
                                onClick = handleCancelClick,
                                enabled = !cancelDissolving,
                                modifier = Modifier.size(48.dp),
                            ) {
                                AppIcon(
                                    Icons.Filled.Close,
                                    contentDescription = "关闭听视频条",
                                    tint = immersiveContentColor ?: AppContentColors.primaryText(),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun Modifier.audioNowPlayingSkipGesture(
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
): Modifier = pointerInput(onSkipNext, onSkipPrevious) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            if (abs(totalDrag) > 64f) {
                if (totalDrag < 0f) onSkipNext() else onSkipPrevious()
            }
            totalDrag = 0f
        },
        onDragCancel = { totalDrag = 0f }
    ) { _, dragAmount ->
        totalDrag += dragAmount
    }
}

@Composable
private fun AudioNowPlayingBarContentRow(
    modifier: Modifier = Modifier,
    mergeProgress: () -> Float,
    searchProgress: () -> Float,
    cover: @Composable () -> Unit,
    title: @Composable () -> Unit,
    play: @Composable () -> Unit,
    queue: @Composable () -> Unit,
    close: @Composable () -> Unit,
) {
    // Share the navigation dock's resting height and 50% capsule corner geometry.
    val height = com.android.purebilibili.feature.home.components
        .resolveBiliPaiBottomBarDockHeight(searchExpanded = false)
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        content = {
            Box(content = { cover() })
            Box(content = { title() })
            Box(content = { play() })
            Box(content = { queue() })
            Box(content = { close() })
        },
    ) { measurables, constraints ->
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = constraints.maxWidth,
            mergeProgress = mergeProgress(),
            searchProgress = searchProgress(),
            density = density,
        )
        val coverPlaceable = measurables[0].measure(
            Constraints.fixed(metrics.coverPx, metrics.coverPx)
        )
        val titlePlaceable = measurables[1].measure(
            Constraints(
                minWidth = 0,
                maxWidth = metrics.titleWidthPx,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
        )
        val playPlaceable = measurables[2].measure(
            Constraints.fixed(metrics.playWidthPx, metrics.controlHeightPx)
        )
        val queuePlaceable = measurables[3].measure(
            Constraints.fixed(metrics.extraWidthPx, metrics.controlHeightPx)
        )
        val closePlaceable = measurables[4].measure(
            Constraints.fixed(metrics.extraWidthPx, metrics.controlHeightPx)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            var x = metrics.contentStartPx
            coverPlaceable.placeRelative(x, (constraints.maxHeight - coverPlaceable.height) / 2)
            x += metrics.coverPx + metrics.spacerPx
            titlePlaceable.placeRelative(x, (constraints.maxHeight - titlePlaceable.height) / 2)
            x += titlePlaceable.width
            val buttonY = (constraints.maxHeight - metrics.controlHeightPx) / 2
            playPlaceable.placeRelative(x, buttonY)
            x += metrics.playWidthPx
            queuePlaceable.placeRelative(x, buttonY)
            x += metrics.extraWidthPx
            closePlaceable.placeRelative(x, buttonY)
        }
    }
}

private fun Modifier.audioNowPlayingArtistHeight(
    mergeProgress: () -> Float,
    searchProgress: () -> Float,
): Modifier = layout { measurable, constraints ->
    val height = (
        20f * resolveAudioNowPlayingSupplementalProgress(
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
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberSystemReduceMotion()
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
        content()
    }
}
