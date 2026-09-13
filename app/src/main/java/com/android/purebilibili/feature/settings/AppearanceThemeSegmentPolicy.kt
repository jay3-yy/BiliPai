package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

internal fun resolveThemeModeSegmentOptions(
    followSystemLabel: String = AppThemeMode.FOLLOW_SYSTEM.label,
    lightLabel: String = AppThemeMode.LIGHT.label,
    darkLabel: String = AppThemeMode.DARK.label
): List<AppSegmentOption<AppThemeMode>> {
    return listOf(
        AppSegmentOption(AppThemeMode.FOLLOW_SYSTEM, followSystemLabel),
        AppSegmentOption(AppThemeMode.LIGHT, lightLabel),
        AppSegmentOption(AppThemeMode.DARK, darkLabel)
    )
}

internal fun resolveColorStyleOptions(): List<AppSegmentOption<PaletteStyle>> {
    return (listOf(PaletteStyle.TonalSpot) + PaletteStyle.entries.filterNot { it == PaletteStyle.TonalSpot })
        .map { style ->
            AppSegmentOption(style, style.name)
        }
}

internal fun resolveColorSpecOptions(): List<AppSegmentOption<ColorSpec.SpecVersion>> {
    return listOf(
        ColorSpec.SpecVersion.SPEC_2021,
        ColorSpec.SpecVersion.SPEC_2025
    ).map { spec ->
        AppSegmentOption(spec, spec.name)
    }
}

internal fun shouldShowMd3CustomColorControls(source: Md3ColorSource): Boolean =
    source == Md3ColorSource.CUSTOM

internal fun resolveAdvancedPaletteSubtitle(uiStyle: AppUiStyle): String =
    if (uiStyle == AppUiStyle.MIUIX) {
        "已保留配置，仅切换到 Material 3 后生效"
    } else {
        "自定义明暗模式的背景、文字与控件色"
    }

internal fun resolveMd3ColorSourceOptions(): List<AppSegmentOption<Md3ColorSource>> {
    return listOf(
        AppSegmentOption(Md3ColorSource.FOLLOW_WALLPAPER, Md3ColorSource.FOLLOW_WALLPAPER.label),
        AppSegmentOption(Md3ColorSource.CUSTOM, Md3ColorSource.CUSTOM.label)
    )
}

internal fun resolveAppIconStyleOptions(): List<AppSegmentOption<AppIconStyle>> {
    return listOf(
        AppSegmentOption(AppIconStyle.AUTO, "跟随预设"),
        AppSegmentOption(AppIconStyle.THEME_CONTAINER, "主题色容器"),
        AppSegmentOption(AppIconStyle.MD3_STANDARD, "MD3 官方推荐")
    )
}

internal fun resolveAppListItemStyleOptions(): List<AppSegmentOption<AppListItemStyle>> {
    return listOf(
        AppSegmentOption(AppListItemStyle.AUTO, "跟随预设"),
        AppSegmentOption(AppListItemStyle.CUSTOM, "自定义条目"),
        AppSegmentOption(AppListItemStyle.NATIVE, "原生组件")
    )
}

internal fun resolveDarkThemeStyleSegmentOptions(
    defaultLabel: String = DarkThemeStyle.DEFAULT.label,
    amoledLabel: String = DarkThemeStyle.AMOLED.label
): List<AppSegmentOption<DarkThemeStyle>> {
    return listOf(
        AppSegmentOption(DarkThemeStyle.DEFAULT, defaultLabel),
        AppSegmentOption(DarkThemeStyle.AMOLED, amoledLabel)
    )
}

internal fun resolveAppLanguageSegmentOptions(
    followSystemLabel: String = "跟随系统",
    simplifiedChineseLabel: String = "简体中文",
    traditionalChineseLabel: String = "繁體中文",
    englishLabel: String = "英语"
): List<AppSegmentOption<AppLanguage>> {
    return listOf(
        AppSegmentOption(AppLanguage.FOLLOW_SYSTEM, followSystemLabel),
        AppSegmentOption(AppLanguage.SIMPLIFIED_CHINESE, simplifiedChineseLabel),
        AppSegmentOption(AppLanguage.TRADITIONAL_CHINESE_TAIWAN, traditionalChineseLabel),
        AppSegmentOption(AppLanguage.ENGLISH, englishLabel)
    )
}
