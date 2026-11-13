package com.android.purebilibili.core.store

import kotlin.math.pow

const val BILIPAI_THEME_ICON_KEY = "icon_bilipai_theme"
const val BILIPAI_THEME_ICON_BASE_PALETTE_SIZE = 216
const val DEFAULT_BILIPAI_THEME_ICON_INDEX = 17

/** 自动背景保留六级 RGB 匹配；独立背景使用前景、背景色板的组合。 */
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

private fun nearestIconColorIndex(color: Int, choices: List<Int>): Int = choices.indices.minBy { index ->
    listOf(0, 8, 16).sumOf { shift ->
        val delta = (color ushr shift and 255) - (choices[index] ushr shift and 255)
        delta * delta
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
