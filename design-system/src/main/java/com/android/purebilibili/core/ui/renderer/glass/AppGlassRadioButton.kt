package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.liquidControlThemeColor
import com.android.purebilibili.core.ui.resolveButtonContentColor

/**
 * 液态玻璃渲染线的 iOS 圆形单选框。
 */
@Composable
internal fun AppGlassRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    val dark = isAppInDarkTheme()
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val accent = liquidControlThemeColor()
    val targetFill = if (selected) accent else AppGlassTokens.neutralPillFill(dark)
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = if (motionEnabled) tween(160) else tween(0),
        label = "AppGlassRadioFill",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motionEnabled) spring(dampingRatio = 0.72f, stiffness = 560f) else tween(0),
        label = "AppGlassRadioDot",
    )
    val dotColor = resolveButtonContentColor(accent, Color.White, MaterialTheme.colorScheme)
    val borderColor = if (selected) {
        Color.White.copy(alpha = if (dark) 0.28f else 0.45f)
    } else {
        AppContentColors.secondaryText().copy(alpha = if (dark) 0.48f else 0.36f)
    }
    val resolvedInteraction = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .let { base ->
                if (onClick != null) {
                    base.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = resolvedInteraction,
                        indication = null,
                        onClick = onClick,
                    )
                } else base
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(fill)
                .border(if (selected) AppGlassTokens.SpecularBorderWidth else 1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (dotScale > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}
