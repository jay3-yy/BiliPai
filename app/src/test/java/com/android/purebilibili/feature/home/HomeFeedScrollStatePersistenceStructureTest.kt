package com.android.purebilibili.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedScrollStatePersistenceStructureTest {

    @Test
    fun `bottom bar reselect scrolls the latest visible home category`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val scrollCollectorSource = source
            .substringAfter("// [新增] 监听全局回顶事件")
            .substringBefore("val homeTopTabSettings")

        assertTrue(
            scrollCollectorSource.contains(
                "latestHomeScrollCategory by rememberUpdatedState(currentCategory)"
            )
        )
        assertTrue(
            scrollCollectorSource.contains(
                "latestHomeScrollPopularSubCategory by rememberUpdatedState(popularSubCategory)"
            )
        )
        assertTrue(scrollCollectorSource.contains("val activeCategory = latestHomeScrollCategory"))
        assertTrue(scrollCollectorSource.contains("gridStates[activeCategory]"))
    }

    @Test
    fun `home double tap is coalesced before scroll to top starts`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val scrollCollectorSource = source
            .substringAfter("LaunchedEffect(scrollChannel)")
            .substringBefore("TrackJankStateFlag(")

        assertTrue(scrollCollectorSource.contains("for (initialRequest in channel)"))
        assertTrue(scrollCollectorSource.contains("withTimeoutOrNull(HOME_RESELECT_DOUBLE_TAP_WINDOW_MS)"))
        assertTrue(scrollCollectorSource.contains("mergeHomeScrollRequests(initialRequest, followUp)"))
        assertFalse(scrollCollectorSource.contains("collectLatest"))
    }

    @Test
    fun `avatar preference does not change card bounds during predictive return`() {
        val screenSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val pageSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt")
        val storySource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/cards/StoryVideoCard.kt"
        )
        val standardCardSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt"
        )

        assertTrue(screenSource.contains("var settledShowHomeUpAvatars by rememberSaveable"))
        assertTrue(screenSource.contains("isReturnGestureInProgressProvider()"))
        assertTrue(screenSource.contains("!videoCardReturnGestureInProgress && !isReturningFromVideoDetail"))
        assertTrue(screenSource.contains("showUpAvatars = settledShowHomeUpAvatars"))

        // 关闭后不创建头像节点，也不创建兜底头像节点；Row 的 spacedBy 只作用于实际子项。
        assertTrue(pageSource.contains("if (showUpAvatars && video.owner.face.isNotBlank())"))
        assertTrue(pageSource.contains("} else if (showUpAvatars) {"))
        assertTrue(storySource.contains("leadingContent = if (showUpAvatar && video.owner.face.isNotEmpty())"))
        assertTrue(standardCardSource.contains("leadingContent = if (showUpAvatar && video.owner.face.isNotEmpty())"))
    }

    @Test
    fun `home skin atmosphere is fixed in header instead of pager backdrop`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val headerCallSource = source
            .substringAfter("HomeHeader(")
            .substringBefore("AnimatedVisibility(")

        assertTrue(source.contains("val uiSkinState by rememberUiSkinState(context)"))
        assertTrue(source.contains("val homeUiSkinDecoration = rememberHomeUiSkinDecoration(uiSkinState)"))
        assertTrue(headerCallSource.contains("uiSkinDecoration = homeUiSkinDecoration"))
        assertFalse(source.contains("import com.android.purebilibili.feature.home.components.HomeSkinAtmosphere"))
        assertFalse(source.contains("HomeSkinAtmosphere(\n                        decoration = homeUiSkinDecoration"))
    }

    @Test
    fun `home feed does not misuse drawer or profile skin backgrounds`() {
        val screenSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val pageSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt")
        val pageCallSource = screenSource
            .substringAfter("HomeCategoryPageContent(")
            .substringBefore("firstGridItemModifier = Modifier")
        val pageFunctionSource = pageSource
            .substringAfter("internal fun HomeCategoryPageContent(")
            .substringBefore("@Composable\nprivate fun PopularSubCategorySegmentedControl")
        assertFalse(pageCallSource.contains("uiSkinDecoration = homeUiSkinDecoration"))
        assertFalse(pageFunctionSource.contains("uiSkinDecoration: HomeUiSkinDecoration? = null"))
        assertFalse(pageSource.contains("resolveHomeFeedSkinAtmosphereImagePath"))
        assertFalse(pageSource.contains("sideBackgroundImagePath"))
        assertFalse(pageSource.contains("profileBackgroundImagePath"))
    }

    @Test
    fun `home pager page refresh uses page category instead of stale current state`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val pagerPageSource = source
            .substringAfter("HorizontalPager(")
            .substringBefore("// Close HorizontalPager lambda")
        val onRefreshSource = pagerPageSource
            .substringAfter("onRefresh = {")
            .substringBefore("},")

        assertTrue(onRefreshSource.contains("viewModel.refresh(category)"))
        assertFalse(onRefreshSource.contains("viewModel.refresh()"))
        assertFalse(onRefreshSource.contains("if (category == HomeCategory.FOLLOW)"))
    }

    @Test
    fun `home feed samples scroll state without observing every scroll transition`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeCategoryPage.kt")
        val pageFunctionSource = source
            .substringAfter("internal fun HomeCategoryPageContent(")
            .substringBefore("@Composable\nprivate fun PopularSubCategorySegmentedControl")

        assertTrue(pageFunctionSource.contains("Snapshot.withoutReadObservation"))
        assertTrue(pageFunctionSource.contains("gridState.isScrollInProgress"))
        assertFalse(pageFunctionSource.contains("derivedStateOf { gridState.isScrollInProgress }"))
    }

    @Test
    fun `home pager frame state is read inside isolated motion layer`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/TopBar.kt")
        val tabsSource = source
            .substringAfter("private fun LightweightHomeTopTabs(")
            .substringBefore("internal enum class TopTabLiquidColorMode")
        val motionSource = tabsSource.substringAfter("HomeTopTabMotionLayer {")

        assertTrue(tabsSource.contains("val currentPositionProvider = remember(pagerState, selectedIndex)"))
        assertTrue(tabsSource.contains("val pagerScrollingProvider = remember(pagerState)"))
        assertTrue(motionSource.contains("val currentPosition = currentPositionProvider()"))
        assertTrue(motionSource.contains("val pagerIsScrolling = pagerScrollingProvider()"))
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
