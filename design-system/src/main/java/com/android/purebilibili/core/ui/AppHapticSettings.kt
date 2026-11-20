package com.android.purebilibili.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** 持久化位固定分配，新增场景不得重排既有位。未保存的场景默认开启。 */
enum class AppHapticScene(val bit: Int, val title: String, val description: String) {
    CONTROLS(1, "按钮与开关", "通用按钮、开关、复选框和确认操作"),
    NAVIGATION(2, "底栏与标签切换", "底栏、顶部标签、侧栏及返回跟手反馈"),
    MENUS(4, "菜单与列表选择", "弹窗滑选、筛选项、分集与播放顺序选择"),
    ADJUSTMENT(8, "滑块与数值调节", "滑块刻度、颜色选择、时间滚轮和双指调整列数"),
    LONG_PRESS(16, "内容长按", "视频卡片、动态和列表的长按操作"),
    TEXT(32, "文字选择与复制", "选择文字、复制和文字操作菜单"),
    PLAYER_GESTURES(64, "播放器手势", "亮度、音量、长按倍速与锁屏操作"),
    PLAYER_SEEK(128, "播放进度与章节", "拖动播放进度、章节与继续观看操作"),
    VIDEO_ACTIONS(256, "视频互动操作", "点赞、投币、收藏、分享、长按三连与合集订阅"),
    REFRESH(512, "刷新与列表操作", "刷新、滑到列表顶部或底部、批量选择与下载操作"),
    RESULTS(1024, "图片保存与分享结果", "图片保存开始、保存与分享的成功或失败反馈");

    val settingId: String get() = "animation.haptic_scene.${name.lowercase()}"
}

data class AppHapticSettings(val disabledMask: Int = 0) {
    fun isEnabled(scene: AppHapticScene): Boolean = disabledMask and scene.bit == 0
    fun withEnabled(scene: AppHapticScene, enabled: Boolean): AppHapticSettings =
        copy(disabledMask = if (enabled) disabledMask and scene.bit.inv() else disabledMask or scene.bit)
}

internal class AppPolicyHapticFeedback(
    val delegate: HapticFeedback,
    private val configProvider: () -> AppThemeConfig,
    private val scene: AppHapticScene = AppHapticScene.CONTROLS,
) : HapticFeedback {
    fun forScene(scene: AppHapticScene): HapticFeedback = AppPolicyHapticFeedback(delegate, configProvider, scene)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val config = configProvider()
        if (config.hapticFeedbackEnabled && config.hapticSettings.isEnabled(scene)) {
            AppActionHaptics.dispatch { delegate.performHapticFeedback(hapticFeedbackType) }
        } else {
            AppActionHaptics.suppressCurrentActionFeedback()
        }
    }
}

/** 场景按操作语义显式选择，不根据 LongPress 等平台振动强度猜测用途。 */
@Composable
fun rememberAppHapticFeedback(scene: AppHapticScene): HapticFeedback {
    val current = LocalHapticFeedback.current
    val config = LocalAppThemeConfig.current
    val latestConfig = rememberUpdatedState(config)
    return remember(current, scene) {
        if (current is AppPolicyHapticFeedback) current.forScene(scene)
        else AppPolicyHapticFeedback(current, { latestConfig.value }, scene)
    }
}

/** 原生控件的隐式反馈也必须归属其功能场景。 */
@Composable
fun ProvideAppHapticScene(scene: AppHapticScene, content: @Composable () -> Unit) {
    val haptic = rememberAppHapticFeedback(scene)
    CompositionLocalProvider(LocalHapticFeedback provides haptic, content = content)
}
