package com.android.purebilibili.feature.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.android.purebilibili.core.theme.alignMaterialSurfacesWithMiuix
import com.android.purebilibili.core.theme.resolveNativeMiuixColors
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppIconStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.ProvideAppContentColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 直播间保留深色播放配色，菜单材质跟随用户液态玻璃开关。 */
internal fun liveRoomThemeConfig(appConfig: AppThemeConfig): AppThemeConfig = appConfig.copy(
    liquidControlsUseThemeColor = false,
    nativeMiuixPopupsEnabled = false,
    headerBlurEnabled = false,
    bottomBarBlurEnabled = false,
    progressiveTopBlurEnabled = false,
    progressiveTopFadeEnabled = false,
)

@Composable
internal fun LiveRoomTheme(
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    iconStyle: AppIconStyle = AppIconStyle.MD3_STANDARD,
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme
    val roomColors = remember(accent.primary, accent.onPrimary, accent.secondary, accent.tertiary) {
        darkColorScheme(
            primary = accent.primary,
            onPrimary = accent.onPrimary,
            secondary = accent.secondary,
            tertiary = accent.tertiary,
        )
    }
    val config = liveRoomThemeConfig(LocalAppThemeConfig.current)
    // 原生弹幕菜单跟随弹窗材质，独立于播放器按钮的玻璃开关。
    val sceneConfig = if (uiStyle == AppUiStyle.MIUIX) config.copy(liquidGlassEnabled = false) else config
    CompositionLocalProvider(
        LocalAppThemeConfig provides sceneConfig,
        LocalAppUiStyle provides uiStyle,
        LocalAppIconStyle provides iconStyle,
    ) {
        if (uiStyle == AppUiStyle.MIUIX) {
            val nativeColors = remember(roomColors) { resolveNativeMiuixColors(roomColors, darkTheme = true) }
            val bridgedColors = remember(roomColors, nativeColors) {
                alignMaterialSurfacesWithMiuix(roomColors, nativeColors)
            }
            MiuixTheme(colors = nativeColors) {
                MaterialTheme(colorScheme = bridgedColors) {
                    ProvideAppContentColors(nativeInteractionColors = true, content = content)
                }
            }
        } else {
            MaterialTheme(colorScheme = roomColors) {
                ProvideAppContentColors(nativeInteractionColors = false, content = content)
            }
        }
    }
}
