package com.android.purebilibili.core.ui.animation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.animation.gl.ThanosEffectView
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

// ==================== iOS 风格抖动效果系统 ====================

object DissolveAnimationManager {
    private val _isAnyCardDissolving = mutableStateOf(false)
    val isAnyCardDissolving: State<Boolean> = _isAnyCardDissolving
    
    private val _dissolvingCardId = mutableStateOf<String?>(null)
    val dissolvingCardId: State<String?> = _dissolvingCardId
    
    fun startDissolving(cardId: String) {
        _dissolvingCardId.value = cardId
        _isAnyCardDissolving.value = true
    }
    
    fun stopDissolving() {
        _dissolvingCardId.value = null
        _isAnyCardDissolving.value = false
    }
}

enum class DissolveAnimationPreset {
    CLASSIC,
    TELEGRAM_FAST
}

internal fun shouldWrapWithDissolveAnimation(isDissolving: Boolean): Boolean {
    return isDissolving
}

internal fun shouldJiggleOnDissolve(
    enabled: Boolean,
    isAnyCardDissolving: Boolean,
    dissolvingCardId: String?,
    cardId: String,
    isCurrentCardDissolving: Boolean
): Boolean {
    if (!enabled || isCurrentCardDissolving) return false
    return isAnyCardDissolving && dissolvingCardId != cardId
}

internal fun shouldPublishGlobalDissolveState(
    publishGlobalState: Boolean
): Boolean {
    return publishGlobalState
}

internal fun shouldCreateDissolveBitmap(
    width: Int,
    height: Int
): Boolean {
    return width > 0 && height > 0
}

internal fun shouldDispatchDissolveCompletion(
    hasCompletedCurrentDissolve: Boolean
): Boolean {
    return !hasCompletedCurrentDissolve
}

@Composable
fun Modifier.jiggleOnDissolve(
    cardId: String,
    enabled: Boolean = true,
    isCurrentCardDissolving: Boolean = false
): Modifier {
    // 🚀 [性能优化] 提前检查是否需要抖动，避免不必要的状态读取和动画创建
    if (!enabled) return this
    
    val isDissolving by DissolveAnimationManager.isAnyCardDissolving
    val dissolvingId by DissolveAnimationManager.dissolvingCardId
    val shouldJiggle = shouldJiggleOnDissolve(
        enabled = enabled,
        isAnyCardDissolving = isDissolving,
        dissolvingCardId = dissolvingId,
        cardId = cardId,
        isCurrentCardDissolving = isCurrentCardDissolving
    )
    
    // 🚀 [关键优化] 不抖动时直接返回，不创建任何动画对象
    if (!shouldJiggle) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jiggleRotation"
    )
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(60, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jiggleOffset"
    )
    
    return this.graphicsLayer {
        rotationZ = rotation
        translationX = offsetX
    }
}

// ==================== Particle dissolve effect ====================

@Composable
fun DissolvableVideoCard(
    isDissolving: Boolean,
    onDissolveComplete: () -> Unit,
    modifier: Modifier = Modifier,
    cardId: String = "",
    preset: DissolveAnimationPreset = DissolveAnimationPreset.CLASSIC,
    collapseAfterDissolve: Boolean = true,
    publishGlobalDissolveState: Boolean = true,
    keepInvisibleAfterDissolve: Boolean = false,
    reflowDuringFinalTail: Boolean = false,
    onReflowStarted: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val completionHandler = remember { Handler(Looper.getMainLooper()) }
    val contentLayer = rememberGraphicsLayer()
    var cardSize by remember(cardId) { mutableStateOf(IntSize.Zero) }
    var cardWindowBounds by remember(cardId) { mutableStateOf<RectF?>(null) }
    var hasRecordedContent by remember(cardId) { mutableStateOf(false) }
    var shouldCollapse by remember(cardId) { mutableStateOf(false) }
    var keepContentHidden by remember(cardId) { mutableStateOf(false) }
    var hasCompletedCurrentDissolve by remember(cardId) { mutableStateOf(false) }
    var effectView by remember(cardId) { mutableStateOf<ThanosEffectView?>(null) }
    val latestOnComplete by rememberUpdatedState(onDissolveComplete)
    val latestOnReflowStarted by rememberUpdatedState(onReflowStarted)
    val latestIsDissolving by rememberUpdatedState(isDissolving)
    val publishGlobalState = shouldPublishGlobalDissolveState(publishGlobalDissolveState)

    fun dispatchCompletionOnce() {
        if (!shouldDispatchDissolveCompletion(hasCompletedCurrentDissolve)) return
        hasCompletedCurrentDissolve = true
        if (publishGlobalState && DissolveAnimationManager.dissolvingCardId.value == cardId) {
            DissolveAnimationManager.stopDissolving()
        }
        latestOnComplete()
    }

    fun beginCollapse() {
        if (hasCompletedCurrentDissolve || shouldCollapse) return
        keepContentHidden = true
        shouldCollapse = true
        latestOnReflowStarted()
    }

    fun finishEffect() {
        effectView?.dispose()
        effectView = null
        if (hasCompletedCurrentDissolve || shouldCollapse) return
        if (collapseAfterDissolve) {
            beginCollapse()
        } else {
            keepContentHidden = keepInvisibleAfterDissolve
            dispatchCompletionOnce()
        }
    }

    val collapseSpec: AnimationSpec<Float> = remember(preset, reflowDuringFinalTail) {
        when (preset) {
            DissolveAnimationPreset.CLASSIC -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
            DissolveAnimationPreset.TELEGRAM_FAST -> if (reflowDuringFinalTail) {
                tween(240, easing = AppMotionEasing.M1Decelerate)
            } else {
                tween(135, easing = AppMotionEasing.M1Standard)
            }
        }
    }
    val heightMultiplier = animateFloatAsState(
        targetValue = if (shouldCollapse) 0f else 1f,
        animationSpec = collapseSpec,
        label = "heightCollapse",
        finishedListener = {
            if (shouldCollapse && collapseAfterDissolve) dispatchCompletionOnce()
        }
    )

    LaunchedEffect(isDissolving, cardId) {
        if (!isDissolving) {
            effectView?.dispose()
            effectView = null
            shouldCollapse = false
            keepContentHidden = false
            return@LaunchedEffect
        }
        hasCompletedCurrentDissolve = false
        val window = findWindow(context)
        if (window == null || !isThanosEffectSupported(context)) {
            finishEffect()
            return@LaunchedEffect
        }
        val ready = withTimeoutOrNull(500L) {
            snapshotFlow { hasRecordedContent && cardWindowBounds != null }
                .first { it }
        }
        if (ready != true) {
            finishEffect()
            return@LaunchedEffect
        }
        // Capture the actual card subtree, including its transparent corners, without
        // the window background or action sheet. This replaces upstream View.draw(Canvas).
        val bitmap: Bitmap? = try {
            withTimeoutOrNull(500L) {
                contentLayer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: RuntimeException) {
            android.util.Log.w("ThanosEffect", "Cannot capture video card", error)
            null
        }
        val bounds = cardWindowBounds
        if (bitmap == null || bounds == null) {
            bitmap?.recycle()
            finishEffect()
            return@LaunchedEffect
        }
        effectView = ThanosEffectView.attach(
            window = window,
            bitmap = bitmap,
            windowBounds = bounds,
            onFirstFrame = {
                keepContentHidden = true
                if (publishGlobalState && cardId.isNotEmpty()) {
                    DissolveAnimationManager.startDissolving(cardId)
                }
            },
            onComplete = { finishEffect() },
            onFinalTail = {
                if (reflowDuringFinalTail && collapseAfterDissolve) beginCollapse()
            },
        )
        if (effectView == null) {
            if (!bitmap.isRecycled) bitmap.recycle()
            finishEffect()
            return@LaunchedEffect
        }
        // Covers missing SurfaceTexture callbacks/device failures without a stuck removal.
        // Normal upstream lifetime is (1.5 + 0.9) / 1.15, about 2.09 seconds.
        delay(5000L)
        if (!hasCompletedCurrentDissolve && !shouldCollapse) finishEffect()
    }

    DisposableEffect(cardId) {
        onDispose {
            effectView?.dispose()
            effectView = null
            // Lazy layouts dispose offscreen items during measurement. Mutating their
            // item provider here invalidates the grid's in-flight index/Lookahead data.
            // A queued callback also survives cancellation of this item's effect scope.
            if (latestIsDissolving) {
                completionHandler.post { dispatchCompletionOnce() }
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { if (!shouldCollapse) cardSize = it }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                cardWindowBounds = RectF(
                    position.x, position.y,
                    position.x + coordinates.size.width,
                    position.y + coordinates.size.height,
                )
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = if (shouldCollapse) {
                    (cardSize.height * heightMultiplier.value).roundToInt().coerceAtLeast(0)
                } else {
                    placeable.height
                }
                layout(placeable.width, height) { placeable.placeRelative(0, 0) }
            }
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = if (keepContentHidden) 0f else 1f }
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                    if (!hasRecordedContent && shouldCreateDissolveBitmap(size.width.toInt(), size.height.toInt())) {
                        hasRecordedContent = true
                    }
                }
        ) { content() }
    }
}

@Composable
fun MaybeDissolvableVideoCard(
    isDissolving: Boolean,
    onDissolveComplete: () -> Unit,
    modifier: Modifier = Modifier,
    cardId: String = "",
    preset: DissolveAnimationPreset = DissolveAnimationPreset.CLASSIC,
    collapseAfterDissolve: Boolean = true,
    publishGlobalDissolveState: Boolean = true,
    keepInvisibleAfterDissolve: Boolean = false,
    reflowDuringFinalTail: Boolean = false,
    onReflowStarted: () -> Unit = {},
    preserveContentLayerWhenIdle: Boolean = false,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit
) {
    if (shouldWrapWithDissolveAnimation(isDissolving)) {
        DissolvableVideoCard(
            isDissolving = true,
            onDissolveComplete = onDissolveComplete,
            modifier = modifier,
            cardId = cardId,
            preset = preset,
            collapseAfterDissolve = collapseAfterDissolve,
            publishGlobalDissolveState = publishGlobalDissolveState,
            keepInvisibleAfterDissolve = keepInvisibleAfterDissolve,
            reflowDuringFinalTail = reflowDuringFinalTail,
            onReflowStarted = onReflowStarted,
            content = content
        )
    } else {
        Box(modifier = modifier, propagateMinConstraints = propagateMinConstraints) {
            if (preserveContentLayerWhenIdle) {
                // SharedBounds records its source relative to the card's existing graphics layer.
                // Keep the same idle hierarchy as DissolvableVideoCard for transition-enabled
                // cards, without restoring its per-frame size/window coordinate tracking.
                Box(modifier = Modifier.alpha(1f), propagateMinConstraints = propagateMinConstraints) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

private fun findWindow(context: Context): Window? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx.window
        ctx = ctx.baseContext
    }
    return null
}
