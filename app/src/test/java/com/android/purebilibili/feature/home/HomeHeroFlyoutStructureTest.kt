package com.android.purebilibili.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeHeroFlyoutStructureTest {

    @Test
    fun homeScreenNavigatesImmediatelyAndDoesNotRunSourceFlyout() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val clickWrapperSource = source
            .substringAfter("val wrappedOnVideoClick")
            .substringBefore("val onTodayWatchVideoClick")

        assertFalse(source.contains("pendingHeroFlyoutRequest"))
        assertFalse(source.contains("shouldRunHomeHeroFlyoutBeforeNavigation(request)"))
        assertFalse(source.contains("resolveHomeHeroFlyoutNavigationDelayMillis()"))
        assertFalse(clickWrapperSource.contains("setBottomBarVisible(false)"))
        assertTrue(source.contains("BottomBarMatchedDockVisibility("))
        assertTrue(source.contains("edge = BottomBarMatchedDockEdge.TOP"))
        assertTrue(source.contains("shouldShowHomeOverlayChromeDuringVideoCardTransition("))
        assertTrue(source.contains("shouldHomeFeedOwnVideoCardTransitionSnapshot("))
        assertTrue(source.contains("videoCardTransitionBackgroundEffect("))
        assertTrue(clickWrapperSource.contains("onVideoClick(request)"))
    }

    @Test
    fun ordinaryHomeVideoCardDoesNotRunSourceFlyout() {
        val pageSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt")
        val cardSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt")

        assertFalse(pageSource.contains("heroFlyoutBvid"))
        assertFalse(pageSource.contains("heroFlyoutActive"))
        assertFalse(cardSource.contains("heroFlyoutActive"))
        assertFalse(cardSource.contains("resolveHomeHeroFlyoutFrame("))
    }

    @Test
    fun ordinaryHomeVideoCardUsesCoverFirstSharedTransitionPolicy() {
        val cardSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt")

        assertTrue(cardSource.contains("resolveVideoCardSharedTransitionMotionSpec("))
        assertTrue(cardSource.contains("resolveVideoSharedTransitionVisualSpec("))
        assertTrue(cardSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertTrue(cardSource.contains("sharedElementSourceRoute"))
        assertFalse(cardSource.contains("使用 renderInSharedTransitionScopeOverlayOption 控制可见性"))
    }

    @Test
    fun homeHeroCarouselUsesWholeCardShellSharedTransition() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/HomeHeroCarousel.kt")

        assertTrue(source.contains("videoCardShellSharedBoundsOrEmpty("))
        assertTrue(source.contains("resolveVideoCardSharedTransitionMotionSpec("))
        assertTrue(source.contains("memoryCacheKey(normalizedCoverUrl)"))
        assertTrue(source.contains("LocalSharedTransitionEnabled.current"))
        assertTrue(source.contains("sourceCornerDp = cardCornerDp.value.roundToInt()"))
        assertFalse(source.contains("videoCoverSharedElementKey("))
        assertFalse(source.contains("videoViewsSharedElementKey("))
        assertFalse(source.contains("videoDanmakuSharedElementKey("))
        assertFalse(source.contains("videoDurationSharedElementKey("))
    }

    @Test
    fun homeHeroCarouselStatsTextUsesValidChineseUnits() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/HomeHeroCarousel.kt")
        // 可见统计文案：分隔符 · + 单位「播放」「弹幕」，禁止损坏字面量。
        assertTrue(source.contains("\" · \""))
        assertTrue(source.contains("formatStat(video.stat.view.toLong()) + \"播放\""))
        assertTrue(source.contains("formatStat(video.stat.danmaku.toLong()) + \"弹幕\""))
        assertFalse(source.contains("formatStat(video.stat.view.toLong()) + \"??\""))
        assertFalse(source.contains("formatStat(video.stat.danmaku.toLong()) + \"??\""))
        assertFalse(source.contains("\" \uFFFD \""))
    }

    @Test
    fun homeHeroCarouselOwnsItsGestureBeforeOuterCategoryPager() {
        val carouselSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/HomeHeroCarousel.kt"
        )
        val homeSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt"
        )
        val gestureSource = loadSource(
            "app/src/main/java/com/android/purebilibili/core/ui/common/VerticalPriorityPagerGesture.kt"
        )

        assertTrue(carouselSource.contains("pointerInput(Unit)"))
        assertTrue(carouselSource.contains("onGestureActiveChangeLatest.value(true)"))
        assertTrue(carouselSource.contains("onGestureActiveChangeLatest.value(false)"))
        assertTrue(carouselSource.contains("userScrollEnabled = videos.size > 1"))
        assertTrue(carouselSource.contains("AppHorizontalPager as HorizontalPager"))
        assertFalse(carouselSource.contains("pointerInput(onGestureActiveChange)"))
        assertFalse(carouselSource.contains("PageSize.Fixed("))
        assertFalse(carouselSource.contains("contentPadding = PaddingValues(horizontal = sidePeek)"))
        assertTrue(homeSource.contains("shouldYieldGesture = shouldYieldHomePagerToHeroCarousel"))
        assertTrue(homeSource.contains("onHeroCarouselGestureActiveChange = onHeroCarouselGestureActiveChange"))
        assertFalse(homeSource.contains("!isHeroCarouselGestureActive"))
        assertTrue(gestureSource.contains("shouldYield: () -> Boolean = { false }"))
        assertTrue(gestureSource.contains("if (latestShouldYield.value()) return@gesture"))
    }

    @Test
    fun homeHeroCarouselUsesPageDotsInsteadOfNumericBadge() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/HomeHeroCarousel.kt"
        )
        assertTrue(source.contains("repeat(videos.size)"))
        assertTrue(source.contains("index == pagerState.currentPage"))
        assertFalse(source.contains("\${pagerState.currentPage + 1} / \${videos.size}"))
    }

    @Test
    fun inlinePartitionPageKeepsPartitionVideoSourceInsteadOfHomeFeed() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val partitionPageSource = source
            .substringAfter("when (val entry = resolveHomeTopTabEntryOrNull(topTabEntries, page))")
            .substringAfter("HomeTopTabEntry.Partition ->")
            .substringBefore("is HomeTopTabEntry.Category ->")

        assertTrue(partitionPageSource.contains("LocalVideoCardSharedElementSourceRoute provides partitionVideoSourceRoute"))
        assertTrue(partitionPageSource.contains("onVideoClick = onPartitionVideoClick"))
        assertFalse(partitionPageSource.contains("wrappedOnVideoClick("))
    }

    @Test
    fun homeCategoryPageProvidesMatchingSourceRouteForSharedElementsAndNavigation() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt")
        val categoryPageSource = source
            .substringAfter("val sourceRoute = remember(category)")
            .substringBefore("// Loading Indicator at bottom")

        assertTrue(categoryPageSource.contains("LocalVideoCardSharedElementSourceRoute provides sourceRoute"))
        assertTrue(categoryPageSource.contains("sourceRoute = sourceRoute"))
        assertTrue(categoryPageSource.contains("HomeHeroCarousel("))
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
