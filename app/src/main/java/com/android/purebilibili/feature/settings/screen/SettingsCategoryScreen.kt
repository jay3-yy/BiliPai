package com.android.purebilibili.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.settings.SettingsNavDestination
import com.android.purebilibili.feature.settings.SettingsRootCategory
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.settings.SettingsViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun SettingsCategoryScreen(
    category: SettingsRootCategory,
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onAnimationClick: () -> Unit = {},
    onPlaybackClick: () -> Unit = {},
    onPermissionClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onSettingsShareClick: () -> Unit = {},
    onWebDavBackupClick: () -> Unit = {},
    onNavigateToBottomBarSettings: () -> Unit = {},
    onTipsClick: () -> Unit = {},
    onReplayOnboardingClick: () -> Unit = {},
    onCategoryClick: (SettingsRootCategory) -> Unit = {},
    onSearchOpen: () -> Unit = {},
    mainHazeState: HazeState? = null,
) {
    SettingsScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenSourceLicensesClick = onOpenSourceLicensesClick,
        onAppearanceClick = onAppearanceClick,
        onAnimationClick = onAnimationClick,
        onPlaybackClick = onPlaybackClick,
        onPermissionClick = onPermissionClick,
        onPluginsClick = onPluginsClick,
        onSettingsShareClick = onSettingsShareClick,
        onWebDavBackupClick = onWebDavBackupClick,
        onNavigateToBottomBarSettings = onNavigateToBottomBarSettings,
        onTipsClick = onTipsClick,
        onReplayOnboardingClick = onReplayOnboardingClick,
        onCategoryClick = onCategoryClick,
        onSearchOpen = onSearchOpen,
        destination = SettingsNavDestination.Category(category),
        mainHazeState = mainHazeState,
    )
}
