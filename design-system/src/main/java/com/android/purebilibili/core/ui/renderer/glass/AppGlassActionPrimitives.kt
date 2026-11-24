package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.ProvideAppButtonSlotPalette
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.resolveButtonContentColor

/**
 * 液态玻璃渲染线的 iOS 26 胶囊主按钮：
 * 默认使用胶囊轮廓与 0.7dp 镜面细边，按压时提供轻柔弹簧缩放与自动对比度保护。
 */
@Composable
internal fun AppGlassButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedShape = if (shape == ButtonDefaults.shape) CircleShape else shape
    val activeContainer = if (enabled) containerColor else disabledContainerColor
    val activeContent = if (enabled) {
        resolveButtonContentColor(activeContainer, contentColor, MaterialTheme.colorScheme)
    } else {
        disabledContentColor
    }
    val dark = isAppInDarkTheme()
    val resolvedBorder = border ?: BorderStroke(
        width = AppGlassTokens.SpecularBorderWidth,
        color = Color.White.copy(alpha = if (dark) 0.22f else 0.42f),
    )
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val pressScale by animateFloatAsState(
        targetValue = if (enabled && pressed && motionEnabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
        label = "AppGlassButtonPressScale",
    )

    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides activeContent,
    ) {
        ProvideAppActionContentColor(activeContent) {
            ProvideAppButtonSlotPalette(
                containerColor = activeContainer,
                enabled = enabled,
            ) {
                Row(
                    modifier = modifier
                        .graphicsLayer {
                            if (pressScale != 1f) {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                        }
                        .defaultMinSize(minWidth = 64.dp, minHeight = AppGlassTokens.DialogActionHeight)
                        .clip(resolvedShape)
                        .background(activeContainer)
                        .border(resolvedBorder, resolvedShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProvideTextStyle(
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = activeContent,
                        )
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃渲染线的文本/轻胶囊按钮。
 */
@Composable
internal fun AppGlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedShape = if (shape == ButtonDefaults.textShape) CircleShape else shape
    val activeContainer = if (enabled) containerColor else disabledContainerColor
    val activeContent = if (enabled) {
        resolveButtonContentColor(activeContainer, contentColor, MaterialTheme.colorScheme)
    } else {
        disabledContentColor
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val pressScale by animateFloatAsState(
        targetValue = if (enabled && pressed && motionEnabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
        label = "AppGlassTextButtonPressScale",
    )

    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides activeContent,
    ) {
        ProvideAppActionContentColor(activeContent) {
            ProvideAppButtonSlotPalette(
                containerColor = activeContainer,
                enabled = enabled,
            ) {
                Row(
                    modifier = modifier
                        .graphicsLayer {
                            if (pressScale != 1f) {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                        }
                        .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        .clip(resolvedShape)
                        .let { if (activeContainer != Color.Transparent) it.background(activeContainer) else it }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProvideTextStyle(
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = activeContent,
                        )
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃渲染线的次要轮廓/磨砂胶囊按钮。
 */
@Composable
internal fun AppGlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = isAppInDarkTheme()
    val resolvedShape = if (shape == ButtonDefaults.outlinedShape) CircleShape else shape
    val baseFill = if (containerColor == Color.Transparent) {
        AppGlassTokens.neutralPillFill(dark)
    } else containerColor
    val resolvedBorder = border ?: BorderStroke(
        width = AppGlassTokens.SpecularBorderWidth,
        color = AppGlassTokens.cardRimColor(dark),
    )
    AppGlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = resolvedShape,
        containerColor = baseFill,
        contentColor = contentColor,
        disabledContainerColor = if (disabledContainerColor == Color.Transparent) {
            baseFill.copy(alpha = baseFill.alpha * 0.5f)
        } else disabledContainerColor,
        disabledContentColor = disabledContentColor,
        border = resolvedBorder,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}
