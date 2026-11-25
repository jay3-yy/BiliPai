package com.android.purebilibili.feature.home

/**
 * 首页只在自己是当前顶层目的地时才接管系统栏。
 *
 * 共享元素转场会保留首页以提供源卡片；此时详情页才是系统栏的所有者，首页不能
 * 再次 show(statusBars)，否则会覆盖“播放页隐藏状态栏”的用户选择。
 */
import com.android.purebilibili.core.plugin.skin.UiSkinTonePolicy

internal fun shouldApplyHomeSystemBars(isTopLevelActive: Boolean): Boolean = isTopLevelActive

internal fun resolveHomeStatusBarDarkIcons(
    hasTopSkinArtwork: Boolean,
    skinColorMode: String?,
    topSkinTintIsLight: Boolean,
    defaultBackgroundIsLight: Boolean,
    sampledTopArtworkLuminance: Float? = null,
    skinForegroundLuminance: Float? = null,
    tonePolicy: UiSkinTonePolicy = UiSkinTonePolicy.AUTO,
): Boolean {
    if (!hasTopSkinArtwork) return defaultBackgroundIsLight
    when (tonePolicy) {
        UiSkinTonePolicy.FORCE_LIGHT_BG -> return true
        UiSkinTonePolicy.FORCE_DARK_BG -> return false
        UiSkinTonePolicy.AUTO -> Unit
    }
    // 1. 优先采用 head_bg 状态栏带真实像素亮度（> 0.52 即浅底，必须使用深色状态栏图标）
    if (sampledTopArtworkLuminance != null) {
        return sampledTopArtworkLuminance > 0.52f
    }
    // 2. 若装扮显式声明了深色前景文字（luminance < 0.42），说明顶栏为浅底
    if (skinForegroundLuminance != null && skinForegroundLuminance < 0.42f) {
        return true
    }
    return when (skinColorMode?.trim()?.lowercase()) {
        "dark" -> false
        "light" -> true
        else -> topSkinTintIsLight
    }
}
