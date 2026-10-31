package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.blur.Backdrop
import kotlin.math.roundToInt

internal fun resolveScrollCompactExtent(expanded: Int, button: Int, progress: Float): Int =
    (expanded + (button - expanded) * progress.coerceIn(-0.03f, 1.03f))
        .roundToInt().coerceAtLeast(1)

/** Expanded glass must preserve the indicator's press/drag overflow beyond the resting shell. */
internal fun shouldClipScrollCompactNavigation(progress: Float): Boolean = progress > 0f

/** Keeps the full navigation measured while only the surface and visible region contract. */
@Composable
internal fun ScrollCompactNavigation(
    compact: Boolean,
    currentItem: BottomNavItem,
    currentLabel: String,
    iconStyle: SharedFloatingBottomBarIconStyle,
    expandedWidthPx: Int,
    expandedHeightPx: Int,
    buttonSizePx: Int,
    backdrop: Backdrop?,
    containerColor: Color,
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    liquidGlassTuning: LiquidGlassTuning,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberSystemReduceMotion() ||
        !com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val progress = animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else com.android.purebilibili.core.ui.components.scrollFeedbackSpring(
            com.android.purebilibili.core.ui.components.LocalScrollFeedbackPreset.current,
            damping = if (compact) 0.78f else 0.74f,
            stiffness = 420f,
        ),
        label = "scrollCompactNavigation",
    )
    val accent = com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation(
        com.android.purebilibili.core.ui.AppNavigationColorRole.BAR,
    )?.selectedContent ?: AppContentColors.selectionAccent(glassEnabled)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        // No hidden targets or focusable tabs remain behind the compact circle.
                        if (progress.value < 0.999f) placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    val amount = progress.value.coerceIn(0f, 1f)
                    alpha = 1f - amount
                    shape = GenericShape { _, _ ->
                        val width = resolveScrollCompactExtent(expandedWidthPx, buttonSizePx, amount).toFloat()
                        val height = resolveScrollCompactExtent(expandedHeightPx, buttonSizePx, amount).toFloat()
                        val top = (expandedHeightPx - height) / 2f
                        addRoundRect(RoundRect(Rect(0f, top, width, top + height), CornerRadius(height / 2f)))
                    }
                    // Morphing needs a mask; the fully expanded dock must allow the original
                    // indicator and shell to bloom outside their resting bounds.
                    clip = shouldClipScrollCompactNavigation(progress.value)
                    // The spring can grow past the expanded endpoint without stretching text.
                    val rebound = (-progress.value).coerceIn(0f, 0.03f)
                    scaleX = 1f + rebound * 0.55f
                }
                .then(if (compact) Modifier.clearAndSetSemantics {} else Modifier)
                .pointerInput(compact) {
                    if (compact) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
        ) { content() }
        Box(
            Modifier
                .layout { measurable, _ ->
                    val width = resolveScrollCompactExtent(expandedWidthPx, buttonSizePx, progress.value)
                    val height = resolveScrollCompactExtent(expandedHeightPx, buttonSizePx, progress.value)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(expandedWidthPx, expandedHeightPx) {
                        if (progress.value > 0f) {
                            placeable.placeRelative(0, (expandedHeightPx - height) / 2)
                        }
                    }
                }
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .biliPaiFloatingDockShell(
                    backdrop = backdrop,
                    containerColor = containerColor,
                    pressProgress = 0f,
                    shape = CircleShape,
                    enabled = glassEnabled,
                    blurEnabled = blurEnabled,
                    hazeState = hazeState,
                    liquidGlassTuning = liquidGlassTuning,
                )
                .then(if (compact) Modifier.clickable(role = Role.Button, onClick = onExpand)
                    else Modifier.clearAndSetSemantics {}),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                    resolveHomeNavigationBarIcon(currentItem, true)
                } else resolveMaterialBottomBarIcon(currentItem, true),
                contentDescription = "$currentLabel，展开底栏",
                tint = accent,
                modifier = Modifier.graphicsLayer {
                    alpha = ((progress.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
            )
        }
    }
}
