package com.android.purebilibili.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import kotlin.math.roundToInt

private class DonationQrAnchors {
    var host: LayoutCoordinates? = null
    var compact: LayoutCoordinates? = null
    var preview: LayoutCoordinates? = null

    fun imageBounds(image: LayoutCoordinates?): Rect? {
        val container = host?.takeIf { it.isAttached } ?: return null
        val coordinates = image?.takeIf { it.isAttached } ?: return null
        val offset = container.localPositionOf(coordinates, Offset.Zero)
        // 两端均为 ContentScale.Fit；预览的图像槽位可能是长方形。
        val side = minOf(coordinates.size.width, coordinates.size.height).toFloat()
        return Rect(offset + Offset((coordinates.size.width - side) / 2, (coordinates.size.height - side) / 2), Size(side, side))
    }
}

/** 两端内容按最终尺寸测量，同一外壳连续形变；过渡中的二维码只绘制一次。 */
@Composable
internal fun DonationPreviewContainer(
    progress: () -> Float,
    previewActive: Boolean,
    compactMaxWidth: Dp,
    modifier: Modifier = Modifier,
    compact: @Composable (qrModifier: Modifier) -> Unit,
    preview: @Composable (qrModifier: Modifier) -> Unit,
) {
    val anchors = remember { DonationQrAnchors() }
    val painter = painterResource(donationQrResource())
    val qrPath = remember { Path() }
    val compactQrCorner = AppShapes.containerCornerDp(ContainerLevel.Card)
    val compactBlur = remember { BlurRenderEffectCache() }
    val previewBlur = remember { BlurRenderEffectCache() }
    val textBlurEnabled = LocalComponentMotionEnabled.current &&
        !rememberSystemReduceMotion() && !isLowBlurBudgetForced()
    fun qrModifier(preview: Boolean) = Modifier
        .onPlaced { if (preview) anchors.preview = it else anchors.compact = it }
        .graphicsLayer { alpha = if (progress() > 0f && progress() < 1f) 0f else 1f }
    Layout(
        modifier = modifier.onPlaced { anchors.host = it },
        content = {
            Box(Modifier
                .graphicsLayer {
                    alpha = (1f - progress() * 2f).coerceIn(0f, 1f)
                    // 文案与操作控件边退场边模糊；共享二维码在独立绘制层保持清晰。
                    val radius = if (textBlurEnabled && alpha > 0f && alpha < 1f) (1f - alpha) * 12.dp.toPx() else 0f
                    renderEffect = compactBlur.resolve((radius * 2f).roundToInt() / 2f)
                }
                .then(if (previewActive) Modifier.clearAndSetSemantics { } else Modifier)) {
                compact(qrModifier(false))
            }
            Box(Modifier
                .graphicsLayer {
                    alpha = ((progress() - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    // 预测返回沿相同进度反向运行；静止或不可见时移除实时模糊。
                    val radius = if (textBlurEnabled && alpha > 0f && alpha < 1f) (1f - alpha) * 12.dp.toPx() else 0f
                    renderEffect = previewBlur.resolve((radius * 2f).roundToInt() / 2f)
                }
                .then(if (!previewActive) Modifier.clearAndSetSemantics { } else Modifier)) {
                preview(qrModifier(true))
            }
            Canvas(Modifier.clearAndSetSemantics { }) {
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction > 0f && fraction < 1f) {
                    val start = anchors.imageBounds(anchors.compact)
                    val end = anchors.imageBounds(anchors.preview)
                    if (start != null && end != null) {
                        val bounds = lerp(start, end, fraction)
                        val radius = donationCornerRadius(compactQrCorner.toPx(), 0f, fraction)
                            .coerceAtMost(bounds.width / 2f)
                        qrPath.rewind()
                        qrPath.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
                        clipPath(qrPath) {
                            translate(bounds.left, bounds.top) { with(painter) { draw(bounds.size) } }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val compactWidth = minOf(width, compactMaxWidth.roundToPx())
        val card = measurables[0].measure(Constraints(minWidth = compactWidth, maxWidth = compactWidth, maxHeight = height))
        val full = measurables[1].measure(Constraints.fixed(width, height))
        val frame = donationPreviewContainerSize(IntSize(card.width, card.height), IntSize(full.width, full.height), progress())
        val sharedQr = measurables[2].measure(Constraints.fixed(frame.width, frame.height))
        layout(frame.width, frame.height) {
            fun placeCard() = card.placeRelative((frame.width - card.width) / 2, (frame.height - card.height) / 2)
            fun placePreview() = full.placeRelative((frame.width - full.width) / 2, (frame.height - full.height) / 2)
            // 仅当前内容位于命中测试顶层；透明端点仍保留测量、滚动及二维码锚点。
            if (previewActive) { placeCard(); placePreview() } else { placePreview(); placeCard() }
            sharedQr.placeRelative(0, 0)
        }
    }
}
