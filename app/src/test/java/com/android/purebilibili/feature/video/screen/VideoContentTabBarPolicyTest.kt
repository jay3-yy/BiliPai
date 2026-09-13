package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.AppTopTabPresentation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoContentTabBarPolicyTest {

    @Test
    fun `disabled automatic hiding lets the whole top row follow list content`() {
        assertEquals(
            0f,
            resolveVideoContentTabBarFollowScrollPx(
                scrollHideEnabled = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                maxFollowPx = 48f,
            ),
        )
        assertEquals(
            24f,
            resolveVideoContentTabBarFollowScrollPx(
                scrollHideEnabled = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 24,
                maxFollowPx = 48f,
            ),
        )
        assertEquals(
            48f,
            resolveVideoContentTabBarFollowScrollPx(
                scrollHideEnabled = false,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                maxFollowPx = 48f,
            ),
        )
        assertEquals(
            0f,
            resolveVideoContentTabBarFollowScrollPx(
                scrollHideEnabled = true,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                maxFollowPx = 48f,
            ),
        )
    }

    @Test
    fun `comment list at top only when first item and zero offset`() {
        assertTrue(isVideoContentCommentListAtTop(0, 0))
        assertFalse(isVideoContentCommentListAtTop(0, 1))
        assertFalse(isVideoContentCommentListAtTop(1, 0))
    }

    @Test
    fun `collapse only consumes scroll on a settled comment page`() {
        assertTrue(
            shouldEnableVideoContentTabBarCollapse(
                settingEnabled = true,
                selectedTabIndex = 1,
                isPagerScrollInProgress = false,
            ),
        )
        assertFalse(
            shouldEnableVideoContentTabBarCollapse(
                settingEnabled = true,
                selectedTabIndex = 0,
                isPagerScrollInProgress = false,
            ),
        )
        assertFalse(
            shouldEnableVideoContentTabBarCollapse(
                settingEnabled = true,
                selectedTabIndex = 1,
                isPagerScrollInProgress = true,
            ),
        )
    }

    @Test
    fun `collapse progress follows nested collapse px and snaps full when list leaves top`() {
        assertEquals(
            0f,
            resolveVideoContentTabBarCollapseProgress(
                collapsePx = 20f,
                maxCollapsePx = 80f,
                selectedTabIndex = 0,
                listAtTop = true,
            ),
        )
        assertEquals(
            0.25f,
            resolveVideoContentTabBarCollapseProgress(
                collapsePx = 20f,
                maxCollapsePx = 80f,
                selectedTabIndex = 1,
                listAtTop = true,
            ),
        )
        assertEquals(
            1f,
            resolveVideoContentTabBarCollapseProgress(
                collapsePx = 10f,
                maxCollapsePx = 80f,
                selectedTabIndex = 1,
                listAtTop = false,
            ),
        )
    }

    @Test
    fun `preScroll collapses with downward content scroll and can reverse expand at top`() {
        val collapse = reduceVideoContentTabBarCollapseOnPreScroll(
            collapsePx = 10f,
            maxCollapsePx = 80f,
            availableY = -30f,
            listAtTop = true,
            enabled = true,
        )
        assertEquals(40f, collapse!!.nextCollapsePx)
        assertEquals(-30f, collapse.consumedY)

        val expand = reduceVideoContentTabBarCollapseOnPreScroll(
            collapsePx = 40f,
            maxCollapsePx = 80f,
            availableY = 25f,
            listAtTop = true,
            enabled = true,
        )
        assertEquals(15f, expand!!.nextCollapsePx)
        assertEquals(25f, expand.consumedY)
    }

    @Test
    fun `preScroll expand is ignored when list not at top so browsing can keep chrome hidden`() {
        assertNull(
            reduceVideoContentTabBarCollapseOnPreScroll(
                collapsePx = 80f,
                maxCollapsePx = 80f,
                availableY = 40f,
                listAtTop = false,
                enabled = true,
            ),
        )
    }

    @Test
    fun `preScroll is interruptible mid collapse`() {
        val mid = reduceVideoContentTabBarCollapseOnPreScroll(
            collapsePx = 0f,
            maxCollapsePx = 100f,
            availableY = -40f,
            listAtTop = true,
            enabled = true,
        )!!
        assertEquals(40f, mid.nextCollapsePx)

        val reverse = reduceVideoContentTabBarCollapseOnPreScroll(
            collapsePx = mid.nextCollapsePx,
            maxCollapsePx = 100f,
            availableY = 15f,
            listAtTop = true,
            enabled = true,
        )!!
        assertEquals(25f, reverse.nextCollapsePx)
        assertEquals(15f, reverse.consumedY)
    }

    @Test
    fun `postScroll expands remainder after list hits top during fling`() {
        val update = reduceVideoContentTabBarCollapseOnPostScroll(
            collapsePx = 50f,
            maxCollapsePx = 80f,
            availableY = 30f,
            listAtTop = true,
            enabled = true,
        )
        assertEquals(20f, update!!.nextCollapsePx)
        assertEquals(30f, update.consumedY)
    }

    @Test
    fun `leaving list top forces full collapse while intro tab resets`() {
        assertEquals(
            80f,
            resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
                collapsePx = 12f,
                maxCollapsePx = 80f,
                listAtTop = false,
                enabled = true,
            ),
        )
        assertEquals(
            0f,
            resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
                collapsePx = 40f,
                maxCollapsePx = 80f,
                listAtTop = true,
                enabled = false,
            ),
        )
        assertEquals(
            40f,
            resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
                collapsePx = 40f,
                maxCollapsePx = 80f,
                listAtTop = true,
                enabled = true,
            ),
        )
    }

    @Test
    fun `tab bar layout stays compact beside danmaku actions`() {
        val spec = resolveVideoContentTabBarLayoutSpec(widthDp = 412)

        assertEquals(1f, spec.tabsRowWeight)
        assertTrue(spec.tabsRowScrollable)
        assertEquals(12, spec.containerHorizontalPaddingDp)
        assertEquals(12, spec.tabHorizontalPaddingDp)
        assertEquals(40, spec.segmentedControlHeightDp)
        assertEquals(30, spec.segmentedControlIndicatorHeightDp)
        assertTrue(
            hasVideoContentTabBarIndicatorScaleClearance(
                containerHeightDp = spec.segmentedControlHeightDp,
                indicatorHeightDp = spec.segmentedControlIndicatorHeightDp
            )
        )
    }

    @Test
    fun `danmaku input stays visible when player is expanded`() {
        assertTrue(
            shouldShowDanmakuSendInput(
                isPlayerCollapsed = false
            )
        )
    }

    @Test
    fun `danmaku send and toggle stay on intro and comments tabs`() {
        assertTrue(shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex = 0))
        assertTrue(shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex = 1))
    }

    @Test
    fun `danmaku input hidden when player is collapsed`() {
        assertFalse(
            shouldShowDanmakuSendInput(
                isPlayerCollapsed = true
            )
        )
    }

    @Test
    fun `danmaku action layout keeps send controls comfortably tappable`() {
        val policy = resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = 412)

        assertEquals("发弹幕", policy.sendLabel)
        assertEquals(12, policy.sendTextSizeSp)
        assertEquals(48, policy.sendMinHeightDp)
        assertEquals(22, policy.toggleIconSizeDp)
        assertEquals(48, policy.toggleButtonSizeDp)
    }

    @Test
    fun `compact phone layout tightens tabs and danmaku actions`() {
        val spec = resolveVideoContentTabBarLayoutSpec(widthDp = 393)
        val policy = resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = 393)

        assertEquals(8, spec.containerHorizontalPaddingDp)
        assertEquals(8, spec.tabHorizontalPaddingDp)
        assertEquals(10, spec.tabSpacingDp)
        assertEquals(16, spec.selectedTabFontSizeSp)
        assertEquals(40, spec.segmentedControlHeightDp)
        assertTrue(
            hasVideoContentTabBarIndicatorScaleClearance(
                containerHeightDp = spec.segmentedControlHeightDp,
                indicatorHeightDp = spec.segmentedControlIndicatorHeightDp
            )
        )
        assertEquals("发弹幕", policy.sendLabel)
        assertEquals(12, policy.sendTextSizeSp)
        assertEquals(48, policy.sendMinHeightDp)
        assertEquals(22, policy.toggleIconSizeDp)
        assertEquals(48, policy.toggleButtonSizeDp)
        assertEquals(4, policy.toggleTrailingPaddingDp)
    }

    @Test
    fun `danmaku action controls share native PiliPlus sizes`() {
        val compact = resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = 393)
        val regular = resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = 412)

        listOf(compact, regular).forEach { policy ->
            assertEquals(22, policy.toggleIconSizeDp)
            assertEquals(48, policy.toggleButtonSizeDp)
            assertEquals(48, policy.sendMinHeightDp)
            assertEquals(12, policy.sendTextSizeSp)
            assertEquals("发弹幕", policy.sendLabel)
        }
        assertEquals(4, compact.toggleTrailingPaddingDp)
        assertEquals(6, regular.toggleTrailingPaddingDp)
    }

    @Test
    fun `danmaku actions are right aligned and settings leave the tab bar`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )
        val tabBarBlock = source
            .substringAfter("private fun VideoContentTabBar(")
            .substringBefore("private fun VideoRecommendationHeader")

        assertTrue(tabBarBlock.contains("Spacer(modifier = Modifier.weight(1f))"))
        assertFalse(tabBarBlock.contains("onDanmakuSettingsClick"))
        assertFalse(tabBarBlock.contains("contentDescription = \"弹幕设置\""))
        assertTrue(tabBarBlock.contains("NativeDanmakuToggleButton("))
        assertTrue(tabBarBlock.contains("text = danmakuActionLayoutPolicy.sendLabel"))
        assertTrue(tabBarBlock.contains("tapToCopyEnabled = false"))
        assertTrue(tabBarBlock.contains(".clickable(onClick = onDanmakuSendClick)"))
        assertFalse(tabBarBlock.contains("RoundedCornerShape("))
        assertFalse(tabBarBlock.contains("AppHorizontalDivider("))
        assertFalse(tabBarBlock.contains("\"开\""))
        assertFalse(tabBarBlock.contains("\"关\""))
        assertFalse(tabBarBlock.contains("rememberAppCommentIcon()"))
    }

    @Test
    fun `info comment tab bar uses theme adaptive chrome during pager motion`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )
        val tabBarBlock = source
            .substringAfter("fun VideoContentTabBar(")
            .substringBefore("// [新增] 恢复画面按钮")

        assertTrue(tabBarBlock.contains("tapPressRefractionEnabled = true"))
        assertTrue(tabBarBlock.contains("AppThemeAdaptiveTabRow("))
        assertFalse(tabBarBlock.contains("AppPrimaryTabRow("))
        assertTrue(tabBarBlock.contains("miuixBackdrop = miuixBackdrop"))
        assertTrue(tabBarBlock.contains("Arrangement.spacedBy(8.dp)"))
        assertTrue(tabBarBlock.contains("indicatorPositionProvider = indicatorPositionProvider"))
        assertTrue(tabBarBlock.contains("dragSelectionEnabled = true"))
        assertFalse(tabBarBlock.contains("tabSwipeModifier"))
        assertTrue(tabBarBlock.contains("modifier = Modifier.weight(1f)"))
        assertTrue(tabBarBlock.contains("compactMiuixWhenTwoOptions = false"))
        assertTrue(source.contains("clip = tabBarCollapseProgress > 0.001f"))
    }

    @Test
    fun `video content section records a full size sibling backdrop for segmented controls`() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )

        assertTrue(source.contains("val videoContentMiuixBackdrop = rememberMiuixLayerBackdrop()"))
        assertFalse(source.contains(".alpha(0f)"))
        assertTrue(source.contains("sortMode = sortMode"))
        assertTrue(source.contains("onSortModeChange = onSortModeChange"))
        assertTrue(source.contains("miuixBackdrop = videoContentMiuixBackdrop"))
        assertTrue(source.contains(".miuixLayerBackdrop(videoContentMiuixBackdrop)"))
        assertTrue(source.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertTrue(source.contains("top = tabBarVisibleHeightDp"))
        assertFalse(source.contains("Modifier.miuixLayerBackdrop(chromeBackdrop)"))
        assertFalse(source.contains("chromeBackdrop: LayerBackdrop?"))
        assertTrue(source.contains("Column(modifier = modifier.fillMaxSize())"))
        assertTrue(
            source.contains(
                "one full-size content source, with liquid docks rendered as"
            )
        )
        val commentTabSource = source.substringAfter("internal fun VideoCommentTab(")
            .substringBefore("internal fun LandscapeCommentPanel(")
        assertTrue(commentTabSource.contains("CommentSortHeader("))
        assertFalse(commentTabSource.contains("CommentSortFilterBar("))
        assertTrue(source.contains("shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex)"))
        assertFalse(source.contains("if (selectedTabIndex != 1)"))
        assertTrue(source.contains("AppThemeAdaptiveTabRow("))
        assertFalse(source.contains("AppPrimaryTabRow("))
        assertTrue(source.contains("indicatorPositionProvider = indicatorPositionProvider"))
        assertTrue(source.contains("showNativeSortHeader = !liquidGlassEnabled"))
        assertTrue(source.contains("showSortControlInHeader = true"))
        assertTrue(source.contains("pagerState.currentPage == 1 &&"))
        assertTrue(source.contains("visible = commentListAtTop"))
        assertTrue(source.contains(".biliPaiProgressiveTopBlur("))
        assertTrue(source.contains("liquidGlassEffectsEnabled = liquidGlassEnabled"))
        assertTrue(source.contains("showHeader = !immersiveVideoContentChromeEnabled"))
        assertTrue(source.contains("floatingHeaderContentPadding = if (immersiveVideoContentChromeEnabled) 46.dp else 0.dp"))
        val pinnedCommentHeader = source.substringAfter("pagerState.currentPage == 1 &&")
            .substringBefore("// Inline 弹幕设置")
        assertFalse(pinnedCommentHeader.contains(".biliPaiProgressiveTopBlur("))
        assertTrue(pinnedCommentHeader.contains("CommentListHeader("))
        val primaryTabChrome = source.substringAfter("contentAlignment = Alignment.TopStart,")
            .substringBefore("pagerState.currentPage == 1 &&")
        assertTrue(primaryTabChrome.contains(".biliPaiProgressiveTopBlur("))
        assertTrue(pinnedCommentHeader.contains("visible = commentListAtTop"))
        assertTrue(pinnedCommentHeader.contains("CommentSortFilterBar("))
        assertTrue(source.contains("backdrop = videoContentMiuixBackdrop"))
        assertTrue(source.contains("showCommentBackToTop"))
        val pagerBlock = source.substringAfter("HorizontalPager(").substringBefore(") { page ->")
        assertTrue(pagerBlock.contains("if (immersiveVideoContentChromeEnabled)"))
        assertTrue(pagerBlock.contains("Modifier.padding(top = tabBarVisibleHeightDp)"))
        assertTrue(source.contains("top = if (immersiveVideoContentChromeEnabled) tabBarVisibleHeightDp else 0.dp"))
        assertFalse(
            pagerBlock.contains("layerBackdrop"),
            "Pager must not capture backdrop; segmented controls inside would self-sample and overflow RenderThread stack on MIUI"
        )
        assertTrue(source.contains("hasBackdrop = miuixBackdrop != null"))
        assertFalse(source.contains("forceLiquidChrome"))
        assertTrue(source.contains("labelFontSize = liquidChromeSpec.labelFontSizeSp.sp"))
    }

    @Test
    fun `moving capsule uses calmer intro comment tab switch motion`() {
        val iosSpec = resolveVideoContentTabSwitchAnimationSpec(AppTopTabPresentation.MOVING_CAPSULE)
        val md3Spec = resolveVideoContentTabSwitchAnimationSpec(AppTopTabPresentation.MATERIAL_UNDERLINE)

        assertEquals(360, iosSpec.durationMs)
        assertEquals(240, md3Spec.durationMs)
        assertTrue(iosSpec.durationMs > md3Spec.durationMs)
        assertEquals(iosSpec.durationMs, resolveInlinePortraitPlayerCommentCollapseDurationMillis(iosSpec))
        assertEquals(md3Spec.durationMs, resolveInlinePortraitPlayerCommentCollapseDurationMillis(md3Spec))
    }

    @Test
    fun `effective selected tab follows target while pager is switching`() {
        assertEquals(
            1,
            resolveVideoContentEffectiveSelectedTabIndex(
                currentPage = 0,
                targetPage = 1,
                isScrollInProgress = true,
                pageCount = 2
            )
        )
    }

    @Test
    fun `effective selected tab uses current page when pager is idle`() {
        assertEquals(
            0,
            resolveVideoContentEffectiveSelectedTabIndex(
                currentPage = 0,
                targetPage = 1,
                isScrollInProgress = false,
                pageCount = 2
            )
        )
    }

    @Test
    fun `effective selected tab falls back to current page for invalid target`() {
        assertEquals(
            0,
            resolveVideoContentEffectiveSelectedTabIndex(
                currentPage = 0,
                targetPage = 3,
                isScrollInProgress = true,
                pageCount = 2
            )
        )
    }

    @Test
    fun `horizontal page drags stay enabled on both intro and comment tabs`() {
        assertTrue(
            shouldEnableVideoContentHorizontalPagerSwipe(
                currentPage = 1,
                commentPageIndex = 1,
                isPagerScrollInProgress = false,
            )
        )
        assertTrue(
            shouldEnableVideoContentHorizontalPagerSwipe(
                currentPage = 1,
                commentPageIndex = 1,
                isPagerScrollInProgress = true,
            )
        )
        assertTrue(
            shouldEnableVideoContentHorizontalPagerSwipe(
                currentPage = 0,
                commentPageIndex = 1,
                isPagerScrollInProgress = false,
            )
        )
    }

    @Test
    fun `intro action row exposes a labeled comment entry and opens comment tab`() {
        val contentSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )
        val actionSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/section/VideoActionSection.kt"
        )

        assertTrue(contentSource.contains("onCommentClick = { onTabSelected(1) }"))
        assertTrue(actionSource.contains("text = \"评论 \${FormatUtils.formatStat(info.stat.reply.toLong())}\""))
        assertTrue(actionSource.contains("onClick = onCommentClick"))
    }

    @Test
    fun `video content tab bar reuses dock at scene size when global glass and backdrop are on`() {
        val layoutSpec = resolveVideoContentTabBarLayoutSpec(widthDp = 393)
        val spec = resolveVideoContentTabBarLiquidChromeSpec(
            androidNativeLiquidGlassEnabled = true,
            hasBackdrop = true,
            layoutSpec = layoutSpec,
        )

        assertTrue(spec.reusesLiquidGlassDock)
        assertTrue(spec.liquidGlassEffectsEnabled)
        assertEquals(layoutSpec.segmentedControlHeightDp, spec.segmentedControlHeightDp)
        assertEquals(layoutSpec.segmentedControlIndicatorHeightDp, spec.segmentedControlIndicatorHeightDp)
        assertEquals(68, spec.itemWidthDp)
        assertEquals(70, resolveVideoContentTabBarDockItemWidthDp(labelFontSizeSp = 15))
        assertEquals(72, resolveVideoContentTabBarDockItemWidthDp(labelFontSizeSp = 16))
        assertEquals(66, resolveVideoContentTabBarDockItemWidthDp(labelFontSizeSp = 13))
        assertEquals(
            136,
            resolveVideoContentTabBarControlWidthDp(
                itemWidthDp = spec.itemWidthDp ?: 0,
                itemCount = 2,
            ),
        )
        assertEquals(
            0,
            resolveVideoContentTabBarStartPaddingDp(
                reusesLiquidGlassDock = true,
                containerHorizontalPaddingDp = layoutSpec.containerHorizontalPaddingDp,
            )
        )
        assertEquals(
            layoutSpec.containerHorizontalPaddingDp,
            resolveVideoContentTabBarStartPaddingDp(
                reusesLiquidGlassDock = false,
                containerHorizontalPaddingDp = layoutSpec.containerHorizontalPaddingDp,
            )
        )
        assertFalse(
            shouldReuseVideoContentTabBarLiquidGlassDock(
                androidNativeLiquidGlassEnabled = true,
                hasBackdrop = false,
            )
        )
        val nativeSpec = resolveVideoContentTabBarLiquidChromeSpec(
            androidNativeLiquidGlassEnabled = false,
            hasBackdrop = true,
            layoutSpec = layoutSpec,
        )
        assertFalse(nativeSpec.reusesLiquidGlassDock)
        assertFalse(nativeSpec.liquidGlassEffectsEnabled)
        assertEquals(
            spec.itemWidthDp,
            resolveVideoContentTabBarDockItemWidthDp(nativeSpec.labelFontSizeSp),
        )

        val contentSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )
        assertFalse(
            contentSource.contains("minTabWidth = liquidChromeSpec.itemWidthDp?.dp"),
            "The video tab row must inherit beta.21 liquid geometry from the shared renderer",
        )
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
