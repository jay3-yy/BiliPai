package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import kotlin.math.roundToInt

internal data class VideoDetailCoverDrawGeometry(val size: Size, val offset: Offset)

/** Preserve center-crop as the aspect ratio changes; scaling a pre-cropped layer would stretch it. */
internal fun resolveVideoDetailCoverDrawGeometry(
    intrinsicSize: Size,
    targetSize: Size,
    contentScale: ContentScale,
    layoutDirection: LayoutDirection,
): VideoDetailCoverDrawGeometry {
    val sourceSize = if (intrinsicSize == Size.Unspecified) targetSize else Size(
        intrinsicSize.width.takeIf { it.isFinite() && it > 0f } ?: targetSize.width,
        intrinsicSize.height.takeIf { it.isFinite() && it > 0f } ?: targetSize.height,
    )
    val scale = contentScale.computeScaleFactor(sourceSize, targetSize)
    val drawSize = Size(sourceSize.width * scale.scaleX, sourceSize.height * scale.scaleY)
    val offset = Alignment.Center.align(
        IntSize(drawSize.width.roundToInt(), drawSize.height.roundToInt()),
        IntSize(targetSize.width.roundToInt(), targetSize.height.roundToInt()),
        layoutDirection,
    )
    return VideoDetailCoverDrawGeometry(drawSize, Offset(offset.x.toFloat(), offset.y.toFloat()))
}

/**
 * 图片请求仍按固定视口解析，封面绘制节点则按当前交接矩形测量。
 * 4:3、16:10 封面可能高于 16:9 播放器；只向小 Canvas 外溢绘制会被图层边界裁掉。
 * 两种材质共用测量阶段的几何，不改变播放器本身的尺寸或图片请求身份。
 */
@Composable
internal fun VideoDetailReturnCoverImage(
    request: ImageRequest,
    landingLayout: VideoDetailReturnSourceCardLayout?,
    handoffProgressProvider: () -> Float,
    inverseScaleXProvider: () -> Float,
    inverseScaleYProvider: () -> Float,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    contentTopInsetProvider: () -> Dp = { 0.dp },
    clipCornerDp: Dp = 0.dp,
    nativeSnapshotBoundsProvider: (() -> Rect?)? = null,
    slotOriginProvider: () -> Offset = { Offset.Zero },
    backingColor: Color = Color.Unspecified,
    onDecoded: () -> Unit = {},
    onCoverImageDecoded: ((ImageBitmap) -> Unit)? = null,
) {
    val sizeResolver = rememberConstraintsSizeResolver()
    val resolvedRequest = remember(request, sizeResolver) {
        // Keep explicit source decode dimensions/cache identity. Otherwise resolve from
        // viewport constraints, independently of the animated cover rectangle.
        if (request.defined.sizeResolver != null) request else request.newBuilder().size(sizeResolver).build()
    }
    val painter = rememberAsyncImagePainter(
        model = resolvedRequest,
        contentScale = contentScale,
        onSuccess = { success ->
            onDecoded()
            if (onCoverImageDecoded != null) {
                val bitmap = (success.result.image as? BitmapImage)?.bitmap
                if (bitmap != null) onCoverImageDecoded(bitmap.asImageBitmap())
            }
        },
    )
    val path = remember { Path() }
    val groupPaint = remember { Paint() }
    val semantics = if (contentDescription != null) Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    } else Modifier
    Box(modifier.then(sizeResolver).then(semantics)) {
        Canvas(
            Modifier.matchParentSize().videoDetailReturnMediaLayout(
                landingLayout = landingLayout,
                handoffProgressProvider = handoffProgressProvider,
                inverseScaleXProvider = inverseScaleXProvider,
                inverseScaleYProvider = inverseScaleYProvider,
                contentTopInsetProvider = contentTopInsetProvider,
                nativeSnapshotBoundsProvider = nativeSnapshotBoundsProvider,
                slotOriginProvider = slotOriginProvider,
            ),
        ) {
            val alpha = alphaProvider().coerceIn(0f, 1f)
            if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas
            val progress = handoffProgressProvider()
            val bounds = Rect(Offset.Zero, size)
            val radius = if (landingLayout?.canRender == true) {
                clipCornerDp.toPx() * progress.coerceIn(0f, 1f)
            } else 0f
            path.reset()
            path.addRoundRect(RoundRect(bounds, CornerRadius(radius, radius)))
            val geometry = resolveVideoDetailCoverDrawGeometry(
                painter.intrinsicSize, bounds.size, contentScale, layoutDirection,
            )
            // Black loading backing and image must fade as a group. Bound that temporary
            // surface to the media rect, and avoid it entirely at the opaque endpoint.
            val hasBacking = backingColor != Color.Unspecified
            val needsGroupAlpha = hasBacking && alpha < 1f
            drawIntoCanvas { canvas ->
                if (needsGroupAlpha) {
                    groupPaint.alpha = alpha
                    canvas.saveLayer(bounds, groupPaint)
                }
                try {
                    clipPath(path) {
                        if (hasBacking) {
                            drawRect(backingColor, topLeft = bounds.topLeft, size = bounds.size)
                        }
                        translate(bounds.left + geometry.offset.x, bounds.top + geometry.offset.y) {
                            with(painter) { draw(geometry.size, alpha = if (hasBacking) 1f else alpha) }
                        }
                    }
                } finally {
                    if (needsGroupAlpha) canvas.restore()
                }
            }
        }
    }
}

/** The cover canvas paints an opaque black backing even while its image is still loading. */
internal fun resolveVideoDetailOpaqueCoverInterior(
    frame: VideoDetailReturnMediaLayoutFrame,
    coverAlpha: Float,
    cornerRadiusPx: Float,
): Rect? {
    // Crossfades still need the complete backing beneath the live player.
    if (!coverAlpha.isFinite() || coverAlpha < 1f || !cornerRadiusPx.isFinite()) return null
    // Stay inside rounded corners and their antialiased boundary; never cull an exposed edge.
    val inset = cornerRadiusPx.coerceAtLeast(0f) + 1f
    val left = frame.offsetXPx + inset
    val top = frame.offsetYPx + inset
    val right = frame.offsetXPx + frame.widthPx - inset
    val bottom = frame.offsetYPx + frame.heightPx - inset
    return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
}

/** Cull only the black pixels that the opaque resident cover will paint again above us. */
internal fun DrawScope.drawVideoDetailMediaBacking(
    frame: VideoDetailReturnMediaLayoutFrame,
    alpha: Float,
    cornerRadiusPx: Float,
    opaqueCoverInterior: Rect?,
) {
    if (alpha <= 0f) return
    fun DrawScope.paintBacking() {
        drawRoundRect(
            color = Color.Black.copy(alpha = alpha),
            topLeft = Offset(frame.offsetXPx.toFloat(), frame.offsetYPx.toFloat()),
            size = Size(frame.widthPx.toFloat(), frame.heightPx.toFloat()),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        )
    }
    if (opaqueCoverInterior == null) {
        paintBacking()
    } else {
        clipRect(
            left = opaqueCoverInterior.left, top = opaqueCoverInterior.top,
            right = opaqueCoverInterior.right, bottom = opaqueCoverInterior.bottom,
            clipOp = ClipOp.Difference,
        ) { paintBacking() }
    }
}
