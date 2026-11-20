package com.android.purebilibili.feature.home.components.cards

import com.android.purebilibili.core.ui.wallpaper.WallpaperReadabilityProfile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource

/**
 * 全局壁纸调色板数据结构
 */
@Immutable
data class WallpaperPalette(
    val topColor: Color,
    val bottomColor: Color,
    val dominantColor: Color = topColor,
    val stops: List<Color> = listOf(topColor, bottomColor),
    val readability: WallpaperReadabilityProfile? = null,
)

/**
 * CompositionLocal 共享当前全局壁纸调色板（单向只读数据，杜绝视图层循环采样）
 */
val LocalWallpaperPalette = staticCompositionLocalOf<WallpaperPalette?> { null }

/**
 * CompositionLocal 分别控制卡片动态取色和毛玻璃。
 */
val LocalHomeCardDynamicTintEnabled = staticCompositionLocalOf { false }
val LocalHomeCardFrostedGlassEnabled = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
internal fun isGlobalWallpaperVisibleForHomeCard(): Boolean =
    LocalGlobalWallpaperBackdropVisible.current && LocalHomeCardFrostedGlassEnabled.current &&
        LocalGlobalWallpaperBackdropSource.current != null

/**
 * CompositionLocal 提供实时滚动 Tick（在 DrawPhase 中按需读取，零重组实现 120fps 极速刷新）
 */
val LocalHomeScrollTickProvider = staticCompositionLocalOf<(() -> Int)?> { null }

// Keep the state objects stable; only blur consumers subscribe to motion boundaries.
val LocalHomeCardScrolling = compositionLocalOf<State<Boolean>?> { null }
val LocalHomeCardTransitionRunning = compositionLocalOf<State<Boolean>?> { null }

/** Only home defers optional network/color work; other card callers keep normal behavior. */
internal val LocalHomeCardAuxiliaryWorkAllowed = compositionLocalOf { true }

/**
 * 纯 Kotlin 采样区域定义（无 Android 运行库依赖，便于快速单元测试）
 */
@Immutable
data class SamplingRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * 卡片底部信息区动态着色决策结果
 */
@Immutable
data class VideoCardAmbientDrawSpec(
    val containerColor: Color,
    val coverGlowAlpha: Float,
    val borderColor: Color
)

/**
 * 计算封面取色区域。代表色必须覆盖整张封面，避免字幕、边缘高光或底部渐变主导结果。
 */
fun resolveCoverBottomSamplingRegion(width: Int, height: Int): SamplingRegion {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    return SamplingRegion(
        left = 0,
        top = 0,
        right = safeWidth,
        bottom = safeHeight
    )
}

/**
 * 壁纸是卡片动态取色的唯一来源时，封面色只作为无壁纸时的回退。
 */
internal fun shouldUseCoverTintForCard(
    wallpaperTintEnabled: Boolean,
    coverTint: Color?,
): Boolean = !wallpaperTintEnabled && coverTint != null && coverTint.alpha > 0f

/**
 * 壁纸随 Y 轴屏幕位置线性插值（图一能力：滚动时 120fps 极轻量渐变，多段平滑过渡）
 */
fun interpolateWallpaperColor(
    palette: WallpaperPalette,
    yFraction: Float
): Color {
    val clamped = yFraction.coerceIn(0f, 1f)
    val stops = palette.stops
    if (stops.isEmpty()) return palette.topColor
    if (stops.size == 1) return stops[0]
    val scaled = clamped * (stops.size - 1)
    val index = scaled.toInt().coerceIn(0, stops.size - 2)
    val fraction = scaled - index
    return lerp(stops[index], stops[index + 1], fraction)
}

/**
 * 壁纸联动玻璃的不透明度。单独取色（无模糊层）时接近不透明，保证读清文字；
 * 有模糊层时也保留足够下限，避免壁纸细节从文字底下透出。
 */
fun resolveWallpaperGlassAlpha(
    frostedGlassEnabled: Boolean,
    isDarkTheme: Boolean,
    isDataSaverActive: Boolean = false,
): Float = if (!frostedGlassEnabled) {
    0.88f
} else if (isDarkTheme) {
    if (isDataSaverActive) 0.65f else 0.56f
} else {
    if (isDataSaverActive) 0.70f else 0.52f
}

/**
 * 综合决策卡片底部组件的着色方案（兼顾图一壁纸与图二封面）
 */
fun resolveVideoCardAmbientDrawSpec(
    wallpaperPalette: WallpaperPalette?,
    yFraction: Float,
    coverTint: Color?,
    wallpaperTintEnabled: Boolean = true,
    isDarkTheme: Boolean,
    defaultContainerColor: Color,
    defaultBorderColor: Color,
    isDataSaverActive: Boolean = false,
    frostedGlassEnabled: Boolean = true,
    dynamicTintEnabled: Boolean = true,
    wallpaperSurfacePalette: WallpaperPalette? = null,
): VideoCardAmbientDrawSpec {
    if (!dynamicTintEnabled) {
        return VideoCardAmbientDrawSpec(
            containerColor = if (frostedGlassEnabled) {
                defaultContainerColor.copy(alpha = if (isDarkTheme) 0.38f else 0.34f)
            } else {
                defaultContainerColor
            },
            coverGlowAlpha = 0f,
            borderColor = defaultBorderColor
        )
    }
    if (!wallpaperTintEnabled && wallpaperPalette == null && coverTint == null) {
        return VideoCardAmbientDrawSpec(defaultContainerColor, 0f, defaultBorderColor)
    }

    val hasValidCoverTint = shouldUseCoverTintForCard(
        wallpaperTintEnabled = wallpaperTintEnabled,
        coverTint = coverTint,
    )

    // 毛玻璃使用半透明着色；透明度下限保证壁纸细节不会从文字底下透出。
    val glassTransparency = resolveWallpaperGlassAlpha(
        frostedGlassEnabled = frostedGlassEnabled,
        isDarkTheme = isDarkTheme,
        isDataSaverActive = isDataSaverActive
    )

    // 壁纸联动保留原色的色相，以主题容器约束明度，避免整块高饱和信息面板。
    val baseColor = if (wallpaperTintEnabled && wallpaperSurfacePalette != null) {
        interpolateWallpaperColor(wallpaperSurfacePalette, yFraction)
    } else if (wallpaperTintEnabled && wallpaperPalette != null) {
        val rawWallpaperColor = interpolateWallpaperColor(wallpaperPalette, yFraction)
        resolveWallpaperCardTint(
            wallpaperColor = rawWallpaperColor,
            defaultContainerColor = defaultContainerColor,
            frostedGlassEnabled = frostedGlassEnabled,
            isDataSaverActive = isDataSaverActive,
        )
    } else if (wallpaperTintEnabled) {
        // Wallpaper is enabled but still loading: hold a neutral glass surface instead of
        // flashing the previous cover's color while the backdrop/palette catches up.
        resolveWallpaperCardTint(
            wallpaperColor = defaultContainerColor,
            defaultContainerColor = defaultContainerColor,
            frostedGlassEnabled = frostedGlassEnabled,
            isDataSaverActive = isDataSaverActive,
        )
    } else if (hasValidCoverTint) {
        val blendFraction = if (isDarkTheme) 0.85f else 0.75f
        lerp(defaultContainerColor, coverTint!!, blendFraction).copy(alpha = glassTransparency)
    } else {
        defaultContainerColor.copy(alpha = glassTransparency)
    }

    // 2. 封面边缘色微光联动（图二）：明显增强顶部向下的流光扩散，使封面氛围色鲜明且通透
    val glowAlpha = if (hasValidCoverTint) {
        if (isDarkTheme) 0.48f else 0.38f
    } else {
        0f
    }

    // 3. 边框微弱辉光呼应（高反差精细白/色边，构筑纯正毛玻璃边缘棱镜质感）
    val finalBorderColor = if (hasValidCoverTint) {
        val borderBlendFraction = if (isDarkTheme) 0.50f else 0.40f
        val borderAlpha = if (isDarkTheme) 0.30f else 0.45f
        val baseBorder = Color.White.copy(alpha = borderAlpha)
        lerp(baseBorder, coverTint!!, borderBlendFraction).copy(alpha = borderAlpha)
    } else {
        if (isDarkTheme) {
            Color.White.copy(alpha = if (wallpaperTintEnabled) 0.12f else 0.25f)
        } else Color.White.copy(alpha = 0.40f)
    }

    return VideoCardAmbientDrawSpec(
        containerColor = baseColor,
        coverGlowAlpha = glowAlpha,
        borderColor = finalBorderColor
    )
}

/**
 * Adaptive text colors for video card based on dynamic surface tint and theme.
 */
@Immutable
data class VideoCardAdaptiveContentColors(
    val titleColor: Color,
    val subtitleColor: Color,
    val isDarkSurface: Boolean,
)

/**
 * Resolves title and metadata colors adapted for dynamic card surface tinting.
 * Prevents low contrast when dark wallpaper or dark cover tint turns the card dark in light theme.
 */
fun resolveVideoCardAdaptiveContentColors(
    wallpaperPalette: WallpaperPalette?,
    coverTint: Color?,
    wallpaperTintEnabled: Boolean,
    isDarkTheme: Boolean,
    defaultOnSurface: Color,
    defaultOnSurfaceVariant: Color,
    homeCardDynamicTintEnabled: Boolean = true,
    wallpaperYFraction: Float = 0.5f,
    wallpaperGlassAlpha: Float = 0f,
    // 已按主题容器调色的卡片不再用壁纸原色决定文字；其他透壁纸表面保留自适应判断。
    wallpaperSurfaceToned: Boolean = false,
): VideoCardAdaptiveContentColors {
    if (!homeCardDynamicTintEnabled) {
        return VideoCardAdaptiveContentColors(
            titleColor = defaultOnSurface,
            subtitleColor = defaultOnSurfaceVariant,
            isDarkSurface = isDarkTheme
        )
    }

    val isDarkSurface = if (isDarkTheme) {
        true
    } else {
        val hasDarkWallpaper = !wallpaperSurfaceToned && wallpaperTintEnabled &&
            wallpaperPalette != null &&
            run {
                // 与容器同一来源的局部取色色值，而不是整张壁纸的主导色：
                // 主导色偏深而卡片所在区域偏浅时，会把浅色表面误判成深色，
                // 套上白色文字导致可读性问题。合成时计入玻璃不透明度下的底色。
                val localLuminance = interpolateWallpaperColor(
                    wallpaperPalette,
                    wallpaperYFraction
                ).luminance()
                if (wallpaperGlassAlpha > 0f) {
                    val baseLuminance = if (isDarkTheme) 0.05f else 0.92f
                    localLuminance * wallpaperGlassAlpha +
                        baseLuminance * (1f - wallpaperGlassAlpha)
                } else {
                    localLuminance
                }
            } < 0.45f

        val hasValidCover = shouldUseCoverTintForCard(
            wallpaperTintEnabled = wallpaperTintEnabled,
            coverTint = coverTint,
        )
        val hasDarkCover = hasValidCover && coverTint!!.luminance() < 0.38f

        hasDarkWallpaper || hasDarkCover
    }

    return if (isDarkSurface && !isDarkTheme) {
        VideoCardAdaptiveContentColors(
            titleColor = Color.White,
            subtitleColor = Color.White.copy(alpha = 0.78f),
            isDarkSurface = true
        )
    } else {
        VideoCardAdaptiveContentColors(
            titleColor = defaultOnSurface,
            subtitleColor = defaultOnSurfaceVariant,
            isDarkSurface = isDarkSurface
        )
    }
}
