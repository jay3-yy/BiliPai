package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.transition.MiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardInverseScaleForDepth
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun Modifier.videoDetailTransitionChrome(alphaProvider: () -> Float): Modifier =
    graphicsLayer { alpha = alphaProvider().coerceIn(0f, 1f) }

/** Measurements are consumed in layout/draw, never in composition. */
internal class LargeScreenVideoTransitionGeometry {
    private var root: LayoutCoordinates? = null
    private var player: LayoutCoordinates? = null
    val playerOrigin = mutableStateOf(Offset.Zero)

    fun recordRoot(coordinates: LayoutCoordinates) {
        root = coordinates
        updateOrigin()
    }

    fun recordPlayer(coordinates: LayoutCoordinates) {
        player = coordinates
        updateOrigin()
    }

    private fun updateOrigin() {
        val ancestor = root?.takeIf { it.isAttached } ?: return
        val coordinates = player?.takeIf { it.isAttached } ?: return
        val next = ancestor.localBoundingBoxOf(coordinates, clipBounds = false).topLeft
        val previous = playerOrigin.value
        if (abs(previous.x - next.x) > 0.5f || abs(previous.y - next.y) > 0.5f) {
            playerOrigin.value = next
        }
    }
}

/** Retains the live player's measurement while its cover lands in entry-space source geometry. */
@Composable
internal fun LargeScreenVideoPlayerTransitionHost(
    geometry: LargeScreenVideoTransitionGeometry,
    mediaGeometry: VideoDetailReturnMediaGeometry,
    transition: MiuixVideoCardTransitionState,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase,
    isReturningProvider: () -> Boolean,
    isCommittedReturn: Boolean,
    coverSource: VideoDetailResidentCoverSource?,
    hasRenderedFirstFrame: Boolean,
    detailContentLoading: Boolean,
    entryCoverAlphaProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    content: @Composable (Dp) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fallbackWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val fallbackHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val landing = if (transition.enabled) resolveVideoDetailReturnSourceCardLayout(
        viewportWidthPx = transition.layoutWidthProvider().takeIf { it > 1f } ?: fallbackWidthPx,
        sourceBounds = transition.sourceBoundsProvider(),
        sourceCoverBounds = transition.sourceCoverBoundsProvider(),
        sourceLayout = transition.sourceLayout,
        sourceInfoBounds = transition.sourceInfoBoundsProvider(),
    ).takeIf { it.canRender } else null
    val request = remember(context, coverSource) {
        coverSource?.let { source ->
            ImageRequest.Builder(context).data(source.url).apply {
                if (source.decodeWidthPx > 0 && source.decodeHeightPx > 0) {
                    size(source.decodeWidthPx, source.decodeHeightPx)
                }
            }.placeholderMemoryCacheKey(source.cacheKey)
                .memoryCacheKey(source.cacheKey).diskCacheKey(source.cacheKey)
                .crossfade(false).build()
        }
    }
    val isNowPlayingBar = transition.sourceChromeSnapshot?.isNowPlayingBar == true
    val nativeBarBitmap = if (transition.enabled && isNowPlayingBar) {
        CardPositionManager.nativeVideoCardBitmapFor(transition.sourceChromeSnapshot)
    } else null
    val isReturning = { isReturningProvider() }
    val sourceChromeAlpha: () -> Float = {
        if (landing == null) 0f else resolveVideoDetailFlyingSourceChromeAlpha(
            morphDepthProgress = transition.progressProvider(),
            phase = phaseProvider(),
            isReturnGestureInProgress = isReturning(),
            sourceLayout = transition.sourceLayout,
            detailContentLoading = detailContentLoading,
            isNowPlayingBar = isNowPlayingBar,
            isCommittedCardReturn = isCommittedReturn,
        )
    }
    val progress: () -> Float = {
        resolveLargeScreenVideoReturnMediaProgress(
            hasLandingGeometry = landing != null,
            phase = phaseProvider(),
            isReturning = isReturning(),
            isCommittedReturn = isCommittedReturn,
            depth = transition.progressProvider(),
            sourceLayout = transition.sourceLayout,
            sourceChromeAlpha = sourceChromeAlpha(),
            isNowPlayingBar = isNowPlayingBar,
        )
    }
    val inverseScale = {
        val sourceHeight = transition.sourceBoundsProvider()?.height ?: 1f
        val viewportHeight = transition.layoutHeightProvider().takeIf { it > 1f } ?: fallbackHeightPx
        resolveMiuixVideoCardInverseScaleForDepth(
            sourceScaleX = landing?.sourceScale ?: 1f,
            sourceScaleY = (sourceHeight / viewportHeight.coerceAtLeast(1f))
                .coerceIn(0.01f, 1f),
            depth = transition.progressProvider(),
        )
    }
    val origin = { geometry.playerOrigin.value }
    // Bounds stay in source pixels; the media-layout modifier applies the inverse
    // once. Both axes use the uniform content scale to preserve the bitmap's ratio.
    val nativeBarBounds: (() -> Rect?)? = if (nativeBarBitmap != null) {
        { transition.sourceBoundsProvider() }
    } else null
    mediaGeometry.frameProvider = {
        val viewport = mediaGeometry.viewportSize.value
        if (viewport.width <= 0 || viewport.height <= 0) null else {
            val inverse = inverseScale()
            val frame = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = viewport.width,
                containerHeightPx = viewport.height,
                landingLayout = landing,
                handoffProgress = progress(),
                inverseScaleX = inverse.scaleX,
                inverseScaleY = inverse.scaleY,
                nativeSnapshotBounds = nativeBarBounds?.invoke(),
            )
            // The info facade lives in entry space, outside the split player slot.
            val local = resolveVideoDetailReturnMediaFrameInSlot(frame, origin(), progress())
            local.copy(offsetYPx = local.offsetYPx + origin().y.roundToInt())
        }
    }
    val corner = transition.sourceChromeSnapshot?.coverPresentation?.cornerRadiusDp?.dp
        ?: AppShapes.mediaCoverCornerDp()
    val followsRelatedSidebarDrag = transition.enabled &&
        transition.sourceLayout == com.android.purebilibili.core.ui.transition.VideoCardSourceLayout.SIDE_BY_SIDE
    val playerViewportModifier = modifier
        .layout { measurable, constraints ->
            val baseWidth = constraints.maxWidth
            val windowWidth = transition.layoutWidthProvider().takeIf { it > 1f }?.roundToInt()
                ?: baseWidth
            val isReturning = isReturningProvider() ||
                phaseProvider() == VideoCardTransitionBackgroundPhase.RETURNING
            val revealProgress = if (followsRelatedSidebarDrag && isReturning) {
                transition.progressProvider()
            } else {
                0f
            }
            val expandedWidth = resolveLargeScreenSidePanelRevealWidthPx(
                baseWidthPx = baseWidth,
                windowWidthPx = windowWidth,
                progress = revealProgress,
            ).coerceAtLeast(baseWidth)
            val placeable = measurable.measure(
                constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth),
            )
            layout(width = baseWidth, height = placeable.height) {
                // Keep the split slot fixed so the recommendation pane follows its own
                // gesture position; let only the player viewport paint into the revealed gap.
                placeable.placeRelative(0, 0)
            }
        }
        .onGloballyPositioned(geometry::recordPlayer)
    BoxWithConstraints(modifier = playerViewportModifier.onSizeChanged {
        mediaGeometry.viewportSize.value = it
    }) {
        val viewportWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoDetailReturnPlayerLayout(
                landingLayout = landing,
                handoffProgressProvider = progress,
                inverseScaleXProvider = { inverseScale().scaleX },
                inverseScaleYProvider = { inverseScale().scaleY },
                clipCornerDp = corner,
                slotOriginProvider = origin,
            ).graphicsLayer {
                alpha = if (transition.enabled && shouldHideLargeScreenVideoAfterReturn(
                    isCommittedReturn, phaseProvider(),
                )) 0f else if (nativeBarBitmap != null) 1f - sourceChromeAlpha() else 1f
            },
        ) { content(viewportWidth) }
        if (request != null && landing != null) {
            VideoDetailReturnCoverImage(
                request = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                landingLayout = landing,
                handoffProgressProvider = progress,
                inverseScaleXProvider = { inverseScale().scaleX },
                inverseScaleYProvider = { inverseScale().scaleY },
                slotOriginProvider = origin,
                clipCornerDp = corner,
                modifier = Modifier.fillMaxSize().zIndex(1f),
                alphaProvider = {
                    val hideAfterReturn = shouldHideLargeScreenVideoAfterReturn(
                        isCommittedReturn, phaseProvider(),
                    )
                    val result = if (hideAfterReturn) 0f else if (nativeBarBitmap != null) {
                        // 源卡片顶栏截图的显现度。进入时 sourceChrome 从 1 淡到 0，
                        // 封面要跟着一起淡出，所以是正相关；写成 1 - sourceChrome
                        // 会在源 chrome 消失后把封面画成全不透明，一直盖住播放器。
                        sourceChromeAlpha()
                    } else if (!hasRenderedFirstFrame) {
                        1f
                    } else {
                        // Use the same handoff as the source information band; geometry
                        // completion alone must not expose the cover ahead of its chrome.
                        // Seek/return owns the pixels immediately if it interrupts entry.
                        // 只有真正在返回时才用源卡片 chrome。
                        // phase 状态机在「从底部正在播放条进入」时也会报 RETURNING，
                        // 那时用 sourceChromeAlpha（恒为 1）会把封面画成不透明、盖住播放器。
                        if (isCommittedReturn || isReturning()) sourceChromeAlpha() else {
                            maxOf(sourceChromeAlpha(), entryCoverAlphaProvider())
                        }
                    }
                    result
                },
            )
        }
        if (nativeBarBitmap != null && landing != null) {
            Image(
                bitmap = nativeBarBitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .videoDetailReturnMediaLayout(
                        landingLayout = landing,
                        handoffProgressProvider = { 1f },
                        inverseScaleXProvider = { inverseScale().scaleX },
                        inverseScaleYProvider = { inverseScale().scaleX },
                        nativeSnapshotBoundsProvider = nativeBarBounds,
                        slotOriginProvider = origin,
                        clipCornerDp = corner,
                    )
                    .zIndex(2f)
                    .graphicsLayer { alpha = sourceChromeAlpha() },
            )
        }
    }
}
