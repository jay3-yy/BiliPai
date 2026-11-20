package com.android.purebilibili.core.ui.skeleton

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentLoadingSkeletonStructureTest {

    @Test
    fun contentSkeletons_coverVideoGridMediaRowUserRowAndComments() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/core/ui/skeleton/ContentLoadingSkeletons.kt"
        )
        assertTrue(source.contains("fun ContentVideoGridSkeleton("))
        assertTrue(source.contains("fun ContentVideoGridSkeletonFixedColumns("))
        assertTrue(source.contains("fun MediaListRowSkeleton("))
        assertTrue(source.contains("fun UserListRowSkeleton("))
        assertTrue(source.contains("fun CommentListSkeleton("))
        assertTrue(source.contains("fun CommentListColumnSkeleton("))
        assertTrue(source.contains("fun ContentMediaListSkeleton("))
        assertTrue(source.contains("fun ArticleDetailSkeleton("))
        assertTrue(source.contains("fun PosterDetailSkeleton("))
        assertTrue(source.contains("fun ContentCategoryGridSkeleton("))
        assertTrue(source.contains("fun TrendingListSkeleton("))
        assertTrue(source.contains("rememberContentSkeletonPulseState("))
        // Sweep progress stays in the shared State and is consumed by draw helpers.
        assertTrue(source.contains("rememberSkeletonSweepState()"))
        assertTrue(source.contains("colorProvider.skeletonBrush(size)"))
        assertFalse(source.contains("fun rememberContentSkeletonPulse(): Float"))
        assertFalse(source.contains("shimmerEffect()"))
    }

    @Test
    fun detailCatalogAndSpaceInitialLoads_matchTheirRealContentShape() {
        val space = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt"
        )
        val spaceSkeleton = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/space/SpaceLoadingSkeleton.kt"
        )
        val article = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/article/ArticleDetailScreen.kt"
        )
        val bangumiDetail = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/bangumi/BangumiDetailScreen.kt"
        )
        val liveArea = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/live/LiveAreaScreen.kt"
        )
        val trending = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/search/SearchTrendingScreen.kt"
        )

        assertTrue(space.contains("SpaceLoadingSkeleton("))
        assertFalse(space.contains("SpaceUiState.Loading -> {\n                        com.android.purebilibili.core.ui.skeleton.ContentMediaListSkeleton("))
        // 空间首屏骨架沿用 SpaceContent 的网格排版，使用固定色块。
        assertTrue(space.contains("isInitialLoading = isLoadingSpace"))
        assertFalse(spaceSkeleton.contains("LazyVerticalStaggeredGrid("))
        assertFalse(spaceSkeleton.contains("rememberContentSkeletonPulse("))
        assertTrue(spaceSkeleton.contains("LazyStaggeredGridScope.SpaceLoadingSkeleton("))
        assertTrue(spaceSkeleton.contains("StaggeredGridItemSpan.FullLine"))
        // 列数 / 封面比例 / 头部形态都按真实内容同规则算好后传入，保证骨架与卡片同形。
        assertTrue(space.contains("columns = gridColumns"))
        assertTrue(space.contains("coverAspectRatio = spaceFeedCoverAspectRatio"))
        assertTrue(space.contains("resolveSpaceBannerMetrics("))
        assertTrue(space.contains("adaptiveLayoutSpec.useExpandedHeader"))
        assertTrue(article.contains("ArticleDetailSkeleton("))
        assertTrue(bangumiDetail.contains("PosterDetailSkeleton("))
        assertTrue(liveArea.contains("ContentCategoryGridSkeleton("))
        assertTrue(trending.contains("TrendingListSkeleton("))
    }

    @Test
    fun searchScreen_usesSkeletonInsteadOfThemeLoadingForResults() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/search/SearchScreen.kt"
        )
        assertTrue(source.contains("SearchResultBodyMode.LOADING"))
        assertTrue(
            source.contains("ContentVideoGridSkeletonFixedColumns(") ||
                source.contains("ContentVideoGridSkeleton(")
        )
        assertTrue(source.contains("ContentMediaListSkeleton("))
        assertFalse(source.contains("LoadingAnimation("))
        assertFalse(source.contains("text = \"搜索中...\""))
    }

    @Test
    fun listStyleScreens_preferSkeletonOnInitialLoad() {
        val category = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/category/CategoryScreen.kt"
        )
        val partition = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/partition/PartitionScreen.kt"
        )
        val watchLater = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt"
        )
        val liveSearch = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/live/LiveSearchScreen.kt"
        )

        assertTrue(category.contains("ContentVideoGridSkeletonFixedColumns("))
        assertTrue(partition.contains("ContentMediaListSkeleton("))
        assertTrue(watchLater.contains("ContentMediaListSkeleton("))
        assertTrue(liveSearch.contains("ContentVideoGridSkeletonFixedColumns("))
        assertTrue(liveSearch.contains("ContentMediaListSkeleton("))
    }

    @Test
    fun bangumiAndCommentSurfaces_useSkeletonsForEmptyInitialLoading() {
        val bangumi = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/bangumi/BangumiHubContent.kt"
        )
        val videoComments = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/components/VideoCommentSheetHost.kt"
        )
        val dynamicComments = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/dynamic/components/DynamicCommentSheet.kt"
        )

        assertTrue(bangumi.contains("BangumiPosterGridSkeleton()"))
        assertTrue(bangumi.contains("BangumiTimelineSkeleton()"))
        assertTrue(bangumi.contains("BangumiFollowManagerSkeleton()"))
        assertTrue(videoComments.contains("state.isRepliesLoading &&"))
        // 视频评论骨架同样改为「稳定槽位」：拆成多个 lazy item（videoCommentSkeletonItems），
        // 真实评论项加 animateItem；骨架换成内容时是槽位原地替换，列表不会跳。
        assertTrue(videoComments.contains("videoCommentSkeletonItems("))
        assertTrue(videoComments.contains("videoCommentItemModifier()"))
        assertTrue(videoComments.contains("commentSkeletonSlotCount"))
        assertFalse(videoComments.contains("CommentListColumnSkeleton("))
        assertTrue(dynamicComments.contains("isLoading && comments.isEmpty()"))
        // 动态评论骨架改为「稳定槽位」：拆成多个 lazy item，并复用与真实评论一致的
        // CommentListItemSkeleton；不再用「单个 item 塞 N 行」的 CommentListColumnSkeleton，
        // 否则骨架换成内容时 item 结构整体替换、列表会跳。
        assertTrue(dynamicComments.contains("dynamicCommentSkeletonItems("))
        assertTrue(dynamicComments.contains("CommentListItemSkeleton("))
    }

    private fun loadSource(path: String): String {
        val candidates = listOf(
            File(path),
            File("app", path.removePrefix("app/")),
            File(path.removePrefix("app/")),
        )
        return candidates.first { it.exists() }.readText()
    }
}
