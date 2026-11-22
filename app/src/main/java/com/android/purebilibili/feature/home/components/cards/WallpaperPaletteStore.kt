package com.android.purebilibili.feature.home.components.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import com.android.purebilibili.core.ui.wallpaper.WallpaperLuminanceBand
import com.android.purebilibili.core.ui.wallpaper.WallpaperReadabilityProfile
import com.android.purebilibili.core.ui.wallpaper.WallpaperTransform
import com.android.purebilibili.core.ui.wallpaper.resolveWallpaperVisibleSourceRegion
import com.android.purebilibili.core.ui.wallpaper.sanitizeWallpaperTransform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

internal data class WallpaperPaletteRequest(
    val uri: String,
    val viewport: IntSize,
    val transform: WallpaperTransform,
    val baseColor: Color,
    val includeBlurredViewport: Boolean = false,
)

internal data class WallpaperPaletteResult(val request: WallpaperPaletteRequest, val palette: WallpaperPalette?)

/** Cache-only suspending API. The calling UI owns cancellation and publication of results. */
object WallpaperPaletteStore {
    private data class CacheKey(
        val uri: String,
        val revision: String,
        val viewport: IntSize,
        val transform: WallpaperTransform,
        val baseColor: Int,
        val includeBlurredViewport: Boolean,
    )
    private val cache = LruCache<CacheKey, WallpaperPalette>(8)

    internal suspend fun loadWallpaperPalette(context: Context, request: WallpaperPaletteRequest): WallpaperPalette? {
        if (request.uri.isBlank() || request.viewport.width <= 0 || request.viewport.height <= 0) return null
        return try {
            val file = when {
                request.uri.startsWith("file:///android_asset/") -> null
                request.uri.startsWith("file:") -> Uri.parse(request.uri).path?.let(::File)
                request.uri.startsWith("/") -> File(request.uri)
                else -> null
            }
            val revision = withContext(Dispatchers.IO) {
                file?.let { "${it.lastModified()}:${it.length()}" }.orEmpty()
            }
            val key = CacheKey(
                uri = request.uri,
                revision = revision,
                viewport = request.viewport,
                transform = sanitizeWallpaperTransform(request.transform),
                baseColor = request.baseColor.toArgb(),
                includeBlurredViewport = request.includeBlurredViewport,
            )
            synchronized(cache) { cache.get(key) }?.let { return it }
            val bitmap = withContext(Dispatchers.IO) {
                val image = ImageRequest.Builder(context)
                    .data(file ?: request.uri)
                    .allowHardware(false)
                    .size(512, 512)
                    .scale(Scale.FIT)
                    .memoryCacheKey("wallpaper_palette_${request.uri}_$revision")
                    .build()
                val result = context.imageLoader.execute(image) as? SuccessResult
                (result?.image as? coil3.BitmapImage)?.bitmap
            } ?: return null
            if (bitmap.isRecycled) return null
            val palette = withContext(Dispatchers.Default) { extractVisiblePalette(bitmap, request) }
            currentCoroutineContext().ensureActive()
            synchronized(cache) { cache.put(key, palette) }
            palette
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // An unreadable/animated source must not introduce an unrelated system wallpaper.
            null
        }
    }

    private suspend fun extractVisiblePalette(bitmap: Bitmap, request: WallpaperPaletteRequest): WallpaperPalette {
        val visible = resolveWallpaperVisibleSourceRegion(
            request.viewport.width.toFloat(), request.viewport.height.toFloat(),
            bitmap.width.toFloat(), bitmap.height.toFloat(), request.transform,
        )
        val left = floor(visible.left).toInt().coerceIn(0, bitmap.width - 1)
        val right = ceil(visible.right).toInt().coerceIn(left + 1, bitmap.width)
        val colors = mutableListOf<Color>()
        val bands = mutableListOf<WallpaperLuminanceBand>()
        repeat(5) { index ->
            currentCoroutineContext().ensureActive()
            val top = floor(visible.top + (visible.bottom - visible.top) * index / 5f)
                .toInt().coerceIn(0, bitmap.height - 1)
            val bottom = ceil(visible.top + (visible.bottom - visible.top) * (index + 1) / 5f)
                .toInt().coerceIn(top + 1, bitmap.height)
            val palette = Palette.from(bitmap).setRegion(left, top, right, bottom)
                .maximumColorCount(8).clearFilters().generate()
            val color = palette.vibrantSwatch?.rgb ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb ?: bitmap.getPixel((left + right - 1) / 2, (top + bottom - 1) / 2)
            colors += Color(color).compositeOver(request.baseColor.copy(alpha = 1f))
            val luminances = mutableListOf<Float>()
            val stepX = ((right - left) / 20).coerceAtLeast(1)
            val stepY = ((bottom - top) / 20).coerceAtLeast(1)
            for (y in top until bottom step stepY) {
                currentCoroutineContext().ensureActive()
                for (x in left until right step stepX) {
                    luminances += Color(bitmap.getPixel(x, y))
                        .compositeOver(request.baseColor.copy(alpha = 1f)).luminance()
                }
            }
            luminances.sort()
            bands += WallpaperLuminanceBand(
                low = luminances[((luminances.lastIndex) * 0.1f).toInt()],
                mean = luminances.average().toFloat(),
                high = luminances[((luminances.lastIndex) * 0.9f).toInt()],
            )
        }
        val blurredViewport = if (request.includeBlurredViewport) {
            currentCoroutineContext().ensureActive()
            // 整窗裁切和上面的分带取色使用同一可见区域，直接缩绘到小位图。
            val viewportTop = floor(visible.top).toInt().coerceIn(0, bitmap.height - 1)
            val viewportBottom = ceil(visible.bottom).toInt().coerceIn(viewportTop + 1, bitmap.height)
            val blurred = createBlurredCardImage(
                image = bitmap,
                longEdge = 96,
                sourceRegion = Rect(left, viewportTop, right, viewportBottom),
                background = request.baseColor.toArgb(),
            )
            WallpaperBlurSample(blurred.asImageBitmap(), request.viewport, blurred.colorBounds())
        } else null
        return WallpaperPalette(
            topColor = colors.first(), bottomColor = colors.last(), dominantColor = colors[2],
            stops = colors, readability = WallpaperReadabilityProfile(bands),
            blurredViewport = blurredViewport,
        )
    }

    // 分别约束 RGB 通道。遮罩合成后，实际像素的亮度也会落在这两个颜色之间。
    private fun Bitmap.colorBounds(): WallpaperBackdropColorBounds {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var minRed = 255
        var minGreen = 255
        var minBlue = 255
        var maxRed = 0
        var maxGreen = 0
        var maxBlue = 0
        for (pixel in pixels) {
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            minRed = minOf(minRed, red)
            minGreen = minOf(minGreen, green)
            minBlue = minOf(minBlue, blue)
            maxRed = maxOf(maxRed, red)
            maxGreen = maxOf(maxGreen, green)
            maxBlue = maxOf(maxBlue, blue)
        }
        return WallpaperBackdropColorBounds(
            lower = Color(minRed, minGreen, minBlue),
            upper = Color(maxRed, maxGreen, maxBlue),
        )
    }

    fun clearCache() { synchronized(cache) { cache.evictAll() } }
    fun clear() = clearCache()
}
