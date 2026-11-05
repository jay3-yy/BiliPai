package com.android.purebilibili.core.ui

/** 位标识保持稳定；新增场景只能追加，未保存的场景延续原有开启行为。 */
enum class AppLiquidGlassScene(val bit: Int, val title: String, val description: String) {
    BOTTOM_DOCK(1, "底栏 Dock", "底部导航及其搜索入口；大屏侧栏使用同一选择"),
    HOME_TOP_BAR(2, "首页顶栏", "首页顶部分类标签与操作按钮"),
    HOME_SEARCH(4, "首页搜索框", "首页顶部搜索框，独立于底栏搜索入口"),
    COMMENT_BAR(8, "评论输入栏", "视频与动态详情的底部评论输入栏"),
    OTHER_COMPONENTS(16, "页面与通用控件", "页面标签、搜索框、设置控件及其他页面玻璃组件"),
    PLAYER_CONTROLS(32, "播放器控件", "横竖屏视频与直播控制条、进度条和锁屏按钮"),
    MENUS_DIALOGS(64, "菜单与弹窗", "下拉菜单、对话框、弹幕输入与底部面板；横竖屏共用选择"),
    NOW_PLAYING(128, "悬浮播放条", "底栏内与独立显示的正在播放横条"),
    ;
    val settingId: String get() = "animation.liquid_glass_scene.${name.lowercase()}"
}

data class AppLiquidGlassSettings(val disabledMask: Int = 0) {
    // 旧版只保存「其他组件」位，拆分后先继承旧选择；首次修改时保存独立选择。
    private val resolvedMask: Int get() = if (disabledMask and SPLIT_SCENES_SAVED == 0 &&
        disabledMask and AppLiquidGlassScene.OTHER_COMPONENTS.bit != 0
    ) disabledMask or SPLIT_SCENES_MASK else disabledMask

    fun isEnabled(scene: AppLiquidGlassScene): Boolean = resolvedMask and scene.bit == 0

    fun withEnabled(scene: AppLiquidGlassScene, enabled: Boolean): AppLiquidGlassSettings = copy(
        disabledMask = (if (enabled) resolvedMask and scene.bit.inv() else resolvedMask or scene.bit) or SPLIT_SCENES_SAVED,
    )

    private companion object {
        const val SPLIT_SCENES_MASK = 32 or 64 or 128
        const val SPLIT_SCENES_SAVED = 1 shl 30
    }
}
