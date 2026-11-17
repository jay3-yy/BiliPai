package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.components.adaptivePreferenceGroupContainerColor
import com.android.purebilibili.core.ui.components.rememberAdaptiveListVisualCapabilities

internal class DonationDialogOrigin {
    var coordinates: LayoutCoordinates? = null
    var layer: GraphicsLayer? = null
    var color = Color.Transparent
    var bottomRadius = 0f
    var recorded = false

    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.let {
        Rect(it.localToScreen(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }
}

internal val LocalDonationDialogOrigin = staticCompositionLocalOf<DonationDialogOrigin?> { null }

/** 打赏是组内末行，只捕获这一行，顶部直角与组底部圆角分别保留。 */
@Composable
internal fun DonationDialogOrigin(content: @Composable () -> Unit) {
    val origin = LocalDonationDialogOrigin.current
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
    if (origin == null || !motionEnabled) { content(); return }
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val nativeGroup = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val radius = rememberAdaptiveListVisualCapabilities().componentSpec.groupCornerRadiusDp
    val color = if (nativeGroup) adaptivePreferenceGroupContainerColor() else AppSurfaceTokens.groupedListContainer()
    SideEffect {
        origin.layer = layer
        origin.color = color
        origin.bottomRadius = if (nativeGroup) with(density) { radius.dp.toPx() } else 0f
    }
    DisposableEffect(origin, layer) {
        onDispose {
            if (origin.layer === layer) {
                origin.coordinates = null
                origin.layer = null
                origin.recorded = false
            }
        }
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin.coordinates = it }.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        origin.recorded = true
        drawLayer(layer)
    }) { content() }
}

/** 圆角在两端缓入缓出，关闭直接反走同一条曲线，不在最后一帧换形状。 */
internal fun donationCornerRadius(start: Float, end: Float, progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    val fraction = t * t * (3f - 2f * t)
    return start + (end - start) * fraction
}

/** 页面保持最终布局尺寸，只有外壳裁剪、来源行与内容透明度随有限进度变化。 */
@Composable
internal fun DonationDialogContainer(
    origin: DonationDialogOrigin,
    progress: () -> Float,
    previewProgress: () -> Float,
    compactRadiusPx: Float,
    previewShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val path = remember { Path() }
    val paint = remember { Paint() }
    val latestProgress by rememberUpdatedState(progress)
    val interactive by remember(progress) { derivedStateOf { progress() >= 1f } }
    Box(modifier
        .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (latestProgress() < 1f) event.changes.forEach { it.consume() }
                }
            }
        }
        .onGloballyPositioned { coordinates[0] = it }.drawWithContent {
        val t = progress().coerceIn(0f, 1f)
        val destination = Rect(Offset.Zero, size)
        val sourceBounds = origin.bounds()
        val host = coordinates[0]?.takeIf { it.isAttached }?.localToScreen(Offset.Zero)
        val source = if (sourceBounds != null && host != null && origin.recorded) sourceBounds.translate(-host) else null
        val frame = if (source != null) androidx.compose.ui.geometry.lerp(source, destination, t) else destination
        val preview = previewProgress().coerceIn(0f, 1f)
        val full = when (val outline = previewShape.createOutline(size, layoutDirection, this)) {
            is Outline.Rounded -> outline.roundRect
            else -> RoundRect(destination, CornerRadius.Zero)
        }
        fun radius(fullRadius: CornerRadius, start: Float): CornerRadius {
            val target = donationCornerRadius(compactRadiusPx, fullRadius.x, preview)
            val value = donationCornerRadius(start, target, t).coerceAtMost(minOf(frame.width, frame.height) / 2f)
            return CornerRadius(value)
        }
        path.rewind()
        path.addRoundRect(RoundRect(
            frame,
            radius(full.topLeftCornerRadius, 0f), radius(full.topRightCornerRadius, 0f),
            radius(full.bottomRightCornerRadius, origin.bottomRadius), radius(full.bottomLeftCornerRadius, origin.bottomRadius),
        ))
        clipPath(path) {
            // 来源底板只负责交接，结束时完全撤掉，保留玻璃材质自身的透明度。
            if (t < 1f) drawRect(origin.color.copy(alpha = origin.color.alpha * (1f - t)), frame.topLeft, frame.size)
            val sourceAlpha = if (source == null) 0f else (1f - t * 2f).coerceIn(0f, 1f)
            val layer = origin.layer
            if (sourceAlpha > 0f && layer != null && source != null) {
                drawContext.canvas.saveLayer(frame, paint.apply { alpha = sourceAlpha })
                translate(frame.left, frame.top) {
                    scale(frame.width / source.width, frame.height / source.height, Offset.Zero) { drawLayer(layer) }
                }
                drawContext.canvas.restore()
            }
            val contentAlpha = if (source == null) t else ((t - 0.15f) / 0.85f).coerceIn(0f, 1f)
            if (contentAlpha > 0f) {
                if (contentAlpha < 1f) drawContext.canvas.saveLayer(frame, paint.apply { alpha = contentAlpha })
                this@drawWithContent.drawContent()
                if (contentAlpha < 1f) drawContext.canvas.restore()
            }
        }
    }.graphicsLayer()) { content() }
}

/** 将二维码预览圆角的重组限制在材质外壳，不让整个弹窗订阅逐帧进度。 */
@Composable
internal fun DonationPopupSurface(
    previewProgress: () -> Float,
    compactRadius: Dp,
    previewShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val fraction = previewProgress().coerceIn(0f, 1f)
    val shape = with(density) {
        val start = compactRadius.toPx()
        fun corner(size: androidx.compose.foundation.shape.CornerSize) =
            donationCornerRadius(start, size.toPx(Size.Zero, this), fraction).toDp()
        RoundedCornerShape(
            topStart = corner(previewShape.topStart), topEnd = corner(previewShape.topEnd),
            bottomEnd = corner(previewShape.bottomEnd), bottomStart = corner(previewShape.bottomStart),
        )
    }
    AppPopupSurface(
        type = AppPopupSurfaceType.DIALOG,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        content = content,
    )
}
