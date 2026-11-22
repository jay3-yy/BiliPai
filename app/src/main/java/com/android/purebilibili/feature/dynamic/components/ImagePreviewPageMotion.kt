package com.android.purebilibili.feature.dynamic.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

private const val PREVIEW_PAGE_ZOOM = 0.1f

internal fun resolveImagePreviewPageScale(progress: Float, dragY: Float, viewportHeight: Float): Float {
    val dragRange = (viewportHeight / 2f).coerceAtLeast(1f)
    val dragProgress = (abs(dragY) / dragRange).coerceIn(0f, 1f)
    return 1f + PREVIEW_PAGE_ZOOM * progress.coerceIn(0f, 1f) * (1f - dragProgress)
}

internal class ImagePreviewPageMotion {
    var readScale by mutableStateOf<(() -> Float)?>(null)
    private var windowPivot by mutableStateOf(Offset.Zero)
    private var windowOriginOnScreen by mutableStateOf(Offset.Zero)

    // A draw transform keeps the source bounds in their original window coordinates.
    val backdropModifier: Modifier = Modifier
        .onGloballyPositioned { coordinates ->
            val originOnScreen = coordinates.localToScreen(Offset.Zero)
            if (originOnScreen.isFinite) {
                windowOriginOnScreen = originOnScreen - coordinates.localToWindow(Offset.Zero)
            }
            windowPivot = coordinates.localToWindow(
                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
            )
        }
        .drawWithContent {
            val pageScale = readScale?.invoke() ?: 1f
            if (pageScale == 1f) {
                drawContent()
            } else {
                clipRect {
                    scale(pageScale, pivot = center) { this@drawWithContent.drawContent() }
                }
            }
        }

    fun mapSourceRect(source: Rect, pageScale: Float, previewOriginOnScreen: Offset): Rect {
        // The thumbnail belongs to its source window, which can be a separate sheet.
        val windowOffset = windowOriginOnScreen - previewOriginOnScreen
        if (pageScale == 1f) return source.translate(windowOffset)
        val pivot = windowPivot
        return Rect(
            topLeft = windowOffset + pivot + (source.topLeft - pivot) * pageScale,
            bottomRight = windowOffset + pivot + (source.bottomRight - pivot) * pageScale,
        )
    }
}
