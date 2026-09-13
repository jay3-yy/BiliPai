package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentSortFilterBarPolicyTest {

    @Test
    fun `comment sort exposes only hot and newest without up filter`() {
        assertEquals(
            listOf(CommentSortMode.HOT, CommentSortMode.NEWEST),
            CommentSortMode.entries
        )

        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/CommentSortFilterBar.kt"
        )
        assertTrue(source.contains("listOf(CommentSortMode.HOT, CommentSortMode.NEWEST)"))
        assertFalse(source.contains("CommentToggleButton"))
        assertFalse(source.contains("onUpOnlyToggle"))
    }

    @Test
    fun `sort segmented control leaves room for bottom bar matched indicator scale`() {
        val spec = resolveCommentSortSegmentedControlSpec(itemCount = 2)

        assertEquals(66, spec.itemWidthDp)
        assertEquals(40, spec.heightDp)
        assertEquals(30, spec.indicatorHeightDp)
        assertTrue(
            hasCommentSortIndicatorScaleClearance(
                containerHeightDp = spec.heightDp,
                indicatorHeightDp = spec.indicatorHeightDp
            )
        )
        assertEquals(
            8,
            resolveCommentSortDockViewportOverflowDp(
                containerHeightDp = spec.heightDp,
                indicatorHeightDp = spec.indicatorHeightDp,
            )
        )
    }

    @Test
    fun `sort segmented control keeps bottom-bar tap press refraction`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/CommentSortFilterBar.kt"
        )

        assertTrue(source.contains("tapPressRefractionEnabled = true"))
        assertTrue(source.contains("itemWidth = spec.itemWidthDp.dp"))
        assertTrue(source.contains("height = spec.heightDp.dp"))
        assertTrue(source.contains("forceEqualWidth = true"))
    }

    @Test
    fun `standalone comment header keeps dock fixed without press refraction`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/CommentSortFilterBar.kt"
        )
        val headerSource = source
            .substringAfter("fun CommentSortHeader(")
            .substringBefore("fun CommentSortFilterBar(")
        val dockHostSource = headerSource
            .substringAfter("if (uiStyle == AppUiStyle.MIUIX) {")
            .substringBefore("AppThemeAdaptiveTabRow(")

        assertTrue(headerSource.contains("tapPressRefractionEnabled = false"))
        assertFalse(headerSource.contains(".offset("))
        assertFalse(headerSource.contains("bottomClearanceDp"))
        assertTrue(
            dockHostSource.contains(
                "Modifier.width((spec.itemWidthDp * sortModes.size).dp)"
            )
        )
        assertFalse(dockHostSource.contains("height = spec.heightDp.dp"))
    }

    @Test
    fun `sort segmented control accepts a stable page sized miuix sibling capture`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/CommentSortFilterBar.kt"
        )

        assertTrue(source.contains("miuixBackdrop = miuixBackdrop"))
        assertFalse(source.contains("forceLiquidChrome"))
        assertTrue(source.contains("liquidGlassEffectsEnabled = liquidGlassEffectsEnabled"))
        assertTrue(source.contains("MiuixBackdrop"))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
