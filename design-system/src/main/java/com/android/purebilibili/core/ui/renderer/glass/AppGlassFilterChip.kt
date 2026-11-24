package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.liquidControlThemeColor
import com.android.purebilibili.core.ui.resolveButtonContentColor

/**
 * 液态玻璃渲染线的 iOS 26 胶囊筛选片：
 * 选中态采用强调色玻璃填充 + 镜面高光细边，未选态采用轻磨砂中性胶囊。
 */
@Composable
internal fun AppGlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    shape: Shape,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
) {
    val dark = isAppInDarkTheme()
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val resolvedShape = if (shape == FilterChipDefaults.shape) CircleShape else shape
    val accent = liquidControlThemeColor()
    val targetContainer = if (selected) {
        accent.copy(alpha = if (dark) 0.86f else 0.90f)
    } else {
        AppGlassTokens.neutralPillFill(dark)
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = if (motionEnabled) tween(160) else tween(0),
        label = "AppGlassFilterChipBg",
    )
    val contentColor = if (selected) {
        resolveButtonContentColor(targetContainer, Color.White, MaterialTheme.colorScheme)
    } else {
        AppContentColors.primaryText()
    }
    val borderColor = if (selected) {
        Color.White.copy(alpha = if (dark) 0.28f else 0.45f)
    } else {
        AppGlassTokens.cardRimColor(dark)
    }
    val resolvedInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by resolvedInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (enabled && pressed && motionEnabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
        label = "AppGlassFilterChipPress",
    )

    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor,
    ) {
        ProvideAppActionContentColor(contentColor) {
            Row(
                modifier = modifier
                    .alpha(if (enabled) 1f else 0.42f)
                    .graphicsLayer {
                        if (pressScale != 1f) {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                    }
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(resolvedShape)
                    .background(containerColor)
                    .border(AppGlassTokens.SpecularBorderWidth, borderColor, resolvedShape)
                    .semantics { this.selected = selected }
                    .clickable(
                        interactionSource = resolvedInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onClick = onClick,
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                ProvideTextStyle(
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                    )
                ) {
                    label()
                }
                trailingIcon?.invoke()
            }
        }
    }
}
