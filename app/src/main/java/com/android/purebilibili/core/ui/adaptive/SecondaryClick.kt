package com.android.purebilibili.core.ui.adaptive

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 鼠标次键（右键）触发 [onClick] 并携带局部坐标下的按下位置。
 * 触屏不会产生次键按下，因此无需输入设备门控；消费事件避免
 * 后续 detectTapGestures 等手势处理器把右键当成点击。
 */
fun Modifier.onSecondaryClick(onClick: ((Offset) -> Unit)?): Modifier =
    if (onClick == null) this else pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    onClick(event.changes.first().position)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
