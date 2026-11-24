package com.android.purebilibili.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 全窗口模态内容使用自己的菜单宿主，不复用父窗口的图层、锚点和焦点。 */
interface AppPopupWindowHostRenderer {
    @Composable
    fun Render(content: @Composable () -> Unit)
}

val LocalAppPopupWindowMaterialEnabled = staticCompositionLocalOf { false }

val LocalAppPopupWindowHostRenderer = staticCompositionLocalOf<AppPopupWindowHostRenderer?> { null }

/**
 * 弹窗表面的不透明底色。嵌在面板内的菜单宿主只录制面板内容（背景透明），
 * 面板自身的底色与窗口模糊不在采样源中；菜单玻璃以此色作为采样底，只进入背景采样，不绘制到屏幕。
 */
val LocalAppPopupBackdropBase = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun AppPopupWindowHost(
    enableWindowMaterial: Boolean = true,
    content: @Composable () -> Unit,
) {
    val config = LocalAppThemeConfig.current
    val renderer = LocalAppPopupWindowHostRenderer.current
    if (LocalAppThemeConfig.current.popupGlassEnabled && renderer != null) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppPopupWindowMaterialEnabled provides enableWindowMaterial,
            LocalAppThemeConfig provides config.copy(liquidGlassEnabled = config.popupGlassEnabled),
        ) {
            renderer.Render(content)
        }
    } else {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppThemeConfig provides config.copy(liquidGlassEnabled = config.popupGlassEnabled),
        ) { content() }
    }
}
