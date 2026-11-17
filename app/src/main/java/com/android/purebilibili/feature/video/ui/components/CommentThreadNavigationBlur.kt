package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.transition.navigationBlurBlend
import com.android.purebilibili.core.ui.transition.resolveNavigationBlurBlendFrame
import com.android.purebilibili.feature.settings.rememberSettingsNavigationConfiguration

/** Blur the covered comment content, never the foreground thread or its controls. */
@Composable
internal fun Modifier.commentThreadNavigationBlur(
    progress: () -> Float,
): Modifier {
    val enabled = rememberSettingsNavigationConfiguration().miuixTransitionBlurEnabled &&
        LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    if (!enabled) return this
    // 玻璃与原生共用；保留实时评论内容，连续权重只在绘制阶段读取。
    return navigationBlurBlend {
        resolveNavigationBlurBlendFrame(progress = progress())
    }
}
