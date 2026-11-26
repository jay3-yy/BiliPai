package com.android.purebilibili.feature.nearby

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val NEARBY_FLIGHT_OUTGOING_MS = 420
internal const val NEARBY_FLIGHT_INCOMING_MS = 520

internal data class NearbyFlightTransform(
    val translationYPx: Float,
    val scale: Float,
    val alpha: Float,
    val rotationXDeg: Float,
    val blurFraction: Float,
    val scrimAlpha: Float,
    val backdropBlurFraction: Float,
    val ambientGlowAlpha: Float,
    val haloProgress: Float,
    val haloAlpha: Float,
)

private fun easeOutCubic(t: Float): Float {
    val inv = 1f - t.coerceIn(0f, 1f)
    return 1f - inv * inv * inv
}

private fun easeInOutCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
}

/** 带轻微弹性过冲的落位曲线（用于接收卡片稳稳落到屏幕中央的物理阻尼感）。 */
private fun easeOutBackSubtle(t: Float): Float {
    val c1 = 0.85f
    val c3 = c1 + 1f
    val x = t.coerceIn(0f, 1f) - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/** 同一进度控制光晕与衬底的柔和包络；首尾归零，不创建常驻时钟。 */
internal fun nearbyFlightEnvelope(progress: Float): Float =
    sin(PI.toFloat() * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)

/**
 * 精雕细琢的物理流转过场曲线：
 * - 发送（incoming = false）：
 *   0.00..0.32 浮起聚焦（scale 0.92 -> 1.03）+ 柔和外扩光环；
 *   0.32..1.00 加速后仰抛入顶边进度胶囊（rotationX 0 -> 16°, scale 1.03 -> 0.28, 轻度动态模糊）。
 * - 接收 / 回落（incoming = true，三段式节奏）：
 *   0.00..0.60 从顶边胶囊前倾俯冲落位中央（rotationX -14° -> 0°, scale 0.36 -> 1.0 带微过冲回稳，模糊清零）；
 *   0.60..0.80 黄金清晰停顿（用户清晰识别接力封面与来源设备，中央氛围高斯光晕柔和展开）；
 *   0.80..1.00 卡片轻盈扩展淡出（scale 1.0 -> 1.06），背后高斯模糊同步消散，无缝露出底层播放器。
 */
internal fun resolveNearbyFlightTransform(
    progress: Float,
    incoming: Boolean,
    travelPx: Float,
    reduceMotion: Boolean,
): NearbyFlightTransform {
    val p = progress.coerceIn(0f, 1f)
    if (reduceMotion) {
        val alpha = if (incoming) {
            (1f - ((p - 0.72f).coerceAtLeast(0f) / 0.28f)).coerceIn(0f, 1f)
        } else {
            1f - p
        }
        return NearbyFlightTransform(
            translationYPx = 0f,
            scale = 1f,
            alpha = alpha,
            rotationXDeg = 0f,
            blurFraction = 0f,
            scrimAlpha = 0f,
            backdropBlurFraction = 0f,
            ambientGlowAlpha = 0f,
            haloProgress = 0f,
            haloAlpha = 0f,
        )
    }

    if (!incoming) {
        val liftSplit = 0.32f
        return if (p <= liftSplit) {
            val t = easeOutCubic(p / liftSplit)
            NearbyFlightTransform(
                translationYPx = -14f * t,
                scale = 0.92f + 0.11f * t,
                alpha = (0.35f + 0.65f * t).coerceIn(0f, 1f),
                rotationXDeg = 4f * t,
                blurFraction = 0f,
                scrimAlpha = 0.32f * t,
                backdropBlurFraction = 0.75f * t,
                ambientGlowAlpha = 0.65f * t,
                haloProgress = t,
                haloAlpha = (1f - t * 0.35f) * t,
            )
        } else {
            val t = easeInOutCubic((p - liftSplit) / (1f - liftSplit))
            NearbyFlightTransform(
                translationYPx = -14f - (travelPx - 14f).coerceAtLeast(0f) * t,
                scale = 1.03f - 0.75f * t,
                alpha = (1f - ((t - 0.52f).coerceAtLeast(0f) / 0.48f)).coerceIn(0f, 1f),
                rotationXDeg = 4f + 12f * t,
                blurFraction = (t * 0.90f).coerceIn(0f, 1f),
                scrimAlpha = 0.32f * (1f - t),
                backdropBlurFraction = 0.75f * (1f - t),
                ambientGlowAlpha = 0.65f * (1f - t),
                haloProgress = 1f,
                haloAlpha = (0.65f * (1f - t * 1.4f)).coerceIn(0f, 1f),
            )
        }
    } else {
        val settleEnd = 0.60f
        val holdEnd = 0.80f
        return when {
            p <= settleEnd -> {
                val t = p / settleEnd
                val moveT = easeOutCubic(t)
                val scaleT = easeOutBackSubtle(t)
                NearbyFlightTransform(
                    translationYPx = -travelPx * (1f - moveT),
                    scale = 0.36f + 0.64f * scaleT,
                    alpha = (0.18f + 0.82f * easeOutCubic((t * 1.7f).coerceAtMost(1f))).coerceIn(0f, 1f),
                    rotationXDeg = -14f * (1f - moveT),
                    blurFraction = ((1f - moveT) * 0.82f).coerceIn(0f, 1f),
                    scrimAlpha = 0.34f * moveT,
                    backdropBlurFraction = 0.85f * moveT,
                    ambientGlowAlpha = 0.75f * moveT,
                    haloProgress = moveT,
                    haloAlpha = nearbyFlightEnvelope(moveT) * 0.8f,
                )
            }
            p <= holdEnd -> {
                // 黄金停顿：清晰展示飞入的封面与设备来源
                NearbyFlightTransform(
                    translationYPx = 0f,
                    scale = 1f,
                    alpha = 1f,
                    rotationXDeg = 0f,
                    blurFraction = 0f,
                    scrimAlpha = 0.34f,
                    backdropBlurFraction = 0.85f,
                    ambientGlowAlpha = 0.75f,
                    haloProgress = 1f,
                    haloAlpha = 0f,
                )
            }
            else -> {
                val t = easeOutCubic((p - holdEnd) / (1f - holdEnd))
                NearbyFlightTransform(
                    translationYPx = 0f,
                    scale = 1f + 0.06f * t,
                    alpha = (1f - t).coerceIn(0f, 1f),
                    rotationXDeg = 0f,
                    blurFraction = 0f,
                    scrimAlpha = 0.34f * (1f - t),
                    backdropBlurFraction = 0.85f * (1f - t),
                    ambientGlowAlpha = 0.75f * (1f - t),
                    haloProgress = 1f,
                    haloAlpha = 0f,
                )
            }
        }
    }
}

@Composable
internal fun Modifier.nearbyFlightBlur(
    blurFractionProvider: () -> Float,
    enabled: Boolean,
    maxBlurDp: Dp = 10.dp,
): Modifier = nearbyBackdropBlur(
    blurFractionProvider = blurFractionProvider,
    maxBlurDp = maxBlurDp,
    enabled = enabled,
    steps = 10,
)

/**
 * 预量化缓存的高斯模糊修饰符：
 * - 用于全屏底层页面实时虚化与卡片动态运动模糊；
 * - 仅在 graphicsLayer 阶段查表切换 RenderEffect，零组合期重组、零每帧对象分配；
 * - blurFraction <= 0 时自动置 renderEffect = null，不留存离屏层开销。
 */
@Composable
internal fun Modifier.nearbyBackdropBlur(
    blurFractionProvider: () -> Float,
    maxBlurDp: Dp = 20.dp,
    enabled: Boolean,
    steps: Int = 12,
): Modifier {
    val density = LocalDensity.current
    val effects = remember(density, enabled, maxBlurDp, steps) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || steps <= 0) {
            emptyList()
        } else {
            val cache = BlurRenderEffectCache()
            val maxPx = with(density) { maxBlurDp.toPx() }
            List<RenderEffect?>(steps + 1) { index ->
                if (index == 0) null else cache.resolve(maxPx * (index.toFloat() / steps.toFloat()))
            }
        }
    }
    return if (effects.isEmpty()) this else graphicsLayer {
        val index = (blurFractionProvider().coerceIn(0f, 1f) * steps).roundToInt().coerceIn(0, steps)
        renderEffect = effects[index]
    }
}

/**
 * 中央大卡片背后的无缝羽化封面氛围高斯光晕：
 * - 在全屏/大尺寸画布中留足四边模糊缓冲区，使用 Shader.TileMode.DECAL 向外自然衰减至透明；
 * - 叠加 Offscreen + BlendMode.DstIn 径向透明度羽化遮罩，彻底消除矩形硬边断层；
 * - 低版本或低模糊预算下平滑降级为主题强调色柔和径向光晕。
 */
@Composable
internal fun NearbyAmbientCoverGlow(
    coverUrl: String,
    accent: Color,
    blurEnabled: Boolean,
    alphaProvider: () -> Float,
    translationYProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val glowEffect = remember(density, blurEnabled) {
        if (!blurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else {
            val radiusPx = with(density) { 44.dp.toPx() }
            AndroidRenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                Shader.TileMode.DECAL,
            ).asComposeRenderEffect()
        }
    }

    // 占满足够大的缓冲区域，确保高斯卷积半径与径向羽化完全在画布内归零，不产生矩形裁切边。
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = alphaProvider().coerceIn(0f, 1f)
                translationY = translationYProvider() * 0.65f
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val radius = minOf(size.width, size.height) * 0.5f
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color.Black,
                        0.42f to Color.Black.copy(alpha = 0.82f),
                        0.72f to Color.Black.copy(alpha = 0.28f),
                        1.0f to Color.Transparent,
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = radius.coerceAtLeast(1f),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (glowEffect != null && coverUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { renderEffect = glowEffect }
                    .padding(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(48.dp)),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0f to accent.copy(alpha = 0.36f),
                            0.55f to accent.copy(alpha = 0.14f),
                            1f to Color.Transparent,
                        )
                    )
            )
        }
    }
}

/**
 * 干净轻薄的圆角扩散光环：
 * 随流转进度向外平稳扩张并柔和淡出，仅在 drawBehind 阶段读取状态。
 */
internal fun Modifier.nearbyCleanPulseRing(
    haloProgressProvider: () -> Float,
    haloAlphaProvider: () -> Float,
    accent: Color,
    highlight: Color,
    glassEnabled: Boolean,
    cornerRadiusDp: Dp = 20.dp,
): Modifier = drawBehind {
    val alpha = haloAlphaProvider().coerceIn(0f, 1f)
    if (alpha <= 0.01f) return@drawBehind
    val progress = haloProgressProvider().coerceIn(0f, 1f)
    val baseRadius = cornerRadiusDp.toPx()
    val expandPx = 18.dp.toPx() * progress
    val ringSize = Size(size.width + expandPx * 2f, size.height + expandPx * 2f)
    val ringOffset = Offset(-expandPx, -expandPx)
    val ringRadius = CornerRadius(baseRadius + expandPx * 0.45f, baseRadius + expandPx * 0.45f)

    drawRoundRect(
        color = accent.copy(alpha = 0.14f * alpha * (1f - progress * 0.45f)),
        topLeft = ringOffset,
        size = ringSize,
        cornerRadius = ringRadius,
        style = Stroke(width = 3.dp.toPx()),
    )
    drawRoundRect(
        color = highlight.copy(alpha = (if (glassEnabled) 0.46f else 0.24f) * alpha),
        topLeft = Offset(-0.75.dp.toPx(), -0.75.dp.toPx()),
        size = Size(size.width + 1.5.dp.toPx(), size.height + 1.5.dp.toPx()),
        cornerRadius = CornerRadius(baseRadius + 0.75.dp.toPx(), baseRadius + 0.75.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
}
