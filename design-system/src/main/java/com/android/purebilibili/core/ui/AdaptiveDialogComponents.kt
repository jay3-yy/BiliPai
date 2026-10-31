package com.android.purebilibili.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import top.yukonga.miuix.kmp.window.WindowDialog

enum class AppAlertDialogRenderer {
    MATERIAL_ALERT,
    LOCAL_DIALOG
}

fun resolveAppAlertDialogRenderer(
    uiStyle: AppUiStyle,
    nativeMiuixPopupsEnabled: Boolean = true,
): AppAlertDialogRenderer = when (uiStyle) {
    // 设置等页对话框常在 AdaptiveScaffold 外层组合；窗口 Dialog 不依赖
    // Miuix Scaffold 的 DialogStates host，点击后状态与弹窗保持一致。
    AppUiStyle.MIUIX -> if (nativeMiuixPopupsEnabled) {
        AppAlertDialogRenderer.LOCAL_DIALOG
    } else {
        AppAlertDialogRenderer.MATERIAL_ALERT
    }
    AppUiStyle.MATERIAL3 -> AppAlertDialogRenderer.MATERIAL_ALERT
}

data class DialogActionLayoutPolicy(
    val expandToContainer: Boolean
)

/**
 * 历史 iOS 的全宽铺满操作区行为已随单向迁移删除（iOS → MIUIX 不再 expandToContainer）。
 */
fun resolveDialogActionLayoutPolicy(): DialogActionLayoutPolicy {
    return DialogActionLayoutPolicy(
        expandToContainer = false
    )
}

/**
 * Style-neutral Alert Dialog backed by the adaptive renderer.
 */
@Composable
internal fun AdaptiveAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape? = null,
    containerColor: Color? = null,
    tonalElevation: Dp? = null,
    properties: DialogProperties = DialogProperties(),
    contentLayout: AppContentDialogLayoutPolicy = resolveAppCompactContentDialogLayoutPolicy(),
) {
    val uiStyle = LocalAppUiStyle.current
    val themeConfig = LocalAppThemeConfig.current
    val renderer = resolveAppAlertDialogRenderer(
        uiStyle = uiStyle,
        nativeMiuixPopupsEnabled = themeConfig.nativeMiuixPopupsEnabled,
    )
    // Dialog actions are drawn on the dialog fill, not on primary. Keep the picked
    // accent for fills elsewhere; only repair these standalone action foregrounds.
    val baseActionBackground = containerColor ?: if (renderer == AppAlertDialogRenderer.LOCAL_DIALOG) {
        AppSurfaceTokens.cardContainer()
    } else MaterialTheme.colorScheme.surface
    val actionBackground = if (renderer == AppAlertDialogRenderer.MATERIAL_ALERT &&
        baseActionBackground == MaterialTheme.colorScheme.surface
    ) MaterialTheme.colorScheme.surfaceColorAtElevation(tonalElevation ?: AlertDialogDefaults.TonalElevation)
        else baseActionBackground
    val confirmContentColor = resolveButtonContentColor(actionBackground, AppSurfaceTokens.primary(), MaterialTheme.colorScheme)
    val dismissContentColor = resolveButtonContentColor(actionBackground, AppSurfaceTokens.onSurface(), MaterialTheme.colorScheme)
    // 玻璃菜单需要完整的同窗坐标空间；关闭玻璃时继续下方原生 Dialog 路径。
    // 对话框自身保留既有表面，内部下拉的背景只采样本窗口，不跨窗口读取页面。
    if (themeConfig.liquidGlassEnabled && LocalAppPopupWindowHostRenderer.current != null) {
        val retainedWindow: @Composable (AppDialogExitState) -> Unit = { exit ->
            androidx.compose.runtime.CompositionLocalProvider(LocalAppDialogExitRunning provides exit.isClosing) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { if (!exit.isClosing) onDismissRequest() },
                    properties = DialogProperties(
                        dismissOnBackPress = properties.dismissOnBackPress,
                        dismissOnClickOutside = properties.dismissOnClickOutside,
                        securePolicy = properties.securePolicy,
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    AppDialogWindowExitEffect(exit.progress)
                    ModalWindowBlurBehindEffect(progress = { 1f - exit.progress.value })
                    AppPopupWindowHost {
                        Box(Modifier.fillMaxSize().blockAppDialogExitInput(exit.isClosing)) {
                            val surface: @Composable () -> Unit = {
                                BoxWithConstraints {
                                    AppPopupSurface(
                                        type = AppPopupSurfaceType.DIALOG,
                                        modifier = modifier
                                            .graphicsLayer {
                                                val fraction = exit.progress.value.coerceIn(0f, 1f)
                                                scaleX = 1f - 0.2f * fraction
                                                scaleY = scaleX
                                                alpha = 1f - fraction
                                            }
                                            .widthIn(min = contentLayout.minWidthDp.dp, max = contentLayout.maxWidthDp.dp)
                                            .heightIn(max = maxHeight)
                                            .fillMaxWidth()
                                            .pointerInput(Unit) { detectTapGestures { } },
                                        shape = shape ?: if (renderer == AppAlertDialogRenderer.LOCAL_DIALOG) {
                                            AppShapes.resolveContainerShape(ContainerLevel.Dialog, uiStyle, liquidGlassEnabled = false)
                                        } else MaterialTheme.shapes.extraLarge,
                                        containerColor = baseActionBackground,
                                        tonalElevation = tonalElevation ?: if (renderer == AppAlertDialogRenderer.LOCAL_DIALOG) {
                                            0.dp
                                        } else AlertDialogDefaults.TonalElevation,
                                    ) {
                                        MiuixAlertDialogBody(icon, title, text, confirmButton, dismissButton,
                                            confirmContentColor, dismissContentColor, constrainText = true)
                                    }
                                }
                            }
                            val regions = LocalHingeSafeOverlayRegions.current.dialog
                            val outsideDismiss = if (properties.dismissOnClickOutside && !exit.isClosing) onDismissRequest else null
                            if (regions != null) {
                                HingeSafeOverlayHost(
                                    regionProvider = regions,
                                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
                                    onDismissRequest = outsideDismiss,
                                ) { surface() }
                            } else {
                                Box(
                                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                                        .pointerInput(outsideDismiss) { detectTapGestures { outsideDismiss?.invoke() } },
                                    contentAlignment = Alignment.Center,
                                ) { surface() }
                            }
                        }
                    }
                }
            }
        }
        if (!RetainAppGlassDialog(retainedWindow)) {
            retainedWindow(AppDialogExitState(rememberUpdatedState(0f)))
        }
        return
    }
    // 半开折叠屏：弹窗整体收进铰链安全侧，避免横跨折缝。
    val hingeSafeRegions = LocalHingeSafeOverlayRegions.current.dialog
    if (hingeSafeRegions != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = properties.dismissOnBackPress,
                dismissOnClickOutside = properties.dismissOnClickOutside,
                securePolicy = properties.securePolicy,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
            HingeSafeOverlayHost(
                regionProvider = hingeSafeRegions,
                modifier = Modifier.fillMaxSize().imePadding(),
                onDismissRequest = if (properties.dismissOnClickOutside) onDismissRequest else null,
            ) {
                BoxWithConstraints {
                    AppPopupSurface(
                        type = AppPopupSurfaceType.DIALOG,
                        modifier = modifier
                            .widthIn(max = contentLayout.maxWidthDp.dp)
                            .heightIn(max = maxHeight)
                            .fillMaxWidth()
                            .pointerInput(Unit) { detectTapGestures { } },
                        shape = shape ?: if (renderer == AppAlertDialogRenderer.LOCAL_DIALOG) {
                            AppShapes.resolveContainerShape(ContainerLevel.Dialog, uiStyle, liquidGlassEnabled = false)
                        } else MaterialTheme.shapes.extraLarge,
                        containerColor = containerColor ?: if (renderer == AppAlertDialogRenderer.LOCAL_DIALOG) {
                            AppSurfaceTokens.cardContainer()
                        } else MaterialTheme.colorScheme.surface,
                        tonalElevation = tonalElevation ?: if (renderer == AppAlertDialogRenderer.LOCAL_DIALOG) {
                            0.dp
                        } else AlertDialogDefaults.TonalElevation,
                    ) {
                        MiuixAlertDialogBody(icon, title, text, confirmButton, dismissButton,
                            confirmContentColor, dismissContentColor, constrainText = true)
                    }
                }
            }
        }
        return
    }
    when (renderer) {
        AppAlertDialogRenderer.LOCAL_DIALOG -> {
            if (isMiuixNonGlassEnabled() && shape == null) {
                // 原生窗口负责表面、圆角、边距和动画；自定义材质继续走下方路径。
                WindowDialog(
                    show = true,
                    modifier = modifier,
                    onDismissRequest = onDismissRequest,
                    maxWidth = contentLayout.maxWidthDp.dp,
                    backgroundColor = containerColor ?: top.yukonga.miuix.kmp.layout.DialogDefaults.backgroundColor(),
                ) {
                    NativeMiuixAlertDialogBody(icon, title, text, confirmButton, dismissButton)
                }
                return
            }
            val dialogShape = shape ?: AppShapes.resolveContainerShape(
                level = ContainerLevel.Dialog,
                uiStyle = uiStyle,
                liquidGlassEnabled = false,
            )
            val dialogColor = containerColor ?: AppSurfaceTokens.cardContainer()
            val dialogBody: @Composable () -> Unit = {
                MiuixAlertDialogBody(
                    icon = icon,
                    title = title,
                    text = text,
                    confirmButton = confirmButton,
                    dismissButton = dismissButton,
                    confirmContentColor = confirmContentColor,
                    dismissContentColor = dismissContentColor,
                    // 与玻璃/铰链路径一致：容器有高度上限时，text 槽用 weight 收缩，
                    // 保证操作行始终在容器内可见。
                    constrainText = true,
                )
            }
            WindowDialog(
                show = true,
                onDismissRequest = onDismissRequest,
                maxWidth = contentLayout.maxWidthDp.dp,
                // WindowDialog 自带背景、圆角和 24dp 内边距。这里只用其窗口/动画，
                // 外观由 AppPopupSurface 负责，避免内外两张圆角卡片叠加。
                backgroundColor = Color.Transparent,
                insideMargin = DpSize(0.dp, 0.dp),
                cornerRadius = 0.dp,
            ) {
                com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
                BoxWithConstraints {
                    AppPopupSurface(
                        type = AppPopupSurfaceType.DIALOG,
                        modifier = modifier
                            .fillMaxWidth()
                            .widthIn(min = contentLayout.minWidthDp.dp, max = contentLayout.maxWidthDp.dp)
                            .heightIn(max = maxHeight),
                        shape = dialogShape,
                        containerColor = dialogColor,
                        tonalElevation = tonalElevation ?: 0.dp,
                    ) {
                        dialogBody()
                    }
                }
            }
            return
        }
        AppAlertDialogRenderer.MATERIAL_ALERT -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                icon = icon,
                title = title,
                text = text,
                confirmButton = {
                    ModalWindowBlurBehindEffect()
                    ProvideAppActionContentColor(confirmContentColor) {
                        confirmButton?.invoke() ?: Spacer(modifier = Modifier)
                    }
                },
                dismissButton = dismissButton?.let { dismiss ->
                    { ProvideAppActionContentColor(dismissContentColor, dismiss) }
                },
                properties = properties,
                shape = shape ?: MaterialTheme.shapes.extraLarge,
                containerColor = containerColor ?: MaterialTheme.colorScheme.surface,
                tonalElevation = tonalElevation ?: AlertDialogDefaults.TonalElevation,
            )
            return
        }
    }
}

@Composable
private fun MiuixAlertDialogBody(
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable (() -> Unit)?,
    dismissButton: @Composable (() -> Unit)?,
    confirmContentColor: Color,
    dismissContentColor: Color,
    constrainText: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        if (title != null) {
            Box(
                modifier = Modifier.padding(
                    top = if (icon != null) 12.dp else 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    title()
                }
            }
        }
        if (text != null) {
            Box(
                modifier = Modifier
                    .then(if (constrainText) Modifier.weight(1f, fill = false) else Modifier)
                    .padding(
                        top = if (title != null) 8.dp else 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp,
                    ),
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(
                        color = AppContentColors.secondaryText()
                    )
                ) {
                    text()
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (dismissButton != null) {
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                    ProvideAppActionContentColor(dismissContentColor, dismissButton)
                }
            }
            if (confirmButton != null) {
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                    ProvideAppActionContentColor(confirmContentColor, confirmButton)
                }
            }
        }
    }
}

private val LocalNativeMiuixDialogActionRow = androidx.compose.runtime.staticCompositionLocalOf { false }

private val LocalNativeMiuixConfirmAction = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun NativeMiuixAlertDialogBody(
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable (() -> Unit)?,
    dismissButton: @Composable (() -> Unit)?,
) {
    val typography = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        icon?.let {
            Box(Modifier.padding(bottom = 12.dp), contentAlignment = Alignment.Center) { it() }
        }
        title?.let {
            NativeMiuixDialogTextSlot(
                style = typography.title4.copy(fontWeight = FontWeight.Medium),
                color = top.yukonga.miuix.kmp.layout.DialogDefaults.titleColor(),
                content = it,
            )
        }
        text?.let {
            NativeMiuixDialogTextSlot(
                style = typography.body1,
                color = top.yukonga.miuix.kmp.layout.DialogDefaults.summaryColor(),
                content = it,
            )
        }
        if (confirmButton != null || dismissButton != null) {
            androidx.compose.runtime.CompositionLocalProvider(LocalNativeMiuixDialogActionRow provides true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    dismissButton?.let { action ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { action() }
                    }
                    confirmButton?.let { action ->
                        androidx.compose.runtime.CompositionLocalProvider(LocalNativeMiuixConfirmAction provides true) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { action() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeMiuixDialogTextSlot(
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    content: @Composable () -> Unit,
) {
    val centeredStyle = style.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(LocalAppTextStyleOverride provides centeredStyle) {
            ProvideTextStyle(centeredStyle) { ProvideAppActionContentColor(color, content) }
        }
    }
}

/**
 * A helper for buttons inside the adaptive alert dialog if you need absolute control,
 * basically a wrapper that removes TextButton padding issues if standard TextButton is used.
 * But actually providing TextStyle above might be enough for simple Text() children.
 * If user passes TextButton, we might need to conform it.
 */
@Composable
internal fun AdaptiveDialogAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isMiuixNonGlassEnabled()) {
        val colors = if (LocalNativeMiuixConfirmAction.current) {
            top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
        } else {
            top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors()
        }
        top.yukonga.miuix.kmp.basic.Button(
            onClick = onClick,
            modifier = if (LocalNativeMiuixDialogActionRow.current) modifier.fillMaxWidth() else modifier,
            colors = colors,
        ) {
            ProvideAppActionContentColor(top.yukonga.miuix.kmp.theme.LocalContentColor.current, content)
        }
        return
    }
    val layoutPolicy = resolveDialogActionLayoutPolicy()
    Box(
        modifier = modifier
            .then(
                if (layoutPolicy.expandToContainer) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
