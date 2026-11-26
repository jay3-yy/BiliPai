package com.android.purebilibili.feature.settings

import android.os.Build

enum class AppearanceSceneRecipe(
    val title: String,
    val subtitle: String,
    val summaryToast: String,
    val requiresAndroid13: Boolean = false,
) {
    NATIVE_MD3(
        title = "纯净原生 MD3",
        subtitle = "动态取色 · 轻快省电",
        summaryToast = "已应用「纯净原生 MD3」配方",
    ),
    FLUID_MIUIX(
        title = "澎湃灵动 Miuix",
        subtitle = "细腻留白 · 柔和层次",
        summaryToast = "已应用「澎湃灵动 Miuix」配方",
    ),
    LIQUID_GLASS(
        title = "液态玻璃通透",
        subtitle = "动态取色 · 光学折射材质",
        summaryToast = "已开启「液态玻璃通透」材质层",
        requiresAndroid13 = true,
    ),
    MIDNIGHT_AMOLED(
        title = "深夜纯黑护眼",
        subtitle = "AMOLED 纯黑 · 夜间不刺眼",
        summaryToast = "已应用「深夜纯黑护眼」配方",
    ),
}

enum class PlaybackSceneRecipe(
    val title: String,
    val subtitle: String,
    val summaryToast: String,
) {
    BINGE_IMMERSIVE(
        title = "追番沉浸",
        subtitle = "自动连播 · 动态环境光",
        summaryToast = "已应用「追番沉浸」配方",
    ),
    LATE_NIGHT_PODCAST(
        title = "深夜听播客",
        subtitle = "后台播放 · 响度均衡 · 20s 渐弱",
        summaryToast = "已应用「深夜听播客」配方",
    ),
    SPEED_RUNNER(
        title = "极速刷片",
        subtitle = "长按锁倍速 · 双指调速 · 自动连播",
        summaryToast = "已应用「极速刷片」配方",
    ),
}

data class SettingsHiddenGem(
    val id: String,
    val title: String,
    val description: String,
    val searchQuery: String,
    val target: SettingsSearchTarget,
    val focusId: String? = null,
)

internal val SETTINGS_HIDDEN_GEMS: List<SettingsHiddenGem> = listOf(
    SettingsHiddenGem(
        id = "gem_ambient",
        title = "视频动态环境光",
        description = "画面色彩实时向播放器四周柔光弥散，暗光观影更有影院氛围。",
        searchQuery = "动态环境光",
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN,
    ),
    SettingsHiddenGem(
        id = "gem_loudness",
        title = "响度均衡与渐弱淡出",
        description = "平衡不同 UP 主忽大忽小的音量，深夜听视频定时结束还能平滑降音。",
        searchQuery = "响度均衡",
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER,
    ),
    SettingsHiddenGem(
        id = "gem_speed_gestures",
        title = "长按锁倍速与双指调速",
        description = "长按倍速后下滑即可锁定，双手刷片也能用双指自由滑动调节倍速。",
        searchQuery = "长按锁倍速",
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_SPEED,
    ),
    SettingsHiddenGem(
        id = "gem_haptic_scenes",
        title = "分场景精细触感反馈",
        description = "可分别控制按钮、滑块、进度拖动与长按三连的震动手感。",
        searchQuery = "振动场景",
        target = SettingsSearchTarget.ANIMATION,
    ),
    SettingsHiddenGem(
        id = "gem_icon_color",
        title = "桌面图标实时调色",
        description = "随心搭配 BiliPai 应用图标风格与主题色，让桌面更合心意。",
        searchQuery = "应用图标",
        target = SettingsSearchTarget.APPEARANCE,
        focusId = SettingsSearchFocusIds.APPEARANCE_SPLASH,
    ),
    SettingsHiddenGem(
        id = "gem_liquid_glass",
        title = "液态玻璃光学材质",
        description = "Android 13+ 支持叠加实时玻璃折射与通透弹窗，且不改所选设计语言。",
        searchQuery = "液态玻璃",
        target = SettingsSearchTarget.ANIMATION,
        focusId = SettingsSearchFocusIds.ANIMATION_VISUAL_EFFECTS,
    ),
)

internal fun filterAppearanceRecipesForApi(
    sdkInt: Int = Build.VERSION.SDK_INT,
): List<AppearanceSceneRecipe> = AppearanceSceneRecipe.entries.filter { recipe ->
    !recipe.requiresAndroid13 || sdkInt >= Build.VERSION_CODES.TIRAMISU
}

internal fun resolveNextHiddenGemIndex(currentIndex: Int, total: Int): Int {
    if (total <= 1) return 0
    val safeCurrent = ((currentIndex % total) + total) % total
    return (safeCurrent + 1) % total
}

/**
 * 为设置首页「界面外观 / 动效与触感 / 播放与画质」生成实时状态亮点摘要，
 * 既让老用户一眼确认当前配置，也帮助新用户发现高阶玩法。
 */
internal fun resolveAppearanceCategoryHighlightSubtitle(
    uiStyleLabel: String,
    liquidGlassEnabled: Boolean,
    dynamicColorEnabled: Boolean,
    amoledDarkEnabled: Boolean,
): String = buildList {
    add(uiStyleLabel)
    if (liquidGlassEnabled) add("液态玻璃")
    if (dynamicColorEnabled) add("动态取色")
    if (amoledDarkEnabled) add("纯黑护眼")
}.joinToString(" · ")

internal fun resolvePlaybackCategoryHighlightSubtitle(
    sleepFadeEnabled: Boolean,
    sleepFadeDurationSec: Int,
    loudnessEnabled: Boolean,
    autoPlayEnabled: Boolean,
    fallbackSubtitle: String,
): String {
    val tags = buildList {
        if (sleepFadeEnabled) add("渐弱 ${sleepFadeDurationSec}s")
        if (loudnessEnabled) add("响度均衡")
        if (autoPlayEnabled) add("自动连播")
    }
    return if (tags.isEmpty()) fallbackSubtitle else tags.joinToString(" · ")
}

internal fun resolveAnimationCategoryHighlightSubtitle(
    hapticEnabled: Boolean,
    enabledSceneCount: Int,
    totalSceneCount: Int,
    fallbackSubtitle: String,
): String = when {
    !hapticEnabled -> "触感反馈已关闭 · 点击调整动效与振动"
    totalSceneCount > 0 -> "触感已开（$enabledSceneCount/$totalSceneCount 场景）· 卡片动效"
    else -> fallbackSubtitle
}

data class CustomizedSettingSummary(
    val title: String,
    val currentValue: String,
    val defaultValue: String,
)

internal fun collectCustomizedAppearanceSettings(
    uiStyleLabel: String,
    isDefaultUiStyle: Boolean,
    themeModeLabel: String,
    isFollowSystemTheme: Boolean,
    amoledDarkEnabled: Boolean,
    liquidGlassEnabled: Boolean,
    dynamicColorEnabled: Boolean,
    focusViewingHideStatsEnabled: Boolean,
): List<CustomizedSettingSummary> = buildList {
    if (!isDefaultUiStyle) add(CustomizedSettingSummary("界面风格", uiStyleLabel, "Material 3"))
    if (!isFollowSystemTheme) add(CustomizedSettingSummary("深浅主题", themeModeLabel, "跟随系统"))
    if (amoledDarkEnabled) add(CustomizedSettingSummary("深色模式风格", "AMOLED 纯黑", "普通黑"))
    if (!liquidGlassEnabled) add(CustomizedSettingSummary("液态玻璃", "已关闭", "已开启"))
    if (!dynamicColorEnabled) add(CustomizedSettingSummary("动态取色", "已关闭", "已开启"))
    if (focusViewingHideStatsEnabled) add(CustomizedSettingSummary("专注观赏（隐藏互动数字）", "已开启", "已关闭"))
}

internal fun collectCustomizedPlaybackSettings(
    autoPlayEnabled: Boolean,
    bgPlayEnabled: Boolean,
    loudnessEnabled: Boolean,
    sleepFadeEnabled: Boolean,
    sleepFadeDurationSec: Int,
    longPressSpeedLockEnabled: Boolean,
    twoFingerVerticalSpeedEnabled: Boolean,
): List<CustomizedSettingSummary> = buildList {
    if (autoPlayEnabled) add(CustomizedSettingSummary("自动连播", "已开启", "已关闭"))
    if (bgPlayEnabled) add(CustomizedSettingSummary("后台播放", "已开启", "已关闭"))
    if (loudnessEnabled) add(CustomizedSettingSummary("响度均衡", "已开启", "已关闭"))
    if (!sleepFadeEnabled) {
        add(CustomizedSettingSummary("定时结束前渐弱淡出", "已关闭", "已开启"))
    } else if (sleepFadeDurationSec != com.android.purebilibili.feature.audio.player.SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC) {
        add(CustomizedSettingSummary("定时渐弱淡出时长", "${sleepFadeDurationSec} 秒", "12 秒"))
    }
    if (longPressSpeedLockEnabled) add(CustomizedSettingSummary("长按锁倍速", "已开启", "已关闭"))
    if (twoFingerVerticalSpeedEnabled) add(CustomizedSettingSummary("双指上下调速", "已开启", "已关闭"))
}
