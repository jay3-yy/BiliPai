package com.android.purebilibili.feature.home.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import top.yukonga.miuix.kmp.blur.Backdrop

private const val WallpaperBlurSampleScale = 0.5f
private val WallpaperBlurRadius = 36.dp

@Composable
internal fun Modifier.wallpaperCardBlur(backdrop: Backdrop): Modifier =
    this.then(WallpaperCardBlurElement(backdrop, rememberGraphicsLayer()))

private data class WallpaperCardBlurElement(
    val backdrop: Backdrop,
    val layer: GraphicsLayer,
) : ModifierNodeElement<WallpaperCardBlurNode>() {
    override fun create() = WallpaperCardBlurNode(backdrop, layer)

    override fun update(node: WallpaperCardBlurNode) {
        node.backdrop = backdrop
        node.layer = layer
    }
}

private class WallpaperCardBlurNode(
    var backdrop: Backdrop,
    var layer: GraphicsLayer,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var position = Offset.Unspecified
    private var radiusPx = 0f

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val newPosition = coordinates.positionInRoot()
        if (this.coordinates !== coordinates || position != newPosition) {
            this.coordinates = coordinates
            position = newPosition
            invalidateDraw()
        }
    }

    override fun onDetach() {
        coordinates = null
        position = Offset.Unspecified
        radiusPx = 0f
        if (!layer.isReleased) {
            layer.renderEffect = null
            if (layer.size.width > 1 || layer.size.height > 1) {
                layer.record(
                    density = androidx.compose.ui.unit.Density(1f),
                    layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
                    size = IntSize(1, 1),
                ) {}
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val target = coordinates
        if (target != null && target.isAttached && size.width > 0f && size.height > 0f &&
            drawContext.canvas.nativeCanvas.isHardwareAccelerated
        ) {
            val radius = WallpaperBlurRadius.toPx() * WallpaperBlurSampleScale
            if (radiusPx != radius || layer.renderEffect == null) {
                layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                radiusPx = radius
            }
            // 以一半分辨率采样，卡片边界外也留出像素给模糊核。
            val padding = ceil(radius * 2f)
            val sampleSize = IntSize(
                ceil(size.width * WallpaperBlurSampleScale + padding * 2f).toInt(),
                ceil(size.height * WallpaperBlurSampleScale + padding * 2f).toInt(),
            )
            layer.record(size = sampleSize) {
                translate(padding, padding) {
                    scale(WallpaperBlurSampleScale, pivot = Offset.Zero) {
                        with(backdrop) { drawBackdrop(density = this@scale, coordinates = target) }
                    }
                }
            }
            scale(1f / WallpaperBlurSampleScale, pivot = Offset.Zero) {
                translate(-padding, -padding) { drawLayer(layer) }
            }
        }
        drawContent()
    }
}

internal fun Modifier.wallpaperCardBlurSample(
    sample: WallpaperBlurSample,
    wallpaperCoordinates: (() -> LayoutCoordinates?)?,
): Modifier = this.then(WallpaperCardBlurSampleElement(sample, wallpaperCoordinates))

private data class WallpaperCardBlurSampleElement(
    val sample: WallpaperBlurSample,
    val wallpaperCoordinates: (() -> LayoutCoordinates?)?,
) : ModifierNodeElement<WallpaperCardBlurSampleNode>() {
    override fun create() = WallpaperCardBlurSampleNode(sample, wallpaperCoordinates)

    override fun update(node: WallpaperCardBlurSampleNode) {
        node.sample = sample
        node.wallpaperCoordinates = wallpaperCoordinates
    }
}

private class WallpaperCardBlurSampleNode(
    var sample: WallpaperBlurSample,
    var wallpaperCoordinates: (() -> LayoutCoordinates?)?,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var position = Offset.Unspecified
    private var rootScale = Offset(1f, 1f)

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val wallpaper = wallpaperCoordinates?.invoke()?.takeIf { it.isAttached }
        val positionInWallpaper = wallpaper?.localPositionOf(coordinates) ?: coordinates.positionInRoot()
        val size = coordinates.size
        val farCorner = Offset(size.width.toFloat(), size.height.toFloat())
        val extent = (wallpaper?.localPositionOf(coordinates, farCorner) ?: coordinates.localToRoot(farCorner)) -
            positionInWallpaper
        val scale = if (size.width > 0 && size.height > 0 && extent.x > 0f && extent.y > 0f) {
            Offset(extent.x / size.width, extent.y / size.height)
        } else Offset(1f, 1f)
        if (position != positionInWallpaper || rootScale != scale) {
            position = positionInWallpaper
            rootScale = scale
            invalidateDraw()
        }
    }

    override fun onDetach() {
        position = Offset.Unspecified
        rootScale = Offset(1f, 1f)
    }

    override fun ContentDrawScope.draw() {
        if (position != Offset.Unspecified) {
            clipRect {
                scale(1f / rootScale.x, 1f / rootScale.y, pivot = Offset.Zero) {
                    translate(-position.x, -position.y) {
                        drawImage(sample.image, dstSize = sample.viewport)
                    }
                }
            }
        }
        drawContent()
    }
}
