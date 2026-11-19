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
    var showingInDialog by mutableStateOf(false)

    fun capture(): DonationDialogSource? {
        val layout = coordinates?.takeIf { it.isAttached } ?: return null
        val snapshot = layer?.takeIf { recorded && it.size.width > 0 && it.size.height > 0 } ?: return null
        val topLeft = layout.localToScreen(Offset.Zero)
        val bottomRight = layout.localToScreen(Offset(layout.size.width.toFloat(), layout.size.height.toFloat()))
        if (!topLeft.isFinite || !bottomRight.isFinite) return null
        val bounds = Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        if (bounds.isEmpty) return null
        return DonationDialogSource(bounds, snapshot, color, bottomRadius)
    }
}

internal data class DonationDialogSource(
    val bounds: Rect,
    val layer: GraphicsLayer,
    val color: Color,
    val bottomRadius: Float,
)

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
        if (!origin.showingInDialog) {
            layer.record { this@drawWithContent.drawContent() }
            origin.recorded = true
            drawLayer(layer)
        }
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
    onPositioned: () -> Unit,
    progress: () -> Float,
    previewProgress: () -> Float,
    compactRadiusPx: Float,
    previewShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var positioned by remember { mutableStateOf(false) }
    var capturedSource by remember { mutableStateOf<DonationDialogSource?>(null) }
    val path = remember { Path() }
    val paint = remember { Paint() }
    val latestProgress by rememberUpdatedState(progress)
    val interactive by remember(progress) { derivedStateOf { progress() >= 1f } }
    DisposableEffect(origin, capturedSource?.layer) {
        origin.showingInDialog = capturedSource != null
        onDispose { origin.showingInDialog = false }
    }
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
        .onGloballyPositioned {
            coordinates[0] = it
            if (!positioned) {
                capturedSource = origin.capture()
                positioned = true
                onPositioned()
            }
        }.drawWithContent {
        val t = progress().coerceIn(0f, 1f)
        if (t >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val destination = Rect(Offset.Zero, size)
        val host = coordinates[0]?.takeIf { it.isAttached }?.localToScreen(Offset.Zero)
        val anchor = capturedSource?.takeIf { host != null && it.layer === origin.layer }
        val source = anchor?.bounds?.translate(-(host ?: Offset.Zero))
        val frame = if (source != null) lerp(source, destination, t) else destination
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
            radius(full.bottomRightCornerRadius, anchor?.bottomRadius ?: 0f),
            radius(full.bottomLeftCornerRadius, anchor?.bottomRadius ?: 0f),
        ))
        clipPath(path) {
            val contentAlpha = if (anchor == null) t else donationContentHandoff(t)
            if (anchor != null && source != null && contentAlpha < 1f) {
                // 图标与文字以统一比例跟随中心，横屏由宽行收窄时也不会拉伸。
                val layer = anchor.layer
                val rowScale = minOf(frame.width, source.width) / layer.size.width
                val rowSize = Size(layer.size.width * rowScale, layer.size.height * rowScale)
                drawRect(
                    anchor.color.copy(alpha = anchor.color.alpha * (1f - contentAlpha)),
                    frame.topLeft,
                    frame.size,
                )
                if (contentAlpha > 0f) drawContext.canvas.saveLayer(frame, paint.apply { alpha = 1f - contentAlpha })
                val rowTopLeft = frame.center - Offset(rowSize.width / 2f, rowSize.height / 2f)
                translate(rowTopLeft.x, rowTopLeft.y) {
                    scale(rowScale, rowScale, Offset.Zero) { drawLayer(layer) }
                }
                if (contentAlpha > 0f) drawContext.canvas.restore()
            }
            if (contentAlpha > 0f) {
                if (contentAlpha < 1f) drawContext.canvas.saveLayer(frame, paint.apply { alpha = contentAlpha })
                // 正文与材质沿外壳移动，内层保持最终布局，二维码始终等比。
                val panelScale = if (source != null) {
                    maxOf(frame.width / destination.width, frame.height / destination.height, 0.9f)
                } else 1f
                val shift = frame.center - destination.center
                translate(shift.x, shift.y) {
                    scale(panelScale, panelScale, destination.center) { this@drawWithContent.drawContent() }
                }
                if (contentAlpha < 1f) drawContext.canvas.restore()
            }
        }
    }.graphicsLayer()) { content() }
}

internal fun donationContentHandoff(progress: Float): Float =
    donationCornerRadius(0f, 1f, (progress - 0.12f) / 0.68f)

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
