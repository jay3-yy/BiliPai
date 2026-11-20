@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.core.ui.common

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

/**
 * 现代全宽沉浸式文本选择底栏。
 *
 * 基于只读 [BasicTextField] 核心，彻底消除列表与外层手势竞争，提供稳定顺滑的水滴拖拽游标。
 * 底部操作栏动态感知选区变化，当选定局部文本时高亮展示【复制所选 (X字)】，杜绝手势冲突与无法单独复制的问题。
 */
@Composable
fun TextSelectionBottomSheet(
    text: String,
    title: String? = null,
    onDismiss: () -> Unit,
) {
    if (!TextSelectionPolicy.shouldShowActions(text)) return

    val context = LocalContext.current
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.TEXT)
    val resolvedTitle = TextSelectionPolicy.resolveTitle(title)
    val focusRequester = remember { FocusRequester() }

    // 默认初始全选，呈现水滴游标供用户直接拖动截取
    var textFieldValue by remember(text) {
        mutableStateOf(
            TextFieldValue(
                text = text,
                selection = TextRange(0, text.length)
            )
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 拦截文本区域内部手势到达边界后的剩余位移，避免向下拖拽选区或滑动时意外触发 BottomSheet 下拉关闭手势冲突
    val textNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) {
                    return Offset.Zero
                }
                return Offset(x = 0f, y = available.y)
            }
        }
    }

    val selectedText = remember(textFieldValue.selection, text) {
        TextSelectionPolicy.extractSelectedText(
            fullText = text,
            start = textFieldValue.selection.start,
            end = textFieldValue.selection.end
        )
    }

    val hasPartialSelection = remember(selectedText, text) {
        TextSelectionPolicy.isPartialSelection(
            selectedLength = selectedText.length,
            totalLength = text.length
        )
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        DisableSelection {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // M3 路径的 modalWindowInsets 已消费导航栏 insets（此处为 0）；
                    // 这层 padding 是给 CenteredDialog/平板限宽弹层路径兜底的，勿删。
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            // 顶部标题与关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                AppIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AppText(
                text = TextSelectionPolicy.resolveSelectionHint(selectedText.length, text.length),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 核心原生无冲突文本选择区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Measure the title and actions first; only the text viewport may shrink.
                    .weight(1f, fill = false)
                    .heightIn(min = 90.dp, max = 340.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .nestedScroll(textNestedScrollConnection)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 底部操作栏（智能联动选区）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPartialSelection) {
                    // 当有局部选区时，主按钮强化为“复制所选”
                    AppButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copyPlainTextToClipboard(context, selectedText, "所选内容")
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                Toast.makeText(context, "已复制所选内容", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        AppText("复制所选 (${selectedText.length}字)")
                    }

                    AppTextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copyPlainTextToClipboard(context, text, resolvedTitle)
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                Toast.makeText(context, "已复制全部内容", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    ) {
                        AppText("复制全部")
                    }
                } else {
                    // 全选或未缩小时，主按钮为复制全部
                    AppButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copyPlainTextToClipboard(context, text, resolvedTitle)
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                Toast.makeText(context, "已复制全部内容", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        AppText("复制全部")
                    }

                    if (selectedText.length < text.length) {
                        AppTextButton(
                            onClick = {
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, text.length)
                                )
                            }
                        ) {
                            AppIcon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            AppText("全选")
                        }
                    }
                }

                AppIconButton(
                    onClick = {
                        try {
                            val shareTarget = if (hasPartialSelection) selectedText else text
                            val shareIntent = TextSelectionPolicy.createShareIntent(shareTarget, resolvedTitle)
                            context.startActivity(shareIntent)
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
}

/**
 * 兼容旧命名别名
 */
@Composable
fun CopySelectionBottomSheet(
    text: String,
    title: String? = null,
    onDismiss: () -> Unit,
) {
    TextSelectionBottomSheet(
        text = text,
        title = title,
        onDismiss = onDismiss,
    )
}
