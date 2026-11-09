package com.android.purebilibili.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import com.android.purebilibili.feature.home.components.liquid.lens
import com.android.purebilibili.feature.home.components.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** Dock、分类栏和搜索胶囊共用外壳材质；尺寸、交互形变由调用方提供。 */
@Composable
internal fun Modifier.dockShellBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    containerColor: Color,
    darkTheme: Boolean,
    tuning: LiquidGlassTuning,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    effectPaddingPx: Float,
    inheritOptics: Boolean,
    highlightActive: Boolean,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val materialBlur = rememberMaterialBackdropBlur(tuning)
    val hasLens = !inheritOptics && refractionHeightPx > 0f && refractionAmountPx > 0f
    val highlight = rememberBiliPaiGravityHighlight(extraDegrees = -45f, active = hasLens && highlightActive)
    val effects = remember(materialBlur, tuning, refractionHeightPx, refractionAmountPx,
        effectPaddingPx, inheritOptics, hasLens) {
        val block: BackdropEffectScope.() -> Unit = {
            padding = maxOf(padding, effectPaddingPx)
            if (!inheritOptics) {
                vibrancy(tuning.saturation)
                materialBlur(tuning.backdropBlurRadius * density)
                if (hasLens) lens(refractionHeightPx, refractionAmountPx,
                    chromaticAberration = tuning.shellChromaticAberrationAmount)
            }
        }
        block
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = effects,
        highlight = { if (hasLens) highlight.value.copy(alpha = 0.75f) else null },
        layerBlock = layerBlock,
        // 搜索框与 dock 叠加同一底色及可读性遮罩，不额外加入按压染色。
        onDrawSurface = {
            drawRect(containerColor)
            if (tuning.contentReadabilityScrimAlpha > 0f) {
                drawRect((if (darkTheme) Color.Black else Color.White)
                    .copy(alpha = tuning.contentReadabilityScrimAlpha))
            }
        },
    )
}
