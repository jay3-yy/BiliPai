/*
 * Motion adapted from Kyant's AndroidLiquidGlass LiquidButton / InteractiveHighlight.
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Modified for BiliPai: keep page-safe backdrop rendering separate, and use the
 * visible button size when its touch target is larger than the glass surface.
 */
package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import android.os.Build
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private object ElasticPressMarker : Modifier.Element

/** A moving capsule can suppress duplicate deformation on its child click targets. */
val LocalElasticPressEnabled = compositionLocalOf { true }

/** Shared gesture state; glass renderers use [layerBlock] to inverse-transform backdrop sampling. */
class AppElasticPressState internal constructor(
    internal val motionEnabled: Boolean,
    private val visibleSizePx: Float?,
    private val expansionPx: Float,
    private val feedbackStrength: Float,
    private val sizeScaledExpansion: Boolean = false,
) {
    internal val press = Animatable(0f, visibilityThreshold = 0.001f)
    internal val position = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    internal var startPosition by mutableStateOf(Offset.Zero)
    internal var gestureSize = Size.Zero
    private val highlight = AppPressHighlight()

    /** Call inside a glass renderer's surface layer so lighting shares its transform. */
    fun drawHighlight(scope: DrawScope) {
        if (motionEnabled) {
            val point = position.value
            val mappedPoint = if (gestureSize.width > 0f && gestureSize.height > 0f) {
                Offset(point.x * scope.size.width / gestureSize.width, point.y * scope.size.height / gestureSize.height)
            } else point
            highlight.draw(scope, press.value, mappedPoint)
        }
    }

    private fun transformFor(size: Size): AppElasticTransform {
        val width = visibleSizePx ?: gestureSize.width.takeIf { it > 0f } ?: size.width
        val height = visibleSizePx ?: gestureSize.height.takeIf { it > 0f } ?: size.height
        val expansion = if (sizeScaledExpansion) {
            appPopupElasticExpansionPx(minOf(width, height), expansionPx)
        } else expansionPx
        return resolveAppElasticTransform(
            Size(width, height), press.value, position.value - startPosition, expansion, feedbackStrength,
            normalizeToShortSide = sizeScaledExpansion,
            dragReferencePx = if (sizeScaledExpansion && feedbackStrength > 0f) {
                expansionPx / feedbackStrength / 4f * APP_POPUP_ELASTIC_DRAG_REFERENCE_DP
            } else null,
        )
    }

    /** 供相邻玻璃的联合轮廓使用，与当前控件实际绘制的缩放和位移同源。 */
    fun transformedBounds(bounds: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect {
        if (!motionEnabled) return bounds
        val transform = transformFor(bounds.size)
        val center = bounds.center + transform.translation
        val halfWidth = bounds.width * transform.scaleX / 2f
        val halfHeight = bounds.height * transform.scaleY / 2f
        return androidx.compose.ui.geometry.Rect(center.x - halfWidth, center.y - halfHeight,
            center.x + halfWidth, center.y + halfHeight)
    }

    val layerBlock: GraphicsLayerScope.() -> Unit = {
        if (motionEnabled) {
            val transform = transformFor(size)
            translationX = transform.translation.x
            translationY = transform.translation.y
            scaleX = transform.scaleX
            scaleY = transform.scaleY
        }
    }

}

@Composable
fun rememberAppElasticPressState(
    visualSize: Dp? = null,
    feedbackStrength: Float = 1f,
    sizeScaledExpansion: Boolean = false,
): AppElasticPressState {
    val density = LocalDensity.current
    val motionEnabled = LocalComponentMotionEnabled.current &&
        com.android.purebilibili.core.ui.blur.LocalAppGlassContainer.current?.ownsDeformation != true
    val visibleSizePx = visualSize?.let { with(density) { it.toPx() } }
    val strength = feedbackStrength.coerceIn(0f, 1f)
    val expansionPx = with(density) { 4.dp.toPx() } * strength
    return remember(motionEnabled, visibleSizePx, expansionPx, strength, sizeScaledExpansion) {
        AppElasticPressState(motionEnabled, visibleSizePx, expansionPx, strength, sizeScaledExpansion)
    }
}

/** Wide search capsules use the same restrained gain as dock shells. */
const val APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH = 0.6f

/** Native editing/selection and parent scrolling retain gesture ownership. */
fun Modifier.appSearchElasticPress(
    enabled: Boolean = true,
    highlightShape: Shape = androidx.compose.foundation.shape.CircleShape,
): Modifier = composed {
    appElasticPress(
        enabled = enabled,
        state = rememberAppElasticPressState(feedbackStrength = APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH),
        highlightShape = highlightShape,
    )
}

/**
 * Kyant's elastic button motion. Does not add clicks, consume gestures, or sample backgrounds.
 * Wraps the complete modifier chain so existing surfaces and clips deform too; native clicks and long
 * presses keep ownership. Parent scrolling cancels the feedback. Disabled controls and the
 * app's reduced-motion setting opt out. Repeated applications on one chain do not stack.
 * [visualSize] overrides geometry for a square surface inside a larger touch target.
 * With [transformInBackdrop], callers must pass [state] and render its layerBlock in drawBackdrop.
 * That renderer transforms its whole subtree; descendants must not repeat the same transform.
 * Sibling content layers may share the state and its observed control geometry.
 */
fun Modifier.appElasticPress(
    enabled: Boolean = true,
    visualSize: Dp? = null,
    dragEnabled: Boolean = true,
    state: AppElasticPressState? = null,
    transformInBackdrop: Boolean = false,
    highlightShape: Shape = RoundedCornerShape(50),
    /** 大面积弹窗可只保留形变；控件自身仍使用局部光照。 */
    highlightEnabled: Boolean = true,
    /** Only true while a caller-owned gesture has consumed the movement. */
    allowConsumedDrag: () -> Boolean = { false },
    /** 退出接管几何时冻结当前反馈，避免再叠加一次松手回弹。 */
    freezeFeedback: () -> Boolean = { false },
): Modifier {
    if (foldIn(false) { found, element -> found || element === ElasticPressMarker }) return this
    return Modifier.then(ElasticPressMarker).composed {
        if (!enabled || !LocalComponentMotionEnabled.current || !LocalElasticPressEnabled.current ||
            com.android.purebilibili.core.ui.blur.LocalAppGlassContainer.current?.ownsDeformation == true
        ) return@composed this
        val elasticState = state ?: rememberAppElasticPressState(visualSize)
        val effectiveDragEnabled = dragEnabled && LocalAppThemeConfig.current.globalDragFeedbackEnabled
        val press = elasticState.press
        val position = elasticState.position
        val scope = rememberCoroutineScope()
        var pressJob by remember { mutableStateOf<Job?>(null) }
        var positionJob by remember { mutableStateOf<Job?>(null) }

        fun release() {
            pressJob?.cancel()
            positionJob?.cancel()
            if (freezeFeedback()) return
            pressJob = scope.launch {
                press.animateTo(0f, appPressHighlightPressSpec())
            }
            positionJob = scope.launch {
                position.animateTo(elasticState.startPosition, appPressHighlightPositionSpec())
            }
        }

        this
            .pointerInput(elasticState, effectiveDragEnabled) {
                // Pointer-down starts feedback; Final-pass observation gives native business
                // gestures first refusal. Multi-touch cancels feedback instead of changing fingers.
                awaitEachGesture {
                    val down = awaitFirstDown(false, PointerEventPass.Initial)
                    if (freezeFeedback()) return@awaitEachGesture
                    pressJob?.cancel()
                    positionJob?.cancel()
                    elasticState.gestureSize = Size(size.width.toFloat(), size.height.toFloat())
                    elasticState.startPosition = down.position
                    pressJob = scope.launch {
                        press.animateTo(1f, appPressHighlightPressSpec())
                    }
                    positionJob = scope.launch { position.snapTo(down.position) }
                    val pointerId = down.id
                    try {
                        while (true) {
                            // Observe after click/scroll/zoom recognizers, never compete with them.
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed || freezeFeedback()) break
                            if (change.position != change.previousPosition) {
                                if (change.isConsumed && !allowConsumedDrag()) break
                                if (effectiveDragEnabled) {
                                    positionJob?.cancel()
                                    positionJob = scope.launch { position.snapTo(change.position) }
                                }
                            }
                        }
                    } finally {
                        release()
                    }
                }
            }
            .then(
                // drawBackdrop must own the transform so its sampling can undo it.
                // An ancestor graphicsLayer only stretches a previously rendered glass texture.
                if (transformInBackdrop) Modifier else Modifier
                    .graphicsLayer(elasticState.layerBlock)
                    .drawWithCache {
                        // Geometry is shared by press frames; resize, density, direction and shape
                        // changes rebuild the cache. Idle controls do not allocate a clip path.
                        val outline = highlightShape.createOutline(size, layoutDirection, this)
                        var highlightPath: Path? = null
                        onDrawWithContent {
                            drawContent()
                            if (highlightEnabled && press.value > 0f && size.minDimension > 0f) {
                                val path = highlightPath ?: Path().apply {
                                    addOutline(outline)
                                }.also { highlightPath = it }
                                // Clip only the light, preserving the content and backdrop transform.
                                clipPath(path) { elasticState.drawHighlight(this) }
                            }
                        }
                    }
            )
    }.then(this)
}

/** 共享按压叠光与指尖位置渐变，供控件表面和标签捕获层使用。 */
class AppPressHighlight {
    private val shader = if (Build.VERSION.SDK_INT >= 33) PressShader() else null

    fun draw(scope: DrawScope, progress: Float, position: Offset) = with(scope) {
        val amount = progress.coerceIn(0f, 1f)
        if (amount <= 0f || size.minDimension <= 0f) return@with
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            drawRect(Color.White.copy(alpha = 0.08f * amount), blendMode = BlendMode.Plus)
            shader.draw(this, amount, position)
        } else {
            drawRect(Color.White.copy(alpha = 0.25f * amount), blendMode = BlendMode.Plus)
        }
    }
}

@RequiresApi(33)
private class PressShader {
    private val shader = RuntimeShader("""
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;
        half4 main(float2 coord) {
            float dist = distance(coord, position);
            return color * smoothstep(radius, radius * 0.5, dist);
        }
    """.trimIndent())
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, progress: Float, position: Offset) = with(scope) {
        shader.setColorUniform("color", android.graphics.Color.argb(0.15f * progress, 1f, 1f, 1f))
        shader.setFloatUniform("radius", size.minDimension * 1.5f)
        shader.setFloatUniform("position", position.x.coerceIn(0f, size.width), position.y.coerceIn(0f, size.height))
        drawRect(brush, blendMode = BlendMode.Plus)
    }
}
