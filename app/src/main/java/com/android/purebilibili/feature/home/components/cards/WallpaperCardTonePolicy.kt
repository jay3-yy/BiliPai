package com.android.purebilibili.feature.home.components.cards

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.theme.ACCESSIBLE_TEXT_MIN_CONTRAST
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.materialkolor.hct.Hct

/** 保留壁纸色相，限制色度，并为当前主题的标题、元数据留出可读明度。 */
internal fun resolveWallpaperCardTint(
    wallpaperColor: Color,
    defaultContainerColor: Color,
    frostedGlassEnabled: Boolean,
    isDataSaverActive: Boolean = false,
    titleColor: Color = if (defaultContainerColor.luminance() < 0.5f) Color.White else Color.Black,
    subtitleColor: Color = if (defaultContainerColor.luminance() < 0.5f) Color(0xFFCAC4D0) else Color(0xFF49454F),
): Color {
    val dark = defaultContainerColor.luminance() < 0.5f
    val source = Hct.fromInt(wallpaperColor.toArgb())
    // 深色表面降低相对色度，暖色壁纸保留暗色调，避免低明度下形成浓重棕橙色块。
    // 浅色路径保持现有色度与明度；原生和玻璃分别约束深色表面的色度上限。
    val chroma = if (dark) {
        (source.chroma * 0.35).coerceAtMost(if (frostedGlassEnabled) 12.0 else 16.0)
    } else {
        source.chroma.coerceAtMost(if (frostedGlassEnabled) 28.0 else 36.0)
    }
    val desiredTone = if (dark) 18.0 + source.tone * 0.06 else 88.0 + source.tone * 0.08
    fun colorAt(tone: Double) = Color(Hct.from(source.hue, chroma, tone).toInt())
    // 为色点插值和 8 位颜色量化留少量余量，避免滚动中恰好跌破文字对比度要求。
    val minimumContrast = ACCESSIBLE_TEXT_MIN_CONTRAST + 0.1f
    fun readable(background: Color): Boolean =
        calculateContrastRatio(titleColor.compositeOver(background), background) >= minimumContrast &&
            calculateContrastRatio(subtitleColor.compositeOver(background), background) >= minimumContrast

    var toned = colorAt(desiredTone)
    if (!readable(toned)) {
        // 自定义文字色需要更多对比度时，只调整表面明度，仍沿用壁纸色相。
        var failingTone = desiredTone
        var passingTone = if (dark) 0.0 else 100.0
        toned = colorAt(passingTone)
        repeat(8) {
            val tone = (failingTone + passingTone) * 0.5
            val candidate = colorAt(tone)
            if (readable(candidate)) {
                toned = candidate
                passingTone = tone
            } else failingTone = tone
        }
    }
    if (!frostedGlassEnabled) return toned

    // 玻璃下方可能同时有亮部与暗部；以两端检查遮罩强度，滚动时不反复切换文字色。
    fun readableAt(alpha: Float): Boolean {
        val plate = toned.copy(alpha = alpha)
        return readable(plate.compositeOver(Color.Black)) && readable(plate.compositeOver(Color.White))
    }
    val minimumAlpha = if (isDataSaverActive) 0.76f else 0.42f
    if (readableAt(minimumAlpha)) return toned.copy(alpha = minimumAlpha)
    var failingAlpha = minimumAlpha
    var passingAlpha = 1f
    repeat(10) {
        val alpha = (failingAlpha + passingAlpha) * 0.5f
        if (readableAt(alpha)) passingAlpha = alpha else failingAlpha = alpha
    }
    return toned.copy(alpha = passingAlpha)
}

/** 在壁纸或主题变化时预先调色；滚动绘制只在这些色点之间插值。 */
internal fun resolveWallpaperCardSurfacePalette(
    palette: WallpaperPalette,
    defaultContainerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    frostedGlassEnabled: Boolean,
    isDataSaverActive: Boolean,
): WallpaperPalette {
    val colors = mutableMapOf<Color, Color>()
    fun surface(color: Color): Color = colors.getOrPut(color) {
        resolveWallpaperCardTint(
            wallpaperColor = color,
            defaultContainerColor = defaultContainerColor,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            frostedGlassEnabled = frostedGlassEnabled,
            isDataSaverActive = isDataSaverActive,
        )
    }
    return WallpaperPalette(
        topColor = surface(palette.topColor),
        bottomColor = surface(palette.bottomColor),
        dominantColor = surface(palette.dominantColor),
        stops = palette.stops.map(::surface),
    )
}
