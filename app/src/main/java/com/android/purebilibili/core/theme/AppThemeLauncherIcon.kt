package com.android.purebilibili.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.resolveBiliPaiThemeIconIndex
import com.android.purebilibili.core.store.syncAppThemeLauncherIcon

internal val LocalAppThemeIconPaletteIndex = staticCompositionLocalOf {
    com.android.purebilibili.core.store.DEFAULT_BILIPAI_THEME_ICON_INDEX
}

internal val LocalAppThemeIconPrimary = staticCompositionLocalOf { Color.Unspecified }

/** 独立图标配色与应用主色在此合流，预览与桌面始终使用同一个匹配索引。 */
@Composable
internal fun rememberAppThemeIconPaletteIndex(primary: Color): Int {
    val context = LocalContext.current.applicationContext
    val customFlow = remember(context) {
        com.android.purebilibili.core.store.getBiliPaiIconColorSettings(context)
    }
    val colors by customFlow.collectAsStateWithLifecycle(
        initialValue = com.android.purebilibili.core.store.getBiliPaiIconColorSettingsSync(context),
    )
    return resolveBiliPaiThemeIconIndex(colors.foreground ?: primary.toArgb(), colors.background)
}

/** 仅应用主窗口调用，使用最终色板端点；取色预览和主题动画帧不写桌面状态。 */
@Composable
internal fun SyncAppThemeLauncherIcon(paletteIndex: Int) {
    val context = LocalContext.current.applicationContext
    val iconFlow = remember(context) { SettingsManager.getAppIcon(context) }
    val icon by iconFlow.collectAsStateWithLifecycle(initialValue = SettingsManager.getAppIconSync(context))
    val splashFlow = remember(context) { SettingsManager.getSplashIconAnimationEnabled(context) }
    val splashVisible by splashFlow.collectAsStateWithLifecycle(
        initialValue = SettingsManager.isSplashIconAnimationEnabledSync(context),
    )
    LaunchedEffect(context, paletteIndex, icon, splashVisible) {
        syncAppThemeLauncherIcon(context, paletteIndex)
    }
}
