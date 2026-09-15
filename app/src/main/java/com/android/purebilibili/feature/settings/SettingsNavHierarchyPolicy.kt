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
    "home_settings",
    "icon_settings",
    "animation_settings",
    "playback_settings",
    "permission_settings",
    "message_notification_settings",
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
    "home_settings",
    "animation_settings",
    "playback_settings",
    "permission_settings",
    "message_notification_settings",
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

/** settings / search / category 可直达的二级页。 */
private val SETTINGS_DIRECT_REACH_FROM_ROOT_CHILDREN: Set<String> = SETTINGS_DEPTH2_ROUTE_BASES

private val SETTINGS_DIRECT_REACH_PARENTS: Set<String> = setOf(
    SETTINGS_ROUTE_BASE,
    SETTINGS_SEARCH_ROUTE_BASE,
    SETTINGS_CATEGORY_ROUTE_BASE,
)

/**
 * 分类级直跳:分类内容页只有一个跳转入口且无内联设置项时,点击分类直接
 * 进入目标设置页,省去中间层的一次点击。
 */
internal fun resolveSettingsCategoryDirectTargetKey(
    category: SettingsRootCategory,
): BiliPaiNavKey? = when (canonicalSettingsRootCategory(category)) {
    SettingsRootCategory.APPEARANCE_THEME -> BiliPaiNavKey.AppearanceSettings
    SettingsRootCategory.PLUGINS_EXTENSIONS -> BiliPaiNavKey.PluginsSettings()
    else -> null
}

internal fun resolveSettingsCategoryNavKey(
    category: SettingsRootCategory,
): BiliPaiNavKey = resolveSettingsCategoryDirectTargetKey(category)
    ?: BiliPaiNavKey.SettingsCategory(canonicalSettingsRootCategory(category))

private val ROUTE_TO_CATEGORY: Map<String, SettingsRootCategory> = mapOf(
    "appearance_settings" to SettingsRootCategory.APPEARANCE_THEME,
    "home_settings" to SettingsRootCategory.HOME_RECOMMENDATION,
    "animation_settings" to SettingsRootCategory.NAVIGATION_INTERACTION,
    "icon_settings" to SettingsRootCategory.APPEARANCE_THEME,
    "bottom_bar_settings" to SettingsRootCategory.NAVIGATION_INTERACTION,
    "playback_settings" to SettingsRootCategory.PLAYBACK_QUALITY,
    "permission_settings" to SettingsRootCategory.PRIVACY_PERMISSION,
    "message_notification_settings" to SettingsRootCategory.PRIVACY_PERMISSION,
    "settings_share" to SettingsRootCategory.STORAGE_BACKUP,
    "webdav_backup" to SettingsRootCategory.STORAGE_BACKUP,
    "plugins_settings" to SettingsRootCategory.PLUGINS_EXTENSIONS,
    "js_plugin" to SettingsRootCategory.PLUGINS_EXTENSIONS,
    "external_media" to SettingsRootCategory.PLUGINS_EXTENSIONS,
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
        "icon_settings" -> "appearance_settings"
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
        is BiliPaiNavKey.SettingsCategory -> canonicalSettingsRootCategory(key.category)
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
    // 任意宿主页面（底栏 tab：首页/动态/我的等）直接打开设置根页，都属于设置树的根入口
    // （“我的”页设置按钮与首页顶栏设置按钮都走这条路径，且 pop 回 MainHost 时经
    // resolveSettingsNavPopTransition 重映射回真实 tab，同样命中这里）。
    if (normalizedChild == SETTINGS_ROUTE_BASE && !isSettingsSubtreeRoute(normalizedParent)) {
        return true
    }
    if (resolveSettingsNavParentRoute(normalizedChild) == normalizedParent) {
        return true
    }
    // 设置根页 / 搜索 / 分类可直达二级子页（分栏、快捷入口、Nav3 push），需与 category 中转同等动画。
    if (normalizedChild in SETTINGS_DIRECT_REACH_FROM_ROOT_CHILDREN &&
        normalizedParent in SETTINGS_DIRECT_REACH_PARENTS
    ) {
        return true
    }
    return false
}

/**
 * 设置树 pop 的单一决策入口。Display / Entry / 预测返回 handler 都应读这里，
 * 避免 MainHost ↔ 底栏 Settings remap 不一致导致预览与提交动画分裂。
 */
internal fun resolveSettingsNavPopTransition(
    fromRoute: String?,
    toRoute: String?,
    activeMainHostRoute: String? = null,
): BiliPaiNavRouteTransition? {
    if (!isSettingsSubtreeRoute(fromRoute)) return null
    val normalizedTo = toRoute?.substringBefore("?")?.takeIf { it.isNotBlank() } ?: return null
    val normalizedActive = activeMainHostRoute?.substringBefore("?")?.takeIf { it.isNotBlank() }
    // 只要底栏有活跃 tab，pop 回 MainHost 时就以该 tab 作为设置树的“父级”，
    // 使首页/动态/我的等任意入口打开设置根页后都能命中设置树 iOS pop 动画。
    val effectiveParentRoute = if (
        normalizedTo == BiliPaiNavKey.MainHost.routeBase &&
        normalizedActive != null
    ) {
        normalizedActive
    } else {
        normalizedTo
    }
    if (isSettingsNavHierarchyTransition(
            parentRoute = effectiveParentRoute,
            childRoute = fromRoute,
        )
    ) {
        return BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP
    }
    // 设置子树任意页直接回到 MainHost，且底栏仍在 Settings：统一走设置 iOS pop，
    // 覆盖非严格父子边（如搜索直达 animation 后一键返回）。
    if (normalizedTo == BiliPaiNavKey.MainHost.routeBase && isSettingsSubtreeRoute(normalizedActive)) {
        return BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP
    }
    return null
}

internal fun resolveSettingsNavPopTransition(
    fromKey: BiliPaiNavKey?,
    toKey: BiliPaiNavKey?,
    activeMainHostRoute: String? = null,
): BiliPaiNavRouteTransition? {
    if (fromKey == null || toKey == null) return null
    return resolveSettingsNavPopTransition(
        fromRoute = fromKey.routeBase,
        toRoute = toKey.routeBase,
        activeMainHostRoute = activeMainHostRoute,
    )
}

internal fun isSettingsNavPopTransition(
    fromKey: BiliPaiNavKey?,
    toKey: BiliPaiNavKey?,
    activeMainHostRoute: String? = null,
): Boolean {
    return resolveSettingsNavPopTransition(
        fromKey = fromKey,
        toKey = toKey,
        activeMainHostRoute = activeMainHostRoute,
    ) != null
}
