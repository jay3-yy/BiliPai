package com.android.purebilibili.core.plugin.skin

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 装扮贴图局部亮度采样器（纯后台 IO 解码 `inSampleSize = 16` 缩略图并按路径缓存）：
 * - 采样 `head_bg` 顶部状态栏带真实明暗，彻底杜绝浅色插画顶栏上出现纯白隐形状态栏图标；
 * - 采样 `tail_bg` 底部标签带真实明暗，确保底栏 `tail_color` / `tail_color_selected` 与真实底图对比清晰。
 */
object UiSkinLuminanceSampler {
    private val cache = ConcurrentHashMap<String, Float>()

    fun sampleRegionLuminanceSync(
        filePath: String,
        topFraction: Float = 0f,
        bottomFraction: Float = 1f,
    ): Float? {
        if (filePath.isBlank()) return null
        val cacheKey = "$filePath|$topFraction|$bottomFraction"
        cache[cacheKey]?.let { return it }

        val file = File(filePath)
        if (!file.isFile) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 16
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(filePath, opts) ?: return null
        return try {
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0) return null
            val yStart = (h * topFraction.coerceIn(0f, 1f)).toInt().coerceIn(0, h - 1)
            val yEnd = (h * bottomFraction.coerceIn(0f, 1f)).toInt().coerceIn(yStart + 1, h)
            var weightedLumSum = 0.0
            var weightSum = 0.0
            val stepX = maxOf(1, w / 24)
            for (y in yStart until yEnd) {
                for (x in 0 until w step stepX) {
                    val pixel = bmp.getPixel(x, y)
                    val alpha = Color.alpha(pixel) / 255.0
                    if (alpha < 0.15) continue
                    val r = Color.red(pixel) / 255.0
                    val g = Color.green(pixel) / 255.0
                    val b = Color.blue(pixel) / 255.0
                    val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                    weightedLumSum += lum * alpha
                    weightSum += alpha
                }
            }
            if (weightSum <= 0.01) return null
            val result = (weightedLumSum / weightSum).toFloat().coerceIn(0f, 1f)
            if (cache.size > 128) cache.clear()
            cache[cacheKey] = result
            result
        } finally {
            bmp.recycle()
        }
    }
}

@Composable
fun rememberSkinRegionLuminance(
    filePath: String?,
    topFraction: Float = 0f,
    bottomFraction: Float = 1f,
): State<Float?> {
    return produceState<Float?>(
        initialValue = null,
        key1 = filePath,
        key2 = topFraction,
        key3 = bottomFraction,
    ) {
        if (filePath.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            UiSkinLuminanceSampler.sampleRegionLuminanceSync(
                filePath = filePath,
                topFraction = topFraction,
                bottomFraction = bottomFraction,
            )
        }
    }
}
