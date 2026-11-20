package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.android.purebilibili.core.ui.LocalAppThemeConfig

private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

@Composable
internal fun ProvideAppMiuixHapticFeedback(
    scene: com.android.purebilibili.core.ui.AppHapticScene = com.android.purebilibili.core.ui.AppHapticScene.CONTROLS,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val platformHaptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(scene)
    val effectiveHaptic = if (enabled && LocalAppThemeConfig.current.hapticFeedbackEnabled) {
        platformHaptic
    } else {
        NoOpHapticFeedback
    }
    CompositionLocalProvider(LocalHapticFeedback provides effectiveHaptic, content = content)
}
