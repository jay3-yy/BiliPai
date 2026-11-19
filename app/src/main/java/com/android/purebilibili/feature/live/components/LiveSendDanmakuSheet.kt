package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.repository.LiveDanmakuPermission
import com.android.purebilibili.feature.live.LiveDanmakuItem

/** 页面底部共用的弹幕编辑器；键盘、表情共用草稿，成功发送后由调用方清理。 */
@Composable
fun LiveSendDanmakuSheet(
    onDismiss: () -> Unit,
    onSend: (String, Int, Int) -> Unit,
    message: TextFieldValue,
    onMessageChange: (TextFieldValue) -> Unit,
    permission: LiveDanmakuPermission = LiveDanmakuPermission(),
    replyTarget: LiveDanmakuItem? = null,
    onOpenEmote: (() -> Unit)? = null,
    isSending: Boolean = false,
    onClearReply: () -> Unit = {},
    emoticonContent: (@Composable () -> Unit)? = null,
    emoticonPackages: List<com.android.purebilibili.data.repository.LiveEmoticonPackage> = emptyList(),
    modifier: Modifier = Modifier,
    medalContent: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val showingEmoticons = emoticonContent != null
    val maxLength = permission.maxLength.takeIf { it > 0 } ?: 40
    val canSend = !isSending && permission.canSend && message.text.isNotBlank() && message.text.length <= maxLength
    val dismiss = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onDismiss()
    }
    val send: (String) -> Unit = { text ->
        if (!isSending && permission.canSend && text.isNotBlank() && text.length <= maxLength) {
            onSend(text.trim(), 16777215, 1)
        }
    }
    LocalNavigationBackHandler(enabled = true) { dismiss() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val panelHeight = maxHeight * 0.5f
        Box(Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "关闭输入，保留草稿",
            onClick = dismiss,
        ))
        AppSurface(
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = AppSurfaceTokens.surface(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = { onOpenEmote?.invoke() }, enabled = !isSending) {
                        AppIcon(Icons.Outlined.EmojiEmotions, if (showingEmoticons) "切换键盘" else "表情",
                            tint = if (showingEmoticons) AppContentColors.accent() else AppContentColors.secondaryText())
                    }
                    Spacer(Modifier.width(12.dp))
                    LiveRichDanmakuEditor(
                        value = message,
                        onValueChange = onMessageChange,
                        replyName = replyTarget?.uname,
                        onClearReply = onClearReply,
                        packages = emoticonPackages,
                        showingEmoticons = showingEmoticons,
                        isSending = isSending,
                        onSend = send,
                        modifier = Modifier.weight(1f),
                    )
                    if (replyTarget != null) {
                        AppIconButton(onClick = onClearReply, enabled = !isSending, modifier = Modifier.size(30.dp)) {
                            AppIcon(Icons.Outlined.Close, "取消回复，保留正文")
                        }
                    }
                    if (message.text.isNotEmpty()) {
                        AppIconButton(onClick = { onMessageChange(TextFieldValue("")) }, enabled = !isSending) {
                            AppIcon(Icons.Outlined.ClearAll, "清空正文，保留回复对象")
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    AppIconButton(onClick = { send(message.text) }, enabled = canSend) {
                        AppIcon(Icons.AutoMirrored.Outlined.Send, if (isSending) "发送中" else "发送",
                            tint = if (canSend) AppContentColors.accent() else AppContentColors.secondaryText())
                    }
                }
                if (medalContent != null) {
                    Box(Modifier.padding(horizontal = 16.dp)) { medalContent() }
                }
                if (!permission.canSend || message.text.length > maxLength) {
                    AppText(
                        if (!permission.canSend) permission.statusText else "弹幕最多 $maxLength 字",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = AppContentColors.secondaryText(), fontSize = 12.sp,
                    )
                }
                if (emoticonContent != null) {
                    Box(Modifier.fillMaxWidth().heightIn(max = panelHeight)) { emoticonContent() }
                }
            }
        }
    }
}
