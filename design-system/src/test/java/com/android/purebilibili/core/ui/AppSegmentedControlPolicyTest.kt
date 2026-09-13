package com.android.purebilibili.core.ui

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.components.shouldUseCompactMiuixTabRow
import com.android.purebilibili.core.ui.components.resolveReadableNativeTabMinWidth
import com.android.purebilibili.core.ui.components.resolveCompactMiuixTabRowWidth
import com.android.purebilibili.core.ui.components.resolveAppMiuixTabContentColor
import com.android.purebilibili.core.ui.components.resolveAppMiuixTabTrackColor
import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSegmentedControlPolicyTest {

    @Test
    fun `non glass Miuix removes outer dock while keeping readable labels`() {
        val track = Color(0xFF303030)
        val inactive = Color(0xFF8A8A8A)
        val readable = Color(0xFFF2F2F2)

        assertEquals(Color.Transparent, resolveAppMiuixTabTrackColor(true, track))
        assertEquals(track, resolveAppMiuixTabTrackColor(false, track))
        assertEquals(readable, resolveAppMiuixTabContentColor(true, inactive, readable))
        assertEquals(inactive, resolveAppMiuixTabContentColor(false, inactive, readable))

        val source = loadSource(
            "src/main/java/com/android/purebilibili/core/ui/renderer/miuix/" +
                "AppMiuixSegmentedControl.kt"
        )
        assertTrue(source.contains("nonGlassMiuix -> tabColors.backgroundColor"))
        assertTrue(source.contains("AppMiuixNonGlassTabItem("))
        assertTrue(source.contains("Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)"))
    }

    @Test
    fun `non glass tabs keep native geometry and grow for accessible text`() {
        assertEquals(RoundedControlVisualGeometry(42.dp, 12.dp),
            resolveMiuixNonGlassControlGeometry(false, 22.dp))
        assertEquals(RoundedControlVisualGeometry(36.dp, 10.dp),
            resolveMiuixNonGlassControlGeometry(true, 20.dp))
        assertEquals(RoundedControlVisualGeometry(64.dp, 12.dp),
            resolveMiuixNonGlassControlGeometry(false, 48.dp))
    }


    @Test
    fun `compact two option rows do not consume viewport`() {
        assertEquals(144.dp, resolveCompactMiuixTabRowWidth(400.dp, 72.dp, 2, false))
        assertEquals(400.dp, resolveCompactMiuixTabRowWidth(400.dp, 72.dp, 2, true))
        assertEquals(400.dp, resolveCompactMiuixTabRowWidth(400.dp, 72.dp, 3, false))
    }

    @Test
    fun `native tabs expand shared item width across themes for complete long labels`() {
        assertEquals(
            56.dp,
            resolveReadableNativeTabMinWidth(
                requestedMinWidth = AppChromeSizeTokens.MinimumTouchTarget,
                labels = listOf("简介", "评论"),
                allowLabelOverflow = true,
            ),
        )
        assertEquals(
            88.dp,
            resolveReadableNativeTabMinWidth(
                requestedMinWidth = 72.dp,
                labels = listOf("播放多", "默认排序", "新发布"),
                allowLabelOverflow = true,
            ),
        )
        assertEquals(
            72.dp,
            resolveReadableNativeTabMinWidth(
                requestedMinWidth = 72.dp,
                labels = listOf("视频", "番剧"),
                allowLabelOverflow = true,
            ),
        )
        assertEquals(
            72.dp,
            resolveReadableNativeTabMinWidth(
                requestedMinWidth = 72.dp,
                labels = listOf("默认排序"),
                allowLabelOverflow = false,
            ),
        )
    }

    @Test
    fun `only non scrollable two option Miuix rows use compact width`() {
        assertTrue(shouldUseCompactMiuixTabRow(2, scrollable = false, compactWhenTwoOptions = true))
        assertFalse(shouldUseCompactMiuixTabRow(2, scrollable = true, compactWhenTwoOptions = true))
        assertFalse(shouldUseCompactMiuixTabRow(3, scrollable = false, compactWhenTwoOptions = true))
        assertFalse(shouldUseCompactMiuixTabRow(2, scrollable = false, compactWhenTwoOptions = false))
    }

    @Test
    fun `material3 exposes material segmented capabilities`() {
        val policy = resolveAppSegmentedControlPolicy(AppUiStyle.MATERIAL3)

        assertTrue(policy.usesEmphasizedTitle)
        assertTrue(policy.usesMaterialFallback)
        assertTrue(policy.usesMaterialColorTokens)
        assertFalse(policy.usesNativeTabRow)
    }

    @Test
    fun `miuix exposes native tab row capability`() {
        val policy = resolveAppSegmentedControlPolicy(AppUiStyle.MIUIX)

        assertTrue(policy.usesEmphasizedTitle)
        assertTrue(policy.usesMaterialFallback)
        assertTrue(policy.usesNativeTabRow)
        assertFalse(policy.usesMaterialColorTokens)
    }

    @Test
    fun `segmented policy exposes semantic corners without a shared visual height`() {
        val material = resolveAppSegmentedControlPolicy(AppUiStyle.MATERIAL3)
        val miuix = resolveAppSegmentedControlPolicy(AppUiStyle.MIUIX)
        assertEquals(10.8.dp, material.preferredCornerRadius)
        assertEquals(13.8.dp, miuix.preferredCornerRadius)
    }

    @Test
    fun `oversized semantic corner is clamped instead of enlarging the control`() {
        val geometry = resolveRoundedControlVisualGeometry(
            preferredCornerRadius = 14.4.dp,
            nativeMinimumHeight = 40.dp,
        )

        assertEquals(40.dp, geometry.height)
        assertEquals(12.dp, geometry.cornerRadius)
        assertTrue(geometry.cornerRadius < geometry.height / 2)
    }

    @Test
    fun `native minimum wins when semantic corner already fits`() {
        val geometry = resolveRoundedControlVisualGeometry(
            preferredCornerRadius = 12.dp,
            nativeMinimumHeight = 42.dp,
        )

        assertEquals(42.dp, geometry.height)
        assertEquals(12.dp, geometry.cornerRadius)
    }

    @Test
    fun `native Miuix tabs keep compact visuals inside a 48dp touch target`() {
        val materialSource = loadSource(
            "src/main/java/com/android/purebilibili/core/ui/renderer/material3/" +
                "AppMaterial3SegmentedControl.kt"
        )
        val miuixSource = loadSource(
            "src/main/java/com/android/purebilibili/core/ui/renderer/miuix/" +
                "AppMiuixSegmentedControl.kt"
        )

        assertFalse(materialSource.contains("heightIn(min = 48.dp)"))
        assertTrue(miuixSource.contains("resolveRoundedControlVisualGeometry("))
        assertTrue(miuixSource.contains("AppMiuixNonGlassTabItem("))
        assertTrue(miuixSource.contains(".height(visualHeight)"))
        assertTrue(miuixSource.contains(".heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)"))
    }

    @Test
    fun `md3 tab row overflows material text padding instead of ellipsizing compact labels`() {
        val materialSource = loadSource(
            "src/main/java/com/android/purebilibili/core/ui/renderer/material3/" +
                "AppMaterial3SegmentedControl.kt"
        )
        val tabRow = materialSource.substringAfter("internal fun <T> AppMaterial3TabRow(")

        assertTrue(tabRow.contains("text = {"))
        assertTrue(tabRow.contains("resolveAppSegmentedLabelFontSizeSp("))
        assertTrue(tabRow.contains("allowLabelOverflow"))
        assertTrue(tabRow.contains("wrapContentWidth("))
        assertTrue(tabRow.contains("unbounded = true"))
        assertTrue(tabRow.contains("softWrap = false"))
        assertTrue(tabRow.contains("TextOverflow.Visible"))
        assertFalse(tabRow.contains("TextOverflow.Ellipsis"))
        assertTrue(tabRow.contains("indicatorPositionProvider: (() -> Float)? = null"))
        assertTrue(tabRow.contains("indicatorPositionProvider = indicatorPositionProvider"))
    }

    @Test
    fun `miuix tab viewport clips scrolling content and background to its corners`() {
        val source = loadSource(
            "src/main/java/com/android/purebilibili/core/ui/renderer/miuix/" +
                "AppMiuixSegmentedControl.kt"
        )
        val tabRow = source.substringAfter("internal fun <T> AppMiuixTabRow(")

        assertTrue(source.contains("import top.yukonga.miuix.kmp.squircle.squircleClip"))
        assertTrue(tabRow.contains("modifier = modifier.squircleClip(geometry.cornerRadius)"))
        assertTrue(tabRow.contains("cornerRadius = geometry.cornerRadius"))
        assertTrue(tabRow.contains("listState = if (scrollable) scrollState else null"))
    }

    private fun loadSource(path: String): String = listOf(
        File(path),
        File("design-system/$path"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("Cannot locate $path from ${File(".").absolutePath}")
}
