package com.android.purebilibili.feature.list

import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.feature.home.GridPinchColumnHudPill
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import com.android.purebilibili.feature.home.resolveHomeFeedPinchColumnBounds
import com.android.purebilibili.core.ui.components.AnimatedVideoListItem
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppCardVariant
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppLiquidAwareTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.ui.components.AppSwitch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animate
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.unifiedBlur
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalContext // [New]
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity // [New]
import androidx.compose.ui.zIndex // [New]
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned // [New]
import com.android.purebilibili.core.store.SettingsManager // [New]
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.HomeHeaderCollapseMode
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.ui.blur.BlurStyles // [New]
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.util.responsiveContentWidth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect // [Fix] Missing import
import kotlinx.coroutines.launch // [Fix] Import
//  Material Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.resolveGlobalWallpaperChromeColor

import com.android.purebilibili.core.ui.rememberAppChromeLiquidGlassEnabled
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import com.android.purebilibili.core.ui.animation.jiggleOnDissolve
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.ui.rememberAppBookmarkIcon
import com.android.purebilibili.core.ui.rememberAppFolderIcon
import com.android.purebilibili.core.ui.rememberAppHeadphonesIcon
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.core.ui.transition.BiliPaiSharedElementKey
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.VideoGridItemSkeleton
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.rememberResponsiveSpacing
import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryItem
import com.android.purebilibili.data.model.response.FavoriteSection
import com.android.purebilibili.data.model.response.FavoriteSearchScope
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.article.ArticleSharedElementSlot
import com.android.purebilibili.feature.article.resolveHistoryArticleCoverAspectRatio
import com.android.purebilibili.feature.article.resolveArticleSharedTransitionKey
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.feature.space.SeasonSeriesDetailViewModel
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.player.PlaylistSession
import com.android.purebilibili.core.util.resolveScrollToTopPlan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal enum class FavoriteContentMode {
    BASE_LIST,
    SINGLE_FOLDER,
    PAGER
}

private enum class FavoriteBrowseSection {
    OWNED,
    SUBSCRIBED
}

internal fun resolveFavoriteContentMode(
    isFavoritePage: Boolean,
    folderCount: Int
): FavoriteContentMode {
    if (!isFavoritePage) return FavoriteContentMode.BASE_LIST
    return when {
        folderCount > 1 -> FavoriteContentMode.PAGER
        folderCount == 1 -> FavoriteContentMode.SINGLE_FOLDER
        else -> FavoriteContentMode.BASE_LIST
    }
}

internal fun resolveFavoritePlayAllItems(
    mode: FavoriteContentMode,
    baseItems: List<VideoItem>,
    selectedFolderItems: List<VideoItem>,
    singleFolderItems: List<VideoItem>
): List<VideoItem> {
    val candidateItems = when (mode) {
        FavoriteContentMode.PAGER -> selectedFolderItems.ifEmpty { baseItems }
        FavoriteContentMode.SINGLE_FOLDER -> singleFolderItems.ifEmpty { baseItems }
        FavoriteContentMode.BASE_LIST -> baseItems
    }
    return candidateItems.filter { !it.isCollectionResource && it.bvid.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonListScreen(
    viewModel: BaseListViewModel,
    onBack: () -> Unit,
    onVideoClick: (String, Long, String, Boolean) -> Unit,
    onUpClick: ((Long) -> Unit)? = null,
    onCollectionClick: ((FavoriteCollectionRoute) -> Unit)? = null,
    onFavoriteFolderClick: ((Long, Long, String, String) -> Unit)? = null,
    onFavoriteBangumiClick: (Long) -> Unit = {},
    onFavoriteCheeseClick: ((Long) -> Unit)? = null,
    onFavoriteArticleClick: (Long, String) -> Unit = { _, _ -> },
    onFavoriteTopicClick: (Long) -> Unit = {},
    onFavoriteWebClick: (String, String) -> Unit = { _, _ -> },
    initialSearchQuery: String = "",
    initialFavoriteSearchScope: FavoriteSearchScope = FavoriteSearchScope.CURRENT_FOLDER,
    initialFavoriteSubscribed: Boolean = false,
    isSearchDestination: Boolean = false,
    onOpenSearchDestination: ((String) -> Unit)? = null,
    listScopedSearchChannel: kotlinx.coroutines.channels.Channel<String>? = null,
    onPlayAllAudioClick: ((String, Long) -> Unit)? = null,
    globalHazeState: HazeState? = null, // [新增] 接收全局 HazeState
    scrollToTopChannel: Channel<Unit>? = null,
    favoriteCollectionSharedElementRoute: FavoriteCollectionRoute? = null,
    isCurrentPage: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 个人列表（历史/收藏）默认单列，列数由双指缩放调节；其余页面保持双列默认。
    val personalListPage = viewModel is HistoryViewModel || viewModel is FavoriteViewModel
    var pinchListColumns by rememberSaveable(viewModel) {
        androidx.compose.runtime.mutableIntStateOf(if (personalListPage) 1 else 2)
    }
    val primaryGridState = rememberLazyGridState()
    val subscribedFolderListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val favoritePagerGridStates = remember { mutableStateMapOf<Int, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val historyPagerGridStates = remember { mutableStateMapOf<Int, androidx.compose.foundation.lazy.grid.LazyGridState>() }

    // 📱 响应式布局参数
    // Fix: 手机端(Compact)使用较小的最小宽度以保证2列显示 (360dp / 170dp = 2.1 -> 2列)
    // 平板端(Expanded)使用较大的最小宽度以避免卡片过小
    val context = LocalContext.current
    val density = LocalDensity.current
    val showOnlineCount by SettingsManager.getShowOnlineCount(context).collectAsStateWithLifecycle(initialValue = false
        )
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val topChromePolicy = rememberAppTopChromePolicy()
    val liquidGlassEnabled = rememberAppChromeLiquidGlassEnabled(
        androidNativeEnabled = homeSettings.androidNativeLiquidGlassEnabled,
    )
    val windowSizeClass = LocalWindowSizeClass.current
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardMotionTier = resolveEffectiveMotionTier(
        baseTier = deviceUiProfile.motionTier,
        animationEnabled = homeSettings.cardAnimationEnabled
    )
    val favoriteCollectionSharedTransitionEnabled =
        homeSettings.cardTransitionEnabled && LocalSharedTransitionEnabled.current

    // Video lists expose an explicit single/double-column choice, independent of home.
    val columns = pinchListColumns
    val configuration = LocalConfiguration.current
    val commonListViewportWidthPx = with(density) {
        configuration.screenWidthDp.dp.roundToPx()
    }
    val personalListColumns = columns
    val spacing = rememberResponsiveSpacing()
    val pinchColumnBounds = remember(windowSizeClass.widthSizeClass, configuration.screenWidthDp) {
        resolveHomeFeedPinchColumnBounds(
            widthSizeClass = windowSizeClass.widthSizeClass,
            contentWidthDp = configuration.screenWidthDp,
        )
    }
    val pinchToZoomColumnsEnabled = homeSettings.pinchToChangeGridColumnsEnabled

    //  [修复] 分页支持：收藏 + 历史记录 + 用户最近点赞
    val favoriteViewModel = viewModel as? FavoriteViewModel
    val historyViewModel = viewModel as? HistoryViewModel
    val likedVideosViewModel = viewModel as? LikedVideosViewModel
    val seasonSeriesDetailViewModel = viewModel as? SeasonSeriesDetailViewModel
    val likedVideosHasMore by likedVideosViewModel?.hasMoreState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val likedVideosIsLoadingMore by likedVideosViewModel?.isLoadingMoreState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val historyDeleteSession by historyViewModel?.deleteSession?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<HistoryDeleteSession?>(null) }
    val isHistoryPaused by historyViewModel?.isHistoryPausedState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isHistoryManagementBusy by historyViewModel?.isHistoryManagementBusyState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val historyHasMore by historyViewModel?.hasMoreState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val historyIsLoadingMore by historyViewModel?.isLoadingMoreState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var historyContentFilter by rememberSaveable { androidx.compose.runtime.mutableStateOf(HistoryContentFilter.ALL) }
    var isHistoryBatchMode by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var selectedHistoryKeys by rememberSaveable { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    var showHistoryBatchDeleteConfirm by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showHistoryClearConfirm by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var pendingHistorySingleDeleteKey by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // 历史与收藏页面支持随全局顶栏收起设置协同折叠搜索栏/标题栏
    val supportsCollapsibleCommonListHeader = (historyViewModel != null || favoriteViewModel != null) &&
        homeSettings.homeHeaderCollapseMode.hasAnyCollapse
    val visibleHistoryItems = remember(state.items, historyContentFilter, historyViewModel) {
        if (historyViewModel == null) {
            state.items
        } else {
            filterHistoryItemsByContent(
                items = state.items,
                filter = historyContentFilter,
                resolveHistoryItem = { video ->
                    historyViewModel.getHistoryItem(historyViewModel.resolveHistoryLookupKey(video))
                }
            )
        }
    }

    LaunchedEffect(historyViewModel, historyContentFilter) {
        historyViewModel?.setHistoryListType(resolveHistoryListType(historyContentFilter))
    }

    LaunchedEffect(
        historyViewModel,
        historyContentFilter,
        state.isLoading,
        state.items.size,
        visibleHistoryItems.size,
        historyHasMore,
        historyIsLoadingMore
    ) {
        if (
            historyViewModel != null &&
            !state.isLoading &&
            shouldLoadMoreHistoryFilterResults(
                filter = historyContentFilter,
                filteredItemCount = visibleHistoryItems.size,
                hasMore = historyHasMore,
                isLoading = historyIsLoadingMore
            )
        ) {
            historyViewModel.loadMore()
        }
    }

    LaunchedEffect(state.items, historyViewModel, isHistoryBatchMode) {
        if (historyViewModel == null) return@LaunchedEffect
        val validKeys = state.items
            .map(historyViewModel::resolveHistoryRenderKey)
            .filter { it.isNotBlank() }
            .toSet()
        selectedHistoryKeys = selectedHistoryKeys.filter { it in validKeys }.toSet()
        if (isHistoryBatchMode && state.items.isEmpty()) {
            isHistoryBatchMode = false
            selectedHistoryKeys = emptySet()
        }
        if (state.items.isEmpty()) {
            showHistoryClearConfirm = false
        }
    }

    // [Feature] BottomBar Scroll Hiding for CommonListScreen (History/Favorite)
    val setBottomBarVisible = com.android.purebilibili.core.ui.LocalSetBottomBarVisible.current
    val bottomBarChromeScrollOffset = LocalHomeScrollOffset.current
    val continuousScrollOffsetConnection = remember(bottomBarChromeScrollOffset) {
        com.android.purebilibili.feature.home.createContinuousScrollOffsetConnection(
            offsetState = bottomBarChromeScrollOffset
        )
    }

    // 监听列表滚动实现底栏自动隐藏/显示
    var lastFirstVisibleItem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastScrollOffset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // 离开页面时恢复底栏显示
    DisposableEffect(Unit) {
        onDispose {
            setBottomBarVisible(true)
            bottomBarChromeScrollOffset.value = 0f
        }
    }

    // [Fix] Import for launch
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val headerSettleMotionSpec = AppMotionTokens.standardSpec<Float>()

    // 📁 [新增] 收藏夹切换 Tab
    val foldersState by favoriteViewModel?.folders?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val subscribedFoldersState by favoriteViewModel?.subscribedFolders?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val subscribedFolderProgressState by favoriteViewModel?.subscribedFolderProgressState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(FavoriteViewModel.SubscribedFolderProgressState())
        }
    val selectedFolderIndex by favoriteViewModel?.selectedFolderIndex?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val favoriteOrder by favoriteViewModel?.favoriteOrderState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(FavoriteResourceOrder.FAVORITE_TIME) }
    val isFavoriteManaging by favoriteViewModel?.isFavoriteManagingState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val favoriteSearchUiState by favoriteViewModel?.searchUiState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ListUiState()) }
    var favoriteBrowseSection by rememberSaveable(initialFavoriteSubscribed) {
        androidx.compose.runtime.mutableStateOf(
            if (initialFavoriteSubscribed) FavoriteBrowseSection.SUBSCRIBED
            else FavoriteBrowseSection.OWNED
        )
    }
    var showFavoriteCleanInvalidConfirm by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showFavoriteDynamicShareConfirm by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var favoriteSection by rememberSaveable { androidx.compose.runtime.mutableStateOf(FavoriteSection.VIDEO) }
    var isFavoriteBatchMode by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var selectedFavoriteResourceIds by rememberSaveable { androidx.compose.runtime.mutableStateOf(setOf<Long>()) }
    var showFavoriteBatchDeleteConfirm by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var pendingFavoriteTransferCopy by rememberSaveable { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    var favoriteFolderEditorMode by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var favoriteFolderEditorTitle by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var favoriteFolderEditorIntro by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var favoriteFolderEditorPrivate by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showFavoriteFolderDeleteConfirm by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(foldersState.size, subscribedFoldersState.size) {
        favoriteBrowseSection = when {
            initialFavoriteSubscribed -> FavoriteBrowseSection.SUBSCRIBED
            favoriteBrowseSection == FavoriteBrowseSection.SUBSCRIBED && subscribedFoldersState.isNotEmpty() -> FavoriteBrowseSection.SUBSCRIBED
            foldersState.isNotEmpty() -> FavoriteBrowseSection.OWNED
            subscribedFoldersState.isNotEmpty() -> FavoriteBrowseSection.SUBSCRIBED
            else -> FavoriteBrowseSection.OWNED
        }
    }
    val isSubscribedBrowse = favoriteViewModel != null &&
        favoriteSection == FavoriteSection.VIDEO &&
        favoriteBrowseSection == FavoriteBrowseSection.SUBSCRIBED
    val loadMoreOwner = resolveCommonListLoadMoreOwner(
        isSubscribedBrowse = isSubscribedBrowse,
        hasFavoriteViewModel = favoriteViewModel != null,
        hasHistoryViewModel = historyViewModel != null,
        hasLikedVideosViewModel = likedVideosViewModel != null,
        hasSeasonSeriesDetailViewModel = seasonSeriesDetailViewModel != null
    )
    val shouldUseFavoritePlaybackQueue = shouldUseFavoriteExternalPlaylist(
        hasFavoriteViewModel = favoriteViewModel != null,
        isFavoriteDetail = seasonSeriesDetailViewModel?.isFavoriteDetail == true
    )
    val favoriteContentMode = resolveFavoriteContentMode(
        isFavoritePage = favoriteViewModel != null &&
            favoriteSection == FavoriteSection.VIDEO &&
            !isSubscribedBrowse,
        folderCount = foldersState.size
    )
    val selectedFolderUiState by favoriteViewModel
        ?.getFolderUiState(selectedFolderIndex)
        ?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ListUiState()) }
    val singleFolderUiState by favoriteViewModel
        ?.getFolderUiState(0)
        ?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ListUiState()) }
    val activeFavoriteItems = resolveFavoritePlayAllItems(
        mode = favoriteContentMode,
        baseItems = state.items,
        selectedFolderItems = selectedFolderUiState.items,
        singleFolderItems = singleFolderUiState.items
    ).takeUnless { isSubscribedBrowse }.orEmpty()
    val selectedFavoriteFolder = foldersState.getOrNull(selectedFolderIndex)
    LaunchedEffect(selectedFolderIndex, favoriteBrowseSection, favoriteSection) {
        isFavoriteBatchMode = false
        selectedFavoriteResourceIds = emptySet()
        pendingFavoriteTransferCopy = null
    }
    val toggleFavoriteResourceSelection: (Long) -> Unit = { resourceId ->
        if (resourceId > 0L) {
            selectedFavoriteResourceIds = if (resourceId in selectedFavoriteResourceIds) {
                selectedFavoriteResourceIds - resourceId
            } else {
                selectedFavoriteResourceIds + resourceId
            }
        }
    }
    val enterFavoriteBatchMode: (Long) -> Unit = { resourceId ->
        if (resourceId > 0L) {
            isFavoriteBatchMode = true
            selectedFavoriteResourceIds = selectedFavoriteResourceIds + resourceId
        }
    }
    // 返回收藏页时直接从已选文件夹恢复，避免先创建第 1 页再跨多页补间加载。
    val pagerState = rememberPagerState(
        initialPage = selectedFolderIndex.coerceIn(0, foldersState.lastIndex.coerceAtLeast(0))
    ) {
        if (favoriteViewModel != null && foldersState.size > 1) foldersState.size else 0
    }
    var hasSyncedFavoritePager by remember(pagerState) { mutableStateOf(false) }

    // 历史分类滑动 Pager：支持在屏幕中央左右手势滑动切换分类
    val historyFilters = remember { HistoryContentFilter.entries }
    val historyPagerState = rememberPagerState(
        initialPage = historyFilters.indexOf(historyContentFilter).coerceAtLeast(0)
    ) {
        historyFilters.size
    }

    LaunchedEffect(historyPagerState.currentPage) {
        val targetFilter = historyFilters.getOrNull(historyPagerState.currentPage) ?: HistoryContentFilter.ALL
        if (historyContentFilter != targetFilter) {
            historyContentFilter = targetFilter
            selectedHistoryKeys = emptySet()
        }
    }

    val commonListBottomPadding = LocalBottomBarContentPadding.current
    val activeCommonListScrollState = remember(
        favoriteViewModel,
        favoriteContentMode,
        isSubscribedBrowse,
        pagerState.currentPage,
        historyViewModel,
        historyPagerState.currentPage,
        primaryGridState,
        subscribedFolderListState,
        favoritePagerGridStates.size,
        historyPagerGridStates.size
    ) {
        {
            when {
                isSubscribedBrowse -> CommonListScrollState.List(subscribedFolderListState)
                favoriteViewModel != null && favoriteContentMode == FavoriteContentMode.PAGER -> {
                    favoritePagerGridStates[pagerState.currentPage]?.let(CommonListScrollState::Grid)
                        ?: CommonListScrollState.Grid(primaryGridState)
                }
                historyViewModel != null -> {
                    historyPagerGridStates[historyPagerState.currentPage]?.let(CommonListScrollState::Grid)
                        ?: CommonListScrollState.Grid(primaryGridState)
                }
                else -> CommonListScrollState.Grid(primaryGridState)
            }
        }
    }
    LaunchedEffect(activeCommonListScrollState) {
        snapshotFlow {
            when (val scrollState = activeCommonListScrollState()) {
                is CommonListScrollState.Grid -> Pair(
                    scrollState.state.firstVisibleItemIndex,
                    scrollState.state.firstVisibleItemScrollOffset
                )
                is CommonListScrollState.List -> Pair(
                    scrollState.state.firstVisibleItemIndex,
                    scrollState.state.firstVisibleItemScrollOffset
                )
            }
        }
            .distinctUntilChanged()
            .collect { (firstVisibleItem, scrollOffset) ->
                if (firstVisibleItem == 0 && scrollOffset < 100) {
                    setBottomBarVisible(true)
                } else {
                    val isScrollingDown = when {
                        firstVisibleItem > lastFirstVisibleItem -> true
                        firstVisibleItem < lastFirstVisibleItem -> false
                        else -> scrollOffset > lastScrollOffset + 50
                    }
                    val isScrollingUp = when {
                        firstVisibleItem < lastFirstVisibleItem -> true
                        firstVisibleItem > lastFirstVisibleItem -> false
                        else -> scrollOffset < lastScrollOffset - 50
                    }

                    if (isScrollingDown) setBottomBarVisible(false)
                    if (isScrollingUp) setBottomBarVisible(true)
                }
                lastFirstVisibleItem = firstVisibleItem
                lastScrollOffset = scrollOffset
            }
    }

    val shouldShowBackToTop by remember(activeCommonListScrollState) {
        derivedStateOf {
            when (val scrollState = activeCommonListScrollState()) {
                is CommonListScrollState.Grid -> shouldShowCommonListBackToTop(
                    firstVisibleItemIndex = scrollState.state.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = scrollState.state.firstVisibleItemScrollOffset
                )
                is CommonListScrollState.List -> shouldShowCommonListBackToTop(
                    firstVisibleItemIndex = scrollState.state.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = scrollState.state.firstVisibleItemScrollOffset
                )
            }
        }
    }

    suspend fun scrollCommonListToTop() {
        when (val scrollState = activeCommonListScrollState()) {
            is CommonListScrollState.Grid -> {
                val currentIndex = scrollState.state.firstVisibleItemIndex
                val plan = resolveScrollToTopPlan(currentIndex)
                plan.preJumpIndex?.let { preJump ->
                    if (currentIndex > preJump) {
                        scrollState.state.scrollToItem(preJump)
                    }
                }
                scrollState.state.animateScrollToItem(plan.animateTargetIndex)
            }
            is CommonListScrollState.List -> {
                val currentIndex = scrollState.state.firstVisibleItemIndex
                val plan = resolveScrollToTopPlan(currentIndex)
                plan.preJumpIndex?.let { preJump ->
                    if (currentIndex > preJump) {
                        scrollState.state.scrollToItem(preJump)
                    }
                }
                scrollState.state.animateScrollToItem(plan.animateTargetIndex)
            }
        }
    }

    LaunchedEffect(scrollToTopChannel) {
        scrollToTopChannel?.receiveAsFlow()?.collect {
            scrollCommonListToTop()
        }
    }

    // [Fix] 协程作用域 (用于 UI 事件触发的滚动)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // 双指缩放列数：换档震动 + HUD 胶囊提示
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    var pinchPillVisible by remember { mutableStateOf(false) }
    var pinchPillDismissJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }
    val onPinchColumnsChange: (Int) -> Unit = { newColumns ->
        pinchListColumns = newColumns
        hapticFeedback.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
        )
        pinchPillVisible = true
        pinchPillDismissJob?.cancel()
    }
    val onPinchColumnsEnd: (Int) -> Unit = { _ ->
        pinchPillDismissJob?.cancel()
        pinchPillDismissJob = coroutineScope.launch {
            kotlinx.coroutines.delay(1000)
            pinchPillVisible = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // 🔍 搜索状态
    var searchQuery by rememberSaveable { androidx.compose.runtime.mutableStateOf(initialSearchQuery) }
    var favoriteSearchScope by rememberSaveable {
        androidx.compose.runtime.mutableStateOf(initialFavoriteSearchScope)
    }
    val hideListTopSearchBar = shouldHideListTopSearchBar(
        bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
        listScopedSearchEnabled = homeSettings.listScopedSearchEnabled,
        isSearchDestination = isSearchDestination,
    )
    val showListScopedSearchActiveBar = shouldShowListScopedSearchActiveBar(
        bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
        listScopedSearchEnabled = homeSettings.listScopedSearchEnabled,
        searchQuery = searchQuery,
    )
    LaunchedEffect(listScopedSearchChannel) {
        listScopedSearchChannel?.receiveAsFlow()?.collect { query ->
            searchQuery = query
        }
    }
    LaunchedEffect(
        searchQuery,
        favoriteSearchScope,
        isSearchDestination,
        favoriteViewModel,
        historyViewModel,
    ) {
        if (isSearchDestination && favoriteViewModel != null) {
            kotlinx.coroutines.delay(350)
            favoriteViewModel.searchVideos(searchQuery, favoriteSearchScope)
        } else if (historyViewModel != null) {
            kotlinx.coroutines.delay(350)
            historyViewModel.searchHistory(searchQuery)
        }
    }
    // [New] 动态顶栏高度测量 (最准确的方式)
    var headerHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var visibleHeaderHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var fixedTopBarHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var searchBarHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val headerHeightDp = with(LocalDensity.current) {
        (if (supportsCollapsibleCommonListHeader) visibleHeaderHeightPx else headerHeightPx).toDp()
    }
    var commonListHeaderOffsetPx by remember { mutableFloatStateOf(0f) }
    var commonListHeaderSettleJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }
    val commonListHeaderCollapseMode = resolveCommonListHeaderCollapseModeForScreen(
        homeHeaderMode = homeSettings.homeHeaderCollapseMode,
    )
    val commonListHeaderCollapseEnabled = supportsCollapsibleCommonListHeader &&
        commonListHeaderCollapseMode != CommonListHeaderCollapseMode.ALWAYS_VISIBLE
    val statusBarHeightPx = with(LocalDensity.current) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val commonListHeaderMaxCollapsePx = if (supportsCollapsibleCommonListHeader) {
        resolveCommonListHeaderMaxCollapsePxForMode(
            homeHeaderMode = homeSettings.homeHeaderCollapseMode,
            topSearchBarVisible = !hideListTopSearchBar,
            searchBarHeightPx = searchBarHeightPx,
            fixedTopBarHeightPx = fixedTopBarHeightPx,
            statusBarHeightPx = statusBarHeightPx,
        )
    } else {
        resolveCommonListHeaderMaxCollapsePx(
            headerHeightPx = headerHeightPx,
            pinnedDockHeightPx = 0,
            topInsetPx = statusBarHeightPx,
            retainPinnedDock = false,
        )
    }
    fun animateCommonListHeaderOffsetTo(targetOffsetPx: Float) {
        if (kotlin.math.abs(commonListHeaderOffsetPx - targetOffsetPx) <= 0.5f) {
            commonListHeaderOffsetPx = targetOffsetPx
            return
        }
        commonListHeaderSettleJob?.cancel()
        commonListHeaderSettleJob = scope.launch {
            animate(
                initialValue = commonListHeaderOffsetPx,
                targetValue = targetOffsetPx,
                animationSpec = headerSettleMotionSpec
            ) { value, _ ->
                commonListHeaderOffsetPx = value
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (commonListHeaderSettleJob === job) {
                    commonListHeaderSettleJob = null
                }
            }
        }
    }
    val isCommonListAtTop by remember(activeCommonListScrollState) {
        derivedStateOf {
            when (val scrollState = activeCommonListScrollState()) {
                is CommonListScrollState.Grid ->
                    scrollState.state.firstVisibleItemIndex == 0 &&
                        scrollState.state.firstVisibleItemScrollOffset == 0
                is CommonListScrollState.List ->
                    scrollState.state.firstVisibleItemIndex == 0 &&
                        scrollState.state.firstVisibleItemScrollOffset == 0
            }
        }
    }
    val isCommonListScrollInProgress by remember(activeCommonListScrollState) {
        derivedStateOf {
            when (val scrollState = activeCommonListScrollState()) {
                is CommonListScrollState.Grid -> scrollState.state.isScrollInProgress
                is CommonListScrollState.List -> scrollState.state.isScrollInProgress
            }
        }
    }
    // 与推荐页共用「列表正在滑」信号，驱动底栏搜索胶囊展开/收起。
    val globalFeedScrollInProgress = com.android.purebilibili.feature.home.LocalHomeFeedScrollInProgress.current
    if (isCurrentPage) {
        SideEffect {
            globalFeedScrollInProgress.value = isCommonListScrollInProgress
        }
    }
    DisposableEffect(isCurrentPage) {
        if (!isCurrentPage) {
            globalFeedScrollInProgress.value = false
        }
        onDispose {
            if (isCurrentPage) {
                globalFeedScrollInProgress.value = false
            }
        }
    }
    LaunchedEffect(
        commonListHeaderCollapseMode,
        isCommonListAtTop,
        isCommonListScrollInProgress,
        headerHeightPx,
        supportsCollapsibleCommonListHeader,
        favoriteContentMode,
        pagerState.isScrollInProgress,
        historyViewModel,
        historyPagerState.isScrollInProgress
    ) {
        if (favoriteContentMode == FavoriteContentMode.PAGER && pagerState.isScrollInProgress) {
            return@LaunchedEffect
        }
        if (historyViewModel != null && historyPagerState.isScrollInProgress) {
            return@LaunchedEffect
        }
        if (
            !supportsCollapsibleCommonListHeader ||
            commonListHeaderCollapseMode == CommonListHeaderCollapseMode.ALWAYS_VISIBLE ||
            (isCommonListAtTop && !isCommonListScrollInProgress)
        ) {
            animateCommonListHeaderOffsetTo(0f)
        }
    }
    LaunchedEffect(
        commonListHeaderCollapseMode,
        commonListHeaderMaxCollapsePx,
        supportsCollapsibleCommonListHeader,
        isSubscribedBrowse,
        favoriteContentMode,
        pagerState.settledPage,
        favoritePagerGridStates.size,
        activeCommonListScrollState,
    ) {
        snapshotFlow {
            when (val scrollState = activeCommonListScrollState()) {
                is CommonListScrollState.Grid -> Triple(
                    scrollState.state.firstVisibleItemIndex,
                    scrollState.state.firstVisibleItemScrollOffset,
                    scrollState.state.isScrollInProgress,
                )
                is CommonListScrollState.List -> Triple(
                    scrollState.state.firstVisibleItemIndex,
                    scrollState.state.firstVisibleItemScrollOffset,
                    scrollState.state.isScrollInProgress,
                )
            }
        }
            .distinctUntilChanged()
            .collect { (firstVisibleItemIndex, firstVisibleItemScrollOffset, isScrollInProgress) ->
                // 手势期间由 nestedScroll 保持跟手；停止后以 Lazy 列表的真实位置校正，
                // 避免部分 Scaffold 实现未把 pre-scroll 继续传给外层时顶栏永久停在展开态。
                if (!isScrollInProgress) {
                    val targetOffsetPx = resolveCommonListHeaderOffsetForSettledContent(
                        firstVisibleItemIndex = firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                        maxCollapsePx = commonListHeaderMaxCollapsePx,
                        mode = if (supportsCollapsibleCommonListHeader) {
                            commonListHeaderCollapseMode
                        } else {
                            CommonListHeaderCollapseMode.ALWAYS_VISIBLE
                        }
                    )
                    animateCommonListHeaderOffsetTo(targetOffsetPx)
                }
            }
    }
    val commonListHeaderScrollConnection = remember(
        commonListHeaderCollapseMode,
        commonListHeaderMaxCollapsePx,
        isCommonListAtTop,
        supportsCollapsibleCommonListHeader
    ) {
        object : NestedScrollConnection {
            // 与首页一致，在内容消费滚动前更新顶部位移，保证 Dock 与手势同帧跟随。
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    !supportsCollapsibleCommonListHeader ||
                    kotlin.math.abs(available.y) < 0.5f ||
                    kotlin.math.abs(available.y) < kotlin.math.abs(available.x)
                ) {
                    return Offset.Zero
                }
                commonListHeaderSettleJob?.cancel()
                commonListHeaderSettleJob = null
                commonListHeaderOffsetPx = resolveCommonListHeaderOffsetAfterContentScroll(
                    currentOffsetPx = commonListHeaderOffsetPx,
                    contentConsumedDeltaYPx = available.y,
                    maxCollapsePx = commonListHeaderMaxCollapsePx,
                    isAtTop = isCommonListAtTop,
                    mode = commonListHeaderCollapseMode
                )
                return Offset.Zero
            }
        }
    }

    // [Feature] Header Blur Optimization
    val isHeaderBlurEnabled = remember(homeSettings) {
        resolveCommonListHeaderBlurEnabled(
            homeSettings = homeSettings,
        )
    }
    val isProgressiveTopBlurEnabled =
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.progressiveTopBlurEnabled
    val isProgressiveTopFadeEnabled =
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.progressiveTopFadeEnabled
    // 实色列表不创建背景采样；玻璃和普通顶栏模糊分别按需保留各自 source。
    val localHazeState = if (isHeaderBlurEnabled &&
        shouldAllowRenderEffectBackedHazeEffect(android.os.Build.VERSION.SDK_INT) &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    ) {
        com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState()
    } else {
        null
    }
    val loadingChromeContent = when {
        favoriteViewModel != null && isSearchDestination && searchQuery.isNotBlank() ->
            favoriteSearchUiState.isLoading && favoriteSearchUiState.items.isEmpty()
        favoriteContentMode == FavoriteContentMode.PAGER ->
            selectedFolderUiState.isLoading && selectedFolderUiState.items.isEmpty()
        favoriteContentMode == FavoriteContentMode.SINGLE_FOLDER ->
            singleFolderUiState.isLoading && singleFolderUiState.items.isEmpty()
        else -> state.isLoading && state.items.isEmpty()
    }
    val commonListChromeSource = if (isProgressiveTopBlurEnabled || liquidGlassEnabled) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else {
        null
    }
    val commonListChromeBackdrop = commonListChromeSource?.takeIf {
        !loadingChromeContent && it.isReady
    }?.backdrop
    val videoCardAppearance = remember(homeSettings, liquidGlassEnabled) {
        resolveCommonListVideoCardAppearance(
            homeSettings = homeSettings,
            liquidGlassEnabled = liquidGlassEnabled,
        )
    }
    val favoriteHeaderLayout = remember(topChromePolicy) {
        resolveCommonListFavoriteHeaderLayout(
            topChromePolicy = topChromePolicy,
        )
    }
    val historyFilterChrome = remember(homeSettings, topChromePolicy) {
        resolveHistoryFilterTabChromeSpec(
            homeSettings = homeSettings,
            topChromePolicy = topChromePolicy,
        )
    }
    val historyUsesFloatingLiquidDocks = shouldUseFloatingCommonListHeaderChrome(
        isHistoryPage = historyViewModel != null,
        isFavoritePage = favoriteViewModel != null,
        globalLiquidGlassReuseEnabled = historyFilterChrome.useLiquidDock,
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val backgroundAlpha = BlurStyles.getBackgroundAlpha(blurIntensity)
    val headerBackgroundAlpha = if (favoriteViewModel != null) {
        (backgroundAlpha * favoriteHeaderLayout.headerBackgroundAlphaMultiplier).coerceIn(0f, 1f)
    } else {
        backgroundAlpha
    }
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val shouldUseHeaderLocalBlur = shouldUseCommonListHeaderLocalBlur(
        headerBlurEnabled = isHeaderBlurEnabled,
        globalWallpaperVisible = globalWallpaperVisible
    )
    val headerBackgroundColor = resolveGlobalWallpaperChromeColor(
        requestedColor = AppSurfaceTokens.groupedListContainer().copy(
            alpha = if (isHeaderBlurEnabled) headerBackgroundAlpha else 1f
        ),
        defaultBackgroundColor = AppSurfaceTokens.background(),
        defaultSurfaceColor = AppSurfaceTokens.surface(),
        globalWallpaperVisible = globalWallpaperVisible
    )

    // 决定顶栏背景 (使用私有的 localHazeState)
    val progressiveHeaderRequested = shouldUseBiliPaiProgressiveTopBlur(
        enabled = isProgressiveTopBlurEnabled && !isHeaderBlurEnabled,
        hasBackdrop = true,
    ) && !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val useProgressiveHeaderBlur = progressiveHeaderRequested && commonListChromeBackdrop != null
    val headerBlurActive = shouldUseHeaderLocalBlur &&
        localHazeState?.let { recoverableBlurEnabled(it) } == true &&
        !useProgressiveHeaderBlur
    val commonListScrollUnderHeader = shouldScrollCommonListUnderHeader(
        isHistoryPage = historyViewModel != null,
        headerCollapseEnabled = commonListHeaderCollapseEnabled,
        captureScrollableContent = progressiveHeaderRequested,
    )
    val topBarBackgroundModifier = if (useProgressiveHeaderBlur || (isProgressiveTopFadeEnabled && !isHeaderBlurEnabled)) {
        Modifier.fillMaxWidth()
    } else if (historyUsesFloatingLiquidDocks) {
        // 悬浮 Dock 必须直接采样下方列表；整块顶栏背景会把动态折射退化成纯色壳。
        Modifier.fillMaxWidth()
    } else if (headerBlurActive) {
        Modifier
            .fillMaxWidth()
            .unifiedBlur(
                hazeState = requireNotNull(localHazeState),
                surfaceType = BlurSurfaceType.HEADER
            )
            .background(headerBackgroundColor)
    } else {
        Modifier
            .fillMaxWidth()
            .background(headerBackgroundColor)
    }

    val playFavoriteVideo: (List<VideoItem>, String, Long, String, Int?, Boolean) -> Unit =
        { items, bvid, cid, coverUrl, folderIndex, playAllAudio ->
            fun startPlayback(playlistItems: List<VideoItem>): PlaylistSession? {
                val externalPlaylist = buildExternalPlaylistFromFavorite(
                    items = playlistItems,
                    clickedBvid = bvid
                )
                val playlistSession = externalPlaylist?.let { playlist ->
                    PlaylistManager.setExternalPlaylist(
                        playlist.playlistItems,
                        playlist.startIndex,
                        source = ExternalPlaylistSource.FAVORITE
                    ).also { PlaylistManager.setPlayMode(PlayMode.SEQUENTIAL) }
                }
                val isVertical = playlistItems.firstOrNull { it.bvid == bvid }?.isVertical ?: false
                if (playAllAudio) {
                    onPlayAllAudioClick?.invoke(bvid, cid)
                        ?: onVideoClick(bvid, cid, coverUrl, isVertical)
                } else {
                    onVideoClick(bvid, cid, coverUrl, isVertical)
                }
                return playlistSession
            }
            if (favoriteViewModel != null && folderIndex != null) {
                val playlistSession = startPlayback(items)
                if (playlistSession != null) {
                    favoriteViewModel.loadAllForPlayback(folderIndex) { allItems ->
                        buildExternalPlaylistFromFavorite(allItems)?.let { playlist ->
                            PlaylistManager.addAllToPlaylistIfCurrent(
                                items = playlist.playlistItems,
                                session = playlistSession,
                            )
                        }
                    }
                }
            } else {
                startPlayback(items)
            }
        }

    AppScaffold(
        modifier = Modifier
            .nestedScroll(continuousScrollOffsetConnection)
            .nestedScroll(commonListHeaderScrollConnection)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (globalWallpaperVisible) {
            Color.Transparent
        } else {
            AppSurfaceTokens.groupedListContainer()
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 底层：内容区域
            // [Haze Audit] 全局源已在 AppNavigation 根层提供，这里仅保留本地源
            // 无全局壁纸时铺满 grouped 底色，避免 header blur 采到空洞；全局壁纸开启后
            // 改为半透明保护色，让根层壁纸透出来。
            val contentModifier = Modifier
                .fillMaxSize()
                .then(
                    commonListChromeSource?.modifier ?: Modifier
                )
                .then(
                    if (localHazeState != null) {
                        Modifier.hazeSourceCompat(state = localHazeState)
                    } else {
                        Modifier
                    }
                )
                .globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer())

            Box(modifier = contentModifier) {
                if (
                    favoriteViewModel != null &&
                    isSearchDestination &&
                    favoriteSection == FavoriteSection.VIDEO &&
                    searchQuery.isNotBlank()
                ) {
                    CommonListContent(
                        items = favoriteSearchUiState.items,
                        isLoading = favoriteSearchUiState.isLoading,
                        error = favoriteSearchUiState.error,
                        searchQuery = "",
                        columns = personalListColumns,
                        isFavoritePersonalList = true,
                        spacing = spacing.medium,
                        padding = PaddingValues(top = headerHeightDp, bottom = commonListBottomPadding),
                        scrollUnderHeader = commonListScrollUnderHeader,
                        cardAnimationEnabled = homeSettings.cardAnimationEnabled,
                        cardTransitionEnabled = homeSettings.cardTransitionEnabled,
                        cardMotionTier = cardMotionTier,
                        showOnlineCount = showOnlineCount,
                        videoCardAppearance = videoCardAppearance,
                        onVideoClick = { bvid, cid, coverUrl, isVertical ->
                            onVideoClick(bvid, cid, coverUrl, isVertical)
                        },
                        onCollectionClick = onCollectionClick,
                        onRetry = { favoriteViewModel.searchVideos(searchQuery, favoriteSearchScope) },
                        onLoadMore = {},
                        onUnfavorite = { favoriteViewModel.removeVideo(it) },
                        onUpClick = onUpClick,
                        gridState = primaryGridState,
                        pinchEnabled = pinchToZoomColumnsEnabled,
                        pinchBounds = pinchColumnBounds,
                        onPinchColumnsChange = onPinchColumnsChange,
                        onPinchColumnsEnd = onPinchColumnsEnd,
                    )
                } else if (favoriteViewModel != null && favoriteSection != FavoriteSection.VIDEO) {
                    FavoriteCategoryRoute(
                        section = favoriteSection,
                        query = searchQuery,
                        contentPadding = PaddingValues(
                            top = headerHeightDp,
                            bottom = commonListBottomPadding,
                        ),
                        onBangumiClick = onFavoriteBangumiClick,
                        onArticleClick = onFavoriteArticleClick,
                        onTopicClick = onFavoriteTopicClick,
                        onWebClick = onFavoriteWebClick,
                        onCheeseClick = onFavoriteCheeseClick,
                    )
                } else if (isSubscribedBrowse) {
                    val favoriteVm = requireNotNull(favoriteViewModel)
                    FavoriteSubscribedFolderList(
                        folders = filterFavoriteFoldersByQuery(subscribedFoldersState, searchQuery),
                        searchQuery = searchQuery,
                        padding = PaddingValues(
                            top = headerHeightDp,
                            bottom = commonListBottomPadding
                        ),
                        listState = subscribedFolderListState,
                        spacing = spacing.medium,
                        hasMore = subscribedFolderProgressState.hasMore,
                        isLoadingMore = subscribedFolderProgressState.isLoadingMore,
                        transitionEnabled = favoriteCollectionSharedTransitionEnabled,
                        onLoadMore = { favoriteVm.loadMoreSubscribedFolders() },
                        onFolderClick = { folder ->
                            val collectionRoute = resolveSubscribedFavoriteFolderRoute(folder)
                            if (collectionRoute != null) {
                                onCollectionClick?.invoke(collectionRoute)
                            } else {
                                onFavoriteFolderClick?.invoke(
                                    resolveFavoriteFolderMediaId(folder),
                                    folder.mid,
                                    folder.title,
                                    folder.upper?.name.orEmpty()
                                )
                            }
                        }
                    )
                } else when (favoriteContentMode) {
                    FavoriteContentMode.PAGER -> {
                        val favoriteVm = requireNotNull(favoriteViewModel)
                        // Personal-list pages use explicit controls for horizontal navigation.
                        // Keeping this pager programmatic avoids competing with predictive back
                        // and with filter/folder controls in the collapsing header.
                        LaunchedEffect(selectedFolderIndex, pagerState.pageCount) {
                            if (pagerState.pageCount > 0) {
                                val targetPage = selectedFolderIndex.coerceIn(
                                    minimumValue = 0,
                                    maximumValue = pagerState.pageCount - 1,
                                )
                                if (!hasSyncedFavoritePager) {
                                    pagerState.scrollToPage(targetPage)
                                    hasSyncedFavoritePager = true
                                } else if (pagerState.currentPage != targetPage) {
                                    // Cap intermediate folder traversal so distant switches
                                    // only animate the final window instead of every folder.
                                    resolveFavoriteFolderSwitchPreJumpPage(
                                        currentPage = pagerState.currentPage,
                                        targetPage = targetPage,
                                    )?.let { preJumpPage ->
                                        pagerState.scrollToPage(
                                            preJumpPage.coerceIn(0, pagerState.pageCount - 1)
                                        )
                                    }
                                    animatePagerSelection(pagerState, targetPage)
                                }
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 0,
                        ) { page ->
                            // 获取当前页面的状态
                            val folderUiState by favoriteVm.getFolderUiState(page).collectAsStateWithLifecycle()

                            // 确保数据加载
                            LaunchedEffect(page) {
                                favoriteVm.loadFolder(page)
                            }

                            // 渲染通用列表内容 (复用下方逻辑，提取为组件)
                            CommonListContent(
                                items = folderUiState.items,
                                isLoading = folderUiState.isLoading,
                                error = folderUiState.error,
                                searchQuery = searchQuery,
                                columns = personalListColumns,
                                isFavoritePersonalList = true,
                                favoriteBatchMode = isFavoriteBatchMode && page == selectedFolderIndex,
                                favoriteSelectedResourceIds = selectedFavoriteResourceIds,
                                onFavoriteToggleSelect = toggleFavoriteResourceSelection,
                                onFavoriteLongPress = enterFavoriteBatchMode,
                                spacing = spacing.medium,
                                padding = PaddingValues(top = headerHeightDp, bottom = commonListBottomPadding),
                                scrollUnderHeader = commonListScrollUnderHeader,
                                cardAnimationEnabled = homeSettings.cardAnimationEnabled,
                                cardTransitionEnabled = homeSettings.cardTransitionEnabled,
                                cardMotionTier = cardMotionTier,
                                showOnlineCount = showOnlineCount,
                                videoCardAppearance = videoCardAppearance,
                                onVideoClick = { bvid, cid, coverUrl, isVertical ->
                                    playFavoriteVideo(folderUiState.items, bvid, cid, coverUrl, page, false)
                                },
                                onCollectionClick = onCollectionClick,
                                onRetry = { favoriteVm.retryFolder(page) },
                                onLoadMore = { favoriteVm.loadMoreForFolder(page) },
                                onUnfavorite = if (folderUiState.canRemoveItems) {
                                    { video -> favoriteVm.removeVideo(video) }
                                } else {
                                    null
                                },
                                onUpClick = onUpClick,
                                gridState = favoritePagerGridStates.getOrPut(page) {
                                    androidx.compose.foundation.lazy.grid.LazyGridState()
                                },
                                pinchEnabled = pinchToZoomColumnsEnabled,
                                pinchBounds = pinchColumnBounds,
                                onPinchColumnsChange = onPinchColumnsChange,
                                onPinchColumnsEnd = onPinchColumnsEnd,
                            )
                        }
                    }

                    FavoriteContentMode.SINGLE_FOLDER -> {
                        val favoriteVm = requireNotNull(favoriteViewModel)
                        val folderUiState by favoriteVm.getFolderUiState(0).collectAsStateWithLifecycle()
                        LaunchedEffect(favoriteVm) {
                            favoriteVm.loadFolder(0)
                        }
                        CommonListContent(
                            items = folderUiState.items,
                            isLoading = folderUiState.isLoading,
                            error = folderUiState.error,
                            searchQuery = searchQuery,
                            columns = personalListColumns,
                            isFavoritePersonalList = true,
                            favoriteBatchMode = isFavoriteBatchMode,
                            favoriteSelectedResourceIds = selectedFavoriteResourceIds,
                            onFavoriteToggleSelect = toggleFavoriteResourceSelection,
                            onFavoriteLongPress = enterFavoriteBatchMode,
                            spacing = spacing.medium,
                            padding = PaddingValues(top = headerHeightDp, bottom = commonListBottomPadding),
                            scrollUnderHeader = commonListScrollUnderHeader,
                            cardAnimationEnabled = homeSettings.cardAnimationEnabled,
                            cardTransitionEnabled = homeSettings.cardTransitionEnabled,
                            cardMotionTier = cardMotionTier,
                            showOnlineCount = showOnlineCount,
                            videoCardAppearance = videoCardAppearance,
                            onVideoClick = { bvid, cid, coverUrl, _ ->
                                playFavoriteVideo(folderUiState.items, bvid, cid, coverUrl, 0, false)
                            },
                            onCollectionClick = onCollectionClick,
                            onRetry = { favoriteVm.retryFolder(0) },
                            onLoadMore = { favoriteVm.loadMoreForFolder(0) },
                            onUnfavorite = if (folderUiState.canRemoveItems) {
                                { video -> favoriteVm.removeVideo(video) }
                            } else {
                                null
                            },
                            onUpClick = onUpClick,
                            gridState = primaryGridState,
                            pinchEnabled = pinchToZoomColumnsEnabled,
                            pinchBounds = pinchColumnBounds,
                            onPinchColumnsChange = onPinchColumnsChange,
                            onPinchColumnsEnd = onPinchColumnsEnd,
                        )
                    }

                    FavoriteContentMode.BASE_LIST -> if (historyViewModel != null) {
                        HorizontalPager(
                            state = historyPagerState,
                            userScrollEnabled = !isHistoryBatchMode,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 0,
                        ) { pageIndex ->
                            val pageFilter = historyFilters.getOrElse(pageIndex) { HistoryContentFilter.ALL }
                            val pageItems = remember(state.items, pageFilter, historyViewModel) {
                                filterHistoryItemsByContent(
                                    items = state.items,
                                    filter = pageFilter,
                                    resolveHistoryItem = { video ->
                                        historyViewModel.getHistoryItem(historyViewModel.resolveHistoryLookupKey(video))
                                    }
                                )
                            }
                            val pageGridState = historyPagerGridStates.getOrPut(pageIndex) {
                                androidx.compose.foundation.lazy.grid.LazyGridState()
                            }

                            CommonListContent(
                                items = pageItems,
                                isLoading = state.isLoading,
                                error = state.error,
                                searchQuery = searchQuery,
                                columns = personalListColumns,
                                isFavoritePersonalList = false,
                                favoriteBatchMode = false,
                                favoriteSelectedResourceIds = emptySet(),
                                onFavoriteToggleSelect = null,
                                onFavoriteLongPress = null,
                                spacing = spacing.medium,
                                padding = PaddingValues(top = headerHeightDp, bottom = commonListBottomPadding),
                                scrollUnderHeader = commonListScrollUnderHeader,
                                cardAnimationEnabled = homeSettings.cardAnimationEnabled,
                                cardTransitionEnabled = homeSettings.cardTransitionEnabled,
                                cardMotionTier = cardMotionTier,
                                showOnlineCount = showOnlineCount,
                                videoCardAppearance = videoCardAppearance,
                                homeDurationStyle = homeSettings.homeDurationStyle,
                                onVideoClick = { bvid, cid, coverUrl, isVertical ->
                                    onVideoClick(bvid, cid, coverUrl, isVertical)
                                },
                                onCollectionClick = onCollectionClick,
                                onRetry = null,
                                onLoadMore = {
                                    historyViewModel.loadMore()
                                },
                                onUnfavorite = null,
                                onUpClick = if (!isHistoryBatchMode) onUpClick else null,
                                searchPaginationFallbackEnabled = true,
                                hasMoreSearchResults = historyHasMore,
                                isLoadingMoreSearchResults = historyIsLoadingMore,
                                historyDeleteSession = historyDeleteSession,
                                historyBatchMode = isHistoryBatchMode,
                                historySelectedKeys = selectedHistoryKeys,
                                resolveHistoryItemKey = { video -> historyViewModel.resolveHistoryRenderKey(video) },
                                resolveHistoryLookupKey = { video -> historyViewModel.resolveHistoryLookupKey(video) },
                                resolveHistoryItem = { video -> historyViewModel.getHistoryItem(historyViewModel.resolveHistoryLookupKey(video)) },
                                onHistoryLongDelete = { key ->
                                    if (!isHistoryBatchMode) {
                                        isHistoryBatchMode = true
                                        selectedHistoryKeys = key.takeIf { it.isNotBlank() }?.let(::setOf).orEmpty()
                                    }
                                },
                                onHistoryDelete = { key -> pendingHistorySingleDeleteKey = key.takeIf { it.isNotBlank() } },
                                onHistoryAddToWatchLater = { item -> historyViewModel.addToWatchLater(item) },
                                onHistoryDissolveComplete = { key -> historyViewModel.completeVideoDissolve(key) },
                                onHistoryToggleSelect = { key ->
                                    if (key.isNotBlank()) {
                                        selectedHistoryKeys = if (key in selectedHistoryKeys) {
                                            selectedHistoryKeys - key
                                        } else {
                                            selectedHistoryKeys + key
                                        }
                                    }
                                },
                                gridState = pageGridState,
                                pinchEnabled = pinchToZoomColumnsEnabled,
                                pinchBounds = pinchColumnBounds,
                                onPinchColumnsChange = onPinchColumnsChange,
                                onPinchColumnsEnd = onPinchColumnsEnd,
                            )
                        }
                    } else {
                        CommonListContent(
                            items = state.items,
                            isLoading = state.isLoading,
                            error = state.error,
                            searchQuery = searchQuery,
                            columns = if (favoriteViewModel != null) personalListColumns else columns,
                            isFavoritePersonalList = favoriteViewModel != null,
                            favoriteBatchMode = favoriteViewModel != null && isFavoriteBatchMode,
                            favoriteSelectedResourceIds = selectedFavoriteResourceIds,
                            onFavoriteToggleSelect = if (favoriteViewModel != null) toggleFavoriteResourceSelection else null,
                            onFavoriteLongPress = if (favoriteViewModel != null) enterFavoriteBatchMode else null,
                            spacing = spacing.medium,
                            padding = PaddingValues(top = headerHeightDp, bottom = commonListBottomPadding),
                            scrollUnderHeader = commonListScrollUnderHeader,
                            cardAnimationEnabled = homeSettings.cardAnimationEnabled,
                            cardTransitionEnabled = homeSettings.cardTransitionEnabled,
                            cardMotionTier = cardMotionTier,
                            showOnlineCount = showOnlineCount,
                            videoCardAppearance = videoCardAppearance,
                            homeDurationStyle = homeSettings.homeDurationStyle,
                            onVideoClick = { bvid, cid, coverUrl, isVertical ->
                                if (shouldUseFavoritePlaybackQueue) {
                                    playFavoriteVideo(state.items, bvid, cid, coverUrl, null, false)
                                } else {
                                    onVideoClick(bvid, cid, coverUrl, isVertical)
                                }
                            },
                            onCollectionClick = onCollectionClick,
                            onRetry = { viewModel.loadData() },
                            onLoadMore = {
                                when (loadMoreOwner) {
                                    CommonListLoadMoreOwner.FAVORITE -> favoriteViewModel?.loadMore()
                                    CommonListLoadMoreOwner.HISTORY -> historyViewModel?.loadMore()
                                    CommonListLoadMoreOwner.LIKED_VIDEOS -> likedVideosViewModel?.loadMore()
                                    CommonListLoadMoreOwner.SEASON_SERIES_DETAIL -> seasonSeriesDetailViewModel?.loadMore()
                                    CommonListLoadMoreOwner.NONE -> Unit
                                }
                            },
                            onUnfavorite = if (favoriteViewModel != null) {
                                { favoriteViewModel.removeVideo(it) }
                            } else null,
                            onUpClick = if (!isHistoryBatchMode) {
                                onUpClick
                            } else {
                                null
                            },
                            searchPaginationFallbackEnabled = likedVideosViewModel != null,
                            hasMoreSearchResults = likedVideosHasMore,
                            isLoadingMoreSearchResults = likedVideosIsLoadingMore,
                            historyDeleteSession = null,
                            historyBatchMode = false,
                            historySelectedKeys = emptySet(),
                            resolveHistoryItemKey = { video -> video.bvid.ifBlank { video.id.toString() } },
                            resolveHistoryLookupKey = null,
                            resolveHistoryItem = null,
                            onHistoryLongDelete = null,
                            onHistoryDelete = null,
                            onHistoryAddToWatchLater = null,
                            onHistoryDissolveComplete = null,
                            onHistoryToggleSelect = null,
                            gridState = primaryGridState,
                            pinchEnabled = pinchToZoomColumnsEnabled,
                            pinchBounds = pinchColumnBounds,
                            onPinchColumnsChange = onPinchColumnsChange,
                            onPinchColumnsEnd = onPinchColumnsEnd,
                        )
                    }
                }
            }

            // 2. 顶层：悬浮顶栏 (使用 onGloballyPositioned 测量高度)
            BiliPaiImmersiveTopBar(
                backdrop = commonListChromeBackdrop,
                enabled = useProgressiveHeaderBlur,
                headerBlurActive = headerBlurActive,
                surfaceColor = headerBackgroundColor,
                fadeEnabled = isProgressiveTopFadeEnabled && !isHeaderBlurEnabled,
                extendBelowBounds = false,
                modifier = Modifier
                    .zIndex(1f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = if (supportsCollapsibleCommonListHeader) 0f else commonListHeaderOffsetPx
                    }
                    .then(topBarBackgroundModifier)
                    .onGloballyPositioned { coordinates ->
                        visibleHeaderHeightPx = coordinates.size.height
                        if (!supportsCollapsibleCommonListHeader || commonListHeaderOffsetPx >= -0.5f) {
                            headerHeightPx = coordinates.size.height
                        }
                    }
            ) {
                Layout(
                    modifier = if (supportsCollapsibleCommonListHeader) Modifier.clipToBounds() else Modifier,
                    content = {
                    AppTopBar(
                        title = state.title,
                        modifier = Modifier.favoriteCollectionSharedBounds(
                            route = favoriteCollectionSharedElementRoute,
                            transitionEnabled = favoriteCollectionSharedTransitionEnabled
                        )
                            .onGloballyPositioned { coordinates ->
                                fixedTopBarHeightPx = coordinates.size.height
                            },
                        navigationIcon = {
                            AppIconButton(onClick = onBack) {
                                AppIcon(rememberAppBackIcon(), contentDescription = "Back")
                            }
                        },
                        actions = {
                            val isBatchActionMode = isFavoriteBatchMode || isHistoryBatchMode
                            if (!isBatchActionMode) {
                                onOpenSearchDestination?.let { openSearch ->
                                    AppIconButton(onClick = { openSearch(searchQuery) }) {
                                        AppIcon(Icons.Rounded.Search, contentDescription = "搜索")
                                    }
                                }
                            }
                            if (favoriteViewModel != null && favoriteSection == FavoriteSection.VIDEO) {
                                if (isFavoriteBatchMode) {
                                    val selectableIds = activeFavoriteItems
                                        .filterNot { it.isCollectionResource }
                                        .map { it.id }
                                        .filter { it > 0L }
                                        .toSet()
                                    val allSelected = selectableIds.isNotEmpty() &&
                                        selectedFavoriteResourceIds.containsAll(selectableIds)
                                    AppTextButton(
                                        onClick = {
                                            selectedFavoriteResourceIds = if (allSelected) emptySet() else selectableIds
                                        }
                                    ) {
                                        AppText(if (allSelected) "取消全选" else "全选")
                                    }
                                    AppWindowActionMenu(
                                        enabled = selectedFavoriteResourceIds.isNotEmpty() && !isFavoriteManaging,
                                        groups = listOf(
                                            listOf(
                                                AppWindowAction(
                                                    label = "复制到收藏夹",
                                                    onClick = { pendingFavoriteTransferCopy = true },
                                                ),
                                                AppWindowAction(
                                                    label = "移动到收藏夹",
                                                    onClick = { pendingFavoriteTransferCopy = false },
                                                ),
                                                AppWindowAction(
                                                    label = "删除",
                                                    onClick = { showFavoriteBatchDeleteConfirm = true },
                                                ),
                                            ),
                                        ),
                                    ) {
                                        AppIcon(Icons.Filled.MoreVert, contentDescription = "批量操作")
                                    }
                                    AppTextButton(
                                        onClick = {
                                            isFavoriteBatchMode = false
                                            selectedFavoriteResourceIds = emptySet()
                                        }
                                    ) {
                                        AppText("完成")
                                    }
                                } else {
                                AppIconButton(
                                    enabled = activeFavoriteItems.any { it.bvid.isNotBlank() } && !isSubscribedBrowse,
                                    onClick = {
                                        activeFavoriteItems.firstOrNull { it.bvid.isNotBlank() }?.let { first ->
                                            playFavoriteVideo(
                                                activeFavoriteItems,
                                                first.bvid,
                                                first.cid,
                                                first.pic,
                                                selectedFolderIndex,
                                                false,
                                            )
                                        }
                                    }
                                ) {
                                    AppIcon(
                                        imageVector = rememberAppPlayIcon(),
                                        contentDescription = "播放全部",
                                    )
                                }
                                AppIconButton(
                                    enabled = activeFavoriteItems.isNotEmpty() && !isSubscribedBrowse,
                                    onClick = {
                                        playFavoriteVideo(
                                            activeFavoriteItems,
                                            activeFavoriteItems.firstOrNull()?.bvid.orEmpty(),
                                            activeFavoriteItems.firstOrNull()?.cid ?: 0L,
                                            activeFavoriteItems.firstOrNull()?.pic.orEmpty(),
                                            if (!isSubscribedBrowse) {
                                                selectedFolderIndex
                                            } else {
                                                null
                                            },
                                            true
                                        )
                                    }
                                ) {
                                    AppIcon(
                                        imageVector = rememberAppHeadphonesIcon(),
                                        contentDescription = "全部听"
                                    )
                                }

                                if (!isSubscribedBrowse) {
                                    AppWindowActionMenu(
                                        enabled = !isFavoriteManaging,
                                        groups = listOf(
                                            listOf(
                                                AppWindowAction(
                                                    label = "新建收藏夹",
                                                    enabled = !isFavoriteManaging,
                                                    onClick = {
                                                        favoriteFolderEditorMode = "create"
                                                        favoriteFolderEditorTitle = ""
                                                        favoriteFolderEditorIntro = ""
                                                        favoriteFolderEditorPrivate = false
                                                    },
                                                ),
                                                AppWindowAction(
                                                    label = "编辑收藏夹",
                                                    enabled = selectedFavoriteFolder != null && !isFavoriteManaging,
                                                    onClick = {
                                                        selectedFavoriteFolder?.let { folder ->
                                                            favoriteFolderEditorMode = "edit"
                                                            favoriteFolderEditorTitle = folder.title
                                                            favoriteFolderEditorIntro = folder.intro
                                                            favoriteFolderEditorPrivate = folder.attr != 0
                                                        }
                                                    },
                                                ),
                                                AppWindowAction(
                                                    label = "删除收藏夹",
                                                    enabled = selectedFolderIndex > 0 && !isFavoriteManaging,
                                                    onClick = { showFavoriteFolderDeleteConfirm = true },
                                                ),
                                            ),
                                            FavoriteResourceOrder.entries.map { order ->
                                                AppWindowAction(
                                                    label = if (order == favoriteOrder) {
                                                        "排序：${order.label}"
                                                    } else {
                                                        order.label
                                                    },
                                                    enabled = !isFavoriteManaging,
                                                    selected = order == favoriteOrder,
                                                    onClick = { favoriteViewModel.changeFavoriteOrder(order) },
                                                )
                                            },
                                            listOf(
                                                AppWindowAction(
                                                    label = "分享收藏夹",
                                                    enabled = selectedFavoriteFolder?.attr == 0 && !isFavoriteManaging,
                                                    onClick = {
                                                        selectedFavoriteFolder?.let { folder ->
                                                            com.android.purebilibili.core.util.ShareUtils.shareText(
                                                                context = context,
                                                                subject = folder.title,
                                                                text = "https://www.bilibili.com/medialist/detail/ml${resolveFavoriteFolderMediaId(folder)}",
                                                                chooserTitle = "分享收藏夹",
                                                            )
                                                        }
                                                    },
                                                ),
                                                AppWindowAction(
                                                    label = "分享至动态",
                                                    enabled = selectedFavoriteFolder?.attr == 0 && !isFavoriteManaging,
                                                    onClick = { showFavoriteDynamicShareConfirm = true },
                                                ),
                                                AppWindowAction(
                                                    label = "清理失效内容",
                                                    enabled = canCleanInvalidFavoriteResources(selectedFavoriteFolder) && !isFavoriteManaging,
                                                    onClick = { showFavoriteCleanInvalidConfirm = true },
                                                ),
                                            ),
                                        ),
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "更多管理"
                                        )
                                    }
                                }
                                }
                            }

                            if (historyViewModel != null) {
                                if (isHistoryBatchMode && visibleHistoryItems.isNotEmpty()) {
                                    val visibleHistoryKeys = visibleHistoryItems
                                        .map(historyViewModel::resolveHistoryRenderKey)
                                        .toSet()
                                    val allSelected = visibleHistoryKeys.isNotEmpty() &&
                                        selectedHistoryKeys.containsAll(visibleHistoryKeys)
                                    AppTextButton(
                                        onClick = {
                                            selectedHistoryKeys = if (allSelected) {
                                                emptySet()
                                            } else {
                                                visibleHistoryKeys
                                            }
                                        }
                                    ) {
                                        AppText(if (allSelected) "取消全选" else "全选")
                                    }
                                    AppTextButton(
                                        enabled = selectedHistoryKeys.isNotEmpty(),
                                        onClick = { showHistoryBatchDeleteConfirm = true }
                                    ) {
                                        AppText("删除(${selectedHistoryKeys.size})")
                                    }
                                    AppTextButton(
                                        onClick = {
                                            isHistoryBatchMode = false
                                            selectedHistoryKeys = emptySet()
                                        }
                                    ) {
                                        AppText("完成")
                                    }
                                } else {
                                    if (state.items.isNotEmpty()) {
                                        AppTextButton(
                                            enabled = !isHistoryManagementBusy,
                                            onClick = {
                                                isHistoryBatchMode = true
                                                selectedHistoryKeys = emptySet()
                                            }
                                        ) {
                                            AppText("批量删除")
                                        }
                                    }

                                    AppWindowActionMenu(
                                        enabled = !isHistoryManagementBusy,
                                        groups = listOf(
                                            listOf(
                                                AppWindowAction(
                                                    label = resolveHistoryPauseActionLabel(isHistoryPaused),
                                                    enabled = !isHistoryManagementBusy,
                                                    onClick = { historyViewModel.toggleHistoryPause() },
                                                ),
                                                AppWindowAction(
                                                    label = "删除已看记录",
                                                    enabled = state.items.isNotEmpty() && !isHistoryManagementBusy,
                                                    onClick = { historyViewModel.deleteViewedHistory() },
                                                ),
                                                AppWindowAction(
                                                    label = "清空历史",
                                                    enabled = state.items.isNotEmpty() && !isHistoryManagementBusy,
                                                    onClick = { showHistoryClearConfirm = true },
                                                ),
                                            ),
                                        ),
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "更多管理"
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = scrollBehavior
                    )

                    // 🔍 搜索栏。历史页开启全局液态玻璃复用后，搜索与筛选各自成为
                    // 一条独立 Dock，结构与首页顶部一致。
                    // 「列表精简搜索」开启后隐藏顶栏搜索，由底栏胶囊页内搜索；有关键词时
                    // 显示轻量结果条以便确认与清除。
                    if (hideListTopSearchBar) {
                        if (showListScopedSearchActiveBar) {
                            ListScopedSearchActiveBar(
                                searchQuery = searchQuery,
                                onClear = { searchQuery = "" },
                                backdrop = commonListChromeBackdrop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        searchBarHeightPx = coordinates.size.height
                                    }
                                    .padding(
                                        horizontal = if (historyViewModel != null && historyFilterChrome.useLiquidDock) {
                                            historyFilterChrome.horizontalPaddingDp.dp
                                        } else {
                                            favoriteHeaderLayout.searchBarHorizontalPaddingDp.dp
                                        },
                                        vertical = favoriteHeaderLayout.searchBarVerticalPaddingDp.dp
                                    ),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    searchBarHeightPx = coordinates.size.height
                                }
                                .padding(
                                    horizontal = if (historyViewModel != null && historyFilterChrome.useLiquidDock) {
                                        historyFilterChrome.horizontalPaddingDp.dp
                                    } else {
                                        favoriteHeaderLayout.searchBarHorizontalPaddingDp.dp
                                    },
                                    vertical = favoriteHeaderLayout.searchBarVerticalPaddingDp.dp
                                )
                        ) {
                            val searchPlaceholder = when {
                                isSubscribedBrowse -> "搜索追更"
                                historyViewModel != null -> "搜索历史"
                                favoriteViewModel != null && favoriteSection != FavoriteSection.VIDEO ->
                                    "搜索${favoriteSection.label}收藏"
                                else -> "搜索视频"
                            }
                            AppLiquidAwareSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = searchPlaceholder,
                                backdrop = commonListChromeBackdrop,
                                isScrollInProgressProvider = {
                                    primaryGridState.isScrollInProgress
                                },
                            )
                        }
                    }

                    if (favoriteViewModel != null) {
                        val favoriteSectionOptions = remember {
                            FavoriteSection.entries.map { section ->
                                AppSegmentOption(value = section, label = section.label)
                            }
                        }
                        AppLiquidAwareTabRow(
                            options = favoriteSectionOptions,
                            selectedValue = favoriteSection,
                            onSelectionChange = { section ->
                                if (favoriteSection != section) {
                                    favoriteSection = section
                                    favoriteBrowseSection = FavoriteBrowseSection.OWNED
                                    searchQuery = ""
                                    isFavoriteBatchMode = false
                                    selectedFavoriteResourceIds = emptySet()
                                }
                            },
                            scrollable = FavoriteSection.entries.size > 4,
                            height = historyFilterChrome.heightDp.dp,
                            indicatorHeight = historyFilterChrome.indicatorHeightDp.dp,
                            labelFontSize = historyFilterChrome.labelFontSizeSp.sp,
                            dragSelectionEnabled = historyFilterChrome.dragSelectionEnabled,
                            tapPressRefractionEnabled = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacingTokens.Medium),
                            miuixBackdrop = commonListChromeBackdrop,
                        )
                        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                    }

                    if (
                        favoriteViewModel != null &&
                        isSearchDestination &&
                        favoriteSection == FavoriteSection.VIDEO
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacingTokens.Medium),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                        ) {
                            FavoriteSearchScope.entries.forEach { scopeOption ->
                                AppFilterChip(
                                    selected = favoriteSearchScope == scopeOption,
                                    onClick = { favoriteSearchScope = scopeOption },
                                    label = { AppText(scopeOption.label) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                    }

                    if (
                        favoriteViewModel != null &&
                        favoriteSection == FavoriteSection.VIDEO &&
                        (foldersState.isNotEmpty() || subscribedFoldersState.isNotEmpty())
                    ) {
                        FavoriteFolderSelector(
                            backdrop = commonListChromeBackdrop,
                            folders = foldersState,
                            selectedFolderIndex = selectedFolderIndex,
                            selectedFolderItems = selectedFolderUiState.items,
                            subscribedSelected = isSubscribedBrowse,
                            layout = favoriteHeaderLayout,
                            onFolderSelected = { index ->
                                favoriteBrowseSection = FavoriteBrowseSection.OWNED
                                favoriteViewModel.switchFolder(index)
                                searchQuery = ""
                            },
                            onSubscribedSelected = {
                                favoriteBrowseSection = FavoriteBrowseSection.SUBSCRIBED
                                isFavoriteBatchMode = false
                                selectedFavoriteResourceIds = emptySet()
                                searchQuery = ""
                            },
                        )
                    }

                    if (historyViewModel != null) {
                        if (isHistoryPaused) {
                            AppSurface(
                                onClick = historyViewModel::toggleHistoryPause,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacingTokens.Medium),
                                shape = AppShapes.container(ContainerLevel.Pill),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                AppText(
                                    text = "历史记录功能已关闭 · 点击开启",
                                    modifier = Modifier.padding(AppSpacingTokens.Medium),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                        }
                        val historyFilterOptions = remember {
                            HistoryContentFilter.entries.map { filter ->
                                AppSegmentOption(value = filter, label = filter.label)
                            }
                        }
                        val onHistoryFilterSelected: (HistoryContentFilter) -> Unit = { filter ->
                            if (filter != historyContentFilter) {
                                historyContentFilter = filter
                                selectedHistoryKeys = emptySet()
                                val targetPage = historyFilters.indexOf(filter).coerceAtLeast(0)
                                scope.launch {
                                    if (historyPagerState.currentPage != targetPage) {
                                        animatePagerSelection(historyPagerState, targetPage)
                                    }
                                    historyPagerGridStates[targetPage]?.scrollToItem(0)
                                }
                            }
                        }
                        AppThemeAdaptiveTabRow(
                            options = historyFilterOptions,
                            selectedValue = historyContentFilter,
                            onSelectionChange = onHistoryFilterSelected,
                            enabled = !isHistoryBatchMode,
                            scrollable = historyFilterChrome.itemWidthDp != null,
                            // Liquid mode has no fixed item width. Preserve the shared beta.21
                            // default instead of turning "unspecified" into an explicit 0.dp.
                            minTabWidth = historyFilterChrome.itemWidthDp?.dp ?: Dp.Unspecified,
                            height = historyFilterChrome.heightDp.dp,
                            indicatorHeight = historyFilterChrome.indicatorHeightDp.dp,
                            labelFontSize = historyFilterChrome.labelFontSizeSp.sp,
                            dragSelectionEnabled = historyFilterChrome.dragSelectionEnabled,
                            tapPressRefractionEnabled = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = historyFilterChrome.horizontalPaddingDp.dp),
                            miuixBackdrop = commonListChromeBackdrop,
                            indicatorPositionProvider = {
                                historyPagerState.currentPage + historyPagerState.currentPageOffsetFraction
                            },
                            isScrollInProgressProvider = { historyPagerState.isScrollInProgress },
                        )
                        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                    }

                    if (favoriteViewModel != null) {
                        Spacer(modifier = Modifier.height(favoriteHeaderLayout.headerBottomPaddingDp.dp))
                    }
                    },
                ) { measurables, constraints ->
                    val boundedMaxWidth = if (constraints.hasBoundedWidth) {
                        constraints.maxWidth
                    } else {
                        commonListViewportWidthPx
                    }
                    val childConstraints = constraints.copy(
                        minWidth = 0,
                        maxWidth = boundedMaxWidth,
                        minHeight = 0,
                    )
                    val placeables = measurables.map { it.measure(childConstraints) }
                    val width = placeables.maxOfOrNull { it.width }
                        ?.coerceIn(constraints.minWidth, boundedMaxWidth)
                        ?: constraints.minWidth
                    if (supportsCollapsibleCommonListHeader && placeables.isNotEmpty()) {
                        val titleHeight = placeables.first().height
                        val isSearchOnly = homeSettings.homeHeaderCollapseMode == HomeHeaderCollapseMode.SEARCH_ONLY &&
                            !hideListTopSearchBar
                        if (isSearchOnly && placeables.size >= 2 && commonListHeaderMaxCollapsePx > 0f) {
                            // 仅折叠搜索：标题栏停留在顶部，搜索行上滑折叠，标签页停在标题栏下方
                            val searchBarHeight = placeables[1].height
                            val collapseFraction = (-commonListHeaderOffsetPx / commonListHeaderMaxCollapsePx).coerceIn(0f, 1f)
                            val searchBarOffset = titleHeight - (collapseFraction * searchBarHeight).toInt()
                            val searchBarVisibleHeight = (searchBarHeight * (1f - collapseFraction)).toInt()
                            val dockTop = titleHeight + searchBarVisibleHeight
                            val remainingHeight = placeables.drop(2).sumOf { it.height }
                            val height = (dockTop + remainingHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
                            layout(width, height) {
                                placeables[0].placeRelative(0, 0)
                                placeables[1].placeRelative(0, searchBarOffset)
                                var y = dockTop
                                placeables.drop(2).forEach { placeable ->
                                    placeable.placeRelative(0, y)
                                    y += placeable.height
                                }
                            }
                        } else {
                            val floatingDockHeight = placeables.drop(1).sumOf { it.height }
                            val titleOffset = resolveHistoryTitleOffsetPx(
                                headerOffsetPx = commonListHeaderOffsetPx,
                                maxCollapsePx = commonListHeaderMaxCollapsePx,
                                titleHeightPx = titleHeight,
                            )
                            val floatingDockTop = (titleHeight + commonListHeaderOffsetPx)
                                .coerceAtLeast(statusBarHeightPx)
                                .toInt()
                            val height = (floatingDockTop + floatingDockHeight)
                                .coerceIn(constraints.minHeight, constraints.maxHeight)
                            layout(width, height) {
                                // 标题完整离场；Dock 仍只上移到状态栏安全区下方。
                                placeables.first().placeRelative(0, titleOffset)
                                var y = floatingDockTop
                                placeables.drop(1).forEach { placeable ->
                                    placeable.placeRelative(0, y)
                                    y += placeable.height
                                }
                            }
                        }
                    } else {
                        val height = placeables.sumOf { it.height }
                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                        layout(width, height) {
                            var y = 0
                            placeables.forEach { placeable ->
                                placeable.placeRelative(0, y)
                                y += placeable.height
                            }
                        }
                    }
                }
            }

            AppLiquidGlassBackToTopButton(
                visible = rememberBackToTopButtonEnabled() && shouldShowBackToTop,
                onClick = {
                    coroutineScope.launch {
                        scrollCommonListToTop()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, bottom = commonListBottomPadding + AppSpacingTokens.Medium),
                backdrop = commonListChromeBackdrop,
            )

            GridPinchColumnHudPill(
                visible = pinchPillVisible,
                columns = pinchListColumns,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (showHistoryBatchDeleteConfirm && historyViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { showHistoryBatchDeleteConfirm = false },
            title = { AppText("批量删除历史") },
            text = { AppText("确认删除已选择的 ${selectedHistoryKeys.size} 条历史记录吗？") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        val targetKeys = selectedHistoryKeys
                        when (resolveHistoryDeleteAnimationMode(targetKeys.size)) {
                            HistoryDeleteAnimationMode.SINGLE_DISSOLVE -> {
                                targetKeys.firstOrNull()?.let(historyViewModel::startVideoDissolve)
                            }
                            HistoryDeleteAnimationMode.DIRECT_DELETE -> {
                                // Batch selection may include off-screen items that never report animation completion.
                                historyViewModel.deleteHistoryItems(targetKeys)
                            }
                        }
                        selectedHistoryKeys = emptySet()
                        isHistoryBatchMode = false
                        showHistoryBatchDeleteConfirm = false
                    }
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showHistoryBatchDeleteConfirm = false }) {
                    AppText("取消")
                }
            }
        )
    }

    if (showFavoriteCleanInvalidConfirm && favoriteViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { showFavoriteCleanInvalidConfirm = false },
            title = { AppText("清理失效内容") },
            text = {
                AppText(
                    resolveFavoriteCleanInvalidConfirmText(
                        selectedFavoriteFolder?.title.orEmpty()
                    )
                )
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        favoriteViewModel.cleanInvalidResourcesInSelectedFolder()
                        showFavoriteCleanInvalidConfirm = false
                    }
                ) {
                    AppText("清理")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showFavoriteCleanInvalidConfirm = false }) {
                    AppText("取消")
                }
            }
        )
    }

    favoriteFolderEditorMode?.let { mode ->
        AppAlertDialog(
            onDismissRequest = { favoriteFolderEditorMode = null },
            title = { AppText(if (mode == "create") "新建收藏夹" else "编辑收藏夹") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)) {
                    AppTextField(
                        value = favoriteFolderEditorTitle,
                        onValueChange = { favoriteFolderEditorTitle = it },
                        label = "名称",
                        placeholder = "收藏夹名称",
                    )
                    AppTextField(
                        value = favoriteFolderEditorIntro,
                        onValueChange = { favoriteFolderEditorIntro = it },
                        label = "简介",
                        placeholder = "可选",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AppText("设为私密")
                        AppSwitch(
                            checked = favoriteFolderEditorPrivate,
                            onCheckedChange = { favoriteFolderEditorPrivate = it },
                        )
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = favoriteFolderEditorTitle.isNotBlank() && isFavoriteManaging.not(),
                    onClick = {
                        if (mode == "create") {
                            favoriteViewModel?.createFavoriteFolder(
                                title = favoriteFolderEditorTitle,
                                intro = favoriteFolderEditorIntro,
                                isPrivate = favoriteFolderEditorPrivate,
                            )
                        } else {
                            favoriteViewModel?.editSelectedFavoriteFolder(
                                title = favoriteFolderEditorTitle,
                                intro = favoriteFolderEditorIntro,
                                isPrivate = favoriteFolderEditorPrivate,
                            )
                        }
                        favoriteFolderEditorMode = null
                    },
                ) {
                    AppText("保存")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { favoriteFolderEditorMode = null }) {
                    AppText("取消")
                }
            },
        )
    }

    if (showFavoriteFolderDeleteConfirm && favoriteViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { showFavoriteFolderDeleteConfirm = false },
            title = { AppText("删除收藏夹") },
            text = { AppText("确认删除“${selectedFavoriteFolder?.title.orEmpty()}”吗？收藏内容不会从站点删除。") },
            confirmButton = {
                AppTextButton(
                    enabled = selectedFolderIndex > 0 && !isFavoriteManaging,
                    onClick = {
                        favoriteViewModel.deleteSelectedFavoriteFolder()
                        showFavoriteFolderDeleteConfirm = false
                    },
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showFavoriteFolderDeleteConfirm = false }) {
                    AppText("取消")
                }
            },
        )
    }

    if (showFavoriteDynamicShareConfirm && favoriteViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { showFavoriteDynamicShareConfirm = false },
            title = { AppText("分享至动态") },
            text = {
                AppText("将“${selectedFavoriteFolder?.title.orEmpty()}”作为收藏夹卡片发布到动态？")
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        favoriteViewModel.shareSelectedFolderToDynamic(
                            content = "分享收藏夹：${selectedFavoriteFolder?.title.orEmpty()}",
                        )
                        showFavoriteDynamicShareConfirm = false
                    }
                ) {
                    AppText("发布")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showFavoriteDynamicShareConfirm = false }) {
                    AppText("取消")
                }
            },
        )
    }

    if (showFavoriteBatchDeleteConfirm && favoriteViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { showFavoriteBatchDeleteConfirm = false },
            title = { AppText("批量删除收藏内容") },
            text = { AppText("确认移除已选择的 ${selectedFavoriteResourceIds.size} 个内容吗？") },
            confirmButton = {
                AppTextButton(
                    enabled = selectedFavoriteResourceIds.isNotEmpty(),
                    onClick = {
                        favoriteViewModel.deleteSelectedFavoriteResources(selectedFavoriteResourceIds)
                        selectedFavoriteResourceIds = emptySet()
                        isFavoriteBatchMode = false
                        showFavoriteBatchDeleteConfirm = false
                    },
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showFavoriteBatchDeleteConfirm = false }) {
                    AppText("取消")
                }
            },
        )
    }

    pendingFavoriteTransferCopy?.let { copy ->
        AppAlertDialog(
            onDismissRequest = { pendingFavoriteTransferCopy = null },
            title = { AppText(if (copy) "复制到收藏夹" else "移动到收藏夹") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                ) {
                    items(
                        items = foldersState.filterIndexed { index, _ -> index != selectedFolderIndex },
                        key = { folder -> resolveFavoriteFolderMediaId(folder) },
                    ) { folder ->
                        AppSurface(
                            onClick = {
                                favoriteViewModel?.copyOrMoveSelectedFavoriteResources(
                                    resourceIds = selectedFavoriteResourceIds,
                                    targetMediaId = resolveFavoriteFolderMediaId(folder),
                                    copy = copy,
                                )
                                selectedFavoriteResourceIds = emptySet()
                                isFavoriteBatchMode = false
                                pendingFavoriteTransferCopy = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.container(ContainerLevel.Card),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                        ) {
                            AppText(
                                text = folder.title,
                                modifier = Modifier.padding(AppSpacingTokens.Medium),
                            )
                        }
                    }
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingFavoriteTransferCopy = null }) {
                    AppText("取消")
                }
            },
        )
    }

    if (showHistoryClearConfirm && historyViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { showHistoryClearConfirm = false },
            title = { AppText("清空历史") },
            text = { AppText(resolveHistoryClearConfirmText(state.items.size)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        historyViewModel.clearAllHistory()
                        selectedHistoryKeys = emptySet()
                        isHistoryBatchMode = false
                        showHistoryClearConfirm = false
                    }
                ) {
                    AppText("清空")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showHistoryClearConfirm = false }) {
                    AppText("取消")
                }
            }
        )
    }

    if (pendingHistorySingleDeleteKey != null && historyViewModel != null) {
        AppAlertDialog(
            onDismissRequest = { pendingHistorySingleDeleteKey = null },
            title = { AppText("删除历史记录") },
            text = { AppText("确认删除这条历史记录吗？") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        pendingHistorySingleDeleteKey?.let { historyViewModel.startVideoDissolve(it) }
                        pendingHistorySingleDeleteKey = null
                    }
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingHistorySingleDeleteKey = null }) {
                    AppText("取消")
                }
            }
        )
    }
}

@Composable
private fun FavoriteFolderSelector(
    folders: List<com.android.purebilibili.data.model.response.FavFolder>,
    selectedFolderIndex: Int,
    selectedFolderItems: List<com.android.purebilibili.data.model.response.VideoItem>,
    subscribedSelected: Boolean,
    layout: CommonListFavoriteHeaderLayout,
    onFolderSelected: (Int) -> Unit,
    onSubscribedSelected: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
) {
    val selectedFolder = folders.getOrNull(selectedFolderIndex)
    if (selectedFolder == null && !subscribedSelected) return
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedPreviewCover = remember(selectedFolder?.cover, selectedFolderItems, subscribedSelected) {
        selectedFolder?.takeUnless { subscribedSelected }?.let { folder ->
            resolveFavoriteFolderPreviewCover(
                folder = folder,
                loadedItems = selectedFolderItems,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = layout.folderChipRowHorizontalPaddingDp.dp,
                end = layout.folderChipRowHorizontalPaddingDp.dp,
                top = layout.folderChipRowTopPaddingDp.dp,
            ),
    ) {
        BottomBarMatchedReusableLiquidDock(
            shape = AppShapes.container(ContainerLevel.Pill),
            modifier = Modifier.fillMaxWidth(),
            backdrop = backdrop,
            reuseEnabled = true,
            useNeutralLiquidContainer = true,
        ) { liquidChromeActive ->
            AppSurface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.container(ContainerLevel.Pill),
                color = if (liquidChromeActive) Color.Transparent else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
                        .padding(horizontal = layout.folderChipHorizontalPaddingDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(layout.folderChipSpacingDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FavoriteFolderChipPreview(
                        coverUrl = selectedPreviewCover,
                        selected = true,
                    )
                    AppText(
                        text = if (subscribedSelected) "追更（订阅）" else selectedFolder?.title.orEmpty(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AppText(
                        text = if (subscribedSelected) "1/${folders.size + 1}" else
                            "${selectedFolderIndex + 2}/${folders.size + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppIcon(
                        imageVector = rememberAppChevronDownIcon(),
                        contentDescription = "切换收藏夹",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 420.dp),
        ) {
            AppDropdownMenuItem(
                text = {
                    AppText(
                        text = "追更（订阅）",
                        fontWeight = if (subscribedSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    AppIcon(
                        imageVector = rememberAppBookmarkIcon(),
                        contentDescription = null,
                        tint = if (subscribedSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingIcon = if (subscribedSelected) {
                    {
                        AppIcon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "当前为追更",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    onSubscribedSelected()
                },
            )
            folders.forEachIndexed { index, folder ->
                val isSelected = !subscribedSelected && index == selectedFolderIndex
                AppDropdownMenuItem(
                    text = {
                        AppText(
                            text = folder.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        FavoriteFolderChipPreview(
                            coverUrl = resolveFavoriteFolderPreviewCover(
                                folder = folder,
                                loadedItems = if (isSelected) selectedFolderItems else emptyList(),
                            ),
                            selected = isSelected,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            AppIcon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "当前收藏夹",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onFolderSelected(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteFolderChipPreview(
    coverUrl: String?,
    selected: Boolean
) {
    Box(
        modifier = Modifier
            .size(AppSpacingTokens.ExtraLarge)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(coverUrl),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AppIcon(
                imageVector = rememberAppFolderIcon(),
                contentDescription = null,
                modifier = Modifier.size(AppSpacingTokens.Large - AppSpacingTokens.Micro / 2),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

// 提取通用列表内容组件
@Composable
private fun CommonListContent(
    items: List<com.android.purebilibili.data.model.response.VideoItem>,
    isLoading: Boolean,
    error: String?,
    searchQuery: String,
    columns: Int,
    isFavoritePersonalList: Boolean = false,
    favoriteBatchMode: Boolean = false,
    favoriteSelectedResourceIds: Set<Long> = emptySet(),
    onFavoriteToggleSelect: ((Long) -> Unit)? = null,
    onFavoriteLongPress: ((Long) -> Unit)? = null,
    spacing: androidx.compose.ui.unit.Dp,
    padding: PaddingValues,
    scrollUnderHeader: Boolean = false,
    cardAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    cardMotionTier: MotionTier,
    showOnlineCount: Boolean,
    videoCardAppearance: CommonListVideoCardAppearance,
    homeDurationStyle: HomeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
    onVideoClick: (String, Long, String, Boolean) -> Unit,
    onCollectionClick: ((FavoriteCollectionRoute) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onLoadMore: () -> Unit,
    onUnfavorite: ((com.android.purebilibili.data.model.response.VideoItem) -> Unit)?,
    historyDeleteSession: HistoryDeleteSession? = null,
    historyBatchMode: Boolean = false,
    historySelectedKeys: Set<String> = emptySet(),
    resolveHistoryItemKey: (com.android.purebilibili.data.model.response.VideoItem) -> String = { video ->
        video.bvid.ifBlank { video.id.toString() }
    },
    resolveHistoryLookupKey: ((com.android.purebilibili.data.model.response.VideoItem) -> String)? = null,
    resolveHistoryItem: ((com.android.purebilibili.data.model.response.VideoItem) -> HistoryItem?)? = null,
    onHistoryLongDelete: ((String) -> Unit)? = null,
    onHistoryDelete: ((String) -> Unit)? = null,
    onHistoryAddToWatchLater: ((HistoryItem) -> Unit)? = null,
    onHistoryDissolveComplete: ((String) -> Unit)? = null,
    onHistoryToggleSelect: ((String) -> Unit)? = null,
    onUpClick: ((Long) -> Unit)? = null,
    searchPaginationFallbackEnabled: Boolean = false,
    hasMoreSearchResults: Boolean = false,
    isLoadingMoreSearchResults: Boolean = false,
    pinchEnabled: Boolean = false,
    pinchBounds: IntRange = 1..1,
    onPinchColumnsChange: (Int) -> Unit = {},
    onPinchColumnsEnd: (Int) -> Unit = {},
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState? = null
) {
    val context = LocalContext.current
    val isHistoryPersonalList = resolveHistoryItem != null
    val isPersonalList = isHistoryPersonalList || isFavoritePersonalList
    val homeFeedCardStyle = if (isPersonalList) {
        HomeFeedCardStyle.BILIPAI
    } else {
        SettingsManager
            .getHomeFeedCardStyle(context)
            .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.BILIPAI)
            .value
    }
    val cardLayout = remember(homeFeedCardStyle) {
        com.android.purebilibili.feature.home.resolveHomeFeedCardLayout(homeFeedCardStyle)
    }
    val gridOuterPaddingDp = if (isPersonalList) 12 else cardLayout.outerPaddingDp
    val gridItemSpacingDp = if (isPersonalList) 12 else cardLayout.itemSpacingDp
    val skeletonCoverAspectRatio = if (isPersonalList) {
        com.android.purebilibili.feature.personal.PERSONAL_LIST_HORIZONTAL_COVER_ASPECT_RATIO
    } else {
        cardLayout.coverAspectRatio
    }
    val resolvedGridState = gridState ?: rememberLazyGridState()
    val fixedHeaderInset = resolveCommonListViewportTopPadding(padding.calculateTopPadding())
    val scrollableHeaderInset = if (scrollUnderHeader) fixedHeaderInset else AppSpacingTokens.None
    val viewportModifier = Modifier
        .fillMaxSize()
        .padding(top = if (scrollUnderHeader) AppSpacingTokens.None else fixedHeaderInset)
    val emptyViewportModifier = Modifier
        .fillMaxSize()
        .padding(top = fixedHeaderInset)
    if (isLoading && items.isEmpty()) {
        val historySkeletonBlockColor = if (isPersonalList) {
            com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor(
                com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonPulse()
            )
        } else {
            null
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = gridOuterPaddingDp.dp,
                end = gridOuterPaddingDp.dp,
                top = scrollableHeaderInset + gridOuterPaddingDp.dp,
                bottom = padding.calculateBottomPadding() + gridOuterPaddingDp.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(gridItemSpacingDp.dp),
            verticalArrangement = Arrangement.spacedBy(gridItemSpacingDp.dp),
            modifier = viewportModifier
        ) {
            items(columns * 4, key = { it }) {
                if (isHistoryPersonalList) {
                    HistoryPersonalCardSkeleton(blockColor = historySkeletonBlockColor)
                } else if (isFavoritePersonalList) {
                    FavoritePersonalCardSkeleton(stacked = columns > 1, blockColor = requireNotNull(historySkeletonBlockColor))
                } else {
                    VideoGridItemSkeleton(coverAspectRatio = skeletonCoverAspectRatio)
                }
            }
        }
    } else if (error != null && items.isEmpty()) {
        Column(
            modifier = emptyViewportModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppText(
                text = error,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                AppButton(onClick = onRetry) {
                    AppText("重试")
                }
            }
        }
    } else if (items.isEmpty()) {
        Box(modifier = emptyViewportModifier, contentAlignment = Alignment.Center) {
             AppText("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val filteredItems = androidx.compose.runtime.remember(items, searchQuery) {
            filterCommonListVideosByQuery(items, searchQuery)
        }
        val renderKeys = androidx.compose.runtime.remember(filteredItems) {
            resolveCommonListRenderKeys(filteredItems.map(resolveHistoryItemKey))
        }
        LaunchedEffect(
            searchPaginationFallbackEnabled,
            searchQuery,
            items.size,
            filteredItems.size,
            hasMoreSearchResults,
            isLoadingMoreSearchResults
        ) {
            if (
                searchPaginationFallbackEnabled &&
                shouldLoadMoreCommonListSearchResults(
                    searchQuery = searchQuery,
                    filteredItemCount = filteredItems.size,
                    hasMore = hasMoreSearchResults,
                    isLoadingMore = isLoadingMoreSearchResults
                )
            ) {
                onLoadMore()
            }
        }

        if (filteredItems.isEmpty() && searchQuery.isNotEmpty()) {
             Box(emptyViewportModifier, contentAlignment = Alignment.Center) {
                AppText("没有找到相关视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
             }
        } else {
            // 自动加载更多
            val shouldLoadMore = androidx.compose.runtime.remember(resolvedGridState) {
                androidx.compose.runtime.derivedStateOf {
                    val layoutInfo = resolvedGridState.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && last >= total - 4
                }
            }
            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value) onLoadMore()
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = resolvedGridState,
                contentPadding = PaddingValues(
                    start = gridOuterPaddingDp.dp,
                    end = gridOuterPaddingDp.dp,
                    top = scrollableHeaderInset + gridOuterPaddingDp.dp,
                    bottom = padding.calculateBottomPadding() + gridOuterPaddingDp.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(gridItemSpacingDp.dp),
                verticalArrangement = Arrangement.spacedBy(gridItemSpacingDp.dp),
                modifier = viewportModifier
                    .homeFeedPinchZoom(
                        enabled = pinchEnabled,
                        currentColumns = columns,
                        bounds = pinchBounds,
                        onColumnsChange = onPinchColumnsChange,
                        onGestureEnd = onPinchColumnsEnd,
                    )
            ) {
                 itemsIndexed(
                    items = filteredItems,
                    key = { index, _ -> renderKeys[index] },
                    span = { _, item ->
                        if (item.isCollectionResource) GridItemSpan(columns) else GridItemSpan(1)
                    }
                ) { index, video ->
                    AnimatedVideoListItem(modifier = videoListItemModifier(enabled = cardAnimationEnabled), enabled = cardAnimationEnabled) {
                        val historyKey = resolveHistoryItemKey(video)
                        val historyItem = resolveHistoryItem?.invoke(video)
                        val historyCardPresentation = remember(historyItem) {
                            resolveHistoryCardPresentation(historyItem)
                        }
                        val displayedVideo = historyCardPresentation?.videoItem ?: video
                        val supportsHistoryDissolve = onHistoryLongDelete != null && onHistoryDissolveComplete != null
                        val isDissolving = supportsHistoryDissolve &&
                            historyKey in resolveActiveHistoryDeleteKeys(historyDeleteSession)
                        val shouldKeepPlaceholderHidden = supportsHistoryDissolve &&
                            shouldKeepHistoryDeletePlaceholderHidden(historyDeleteSession, historyKey)
                        val isSelected = historyBatchMode && historyKey in historySelectedKeys
                        val historyDeleteAnimationMode = historyDeleteSession?.animationMode
                            ?: HistoryDeleteAnimationMode.SINGLE_DISSOLVE
                        val historySelectionShape = if (historyItem?.business == HistoryBusiness.ARTICLE) {
                            AppShapes.container(ContainerLevel.Sheet)
                        } else {
                            AppShapes.container(ContainerLevel.Card)
                        }

                        val cardContent: @Composable () -> Unit = {
                            Box {
                                if (video.isCollectionResource) {
                                    FavoriteCollectionRow(
                                        item = video,
                                        onClick = {
                                            resolveFavoriteCollectionRoute(video)?.let { route ->
                                                onCollectionClick?.invoke(route)
                                            }
                                        }
                                    )
                                } else if (historyItem != null) {
                                    HistoryPersonalCard(
                                        item = historyItem,
                                        stacked = columns > 1,
                                        selected = isSelected,
                                        batchMode = historyBatchMode,
                                        transitionEnabled = cardTransitionEnabled,
                                        onClick = {
                                            if (historyBatchMode) {
                                                onHistoryToggleSelect?.invoke(historyKey)
                                            } else {
                                                resolveCommonListVideoNavigationRequest(
                                                    video = video,
                                                    fallbackLookupKey = resolveHistoryLookupKey?.invoke(video)
                                                )?.let { request ->
                                                    onVideoClick(
                                                        request.lookupKey,
                                                        request.cid,
                                                        request.coverUrl,
                                                        request.isVertical
                                                    )
                                                }
                                            }
                                        },
                                        onLongClick = { onHistoryLongDelete?.invoke(historyKey) },
                                        onUpClick = historyItem.videoItem.owner.mid
                                            .takeIf { it > 0L }
                                            ?.let { mid -> { onUpClick?.invoke(mid) } },
                                        onAddToWatchLater = historyItem
                                            .takeIf(::canAddHistoryToWatchLater)
                                            ?.let { eligible -> { onHistoryAddToWatchLater?.invoke(eligible) } },
                                        onDelete = { onHistoryDelete?.invoke(historyKey) },
                                    )
                                } else if (isFavoritePersonalList) {
                                    FavoritePersonalCard(
                                        item = video,
                                        stacked = columns > 1,
                                        transitionEnabled = cardTransitionEnabled,
                                        batchMode = favoriteBatchMode,
                                        selected = video.id in favoriteSelectedResourceIds,
                                        canRemove = onUnfavorite != null,
                                        onClick = {
                                            if (favoriteBatchMode) {
                                                onFavoriteToggleSelect?.invoke(video.id)
                                            } else {
                                                resolveCommonListVideoNavigationRequest(
                                                    video = video,
                                                    fallbackLookupKey = resolveHistoryLookupKey?.invoke(video)
                                                )?.let { request ->
                                                    onVideoClick(
                                                        request.lookupKey,
                                                        request.cid,
                                                        request.coverUrl,
                                                        request.isVertical
                                                    )
                                                }
                                            }
                                        },
                                        onLongClick = { onFavoriteLongPress?.invoke(video.id) },
                                        onRemove = onUnfavorite?.let { remove ->
                                            { remove(video) }
                                        },
                                    )
                                } else {
                                    ElegantVideoCard(
                                        video = displayedVideo,
                                        singleColumn = columns == 1,
                                        index = index,
                                        animationEnabled = false, // The stable item wrapper owns column-switch motion.
                                        motionTier = cardMotionTier,
                                        transitionEnabled = cardTransitionEnabled,
                                        glassEnabled = videoCardAppearance.glassEnabled,
                                        blurEnabled = videoCardAppearance.blurEnabled,
                                        showCoverGlassBadges = videoCardAppearance.showCoverGlassBadges,
                                        showInfoGlassBadges = videoCardAppearance.showInfoGlassBadges,
                                        showUpBadge = historyCardPresentation?.showUpBadge,
                                        coverAspectRatio = cardLayout.coverAspectRatio,
                                        compactMetadata = cardLayout.compactMetadata,
                                        homeDurationStyle = homeDurationStyle,
                                        showOnlineCount = showOnlineCount,
                                        onClick = { _, _ ->
                                            if (historyBatchMode) {
                                                onHistoryToggleSelect?.invoke(historyKey)
                                            } else {
                                                resolveCommonListVideoNavigationRequest(
                                                    video = video,
                                                    fallbackLookupKey = resolveHistoryLookupKey?.invoke(video)
                                                )?.let { request ->
                                                    onVideoClick(
                                                        request.lookupKey,
                                                        request.cid,
                                                        request.coverUrl,
                                                        request.isVertical
                                                    )
                                                }
                                            }
                                        },
                                        onUnfavorite = if (onUnfavorite != null) { { onUnfavorite(video) } } else null,
                                        onUpClick = onUpClick,
                                        onLongClick = if (!historyBatchMode && supportsHistoryDissolve) {
                                            { onHistoryLongDelete(historyKey) }
                                        } else null
                                    )
                                }

                                if (historyBatchMode && historyItem == null) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .border(
                                                width = if (isSelected) AppSpacingTokens.Micro else AppSpacingTokens.Micro / 2,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                                },
                                                shape = historySelectionShape
                                            )
                                            .background(
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = historySelectionShape
                                            )
                                    )
                                    AppIcon(
                                        imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = if (isSelected) "已选择" else "未选择",
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(AppSpacingTokens.Small)
                                    )
                                }
                            }
                        }

                        if (supportsHistoryDissolve) {
                            MaybeDissolvableVideoCard(
                                isDissolving = isDissolving,
                                onDissolveComplete = { onHistoryDissolveComplete(historyKey) },
                                cardId = historyKey,
                                preset = DissolveAnimationPreset.TELEGRAM_FAST,
                                collapseAfterDissolve = shouldCollapseHistoryDeleteCard(historyDeleteAnimationMode),
                                publishGlobalDissolveState = shouldJiggleHistoryDeleteCards(historyDeleteAnimationMode),
                                keepInvisibleAfterDissolve = shouldKeepPlaceholderHidden ||
                                    historyDeleteAnimationMode == HistoryDeleteAnimationMode.DIRECT_DELETE,
                                modifier = Modifier.jiggleOnDissolve(
                                    cardId = historyKey,
                                    enabled = shouldJiggleHistoryDeleteCards(historyDeleteAnimationMode),
                                    isCurrentCardDissolving = isDissolving
                                )
                            ) {
                                cardContent()
                            }
                        } else {
                            cardContent()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun HistoryArticleCard(
    article: VideoItem,
    transitionEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val articleId = article.id.coerceAtLeast(0L)
    val coverTransitionKey = remember(articleId) {
        resolveArticleSharedTransitionKey(articleId, ArticleSharedElementSlot.COVER)
    }
    val cardBoundsRef = remember { object { var value: androidx.compose.ui.geometry.Rect? = null } }
    val triggerArticleClick = {
        cardBoundsRef.value?.let { bounds ->
            CardPositionManager.recordCardPosition(
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                density = density.density
            )
        }
        onClick()
    }
    val baseCoverModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(resolveHistoryArticleCoverAspectRatio())
    val coverModifier = if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null && articleId > 0L) {
        with(sharedTransitionScope) {
            baseCoverModifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = coverTransitionKey),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> commonListSharedBoundsMotionSpec() },
                clipInOverlayDuringTransition = OverlayClip(AppShapes.container(ContainerLevel.Sheet))
            )
        }
    } else {
        baseCoverModifier
    }
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                cardBoundsRef.value = coordinates.boundsInRoot()
            }
            .combinedClickable(
                onClick = triggerArticleClick,
                onLongClick = onLongClick
            ),
        shape = AppCardShape.Semantic(ContainerLevel.Sheet),
        colors = AppCardDefaults.colors(
            containerColor = AppSurfaceTokens.cardContainer()
        ),
        variant = AppCardVariant.Elevated,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, topEnd = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall))
            ) {
                AsyncImage(
                    model = article.pic,
                    contentDescription = article.title,
                    modifier = coverModifier,
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
            ) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Pill),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    AppText(
                        text = "专栏",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = AppSpacingTokens.Small + AppSpacingTokens.Micro, vertical = AppSpacingTokens.ExtraSmall)
                    )
                }
                AppText(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                AppText(
                    text = article.owner.name.ifBlank { "未知作者" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FavoriteSubscribedFolderList(
    folders: List<com.android.purebilibili.data.model.response.FavFolder>,
    searchQuery: String,
    padding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    spacing: androidx.compose.ui.unit.Dp,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    transitionEnabled: Boolean,
    onLoadMore: () -> Unit,
    onFolderClick: (com.android.purebilibili.data.model.response.FavFolder) -> Unit
) {
    if (folders.isEmpty()) {
        val message = if (searchQuery.isNotBlank()) "没有找到相关追更" else "暂无追更合集"
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppText(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val shouldLoadMore = androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value, hasMore, isLoadingMore) {
        if (shouldLoadMore.value && hasMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        modifier = Modifier
            .responsiveContentWidth(resolveCommonListSingleColumnMaxWidth())
            .fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = spacing,
            end = spacing,
            top = padding.calculateTopPadding() + spacing,
            bottom = padding.calculateBottomPadding() + spacing + AppSpacingTokens.ExtraLarge
        ),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(items = folders, key = { "favorite_subscribed_${it.id}_${it.fid}" }) { folder ->
            FavoriteSubscribedFolderRow(
                folder = folder,
                transitionEnabled = transitionEnabled,
                onClick = { onFolderClick(folder) }
            )
        }
    }
}

private sealed interface CommonListScrollState {
    data class Grid(val state: androidx.compose.foundation.lazy.grid.LazyGridState) : CommonListScrollState
    data class List(val state: androidx.compose.foundation.lazy.LazyListState) : CommonListScrollState
}

@Composable
private fun FavoriteSubscribedFolderRow(
    folder: com.android.purebilibili.data.model.response.FavFolder,
    transitionEnabled: Boolean,
    onClick: () -> Unit
) {
    val sharedElementRoute = remember(folder) {
        resolveSubscribedFavoriteFolderRoute(folder)
    }
    val previewCover = remember(folder.cover) {
        resolveFavoriteFolderPreviewCover(folder, emptyList())
    }
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .favoriteCollectionSharedBounds(
                route = sharedElementRoute,
                transitionEnabled = transitionEnabled
            )
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        shape = AppShapes.container(ContainerLevel.Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacingTokens.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FavoriteSubscribedFolderPreview(
                coverUrl = previewCover,
                title = folder.title
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = folder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                AppText(
                    text = "${folder.media_count} 个内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
            AppAssistChip(
                onClick = onClick,
                label = { AppText("订阅") }
            )
        }
    }
}

@Composable
private fun FavoriteSubscribedFolderPreview(
    coverUrl: String?,
    title: String
) {
    val shape = AppShapes.mediaCover()
    Box(
        modifier = Modifier
            .width(resolveFavoriteSubscribedFolderPreviewWidth())
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(coverUrl),
                contentDescription = "$title 最新视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AppIcon(
                imageVector = rememberAppFolderIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.favoriteCollectionSharedBounds(
    route: FavoriteCollectionRoute?,
    transitionEnabled: Boolean
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedElementId = remember(route?.type, route?.id) {
        route?.let { resolveFavoriteCollectionSharedElementId(it.type, it.id) }
    }
    if (
        !transitionEnabled ||
        route?.sharedElementTransition != true ||
        sharedElementId == null ||
        sharedTransitionScope == null ||
        animatedVisibilityScope == null
    ) {
        return this
    }
    val sharedElementKey = remember(sharedElementId) {
        BiliPaiSharedElementKey.Raw(
            namespace = "favorite_collection",
            id = sharedElementId
        )
    }
    return with(sharedTransitionScope) {
        this@favoriteCollectionSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> commonListSharedBoundsMotionSpec() },
            clipInOverlayDuringTransition = OverlayClip(AppShapes.container(ContainerLevel.Card))
        )
    }
}

@Composable
private fun FavoriteCollectionRow(
    item: com.android.purebilibili.data.model.response.VideoItem,
    onClick: () -> Unit
) {
    val subtitleParts = remember(item.owner.name, item.collectionMediaCount, item.collectionSubtitle) {
        buildList {
            item.owner.name.takeIf { it.isNotBlank() }?.let(::add)
            item.collectionMediaCount.takeIf { it > 0 }?.let { add("${it} 个视频") }
            item.collectionSubtitle.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    val subtitle = remember(subtitleParts) { subtitleParts.joinToString(separator = " · ") }

    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = AppShapes.container(ContainerLevel.Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacingTokens.Medium + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Medium + AppSpacingTokens.Micro),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = rememberAppFolderIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
            AppAssistChip(
                onClick = onClick,
                label = { AppText("合集") }
            )
        }
    }
}
