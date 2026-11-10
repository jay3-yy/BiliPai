package com.android.purebilibili.feature.home.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlurEffect
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur

internal const val BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP = 10f
internal const val BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION = 0f
internal const val BILIPAI_PROGRESSIVE_TOP_BLUR_FALLOFF_CURVE = 1.25f
private const val BILIPAI_PROGRESSIVE_TOP_BLUR_MIN_EXTENSION_DP = 20f
private const val BILIPAI_PROGRESSIVE_TOP_BLUR_EXTRA_EXTENSION_DP = 28f
private val BiliPaiProgressiveTopBlurShape = RoundedCornerShape(
    bottomStart = 28.dp,
    bottomEnd = 28.dp,
)

/**
 * Shared progressive top blur gradient preset inspired by HyperIsland's top status bar design.
 * Smooth continuous falloff from status-bar top edge (0f) toward the clear edge.
 *
 * Preserves gradient = ProgressiveBlur.Top contract for policy tests.
 */
internal val BILIPAI_PROGRESSIVE_TOP_BLUR_DEFAULT_GRADIENT: ProgressiveBlur = ProgressiveBlur.Top.copy(
    startFraction = 0f,
    endFraction = 1f,
    curve = BILIPAI_PROGRESSIVE_TOP_BLUR_FALLOFF_CURVE,
)

internal fun shouldUseBiliPaiProgressiveTopBlur(
    enabled: Boolean,
    hasBackdrop: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
    blurRadiusDp: Float = BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP,
): Boolean = enabled && hasBackdrop && sdkInt >= Build.VERSION_CODES.TIRAMISU &&
    blurRadiusDp.isFinite() && blurRadiusDp > 0.001f

/** Floating controls need a continuous opaque surface when neither header blur is active. */
internal fun shouldUseOpaqueTopChromeBackground(
    progressiveBlurActive: Boolean,
    headerBlurActive: Boolean,
    liquidGlassActive: Boolean = false,
): Boolean = !progressiveBlurActive && !headerBlurActive && !liquidGlassActive

internal fun resolveProgressiveTopBlurBottomExtension(
    enabled: Boolean,
    endFraction: Float,
): Dp = if (enabled) {
    (
        BILIPAI_PROGRESSIVE_TOP_BLUR_MIN_EXTENSION_DP +
            endFraction.coerceIn(0f, 1f) * BILIPAI_PROGRESSIVE_TOP_BLUR_EXTRA_EXTENSION_DP
    ).dp
} else {
    0.dp
}

internal fun shouldExtendProgressiveTopBlurBelowTabs(
    progressiveBlurEnabled: Boolean,
    tabRowIncludedInBlur: Boolean,
): Boolean = progressiveBlurEnabled && !tabRowIncludedInBlur

/** 在调用处解析材质并复用绘制节点，避免每个顶栏再创建一层修饰器组合。 */
@Composable
internal fun Modifier.biliPaiProgressiveTopBlur(
    backdrop: Backdrop?,
    enabled: Boolean,
    shape: Shape = BiliPaiProgressiveTopBlurShape,
    blurRadiusDp: Float = BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP,
    gradient: ProgressiveBlur = BILIPAI_PROGRESSIVE_TOP_BLUR_DEFAULT_GRADIENT,
    colors: BlurColors = BlurColors(),
    progressiveCompositeEnabled: Boolean = true,
): Modifier {
    if (
        !shouldUseBiliPaiProgressiveTopBlur(
            enabled = enabled,
            hasBackdrop = backdrop != null,
            blurRadiusDp = blurRadiusDp,
        )
    ) {
        return this
    }
    val source = requireNotNull(backdrop)
    if (isLowBlurBudgetForced()) return this
    // 渲染方案与玻璃开关独立，各主题保留自己的几何、底色和淡出参数。
    // 自定义调色仍交给原有材质链，避免官方路径丢失扩展混色语义。
    if (isOfficialProgressiveBlurSupported() && colors == BlurColors()) {
        val renderer = com.android.purebilibili.core.ui.blur.LocalProgressiveBlurRenderer.current
        if (renderer == com.android.purebilibili.core.store.home.ProgressiveBlurRenderer.ANDROID) {
            val official = rememberOfficialProgressiveTopBlur(shape, blurRadiusDp, gradient)
            if (official != null) return this.then(official)
        }
    }
    val continuousSource = com.android.purebilibili.core.ui.blur.rememberContinuousBackdrop(source)
    // 材质不变时复用形状和效果回调，避免无关重组重建渐进合成与清晰端效果。
    // 尺寸与背景内容变化仍由绘制节点更新，不缓存画面帧。
    val effect = remember(continuousSource, shape, blurRadiusDp, gradient, colors, progressiveCompositeEnabled) {
        if (progressiveCompositeEnabled) Modifier.progressiveTextureBlur(
            backdrop = continuousSource,
            shape = shape,
            blurRadius = blurRadiusDp,
            gradient = gradient,
            colors = colors,
        ) else Modifier.drawBackdrop(
            backdrop = continuousSource,
            shape = { shape },
            effects = {
                // Omitting progressiveGradient avoids the downscaled multi-level composite.
                // The effect itself still varies the blur radius continuously with height.
                progressiveTextureBlurEffect(
                    blurRadiusX = blurRadiusDp,
                    gradient = gradient,
                    colors = colors,
                )
            },
        )
    }
    return this.then(effect)
}

/** Convenience overload that blends [surfaceColor] into the progressive blur shader pass. */
@Composable
internal fun Modifier.biliPaiProgressiveTopBlur(
    backdrop: Backdrop?,
    enabled: Boolean,
    surfaceColor: Color,
    shape: Shape = BiliPaiProgressiveTopBlurShape,
    blurRadiusDp: Float = BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP,
    gradient: ProgressiveBlur = BILIPAI_PROGRESSIVE_TOP_BLUR_DEFAULT_GRADIENT,
): Modifier {
    val blurColors = remember(surfaceColor) {
        if (surfaceColor.alpha > 0.001f) {
            BlurColors(blendColors = listOf(BlendColorEntry(color = surfaceColor)))
        } else {
            BlurColors()
        }
    }
    return biliPaiProgressiveTopBlur(
        backdrop = backdrop,
        enabled = enabled,
        shape = shape,
        blurRadiusDp = blurRadiusDp,
        gradient = gradient,
        colors = blurColors,
    )
}

/** 仅在壁纸实际属于当前顶栏背景时取色，避免个人头图被全局壁纸染色。 */
@androidx.compose.runtime.Composable
internal fun currentTopChromeWallpaperPalette(surfaceColor: Color): com.android.purebilibili.feature.home.components.cards.WallpaperPalette? {
    if (surfaceColor.alpha <= 0f) return null
    val route = com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute.current
        ?.substringBefore('?')?.substringBefore('/')
    val visible = com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible.current || route == "home"
    if (!visible || route == "profile") return null
    return com.android.purebilibili.feature.home.components.cards.LocalWallpaperPalette.current
}

/**
 * Draws the shared gradient beyond the chrome without enlarging its layout or touch region.
 *
 * When [extendBelowBounds] is false (default) the progressive blur is confined to the chrome's own height
 * and does not bleed below its bottom edge into content or lists under the bar.
 * The blur effect inside the chrome itself is unchanged.
 */
@androidx.compose.runtime.Composable
internal fun BiliPaiImmersiveTopBar(
    backdrop: Backdrop?,
    enabled: Boolean,
    /** True only when a real Haze effect is attached and ready to render. */
    headerBlurActive: Boolean = false,
    liquidGlassActive: Boolean = false,
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.background,
    fadeEnabled: Boolean? = null,
    extendBelowBounds: Boolean = false,
    opaqueBackgroundFallback: Boolean = true,
    progressiveCompositeEnabled: Boolean = true,
    iconSurfaceColor: Color = Color.Transparent,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val themeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val active = shouldUseBiliPaiProgressiveTopBlur(enabled, backdrop != null) &&
        !isLowBlurBudgetForced()
    val solidFadeActive = (fadeEnabled ?: themeConfig.progressiveTopFadeEnabled) && !headerBlurActive
    val wallpaperPalette = currentTopChromeWallpaperPalette(surfaceColor)
    val readability = wallpaperPalette?.readability
    // 渐隐取色属于顶部特效，玻璃和原生路径共用；特效关闭后的背景不参与。
    val fadeColor = resolveTopChromeFadeColor(
        surfaceColor = surfaceColor,
        foregroundColor = MaterialTheme.colorScheme.onSurface,
        blurred = active,
        backgroundColor = wallpaperPalette?.topColor,
        readabilityBand = readability?.top,
    )
    // Local protection is part of the enabled progressive material. With both effects off,
    // preserve the user's plain chrome choice; Haze continues to own its existing scrim.
    val drawProtectionFade = solidFadeActive || (active && !headerBlurActive && readability != null)
    val protectedFraction = if (readability != null) 0.65f else 0f
    val fadeBrush = if (drawProtectionFade) {
        remember(fadeColor, protectedFraction) {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colorStops = com.android.purebilibili.core.ui.blur.ProgressiveFadeDefaults.createStops(
                    fadeColor, protectedFraction = protectedFraction,
                ).toTypedArray(),
            )
        }
    } else null
    val opaqueBackground = opaqueBackgroundFallback && shouldUseOpaqueTopChromeBackground(
        progressiveBlurActive = (active || solidFadeActive),
        headerBlurActive = headerBlurActive,
        liquidGlassActive = liquidGlassActive,
    )
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(
                if (opaqueBackground) MaterialTheme.colorScheme.background.copy(alpha = 1f)
                else Color.Transparent
            )
            .then(modifier),
    ) {
        // 两种特效共用相同绘制边界：先绘制模糊，再叠加渐隐，不增加第二个测量层。
        // 不改变玻璃开关关闭时的原生布局，也不改变任一特效的独立开关。
        if (active || drawProtectionFade) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val extension = resolveProgressiveTopBlurBottomExtension(
                            enabled = extendBelowBounds,
                            endFraction = BILIPAI_PROGRESSIVE_TOP_BLUR_DEFAULT_GRADIENT.endFraction,
                        ).roundToPx()
                        val extended = constraints.copy(
                            minHeight = constraints.minHeight + extension,
                            maxHeight = constraints.maxHeight + extension,
                        )
                        val placeable = measurable.measure(extended)
                        layout(placeable.width, placeable.height - extension) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .biliPaiProgressiveTopBlur(
                        backdrop = backdrop,
                        enabled = active,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        progressiveCompositeEnabled = progressiveCompositeEnabled,
                    )
                    .then(if (fadeBrush != null) Modifier.background(fadeBrush) else Modifier),
            )
        }
        val iconBackground = if (opaqueBackground) {
            iconSurfaceColor.compositeOver(MaterialTheme.colorScheme.background)
        } else iconSurfaceColor
        androidx.compose.runtime.CompositionLocalProvider(
            com.android.purebilibili.core.ui.components.LocalAppTopBarSurfaceColor provides iconBackground,
            com.android.purebilibili.core.ui.LocalImmersiveTopChromeActive provides
                ((active || headerBlurActive) || solidFadeActive),
            content = content,
        )
    }
}
