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
    maxChroma: Double = Double.POSITIVE_INFINITY,
    backdropColorBounds: WallpaperBackdropColorBounds? = null,
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
    val desiredTone = when {
        dark && frostedGlassEnabled -> 12.0 + source.tone * 0.035
        frostedGlassEnabled -> 92.0 + source.tone * 0.035
        dark -> 18.0 + source.tone * 0.06
        else -> 88.0 + source.tone * 0.08
    }
    val surfaceChroma = minOf(chroma, maxChroma)
    fun colorAt(tone: Double) = Color(Hct.from(source.hue, surfaceChroma, tone).toInt())
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

    // 缓存模糊图的色域已知时，用实际边界约束衬色。实时采样仍以黑、白两端保护文字。
    val lower = backdropColorBounds?.lower ?: Color.Black
    val upper = backdropColorBounds?.upper ?: Color.White
    fun readableAt(alpha: Float): Boolean {
        val plate = toned.copy(alpha = alpha)
        return readable(plate.compositeOver(lower)) && readable(plate.compositeOver(upper))
    }
    val minimumAlpha = when {
        backdropColorBounds != null -> 0.24f
        isDataSaverActive -> 0.76f
        else -> 0.36f
    }
    if (readableAt(minimumAlpha)) return toned.copy(alpha = minimumAlpha)
    var failingAlpha = minimumAlpha
    var passingAlpha = 1f
    repeat(10) {
        val alpha = (failingAlpha + passingAlpha) * 0.5f
        if (readableAt(alpha)) passingAlpha = alpha else failingAlpha = alpha
    }
    return toned.copy(alpha = passingAlpha)
}

/** 固定模式只从当前主题选择衬色。底图亮度可用于维持文字对比度。 */
internal fun resolveNeutralFrostedCardTint(
    defaultContainerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    backdropColorBounds: WallpaperBackdropColorBounds? = null,
): Color = resolveWallpaperCardTint(
    wallpaperColor = defaultContainerColor,
    defaultContainerColor = defaultContainerColor,
    frostedGlassEnabled = true,
    titleColor = titleColor,
    subtitleColor = subtitleColor,
    maxChroma = 4.0,
    backdropColorBounds = backdropColorBounds,
)

/** 保留封面色相，限制信息区色度，并为主题文字保留对比度。 */
internal fun resolveCoverCardTint(
    coverColor: Color,
    defaultContainerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    blurredCover: Boolean = false,
): Color = resolveWallpaperCardTint(
    wallpaperColor = coverColor,
    defaultContainerColor = defaultContainerColor,
    frostedGlassEnabled = blurredCover,
    titleColor = titleColor,
    subtitleColor = subtitleColor,
    maxChroma = if (defaultContainerColor.luminance() < 0.5f) 10.0 else 18.0,
)

/** 在壁纸或主题变化时预先调色；滚动绘制只在这些色点之间插值。 */
internal fun resolveWallpaperCardSurfacePalette(
    palette: WallpaperPalette,
    defaultContainerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    frostedGlassEnabled: Boolean,
    isDataSaverActive: Boolean,
    backdropColorBounds: WallpaperBackdropColorBounds? = null,
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
            backdropColorBounds = backdropColorBounds,
        )
    }
    return WallpaperPalette(
        topColor = surface(palette.topColor),
        bottomColor = surface(palette.bottomColor),
        dominantColor = surface(palette.dominantColor),
        stops = palette.stops.map(::surface),
    )
}
