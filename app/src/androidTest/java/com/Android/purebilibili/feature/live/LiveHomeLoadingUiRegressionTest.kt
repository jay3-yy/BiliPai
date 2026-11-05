package com.android.purebilibili.feature.live

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LiveHomeLoadingUiRegressionTest(
    private val style: AppUiStyle,
    private val glassEnabled: Boolean,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun partialFirstResponseStaysBehindSkeletonUntilInitialLoadCompletes() {
        val initialLoadComplete = mutableStateOf(false)
        val contentLoadComplete = mutableStateOf(false)
        val room = LiveRoomItem(
            roomId = 1, title = "首批真实直播内容", cover = "", uname = "主播", face = "",
            online = 1, areaName = "分区",
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppUiStyle provides style,
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glassEnabled),
            ) {
                MaterialTheme {
                    val visualSpec = resolveLiveVisualSpec(rememberAppTopChromePolicy().tabPresentation)
                    LiveHomeContent(
                        gridState = rememberLazyGridState(),
                        followRowState = rememberLazyListState(),
                        // 模拟卡片与关注数据先到、整轮首次加载尚未结束。
                        contentItems = listOf(room), followItems = listOf(room),
                        areaEntries = emptyList(), areaList = emptyList(),
                        selectedAreaIndex = 0, selectedParentAreaId = 0,
                        sortTags = emptyList(), selectedSortType = null,
                        livingCount = 1, showFollowSection = true, followError = null,
                        isLoading = !contentLoadComplete.value,
                        showInitialSkeleton = !initialLoadComplete.value,
                        showContentSkeleton = !contentLoadComplete.value,
                        error = null, onRetry = {}, isLoadingMore = false,
                        hasMore = false, loadMoreError = null, showFirstFrame = false,
                        bottomPadding = 0.dp, topPadding = 0.dp,
                        metrics = visualSpec.homeMetrics, visualSpec = visualSpec,
                        onLiveClick = { _, _, _ -> }, onUserClick = {}, onAreaSelected = {},
                        onToggleFirstFrame = {}, onAreaListClick = {}, onMatchClick = {},
                        onSortTagSelected = {}, onLoadMore = {},
                        onAreaDetailClick = { _, _, _ -> }, onFollowingClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("首批真实直播内容").assertDoesNotExist()
        composeRule.onNodeWithText("我的关注", substring = true).assertDoesNotExist()
        composeRule.runOnIdle {
            initialLoadComplete.value = true
            contentLoadComplete.value = true
        }
        composeRule.onNodeWithText("我的关注", substring = true).assertExists()
        composeRule.onNodeWithText("首批真实直播内容").assertExists()
        // 后续切分区只遮住卡片区，保留分类与关注入口；先到的卡片仍不能穿透骨架。
        composeRule.runOnIdle { contentLoadComplete.value = false }
        composeRule.onNodeWithText("我的关注", substring = true).assertExists()
        composeRule.onNodeWithText("首批真实直播内容").assertDoesNotExist()
        composeRule.runOnIdle { contentLoadComplete.value = true }
        composeRule.onNodeWithText("首批真实直播内容").assertExists()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "style={0}, glass={1}")
        fun configurations(): List<Array<Any>> = listOf(
            arrayOf(AppUiStyle.MATERIAL3, false),
            arrayOf(AppUiStyle.MIUIX, false),
            arrayOf(AppUiStyle.MATERIAL3, true),
            arrayOf(AppUiStyle.MIUIX, true),
        )
    }
}
