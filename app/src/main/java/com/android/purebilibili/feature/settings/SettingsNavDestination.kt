package com.android.purebilibili.feature.settings

sealed interface SettingsNavDestination {
    data object Home : SettingsNavDestination
    data object Search : SettingsNavDestination
    data class Category(val category: SettingsRootCategory) : SettingsNavDestination
}

internal fun resolveSettingsNavDestinationTitle(destination: SettingsNavDestination): String {
    return when (destination) {
        SettingsNavDestination.Home -> "设置"
        SettingsNavDestination.Search -> "搜索"
        is SettingsNavDestination.Category -> destination.category.title
    }
}
