package com.android.purebilibili.feature.home.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReusableLiquidGlassBackdropStructureTest {

    @Test
    fun `production liquid glass uses only miuix backdrop`() {
        val projectRoot = listOf(File("."), File(".."))
            .first { File(it, "app/src/main").exists() }
        val productionSources = listOf(
            File(projectRoot, "app/src/main"),
            File(projectRoot, "design-system/src/main"),
        )
        val legacyVendor = "ky" + "ant"
        val legacyPackage = listOf("com", legacyVendor, "backdrop").joinToString(".")
        val legacySources = productionSources
            .flatMap { root -> root.walkTopDown().filter(File::isFile).toList() }
            .filter { source -> source.extension == "kt" && legacyPackage in source.readText() }

        assertTrue(legacySources.isEmpty(), "Legacy backdrop remains in: $legacySources")
        val legacyCoordinate = listOf("io.github.${legacyVendor}0", "backdrop").joinToString(":")
        assertFalse(
            File(projectRoot, "app/build.gradle.kts").readText().contains(legacyCoordinate)
        )
    }

    @Test
    fun `audio library keeps pager outside segmented control and synchronizes indicator`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/audio/screen/ListenVideoScreen.kt"
        )

        val segmentedControlSource = source
            .substringAfter("BottomBarLiquidSegmentedControl(")
            .substringBefore("HorizontalPager(")

        assertTrue(segmentedControlSource.contains("indicatorPositionProvider = {"))
        assertTrue(segmentedControlSource.contains("pagerState.tabIndicatorPosition"))
        assertTrue(segmentedControlSource.contains("preferInlineContentStyle = false"))
        assertTrue(source.contains("HorizontalPager("))
        assertTrue(source.indexOf("HorizontalPager(") > source.indexOf("BottomBarLiquidSegmentedControl("))
    }

    @Test
    fun `bangumi tabs sample full bounds with background and stay outside source`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/bangumi/ui/player/BangumiPlayerContent.kt"
        )

        assertTrue(source.contains("val selectionBackdrop = rememberLayerBackdrop()"))
        assertTrue(source.contains("miuixBackdrop = if (liquidGlassEnabled) selectionBackdrop else null"))
        assertTrue(source.contains(".layerBackdrop(selectionBackdrop)"))
        val captureStart = source.indexOf(".layerBackdrop(selectionBackdrop)")
        val pagerStart = source.indexOf("HorizontalPager(")
        val overlayStart = source.indexOf("if (liquidGlassEnabled && !commentsOnly)")
        assertTrue(captureStart < pagerStart)
        assertTrue(pagerStart < overlayStart)
        assertTrue(
            source.substring(captureStart, pagerStart)
                .contains(".background(MaterialTheme.colorScheme.background)")
        )
        assertTrue(source.contains("tabBarContent(Modifier.fillMaxWidth())"))
        assertTrue(source.contains(".onSizeChanged { tabBarHeightPx.intValue = it.height }"))
    }

    @Test
    fun `today watch mode samples queue content from a sibling layer`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt"
        )

        assertTrue(source.contains("val todayWatchBackdrop = rememberLayerBackdrop()"))
        assertTrue(source.contains("miuixBackdrop = todayWatchBackdrop"))
        assertTrue(source.contains(".layerBackdrop(todayWatchBackdrop)"))
        assertTrue(
            source.indexOf(".layerBackdrop(todayWatchBackdrop)") <
                source.indexOf("miuixBackdrop = todayWatchBackdrop")
        )
    }

    @Test
    fun `dynamic comment sort samples list content from a sibling layer`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/components/DynamicCommentSheet.kt"
        )

        assertTrue(source.contains("val commentChromeBackdrop = rememberLayerBackdrop()"))
        assertTrue(source.contains("backdrop = commentChromeBackdrop"))
        assertTrue(source.contains(".layerBackdrop(commentChromeBackdrop)"))
        assertTrue(source.contains(".matchParentSize()\n                    .layerBackdrop(commentChromeBackdrop)"))
        assertTrue(
            source.indexOf(".layerBackdrop(commentChromeBackdrop)") <
                source.indexOf("miuixBackdrop = commentChromeBackdrop")
        )
        assertTrue(source.contains("DynamicAdaptiveSegmentedControl("))
        assertFalse(source.contains("CommentSegmentedControl("))
        val inlineHeader = source
            .substringAfter("fun DynamicInlineCommentHeader(")
            .substringBefore("fun LazyListScope.dynamicInlineCommentItems(")
        assertFalse(inlineHeader.contains("fallbackBackdrop"))
        assertTrue(inlineHeader.contains("miuixBackdrop = miuixBackdrop"))
    }

    @Test
    fun `live player tabs receive a sibling content backdrop`() {
        val playerSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/live/LivePlayerScreen.kt"
        )

        assertTrue(playerSource.contains("miuixBackdrop = selectionBackdrop"))
        assertTrue(playerSource.contains(".layerBackdrop(selectionBackdrop)"))
        assertTrue(
            playerSource.indexOf(".layerBackdrop(selectionBackdrop)") <
                playerSource.indexOf("miuixBackdrop = selectionBackdrop")
        )
    }

    @Test
    fun `music page switcher samples the pager without recording itself`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/audio/screen/MusicPlayerContent.kt"
        )

        assertTrue(source.contains("val musicBackdrop = rememberMiuixLayerBackdrop()"))
        assertTrue(source.contains("miuixBackdrop = musicBackdrop"))
        assertTrue(source.contains(".miuixLayerBackdrop(musicBackdrop)"))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(File(path), File(normalizedPath)).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
