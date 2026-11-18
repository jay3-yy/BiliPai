package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.feature.home.collectAsHomeFeedState
import com.android.purebilibili.navigation.tabIndicatorPosition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSnackbarHost
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.components.AppLiquidAwareTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption

/**
 * 首页顶栏「追番」独立页：留在首页 Pager 内，
 * 不切走导航，也不再套一层带返回的番剧二级页。
 */
@Composable
fun HomeBangumiTabPage(
    contentPadding: PaddingValues,
    onBangumiClick: (Long) -> Unit,
    onBangumiEpisodeClick: (Long, Long) -> Unit,
    initialType: Int = 1,
    scrollToTopRequestId: Int = 0,
    viewModel: BangumiHubViewModel = viewModel(),
    pageActive: Boolean = true,
    onOpenFavoritePgc: ((BangumiChannel) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsHomeFeedState(pageActive)
    val channelPager = rememberBangumiChannelPagerState(
        channel = state.channel,
        enabled = pageActive && state.page == BangumiHubPage.HOME,
        onChannelSelected = viewModel::selectChannel,
    )
    val inHubHome = state.page == BangumiHubPage.HOME
    val snackbarHostState = remember { SnackbarHostState() }
    val handleBack: () -> Unit = { viewModel.consumeBack() }
    LocalNavigationBackHandler(enabled = pageActive && !inHubHome, onBackCompleted = handleBack)
    LaunchedEffect(viewModel, pageActive) {
        if (pageActive) viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    val context = LocalContext.current
    val showPgcTimeline by SettingsManager.getShowPgcTimeline(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val channelOptions = remember {
        BangumiChannel.entries.map { AppSegmentOption(it, it.label) }
    }

    LaunchedEffect(initialType, pageActive) { if (pageActive) viewModel.initialize(initialType) }
    LaunchedEffect(showPgcTimeline, pageActive) { if (pageActive) viewModel.setShowPgcTimeline(showPgcTimeline) }

    val layoutDirection = LocalLayoutDirection.current
    val themeConfig = LocalAppThemeConfig.current
    val chromeSource = if (themeConfig.liquidGlassEnabled) {
        rememberChromeBackdropSource(recordingBackground = MaterialTheme.colorScheme.background)
    } else null
    val channelBackdrop = chromeSource?.takeIf {
        shouldCaptureBangumiHubChrome(state) && it.isReady
    }?.backdrop
    val density = LocalDensity.current
    var channelHeightPx by remember { mutableIntStateOf(0) }
    val channelHeight = with(density) { channelHeightPx.toDp() }
    // 频道 tab 行（番剧/影视）：下滑折叠隐藏，上滑/回顶重新出现
    var channelTabsVisible by remember { mutableStateOf(true) }
    var lastScrollIndex by remember { mutableStateOf(0) }
    var lastScrollOffset by remember { mutableStateOf(0) }
    val homeTopPadding = contentPadding.calculateTopPadding()
    val visibleChannelHeight = if (channelTabsVisible || !inHubHome) channelHeight else 0.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(chromeSource?.modifier ?: Modifier)
                .globalWallpaperAwareBackground(MaterialTheme.colorScheme.background),
        ) {
            // 顶部避让放进列表，让内容滚动到首页顶栏下方参与渐进模糊与纯色模糊采样。
            Box(Modifier.fillMaxSize().clipToBounds()) {
                BangumiHubContent(
                    state = state,
                    channelPagerState = channelPager,
                    onPrepareChannel = viewModel::prepareHomeChannel,
                    onBangumiClick = onBangumiClick,
                    onEpisodeClick = onBangumiEpisodeClick,
                    onRefreshHome = { viewModel.refreshHome() },
                    onRefreshHomeFollows = viewModel::refreshHomeFollows,
                    onLoadMoreHomeRecommendations = viewModel::loadMoreHomeRecommendations,
                    onLoadMoreHomeFollows = viewModel::loadMoreHomeFollows,
                    onRetryTimeline = viewModel::retryTimeline,
                    onOpenIndex = viewModel::openIndex,
                    onOpenFollow = {
                        if (onOpenFavoritePgc != null) onOpenFavoritePgc(state.channel)
                        else viewModel.openFollowManager()
                    },
                    onIndexCategorySelected = viewModel::selectIndexCategory,
                    onIndexFilterSelected = viewModel::selectIndexFilter,
                    onToggleFiltersExpanded = viewModel::toggleIndexFiltersExpanded,
                    onRetryIndexConditions = viewModel::retryIndexConditions,
                    onRetryIndexResults = viewModel::retryIndexResults,
                    onLoadMoreIndexResults = viewModel::loadMoreIndexResults,
                    onFollowStatusSelected = viewModel::selectFollowStatus,
                    onRefreshFollow = viewModel::refreshFollowManager,
                    onLoadMoreFollow = viewModel::loadMoreFollowManager,
                    onToggleFollowSelection = viewModel::toggleFollowSelection,
                    onSelectAllFollow = viewModel::selectAllFollowItems,
                    onClearFollowSelection = viewModel::clearFollowSelection,
                    onMoveSelectedFollow = viewModel::moveSelectedFollowItems,
                    onMoveSingleFollow = viewModel::updateSingleFollowItem,
                    onUnfollowSingle = viewModel::unfollowSingleItem,
                    onSearchCategorySelected = viewModel::selectSearchCategory,
                    onLoadMoreSearch = viewModel::loadMoreSearch,
                    scrollToTopRequestId = scrollToTopRequestId,
                    listBottomPadding = contentPadding.calculateBottomPadding(),
                    // 频道栏收起后撤销其占位，不保留上次测量的完整高度。
                    listTopPadding = homeTopPadding + visibleChannelHeight,
                    tabBackdrop = null,
                    onHomeScrollChanged = { index, offset ->
                        channelTabsVisible = if (index == 0 && offset < 100) {
                            true
                        } else {
                            val scrollingDown = when {
                                index > lastScrollIndex -> true
                                index < lastScrollIndex -> false
                                else -> offset > lastScrollOffset + 50
                            }
                            val scrollingUp = when {
                                index < lastScrollIndex -> true
                                index > lastScrollIndex -> false
                                else -> offset < lastScrollOffset - 50
                            }
                            if (scrollingDown) false else if (scrollingUp) true else channelTabsVisible
                        }
                        lastScrollIndex = index
                        lastScrollOffset = offset
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = channelTabsVisible || !inHubHome,
            modifier = Modifier.padding(top = homeTopPadding),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().onSizeChanged { channelHeightPx = it.height }) {
                if (!inHubHome) {
                    AppTopBar(
                        modifier = Modifier.consumeWindowInsets(WindowInsets.statusBars),
                        title = when (state.page) {
                            BangumiHubPage.INDEX -> "索引"
                            BangumiHubPage.FOLLOW -> if (state.channel == BangumiChannel.BANGUMI) "我的追番" else "我的追剧"
                            BangumiHubPage.SEARCH -> "搜索"
                            BangumiHubPage.HOME -> state.channel.label
                        },
                        navigationIcon = {
                            AppIconButton(onClick = handleBack) {
                                AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                            }
                        },
                    )
                } else {
                    AppLiquidAwareTabRow(
                        // Glass indicator fills the measured slot; native geometry keeps its defaults.
                        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                        options = channelOptions,
                        selectedValue = state.channel,
                        indicatorPositionProvider = { channelPager.tabIndicatorPosition },
                        isScrollInProgressProvider = { channelPager.isScrollInProgress },
                        onSelectionChange = viewModel::selectChannel,
                        dragSelectionEnabled = channelOptions.size > 1,
                        tapPressRefractionEnabled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        miuixBackdrop = channelBackdrop,
                    )
                }
            }
        }
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}
