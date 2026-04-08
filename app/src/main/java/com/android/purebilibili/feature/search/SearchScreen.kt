// 文件路径: feature/search/SearchScreen.kt
package com.android.purebilibili.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppHistoryIcon
import com.android.purebilibili.core.ui.rememberAppSearchIcon
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard  //  使用首页卡片
import com.android.purebilibili.core.store.SettingsManager  //  读取动画设置
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.SearchOrderSort
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.SearchArticleItem
import kotlinx.coroutines.launch

internal fun shouldShowSearchHotSection(
    hotItemCount: Int,
    hotSearchEnabled: Boolean
): Boolean = hotSearchEnabled && hotItemCount > 0

internal fun shouldShowSearchHotHeader(
    hotItemCount: Int,
    hotSearchEnabled: Boolean
): Boolean = hotItemCount > 0

internal data class SearchTopBarLayoutSpec(
    val showInlineHotToggle: Boolean,
    val placeholderMaxLines: Int
)

internal fun resolveSearchTopBarLayoutSpec(): SearchTopBarLayoutSpec {
    return SearchTopBarLayoutSpec(
        showInlineHotToggle = false,
        placeholderMaxLines = 1
    )
}

internal data class SearchChromeVisualSpec(
    val inputHeightDp: Int,
    val inputCornerRadiusDp: Int,
    val actionContainerCornerRadiusDp: Int,
    val useFilledSearchAction: Boolean,
    val suggestionContainerCornerRadiusDp: Int
)

internal fun resolveSearchChromeVisualSpec(
    uiPreset: UiPreset
): SearchChromeVisualSpec {
    return if (uiPreset == UiPreset.MD3) {
        SearchChromeVisualSpec(
            inputHeightDp = 48,
            inputCornerRadiusDp = 28,
            actionContainerCornerRadiusDp = 18,
            useFilledSearchAction = true,
            suggestionContainerCornerRadiusDp = 20
        )
    } else {
        SearchChromeVisualSpec(
            inputHeightDp = 42,
            inputCornerRadiusDp = 50,
            actionContainerCornerRadiusDp = 18,
            useFilledSearchAction = false,
            suggestionContainerCornerRadiusDp = 12
        )
    }
}

internal data class SearchHomeContentMotionSpec(
    val fadeInDurationMillis: Int,
    val fadeOutDurationMillis: Int,
    val sizeTransformDurationMillis: Int,
    val enterFromTop: Boolean,
    val exitTowardTop: Boolean,
    val enterOffsetDp: Int,
    val exitOffsetDp: Int
)

internal fun resolveSearchHomeContentMotionSpec(
    reducedMotion: Boolean
): SearchHomeContentMotionSpec {
    return if (reducedMotion) {
        SearchHomeContentMotionSpec(
            fadeInDurationMillis = 90,
            fadeOutDurationMillis = 80,
            sizeTransformDurationMillis = 120,
            enterFromTop = true,
            exitTowardTop = true,
            enterOffsetDp = 0,
            exitOffsetDp = 0
        )
    } else {
        SearchHomeContentMotionSpec(
            fadeInDurationMillis = 320,
            fadeOutDurationMillis = 220,
            sizeTransformDurationMillis = 380,
            enterFromTop = true,
            exitTowardTop = true,
            enterOffsetDp = 18,
            exitOffsetDp = 14
        )
    }
}

internal fun shouldApplyInitialSearchKeyword(
    initialKeyword: String,
    currentQuery: String,
    showResults: Boolean
): Boolean {
    val normalizedKeyword = initialKeyword.trim()
    if (normalizedKeyword.isBlank()) return false
    return normalizedKeyword != currentQuery || !showResults
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    userFace: String = "",
    initialKeyword: String = "",
    onInitialKeywordConsumed: (String) -> Unit = {},
    onBack: () -> Unit,
    onVideoClick: (String, Long) -> Unit,
    onUpClick: (Long) -> Unit,  //  点击UP主跳转到空间
    onBangumiClick: (Long) -> Unit, //  点击番剧/影视跳转详情
    onLiveClick: (Long, String, String) -> Unit, // [新增] 直播点击
    onArticleClick: (Long, String) -> Unit,
    onAvatarClick: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val searchChromeSpec = remember(uiPreset) { resolveSearchChromeVisualSpec(uiPreset) }
    val state by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    val searchLayoutPolicy = remember(configuration.screenWidthDp) {
        resolveSearchLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    
    //  自动聚焦搜索框
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // 1. 滚动状态监听 (用于列表)
    val historyListState = rememberLazyListState()
    val resultGridState = rememberLazyGridState()
    val resultListState = rememberLazyListState()

    // 每次新搜索（换关键词、切类型、改筛选项）都回到列表顶部
    // searchVersion 由 ViewModel.search() 自增，作为唯一的触发信号
    LaunchedEffect(state.searchVersion) {
        if (state.searchVersion > 0) {
            resultGridState.scrollToItem(0)
            resultListState.scrollToItem(0)
        }
    }

    // ✨ Haze State
    val hazeState = rememberRecoverableHazeState()

    // 2. 顶部避让高度计算
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val topBarHeight = 64.dp // 搜索栏高度
    val contentTopPadding = statusBarHeight + topBarHeight
    
    //  读取动画设置开关
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardAnimationEnabled by SettingsManager.getCardAnimationEnabled(context).collectAsState(initial = true)
    val hotSearchEnabled by SettingsManager.getSearchHotSectionEnabled(context).collectAsState(initial = true)
    val liquidGlassEnabled by SettingsManager.getLiquidGlassEnabled(context).collectAsState(initial = true)
    val headerBlurEnabled by SettingsManager.getHeaderBlurEnabled(context).collectAsState(initial = true)
    val bottomBarBlurEnabled by SettingsManager.getBottomBarBlurEnabled(context).collectAsState(initial = true)
    val showHomeCoverGlassBadges by SettingsManager.getHomeCoverGlassBadgesVisible(context).collectAsState(initial = true)
    val showHomeInfoGlassBadges by SettingsManager.getHomeInfoGlassBadgesVisible(context).collectAsState(initial = true)
    val cardMotionTier = resolveEffectiveMotionTier(
        baseTier = deviceUiProfile.motionTier,
        animationEnabled = cardAnimationEnabled
    )
    val searchCardBlurEnabled = remember(headerBlurEnabled, bottomBarBlurEnabled) {
        resolveSearchCardBlurEnabled(
            headerBlurEnabled = headerBlurEnabled,
            bottomBarBlurEnabled = bottomBarBlurEnabled
        )
    }
    val videoCardAppearance = remember(
        liquidGlassEnabled,
        searchCardBlurEnabled,
        showHomeCoverGlassBadges,
        showHomeInfoGlassBadges
    ) {
        resolveSearchVideoCardAppearance(
            liquidGlassEnabled = liquidGlassEnabled,
            blurEnabled = searchCardBlurEnabled,
            showHomeCoverGlassBadges = showHomeCoverGlassBadges,
            showHomeInfoGlassBadges = showHomeInfoGlassBadges
        )
    }
    val genericResultCardAppearance = remember(liquidGlassEnabled) {
        resolveSearchResultCardAppearance(
            liquidGlassEnabled = liquidGlassEnabled
        )
    }
    val cardTransitionEnabled by SettingsManager.getCardTransitionEnabled(context).collectAsState(initial = false)
    val isSearchResultsScrolling by remember(historyListState, resultGridState, resultListState) {
        derivedStateOf {
            historyListState.isScrollInProgress ||
                resultGridState.isScrollInProgress ||
                resultListState.isScrollInProgress
        }
    }
    val searchMotionBudget by remember(state.query, state.isSearching, isSearchResultsScrolling) {
        derivedStateOf {
            resolveSearchMotionBudget(
                hasQuery = state.query.isNotBlank(),
                isSearching = state.isSearching,
                isScrolling = isSearchResultsScrolling
            )
        }
    }
    val searchHazeEnabled = shouldEnableSearchHazeSource(
        isSearching = state.isSearching
    )
    val effectiveCardTransitionEnabled =
        cardTransitionEnabled && searchMotionBudget == SearchMotionBudget.FULL
    val forceLowBudgetSearchHeaderBlur = remember(state.isSearching, isSearchResultsScrolling) {
        shouldForceLowBudgetSearchHeaderBlur(
            isSearching = state.isSearching,
            isScrollingResults = isSearchResultsScrolling
        )
    }
    val showHotSectionHeader by remember(state.hotList, hotSearchEnabled) {
        derivedStateOf {
            shouldShowSearchHotHeader(
                hotItemCount = state.hotList.size,
                hotSearchEnabled = hotSearchEnabled
            )
        }
    }
    val searchHomeMotionSpec = remember(searchMotionBudget) {
        resolveSearchHomeContentMotionSpec(
            reducedMotion = searchMotionBudget == SearchMotionBudget.REDUCED
        )
    }
    val emptyStateCopy = remember(state.emptyStateReason, state.searchType) {
        if (state.emptyStateReason == SearchEmptyStateReason.NONE) {
            null
        } else {
            resolveSearchEmptyStateCopy(
                reason = state.emptyStateReason,
                searchType = state.searchType
            )
        }
    }
    
    //  [埋点] 页面浏览追踪
    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("SearchScreen")
    }

    LaunchedEffect(initialKeyword) {
        val normalizedKeyword = initialKeyword.trim()
        if (normalizedKeyword.isNotBlank()) {
            if (shouldApplyInitialSearchKeyword(normalizedKeyword, state.query, state.showResults)) {
                viewModel.onQueryChange(normalizedKeyword)
                viewModel.search(normalizedKeyword)
            }
            onInitialKeywordConsumed(normalizedKeyword)
        }
    }

    val resultBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 16.dp
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        //  移除 bottomBar，搜索栏现在位于顶部 Box 中
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // --- 列表内容层 ---
            if (state.showResults) {
                if (state.isSearching) {
                    //  使用 Lottie 加载动画
                    LoadingAnimation(
                        modifier = Modifier.align(Alignment.Center),
                        size = 80.dp,
                        text = "搜索中..."
                    )
                } else if (state.error != null) {
                    Text(
                        text = state.error ?: "未知错误",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        //  搜索彩蛋消息横幅
                        val easterEggMsg = state.easterEggMessage
                        if (easterEggMsg != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = easterEggMsg,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        //  根据搜索类型显示不同结果
                        when (state.searchType) {
                            com.android.purebilibili.data.model.response.SearchType.VIDEO -> {
                                // 视频搜索结果
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = searchLayoutPolicy.resultGridMinItemWidthDp.dp),
                                    state = resultGridState,
                                    contentPadding = PaddingValues(
                                        top = contentTopPadding + 8.dp,
                                        bottom = resultBottomPadding,
                                        start = searchLayoutPolicy.resultHorizontalPaddingDp.dp,
                                        end = searchLayoutPolicy.resultHorizontalPaddingDp.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(searchLayoutPolicy.resultGridSpacingDp.dp),
                                    verticalArrangement = Arrangement.spacedBy(searchLayoutPolicy.resultGridSpacingDp.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier)
                        ) {
                                    // ✨ Filter Bar inside Grid
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                         SearchFilterBar(
                                            currentType = state.searchType,
                                            currentOrder = state.searchOrder,
                                            currentDuration = state.searchDuration,
                                            currentVideoTid = state.videoTid,
                                            currentUpOrder = state.upOrder,
                                            currentUpOrderSort = state.upOrderSort,
                                            currentUpUserType = state.upUserType,
                                            currentLiveOrder = state.liveOrder,
                                            onTypeChange = { viewModel.setSearchType(it) },
                                            onOrderChange = { viewModel.setSearchOrder(it) },
                                            onDurationChange = { viewModel.setSearchDuration(it) },
                                            onVideoTidChange = { viewModel.setVideoTid(it) },
                                            onUpOrderChange = { viewModel.setUpOrder(it) },
                                            onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                            onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                            onLiveOrderChange = { viewModel.setLiveOrder(it) }
                                        )
                                    }
                                
                                itemsIndexed(state.searchResults) { index, video ->
                                        ElegantVideoCard(
                                            video = video,
                                            index = index,
                                            animationEnabled = cardAnimationEnabled,
                                            motionTier = cardMotionTier,
                                            transitionEnabled = effectiveCardTransitionEnabled,
                                            showPublishTime = true,
                                            glassEnabled = videoCardAppearance.glassEnabled,
                                            blurEnabled = videoCardAppearance.blurEnabled,
                                            showCoverGlassBadges = videoCardAppearance.showCoverGlassBadges,
                                            showInfoGlassBadges = videoCardAppearance.showInfoGlassBadges,
                                            modifier = Modifier,
                                            //  [交互优化] 传递 onWatchLater 用于显示菜单选项
                                            onWatchLater = { viewModel.addToWatchLater(video.bvid, video.id) },
                                            onClick = { bvid, _ -> onVideoClick(bvid, 0) }
                                        )
                                        
                                        //  [新增] 无限滚动触发：当滚动到最后几个 item 时加载更多
                                        if (index == state.searchResults.size - 3 && state.hasMoreResults && !state.isLoadingMore) {
                                            LaunchedEffect(state.currentPage) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }
                                    
                                    // [新增] 空状态提示 (提示可能被屏蔽)
                                    if (!state.isSearching && state.searchResults.isEmpty() && state.error == null && emptyStateCopy != null) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = emptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = emptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    //  [新增] 加载更多指示器
                                    if (state.isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                    
                                    //  [新增] 已加载全部提示
                                    if (!state.hasMoreResults && state.searchResults.isNotEmpty() && !state.isLoadingMore) {
                                        item {
                                            Text(
                                                text = "已加载全部 ${state.searchResults.size} 条结果",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.UP -> {
                                //  UP主搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = resultListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier)
                                ) {
                                    item {
                                        SearchFilterBar(
                                            currentType = state.searchType,
                                            currentOrder = state.searchOrder,
                                            currentDuration = state.searchDuration,
                                            currentVideoTid = state.videoTid,
                                            currentUpOrder = state.upOrder,
                                            currentUpOrderSort = state.upOrderSort,
                                            currentUpUserType = state.upUserType,
                                            currentLiveOrder = state.liveOrder,
                                            onTypeChange = { viewModel.setSearchType(it) },
                                            onOrderChange = { viewModel.setSearchOrder(it) },
                                            onDurationChange = { viewModel.setSearchDuration(it) },
                                            onVideoTidChange = { viewModel.setVideoTid(it) },
                                            onUpOrderChange = { viewModel.setUpOrder(it) },
                                            onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                            onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                            onLiveOrderChange = { viewModel.setLiveOrder(it) }
                                        )
                                    }

                                    itemsIndexed(state.upResults) { index, upItem ->
                                        UpSearchResultCard(
                                            upItem = upItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = { onUpClick(upItem.mid) }
                                        )
                                        if (index == state.upResults.size - 3 && state.hasMoreResults && !state.isLoadingMore) {
                                            LaunchedEffect(state.currentPage, state.searchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }
                                    
                                     // [新增] 空状态提示
                                    if (!state.isSearching && state.upResults.isEmpty() && state.error == null && emptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = emptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = emptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (state.isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.BANGUMI,
                            com.android.purebilibili.data.model.response.SearchType.MEDIA_FT -> {
                                //  番剧/影视搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = resultListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier)
                                ) {
                                    item {
                                        SearchFilterBar(
                                            currentType = state.searchType,
                                            currentOrder = state.searchOrder,
                                            currentDuration = state.searchDuration,
                                            currentVideoTid = state.videoTid,
                                            currentUpOrder = state.upOrder,
                                            currentUpOrderSort = state.upOrderSort,
                                            currentUpUserType = state.upUserType,
                                            currentLiveOrder = state.liveOrder,
                                            onTypeChange = { viewModel.setSearchType(it) },
                                            onOrderChange = { viewModel.setSearchOrder(it) },
                                            onDurationChange = { viewModel.setSearchDuration(it) },
                                            onVideoTidChange = { viewModel.setVideoTid(it) },
                                            onUpOrderChange = { viewModel.setUpOrder(it) },
                                            onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                            onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                            onLiveOrderChange = { viewModel.setLiveOrder(it) }
                                        )
                                    }

                                    itemsIndexed(state.bangumiResults) { index, bangumiItem ->
                                        BangumiSearchResultCard(
                                            item = bangumiItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = {
                                                if (bangumiItem.seasonId > 0) {
                                                    onBangumiClick(bangumiItem.seasonId)
                                                }
                                            }
                                        )
                                        if (index == state.bangumiResults.size - 3 && state.hasMoreResults && !state.isLoadingMore) {
                                            LaunchedEffect(state.currentPage, state.searchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (state.isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.LIVE -> {
                                //  直播搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = resultListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier)
                                ) {
                                    item {
                                        SearchFilterBar(
                                            currentType = state.searchType,
                                            currentOrder = state.searchOrder,
                                            currentDuration = state.searchDuration,
                                            currentVideoTid = state.videoTid,
                                            currentUpOrder = state.upOrder,
                                            currentUpOrderSort = state.upOrderSort,
                                            currentUpUserType = state.upUserType,
                                            currentLiveOrder = state.liveOrder,
                                            onTypeChange = { viewModel.setSearchType(it) },
                                            onOrderChange = { viewModel.setSearchOrder(it) },
                                            onDurationChange = { viewModel.setSearchDuration(it) },
                                            onVideoTidChange = { viewModel.setVideoTid(it) },
                                            onUpOrderChange = { viewModel.setUpOrder(it) },
                                            onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                            onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                            onLiveOrderChange = { viewModel.setLiveOrder(it) }
                                        )
                                    }

                                    itemsIndexed(state.liveResults) { index, liveItem ->
                                        LiveSearchResultCard(
                                            item = liveItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = { onLiveClick(liveItem.roomid, liveItem.title, liveItem.uname) }
                                        )
                                        if (index == state.liveResults.size - 3 && state.hasMoreResults && !state.isLoadingMore) {
                                            LaunchedEffect(state.currentPage, state.searchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }
                                    
                                    // [新增] 空状态提示
                                    if (!state.isSearching && state.liveResults.isEmpty() && state.error == null && emptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = emptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = emptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (state.isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.ARTICLE -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = resultListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier)
                                ) {
                                    item {
                                        SearchFilterBar(
                                            currentType = state.searchType,
                                            currentOrder = state.searchOrder,
                                            currentDuration = state.searchDuration,
                                            currentVideoTid = state.videoTid,
                                            currentUpOrder = state.upOrder,
                                            currentUpOrderSort = state.upOrderSort,
                                            currentUpUserType = state.upUserType,
                                            currentLiveOrder = state.liveOrder,
                                            onTypeChange = { viewModel.setSearchType(it) },
                                            onOrderChange = { viewModel.setSearchOrder(it) },
                                            onDurationChange = { viewModel.setSearchDuration(it) },
                                            onVideoTidChange = { viewModel.setVideoTid(it) },
                                            onUpOrderChange = { viewModel.setUpOrder(it) },
                                            onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                            onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                            onLiveOrderChange = { viewModel.setLiveOrder(it) }
                                        )
                                    }

                                    itemsIndexed(state.articleResults) { index, articleItem ->
                                        ArticleSearchResultCard(
                                            item = articleItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = { onArticleClick(articleItem.id, articleItem.title) }
                                        )
                                        if (index == state.articleResults.size - 3 && state.hasMoreResults && !state.isLoadingMore) {
                                            LaunchedEffect(state.currentPage, state.searchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (!state.isSearching && state.articleResults.isEmpty() && state.error == null && emptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = emptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = emptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (state.isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 判断是否使用分栏布局 (平板横屏)
                val useSplitLayout = shouldUseSearchSplitLayout(
                    widthDp = configuration.screenWidthDp
                )

                val enterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                val exitEasing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
                val enterOffsetPx = with(density) { searchHomeMotionSpec.enterOffsetDp.dp.roundToPx() }
                val exitOffsetPx = with(density) { searchHomeMotionSpec.exitOffsetDp.dp.roundToPx() }

                AnimatedContent(
                targetState = showHotSectionHeader,
                    transitionSpec = {
                        val resolvedEnterOffset = if (searchHomeMotionSpec.enterFromTop) {
                            -enterOffsetPx
                        } else {
                            enterOffsetPx
                        }
                        val resolvedExitOffset = if (searchHomeMotionSpec.exitTowardTop) {
                            -exitOffsetPx
                        } else {
                            exitOffsetPx
                        }
                        val targetEnter = fadeIn(
                            animationSpec = tween(
                                durationMillis = searchHomeMotionSpec.fadeInDurationMillis,
                                easing = enterEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = searchHomeMotionSpec.sizeTransformDurationMillis,
                                easing = enterEasing
                            ),
                            initialOffsetY = { resolvedEnterOffset }
                        )
                        val initialExit = fadeOut(
                            animationSpec = tween(
                                durationMillis = searchHomeMotionSpec.fadeOutDurationMillis,
                                easing = exitEasing
                            )
                        ) + slideOutVertically(
                            animationSpec = tween(
                                durationMillis = searchHomeMotionSpec.fadeOutDurationMillis,
                                easing = exitEasing
                            ),
                            targetOffsetY = { resolvedExitOffset }
                        )
                        ContentTransform(
                            targetContentEnter = targetEnter,
                            initialContentExit = initialExit,
                            sizeTransform = SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    tween(
                                        durationMillis = searchHomeMotionSpec.sizeTransformDurationMillis,
                                        easing = enterEasing
                                    )
                                }
                            )
                        )
                    },
                    label = "searchHomeContent"
                ) { targetShowHotSection ->
                    if (useSplitLayout && targetShowHotSection) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(searchLayoutPolicy.leftPaneWeight)
                                    .fillMaxHeight(),
                                contentPadding = PaddingValues(
                                    top = contentTopPadding + 16.dp,
                                    bottom = resultBottomPadding,
                                    start = searchLayoutPolicy.splitOuterPaddingDp.dp,
                                    end = searchLayoutPolicy.splitInnerGapDp.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                item {
                                    SearchHistorySection(
                                        historyList = state.historyList,
                                        onItemClick = {
                                            viewModel.search(it)
                                            keyboardController?.hide()
                                        },
                                        onClear = { viewModel.clearHistory() },
                                        onDelete = { viewModel.deleteHistory(it) }
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .weight(searchLayoutPolicy.rightPaneWeight)
                                    .fillMaxHeight(),
                                contentPadding = PaddingValues(
                                    top = contentTopPadding + 16.dp,
                                    bottom = resultBottomPadding,
                                    start = searchLayoutPolicy.splitInnerGapDp.dp,
                                    end = searchLayoutPolicy.splitOuterPaddingDp.dp
                                )
                            ) {
                                item {
                                    SearchHotSection(
                                        hotList = state.hotList,
                                        hotSearchEnabled = hotSearchEnabled,
                                        hotColumns = searchLayoutPolicy.hotSearchColumns,
                                        onToggleHotSearch = {
                                            scope.launch {
                                                SettingsManager.setSearchHotSectionEnabled(context, !hotSearchEnabled)
                                            }
                                        },
                                        onItemClick = {
                                            viewModel.search(it)
                                            keyboardController?.hide()
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .responsiveContentWidth()
                                .then(if (searchHazeEnabled) Modifier.hazeSource(state = hazeState) else Modifier),
                            state = historyListState,
                            contentPadding = PaddingValues(
                                top = contentTopPadding + 16.dp,
                                bottom = resultBottomPadding,
                                start = searchLayoutPolicy.resultHorizontalPaddingDp.dp,
                                end = searchLayoutPolicy.resultHorizontalPaddingDp.dp
                            )
                        ) {
                            if (targetShowHotSection) {
                                item {
                                    SearchHotSection(
                                        hotList = state.hotList,
                                        hotSearchEnabled = hotSearchEnabled,
                                        hotColumns = searchLayoutPolicy.hotSearchColumns,
                                        onToggleHotSearch = {
                                            scope.launch {
                                                SettingsManager.setSearchHotSectionEnabled(context, !hotSearchEnabled)
                                            }
                                        },
                                        onItemClick = {
                                            viewModel.search(it)
                                            keyboardController?.hide()
                                        }
                                    )
                                }
                            }

                            item {
                                SearchHistorySection(
                                    historyList = state.historyList,
                                    onItemClick = {
                                        viewModel.search(it)
                                        keyboardController?.hide()
                                    },
                                    onClear = { viewModel.clearHistory() },
                                    onDelete = { viewModel.deleteHistory(it) }
                                )
                            }
                        }
                    }
                }
            }

            // ---  顶部搜索栏 (常驻顶部) ---
            SearchTopBar(
                query = state.query,
                onBack = onBack,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = {
                    viewModel.search(it)
                    keyboardController?.hide()
                },
                onClearQuery = { viewModel.onQueryChange("") },
                focusRequester = searchFocusRequester,  //  传递 focusRequester
                placeholder = state.defaultSearchHint.ifBlank { "搜索视频、UP主..." },
                reducedMotionBudget = searchMotionBudget == SearchMotionBudget.REDUCED,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(
                        if (searchHazeEnabled) {
                            Modifier.unifiedBlur(
                                hazeState = hazeState,
                                surfaceType = com.android.purebilibili.core.ui.blur.BlurSurfaceType.HEADER,
                                isScrolling = isSearchResultsScrolling,
                                forceLowBudget = forceLowBudgetSearchHeaderBlur
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                        }
                    )
            )
            
            // ---  搜索建议下拉列表 ---
            if (state.suggestions.isNotEmpty() && state.query.isNotEmpty() && !state.showResults) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = contentTopPadding + 4.dp)
                        .padding(horizontal = searchLayoutPolicy.resultHorizontalPaddingDp.dp)
                        .align(Alignment.TopCenter)
                        .responsiveContentWidth(),
                    shape = RoundedCornerShape(searchChromeSpec.suggestionContainerCornerRadiusDp.dp),
                    shadowElevation = 8.dp,
                    color = if (uiPreset == UiPreset.MD3) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        state.suggestions.forEachIndexed { index, suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusable()
                                    .clickable {
                                        viewModel.search(suggestion)
                                        keyboardController?.hide()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    rememberAppSearchIcon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = suggestion,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

//  新设计的顶部搜索栏 (含 Focus 高亮动画)
@Composable
fun SearchTopBar(
    query: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit,
    placeholder: String = "搜索视频、UP主...",
    focusRequester: androidx.compose.ui.focus.FocusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
    reducedMotionBudget: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiPreset = LocalUiPreset.current
    val layoutSpec = remember { resolveSearchTopBarLayoutSpec() }
    val chromeSpec = remember(uiPreset) { resolveSearchChromeVisualSpec(uiPreset) }
    val backIcon = rememberAppBackIcon()
    val searchIcon = rememberAppSearchIcon()
    val clearIcon = rememberAppClearIcon()
    //  Focus 状态追踪
    var isFocused by remember { mutableStateOf(false) }
    
    //  自动聚焦并弹出键盘
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            kotlinx.coroutines.delay(100)  // 等待页面加载完成
            focusRequester.requestFocus()
        }
    }
    
    //  边框宽度动画
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 0.dp,
        animationSpec = if (reducedMotionBudget) snap() else tween(durationMillis = 200),
        label = "borderWidth"
    )
    
    //  搜索图标颜色动画
    val searchIconColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = if (reducedMotionBudget) snap() else tween(durationMillis = 200),
        label = "iconColor"
    )
    val backLabel = stringResource(R.string.common_back)
    val searchLabel = stringResource(R.string.common_search)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            Row(
                modifier = Modifier
                    .responsiveContentWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).asPaddingValues()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        backIcon,
                        contentDescription = backLabel,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                //  搜索输入框 (带 Focus 边框动画)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(chromeSpec.inputHeightDp.dp)
                        .clip(RoundedCornerShape(chromeSpec.inputCornerRadiusDp.dp))
                        .border(
                            width = borderWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(chromeSpec.inputCornerRadiusDp.dp)
                        )
                        .background(
                            if (uiPreset == UiPreset.MD3) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        searchIcon,
                        null,
                        tint = searchIconColor,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)  //  应用 focusRequester
                            .onFocusChanged { isFocused = it.isFocused },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                            fontSize = 15.sp
                                        ),
                                        maxLines = layoutSpec.placeholderMaxLines,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClearQuery,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                clearIcon,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (chromeSpec.useFilledSearchAction) {
                    FilledTonalButton(
                        onClick = { onSearch(query) },
                        enabled = query.isNotEmpty(),
                        shape = RoundedCornerShape(chromeSpec.actionContainerCornerRadiusDp.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(searchLabel, fontSize = 15.sp)
                    }
                } else {
                    TextButton(
                        onClick = { onSearch(query) },
                        enabled = query.isNotEmpty()
                    ) {
                        Text(
                            searchLabel,
                            color = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

//  气泡化历史记录组件
@Composable
fun HistoryChip(
    keyword: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val historyIcon = rememberAppHistoryIcon()
    val clearIcon = rememberAppClearIcon()
    val deleteLabel = stringResource(R.string.common_delete)
    val chromeSpec = remember(uiPreset) { resolveSearchChromeVisualSpec(uiPreset) }
    Surface(
        onClick = onClick,
        color = if (uiPreset == UiPreset.MD3) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        shape = RoundedCornerShape(chromeSpec.actionContainerCornerRadiusDp.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                historyIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = keyword,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    clearIcon,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// 保留旧版 HistoryItem 用于兼容 (可选保留)
@Composable
fun HistoryItem(
    history: SearchHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val historyIcon = rememberAppHistoryIcon()
    val clearIcon = rememberAppClearIcon()
    val deleteLabel = stringResource(R.string.common_delete)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(historyIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = history.keyword, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(clearIcon, contentDescription = deleteLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
}

/**
 *  快捷分类入口
 */
@Composable
fun QuickCategory(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Text(text = emoji, fontSize = 24.sp)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ============================================================================================
// 📱 搜索模块组件提取 (用于平板适配)
// ============================================================================================

/**
 * 💎 搜索发现 / 推荐板块
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchDiscoverySection(
    title: String,
    list: List<String>,
    onItemClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column {
        //  搜索发现 / 个性化推荐
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "💎",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    title, //  使用动态标题
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 刷新按钮
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRefresh() }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "换一换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 动态发现内容
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            list.forEach { keyword -> //  使用动态列表
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onItemClick(keyword) }
                ) {
                    Text(
                        keyword,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 🔥 热门搜索板块
 */
@Composable
fun SearchHotSection(
    hotList: List<HotItem>,
    hotSearchEnabled: Boolean,
    hotColumns: Int = 2,
    onToggleHotSearch: () -> Unit,
    onItemClick: (String) -> Unit
) {
    val showHotBody = shouldShowSearchHotSection(
        hotItemCount = hotList.size,
        hotSearchEnabled = hotSearchEnabled
    )
    if (shouldShowSearchHotHeader(hotList.size, hotSearchEnabled)) {
        Column {
            //  热搜标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "", // 🔥
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "热门搜索",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    onClick = onToggleHotSearch,
                    shape = RoundedCornerShape(16.dp),
                    color = if (hotSearchEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    }
                ) {
                    Text(
                        text = if (hotSearchEnabled) "热搜开" else "热搜关",
                        color = if (hotSearchEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (showHotBody) {
                //  热搜列表 (动态布局)
                val safeColumns = hotColumns.coerceAtLeast(1)
                val displayList = hotList.take(20)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    displayList.chunked(safeColumns).forEachIndexed { rowIndex, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEachIndexed { indexInRow, hotItem ->
                                val globalIndex = rowIndex * safeColumns + indexInRow
                                val isTop3 = globalIndex < 3

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onItemClick(hotItem.keyword) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 排名序号
                                    Text(
                                        text = "${globalIndex + 1}",
                                        fontSize = 14.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.width(24.dp)
                                    )

                                    // 标题
                                    Text(
                                        text = hotItem.show_name,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // 如果不足一行，补空位占位
                            if (rowItems.size < safeColumns) {
                                Spacer(modifier = Modifier.weight((safeColumns - rowItems.size).toFloat()))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 🕒 历史记录板块
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHistorySection(
    historyList: List<SearchHistory>,
    onItemClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: (SearchHistory) -> Unit
) {
    if (historyList.isNotEmpty()) {
        val historyTitle = stringResource(R.string.search_history_title)
        val clearLabel = stringResource(R.string.common_clear)
        Column {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    historyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onClear) {
                    Text(clearLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            //  气泡化历史记录
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                historyList.forEach { history ->
                    HistoryChip(
                        keyword = history.keyword,
                        onClick = { onItemClick(history.keyword) },
                        onDelete = { onDelete(history) }
                    )
                }
            }
        }
    }
}


/**
 *  搜索筛选条件栏 (含类型切换)
 */
@Composable
fun SearchFilterBar(
    currentType: com.android.purebilibili.data.model.response.SearchType,
    currentOrder: SearchOrder,
    currentDuration: SearchDuration,
    currentVideoTid: Int,
    currentUpOrder: SearchUpOrder,
    currentUpOrderSort: SearchOrderSort,
    currentUpUserType: SearchUserType,
    currentLiveOrder: SearchLiveOrder,
    onTypeChange: (com.android.purebilibili.data.model.response.SearchType) -> Unit,
    onOrderChange: (SearchOrder) -> Unit,
    onDurationChange: (SearchDuration) -> Unit,
    onVideoTidChange: (Int) -> Unit,
    onUpOrderChange: (SearchUpOrder) -> Unit,
    onUpOrderSortChange: (SearchOrderSort) -> Unit,
    onUpUserTypeChange: (SearchUserType) -> Unit,
    onLiveOrderChange: (SearchLiveOrder) -> Unit
) {
    var showOrderMenu by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }
    var showVideoTidMenu by remember { mutableStateOf(false) }
    var showUpOrderMenu by remember { mutableStateOf(false) }
    var showUpOrderSortMenu by remember { mutableStateOf(false) }
    var showUpUserTypeMenu by remember { mutableStateOf(false) }
    var showLiveOrderMenu by remember { mutableStateOf(false) }

    val videoTidOptions = remember {
        listOf(
            0 to "全部分区",
            1 to "动画",
            3 to "音乐",
            4 to "游戏",
            5 to "娱乐",
            36 to "科技",
            119 to "鬼畜",
            160 to "生活",
            181 to "影视"
        )
    }
    val selectedVideoTidName = remember(currentVideoTid, videoTidOptions) {
        videoTidOptions.find { it.first == currentVideoTid }?.second ?: "分区$currentVideoTid"
    }
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        //  搜索类型切换 Tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                com.android.purebilibili.data.model.response.SearchType.VIDEO to "视频",
                com.android.purebilibili.data.model.response.SearchType.UP to "UP主",
                com.android.purebilibili.data.model.response.SearchType.BANGUMI to "番剧",
                com.android.purebilibili.data.model.response.SearchType.MEDIA_FT to "影视",
                com.android.purebilibili.data.model.response.SearchType.LIVE to "直播",
                com.android.purebilibili.data.model.response.SearchType.ARTICLE to "专栏"
            ).forEach { (type, label) ->
                val isSelected = currentType == type
                val chipColors = resolveSearchSelectionChipColors(
                    isSelected = isSelected,
                    colorScheme = MaterialTheme.colorScheme
                )
                Surface(
                    onClick = { onTypeChange(type) },
                    color = chipColors.backgroundColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = chipColors.textColor,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        
        //  只有视频类型才显示排序和时长筛选
        if (currentType == com.android.purebilibili.data.model.response.SearchType.VIDEO) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    FilterMenuChip(
                        text = currentOrder.displayName,
                        highlighted = currentOrder != SearchOrder.TOTALRANK,
                        onClick = { showOrderMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOrderMenu,
                        onDismissRequest = { showOrderMenu = false }
                    ) {
                        SearchOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName) },
                                onClick = {
                                    onOrderChange(order)
                                    showOrderMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    FilterMenuChip(
                        text = currentDuration.displayName,
                        highlighted = currentDuration != SearchDuration.ALL,
                        onClick = { showDurationMenu = true }
                    )
                    DropdownMenu(
                        expanded = showDurationMenu,
                        onDismissRequest = { showDurationMenu = false }
                    ) {
                        SearchDuration.entries.forEach { duration ->
                            DropdownMenuItem(
                                text = { Text(duration.displayName) },
                                onClick = {
                                    onDurationChange(duration)
                                    showDurationMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    FilterMenuChip(
                        text = selectedVideoTidName,
                        highlighted = currentVideoTid != 0,
                        onClick = { showVideoTidMenu = true }
                    )
                    DropdownMenu(
                        expanded = showVideoTidMenu,
                        onDismissRequest = { showVideoTidMenu = false }
                    ) {
                        videoTidOptions.forEach { (tid, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onVideoTidChange(tid)
                                    showVideoTidMenu = false
                                }
                            )
                        }
                    }
                }
            }
        } else if (currentType == com.android.purebilibili.data.model.response.SearchType.UP) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    FilterMenuChip(
                        text = currentUpOrder.displayName,
                        highlighted = currentUpOrder != SearchUpOrder.DEFAULT,
                        onClick = { showUpOrderMenu = true }
                    )
                    DropdownMenu(
                        expanded = showUpOrderMenu,
                        onDismissRequest = { showUpOrderMenu = false }
                    ) {
                        SearchUpOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName) },
                                onClick = {
                                    onUpOrderChange(order)
                                    showUpOrderMenu = false
                                }
                            )
                        }
                    }
                }

                if (currentUpOrder != SearchUpOrder.DEFAULT) {
                    Box {
                        FilterMenuChip(
                            text = currentUpOrderSort.displayName,
                            highlighted = true,
                            onClick = { showUpOrderSortMenu = true }
                        )
                        DropdownMenu(
                            expanded = showUpOrderSortMenu,
                            onDismissRequest = { showUpOrderSortMenu = false }
                        ) {
                            SearchOrderSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.displayName) },
                                    onClick = {
                                        onUpOrderSortChange(sort)
                                        showUpOrderSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box {
                    FilterMenuChip(
                        text = currentUpUserType.displayName,
                        highlighted = currentUpUserType != SearchUserType.ALL,
                        onClick = { showUpUserTypeMenu = true }
                    )
                    DropdownMenu(
                        expanded = showUpUserTypeMenu,
                        onDismissRequest = { showUpUserTypeMenu = false }
                    ) {
                        SearchUserType.entries.forEach { userType ->
                            DropdownMenuItem(
                                text = { Text(userType.displayName) },
                                onClick = {
                                    onUpUserTypeChange(userType)
                                    showUpUserTypeMenu = false
                                }
                            )
                        }
                    }
                }
            }
        } else if (currentType == com.android.purebilibili.data.model.response.SearchType.LIVE) {
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                FilterMenuChip(
                    text = currentLiveOrder.displayName,
                    highlighted = currentLiveOrder != SearchLiveOrder.ONLINE,
                    onClick = { showLiveOrderMenu = true }
                )
                DropdownMenu(
                    expanded = showLiveOrderMenu,
                    onDismissRequest = { showLiveOrderMenu = false }
                ) {
                    SearchLiveOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.displayName) },
                            onClick = {
                                onLiveOrderChange(order)
                                showLiveOrderMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterMenuChip(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val chevronIcon = rememberAppChevronDownIcon()
    Surface(
        onClick = onClick,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            if (uiPreset == UiPreset.MD3) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        },
        shape = RoundedCornerShape(if (uiPreset == UiPreset.MD3) 14.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                chevronIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchResultCardSurface(
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = appearance.containerAlpha),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = appearance.tonalElevationDp.dp,
        shadowElevation = appearance.shadowElevationDp.dp,
        border = if (appearance.borderAlpha > 0f) {
            androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = appearance.borderAlpha))
        } else {
            null
        }
    ) {
        content()
    }
}

/**
 *  搜索结果卡片 (显示发布时间)
 */
@Composable
fun SearchResultCard(
    video: VideoItem,
    index: Int,
    onClick: (String) -> Unit
) {
    val coverUrl = remember(video.bvid) {
        FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(video.bvid) }
            .padding(bottom = 8.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(150)
                    .size(480, 300)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 底部渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
            
            // 时长标签
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            
            // 播放量
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▶ ${FormatUtils.formatStat(video.stat.view.toLong())}",
                    color = Color.White,
                    fontSize = 11.sp
                )
                if (video.stat.danmaku > 0) {
                    Text(
                        text = "   ${FormatUtils.formatStat(video.stat.danmaku.toLong())}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 标题
        Text(
            text = video.title,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // UP主 + 发布时间
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpBadgeName(
                name = video.owner.name,
                leadingContent = if (video.owner.face.isNotBlank()) {
                    {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(FormatUtils.fixImageUrl(video.owner.face))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else null,
                nameStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                badgeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f, fill = false)
            )
            
            //  显示发布时间
            if (video.pubdate > 0) {
                Text(
                    text = " · ${FormatUtils.formatPublishTime(video.pubdate)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 *  UP主搜索结果卡片
 */
@Composable
internal fun UpSearchResultCard(
    upItem: com.android.purebilibili.data.model.response.SearchUpItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {

    val cleanedItem = remember(upItem.mid) { upItem.cleanupFields() }
    
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            val avatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = "up_avatar_${cleanedItem.mid}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        clipInOverlayDuringTransition = OverlayClip(CircleShape)
                    )
                }
            } else Modifier

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cleanedItem.upic)
                    .crossfade(true)
                    .build(),
                contentDescription = cleanedItem.uname,
                modifier = Modifier
                    .then(avatarModifier)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // UP主信息
            Column(modifier = Modifier.weight(1f)) {
                // 名称 + 认证标志
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cleanedItem.uname,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 认证标志
                    cleanedItem.official_verify?.let { verify ->
                        when (resolveSearchVerifyBadge(verify.type, verify.desc)) {
                            SearchVerifyBadge.NONE -> Unit
                            SearchVerifyBadge.PERSONAL,
                            SearchVerifyBadge.ORGANIZATION -> {
                                val isPersonal = resolveSearchVerifyBadge(verify.type, verify.desc) == SearchVerifyBadge.PERSONAL
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = if (isPersonal) Color(0xFFFFB300) else Color(0xFF2196F3),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isPersonal) "个人" else "机构",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 个性签名
                if (cleanedItem.usign.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanedItem.usign,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 粉丝数 + 视频数
                Row {
                    Text(
                        text = "粉丝 ${FormatUtils.formatStat(cleanedItem.fans.toLong())}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "视频 ${cleanedItem.videos}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 *  番剧搜索结果卡片
 */
@Composable
internal fun BangumiSearchResultCard(
    item: com.android.purebilibili.data.model.response.BangumiSearchItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 封面
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .width(80.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 番剧信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 类型 + 集数
                Row {
                    if (item.seasonTypeName.isNotBlank()) {
                        Text(
                            text = item.seasonTypeName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (item.indexShow.isNotBlank()) {
                        Text(
                            text = item.indexShow,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 评分
                item.mediaScore?.let { score ->
                    if (score.score > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⭐ ${score.score}",
                                fontSize = 12.sp,
                                color = Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${score.userCount}人评分",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                // 简介
                if (item.desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.desc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 *  直播搜索结果卡片
 */
@Composable
internal fun LiveSearchResultCard(
    item: com.android.purebilibili.data.model.response.LiveRoomSearchItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover.ifBlank { item.uface })
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                
                // 直播状态标签
                if (item.live_status == 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        color = Color(0xFFFF4081),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "直播中",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                
                // 在线人数
                if (item.online > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatStat(item.online.toLong()),
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 直播信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 主播名
                Text(
                    text = item.uname,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // 分区
                if (item.area_v2_name.isNotBlank()) {
                    Text(
                        text = "${item.area_v2_parent_name} · ${item.area_v2_name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArticleSearchResultCard(
    item: SearchArticleItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (item.imageUrls.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.buildSizedImageUrl(item.imageUrls.first(), width = 360, height = 240))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 112.dp, height = 74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metaLine = buildString {
                    val publishTime = FormatUtils.formatPublishTime(item.pubTime)
                    if (publishTime.isNotBlank()) {
                        append(publishTime)
                    }
                    if (item.categoryName.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.categoryName)
                    }
                }
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${FormatUtils.formatStat(item.view.toLong())}浏览 · ${FormatUtils.formatStat(item.reply.toLong())}评论 · ${FormatUtils.formatStat(item.like.toLong())}点赞",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
