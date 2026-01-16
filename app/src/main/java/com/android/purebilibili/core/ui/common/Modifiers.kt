package com.android.purebilibili.core.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable

/**
 *  长按复制文本修饰符
 * 
 * @param text 要复制的文本内容
 * @param label 复制成功提示中显示的文本描述（可选，例如 "视频链接"）
 */
fun Modifier.copyOnLongPress(
    text: String,
    label: String? = null
): Modifier = composed {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    
    pointerInput(text) {
        detectTapGestures(
            onLongPress = {
                // 1. 震动反馈
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                
                // 2. 写入剪贴板
                clipboardManager.setText(AnnotatedString(text))
                
                // 3. 兼容旧版剪贴板提示 (Android 13+ 系统自带提示，12及以下需要手动提示)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    val toastMsg = if (label != null) "已复制 $label" else "已复制到剪贴板"
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * 单击复制文本修饰符
 *
 * @param text 要复制的文本内容
 * @param label 复制成功提示中显示的文本描述（可选）
 */
fun Modifier.copyOnClick(
    text: String,
    label: String? = null
): Modifier = composed {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    clickable {
        // 1. 震动反馈
        haptic.performHapticFeedback(HapticFeedbackType.LongPress) // 使用长按震动感增加确认感

        // 2. 写入剪贴板
        clipboardManager.setText(AnnotatedString(text))

        // 3. 提示
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            val toastMsg = if (label != null) "已复制 $label" else "已复制到剪贴板"
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        } else {
            // Android 13+ 也可以显示一个简短提示确认
             val toastMsg = if (label != null) "已复制 $label" else "已复制"
             Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
