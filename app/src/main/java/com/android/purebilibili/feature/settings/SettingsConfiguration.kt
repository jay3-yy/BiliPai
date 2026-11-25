package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.store.AppNavigationSettings
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.PlayerInteractionSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.SettingsIconGlyphs

internal const val SETTINGS_ICON_GLYPHS_SETTING_ID = "appearance.settings_icon_glyphs"

internal val SettingsIconGlyphs.labelResId: Int
    get() = when (this) {
        SettingsIconGlyphs.PHOSPHOR -> R.string.appearance_settings_icon_glyphs_phosphor
        SettingsIconGlyphs.ORIGINAL -> R.string.appearance_settings_icon_glyphs_original
    }

// 导航宿主在真实配置加载后提供原始保存值，设置页首帧与当前外壳一致。
// 使用可跟踪的 local，只让读取对应配置的内容响应变化，不使整个导航子树失效。
internal val LocalSettingsHomeConfiguration = compositionLocalOf<HomeSettings?> { null }
internal val LocalSettingsNavigationConfiguration = compositionLocalOf<AppNavigationSettings?> { null }
internal val LocalSettingsThemeConfiguration = compositionLocalOf<AppThemeSettings?> { null }
internal val LocalSettingsPlayerConfiguration = compositionLocalOf<PlayerInteractionSettings?> { null }

@Composable
internal fun rememberSettingsThemeConfiguration(): AppThemeSettings {
    val provided = LocalSettingsThemeConfiguration.current
    if (provided != null) return provided
    val context = LocalContext.current
    val initialSettings = remember(context) { SettingsManager.getInitialAppThemeSettings(context) }
    val settings by remember(context) { SettingsManager.getAppThemeSettings(context) }
        .collectAsStateWithLifecycle(initialValue = initialSettings)
    return settings
}

@Composable
internal fun rememberSettingsPlayerConfiguration(): PlayerInteractionSettings {
    val provided = LocalSettingsPlayerConfiguration.current
    if (provided != null) return provided
    val context = LocalContext.current
    // 独立设置入口沿用倍速提示已有的同步缓存种子，不先覆盖为默认显示状态。
    val initialSettings = remember(context) {
        PlayerInteractionSettings(
            longPressSpeedHintHidden = SettingsManager.getLongPressSpeedHintHiddenSync(context),
            longPressSpeedHintScale = SettingsManager.getLongPressSpeedHintScaleSync(context),
            longPressSpeedHintAlpha = SettingsManager.getLongPressSpeedHintAlphaSync(context),
        )
    }
    val settings by remember(context) { SettingsManager.getPlayerInteractionSettings(context) }
        .collectAsStateWithLifecycle(initialValue = initialSettings)
    return settings
}

@Composable
internal fun rememberSettingsHomeConfiguration(): HomeSettings {
    val provided = LocalSettingsHomeConfiguration.current
    if (provided != null) return provided
    // 独立页面及预览没有导航宿主，保留原有存储读取路径。
    val context = LocalContext.current
    val settings by remember(context) { SettingsManager.getHomeSettings(context) }
        .collectAsStateWithLifecycle(initialValue = HomeSettings())
    return settings
}

@Composable
internal fun rememberSettingsNavigationConfiguration(): AppNavigationSettings {
    val provided = LocalSettingsNavigationConfiguration.current
    if (provided != null) return provided
    val context = LocalContext.current
    val settings by remember(context) { SettingsManager.getAppNavigationSettings(context) }
        .collectAsStateWithLifecycle(initialValue = AppNavigationSettings())
    return settings
}
