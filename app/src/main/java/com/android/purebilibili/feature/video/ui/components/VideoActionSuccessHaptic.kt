package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberVisibleHapticFeedback

/** 成功事件即时确认；页面隐藏、后台或失焦时丢弃触感，不在恢复页面后补振。 */
@Composable
internal fun rememberVideoActionSuccessHaptic(isVisible: Boolean = true): () -> Unit {
    val haptic = rememberVisibleHapticFeedback(AppHapticScene.VIDEO_ACTIONS, isVisible)
    return remember(haptic) { { haptic(HapticType.MEDIUM) } }
}
