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
    onSettingsHome: () -> Unit = onBack,
    onOpenSourceLicensesClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onAnimationClick: () -> Unit = {},
    onPlaybackClick: () -> Unit = {},
    onPermissionClick: () -> Unit = {},
    onSpacePrivacyClick: () -> Unit = {},
    onLoginActivityClick: () -> Unit = {},
    onMessageNotificationClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onSettingsShareClick: () -> Unit = {},
    onWebDavBackupClick: () -> Unit = {},
    onNavigateToBottomBarSettings: () -> Unit = {},
    onTipsClick: () -> Unit = {},
    onKeyboardShortcutsClick: () -> Unit = {},
    onReplayOnboardingClick: () -> Unit = {},
    onCategoryClick: (SettingsRootCategory) -> Unit = {},
    onSearchOpen: () -> Unit = {},
    mainHazeState: HazeState? = null,
    forceSinglePaneContent: Boolean = false,
) {
    SettingsScreen(
        viewModel = viewModel,
        onBack = onBack,
        onSettingsHome = onSettingsHome,
        onOpenSourceLicensesClick = onOpenSourceLicensesClick,
        onAppearanceClick = onAppearanceClick,
        onHomeClick = onHomeClick,
        onAnimationClick = onAnimationClick,
        onPlaybackClick = onPlaybackClick,
        onPermissionClick = onPermissionClick,
        onSpacePrivacyClick = onSpacePrivacyClick,
        onLoginActivityClick = onLoginActivityClick,
        onMessageNotificationClick = onMessageNotificationClick,
        onPluginsClick = onPluginsClick,
        onSettingsShareClick = onSettingsShareClick,
        onWebDavBackupClick = onWebDavBackupClick,
        onNavigateToBottomBarSettings = onNavigateToBottomBarSettings,
        onTipsClick = onTipsClick,
        onKeyboardShortcutsClick = onKeyboardShortcutsClick,
        onReplayOnboardingClick = onReplayOnboardingClick,
        onCategoryClick = onCategoryClick,
        onSearchOpen = onSearchOpen,
        destination = SettingsNavDestination.Category(category),
        mainHazeState = mainHazeState,
        forceSinglePaneContent = forceSinglePaneContent,
    )
}
