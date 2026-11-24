@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.resolveButtonContentColor
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
): Unit = AppExactTouchTargets {
    val elasticFeedbackEnabled = enabled && !isLoading && LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.liquidGlassEnabled
    ProvideAppElasticFeedback(enabled = elasticFeedbackEnabled) {
        val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(
            com.android.purebilibili.core.ui.AppHapticScene.CONTROLS
        )
        val hapticEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
        val feedbackClick = appActionWithFeedback {
            onClick()
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        val interactionSource = remember { MutableInteractionSource() }
        val primaryContentColor = resolveButtonContentColor(
            containerColor = AppContentColors.accent(),
            contentColor = AppContentColors.onAccent(),
            colorScheme = MaterialTheme.colorScheme,
        )

        if (LocalAppUiStyle.current == AppUiStyle.MATERIAL3) {
            Button(
                onClick = feedbackClick,
                modifier = modifier,
                enabled = enabled && !isLoading,
                shape = ButtonDefaults.shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppContentColors.accent(),
                    contentColor = primaryContentColor,
                    disabledContainerColor = AppContentColors.primaryText().copy(alpha = 0.12f),
                    disabledContentColor = AppContentColors.primaryText().copy(alpha = 0.38f),
                ),
                contentPadding = AppButtonDefaults.ContentPadding,
                interactionSource = interactionSource,
            ) {
                PrimaryButtonLoadingContent(
                    isLoading = isLoading,
                    indicatorSize = 20.dp,
                    indicatorColor = primaryContentColor,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (leadingIcon != null) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = primaryContentColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            return@ProvideAppElasticFeedback
        }

        AppButton(
            onClick = feedbackClick,
            modifier = modifier.heightIn(min = AppChromeSizeTokens.CompactActionHeight),
            enabled = enabled && !isLoading,
            interactionSource = interactionSource,
        ) {
            val contentColor = MiuixLocalContentColor.current
            PrimaryButtonLoadingContent(
                isLoading = isLoading,
                indicatorSize = 24.dp,
                indicatorColor = contentColor,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                    }
                    MiuixText(text = text, style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}


/** 文案持续参与测量；加载指示覆盖在同一槽位，文字变化不依赖历史测量缓存。 */
@Composable
private fun PrimaryButtonLoadingContent(
    isLoading: Boolean,
    indicatorSize: Dp,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val visualWorkActive = rememberVisualWorkActive()
    var inViewport by remember { mutableStateOf(false) }
    val labelAlpha = animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(if (motionEnabled) 150 else 0),
        label = "primaryButtonLabelAlpha",
    )
    Box(
        modifier = modifier
            .sizeIn(minWidth = indicatorSize, minHeight = indicatorSize)
            .onGloballyPositioned { coordinates ->
                inViewport = coordinates.isAttached && run {
                    val bounds = coordinates.boundsInWindow()
                    val viewport = coordinates.findRootCoordinates().boundsInWindow()
                    bounds.width > 0f && bounds.height > 0f && bounds.overlaps(viewport)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = labelAlpha.value }
                .then(if (isLoading) Modifier.clearAndSetSemantics { } else Modifier),
        ) { label() }
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(tween(if (motionEnabled) 150 else 0)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)),
        ) {
            if (isLoading && motionEnabled && visualWorkActive && inViewport) {
                AppCircularProgressIndicator(
                    modifier = Modifier.size(indicatorSize),
                    color = indicatorColor,
                    strokeWidth = 2.dp,
                )
            } else {
                // 隐藏、减少动态效果和退出淡出阶段不保留无限动画；静态弧仍表达未知进度。
                AppCircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier
                        .size(indicatorSize)
                        .clearAndSetSemantics {
                            if (isLoading) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        },
                    color = indicatorColor,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
