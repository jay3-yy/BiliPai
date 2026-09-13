package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiuixV2MigrationStructureTest {

    @Test
    fun webDavBackupScreen_usesSettingsPageScaffold_notLargeTitleBar() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/webdav/WebDavBackupScreen.kt")
        assertTrue(source.contains("SettingsPageScaffold("))
        assertFalse(source.contains("iOSLargeTitleBar("))
        assertFalse(source.contains("globalWallpaperAwareBackground("))
    }

    @Test
    fun settingsShareScreen_usesSettingsPageScaffold_notLargeTitleBar() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/share/SettingsShareScreen.kt")
        assertTrue(source.contains("SettingsPageScaffold("))
        assertFalse(source.contains("iOSLargeTitleBar("))
    }

    @Test
    fun iosSectionTitle_usesMiuixSmallTitleOnMiuixBranch() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
        assertTrue(source.contains("SmallTitle("))
        assertTrue(source.contains("if (uiStyle == AppUiStyle.MIUIX) {"))
    }

    @Test
    fun appAlertDialog_routesMiuixStyleToWindowDialog() {
        // 2B 迁移：MIUIX 经两值模型路由到窗口级 LOCAL_DIALOG，不再按旧 variant 分支。
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AdaptiveDialogComponents.kt")
        assertTrue(source.contains("AppUiStyle.MIUIX ->"))
        assertTrue(source.contains("AppAlertDialogRenderer.LOCAL_DIALOG"))
        assertTrue(source.contains("Dialog("))
    }

    @Test
    fun appSurfaceTokens_exposesMiuixSemanticColors() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AppSurfaceTokens.kt")
        assertTrue(source.contains("fun onSurfaceVariantSummary()"))
        assertTrue(source.contains("fun onSurfaceVariantActions()"))
        assertTrue(source.contains("MiuixTheme.colorScheme.onSurfaceVariantSummary"))
    }

    @Test
    fun versionCatalog_pinsMiuixToPublishedUpstreamSnapshot() {
        val source = loadSource("gradle/libs.versions.toml")
        assertTrue(source.contains("miuix = \"0.9.4-5157b503-SNAPSHOT\""))
    }

    @Test
    fun buildGradle_includesMiuixShaderArtifact() {
        val source = loadSource("app/build.gradle.kts")
        assertTrue(source.contains("miuix-shader-android"))
    }

    @Test
    fun featureLayer_avoidsDirectMiuixThemeColorSchemeReads() {
        val allowed = setOf(
            "app/src/main/java/com/android/purebilibili/core/theme/Theme.kt",
            "app/src/main/java/com/android/purebilibili/core/ui/AppSurfaceTokens.kt",
            "design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt"
        )
        val offenders = listOf(
            "app/src/main/java/com/android/purebilibili/feature/search/SearchScreen.kt",
            "app/src/main/java/com/android/purebilibili/feature/home/components/TopBar.kt",
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt",
            "app/src/main/java/com/android/purebilibili/feature/message/InboxScreen.kt",
            "app/src/main/java/com/android/purebilibili/feature/message/feed/MessageFeedCommon.kt",
            "app/src/main/java/com/android/purebilibili/feature/video/ui/section/AiSummarySection.kt",
            "app/src/main/java/com/android/purebilibili/feature/video/ui/section/VideoNoteSection.kt",
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/VideoSettingsPanel.kt",
            "app/src/main/java/com/android/purebilibili/feature/settings/AppSegmentedControl.kt",
            "app/src/main/java/com/android/purebilibili/navigation3/BiliPaiNavDisplayHost.kt",
        ).filter { path ->
            loadSource(path).contains("MiuixTheme.colorScheme")
        }
        assertTrue(
            offenders.isEmpty(),
            "Direct MiuixTheme.colorScheme reads should route through AppSurfaceTokens:\n" +
                offenders.joinToString("\n")
        )
    }

    @Test
    fun buildGradle_includesMiuixSquircleArtifact() {
        val source = loadSource("app/build.gradle.kts")
        assertTrue(source.contains("miuix-squircle-android"))
    }

    @Test
    fun buildGradle_includesMiuixIconsArtifact() {
        val source = loadSource("app/build.gradle.kts")
        assertTrue(source.contains("miuix-icons-android"))
    }

    @Test
    fun md3SegmentedControl_routesMiuixVariantToTabRow() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/renderer/miuix/AppMiuixSegmentedControl.kt")
        val componentSource = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/components/AppSegmentedControl.kt")
        val policySource = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AppSegmentedControlPolicy.kt")
        // 双值 AppUiStyle 政策：MIUIX 用原生 TabRow，MATERIAL3 用 Material fallback。
        assertTrue(policySource.contains("AppUiStyle.MIUIX"))
        assertTrue(policySource.contains("usesNativeTabRow = true"))
        assertTrue(policySource.contains("usesNativeTabRow = false"))
        assertTrue(componentSource.contains("resolveAppSegmentedRenderer(policy.usesNativeTabRow)"))
        assertTrue(componentSource.contains("AppMiuixSegmentedControl("))
        assertTrue(source.contains("TabRow("))
        assertTrue(source.contains("rememberLazyListState()"))
        assertTrue(source.contains("selectedIndex == 0 || selectedIndex == options.lastIndex"))
        assertTrue(source.contains("scrollState.scrollToItem(selectedIndex)"))
    }

    @Test
    fun adaptivePullToRefreshBox_routesMiuixVariantToMiuixPullToRefresh() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AdaptivePullToRefreshBox.kt")
        assertTrue(source.contains("MiuixPullToRefresh("))
        assertTrue(source.contains("PresetPrimitiveRenderer.MIUIX_BRIDGED"))
        assertTrue(source.contains("ComfortablePullToRefreshBox("))
        assertTrue(source.contains("indicatorTopInset"))
        assertTrue(source.contains("mergedContentPadding"))
    }

    @Test
    fun homeScreen_usesAdaptivePullToRefreshBox() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        assertTrue(source.contains("AdaptivePullToRefreshBox("))
        assertFalse(source.contains("ComfortablePullToRefreshBox("))
    }

    @Test
    fun ioSearchBar_miuixBranchUsesOfficialInputField() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
        assertTrue(source.contains("InputField("))
        assertTrue(source.contains("shouldUseNativeMiuixSearchBar("))
    }

    @Test
    fun iosClickableItem_routesThroughAdaptiveListItemPolicy() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/components/AdaptivePreferenceComponents.kt")
        assertTrue(source.contains("resolveAppClickableItemRenderer("))
        assertTrue(source.contains("AppClickableItemRenderer.MIUIX_ARROW"))
        assertTrue(source.contains("AppClickableItemRenderer.MIUIX_BASIC"))
        assertTrue(source.contains("shouldRouteSwitchItemToMiuixSwitchPreference("))
        assertTrue(source.contains("shouldRouteSliderPreferenceToMiuixSliderPreference("))
        assertTrue(source.contains("MiuixSliderPreference("))
    }

    @Test
    fun themeController_remembersUserThemeInputsWithMiuixBridgeColors() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/core/theme/Theme.kt")
        assertTrue(source.contains("ThemeController("))
        assertTrue(source.contains("customPrimaryColor,"))
        assertTrue(source.contains("themeRoleOverrides,"))
        assertTrue(source.contains("amoledDarkTheme,"))
        assertTrue(source.contains("resolveMiuixColorsFromMaterialBridge("))
    }

    @Test
    fun miuixDockedBottomBar_routesStandardItemsThroughPlatformNavigationFacade() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt")
        assertTrue(source.contains("AppPlatformNavigationBarItem("))
        assertTrue(source.contains("shouldUseMiuixOfficialNavigationBarItem("))
    }

    @Test
    fun searchTopBar_usesNeutralSearchField() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/search/SearchScreen.kt")
        // 迁移后的搜索页：共享 BasicTextField 实现 + 中性 App* 组件，
        // 不再使用 AppSearchField，也不再有原生 chrome 分发（该组件仍服务于其余列表页）。
        assertTrue(source.contains("SearchTopBarInputField("))
        assertTrue(source.contains("AppIconButton("))
        assertFalse(source.contains("resolveSearchNativeChrome("))
        assertFalse(source.contains("SearchNativeChrome"))
    }

    @Test
    fun homePullRefreshPolicy_routesMiuixToNativeIndicator() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AppPullRefreshIndicator.kt")
        assertTrue(source.contains("AppPullRefreshIndicatorStyle.MIUIX_NATIVE"))
    }

    @Test
    fun adaptiveScaffold_miuixPathMountsPopupHostForOverlayDialogs() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AdaptiveChrome.kt")
        assertTrue(source.contains("resolveAdaptiveScaffoldRenderer("))
        assertTrue(source.contains("MiuixPopupUtils.MiuixPopupHost()"))
        assertTrue(source.contains("popupHost ="))
    }

    @Test
    fun adaptiveScaffoldPolicy_requiresMiuixPopupHostOnMiuixVariant() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AdaptiveScaffoldPolicy.kt")
        assertTrue(source.contains("shouldMountMiuixPopupHostOnAdaptiveScaffold("))
        assertTrue(source.contains("MIUIX_SCAFFOLD_WITH_POPUP_HOST"))
    }

    @Test
    fun featureLayer_doesNotCallIosLargeTitleBarDirectly() {
        val featureRoot = File("app/src/main/java/com/android/purebilibili/feature")
        val offenders = featureRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("iOSLargeTitleBar(") }
            .map { it.path }
            .toList()
        assertTrue(
            offenders.isEmpty(),
            "iOSLargeTitleBar should remain iOS-only chrome; migrate feature screens to AdaptiveScaffold:\n" +
                offenders.joinToString("\n")
        )
    }

    @Test
    fun legacyIosLargeTitleBar_isRemovedAfterFeatureMigration() {
        val source = File("app/src/main/java/com/android/purebilibili/core/ui/iOSLargeTitleBar.kt")
        assertFalse(source.exists())
    }

    @Test
    fun md3SegmentedControl_usesAdaptiveSquircleBackground() {
        val source = loadSource(
            "design-system/src/main/java/com/android/purebilibili/core/ui/renderer/miuix/AppMiuixSegmentedControl.kt"
        )
        assertTrue(source.contains("adaptiveSquircleBackground("))
    }

    @Test
    fun appSurfaceTokens_exposesFullMiuixSemanticPalette() {
        val source = loadSource("design-system/src/main/java/com/android/purebilibili/core/ui/AppSurfaceTokens.kt")
        listOf(
            "fun background()",
            "fun surface()",
            "fun surfaceContainer()",
            "fun surfaceContainerHigh()",
            "fun onSecondaryContainer()",
            "fun onSurfaceContainerHigh()",
            "fun onSurfaceContainerHighest()",
            "fun primary()",
            "fun resolveMiuixSemanticColor("
        ).forEach { token ->
            assertTrue(source.contains(token), "Missing token: $token")
        }
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File("../$path"),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
