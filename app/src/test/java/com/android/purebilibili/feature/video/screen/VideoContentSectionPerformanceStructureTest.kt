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
        val commentTabSource = source.substringAfter("private fun VideoCommentTab(")

        assertTrue(commentTabSource.contains("val shouldLoadMore by remember("))
        assertTrue(commentTabSource.contains("LaunchedEffect(shouldLoadMore)"))
        assertFalse(
            commentTabSource.contains(
                "item {\n                    val shouldLoadMore by remember("
            )
        )
    }

    @Test
    fun videoContentSection_pagerAvoidsKeepingIntroAliveOnCommentTab() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoContentSection.kt"
        )

        assertTrue(source.contains("beyondViewportPageCount = resolveVideoDetailBeyondViewportPageCount("))
        assertTrue(source.contains("isVideoPlaying = isVideoPlaying,"))
        assertTrue(source.contains("selectedTabIndex = pagerState.currentPage"))
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
        assertTrue(source.contains("var tabBarCollapsePx by remember"))
        assertTrue(source.contains("resolveVideoContentTabBarCollapseProgress("))
        assertTrue(source.contains("enabled = tabBarCollapseEnabled"))
        assertFalse(source.contains("visible = !collapseTabBarForCommentScroll"))
        // Sort filter bar lives in the top chrome beside the comment tab.
        assertTrue(source.contains("pagerState.currentPage == 1 &&"))
        assertTrue(source.contains("visible = commentListAtTop"))
        assertTrue(source.contains("CommentSortFilterBar("))
        val sortControlSource = source
            .substringAfter("CommentSortFilterBar(")
            .substringBefore("AppLiquidGlassBackToTopButton(")
        assertTrue(sortControlSource.contains("liquidGlassEffectsEnabled = liquidGlassEnabled"))
        assertTrue(sortControlSource.contains(".align(Alignment.TopEnd)"))
        assertFalse(sortControlSource.contains("visible = commentListAtTop"))
        assertFalse(source.contains("(!tabBarCollapseEnabled || commentListAtTop)"))
        assertTrue(source.contains("顶部标签与评论标题/排序共用同一张渐进模糊材质"))
        assertTrue(source.contains(".height(tabBarVisibleHeightDp + commentChromeHeight)"))
        assertTrue(source.contains("showHeader = !immersiveVideoContentChromeEnabled"))
        assertTrue(source.contains("shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex)"))
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
