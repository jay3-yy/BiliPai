package com.android.purebilibili.feature.audio.lyrics.halcyon

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max

/**
 * Halcyon `PlayerPalette` (bitmap entry points only).
 * Cover/lyric surfaces share one palette so page transitions do not flash.
 */
internal data class PlayerPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accent: Color,
    val onBackground: Color = Color.White,
    val isLight: Boolean = false,
) {
    companion object {
        private val LightContent = Color(0xFF16181C)

        val Default = PlayerPalette(
            top = Color(0xFF171717),
            middle = Color(0xFF0B0B0D),
            bottom = Color.Black,
            accent = Color(0xFF2F7DFF),
        )

        val LightDefault = PlayerPalette(
            top = Color(0xFFFCFCFD),
            middle = Color(0xFFF1F2F5),
            bottom = Color(0xFFE6E8EC),
            accent = Color(0xFF2F7DFF),
            onBackground = LightContent,
            isLight = true,
        )

        fun from(bitmap: Bitmap?, light: Boolean = false): PlayerPalette =
            fromCoverBackground(bitmap, light)

        fun pairFrom(bitmap: Bitmap?, light: Boolean = false): Pair<PlayerPalette, PlayerPalette> {
            val palette = fromCoverBackground(bitmap, light)
            return palette to palette
        }

        fun fromCoverBackground(bitmap: Bitmap?, light: Boolean = false): PlayerPalette {
            val representative = representativeAccent(bitmap)
                ?: return if (light) LightDefault else Default
            return fromSeedColor(representative, light = light)
        }

        fun fromSeedColor(seed: Color, light: Boolean = false): PlayerPalette {
            val accent = seed.toPlayerAccent()
            return if (light) lightPalette(accent) else PlayerPalette(
                top = accent.darken(0.40f),
                middle = accent.darken(0.66f),
                bottom = accent.darken(0.86f),
                accent = accent,
            )
        }

        fun fromLyricBackground(bitmap: Bitmap?, light: Boolean = false): PlayerPalette {
            val accent = representativeAccent(bitmap)?.toPlayerAccent()
                ?: return if (light) LightDefault else Default
            return if (light) lightPalette(accent) else PlayerPalette(
                top = accent.darken(0.42f),
                middle = accent.darken(0.68f),
                bottom = accent.darken(0.88f),
                accent = accent,
            )
        }

        private fun lightPalette(accent: Color): PlayerPalette = PlayerPalette(
            top = accent.lighten(0.55f),
            middle = accent.lighten(0.72f),
            bottom = accent.lighten(0.86f),
            accent = accent.darken(0.18f),
            onBackground = LightContent,
            isLight = true,
        )

        private fun representativeAccent(bitmap: Bitmap?): Color? {
            if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
            val sampleStep = (minOf(bitmap.width, bitmap.height) / 36).coerceAtLeast(1)
            val buckets = linkedMapOf<Int, LongArray>()
            val fallback = LongArray(4)
            val hsv = FloatArray(3)
            var sampled = 0
            var brightNeutral = 0
            var eligible = 0

            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = AndroidColor.alpha(pixel)
                    if (alpha > 24) {
                        val r = AndroidColor.red(pixel)
                        val g = AndroidColor.green(pixel)
                        val b = AndroidColor.blue(pixel)
                        AndroidColor.RGBToHSV(r, g, b, hsv)
                        val saturation = hsv[1]
                        val value = hsv[2]

                        sampled++
                        fallback[0]++
                        fallback[1] += r.toLong()
                        fallback[2] += g.toLong()
                        fallback[3] += b.toLong()
                        if (value > 0.78f && saturation < 0.18f) brightNeutral++

                        if (value > 0.08f && !(value > 0.94f && saturation < 0.20f)) {
                            eligible++
                            val key = ((r ushr 4) shl 8) or ((g ushr 4) shl 4) or (b ushr 4)
                            val bucket = buckets.getOrPut(key) { LongArray(4) }
                            bucket[0]++
                            bucket[1] += r.toLong()
                            bucket[2] += g.toLong()
                            bucket[3] += b.toLong()
                        }
                    }
                    x += sampleStep
                }
                y += sampleStep
            }
            if (fallback[0] == 0L) return null
            if (
                sampled > 0 &&
                brightNeutral.toFloat() / sampled.toFloat() > 0.56f &&
                eligible.toFloat() / sampled.toFloat() < 0.24f
            ) {
                val count = fallback[0].coerceAtLeast(1L)
                return Color(
                    (fallback[1] / count).toInt(),
                    (fallback[2] / count).toInt(),
                    (fallback[3] / count).toInt(),
                )
            }

            val best = buckets.values.maxByOrNull { bucket ->
                val count = bucket[0].coerceAtLeast(1L)
                val r = (bucket[1] / count).toInt()
                val g = (bucket[2] / count).toInt()
                val b = (bucket[3] / count).toInt()
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val balance = 1f - abs(luminance - 0.50f).coerceIn(0f, 0.50f) * 1.25f
                count.toFloat() * (0.55f + hsv[1] * 1.65f) * (0.75f + balance * 0.55f)
            } ?: fallback

            val count = best[0].coerceAtLeast(1L)
            return Color(
                (best[1] / count).toInt(),
                (best[2] / count).toInt(),
                (best[3] / count).toInt(),
            )
        }
    }
}

internal fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f,
)

internal fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

internal fun Color.boosted(): Color {
    val maxChannel = maxOf(red, green, blue).coerceAtLeast(0.01f)
    val scale = (0.86f / maxChannel).coerceIn(1f, 2.4f)
    return Color(
        red = (red * scale).coerceAtMost(1f),
        green = (green * scale).coerceAtMost(1f),
        blue = (blue * scale).coerceAtMost(1f),
        alpha = 1f,
    )
}

private fun Color.toPlayerAccent(): Color {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    if (hsv[1] < 0.12f) return Color(0xFF4D72B8)
    hsv[1] = hsv[1].coerceAtLeast(0.34f)
    hsv[2] = hsv[2].coerceIn(0.46f, 0.88f)
    return Color(AndroidColor.HSVToColor(hsv))
}

internal val LocalPlayerSurfaceActive = compositionLocalOf { true }
