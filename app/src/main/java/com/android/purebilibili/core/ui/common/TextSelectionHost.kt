@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.core.ui.common

import com.android.purebilibili.core.ui.components.AppSurface

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

/**
 * 文本选择请求数据
 */
data class TextSelectionRequest(
    val text: String,
    val title: String? = null,
)

/**
 * 原地悬浮选择条状态
 */
data class AppSelectionToolbarState(
    val visible: Boolean = false,
    val rect: Rect = Rect.Zero,
    val onCopy: (() -> Unit)? = null,
    val onSelectAll: (() -> Unit)? = null,
)

data class AppSelectedTextAction(val label: String, val onClick: (String) -> Unit)

/**
 * 原地悬浮选择栏位置计算器，保证在选区正上方展示，顶边不够则翻转至正下方，水平自动留出边距居中
 */
private class SelectionToolbarPositionProvider(
    private val rect: Rect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = rect.left.toInt() + (rect.width.toInt() / 2)
        val popupWidth = popupContentSize.width
        val x = (anchorCenterX - popupWidth / 2).coerceIn(
            16,
            (windowSize.width - popupWidth - 16).coerceAtLeast(16)
        )

        val spacing = 20
        val yAbove = rect.top.toInt() - popupContentSize.height - spacing
        val y = if (yAbove >= 48) {
            yAbove
        } else {
            (rect.bottom.toInt() + spacing).coerceAtMost(
                (windowSize.height - popupContentSize.height - 48).coerceAtLeast(16)
            )
        }
        return IntOffset(x, y)
    }
}

/**
 * 全局现代 Miuix 风格悬浮选择栏
 */
@Composable
internal fun AppFloatingSelectionToolbar(
    rect: Rect,
    selectedText: String,
    onCopy: (() -> Unit)?,
    onSelectAll: (() -> Unit)?,
    onOpenSheet: (() -> Unit)?,
    onDismiss: () -> Unit,
    additionalActions: List<AppSelectedTextAction> = emptyList(),
) {
    val context = LocalContext.current
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.TEXT)
    val positionProvider = remember(rect) { SelectionToolbarPositionProvider(rect) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true
        )
    ) {
        DisableSelection {
            AppSurface(
                shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = (if (additionalActions.isEmpty()) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 实时字数气泡角标（手指按住划选时即时看到选中字数）
                val characterCount = selectedText.length
                if (characterCount > 0) {
                    AppSurface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                    ) {
                        AppText(
                            text = TextSelectionPolicy.formatSelectedCountBadge(characterCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // 1. 原地实时复制（核心主路径，零弹窗）
                if (onCopy != null) {
                    AppTextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val copiedLength = selectedText.length
                            onCopy.invoke()
                            onDismiss()
                            Toast.makeText(
                                context,
                                TextSelectionPolicy.formatInPlaceCopyFeedback(copiedLength),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        AppText(
                            text = "复制",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 2. 弹窗选择（作为额外 / 高级功能保留）
                if (onOpenSheet != null && additionalActions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    AppTextButton(
                        onClick = {
                            onOpenSheet.invoke()
                            onDismiss()
                        }
                    ) {
                        AppText(
                            text = "弹窗选择",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 3. 全选
                if (onSelectAll != null) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    AppTextButton(
                        onClick = {
                            onSelectAll.invoke()
                        }
                    ) {
                        AppIcon(
                            imageVector = Icons.Filled.SelectAll,
                            contentDescription = "全选",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        AppText(
                            text = "全选",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                additionalActions.forEach { action ->
                    AppTextButton(onClick = {
                        onDismiss()
                        action.onClick(selectedText)
                    }) {
                        AppText(action.label, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 4. 分享
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                AppTextButton(
                    onClick = {
                        val textToShare = selectedText
                        onDismiss()
                        if (textToShare.isNotEmpty()) {
                            try {
                                val shareIntent = TextSelectionPolicy.createShareIntent(textToShare, "分享所选内容")
                                context.startActivity(shareIntent)
                            } catch (_: Exception) {
                            }
                        }
                    }
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
}

/**
 * 全局文本选择控制器，提供在应用任何位置呼出部分选择底栏的能力
 */
interface TextSelectionController {
    val activeRequest: TextSelectionRequest?
    fun show(text: String, title: String? = null)
    fun dismiss()
}

internal class DefaultTextSelectionController : TextSelectionController {
    override var activeRequest by mutableStateOf<TextSelectionRequest?>(null)
        private set

    override fun show(text: String, title: String?) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            activeRequest = TextSelectionRequest(text = trimmed, title = title)
        }
    }

    override fun dismiss() {
        activeRequest = null
    }
}

/**
 * CompositionLocal 提供者
 */
val LocalTextSelectionController = staticCompositionLocalOf<TextSelectionController?> { null }

/** 保留原生选择工具栏，只将文字选择触感绑定到文字场景。 */
@Composable
fun AppNativeSelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
        SelectionContainer(modifier = modifier, content = content)
    }
}

/** 只为静态文本定制工具栏；输入框继续使用系统的剪切、粘贴和自动填充菜单。 */
@Composable
fun AppSelectionContainer(
    modifier: Modifier = Modifier,
    state: SelectionState = rememberSelectionState(),
    additionalActions: (String) -> List<AppSelectedTextAction> = { emptyList() },
    content: @Composable () -> Unit,
) {
    val selection = state
    val controller = LocalTextSelectionController.current
    var toolbarState by remember { mutableStateOf(AppSelectionToolbarState()) }
    val customToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (toolbarState.visible) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                toolbarState = AppSelectionToolbarState(
                    visible = true,
                    rect = rect,
                    onCopy = onCopyRequested,
                    onSelectAll = onSelectAllRequested,
                )
            }

            override fun hide() {
                toolbarState = AppSelectionToolbarState()
            }
        }
    }
    val textHaptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.TEXT)
    CompositionLocalProvider(LocalTextToolbar provides customToolbar, LocalHapticFeedback provides textHaptic) {
        SelectionContainer(state = selection, modifier = modifier, content = content)
    }
    // 与官方复制行为相同，跨 Text 节点时用换行连接；始终读取当前容器的选区。
    val selectedText = selection.selectedTexts.joinToString("\n") { it.text }
    if (toolbarState.visible && selectedText.isNotEmpty()) {
        AppFloatingSelectionToolbar(
            rect = toolbarState.rect,
            selectedText = selectedText,
            onCopy = toolbarState.onCopy,
            onSelectAll = toolbarState.onSelectAll,
            onOpenSheet = controller?.let { host ->
                { host.show(selectedText, "所选内容") }
            },
            onDismiss = customToolbar::hide,
            additionalActions = additionalActions(selectedText),
        )
    }
}

/** 在顶层提供部分选择底栏，不覆盖输入框的系统工具栏。 */
@Composable
fun ProvideAppTextSelectionHost(content: @Composable () -> Unit) {
    val controller = remember { DefaultTextSelectionController() }
    CompositionLocalProvider(LocalTextSelectionController provides controller) {
        content()
        controller.activeRequest?.let { request ->
            DisableSelection {
                TextSelectionBottomSheet(
                    text = request.text,
                    title = request.title,
                    onDismiss = { controller.dismiss() },
                )
            }
        }
    }
}
