// 文件路径: feature/search/SearchScreen.kt
package com.android.purebilibili.feature.search

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import com.android.purebilibili.core.util.HtmlEntityUtils
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.resolveVideoListColumns
import com.android.purebilibili.core.ui.components.rememberVideoListLayoutControl
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.core.ui.components.AnimatedVideoListItem
import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppInputChip
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTab
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.KeepScrollableTabSelectionVisible
import com.android.purebilibili.core.ui.components.liquidDockViewport
import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.navigation.SearchSubmitAction
import com.android.purebilibili.navigation.resolveSearchSubmitAction
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.feature.list.resolveCommonListHeaderOffsetAfterContentScroll
import com.android.purebilibili.feature.list.resolveCommonListHeaderOffsetForSettledContent

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import com.android.purebilibili.feature.home.resolveHomeFeedPinchColumnBounds
import com.android.purebilibili.feature.home.GridPinchColumnHudPill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
//  Material Icons
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animate
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.renderer.material3.AppTonalPillTabRow
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors
import com.android.purebilibili.core.theme.resolveFilledSelectionAccentColors
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.rememberContentCardSurfaceSpec
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.home.components.resolveHomeTopSearchContainerShape
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.HomeTopChromeRenderMode
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.homeTopBottomBarMatchedSurface
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveHomeTopEdgeButtonShape
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.skeleton.ContentMediaListSkeleton
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeleton
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeletonFixedColumns
import com.android.purebilibili.core.ui.OfficialVerifyAvatarBadge
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.ui.AppTopChromePolicy
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.ui.rememberAppChromeLiquidGlassEnabled
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppHistoryIcon
import com.android.purebilibili.core.ui.rememberAppSearchIcon
import com.android.purebilibili.core.ui.resolveOfficialVerifyBadge
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.feature.home.resolveHomeCoverRequestSpec
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard  //  使用首页卡片
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.resolveHomeFeedCardLayout
import com.android.purebilibili.feature.home.resolveReturnAnimationSuppressionDurationMs
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager  //  读取动画设置
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.SearchOrderSort
import com.android.purebilibili.data.repository.SearchArticleCategory
import com.android.purebilibili.data.repository.SearchPhotoCategory
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import com.android.purebilibili.data.repository.resolveSearchDurationFilterLabel
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.util.shouldShowScrollToTop
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.SearchArticleItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import com.android.purebilibili.data.model.response.SearchPhotoItem
import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.data.model.response.SearchTopicItem
import kotlinx.coroutines.launch

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator


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

private const val SEARCH_INPUT_LINE_HEIGHT_SP = 20

/** Total vertical padding around the search controls. */
internal const val SEARCH_TOP_BAR_VERTICAL_PADDING_DP = 8

internal fun resolveSearchTopBarRowMinHeightDp(
    inputHeightDp: Int,
    verticalPaddingDp: Int = SEARCH_TOP_BAR_VERTICAL_PADDING_DP
): Int = maxOf(48, inputHeightDp) + verticalPaddingDp

internal fun resolveSearchInputHeightDp(
    minHeightDp: Int,
    lineHeightDp: Float,
    fontSizeDp: Float,
): Int = maxOf(minHeightDp, ceil(maxOf(lineHeightDp, fontSizeDp) + 8f).toInt())

@Composable
private fun rememberSearchInputHeightDp(minHeightDp: Int): Int {
    val density = LocalDensity.current
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize
    return remember(minHeightDp, density, fontSize) {
        with(density) {
            resolveSearchInputHeightDp(
                minHeightDp = minHeightDp,
                lineHeightDp = SEARCH_INPUT_LINE_HEIGHT_SP.sp.toDp().value,
                fontSizeDp = fontSize.toDp().value,
            )
        }
    }
}

internal fun shouldOmitSearchInputLeadingIcon(
    tabPresentation: AppTopTabPresentation,
): Boolean = tabPresentation == AppTopTabPresentation.MATERIAL_UNDERLINE

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
    headerBlurRequested: Boolean,
    hazeSourceEnabled: Boolean,
    globalWallpaperVisible: Boolean
): Boolean = headerBlurRequested && hazeSourceEnabled && !globalWallpaperVisible

internal fun shouldUseSearchSolidTopChrome(
    headerBlurRequested: Boolean,
    progressiveBlurRequested: Boolean,
): Boolean = !headerBlurRequested && !progressiveBlurRequested

/** Search chrome sizes and semantic shape levels for actions and content surfaces. */
/** Shared alignment and spacing for the search field, type tabs and order rail. */
internal data class SearchComponentSpacingSpec(
    val horizontalPaddingDp: Int,
    val resultRailHorizontalPaddingDp: Int,
    val rowVerticalPaddingDp: Int,
    val actionGapDp: Int,
)

internal fun resolveSearchComponentSpacingSpec(uiStyle: AppUiStyle): SearchComponentSpacingSpec =
    when (uiStyle) {
        AppUiStyle.MATERIAL3 -> SearchComponentSpacingSpec(16, 8, 0, 12)
        AppUiStyle.MIUIX -> SearchComponentSpacingSpec(12, 8, 0, 8)
    }

internal data class SearchChromeVisualSpec(
    val inputHeightDp: Int,
    /** Search-action hit target beside the field, using the same capsule curvature. */
    val actionShapeLevel: ContainerLevel,
    val useFilledSearchAction: Boolean,
    /** Suggestion / history / discover surface cards. */
    val suggestionShapeLevel: ContainerLevel,
    val clearActionSizeDp: Int,
    val submitActionSizeDp: Int,
    val actionIconSizeDp: Int,
    val horizontalGapDp: Int,
    val inputHorizontalPaddingDp: Int,
    val chipHeightDp: Int,
    val compactChipHeightDp: Int,
    val chipShapeLevel: ContainerLevel,
    val chipHorizontalPaddingDp: Int
)

internal fun resolveSearchInputShape(
    chromePolicy: AppTopChromePolicy,
): androidx.compose.ui.graphics.Shape = resolveHomeTopSearchContainerShape(chromePolicy)

internal fun resolveSearchChromeVisualSpec(
    chromePolicy: AppTopChromePolicy,
): SearchChromeVisualSpec {
    val compactChrome = chromePolicy.compactChromeSpec
    return SearchChromeVisualSpec(
        inputHeightDp = minOf(compactChrome.primaryHeightDp, 48),
        actionShapeLevel = ContainerLevel.Pill,
        useFilledSearchAction = chromePolicy.tabPresentation != AppTopTabPresentation.MOVING_CAPSULE,
        suggestionShapeLevel = ContainerLevel.Card,
        clearActionSizeDp = minOf(compactChrome.secondaryButtonSizeDp, 40),
        submitActionSizeDp = minOf(compactChrome.secondaryButtonSizeDp, 40),
        actionIconSizeDp = minOf(compactChrome.iconSizeDp, 20),
        horizontalGapDp = compactChrome.standardGapDp,
        inputHorizontalPaddingDp = compactChrome.inputHorizontalPaddingDp,
        chipHeightDp = compactChrome.chipHeightDp,
        compactChipHeightDp = compactChrome.compactChipHeightDp,
        chipShapeLevel = ContainerLevel.Pill,
        chipHorizontalPaddingDp = compactChrome.chipHorizontalPaddingDp,
    )
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
    lastResetSessionId: Long,
    isReturningFromVideoDetail: Boolean = false,
): Boolean {
    return !isReturningFromVideoDetail &&
        showResults &&
        searchSessionId > 0L &&
        searchSessionId != lastResetSessionId
}

internal fun shouldShowSearchBackToTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    offsetThresholdPx: Int = 280
): Boolean {
    return shouldShowScrollToTop(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        offsetThresholdPx = offsetThresholdPx,
    )
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
    LIVE_ORDER,
    ARTICLE_ORDER,
    ARTICLE_CATEGORY,
    PHOTO_ORDER,
    PHOTO_CATEGORY
}

internal val defaultSearchFilterTabOrder: List<SearchType> = listOf(
    SearchType.VIDEO,
    SearchType.BANGUMI,
    SearchType.MEDIA_FT,
    SearchType.LIVE,
    SearchType.LIVE_USER,
    SearchType.UP,
    SearchType.ARTICLE,
    SearchType.TOPIC,
    SearchType.PHOTO
)

internal fun resolveSearchFilterTabs(
    savedOrder: List<String> = emptyList()
): List<SearchType> {
    val knownByValue = SearchType.entries.associateBy(SearchType::value)
    val ordered = savedOrder.mapNotNull(knownByValue::get).distinct()
    val remaining = (defaultSearchFilterTabOrder + SearchType.entries)
        .distinct()
        .filterNot(ordered::contains)
    return ordered + remaining
}

internal fun resolveSearchDefaultPlaceholder(): String {
    return "搜索视频、番剧、影视、直播、UP主、专栏等..."
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
        SearchType.LIVE_USER -> emptyList()
        SearchType.ARTICLE -> listOf(
            SearchFilterControl.ARTICLE_ORDER,
            SearchFilterControl.ARTICLE_CATEGORY
        )
        SearchType.PHOTO -> listOf(
            SearchFilterControl.PHOTO_ORDER,
            SearchFilterControl.PHOTO_CATEGORY
        )
        SearchType.TOPIC -> emptyList()
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
    return HtmlEntityUtils.unescape(raw.replace(Regex("<.*?>"), ""))
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
    onNavigateSearchTarget: (BilibiliNavigationTarget) -> Boolean,
    onVideoClick: (String, Long, String) -> Unit,
    onWebClick: (String, String) -> Unit,
    onUpClick: (Long) -> Unit,  //  点击UP主跳转到空间
    onBangumiClick: (Long) -> Unit, //  点击番剧/影视跳转详情
    onCheeseClick: ((Long, Long) -> Unit)? = null, // 点击课堂跳转
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
    val topChromePolicy = rememberAppTopChromePolicy()
    val semanticVisualPolicy = rememberAppSemanticVisualPolicy()
    val contentCardSurfaceSpec = rememberContentCardSurfaceSpec()
    val backToTopButtonEnabled = rememberBackToTopButtonEnabled()
    val searchChromeSpec = remember(topChromePolicy) { resolveSearchChromeVisualSpec(topChromePolicy) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listLayout = rememberVideoListLayoutControl()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    var startupSettled by remember { mutableStateOf(false) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var retainSearchImeAfterClear by remember { mutableStateOf(false) }
    var searchQueryClearedAtElapsedMs by remember { mutableLongStateOf(-1L) }
    // One-shot autofocus for empty landing only; never re-open keyboard after results.
    var autoFocusConsumed by rememberSaveable { mutableStateOf(false) }
    var previousShowResults by rememberSaveable { mutableStateOf(false) }
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
    // A fresh SearchScreen entry starts at the top. Keep this non-saveable so leaving
    // search and opening it again does not inherit the previous result position.
    var lastResetSearchSessionId by remember { mutableLongStateOf(0L) }
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
                lastResetSessionId = lastResetSearchSessionId,
                isReturningFromVideoDetail = isReturningFromVideoDetail,
            )
        ) {
            if (isReturningFromVideoDetail && state.showResults && state.searchSessionId > 0L) {
                lastResetSearchSessionId = state.searchSessionId
            }
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
    val inputHeightDp = rememberSearchInputHeightDp(searchChromeSpec.inputHeightDp)
    val topBarHeight = resolveSearchTopBarRowMinHeightDp(inputHeightDp).dp
    val contentTopPadding = statusBarHeight + topBarHeight
    
    //  读取动画设置开关
    val context = LocalContext.current
    val searchHintEnabled by remember(context) {
        com.android.purebilibili.core.store.SearchHintSettingsStore.isEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val displayedSearchHint = state.defaultSearchHint.takeIf { searchHintEnabled }.orEmpty()
    val scope = rememberCoroutineScope()
    val savedSearchFilterTabOrder by SettingsManager
        .getSearchFilterTabOrder(context)
        .collectAsStateWithLifecycle(
            initialValue = defaultSearchFilterTabOrder.map { it.value }
        )
    val searchTabs = remember(savedSearchFilterTabOrder) {
        resolveSearchFilterTabs(savedSearchFilterTabOrder)
    }
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
    val homeDurationStyle by SettingsManager
        .getHomeDurationStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeDurationStyle.OUTSIDE_COVER)
    val compactVideoStatsOnCover by SettingsManager
        .getCompactVideoStatsOnCover(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val hotSearchEnabled by SettingsManager.getSearchHotSectionEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val discoverSectionEnabled by SettingsManager.getSearchDiscoverSectionEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val androidNativeLiquidGlassEnabled = appThemeConfig.liquidGlassEnabled
    val effectiveLiquidGlassEnabled = rememberAppChromeLiquidGlassEnabled(
        androidNativeEnabled = androidNativeLiquidGlassEnabled,
    )
    val headerBlurEnabled = appThemeConfig.headerBlurEnabled
    val progressiveTopBlurEnabled = appThemeConfig.progressiveTopBlurEnabled
    val bottomBarBlurEnabled = appThemeConfig.bottomBarBlurEnabled
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
        effectiveLiquidGlassEnabled,
        searchCardBlurEnabled,
    ) {
        resolveSearchVideoCardAppearance(
            effectiveLiquidGlassEnabled = effectiveLiquidGlassEnabled,
            blurEnabled = searchCardBlurEnabled,
            showHomeCoverGlassBadges = false,
            showHomeInfoGlassBadges = false,
        )
    }
    val genericResultCardAppearance = remember(
        effectiveLiquidGlassEnabled,
        semanticVisualPolicy.supportsIndependentLiquidGlass,
        contentCardSurfaceSpec.tonalElevationDp,
    ) {
        resolveSearchResultCardAppearance(
            effectiveLiquidGlassEnabled = effectiveLiquidGlassEnabled,
            supportsIndependentLiquidGlass = semanticVisualPolicy.supportsIndependentLiquidGlass,
            tonalElevationDp = contentCardSurfaceSpec.tonalElevationDp.toInt(),
        )
    }
    val cardTransitionEnabled by SettingsManager.getCardTransitionEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val showOnlineCount by SettingsManager.getShowOnlineCount(context).collectAsStateWithLifecycle(initialValue = false)
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.BILIPAI)
    val homeSettings by SettingsManager
        .getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = HomeSettings())
    val searchContentWidth = resolveSearchContentWidth(
        isExpandedScreen = windowSizeClass.isExpandedScreen,
        widthDp = windowSizeClass.widthDp
    )
    val videoGridColumns = remember(
        searchContentWidth,
        listLayout.singleColumn,
        homeSettings.gridColumnCount,
        homeSettings.gridColumnCountCompact,
        homeSettings.homeFeedCardWidthPreset,
        windowSizeClass.widthSizeClass
    ) {
        resolveSearchVideoGridColumns(
            singleColumn = listLayout.singleColumn,
            contentWidthDp = searchContentWidth.value.toInt(),
            fixedColumnCount = com.android.purebilibili.feature.home.resolveHomeFeedStoredColumnCount(
                widthSizeClass = windowSizeClass.widthSizeClass,
                compactColumnCount = homeSettings.gridColumnCountCompact,
                defaultColumnCount = homeSettings.gridColumnCount,
            ),
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardLayout = remember(
        homeFeedCardStyle,
        videoGridColumns,
        windowSizeClass.widthSizeClass,
    ) {
        resolveHomeFeedCardLayout(
            style = homeFeedCardStyle,
            gridColumns = videoGridColumns,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
    }
    val isSearchResultsScrolling by remember(historyListState, resultGridState, resultListState, searchPagerState) {
        derivedStateOf {
            historyListState.isScrollInProgress ||
                resultGridState.isScrollInProgress ||
                resultListState.isScrollInProgress ||
                searchPagerState.isScrollInProgress
        }
    }
    val searchHeaderCollapseMode = homeSettings.commonListHeaderCollapseMode
    val isSearchCollapseEnabled = searchHeaderCollapseMode != CommonListHeaderCollapseMode.ALWAYS_VISIBLE
    var searchTopBarHeightPx by remember { mutableIntStateOf(0) }
    var searchHeaderOffsetPx by remember { mutableFloatStateOf(0f) }
    var searchHeaderSettleJob by remember { mutableStateOf<Job?>(null) }
    val searchStatusBarHeightPx = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val searchCollapseDistancePx = searchTopBarHeightPx.toFloat().coerceAtLeast(0f)
    val searchHeaderSettleMotionSpec = AppMotionTokens.emphasizedSpec<Float>()

    val isSearchResultsAtTop by remember(
        state.showResults,
        state.searchType,
        resultGridState,
        resultListState
    ) {
        derivedStateOf {
            if (!state.showResults) {
                true
            } else if (state.searchType == SearchType.VIDEO) {
                resultGridState.firstVisibleItemIndex == 0 && resultGridState.firstVisibleItemScrollOffset == 0
            } else {
                resultListState.firstVisibleItemIndex == 0 && resultListState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    fun animateSearchHeaderOffsetTo(targetOffsetPx: Float) {
        if (kotlin.math.abs(searchHeaderOffsetPx - targetOffsetPx) <= 0.5f) {
            searchHeaderOffsetPx = targetOffsetPx
            return
        }
        searchHeaderSettleJob?.cancel()
        searchHeaderSettleJob = scope.launch {
            animate(
                initialValue = searchHeaderOffsetPx,
                targetValue = targetOffsetPx,
                animationSpec = searchHeaderSettleMotionSpec
            ) { value, _ ->
                searchHeaderOffsetPx = value
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (searchHeaderSettleJob === job) {
                    searchHeaderSettleJob = null
                }
            }
        }
    }

    LaunchedEffect(isSearchCollapseEnabled, isSearchResultsAtTop) {
        if (!isSearchCollapseEnabled || isSearchResultsAtTop) {
            animateSearchHeaderOffsetTo(0f)
        }
    }

    LaunchedEffect(
        searchHeaderCollapseMode, searchCollapseDistancePx, state.showResults, state.searchType,
        resultGridState, resultListState,
    ) {
        snapshotFlow {
            val index = if (state.searchType == SearchType.VIDEO) {
                resultGridState.firstVisibleItemIndex
            } else {
                resultListState.firstVisibleItemIndex
            }
            val offset = if (state.searchType == SearchType.VIDEO) {
                resultGridState.firstVisibleItemScrollOffset
            } else {
                resultListState.firstVisibleItemScrollOffset
            }
            Triple(index, offset, isSearchResultsScrolling)
        }.collect { (index, offset, scrolling) ->
            if (!scrolling) {
                animateSearchHeaderOffsetTo(resolveCommonListHeaderOffsetForSettledContent(
                    firstVisibleItemIndex = if (state.showResults) index else 0,
                    firstVisibleItemScrollOffset = if (state.showResults) offset else 0,
                    maxCollapsePx = searchCollapseDistancePx,
                    mode = searchHeaderCollapseMode,
                ))
            }
        }
    }
    val searchHeaderScrollConnection = remember(searchHeaderCollapseMode, searchCollapseDistancePx, isSearchResultsAtTop) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isSearchCollapseEnabled || searchCollapseDistancePx <= 0f) return Offset.Zero
                if (kotlin.math.abs(available.y) < 0.5f) return Offset.Zero
                if (kotlin.math.abs(available.y) < kotlin.math.abs(available.x)) return Offset.Zero
                searchHeaderSettleJob?.cancel()
                searchHeaderSettleJob = null
                searchHeaderOffsetPx = resolveCommonListHeaderOffsetAfterContentScroll(
                    currentOffsetPx = searchHeaderOffsetPx,
                    contentConsumedDeltaYPx = available.y,
                    maxCollapsePx = searchCollapseDistancePx,
                    isAtTop = isSearchResultsAtTop,
                    mode = searchHeaderCollapseMode,
                )
                return Offset.Zero
            }
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
    val exitMotionSpec = remember(entryMotionKey, configuration.screenHeightDp) {
        resolveSearchExitMotionSpec(
            entrySpec = entryMotionSpec,
            screenHeightDp = configuration.screenHeightDp,
        )
    }
    var exitMotionKey by remember { mutableIntStateOf(0) }
    var exitMotionInProgress by remember { mutableStateOf(false) }
    val exitContentAlpha by animateFloatAsState(
        // Keep the destination visibly populated until navigation hands off to Home. Fading to
        // zero here leaves only Search's opaque scaffold background for the remainder of the
        // search-field morph, which reads as a white flash.
        targetValue = if (exitMotionInProgress) 0.72f else 1f,
        animationSpec = tween(
            durationMillis = 220,
            easing = AppMotionEasing.Continuity,
        ),
        label = "searchExitContentAlpha",
    )
    val searchHazeAvailable = shouldAllowRenderEffectBackedHazeEffect(android.os.Build.VERSION.SDK_INT) &&
        recoverableBlurEnabled(hazeState) &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val searchHazeEnabled = searchHazeAvailable && shouldEnableSearchHazeSource(
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
        headerBlurRequested = headerBlurEnabled,
        hazeSourceEnabled = searchHazeEnabled,
        globalWallpaperVisible = globalWallpaperVisible
    )
    val searchUsesSolidChrome = shouldUseSearchSolidTopChrome(
        headerBlurRequested = headerBlurEnabled,
        progressiveBlurRequested = progressiveTopBlurEnabled,
    )
    val searchTopBarHeaderColor = resolveSearchTopBarHeaderColor(
        // Keep the top chrome on the same semantic plane as the Miuix list scaffold.
        // Using Material surface here made the header black while the list stayed gray.
        surfaceColor = AppSurfaceTokens.groupedListContainer(),
        backgroundAlpha = if (searchUsesSolidChrome) 1f else 0.96f,
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

    val dismissSearchKeyboardAndFocus = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchFieldFocused = false
        autoFocusConsumed = true
    }
    val updateSearchQuery: (String) -> Unit = { query ->
        if (
            shouldRetainSearchImeWhenQueryCleared(
                fieldFocused = searchFieldFocused,
                previousQuery = state.query,
                newQuery = query,
            )
        ) {
            retainSearchImeAfterClear = true
            searchQueryClearedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        }
        viewModel.onQueryChange(query)
    }
    // No in-field clear icon. Leaving results through this path must not reopen the IME.
    val clearSearchQuery = {
        viewModel.onQueryChange("")
        viewModel.exitResultsToLanding()
    }
    LaunchedEffect(retainSearchImeAfterClear) {
        if (!retainSearchImeAfterClear) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        runCatching { searchFocusRequester.requestFocus() }
        keyboardController?.show()
        retainSearchImeAfterClear = false
    }
    val submitSearch: (String) -> Unit = { keyword ->
        when (val action = resolveSearchSubmitAction(keyword)) {
            SearchSubmitAction.Ignore -> Unit
            is SearchSubmitAction.OpenSearch -> viewModel.search(action.keyword)
            is SearchSubmitAction.OpenNativeTarget -> {
                viewModel.dismissSuggestions()
                if (!onNavigateSearchTarget(action.target)) viewModel.search(keyword)
            }
        }
        dismissSearchKeyboardAndFocus()
    }

    val handleSearchBack = {
        when (
            resolveSearchBackAction(
                showResults = state.showResults,
                suggestionsVisible = state.suggestions.isNotEmpty(),
                searchFieldFocused = searchFieldFocused
            )
        ) {
            SearchBackAction.LEAVE_SEARCH -> {
                dismissSearchKeyboardAndFocus()
                if (exitMotionSpec != null && !exitMotionInProgress) {
                    exitMotionInProgress = true
                    exitMotionKey += 1
                } else if (!exitMotionInProgress) {
                    onBack()
                }
            }
        }
    }

    BackHandler(onBack = handleSearchBack)

    // Entering results must drop focus so the keyboard cannot reappear.
    LaunchedEffect(state.showResults) {
        if (shouldClearSearchFocusWhenShowingResults(
                showResults = state.showResults,
                previousShowResults = previousShowResults
            )
        ) {
            dismissSearchKeyboardAndFocus()
        }
        previousShowResults = state.showResults
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
        // Hard-settle only when the pager is fully idle. Mid-flight corrections would snap the
        // tab indicator while animatePagerSelection is still gliding.
        if (
            !searchPagerState.isScrollInProgress &&
            kotlin.math.abs(searchPagerState.currentPageOffsetFraction) <= 0.001f &&
            searchPagerState.currentPage != targetPage
        ) {
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

    val searchTopBarCallbacks = SearchTopBarCallbacks(
        onBack = handleSearchBack,
        onQueryChange = updateSearchQuery,
        onSearch = submitSearch,
        onClearQuery = clearSearchQuery,
        onFocusChanged = { focused ->
            searchFieldFocused = focused
            if (focused) {
                autoFocusConsumed = true
            }
        },
        onEntryMotionFinished = onEntryMotionConsumed,
        onExitMotionFinished = { completedKey ->
            if (exitMotionInProgress && completedKey == exitMotionKey) {
                onBack()
            }
        },
        isScrollInProgressProvider = { isSearchResultsScrolling },
    )
    // One BasicTextField moves between the results scaffold and the landing overlay.
    // Call sites pass the current query, placement, trailing actions, and callbacks so
    // this remembered content does not close over the first composition's state.
    val searchTopBarContent = remember {
        movableContentOf<
            String,
            SearchTopBarPlacement,
            (@Composable RowScope.() -> Unit)?,
            SearchTopBarCallbacks,
        > { query, placement, trailingActions, callbacks ->
            SearchTopBar(
                query = query,
                onBack = callbacks.onBack,
                onQueryChange = callbacks.onQueryChange,
                onSearch = callbacks.onSearch,
                onClearQuery = callbacks.onClearQuery,
                onFocusChanged = callbacks.onFocusChanged,
                focusRequester = placement.focusRequester,
                placeholder = placement.placeholder,
                suggestedKeyword = placement.suggestedKeyword,
                autoFocusEnabled = placement.autoFocusEnabled,
                reducedMotionBudget = placement.reducedMotionBudget,
                entryMotionSpec = placement.entryMotionSpec,
                entryMotionKey = placement.entryMotionKey,
                onEntryMotionFinished = callbacks.onEntryMotionFinished,
                exitMotionSpec = placement.exitMotionSpec,
                exitMotionKey = placement.exitMotionKey,
                onExitMotionFinished = callbacks.onExitMotionFinished,
                isScrollInProgressProvider = callbacks.isScrollInProgressProvider,
                liquidGlassEnabled = placement.liquidGlassEnabled,
                miuixBackdrop = placement.miuixBackdrop,
                includeStatusBarPadding = placement.includeStatusBarPadding,
                trailingActions = trailingActions,
                modifier = placement.modifier,
            )
        }
    }

    val resultBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 16.dp
    ) + if (com.android.purebilibili.core.ui.rememberNowPlayingBarOverlayVisible()) {
        // 听视频小横条悬浮时统一上浮避让（与首页/稍后再看一致）
        com.android.purebilibili.core.ui.NowPlayingBarOverlayAvoidancePadding
    } else {
        0.dp
    }

    AppScaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        //  移除 bottomBar，搜索栏现在位于顶部 Box 中
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .globalWallpaperAwareBackground()
                .then(
                    if (searchUsesSolidChrome) {
                        Modifier.background(AppSurfaceTokens.groupedListContainer())
                    } else {
                        Modifier
                    }
                )
                .padding(padding)
        ) {
            val searchChromeSource = if (
                shouldKeepSearchChromeBackdropSource(
                    progressiveTopBlurEnabled = progressiveTopBlurEnabled,
                    liquidGlassEnabled = effectiveLiquidGlassEnabled,
                )
            ) {
                com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
            } else {
                null
            }
            val searchChromeBackdrop = searchChromeSource?.takeIf { it.isReady }?.backdrop
            val immersiveSearchChrome = shouldUseBiliPaiProgressiveTopBlur(
                enabled = progressiveTopBlurEnabled && !headerBlurEnabled,
                hasBackdrop = searchChromeBackdrop != null,
            ) && !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
            val searchFadeActive = appThemeConfig.progressiveTopFadeEnabled && !headerBlurEnabled
            // --- 列表内容层 ---
            if (state.showResults) {
                AppScaffold(
                    modifier = Modifier
                        .responsiveContentWidth(maxWidth = searchContentWidth)
                        .fillMaxSize()
                        .nestedScroll(searchHeaderScrollConnection)
                        .graphicsLayer { alpha = exitContentAlpha },
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        val searchChromeSurface = com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(
                            AppSurfaceTokens.groupedListContainer()
                        )
                        BiliPaiImmersiveTopBar(
                            backdrop = searchChromeBackdrop,
                            enabled = immersiveSearchChrome,
                            headerBlurActive = shouldUseSearchTopBarBlur && !immersiveSearchChrome,
                            surfaceColor = searchChromeSurface,
                            fadeEnabled = searchFadeActive,
                            extendBelowBounds = false,
                            modifier = Modifier.then(
                                if (immersiveSearchChrome || searchFadeActive) {
                                    Modifier.background(Color.Transparent)
                                } else if (shouldUseSearchTopBarBlur) {
                                    Modifier
                                        .unifiedBlur(
                                            hazeState = hazeState,
                                            surfaceType = com.android.purebilibili.core.ui.blur.BlurSurfaceType.HEADER,
                                        )
                                        .background(
                                            searchChromeSurface
                                                .copy(alpha = AppSurfaceTokens.FrostedScrimAlpha)
                                        )
                                } else {
                                    Modifier.background(searchChromeSurface)
                                }
                            ),
                        ) {
                            val searchCollapseFraction = if (isSearchCollapseEnabled && searchCollapseDistancePx > 0f) {
                                (-searchHeaderOffsetPx / searchCollapseDistancePx).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Layout(
                                modifier = if (searchCollapseFraction > 0f) {
                                    // Only collapsing controls need a viewport clip. Expanded
                                    // chrome must let the whole capsule's spring overflow.
                                    Modifier.clipToBounds().drawWithContent {
                                        val top = searchStatusBarHeightPx.coerceIn(0f, size.height)
                                        if (size.height > top) {
                                            clipRect(top = top) { this@drawWithContent.drawContent() }
                                        }
                                    }
                                } else Modifier,
                                content = {
                                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coordinates ->
                                                    if (coordinates.size.height > 0 && searchCollapseFraction <= 0.05f) {
                                                        searchTopBarHeightPx = coordinates.size.height
                                                    }
                                                }
                                        ) {
                                            searchTopBarContent(
                                                state.query,
                                                SearchTopBarPlacement(
                                                    focusRequester = searchFocusRequester,
                                                    placeholder = displayedSearchHint.ifBlank { resolveSearchDefaultPlaceholder() },
                                                    suggestedKeyword = displayedSearchHint,
                                                    autoFocusEnabled = false,
                                                    reducedMotionBudget = effectiveSearchMotionBudget == SearchMotionBudget.REDUCED,
                                                    entryMotionSpec = entryMotionSpec,
                                                    entryMotionKey = entryMotionKey,
                                                    exitMotionSpec = exitMotionSpec,
                                                    exitMotionKey = exitMotionKey,
                                                    liquidGlassEnabled = effectiveLiquidGlassEnabled,
                                                    miuixBackdrop = searchChromeBackdrop,
                                                    includeStatusBarPadding = false,
                                                    modifier = Modifier,
                                                ),
                                                if (state.searchType == SearchType.VIDEO) {
                                                    val videoFilters: @Composable RowScope.() -> Unit = {
                                                        SearchVideoFilterActions(
                                                            singleColumn = listLayout.singleColumn,
                                                            onLayoutToggle = listLayout.toggle,
                                                            currentDurations = state.searchDurations,
                                                            currentVideoTid = state.videoTid,
                                                            currentPubTimeType = state.pubTimeType,
                                                            currentPubBegin = state.pubBegin,
                                                            currentPubEnd = state.pubEnd,
                                                            miuixBackdrop = searchChromeBackdrop,
                                                            onDurationSelect = { viewModel.setSearchDuration(it) },
                                                            onVideoTidChange = { viewModel.setVideoTid(it) },
                                                            onPubTimeTypeChange = { viewModel.setPubTimeType(it) },
                                                            onCustomPubTimeRange = { begin, end ->
                                                                viewModel.setCustomPubTimeRange(begin, end)
                                                            }
                                                        )
                                                    }
                                                    videoFilters
                                                } else {
                                                    null
                                                },
                                                searchTopBarCallbacks,
                                            )
                                            //  搜索彩蛋消息横幅
                                            val easterEggMsg = state.easterEggMessage
                                            if (easterEggMsg != null) {
                                                val easterEggColors = resolveAccessibleContainerColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                                    fallbackContentColors = listOf(
                                                        MaterialTheme.colorScheme.onSurface,
                                                        MaterialTheme.colorScheme.onBackground,
                                                    ),
                                                )
                                                AppSurface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    color = easterEggColors.containerColor,
                                                    shape = AppShapes.container(ContainerLevel.Card)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        AppText(
                                                            text = easterEggMsg,
                                                            color = easterEggColors.contentColor,
                                                            style = MaterialTheme.typography.bodyMedium,
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
                                                counts = state.searchTypeCounts,
                                                miuixBackdrop = searchChromeBackdrop,
                                                onTabClick = { page, type ->
                                                    if (searchPagerState.currentPage == page && state.searchType == type) {
                                                        scrollToTopSearchType = type
                                                        scrollToTopRequestId += 1
                                                        animateSearchHeaderOffsetTo(0f)
                                                    } else {
                                                        scope.launch { animatePagerSelection(searchPagerState, page) }
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
                                                if (state.searchType == SearchType.VIDEO) {
                                                    SearchVideoFilterBar(
                                                        currentOrder = state.searchOrder,
                                                        miuixBackdrop = searchChromeBackdrop,
                                                        onOrderChange = { viewModel.setSearchOrder(it) },
                                                    )
                                                } else {
                                                    SearchFilterBar(
                                                        currentType = state.searchType,
                                                        currentOrder = state.searchOrder,
                                                        currentDurations = state.searchDurations,
                                                        currentVideoTid = state.videoTid,
                                                        currentUpOrder = state.upOrder,
                                                        currentUpOrderSort = state.upOrderSort,
                                                        currentUpUserType = state.upUserType,
                                                        currentLiveOrder = state.liveOrder,
                                                        currentArticleOrder = state.articleOrder,
                                                        currentArticleCategory = state.articleCategory,
                                                        currentPhotoOrder = state.photoOrder,
                                                        currentPhotoCategory = state.photoCategory,
                                                        onOrderChange = { viewModel.setSearchOrder(it) },
                                                        onDurationToggle = { viewModel.toggleSearchDuration(it) },
                                                        onVideoTidChange = { viewModel.setVideoTid(it) },
                                                        onUpOrderChange = { viewModel.setUpOrder(it) },
                                                        onUpOrderSortChange = { viewModel.setUpOrderSort(it) },
                                                        onUpUserTypeChange = { viewModel.setUpUserType(it) },
                                                        onLiveOrderChange = { viewModel.setLiveOrder(it) },
                                                        onArticleOrderChange = viewModel::setArticleOrder,
                                                        onArticleCategoryChange = viewModel::setArticleCategory,
                                                        onPhotoOrderChange = viewModel::setPhotoOrder,
                                                        onPhotoCategoryChange = viewModel::setPhotoCategory
                                                    )
                                                }
                                            }
                                        }
                                }
                            ) { measurables, constraints ->
                                val placeables = measurables.map { it.measure(constraints) }
                                val width = placeables.maxOfOrNull { it.width }?.coerceIn(constraints.minWidth, constraints.maxWidth)
                                    ?: constraints.minWidth
                                val topInset = placeables.firstOrNull()?.height ?: 0
                                val offset = if (isSearchCollapseEnabled) searchHeaderOffsetPx.toInt() else 0
                                val height = (placeables.sumOf { it.height } + offset)
                                    .coerceIn(topInset.coerceAtLeast(constraints.minHeight), constraints.maxHeight)
                                layout(width, height) {
                                    placeables.firstOrNull()?.placeRelative(0, 0)
                                    var y = topInset + offset
                                    placeables.drop(1).forEach { placeable ->
                                        placeable.placeRelative(0, y)
                                        y += placeable.height
                                    }
                                }
                            }
                        }
                    },
                ) { resultChromePadding ->
                    val resultTopPadding = resultChromePadding.calculateTopPadding()
                        com.android.purebilibili.core.ui.adaptive.AppHingeSafeContent(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val requestedSkeletonColumns = videoGridColumns
                            val videoGridColumns = if (
                                com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.shouldAvoidHinge
                            ) {
                                com.android.purebilibili.core.ui.adaptive.resolveHingeSafeFeedColumns(
                                    requestedSkeletonColumns, maxWidth.value,
                                    homeSettings.homeFeedCardWidthPreset.minCardWidthDp ?: 180,
                                )
                            } else requestedSkeletonColumns
                        HorizontalPager(
                            state = searchPagerState,
                            userScrollEnabled = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(searchChromeSource?.modifier ?: Modifier)
                        .globalWallpaperAwareBackground(),
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
                        val isEmptyVideoPage = targetSearchType == SearchType.VIDEO &&
                            pageResultState.totalCount == 0
                        val pageError = pageResultState.error
                            ?: pageResultState.loadMoreError.takeIf { isEmptyVideoPage }
                        val isFillingEmptyVideoPage = isEmptyVideoPage &&
                            pageResultState.hasMoreResults && pageError == null
                        val pagePresentation = remember(
                            pageResultState.totalCount,
                            pageResultState.isSearching,
                            pageError,
                            pageResultState.emptyStateReason,
                            pageResultState.isLoadingMore,
                            pageResultState.loadMoreError,
                            pageResultState.hasMoreResults,
                            isFillingEmptyVideoPage
                        ) {
                            resolveSearchResultPresentation(
                                itemCount = pageResultState.totalCount,
                                isSearching = pageResultState.isSearching || isFillingEmptyVideoPage,
                                error = pageError,
                                emptyStateReason = pageResultState.emptyStateReason,
                                isLoadingMore = pageResultState.isLoadingMore,
                                loadMoreError = pageResultState.loadMoreError,
                                hasMoreResults = pageResultState.hasMoreResults
                            )
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
                        val activePageGridState = if (targetSearchType == state.searchType) {
                            resultGridState
                        } else {
                            pageGridState
                        }
                        val activePageListState = if (targetSearchType == state.searchType) {
                            resultListState
                        } else {
                            pageListState
                        }
                        // Observe the viewport, not a specific card's composition. This also
                        // continues after a page is entirely removed by local search filters.
                        val latestVideoPage by rememberUpdatedState(pageResultState)
                        if (targetSearchType == SearchType.VIDEO && targetSearchType == state.searchType) {
                            LaunchedEffect(activePageGridState, state.searchSessionId) {
                                snapshotFlow {
                                    val current = latestVideoPage
                                    current.currentPage to shouldLoadMoreSearchVideos(
                                        itemCount = current.searchResults.size,
                                        lastVisibleItemIndex = activePageGridState.layoutInfo
                                            .visibleItemsInfo.lastOrNull()?.index ?: -1,
                                        hasMoreResults = current.hasMoreResults,
                                        isSearching = current.isSearching,
                                        isLoadingMore = current.isLoadingMore,
                                        hasError = current.error != null || current.loadMoreError != null,
                                    )
                                }.collect { (_, shouldLoad) ->
                                    if (shouldLoad) viewModel.loadMoreResults()
                                }
                            }
                        }
                        LaunchedEffect(scrollToTopRequestId, scrollToTopSearchType, targetSearchType) {
                            if (scrollToTopSearchType == targetSearchType && scrollToTopRequestId > 0) {
                                if (targetSearchType == SearchType.VIDEO) {
                                    activePageGridState.animateScrollToItem(0)
                                } else {
                                    activePageListState.animateScrollToItem(0)
                                }
                            }
                        }
                        if (pagePresentation.body == SearchResultBodyMode.LOADING) {
                            // 结果形态已知：用骨架占位，不用主题 Loading 动画。
                            when (targetSearchType) {
                                SearchType.VIDEO -> {
                                    val skeletonModifier = Modifier
                                        .then(
                                            if (videoGridColumns == 1) {
                                                Modifier.responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .fillMaxSize()
                                    ContentVideoGridSkeletonFixedColumns(
                                        columns = videoGridColumns,
                                        coverAspectRatio = cardLayout.coverAspectRatio,
                                        contentPadding = PaddingValues(
                                            top = resultTopPadding,
                                            bottom = resultBottomPadding,
                                            start = cardLayout.outerPaddingDp.dp,
                                            end = cardLayout.outerPaddingDp.dp,
                                        ),
                                        spacing = cardLayout.itemSpacingDp.dp,
                                        modifier = skeletonModifier,
                                    )
                                }
                                SearchType.UP, SearchType.LIVE_USER -> ContentMediaListSkeleton(
                                    useUserRow = true,
                                    contentPadding = PaddingValues(
                                        top = resultTopPadding,
                                        bottom = resultBottomPadding,
                                    ),
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
                                        .fillMaxSize(),
                                )
                                else -> ContentMediaListSkeleton(
                                    useUserRow = false,
                                    contentPadding = PaddingValues(
                                        top = resultTopPadding,
                                        bottom = resultBottomPadding,
                                    ),
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
                                        .fillMaxSize(),
                                )
                            }
                        } else if (pagePresentation.body == SearchResultBodyMode.ERROR) {
                            SearchNativeMessageState(
                                title = "搜索失败",
                                message = pageError,
                                actionLabel = "重试",
                                onAction = {
                                    if (isEmptyVideoPage && pageResultState.loadMoreError != null) {
                                        viewModel.loadMoreResults()
                                    } else {
                                        viewModel.search(pageResultState.query)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = resultTopPadding, bottom = resultBottomPadding),
                                animation = com.android.purebilibili.core.ui.MaidAnimation.RETRY,
                                isVisible = searchPagerState.currentPage == page
                            )
                        } else if (pagePresentation.body == SearchResultBodyMode.EMPTY) {
                            val copy = pageEmptyStateCopy
                            if (copy != null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(top = resultTopPadding, bottom = resultBottomPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.android.purebilibili.core.ui.EmptyState(
                                        message = copy.title,
                                        subtitle = copy.subtitle,
                                        animation = com.android.purebilibili.core.ui.MaidAnimation.SEARCH_EMPTY,
                                        actionText = "重新搜索",
                                        onAction = { viewModel.search(pageResultState.query) },
                                        enableEasterEgg = false,
                                        isVisible = searchPagerState.currentPage == page
                                    )
                                }
                            }
                        } else {
                        when (targetSearchType) {
                            com.android.purebilibili.data.model.response.SearchType.VIDEO -> {
                                // Size cover requests against the actual result pane, including split windows.
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val actualGridColumns = resolveSearchVideoGridColumns(
                                        singleColumn = listLayout.singleColumn,
                                        contentWidthDp = maxWidth.value.toInt(),
                                        fixedColumnCount = com.android.purebilibili.feature.home.resolveHomeFeedStoredColumnCount(
                                            widthSizeClass = windowSizeClass.widthSizeClass,
                                            compactColumnCount = homeSettings.gridColumnCountCompact,
                                            defaultColumnCount = homeSettings.gridColumnCount,
                                        ),
                                        cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
                                        widthSizeClass = windowSizeClass.widthSizeClass
                                    )
                                    var interactiveColumns by remember { mutableStateOf<Int?>(null) }
                                    var isPinchPillVisible by remember { mutableStateOf(false) }
                                    var pinchPillDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                                    val haptic = LocalHapticFeedback.current
                                    val requestedSearchGridColumns = interactiveColumns ?: actualGridColumns
                                    val effectiveSearchGridColumns = if (
                                        com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.shouldAvoidHinge
                                    ) {
                                        com.android.purebilibili.core.ui.adaptive.resolveHingeSafeFeedColumns(
                                            requestedSearchGridColumns, maxWidth.value,
                                            homeSettings.homeFeedCardWidthPreset.minCardWidthDp ?: 180,
                                        )
                                    } else requestedSearchGridColumns
                                    val pinchColumnBounds = remember(windowSizeClass.widthSizeClass, maxWidth) {
                                        resolveHomeFeedPinchColumnBounds(
                                            widthSizeClass = windowSizeClass.widthSizeClass,
                                            contentWidthDp = maxWidth.value.toInt(),
                                        )
                                    }
                                    LaunchedEffect(homeSettings.gridColumnCount, homeSettings.gridColumnCountCompact) {
                                        interactiveColumns = null
                                    }
                                    val searchGridCardLayout = remember(
                                        homeFeedCardStyle,
                                        effectiveSearchGridColumns,
                                        windowSizeClass.widthSizeClass,
                                    ) {
                                        resolveHomeFeedCardLayout(
                                            style = homeFeedCardStyle,
                                            gridColumns = effectiveSearchGridColumns,
                                            widthSizeClass = windowSizeClass.widthSizeClass,
                                        )
                                    }
                                    val searchGridHorizontalArrangement =
                                        Arrangement.spacedBy(searchGridCardLayout.itemSpacingDp.dp)
                                    val searchCoverRequestSpec = remember(
                                        maxWidth, density.density, searchGridCardLayout, searchLayoutPolicy, effectiveSearchGridColumns
                                    ) {
                                        resolveHomeCoverRequestSpec(
                                            cardWidthDp = resolveSearchGridCardWidthDp(
                                                availableWidthDp = maxWidth.value,
                                                minItemWidthDp = searchLayoutPolicy.resultGridMinItemWidthDp.toFloat(),
                                                horizontalPaddingDp = searchGridCardLayout.outerPaddingDp.toFloat(),
                                                spacingDp = searchGridCardLayout.itemSpacingDp.toFloat(),
                                                fixedColumnCount = effectiveSearchGridColumns,
                                            ),
                                            density = density.density,
                                            useLowQualityCover = false,
                                        )
                                    }
                                    val videoGridModifier = Modifier
                                        .then(
                                            if (effectiveSearchGridColumns == 1) {
                                                Modifier.responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .fillMaxSize()
                                        .homeFeedPinchZoom(
                                            enabled = !listLayout.singleColumn && homeSettings.pinchToChangeGridColumnsEnabled,
                                            currentColumns = effectiveSearchGridColumns,
                                            bounds = pinchColumnBounds,
                                            onColumnsChange = { newColumns ->
                                                interactiveColumns = newColumns
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                isPinchPillVisible = true
                                                pinchPillDismissJob?.cancel()
                                            },
                                            onGestureEnd = { finalColumns ->
                                                scope.launch {
                                                    if (com.android.purebilibili.feature.home.isCompactHomeFeedScreen(windowSizeClass.widthSizeClass)) {
                                                        SettingsManager.setGridColumnCountCompact(context, finalColumns)
                                                    } else {
                                                        SettingsManager.setGridColumnCount(context, finalColumns)
                                                    }
                                                }
                                                pinchPillDismissJob?.cancel()
                                                pinchPillDismissJob = scope.launch {
                                                    kotlinx.coroutines.delay(1000)
                                                    isPinchPillVisible = false
                                                }
                                            }
                                        )
                                        .then(if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(effectiveSearchGridColumns),
                                    state = activePageGridState,
                                    contentPadding = PaddingValues(
                                        top = resultTopPadding,
                                        bottom = resultBottomPadding,
                                        start = searchGridCardLayout.outerPaddingDp.dp,
                                        end = searchGridCardLayout.outerPaddingDp.dp
                                    ),
                                    horizontalArrangement = searchGridHorizontalArrangement,
                                    verticalArrangement = Arrangement.spacedBy(searchGridCardLayout.itemSpacingDp.dp),
                                    modifier = videoGridModifier
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
                                    },
                                    contentType = { _, video -> "search_video_${video.contentType}" }
                                ) { index, video ->
                                        AnimatedVideoListItem(modifier = videoListItemModifier(enabled = cardAnimationEnabled), enabled = cardAnimationEnabled) {
                                            val highlightedTitle = rememberSearchHighlightedTitle(video)
                                            ElegantVideoCard(
                                                video = video,
                                                singleColumn = effectiveSearchGridColumns == 1,
                                                index = index,
                                                animationEnabled = false, // The stable item wrapper owns column-switch motion.
                                                motionTier = cardMotionTier,
                                                transitionEnabled = effectiveCardTransitionEnabled,
                                                isReturningFromVideoDetail = isReturningFromVideoDetail,
                                                isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
                                                showPublishTime = true,
                                                coverRequestSpec = searchCoverRequestSpec,
                                                glassEnabled = videoCardAppearance.glassEnabled,
                                                blurEnabled = videoCardAppearance.blurEnabled,
                                                showCoverGlassBadges = videoCardAppearance.showCoverGlassBadges,
                                                showInfoGlassBadges = videoCardAppearance.showInfoGlassBadges,
                                                coverAspectRatio = searchGridCardLayout.coverAspectRatio,
                                                compactMetadata = searchGridCardLayout.compactMetadata,
                                                compactStatsOnCover = compactVideoStatsOnCover,
                                                titleMinLines = 1,
                                                homeDurationStyle = homeDurationStyle,
                                                highlightedTitle = highlightedTitle,
                                                showOnlineCount = showOnlineCount,
                                                //  [交互优化] 传递 onWatchLater 用于显示菜单选项
                                                onWatchLater = if (video.bvid.isNotBlank()) {
                                                    { viewModel.addToWatchLater(video.bvid, video.id) }
                                                } else {
                                                    null
                                                },
                                                onUpClick = onUpClick,
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
                                                            onVideoClick(target.bvid, 0, video.pic)
                                                        is SearchResultNavigationTarget.Course -> {
                                                            if (onCheeseClick != null) {
                                                                onCheeseClick(target.seasonId, target.epId)
                                                            } else {
                                                                onBangumiClick(target.seasonId)
                                                            }
                                                        }
                                                        is SearchResultNavigationTarget.Web ->
                                                            onWebClick(target.url, target.title)
                                                        else -> Unit
                                                    }
                                                }
                                            )

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
                                                com.android.purebilibili.core.ui.EmptyState(
                                                    message = pageEmptyStateCopy.title,
                                                    subtitle = pageEmptyStateCopy.subtitle,
                                                    animation = com.android.purebilibili.core.ui.MaidAnimation.SEARCH_EMPTY,
                                                    enableEasterEgg = false,
                                                    isVisible = searchPagerState.currentPage == page
                                                )
                                            }
                                        }
                                    }
                                    
                                    //  [新增] 加载更多指示器
                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                                    SearchLoadMoreIndicator(
                                                        error = pageResultState.loadMoreError,
                                                        onRetry = viewModel::loadMoreResults
                                                    )
                                                } else {
                                                    AdaptiveLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }
                                    
                                    //  [新增] 已加载全部提示
                                    if (pagePresentation.footer == SearchResultFooterMode.END) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            AppText(
                                                text = "已加载全部 ${pageResultState.searchResults.size} 条结果",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                    // [新增] 双指缩放切换网格列数 HUD 胶囊 (自适应 MD3 / MIUIX)
                                    GridPinchColumnHudPill(
                                        visible = isPinchPillVisible,
                                        columns = effectiveSearchGridColumns,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = resultTopPadding + 16.dp)
                                    )
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.UP -> {
                                //  UP主搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_up_card" }
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
                                                com.android.purebilibili.core.ui.EmptyState(
                                                    message = pageEmptyStateCopy.title,
                                                    subtitle = pageEmptyStateCopy.subtitle,
                                                    animation = com.android.purebilibili.core.ui.MaidAnimation.SEARCH_EMPTY,
                                                    enableEasterEgg = false,
                                                    isVisible = searchPagerState.currentPage == page
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                                    SearchLoadMoreIndicator(
                                                        error = pageResultState.loadMoreError,
                                                        onRetry = viewModel::loadMoreResults
                                                    )
                                                } else {
                                                    AdaptiveLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.BANGUMI,
                            com.android.purebilibili.data.model.response.SearchType.MEDIA_FT -> {
                                //  番剧/影视搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_bangumi_card" }
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

                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                                    SearchLoadMoreIndicator(
                                                        error = pageResultState.loadMoreError,
                                                        onRetry = viewModel::loadMoreResults
                                                    )
                                                } else {
                                                    AdaptiveLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }

                                    if (!pageResultState.isSearching && pageResultState.bangumiResults.isEmpty() && pageResultState.error == null && pageEmptyStateCopy != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.android.purebilibili.core.ui.EmptyState(
                                                    message = pageEmptyStateCopy.title,
                                                    subtitle = pageEmptyStateCopy.subtitle,
                                                    animation = com.android.purebilibili.core.ui.MaidAnimation.SEARCH_EMPTY,
                                                    enableEasterEgg = false,
                                                    isVisible = searchPagerState.currentPage == page
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.LIVE -> {
                                //  直播搜索结果
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_live_card" }
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
                                                com.android.purebilibili.core.ui.EmptyState(
                                                    message = pageEmptyStateCopy.title,
                                                    subtitle = pageEmptyStateCopy.subtitle,
                                                    animation = com.android.purebilibili.core.ui.MaidAnimation.SEARCH_EMPTY,
                                                    enableEasterEgg = false,
                                                    isVisible = searchPagerState.currentPage == page
                                                )
                                            }
                                        }
                                    }

                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                                    SearchLoadMoreIndicator(
                                                        error = pageResultState.loadMoreError,
                                                        onRetry = viewModel::loadMoreResults
                                                    )
                                                } else {
                                                    AdaptiveLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.LIVE_USER -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_live_user_card" }
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

                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            SearchLoadMoreIndicator(
                                                error = pageResultState.loadMoreError,
                                                onRetry = viewModel::loadMoreResults
                                            )
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.ARTICLE -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_article_card" }
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
                                                com.android.purebilibili.core.ui.EmptyState(
                                                    message = pageEmptyStateCopy.title,
                                                    subtitle = pageEmptyStateCopy.subtitle,
                                                    animation = com.android.purebilibili.core.ui.MaidAnimation.SEARCH_EMPTY,
                                                    enableEasterEgg = false,
                                                    isVisible = searchPagerState.currentPage == page
                                                )
                                            }
                                        }
                                    }

                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                                    SearchLoadMoreIndicator(
                                                        error = pageResultState.loadMoreError,
                                                        onRetry = viewModel::loadMoreResults
                                                    )
                                                } else {
                                                    AdaptiveLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.TOPIC -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_topic_card" }
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

                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            SearchLoadMoreIndicator(
                                                error = pageResultState.loadMoreError,
                                                onRetry = viewModel::loadMoreResults
                                            )
                                        }
                                    }
                                }
                            }
                            com.android.purebilibili.data.model.response.SearchType.PHOTO -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = resultTopPadding, bottom = resultBottomPadding, start = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    state = activePageListState,
                                    modifier = Modifier
                                        .responsiveContentWidth(maxWidth = resolveSearchSingleColumnResultMaxWidth())
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
                                        },
                                        contentType = { _, _ -> "search_photo_card" }
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

                                    if (pagePresentation.footer == SearchResultFooterMode.LOADING || pagePresentation.footer == SearchResultFooterMode.ERROR) {
                                        item {
                                            SearchLoadMoreIndicator(
                                                error = pageResultState.loadMoreError,
                                                onRetry = viewModel::loadMoreResults
                                            )
                                        }
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
                    hotListError = state.hotListError,
                    isRefreshingHotList = state.isRefreshingHotList,
                    discoverTitle = state.discoverTitle,
                    discoverList = state.discoverList,
                    discoverListError = state.discoverListError,
                    isRefreshingDiscoverList = state.isRefreshingDiscoverList,
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
                    onKeywordClick = { keyword ->
                        if (
                            shouldAcceptSearchLandingKeywordClick(
                                clearedAtElapsedMs = searchQueryClearedAtElapsedMs,
                                nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
                            )
                        ) {
                            submitSearch(keyword)
                        }
                    },
                    onClearHistory = viewModel::clearHistory,
                    onDeleteHistory = viewModel::deleteHistory,
                    modifier = Modifier
                        .then(searchChromeSource?.modifier ?: Modifier)
                        .globalWallpaperAwareBackground()
                        .graphicsLayer { alpha = exitContentAlpha }
                        .then(
                            if (searchHazeEnabled) Modifier.hazeSourceCompat(state = hazeState) else Modifier
                        )
                )
            }

            // Landing keeps an overlay search bar; results pin it in the scaffold chrome
            // so the type dock stays below it instead of sliding underneath.
            if (!state.showResults) {
            BiliPaiImmersiveTopBar(
                backdrop = searchChromeBackdrop,
                enabled = immersiveSearchChrome,
                headerBlurActive = shouldUseSearchTopBarBlur && !immersiveSearchChrome,
                extendBelowBounds = false,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
            searchTopBarContent(
                state.query,
                SearchTopBarPlacement(
                    focusRequester = searchFocusRequester,
                    placeholder = displayedSearchHint.ifBlank { resolveSearchDefaultPlaceholder() },
                    suggestedKeyword = displayedSearchHint,
                    autoFocusEnabled = shouldAutoFocusSearchField(
                        startupSettled = startupSettled,
                        query = state.query,
                        showResults = state.showResults,
                        autoFocusConsumed = autoFocusConsumed
                    ),
                    reducedMotionBudget = effectiveSearchMotionBudget == SearchMotionBudget.REDUCED,
                    entryMotionSpec = entryMotionSpec,
                    entryMotionKey = entryMotionKey,
                    exitMotionSpec = exitMotionSpec,
                    exitMotionKey = exitMotionKey,
                    liquidGlassEnabled = effectiveLiquidGlassEnabled,
                    miuixBackdrop = searchChromeBackdrop,
                    includeStatusBarPadding = true,
                    modifier = Modifier
                        .then(
                            if (!immersiveSearchChrome && shouldUseSearchTopBarBlur) {
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
                        .background(
                            if (immersiveSearchChrome || shouldUseSearchTopBarBlur) {
                                Color.Transparent
                            } else {
                                searchTopBarHeaderColor
                            }
                        ),
                ),
                null,
                searchTopBarCallbacks,
            )

            }
            }

            AppLiquidGlassBackToTopButton(
                visible = backToTopButtonEnabled && shouldShowBackToTop,
                onClick = {
                    scope.launch {
                        animateSearchHeaderOffsetTo(0f)
                        if (state.searchType == SearchType.VIDEO) {
                            resultGridState.animateScrollToTop(fast = true)
                        } else {
                            resultListState.animateScrollToTop(fast = true)
                        }
                    }
                },
                backdrop = searchChromeBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = resultBottomPadding + 12.dp
                    ),
            )
            
            // ---  搜索建议下拉列表 ---
            if (state.suggestions.isNotEmpty() && state.query.isNotEmpty() && !state.showResults) {
                SearchSuggestionDropdown(
                    suggestions = state.suggestions,
                    onSuggestionClick = submitSearch,
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

private class SearchTopBarPlacement(
    val focusRequester: androidx.compose.ui.focus.FocusRequester,
    val placeholder: String,
    val suggestedKeyword: String,
    val autoFocusEnabled: Boolean,
    val reducedMotionBudget: Boolean,
    val entryMotionSpec: SearchEntryMotionSpec?,
    val entryMotionKey: Int,
    val exitMotionSpec: SearchEntryMotionSpec?,
    val exitMotionKey: Int,
    val liquidGlassEnabled: Boolean,
    val miuixBackdrop: MiuixBackdrop?,
    val includeStatusBarPadding: Boolean,
    val modifier: Modifier,
)

private class SearchTopBarCallbacks(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearch: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val onEntryMotionFinished: (Int) -> Unit,
    val onExitMotionFinished: (Int) -> Unit,
    val isScrollInProgressProvider: () -> Boolean,
)

// 顶部搜索栏：中性 TextFieldValue 实现，保光标
@Composable
fun SearchTopBar(
    query: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String = resolveSearchDefaultPlaceholder(),
    suggestedKeyword: String = "",
    focusRequester: androidx.compose.ui.focus.FocusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
    autoFocusEnabled: Boolean = true,
    reducedMotionBudget: Boolean = false,
    entryMotionSpec: SearchEntryMotionSpec? = null,
    entryMotionKey: Int = 0,
    onEntryMotionFinished: (Int) -> Unit = {},
    exitMotionSpec: SearchEntryMotionSpec? = null,
    exitMotionKey: Int = 0,
    onExitMotionFinished: (Int) -> Unit = {},
    isScrollInProgressProvider: () -> Boolean = { false },
    liquidGlassEnabled: Boolean = false,
    miuixBackdrop: MiuixBackdrop? = null,
    includeStatusBarPadding: Boolean = true,
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val componentSpacing = resolveSearchComponentSpacingSpec(LocalAppUiStyle.current)
    val topChromePolicy = rememberAppTopChromePolicy()
    val chromeSpec = remember(topChromePolicy) { resolveSearchChromeVisualSpec(topChromePolicy) }
    val inputHeightDp = rememberSearchInputHeightDp(chromeSpec.inputHeightDp)
    val topBarRowMinHeightDp = remember(inputHeightDp) {
        resolveSearchTopBarRowMinHeightDp(inputHeightDp)
    }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val backIcon = rememberAppBackIcon()
    val density = LocalDensity.current
    val entryMotionProgress = remember { Animatable(1f) }
    val latestOnEntryMotionFinished by rememberUpdatedState(onEntryMotionFinished)
    val latestOnExitMotionFinished by rememberUpdatedState(onExitMotionFinished)
    val backLabel = stringResource(R.string.common_back)
    val resolvedSubmitKeyword = remember(query, suggestedKeyword) {
        resolveSearchSubmitKeyword(
            query = query,
            suggestedKeyword = suggestedKeyword
        )
    }
    val canSubmit = resolvedSubmitKeyword.isNotBlank()
    val liquidGlassRenderConfig = LocalLiquidGlassRenderConfig.current
    val glassActive = liquidGlassEnabled && miuixBackdrop != null && !isLowBlurBudgetForced()
    val glassRenderMode = if (glassActive) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
    } else {
        HomeTopChromeRenderMode.PLAIN
    }
    val searchElasticState = com.android.purebilibili.core.ui.components.rememberAppElasticPressState(
        feedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
    )
    val backElasticState = com.android.purebilibili.core.ui.components.rememberAppElasticPressState()
    fun Modifier.searchTopChromeGlass(
        shape: androidx.compose.ui.graphics.Shape,
        controlHeightDp: Int,
        interactionState: com.android.purebilibili.core.ui.components.AppElasticPressState? = null,
    ): Modifier {
        if (!glassActive) return this
        return homeTopBottomBarMatchedSurface(
            renderMode = glassRenderMode,
            shape = shape,
            hazeState = null,
            miuixBackdrop = miuixBackdrop,
            liquidGlassStyle = com.android.purebilibili.core.store.LiquidGlassStyle.CLASSIC,
            liquidGlassTuning = liquidGlassRenderConfig.tuning,
            liquidGlassPreset = liquidGlassRenderConfig.preset,
            motionTier = MotionTier.Normal,
            isScrolling = isScrollInProgressProvider(),
            isTransitionRunning = false,
            forceLowBlurBudget = false,
            drawShellLens = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(controlHeightDp.toFloat()),
            interactionState = interactionState,
        )
    }

    // Preserve caret/selection while typing; only resync when external text changes
    // (clear, keyword click, initial keyword). Using TextFieldValue avoids String-field
    // cursor jumps on every parent recomposition.
    var textFieldValue by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = query,
                selection = androidx.compose.ui.text.TextRange(query.length)
            )
        )
    }
    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = query,
                selection = androidx.compose.ui.text.TextRange(query.length)
            )
        }
    }

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
        latestOnEntryMotionFinished(entryMotionKey)
    }
    LaunchedEffect(exitMotionKey, exitMotionSpec) {
        val spec = exitMotionSpec
        if (exitMotionKey <= 0 || spec == null) return@LaunchedEffect
        if (spec.durationMillis <= 0) {
            entryMotionProgress.snapTo(0f)
        } else {
            entryMotionProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = spec.durationMillis,
                    easing = AppMotionEasing.Continuity
                )
            )
        }
        latestOnExitMotionFinished(exitMotionKey)
    }
    LaunchedEffect(autoFocusEnabled, focusRequester) {
        if (autoFocusEnabled) {
            kotlinx.coroutines.delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }
    val activeMotionSpec = entryMotionSpec ?: exitMotionSpec
    val entryMotionModifier = if (activeMotionSpec != null) {
        Modifier.graphicsLayer {
            val progress = entryMotionProgress.value
            val spec = activeMotionSpec
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

    AppSurface(
        modifier = modifier
            .fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Column {
            if (includeStatusBarPadding) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }

            Row(
                modifier = Modifier
                    .responsiveContentWidth()
                    .heightIn(min = topBarRowMinHeightDp.dp)
                    .padding(horizontal = componentSpacing.horizontalPaddingDp.dp, vertical = (SEARCH_TOP_BAR_VERTICAL_PADDING_DP / 2).dp)
                    .padding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).asPaddingValues())
                    .then(entryMotionModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val inputShape = resolveSearchInputShape(topChromePolicy)
                val actionShape = resolveHomeTopEdgeButtonShape(topChromePolicy)
                SearchTopBarIconButton(
                    onClick = onBack,
                    elasticState = backElasticState,
                    shape = actionShape,
                    transformInBackdrop = glassActive,
                    modifier = Modifier
                        .size(chromeSpec.clearActionSizeDp.dp)
                        .searchTopChromeGlass(actionShape, chromeSpec.clearActionSizeDp, backElasticState)
                ) {
                    AppIcon(
                        backIcon,
                        contentDescription = backLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(chromeSpec.actionIconSizeDp.dp)
                    )
                }

                Spacer(modifier = Modifier.width(componentSpacing.actionGapDp.dp))

                val containerColor = if (glassActive) {
                    Color.Transparent
                } else if (chromeSpec.useFilledSearchAction) {
                    AppSurfaceTokens.surfaceContainerHigh()
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                SearchTopBarInputField(
                    value = textFieldValue,
                    onValueChange = { next ->
                        textFieldValue = next
                        if (next.text != query) {
                            onQueryChange(next.text)
                        }
                    },
                    onSearch = {
                        if (canSubmit) onSearch(resolvedSubmitKeyword)
                    },
                    placeholder = placeholder,
                    containerColor = containerColor,
                    fieldShape = inputShape,
                    focusRequester = focusRequester,
                    interactionSource = searchInteractionSource,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(inputHeightDp.dp)
                        .appElasticPress(state = searchElasticState, transformInBackdrop = glassActive, highlightShape = inputShape)
                        .searchTopChromeGlass(inputShape, inputHeightDp, searchElasticState)
                        .onFocusChanged { onFocusChanged(it.isFocused) }
                )

                if (trailingActions != null) {
                    Spacer(modifier = Modifier.width(chromeSpec.horizontalGapDp.dp))
                    trailingActions()
                }
            }
        }
    }
}

@Composable
private fun SearchTopBarIconButton(
    onClick: () -> Unit,
    elasticState: com.android.purebilibili.core.ui.components.AppElasticPressState,
    shape: androidx.compose.ui.graphics.Shape,
    transformInBackdrop: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.sizeIn(
            minWidth = AppChromeSizeTokens.MinimumTouchTarget,
            minHeight = AppChromeSizeTokens.MinimumTouchTarget,
        ),
        contentAlignment = Alignment.Center,
    ) {
        // The surface owns motion; never move only the glyph inside a fixed clipping shell.
        Box(
            modifier = modifier.appElasticPress(
                enabled = enabled,
                state = elasticState,
                transformInBackdrop = transformInBackdrop,
                highlightShape = shape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.android.purebilibili.core.ui.components.LocalElasticPressEnabled provides false,
            ) {
                AppIconButton(
                    onClick = onClick,
                    // drawBackdrop already transforms the surface and its descendants.
                    modifier = Modifier.matchParentSize(),
                    enabled = enabled,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SearchTopBarInputField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    containerColor: Color,
    fieldShape: androidx.compose.ui.graphics.Shape,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Use AppSurfaceTokens so capsule text keeps contrast in both themes.
    val contentColor = AppSurfaceTokens.onSurface()
    val placeholderColor = AppSurfaceTokens.onSurfaceVariantSummary()
    val focusBorderColor = AppSurfaceTokens.primary()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = contentColor,
        // Explicit line height avoids type-scale clipping in single-line fields.
        lineHeight = SEARCH_INPUT_LINE_HEIGHT_SP.sp
    )
    val cursorBrush = androidx.compose.ui.graphics.SolidColor(focusBorderColor)

    // Shared implementation: BasicTextField + TextFieldValue keeps cursor state.
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .then(
                if (containerColor.alpha > 0.001f) {
                    Modifier
                        .clip(fieldShape)
                        .background(containerColor, fieldShape)
                } else {
                    Modifier
                }
            )
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = focusBorderColor,
                        shape = fieldShape
                    )
                } else {
                    Modifier
                }
            ),
        textStyle = textStyle,
        singleLine = true,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                if (value.text.isEmpty()) {
                    AppText(
                        text = placeholder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle,
                        color = placeholderColor
                    )
                }
                // Provide LocalContentColor so platform text paint never falls back to
                // a low-contrast Miuix default inside the transparent liquid capsule.
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides contentColor
                ) {
                    innerTextField()
                }
            }
        }
    )
}

// 气泡化历史记录：中性 InputChip（AppInputChip），视觉跟随主题层。
@Composable
fun HistoryChip(
    keyword: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val clearIcon = rememberAppClearIcon()
    val deleteLabel = stringResource(R.string.common_delete)
    AppInputChip(
        selected = false,
        onClick = onClick,
        label = {
            AppText(
                text = keyword,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            AppIconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                AppIcon(
                    clearIcon,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    )
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
            .heightIn(min = 48.dp)
            .padding(8.dp)
    ) {
        AppText(text = emoji, style = MaterialTheme.typography.headlineSmall)
        AppText(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
                AppText(
                    "💎",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    title, //  使用动态标题
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 刷新按钮
            AppTextButton(onClick = onRefresh) {
                AppText(
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
                AppSurface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = AppShapes.container(ContainerLevel.Chip),
                    modifier = Modifier.clickable { onItemClick(keyword) }
                ) {
                    AppText(
                        keyword,
                        style = MaterialTheme.typography.labelMedium,
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
                    AppText(
                        "", // 🔥
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText(
                        "热门搜索",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                AppSurface(
                    onClick = onToggleHotSearch,
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = if (hotSearchEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    }
                ) {
                    AppText(
                        text = if (hotSearchEnabled) "热搜开" else "热搜关",
                        color = if (hotSearchEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelSmall,
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
                                    AppText(
                                        text = "${globalIndex + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.width(24.dp)
                                    )

                                    // 标题
                                    AppText(
                                        text = hotItem.show_name,
                                        style = MaterialTheme.typography.bodyMedium,
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
 * Search type tabs — same interaction model as home top tabs:
 * floating capsule follows [PagerState.currentPage] + [PagerState.currentPageOffsetFraction]
 * and can be interrupted mid-swipe / mid-animate.
 */
/** PiliPlus 同款分类计数标签:未加载(-1/null)只显示名称,超过 99 显示 99+。 */
internal fun resolveSearchTypeTabLabel(displayName: String, count: Int?): String {
    if (count == null || count < 0) return displayName
    return "$displayName ${if (count > 99) "99+" else count}"
}

@Composable
private fun SearchResultTypeTabRow(
    tabs: List<SearchType>,
    pagerState: PagerState,
    counts: Map<SearchType, Int>,
    onTabClick: (Int, SearchType) -> Unit,
    miuixBackdrop: MiuixBackdrop? = null,
) {
    val componentSpacing = resolveSearchComponentSpacingSpec(LocalAppUiStyle.current)
    if (tabs.isEmpty()) return
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val selectedIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
    val tabLabels = tabs.map { type ->
        resolveSearchTypeTabLabel(type.displayName, counts[type])
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = componentSpacing.resultRailHorizontalPaddingDp.dp,
                vertical = componentSpacing.rowVerticalPaddingDp.dp,
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val viewportWidthDp = maxWidth.value.roundToInt()
        val useScrollableRail = shouldScrollSearchTypeTabs(
            itemCount = tabs.size,
            viewportWidthDp = viewportWidthDp
        )
        val itemWidthDp = resolveSearchTypeTabAdaptiveItemWidthDp(
            itemCount = tabs.size,
            viewportWidthDp = viewportWidthDp
        )
        val itemWidth = itemWidthDp.dp
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val containerHorizontalPaddingPx = with(density) { AppSpacingTokens.ExtraSmall.toPx() }
        val dragFollowEdgePaddingPx = with(density) { 12.dp.toPx() }

        if (!LocalAppThemeConfig.current.liquidGlassEnabled) {
            // Compact non-glass categories use the active theme colors and label typography.
            // 液态玻璃开启时保持玻璃胶囊指示器,不进入本分支。
            AppTonalPillTabRow(
                options = tabs.mapIndexed { index, type ->
                    AppSegmentOption(value = type, label = tabLabels[index])
                },
                selectedValue = tabs.getOrElse(selectedIndex) { tabs.first() },
                onSelectionChange = { type ->
                    tabs.indexOf(type).takeIf { it >= 0 }?.let { index ->
                        onTabClick(index, type)
                    }
                },
                scrollable = true,
                height = 40.dp,
                selectedContainerColor = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                    com.android.purebilibili.core.ui.AppSurfaceTokens.surfaceContainerHigh()
                } else {
                    com.android.purebilibili.core.ui.AppSurfaceTokens.primary().copy(alpha = 0.16f)
                },
                selectedContentColor = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                    com.android.purebilibili.core.ui.AppSurfaceTokens.onSurfaceContainerHigh()
                } else {
                    com.android.purebilibili.core.ui.AppSurfaceTokens.primary()
                },
                unselectedContentColor = com.android.purebilibili.core.ui.AppSurfaceTokens.onSurfaceVariantSummary(),
                labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
                indicatorPositionProvider = {
                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                },
            )
            return@BoxWithConstraints
        }

        KeepScrollableTabSelectionVisible(
            scrollState = scrollState,
            selectedIndex = if (useScrollableRail) selectedIndex else 0,
            itemWidthPx = itemWidthPx,
            contentPaddingPx = containerHorizontalPaddingPx,
            // Glide with the indicator (page + fraction) while the pager is moving; settle when idle.
            focusPosition = {
                if (useScrollableRail) {
                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                } else {
                    0f
                }
            },
            continuousFollow = { useScrollableRail && pagerState.isScrollInProgress },
        )

        BottomBarLiquidSegmentedControl(
            items = tabLabels,
            selectedIndex = selectedIndex,
            onSelected = { index ->
                tabs.getOrNull(index)?.let { onTabClick(index, it) }
            },
            itemWidth = itemWidth.takeIf { useScrollableRail },
            height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
            indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
            labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
            allowNativeLabelOverflow = true,
            miuixBackdrop = miuixBackdrop,
            liquidGlassEffectsEnabled = true,
            tapPressRefractionEnabled = !useScrollableRail,
            dragSelectionEnabled = tabs.size > 1,
            indicatorPositionProvider = {
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            },
            isScrollInProgressProvider = { pagerState.isScrollInProgress },
            externalPagerMotionEffectsEnabled = true,
            onIndicatorPositionChanged = { position ->
                // Continuous pager motion is owned by KeepScrollableTabSelectionVisible lock-step.
                // Edge-follow remains only for idle indicator nudges so the two never fight.
                if (useScrollableRail && !pagerState.isScrollInProgress) {
                    scrollState.dispatchRawDelta(
                        resolveSearchTypeTabDragScrollDeltaPx(
                            indicatorPosition = position,
                            itemWidthPx = itemWidthPx,
                            viewportWidthPx = viewportWidthPx,
                            currentScrollPx = scrollState.value.toFloat(),
                            containerHorizontalPaddingPx = containerHorizontalPaddingPx,
                            edgePaddingPx = dragFollowEdgePaddingPx
                        )
                    )
                }
            },
            modifier = if (useScrollableRail) {
                Modifier.liquidDockViewport()
            } else {
                Modifier.fillMaxWidth()
            },
            scrollState = scrollState.takeIf { useScrollableRail },
        )
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
    currentArticleOrder: SearchOrder,
    currentArticleCategory: SearchArticleCategory,
    currentPhotoOrder: SearchOrder,
    currentPhotoCategory: SearchPhotoCategory,
    onOrderChange: (SearchOrder) -> Unit,
    onDurationToggle: (SearchDuration) -> Unit,
    onVideoTidChange: (Int) -> Unit,
    onUpOrderChange: (SearchUpOrder) -> Unit,
    onUpOrderSortChange: (SearchOrderSort) -> Unit,
    onUpUserTypeChange: (SearchUserType) -> Unit,
    onLiveOrderChange: (SearchLiveOrder) -> Unit,
    onArticleOrderChange: (SearchOrder) -> Unit,
    onArticleCategoryChange: (SearchArticleCategory) -> Unit,
    onPhotoOrderChange: (SearchOrder) -> Unit,
    onPhotoCategoryChange: (SearchPhotoCategory) -> Unit
) {
    var showOrderMenu by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }
    var showVideoTidMenu by remember { mutableStateOf(false) }
    var showUpOrderMenu by remember { mutableStateOf(false) }
    var showUpOrderSortMenu by remember { mutableStateOf(false) }
    var showUpUserTypeMenu by remember { mutableStateOf(false) }
    var showLiveOrderMenu by remember { mutableStateOf(false) }
    var showArticleOrderMenu by remember { mutableStateOf(false) }
    var showArticleCategoryMenu by remember { mutableStateOf(false) }
    var showPhotoOrderMenu by remember { mutableStateOf(false) }
    var showPhotoCategoryMenu by remember { mutableStateOf(false) }

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
    
    val miuixNonGlass = isMiuixNonGlassEnabled()
    val filterRowPadding = if (miuixNonGlass) {
        Modifier.padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small)
    } else {
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    }
    val chipSpacing = if (miuixNonGlass) 9.dp else 10.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(filterRowPadding)
    ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                verticalArrangement = Arrangement.spacedBy(if (miuixNonGlass) AppSpacingTokens.Small else 8.dp)
            ) {
                if (SearchFilterControl.VIDEO_ORDER in filterControls) {
                Box {
                    FilterMenuChip(
                        text = resolveSearchOrderChipLabel(currentOrder),
                        highlighted = currentOrder != SearchOrder.TOTALRANK,
                        onClick = { showOrderMenu = true }
                    )
                    AppDropdownMenu(
                        expanded = showOrderMenu,
                        onDismissRequest = { showOrderMenu = false }
                    ) {
                        resolveSearchVideoOrderOptions().forEach { order ->
                            AppDropdownMenuItem(
                                text = { AppText(resolveSearchOrderChipLabel(order)) },
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
                    AppDropdownMenu(
                        expanded = showDurationMenu,
                        onDismissRequest = { showDurationMenu = false }
                    ) {
                        SearchDuration.entries.forEach { duration ->
                            val selected = if (duration == SearchDuration.ALL) {
                                currentDurations.isEmpty()
                            } else {
                                duration in currentDurations
                            }
                            AppDropdownMenuItem(
                                text = { AppText(duration.displayName) },
                                leadingIcon = {
                                    AppCheckbox(
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
                    AppDropdownMenu(
                        expanded = showVideoTidMenu,
                        onDismissRequest = { showVideoTidMenu = false }
                    ) {
                        videoTidOptions.forEach { (tid, name) ->
                            AppDropdownMenuItem(
                                text = { AppText(name) },
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
                    AppDropdownMenu(
                        expanded = showUpOrderMenu,
                        onDismissRequest = { showUpOrderMenu = false }
                    ) {
                        SearchUpOrder.entries.forEach { order ->
                            AppDropdownMenuItem(
                                text = { AppText(order.displayName) },
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
                        AppDropdownMenu(
                            expanded = showUpOrderSortMenu,
                            onDismissRequest = { showUpOrderSortMenu = false }
                        ) {
                            SearchOrderSort.entries.forEach { sort ->
                                AppDropdownMenuItem(
                                    text = { AppText(sort.displayName) },
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
                    AppDropdownMenu(
                        expanded = showUpUserTypeMenu,
                        onDismissRequest = { showUpUserTypeMenu = false }
                    ) {
                        SearchUserType.entries.forEach { userType ->
                            AppDropdownMenuItem(
                                text = { AppText(userType.displayName) },
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
                        AppDropdownMenu(
                            expanded = showLiveOrderMenu,
                            onDismissRequest = { showLiveOrderMenu = false }
                        ) {
                            SearchLiveOrder.entries.forEach { order ->
                                AppDropdownMenuItem(
                                    text = { AppText(order.displayName) },
                                    onClick = {
                                        onLiveOrderChange(order)
                                        showLiveOrderMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (SearchFilterControl.ARTICLE_ORDER in filterControls) {
                    SearchOrderMenu(
                        current = currentArticleOrder,
                        expanded = showArticleOrderMenu,
                        options = SearchOrder.entries,
                        onExpandedChange = { showArticleOrderMenu = it },
                        onSelected = onArticleOrderChange
                    )
                }

                if (SearchFilterControl.ARTICLE_CATEGORY in filterControls) {
                    SearchCategoryMenu(
                        label = currentArticleCategory.displayName,
                        highlighted = currentArticleCategory != SearchArticleCategory.ALL,
                        expanded = showArticleCategoryMenu,
                        options = SearchArticleCategory.entries.map { it.displayName },
                        onExpandedChange = { showArticleCategoryMenu = it },
                        onSelected = { index ->
                            SearchArticleCategory.entries.getOrNull(index)?.let(onArticleCategoryChange)
                        }
                    )
                }

                if (SearchFilterControl.PHOTO_ORDER in filterControls) {
                    SearchOrderMenu(
                        current = currentPhotoOrder,
                        expanded = showPhotoOrderMenu,
                        options = SearchOrder.entries.filter { it != SearchOrder.ATTENTION },
                        onExpandedChange = { showPhotoOrderMenu = it },
                        onSelected = onPhotoOrderChange
                    )
                }

                if (SearchFilterControl.PHOTO_CATEGORY in filterControls) {
                    SearchCategoryMenu(
                        label = currentPhotoCategory.displayName,
                        highlighted = currentPhotoCategory != SearchPhotoCategory.ALL,
                        expanded = showPhotoCategoryMenu,
                        options = SearchPhotoCategory.entries.map { it.displayName },
                        onExpandedChange = { showPhotoCategoryMenu = it },
                        onSelected = { index ->
                            SearchPhotoCategory.entries.getOrNull(index)?.let(onPhotoCategoryChange)
                        }
                    )
                }
        }
    }
}

@Composable
private fun SearchOrderMenu(
    current: SearchOrder,
    expanded: Boolean,
    options: List<SearchOrder>,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (SearchOrder) -> Unit
) {
    Box {
        FilterMenuChip(
            text = current.displayName,
            highlighted = current != SearchOrder.TOTALRANK,
            onClick = { onExpandedChange(true) }
        )
        AppDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { order ->
                AppDropdownMenuItem(
                    text = { AppText(order.displayName) },
                    onClick = {
                        onSelected(order)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchCategoryMenu(
    label: String,
    highlighted: Boolean,
    expanded: Boolean,
    options: List<String>,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int) -> Unit
) {
    Box {
        FilterMenuChip(label, highlighted) { onExpandedChange(true) }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEachIndexed { index, option ->
                AppDropdownMenuItem(
                    text = { AppText(option) },
                    onClick = {
                        onSelected(index)
                        onExpandedChange(false)
                    }
                )
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
    AppFilterChip(
        selected = highlighted,
        onClick = onClick,
        label = {
            AppText(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            AppIcon(
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
    val surfaceSpec = rememberContentCardSurfaceSpec()
    val shape = AppShapes.borderedContainer(surfaceSpec.cornerLevel)
    val color = if (surfaceSpec.usesTonalContainerTreatment) {
        AppSurfaceTokens.surfaceContainer()
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = appearance.containerAlpha)
    }
    val border = when {
        surfaceSpec.usesTonalContainerTreatment -> {
            androidx.compose.foundation.BorderStroke(
                surfaceSpec.borderWidthDp.dp,
                AppSurfaceTokens.divider().copy(alpha = surfaceSpec.borderAlpha)
            )
        }
        appearance.borderAlpha > 0f -> {
            androidx.compose.foundation.BorderStroke(
                0.8.dp,
                Color.White.copy(alpha = appearance.borderAlpha)
            )
        }
        else -> null
    }
    if (onClick != null) {
        AppSurface(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            color = color,
            shape = shape,
            tonalElevation = if (surfaceSpec.usesTonalContainerTreatment) {
                surfaceSpec.tonalElevationDp.dp
            } else {
                appearance.tonalElevationDp.dp
            },
            shadowElevation = if (surfaceSpec.usesTonalContainerTreatment) {
                surfaceSpec.shadowElevationDp.dp
            } else {
                appearance.shadowElevationDp.dp
            },
            border = border
        ) {
            content()
        }
    } else {
        AppSurface(
            modifier = modifier.fillMaxWidth(),
            color = color,
            shape = shape,
            tonalElevation = if (surfaceSpec.usesTonalContainerTreatment) {
                surfaceSpec.tonalElevationDp.dp
            } else {
                appearance.tonalElevationDp.dp
            },
            shadowElevation = if (surfaceSpec.usesTonalContainerTreatment) {
                surfaceSpec.shadowElevationDp.dp
            } else {
                appearance.shadowElevationDp.dp
            },
            border = border
        ) {
            content()
        }
    }
}

private enum class SearchResultTextRole {
    DENSE_TITLE,
    TITLE,
    BODY,
    METADATA,
    BADGE,
}

@Composable
private fun SearchResultText(
    text: String,
    role: SearchResultTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val style = when (role) {
        SearchResultTextRole.DENSE_TITLE -> MaterialTheme.typography.bodySmall
        SearchResultTextRole.TITLE -> MaterialTheme.typography.titleSmall
        SearchResultTextRole.BODY -> MaterialTheme.typography.bodySmall
        SearchResultTextRole.METADATA -> MaterialTheme.typography.labelMedium
        SearchResultTextRole.BADGE -> MaterialTheme.typography.labelSmall
    }
    AppText(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        minLines = minLines,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 *  搜索结果卡片 (显示发布时间)
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchResultCard(
    video: VideoItem,
    index: Int,
    onClick: (String) -> Unit
) {
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    val coverUrl = remember(video.bvid) {
        FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .clickable { onClick(video.bvid) }
            .padding(bottom = if (useMiuixNonGlassPresentation) AppSpacingTokens.Small else 8.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(AppShapes.mediaCover())
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
            
            VideoCardCoverDurationText(
                text = FormatUtils.formatDuration(video.duration),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
            
        }
        
        Spacer(
            modifier = Modifier.height(
                if (useMiuixNonGlassPresentation) AppSpacingTokens.Small else 8.dp
            )
        )
        
        // 标题
        SearchResultText(
            text = video.title,
            role = SearchResultTextRole.DENSE_TITLE,
            minLines = 1,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(
            modifier = Modifier.height(
                if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 6.dp
            )
        )
        HorizontalVideoStatRow(
            playText = FormatUtils.formatStat(video.stat.view.toLong()),
            danmakuText = if (video.stat.danmaku > 0) {
                FormatUtils.formatStat(video.stat.danmaku.toLong())
            } else {
                ""
            },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        
        Spacer(
            modifier = Modifier.height(
                if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 6.dp
            )
        )
        
        // UP主 + 发布时间
        FlowRow(
            modifier = Modifier.padding(horizontal = 2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 2.dp
            ),
            verticalArrangement = Arrangement.spacedBy(
                if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 2.dp
            )
        ) {
            UpBadgeName(
                name = video.owner.name,
                leadingContent = if (
                    com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars &&
                    video.owner.face.isNotBlank()
                ) {
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
                nameStyle = MaterialTheme.typography.labelSmall,
                nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                badgeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
                modifier = Modifier.wrapContentWidth()
            )
            
            //  显示发布时间
            if (video.pubdate > 0) {
                SearchResultText(
                    text = "· ${FormatUtils.formatPublishTime(video.pubdate)}",
                    role = SearchResultTextRole.BADGE,
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
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
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
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
                ),
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

            val verifyBadge = cleanedItem.official_verify?.let { verify ->
                resolveOfficialVerifyBadge(type = verify.type, desc = verify.desc)
            }
            Box(modifier = avatarModifier.size(42.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(cleanedItem.upic)
                        .crossfade(true)
                        .build(),
                    contentDescription = cleanedItem.uname,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (verifyBadge != null) {
                    OfficialVerifyAvatarBadge(
                        badge = verifyBadge,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                } else {
                    UserAvatarCornerMarkBadge(
                        mark = resolveUserAvatarCornerMark(
                            officialType = null,
                            vipStatus = cleanedItem.vip?.status,
                        ),
                        modifier = Modifier.align(Alignment.BottomEnd),
                        badgeSize = 14.dp,
                    )
                }
            }
            
            Spacer(
                modifier = Modifier.width(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 10.dp
                )
            )
            
            // UP主信息
            Column(modifier = Modifier.weight(1f)) {
                // 名称 + 官方等级标志
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 6.dp
                    )
                ) {
                    SearchResultText(
                        text = cleanedItem.uname,
                        role = SearchResultTextRole.TITLE,
                        modifier = Modifier.weight(1f, fill = false),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    UserLevelBadge(
                        level = cleanedItem.level,
                        isSeniorMember = cleanedItem.is_senior_member == 1
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 2.dp
                    )
                )
                SearchResultText(
                    text = "粉丝：${FormatUtils.formatStat(cleanedItem.fans.toLong())}  " +
                        "视频：${cleanedItem.videos}",
                    role = SearchResultTextRole.METADATA,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (verifyBadge != null && verifyBadge.text.isNotBlank()) {
                    Spacer(
                        modifier = Modifier.height(
                            if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 2.dp
                        )
                    )
                    SearchResultText(
                        text = verifyBadge.text,
                        role = SearchResultTextRole.METADATA,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
                ),
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
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            
            // 番剧信息
            Column(modifier = Modifier.weight(1f)) {
                if (!categoryLabel.isNullOrBlank()) {
                    AppAssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            SearchResultText(
                                text = categoryLabel,
                                role = SearchResultTextRole.BADGE,
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                }
                SearchResultText(
                    text = item.title,
                    role = SearchResultTextRole.TITLE,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                // 类型 + 集数
                Row {
                    if (item.seasonTypeName.isNotBlank()) {
                        SearchResultText(
                            text = item.seasonTypeName,
                            role = SearchResultTextRole.METADATA,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                    }
                    if (item.indexShow.isNotBlank()) {
                        SearchResultText(
                            text = item.indexShow,
                            role = SearchResultTextRole.METADATA,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 评分
                item.mediaScore?.let { score ->
                    if (score.score > 0) {
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchResultText(
                                text = "⭐ ${score.score}",
                                role = SearchResultTextRole.METADATA,
                                color = Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                            SearchResultText(
                                text = "${score.userCount}人评分",
                                role = SearchResultTextRole.BADGE,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                // 简介
                if (item.desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                    SearchResultText(
                        text = item.desc,
                        role = SearchResultTextRole.BODY,
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
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
                ),
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
                        .clip(AppShapes.container(ContainerLevel.Chip))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                // 直播状态标签
                if (item.live_status == 1) {
                    AppSurface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(AppSpacingTokens.ExtraSmall),
                        color = MaterialTheme.colorScheme.error,
                        shape = AppShapes.container(ContainerLevel.Tag)
                    ) {
                        SearchResultText(
                            text = "直播中",
                            role = SearchResultTextRole.BADGE,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(
                                horizontal = AppSpacingTokens.ExtraSmall,
                                vertical = AppSpacingTokens.Micro,
                            )
                        )
                    }
                }
                // 在线人数
                if (item.online > 0) {
                    AppSurface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(AppSpacingTokens.ExtraSmall),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = AppShapes.container(ContainerLevel.Tag)
                    ) {
                        SearchResultText(
                            text = FormatUtils.formatStat(item.online.toLong()),
                            role = SearchResultTextRole.BADGE,
                            color = Color.White,
                            modifier = Modifier.padding(
                                horizontal = AppSpacingTokens.ExtraSmall,
                                vertical = AppSpacingTokens.Micro,
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            
            // 直播信息
            Column(modifier = Modifier.weight(1f)) {
                SearchResultText(
                    text = item.title,
                    role = SearchResultTextRole.TITLE,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                // 主播名
                SearchResultText(
                    text = item.uname,
                    role = SearchResultTextRole.METADATA,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(
                    modifier = Modifier.height(
                        if (useMiuixNonGlassPresentation) AppSpacingTokens.ExtraSmall else 2.dp
                    )
                )
                // 分区
                if (item.area_v2_name.isNotBlank()) {
                    SearchResultText(
                        text = "${item.area_v2_parent_name} · ${item.area_v2_name}",
                        role = SearchResultTextRole.BADGE,
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
private fun SearchLoadMoreIndicator(
    error: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    if (error != null) {
        SearchNativeMessageState(
            title = "加载更多失败",
            message = error,
            actionLabel = onRetry?.let { "重试" },
            onAction = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SearchNativeMessageState(
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    animation: com.android.purebilibili.core.ui.MaidAnimation? = null,
    isVisible: Boolean = true
) {
    com.android.purebilibili.core.ui.MaidStateViewport(
        modifier = modifier.padding(24.dp)
    ) {
        if (animation != null) {
            var replayKey by remember(animation, title, message) { mutableIntStateOf(0) }
            com.android.purebilibili.core.ui.BlueSnowMaidAnimation(
                animation = animation,
                isVisible = isVisible,
                replayKey = replayKey,
                modifier = Modifier
                    .size(com.android.purebilibili.core.ui.maidStateIllustrationSize())
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = "重播蓝雪女仆动画"
                    ) { replayKey++ }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            if (animation == com.android.purebilibili.core.ui.MaidAnimation.RETRY) {
                com.android.purebilibili.core.ui.components.AppButton(onClick = onAction) {
                    AppText(actionLabel)
                }
            } else {
                AppTextButton(onClick = onAction) {
                    AppText(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun LiveUserSearchResultCard(
    item: SearchLiveUserItem,
    appearance: SearchResultCardAppearance,
    onClick: () -> Unit
) {
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    val cleaned = remember(item.uid, item.uname, item.uface) { item.cleanupFields() }
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
                ),
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
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchResultText(
                        text = cleaned.uname,
                        role = SearchResultTextRole.TITLE,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (cleaned.isLive || cleaned.liveStatus == 1) {
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                        AppSurface(
                            color = MaterialTheme.colorScheme.error,
                            shape = AppShapes.container(ContainerLevel.Tag)
                        ) {
                            SearchResultText(
                                text = "直播中",
                                role = SearchResultTextRole.BADGE,
                                color = Color.White,
                                modifier = Modifier.padding(
                                    horizontal = AppSpacingTokens.ExtraSmall,
                                    vertical = AppSpacingTokens.Micro,
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                SearchResultText(
                    text = "粉丝 ${FormatUtils.formatStat(cleaned.attentions.toLong())}",
                    role = SearchResultTextRole.METADATA,
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
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    val cleaned = remember(item.topicId, item.title, item.cover) { item.cleanupFields() }
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
                ),
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
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            Column(modifier = Modifier.weight(1f)) {
                SearchResultText(
                    text = cleaned.title,
                    role = SearchResultTextRole.TITLE,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cleaned.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                    SearchResultText(
                        text = cleaned.description,
                        role = SearchResultTextRole.BODY,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                SearchResultText(
                    text = "浏览 ${FormatUtils.formatStat(cleaned.view.toLong())}",
                    role = SearchResultTextRole.METADATA,
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
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
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
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
                ),
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
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            Column(modifier = Modifier.weight(1f)) {
                SearchResultText(
                    text = cleaned.title,
                    role = SearchResultTextRole.TITLE,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                SearchResultText(
                    text = cleaned.uname,
                    role = SearchResultTextRole.METADATA,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                SearchResultText(
                    text = "图片 ${cleaned.count} · 浏览 ${FormatUtils.formatStat(cleaned.view.toLong())} · 喜欢 ${FormatUtils.formatStat(cleaned.like.toLong())}",
                    role = SearchResultTextRole.BADGE,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                SearchResultText(
                    text = "暂不支持打开",
                    role = SearchResultTextRole.BADGE,
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
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    SearchResultCardSurface(
        appearance = appearance,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 14.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
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
                        .clip(AppShapes.container(ContainerLevel.Field))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
            ) {
                SearchResultText(
                    text = item.title,
                    role = SearchResultTextRole.TITLE,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.description.isNotBlank()) {
                    SearchResultText(
                        text = item.description,
                        role = SearchResultTextRole.BODY,
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
                    SearchResultText(
                        text = metaLine,
                        role = SearchResultTextRole.METADATA,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SearchResultText(
                    text = "${FormatUtils.formatStat(item.view.toLong())}浏览 · ${FormatUtils.formatStat(item.reply.toLong())}评论 · ${FormatUtils.formatStat(item.like.toLong())}点赞",
                    role = SearchResultTextRole.METADATA,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
