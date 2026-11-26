// 文件路径: feature/dynamic/components/ActionButton.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppTypographyTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.feature.dynamic.DynamicStatusPalette
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.rememberAppLikeFilledIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.feature.dynamic.resolveDynamicActionButtonText
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
/**
 *  iOS 风格操作按钮 - 现代化胶囊设计
 *
 * @param icon 图标
 * @param count 数量
 * @param label 标签（点赞/评论/转发）
 * @param isActive 是否激活状态（如已点赞）
 * @param onClick 点击回调
 */
@Composable
fun ActionButton(
    count: Int,
    label: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    activeColor: Color = AppContentColors.secondaryText().copy(0.6f),
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLike = label == "点赞"
    val isForward = label == "转发"
    val isComment = label == "评论"

    // Ordinary actions remain neutral; confirmed likes use the active theme accent.
    val miuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val neutralContentColor = if (miuix) MiuixTheme.colorScheme.onSecondaryVariant
        else colorScheme.onSurfaceVariant
    val buttonColor = when {
        !enabled -> if (miuix) MiuixTheme.colorScheme.disabledOnSecondaryVariant
            else colorScheme.onSurface.copy(alpha = 0.38f)
        isLike && isActive -> DynamicStatusPalette.liked()
        else -> neutralContentColor
    }
    val miuixButtonColors = MiuixButtonDefaults.buttonColors(
        contentColor = buttonColor,
    )

    val interactionSource = remember { MutableInteractionSource() }

    //  优雅的图标 - 根据状态切换填充/描边
    val commentBubbleIcon = ImageVector.vectorResource(R.drawable.ic_dynamic_comment_bubble_24)
    val buttonIcon = when {
        isLike && isActive -> rememberAppLikeFilledIcon()
        isLike -> rememberAppLikeIcon()
        isForward -> Icons.Outlined.IosShare
        isComment -> commentBubbleIcon
        else -> commentBubbleIcon
    }
    val countFadeAnimationSpec = AppMotionTokens.standardSpec<Float>()
    val countSlideAnimationSpec = AppMotionTokens.standardSpec<IntOffset>()
    Box(modifier = modifier) {
        val actionText = remember(label, count) {
            resolveDynamicActionButtonText(
                label = label,
                count = count
            )
        }

        // Miuix 动态底栏统一使用原生 Button，保证图标、文字、触控区与桌面 PiliPlus 风格一致。
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            MiuixButton(
                onClick = onClick,
                enabled = enabled,
                colors = miuixButtonColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .appElasticPress(enabled = enabled),
                // This remains a native Miuix button with glass enabled; keep its
                // content metrics independent from the material used by page docks.
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                AppIcon(
                    imageVector = buttonIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = buttonColor,
                )
                DynamicNativeActionText(
                    actionText = actionText,
                    countFadeAnimationSpec = countFadeAnimationSpec,
                    countSlideAnimationSpec = countSlideAnimationSpec,
                    spacing = AppSpacingTokens.ExtraSmall
                )
            }
            return@Box
        }

        // Material 3 主题同样使用原生 Button，避免主题切换后退回自绘点击 Row。
        if (LocalAppUiStyle.current == AppUiStyle.MATERIAL3) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
                    .appElasticPress(enabled = enabled),
                contentPadding = PaddingValues(
                    horizontal = AppSpacingTokens.Small,
                    vertical = AppSpacingTokens.Small
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = buttonColor,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = buttonColor,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                AppIcon(
                    imageVector = buttonIcon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = buttonColor,
                )
                DynamicNativeActionText(
                    actionText = actionText,
                    countFadeAnimationSpec = countFadeAnimationSpec,
                    countSlideAnimationSpec = countSlideAnimationSpec,
                    spacing = AppSpacingTokens.ExtraSmall,
                )
            }
            return@Box
        }

        val useFilledShell = !isComment && LocalAppUiStyle.current != AppUiStyle.MATERIAL3
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
                .appElasticPress(enabled = enabled)
                .then(
                    if (useFilledShell) {
                        Modifier
                            .clip(AppShapes.container(ContainerLevel.Pill))
                            .background(
                                color = buttonColor.copy(
                                    alpha = if (isActive && isLike) 0.15f else 0.08f
                                )
                            )
                    } else {
                        Modifier
                    }
                )
                .appActionClickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() }
                .padding(horizontal = AppSpacingTokens.Small + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Small)
        ) {
            AppIcon(
                imageVector = buttonIcon,
                contentDescription = label,
                modifier = Modifier.size(AppSpacingTokens.Large + AppSpacingTokens.Micro),
                tint = buttonColor
            )

            if (actionText != null) {
                Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                //  计数变化动画（对齐 BiliPai AnimatedSwitcher + ScaleTransition）
                AnimatedContent(
                    targetState = actionText,
                    transitionSpec = {
                        (fadeIn(animationSpec = countFadeAnimationSpec) +
                            slideInVertically(animationSpec = countSlideAnimationSpec) { it / 3 })
                            .togetherWith(
                                fadeOut(animationSpec = countFadeAnimationSpec) +
                                    slideOutVertically(animationSpec = countSlideAnimationSpec) { -it / 3 }
                            )
                    },
                    label = "actionButtonCount"
                ) { text ->
                    AppText(
                        text = text,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = buttonColor,
                        letterSpacing = AppTypographyTokens.ZeroLetterSpacing,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicNativeActionText(
    actionText: String?,
    countFadeAnimationSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>,
    countSlideAnimationSpec: androidx.compose.animation.core.FiniteAnimationSpec<IntOffset>,
    spacing: androidx.compose.ui.unit.Dp
) {
    if (actionText == null) return
    Spacer(modifier = Modifier.width(spacing))
    AnimatedContent(
        targetState = actionText,
        transitionSpec = {
            (fadeIn(animationSpec = countFadeAnimationSpec) +
                slideInVertically(animationSpec = countSlideAnimationSpec) { it / 3 })
                .togetherWith(
                    fadeOut(animationSpec = countFadeAnimationSpec) +
                        slideOutVertically(animationSpec = countSlideAnimationSpec) { -it / 3 }
                )
        },
        label = "nativeActionButtonCount"
    ) { text ->
        AppText(
            text = text,
            // Both native themes share constrained action slots, with or without glass.
            style = MaterialTheme.typography.labelLarge,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = MaterialTheme.typography.labelSmall.fontSize,
                maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
