package com.android.purebilibili.core.ui.motion

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 依据当前系统主题自适应应用 Miuix 官方触控按压反馈。
 *
 * Miuix 底座在液态玻璃开启与关闭时均保留 0.94x 下沉微缩与 Folme 回弹；
 * Material 3 路径保持 No-op。
 *
 * @param enabled 是否开启按压反馈
 * @param feedbackType 反馈效果类型，默认 [PressFeedbackType.Sink]
 * @param interactionSource 交互事件流；若不传入则在内部自动创建并维持
 */
@Composable
fun Modifier.adaptiveMiuixPressFeedback(
    enabled: Boolean = true,
    feedbackType: PressFeedbackType = PressFeedbackType.Sink,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    if (!enabled || feedbackType == PressFeedbackType.None) return this
    if (com.android.purebilibili.core.theme.LocalAppUiStyle.current != com.android.purebilibili.core.theme.AppUiStyle.MIUIX) return this

    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = remember(feedbackType) {
        when (feedbackType) {
            PressFeedbackType.None -> null
            PressFeedbackType.Sink -> SinkFeedback()
            PressFeedbackType.Tilt -> TiltFeedback()
        }
    }

    return this.pressable(
        interactionSource = resolvedInteractionSource,
        indication = feedback,
        delay = null,
    )
}
