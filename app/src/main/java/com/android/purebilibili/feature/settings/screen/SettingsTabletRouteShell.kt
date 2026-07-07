package com.android.purebilibili.feature.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.android.purebilibili.feature.settings.isSettingsSubtreeNavKey
import com.android.purebilibili.feature.settings.resolveSettingsTabletShellCategory
import com.android.purebilibili.feature.settings.shouldUseSettingsSplitLayout
import com.android.purebilibili.navigation3.BiliPaiNavKey

@Composable
internal fun SettingsTabletNavEntryShell(
    key: BiliPaiNavKey,
    onSystemBack: () -> Unit,
    onPushKey: (BiliPaiNavKey) -> Unit,
    content: @Composable () -> Unit,
) {
    SettingsTabletRouteShell(
        key = key,
        onBack = onSystemBack,
        onCategoryClick = { category -> onPushKey(BiliPaiNavKey.SettingsCategory(category)) },
        onSearchOpen = { onPushKey(BiliPaiNavKey.SettingsSearch) },
        phoneContent = content,
    )
}

@Composable
internal fun SettingsTabletRouteShell(
    key: BiliPaiNavKey,
    onBack: () -> Unit,
    onCategoryClick: (com.android.purebilibili.feature.settings.SettingsRootCategory) -> Unit,
    onSearchOpen: () -> Unit,
    phoneContent: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    if (shouldUseSettingsSplitLayout(widthDp = configuration.screenWidthDp) && isSettingsSubtreeNavKey(key)) {
        SettingsTabletShell(
            selectedCategory = resolveSettingsTabletShellCategory(key),
            onCategoryClick = onCategoryClick,
            onBack = onBack,
            onSearchOpen = onSearchOpen,
            rightPane = {
                Box(modifier = Modifier.fillMaxSize()) {
                    phoneContent()
                }
            },
        )
    } else {
        phoneContent()
    }
}
