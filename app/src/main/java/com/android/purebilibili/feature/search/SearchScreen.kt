// 文件路径: feature/search/SearchScreen.kt
package com.android.purebilibili.feature.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.OfficialVerifyBadge
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.ui.resolveCompactCapsuleChromeSpec
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppHistoryIcon
import com.android.purebilibili.core.ui.rememberAppSearchIcon
import com.android.purebilibili.core.ui.resolveOfficialVerifyBadge
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.components.shouldUseNativeMiuixSearchBar
import top.yukonga.miuix.kmp.basic.InputField
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard  //  使用首页卡片
import com.android.purebilibili.feature.home.resolveHomeFeedCardLayout
import com.android.purebilibili.feature.home.resolveReturnAnimationSuppressionDurationMs
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.SettingsManager  //  读取动画设置
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.SearchOrderSort
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import com.android.purebilibili.data.repository.resolveSearchDurationFilterLabel
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
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.SearchArticleItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import com.android.purebilibili.data.model.response.SearchPhotoItem
import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.data.model.response.SearchTopicItem
import kotlinx.coroutines.launch

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

internal const val SEARCH_TOP_BAR_VERTICAL_PADDING_DP = 16

internal fun resolveSearchTopBarRowMinHeightDp(
    inputHeightDp: Int,
    verticalPaddingDp: Int = SEARCH_TOP_BAR_VERTICAL_PADDING_DP
): Int = maxOf(64, inputHeightDp + verticalPaddingDp)

internal fun shouldOmitSearchInputLeadingIcon(
    uiPreset: UiPreset,
    usesMiuixSearchInput: Boolean
): Boolean = uiPreset == UiPreset.MD3 && !usesMiuixSearchInput

internal fun resolveSearchTopBarHeaderColor(
    surfaceColor: Color,
    backgroundAlpha: Float,
    globalWallpaperVisible: Boolean,
    useHeaderBlur: Boolean
): Color {
    return if (globalWallpaperVisible) {
        val protectiveColor = resolveGlobalWallpaperProtectiveColor(surfaceColor)
        protectiveColor.copy(alpha = maxOf(protectiveColor.alpha, backgroundAlpha))
    } else if (useHeaderBlur) {
        Color.Transparent
    } else {
        surfaceColor.copy(alpha = backgroundAlpha)
    }
}

internal fun shouldUseSearchTopBarHeaderBlur(
    hazeSourceEnabled: Boolean,
    globalWallpaperVisible: Boolean
): Boolean = hazeSourceEnabled && !globalWallpaperVisible

internal data class SearchChromeVisualSpec(
    val inputHeightDp: Int,
    val inputCornerRadiusDp: Int,
    val actionContainerCornerRadiusDp: Int,
    val useFilledSearchAction: Boolean,
    val suggestionContainerCornerRadiusDp: Int,
    val clearActionSizeDp: Int,
    val submitActionSizeDp: Int,
    val actionIconSizeDp: Int,
    val horizontalGapDp: Int,
    val inputHorizontalPaddingDp: Int,
    val chipHeightDp: Int,
    val compactChipHeightDp: Int,
    val chipCornerRadiusDp: Int,
    val chipHorizontalPaddingDp: Int
)

internal fun resolveSearchChromeVisualSpec(
    uiPreset: UiPreset,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): SearchChromeVisualSpec {
    val compactChrome = resolveCompactCapsuleChromeSpec(uiPreset, androidNativeVariant)
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        SearchChromeVisualSpec(
            inputHeightDp = compactChrome.primaryHeightDp,
            inputCornerRadiusDp = compactChrome.primaryCornerRadiusDp,
            actionContainerCornerRadiusDp = compactChrome.secondaryButtonCornerRadiusDp,
            useFilledSearchAction = true,
            suggestionContainerCornerRadiusDp = 18,
            clearActionSizeDp = compactChrome.secondaryButtonSizeDp,
            submitActionSizeDp = compactChrome.secondaryButtonSizeDp,
            actionIconSizeDp = compactChrome.iconSizeDp,
            horizontalGapDp = compactChrome.standardGapDp,
            inputHorizontalPaddingDp = compactChrome.inputHorizontalPaddingDp,
            chipHeightDp = compactChrome.chipHeightDp,
            compactChipHeightDp = compactChrome.compactChipHeightDp,
            chipCornerRadiusDp = compactChrome.chipCornerRadiusDp,
            chipHorizontalPaddingDp = compactChrome.chipHorizontalPaddingDp
        )
    } else if (uiPreset == UiPreset.MD3) {
        SearchChromeVisualSpec(
            inputHeightDp = compactChrome.primaryHeightDp,
            inputCornerRadiusDp = compactChrome.primaryCornerRadiusDp,
            actionContainerCornerRadiusDp = compactChrome.secondaryButtonCornerRadiusDp,
            useFilledSearchAction = true,
            suggestionContainerCornerRadiusDp = 20,
            clearActionSizeDp = compactChrome.secondaryButtonSizeDp,
            submitActionSizeDp = compactChrome.secondaryButtonSizeDp,
            actionIconSizeDp = compactChrome.iconSizeDp,
            horizontalGapDp = compactChrome.standardGapDp,
            inputHorizontalPaddingDp = compactChrome.inputHorizontalPaddingDp,
            chipHeightDp = compactChrome.chipHeightDp,
            compactChipHeightDp = compactChrome.compactChipHeightDp,
            chipCornerRadiusDp = compactChrome.chipCornerRadiusDp,
            chipHorizontalPaddingDp = compactChrome.chipHorizontalPaddingDp
        )
    } else {
        SearchChromeVisualSpec(
            inputHeightDp = compactChrome.primaryHeightDp,
            inputCornerRadiusDp = compactChrome.primaryCornerRadiusDp,
            actionContainerCornerRadiusDp = compactChrome.secondaryButtonCornerRadiusDp,
            useFilledSearchAction = false,
            suggestionContainerCornerRadiusDp = 12,
            clearActionSizeDp = compactChrome.secondaryButtonSizeDp,
            submitActionSizeDp = compactChrome.secondaryButtonSizeDp,
            actionIconSizeDp = compactChrome.iconSizeDp,
            horizontalGapDp = compactChrome.standardGapDp,
            inputHorizontalPaddingDp = compactChrome.inputHorizontalPaddingDp,
            chipHeightDp = compactChrome.chipHeightDp,
            compactChipHeightDp = compactChrome.compactChipHeightDp,
            chipCornerRadiusDp = compactChrome.chipCornerRadiusDp,
            chipHorizontalPaddingDp = compactChrome.chipHorizontalPaddingDp
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

internal fun shouldResetSearchResultScroll(
    searchSessionId: Long,
    showResults: Boolean,
    lastResetSessionId: Long
): Boolean {
    return showResults && searchSessionId > 0L && searchSessionId != lastResetSessionId
}

internal fun shouldShowSearchBackToTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    offsetThresholdPx: Int = 280
): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= offsetThresholdPx
}

internal fun resolveSearchSubmitKeyword(
    query: String,
    suggestedKeyword: String
): String {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isNotBlank()) return normalizedQuery
    return suggestedKeyword.trim()
}

internal enum class SearchFilterControl {
    VIDEO_ORDER,
    VIDEO_DURATION,
    VIDEO_TID,
    UP_ORDER,
    UP_ORDER_SORT,
    UP_USER_TYPE,
    LIVE_ORDER
}

internal fun resolveSearchFilterTabs(): List<SearchType> {
    return listOf(
        SearchType.VIDEO,
        SearchType.BANGUMI,
        SearchType.MEDIA_FT,
        SearchType.LIVE,
        SearchType.UP,
        SearchType.ARTICLE
    )
}

internal fun resolveSearchDefaultPlaceholder(): String {
    return "搜索视频、番剧、影视、直播、UP主、专栏..."
}

internal fun resolveSearchUpUserTypeFilterLabel(userType: SearchUserType): String {
    return when (userType) {
        SearchUserType.ALL -> "用户类型"
        else -> userType.displayName
    }
}

internal fun resolveSearchTypeForPagerPage(
    page: Int,
    tabs: List<SearchType> = resolveSearchFilterTabs()
): SearchType {
    return tabs.getOrNull(page) ?: SearchType.VIDEO
}

internal fun resolveSearchPagerPageForType(
    currentType: SearchType,
    tabs: List<SearchType> = resolveSearchFilterTabs()
): Int {
    return tabs.indexOf(currentType).takeIf { it >= 0 } ?: 0
}

internal fun resolveSearchResultPageState(
    state: SearchUiState,
    searchType: SearchType
): SearchResultPageUiState {
    return if (state.searchType == searchType) {
        state.toCurrentSearchResultPage()
    } else {
        state.resultPages[searchType] ?: SearchResultPageUiState(query = state.query.trim())
    }
}

internal fun resolveSearchFilterControls(
    currentType: SearchType,
    currentUpOrder: SearchUpOrder
): List<SearchFilterControl> {
    return when (currentType) {
        SearchType.VIDEO -> listOf(
            SearchFilterControl.VIDEO_ORDER,
            SearchFilterControl.VIDEO_DURATION,
            SearchFilterControl.VIDEO_TID
        )
        SearchType.UP -> buildList {
            add(SearchFilterControl.UP_ORDER)
            if (currentUpOrder != SearchUpOrder.DEFAULT) {
                add(SearchFilterControl.UP_ORDER_SORT)
            }
            add(SearchFilterControl.UP_USER_TYPE)
        }
        SearchType.LIVE -> listOf(SearchFilterControl.LIVE_ORDER)
        SearchType.BANGUMI,
        SearchType.MEDIA_FT,
        SearchType.LIVE_USER,
        SearchType.ARTICLE,
        SearchType.TOPIC,
        SearchType.PHOTO -> emptyList()
    }
}

internal fun resolveSearchResultLazyItemKey(
    searchType: SearchType,
    index: Int,
    textKey: String = "",
    numericKey: Long = 0L,
    secondaryNumericKey: Long = 0L
): String {
    val normalizedTextKey = textKey.trim()
    return when {
        normalizedTextKey.isNotEmpty() -> "${searchType.value}:$index:text:$normalizedTextKey"
        numericKey > 0L -> "${searchType.value}:$index:id:$numericKey"
        secondaryNumericKey > 0L -> "${searchType.value}:$index:secondary:$secondaryNumericKey"
        else -> "${searchType.value}:local:$index"
    }
}

internal data class SearchHighlightedTextSegment(
    val text: String,
    val highlighted: Boolean
)

internal fun resolveSearchHighlightedTextSegments(rawTitle: String): List<SearchHighlightedTextSegment> {
    if (rawTitle.isBlank()) return emptyList()
    val segments = mutableListOf<SearchHighlightedTextSegment>()
    val regex = Regex("<em[^>]*>(.*?)</em>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    var cursor = 0
    regex.findAll(rawTitle).forEach { match ->
        if (match.range.first > cursor) {
            val plain = decodeSearchHighlightedText(rawTitle.substring(cursor, match.range.first))
            if (plain.isNotEmpty()) {
                segments += SearchHighlightedTextSegment(plain, highlighted = false)
            }
        }
        val highlighted = decodeSearchHighlightedText(match.groupValues.getOrElse(1) { "" })
        if (highlighted.isNotEmpty()) {
            segments += SearchHighlightedTextSegment(highlighted, highlighted = true)
        }
        cursor = match.range.last + 1
    }
    if (cursor < rawTitle.length) {
        val plain = decodeSearchHighlightedText(rawTitle.substring(cursor))
        if (plain.isNotEmpty()) {
            segments += SearchHighlightedTextSegment(plain, highlighted = false)
        }
    }
    return segments
}

private fun decodeSearchHighlightedText(raw: String): String {
    return raw.replace(Regex("<.*?>"), "")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

internal data class SearchTypeTabLayoutSpec(
    val horizontalSpacingDp: Int,
    val verticalSpacingDp: Int,
    val horizontalPaddingDp: Int,
    val minHeightDp: Int,
    val fontSizeSp: Int
)

internal fun resolveSearchTypeTabLayoutSpec(widthDp: Int): SearchTypeTabLayoutSpec {
    return if (widthDp < 400) {
        SearchTypeTabLayoutSpec(
            horizontalSpacingDp = 6,
            verticalSpacingDp = 6,
            horizontalPaddingDp = 10,
            minHeightDp = 36,
            fontSizeSp = 13
        )
    } else {
        SearchTypeTabLayoutSpec(
            horizontalSpacingDp = 8,
            verticalSpacingDp = 8,
            horizontalPaddingDp = 16,
            minHeightDp = 40,
            fontSizeSp = 14
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    userFace: String = "",
    initialKeyword: String = "",
    onInitialKeywordConsumed: (String) -> Unit = {},
    onBack: () -> Unit,
    onOpenTrending: () -> Unit,
    onVideoClick: (String, Long) -> Unit,
    onWebClick: (String, String) -> Unit,
    onUpClick: (Long) -> Unit,  //  点击UP主跳转到空间
    onBangumiClick: (Long) -> Unit, //  点击番剧/影视跳转详情
    onLiveClick: (Long, String, String) -> Unit, // [新增] 直播点击
    onTopicClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onAvatarClick: () -> Unit,
    entryMotionSource: SearchEntryMotionSource = SearchEntryMotionSource.NONE,
    entryMotionKey: Int = 0,
    onEntryMotionConsumed: (Int) -> Unit = {},
    isReturningFromVideoDetail: Boolean = false,
    isQuickReturningFromVideoDetail: Boolean = false,
    onVideoDetailReturnAnimationConsumed: () -> Unit = {}
) {
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val searchChromeSpec = remember(uiPreset, androidNativeVariant) {
        resolveSearchChromeVisualSpec(uiPreset, androidNativeVariant)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    var startupSettled by remember { mutableStateOf(false) }
    val searchLayoutPolicy = remember(configuration.screenWidthDp) {
        resolveSearchLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    
    //  自动聚焦搜索框
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // 1. 滚动状态监听 (用于列表)
    val historyListState = rememberLazyListState()
    val resultStateKey = remember(state.searchSessionId, state.searchType) {
        state.searchSessionId to state.searchType
    }
    val resultGridState = rememberSaveable(resultStateKey, saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val resultListState = rememberSaveable(resultStateKey, saver = LazyListState.Saver) {
        LazyListState()
    }
    var lastResetSearchSessionId by rememberSaveable { mutableLongStateOf(0L) }
    val shouldShowBackToTop by remember(
        state.showResults,
        state.isSearching,
        state.searchType,
        resultGridState,
        resultListState
    ) {
        derivedStateOf {
            state.showResults &&
                !state.isSearching &&
                if (state.searchType == SearchType.VIDEO) {
                    shouldShowSearchBackToTop(
                        firstVisibleItemIndex = resultGridState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = resultGridState.firstVisibleItemScrollOffset
                    )
                } else {
                    shouldShowSearchBackToTop(
                        firstVisibleItemIndex = resultListState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = resultListState.firstVisibleItemScrollOffset
                    )
                }
        }
    }

    LaunchedEffect(resultStateKey, state.showResults) {
        if (!shouldResetSearchResultScroll(
                searchSessionId = state.searchSessionId,
                showResults = state.showResults,
                lastResetSessionId = lastResetSearchSessionId
            )
        ) {
            return@LaunchedEffect
        }
        resultListState.scrollToItem(0)
        resultGridState.scrollToItem(0)
        lastResetSearchSessionId = state.searchSessionId
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
    val searchTabs = remember { resolveSearchFilterTabs() }
    val searchPagerState = rememberPagerState(
        initialPage = resolveSearchPagerPageForType(state.searchType, searchTabs),
        pageCount = { searchTabs.size }
    )
    var scrollToTopSearchType by remember { mutableStateOf<SearchType?>(null) }
    var scrollToTopRequestId by remember { mutableIntStateOf(0) }
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardAnimationEnabled by SettingsManager.getCardAnimationEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val hotSearchEnabled by SettingsManager.getSearchHotSectionEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val discoverSectionEnabled by SettingsManager.getSearchDiscoverSectionEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val liquidGlassEnabled by SettingsManager.getLiquidGlassEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val androidNativeLiquidGlassEnabled by SettingsManager
        .getAndroidNativeLiquidGlassEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val headerBlurEnabled by SettingsManager.getHeaderBlurEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val bottomBarBlurEnabled by SettingsManager.getBottomBarBlurEnabled(context).collectAsStateWithLifecycle(initialValue = true)
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
        uiPreset,
        androidNativeLiquidGlassEnabled
    ) {
        resolveSearchVideoCardAppearance(
            liquidGlassEnabled = liquidGlassEnabled,
            blurEnabled = searchCardBlurEnabled,
            showHomeCoverGlassBadges = false,
            showHomeInfoGlassBadges = false,
            uiPreset = uiPreset,
            androidNativeLiquidGlassEnabled = androidNativeLiquidGlassEnabled
        )
    }
    val genericResultCardAppearance = remember(
        liquidGlassEnabled,
        uiPreset,
        androidNativeLiquidGlassEnabled,
        androidNativeVariant
    ) {
        resolveSearchResultCardAppearance(
            liquidGlassEnabled = liquidGlassEnabled,
            uiPreset = uiPreset,
            androidNativeLiquidGlassEnabled = androidNativeLiquidGlassEnabled,
            androidNativeVariant = androidNativeVariant
        )
    }
    val cardTransitionEnabled by SettingsManager.getCardTransitionEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val showOnlineCount by SettingsManager.getShowOnlineCount(context).collectAsStateWithLifecycle(initialValue = false)
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.OFFICIAL)
    val cardLayout = remember(homeFeedCardStyle) {
        resolveHomeFeedCardLayout(homeFeedCardStyle)
    }
    val isSearchResultsScrolling by remember(historyListState, resultGridState, resultListState, searchPagerState) {
        derivedStateOf {
            historyListState.isScrollInProgress ||
                resultGridState.isScrollInProgress ||
                resultListState.isScrollInProgress ||
                searchPagerState.isScrollInProgress
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
    val effectiveSearchMotionBudget = remember(startupSettled, searchMotionBudget) {
        resolveEffectiveSearchMotionBudget(
            startupSettled = startupSettled,
            baseBudget = searchMotionBudget
        )
    }
    val entryMotionSpec = resolveSearchEntryMotionSpec(
        source = entryMotionSource,
        reducedMotionBudget = searchMotionBudget == SearchMotionBudget.REDUCED
    )
    val searchHazeEnabled = shouldEnableSearchHazeSource(
        isSearching = state.isSearching,
        startupSettled = startupSettled
    )
    val effectiveCardTransitionEnabled = resolveEffectiveSearchCardTransitionEnabled(
        cardTransitionEnabled = cardTransitionEnabled,
        motionBudget = effectiveSearchMotionBudget,
        isReturningFromVideoDetail = isReturningFromVideoDetail
    )
    val returnAnimationSuppressionDurationMs = resolveReturnAnimationSuppressionDurationMs(
        isTabletLayout = windowSizeClass.isTablet,
        cardAnimationEnabled = cardAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled,
        isQuickReturnFromDetail = isQuickReturningFromVideoDetail
    )
    LaunchedEffect(returnAnimationSuppressionDurationMs, isReturningFromVideoDetail) {
        if (!isReturningFromVideoDetail) return@LaunchedEffect
        kotlinx.coroutines.delay(returnAnimationSuppressionDurationMs)
        onVideoDetailReturnAnimationConsumed()
    }
    val forceLowBudgetSearchHeaderBlur = remember(state.isSearching, isSearchResultsScrolling) {
        shouldForceLowBudgetSearchHeaderBlur(
            isSearching = state.isSearching,
            isScrollingResults = isSearchResultsScrolling
        )
    }
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val shouldUseSearchTopBarBlur = shouldUseSearchTopBarHeaderBlur(
        hazeSourceEnabled = searchHazeEnabled,
        globalWallpaperVisible = globalWallpaperVisible
    )
    val searchTopBarHeaderColor = resolveSearchTopBarHeaderColor(
        surfaceColor = MaterialTheme.colorScheme.surface,
        backgroundAlpha = 0.96f,
        globalWallpaperVisible = globalWallpaperVisible,
        useHeaderBlur = shouldUseSearchTopBarBlur
    )
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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(140)
        startupSettled = true
    }

    LaunchedEffect(state.searchType, searchTabs) {
        val targetPage = resolveSearchPagerPageForType(state.searchType, searchTabs)
        if (!searchPagerState.isScrollInProgress && searchPagerState.currentPage != targetPage) {
            searchPagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(searchPagerState, searchTabs, state.showResults) {
        snapshotFlow { searchPagerState.settledPage }
            .collect { page ->
                if (state.showResults) {
                    viewModel.setSearchType(resolveSearchTypeForPagerPage(page, searchTabs))
                }
            }
    }

    LaunchedEffect(startupSettled, state.showResults, state.query) {
        if (shouldBootstrapSearchLandingData(
                startupSettled = startupSettled,
                showResults = state.showResults,
                query = state.query
            )
        ) {
            viewModel.ensureLandingBootstrap()
        }
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

    AdaptiveScaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        //  移除 bottomBar，搜索栏现在位于顶部 Box 中
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .globalWallpaperAwareBackground()
                .padding(padding)
        ) {
            // --- 列表内容层 ---
            if (state.showResults) {
                if (state.error != null) {
                    Text(
                        text = state.error ?: "未知错误",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                        ) {
                            Spacer(modifier = Modifier.height(contentTopPadding + 8.dp))
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
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                            SearchResultTypeTabRow(
                                tabs = searchTabs,
                                pagerState = searchPagerState,
                                onTabClick = { page, type ->
                                    if (searchPagerState.currentPage == page && state.searchType == type) {
                                        scrollToTopSearchType = type
                                        scrollToTopRequestId += 1
                                    } else {
                                        scope.launch { searchPagerState.animateScrollToPage(page) }
                                    }
                                }
                            )
                            val showStableFilterBar = resolveSearchFilterControls(
                                currentType = state.searchType,
                                currentUpOrder = state.upOrder
                            ).isNotEmpty()
                            AnimatedVisibility(
                                visible = showStableFilterBar,
                                enter = fadeIn(animationSpec = tween(90)),
                                exit = fadeOut(animationSpec = tween(70))
                            ) {
                                SearchFilterBar(
                                    currentType = state.searchType,
                                    currentOrder = state.searchOrder,
                                    currentDurations = state.searchDurations,
                                    currentVideoTid = state.videoTid,
                                    currentUpOrder = state.upOrder,
                                    currentUpOrderSort = state.upOrderSort,
                                    currentUpUserType = state.upUserType,
                                    currentLiveOrder = state.liveOrder,
                                    onOrderChange = { viewModel.setSearchOrder(it) },
                                    onDurationToggle = { viewModel.toggleSearchDuration(it) },
                                    onVideoTidChange = { viewModel.setVideoTid(it) },
                                    onUpOrderChange = { viewModel.setUpOrder(it) },
                                    onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                    onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                    onLiveOrderChange = { viewModel.setLiveOrder(it) }
                                )
                            }
                        HorizontalPager(
                            state = searchPagerState,
                            modifier = Modifier.weight(1f),
                            beyondViewportPageCount = 1
                        ) { page ->
                        val targetSearchType = resolveSearchTypeForPagerPage(page, searchTabs)
                        val pageResultState = resolveSearchResultPageState(
                            state = state,
                            searchType = targetSearchType
                        )
                        val pageEmptyStateCopy = remember(pageResultState.emptyStateReason, targetSearchType) {
                            if (pageResultState.emptyStateReason == SearchEmptyStateReason.NONE) {
                                null
                            } else {
                                resolveSearchEmptyStateCopy(
                                    reason = pageResultState.emptyStateReason,
                                    searchType = targetSearchType
                                )
                            }
                        }
                        val pageGridState = rememberSaveable(
                            pageResultState.query,
                            targetSearchType.value,
                            saver = LazyGridState.Saver
                        ) {
                            LazyGridState()
                        }
                        val pageListState = rememberSaveable(
                            pageResultState.query,
                            targetSearchType.value,
                            saver = LazyListState.Saver
                        ) {
                            LazyListState()
                        }
                        LaunchedEffect(scrollToTopRequestId, scrollToTopSearchType, targetSearchType) {
                            if (scrollToTopSearchType == targetSearchType && scrollToTopRequestId > 0) {
                                if (targetSearchType == SearchType.VIDEO) {
                                    pageGridState.animateScrollToItem(0)
                                } else {
                                    pageListState.animateScrollToItem(0)
                                }
                            }
                        }
                        if (pageResultState.isSearching) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingAnimation(
                                    size = 80.dp,
                                    text = "搜索中..."
                                )
                            }
                        } else {
                        when (targetSearchType) {
                            com.android.purebilibili.data.model.response.SearchType.VIDEO -> {
                                // 视频搜索结果
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = searchLayoutPolicy.resultGridMinItemWidthDp.dp),
                                    state = pageGridState,
                                    contentPadding = PaddingValues(
                                        top = 0.dp,
                                        bottom = resultBottomPadding,
                                        start = cardLayout.outerPaddingDp.dp,
                                        end = cardLayout.outerPaddingDp.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp),
                                    verticalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                        ) {
                                itemsIndexed(
                                    pageResultState.searchResults,
                                    key = { index, video ->
                                        resolveSearchResultLazyItemKey(
                                            searchType = SearchType.VIDEO,
                                            index = index,
                                            textKey = video.bvid,
                                            numericKey = video.id
                                        )
                                    }
                                ) { index, video ->
                                        val highlightedTitle = rememberSearchHighlightedTitle(video)
                                        ElegantVideoCard(
                                            video = video,
                                            index = index,
                                            animationEnabled = cardAnimationEnabled,
                                            motionTier = cardMotionTier,
                                            transitionEnabled = effectiveCardTransitionEnabled,
                                            isReturningFromVideoDetail = isReturningFromVideoDetail,
                                            isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
                                            showPublishTime = true,
                                            glassEnabled = videoCardAppearance.glassEnabled,
                                            blurEnabled = videoCardAppearance.blurEnabled,
                                            showCoverGlassBadges = videoCardAppearance.showCoverGlassBadges,
                                            showInfoGlassBadges = videoCardAppearance.showInfoGlassBadges,
                                            coverAspectRatio = cardLayout.coverAspectRatio,
                                            compactMetadata = cardLayout.compactMetadata,
                                            titleMinLines = 1,
                                            highlightedTitle = highlightedTitle,
                                            showOnlineCount = showOnlineCount,
                                            modifier = Modifier,
                                            //  [交互优化] 传递 onWatchLater 用于显示菜单选项
                                            onWatchLater = if (video.bvid.isNotBlank()) {
                                                { viewModel.addToWatchLater(video.bvid, video.id) }
                                            } else {
                                                null
                                            },
                                            onClick = { _, _ ->
                                                when (
                                                    val target = resolveVideoSearchNavigationTarget(
                                                        bvid = video.bvid,
                                                        contentType = video.contentType,
                                                        navigationUrl = video.navigationUrl,
                                                        title = video.title
                                                    )
                                                ) {
                                                    is SearchResultNavigationTarget.Video ->
                                                        onVideoClick(target.bvid, 0)
                                                    is SearchResultNavigationTarget.Web ->
                                                        onWebClick(target.url, target.title)
                                                    else -> Unit
                                                }
                                            }
                                        )
                                        
                                        //  [新增] 无限滚动触发：当滚动到最后几个 item 时加载更多
                                        if (targetSearchType == state.searchType && index == pageResultState.searchResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }
                                    
                                    // [新增] 空状态提示 (提示可能被屏蔽)
                                    if (!pageResultState.isSearching && pageResultState.searchResults.isEmpty() && pageResultState.error == null && pageEmptyStateCopy != null) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = pageEmptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = pageEmptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    //  [新增] 加载更多指示器
                                    if (pageResultState.isLoadingMore) {
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
                                    if (!pageResultState.hasMoreResults && pageResultState.searchResults.isNotEmpty() && !pageResultState.isLoadingMore) {
                                        item {
                                            Text(
                                                text = "已加载全部 ${pageResultState.searchResults.size} 条结果",
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
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.upResults,
                                        key = { index, upItem ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = SearchType.UP,
                                                index = index,
                                                numericKey = upItem.mid
                                            )
                                        }
                                    ) { index, upItem ->
                                        UpSearchResultCard(
                                            upItem = upItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = { onUpClick(upItem.mid) }
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.upResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }
                                    
                                     // [新增] 空状态提示
                                    if (!pageResultState.isSearching && pageResultState.upResults.isEmpty() && pageResultState.error == null && pageEmptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = pageEmptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = pageEmptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (pageResultState.isLoadingMore) {
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
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.bangumiResults,
                                        key = { index, bangumiItem ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = targetSearchType,
                                                index = index,
                                                numericKey = bangumiItem.seasonId,
                                                secondaryNumericKey = bangumiItem.mediaId
                                            )
                                        }
                                    ) { index, bangumiItem ->
                                        BangumiSearchResultCard(
                                            item = bangumiItem,
                                            categoryLabel = targetSearchType.displayName,
                                            appearance = genericResultCardAppearance,
                                            onClick = {
                                                if (bangumiItem.seasonId > 0) {
                                                    onBangumiClick(bangumiItem.seasonId)
                                                }
                                            }
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.bangumiResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (pageResultState.isLoadingMore) {
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

                                    if (!pageResultState.isSearching && pageResultState.bangumiResults.isEmpty() && pageResultState.error == null && pageEmptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = pageEmptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = pageEmptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.LIVE -> {
                                //  直播搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.liveResults,
                                        key = { index, liveItem ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = SearchType.LIVE,
                                                index = index,
                                                numericKey = liveItem.roomid,
                                                secondaryNumericKey = liveItem.uid
                                            )
                                        }
                                    ) { index, liveItem ->
                                        LiveSearchResultCard(
                                            item = liveItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = { onLiveClick(liveItem.roomid, liveItem.title, liveItem.uname) }
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.liveResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }
                                    
                                    // [新增] 空状态提示
                                    if (!pageResultState.isSearching && pageResultState.liveResults.isEmpty() && pageResultState.error == null && pageEmptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = pageEmptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = pageEmptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (pageResultState.isLoadingMore) {
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
                            com.android.purebilibili.data.model.response.SearchType.LIVE_USER -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.liveUserResults,
                                        key = { index, item ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = SearchType.LIVE_USER,
                                                index = index,
                                                numericKey = item.uid,
                                                secondaryNumericKey = item.roomid
                                            )
                                        }
                                    ) { index, item ->
                                        LiveUserSearchResultCard(
                                            item = item,
                                            appearance = genericResultCardAppearance,
                                            onClick = {
                                                when (val target = resolveLiveUserSearchNavigationTarget(
                                                    roomId = item.roomid,
                                                    uid = item.uid,
                                                    isLive = item.isLive || item.liveStatus == 1,
                                                    title = item.uname,
                                                    uname = item.uname
                                                )) {
                                                    is SearchResultNavigationTarget.LiveRoom -> onLiveClick(target.roomId, target.title, target.uname)
                                                    is SearchResultNavigationTarget.Space -> onUpClick(target.mid)
                                                    else -> Unit
                                                }
                                            }
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.liveUserResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (pageResultState.isLoadingMore) {
                                        item { SearchLoadMoreIndicator() }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.ARTICLE -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.articleResults,
                                        key = { index, articleItem ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = SearchType.ARTICLE,
                                                index = index,
                                                numericKey = articleItem.id
                                            )
                                        }
                                    ) { index, articleItem ->
                                        ArticleSearchResultCard(
                                            item = articleItem,
                                            appearance = genericResultCardAppearance,
                                            onClick = { onArticleClick(articleItem.id, articleItem.title) }
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.articleResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (!pageResultState.isSearching && pageResultState.articleResults.isEmpty() && pageResultState.error == null && pageEmptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = pageEmptyStateCopy.title,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = pageEmptyStateCopy.subtitle,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (pageResultState.isLoadingMore) {
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
                            com.android.purebilibili.data.model.response.SearchType.TOPIC -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.topicResults,
                                        key = { index, item ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = SearchType.TOPIC,
                                                index = index,
                                                numericKey = item.topicId
                                            )
                                        }
                                    ) { index, item ->
                                        TopicSearchResultCard(
                                            item = item,
                                            appearance = genericResultCardAppearance,
                                            onClick = {
                                                if (item.topicId > 0L) onTopicClick(item.topicId)
                                            }
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.topicResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (pageResultState.isLoadingMore) {
                                        item { SearchLoadMoreIndicator() }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.PHOTO -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = 0.dp, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = pageListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                ) {
                                    itemsIndexed(
                                        pageResultState.photoResults,
                                        key = { index, item ->
                                            resolveSearchResultLazyItemKey(
                                                searchType = SearchType.PHOTO,
                                                index = index,
                                                numericKey = item.id,
                                                secondaryNumericKey = item.mid
                                            )
                                        }
                                    ) { index, item ->
                                        PhotoSearchResultCard(
                                            item = item,
                                            appearance = genericResultCardAppearance
                                        )
                                        if (targetSearchType == state.searchType && index == pageResultState.photoResults.size - 3 && pageResultState.hasMoreResults && !pageResultState.isLoadingMore) {
                                            LaunchedEffect(pageResultState.currentPage, targetSearchType) {
                                                viewModel.loadMoreResults()
                                            }
                                        }
                                    }

                                    if (pageResultState.isLoadingMore) {
                                        item { SearchLoadMoreIndicator() }
                                    }
                                }
                            }
                        }
                        }
                        }
                    }
                }
            } else {
                val useSplitLayout = shouldUseSearchSplitLayout(
                    widthDp = configuration.screenWidthDp
                )
                SearchLandingContent(
                    historyListState = historyListState,
                    useSplitLayout = useSplitLayout,
                    layoutPolicy = searchLayoutPolicy,
                    contentTopPadding = contentTopPadding,
                    bottomPadding = resultBottomPadding,
                    hotList = state.hotList,
                    discoverTitle = state.discoverTitle,
                    discoverList = state.discoverList,
                    historyList = state.historyList,
                    hotSearchEnabled = hotSearchEnabled,
                    discoverSectionEnabled = discoverSectionEnabled,
                    onToggleHotSearch = {
                        scope.launch {
                            SettingsManager.setSearchHotSectionEnabled(context, !hotSearchEnabled)
                        }
                    },
                    onToggleDiscoverSection = {
                        scope.launch {
                            SettingsManager.setSearchDiscoverSectionEnabled(
                                context,
                                !discoverSectionEnabled
                            )
                        }
                    },
                    onRefreshHot = viewModel::refreshHotSearch,
                    onOpenTrending = onOpenTrending,
                    onRefreshDiscover = viewModel::refreshDiscover,
                    onKeywordClick = {
                        viewModel.search(it)
                        keyboardController?.hide()
                    },
                    onClearHistory = viewModel::clearHistory,
                    onDeleteHistory = viewModel::deleteHistory,
                    modifier = Modifier.then(
                        if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier
                    )
                )
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
                placeholder = state.defaultSearchHint.ifBlank { resolveSearchDefaultPlaceholder() },
                suggestedKeyword = state.defaultSearchHint,
                autoFocusEnabled = shouldAutoFocusSearchField(
                    startupSettled = startupSettled,
                    query = state.query
                ),
                reducedMotionBudget = effectiveSearchMotionBudget == SearchMotionBudget.REDUCED,
                entryMotionSpec = entryMotionSpec,
                entryMotionKey = entryMotionKey,
                onEntryMotionFinished = onEntryMotionConsumed,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(
                        if (shouldUseSearchTopBarBlur) {
                            Modifier.unifiedBlur(
                                hazeState = hazeState,
                                surfaceType = com.android.purebilibili.core.ui.blur.BlurSurfaceType.HEADER,
                                isScrolling = isSearchResultsScrolling,
                                forceLowBudget = forceLowBudgetSearchHeaderBlur
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(searchTopBarHeaderColor)
            )

            AnimatedVisibility(
                visible = shouldShowBackToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = resultBottomPadding + 12.dp
                    ),
                enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.92f)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (state.searchType == SearchType.VIDEO) {
                                resultGridState.animateScrollToItem(0)
                            } else {
                                resultListState.animateScrollToItem(0)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = rememberAppChevronUpIcon(),
                        contentDescription = "回到顶部"
                    )
                }
            }
            
            // ---  搜索建议下拉列表 ---
            if (state.suggestions.isNotEmpty() && state.query.isNotEmpty() && !state.showResults) {
                SearchSuggestionDropdown(
                    suggestions = state.suggestions,
                    onSuggestionClick = { suggestion ->
                        viewModel.search(suggestion)
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = contentTopPadding + 6.dp)
                        .padding(horizontal = searchLayoutPolicy.resultHorizontalPaddingDp.dp)
                        .align(Alignment.TopCenter)
                        .responsiveContentWidth()
                )
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
    placeholder: String = resolveSearchDefaultPlaceholder(),
    suggestedKeyword: String = "",
    focusRequester: androidx.compose.ui.focus.FocusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
    autoFocusEnabled: Boolean = true,
    reducedMotionBudget: Boolean = false,
    entryMotionSpec: SearchEntryMotionSpec? = null,
    entryMotionKey: Int = 0,
    onEntryMotionFinished: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val layoutSpec = remember { resolveSearchTopBarLayoutSpec() }
    val chromeSpec = remember(uiPreset, androidNativeVariant) {
        resolveSearchChromeVisualSpec(uiPreset, androidNativeVariant)
    }
    val usesMiuixSearchInput = shouldUseNativeMiuixSearchBar(uiPreset, androidNativeVariant)
    val omitSearchInputLeadingIcon = shouldOmitSearchInputLeadingIcon(
        uiPreset = uiPreset,
        usesMiuixSearchInput = usesMiuixSearchInput
    )
    val topBarRowMinHeightDp = remember(chromeSpec.inputHeightDp) {
        resolveSearchTopBarRowMinHeightDp(chromeSpec.inputHeightDp)
    }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchFieldFocused by searchInteractionSource.collectIsFocusedAsState()
    val backIcon = rememberAppBackIcon()
    val searchIcon = rememberAppSearchIcon()
    val clearIcon = rememberAppClearIcon()
    val density = LocalDensity.current
    val entryMotionProgress = remember { Animatable(1f) }
    //  Focus 状态追踪
    var isFocused by remember { mutableStateOf(false) }
    
    //  自动聚焦并弹出键盘（Miuix InputField 在 expanded=true 时自行 requestFocus）
    LaunchedEffect(autoFocusEnabled, query, usesMiuixSearchInput) {
        if (!usesMiuixSearchInput && autoFocusEnabled && query.isEmpty()) {
            kotlinx.coroutines.delay(60)
            runCatching {
                focusRequester.requestFocus()
            }.onFailure { e ->
                com.android.purebilibili.core.util.Logger.e("SearchScreen", "Failed to auto focus search field", e)
            }
        }
    }
    SideEffect {
        if (usesMiuixSearchInput) {
            isFocused = isSearchFieldFocused
        }
    }
    
    //  搜索图标颜色动画
    val searchIconColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = if (reducedMotionBudget) snap() else tween(durationMillis = 200),
        label = "iconColor"
    )
    val backLabel = stringResource(R.string.common_back)
    val searchLabel = stringResource(R.string.common_search)
    val resolvedSubmitKeyword = remember(query, suggestedKeyword) {
        resolveSearchSubmitKeyword(
            query = query,
            suggestedKeyword = suggestedKeyword
        )
    }
    val canSubmit = resolvedSubmitKeyword.isNotBlank()
    LaunchedEffect(entryMotionKey, entryMotionSpec) {
        val spec = entryMotionSpec
        if (spec == null) {
            entryMotionProgress.snapTo(1f)
            return@LaunchedEffect
        }
        entryMotionProgress.snapTo(0f)
        if (spec.durationMillis <= 0) {
            entryMotionProgress.snapTo(1f)
        } else {
            entryMotionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = spec.durationMillis,
                    easing = AppMotionEasing.Continuity
                )
            )
        }
        onEntryMotionFinished(entryMotionKey)
    }
    val entryMotionModifier = if (entryMotionSpec != null) {
        Modifier.graphicsLayer {
            val progress = entryMotionProgress.value
            val spec = entryMotionSpec
            alpha = lerp(spec.initialAlpha, 1f, progress)
            scaleX = lerp(spec.initialScale, 1f, progress)
            scaleY = lerp(spec.initialScale, 1f, progress)
            translationY = with(density) {
                spec.initialTranslationYDp.dp.toPx()
            } * (1f - progress)
            transformOrigin = TransformOrigin(
                spec.transformOriginPivotX,
                spec.transformOriginPivotY
            )
        }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(entryMotionModifier),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            Row(
                modifier = Modifier
                    .responsiveContentWidth()
                    .heightIn(min = topBarRowMinHeightDp.dp)
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

                if (usesMiuixSearchInput) {
                    InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {
                            if (canSubmit) {
                                onSearch(resolvedSubmitKeyword)
                            }
                        },
                        expanded = true,
                        onExpandedChange = {},
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = chromeSpec.inputHeightDp.dp),
                        label = placeholder,
                        interactionSource = searchInteractionSource,
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = chromeSpec.inputHeightDp.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused },
                        placeholder = {
                            Text(
                                text = placeholder,
                                maxLines = layoutSpec.placeholderMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = if (omitSearchInputLeadingIcon) {
                            null
                        } else {
                            {
                                Icon(
                                    searchIcon,
                                    contentDescription = searchLabel,
                                    tint = searchIconColor,
                                    modifier = Modifier.size(chromeSpec.actionIconSizeDp.dp)
                                )
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(chromeSpec.inputCornerRadiusDp.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = if (uiPreset == UiPreset.MD3) {
                                AppSurfaceTokens.surfaceContainerHigh()
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            unfocusedContainerColor = if (uiPreset == UiPreset.MD3) {
                                AppSurfaceTokens.surfaceContainerHigh()
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (canSubmit) {
                                    onSearch(resolvedSubmitKeyword)
                                }
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.width(chromeSpec.horizontalGapDp.dp))

                if (!usesMiuixSearchInput) {
                    IconButton(
                        onClick = onClearQuery,
                        enabled = query.isNotEmpty(),
                        modifier = Modifier.size(chromeSpec.clearActionSizeDp.dp)
                    ) {
                        Icon(
                            clearIcon,
                            contentDescription = stringResource(R.string.common_clear),
                            tint = if (query.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(chromeSpec.actionIconSizeDp.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onSearch(resolvedSubmitKeyword) },
                    enabled = canSubmit,
                    modifier = Modifier
                        .size(chromeSpec.submitActionSizeDp.dp)
                        .clip(RoundedCornerShape(chromeSpec.actionContainerCornerRadiusDp.dp))
                        .background(
                            if (canSubmit) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            }
                        )
                ) {
                    Icon(
                        searchIcon,
                        contentDescription = searchLabel,
                        tint = if (canSubmit) {
                            searchIconColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(chromeSpec.actionIconSizeDp.dp)
                    )
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
    val historyIcon = rememberAppHistoryIcon()
    val clearIcon = rememberAppClearIcon()
    val deleteLabel = stringResource(R.string.common_delete)
    androidx.compose.material3.InputChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = keyword,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                historyIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                modifier = Modifier.size(16.dp)
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    clearIcon,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    )
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


@Composable
private fun SearchResultTypeTabRow(
    tabs: List<SearchType>,
    pagerState: PagerState,
    onTabClick: (Int, SearchType) -> Unit
) {
    val selectedPage = pagerState.currentPage.coerceIn(tabs.indices)
    ScrollableTabRow(
        selectedTabIndex = selectedPage,
        modifier = Modifier
            .fillMaxWidth(),
        edgePadding = 8.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        divider = {},
        indicator = { tabPositions ->
            if (tabPositions.isNotEmpty()) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedPage.coerceIn(tabPositions.indices)]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, type ->
            val selected = selectedPage == index
            Tab(
                selected = selected,
                onClick = { onTabClick(index, type) },
                interactionSource = remember { MutableInteractionSource() },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = type.displayName,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun rememberSearchHighlightedTitle(video: VideoItem): androidx.compose.ui.text.AnnotatedString? {
    val highlightColor = MaterialTheme.colorScheme.primary
    return remember(video.searchHighlightedTitle, highlightColor) {
        val segments = resolveSearchHighlightedTextSegments(video.searchHighlightedTitle)
        if (segments.none { it.highlighted }) {
            null
        } else {
            buildAnnotatedString {
                segments.forEach { segment ->
                    if (segment.highlighted) {
                        pushStyle(
                            SpanStyle(
                                color = highlightColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(segment.text)
                        pop()
                    } else {
                        append(segment.text)
                    }
                }
            }
        }
    }
}

/**
 *  搜索筛选条件栏
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterBar(
    currentType: SearchType,
    currentOrder: SearchOrder,
    currentDurations: Set<SearchDuration>,
    currentVideoTid: Int,
    currentUpOrder: SearchUpOrder,
    currentUpOrderSort: SearchOrderSort,
    currentUpUserType: SearchUserType,
    currentLiveOrder: SearchLiveOrder,
    onOrderChange: (SearchOrder) -> Unit,
    onDurationToggle: (SearchDuration) -> Unit,
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
    val filterControls = remember(currentType, currentUpOrder) {
        resolveSearchFilterControls(
            currentType = currentType,
            currentUpOrder = currentUpOrder
        )
    }
    if (filterControls.isEmpty()) return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (SearchFilterControl.VIDEO_ORDER in filterControls) {
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
                }

                if (SearchFilterControl.VIDEO_DURATION in filterControls) {
                Box {
                    FilterMenuChip(
                        text = resolveSearchDurationFilterLabel(currentDurations),
                        highlighted = currentDurations.isNotEmpty(),
                        onClick = { showDurationMenu = true }
                    )
                    DropdownMenu(
                        expanded = showDurationMenu,
                        onDismissRequest = { showDurationMenu = false }
                    ) {
                        SearchDuration.entries.forEach { duration ->
                            val selected = if (duration == SearchDuration.ALL) {
                                currentDurations.isEmpty()
                            } else {
                                duration in currentDurations
                            }
                            DropdownMenuItem(
                                text = { Text(duration.displayName) },
                                leadingIcon = {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    onDurationToggle(duration)
                                }
                            )
                        }
                    }
                }
                }

                if (SearchFilterControl.VIDEO_TID in filterControls) {
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

                if (SearchFilterControl.UP_ORDER in filterControls) {
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
                }

                if (SearchFilterControl.UP_ORDER_SORT in filterControls) {
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

                if (SearchFilterControl.UP_USER_TYPE in filterControls) {
                Box {
                    FilterMenuChip(
                        text = resolveSearchUpUserTypeFilterLabel(currentUpUserType),
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

                if (SearchFilterControl.LIVE_ORDER in filterControls) {
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
}

@Composable
private fun FilterMenuChip(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val chevronIcon = rememberAppChevronDownIcon()
    androidx.compose.material3.FilterChip(
        selected = highlighted,
        onClick = onClick,
        label = {
            Text(
                text = text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                chevronIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun SearchResultCardSurface(
    appearance: SearchResultCardAppearance,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val isMiuix = uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX
    val shape = RoundedCornerShape(if (isMiuix) 18.dp else 12.dp)
    val color = if (isMiuix) {
        AppSurfaceTokens.surfaceContainer()
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = appearance.containerAlpha)
    }
    val border = if (appearance.borderAlpha > 0f) {
        androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isMiuix) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            } else {
                Color.White.copy(alpha = appearance.borderAlpha)
            }
        )
    } else {
        null
    }
    if (onClick != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            color = color,
            shape = shape,
            tonalElevation = if (isMiuix) 0.dp else appearance.tonalElevationDp.dp,
            shadowElevation = if (isMiuix) 0.dp else appearance.shadowElevationDp.dp,
            border = border
        ) {
            content()
        }
    } else {
        Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = shape,
        tonalElevation = if (isMiuix) 0.dp else appearance.tonalElevationDp.dp,
        shadowElevation = if (isMiuix) 0.dp else appearance.shadowElevationDp.dp,
        border = border
    ) {
        content()
    }
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
            minLines = 1,
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
                        rememberSharedContentState(key = com.android.purebilibili.core.ui.transition.avatarSharedElementKey(cleanedItem.mid)),
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
                    
                    val verifyBadge = cleanedItem.official_verify?.let { verify ->
                        resolveOfficialVerifyBadge(
                            type = verify.type,
                            desc = verify.desc,
                            compact = true
                        )
                    }
                    if (verifyBadge != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        OfficialVerifyBadge(
                            badge = verifyBadge,
                            compact = true
                        )
                    }
                }
                
                // 个性签名
                if (cleanedItem.usign.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanedItem.usign,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
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
    onClick: () -> Unit,
    categoryLabel: String? = null
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
                if (!categoryLabel.isNullOrBlank()) {
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = categoryLabel,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
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
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (item.indexShow.isNotBlank()) {
                        Text(
                            text = item.indexShow,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // 分区
                if (item.area_v2_name.isNotBlank()) {
                    Text(
                        text = "${item.area_v2_parent_name} · ${item.area_v2_name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchLoadMoreIndicator() {
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

@Composable
internal fun LiveUserSearchResultCard(
    item: SearchLiveUserItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {
    val cleaned = remember(item.uid, item.uname, item.uface) { item.cleanupFields() }
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cleaned.uface)
                    .crossfade(true)
                    .build(),
                contentDescription = cleaned.uname,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cleaned.uname,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (cleaned.isLive || cleaned.liveStatus == 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
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
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "粉丝 ${FormatUtils.formatStat(cleaned.attentions.toLong())}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun TopicSearchResultCard(
    item: SearchTopicItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {
    val cleaned = remember(item.topicId, item.title, item.cover) { item.cleanupFields() }
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cleaned.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = cleaned.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleaned.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cleaned.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleaned.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "浏览 ${FormatUtils.formatStat(cleaned.view.toLong())}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
internal fun PhotoSearchResultCard(
    item: SearchPhotoItem,
    appearance: SearchResultCardAppearance
) {
    val cleaned = remember(item.id, item.title, item.cover) { item.cleanupFields() }
    SearchResultCardSurface(
        appearance = appearance.copy(
            containerAlpha = appearance.containerAlpha * 0.72f
        ),
        onClick = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cleaned.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = cleaned.title,
                modifier = Modifier
                    .size(width = 104.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleaned.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleaned.uname,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "图片 ${cleaned.count} · 浏览 ${FormatUtils.formatStat(cleaned.view.toLong())} · 喜欢 ${FormatUtils.formatStat(cleaned.like.toLong())}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "暂不支持打开",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
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

                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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
