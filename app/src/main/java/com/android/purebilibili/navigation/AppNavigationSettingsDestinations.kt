package com.android.purebilibili.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.settings.AppearanceSettingsScreen
import com.android.purebilibili.feature.settings.PlaybackSettingsScreen
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.settings.SettingsViewModel
import com.android.purebilibili.feature.settings.resolveSettingsCategoryNavKey
import com.android.purebilibili.feature.settings.resolveSettingsSearchNavigation
import com.android.purebilibili.feature.settings.screen.SettingsCategoryScreen
import com.android.purebilibili.feature.settings.screen.SettingsSearchScreen
import com.android.purebilibili.feature.settings.screen.SettingsTabletNavEntryShell
import com.android.purebilibili.feature.settings.share.SettingsShareViewModel
import com.android.purebilibili.feature.settings.share.SettingsShareViewModelFactory
import com.android.purebilibili.feature.settings.webdav.WebDavBackupViewModel
import com.android.purebilibili.feature.settings.webdav.WebDavBackupViewModelFactory
import com.android.purebilibili.navigation3.BiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.BiliPaiNavKey
import dev.chrisbanes.haze.HazeState

/**
 * 拆出设置、插件与引导相关路由渲染，避免 AppNavigation 单方法超过 ART 16,384 DEX 指令上限导致 Baseline Profile AOT 退化。
 */
@Composable
internal fun RenderAppSettingsNavigationDestination(
    key: BiliPaiNavKey,
    role: BiliPaiNavEntryContentRole,
    isBottomPagerHosted: Boolean,
    settingsViewModelProvider: () -> SettingsViewModel,
    application: Application,
    mainHazeState: HazeState?,
    navigation3BackStack: List<BiliPaiNavKey>,
    onReplaceBackStack: (List<BiliPaiNavKey>) -> Unit,
    onSystemBack: () -> Unit,
    onPushKey: (BiliPaiNavKey) -> Unit,
    onPushTarget: (BiliPaiNavKey) -> Unit,
    onNavigateToVideo: (String, Long, String) -> Unit,
) {
    when (role) {
        BiliPaiNavEntryContentRole.ONBOARDING -> {
            com.android.purebilibili.feature.onboarding.OnboardingScreen(
                onBack = onSystemBack,
            )
        }
        BiliPaiNavEntryContentRole.SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                SettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                    onOpenSourceLicensesClick = { onPushKey(BiliPaiNavKey.OpenSourceLicenses) },
                    onAppearanceClick = { onPushKey(BiliPaiNavKey.AppearanceSettings) },
                    onHomeClick = { onPushKey(BiliPaiNavKey.HomeSettings) },
                    onPlaybackClick = { onPushKey(BiliPaiNavKey.PlaybackSettings) },
                    onPermissionClick = { onPushKey(BiliPaiNavKey.PermissionSettings) },
                    onSpacePrivacyClick = { onPushKey(BiliPaiNavKey.SpacePrivacySettings) },
                    onLoginActivityClick = { onPushKey(BiliPaiNavKey.LoginActivitySettings) },
                    onMessageNotificationClick = { onPushKey(BiliPaiNavKey.MessageNotificationSettings) },
                    onPluginsClick = { onPushKey(BiliPaiNavKey.PluginsSettings()) },
                    onSettingsShareClick = { onPushKey(BiliPaiNavKey.SettingsShare) },
                    onWebDavBackupClick = { onPushKey(BiliPaiNavKey.WebDavBackup) },
                    onNavigateToBottomBarSettings = { onPushKey(BiliPaiNavKey.BottomBarSettings) },
                    onTipsClick = { onPushKey(BiliPaiNavKey.TipsSettings) },
                    onKeyboardShortcutsClick = { onPushKey(BiliPaiNavKey.KeyboardShortcutsSettings) },
                    onProcessMemoryClick = { onPushKey(BiliPaiNavKey.ProcessMemory) },
                    onPerformanceHistoryClick = { onPushKey(BiliPaiNavKey.PerformanceHistory) },
                    onReplayOnboardingClick = { onPushTarget(BiliPaiNavKey.Onboarding) },
                    onCategoryClick = { category ->
                        onPushKey(resolveSettingsCategoryNavKey(category))
                    },
                    onSearchOpen = { onPushKey(BiliPaiNavKey.SettingsSearch) },
                    forceSinglePaneContent = true,
                    rootEntranceEnabled = !isBottomPagerHosted,
                )
            }
        }
        BiliPaiNavEntryContentRole.SETTINGS_CATEGORY -> {
            val categoryKey = key as BiliPaiNavKey.SettingsCategory
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                SettingsCategoryScreen(
                    category = categoryKey.category,
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                    onSettingsHome = {
                        val rootIndex = navigation3BackStack.indexOfLast { it == BiliPaiNavKey.Settings }
                        if (rootIndex >= 0) {
                            onReplaceBackStack(navigation3BackStack.take(rootIndex + 1))
                        } else {
                            val categoryIndex = navigation3BackStack.indexOfFirst { it is BiliPaiNavKey.SettingsCategory }
                            val prefix = navigation3BackStack.take(categoryIndex.coerceAtLeast(1))
                            onReplaceBackStack(prefix + BiliPaiNavKey.Settings)
                        }
                    },
                    onOpenSourceLicensesClick = { onPushKey(BiliPaiNavKey.OpenSourceLicenses) },
                    onAppearanceClick = { onPushKey(BiliPaiNavKey.AppearanceSettings) },
                    onHomeClick = { onPushKey(BiliPaiNavKey.HomeSettings) },
                    onPlaybackClick = { onPushKey(BiliPaiNavKey.PlaybackSettings) },
                    onPermissionClick = { onPushKey(BiliPaiNavKey.PermissionSettings) },
                    onSpacePrivacyClick = { onPushKey(BiliPaiNavKey.SpacePrivacySettings) },
                    onLoginActivityClick = { onPushKey(BiliPaiNavKey.LoginActivitySettings) },
                    onMessageNotificationClick = { onPushKey(BiliPaiNavKey.MessageNotificationSettings) },
                    onPluginsClick = { onPushKey(BiliPaiNavKey.PluginsSettings()) },
                    onSettingsShareClick = { onPushKey(BiliPaiNavKey.SettingsShare) },
                    onWebDavBackupClick = { onPushKey(BiliPaiNavKey.WebDavBackup) },
                    onNavigateToBottomBarSettings = { onPushKey(BiliPaiNavKey.BottomBarSettings) },
                    onTipsClick = { onPushKey(BiliPaiNavKey.TipsSettings) },
                    onKeyboardShortcutsClick = { onPushKey(BiliPaiNavKey.KeyboardShortcutsSettings) },
                    onProcessMemoryClick = { onPushKey(BiliPaiNavKey.ProcessMemory) },
                    onPerformanceHistoryClick = { onPushKey(BiliPaiNavKey.PerformanceHistory) },
                    onReplayOnboardingClick = { onPushTarget(BiliPaiNavKey.Onboarding) },
                    onCategoryClick = { category ->
                        onPushKey(resolveSettingsCategoryNavKey(category))
                    },
                    onSearchOpen = { onPushKey(BiliPaiNavKey.SettingsSearch) },
                    forceSinglePaneContent = true,
                )
            }
        }
        BiliPaiNavEntryContentRole.PROCESS_MEMORY -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.ProcessMemoryScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.PERFORMANCE_HISTORY -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.PerformanceHistoryScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.SETTINGS_SEARCH -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                SettingsSearchScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                    onCategoryClick = { category ->
                        onPushKey(resolveSettingsCategoryNavKey(category))
                    },
                    onSearchResultClick = { result ->
                        resolveSettingsSearchNavigation(result)?.let(onPushKey)
                    },
                    mainHazeState = mainHazeState,
                )
            }
        }
        BiliPaiNavEntryContentRole.OPEN_SOURCE_LICENSES -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.OpenSourceLicensesScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.APPEARANCE_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                AppearanceSettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                    onNavigateToIconSettings = { onPushKey(BiliPaiNavKey.IconSettings) },
                )
            }
        }
        BiliPaiNavEntryContentRole.HOME_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.HomeSettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.ICON_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.IconSettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.ANIMATION_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.AnimationSettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                PlaybackSettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.PERMISSION_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.PermissionSettingsScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.SPACE_PRIVACY_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.SpacePrivacySettingsScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.LOGIN_ACTIVITY_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.LoginActivitySettingsScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.MESSAGE_NOTIFICATION_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.MessageNotificationSettingsScreen(
                    onBack = onSystemBack,
                    onCustomPushClick = { onPushKey(BiliPaiNavKey.CustomPushSettings) },
                )
            }
        }
        BiliPaiNavEntryContentRole.CUSTOM_PUSH_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.CustomPushSettingsScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.PLUGINS_SETTINGS -> {
            val pluginsKey = key as BiliPaiNavKey.PluginsSettings
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.PluginsScreen(
                    onBack = onSystemBack,
                    initialImportUrl = pluginsKey.importUrl,
                    onNavigate = onPushKey,
                )
            }
        }
        BiliPaiNavEntryContentRole.PLUGIN_CENTER_DETAIL -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.PluginCenterDetailScreen(
                    key = key,
                    onBack = onSystemBack,
                    onOpenFilterHistory = { onPushKey(BiliPaiNavKey.ContentFilterHistory) },
                )
            }
        }
        BiliPaiNavEntryContentRole.CONTENT_FILTER_HISTORY -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.screen.ContentFilterHistoryScreen(
                    onBack = onSystemBack,
                    onVideoClick = { bvid -> onNavigateToVideo(bvid, 0L, "") },
                    onDynamicClick = { dynamicId -> onPushKey(BiliPaiNavKey.DynamicDetail(dynamicId)) },
                )
            }
        }
        BiliPaiNavEntryContentRole.JS_PLUGIN_CONTENT -> {
            val jsPluginKey = key as BiliPaiNavKey.JsPluginContent
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.plugin.js.BiliPaiJsPluginContentScreen(
                    pluginId = jsPluginKey.pluginId,
                    onBack = onSystemBack,
                    onPlayMedia = { launchId ->
                        onPushKey(BiliPaiNavKey.ExternalMedia(launchId))
                    },
                )
            }
        }
        BiliPaiNavEntryContentRole.EXTERNAL_MEDIA -> {
            val mediaKey = key as BiliPaiNavKey.ExternalMedia
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.plugin.js.ExternalMediaPlayerScreen(
                    launchId = mediaKey.launchId,
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.BOTTOM_BAR_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.BottomBarSettingsScreen(
                    viewModel = settingsViewModelProvider(),
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.SETTINGS_SHARE -> {
            val settingsShareViewModel: SettingsShareViewModel = viewModel(
                factory = remember(application) { SettingsShareViewModelFactory(application) },
            )
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.share.SettingsShareScreen(
                    onBack = onSystemBack,
                    viewModel = settingsShareViewModel,
                )
            }
        }
        BiliPaiNavEntryContentRole.WEB_DAV_BACKUP -> {
            val webDavBackupViewModel: WebDavBackupViewModel = viewModel(
                factory = remember(application) { WebDavBackupViewModelFactory(application) },
            )
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.webdav.WebDavBackupScreen(
                    onBack = onSystemBack,
                    viewModel = webDavBackupViewModel,
                )
            }
        }
        BiliPaiNavEntryContentRole.TIPS_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.TipsSettingsScreen(
                    onBack = onSystemBack,
                    onNavigate = onPushKey,
                )
            }
        }
        BiliPaiNavEntryContentRole.KEYBOARD_SHORTCUTS_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.KeyboardShortcutsSettingsScreen(
                    onBack = onSystemBack,
                )
            }
        }
        BiliPaiNavEntryContentRole.GAMEPAD_CONTROL_SETTINGS -> {
            SettingsTabletNavEntryShell(
                key = key,
                onSystemBack = onSystemBack,
                onPushKey = onPushKey,
            ) {
                com.android.purebilibili.feature.settings.GamepadControlSettingsScreen(
                    onBack = onSystemBack,
                )
            }
        }
        else -> Unit
    }
}
