package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.liquidControlThemeColor

internal val LocalGlassDialogActionRow = staticCompositionLocalOf { false }
internal val LocalGlassDialogConfirmAction = staticCompositionLocalOf { false }

/**
 * iOS 26 Liquid Glass 居中对话框内容体：
 * 居中标题与说明文案，底部采用并排胶囊操作按钮（次要操作为轻磨砂中性胶囊，主操作为玻璃强调胶囊）。
 */
@Composable
internal fun AppGlassAlertDialogBody(
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable (() -> Unit)?,
    dismissButton: @Composable (() -> Unit)?,
    confirmContentColor: Color,
    dismissContentColor: Color,
    constrainText: Boolean = true,
) {
    val primaryText = AppContentColors.primaryText()
    val secondaryText = AppContentColors.secondaryText()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppGlassTokens.DialogPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                ProvideAppActionContentColor(primaryText, icon)
            }
        }
        if (title != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (text != null) 8.dp else 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = primaryText,
                    )
                ) {
                    ProvideAppActionContentColor(primaryText, title)
                }
            }
        }
        if (text != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (constrainText) Modifier.weight(1f, fill = false) else Modifier)
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = secondaryText,
                    )
                ) {
                    ProvideAppActionContentColor(secondaryText, text)
                }
            }
        }
        if (dismissButton != null || confirmButton != null) {
            CompositionLocalProvider(LocalGlassDialogActionRow provides true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppGlassTokens.DialogActionSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.let { dismiss ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            ProvideAppActionContentColor(dismissContentColor, dismiss)
                        }
                    }
                    confirmButton?.let { confirm ->
                        CompositionLocalProvider(LocalGlassDialogConfirmAction provides true) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ProvideAppActionContentColor(confirmContentColor, confirm)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃对话框内的胶囊操作按钮。
 */
@Composable
internal fun AppGlassDialogPillAction(
    onClick: () -> Unit,
    isConfirm: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme()
    val accent = liquidControlThemeColor()
    val containerColor = if (isConfirm) {
        accent.copy(alpha = if (dark) 0.88f else 0.92f)
    } else {
        AppGlassTokens.neutralPillFill(dark)
    }
    val contentColor = if (isConfirm) {
        com.android.purebilibili.core.ui.resolveButtonContentColor(
            containerColor = containerColor,
            contentColor = Color.White,
            colorScheme = MaterialTheme.colorScheme,
        )
    } else {
        AppContentColors.primaryText()
    }
    val borderColor = if (isConfirm) {
        Color.White.copy(alpha = if (dark) 0.28f else 0.42f)
    } else {
        Color.White.copy(alpha = if (dark) 0.14f else 0.55f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppGlassTokens.DialogActionHeight)
            .clip(CircleShape)
            .background(containerColor)
            .border(AppGlassTokens.SpecularBorderWidth, borderColor, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                fontWeight = if (isConfirm) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
            )
        ) {
            ProvideAppActionContentColor(contentColor, content)
        }
    }
}
