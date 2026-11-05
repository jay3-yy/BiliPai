package com.android.purebilibili.feature.home

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

internal data class HomeTodayWatchContentState(
    val enabled: Boolean = false,
    val mode: TodayWatchMode = TodayWatchMode.RELAX,
    val plan: TodayWatchPlan? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val collapsed: Boolean = false,
    val cardConfig: TodayWatchCardUiConfig = TodayWatchCardUiConfig(),
)

/** 今日推荐的数据变化只重组推荐页内容，不订阅到首页分页与导航宿主。 */
@Composable
internal fun HomeTodayWatchContentHost(
    viewModel: HomeViewModel,
    category: HomeCategory,
    updatesAllowed: Boolean,
    content: @Composable (HomeTodayWatchContentState) -> Unit,
) {
    if (category != HomeCategory.RECOMMEND) {
        content(remember { HomeTodayWatchContentState() })
        return
    }
    val enabled by viewModel.todayWatchPluginEnabled.collectAsHomeFeedState(updatesAllowed)
    val mode by viewModel.todayWatchMode.collectAsHomeFeedState(updatesAllowed)
    val plan by viewModel.todayWatchPlan.collectAsHomeFeedState(updatesAllowed)
    val loading by viewModel.todayWatchLoading.collectAsHomeFeedState(updatesAllowed)
    val error by viewModel.todayWatchError.collectAsHomeFeedState(updatesAllowed)
    val collapsed by viewModel.todayWatchCollapsed.collectAsHomeFeedState(updatesAllowed)
    val cardConfig by viewModel.todayWatchCardConfig.collectAsHomeFeedState(updatesAllowed)
    content(HomeTodayWatchContentState(enabled, mode, plan, loading, error, collapsed, cardConfig))
}

/** 首次推荐回顶仍由同一列表和组合协程执行，保留导航期间的工作门控。 */
@Composable
internal fun HomeTodayWatchStartupEffect(
    viewModel: HomeViewModel,
    currentCategory: HomeCategory,
    homeFeedWorkAllowed: Boolean,
    useRegularHomeGrid: Boolean,
    gridStates: Map<HomeCategory, HomeFeedGridState>,
    homeStartupElapsedAt: Long,
    onResetHeader: () -> Unit,
    onScrollReset: () -> Unit,
) {
    val resetHeader by rememberUpdatedState(onResetHeader)
    val resetScroll by rememberUpdatedState(onScrollReset)
    var todayWatchStartupRevealHandled by rememberSaveable { mutableStateOf(false) }
    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val updatesAllowed = homeFeedWorkAllowed && visualWorkActive &&
        currentCategory == HomeCategory.RECOMMEND && !todayWatchStartupRevealHandled
    val todayWatchPluginEnabled by viewModel.todayWatchPluginEnabled.collectAsHomeFeedState(updatesAllowed)
    val todayWatchPlan by viewModel.todayWatchPlan.collectAsHomeFeedState(updatesAllowed)
    val todayWatchCollapsed by viewModel.todayWatchCollapsed.collectAsHomeFeedState(updatesAllowed)
    // [TodayWatch首曝] 冷启动启动窗口内自动回顶一次，确保用户能看到今日推荐单卡片。
    LaunchedEffect(
        todayWatchPluginEnabled,
        todayWatchPlan?.generatedAt,
        currentCategory,
        homeFeedWorkAllowed,
        useRegularHomeGrid,
        visualWorkActive,
    ) {
        if (!homeFeedWorkAllowed || !visualWorkActive || todayWatchStartupRevealHandled) return@LaunchedEffect

        val recommendGridState = gridStates[HomeCategory.RECOMMEND] ?: return@LaunchedEffect
        val decision = decideTodayWatchStartupReveal(
            startupElapsedMs = SystemClock.elapsedRealtime() - homeStartupElapsedAt,
            isPluginEnabled = todayWatchPluginEnabled,
            currentCategory = currentCategory,
            hasTodayPlan = todayWatchPlan != null && !todayWatchCollapsed,
            firstVisibleItemIndex = recommendGridState.firstVisibleItemIndex,
            firstVisibleItemOffset = recommendGridState.firstVisibleItemScrollOffset
        )

        when (decision) {
            TodayWatchStartupRevealDecision.REVEAL -> {
                resetHeader()
                if (recommendGridState.firstVisibleItemIndex > 12) {
                    recommendGridState.scrollToItem(12)
                }
                recommendGridState.animateScrollToItem(0)
                resetHeader()
                resetScroll()
                todayWatchStartupRevealHandled = true
            }
            TodayWatchStartupRevealDecision.SKIP -> {
                todayWatchStartupRevealHandled = true
            }
            TodayWatchStartupRevealDecision.WAIT -> Unit
        }
    }
}
