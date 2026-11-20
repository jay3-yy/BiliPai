package com.android.purebilibili.feature.home.components

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChromeLiquidSurfaceStructureTest {

    @Test
    fun `top header uses shared renderer while bottom bar uses BiliPai renderer only`() {
        val workspaceRoot = generateSequence(
            Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        ) { current ->
            current.parent
        }.first { candidate ->
            Files.exists(
                candidate.resolve(
                    "app/src/main/java/com/android/purebilibili/feature/home/components/HomeHeader.kt"
                )
            )
        }
        val componentsDir = workspaceRoot.resolve(
            "app/src/main/java/com/android/purebilibili/feature/home/components"
        )
        val homeScreenSource = workspaceRoot.resolve(
            "app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt"
        ).readText()

        val topHeader = componentsDir.resolve("HomeHeader.kt")
        val topTabChrome = componentsDir.resolve("HomeTopTabChrome.kt")
        val topBar = componentsDir.resolve("TopBar.kt")
        val bottomBar = componentsDir.resolve("BottomBar.kt")
        val sharedChrome = componentsDir.resolve("BottomBarMatchedLiquidChrome.kt")

        assertFalse(
            "home chrome should not keep the old shared renderer file after migrating the only real dependency",
            Files.exists(componentsDir.resolve("HomeChromeLiquidSurface.kt"))
        )
        val topHeaderSource = topHeader.readText()
        val topBarSource = topBar.readText()
        val sharedChromeSource = sharedChrome.readText()
        assertTrue(
            "search-only collapse must keep the top dock visible",
            homeScreenSource.contains("val collapseSearchOnScroll = headerCollapseMode.collapseSearch") &&
                homeScreenSource.contains("val collapseTabsOnScroll = headerCollapseMode.collapseTabs")
        )
        assertTrue(
            "top header liquid chrome should route through the bottom-bar matched BiliPai surface",
            topHeaderSource.contains("return@composed this.homeTopBottomBarMatchedSurface(") &&
                topHeaderSource.contains("liquidGlassPreset: BottomBarLiquidGlassPreset") &&
                topHeaderSource.contains("private data class HomeTopChromeSurfaceStyle(") &&
                topHeaderSource.contains("private fun resolveHomeTopChromeBackdropSpec(")
        )
        assertFalse(
            "top header should not delegate to the removed app chrome renderer",
            topHeaderSource.contains(".appChromeLiquidSurface(") ||
                topHeaderSource.contains("AppChromeLiquidSurfaceStyle")
        )
        val topHeaderMatchedSurfaceCalls = Regex("""\.homeTopBottomBarMatchedSurface\(""")
            .findAll(topHeaderSource)
            .count()
        val topHeaderDisabledShellLensCalls = Regex("""drawShellLens\s*=\s*false""")
            .findAll(topHeaderSource)
            .count()
        assertTrue(
            "top header should use the same matched dock surface helper as the bottom bar",
            topHeaderMatchedSurfaceCalls > 0
        )
        assertTrue(
            "legacy flat chrome may disable lens while active compact surfaces use scaled intensity",
            topHeaderDisabledShellLensCalls >= 1 && topHeaderMatchedSurfaceCalls >= 1
        )
        assertTrue(
            "top tab row should only treat chrome as external when the outer surface is actually drawn",
            topHeaderSource.contains("hasOuterChromeSurface = drawTopTabDockChrome")
        )
        assertTrue(
            "home header should only draw a matched dock for presentations that need an outer track",
            topHeaderSource.contains("val topTabDockChromeRenderMode = resolveHomeTopTabDockChromeRenderMode(") &&
                topHeaderSource.contains("val topTabDockHazeState = hazeState.takeIf") &&
                topHeaderSource.contains("shouldApplyHomeTopTabDockHaze(") &&
                topHeaderSource.contains("localTabChromeRenderMode = unifiedLocalTabChromeRenderMode") &&
                topHeaderSource.contains("val topTabLiquidGlassEnabled =") &&
                topHeaderSource.contains("val drawTopTabDockChrome = drawTopTabOuterChromeSurface") &&
                topHeaderSource.contains("val useTopTabBottomBarMatchedDock = drawTopTabDockChrome") &&
                topHeaderSource.contains("shouldHomeTopTabChromeDrawOuterShell(") &&
                topHeaderSource.contains("useBottomBarMatchedSurface = useTopTabBottomBarMatchedDock") &&
                topHeaderSource.contains("drawMatchedShellLens = topTabLiquidGlassEnabled") &&
                topHeaderSource.contains("matchedShellLensIntensity = resolveFloatingDockGeometryScale(") &&
                topHeaderSource.contains("tabChromeRenderMode = if (useTopTabBottomBarMatchedDock)") &&
                topHeaderSource.contains("val bottomBarLiquidGlassPreset = homeSettings?.bottomBarLiquidGlassPreset") &&
                topHeaderSource.contains("liquidGlassPreset = bottomBarLiquidGlassPreset") &&
                topHeaderSource.contains("hazeState = topTabDockHazeState") &&
                topHeaderSource.contains("topTabDockChromeRenderMode") &&
                topHeaderSource.contains("tabShape = if (useUnifiedTopPanel)") &&
                topHeaderSource.contains("resolveSharedBottomBarCapsuleShape()") &&
                topTabChrome.readText().contains("useBottomBarMatchedSurface: Boolean = false") &&
                topTabChrome.readText().contains("drawMatchedShellLens: Boolean = true") &&
                topTabChrome.readText().contains("matchedShellLensIntensity: Float = TOP_DOCK_SHELL_LENS_INTENSITY") &&
                topTabChrome.readText().contains("shellLensIntensity = matchedShellLensIntensity") &&
                topTabChrome.readText().contains("liquidGlassPreset: BottomBarLiquidGlassPreset") &&
                topTabChrome.readText().contains(".homeTopBottomBarMatchedSurface(") &&
                topBarSource.contains("TOP_DOCK_SHELL_LENS_INTENSITY")
        )
        assertTrue(
            "home top avatar, search content and unread badge should live in extracted top-control components",
            componentsDir.resolve("HomeTopControls.kt").readText().contains("HomeTopAvatarContent(") &&
                componentsDir.resolve("HomeTopControls.kt").readText().contains("HomeTopSearchPillContent(") &&
                componentsDir.resolve("HomeTopControls.kt").readText().contains("HomeTopUnreadBadge(") &&
                topHeaderSource.contains("HomeTopAvatarContent(") &&
                topHeaderSource.contains("HomeTopSearchPillContent(") &&
                topHeaderSource.contains("HomeTopUnreadBadge(")
        )
        val searchLayerIndex = topHeaderSource.indexOf(".height(currentSearchHeight)")
        val tabsThenSearchIndex = topHeaderSource.indexOf("if (topLayoutOrder == HomeTopLayoutOrder.TABS_THEN_SEARCH)")
        val searchThenTabsIndex = topHeaderSource.indexOf("if (topLayoutOrder == HomeTopLayoutOrder.SEARCH_THEN_TABS)")
        assertTrue(
            "search-first mode should render top tabs after the search layer",
            searchLayerIndex in 0 until searchThenTabsIndex &&
                topHeaderSource.indexOf("topTabsContent(", startIndex = searchThenTabsIndex) > searchThenTabsIndex
        )
        assertTrue(
            "an independently rendered liquid dock must not inherit the collapsing search panel clip",
            topHeaderSource.contains("if (drawUnifiedTopPanelChrome) {\n                                        Modifier.clip(unifiedPanelShape)")
        )
        assertTrue(
            "tabs-first mode should keep its explicit branch before the search layer",
            tabsThenSearchIndex in 0 until searchLayerIndex
        )
        assertTrue(
            "top tab row should use the lightweight native tab implementation",
            topBarSource.contains("LightweightHomeTopTabs(") &&
                topBarSource.contains("resolveTopTabClickAction(index, selectedIndex)")
        )
        assertFalse(
            "top tab row should not keep the old liquid dock renderer",
            topBarSource.contains("private fun TopTabDockSurface(") ||
                topBarSource.contains("private fun Md3CategoryTabRow(")
        )
        assertFalse(
            "top tab chrome should not clip the enlarged child indicator",
            topTabChrome.readText().contains("Modifier.clip(tabShape)")
        )
        assertTrue(
            "top tab content should be a sibling overlay outside the clipped shell surface and allow indicator overflow",
            topTabChrome.readText().let { source ->
                source.contains("alpha = tabContentAlphaProvider.value") &&
                    source.contains("clip = false") &&
                    source.contains("contentAlignment = Alignment.Center") &&
                    source.contains("content()")
            }
        )
        assertTrue(
            "top tab indicator host should not clip drag-scale overflow past the dock",
            Regex(
                """\.fillMaxSize\(\)\s*\n\s*\.zIndex\(1f\)\s*\n\s*\.graphicsLayer\s*\{\s*clip = false\s*\}"""
            ).containsMatchIn(topBarSource)
        )
        assertTrue(
            "top tab chrome should center the fixed-height tab row inside the taller shell",
            topTabChrome.readText().contains("contentAlignment = Alignment.Center")
        )
        assertFalse(
            "top tab dock should not switch sampling off during feed scroll",
            topBarSource.contains("shouldSampleTopTabDockBackdrop(")
        )
        assertFalse(
            "home top chrome fallback backgrounds must keep the provided shape so scrolling or low-budget material paths do not flash square corners",
            topHeaderSource.contains(".background(surfaceColor)") ||
                topHeaderSource.contains(".background(resolvedSurfaceColor)")
        )
        assertTrue(
            "top tab row should continue to follow pager drag offset",
            topBarSource.contains("resolveTopTabIndicatorRenderPosition(") &&
                topBarSource.contains("pagerCurrentPageOffsetFraction = pagerState?.currentPageOffsetFraction")
        )
        assertTrue(
            "MD3 top tab indicator should be a single moving layer tied to pager offset",
            topBarSource.contains("resolveMd3TopTabIndicatorTranslationPx(") &&
                (
                    topBarSource.contains("translationX = md3IndicatorTranslationXPx") ||
                        topBarSource.contains("translationX = md3LiquidCapsuleTranslationXPx") ||
                        topBarSource.contains("md3LiquidCapsuleTranslationXPx")
                    )
        )
        val lightweightTopTabItemSource = topBarSource
            .substringAfter("private fun LightweightTopTabItem(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")
        assertFalse(
            "MD3 top tab item should not draw a second per-item underline",
            lightweightTopTabItemSource.contains(".align(Alignment.BottomCenter)")
        )
        assertTrue(
            "matched top dock helper should delegate to the same shared surface as the bottom bar",
            topBarSource.contains(".bottomBarMatchedLiquidDockSurface(") &&
                topBarSource.contains("liquidGlassPreset: BottomBarLiquidGlassPreset") &&
                topBarSource.contains("liquidGlassPreset = liquidGlassPreset")
        )
        assertTrue(
            "top tab indicator should reuse the bottom bar matched indicator and sibling capture topology",
            topBarSource.contains("val shouldUseMd3LiquidCapsule = effectivePresentation == AppTopTabPresentation.MATERIAL_UNDERLINE") &&
                topBarSource.contains("resolveHomeSelectionIndicatorStyle(") &&
                topBarSource.contains("shouldUseHomeCapsule") &&
                topBarSource.contains("!hasOuterChromeSurface") &&
                topBarSource.contains("val shouldUseMd3DockBackedCapsule =") &&
                topBarSource.contains("BiliPaiFloatingDockIndicator(") &&
                !topBarSource.contains("BottomBarMatchedLiquidIndicator(") &&
                topBarSource.contains("val shouldPrimeTopTabLiquidGlassCapture =") &&
                !topBarSource.contains("val topTabContentBackdrop = rememberLayerBackdrop()") &&
                topBarSource.contains("val topTabMiuixContentBackdrop = rememberMiuixLayerBackdrop()") &&
                !topBarSource.contains("layerBackdrop(topTabContentBackdrop)") &&
                topBarSource.contains("miuixLayerBackdrop(topTabMiuixContentBackdrop)") &&
                topBarSource.contains("rememberMiuixCombinedBackdrop(miuixBackdrop, topTabMiuixContentBackdrop)") &&
                topBarSource.contains("biliPaiFloatingDockCaptureSurface(") &&
                topBarSource.contains("biliPaiFloatingDockShell(") &&
                topBarSource.contains("ColorFilter.tint(topTabExportTintColor)") &&
                topBarSource.contains("TopTabLiquidColorMode.GLASS_EXPORT") &&
                topBarSource.contains("TopTabLiquidColorMode.GLASS_VISIBLE") &&
                topBarSource.contains("resolveSharedLiquidExportMonochromeColor(") &&
                topBarSource.contains("resolveTopTabIndicatorBackdropPolicy(") &&
                !topBarSource.contains("topTabIndicatorDrag(") &&
                topBarSource.contains("topTabListScrollOffsetPx") &&
                topBarSource.contains("One shared shift for export") &&
                topBarSource.contains("indicatorPanelOffsetPx = 0f") &&
                topBarSource.contains("!shouldUseMd3DockBackedCapsule && !shouldUseMd3LiquidCapsule") &&
                !sharedChromeSource.contains("BiliPaiBottomBarIndicatorLayer(")
        )
        assertFalse(
            "top tab row should not keep the old bottom-bar local backdrop capture names",
            topBarSource.contains("backdrop = tabsBackdrop") ||
                topBarSource.contains(".layerBackdrop(tabsBackdrop)") ||
                topBarSource.contains("rememberCombinedBackdrop(backdrop, tabsBackdrop)") ||
                topBarSource.contains("rememberCombinedBackdrop(backdrop, tabContentBackdrop)") ||
                topBarSource.contains("rememberCombinedBackdrop(backdrop, topTabContentBackdrop)")
        )
        assertFalse(
            "top tab indicator should not keep its old custom indicator renderer",
            topBarSource.contains("BottomBarStyleIndicatorSurface(") ||
                Regex("""(?m)^\s*LiquidIndicator\(""").containsMatchIn(topBarSource) ||
                topBarSource.contains("rememberCombinedBackdrop(backdrop, tabsBackdrop)") ||
                topBarSource.contains("rememberCombinedBackdrop(backdrop, tabContentBackdrop)")
        )
        assertTrue(
            "Miuix dock surface should use vibrancy, blur, and soft-scalable shell lens like the floating bottom bar",
            bottomBar.readText().contains("internal fun Modifier.biliPaiMiuixFloatingDockSurface(") &&
                !bottomBar.readText().contains("internal fun Modifier.biliPaiFloatingDockSurface(") &&
                bottomBar.readText().contains("miuixVibrancy()") &&
                bottomBar.readText().contains("drawShellLens: Boolean = true") &&
                bottomBar.readText().contains("shellLensIntensity: Float = 1f") &&
                bottomBar.readText().contains("effectiveShellLensIntensity") &&
                bottomBar.readText().contains("drawShellLens &&") &&
                bottomBar.readText().contains("shellRefractionHeightDp") &&
                bottomBar.readText().contains("shellRefractionAmountDp")
        )
        assertFalse(
            "bottom bar should not keep the old appChromeLiquidSurface renderer",
            bottomBar.readText().contains(".appChromeLiquidSurface(")
        )
        assertFalse(
            "bottom bar should not keep the old floating dock surface style",
            bottomBar.readText().contains("resolveFloatingDockLiquidSurfaceStyle(")
        )
        assertFalse(
            "bottom bar should not keep the old LiquidIndicator renderer",
            Regex("""(?m)^\s*LiquidIndicator\(""").containsMatchIn(bottomBar.readText())
        )
        assertFalse(
            "bottom bar should not keep the old BottomBarContent renderer",
            bottomBar.readText().contains("BottomBarContent(")
        )
    }

    @Test
    fun `top tab indicator reuses bottom bar idle backdrop policy`() {
        assertFalse(
            shouldRenderBottomBarIndicatorBackdrop(
                glassEnabled = true,
                hasContentBackdrop = true,
                indicatorProgress = 0f,
                isTransitionRunning = false,
                isBottomBarInteractionActive = false,
                allowIdleGlassEffect = false,
                allowTransitionIndicatorPulse = false
            )
        )
    }
}
