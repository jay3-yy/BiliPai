package com.android.purebilibili.feature.home.components

import java.io.File
import com.android.purebilibili.core.theme.UiPreset
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AppUiStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarLiquidSegmentedControlStructureTest {
    @Test
    fun `popular subcategories use content-sized native tabs`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt"
        )
        val popularControl = source.substringAfter("private fun PopularSubCategorySegmentedControl(")
            .substringBefore("private fun TodayWatchModeSegmentedControl(")

        assertTrue(popularControl.contains("contentSizedMiuixNonGlassItems = true"))
        assertTrue(popularControl.contains("contentAlignment = Alignment.Center"))
        assertTrue(popularControl.contains(".widthIn(max = 400.dp)"))
    }

    @Test
    fun `liquid segmented labels keep bottom bar foreground opacity`() {
        val onSurface = Color(0xFFF1F1F1)

        assertEquals(
            onSurface,
            resolveLiquidSegmentedControlUnselectedTextColor(
                onSurface = onSurface,
                enabled = true
            )
        )
        assertEquals(
            onSurface.copy(alpha = 0.42f),
            resolveLiquidSegmentedControlUnselectedTextColor(
                onSurface = onSurface,
                enabled = false
            )
        )
    }

    @Test
    fun `segmented labels reuse bottom bar glass content colors while moving`() {
        val unselected = Color(0xFF666666)
        val selected = Color(0xFFFF6699)

        val colors = resolveLiquidGlassSelectionContentColors(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 1f,
            glassEnabled = true,
            indicatorProgress = 0.8f,
            indicatorBackdropEnabled = true
        )

        assertEquals(unselected, colors.visibleColor)
        assertEquals(unselected, colors.exportColor)
    }

    @Test
    fun `two segment indicator reuses the full home dock slot`() {
        val width = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = 180f,
            indicatorHeightDp = 30f,
            itemCount = 2
        )

        assertEquals(180f, width)
    }

    @Test
    fun `multi segment indicator does not add inner edge insets`() {
        val width = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = 66f,
            indicatorHeightDp = 30f,
            itemCount = 4,
        )

        assertEquals(66f, width)
    }

    @Test
    fun `multi segment indicator reuses the full home dock slot`() {
        val width = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = 180f,
            indicatorHeightDp = 30f,
            itemCount = 3,
        )

        assertEquals(180f, width)
    }

    @Test
    fun `segmented capture expands past full drag scale lens and panel offset`() {
        assertEquals(
            72f,
            resolveBottomBarCaptureSafeInsetDp(
                indicatorWidthDp = 224f,
                refractionHeightDp = 24f,
                refractionAmountDp = 24f,
                panelOffsetDp = 4f
            ),
            0.001f
        )
        assertEquals(
            24f,
            resolveBottomBarCaptureSafeInsetDp(
                indicatorWidthDp = 0f,
                refractionHeightDp = 24f,
                refractionAmountDp = 24f,
                panelOffsetDp = 0f
            ),
            0.001f
        )
    }

    @Test
    fun `segmented indicator reduces height for cramped slots to stay capsule shaped`() {
        assertEquals(
            37.5f,
            resolveSegmentedControlIndicatorHeightDp(
                slotWidthDp = 60f,
                indicatorHeightDp = 56f,
            )
        )
    }

    @Test
    fun `segmented indicator keeps full height for already wide home slots`() {
        assertEquals(
            56f,
            resolveSegmentedControlIndicatorHeightDp(
                slotWidthDp = 128f,
                indicatorHeightDp = 56f,
            )
        )
    }

    @Test
    fun `segmented indicator offset follows slot position without clamping dead zone`() {
        assertEquals(
            4f,
            resolveSegmentedControlIndicatorOffsetDp(
                position = 0f,
                slotWidthDp = 60f,
                contentPaddingDp = 4f,
            )
        )
        assertEquals(
            34f,
            resolveSegmentedControlIndicatorOffsetDp(
                position = 0.5f,
                slotWidthDp = 60f,
                contentPaddingDp = 4f,
            )
        )
        assertEquals(
            244f,
            resolveSegmentedControlIndicatorOffsetDp(
                position = 4f,
                slotWidthDp = 60f,
                contentPaddingDp = 4f,
            )
        )
    }

    @Test
    fun `segmented control only follows continuous drag when touch starts on indicator`() {
        assertTrue(
            shouldFollowSegmentedControlIndicatorDrag(
                pointerX = 132f,
                indicatorPosition = 2f,
                itemWidthPx = 64f
            )
        )
        assertFalse(
            shouldFollowSegmentedControlIndicatorDrag(
                pointerX = 80f,
                indicatorPosition = 2f,
                itemWidthPx = 64f
            )
        )
        assertFalse(
            shouldFollowSegmentedControlIndicatorDrag(
                pointerX = 196.1f,
                indicatorPosition = 2f,
                itemWidthPx = 64f
            )
        )
    }

    @Test
    fun `segmented control sweep release resolves label without requiring indicator follow`() {
        assertEquals(
            0,
            resolveSegmentedControlSweepSelectionIndex(
                pointerX = -12f,
                itemWidthPx = 64f,
                itemCount = 4
            )
        )
        assertEquals(
            1,
            resolveSegmentedControlSweepSelectionIndex(
                pointerX = 82f,
                itemWidthPx = 64f,
                itemCount = 4
            )
        )
        assertEquals(
            3,
            resolveSegmentedControlSweepSelectionIndex(
                pointerX = 260f,
                itemWidthPx = 64f,
                itemCount = 4
            )
        )
    }

    @Test
    fun `segmented indicator can follow external realtime page position`() {
        assertEquals(
            1.35f,
            resolveSegmentedControlIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 1.35f,
                itemCount = 4
            )
        )
        assertEquals(
            0f,
            resolveSegmentedControlIndicatorPosition(
                internalPosition = 1f,
                externalPosition = -0.2f,
                itemCount = 4
            )
        )
        assertEquals(
            3f,
            resolveSegmentedControlIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 4.2f,
                itemCount = 4
            )
        )
    }

    @Test
    fun `native underline matches label width and remains centered in its segment`() {
        assertEquals(
            NativeUnderlineGeometry(offsetDp = 30f, widthDp = 40f),
            resolveNativeUnderlineGeometry(
                indicatorPosition = 0f,
                segmentWidthDp = 100f,
                labelWidthsDp = listOf(40f, 80f),
            )
        )
        assertEquals(
            NativeUnderlineGeometry(offsetDp = 110f, widthDp = 80f),
            resolveNativeUnderlineGeometry(
                indicatorPosition = 1f,
                segmentWidthDp = 100f,
                labelWidthsDp = listOf(40f, 80f),
            )
        )
    }

    @Test
    fun `native underline fits segments narrower than its preferred minimum width`() {
        assertEquals(
            NativeUnderlineGeometry(offsetDp = 0f, widthDp = 19f),
            resolveNativeUnderlineGeometry(
                indicatorPosition = 0f,
                segmentWidthDp = 19f,
                labelWidthsDp = listOf(16f, 16f),
            )
        )
    }

    @Test
    fun `native underline stretches between labels during page motion`() {
        val midpoint = resolveNativeUnderlineGeometry(
                indicatorPosition = 0.5f,
                segmentWidthDp = 100f,
                labelWidthsDp = listOf(40f, 80f),
            )
        assertEquals(53.431f, midpoint.offsetDp, 0.001f)
        assertEquals(101.421f, midpoint.widthDp, 0.001f)
    }

    @Test
    fun `segmented indicator only samples hidden tab backdrop while sliding without external backdrop`() {
        assertFalse(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = true,
                motionProgress = 0f,
                hasExternalBackdrop = false
            )
        )
        assertTrue(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = true,
                motionProgress = 0.01f,
                hasExternalBackdrop = false
            )
        )
        assertTrue(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = true,
                motionProgress = 0f,
                hasExternalBackdrop = true
            )
        )
        assertFalse(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = false,
                motionProgress = 1f,
                hasExternalBackdrop = true
            )
        )
    }

    @Test
    fun `export capture backdrop requires an external page layer`() {
        assertTrue(
            shouldDrawSegmentedControlExportCaptureBackdrop(
                liquidGlassEnabled = true,
                hasExternalBackdrop = true
            )
        )
        assertFalse(
            shouldDrawSegmentedControlExportCaptureBackdrop(
                liquidGlassEnabled = true,
                hasExternalBackdrop = false
            )
        )
        assertFalse(
            shouldDrawSegmentedControlExportCaptureBackdrop(
                liquidGlassEnabled = false,
                hasExternalBackdrop = true
            )
        )
    }

    @Test
    fun `global glass uses dock plus indicator on native chrome`() {
        assertEquals(
            SegmentedControlChromeStyle.LIQUID_PILL,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MATERIAL3,
                prefersNativeChrome = true,
                androidNativeLiquidGlassEnabled = true,
                preferInlineContentStyle = true
            )
        )
    }

    @Test
    fun `android native chrome segmented control uses liquid pill when global glass is enabled`() {
        assertEquals(
            SegmentedControlChromeStyle.LIQUID_PILL,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MIUIX,
                prefersNativeChrome = true,
                androidNativeLiquidGlassEnabled = true,
                preferInlineContentStyle = false
            )
        )
    }

    @Test
    fun `segmented liquid glass follows the master switch and effect gate`() {
        assertTrue(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = false,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = false,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 33,
            )
        )
        assertFalse(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = false,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 33,
            )
        )
        assertFalse(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = false,
                sdkInt = 33,
            )
        )
    }

    @Test
    fun `segmented liquid glass falls back before Android 13 so selected content stays visible`() {
        assertFalse(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 32,
            )
        )
        assertTrue(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 33,
            )
        )
    }

    @Test
    fun `global segmented control delegates liquid chrome to bottom bar matched implementation`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarLiquidSegmentedControl.kt"
        )
        val floating = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarFloatingSegmentedControl.kt"
        )
        val sharedChrome = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarMatchedLiquidChrome.kt"
        )

        assertTrue(source.contains("BottomBarMotionProfile.ANDROID_NATIVE_FLOATING"))
        assertFalse(source.contains("BottomBarMotionProfile.IOS_FLOATING"))
        assertTrue(source.contains("!homeSettings.androidNativeLiquidGlassEnabled"))
        assertTrue(source.contains("AppNativeTabRow("))
        assertTrue(source.contains("allowLabelOverflow = allowNativeLabelOverflow"))
        assertTrue(source.contains("drawMiuixNonGlassTrack = drawMiuixNonGlassTrack"))
        assertTrue(source.contains("indicatorPositionProvider = indicatorPositionProvider"))
        assertTrue(source.contains("BottomBarFloatingSegmentedControl("))
        assertTrue(floating.contains("FloatingBottomBar("))
        assertTrue(floating.contains("FloatingBottomBarItem("))
        assertTrue(floating.contains("resolveBiliPaiBottomBarShellColor("))
        assertTrue(floating.contains("shellHeight = effectiveHeight"))
        assertTrue(floating.contains("indicatorHeight = indicatorHeight"))
        assertFalse(floating.contains("FloatingBottomBarDefaultShellHeight"))
        assertFalse(floating.contains("modifier.wrapContentWidth()"))
        assertTrue(floating.contains("indicatorPositionProvider = indicatorPositionProvider"))
        assertFalse(floating.contains("DampedDragAnimation("))
        assertFalse(
            floating.contains(".drawBackdrop("),
            "Reuse chrome must not own a drawBackdrop recipe; use FloatingBottomBar"
        )
        assertFalse(floating.contains("vibrancy()"))
        assertEquals(
            0,
            Regex("""\blens\(""").findAll(floating).count(),
            "Segmented control must not call lens(); FloatingBottomBar owns the recipe"
        )
        assertFalse(floating.contains("biliPaiFloatingDockShell("))
        assertFalse(floating.contains("BiliPaiFloatingDockIndicator("))
        assertFalse(floating.contains("displayPosition"))
        assertTrue(
            floating.contains("BottomBarMatchedLiquidDock("),
            "Scrollable rails need one fixed-width liquid shell behind their moving content"
        )
        assertTrue(floating.contains("drawShell = scrollState == null"))
        assertFalse(floating.contains("horizontalDragGesture("))
        assertFalse(floating.contains("rememberBottomBarMatchedLiquidChromeState("))
        assertFalse(floating.contains("Invisible hit / drag layer"))
        assertTrue(source.contains("BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP = 58"))
        assertTrue(source.contains("liquidGlassEffectsEnabled: Boolean = true"))
        assertTrue(source.contains("dragSelectionEnabled: Boolean = true"))
        assertTrue(source.contains("longPressDragSelectionEnabled: Boolean = false"))
        assertTrue(source.contains("resolveSegmentedControlChromeStyle("))
        assertTrue(source.contains("uiStyle = LocalAppUiStyle.current"))
        assertTrue(source.contains("resolveHomeSelectionIndicatorStyle("))
        assertTrue(source.contains("AndroidNativeUnderlinedSegmentedControl("))
        assertTrue(source.contains("indicatorPositionProvider: (() -> Float)? = null"))
        assertTrue(source.contains("resolveNativeUnderlineGeometry("))
        assertTrue(source.contains("animateFloatAsState("))
        assertTrue(source.contains("onTextLayout = { result ->"))
        assertTrue(floating.contains("FloatingBottomBarMode.LiquidGlass"))
        assertTrue(floating.contains("LocalFloatingBottomBarContentColor.current"))
        assertTrue(sharedChrome.contains("holdPressUntilReleaseTargetSettles = true"))
        assertFalse(source.contains("BottomBarLiquidIndicatorSurface("))
    }

    @Test
    fun `dynamic top tabs reuse shared liquid chrome with pager and scroll state`() {
        val dynamicScreen = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/DynamicScreen.kt"
        )
        val dynamicTopBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/components/DynamicTopBar.kt"
        )

        assertTrue(dynamicTopBar.contains("AppThemeAdaptiveTabRow("))
        assertTrue(dynamicTopBar.contains("modifier = Modifier.weight(1f)"))
        assertFalse(dynamicTopBar.contains("resolveDynamicTopBarTabItemWidthDp"))
        assertFalse(dynamicTopBar.contains("fillMaxHeight()"))
        assertFalse(dynamicTopBar.contains("AppNativeTabRow("))
        assertFalse(dynamicTopBar.contains("forceLiquidChrome"))
        assertTrue(dynamicTopBar.contains("allowNativeLabelOverflow = true"))
        assertTrue(dynamicTopBar.contains("indicatorPositionProvider = indicatorPositionProvider"))
        assertTrue(dynamicTopBar.contains("isScrollInProgressProvider = isScrollInProgressProvider"))
        assertFalse(dynamicTopBar.contains("DynamicCompactTabRow("))
        assertTrue(dynamicScreen.contains("BottomBarMatchedDockVisibility("))
        assertTrue(dynamicScreen.contains("edge = BottomBarMatchedDockEdge.TOP"))
        assertTrue(dynamicScreen.contains("animateScale = false"))
        assertTrue(dynamicScreen.contains("activeListState?.isScrollInProgress == true"))
        assertTrue(dynamicScreen.contains("pagerState.isScrollInProgress"))
    }

    @Test
    fun `common list and video tabs pass page backdrop into segmented control`() {
        val commonList = loadSource("app/src/main/java/com/android/purebilibili/feature/list/CommonListScreen.kt")
        val iosSegmented = loadSource("app/src/main/java/com/android/purebilibili/feature/settings/AppSegmentedControl.kt")

        val videoContent = loadSource("app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt")
        val commentSortBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/CommentSortFilterBar.kt"
        )
        val commentSheetHost = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/VideoCommentSheetHost.kt"
        )

        assertTrue(commonList.contains("val commonListChromeBackdrop = rememberLayerBackdrop()"))
        assertTrue(commonList.contains(".layerBackdrop(commonListChromeBackdrop)"))
        assertTrue(commonList.contains("miuixBackdrop = commonListChromeBackdrop"))
        assertTrue(videoContent.contains("val videoContentMiuixBackdrop = rememberMiuixLayerBackdrop()"))
        assertTrue(videoContent.contains("chromeBackdrop = videoContentMiuixBackdrop"))
        assertTrue(videoContent.contains("miuixBackdrop = videoContentMiuixBackdrop"))
        assertTrue(videoContent.contains("Column(modifier = modifier.fillMaxSize())"))
        assertTrue(commentSortBar.contains("miuixBackdrop = miuixBackdrop"))
        assertTrue(commentSheetHost.contains("val commentChromeBackdrop = rememberLayerBackdrop()"))
        assertTrue(commentSheetHost.contains(".layerBackdrop(commentChromeBackdrop)"))
        assertFalse(iosSegmented.contains("backdrop = backdrop"))
    }

    @Test
    fun `segmented control does not attach drag gesture when drag selection is disabled`() {
        val floating = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarFloatingSegmentedControl.kt"
        )

        assertTrue(
            floating.contains("dragSelectionEnabled = enabled && itemCount > 1"),
            "Scrollable contribution tabs disable drag selection, so the liquid indicator must not attach a competing drag gesture"
        )
        assertTrue(
            floating.contains("longPressDragSelectionEnabled && enabled && itemCount > 1"),
            "Scrollable segmented controls may opt into indicator dragging only after a long press"
        )
    }

    @Test
    fun `external pager motion opt in reaches floating dock renderer`() {
        val liquid = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarLiquidSegmentedControl.kt"
        )
        val floatingSegmented = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarFloatingSegmentedControl.kt"
        )
        val floatingDock = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/FloatingBottomBar.kt"
        )

        assertTrue(liquid.contains("externalPagerMotionEffectsEnabled = externalPagerMotionEffectsEnabled"))
        assertTrue(floatingSegmented.contains("externalPagerMotionEffectsEnabled = externalPagerMotionEffectsEnabled"))
        assertTrue(floatingDock.contains("visualIndicatorPositionProvider()"))
    }

    @Test
    fun `global video dynamic and live segmented surfaces share android native fallback`() {
        val paths = listOf(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/CommentSortFilterBar.kt",
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt",
            "app/src/main/java/com/android/purebilibili/feature/live/LivePlayerScreen.kt",
            "app/src/main/java/com/android/purebilibili/feature/dynamic/components/DynamicCommentSheet.kt",
            "app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt",
            "app/src/main/java/com/android/purebilibili/feature/plugin/TodayWatchPlugin.kt",
            "app/src/main/java/com/android/purebilibili/feature/bangumi/BangumiReviewScreen.kt",
        )

        paths.forEach { path ->
            assertTrue(
                (loadSource(path).contains("BottomBarLiquidSegmentedControl(") ||
                    loadSource(path).contains("AppThemeAdaptiveTabRow(")),
                "$path should keep using BottomBarLiquidSegmentedControl so the global Android native fallback applies"
            )
        }

        // 上游合流后直播首页分区行/全部分区行改用 LiveHomeSelectableChip（按 preset 原生分发），
        // 不再走 BottomBarLiquidSegmentedControl；原生 fallback 约束只对仍在用共享控件的面成立。
        val liveList = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/live/LiveListScreen.kt"
        )
        val liveArea = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/live/LiveAreaScreen.kt"
        )
        assertTrue(liveList.contains("LiveHomeSelectableChip("))
        assertTrue(liveArea.contains("AppThemeAdaptiveTabRow("))
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
