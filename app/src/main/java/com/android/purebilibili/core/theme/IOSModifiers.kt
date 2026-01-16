package com.android.purebilibili.core.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

/**
 * iOS Style Modifier Extensions
 * Implements core visual and interaction design patterns from Apple's Design System.
 */

/**
 * Apply a standard iOS Card style with:
 * - Continuous curvature rounded corners (simulated via standard RoundedCornerShape for now)
 * - Subtle diffuse shadow
 * - Spring-loaded press scale animation (optional)
 * - Haptic feedback on press (optional)
 */
fun Modifier.iosCard(
    shape: Shape = RoundedCornerShape(12.dp), // Standard iOS corner radius
    backgroundColor: Color? = null,
    elevation: Dp = 2.dp, // Subtle elevation
    pressEffect: Boolean = true,
    hapticFeedback: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && pressEffect) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iosPressScale"
    )
    
    val haptic = rememberHapticFeedback()
    val currentBackgroundColor = backgroundColor ?: MaterialTheme.colorScheme.surface

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            // iOS shadows are often more diffuse and less "elevated" looking than Material
            // We can simulate this with standard shadow for now, or custom drawn shadow later
            shadowElevation = if (isPressed) elevation.toPx() / 2 else elevation.toPx()
            this.shape = shape
            clip = true
        }
        .background(currentBackgroundColor, shape)
        .let { modifier ->
            if (onClick != null) {
                modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            if (hapticFeedback) {
                                haptic(HapticType.MEDIUM) // iOS "Tock" feel
                            }
                            
                            val up = waitForUpOrCancellation()
                            isPressed = false
                            if (up != null) {
                                onClick()
                            }
                        }
                    }
                }
            } else {
                modifier
            }
        }
}

/**
 * iOS Style "Squishy" Press Effect (Scale Only)
 * useful for items that don't need the full card background/shadow
 */
fun Modifier.iosPressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f
): Modifier = composed {
    if (!enabled) return@composed this
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iosPressScaleSimple"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null // Disable standard Material ripple (optional, or keep generic)
        ) { /* Logic handled by parent clickable usually, this modifier might be tricky with composed click events */ }
}
