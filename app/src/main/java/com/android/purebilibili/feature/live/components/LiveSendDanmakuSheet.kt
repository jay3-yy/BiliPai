package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.data.repository.LiveDanmakuPermission
import com.android.purebilibili.feature.live.LiveDanmakuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf

/** 弹幕默认颜色（白色） */
private const val LIVE_DANMAKU_DEFAULT_COLOR = 16777215
/** 弹幕默认模式（1 = 滚动） */
private const val LIVE_DANMAKU_DEFAULT_MODE = 1
/** 颜色选择器最多展示的色块数量 */
private const val LIVE_DANMAKU_MAX_COLOR_SWATCHES = 8

/**
 * 发送直播弹幕弹窗
 *
 * 支持选择弹幕颜色与滚动模式（依据服务端返回的权限 [LiveDanmakuPermission]），
 * 发送时携带 color / mode 字段；无权限时回退默认白色滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSendDanmakuSheet(
    onDismiss: () -> Unit,
    onSend: (String, Int, Int) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    permission: LiveDanmakuPermission = LiveDanmakuPermission(),
    replyTarget: LiveDanmakuItem? = null,
    onOpenEmote: (() -> Unit)? = null,
    isSending: Boolean = false,
    selectedColor: Int = LIVE_DANMAKU_DEFAULT_COLOR,
    selectedMode: Int = LIVE_DANMAKU_DEFAULT_MODE,
    onColorSelected: (Int) -> Unit = {},
    onModeSelected: (Int) -> Unit = {},
    onClearReply: () -> Unit = {},
    emoticonContent: (@Composable () -> Unit)? = null,
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(emoticonContent == null) {
        if (emoticonContent == null) { focusRequester.requestFocus(); keyboard?.show() } else keyboard?.hide()
    }
    androidx.compose.runtime.LaunchedEffect(permission.availableColors, permission.availableModes) {
        if (permission.availableColors.isNotEmpty() && permission.availableColors.none { it.color == selectedColor }) onColorSelected(permission.availableColors.first().color)
        if (permission.availableModes.isNotEmpty() && permission.availableModes.none { it.mode == selectedMode }) onModeSelected(permission.availableModes.first().mode)
    }
    val maxLength = permission.maxLength.takeIf { it > 0 } ?: 40
    var showStyle by rememberSaveable { mutableStateOf(false) }
    val canChooseStyle = permission.availableColors.isNotEmpty() && permission.availableModes.isNotEmpty()

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = AppSpacingTokens.ExtraLarge,
                    vertical = AppSpacingTokens.Small,
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Large)
        ) {
            AppText(
                text = "发弹幕",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            AppSurface(
                color = AppSurfaceTokens.cardContainer(),
                shape = AppShapes.container(ContainerLevel.Card)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacingTokens.Large),
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
                ) {
                    if (replyTarget != null) {
                        AppSurface(shape = AppShapes.container(ContainerLevel.Pill), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = AppSpacingTokens.Medium)) {
                                AppText("@${replyTarget.uname.ifBlank { replyTarget.uid.toString() }}", modifier = Modifier.weight(1f))
                                AppIconButton(onClick = onClearReply, enabled = !isSending) { AppIcon(Icons.Outlined.Close, "取消回复，保留正文") }
                            }
                        }
                    }
                    AppOutlinedTextField(
                        value = message,
                        onValueChange = { onMessageChange(it.take(maxLength)) },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = focusRequester,
                        enabled = !isSending,
                        minLines = 2,
                        maxLines = 4,
                        placeholderText = if (replyTarget == null) "输入弹幕内容" else "输入回复内容",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val content = message.trim()
                            if (content.isNotEmpty() && permission.canSend && !isSending) {
                                onSend(content, selectedColor, selectedMode)
                            }
                        })
                    )
                    if (canChooseStyle) {
                        com.android.purebilibili.core.ui.components.AppTextButton(onClick = { showStyle = !showStyle }) {
                            AppText(if (showStyle) "收起弹幕样式" else "弹幕样式")
                        }
                    }
                    if (canChooseStyle && showStyle) {
                        DanmakuColorSelector(
                            options = permission.availableColors,
                            selectedColor = selectedColor,
                            onColorSelected = onColorSelected,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DanmakuModeSelector(
                            options = permission.availableModes,
                            selectedMode = selectedMode,
                            onModeSelected = onModeSelected,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AppText(
                        text = buildString {
                            append(permission.statusText)
                            append(" · ")
                            append(message.length)
                            append("/")
                            append(maxLength)
                            if (permission.availableColors.isNotEmpty()) {
                                append(" · ")
                                append(permission.availableColors.take(3).joinToString("、") { it.name })
                            }
                            if (permission.availableModes.isNotEmpty()) {
                                append(" · ")
                                append(permission.availableModes.take(2).joinToString("、") { it.name })
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onOpenEmote != null) {
                            AppIconButton(enabled = !isSending, onClick = onOpenEmote) {
                                AppIcon(
                                    imageVector = Icons.Outlined.EmojiEmotions,
                                    contentDescription = "表情",
                                )
                            }
                        } else {
                            Box(Modifier)
                        }
                        AppButton(
                            enabled = !isSending && permission.canSend && message.trim().isNotEmpty(),
                            onClick = { onSend(message.trim(), selectedColor, selectedMode) }
                        ) {
                            AppText(if (isSending) "发送中…" else "发送")
                        }
                    }
                }
            }
            emoticonContent?.invoke()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DanmakuColorSelector(
    options: List<com.android.purebilibili.data.repository.LiveDanmakuColorOption>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
    ) {
        AppText(
            text = "弹幕颜色",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        // FlowRow：窄屏自动换行，避免 8 个色块横向溢出
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
        ) {
            options.take(LIVE_DANMAKU_MAX_COLOR_SWATCHES).forEach { option ->
                val argb = (0xFF000000L or option.color.toLong()).toInt()
                val isSelected = selectedColor == option.color
                val swatchColor = Color(argb)
                // 外层 48dp 触摸目标（满足最小交互尺寸），内层 24dp 视觉色块
                Box(
                    modifier = Modifier
                        .size(AppSpacingTokens.TripleExtraLarge)
                        .clickable(
                            role = Role.RadioButton,
                            onClickLabel = option.name,
                            onClick = { onColorSelected(option.color) }
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = option.name
                            this.selected = isSelected
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(AppSpacingTokens.ExtraLarge)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) {
                                    AppSpacingTokens.Micro
                                } else {
                                    AppSurfaceTokens.OutlineWidth
                                },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            AppIcon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                // 浅色色块用黑色勾，深色用白色勾，保证对比度
                                tint = if (swatchColor.luminance() > 0.5f) {
                                    Color.Black
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(AppSpacingTokens.Large)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DanmakuModeSelector(
    options: List<com.android.purebilibili.data.repository.LiveDanmakuModeOption>,
    selectedMode: Int,
    onModeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only render a selector when the room actually offers a choice.
    if (options.size == 1) {
        AppText(
            text = "弹幕模式：${options.single().name}",
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
        ) {
            AppText(
                text = "弹幕模式",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
            ) {
                options.forEach { option ->
                    AppFilterChip(
                        selected = selectedMode == option.mode,
                        onClick = { onModeSelected(option.mode) },
                        label = { AppText(option.name) },
                    )
                }
            }
        }
    }
}
