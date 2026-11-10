package com.android.purebilibili.feature.home.components.miuix

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    /** A dock can use its existing press spring for drag, tap and pager feedback. */
    val pressProgressProvider: (() -> Float)? = null,
    /** Optional motion light, combined with the pointer light without replacing its lifetime. */
    val motionProgressProvider: (() -> Float)? = null,
) {

    private val pressProgressAnimationSpec = interactiveHighlightPressSpec()
    private val positionAnimationSpec = interactiveHighlightPositionSpec()

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    private var requestedPosition = Offset.Zero
    val currentPosition: Offset get() = requestedPosition
    val offset: Offset get() = positionAnimation.value - startPosition

    private var pressJob: Job? = null
    private var positionJob: Job? = null

    fun setPressed(pressed: Boolean) {
        if (pressProgressProvider != null) return
        pressJob?.cancel()
        pressJob = animationScope.launch {
            pressProgressAnimation.animateTo(
                if (pressed) 1f else 0f,
                pressProgressAnimationSpec,
            )
        }
    }

    /** Read in draw so non-pointer selection light shares the capsule's current geometry. */
    var drawPositionProvider: ((Size) -> Offset?)? = null

    private val highlight = com.android.purebilibili.core.ui.components.AppPressHighlight()

    val modifier: Modifier = Modifier.drawWithContent {
        drawContent()
        drawHighlight()
    }

    /** 玻璃捕获层先照亮材质，再绘制选中内容，避免加亮混合改变图标与文字的主题色。 */
    val backgroundModifier: Modifier = Modifier.drawBehind {
        drawHighlight()
    }

    private fun DrawScope.drawHighlight() {
        val pill = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.minDimension / 2f)))
        }
        clipPath(pill) {
            highlight.draw(
                this,
                maxOf(
                    pressProgressProvider?.invoke() ?: pressProgressAnimation.value,
                    motionProgressProvider?.invoke() ?: 0f,
                ),
                drawPositionProvider?.invoke(size) ?: position(size, positionAnimation.value),
            )
        }
    }

    /** Driven by the indicator's existing pointer owner; never installs another recognizer. */
    fun start(point: Offset) {
        startPosition = point
        updatePosition(point)
        setPressed(true)
    }

    fun updatePosition(point: Offset) {
        requestedPosition = point
        positionJob?.cancel()
        positionJob = animationScope.launch { positionAnimation.snapTo(point) }
    }

    fun release() {
        setPressed(false)
        positionJob?.cancel()
        positionJob = animationScope.launch {
            positionAnimation.animateTo(startPosition, positionAnimationSpec)
        }
    }
    /** Compatibility observer for side rails without an indicator feedback callback. */
    val gestureModifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            onDragStart = { start(it.position) },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ -> updatePosition(change.position) }
    }

}
