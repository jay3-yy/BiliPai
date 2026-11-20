package com.android.purebilibili.feature.video.screen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoContentSectionPerformanceStructureTest {

    @Test
    fun videoContentSection_usesDerivedStateForMotionBudgetAndCommentLiteMode() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )

        assertTrue(source.contains("val isIntroListScrolling by remember"))
        assertTrue(source.contains("val isCommentListScrolling by remember"))
        assertTrue(source.contains("val videoDetailMotionBudget by remember"))
        assertTrue(source.contains("val lightweightCommentRendering by remember"))
        assertTrue(source.contains("isCommentListScrolling = isCommentListScrolling"))
    }

    @Test
    fun videoContentSection_commentLoadMore_isHoistedOutsideLazyItem() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )
        val commentTabSource = source.substringAfter("internal fun VideoCommentTab(")

        assertTrue(commentTabSource.contains("val shouldLoadMore by remember("))
        assertTrue(commentTabSource.contains("LaunchedEffect(shouldLoadMore, isRepliesRefreshing, repliesError, paginationPage)"))
        assertFalse(
            commentTabSource.contains(
                "item {\n                    val shouldLoadMore by remember("
            )
        )
    }

    @Test
    fun videoContentSection_collapsesIntroCommentTabBarWithInterruptibleNestedScroll() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )

        // Real-time finger follow via NestedScrollConnection, not AnimatedVisibility snap.
        assertTrue(source.contains("reduceVideoContentTabBarCollapseOnPreScroll("))
        assertTrue(source.contains("reduceVideoContentTabBarCollapseOnPostScroll("))
        assertTrue(source.contains("nestedScroll(tabBarCollapseConnection)"))
        assertTrue(source.contains("var commentHeaderCollapsePx by remember"))
        assertTrue(source.contains("resolveVideoContentTabBarCollapseProgress("))
        assertTrue(source.contains("enabled = commentHeaderCollapseEnabled"))
        assertFalse(source.contains("visible = !collapseTabBarForCommentScroll"))
        // Sort filter bar lives in the top chrome beside the comment tab.
        // Essential comment controls stay available independently of tab collapse.
        val sharedHeader = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/VideoCommentFloatingHeader.kt"
        )
        assertTrue(source.contains("VideoCommentFloatingHeader("))
        assertFalse(sharedHeader.contains("AnimatedVisibility"))
        assertFalse(sharedHeader.contains("listAtTop"))
        assertFalse(sharedHeader.contains("showNativeTitle"))
        assertTrue(source.contains("val commentListAtTop by remember(commentListState)"))
        assertTrue(source.contains("commentHeaderCollapseEnabled by remember(pagerState)"))
        assertTrue(source.contains("showHeader = !immersiveVideoContentChromeEnabled && !liquidGlassEnabled"))
        assertTrue(source.contains("floatingHeaderContentPaddingPx = if (immersiveVideoContentChromeEnabled || liquidGlassEnabled)"))
        assertTrue(source.contains("tabBarVisibleHeightPx() + commentHeaderVisibleHeightPx() * commentFraction"))
        assertTrue(source.contains(".videoContentHeight(commentHeaderVisibleHeightPx)"))
        assertFalse(source.contains("tabBarVisibleHeightPx() + commentHeaderHeightProvider() * commentFraction"))
        assertTrue(source.contains("CommentSortFilterBar("))
        assertTrue(sharedHeader.contains("liquidGlassEffectsEnabled = liquidGlassEnabled"))
        assertTrue(sharedHeader.contains("FlowRow("))
        assertTrue(sharedHeader.contains("Modifier.offset(y = (-nativeSortDockLiftDp).dp)"))
        assertFalse(source.contains("(!tabBarCollapseEnabled || commentListAtTop)"))
        assertTrue(source.contains("顶部标签与评论标题/排序共用同一张渐进模糊材质"))
        assertTrue(source.contains("resolveVideoContentCommentChromeHeightFraction("))
        assertFalse(source.contains("pagerState.currentPage == 1 || pagerState.isScrollInProgress"))
        assertTrue(source.contains("progressiveTopFadeEnabled"))
        assertTrue(source.contains(".topSolidProgressiveFade("))
        assertTrue(source.contains("showHeader = !immersiveVideoContentChromeEnabled"))
        assertTrue(source.contains("showDanmakuActions && shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex)"))
    }

    private fun loadSource(path: String): String {
        val candidates = listOf(
            File(path),
            File("app", path.removePrefix("app/")),
            File(path.removePrefix("app/"))
        )
        return candidates.first { it.exists() }.readText()
    }
}
