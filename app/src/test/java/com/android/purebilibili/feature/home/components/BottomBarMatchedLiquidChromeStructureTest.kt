package com.android.purebilibili.feature.home.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarMatchedLiquidChromeStructureTest {

    @Test
    fun `shared chrome owns dock indicator state orientation and visibility contracts`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarMatchedLiquidChrome.kt"
        )

        assertTrue(source.contains("internal class BottomBarMatchedLiquidChromeState"))
        assertTrue(source.contains("internal fun rememberBottomBarMatchedLiquidChromeState("))
        assertTrue(source.contains("DampedDragTrackingMode.BILIPAI_SPRING"))
        assertTrue(source.contains("pressedScale: Float = FloatingBottomBarPressedScale"))
        assertTrue(source.contains("internal fun BottomBarMatchedLiquidDock("))
        assertTrue(source.contains("internal fun BoxScope.BottomBarMatchedLiquidIndicator("))
        assertTrue(source.contains("interactionModifier: Modifier = Modifier"))
        assertTrue(source.contains("internal enum class BottomBarLiquidOrientation"))
        assertTrue(source.contains("BottomBarLiquidOrientation.VERTICAL"))
        assertTrue(source.contains("swapMotionAxes = orientation == BottomBarLiquidOrientation.VERTICAL"))
        assertTrue(source.contains("internal fun BottomBarMatchedDockVisibility("))
        assertTrue(source.contains("internal enum class BottomBarMatchedDockEdge"))
        assertTrue(source.contains("    TOP,"))
        assertTrue(source.contains("    BOTTOM"))
        assertTrue(source.contains("resolveBottomBarMaterialScrollAnimationDurationMillis(isScrolling)"))
        assertTrue(source.contains("BiliPaiMiuixBottomBarIndicatorLayer("))
        assertTrue(source.contains("liquidGlassTuning = liquidGlassTuning"))
        // Legacy indicator path removed — chrome is Miuix-only.
        assertFalse(source.contains("BiliPaiBottomBarIndicatorLayer("))
        assertFalse(source.contains("legacyBackdrop"))
        assertFalse(source.contains("legacyContentBackdrop"))
        assertFalse(source.contains("biliPaiFloatingDockSurface("))
        assertTrue(source.contains("val localBackdrop = if (backdrop == null) rememberLayerBackdrop() else null"))
        assertTrue(source.contains("val effectiveBackdrop = backdrop ?: localBackdrop"))
        assertTrue(source.contains("if (localBackdrop != null)"))
        assertFalse(source.contains("rememberCombinedBackdrop(localBackdrop, backdrop)"))
        assertTrue(source.contains("bottomBarMatchedCaptureOverflow(captureSafeInset)"))
        assertTrue(source.contains("biliPaiMiuixFloatingDockSurface("))
        assertTrue(source.contains("reuseEnabled: Boolean = false"))
        assertTrue(source.contains("if (!reuseEnabled || !reuseAllowed || !liquidGlassEffectsEnabled)"))
        assertTrue(source.contains("LocalLiquidGlassRenderConfig.current"))
        assertFalse(source.contains("SettingsManager.getHomeSettings"))
        assertFalse(source.contains("collectAsStateWithLifecycle"))
    }

    @Test
    fun `navigation root provides one narrow liquid glass render configuration`() {
        val navigation = loadSource(
            "app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"
        )

        assertTrue(navigation.contains("LiquidGlassRenderConfig("))
        assertTrue(
            navigation.contains(
                "LocalLiquidGlassRenderConfig provides liquidGlassRenderConfig"
            )
        )
    }

    @Test
    fun `bottom top and segmented chrome all delegate to shared implementation`() {
        val bottomBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt"
        )
        val topBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/TopBar.kt"
        )
        val segmented = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarLiquidSegmentedControl.kt"
        )
        val floating = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarFloatingSegmentedControl.kt"
        )
        val sharedChrome = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarMatchedLiquidChrome.kt"
        )

        listOf(bottomBar, topBar).forEach { source ->
            assertTrue(source.contains("rememberBottomBarMatchedLiquidChromeState("))
            assertTrue(source.contains("BottomBarMatchedLiquidIndicator("))
        }
        assertTrue(bottomBar.contains("BottomBarMatchedLiquidDock("))
        assertTrue(topBar.contains(".bottomBarMatchedLiquidDockSurface("))
        assertFalse(topBar.contains(".biliPaiFloatingDockSurface("))
        assertFalse(topBar.contains(".biliPaiMiuixFloatingDockSurface("))
        assertTrue(segmented.contains("BottomBarFloatingSegmentedControl("))
        assertTrue(floating.contains("FloatingBottomBar("))
        assertTrue(floating.contains("FloatingBottomBarItem("))
        assertFalse(floating.contains(".drawBackdrop("))
        assertTrue(sharedChrome.contains("drawShellLens = drawShellLens"))
        assertTrue(
            bottomBar.contains("homeIndicatorLens(")
        )
        assertTrue(
            floating.contains("homeIndicatorLens(")
        )
        assertFalse(bottomBar.contains("chromaticAberration = 0.5f"))
        assertFalse(segmented.contains(".biliPaiFloatingDockSurface("))
        assertFalse(segmented.contains(".biliPaiMiuixFloatingDockSurface("))
        assertFalse(segmented.contains("BiliPaiBottomBarIndicatorLayer("))
        assertFalse(segmented.contains("BiliPaiMiuixBottomBarIndicatorLayer("))
    }

    @Test
    fun `dynamic search detail and partition chrome use shared entry points`() {
        val dynamicTopBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/components/DynamicTopBar.kt"
        )
        val dynamicScreen = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/DynamicScreen.kt"
        )
        val search = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/search/SearchScreen.kt"
        )
        val bottomInput = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/BottomInputBar.kt"
        )
        val partition = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/partition/PartitionScreen.kt"
        )
        val musicPlayer = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/audio/screen/MusicPlayerContent.kt"
        )

        assertTrue(dynamicTopBar.contains("AppThemeAdaptiveTabRow("))
        assertTrue(dynamicTopBar.contains("indicatorPositionProvider = indicatorPositionProvider"))
        assertTrue(dynamicTopBar.contains("isScrollInProgressProvider = isScrollInProgressProvider"))
        assertTrue(dynamicScreen.contains("BottomBarMatchedDockVisibility("))
        assertTrue(dynamicScreen.contains("edge = BottomBarMatchedDockEdge.TOP"))
        assertFalse(search.contains("BottomBarMatchedReusableLiquidDock("))
        assertTrue(search.contains("drawShellLens = false"))
        assertTrue(bottomInput.contains("BottomBarMatchedReusableLiquidDock("))
        // 独立评论胶囊保留 lens，并按实际高度缩放折射几何。
        assertTrue(bottomInput.contains("drawShellLens = true"))
        assertTrue(bottomInput.contains("shellLensIntensity = resolveFloatingDockGeometryScale(44f)"))
        assertFalse(bottomInput.contains("drawShellLens = false"))
        assertFalse(bottomInput.contains("BottomBarMatchedLiquidDock("))
        assertFalse(bottomInput.contains(".biliPaiFloatingDockSurface("))
        assertTrue(partition.contains("DampedDragAnimation("))
        assertTrue(partition.contains("BottomBarMatchedLiquidIndicator("))
        assertFalse(partition.contains("rememberBottomBarMatchedLiquidChromeState("))
        assertTrue(partition.contains("orientation = BottomBarLiquidOrientation.VERTICAL"))
        assertFalse(partition.contains("BiliPaiBottomBarIndicatorLayer("))
        assertFalse(musicPlayer.contains("BottomBarMatchedReusableLiquidDock("))
        assertFalse(musicPlayer.contains("bottomBarMatchedLiquidDockSurface("))
        assertFalse(musicPlayer.contains("biliPaiMiuixFloatingDockSurface("))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(File(path), File(normalizedPath)).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
