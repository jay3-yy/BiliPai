package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition

internal const val SETTINGS_ROUTE_BASE = "settings"
internal const val SETTINGS_CATEGORY_ROUTE_BASE = "settings_category"
internal const val SETTINGS_SEARCH_ROUTE_BASE = "settings_search"

internal val SETTINGS_SUBTREE_ROUTE_BASES: Set<String> = setOf(
    SETTINGS_ROUTE_BASE,
    SETTINGS_CATEGORY_ROUTE_BASE,
    SETTINGS_SEARCH_ROUTE_BASE,
    "appearance_settings",
    "icon_settings",
    "animation_settings",
    "playback_settings",
    "permission_settings",
    "plugins_settings",
    "js_plugin",
    "external_media",
    "bottom_bar_settings",
    "settings_share",
    "webdav_backup",
    "tips_settings",
    "open_source_licenses",
)

private val SETTINGS_DEPTH2_ROUTE_BASES: Set<String> = setOf(
    "appearance_settings",
    "animation_settings",
    "playback_settings",
    "permission_settings",
    "plugins_settings",
    "bottom_bar_settings",
    "settings_share",
    "webdav_backup",
    "tips_settings",
    "open_source_licenses",
)

private val SETTINGS_DEPTH3_ROUTE_BASES: Set<String> = setOf(
    "icon_settings",
    "js_plugin",
)

private val SETTINGS_DEPTH4_ROUTE_BASES: Set<String> = setOf(
    "external_media",
)

private val ROUTE_TO_CATEGORY: Map<String, SettingsRootCategory> = mapOf(
    "appearance_settings" to SettingsRootCategory.APPEARANCE_INTERACTION,
    "animation_settings" to SettingsRootCategory.APPEARANCE_INTERACTION,
    "icon_settings" to SettingsRootCategory.APPEARANCE_INTERACTION,
    "bottom_bar_settings" to SettingsRootCategory.APPEARANCE_INTERACTION,
    "playback_settings" to SettingsRootCategory.CONTENT_PLAYBACK,
    "permission_settings" to SettingsRootCategory.PRIVACY_STORAGE,
    "settings_share" to SettingsRootCategory.PRIVACY_STORAGE,
    "webdav_backup" to SettingsRootCategory.PRIVACY_STORAGE,
    "plugins_settings" to SettingsRootCategory.SYSTEM_ABOUT,
    "js_plugin" to SettingsRootCategory.SYSTEM_ABOUT,
    "external_media" to SettingsRootCategory.SYSTEM_ABOUT,
    "tips_settings" to SettingsRootCategory.SYSTEM_ABOUT,
    "open_source_licenses" to SettingsRootCategory.SYSTEM_ABOUT,
)

internal fun isSettingsSubtreeRoute(routeBase: String?): Boolean {
    val normalized = routeBase?.substringBefore("?")?.takeIf { it.isNotBlank() } ?: return false
    return normalized in SETTINGS_SUBTREE_ROUTE_BASES
}

internal fun resolveSettingsNavRouteBase(key: BiliPaiNavKey): String = key.routeBase

internal fun resolveSettingsNavDepth(routeBase: String?): Int {
    val normalized = routeBase?.substringBefore("?")?.takeIf { it.isNotBlank() } ?: return -1
    return when (normalized) {
        SETTINGS_ROUTE_BASE -> 0
        SETTINGS_CATEGORY_ROUTE_BASE,
        SETTINGS_SEARCH_ROUTE_BASE -> 1
        in SETTINGS_DEPTH2_ROUTE_BASES -> 2
        in SETTINGS_DEPTH3_ROUTE_BASES -> 3
        in SETTINGS_DEPTH4_ROUTE_BASES -> 4
        else -> -1
    }
}

internal fun resolveSettingsNavParentRoute(childRoute: String?): String? {
    val normalized = childRoute?.substringBefore("?")?.takeIf { it.isNotBlank() } ?: return null
    return when (normalized) {
        SETTINGS_ROUTE_BASE -> null
        SETTINGS_CATEGORY_ROUTE_BASE,
        SETTINGS_SEARCH_ROUTE_BASE -> SETTINGS_ROUTE_BASE
        in SETTINGS_DEPTH2_ROUTE_BASES -> SETTINGS_CATEGORY_ROUTE_BASE
        "icon_settings",
        "animation_settings" -> "appearance_settings"
        "js_plugin" -> "plugins_settings"
        "external_media" -> "js_plugin"
        else -> null
    }
}

internal fun resolveSettingsRootCategoryForRoute(routeBase: String?): SettingsRootCategory? {
    val normalized = routeBase?.substringBefore("?")?.takeIf { it.isNotBlank() } ?: return null
    return when (normalized) {
        SETTINGS_CATEGORY_ROUTE_BASE -> null
        in ROUTE_TO_CATEGORY -> ROUTE_TO_CATEGORY.getValue(normalized)
        else -> null
    }
}

internal fun resolveSettingsRootCategoryForNavKey(key: BiliPaiNavKey): SettingsRootCategory? {
    return when (key) {
        is BiliPaiNavKey.SettingsCategory -> key.category
        else -> resolveSettingsRootCategoryForRoute(key.routeBase)
    }
}

internal fun isSettingsNavHierarchyTransition(
    parentRoute: String?,
    childRoute: String?,
): Boolean {
    if (parentRoute == null || childRoute == null) return false
    if (!isSettingsSubtreeRoute(childRoute)) return false
    val normalizedParent = parentRoute.substringBefore("?")
    val normalizedChild = childRoute.substringBefore("?")
    if (resolveSettingsNavParentRoute(normalizedChild) == normalizedParent) {
        return true
    }
    return normalizedParent == SETTINGS_SEARCH_ROUTE_BASE &&
        normalizedChild in SETTINGS_DEPTH2_ROUTE_BASES
}

internal fun resolveSettingsNavRouteTransition(
    fromRoute: String?,
    toRoute: String?,
    forward: Boolean,
): BiliPaiNavRouteTransition? {
    if (forward) {
        if (!isSettingsNavHierarchyTransition(fromRoute, toRoute)) return null
        return BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_FORWARD
    }
    if (!isSettingsNavHierarchyTransition(toRoute, fromRoute)) return null
    return BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP
}

internal fun isSettingsNavPopTransition(
    fromKey: BiliPaiNavKey?,
    toKey: BiliPaiNavKey?,
): Boolean {
    if (fromKey == null || toKey == null) return false
    return isSettingsNavHierarchyTransition(
        parentRoute = toKey.routeBase,
        childRoute = fromKey.routeBase,
    )
}
