package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey

internal fun isSettingsSubtreeNavKey(key: BiliPaiNavKey): Boolean {
    return isSettingsSubtreeRoute(key.routeBase)
}

internal fun resolveSettingsTabletShellCategory(key: BiliPaiNavKey): SettingsRootCategory? {
    return when (key) {
        BiliPaiNavKey.Settings -> null
        is BiliPaiNavKey.SettingsCategory -> key.category
        else -> resolveSettingsRootCategoryForNavKey(key)
    }
}
