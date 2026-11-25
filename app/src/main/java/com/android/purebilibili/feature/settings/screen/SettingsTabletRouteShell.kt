package com.android.purebilibili.feature.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.AppHingeOrientation
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.LocalWindowSizeClass
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.feature.settings.isSettingsSearchNavKey
import com.android.purebilibili.feature.settings.isSettingsSubtreeNavKey
import com.android.purebilibili.feature.settings.resolveSettingsCategoryNavKey
import com.android.purebilibili.feature.settings.resolveSettingsTabletShellCategory
import com.android.purebilibili.feature.settings.shouldUseSettingsSplitLayout
import com.android.purebilibili.feature.settings.ui.SettingsOpaqueSurfaceHost
import com.android.purebilibili.navigation3.BiliPaiNavKey

@Composable
private fun rememberSettingsHasVerticalSeparatingHinge(): Boolean {
    val adaptiveInfo = LocalAppWindowAdaptiveInfo.current
    return adaptiveInfo.posture == AppFoldPosture.Book ||
        (adaptiveInfo.shouldAvoidHinge &&
            adaptiveInfo.foldingFeature.hingeOrientation == AppHingeOrientation.Vertical)
}

/** 与设置脚手架使用相同的窗口和铰链条件，避免单栏退化时误禁用页面导航。 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun settingsHasPersistentPanes(): Boolean {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val windowWidthDp = LocalWindowSizeClass.current.widthDp.value.toInt()
    val hasVerticalHinge = rememberSettingsHasVerticalSeparatingHinge()
    return shouldUseSettingsSplitLayout(windowWidthDp, hasVerticalHinge) &&
        directive.maxHorizontalPartitions > 1 &&
        LocalAppWindowAdaptiveInfo.current.posture != AppFoldPosture.Tabletop
}

@Composable
internal fun SettingsTabletNavEntryShell(
    key: BiliPaiNavKey,
    onSystemBack: () -> Unit,
    onPushKey: (BiliPaiNavKey) -> Unit,
    content: @Composable () -> Unit,
) {
    SettingsOpaqueSurfaceHost {
        SettingsTabletRouteShell(
            key = key,
            onBack = onSystemBack,
            onCategoryClick = { category -> onPushKey(resolveSettingsCategoryNavKey(category)) },
            onSearchOpen = { onPushKey(BiliPaiNavKey.SettingsSearch) },
            phoneContent = content,
        )
    }
}

@Composable
internal fun SettingsTabletRouteShell(
    key: BiliPaiNavKey,
    onBack: () -> Unit,
    onCategoryClick: (com.android.purebilibili.feature.settings.SettingsRootCategory) -> Unit,
    onSearchOpen: () -> Unit,
    phoneContent: @Composable () -> Unit,
) {
    val windowWidthDp = LocalWindowSizeClass.current.widthDp.value.toInt()
    val hasVerticalHinge = rememberSettingsHasVerticalSeparatingHinge()
    if (shouldUseSettingsSplitLayout(widthDp = windowWidthDp, hasVerticalSeparatingHinge = hasVerticalHinge) &&
        isSettingsSubtreeNavKey(key)
    ) {
        SettingsTabletShell(
            selectedCategory = resolveSettingsTabletShellCategory(key),
            onCategoryClick = onCategoryClick,
            onBack = onBack,
            onSearchOpen = onSearchOpen,
            isSearchActive = isSettingsSearchNavKey(key),
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
