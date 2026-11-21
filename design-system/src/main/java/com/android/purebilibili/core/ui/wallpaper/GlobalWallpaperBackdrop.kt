package com.android.purebilibili.core.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource

/** A wallpaper-only recording. Never attach page/chrome content to this source. */
val LocalGlobalWallpaperBackdropSource = compositionLocalOf<ChromeBackdropSource?> { null }
val LocalGlobalWallpaperUri = compositionLocalOf { "" }

private data class WallpaperReplayElement(val source: ChromeBackdropSource) : ModifierNodeElement<WallpaperReplayNode>() {
    override fun create() = WallpaperReplayNode(source)
    override fun update(node: WallpaperReplayNode) { node.source = source }
    override fun InspectorInfo.inspectableProperties() { name = "wallpaperReplay" }
}

/** 祖先移动也要更新屏幕坐标；只使绘制失效，不逐帧创建位置快照或回写 Compose 状态。 */
private class WallpaperReplayNode(var source: ChromeBackdropSource) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var rootPosition = Offset.Unspecified
    private var rootScale = Offset(1f, 1f)

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        val size = coordinates.size
        val extent = coordinates.localToRoot(Offset(size.width.toFloat(), size.height.toFloat())) - position
        val scale = if (size.width > 0 && size.height > 0 && extent.x > 0f && extent.y > 0f) {
            Offset(extent.x / size.width, extent.y / size.height)
        } else Offset(1f, 1f)
        if (this.coordinates !== coordinates || rootPosition != position || rootScale != scale) {
            this.coordinates = coordinates
            rootPosition = position
            rootScale = scale
            invalidateDraw()
        }
    }

    override fun onDetach() {
        coordinates = null
        rootPosition = Offset.Unspecified
        rootScale = Offset(1f, 1f)
    }

    override fun ContentDrawScope.draw() {
        val replayCoordinates = coordinates
        clipRect {
            // 导航只缩放页面及圆角裁剪，回放的壁纸仍与根层像素对齐。
            scale(1f / rootScale.x, 1f / rootScale.y, pivot = Offset.Zero) {
                with(source.backdrop) {
                    drawBackdrop(density = this@scale, coordinates = replayCoordinates)
                }
            }
        }
        drawContent()
    }
}

/** Replay in screen coordinates, so retained entries never reveal the previous page. */
@Composable
fun Modifier.sharedGlobalWallpaperBackground(
    fallbackColor: Color,
    fallbackColorProvider: (() -> Color)? = null,
): Modifier {
    // Existing constant-color callers retain their background modifier. Moving surfaces
    // can provide a color read in draw, without subscribing composition to their position.
    fun Modifier.fallbackBackground(opaque: Boolean): Modifier =
        if (fallbackColorProvider == null) {
            background(if (opaque) fallbackColor.copy(alpha = 1f) else fallbackColor)
        } else {
            drawBehind {
                val color = fallbackColorProvider()
                drawRect(if (opaque) color.copy(alpha = 1f) else color)
            }
        }
    val visible = LocalGlobalWallpaperBackdropVisible.current
    val source = LocalGlobalWallpaperBackdropSource.current
        ?.takeIf { visible && it.isReady }
        ?: return fallbackBackground(opaque = false)
    // Replay directly: drawBackdrop's effect modifier requires Android 13 even when effects
    // are empty. Drawing the recorded layer itself also supports older Android versions.
    return fallbackBackground(opaque = true)
        .then(WallpaperReplayElement(source))
}
