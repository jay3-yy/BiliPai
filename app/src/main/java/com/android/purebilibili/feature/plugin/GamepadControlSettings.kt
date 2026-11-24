package com.android.purebilibili.feature.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.feature.settings.GamepadControlSettingsBody

/**
 * 插件中心内的手柄控制配置入口：直接复用「设置 → 手柄控制」的完整设置体，保持两端配置 100% 同步。
 */
@Composable
internal fun GamepadControlSettings(
    @Suppress("UNUSED_PARAMETER") plugin: GamepadControlPlugin,
    modifier: Modifier = Modifier,
) {
    GamepadControlSettingsBody(
        showMasterSwitch = false,
        modifier = modifier,
    )
}
