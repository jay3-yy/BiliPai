package com.android.purebilibili.feature.home.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.pow
import kotlin.math.roundToInt

private const val ADAPTIVE_READABILITY_SAMPLE_INTERVAL_MILLIS = 500L
private const val ADAPTIVE_READABILITY_INITIAL_DELAY_MILLIS = 350L
private const val ADAPTIVE_READABILITY_SAMPLE_WIDTH = 24
private const val ADAPTIVE_READABILITY_SAMPLE_HEIGHT = 8
private const val ADAPTIVE_READABILITY_LIGHT_TO_DARK_THRESHOLD = 0.58f
private const val ADAPTIVE_READABILITY_DARK_TO_LIGHT_THRESHOLD = 0.42f
private val adaptiveReadabilityPixelCopyHandler by lazy(LazyThreadSafetyMode.NONE) {
    Handler(Looper.getMainLooper())
}
private val adaptiveReadabilityPixelCopyMutex = Mutex()

internal enum class LiquidGlassAdaptiveForegroundTone {
    DARK,
    LIGHT,
}

internal fun resolveLiquidGlassAdaptiveForegroundTone(
    previous: LiquidGlassAdaptiveForegroundTone?,
    backgroundLuminance: Float,
): LiquidGlassAdaptiveForegroundTone {
    val luminance = backgroundLuminance.coerceIn(0f, 1f)
    return when (previous) {
        null -> if (luminance >= 0.5f) {
            LiquidGlassAdaptiveForegroundTone.DARK
        } else {
            LiquidGlassAdaptiveForegroundTone.LIGHT
        }
        LiquidGlassAdaptiveForegroundTone.DARK ->
            if (luminance < ADAPTIVE_READABILITY_DARK_TO_LIGHT_THRESHOLD) {
                LiquidGlassAdaptiveForegroundTone.LIGHT
            } else {
                previous
            }
        LiquidGlassAdaptiveForegroundTone.LIGHT ->
            if (luminance > ADAPTIVE_READABILITY_LIGHT_TO_DARK_THRESHOLD) {
                LiquidGlassAdaptiveForegroundTone.DARK
            } else {
                previous
            }
    }
}

@Stable
internal class LiquidGlassAdaptiveReadabilityState {
    internal var sampleBounds: Rect? by mutableStateOf(null)
        private set

    internal var foregroundTone: LiquidGlassAdaptiveForegroundTone? by mutableStateOf(null)
        private set

    internal fun updateBounds(bounds: ComposeRect) {
        val left = bounds.left.roundToInt()
        val top = bounds.top.roundToInt()
        val right = bounds.right.roundToInt()
        val bottom = bounds.bottom.roundToInt()
        sampleBounds = if (right > left && bottom > top) {
            Rect(left, top, right, bottom)
        } else {
            null
        }
    }

    internal fun updateLuminance(luminance: Float) {
        foregroundTone = resolveLiquidGlassAdaptiveForegroundTone(
            previous = foregroundTone,
            backgroundLuminance = luminance,
        )
    }

    internal fun reset() {
        sampleBounds = null
        foregroundTone = null
    }
}

@Composable
internal fun rememberLiquidGlassAdaptiveReadabilityState(
    enabled: Boolean,
): LiquidGlassAdaptiveReadabilityState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = remember(context) { context.findLiquidGlassHostActivity() }
    val state = remember { LiquidGlassAdaptiveReadabilityState() }
    androidx.compose.runtime.LaunchedEffect(enabled, activity, lifecycle, state) {
        if (!enabled || activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            state.reset()
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(ADAPTIVE_READABILITY_INITIAL_DELAY_MILLIS)
            while (isActive) {
                val decorView = activity.window.decorView
                val sourceBounds = state.sampleBounds?.clampedTo(
                    width = decorView.width,
                    height = decorView.height,
                )
                if (sourceBounds != null) {
                    adaptiveReadabilityPixelCopyMutex.withLock {
                        sampleWindowLuminance(activity, sourceBounds)
                    }?.let(state::updateLuminance)
                }
                delay(ADAPTIVE_READABILITY_SAMPLE_INTERVAL_MILLIS)
            }
        }
    }
    return state
}

internal fun Modifier.trackLiquidGlassAdaptiveReadability(
    state: LiquidGlassAdaptiveReadabilityState,
    enabled: Boolean,
): Modifier = if (enabled) {
    onGloballyPositioned { coordinates -> state.updateBounds(coordinates.boundsInWindow()) }
} else {
    this
}

@Composable
internal fun rememberLiquidGlassAdaptiveContentColor(
    stableColor: Color,
    state: LiquidGlassAdaptiveReadabilityState,
    enabled: Boolean,
    contrastBackgroundColor: Color? = null,
): Color {
    val sampledColor = if (enabled) {
        when (state.foregroundTone) {
            LiquidGlassAdaptiveForegroundTone.DARK -> Color.Black.copy(alpha = 0.90f)
            LiquidGlassAdaptiveForegroundTone.LIGHT -> Color.White.copy(alpha = 0.96f)
            null -> stableColor
        }
    } else {
        stableColor
    }
    val targetColor = resolveLiquidGlassContrastGuardedForeground(
        sampledColor = sampledColor,
        stableColor = stableColor,
        backgroundColor = contrastBackgroundColor,
    )
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 240),
        label = "liquidGlassAdaptiveContentColor",
    )
    return animatedColor
}

internal fun resolveLiquidGlassContrastGuardedForeground(
    sampledColor: Color,
    stableColor: Color,
    backgroundColor: Color?,
    minimumContrastRatio: Float = 3f,
): Color {
    val background = backgroundColor ?: return sampledColor
    if (liquidGlassContrastRatio(sampledColor, background) >= minimumContrastRatio) {
        return sampledColor
    }
    if (liquidGlassContrastRatio(stableColor, background) >= minimumContrastRatio) {
        return stableColor
    }
    return listOf(Color.Black, Color.White)
        .maxBy { candidate -> liquidGlassContrastRatio(candidate, background) }
}

private fun liquidGlassContrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private suspend fun sampleWindowLuminance(
    activity: Activity,
    sourceBounds: Rect,
): Float? = suspendCancellableCoroutine { continuation ->
    val bitmap = Bitmap.createBitmap(
        ADAPTIVE_READABILITY_SAMPLE_WIDTH,
        ADAPTIVE_READABILITY_SAMPLE_HEIGHT,
        Bitmap.Config.ARGB_8888,
    )
    try {
        PixelCopy.request(
            activity.window,
            sourceBounds,
            bitmap,
            { result ->
                val luminance = if (result == PixelCopy.SUCCESS) {
                    bitmap.averageRelativeLuminance()
                } else {
                    null
                }
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(luminance)
            },
            adaptiveReadabilityPixelCopyHandler,
        )
    } catch (_: IllegalArgumentException) {
        bitmap.recycle()
        if (continuation.isActive) continuation.resume(null)
    }
}

private fun Bitmap.averageRelativeLuminance(): Float {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    if (pixels.isEmpty()) return 0.5f
    var total = 0.0
    pixels.forEach { pixel ->
        val red = ((pixel shr 16) and 0xFF) / 255.0
        val green = ((pixel shr 8) and 0xFF) / 255.0
        val blue = (pixel and 0xFF) / 255.0
        total += 0.2126 * red.toLinearChannel() +
            0.7152 * green.toLinearChannel() +
            0.0722 * blue.toLinearChannel()
    }
    return (total / pixels.size).toFloat().coerceIn(0f, 1f)
}

private fun Double.toLinearChannel(): Double =
    if (this <= 0.04045) this / 12.92 else ((this + 0.055) / 1.055).pow(2.4)

private fun Rect.clampedTo(width: Int, height: Int): Rect? {
    if (width <= 0 || height <= 0) return null
    val clamped = Rect(
        left.coerceIn(0, width),
        top.coerceIn(0, height),
        right.coerceIn(0, width),
        bottom.coerceIn(0, height),
    )
    return clamped.takeIf { it.width() > 0 && it.height() > 0 }
}

private tailrec fun Context.findLiquidGlassHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findLiquidGlassHostActivity()
    else -> null
}
