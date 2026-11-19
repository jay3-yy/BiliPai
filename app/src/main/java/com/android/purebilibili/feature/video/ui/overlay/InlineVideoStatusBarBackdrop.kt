package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.feature.video.LocalVideoDetailOpeningSeed

internal class InlineStatusBarImage(
    val bitmap: ImageBitmap,
    val pixels: IntRect = IntRect(0, 0, bitmap.width, bitmap.height),
) {
    val painter = BitmapPainter(
        bitmap,
        srcOffset = IntOffset(pixels.left, pixels.top),
        srcSize = IntSize(pixels.width, pixels.height),
    )
}

@Stable
internal class InlineVideoStatusBarState(
    val enabled: Boolean,
    val collapseProgress: State<Float>,
    val targetCid: Long,
    val coverImage: MutableState<InlineStatusBarImage?>,
    val ambientFrame: MutableState<ImageBitmap?>,
) {
    var videoBoundsInWindow: (() -> Rect?)? by mutableStateOf(null)
}

@Composable
internal fun rememberInlineVideoStatusBarState(
    enabled: Boolean,
    bvid: String,
    cid: Long,
    player: Player,
    collapseProgress: State<Float>,
): InlineVideoStatusBarState {
    val cover = remember(bvid) { mutableStateOf<InlineStatusBarImage?>(null) }
    // 重测可以替换进度对象，同一媒体仍保留最后一次有效采样。
    val frame = remember(bvid, cid, player) { mutableStateOf<ImageBitmap?>(null) }
    return remember(enabled, cid, collapseProgress, cover, frame) {
        InlineVideoStatusBarState(enabled, collapseProgress, cid, cover, frame)
    }
}

@Composable
internal fun rememberVideoDetailOpeningCoverImage(matchesEntry: Boolean): InlineStatusBarImage? {
    val seed = LocalVideoDetailOpeningSeed.current
    if (!matchesEntry || seed == null) return null
    return remember(seed) {
        InlineStatusBarImage(
            seed.bitmap.asImageBitmap(),
            IntRect(seed.coverPixels.left, seed.coverPixels.top, seed.coverPixels.right, seed.coverPixels.bottom),
        )
    }
}

/**
 * 系统栏高度由播放器宿主提供。沉浸开关只控制背景内容，玻璃、MD3 和 Miuix
 * 共用此层，保留各自的播放器控件和避让尺寸。
 */
@Composable
internal fun InlineVideoStatusBarBackdrop(
    state: InlineVideoStatusBarState,
    residentCover: State<InlineStatusBarImage?>,
    openingCover: InlineStatusBarImage?,
    darkStatusBarIcons: MutableState<Boolean>?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    if (!state.enabled || height.value <= 0f) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Black),
    ) {
        val cover = remember(state.coverImage, residentCover, openingCover) {
            derivedStateOf { state.coverImage.value ?: residentCover.value ?: openingCover }
        }
        val showBlur by remember(state, cover) {
            derivedStateOf {
                state.collapseProgress.value < 1f &&
                    (state.ambientFrame.value != null || cover.value != null)
            }
        }
        if (!showBlur) return@Box

        val hazeState = rememberRecoverableHazeState()
        val hazeStyle = remember { resolveVideoStatusBarAmbientHazeStyle() }
        var stripBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
        var stripHeightToWidth by remember { mutableFloatStateOf(0f) }
        if (darkStatusBarIcons != null && rememberVisualWorkActive()) {
            InlineVideoStatusBarIconEffect(
                state = state,
                cover = cover,
                stripHeightToWidth = { stripHeightToWidth },
                darkStatusBarIcons = darkStatusBarIcons,
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                // 裁剪离屏效果，避免 GPU 在相邻视频面上绘出模糊层的边缘。
                .clipToBounds()
                .onSizeChanged { stripHeightToWidth = if (it.width > 0) it.height.toFloat() / it.width else 0f }
                .onGloballyPositioned { stripBoundsInWindow = it.boundsInWindow() }
                .graphicsLayer { alpha = 1f - state.collapseProgress.value },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeSourceCompat(hazeState)
                    .drawWithCache {
                        val livePainter = state.ambientFrame.value?.let { BitmapPainter(it) }
                        onDrawBehind {
                            if (livePainter != null) {
                                drawTopEdge(
                                    painter = livePainter,
                                    stripBounds = stripBoundsInWindow,
                                    videoBounds = state.videoBoundsInWindow?.invoke(),
                                )
                            } else {
                                val painter = cover.value?.painter ?: return@onDrawBehind
                                drawTopEdge(painter, stripBounds = null, videoBounds = null)
                            }
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .unifiedBlur(
                        hazeState = hazeState,
                        // 沉浸背景由播放页开关控制。
                        surfaceType = BlurSurfaceType.GENERIC,
                        blurStyleOverride = hazeStyle,
                    )
                    .background(Color.Black.copy(alpha = InlineStatusBarScrimAlpha)),
            )
        }
    }
}

/** 将视频顶边延伸进留出的状态栏区域，水平方向保持与视频面的窗口坐标一致。 */
private fun DrawScope.drawTopEdge(painter: Painter, stripBounds: Rect?, videoBounds: Rect?) {
    val viewport: Size
    val left: Float
    val top: Float
    if (stripBounds != null && !stripBounds.isEmpty && videoBounds != null && !videoBounds.isEmpty) {
        val scaleX = size.width / stripBounds.width
        val scaleY = size.height / stripBounds.height
        viewport = Size(videoBounds.width * scaleX, videoBounds.height * scaleY)
        left = (videoBounds.left - stripBounds.left) * scaleX
        top = if (videoBounds.overlaps(stripBounds)) {
            (videoBounds.top - stripBounds.top).coerceAtMost(0f) * scaleY
        } else 0f
    } else {
        viewport = size
        left = 0f
        top = 0f
    }

    val intrinsic = painter.intrinsicSize
    val scale = ContentScale.Crop.computeScaleFactor(intrinsic, viewport)
    val imageSize = Size(intrinsic.width * scale.scaleX, intrinsic.height * scale.scaleY)
    clipRect(left = left, right = left + viewport.width) {
        translate(left + (viewport.width - imageSize.width) / 2f, top) {
            with(painter) { draw(imageSize) }
        }
    }
}
