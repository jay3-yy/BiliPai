package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 最近一层玻璃外壳的材质与形变归属。背景只能记录外壳，不能包含消费它的子控件。
 * 子外壳继承已处理的材质，不重复磨砂与边缘折射；标签指示器、滑块等独立交互不受限。
 */
class AppGlassContainer internal constructor(
    val backdrop: Backdrop?,
    val ownsDeformation: Boolean,
)

val LocalAppGlassContainer = compositionLocalOf<AppGlassContainer?> { null }

/**
 * 只在实际绘制玻璃外壳的内容槽建立边界，不增加布局、动画或逐帧任务。
 * 调用方在外壳录制就绪前提供当前背景作为回退；新窗口须清除旧窗口的边界。
 * 关闭玻璃时不改变 MD3、Miuix 原生内容的参数与反馈。
 */
@Composable
fun AppGlassContainerContent(
    enabled: Boolean,
    backdrop: Backdrop?,
    ownsDeformation: Boolean,
    materialEnabled: Boolean = LocalAppThemeConfig.current.liquidGlassEnabled,
    content: @Composable () -> Unit,
) {
    if (!enabled || !materialEnabled) {
        content()
        return
    }
    val parent = LocalAppGlassContainer.current
    val inheritedDeformation = ownsDeformation || parent?.ownsDeformation == true
    val material = backdrop ?: parent?.backdrop
    val container = remember(material, inheritedDeformation) {
        AppGlassContainer(material, inheritedDeformation)
    }
    CompositionLocalProvider(LocalAppGlassContainer provides container, content = content)
}
