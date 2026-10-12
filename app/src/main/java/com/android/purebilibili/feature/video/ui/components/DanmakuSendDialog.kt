// 文件路径: feature/video/ui/components/DanmakuSendDialog.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppColorPicker
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.HingeSafeInputOverlayHost
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

internal data class DanmakuSendDialogLayoutPolicy(
    val fillMaxWidthFraction: Float,
    val bottomAligned: Boolean,
    val bottomLiftDp: Int
)

internal data class DanmakuSendSelectionState(
    val color: Int,
    val mode: Int,
    val fontSize: Int
)

internal fun resolveDanmakuSendDialogLayoutPolicy(): DanmakuSendDialogLayoutPolicy {
    return DanmakuSendDialogLayoutPolicy(
        fillMaxWidthFraction = 1f,
        bottomAligned = true,
        bottomLiftDp = 14
    )
}

internal fun resolveDanmakuDialogBottomLiftDp(
    defaultBottomLiftDp: Int,
    imeBottomPx: Int
): Int {
    return if (imeBottomPx > 0) 0 else defaultBottomLiftDp
}

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
            it in colorOptions || it == DANMAKU_SEND_VIP_GRADUAL_COLOR || it >= 0
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
    onDraftChange: (String, Boolean) -> Unit = { _, _ -> },
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit = { _, _, _ -> },
    topReservedSpace: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val layoutPolicy = remember { resolveDanmakuSendDialogLayoutPolicy() }
    val configuration = LocalConfiguration.current
    // 宽窗口限宽居中；半开折叠时弹层整体由 HingeSafeInputOverlayHost 收进铰链安全区。
    val inputOverlayMaxWidthDp = remember(configuration.screenWidthDp) {
        resolveBottomInputOverlayMaxWidthDp(configuration.screenWidthDp)
    }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val effectiveBottomLiftDp = resolveDanmakuDialogBottomLiftDp(
        defaultBottomLiftDp = layoutPolicy.bottomLiftDp,
        imeBottomPx = imeBottomPx
    )
    val maxSheetHeight = remember(configuration.screenHeightDp, topReservedSpace) {
        minOf(
            configuration.screenHeightDp.dp * 0.5f,
            (configuration.screenHeightDp.dp - topReservedSpace).coerceAtLeast(1.dp)
        )
    }

    val colorOptions = remember { danmakuSendColorOptions().map { it.value to it.label } }
    val modeOptions = remember { danmakuSendModeOptions().map { it.value to it.label } }
    val fontSizeOptions = remember { danmakuSendFontSizeOptions().map { it.value to it.label } }

    // 状态
    var text by rememberSaveable { mutableStateOf(initialText) }
    var selectedColor by rememberSaveable { mutableIntStateOf(initialColor) }
    var selectedMode by rememberSaveable { mutableIntStateOf(initialMode) }
    var selectedFontSize by rememberSaveable { mutableIntStateOf(initialFontSize) }
    var attentionCommandChecked by rememberSaveable { mutableStateOf(initialAttentionCommand) }
    var showSettings by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    var lastCustomColor by remember { mutableIntStateOf(0x66CCFF) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 重置状态
    var inputWasVisible by rememberSaveable { mutableStateOf(false) }
    // 草稿和样式会随输入回流，只在开始新会话时初始化，避免反复重置焦点与设置面板。
    LaunchedEffect(visible) {
        if (!visible) {
            inputWasVisible = false
            return@LaunchedEffect
        }
        if (!inputWasVisible) {
            inputWasVisible = true
            val selection = resolveDanmakuSendSelectionState(
                initialColor = initialColor,
                initialMode = initialMode,
                initialFontSize = initialFontSize,
                colorOptions = colorOptions.map { it.first },
                modeOptions = modeOptions.map { it.first },
                fontSizeOptions = fontSizeOptions.map { it.first }
            )
            text = initialText
            selectedColor = selection.color
            selectedMode = selection.mode
            selectedFontSize = selection.fontSize
            attentionCommandChecked = initialAttentionCommand
            showSettings = false
        }
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(selectedColor, selectedMode, selectedFontSize, visible) {
        if (!visible) return@LaunchedEffect
        onSelectionChange(selectedColor, selectedMode, selectedFontSize)
    }

    if (showCustomColorPicker) {
        DanmakuCustomColorPickerDialog(
            initialColor = lastCustomColor,
            onConfirm = { picked ->
                lastCustomColor = picked
                selectedColor = picked
                showCustomColorPicker = false
            },
            onDismiss = { showCustomColorPicker = false }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
            // 统一输入弹层宿主：半开折叠时弹层整体收进铰链安全区，避免输入控件跨缝；
            // 平铺窗口时点击空白处关闭、底部对齐，宽窗口由 widthIn 限宽并水平居中。
            HingeSafeInputOverlayHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topReservedSpace)
                    .padding(bottom = effectiveBottomLiftDp.dp)
                    .imePadding(),
                alignment = if (layoutPolicy.bottomAligned) Alignment.BottomCenter else Alignment.Center,
                onDismissRequest = if (layoutPolicy.bottomAligned) onDismiss else null,
            ) {
                AppSurface(
                    modifier = modifier
                        .widthIn(max = inputOverlayMaxWidthDp.dp)
                        .fillMaxWidth(layoutPolicy.fillMaxWidthFraction)
                        .heightIn(max = maxSheetHeight)
                        .wrapContentHeight(),
                    shape = if (layoutPolicy.bottomAligned) {
                        AppShapes.container(ContainerLevel.Sheet)
                    } else {
                        AppShapes.container(ContainerLevel.Floating)
                    },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 标题栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText(
                                text = "发送弹幕",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            AppIconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 输入框
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(AppShapes.container(ContainerLevel.Card))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = {
                                    if (it.length <= 100) {
                                        text = it
                                        onDraftChange(it, attentionCommandChecked)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("danmaku_compact_input")
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (text.isEmpty()) {
                                            AppText(
                                                text = "发个友善的弹幕见证当下",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        // 字数统计
                        AppText(
                            text = "${text.length}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (text.length > 90) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )

                        AppTextButton(
                            onClick = { showSettings = !showSettings },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            AppText(if (showSettings) "收起弹幕设置" else "颜色、位置与大小")
                        }

                        if (showSettings) {
                        // 颜色选择
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText(
                                text = "颜色",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colorOptions.forEach { (colorValue, _) ->
                                    val isSelected = selectedColor == colorValue
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (colorValue == DANMAKU_SEND_VIP_GRADUAL_COLOR) {
                                                    Modifier.background(
                                                        Brush.linearGradient(
                                                            colors = listOf(
                                                                Color(0xFFDD94DA),
                                                                Color(0xFF72B2EA)
                                                            )
                                                        )
                                                    )
                                                } else {
                                                    Modifier.background(Color(colorValue or 0xFF000000.toInt()))
                                                }
                                            )
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = CircleShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .clickable { selectedColor = colorValue }
                                    ) {
                                        if (colorValue == DANMAKU_SEND_VIP_GRADUAL_COLOR) {
                                            AppText(
                                                text = "VIP",
                                                modifier = Modifier.align(Alignment.Center),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                val isCustomSelection = selectedColor >= 0 &&
                                    selectedColor != DANMAKU_SEND_VIP_GRADUAL_COLOR &&
                                    colorOptions.none { (preset, _) -> preset == selectedColor }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFFFF5252),
                                                    Color(0xFFFFEB3B),
                                                    Color(0xFF4CAF50),
                                                    Color(0xFF00BCD4),
                                                    Color(0xFF3F51B5),
                                                    Color(0xFFE040FB)
                                                )
                                            )
                                        )
                                        .then(
                                            if (isCustomSelection) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { showCustomColorPicker = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText(
                                        text = "自定义",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (attentionCommandAvailable) Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.container(ContainerLevel.Card))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                                .clickable {
                                    attentionCommandChecked = !attentionCommandChecked
                                    onDraftChange(text, attentionCommandChecked)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppCheckbox(
                                checked = attentionCommandChecked,
                                onCheckedChange = {
                                    attentionCommandChecked = it
                                    onDraftChange(text, it)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = "内嵌关注按钮",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                AppText(
                                    text = "发送一个视频内嵌关注按钮",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 位置和大小选择 - 垂直布局避免拥挤
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 位置选择
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppText(
                                    text = "位置",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    modeOptions.forEach { (modeValue, label) ->
                                        val isSelected = selectedMode == modeValue
                                        AppFilterChip(
                                            selected = isSelected,
                                            onClick = { selectedMode = modeValue },
                                            label = {
                                                AppText(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }

                            // 大小选择
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppText(
                                    text = "大小",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    fontSizeOptions.forEach { (sizeValue, label) ->
                                        val isSelected = selectedFontSize == sizeValue
                                        AppFilterChip(
                                            selected = isSelected,
                                            onClick = { selectedFontSize = sizeValue },
                                            label = {
                                                AppText(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        }

                        // 发送按钮
                        AppButton(
                            onClick = {
                                if (text.isNotBlank() && !isSending) {
                                    onSend(
                                        text.trim(),
                                        selectedColor,
                                        selectedMode,
                                        selectedFontSize,
                                        attentionCommandChecked && attentionCommandAvailable
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = text.isNotBlank() && !isSending,
                            shape = AppShapes.container(ContainerLevel.Card),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                                contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                            )
                        ) {
                            if (isSending) {
                                AppCircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                AppText(
                                    text = "发送",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
            }
        }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
