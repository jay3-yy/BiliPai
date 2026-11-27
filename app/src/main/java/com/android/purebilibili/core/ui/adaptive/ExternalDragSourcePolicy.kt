package com.android.purebilibili.core.ui.adaptive

import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.android.purebilibili.core.util.AppWindowAdaptiveInfo
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import kotlin.math.abs

private const val DRAG_SHARE_TITLE_MAX_LENGTH = 48
internal val INTERNAL_EXTERNAL_DRAG_LOCAL_STATE = Any()

internal fun buildVideoDragShareText(
    bvid: String,
    title: String = "",
): String? {
    val cleanBvid = bvid.trim()
    if (cleanBvid.isEmpty()) return null
    val url = "https://www.bilibili.com/video/$cleanBvid"
    val cleanTitle = title.trim().replace(Regex("\\s+"), " ").take(DRAG_SHARE_TITLE_MAX_LENGTH)
    return if (cleanTitle.isEmpty()) url else "$cleanTitle $url"
}

internal fun buildDynamicDragShareText(
    dynamicId: String,
    summary: String = "",
): String? {
    val cleanId = dynamicId.trim()
    if (cleanId.isEmpty()) return null
    val url = "https://t.bilibili.com/$cleanId"
    val cleanSummary = summary.trim().replace(Regex("\\s+"), " ").take(DRAG_SHARE_TITLE_MAX_LENGTH)
    return if (cleanSummary.isEmpty()) url else "$cleanSummary $url"
}

internal fun shouldEnableExternalDragSource(
    adaptiveInfo: AppWindowAdaptiveInfo,
): Boolean = adaptiveInfo.precisePointerConnected

/**
 * 仅在水平位移明显占主导且超过 1.5 倍 touchSlop 时发起跨应用拖出，
 * 避免鼠标左键上下拖拽滚动列表时误触发系统拖拽。
 */
internal fun shouldStartExternalDrag(
    dx: Float,
    dy: Float,
    touchSlop: Float,
): Boolean {
    val absDx = abs(dx)
    val absDy = abs(dy)
    return absDx >= touchSlop * 1.5f && absDx > absDy * 1.25f
}

/**
 * 鼠标或手写笔主键拖动超过 touchSlop 时发起系统级跨应用文本拖拽。
 * 不拦截触屏点击、列表滚动或长按上下文菜单。
 */
@Composable
internal fun Modifier.adaptiveExternalTextDragSource(
    label: String,
    textProvider: () -> String?,
    previewTitleProvider: () -> String = { label },
    enabled: Boolean = shouldEnableExternalDragSource(LocalAppWindowAdaptiveInfo.current),
): Modifier {
    if (!enabled) return this
    val view = LocalView.current
    val currentLabel by rememberUpdatedState(label)
    val currentTextProvider by rememberUpdatedState(textProvider)
    val currentPreviewTitleProvider by rememberUpdatedState(previewTitleProvider)
    return pointerInput(view) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse && down.type != PointerType.Stylus) return@awaitEachGesture
            val downPosition = down.position
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (down.type == PointerType.Mouse && !event.buttons.isPrimaryPressed) break
                val dx = change.position.x - downPosition.x
                val dy = change.position.y - downPosition.y
                if (shouldStartExternalDrag(dx = dx, dy = dy, touchSlop = touchSlop)) {
                    val shareText = currentTextProvider()?.trim()?.takeIf { it.isNotEmpty() } ?: break
                    change.consume()
                    val clipData = ClipData.newPlainText(currentLabel, shareText)
                    val shadow = PillDragShadowBuilder(
                        density = view.resources.displayMetrics.density,
                        text = currentPreviewTitleProvider().ifBlank { shareText },
                    )
                    view.startDragAndDrop(
                        clipData,
                        shadow,
                        INTERNAL_EXTERNAL_DRAG_LOCAL_STATE,
                        View.DRAG_FLAG_GLOBAL,
                    )
                    break
                }
            }
        }
    }
}

private class PillDragShadowBuilder(
    private val density: Float,
    text: String,
) : View.DragShadowBuilder() {
    private val displayText = text.trim().replace(Regex("\\s+"), " ").take(28)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6222429.toInt()
    }
    private val horizontalPadding = 14f * density
    private val pillHeight = (36f * density).toInt()
    private val pillWidth = (textPaint.measureText(displayText) + horizontalPadding * 2f)
        .toInt()
        .coerceIn((96f * density).toInt(), (240f * density).toInt())

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(pillWidth, pillHeight)
        outShadowTouchPoint.set(pillWidth / 2, pillHeight / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = pillHeight / 2f
        canvas.drawRoundRect(RectF(0f, 0f, pillWidth.toFloat(), pillHeight.toFloat()), radius, radius, backgroundPaint)
        val fm = textPaint.fontMetrics
        val baseline = (pillHeight - fm.ascent - fm.descent) / 2f
        canvas.drawText(displayText, horizontalPadding, baseline, textPaint)
    }
}
