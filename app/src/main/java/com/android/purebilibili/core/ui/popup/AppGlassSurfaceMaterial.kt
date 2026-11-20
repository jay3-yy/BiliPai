package com.android.purebilibili.core.ui.popup

import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.home.GlassPopupPalette
import com.android.purebilibili.core.store.home.LiquidGlassSettingsStore
import com.materialkolor.hct.Hct
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.resolveBiliPaiBottomBarContainerColor
import com.android.purebilibili.feature.home.components.liquid.lens
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** 配色仅由玻璃弹窗入口读取，原生组件和侧栏继续使用自身材质。 */
@Composable
internal fun rememberGlassPopupPalette(): GlassPopupPalette {
    val context = LocalContext.current
    val flow = remember(context) { LiquidGlassSettingsStore.observePopupPalette(context) }
    val palette by flow.collectAsStateWithLifecycle(initialValue = GlassPopupPalette.DOCK)
    return palette
}

internal fun resolveThemeFrostedPopupColor(darkTheme: Boolean, colors: ColorScheme): Color {
    if (!darkTheme) return colors.surfaceBright.copy(alpha = 0.55f)
    val card = Hct.fromInt(colors.surfaceContainer.toArgb())
    return Color(Hct.from(card.hue, card.chroma, (card.tone + 7.0).coerceIn(0.0, 100.0)).toInt())
        .copy(alpha = 0.63f)
}

/** 菜单与侧栏共用 Dock 底色；按内容类型保留独立遮色强度。 */
internal fun resolveAppGlassSurfaceColor(
    darkTheme: Boolean,
    lowBlurBudget: Boolean,
    interactivePanel: Boolean,
    mediaBackdrop: Boolean,
    tuning: LiquidGlassTuning,
): Color = resolveBiliPaiBottomBarContainerColor(darkTheme, tuning).copy(alpha = when {
    lowBlurBudget -> 0.96f
    // 浅色模式的弹窗文字为深色，底色必须保证叠在任意亮度背景（如深色视频）上
    // 合成后仍足够浅，因此浅色各分支的白色不透明度更高；深色媒体材质配浅色文字，
    // 维持原有轻遮透明度即可满足对比，避免玻璃变成过实的深色厚板。
    interactivePanel -> if (darkTheme) 0.24f else 0.68f
    mediaBackdrop -> if (darkTheme) 0.36f else 0.72f
    else -> if (darkTheme) 0.63f else 0.70f
})

/** 侧栏保持自身布局与手势，只复用弹窗的中性磨砂、底色和轻折射。 */
@Composable
internal fun Modifier.appGlassDrawerSurface(
    backdrop: Backdrop,
    shape: Shape,
    darkTheme: Boolean,
    tuning: LiquidGlassTuning,
    forceLowBlurBudget: Boolean,
): Modifier {
    val density = LocalDensity.current
    val lowBudget = com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val surfaceColor = resolveAppGlassSurfaceColor(darkTheme, lowBudget, false, false, tuning)
    val blurPx = with(density) { if (lowBudget) 0f else 24.dp.toPx() }
    val heightPx = with(density) { (tuning.refractionHeight * 0.5f).dp.toPx() }
    val amountPx = with(density) { (tuning.refractionAmount * 0.5f).dp.toPx() }
    // 开合期间只停折射，底色与磨砂保持稳定，避免落定时透明度突然变化。
    val drawLens = !lowBudget && !forceLowBlurBudget && tuning.contentDistortionScale > 0.001f
    val effects = remember(blurPx, heightPx, amountPx, drawLens) {
        val block: BackdropEffectScope.() -> Unit = {
            blur(blurPx, blurPx)
            colorControls(brightness = 0f, saturation = 1f)
            if (drawLens) lens(refractionHeight = heightPx, refractionAmount = amountPx)
        }
        block
    }
    return this.drawBackdrop(
        backdrop = com.android.purebilibili.core.ui.blur.rememberContinuousBackdrop(backdrop),
        shape = { shape },
        effects = effects,
        onDrawSurface = { drawRect(surfaceColor) },
    ).border(
        0.7.dp,
        (if (darkTheme) Color.White else Color.Black).copy(alpha = if (darkTheme) 0.12f else 0.14f),
        shape,
    )
}
