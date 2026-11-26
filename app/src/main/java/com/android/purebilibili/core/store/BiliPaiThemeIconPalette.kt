package com.android.purebilibili.core.store

import kotlin.math.cbrt
import kotlin.math.pow

const val BILIPAI_THEME_ICON_KEY = "icon_bilipai_theme"
const val BILIPAI_THEME_ICON_BASE_PALETTE_SIZE = 216
const val DEFAULT_BILIPAI_THEME_ICON_INDEX = 17

/** 自动背景保留六级 RGB 匹配；独立背景使用前景、背景色板的感知色距（OKLab）组合。 */
fun resolveBiliPaiThemeIconIndex(argb: Int, background: Int? = null): Int {
    if (background != null) {
        val foregroundIndex = nearestIconColorIndex(argb, biliPaiIconForegroundChoices)
        val backgroundIndex = nearestIconColorIndex(background, biliPaiIconBackgroundChoices)
        return BILIPAI_THEME_ICON_BASE_PALETTE_SIZE + backgroundIndex * biliPaiIconForegroundChoices.size + foregroundIndex
    }
    val red = ((argb ushr 16 and 255) + 25) / 51
    val green = ((argb ushr 8 and 255) + 25) / 51
    val blue = ((argb and 255) + 25) / 51
    return red * 36 + green * 6 + blue
}

/** 当前所选前景/背景与主图标预置色板是否完全一致（零色差）。 */
fun isBiliPaiThemeIconExactMatch(
    requestedForeground: Int,
    requestedBackground: Int?,
    matchedIndex: Int,
): Boolean {
    val effectiveRequestedFg = requestedForeground or (0xFF shl 24)
    val effectiveRequestedBg = (requestedBackground ?: automaticBiliPaiIconBackground(effectiveRequestedFg)) or (0xFF shl 24)
    val matchedFg = biliPaiThemeIconColor(matchedIndex) or (0xFF shl 24)
    val matchedBg = biliPaiThemeIconBackground(matchedIndex) or (0xFF shl 24)
    return effectiveRequestedFg == matchedFg && effectiveRequestedBg == matchedBg
}

private data class OkLabColor(val l: Double, val a: Double, val b: Double)

private fun srgbChannelToLinear(channel: Int): Double {
    val v = channel / 255.0
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun argbToOkLab(argb: Int): OkLabColor {
    val r = srgbChannelToLinear(argb ushr 16 and 255)
    val g = srgbChannelToLinear(argb ushr 8 and 255)
    val b = srgbChannelToLinear(argb and 255)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return OkLabColor(
        l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

private fun nearestIconColorIndex(color: Int, choices: List<Int>): Int {
    val normalized = color or (0xFF shl 24)
    val exactIndex = choices.indexOfFirst { (it or (0xFF shl 24)) == normalized }
    if (exactIndex >= 0) return exactIndex
    val target = argbToOkLab(normalized)
    return choices.indices.minBy { index ->
        val candidate = argbToOkLab(choices[index])
        val dl = target.l - candidate.l
        val da = target.a - candidate.a
        val db = target.b - candidate.b
        dl * dl + da * da + db * db
    }
}

fun biliPaiThemeIconColor(index: Int): Int {
    require(index in 0 until BILIPAI_THEME_ICON_PALETTE_SIZE)
    if (index >= BILIPAI_THEME_ICON_BASE_PALETTE_SIZE) {
        return biliPaiIconForegroundChoices[(index - BILIPAI_THEME_ICON_BASE_PALETTE_SIZE) % biliPaiIconForegroundChoices.size]
    }
    return (255 shl 24) or (index / 36 * 51 shl 16) or
        (index / 6 % 6 * 51 shl 8) or (index % 6 * 51)
}

fun automaticBiliPaiIconBackground(color: Int): Int {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
    }
    val luminance = .2126 * linear(color ushr 16 and 255) +
        .7152 * linear(color ushr 8 and 255) + .0722 * linear(color and 255)
    return if (luminance > .4) 0xFF242321.toInt() else 0xFFFFFFFF.toInt()
}

fun biliPaiThemeIconBackground(index: Int): Int {
    require(index in 0 until BILIPAI_THEME_ICON_PALETTE_SIZE)
    return if (index < BILIPAI_THEME_ICON_BASE_PALETTE_SIZE) automaticBiliPaiIconBackground(biliPaiThemeIconColor(index))
    else biliPaiIconBackgroundChoices[(index - BILIPAI_THEME_ICON_BASE_PALETTE_SIZE) / biliPaiIconForegroundChoices.size]
}

internal fun biliPaiThemeAliasSuffix(index: Int, splashIconVisible: Boolean): String {
    require(index in 0 until BILIPAI_THEME_ICON_PALETTE_SIZE)
    return "MainActivityAliasBiliPaiTheme${index.toString().padStart(3, '0')}" +
        if (splashIconVisible) "" else "NoIcon"
}
