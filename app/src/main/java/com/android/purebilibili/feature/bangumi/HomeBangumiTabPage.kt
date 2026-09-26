package com.android.purebilibili.feature.bangumi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.components.AppLiquidAwareTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.feature.download.DownloadManager
import kotlinx.coroutines.launch

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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showPgcTimeline by SettingsManager.getShowPgcTimeline(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val channelOptions = remember {
        BangumiChannel.entries.map { AppSegmentOption(it, it.label) }
    }

    LaunchedEffect(initialType) { viewModel.initialize(initialType) }
    LaunchedEffect(showPgcTimeline) { viewModel.setShowPgcTimeline(showPgcTimeline) }

    val layoutDirection = LocalLayoutDirection.current
    val themeConfig = LocalAppThemeConfig.current
    val chromeSource = if (themeConfig.liquidGlassEnabled) {
        rememberChromeBackdropSource()
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(chromeSource?.modifier ?: Modifier)
                .globalWallpaperAwareBackground(MaterialTheme.colorScheme.background),
        ) {
            BangumiHubContent(
                state = state,
                onBangumiClick = onBangumiClick,
                onEpisodeClick = onBangumiEpisodeClick,
                onRefreshHome = { viewModel.refreshHome() },
                onLoadMoreHomeRecommendations = viewModel::loadMoreHomeRecommendations,
                onLoadMoreHomeFollows = viewModel::loadMoreHomeFollows,
                onRetryTimeline = viewModel::retryTimeline,
                onTimelineRangeSelected = viewModel::selectTimelineRange,
                onOpenIndex = viewModel::openIndex,
                onOpenFollow = viewModel::openFollowManager,
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
                onSaveCover = { url, title ->
                    scope.launch {
                        DownloadManager.saveImageToGallery(context, url, title)
                    }
                },
                scrollToTopRequestId = scrollToTopRequestId,
                listBottomPadding = contentPadding.calculateBottomPadding(),
                listTopPadding = channelHeight,
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
        AnimatedVisibility(
            visible = channelTabsVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AppLiquidAwareTabRow(
                options = channelOptions,
                selectedValue = state.channel,
                onSelectionChange = viewModel::selectChannel,
                dragSelectionEnabled = channelOptions.size > 1,
                tapPressRefractionEnabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { channelHeightPx = it.height }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                miuixBackdrop = channelBackdrop,
            )
        }
    }
}
