package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import com.android.purebilibili.core.ui.AppActionHaptics
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.rememberAppHapticFeedback

/** 长按按功能场景派发一次反馈；短按、双击继续由调用方决定。 */
@Composable
fun Modifier.appCombinedClickable(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    longPressScene: AppHapticScene = AppHapticScene.LONG_PRESS,
    onClick: () -> Unit,
): Modifier {
    val longClick = onLongClick?.let { appLongClickWithFeedback(it, longPressScene) }
    return combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = longClick,
        onDoubleClick = onDoubleClick,
        hapticFeedbackEnabled = false,
        onClick = onClick,
    )
}

@Composable
fun appLongClickWithFeedback(
    onLongClick: () -> Unit,
    scene: AppHapticScene = AppHapticScene.LONG_PRESS,
): () -> Unit {
    val haptic = rememberAppHapticFeedback(scene)
    return {
        AppActionHaptics.runAction {
            onLongClick()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}
