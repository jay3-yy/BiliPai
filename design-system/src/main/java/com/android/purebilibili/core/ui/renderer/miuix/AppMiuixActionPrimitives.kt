package com.android.purebilibili.core.ui.renderer.miuix

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppMiuixActionTone
import com.android.purebilibili.core.ui.components.appDesktopInteractionVisuals
import com.android.purebilibili.core.ui.components.resolveMiuixChipActionTone
import com.android.purebilibili.core.ui.components.resolveMiuixFabMinSizeDp
import com.android.purebilibili.core.ui.components.resolveMiuixNonGlassChipMetrics
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors as MiuixButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults as MiuixFloatingActionButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SnackbarDefaults as MiuixSnackbarDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppMiuixButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    colors: MiuixButtonColors,
    insideMargin: PaddingValues,
    interactionSource: MutableInteractionSource,
    border: BorderStroke? = null,
    content: @Composable RowScope.() -> Unit,
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier.then(
            if (border == null) {
                Modifier
            } else {
                val brush = border.brush
                if (brush is SolidColor) {
                    Modifier.squircleBorder(
                        width = border.width,
                        color = brush.value,
                        cornerRadius = MiuixButtonDefaults.CornerRadius,
                    )
                } else {
                    Modifier.border(border, RoundedCornerShape(MiuixButtonDefaults.CornerRadius))
                }
            }
        ),
        enabled = enabled,
        colors = colors,
        insideMargin = insideMargin,
        minHeight = MiuixButtonDefaults.MinHeight,
        minWidth = MiuixButtonDefaults.MinWidth,
        cornerRadius = MiuixButtonDefaults.CornerRadius,
        interactionSource = interactionSource,
        content = {
            // Slots can contain Material Text/Icon alongside native Miuix content.
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MiuixLocalContentColor.current,
                com.android.purebilibili.core.ui.LocalAppButtonSlotPalette provides
                    com.android.purebilibili.core.ui.AppButtonSlotPalette(
                        containerColor = if (enabled) colors.color else colors.disabledColor,
                        contentColor = MiuixLocalContentColor.current,
                        enabled = enabled,
                    ),
            ) {
                content()
            }
        },
    )
}

@Composable
internal fun AppMiuixChip(
    onClick: () -> Unit,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    interactionSource: MutableInteractionSource?,
    navigationColors: Boolean = false,
    label: @Composable () -> Unit,
) {
    val colors = when (resolveMiuixChipActionTone(selected)) {
        AppMiuixActionTone.PRIMARY -> MiuixButtonDefaults.buttonColorsPrimary()
        AppMiuixActionTone.SECONDARY -> MiuixButtonDefaults.buttonColors()
    }
    val navigation = if (navigationColors && selected) {
        com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation()
    } else null
    val readableColors = if (navigation != null) MiuixButtonColors(
        color = navigation.selectedContainer,
        contentColor = navigation.selectedContent,
        disabledColor = colors.disabledColor,
        disabledContentColor = colors.disabledContentColor,
    ) else colors
    val metrics = resolveMiuixNonGlassChipMetrics()
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = readableColors,
        insideMargin = MiuixButtonDefaults.InsideMargin,
        minHeight = MiuixButtonDefaults.MinHeight,
        minWidth = MiuixButtonDefaults.MinWidth,
        cornerRadius = MiuixButtonDefaults.CornerRadius,
        interactionSource = interactionSource,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MiuixLocalContentColor.current,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(metrics.iconGapDp.dp))
            }
            label()
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(metrics.iconGapDp.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
internal fun AppMiuixFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    small: Boolean,
    content: @Composable () -> Unit,
) {
    val minSize = resolveMiuixFabMinSizeDp(small).dp
    MiuixFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = containerColor,
        shadowElevation = MiuixFloatingActionButtonDefaults.ShadowElevation,
        minWidth = minSize,
        minHeight = minSize,
    ) {
        // Native FAB's internal Surface resets LocalContentColor; provide the pair inside it.
        CompositionLocalProvider(
            MiuixLocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

@Composable
internal fun AppMiuixSnackbar(
    modifier: Modifier,
    action: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    // 跟随当前深浅色主题，避免暗黑模式下 Miuix 默认反色容器刺眼发白。
    val containerColor = AppSurfaceTokens.surfaceContainerHigh()
    val contentColor = AppContentColors.primaryText()
    AppMiuixSnackbarContainer(modifier = modifier, containerColor = containerColor) {
        CompositionLocalProvider(
            MiuixLocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .defaultMinSize(minHeight = AppChromeSizeTokens.MinimumTouchTarget)
                    .padding(MiuixSnackbarDefaults.InsideMargin),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    content()
                }
                if (action != null) {
                    Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                    action()
                }
            }
        }
    }
}

@Composable
internal fun AppMiuixMessageSnackbar(
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    withDismissAction: Boolean,
    onDismiss: (() -> Unit)?,
    modifier: Modifier,
) {
    // 跟随当前深浅色主题，避免暗黑模式下 Miuix 默认反色容器刺眼发白。
    val containerColor = AppSurfaceTokens.surfaceContainerHigh()
    val contentColor = AppContentColors.primaryText()
    val actionContainerColor = AppContentColors.accent()
    val actionContentColor = AppContentColors.onAccent()
    val dismissActionContentColor = AppContentColors.secondaryText()
    AppMiuixSnackbarContainer(modifier = modifier, containerColor = containerColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .defaultMinSize(minHeight = AppChromeSizeTokens.MinimumTouchTarget)
                .padding(MiuixSnackbarDefaults.InsideMargin),
        ) {
            MiuixText(
                text = message,
                color = contentColor,
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!actionLabel.isNullOrEmpty() && onAction != null) {
                MiuixTextButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.padding(start = AppSpacingTokens.Medium),
                    cornerRadius = MiuixSnackbarDefaults.ActionCornerRadius,
                    minWidth = 26.dp,
                    minHeight = 26.dp,
                    colors = MiuixButtonDefaults.textButtonColorsPrimary(
                        color = actionContainerColor,
                        textColor = actionContentColor,
                    ),
                    insideMargin = MiuixSnackbarDefaults.ActionInsideMargin,
                    textStyle = MiuixTheme.textStyles.button,
                )
            }
            if (withDismissAction && onDismiss != null) {
                MiuixIcon(
                    imageVector = MiuixIcons.Basic.Close,
                    contentDescription = "关闭",
                    tint = dismissActionContentColor,
                    modifier = Modifier
                        .padding(start = AppSpacingTokens.Small)
                        .size(20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = null,
                            onClick = onDismiss,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun AppMiuixNavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    badge: (@Composable () -> Unit)?,
    interactionSource: MutableInteractionSource,
) {
    val navigationColors = com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation()
    val selectedContent = navigationColors?.selectedContent ?: MiuixTheme.colorScheme.onPrimaryContainer
    val itemModifier = if (selected) modifier.squircleBackground(
        color = navigationColors?.selectedContainer ?: MiuixTheme.colorScheme.primaryContainer,
        cornerRadius = MiuixButtonDefaults.CornerRadius,
    ) else modifier
    BasicComponent(
        modifier = itemModifier.appDesktopInteractionVisuals(interactionSource),
        startAction = icon?.let { action ->
            {
                com.android.purebilibili.core.ui.components.ProvideAppActionContentColor(
                    if (selected) selectedContent else AppContentColors.secondaryText(),
                    action,
                )
            }
        },
        endActions = badge?.let { slot -> { slot() } },
        onClick = onClick,
        holdDownState = selected,
        interactionSource = interactionSource,
    ) {
        com.android.purebilibili.core.ui.components.ProvideAppActionContentColor(
            if (selected) selectedContent else AppContentColors.primaryText(),
            label,
        )
    }
}

@Composable
private fun AppMiuixSnackbarContainer(
    modifier: Modifier,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    val cornerRadius = MiuixSnackbarDefaults.CornerRadius
    Box(
        modifier = modifier
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
                liveRegion = LiveRegionMode.Polite
            }
            .padding(MiuixSnackbarDefaults.OuterPadding)
            .dropShadow(
                shape = RoundedCornerShape(cornerRadius),
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = 0.1f,
                ),
            )
            .squircleBackground(color = containerColor, cornerRadius = cornerRadius),
    ) {
        content()
    }
}
