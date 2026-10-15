package com.android.purebilibili.feature.settings

internal const val LEGACY_THEME_MODE_AMOLED = 3

internal data class ThemePreferenceState(
    val useDarkTheme: Boolean,
    val useAmoledDarkTheme: Boolean
)

enum class Md3ColorSource(val label: String) {
    FOLLOW_WALLPAPER("跟随系统壁纸"),
    WALLPAPER_RETONED("系统壁纸重新调色"),
    CUSTOM("自定义颜色")
}

internal fun resolveThemeModePreference(
    themeModeValue: Int
): AppThemeMode {
    return when (themeModeValue) {
        AppThemeMode.FOLLOW_SYSTEM.value -> AppThemeMode.FOLLOW_SYSTEM
        AppThemeMode.LIGHT.value -> AppThemeMode.LIGHT
        AppThemeMode.DARK.value,
        LEGACY_THEME_MODE_AMOLED -> AppThemeMode.DARK
        else -> AppThemeMode.FOLLOW_SYSTEM
    }
}

internal fun resolveDarkThemeStylePreference(
    darkThemeStyleValue: Int?,
    legacyThemeModeValue: Int?
): DarkThemeStyle {
    return when {
        darkThemeStyleValue != null -> DarkThemeStyle.fromValue(darkThemeStyleValue)
        legacyThemeModeValue == LEGACY_THEME_MODE_AMOLED -> DarkThemeStyle.AMOLED
        else -> DarkThemeStyle.DEFAULT
    }
}

internal fun resolveThemePreferenceState(
    themeMode: AppThemeMode,
    darkThemeStyle: DarkThemeStyle,
    systemInDark: Boolean,
    scheduledDarkTheme: Boolean? = null,
): ThemePreferenceState {
    val useDarkTheme = scheduledDarkTheme ?: when (themeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    return ThemePreferenceState(
        useDarkTheme = useDarkTheme,
        useAmoledDarkTheme = useDarkTheme && darkThemeStyle == DarkThemeStyle.AMOLED
    )
}

internal fun resolveMd3ColorSourcePreference(
    sourceValue: String?,
    legacyDynamicColorEnabled: Boolean?
): Md3ColorSource {
    val explicitSource = runCatching {
        sourceValue?.let(Md3ColorSource::valueOf)
    }.getOrNull()
    if (explicitSource != null) return explicitSource

    return if (legacyDynamicColorEnabled == false) {
        Md3ColorSource.CUSTOM
    } else {
        Md3ColorSource.FOLLOW_WALLPAPER
    }
}

internal fun normalizeMd3CustomColorHex(
    rawValue: String?,
    fallback: String = "#007AFF"
): String {
    val normalizedFallback = fallback
        .trim()
        .removePrefix("#")
        .uppercase()
        .takeIf { it.length == 6 && it.all(Char::isHexDigit) }
        ?.let { "#$it" }
        ?: "#007AFF"
    val rawHex = rawValue
        ?.trim()
        ?.removePrefix("#")
        ?.uppercase()
        ?: return normalizedFallback
    return if (rawHex.length == 6 && rawHex.all(Char::isHexDigit)) {
        "#$rawHex"
    } else {
        normalizedFallback
    }
}

internal fun isValidMd3CustomColorHex(rawValue: String?): Boolean {
    val rawHex = rawValue
        ?.trim()
        ?.removePrefix("#")
        ?: return false
    return rawHex.length == 6 && rawHex.all(Char::isHexDigit)
}

private fun Char.isHexDigit(): Boolean {
    return this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'
}
