package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.VideoStatRow
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceInfoPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.isVideoCardReturnContentYieldActive
import com.android.purebilibili.core.ui.transition.resolveNowPlayingBarSourceChromeReveal
import com.android.purebilibili.core.ui.transition.resolveSourceShellFillReveal
import com.android.purebilibili.core.ui.transition.resolveVideoCardDetailChromeAlpha
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceChromeVisualFrame
import com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardInverseScale
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardInverseScaleForDepth
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.theme.withDensityScale
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.home.resolveHomeCardInfoSurfaceAppearance
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardCoverOverlayTextShadow
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardPrimaryStatBadgeMinWidthDp
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardSecondaryStatBadgeMinWidthDp

/** Click-time card text rendered inside the Miuix flying detail entry. */
internal data class VideoDetailReturnSourceCardChromeModel(
    val title: String,
    val ownerName: String,
    val viewText: String = "",
    val danmakuText: String = "",
    val durationText: String = "",
    val followed: Boolean = false,
    val recommendationReason: String = "",
    val infoPresentation: VideoCardSourceInfoPresentation = VideoCardSourceInfoPresentation(),
    val coverPresentation: VideoCardSourceCoverPresentation =
        VideoCardSourceCoverPresentation(),
)

internal fun resolveVideoDetailReturnSourceCardChromeModel(
    info: ViewInfo?,
    snapshot: VideoCardSourceChromeSnapshot?,
): VideoDetailReturnSourceCardChromeModel? {
    if (snapshot != null) {
        return VideoDetailReturnSourceCardChromeModel(
            title = snapshot.title.ifBlank { info?.title.orEmpty() },
            ownerName = snapshot.ownerName.ifBlank { info?.owner?.name.orEmpty() },
            viewText = snapshot.viewText,
            danmakuText = snapshot.danmakuText,
            durationText = snapshot.durationText,
            followed = snapshot.followed,
            recommendationReason = snapshot.recommendationReason,
            infoPresentation = snapshot.infoPresentation,
            coverPresentation = snapshot.coverPresentation,
        ).takeIf { it.title.isNotBlank() || it.ownerName.isNotBlank() }
    }
    if (info == null) return null
    return VideoDetailReturnSourceCardChromeModel(
        title = info.title,
        ownerName = info.owner.name,
        infoPresentation = VideoCardSourceInfoPresentation(
            publishTimeText = if (info.pubdate > 0L) {
                FormatUtils.formatPublishTime(info.pubdate)
            } else {
                ""
            },
            showStatsInInfo = false,
        ),
    )
}

internal data class VideoDetailReturnInfoSurfaceSpec(
    val useTintedSurface: Boolean,
    val containerColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
)

internal fun resolveVideoDetailReturnInfoSurfaceSpec(
    useTintedInfoSurface: Boolean,
    isDarkTheme: Boolean,
    baseContainerColor: Color,
): VideoDetailReturnInfoSurfaceSpec {
    if (!useTintedInfoSurface) {
        return VideoDetailReturnInfoSurfaceSpec(
            useTintedSurface = false,
            containerColor = baseContainerColor,
            borderColor = Color.Transparent,
            borderWidth = 0.dp,
        )
    }
    val appearance = resolveHomeCardInfoSurfaceAppearance(
        wallpaperTintEnabled = true,
        isDarkTheme = isDarkTheme,
        isDataSaverActive = false,
        hasWallpaperHazeState = false,
        hasLayerBackdrop = false,
        blurEnabled = true,
    )
    return VideoDetailReturnInfoSurfaceSpec(
        useTintedSurface = true,
        containerColor = baseContainerColor.copy(alpha = appearance.containerAlpha),
        borderColor = MediaContrastPalette.Foreground.copy(alpha = appearance.borderAlpha),
        borderWidth = AppSpacingTokens.Micro * 0.4f,
    )
}

/** 封面与真实播放器共用的媒体透明度；普通详情保持可见，底部入口与源快照互补交接。 */
internal fun resolveVideoDetailNowPlayingMediaAlpha(
    ownsNowPlayingTransition: Boolean,
    isCommittedCardReturn: Boolean,
    phase: VideoCardTransitionBackgroundPhase,
    sourceChromeAlpha: Float,
): Float {
    if (!ownsNowPlayingTransition) return 1f
    if (isCommittedCardReturn && phase == VideoCardTransitionBackgroundPhase.IDLE) return 0f
    return 1f - sourceChromeAlpha
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveVideoDetailFlyingSourceChromeAlpha(
    morphDepthProgress: Float,
    phase: VideoCardTransitionBackgroundPhase,
    isReturnGestureInProgress: Boolean,
    sourceLayout: VideoCardSourceLayout,
    detailContentLoading: Boolean = false,
    isNowPlayingBar: Boolean = false,
    followProgressEnabled: Boolean = true,
    /**
     * 卡片返回是否**已提交**（`markReturning` / 用户点返回）。
     *
     * 提交后 morph 会走到 `phase == IDLE`，但详情 entry 往往还没被卸载。此时
     * [resolveVideoCardDetailChromeAlpha] 的 IDLE 守卫已经返回 **0f**（详情 chrome 让位），
     * 若重建壳继续沿用 `1f − detailChromeAlpha` 的互补关系，就会得到 **1f** —— 一块全不透明的
     * 信息板凭空盖在卡槽上，正是「白色遮罩」。返回已提交时必须让两者**同时为 0**。
     */
    isCommittedCardReturn: Boolean = false,
    preserveNativeViewport: Boolean = false,
): Float {
    // IDLE + 返回已提交：morph 已 settle，视觉所有权交回真正的列表卡，重建壳必须退场。
    // 预测返回手势进行中不算（那时 IDLE 只是手势起点，重建壳仍要参与合成）。
    if (
        isCommittedCardReturn &&
        !isReturnGestureInProgress &&
        phase == VideoCardTransitionBackgroundPhase.IDLE
    ) {
        return 0f
    }
    if (!followProgressEnabled && morphDepthProgress > 0f &&
        isVideoCardReturnContentYieldActive(phase, isReturnGestureInProgress, morphDepthProgress)
    ) return 0f
    // The complete bar bitmap must use the same narrow window in both directions.
    // Ordinary opening chrome stays visible until depth 0.72, which stretches the
    // short capsule into large text before the real player has taken over.
    if (isNowPlayingBar) return when {
        phase == VideoCardTransitionBackgroundPhase.OPENING ||
            isVideoCardReturnContentYieldActive(
                phase, isReturnGestureInProgress, morphDepthProgress,
            ) -> resolveNowPlayingBarSourceChromeReveal(morphDepthProgress)
        else -> 0f
    }
    // Native navigation contracts the shell with this same reversible envelope.
    // Waiting for the late media-cover swap leaves an empty info band; using the
    // legacy opening window also makes a quick reverse switch content timelines.
    if (preserveNativeViewport && sourceLayout != VideoCardSourceLayout.COVER_ONLY &&
        (phase == VideoCardTransitionBackgroundPhase.OPENING ||
            isVideoCardReturnContentYieldActive(phase, isReturnGestureInProgress, morphDepthProgress))
    ) {
        return 1f - com.android.purebilibili.core.ui.transition
            .resolveVideoCardNativeBodyPresence(morphDepthProgress)
    }
    // Opening, settled-detail return, quick reverse, and predictive seek all hide the
    // list slot. The flying entry must keep the complementary info band or the morph
    // is cover-only. COVER_ONLY has no info band; its cover badges still use this alpha.
    val regularAlpha = 1f - resolveVideoCardDetailChromeAlpha(
        morphDepthProgress = morphDepthProgress,
        phase = phase,
        isReturnGestureInProgress = isReturnGestureInProgress,
        sourceLayout = sourceLayout,
    )
    if (!detailContentLoading || !isVideoCardReturnContentYieldActive(
            phase = phase,
            isReturnGestureInProgress = isReturnGestureInProgress,
            morphDepthProgress = morphDepthProgress,
        )
    ) return regularAlpha
    // Loading details need the early shell-fill fallback; playback bars use the
    // separate geometry-safe window above.
    val returnProgress = resolveSourceShellFillReveal(morphDepthProgress)
    return maxOf(regularAlpha, returnProgress)
}

/** Only the info pixels to the right of the moving media edge may be painted. */
internal fun resolveVideoDetailHorizontalInfoClipLeftPx(
    viewportWidthPx: Float,
    coverRightPx: Float,
    infoLeftPx: Float,
    inverseScaleX: Float,
    handoffProgress: Float,
    localScaleX: Float = 1f,
): Float {
    val progress = handoffProgress.coerceIn(0f, 1f)
    val mediaRight = viewportWidthPx +
        (coverRightPx * inverseScaleX - viewportWidthPx) * progress
    return ((mediaRight - infoLeftPx * inverseScaleX) /
        localScaleX.coerceAtLeast(0.01f)).coerceAtLeast(0f)
}

/**
 * Reconstructs the source card's information region in the same entry that owns the flying media.
 * The retained list card is layout-only until the navigation transition reaches IDLE.
 */
@Composable
internal fun BoxScope.VideoDetailReturnSourceCardChrome(
    sourceBounds: Rect?,
    sourceCoverBounds: Rect?,
    morphDepthProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
    sourceInfoBounds: Rect? = null,
    sourceLayout: VideoCardSourceLayout? = null,
    info: ViewInfo? = null,
    sourceChromeSnapshot: VideoCardSourceChromeSnapshot? = null,
    detailContentLoading: Boolean = false,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase = {
        VideoCardTransitionBackgroundPhase.RETURNING
    },
    isReturnGestureInProgressProvider: () -> Boolean = { true },
    followProgressEnabledProvider: () -> Boolean = { true },
    horizontalMediaHandoffEnabled: Boolean = true,
    mediaFrameProvider: () -> VideoDetailReturnMediaLayoutFrame? = { null },
    /** 返回是否已提交；IDLE 时重建壳必须与详情 chrome 同时归零。见 [resolveVideoDetailFlyingSourceChromeAlpha]。 */
    isCommittedCardReturn: Boolean = false,
) {
    val model = resolveVideoDetailReturnSourceCardChromeModel(info, sourceChromeSnapshot) ?: return
    val isNowPlayingBar = sourceChromeSnapshot?.isNowPlayingBar == true
    // The complete bar (including buttons and material) is rendered by the media overlay.
    // Rebuilding an info band here would double-paint it over the frozen native pixels.
    if (isNowPlayingBar && CardPositionManager.nativeVideoCardBitmapFor(sourceChromeSnapshot) != null) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val miuixHost = LocalMiuixVideoCardTransitionState.current
    val viewportWidthPx = miuixHost.layoutWidthProvider().takeIf { it > 1f }
        ?: with(density) { configuration.screenWidthDp.dp.toPx() }
    val viewportHeightPx = miuixHost.layoutHeightProvider().takeIf { it > 1f }
        ?: with(density) { configuration.screenHeightDp.dp.toPx() }
    val layout = resolveVideoDetailReturnSourceCardLayout(
        viewportWidthPx = viewportWidthPx,
        sourceBounds = sourceBounds,
        sourceCoverBounds = sourceCoverBounds,
        sourceLayout = sourceLayout ?: miuixHost.sourceLayout,
        sourceInfoBounds = sourceInfoBounds ?: miuixHost.sourceInfoBoundsProvider(),
    )
    if (!layout.canRender) return

    val cardWidth = with(density) { layout.cardWidthPx.toDp() }
    val cardHeight = with(density) { layout.cardHeightPx.toDp() }
    val infoWidth = with(density) { layout.infoWidthPx.toDp() }
    val infoHeight = with(density) { layout.infoHeightPx.toDp() }
    val sourceScaleY = (
        (sourceBounds?.height ?: layout.cardHeightPx) /
            viewportHeightPx.coerceAtLeast(1f)
        ).coerceIn(0.01f, 1f)
    val frozenInfoAnchorXPx = layout.infoAnchorXInViewportPx * layout.sourceScale
    val frozenInfoAnchorYPx = layout.infoAnchorYInViewportPx * layout.sourceScale
    val frozenCardAnchorXPx = layout.cardAnchorXInViewportPx * layout.sourceScale
    val frozenCardAnchorYPx = layout.cardAnchorYInViewportPx * layout.sourceScale
    val preserveNativeViewport = miuixHost.enabled && miuixHost.motionSpec?.reducedMotion != true

    // FillWidthTop compensation cancels the outer scale on both axes. Source-pixel
    // reconstruction is therefore constant (1 / sourceScale), even while depth changes.
    // Do not subscribe the native info slot's measure pass to a frame-rate value.
    val sourcePixelInverseScale = resolveMiuixVideoCardInverseScaleForDepth(
        sourceScaleX = layout.sourceScale,
        sourceScaleY = sourceScaleY,
        depth = 0f,
    )

    fun horizontalInfoClipLeftPx(localScaleX: Float = 1f): Float {
        if (!horizontalMediaHandoffEnabled ||
            layout.layout != VideoCardSourceLayout.SIDE_BY_SIDE || isNowPlayingBar
        ) return 0f
        val sourceAlpha = resolveVideoDetailFlyingSourceChromeAlpha(
            morphDepthProgress = morphDepthProgressProvider(),
            phase = phaseProvider(),
            isReturnGestureInProgress = isReturnGestureInProgressProvider(),
            sourceLayout = layout.layout,
            detailContentLoading = detailContentLoading,
            followProgressEnabled = followProgressEnabledProvider(),
            isCommittedCardReturn = isCommittedCardReturn,
            preserveNativeViewport = preserveNativeViewport,
        )
        return resolveVideoDetailHorizontalInfoClipLeftPx(
            viewportWidthPx = viewportWidthPx,
            coverRightPx = layout.coverOffsetXPx + layout.coverWidthPx,
            infoLeftPx = frozenInfoAnchorXPx,
            inverseScaleX = sourcePixelInverseScale.scaleX,
            handoffProgress = sourceAlpha,
            localScaleX = localScaleX,
        )
    }

    fun infoTopPx(inverse: MiuixVideoCardInverseScale): Int =
        if (layout.layout == VideoCardSourceLayout.STACKED) {
            resolveVideoDetailStackedInfoTopPx(layout, inverse.scaleY, mediaFrameProvider())
        } else {
            (frozenInfoAnchorYPx * inverse.scaleY).roundToInt()
        }

    fun Modifier.landingOffset(anchorXPx: Float, anchorYPx: Float): Modifier = offset {
        val inverse = sourcePixelInverseScale
        IntOffset(
            x = (anchorXPx * inverse.scaleX).roundToInt(),
            y = if (layout.layout == VideoCardSourceLayout.STACKED) {
                infoTopPx(inverse)
            } else {
                (anchorYPx * inverse.scaleY).roundToInt()
            },
        )
    }

    fun Modifier.landingLayer(): Modifier = graphicsLayer {
        val phase = phaseProvider()
        val isReturnGestureInProgress = isReturnGestureInProgressProvider()
        val morphDepthProgress = morphDepthProgressProvider()
        val frame = resolveVideoCardSourceChromeVisualFrame(
            morphDepthProgress = morphDepthProgress,
            phase = phase,
            isReturnGestureInProgress = isReturnGestureInProgress,
            sourceLayout = layout.layout,
        )
        val inverse = resolveMiuixVideoCardInverseScaleForDepth(
            sourceScaleX = layout.sourceScale,
            sourceScaleY = sourceScaleY,
            depth = morphDepthProgress,
        )
        scaleX = inverse.scaleX * frame.layoutScaleMultiplier
        scaleY = inverse.scaleY * frame.layoutScaleMultiplier
        transformOrigin = TransformOrigin(0f, 0f)
        alpha = resolveVideoDetailFlyingSourceChromeAlpha(
            morphDepthProgress = morphDepthProgress,
            phase = phase,
            isReturnGestureInProgress = isReturnGestureInProgress,
            sourceLayout = layout.layout,
            detailContentLoading = detailContentLoading,
            isNowPlayingBar = isNowPlayingBar,
            followProgressEnabled = followProgressEnabledProvider(),
            isCommittedCardReturn = isCommittedCardReturn,
            preserveNativeViewport = preserveNativeViewport,
        )
    }

    val nativeCardLayer = CardPositionManager.nativeVideoCardLayerFor(sourceChromeSnapshot)
    if (nativeCardLayer != null) {
        val infoCropXPx = layout.coverOffsetXPx + layout.coverWidthPx
        val infoCropYPx = frozenInfoAnchorYPx
        fun Modifier.nativeInfoSlot(
            widthPx: Float,
            heightPx: Float,
            cropXPx: Float,
            cropYPx: Float,
        ): Modifier = offset {
            val inverse = sourcePixelInverseScale
            IntOffset(
                x = (frozenInfoAnchorXPx * inverse.scaleX).roundToInt(),
                y = infoTopPx(inverse),
            )
        }.layout { measurable, _ ->
            val inverse = sourcePixelInverseScale
            val w = (widthPx * inverse.scaleX).roundToInt().coerceAtLeast(1)
            val h = (heightPx * inverse.scaleY).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }.clipToBounds().graphicsLayer {
            val phase = phaseProvider()
            val isReturnGestureInProgress = isReturnGestureInProgressProvider()
            alpha = resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = morphDepthProgressProvider(),
                phase = phase,
                isReturnGestureInProgress = isReturnGestureInProgress,
                sourceLayout = layout.layout,
                detailContentLoading = detailContentLoading,
                isNowPlayingBar = isNowPlayingBar,
                followProgressEnabled = followProgressEnabledProvider(),
                isCommittedCardReturn = isCommittedCardReturn,
                preserveNativeViewport = preserveNativeViewport,
            )
        }.drawWithContent {
            if (CardPositionManager.nativeVideoCardLayerFor(sourceChromeSnapshot) !== nativeCardLayer) {
                return@drawWithContent
            }
            val inverse = sourcePixelInverseScale
            clipRect(left = horizontalInfoClipLeftPx().coerceAtMost(size.width)) {
                scale(
                    scaleX = inverse.scaleX,
                    scaleY = inverse.scaleY,
                    pivot = Offset.Zero,
                ) {
                    translate(-cropXPx, -cropYPx) {
                        drawLayer(nativeCardLayer)
                    }
                }
            }
        }
        when (layout.layout) {
            VideoCardSourceLayout.STACKED -> Box(
                modifier = modifier
                    .zIndex(1f)
                    .align(Alignment.TopStart)
                    .nativeInfoSlot(
                        widthPx = layout.infoWidthPx,
                        heightPx = layout.infoHeightPx,
                        cropXPx = 0f,
                        cropYPx = infoCropYPx,
                    ),
            )
            VideoCardSourceLayout.SIDE_BY_SIDE -> Box(
                modifier = modifier
                    .zIndex(1f)
                    .align(Alignment.TopStart)
                    .nativeInfoSlot(
                        widthPx = layout.infoWidthPx,
                        heightPx = layout.cardHeightPx,
                        cropXPx = infoCropXPx,
                        cropYPx = 0f,
                    ),
            )
            VideoCardSourceLayout.COVER_ONLY -> Unit
        }
        return
    }

    val baseContainer = AppSurfaceTokens.videoCardContainer(
        glassEnabled = model.infoPresentation.useTintedInfoSurface,
    )
    val isDarkTheme = AppSurfaceTokens.chromeBackground().luminance() < 0.5f
    val surface = remember(
        model.infoPresentation.useTintedInfoSurface,
        isDarkTheme,
        baseContainer,
    ) {
        resolveVideoDetailReturnInfoSurfaceSpec(
            useTintedInfoSurface = model.infoPresentation.useTintedInfoSurface,
            isDarkTheme = isDarkTheme,
            baseContainerColor = baseContainer,
        )
    }

    fun Modifier.infoSurface(
        shape: Shape,
        drawBorder: Boolean = true,
    ): Modifier {
        val frozenContainer = sourceChromeSnapshot?.infoContainerColor
        return clip(shape)
            // Prepare the source plate before the first flying frame; never rebuild it
            // from the detail theme while the wallpaper source belongs to the list.
            .background(frozenContainer ?: baseContainer, shape)
            .then(
                if (frozenContainer == null) Modifier.background(surface.containerColor, shape)
                else Modifier
            )
            .then(
                if (surface.useTintedSurface && drawBorder) {
                    Modifier.border(surface.borderWidth, surface.borderColor, shape)
                } else {
                    Modifier
                },
            )
    }

    when (layout.layout) {
        VideoCardSourceLayout.SIDE_BY_SIDE -> {
            val isCapsuleDock = (CardPositionManager.lastClickedVideoSourceCornerDp ?: 0) >= 24 ||
                (layout.cardHeightPx <= with(density) { 72.dp.toPx() } && (CardPositionManager.lastClickedVideoSourceCornerDp ?: 0) >= 20)
            val capsuleShape = resolveSharedBottomBarCapsuleShape()
            val capsuleContainer = AppSurfaceTokens.surfaceContainer().copy(alpha = 0.72f)

            Box(
                modifier = modifier
                    .zIndex(-1f)
                    .align(Alignment.TopStart)
                    .landingOffset(frozenCardAnchorXPx, frozenCardAnchorYPx)
                    .width(cardWidth)
                    .height(cardHeight)
                    .landingLayer()
                    .then(
                        if (isCapsuleDock) {
                            Modifier
                                .dropShadow(
                                    shape = capsuleShape,
                                    shadow = Shadow(
                                        radius = 10.dp,
                                        color = Color.Black,
                                        alpha = if (isDarkTheme) 0.25f else 0.12f,
                                    ),
                                )
                                .clip(capsuleShape)
                                .background(capsuleContainer, capsuleShape)
                                .border(
                                    width = 0.8.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDarkTheme) 0.22f else 0.40f),
                                            Color.White.copy(alpha = if (isDarkTheme) 0.04f else 0.12f),
                                        ),
                                    ),
                                    shape = capsuleShape,
                                )
                        } else {
                            Modifier.background(baseContainer)
                        },
                    ),
            )
            Box(
                modifier = modifier
                    .zIndex(1f)
                    .align(Alignment.TopStart)
                    .landingOffset(frozenInfoAnchorXPx, frozenCardAnchorYPx)
                    .width(infoWidth)
                    .height(cardHeight)
                    .landingLayer()
                    .drawWithContent {
                        val left = horizontalInfoClipLeftPx(sourcePixelInverseScale.scaleX)
                            .coerceAtMost(size.width)
                        clipRect(left = left) { this@drawWithContent.drawContent() }
                    }
                    .then(
                        if (isCapsuleDock) {
                            Modifier
                        } else {
                            Modifier.infoSurface(
                                AppShapes.endRounded(
                                    AppShapes.containerCornerDp(ContainerLevel.Field),
                                ),
                                drawBorder = false,
                            )
                        },
                    )
                    .padding(
                        start = if (isCapsuleDock) AppSpacingTokens.Small else AppSpacingTokens.Medium,
                        end = if (isCapsuleDock) AppSpacingTokens.Large else AppSpacingTokens.Small,
                        top = AppSpacingTokens.Small,
                        // The measured source height already contains the horizontal card's
                        // outer inset. Reserving another bottom inset here shortens the flying
                        // info column and clips the statistics row at the landing boundary.
                        bottom = AppSpacingTokens.None,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                LandingInfoContent(
                    model = model,
                    sourceLayout = layout.layout,
                    modifier = Modifier,
                    constrainedHeight = (cardHeight - AppSpacingTokens.Small)
                        .coerceAtLeast(AppSpacingTokens.None),
                )
            }
        }

        VideoCardSourceLayout.STACKED -> Box(
            modifier = modifier
                .zIndex(1f)
                .align(Alignment.TopStart)
                .landingOffset(frozenInfoAnchorXPx, frozenInfoAnchorYPx)
                .width(infoWidth)
                .height(infoHeight)
                .landingLayer()
                .infoSurface(AppShapes.bottomRounded(AppSpacingTokens.Small))
                .padding(
                    horizontal = 6.dp,
                    vertical = AppSpacingTokens.Small,
                ),
        ) {
            LandingInfoContent(
                model = model,
                sourceLayout = layout.layout,
            )
        }

        VideoCardSourceLayout.COVER_ONLY -> Unit
    }
}

private fun VideoCardSourceCoverPresentation.hasVisibleChrome(): Boolean =
    showGradientMask ||
        showStatsOnCover ||
        showDurationOnCover ||
        showDurationAsStat ||
        premiumBadgeText.isNotBlank() ||
        showHistoryProgressBar

/**
 * Rebuilds only the click-time cover chrome beside the resident cover/player layer.
 * The caller owns the media geometry so these pixels travel with the same cover crop.
 */
@Composable
internal fun VideoDetailReturnCoverChrome(
    sourceChromeSnapshot: VideoCardSourceChromeSnapshot?,
    sourceScale: Float,
    modifier: Modifier = Modifier,
    inverseScaleXProvider: () -> Float = {
        1f / sourceScale.coerceIn(0.01f, 1f)
    },
    inverseScaleYProvider: () -> Float = inverseScaleXProvider,
) {
    val nativeBarBitmap = CardPositionManager.nativeVideoCardBitmapFor(sourceChromeSnapshot)
    if (
        nativeBarBitmap != null &&
        shouldDrawNativeCoverOverlay(
            overlayWidthPx = nativeBarBitmap.width.toFloat(),
            overlayHeightPx = nativeBarBitmap.height.toFloat(),
        )
    ) {
        Box(
            modifier = modifier.drawWithContent {
                if (CardPositionManager.nativeVideoCardBitmapFor(sourceChromeSnapshot) !== nativeBarBitmap) {
                    return@drawWithContent
                }
                scale(
                    // The media layout already compensates the measured capsule bounds.
                    // Fit its pixels once; another inverse here doubles the transformation.
                    scaleX = size.width / nativeBarBitmap.width.toFloat(),
                    scaleY = size.height / nativeBarBitmap.height.toFloat(),
                    pivot = Offset.Zero,
                ) {
                    drawImage(nativeBarBitmap)
                }
            },
        )
        return
    }
    val overlayLayer = CardPositionManager.nativeVideoCoverOverlayLayerFor(sourceChromeSnapshot)
    if (
        overlayLayer != null &&
        shouldDrawNativeCoverOverlay(
            overlayWidthPx = overlayLayer.size.width.toFloat(),
            overlayHeightPx = overlayLayer.size.height.toFloat(),
        )
    ) {
        Box(
            modifier = modifier.drawWithContent {
                if (CardPositionManager.nativeVideoCoverOverlayLayerFor(sourceChromeSnapshot) !== overlayLayer) {
                    return@drawWithContent
                }
                scale(
                    scaleX = size.width / overlayLayer.size.width.toFloat(),
                    scaleY = size.height / overlayLayer.size.height.toFloat(),
                    pivot = Offset.Zero,
                ) {
                    drawLayer(overlayLayer)
                }
            },
        )
        return
    }
    val snapshot = sourceChromeSnapshot ?: return
    if (!snapshot.coverPresentation.hasVisibleChrome()) return
    val model = resolveVideoDetailReturnSourceCardChromeModel(
        info = null,
        snapshot = snapshot,
    ) ?: return
    val baseDensity = LocalDensity.current
    val densityScale = resolveVideoDetailReturnCoverChromeDensityScale(sourceScale)
    // 仅叠加封面缩放系数；sp↔px 换算委托系统 Density，保留非线性字体缩放曲线。
    val compensatedDensity = remember(baseDensity, densityScale) {
        baseDensity.withDensityScale(densityScale)
    }
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalDensity provides compensatedDensity) {
            Box(modifier = Modifier.fillMaxSize()) {
                LandingCoverChrome(model = model)
            }
        }
    }
}

/** Inverse source scaling keeps cover text and icons at their stationary-card size. */
internal fun resolveVideoDetailReturnCoverChromeDensityScale(sourceScale: Float): Float =
    1f / sourceScale.coerceIn(0.01f, 1f)

@Composable
private fun BoxScope.LandingCoverChrome(
    model: VideoDetailReturnSourceCardChromeModel,
) {
    val presentation = model.coverPresentation
    val overlayStyle = remember { TextStyle(shadow = resolveVideoCardCoverOverlayTextShadow()) }

    if (presentation.showGradientMask) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MediaContrastPalette.Scrim.copy(alpha = 0.3f),
                            MediaContrastPalette.Scrim.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )
    }

    if (presentation.premiumBadgeText.isNotBlank()) {
        AppText(
            text = presentation.premiumBadgeText,
            color = MediaContrastPalette.Foreground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(AppSpacingTokens.Small)
                .clip(AppShapes.container(ContainerLevel.Tag))
                .background(BiliPink.copy(alpha = 0.88f))
                .padding(
                    horizontal = AppSpacingTokens.ExtraSmall,
                    vertical = AppSpacingTokens.Micro,
                ),
        )
    }

    val progressBottomPadding = if (presentation.showHistoryProgressBar) {
        AppSpacingTokens.ExtraSmall
    } else {
        AppSpacingTokens.None
    }
    if (presentation.showHistoryProgressBar) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(MediaContrastPalette.Foreground.copy(alpha = 0.24f)),
        )
        if (presentation.historyProgressFraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(presentation.historyProgressFraction.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(AppContentColors.accent()),
            )
        }
    }

    if (presentation.showStatsOnCover) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = AppSpacingTokens.Small,
                    end = AppSpacingTokens.Small,
                    bottom = AppSpacingTokens.ExtraSmall + progressBottomPadding,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        end = if (presentation.showDurationOnCover) {
                            AppSpacingTokens.TripleExtraLarge
                        } else {
                            AppSpacingTokens.None
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,
                ),
            ) {
                LandingCoverStat(
                    icon = Icons.Outlined.PlayCircle,
                    text = model.viewText,
                    minWidth = resolveVideoCardPrimaryStatBadgeMinWidthDp(model.viewText).dp,
                    useGlass = presentation.useGlassStats,
                    textStyle = overlayStyle,
                )
                if (presentation.showSecondaryStatOnCover && model.danmakuText.isNotBlank()) {
                    LandingCoverStat(
                        icon = Icons.Outlined.Subtitles,
                        text = model.danmakuText,
                        minWidth = resolveVideoCardSecondaryStatBadgeMinWidthDp(
                            model.danmakuText,
                        ).dp,
                        useGlass = presentation.useGlassStats,
                        textStyle = overlayStyle,
                    )
                }
                if (presentation.showOnlineCountOnCover) {
                    LandingCoverStat(
                        icon = Icons.Outlined.Visibility,
                        text = presentation.onlineCountText,
                        useGlass = presentation.useGlassStats,
                        textStyle = overlayStyle,
                    )
                }
                if (presentation.showDurationAsStat && model.durationText.isNotBlank()) {
                    LandingCoverStat(
                        icon = Icons.Outlined.Alarm,
                        text = model.durationText,
                        useGlass = presentation.useGlassStats,
                        textStyle = overlayStyle,
                    )
                }
            }
            if (presentation.showDurationOnCover && model.durationText.isNotBlank()) {
                AppText(
                    text = model.durationText,
                    color = MediaContrastPalette.Foreground,
                    style = MaterialTheme.typography.labelSmall.merge(overlayStyle),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    maxLines = 1,
                )
            }
        }
    } else if (presentation.showDurationOnCover && model.durationText.isNotBlank()) {
        AppText(
            text = model.durationText,
            color = MediaContrastPalette.Foreground,
            style = MaterialTheme.typography.labelSmall.merge(overlayStyle),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = AppSpacingTokens.Small + AppSpacingTokens.Micro,
                    bottom = AppSpacingTokens.Small + progressBottomPadding,
                ),
        )
    }
}

@Composable
private fun LandingCoverStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    useGlass: Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
) {
    if (text.isBlank()) return
    val pillShape = AppShapes.container(ContainerLevel.Pill)
    val pillDecoration = if (useGlass) {
        Modifier
            .clip(pillShape)
            .background(MediaContrastPalette.Scrim.copy(alpha = 0.46f))
            .border(
                width = AppSpacingTokens.Micro * 0.4f,
                color = MediaContrastPalette.Foreground.copy(alpha = 0.22f),
                shape = pillShape,
            )
            .padding(
                horizontal = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,
                vertical = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2,
            )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .widthIn(min = minWidth)
            .then(pillDecoration),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro),
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            tint = MediaContrastPalette.Foreground.copy(alpha = 0.92f),
            modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.Micro),
        )
        AppText(
            text = text,
            color = MediaContrastPalette.Foreground.copy(alpha = 0.94f),
            style = MaterialTheme.typography.labelSmall.merge(textStyle),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun BoxScope.LandingInfoContent(
    model: VideoDetailReturnSourceCardChromeModel,
    sourceLayout: VideoCardSourceLayout,
    modifier: Modifier = Modifier,
    constrainedHeight: Dp? = null,
) {
    Column(
        modifier = (if (constrainedHeight == null) {
            modifier.fillMaxSize()
        } else {
            modifier.fillMaxWidth().height(constrainedHeight)
        }).padding(
            end = if (model.infoPresentation.showOverflowMenu) {
                if (sourceLayout == VideoCardSourceLayout.STACKED) 10.dp else AppSpacingTokens.Large
            } else {
                AppSpacingTokens.None
            },
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
    ) {
        LandingInfoTexts(model = model, sourceLayout = sourceLayout)
    }
    if (model.infoPresentation.showOverflowMenu) {
        AppIcon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(AppSpacingTokens.Medium + AppSpacingTokens.Micro),
        )
    }
}

@Composable
private fun LandingInfoTexts(
    model: VideoDetailReturnSourceCardChromeModel,
    sourceLayout: VideoCardSourceLayout,
) {
    val contentTypography = feedContentTypography()
    AppText(
        text = model.title,
        modifier = Modifier.fillMaxWidth(),
        style = contentTypography.title,
        color = AppContentColors.primaryText(),
        fontWeight = FontWeight.Medium,
        maxLines = if (model.infoPresentation.singleLineMetadata || sourceLayout == VideoCardSourceLayout.SIDE_BY_SIDE) {
            2
        } else {
            videoCardTitleMaxLines()
        },
        minLines = if (model.infoPresentation.singleLineMetadata) 2 else 1,
        overflow = if (model.infoPresentation.singleLineMetadata || sourceLayout == VideoCardSourceLayout.SIDE_BY_SIDE) {
            TextOverflow.Ellipsis
        } else {
            videoCardTitleOverflow()
        },
    )

    if (sourceLayout == VideoCardSourceLayout.SIDE_BY_SIDE) {
        LandingSideBySideMetadata(model)
        if (model.infoPresentation.showStatsInInfo) {
            // Match the source horizontal card's 13dp icons. The generic row uses 16dp
            // icons, which can extend below the measured source bounds during landing.
            HorizontalVideoStatRow(
                playText = model.viewText,
                danmakuText = model.danmakuText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    if (model.infoPresentation.ownerBeforeStats) {
        LandingOwnerMetadata(model, Modifier.fillMaxWidth())
    }
    if (model.infoPresentation.showStatsInInfo) {
        if (model.infoPresentation.singleLineMetadata) {
            HorizontalVideoStatRow(
                playText = model.viewText,
                danmakuText = model.danmakuText,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        } else {
            VideoStatRow(
                playText = model.viewText,
                danmakuText = model.danmakuText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (!model.infoPresentation.ownerBeforeStats) {
        LandingOwnerMetadata(model, Modifier.fillMaxWidth())
    }
    if (model.infoPresentation.onlineCountText.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(AppSpacingTokens.Medium),
                tint = AppContentColors.secondaryText(),
            )
            AppText(
                text = model.infoPresentation.onlineCountText,
                style = contentTypography.statistic.copy(fontWeight = FontWeight.Medium),
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val footer = buildList {
        if (model.infoPresentation.singleLineMetadata && model.infoPresentation.publishTimeText.isNotBlank()) {
            add(model.infoPresentation.publishTimeText)
        }
        if (model.infoPresentation.showDurationInInfo && model.durationText.isNotBlank()) {
            add(model.durationText)
        }
        if (!model.infoPresentation.singleLineMetadata && model.infoPresentation.publishTimeText.isNotBlank()) {
            add(model.infoPresentation.publishTimeText)
        }
    }.joinToString(if (model.infoPresentation.singleLineMetadata) " · " else "  ·  ")
    if (footer.isNotBlank()) {
        LandingPublishMetadata(footer, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LandingSideBySideMetadata(model: VideoDetailReturnSourceCardChromeModel) {
    val publishTime = model.infoPresentation.publishTimeText
    val hasOwner = model.ownerName.isNotBlank() || model.followed || model.recommendationReason.isNotBlank()
    if (publishTime.isBlank() && !hasOwner) return

    if (model.infoPresentation.ownerBeforePublish) {
        if (hasOwner) LandingOwnerMetadata(model, Modifier.fillMaxWidth())
        if (publishTime.isNotBlank()) {
            LandingPublishMetadata(publishTime, Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
        ) {
            if (publishTime.isNotBlank()) {
                LandingPublishMetadata(
                    text = publishTime,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (hasOwner) {
                LandingOwnerMetadata(model, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LandingPublishMetadata(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = feedContentTypography().statistic,
        color = AppContentColors.secondaryText(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LandingOwnerMetadata(
    model: VideoDetailReturnSourceCardChromeModel,
    modifier: Modifier = Modifier,
) {
    if (model.ownerName.isBlank() && !model.followed && model.recommendationReason.isBlank()) return
    val contentTypography = feedContentTypography()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
    ) {
        com.android.purebilibili.feature.home.components.cards.VideoRecommendationBadge(model.recommendationReason)
        if (model.followed) {
            AppText(
                text = "已关注",
                style = contentTypography.coverBadge,
                color = AppContentColors.accent(),
                maxLines = 1,
            )
        }
        if (model.ownerName.isNotBlank()) {
            AppText(
                text = model.ownerName,
                style = contentTypography.author,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
