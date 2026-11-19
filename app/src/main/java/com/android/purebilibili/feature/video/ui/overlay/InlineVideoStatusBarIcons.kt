package com.android.purebilibili.feature.video.ui.overlay

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val InlineStatusBarScrimAlpha = 0.34f
private const val IconToneSettleMillis = 180L

@Composable
internal fun InlineVideoStatusBarIconEffect(
    state: InlineVideoStatusBarState,
    cover: State<InlineStatusBarImage?>,
    stripHeightToWidth: () -> Float,
    darkStatusBarIcons: MutableState<Boolean>,
) {
    val latestStripHeightToWidth by rememberUpdatedState(stripHeightToWidth)
    val sourceColor by produceState<Color?>(null, state.ambientFrame, cover) {
        snapshotFlow {
            val ratio = latestStripHeightToWidth()
            val frame = state.ambientFrame.value
            when {
                ratio <= 0f -> null
                frame != null -> TopEdgeColorInput(frame, IntRect(0, 0, frame.width, frame.height), ratio)
                else -> cover.value?.let { TopEdgeColorInput(it.bitmap, it.pixels, ratio) }
            }
        }.collectLatest { input ->
            value = input?.let { withContext(Dispatchers.Default) { it.averageColor() } }
        }
    }

    LaunchedEffect(state.collapseProgress, darkStatusBarIcons) {
        snapshotFlow {
            val color = sourceColor ?: return@snapshotFlow null
            val imageAlpha = (1f - InlineStatusBarScrimAlpha) * (1f - state.collapseProgress.value)
            val luminance = Color(color.red * imageAlpha, color.green * imageAlpha, color.blue * imageAlpha).luminance()
            shouldUseDarkInlineStatusBarIcons(luminance, darkStatusBarIcons.value)
        }.collectLatest { dark ->
            if (dark == null) {
                darkStatusBarIcons.value = false
            } else if (dark != darkStatusBarIcons.value) {
                delay(IconToneSettleMillis)
                darkStatusBarIcons.value = dark
            }
        }
    }
    DisposableEffect(darkStatusBarIcons) {
        onDispose { darkStatusBarIcons.value = false }
    }
}

/** The band between the thresholds retains the current tone. */
private fun shouldUseDarkInlineStatusBarIcons(luminance: Float, currentlyDark: Boolean): Boolean =
    if (currentlyDark) luminance > 0.16f else luminance >= 0.23f

private data class TopEdgeColorInput(
    val image: ImageBitmap,
    val pixels: IntRect,
    val heightToWidth: Float,
) {
    fun averageColor(): Color? {
        val source = image.asAndroidBitmap()
        // Coil may decode a hardware cover, whereas the shared video sampler already uses software bitmaps.
        val bitmap = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else source
        try {
            val bandHeight = (pixels.width * heightToWidth).roundToInt().coerceIn(1, pixels.height)
            val rows = minOf(bandHeight, 4)
            val columns = minOf(pixels.width, 8)
            var red = 0f
            var green = 0f
            var blue = 0f
            for (row in 0 until rows) {
                val y = pixels.top + (2 * row + 1) * bandHeight / (2 * rows)
                for (column in 0 until columns) {
                    val x = pixels.left + (2 * column + 1) * pixels.width / (2 * columns)
                    val pixel = Color(bitmap.getPixel(x, y))
                    red += pixel.red * pixel.alpha
                    green += pixel.green * pixel.alpha
                    blue += pixel.blue * pixel.alpha
                }
            }
            val count = rows * columns
            return Color(red / count, green / count, blue / count)
        } finally {
            if (bitmap !== source) bitmap.recycle()
        }
    }
}
