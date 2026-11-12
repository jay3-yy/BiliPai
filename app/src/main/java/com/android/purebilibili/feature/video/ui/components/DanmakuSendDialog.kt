// 文件路径: feature/video/ui/components/DanmakuSendDialog.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.AppColorPicker
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Send

internal data class DanmakuSendSelectionState(
    val color: Int,
    val mode: Int,
    val fontSize: Int
)

internal fun resolveDanmakuSendSelectionState(
    initialColor: Int,
    initialMode: Int,
    initialFontSize: Int,
    colorOptions: List<Int>,
    modeOptions: List<Int>,
    fontSizeOptions: List<Int>
): DanmakuSendSelectionState {
    val fallbackColor = 16777215.takeIf { it in colorOptions } ?: colorOptions.firstOrNull() ?: 16777215
    val fallbackMode = 1.takeIf { it in modeOptions } ?: modeOptions.firstOrNull() ?: 1
    val fallbackFontSize = 25.takeIf { it in fontSizeOptions } ?: fontSizeOptions.firstOrNull() ?: 25
    return DanmakuSendSelectionState(
        color = initialColor.takeIf {
            it in colorOptions || it in 0..0xFFFFFF || it == DANMAKU_SEND_VIP_GRADUAL_COLOR
        } ?: fallbackColor,
        mode = initialMode.takeIf { it in modeOptions } ?: fallbackMode,
        fontSize = initialFontSize.takeIf { it in fontSizeOptions } ?: fallbackFontSize
    )
}

/**
 * 弹幕发送对话框
 *
 * 提供弹幕输入、颜色选择、位置/大小设置功能
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DanmakuSendDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSend: (message: String, color: Int, mode: Int, fontSize: Int, attentionCommand: Boolean) -> Unit,
    isSending: Boolean = false,
    initialColor: Int = 16777215,
    initialMode: Int = 1,
    initialFontSize: Int = 25,
    initialText: String = "",
    initialAttentionCommand: Boolean = false,
    attentionCommandAvailable: Boolean = true,
    vipColorAvailable: Boolean = false,
    onDraftChange: (String, Boolean) -> Unit = { _, _ -> },
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit = { _, _, _ -> },
    topReservedSpace: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var nativeClosing by remember { mutableStateOf(false) }
    val latestVisible by rememberUpdatedState(visible)
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) {
        nativeClosing = false
    }
    val inputActive = visible
    com.android.purebilibili.core.ui.AppModalBottomSheet(
        visible = inputActive,
        onDismissStarted = { nativeClosing = true },
        onDismissRequest = { if (latestVisible) latestDismiss() },
        modifier = modifier.widthIn(max = 450.dp),
        presentationOverride = com.android.purebilibili.core.ui.AppModalPresentation.BottomSheet,
        dragHandle = null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        sheetSurfaceModifier = Modifier.widthIn(max = 450.dp),
    ) {
        DanmakuSendDialogContent(
            visible = inputActive && !nativeClosing,
            onSend = { message, color, mode, size, attention ->
                if (inputActive && !nativeClosing) onSend(message, color, mode, size, attention)
            },
            isSending = isSending,
            initialColor = initialColor,
            initialMode = initialMode,
            initialFontSize = initialFontSize,
            initialText = initialText,
            initialAttentionCommand = initialAttentionCommand,
            attentionCommandAvailable = attentionCommandAvailable,
            vipColorAvailable = vipColorAvailable,
            onDraftChange = onDraftChange,
            onSelectionChange = onSelectionChange,
            topReservedSpace = topReservedSpace,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DanmakuSendDialogContent(
    visible: Boolean,
    onSend: (message: String, color: Int, mode: Int, fontSize: Int, attentionCommand: Boolean) -> Unit,
    isSending: Boolean = false,
    initialColor: Int = 16777215,
    initialMode: Int = 1,
    initialFontSize: Int = 25,
    initialText: String = "",
    initialAttentionCommand: Boolean = false,
    attentionCommandAvailable: Boolean = true,
    vipColorAvailable: Boolean = false,
    onDraftChange: (String, Boolean) -> Unit = { _, _ -> },
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit = { _, _, _ -> },
    topReservedSpace: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val colorOptions = remember { danmakuSendColorOptions() }
    val modeOptions = remember { danmakuSendModeOptions() }
    val fontSizeOptions = remember { danmakuSendFontSizeOptions() }
    var text by rememberSaveable { mutableStateOf(initialText) }
    var selectedColor by rememberSaveable { mutableIntStateOf(initialColor) }
    var selectedMode by rememberSaveable { mutableIntStateOf(initialMode) }
    var selectedFontSize by rememberSaveable { mutableIntStateOf(initialFontSize) }
    var attentionCommandChecked by rememberSaveable { mutableStateOf(initialAttentionCommand) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    var lastKeyboardHeight by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    val density = androidx.compose.ui.platform.LocalDensity.current
    val keyboardHeight = (WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density))
        .coerceAtLeast(0)
    LaunchedEffect(keyboardHeight) {
        if (keyboardHeight > 0) lastKeyboardHeight = keyboardHeight
    }
    DisposableEffect(keyboardController, focusManager) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    var inputWasVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            inputWasVisible = false
        } else if (!inputWasVisible) {
            inputWasVisible = true
            val selection = resolveDanmakuSendSelectionState(
                if (initialColor == DANMAKU_SEND_VIP_GRADUAL_COLOR && !vipColorAvailable) 0xFFFFFF else initialColor,
                initialMode, initialFontSize,
                colorOptions.map { it.value }, modeOptions.map { it.value }, fontSizeOptions.map { it.value },
            )
            text = initialText
            selectedColor = selection.color
            selectedMode = selection.mode
            selectedFontSize = selection.fontSize
            attentionCommandChecked = initialAttentionCommand
            showSettings = false
            showAdvancedOptions = false
        }
    }
    LaunchedEffect(visible, showSettings, showCustomColorPicker, windowFocused, isSending) {
        if (!visible || showSettings || showCustomColorPicker || isSending) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else if (windowFocused) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val latestSelectionChange by rememberUpdatedState(onSelectionChange)
    LaunchedEffect(selectedColor, selectedMode, selectedFontSize, visible) {
        if (visible) latestSelectionChange(selectedColor, selectedMode, selectedFontSize)
    }
    val sendMessage = {
        if (visible && text.isNotBlank() && !isSending) {
            onSend(text.trim(), selectedColor, selectedMode, selectedFontSize,
                attentionCommandChecked && attentionCommandAvailable)
        }
    }
    val availableHeight = (LocalConfiguration.current.screenHeightDp.dp - topReservedSpace)
        .coerceAtLeast(1.dp)
    val panelHeight = maxOf(
        if (com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet) 300.dp else 170.dp,
        with(density) { lastKeyboardHeight.toDp() },
    )
    // 输入栏和样式面板共用布局；材质、文字和选中态由当前玻璃 / 原生主题提供。
    Column(
        modifier = modifier
            .heightIn(max = availableHeight)
            .then(if (showSettings) Modifier else Modifier.imePadding())
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                onClick = { showSettings = !showSettings },
                enabled = visible && !isSending,
                modifier = Modifier.size(48.dp).testTag("danmaku_compact_style_toggle"),
            ) {
                AppIcon(
                    Icons.Outlined.TextFormat,
                    contentDescription = "弹幕样式",
                    tint = if (showSettings) AppContentColors.accent() else AppContentColors.secondaryText(),
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = {
                    if (it.length <= 100) {
                        text = it
                        onDraftChange(it, attentionCommandChecked)
                    }
                },
                modifier = Modifier.weight(1f)
                    .padding(start = 12.dp)
                    .heightIn(min = 48.dp)
                    .testTag("danmaku_compact_input")
                    .focusRequester(focusRequester)
                    .pointerInput(isSending) {
                        if (!isSending) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                showSettings = false
                            }
                        }
                    },
                enabled = visible && !isSending,
                readOnly = showSettings || showCustomColorPicker,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppContentColors.primaryText()),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppContentColors.accent()),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { sendMessage() }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) AppText(
                            "输入弹幕内容",
                            color = AppContentColors.secondaryText(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                        field()
                    }
                },
            )
            if (text.isNotEmpty() && !isSending) AppIconButton(
                onClick = { text = ""; onDraftChange("", attentionCommandChecked) },
                modifier = Modifier.size(36.dp),
            ) { AppIcon(Icons.Outlined.Close, contentDescription = "清空") }
            AppIconButton(
                onClick = sendMessage,
                enabled = visible && text.isNotBlank() && !isSending,
                modifier = Modifier.size(48.dp).testTag("danmaku_compact_send"),
            ) {
                if (isSending) AppCircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else AppIcon(
                    Icons.Outlined.Send,
                    contentDescription = "发送弹幕",
                    tint = if (text.isNotBlank()) AppContentColors.accent() else AppContentColors.secondaryText(),
                )
            }
        }
        if (showSettings) {
            com.android.purebilibili.core.ui.components.AppHorizontalDivider()
            Column(
                modifier = Modifier.weight(1f, fill = false)
                    .height(panelHeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("danmaku_compact_style_panel"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DanmakuCompactOptionRow("弹幕字号") {
                    fontSizeOptions.forEach { option ->
                        DanmakuCompactOption(option.label, selectedFontSize == option.value,
                            onClick = { selectedFontSize = option.value })
                    }
                }
                DanmakuCompactOptionRow("弹幕样式") {
                    modeOptions.forEach { option ->
                        DanmakuCompactOption(option.label, selectedMode == option.value,
                            onClick = { selectedMode = option.value })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppText("弹幕颜色", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp))
                    DanmakuCompactColorGrid(
                        selectedColor = selectedColor,
                        onColorSelected = { selectedColor = it },
                        onCustomColor = { showCustomColorPicker = true },
                        vipColorAvailable = vipColorAvailable,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (attentionCommandAvailable) {
                    AppTextButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                        AppText(if (showAdvancedOptions) "收起发送选项" else "更多发送选项")
                    }
                    if (showAdvancedOptions) Row(verticalAlignment = Alignment.CenterVertically) {
                        AppCheckbox(
                            checked = attentionCommandChecked,
                            onCheckedChange = {
                                attentionCommandChecked = it
                                onDraftChange(text, it)
                            },
                        )
                        AppText("内嵌关注按钮")
                    }
                }
            }
        }
    }
    if (visible && showCustomColorPicker) {
        DanmakuCustomColorPickerDialog(
            initialColor = if (selectedColor >= 0) selectedColor else 0x66CCFF,
            onConfirm = { selectedColor = it; showCustomColorPicker = false },
            onDismiss = { showCustomColorPicker = false },
        )
    }
}

@Composable
private fun DanmakuCompactOptionRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 11.dp))
        content()
    }
}

@Composable
private fun RowScope.DanmakuCompactOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.weight(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectable(selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else AppContentColors.secondaryText(),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/**
 * 自定义弹幕颜色：Miuix HSV 取色器 / Material 原生滑杆，确认时保存 RGB。
 */
@Composable
internal fun DanmakuCustomColorPickerDialog(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingColor by remember(initialColor) { mutableStateOf(Color(initialColor or 0xFF000000.toInt())) }
    val pickerRgb = pendingColor.toArgb() and 0xFFFFFF

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("自定义弹幕颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppColorPicker(
                    color = pendingColor,
                    onColorChanged = { pendingColor = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppText(
                    text = "#%06X".format(pickerRgb),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText()
                )
            }
        },
        confirmButton = {
            AppTextButton(onClick = { onConfirm(pickerRgb) }) {
                AppText("确定")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("取消")
            }
        }
    )
}
