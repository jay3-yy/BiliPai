package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalAppButtonSlotColorCorrectionEnabled
import com.android.purebilibili.core.theme.ACCESSIBLE_TEXT_MIN_CONTRAST
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.theme.resolveReadableTextColor
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.delay

private val LandscapeDanmakuSurfaceColor = Color(0xFF0B0B0B)
private val LandscapeDanmakuInputColor = Color(0xFFF4F5F7)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlassLandscapeDanmakuComposer(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSend: (message: String, color: Int, mode: Int, fontSize: Int, attentionCommand: Boolean) -> Unit,
    isSending: Boolean,
    initialColor: Int,
    initialMode: Int,
    initialFontSize: Int,
    initialText: String,
    initialAttentionCommand: Boolean,
    onDraftChange: (String, Boolean) -> Unit,
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var nativeClosing by remember { mutableStateOf(false) }
    val latestVisible by rememberUpdatedState(visible)
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) {
        nativeClosing = false
    }
    com.android.purebilibili.core.ui.AppModalBottomSheet(
        visible = visible,
        onDismissStarted = { nativeClosing = true },
        onDismissRequest = { if (latestVisible) latestDismiss() },
        modifier = modifier,
        presentationOverride = com.android.purebilibili.core.ui.AppModalPresentation.BottomSheet,
        sheetSurfaceModifier = Modifier.imePadding(),
    ) {
        LandscapeDanmakuComposerEditor(
            visible = visible && !nativeClosing,
            onSend = { message, color, mode, size, attention ->
                if (visible && !nativeClosing) onSend(message, color, mode, size, attention)
            },
            isSending = isSending,
            initialColor = initialColor,
            initialMode = initialMode,
            initialFontSize = initialFontSize,
            initialText = initialText,
            initialAttentionCommand = initialAttentionCommand,
            onDraftChange = onDraftChange,
            onSelectionChange = onSelectionChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LandscapeDanmakuComposer(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSend: (message: String, color: Int, mode: Int, fontSize: Int, attentionCommand: Boolean) -> Unit,
    isSending: Boolean,
    initialColor: Int,
    initialMode: Int,
    initialFontSize: Int,
    initialText: String,
    initialAttentionCommand: Boolean,
    onDraftChange: (String, Boolean) -> Unit,
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Only the glass editor changes; native keeps its existing dialog, insets, and defaults.
    if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
        GlassLandscapeDanmakuComposer(
            visible = visible,
            onDismiss = onDismiss,
            onSend = onSend,
            isSending = isSending,
            initialColor = initialColor,
            initialMode = initialMode,
            initialFontSize = initialFontSize,
            initialText = initialText,
            initialAttentionCommand = initialAttentionCommand,
            onDraftChange = onDraftChange,
            onSelectionChange = onSelectionChange,
            modifier = modifier,
        )
    } else {
        PlainLandscapeDanmakuComposer(
            visible = visible,
            onDismiss = onDismiss,
            onSend = onSend,
            isSending = isSending,
            initialColor = initialColor,
            initialMode = initialMode,
            initialFontSize = initialFontSize,
            initialText = initialText,
            initialAttentionCommand = initialAttentionCommand,
            onDraftChange = onDraftChange,
            onSelectionChange = onSelectionChange,
            modifier = modifier,
        )
    }
}

/** Shared editor; glass and native retain their own modal hosts. */
@Composable
internal fun LandscapeDanmakuComposerEditor(
    visible: Boolean,
    onSend: (message: String, color: Int, mode: Int, fontSize: Int, attentionCommand: Boolean) -> Unit,
    isSending: Boolean,
    initialColor: Int,
    initialMode: Int,
    initialFontSize: Int,
    initialText: String,
    initialAttentionCommand: Boolean,
    onDraftChange: (String, Boolean) -> Unit,
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit,
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
    var showStylePanel by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    var lastCustomColor by remember { mutableIntStateOf(0x66CCFF) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    DisposableEffect(keyboardController, focusManager) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val selection = resolveDanmakuSendSelectionState(
            initialColor = initialColor,
            initialMode = initialMode,
            initialFontSize = initialFontSize,
            colorOptions = colorOptions.map { it.value },
            modeOptions = modeOptions.map { it.value },
            fontSizeOptions = fontSizeOptions.map { it.value },
        )
        text = initialText
        selectedColor = selection.color
        selectedMode = selection.mode
        selectedFontSize = selection.fontSize
        attentionCommandChecked = initialAttentionCommand
    }

    LaunchedEffect(visible, showStylePanel, showCustomColorPicker) {
        if (!visible || showStylePanel || showCustomColorPicker) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else {
            delay(80)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(selectedColor, selectedMode, selectedFontSize, visible) {
        if (!visible) return@LaunchedEffect
        onSelectionChange(selectedColor, selectedMode, selectedFontSize)
    }

    val requestedAccent = AppContentColors.accent()
    val inverseAccent = MaterialTheme.colorScheme.inversePrimary
    val accent = remember(requestedAccent, inverseAccent) {
        if (calculateContrastRatio(requestedAccent, LandscapeDanmakuSurfaceColor) >= ACCESSIBLE_TEXT_MIN_CONTRAST) {
            requestedAccent
        } else {
            resolveReadableTextColor(
                candidate = inverseAccent,
                background = LandscapeDanmakuSurfaceColor,
                fallback = Color.White,
                minimumContrast = ACCESSIBLE_TEXT_MIN_CONTRAST,
            )
        }
    }
    val inputCursor = remember(requestedAccent) {
        SolidColor(
            resolveReadableTextColor(
                candidate = requestedAccent,
                background = LandscapeDanmakuInputColor,
                fallback = Color(0xFF242424),
                minimumContrast = ACCESSIBLE_TEXT_MIN_CONTRAST,
            )
        )
    }
    val secondaryText = Color.White.copy(alpha = 0.62f)
    val sendMessage = {
        if (visible && text.isNotBlank() && !isSending) {
            onSend(text.trim(), selectedColor, selectedMode, selectedFontSize, attentionCommandChecked)
        }
    }
    CompositionLocalProvider(LocalAppButtonSlotColorCorrectionEnabled provides false) {
    Column(
        modifier = modifier
            .background(LandscapeDanmakuSurfaceColor)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIconButton(
                onClick = {
                    if (!showStylePanel) {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    }
                    showStylePanel = !showStylePanel
                },
                modifier = Modifier.size(48.dp).testTag("danmaku_landscape_style_toggle"),
            ) {
                AppText(
                    text = "A",
                    color = if (showStylePanel) accent else Color.White,
                    fontSize = 28.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.semantics { contentDescription = "弹幕样式" },
                )
            }

            BasicTextField(
                value = text,
                onValueChange = {
                    if (it.length <= 100) {
                        text = it
                        onDraftChange(it, attentionCommandChecked)
                    }
                },
                modifier = Modifier.weight(1f)
                    .testTag("danmaku_landscape_composer_input")
                    .focusRequester(focusRequester)
                    .pointerInput(isSending) {
                        if (!isSending) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                showStylePanel = false
                            }
                        }
                    }
                    .semantics {
                        onClick {
                            if (isSending) {
                                false
                            } else {
                                showStylePanel = false
                                focusRequester.requestFocus()
                                keyboardController?.show()
                                true
                            }
                        }
                    },
                enabled = visible && !isSending,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF242424)),
                cursorBrush = inputCursor,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp))
                            .background(LandscapeDanmakuInputColor)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            AppText(
                                "发个友善的弹幕见证当下",
                                color = Color(0xFFB9BABD),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            AppIconButton(
                onClick = sendMessage,
                enabled = visible && text.isNotBlank() && !isSending,
                modifier = Modifier.size(48.dp),
            ) {
                if (isSending) {
                    AppCircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                } else {
                    AppIcon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = "发送弹幕",
                        tint = if (text.isNotBlank()) Color.White else secondaryText,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        if (showStylePanel) {
            Column(
                modifier = Modifier.weight(1f, fill = false)
                    .fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LandscapeDanmakuStyleRow("字体大小") {
                    fontSizeOptions.forEach { option ->
                        LandscapeDanmakuStyleOption(
                            label = option.label,
                            selected = selectedFontSize == option.value,
                            onClick = { selectedFontSize = option.value },
                            accent = accent,
                        ) { tint ->
                            AppText("A", color = tint, fontSize = (option.value / 2 + 8).sp)
                        }
                    }
                }

                LandscapeDanmakuStyleRow("弹幕位置") {
                    modeOptions.forEach { option ->
                        LandscapeDanmakuStyleOption(
                            label = when (option.value) {
                                5 -> "置顶"
                                4 -> "置底"
                                else -> option.label
                            },
                            selected = selectedMode == option.value,
                            onClick = { selectedMode = option.value },
                            accent = accent,
                        ) { tint ->
                            Canvas(Modifier.size(20.dp)) {
                                val y = when (option.value) {
                                    5 -> size.height * 0.2f
                                    4 -> size.height * 0.8f
                                    else -> size.height * 0.5f
                                }
                                drawLine(tint, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                                if (option.value == 1) {
                                    drawLine(tint, Offset(0f, y - 5.dp.toPx()), Offset(size.width * 0.6f, y - 5.dp.toPx()), 2.dp.toPx())
                                }
                            }
                        }
                    }
                }

                LandscapeDanmakuStyleRow("弹幕颜色") {
                    colorOptions.forEach { option ->
                        val selected = selectedColor == option.value
                        val gradient = option.value == DANMAKU_SEND_VIP_GRADUAL_COLOR
                        val swatch = if (gradient) {
                            Brush.linearGradient(listOf(Color(0xFFFF8FC7), Color(0xFF6DCBFF)))
                        } else {
                            SolidColor(Color(0xFF000000.toInt() or option.value))
                        }
                        Box(
                            modifier = Modifier.size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(swatch)
                                .border(2.dp, if (selected) accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .selectable(selected, onClick = { selectedColor = option.value }, role = Role.RadioButton)
                                .semantics { contentDescription = option.label },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (gradient) {
                                AppText("会员", color = Color(0xFF242424), fontSize = 10.sp)
                            }
                            if (selected) {
                                AppIcon(
                                    Icons.Outlined.Check,
                                    contentDescription = "已选",
                                    tint = accent,
                                    modifier = Modifier.size(22.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                    LandscapeDanmakuStyleOption(
                        label = "自定义",
                        selected = selectedColor >= 0 && colorOptions.none { it.value == selectedColor },
                        onClick = { showCustomColorPicker = true },
                        accent = accent,
                    ) { tint ->
                        AppIcon(Icons.Outlined.Palette, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }

                if (showAdvancedOptions) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                attentionCommandChecked = !attentionCommandChecked
                                onDraftChange(text, attentionCommandChecked)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCheckbox(
                            checked = attentionCommandChecked,
                            onCheckedChange = {
                                attentionCommandChecked = it
                                onDraftChange(text, it)
                            },
                        )
                        AppText("内嵌关注按钮", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    AppTextButton(
                        onClick = { showAdvancedOptions = true },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        AppText("更多发送选项", color = secondaryText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    }

    if (visible && showCustomColorPicker) {
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
}

@Composable
private fun LandscapeDanmakuStyleRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(
            label,
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun LandscapeDanmakuStyleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color,
    symbol: @Composable (Color) -> Unit,
) {
    val tint = if (selected) accent else Color.White
    Row(
        modifier = Modifier.selectable(selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp)
                .border(2.dp, tint, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            symbol(tint)
        }
        AppText(label, color = tint, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
