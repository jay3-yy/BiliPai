package com.android.purebilibili.core.store

const val APP_ICON_COMPONENT_PACKAGE_NAME = "com.android.purebilibili"
const val APP_ICON_COMPAT_ALIAS_CLASS_NAME = "$APP_ICON_COMPONENT_PACKAGE_NAME.MainActivityAlias3D"

const val DEFAULT_APP_ICON_KEY = "icon_blue_snow_maid"

private val APPEARANCE_APP_ICON_KEYS = setOf(
    "icon_blue_snow_maid",
    "icon_blue_snow_maid_announcement",
    "icon_blue_snow_maid_front",
    "icon_3d"
)

enum class AppIconAppearance(val storedValue: Int) {
    FOLLOW_SYSTEM(0),
    LIGHT(1),
    DARK(2)
}

fun resolveAppIconAppearance(storedValue: Int): AppIconAppearance {
    return AppIconAppearance.entries.firstOrNull { it.storedValue == storedValue }
        ?: AppIconAppearance.FOLLOW_SYSTEM
}

private val CANONICAL_APP_ICON_KEYS = setOf(
    "icon_blue_snow_maid",
    "icon_blue_snow_maid_announcement",
    "icon_blue_snow_maid_front",
    "icon_3d",
    "icon_bilipai",
    "icon_bilipai_pink",
    "icon_bilipai_white",
    BILIPAI_THEME_ICON_KEY,
    "icon_bilipai_violet",
    "icon_bilipai_green",
    "icon_bilipai_ink",
    "icon_bilipai_monet"
)

private val LAUNCHER_ALIAS_SUFFIX_BY_KEY = mapOf(
    "icon_blue_snow_maid" to "MainActivityAliasBlueSnowMaid",
    "icon_blue_snow_maid_announcement" to "MainActivityAliasBlueSnowMaidAnnouncement",
    "icon_blue_snow_maid_front" to "MainActivityAliasBlueSnowMaidFront",
    "icon_3d" to "MainActivityAlias3DLauncher",
    "icon_bilipai" to "MainActivityAliasBiliPai",
    "icon_bilipai_pink" to "MainActivityAliasBiliPaiPink",
    "icon_bilipai_white" to "MainActivityAliasBiliPaiWhite",
    "icon_bilipai_violet" to "MainActivityAliasBiliPaiViolet",
    "icon_bilipai_green" to "MainActivityAliasBiliPaiGreen",
    "icon_bilipai_ink" to "MainActivityAliasBiliPaiInk",
    "icon_bilipai_monet" to "MainActivityAliasBiliPaiMonet"
)

private val NO_ICON_LAUNCHER_ALIAS_SUFFIX_BY_KEY = mapOf(
    "icon_blue_snow_maid" to "MainActivityAliasBlueSnowMaidNoIcon",
    "icon_blue_snow_maid_announcement" to "MainActivityAliasBlueSnowMaidAnnouncementNoIcon",
    "icon_blue_snow_maid_front" to "MainActivityAliasBlueSnowMaidFrontNoIcon",
    "icon_3d" to "MainActivityAlias3DNoIcon",
    "icon_bilipai" to "MainActivityAliasBiliPaiNoIcon",
    "icon_bilipai_pink" to "MainActivityAliasBiliPaiPinkNoIcon",
    "icon_bilipai_white" to "MainActivityAliasBiliPaiWhiteNoIcon",
    "icon_bilipai_violet" to "MainActivityAliasBiliPaiVioletNoIcon",
    "icon_bilipai_green" to "MainActivityAliasBiliPaiGreenNoIcon",
    "icon_bilipai_ink" to "MainActivityAliasBiliPaiInkNoIcon",
    "icon_bilipai_monet" to "MainActivityAliasBiliPaiMonetNoIcon"
)

private val FIXED_APPEARANCE_LAUNCHER_ALIAS_SUFFIX_BY_KEY_AND_APPEARANCE = mapOf(
    ("icon_3d" to AppIconAppearance.LIGHT) to "MainActivityAlias3DLight",
    ("icon_3d" to AppIconAppearance.DARK) to "MainActivityAlias3DDark",
    ("icon_blue_snow_maid" to AppIconAppearance.LIGHT) to "MainActivityAliasBlueSnowMaidLight",
    ("icon_blue_snow_maid" to AppIconAppearance.DARK) to "MainActivityAliasBlueSnowMaidDark",
    ("icon_blue_snow_maid_announcement" to AppIconAppearance.LIGHT) to "MainActivityAliasBlueSnowMaidAnnouncementLight",
    ("icon_blue_snow_maid_announcement" to AppIconAppearance.DARK) to "MainActivityAliasBlueSnowMaidAnnouncementDark",
    ("icon_blue_snow_maid_front" to AppIconAppearance.LIGHT) to "MainActivityAliasBlueSnowMaidFrontLight",
    ("icon_blue_snow_maid_front" to AppIconAppearance.DARK) to "MainActivityAliasBlueSnowMaidFrontDark"
)

private val FIXED_APPEARANCE_NO_ICON_ALIAS_SUFFIX_BY_KEY_AND_APPEARANCE = mapOf(
    ("icon_3d" to AppIconAppearance.LIGHT) to "MainActivityAlias3DLightNoIcon",
    ("icon_3d" to AppIconAppearance.DARK) to "MainActivityAlias3DDarkNoIcon",
    ("icon_blue_snow_maid" to AppIconAppearance.LIGHT) to "MainActivityAliasBlueSnowMaidLightNoIcon",
    ("icon_blue_snow_maid" to AppIconAppearance.DARK) to "MainActivityAliasBlueSnowMaidDarkNoIcon",
    ("icon_blue_snow_maid_announcement" to AppIconAppearance.LIGHT) to "MainActivityAliasBlueSnowMaidAnnouncementLightNoIcon",
    ("icon_blue_snow_maid_announcement" to AppIconAppearance.DARK) to "MainActivityAliasBlueSnowMaidAnnouncementDarkNoIcon",
    ("icon_blue_snow_maid_front" to AppIconAppearance.LIGHT) to "MainActivityAliasBlueSnowMaidFrontLightNoIcon",
    ("icon_blue_snow_maid_front" to AppIconAppearance.DARK) to "MainActivityAliasBlueSnowMaidFrontDarkNoIcon"
)

private val RETIRED_APP_ICON_ALIAS_SUFFIXES = setOf(
    "MainActivityAliasAnime",
    "MainActivityAliasFlat",
    "MainActivityAliasTelegramBlue",
    "MainActivityAliasDark",
    "MainActivityAliasYuki",
    "MainActivityAliasHeadphone",
    "MainActivityAliasAnimeNoIcon",
    "MainActivityAliasFlatNoIcon",
    "MainActivityAliasTelegramBlueNoIcon",
    "MainActivityAliasDarkNoIcon",
    "MainActivityAliasYukiNoIcon",
    "MainActivityAliasHeadphoneNoIcon"
)

fun normalizeAppIconKey(rawKey: String?): String {
    val key = rawKey?.trim().orEmpty()
    if (key.isEmpty()) return DEFAULT_APP_ICON_KEY

    return when (key) {
        "default", "Blue Snow Maid", "蓝雪女仆" -> "icon_blue_snow_maid"
        "Blue Snow Maid Announcement", "蓝雪女仆·喇叭", "蓝雪女仆喇叭" -> "icon_blue_snow_maid_announcement"
        "Blue Snow Maid Front", "蓝雪女仆·正面", "蓝雪女仆正面" -> "icon_blue_snow_maid_front"
        "3D" -> "icon_3d"
        "BiliPai", "bilipai", "Icon BiliPai" -> "icon_bilipai"
        "BiliPai Pink", "BiliPai 粉", "bilipai_pink" -> "icon_bilipai_pink"
        "BiliPai White", "BiliPai 白", "bilipai_white" -> "icon_bilipai_white"
        "BiliPai Monet", "BiliPai 莫奈", "bilipai_monet" -> "icon_bilipai_monet"
        else -> if (CANONICAL_APP_ICON_KEYS.contains(key)) key else DEFAULT_APP_ICON_KEY
    }
}

fun supportsAppIconAppearance(rawKey: String?): Boolean {
    return rawKey?.trim() in APPEARANCE_APP_ICON_KEYS
}

fun resolveAppIconLauncherAlias(
    packageName: String,
    rawKey: String?,
    splashIconVisible: Boolean = true,
    appearance: AppIconAppearance = AppIconAppearance.FOLLOW_SYSTEM,
    themeIconIndex: Int = DEFAULT_BILIPAI_THEME_ICON_INDEX,
): String {
    val normalizedKey = normalizeAppIconKey(rawKey)
    if (normalizedKey == BILIPAI_THEME_ICON_KEY) {
        return "$APP_ICON_COMPONENT_PACKAGE_NAME.${biliPaiThemeAliasSuffix(themeIconIndex, splashIconVisible)}"
    }
    val aliasMap = if (splashIconVisible) {
        LAUNCHER_ALIAS_SUFFIX_BY_KEY
    } else {
        NO_ICON_LAUNCHER_ALIAS_SUFFIX_BY_KEY
    }
    val fixedAppearanceAliasMap = if (splashIconVisible) {
        FIXED_APPEARANCE_LAUNCHER_ALIAS_SUFFIX_BY_KEY_AND_APPEARANCE
    } else {
        FIXED_APPEARANCE_NO_ICON_ALIAS_SUFFIX_BY_KEY_AND_APPEARANCE
    }
    val aliasSuffix = fixedAppearanceAliasMap[normalizedKey to appearance]
        ?: aliasMap[normalizedKey]
        ?: aliasMap.getValue(DEFAULT_APP_ICON_KEY)
    return "$APP_ICON_COMPONENT_PACKAGE_NAME.$aliasSuffix"
}

fun allManagedAppIconLauncherAliases(packageName: String): Set<String> {
    return (
        LAUNCHER_ALIAS_SUFFIX_BY_KEY.values +
            NO_ICON_LAUNCHER_ALIAS_SUFFIX_BY_KEY.values +
            FIXED_APPEARANCE_LAUNCHER_ALIAS_SUFFIX_BY_KEY_AND_APPEARANCE.values +
            FIXED_APPEARANCE_NO_ICON_ALIAS_SUFFIX_BY_KEY_AND_APPEARANCE.values +
            RETIRED_APP_ICON_ALIAS_SUFFIXES +
            (0 until BILIPAI_THEME_ICON_PALETTE_SIZE).flatMap { index ->
                listOf(biliPaiThemeAliasSuffix(index, true), biliPaiThemeAliasSuffix(index, false))
            }
        )
        .map { aliasSuffix -> "$APP_ICON_COMPONENT_PACKAGE_NAME.$aliasSuffix" }
        .plus(APP_ICON_COMPAT_ALIAS_CLASS_NAME)
        .toSet()
}
