package com.android.purebilibili.feature.space

import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.navigation.isTabSelectionInProgress
import com.android.purebilibili.feature.home.effectiveGridColumnCountForWindow
import com.android.purebilibili.feature.home.effectiveGridColumnCountCompact
import com.android.purebilibili.feature.home.gridColumnPinchEnabled
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.ReportProblem
import com.android.purebilibili.feature.following.FanManagementState
import com.android.purebilibili.feature.following.FanManagementViewModel
import com.android.purebilibili.feature.following.RemoveFanConfirmDialog

import com.android.purebilibili.core.ui.components.rememberFollowActionWithConfirmation
import com.android.purebilibili.core.ui.components.VideoStatusBadges
import com.android.purebilibili.core.ui.AppContentColors

import android.os.Build
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.core.ui.components.AnimatedVideoListItem
import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.animation.AnimatedVisibility
import com.android.purebilibili.core.ui.common.contentTabPagerGestures
import com.android.purebilibili.core.ui.common.rememberContentTabPager
import com.android.purebilibili.core.ui.common.stationaryPagerHeader
import androidx.compose.foundation.pager.PagerState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.android.purebilibili.feature.home.rememberFeedGridColumnPreference
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import com.android.purebilibili.feature.home.resolveHomeFeedPinchColumnBounds
import com.android.purebilibili.feature.home.resolveHomeFeedStoredColumnCount
import com.android.purebilibili.feature.home.isCompactHomeFeedScreen
import com.android.purebilibili.feature.home.GridPinchColumnHudPill
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.util.shouldShowScrollToTop
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import com.android.purebilibili.core.ui.common.AppSelectionContainer as SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.android.purebilibili.core.ui.components.liquidDockViewport
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import com.android.purebilibili.core.ui.components.AppLazyVerticalStaggeredGrid as LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import com.android.purebilibili.core.ui.rememberCompatibleStaggeredGridState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ViewAgenda
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.resolveUpNameColor
import androidx.compose.material3.ButtonDefaults
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconBackdrop
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.android.purebilibili.feature.profile.buildForProfileFusion
import coil3.size.Scale
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.OfficialVerifyBadgeSpec
import com.android.purebilibili.core.ui.OfficialVerifyBadgeTone
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveOfficialVerifyBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField
import com.android.purebilibili.core.ui.components.AppNativeTabRow
import com.android.purebilibili.core.ui.components.AppTabRowIndicatorPresentation
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.MiuixNonGlassTabItemWidthMode
import com.android.purebilibili.core.ui.components.KeepScrollableTabSelectionVisible
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardCoverOverlayTextShadow
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.videoCoverSharedElementKey
import com.android.purebilibili.core.ui.transition.videoSharedElementBoundsTransformSpec
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.feature.home.components.cards.videoCardShellReturnChromeAlpha
import com.android.purebilibili.feature.home.resolveHomeFeedCardLayout
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.SeasonArchiveItem
import com.android.purebilibili.data.model.response.SeriesArchiveItem
import com.android.purebilibili.data.model.response.DynamicDesc
import kotlin.math.roundToInt
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceArticleItem
import com.android.purebilibili.data.model.response.SpaceCheeseItem
import com.android.purebilibili.data.model.response.displayImageUrls
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberStaticSkeletonBlockColor
import com.android.purebilibili.data.model.response.SpaceAudioItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceTopArcData
import com.android.purebilibili.data.model.response.SpaceUserInfo
import com.android.purebilibili.data.model.response.SpaceVideoCategory
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.data.model.response.RelationStatData
import com.android.purebilibili.data.model.response.UpStatData
import com.android.purebilibili.data.model.response.VideoSortOrder
import com.android.purebilibili.feature.dynamic.DynamicDeleteAction
import com.android.purebilibili.feature.dynamic.DynamicViewModel
import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import com.android.purebilibili.feature.plugin.rememberSponsorFeedBadgeLabel
import com.android.purebilibili.feature.dynamic.components.DynamicCardActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardInteractionActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardNavigationActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardPresentation
import com.android.purebilibili.feature.dynamic.components.RichTextContent
import com.android.purebilibili.feature.dynamic.components.DynamicCommentOverlayHost
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.imagePreviewSourceBounds
import com.android.purebilibili.feature.dynamic.components.rememberImagePreviewSourceRect
import com.android.purebilibili.feature.dynamic.components.prepareImagePreviewSourceTransition
import com.android.purebilibili.feature.dynamic.components.resolveImagePreviewPlaceholderCacheKey
import com.android.purebilibili.feature.dynamic.components.RepostDialog
import com.android.purebilibili.feature.list.VideoProgressDisplayState
import com.android.purebilibili.core.player.PlaybackProgressManager
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.feature.video.ui.feedback.followActionAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

// UP 空间多张头图自动轮播间隔
private const val SPACE_BANNER_AUTO_ROTATE_INTERVAL_MS = 4_000L

// 课堂 Tab 的骨架行与真实行共用同一种 contentType：数据到达时是 item 原地替换，
// 列表不会被整棵重建。
private const val SPACE_CHEESE_ROW_CONTENT_TYPE = "space_cheese_row"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SpaceScreen(
    mid: Long,
    targetBvid: String? = null,
    onBack: () -> Unit,
    onVideoClick: (String, Long, Long) -> Unit,
    onAudioClick: (Long) -> Unit = {},
    onBangumiClick: (Long) -> Unit = {},
    onCheeseClick: (Long) -> Unit = onBangumiClick,
    onWebClick: (String, String) -> Unit = { _, _ -> },
    onWebVideosClick: (String, String, Long) -> Unit = { _, _, _ -> },
    onLiveClick: (Long, String, String) -> Unit = { _, _, _ -> },
    onUserClick: (Long) -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onDynamicDetailClick: (String) -> Unit = {},
    onArticleClick: (Long, String) -> Unit = { _, _ -> },
    onArticleCollectionClick: (Long, String) -> Unit = { _, _ -> },
    onMatchClick: ((Long) -> Unit)? = null,
    onViewAllClick: (String, Long, Long, String, String) -> Unit = { _, _, _, _, _ -> },
    onLikedVideosClick: ((Long, String) -> Unit)? = null,
    onMessageClick: (Long, String, String) -> Unit = { _, _, _ -> },
    onFollowingClick: (Long) -> Unit = {},
    onFansClick: (Long) -> Unit = {},
    onUpowerRankClick: ((Long, String, Long) -> Unit)? = null,
    onMemberGuardClick: ((Long, String, Long) -> Unit)? = null,
    onAccountPageClick: (SpaceAccountPage) -> Unit = {},
    viewModel: SpaceViewModel = viewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    contentTransitionReady: Boolean = true,
) {
    val context = LocalContext.current
    val fanIdentity by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val fanModel: FanManagementViewModel = viewModel(
        key = "fan_management_$mid",
        factory = viewModelFactory { initializer { FanManagementViewModel(mid) } },
    )
    val rawFanState by fanModel.state.collectAsStateWithLifecycle()
    val fanState = rawFanState.takeIf { it.identity == fanIdentity } ?: FanManagementState(identity = fanIdentity)
    var showRemoveFanConfirm by remember(mid, fanIdentity) { mutableStateOf(false) }
    var showReservations by remember(mid, fanIdentity) { mutableStateOf(false) }
    var refreshProfileOnResume by rememberSaveable(mid, fanIdentity) { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (refreshProfileOnResume) {
            refreshProfileOnResume = false
            viewModel.refreshProfileHeader()
        }
    }
    LaunchedEffect(fanModel) { fanModel.load() }
    LaunchedEffect(fanState.message) {
        fanState.message?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            fanModel.clearMessage()
        }
    }
    val queryAicu = com.android.purebilibili.feature.aicu.LocalAicuNavigation.current
    val copyToClipboard = rememberClipboardCopyHandler()
    val playbackProgressManager = remember(context) {
        PlaybackProgressManager.getInstance(context)
    }
    val videoProgressLookup: (String) -> Long = remember(playbackProgressManager) {
        { bvid -> playbackProgressManager.getCachedPosition(bvid) }
    }
    val dynamicInteractionViewModel: DynamicViewModel = viewModel()
    val loadedUiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Defer only a container's initial expansion. The host keeps ready=true on return,
    // so an already opened profile never falls back to a skeleton during a back gesture.
    val uiState = if (contentTransitionReady) loadedUiState else SpaceUiState.Loading
    val contentRevealAlpha = com.android.purebilibili.core.ui.transition.rememberSharedCardDetailRevealAlpha(
        entryKey = mid,
        contentTransitionReady = contentTransitionReady,
        contentLoaded = uiState !is SpaceUiState.Loading,
    )
    val likedDynamics by dynamicInteractionViewModel.likedDynamics.collectAsStateWithLifecycle()
    val dynamicLikeOverrides by dynamicInteractionViewModel.likeOverrides.collectAsStateWithLifecycle()
    val forwardCountDeltas = remember { mutableStateMapOf<String, Int>() }
    val followGroupDialogVisible by viewModel.followGroupDialogVisible.collectAsStateWithLifecycle()
    val followGroupTags by viewModel.followGroupTags.collectAsStateWithLifecycle()
    val followGroupSelectedTagIds by viewModel.followGroupSelectedTagIds.collectAsStateWithLifecycle()
    val isFollowGroupsLoading by viewModel.isFollowGroupsLoading.collectAsStateWithLifecycle()
    val isSavingFollowGroups by viewModel.isSavingFollowGroups.collectAsStateWithLifecycle()
    val blockedUpRepository = remember { com.android.purebilibili.data.repository.BlockedUpRepository(context) }
    val isLocallyBlocked by blockedUpRepository.isBlocked(mid).collectAsStateWithLifecycle(initialValue = false)
    val coroutineScope = rememberCoroutineScope()

    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showTopPhotoPreview by remember(mid) { mutableStateOf(false) }
    var topPhotoSourceRect by remember(mid) { mutableStateOf<Rect?>(null) }
    // 点击时 banner 实际显示的 URL（装扮头图可能与 topPhoto 不同）
    var topPhotoBannerUrl by remember(mid) { mutableStateOf<String?>(null) }
    var avatarSourceRect by remember(mid) { mutableStateOf<Rect?>(null) }
    var showAvatarPreview by remember(mid) { mutableStateOf(false) }
    var repostDynamicId by remember { mutableStateOf<String?>(null) }
    val spaceThemeConfig = LocalAppThemeConfig.current
    // 交互以用户开关为边界，不跟随模糊性能降级切换，避免滚动过程中改变吸顶规则。
    // 顶栏磨砂、渐进模糊与纯色渐变共用连续滚动布局；全部关闭时保留原有折叠路径。
    // 此处只决定页面滚动，不改变玻璃、MD3 或 Miuix 的材质参数。
    val pinContentTabs = shouldPinSpaceContentTabs(
        progressiveTopBlurEnabled = spaceThemeConfig.progressiveTopBlurEnabled,
        headerBlurEnabled = spaceThemeConfig.headerBlurEnabled,
        progressiveTopFadeEnabled = spaceThemeConfig.progressiveTopFadeEnabled,
    )
    val spaceDensity = LocalDensity.current
    val hazeState = if (
        spaceThemeConfig.headerBlurEnabled &&
            shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
            !isLowBlurBudgetForced()
    ) rememberRecoverableHazeState() else null
    val defaultPageGridStates = SpaceMainTab.entries.associateWith { rememberCompatibleStaggeredGridState() }
    val contributionGridStateFor = remember(mid) {
        val states = mutableMapOf<String, LazyStaggeredGridState>()
        val stateFor: (String) -> LazyStaggeredGridState = { id -> states.getOrPut(id) { com.android.purebilibili.core.ui.createCompatibleStaggeredGridState() } }
        stateFor
    }
    val contributionTabId = (uiState as? SpaceUiState.Success)?.selectedContributionTabId.orEmpty()
    val contributionGridState = remember(mid, contributionTabId) { contributionGridStateFor(contributionTabId) }
    val pageGridStates = if (pinContentTabs) {
        defaultPageGridStates + (SpaceMainTab.CONTRIBUTION to contributionGridState)
    } else defaultPageGridStates
    val activePrimaryTab = resolveSpacePrimaryTab(
        (uiState as? SpaceUiState.Success)?.tabShellState?.selectedTab ?: SpaceMainTab.HOME
    )
    val gridState = pageGridStates.getValue(activePrimaryTab)
    val isSpaceScrolling by remember(gridState) {
        derivedStateOf { gridState.isScrollInProgress }
    }
    val pinnedTopChromeScrim by remember(gridState, pinContentTabs, spaceDensity) {
        derivedStateOf {
            if (pinContentTabs) {
                val header = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                if (header == null) {
                    if (gridState.firstVisibleItemIndex > 0) 1f else 0f
                } else {
                    resolveSpaceHeaderClosingFraction(
                        remainingHeightPx = (header.offset.y + header.size.height).toFloat(),
                        toolbarHeightPx = with(spaceDensity) { 56.dp.toPx() },
                    )
                }
            } else resolveSpacePinnedTopChromeScrim(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
        }
    }
    val shouldShowBackToTop by remember(gridState) {
        derivedStateOf {
            shouldShowScrollToTop(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(mid, fanIdentity) {
        viewModel.loadSpaceInfo(mid)
    }

    val currentSuccessState = uiState as? SpaceUiState.Success
    val isBlocked = isLocallyBlocked || currentSuccessState?.userInfo?.relationStatus == 128
    var contributionVideoLayoutMode by rememberSaveable(mid) {
        mutableStateOf(defaultSpaceContributionVideoLayoutMode())
    }
    val nextContributionVideoLayoutMode = toggleSpaceContributionVideoLayoutMode(contributionVideoLayoutMode)
    val showContributionVideoMenuActions = currentSuccessState?.let { state ->
        state.tabShellState.selectedTab == SpaceMainTab.CONTRIBUTION &&
            state.selectedSubTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO)
    } == true
    val showContributionLayoutToggle = currentSuccessState?.let { state ->
        state.tabShellState.selectedTab == SpaceMainTab.CONTRIBUTION &&
            state.selectedSubTab in setOf(
                SpaceSubTab.VIDEO,
                SpaceSubTab.CHARGING_VIDEO,
                SpaceSubTab.SEASON_VIDEO,
                SpaceSubTab.SERIES,
            )
    } == true
    val playedVideoBvid = targetBvid?.trim().orEmpty()
    val playedVideoLocatePromptEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSpacePlayedVideoLocatePromptEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    // The prompt is deliberately scoped to this visit. Persisting the dismissal made a second
    // visit to the same UP silently lose its locate entry.
    var playedVideoLocatePromptHandled by remember(mid, playedVideoBvid) {
        mutableStateOf(false)
    }
    val shouldPromptToLocatePlayedVideo = shouldPromptToLocatePlayedVideo(
        targetBvid = playedVideoBvid,
        hasLoadedSpace = currentSuccessState != null,
        promptEnabled = playedVideoLocatePromptEnabled,
        promptHandled = playedVideoLocatePromptHandled
    )
    val locateMessage = currentSuccessState?.locateMessage
    LaunchedEffect(locateMessage) {
        locateMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeLocateMessage(message)
        }
    }
    val currentSearchScope = currentSuccessState?.let { success ->
        resolveSpaceSearchScope(
            selectedMainTab = success.tabShellState.selectedTab,
            selectedSubTab = success.selectedSubTab
        )
    } ?: SpaceSearchScope.NONE
    val canSearch = currentSearchScope != SpaceSearchScope.NONE
    val isSearchMode = currentSuccessState?.isSearchMode == true
    val hasContributionToolbarForSearch = currentSuccessState?.let { success ->
        currentSearchScope == SpaceSearchScope.VIDEO &&
            resolveDisplayedSpaceContributionTabs(
                tabs = success.contributionTabs,
                totalAudios = success.totalAudios
            ).isNotEmpty()
    } == true
    val screenTitle = currentSuccessState?.userInfo?.name
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.space_title)
    val backLabel = stringResource(R.string.common_back)
    val collapsedTitleAlpha = animateFloatAsState(
        targetValue = if (pinContentTabs && pinnedTopChromeScrim >= 1f) 1f else 0f,
        animationSpec = tween(500, easing = AppMotionEasing.Md3Standard),
        label = "space_collapsed_title",
    )
    val moreLabel = stringResource(R.string.common_more)
    val blockUserLabel = stringResource(R.string.space_block_user)
    val unblockUserLabel = stringResource(R.string.space_unblock_user)

    val spaceProgressiveBlur = shouldUseBiliPaiProgressiveTopBlur(
        enabled = spaceThemeConfig.progressiveTopBlurEnabled && !spaceThemeConfig.headerBlurEnabled,
        hasBackdrop = true,
    ) && !isLowBlurBudgetForced()
    val spaceFadeActive = spaceThemeConfig.progressiveTopFadeEnabled && !spaceThemeConfig.headerBlurEnabled
    val spaceChromeSource = if (spaceProgressiveBlur || spaceThemeConfig.liquidGlassEnabled) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else {
        null
    }
    val spaceChromeBackdrop = spaceChromeSource?.takeIf {
        uiState is SpaceUiState.Success && it.isReady
    }?.backdrop
    val spaceHeaderBlurActive = spaceThemeConfig.headerBlurEnabled &&
        hazeState?.let { recoverableBlurEnabled(it) } == true &&
        !spaceProgressiveBlur
    val spaceTopChromeSurfaceColor =
        if (spaceProgressiveBlur || spaceHeaderBlurActive || spaceFadeActive) Color.Transparent
        else com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.surface)
            .copy(alpha = pinnedTopChromeScrim)
    // 仅按钮采样合入顶栏实际遮色；列表与返回顶部按钮仍采样各自位置的内容。
    // 共享顶栏颜色来源，关闭玻璃时沿用原生 MD3 / Miuix 的背景与布局。
    val spaceTopBarIconBackdrop = com.android.purebilibili.core.ui.blur.rememberSurfaceOverlayBackdrop(spaceChromeBackdrop, spaceTopChromeSurfaceColor)
    AppScaffold(
        topBarIconBackdropSource = spaceChromeSource,
        topBar = {
            BiliPaiImmersiveTopBar(
                backdrop = spaceChromeBackdrop,
                enabled = spaceProgressiveBlur,
                headerBlurActive = spaceHeaderBlurActive,
                surfaceColor = com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.surface),
                fadeEnabled = spaceFadeActive,
                opaqueBackgroundFallback = false,
                modifier = Modifier.background(spaceTopChromeSurfaceColor),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (spaceProgressiveBlur) {
                            Modifier
                        } else {
                            hazeState?.let {
                                Modifier
                                    .unifiedBlur(
                                        hazeState = it,
                                        surfaceType = BlurSurfaceType.HEADER,
                                        isScrolling = isSpaceScrolling,
                                        enabled = pinnedTopChromeScrim > 0f
                                    )
                                    .background(
                                        com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.surface)
                                            .copy(alpha = AppSurfaceTokens.FrostedScrimAlpha * pinnedTopChromeScrim)
                                    )
                            } ?: Modifier
                        }
                    )
            ) {
                AppTopBar(
                    title = if (pinContentTabs || pinnedTopChromeScrim > 0.4f) screenTitle else "",
                    smallTitleColor = if (pinContentTabs) {
                        AppContentColors.primaryText().copy(alpha = collapsedTitleAlpha.value)
                    } else Color.Unspecified,
                    navigationIcon = {
                        CompositionLocalProvider(LocalAppTopBarIconBackdrop provides spaceTopBarIconBackdrop) {
                            AppIconButton(onClick = onBack) {
                                AppIcon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = backLabel
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    actions = {
                        // Only glass chrome reserves explicit action slots; native MD3/Miuix
                        // buttons retain their original sizing and spacing when glass is disabled.
                        Row(
                            modifier = if (spaceThemeConfig.liquidGlassEnabled) {
                                Modifier.padding(end = 8.dp)
                            } else Modifier,
                            horizontalArrangement = Arrangement.spacedBy(
                                if (spaceThemeConfig.liquidGlassEnabled) 8.dp else 0.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalAppTopBarIconBackdrop provides spaceTopBarIconBackdrop) {
                                if (canSearch) {
                                    AppIconButton(
                                        onClick = { viewModel.setSearchMode(!isSearchMode) },
                                        modifier = if (spaceThemeConfig.liquidGlassEnabled) {
                                            Modifier.size(com.android.purebilibili.core.ui.AppChromeSizeTokens.TopBarIconVisualSize)
                                        } else Modifier,
                                    ) {
                                        AppIcon(
                                            imageVector = if (isSearchMode) Icons.Outlined.Close else Icons.Outlined.Search,
                                            contentDescription = if (isSearchMode) "关闭搜索" else "搜索"
                                        )
                                    }
                                }
                                AppWindowActionMenu(
                                    modifier = if (spaceThemeConfig.liquidGlassEnabled) {
                                        Modifier.size(com.android.purebilibili.core.ui.AppChromeSizeTokens.TopBarIconVisualSize)
                                    } else Modifier,
                                    groups = buildList {
                                        if (showContributionVideoMenuActions) {
                                            add(
                                                listOf(
                                                    contributionLayoutToggleAction(
                                                        layoutMode = contributionVideoLayoutMode,
                                                        onToggle = {
                                                            contributionVideoLayoutMode =
                                                                nextContributionVideoLayoutMode
                                                        },
                                                    ),
                                                    AppWindowAction(
                                                        label = "排序：${resolveSpaceVideoSortCompactLabel(currentSuccessState?.sortOrder ?: VideoSortOrder.PUBDATE)}",
                                                        icon = Icons.AutoMirrored.Outlined.Sort,
                                                        children = VideoSortOrder.entries.map { order ->
                                                            AppWindowAction(
                                                                label = order.displayName,
                                                                selected = currentSuccessState?.sortOrder == order,
                                                                onClick = { viewModel.selectSortOrder(order) },
                                                            )
                                                        },
                                                    ),
                                                )
                                            )
                                        } else if (showContributionLayoutToggle) {
                                            add(
                                                listOf(
                                                    contributionLayoutToggleAction(
                                                        layoutMode = contributionVideoLayoutMode,
                                                        onToggle = {
                                                            contributionVideoLayoutMode =
                                                                nextContributionVideoLayoutMode
                                                        },
                                                    ),
                                                )
                                            )
                                        }
                                        val profile = currentSuccessState
                                        if (!profile?.reservations.isNullOrEmpty()) {
                                            add(listOf(AppWindowAction(
                                                label = "预约 (${profile?.reservations?.size})", icon = Icons.Outlined.NotificationsNone,
                                                onClick = { showReservations = true },
                                            )))
                                        }
                                        if (profile?.hasMedalWall == true) {
                                            add(listOf(AppWindowAction(
                                                label = "粉丝勋章墙", icon = Icons.Outlined.WorkspacePremium,
                                                enabled = !profile.isProfileActionRunning, onClick = viewModel::loadMedalWall,
                                            )))
                                        }
                                        if (profile?.userInfo?.isFollowed == true && mid != fanIdentity.mid && fanIdentity.mid != null) {
                                            add(listOf(
                                                AppWindowAction(label = if (profile.userInfo.relationStatus == -10) "移除特别关注" else "加入特别关注",
                                                    icon = Icons.Outlined.StarBorder, enabled = !profile.isProfileActionRunning, onClick = viewModel::toggleSpecialFollow),
                                                AppWindowAction(label = "设置关注分组", icon = Icons.Outlined.FolderOpen,
                                                    onClick = { viewModel.showFollowGroupDialogForUser(mid) }),
                                                AppWindowAction(
                                                label = "关注时间", icon = Icons.Outlined.History,
                                                enabled = !profile.isProfileActionRunning, onClick = viewModel::showFollowTime,
                                            )))
                                        }
                                        add(listOf(
                                            AppWindowAction(label = "网页投稿", icon = Icons.Outlined.OpenInBrowser,
                                                enabled = profile != null,
                                                onClick = {
                                                    val selected = profile?.let {
                                                        resolveSelectedContributionTab(it.contributionTabs, it.selectedContributionTabId, it.selectedSubTab)
                                                    }
                                                    val (type, id) = when {
                                                        selected == null -> "" to 0L
                                                        selected.seasonId > 0L -> "season" to selected.seasonId
                                                        selected.seriesId > 0L -> "series" to selected.seriesId
                                                        else -> "" to 0L
                                                    }
                                                    onWebVideosClick(profile?.userInfo?.name.orEmpty(), type, id)
                                                }),
                                            AppWindowAction(label = "添加至桌面", icon = Icons.Outlined.AddToHomeScreen,
                                                enabled = context.getSystemService(android.content.pm.ShortcutManager::class.java).isRequestPinShortcutSupported,
                                                onClick = {
                                                    if (!pinSpaceShortcut(context, mid, profile?.userInfo?.name?.ifBlank { "UID $mid" } ?: "UID $mid")) {
                                                        android.widget.Toast.makeText(context, "未能添加桌面快捷方式", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }),
                                        ))
                                        if (fanIdentity.mid != null && mid == fanIdentity.mid) {
                                            add(buildList {
                                                if ((profile?.userInfo?.vip?.status ?: 0) > 0) add(AppWindowAction(
                                                    label = "领取大会员经验", icon = Icons.Outlined.Stars,
                                                    enabled = profile?.isProfileActionRunning != true, onClick = viewModel::claimVipExperience))
                                                add(AppWindowAction(label = "登录设备与记录", icon = Icons.Outlined.Devices,
                                                    onClick = { onAccountPageClick(SpaceAccountPage.LOGIN_ACTIVITY) }))
                                                add(AppWindowAction(label = "硬币记录", icon = Icons.Outlined.Toll,
                                                    onClick = { onAccountPageClick(SpaceAccountPage.COINS) }))
                                                add(AppWindowAction(label = "经验记录", icon = Icons.Outlined.TrendingUp,
                                                    onClick = { onAccountPageClick(SpaceAccountPage.EXPERIENCE) }))
                                                add(AppWindowAction(label = "空间设置", icon = Icons.Outlined.ManageAccounts,
                                                    onClick = { onAccountPageClick(SpaceAccountPage.PRIVACY) }))
                                            })
                                        }
                                        if (queryAicu != null && mid > 0) {
                                            add(listOf(AppWindowAction(
                                                label = "评论与弹幕查询",
                                                icon = Icons.Outlined.Search,
                                                onClick = { queryAicu(mid) },
                                            )))
                                        }
                                        add(
                                            listOf(
                                                AppWindowAction(
                                                    label = "复制空间链接",
                                                    icon = Icons.Outlined.ContentCopy,
                                                    onClick = {
                                                        copyToClipboard(
                                                            "https://space.bilibili.com/$mid",
                                                            "空间链接",
                                                        )
                                                    },
                                                ),
                                                AppWindowAction(
                                                    label = "复制 UID",
                                                    icon = Icons.Outlined.ContentCopy,
                                                    onClick = { copyToClipboard(mid.toString(), "UID") },
                                                ),
                                                AppWindowAction(
                                                    label = "分享",
                                                    icon = Icons.Outlined.Share,
                                                    onClick = {
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, "https://space.bilibili.com/$mid")
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "分享空间"))
                                                    },
                                                ),
                                            )
                                        )
                                        if (fanState.isFan == true && mid != fanIdentity.mid) {
                                            add(listOf(AppWindowAction(
                                                label = if (fanState.removing) "移除中…" else "移除粉丝",
                                                icon = Icons.Outlined.PersonRemove,
                                                enabled = !fanState.removing,
                                                onClick = { showRemoveFanConfirm = true },
                                            )))
                                        }
                                        if (mid != fanIdentity.mid) add(
                                            listOf(
                                                AppWindowAction(
                                                    label = "举报",
                                                    icon = Icons.Outlined.ReportProblem,
                                                    iconTint = AppContentColors.error(),
                                                    onClick = {
                                                        onWebClick(
                                                            "https://www.bilibili.com/appeal/?prefid=0&mid=$mid",
                                                            "举报",
                                                        )
                                                    },
                                                ),
                                                AppWindowAction(
                                                    label = if (isBlocked) unblockUserLabel else blockUserLabel,
                                                    icon = if (isBlocked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                                    iconTint = if (isBlocked) {
                                                        AppContentColors.accent()
                                                    } else {
                                                        AppContentColors.error()
                                                    },
                                                    onClick = { showBlockConfirmDialog = true },
                                                ),
                                            )
                                        )
                                    },
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = moreLabel,
                                    )
                                }
                            }
                        }
                    }
                )

            }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { scaffoldPadding ->
        val density = LocalDensity.current
        val searchBarRevealScrollOffsetPx = with(density) {
            resolveSpaceSearchBarRevealScrollOffsetPx(
                topBarHeightPx = scaffoldPadding.calculateTopPadding().roundToPx(),
                extraVisibleMarginPx = 8.dp.roundToPx()
            )
        }

        LaunchedEffect(
            isSearchMode,
            currentSearchScope,
            hasContributionToolbarForSearch,
            searchBarRevealScrollOffsetPx,
            pinContentTabs,
            gridState,
        ) {
            if (!isSearchMode) return@LaunchedEffect
            resolveSpaceSearchBarGridItemIndex(
                scope = currentSearchScope,
                hasContributionToolbar = hasContributionToolbarForSearch
            )?.let { searchBarIndex ->
                gridState.animateScrollToItem(
                    index = if (pinContentTabs) 2 else searchBarIndex,
                    scrollOffset = if (pinContentTabs) {
                        -gridState.measureSpacePinnedTabsHeightPx() - with(density) { 8.dp.roundToPx() }
                    } else searchBarRevealScrollOffsetPx
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = if (uiState is SpaceUiState.Loading) 1f else contentRevealAlpha.value
        }) {
                // Loading 不再单独渲染一棵骨架网格：它和 Success 共用同一个 SpaceContent
                // 调用点（同一棵 LazyVerticalStaggeredGrid）。Loading 时喂一个占位 Success 并把
                // isInitialLoading 传下去，由内容网格在原地铺骨架 item；数据到达后是
                // item 原地替换，网格与滚动状态都不会重建，首屏不跳。
                val isLoadingSpace = uiState is SpaceUiState.Loading
                val spaceState: SpaceUiState = (uiState as? SpaceUiState.Success)
                    ?: remember { SpaceUiState.Success(userInfo = SpaceUserInfo()) }
                when (val state = spaceState) {
                    is SpaceUiState.Loading -> {
                        // Loading 已被上面的占位 Success 取代，此分支仅为穷尽 when 保留。
                    }

                    is SpaceUiState.Error -> {
                        SpaceErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadSpaceInfo(mid) }
                        )
                    }

                    is SpaceUiState.Success -> {
                        val filteredDynamics = remember(
                            state.dynamics,
                            state.searchQuery,
                            currentSearchScope
                        ) {
                            if (currentSearchScope == SpaceSearchScope.DYNAMIC) {
                                filterSpaceDynamicItemsByQuery(
                                    items = state.dynamics,
                                    query = state.searchQuery
                                )
                            } else {
                                state.dynamics
                            }
                        }
                        val dynamicCardItems = remember(filteredDynamics) {
                            resolveSpaceDynamicCardItems(filteredDynamics)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(spaceChromeSource?.modifier ?: Modifier)
                                .then(if (hazeState != null) Modifier.hazeSourceCompat(state = hazeState) else Modifier)
                                .globalWallpaperAwareBackground(MaterialTheme.colorScheme.surface),
                        ) {
                        SpaceContent(
                            state = state,
                            gridState = gridState,
                            pageGridStates = pageGridStates,
                            onVideoClick = onVideoClick,
                            videoProgressLookup = videoProgressLookup,
                            onAudioClick = onAudioClick,
                            onBangumiClick = onBangumiClick,
                            onCheeseClick = onCheeseClick,
                            onWebClick = onWebClick,
                            onLiveClick = onLiveClick,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onTopicKeywordClick = onTopicKeywordClick,
                            onDynamicDetailClick = onDynamicDetailClick,
                            onArticleClick = onArticleClick,
                            onArticleCollectionClick = onArticleCollectionClick,
                            onMatchClick = onMatchClick,
                            onViewAllClick = onViewAllClick,
                            onLikedVideosClick = onLikedVideosClick,
                            onMainTabSelected = viewModel::selectMainTab,
                            onContributionTabSelected = viewModel::selectContributionTab,
                            onCategorySelected = viewModel::selectCategory,
                            onSelectSortOrder = viewModel::selectSortOrder,
                            contributionVideoLayoutMode = contributionVideoLayoutMode,
                            onLoadMoreVideos = viewModel::loadMoreVideos,
                            onLoadHome = viewModel::loadSpaceHome,
                            onLoadDynamic = { viewModel.loadSpaceDynamic(refresh = true) },
                            onLoadMoreDynamic = { viewModel.loadSpaceDynamic(refresh = false) },
                            onLoadBangumi = { viewModel.loadSpaceBangumi(refresh = true) },
                            onLoadMoreBangumi = { viewModel.loadSpaceBangumi(refresh = false) },
                            onLoadAudios = { viewModel.loadSpaceAudios(refresh = true) },
                            onLoadMoreAudios = { viewModel.loadSpaceAudios(refresh = false) },
                            onLoadArticles = { viewModel.loadSpaceArticles(refresh = true) },
                            onLoadMoreArticles = { viewModel.loadSpaceArticles(refresh = false) },
                            onLoadCheese = { viewModel.loadSpaceCheese(refresh = true) },
                            onLoadMoreCheese = { viewModel.loadSpaceCheese(refresh = false) },
                            onLoadComics = viewModel::loadSpaceComics,
                            onLoadMoreOpus = { viewModel.loadSpaceOpus() },
                            onSelectOpusFilter = viewModel::selectOpusFilter,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSearchEntryClick = { viewModel.setSearchMode(true) },
                            onLocateTargetConsumed = viewModel::consumePendingLocateBvid,
                            onLocateTargetMissing = viewModel::reportPendingLocateBvidMissing,
                            onLocateTargetLoadFailed = viewModel::reportPendingLocateBvidLoadFailed,
                            onFollowClick = {
                                when {
                                    mid == fanIdentity.mid -> {
                                        refreshProfileOnResume = true
                                        onAccountPageClick(SpaceAccountPage.EDIT_PROFILE)
                                    }
                                    state.userInfo.relationStatus == 128 -> showBlockConfirmDialog = true
                                    else -> viewModel.toggleFollow()
                                }
                            },
                            onMessageClick = {
                                val user = state.userInfo
                                onMessageClick(user.mid, user.name, user.face)
                            },
                            onFollowingClick = { onFollowingClick(state.userInfo.mid) },
                            onFansClick = { onFansClick(state.userInfo.mid) },
                            onFollowedUsersClick = viewModel::openFollowedUsers,
                            onUpowerRankClick = onUpowerRankClick
                                ?: { m, _, _ -> onWebClick("https://space.bilibili.com/$m/upower/rank", "充电排行") },
                            onMemberGuardClick = onMemberGuardClick
                                ?: { m, _, _ -> onWebClick("https://space.bilibili.com/$m", "大航海") },
                            onTopPhotoClick = { rect, bannerUrl ->
                                prepareImagePreviewSourceTransition(rect)
                                topPhotoSourceRect = rect
                                topPhotoBannerUrl = bannerUrl
                                showTopPhotoPreview = true
                            },
                            onAvatarClick = { rect ->
                                prepareImagePreviewSourceTransition(rect)
                                avatarSourceRect = rect
                                showAvatarPreview = true
                            },
                            dynamicCardItems = dynamicCardItems,
                            likedDynamics = likedDynamics,
                            likeOverrides = dynamicLikeOverrides,
                            forwardCountDeltas = forwardCountDeltas,
                            onSpaceDynamicCommentClick = dynamicInteractionViewModel::openCommentSheet,
                            onSpaceDynamicRepostClick = { repostDynamicId = it },
                            onSpaceDynamicLikeClick = { dynamicId, isLiked ->
                                dynamicInteractionViewModel.likeDynamic(dynamicId, isLiked) { _, message ->
                                    android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onSpaceDynamicReserveClick = dynamicInteractionViewModel::toggleDynamicReserve,
                            onSpaceDynamicDeleteClick = { action ->
                                dynamicInteractionViewModel.deleteDynamic(action) { success, message ->
                                    android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    if (success) viewModel.removeSpaceDynamic(action.dynamicId)
                                }
                            },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            chromeTopInset = scaffoldPadding.calculateTopPadding(),
                            pinContentTabs = pinContentTabs,
                            contributionGridStateFor = contributionGridStateFor,
                            isInitialLoading = isLoadingSpace,
                        )
                        }

                        DynamicCommentOverlayHost(
                            viewModel = dynamicInteractionViewModel,
                            primaryItems = dynamicCardItems,
                            toastContext = context,
                            onUserClick = onUserClick,
                        )
                    }
                }

            SpacePlayedVideoLocatePrompt(
                visible = shouldPromptToLocatePlayedVideo,
                onDismiss = { playedVideoLocatePromptHandled = true },
                onConfirm = {
                    playedVideoLocatePromptHandled = true
                    viewModel.locatePlayedVideoContribution(playedVideoBvid)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )

            val animatedBackToTopBottomPadding by animateDpAsState(
                targetValue = if (shouldPromptToLocatePlayedVideo) 76.dp else 24.dp,
                label = "space_back_to_top_bottom_padding",
            )
            // 听视频小横条悬浮时统一上浮避让（与首页/稍后再看一致）
            val spaceBackToTopAvoidance = if (com.android.purebilibili.core.ui.rememberNowPlayingBarOverlayVisible()) {
                com.android.purebilibili.core.ui.NowPlayingBarOverlayAvoidancePadding
            } else {
                0.dp
            }

            AppLiquidGlassBackToTopButton(
                visible = rememberBackToTopButtonEnabled() &&
                    uiState is SpaceUiState.Success &&
                    shouldShowBackToTop,
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToTop()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = animatedBackToTopBottomPadding + spaceBackToTopAvoidance,
                    ),
                backdrop = spaceChromeBackdrop,
            )
        }
    }

    // 与 SpaceHeader 同规则解析夜间封面，保证回位落在用户看到的同一张图上
    val spaceHeaderIsDarkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val previewUrl = normalizeSpaceTopPhotoUrl(
        if (spaceHeaderIsDarkTheme) {
            currentSuccessState?.userInfo?.nightTopPhoto.takeUnless { it.isNullOrBlank() }
                ?: currentSuccessState?.userInfo?.topPhoto.orEmpty()
        } else {
            currentSuccessState?.userInfo?.topPhoto.orEmpty()
        }
    )
    val avatarPreviewUrl = currentSuccessState?.userInfo?.face.orEmpty()
    val density = LocalDensity.current
    val avatarCornerDp = avatarSourceRect?.let { rect ->
        with(density) { (minOf(rect.width, rect.height) / 2f).toDp().value }
    } ?: 40f
    // 装扮头图（可能多张）优先：预览列表与点击时实际显示的页对齐，支持滑动切换
    val topPhotoPreviewImages = remember(topPhotoBannerUrl, currentSuccessState?.userInfo?.topImages, previewUrl) {
        val topImages = currentSuccessState?.userInfo?.topImages.orEmpty()
        if (topImages.isNotEmpty()) {
            topImages.map { normalizeSpaceTopPhotoUrl(it.header) }.filter { it.isNotBlank() }
        } else {
            listOf(previewUrl)
        }
    }
    val topPhotoPreviewIndex = topPhotoPreviewImages.indexOf(topPhotoBannerUrl).takeIf { it >= 0 } ?: 0
    val topPhotoPreviewEnabled = topPhotoPreviewImages.any { shouldEnableSpaceTopPhotoPreview(it) }
    if (showTopPhotoPreview && topPhotoPreviewEnabled) {
        ImagePreviewDialog(
            images = topPhotoPreviewImages,
            initialIndex = topPhotoPreviewIndex,
            sourceRect = topPhotoSourceRect,
            // hero 封面全出血无圆角
            sourceCornerRadiusDp = 0f,
            onDismiss = { showTopPhotoPreview = false }
        )
    }
    if (showAvatarPreview && avatarPreviewUrl.isNotBlank()) {
        ImagePreviewDialog(
            images = listOf(avatarPreviewUrl),
            initialIndex = 0,
            sourceRect = avatarSourceRect,
            // 头像源是圆形，回位圆角取短边一半
            sourceCornerRadiusDp = avatarCornerDp,
            onDismiss = { showAvatarPreview = false }
        )
    }

    repostDynamicId?.let { dynamicId ->
        RepostDialog(
            contentKey = dynamicId,
            onDismiss = { repostDynamicId = null },
            onRepost = { content, alsoComment, richContents, topic, onComplete ->
                dynamicInteractionViewModel.repostDynamic(dynamicId, content, alsoComment, richContents, topic) { success, message ->
                    android.widget.Toast.makeText(
                        context,
                        message,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    if (success) {
                        forwardCountDeltas[dynamicId] = (forwardCountDeltas[dynamicId] ?: 0) + 1
                        repostDynamicId = null
                    }
                    onComplete(success)
                }
            }
        )
    }

    if (showRemoveFanConfirm && fanState.isFan == true) {
        RemoveFanConfirmDialog(
            name = currentSuccessState?.userInfo?.name.orEmpty(),
            onDismiss = { showRemoveFanConfirm = false },
            onConfirm = { showRemoveFanConfirm = false; fanModel.remove() },
        )
    }

    currentSuccessState?.let { state ->
        SpaceProfileDialogs(
            state = state, showReservations = showReservations,
            onDismissReservations = { showReservations = false }, onToggleReservation = viewModel::toggleReservation,
            onDismissNotice = viewModel::dismissProfileNotice, onDismissMedals = viewModel::dismissMedalWall,
            onDismissFollowedUsers = viewModel::dismissFollowedUsers, onLoadMoreFollowedUsers = viewModel::loadMoreFollowedUsers,
            onDynamicClick = onDynamicDetailClick, onUserClick = onUserClick, onLiveClick = onLiveClick, onWebClick = onWebClick,
        )
    }

    if (showBlockConfirmDialog) {
        val userName = currentSuccessState?.userInfo?.name ?: "该用户"
        val userFace = currentSuccessState?.userInfo?.face.orEmpty()
        AppAlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { AppText(if (isBlocked) "解除屏蔽" else "屏蔽 UP 主") },
            text = {
                AppText(
                    if (isBlocked) {
                        "确定要解除对 $userName 的屏蔽吗？"
                    } else {
                        "屏蔽后，将不再推荐 $userName 的视频。\n确定要屏蔽吗？"
                    }
                )
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        coroutineScope.launch {
                            if (isBlocked) {
                                val result = blockedUpRepository.unblockUpWithBilibiliSync(mid)
                                android.widget.Toast.makeText(
                                    context,
                                    result.message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val result = blockedUpRepository.blockUpWithBilibiliSync(mid, userName, userFace)
                                android.widget.Toast.makeText(
                                    context,
                                    result.message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            viewModel.refreshRelation()
                            showBlockConfirmDialog = false
                        }
                    }
                ) {
                    AppText(if (isBlocked) "解除屏蔽" else "屏蔽")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showBlockConfirmDialog = false }) {
                    AppText("取消")
                }
            }
        )
    }

    if (followGroupDialogVisible) {
        AppAlertDialog(
            onDismissRequest = {
                if (!isSavingFollowGroups) {
                    viewModel.dismissFollowGroupDialog()
                }
            },
            title = { AppText("设置关注分组") },
            text = {
                if (isFollowGroupsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AdaptiveLoadingIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (followGroupTags.isEmpty()) {
                            AppText(
                                text = "暂无可用分组（不勾选即为默认分组）",
                                color = AppContentColors.secondaryText(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            followGroupTags.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .appActionClickable { viewModel.toggleFollowGroupSelection(tag.tagid) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppCheckbox(
                                        checked = followGroupSelectedTagIds.contains(tag.tagid),
                                        onCheckedChange = { viewModel.toggleFollowGroupSelection(tag.tagid) }
                                    )
                                    AppText(
                                        text = "${tag.name} (${tag.count})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppContentColors.primaryText()
                                    )
                                }
                            }
                        }
                        AppText(
                            text = "可多选，确定后覆盖原分组设置。",
                            color = AppContentColors.secondaryText(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                AppButton(
                    onClick = { viewModel.saveFollowGroupSelection() },
                    enabled = !isFollowGroupsLoading && !isSavingFollowGroups
                ) {
                    if (isSavingFollowGroups) {
                        AppCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppContentColors.onAccent()
                        )
                    } else {
                        AppText("确定")
                    }
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { viewModel.dismissFollowGroupDialog() },
                    enabled = !isSavingFollowGroups
                ) {
                    AppText("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpacePlayedVideoLocatePrompt(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(160)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(160)
        ),
        exit = fadeOut(animationSpec = tween(120)) + scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(120)
        )
    ) {
        AppSurface(
            shape = AppShapes.container(ContainerLevel.Card),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 248.dp)
                    .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 6.dp)
            ) {
                AppText(
                    text = "刚刚看过",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                AppText(
                    text = "是否定位到视频投稿",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppTextButton(onClick = onDismiss) {
                        AppText("暂不")
                    }
                    AppTextButton(onClick = onConfirm) {
                        AppText("定位")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SpaceContent(
    state: SpaceUiState.Success,
    gridState: LazyStaggeredGridState,
    pageGridStates: Map<SpaceMainTab, LazyStaggeredGridState>,
    onVideoClick: (String, Long, Long) -> Unit,
    videoProgressLookup: (String) -> Long,
    onAudioClick: (Long) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onCheeseClick: (Long) -> Unit = onBangumiClick,
    onWebClick: (String, String) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit,
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onDynamicDetailClick: (String) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onArticleCollectionClick: (Long, String) -> Unit,
    onMatchClick: ((Long) -> Unit)? = null,
    onViewAllClick: (String, Long, Long, String, String) -> Unit,
    onLikedVideosClick: ((Long, String) -> Unit)? = null,
    onMainTabSelected: (SpaceMainTab) -> Unit,
    onContributionTabSelected: (String) -> Unit,
    onCategorySelected: (Int) -> Unit,
    onSelectSortOrder: (VideoSortOrder) -> Unit = {},
    contributionVideoLayoutMode: SpaceContributionVideoLayoutMode,
    onLoadMoreVideos: () -> Unit,
    onLoadHome: () -> Unit,
    onLoadDynamic: () -> Unit,
    onLoadMoreDynamic: () -> Unit,
    onLoadBangumi: () -> Unit,
    onLoadMoreBangumi: () -> Unit,
    onLoadAudios: () -> Unit,
    onLoadMoreAudios: () -> Unit,
    onLoadArticles: () -> Unit,
    onLoadMoreArticles: () -> Unit,
    onLoadCheese: () -> Unit = {},
    onLoadMoreCheese: () -> Unit = {},
    onLoadComics: () -> Unit = {},
    onLoadMoreOpus: () -> Unit = {},
    onSelectOpusFilter: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onSearchEntryClick: () -> Unit,
    onLocateTargetConsumed: (String) -> Unit,
    onLocateTargetMissing: (String) -> Unit,
    onLocateTargetLoadFailed: (String) -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onFollowedUsersClick: () -> Unit,
    onUpowerRankClick: (Long, String, Long) -> Unit = { _, _, _ -> },
    onMemberGuardClick: (Long, String, Long) -> Unit = { _, _, _ -> },
    onTopPhotoClick: (Rect?, String?) -> Unit,
    onAvatarClick: (Rect?) -> Unit,
    dynamicCardItems: List<com.android.purebilibili.data.model.response.DynamicItem>,
    likedDynamics: Set<String>,
    likeOverrides: Map<String, Boolean>,
    forwardCountDeltas: Map<String, Int>,
    onSpaceDynamicCommentClick: (com.android.purebilibili.data.model.response.DynamicItem) -> Unit,
    onSpaceDynamicRepostClick: (String) -> Unit,
    onSpaceDynamicLikeClick: (String, Boolean) -> Unit,
    onSpaceDynamicReserveClick: (
        com.android.purebilibili.feature.dynamic.components.DynamicReserveAction,
        (Result<com.android.purebilibili.feature.dynamic.components.DynamicReserveResult>) -> Unit,
    ) -> Unit,
    onSpaceDynamicDeleteClick: (DynamicDeleteAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    chromeTopInset: Dp = 0.dp,
    pinContentTabs: Boolean = false,
    contributionGridStateFor: (String) -> LazyStaggeredGridState,
    /**
     * 首屏加载中：在同一个内容网格里铺骨架 item，而不是另起一棵独立的骨架网格。
     * 骨架与真实卡片共用同一个 LazyVerticalStaggeredGrid / gridState，数据到达时是 item
     * 原地替换，网格不重建、滚动锚点不丢。
     */
    isInitialLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val windowWidthDp = windowSizeClass.widthDp.value.roundToInt().coerceAtLeast(0)
    val adaptiveLayoutSpec = remember(windowWidthDp, windowSizeClass.widthSizeClass) {
        resolveSpaceAdaptiveLayoutSpec(
            widthDp = windowWidthDp,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
    }
    val boundedListModifier = Modifier.responsiveContentWidth(
        maxWidth = adaptiveLayoutSpec.listContentMaxWidthDp.dp,
    )
    // 投稿网格跟随首页信息流设置（固定列数 / 卡宽预设 / 卡片风格），保证排版与首页 feed 一致。
    val homeSettings by com.android.purebilibili.core.store.SettingsManager
        .getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings())
    val sharedTabsCoroutineScope = rememberCoroutineScope()
    // 外边距由卡片风格决定，不依赖各分页的网格列数；共享主栏与分页使用同一来源。
    val outerPaddingDp = maxOf(16, resolveHomeFeedCardLayout(
        style = homeSettings.homeFeedCardStyle,
        widthSizeClass = windowSizeClass.widthSizeClass,
    ).outerPaddingDp).dp
    val selectedMainTab = state.tabShellState.selectedTab
    // 各标签页各自持有头部，但轮播页码属于同一个用户空间。
    var sharedBannerPage by remember(state.userInfo.mid) { mutableStateOf(0) }
    val contentDensity = LocalDensity.current
    val shopContext = LocalContext.current
    val showMemberShop by remember(shopContext) {
        com.android.purebilibili.core.store.ContentDisplaySettings.observe(
            shopContext, com.android.purebilibili.core.store.ContentDisplaySettings.SHOW_MEMBER_SHOP
        )
    }.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(showMemberShop, selectedMainTab) {
        if (!showMemberShop && selectedMainTab == SpaceMainTab.SHOP) onMainTabSelected(SpaceMainTab.HOME)
    }
    var shopReload by remember(state.userInfo.mid) { mutableIntStateOf(0) }
    val shopResult by produceState<Result<com.android.purebilibili.data.repository.SpaceShopPage>?>(
        null, state.userInfo.mid, selectedMainTab == SpaceMainTab.SHOP && showMemberShop, shopReload,
    ) {
        if (selectedMainTab == SpaceMainTab.SHOP && showMemberShop) {
            value = null
            value = com.android.purebilibili.data.repository.SpaceShopRepository.load(state.userInfo.mid)
        }
    }
    val displayedMainTabs = resolveSpaceDisplayedMainTabs(
        tabs = state.mainTabs.filter { showMemberShop || it.tab != SpaceMainTab.SHOP },
        selectedTab = selectedMainTab,
        hasCheese = state.hasCheeseTab || state.cheeseItems.isNotEmpty(),
    )
    val selectedPage = displayedMainTabs.indexOfFirst {
        it.tab == resolveSpacePrimaryTab(selectedMainTab)
    }.coerceAtLeast(0)
    val pagerState = rememberContentTabPager(
        pageCount = displayedMainTabs.size,
        selectedPage = selectedPage,
        onPageSelected = { page ->
            displayedMainTabs.getOrNull(page)?.tab?.let { tab ->
                if (tab != resolveSpacePrimaryTab(selectedMainTab)) onMainTabSelected(tab)
            }
        },
        prepareNeighbors = { sourcePage ->
            val source = pageGridStates.getValue(displayedMainTabs[sourcePage].tab)
            // 资料头或标签仍在视口时，覆盖目标页的旧正文位置并保留标签局部偏移。
            // 两页均进入正文后才保留各自的内容历史，避免切到投稿时主标签突然消失。
            displayedMainTabs.forEachIndexed { index, tab ->
                if (index != sourcePage) {
                    alignSpacePageHeader(source, pageGridStates.getValue(tab.tab))
                }
            }
        },
    )
    val selectedContributionTab = remember(
        state.contributionTabs,
        state.selectedContributionTabId,
        state.selectedSubTab
    ) {
        resolveSelectedContributionTab(
            tabs = state.contributionTabs,
            selectedTabId = state.selectedContributionTabId,
            selectedSubTab = state.selectedSubTab
        )
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(0.dp)
    val currentSearchScope = remember(selectedMainTab, state.selectedSubTab) {
        resolveSpaceSearchScope(
            selectedMainTab = selectedMainTab,
            selectedSubTab = state.selectedSubTab
        )
    }
    val searchFocusRequester = remember { FocusRequester() }
    val sharedTransitionEnabled = LocalSharedTransitionEnabled.current
    val lazyGridSharedTransitionEnabled = remember(
        sharedTransitionEnabled,
        sharedTransitionScope,
        animatedVisibilityScope
    ) {
        shouldEnableSpaceLazyGridSharedTransition(
            transitionEnabled = sharedTransitionEnabled,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null
        )
    }
    val lazyGridSharedTransitionScope = sharedTransitionScope.takeIf { lazyGridSharedTransitionEnabled }
    val lazyGridAnimatedVisibilityScope = animatedVisibilityScope.takeIf { lazyGridSharedTransitionEnabled }
    val shouldLoadMoreVideos by remember(
        gridState,
        selectedMainTab,
        selectedContributionTab,
        state.isLoadingMore,
        state.hasMoreVideos
    ) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            selectedMainTab == SpaceMainTab.CONTRIBUTION &&
                selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO) &&
                state.hasMoreVideos &&
                !state.isLoadingMore &&
                totalItems > 0 &&
                lastVisible >= totalItems - 6
        }
    }
    val playVideoFromSpace: (String) -> Unit = play@{ bvid ->
        val playbackTarget = resolveSpacePlaybackTarget(
            syncedProgress = state.watchProgressByBvid[bvid],
            localPositionMs = videoProgressLookup(bvid)
        )
        val playlist = state.videos
            .takeIf { videos -> videos.any { it.bvid == bvid } }
            ?.let { videos -> buildExternalPlaylistFromSpaceVideos(videos, clickedBvid = bvid) }
            ?: return@play onVideoClick(bvid, playbackTarget.cid, playbackTarget.resumePositionMs)
        com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
            playlist.playlistItems,
            playlist.startIndex,
            source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.SPACE
        )
        onVideoClick(bvid, playbackTarget.cid, playbackTarget.resumePositionMs)
    }
    // 首屏骨架阶段不触发「主页 Tab」加载：此时 state 是占位值（mid = 0）。真实数据
    // 到达后 isInitialLoading 转 false，effect 会以真实 mid 重新触发一次，语义不变。
    LaunchedEffect(state.userInfo.mid, isInitialLoading) {
        if (!isInitialLoading) onLoadHome()
    }

    val bangumiTabState = state.tabShellState.tabStates[SpaceMainTab.BANGUMI] ?: SpaceTabContentState()
    val cheeseTabState = state.tabShellState.tabStates[SpaceMainTab.CHEESE] ?: SpaceTabContentState()
    var highlightedLocateBvid by remember { mutableStateOf<String?>(null) }
    var isLocateHighlightVisible by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMainTab, state.hasLoadedDynamicsOnce, state.isLoadingDynamics) {
        if (
            selectedMainTab == SpaceMainTab.DYNAMIC &&
            shouldRequestInitialSpaceDynamicLoad(
                hasLoadedOnce = state.hasLoadedDynamicsOnce,
                isLoading = state.isLoadingDynamics
            )
        ) {
            onLoadDynamic()
        }
    }

    LaunchedEffect(selectedMainTab, bangumiTabState.hasLoaded, state.isLoadingBangumi) {
        if (selectedMainTab == SpaceMainTab.BANGUMI && !bangumiTabState.hasLoaded && !state.isLoadingBangumi) {
            onLoadBangumi()
        }
    }

    LaunchedEffect(selectedMainTab, cheeseTabState.hasLoaded, state.isLoadingCheese) {
        if (selectedMainTab == SpaceMainTab.CHEESE && !cheeseTabState.hasLoaded && !state.isLoadingCheese) {
            onLoadCheese()
        }
    }

    LaunchedEffect(shouldLoadMoreVideos) {
        if (shouldLoadMoreVideos) {
            onLoadMoreVideos()
        }
    }

    val contributionVideoItemStartIndex = remember(
        selectedMainTab,
        selectedContributionTab,
        state.isSearchMode,
        currentSearchScope,
        pinContentTabs,
        state.videos.isNotEmpty(),
        state.totalVideos,
    ) {
        if (selectedMainTab != SpaceMainTab.CONTRIBUTION) {
            0
        } else if (pinContentTabs) {
            2 + (if (state.isSearchMode) 1 else 0) +
                (if (state.isSearchMode && (state.videos.isNotEmpty() || state.totalVideos > 0)) 1 else 0)
        } else {
            1 +
                (if (shouldShowSpaceSearchEntry(currentSearchScope, state.isSearchMode)) 1 else 0) +
                (if (state.isSearchMode && currentSearchScope == SpaceSearchScope.VIDEO) 1 else 0)
        }
    }


    // 遮挡铰链时整页内容落入最大安全区，软折痕允许跨越（与首页/动态/搜索一致）。
    com.android.purebilibili.core.ui.adaptive.AppHingeSafeContent(
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .responsiveContentWidth(maxWidth = adaptiveLayoutSpec.contentMaxWidthDp.dp)
            .then(modifier)
    ) {
        // 手势边界在回调中读取测量值，不让滚动逐帧触发组合。
        val pinnedTabsHeights = remember { mutableMapOf<SpaceMainTab, Int>() }
        val sharedGlassTabs = LocalAppThemeConfig.current.liquidGlassEnabled
        // 玻璃主栏位于列表外，回弹必须作用于二者的共同父层，避免列表拉伸而 dock 原地漂浮。
        // 原生 MD3/Miuix 仍由各页网格绘制回弹；标签吸顶模式继续禁用回弹。
        val sharedSpaceOverscrollEffect = rememberOverscrollEffect()
        val pagerDragSource = remember(pagerState) {
            androidx.compose.foundation.interaction.MutableInteractionSource()
        }
        val pagerDragging by pagerDragSource.collectIsDraggedAsState()
        com.android.purebilibili.core.ui.AdaptivePullToRefreshBox(
            isRefreshing = selectedMainTab == SpaceMainTab.SHOP && shopResult == null,
            onRefresh = { shopReload++ },
            enabled = selectedMainTab == SpaceMainTab.SHOP,
            indicatorTopInset = chromeTopInset,
            modifier = Modifier.fillMaxSize(),
        ) {
        SpaceMainTabsHost(
            sharedGlassTabs = sharedGlassTabs,
            modifier = if (sharedGlassTabs && !pinContentTabs) {
                Modifier.overscroll(sharedSpaceOverscrollEffect)
            } else Modifier,
            position = {
                val currentGrid = pageGridStates.getValue(displayedMainTabs[pagerState.currentPage].tab)
                val info = currentGrid.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }
                val chromeBottom = with(contentDensity) { chromeTopInset.roundToPx() }
                val pinned = pinContentTabs && shouldShowSpacePinnedTabs(
                    tabsOffsetPx = item?.offset?.y,
                    beforeContentPaddingPx = info.beforeContentPadding,
                    chromeBottomPx = chromeBottom,
                    firstVisibleItemIndex = currentGrid.firstVisibleItemIndex,
                )
                when {
                    isInitialLoading -> null
                    pinned -> androidx.compose.ui.unit.IntOffset(0, chromeBottom)
                    item != null -> androidx.compose.ui.unit.IntOffset(0, item.offset.y + info.beforeContentPadding)
                    else -> null
                }
            },
            tabs = {
                SpaceMainTabRow(
                    tabs = displayedMainTabs,
                    selectedTab = resolveSpacePrimaryTab(selectedMainTab),
                    onSelect = { tab ->
                        if (pinContentTabs && tab == resolveSpacePrimaryTab(selectedMainTab)) {
                            val grid = pageGridStates.getValue(tab)
                            if (grid.firstVisibleItemIndex > 0) {
                                sharedTabsCoroutineScope.launch { grid.animateScrollToItem(1) }
                            }
                        } else onMainTabSelected(tab)
                    },
                    modifier = Modifier
                        .padding(horizontal = outerPaddingDp)
                        .scrollable(
                            state = pageGridStates.getValue(displayedMainTabs[pagerState.currentPage].tab),
                            orientation = Orientation.Vertical,
                            reverseDirection = true,
                        ),
                    indicatorPositionProvider = { pagerState.tabIndicatorPosition },
                    isScrollInProgressProvider = { pagerState.isScrollInProgress || pagerState.isTabSelectionInProgress },
                    isPagerDraggingProvider = { pagerDragging },
                    pagerTargetPositionProvider = { pagerState.targetPage.toFloat() },
                )
            },
        ) { mainTabsSlot ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            key = { displayedMainTabs[it].tab },
            modifier = Modifier.fillMaxSize().contentTabPagerGestures(
                pager = pagerState,
                enabled = !isInitialLoading,
                dragInteractionSource = if (sharedGlassTabs) pagerDragSource else null,
                contentTopPx = {
                    val info = pageGridStates.getValue(displayedMainTabs[pagerState.currentPage].tab).layoutInfo
                    val tabs = info.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }
                    val chromeBottom = with(contentDensity) { chromeTopInset.toPx() }
                    if (tabs != null) {
                        val naturalTop = (tabs.offset.y + info.beforeContentPadding).toFloat()
                        if (pinContentTabs) {
                            naturalTop.coerceAtLeast(chromeBottom) + tabs.size.height
                        } else (naturalTop + tabs.size.height).coerceAtLeast(chromeBottom)
                    } else if (info.visibleItemsInfo.any { it.index > 1 }) {
                        chromeBottom + if (pinContentTabs) {
                            (pinnedTabsHeights[displayedMainTabs[pagerState.currentPage].tab] ?: 0)
                        } else 0
                    } else {
                        Float.POSITIVE_INFINITY
                    }
                },
            ),
        ) { page ->
        val primaryTab = displayedMainTabs[page].tab
        val isPageActive = page == selectedPage
        val pageTab = if (isPageActive) selectedMainTab else primaryTab
        val state = state.copy(
            tabShellState = state.tabShellState.withSelectedTab(pageTab),
            isSearchMode = state.isSearchMode && isPageActive,
        )
        val selectedMainTab = pageTab
        val gridState = pageGridStates.getValue(primaryTab)
        // 外层回弹可共享，事件包装仍包含可挂载的节点，必须由各个 Pager 页分别持有。
        // 否则保留页与当前页会重复挂载、卸载同一节点，退出页面时可能崩溃。
        val sharedGridOverscrollEffect = remember(sharedSpaceOverscrollEffect) {
            sharedSpaceOverscrollEffect?.withoutVisualEffect()
        }
        val lazyGridSharedTransitionScope = lazyGridSharedTransitionScope.takeIf { isPageActive }
        val lazyGridAnimatedVisibilityScope = lazyGridAnimatedVisibilityScope.takeIf { isPageActive }
        val density = LocalDensity.current
        val measuredHeaderHeightPx = remember(gridState) { intArrayOf(0) }
        // [重构] 折叠进度：header 是 index 0，滚动偏移驱动 header 内容上移淡出（视差折叠）。
        // 折叠范围用 dp 换算，避免固定像素在不同 density 下曲线不一致
        val headerCollapseRangePx = with(density) { 320.dp.toPx() }
        val headerCollapseFraction = remember(gridState, headerCollapseRangePx) {
            derivedStateOf {
                if (gridState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (gridState.firstVisibleItemScrollOffset.toFloat() / headerCollapseRangePx)
                        .coerceIn(0f, 1f)
                }
            }
        }

        // 背景图视差（对齐 PiliPlus DynamicFlexibleSpaceBar 的 1/4 速率）：
        // 把头部 item 的原始滚动位移交给 SpaceHeader，在图片向上溢出的余量内做 translationY。
        val bannerScrollOffsetPx = remember(gridState) {
            derivedStateOf {
                if (gridState.firstVisibleItemIndex > 0) {
                    Float.MAX_VALUE
                } else {
                    gridState.firstVisibleItemScrollOffset.toFloat()
                }
            }
        }

        // 视频类内容继续跟随首页的卡宽/固定列数设置；动态卡片
        // 使用 360dp 的可读宽度，避免在展开屏上被媒体卡片的紧密列数压窄。
        val preferredGridColumns = resolveSpaceContentGridColumnCount(
            widthDp = windowWidthDp,
            fixedColumnCount = resolveHomeFeedStoredColumnCount(
                widthSizeClass = windowSizeClass.widthSizeClass,
                compactColumnCount = homeSettings.effectiveGridColumnCountCompact,
                defaultColumnCount = homeSettings.effectiveGridColumnCountForWindow(windowSizeClass),
            ),
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
            contentMaxWidthDp = adaptiveLayoutSpec.contentMaxWidthDp,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
        val gridColumnPreference = rememberFeedGridColumnPreference(
            settings = homeSettings,
            widthSizeClass = windowSizeClass.widthSizeClass,
            defaultColumns = preferredGridColumns,
            bounds = resolveHomeFeedPinchColumnBounds(windowSizeClass.widthSizeClass, windowWidthDp),
        )
        var isPinchPillVisible by remember { mutableStateOf(false) }
        var pinchPillDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT)
        val coroutineScope = rememberCoroutineScope()
        val spaceOverscrollEffect = rememberOverscrollEffect()
        val effectivePreferredColumns = gridColumnPreference.columns
        val gridColumns = if (selectedMainTab == SpaceMainTab.DYNAMIC) {
            adaptiveLayoutSpec.dynamicColumns
        } else {
            effectivePreferredColumns
        }
        LaunchedEffect(
            state.pendingLocateBvid,
            selectedMainTab,
            selectedContributionTab,
            contributionVideoItemStartIndex,
            state.videos,
            state.hasMoreVideos,
            state.videoPageLoadCompletionVersion,
            gridColumns,
            contributionVideoLayoutMode,
            homeSettings.showFullVideoCardContent,
        ) {
            val targetBvid = state.pendingLocateBvid ?: return@LaunchedEffect
            if (
                selectedMainTab == SpaceMainTab.CONTRIBUTION &&
                selectedContributionTab.subTab == SpaceSubTab.VIDEO
            ) {
                val targetVideoIndex = when (
                    val action = resolveSpaceLocateTargetPageAction(
                        targetBvid = targetBvid,
                        videos = state.videos,
                        isLoading = state.isLoadingMore,
                        hasMore = state.hasMoreVideos,
                        lastLoadFailed = state.lastVideoPageLoadFailed,
                    )
                ) {
                    is SpaceLocateTargetPageAction.Found -> action.index
                    SpaceLocateTargetPageAction.Wait -> return@LaunchedEffect
                    SpaceLocateTargetPageAction.LoadMore -> {
                        onLoadMoreVideos()
                        return@LaunchedEffect
                    }
                    SpaceLocateTargetPageAction.LoadFailed -> {
                        onLocateTargetLoadFailed(targetBvid)
                        return@LaunchedEffect
                    }
                    SpaceLocateTargetPageAction.Missing -> {
                        onLocateTargetMissing(targetBvid)
                        return@LaunchedEffect
                    }
                }

                gridState.animateScrollToItem(
                    contributionVideoItemStartIndex + if (
                        contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID &&
                        gridColumns > 1 && !homeSettings.showFullVideoCardContent
                    ) targetVideoIndex / gridColumns else targetVideoIndex,
                    scrollOffset = if (pinContentTabs) {
                        -gridState.measureSpacePinnedTabsHeightPx()
                    } else 0,
                )
                highlightedLocateBvid = targetBvid
                repeat(3) {
                    isLocateHighlightVisible = true
                    kotlinx.coroutines.delay(220)
                    isLocateHighlightVisible = false
                    kotlinx.coroutines.delay(140)
                }
                highlightedLocateBvid = null
                onLocateTargetConsumed(targetBvid)
            }
        }
        val pinchColumnBounds = remember(windowSizeClass.widthSizeClass, windowWidthDp) {
            resolveHomeFeedPinchColumnBounds(
                widthSizeClass = windowSizeClass.widthSizeClass,
                contentWidthDp = windowWidthDp,
            )
        }
        val spaceFeedCardLayout = resolveHomeFeedCardLayout(
            style = homeSettings.homeFeedCardStyle,
            gridColumns = gridColumns,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
        val spaceFeedCoverAspectRatio = spaceFeedCardLayout.coverAspectRatio
        // 首屏骨架与真实卡片共用同一个网格、同一种排版：色块用固定色（不呼吸），
        // 骨架只负责占位，数据到达时是 item 原地替换而不是整棵网格重建。
        val spaceSkeletonBlockColor = rememberStaticSkeletonBlockColor()
        val spaceSkeletonBannerHeightDp = remember(
            windowWidthDp,
            windowSizeClass.widthDp,
            windowSizeClass.heightDp,
        ) {
            resolveSpaceBannerMetrics(
                renderedBannerWidthDp = windowWidthDp.toFloat(),
                windowWidthDp = windowSizeClass.widthDp.value,
                windowHeightDp = windowSizeClass.heightDp.value,
            ).heightDp
        }
        val tabsPinned by remember(gridState, pinContentTabs, chromeTopInset, density, isInitialLoading) {
            derivedStateOf {
                if (!pinContentTabs || isInitialLoading) false else {
                    val info = gridState.layoutInfo
                    val tabs = info.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }
                    shouldShowSpacePinnedTabs(
                        tabsOffsetPx = tabs?.offset?.y,
                        beforeContentPaddingPx = info.beforeContentPadding,
                        chromeBottomPx = with(density) { chromeTopInset.roundToPx() },
                        firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    )
                }
            }
        }
        val pinnedTabsContent: @Composable () -> Unit = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { pinnedTabsHeights[primaryTab] = it.height }
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SpaceContentTabs(
                    state = state,
                    onMainTabSelected = onMainTabSelected,
                    onMainTabReselected = {
                        if (gridState.firstVisibleItemIndex > 0) {
                            coroutineScope.launch { gridState.animateScrollToItem(1) }
                        }
                    },
                    onContributionTabSelected = { id ->
                        if (id != state.selectedContributionTabId) {
                            alignSpacePageHeader(gridState, contributionGridStateFor(id))
                        }
                        onContributionTabSelected(id)
                    },
                    pagerState = pagerState,
                    page = page,
                    mainTabsSlot = mainTabsSlot,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectedMainTab == SpaceMainTab.CONTRIBUTION &&
                    selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO) &&
                    !state.isSearchMode && (state.videos.isNotEmpty() || state.totalVideos > 0)
                ) {
                    SpaceContributionVideoSummaryBar(
                        totalCount = state.totalVideos.takeIf { it > 0 } ?: state.videos.size,
                        currentOrder = state.sortOrder,
                        onSelectSortOrder = onSelectSortOrder,
                    )
                }
            }
        }
        // 瀑布流不提供原生吸顶 item；每页用独立顶层承接标签，跨栏占位保留滚动几何。
        // 玻璃与原生材质继续使用各自的标签组件参数。
        Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(gridColumns),
            state = gridState,
            overscrollEffect = when {
                pinContentTabs -> null
                sharedGlassTabs -> sharedGridOverscrollEffect
                else -> spaceOverscrollEffect
            },
            modifier = Modifier
                .fillMaxSize()
                .homeFeedPinchZoom(
                    enabled = selectedMainTab != SpaceMainTab.DYNAMIC && homeSettings.gridColumnPinchEnabled,
                    currentColumns = gridColumns,
                    bounds = pinchColumnBounds,
                    onColumnsChange = { newColumns ->
                        gridColumnPreference.onChange(newColumns)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isPinchPillVisible = true
                        pinchPillDismissJob?.cancel()
                    },
                    onGestureEnd = { finalColumns ->
                        gridColumnPreference.onGestureEnd(finalColumns)
                        pinchPillDismissJob?.cancel()
                        pinchPillDismissJob = coroutineScope.launch {
                            kotlinx.coroutines.delay(1000)
                            isPinchPillVisible = false
                        }
                    }
                ),
            contentPadding = PaddingValues(
                start = outerPaddingDp,
                end = outerPaddingDp,
                top = chromeTopInset,
                bottom = bottomInset + 24.dp +
                    if (com.android.purebilibili.core.ui.rememberNowPlayingBarOverlayVisible()) {
                        com.android.purebilibili.core.ui.NowPlayingBarOverlayAvoidancePadding
                    } else {
                        0.dp
                    }
            ),
            horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
            verticalItemSpacing = spaceFeedCardLayout.verticalItemSpacingDp.dp
        ) LazyVerticalStaggeredGrid@{
            // 首屏骨架直接铺在内容网格里：与真实卡片共用同一个 LazyVerticalStaggeredGrid 与同一个
            // gridState，数据到达后是 item 原地替换，网格本身不重建、也不丢滚动锚点。
            // （显式标签：LazyVerticalStaggeredGrid 是 AppLazyVerticalStaggeredGrid 的导入别名，避免隐式
            // 标签名与别名不一致导致 return@ 解析到别处。）
            if (isInitialLoading) {
                SpaceLoadingSkeleton(
                    columns = gridColumns,
                    coverAspectRatio = spaceFeedCoverAspectRatio,
                    bannerHeightDp = spaceSkeletonBannerHeightDp,
                    useExpandedHeader = adaptiveLayoutSpec.useExpandedHeader,
                    blockColor = spaceSkeletonBlockColor,
                )
                return@LazyVerticalStaggeredGrid
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(
                    Modifier
                        .stationaryPagerHeader(pagerState, page)
                        .onSizeChanged { measuredHeaderHeightPx[0] = it.height }
                ) {
                SpaceHeader(
                    userInfo = state.headerState.userInfo ?: state.userInfo,
                    relationStat = state.headerState.relationStat ?: state.relationStat,
                    upStat = state.headerState.upStat ?: state.upStat,
                    collapseFractionProvider = { if (pinContentTabs) 0f else headerCollapseFraction.value },
                    collapseAsUnit = pinContentTabs,
                    bannerScrollOffsetPxProvider = { bannerScrollOffsetPx.value },
                    bannerScrollState = gridState,
                    chargeGroup = state.chargeGroup,
                    guardGroup = state.guardGroup,
                    onWebClick = onWebClick,
                    onUpowerRankClick = onUpowerRankClick,
                    onMemberGuardClick = onMemberGuardClick,
                    onFollowClick = onFollowClick,
                    onMessageClick = onMessageClick,
                    onFollowingClick = onFollowingClick,
                    onFansClick = onFansClick,
                    onFollowedUsersClick = onFollowedUsersClick,
                    onTopPhotoClick = onTopPhotoClick,
                    onAvatarClick = onAvatarClick,
                    onLiveClick = { roomId, title, uname -> onLiveClick(roomId, title, uname) },
                    sharedTransitionScope = lazyGridSharedTransitionScope,
                    animatedVisibilityScope = lazyGridAnimatedVisibilityScope,
                    chromeTopInset = chromeTopInset,
                    outerPadding = outerPaddingDp,
                    useExpandedLayout = adaptiveLayoutSpec.useExpandedHeader,
                    bannerPage = sharedBannerPage,
                    onBannerPageChange = { sharedBannerPage = it },
                    bannerPageActive = isPageActive,
                    contentPageMoving = pagerState.isScrollInProgress,
                )
                }
            }

            item(key = "space_tabs", span = StaggeredGridItemSpan.FullLine) {
                if (pinContentTabs) {
                    // 保留列表中的尺寸，吸顶后隐藏原位内容与语义，避免重复入口。
                    Box(
                        Modifier
                            .graphicsLayer { alpha = if (tabsPinned) 0f else 1f }
                            .then(if (tabsPinned) Modifier.clearAndSetSemantics { } else Modifier),
                    ) {
                        pinnedTabsContent()
                    }
                } else {
                    SpaceContentTabs(
                        state = state,
                        onMainTabSelected = onMainTabSelected,
                        onContributionTabSelected = onContributionTabSelected,
                        pagerState = pagerState,
                        page = page,
                        mainTabsSlot = mainTabsSlot,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val showSearch = selectedMainTab == SpaceMainTab.DYNAMIC ||
                (selectedMainTab == SpaceMainTab.CONTRIBUTION &&
                    selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO))
            if (showSearch && state.isSearchMode) {
                item(key = "space_search", span = StaggeredGridItemSpan.FullLine) {
                    LaunchedEffect(state.isSearchMode, currentSearchScope) {
                        searchFocusRequester.requestFocus()
                    }
                    AppLiquidAwareSearchField(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = resolveSpaceSearchPlaceholder(currentSearchScope),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester),
                    )
                }
            } else if (false && shouldShowSpaceSearchEntry(currentSearchScope, state.isSearchMode)) {
                item(key = "space_search_entry", span = StaggeredGridItemSpan.FullLine) {
                    SpaceSearchEntryChip(
                        label = resolveSpaceSearchEntryLabel(currentSearchScope),
                        onClick = onSearchEntryClick,
                    )
                }
            }

        when (selectedMainTab) {
            SpaceMainTab.HOME -> {
                state.topVideo?.let { topVideo ->
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceTopVideoCard(
                            video = topVideo,
                            onClick = { playVideoFromSpace(topVideo.bvid) },
                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(topVideo.bvid),
                            sharedTransitionScope = lazyGridSharedTransitionScope,
                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                        )
                    }
                }

                if (state.notice.isNotBlank()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceNoticeCard(notice = state.notice)
                    }
                }

                if (state.homeVideos.isNotEmpty() || state.homeVideoCount > 0) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "视频",
                            count = state.homeVideoCount.takeIf { it > 0 } ?: state.homeVideos.size,
                            onActionClick = {
                                onMainTabSelected(SpaceMainTab.CONTRIBUTION)
                                state.contributionTabs.firstOrNull { it.subTab == SpaceSubTab.VIDEO }?.let {
                                    onContributionTabSelected(it.id)
                                }
                            }
                        )
                    }
                    val homeVideos = state.homeVideos.take(4)
                    val homeVideoKeys = homeVideos.map { "home_video_${it.bvid}" }
                    val alignHomeVideos = gridColumns > 1 && !homeSettings.showFullVideoCardContent
                    val renderHomeVideo: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                        val video = homeVideos[index]
                        val localProgressMs = videoProgressLookup(video.bvid)
                        SpaceHomeVideoCard(
                            video = video,
                            modifier = cardModifier,
                            progressState = resolveSpaceVideoProgressState(
                                video = video,
                                localPositionMs = localProgressMs,
                                syncedProgress = state.watchProgressByBvid[video.bvid]
                            ),
                            coverAspectRatio = spaceFeedCoverAspectRatio,
                            onClick = { playVideoFromSpace(video.bvid) },
                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(video.bvid),
                            sharedTransitionScope = lazyGridSharedTransitionScope,
                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                        )
                    }
                    if (!alignHomeVideos) {
                        itemsIndexed(homeVideos, key = { index, _ -> homeVideoKeys[index] }) { index, _ ->
                            renderHomeVideo(index, Modifier)
                        }
                    } else {
                        com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(homeVideos.size, gridColumns)
                            .forEach { row ->
                                item(key = "space_home_video_row_${homeVideoKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                                    com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                        indices = row,
                                        itemKeys = homeVideoKeys,
                                        columns = gridColumns,
                                        horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        content = renderHomeVideo,
                                    )
                                }
                            }
                    }
                }

                if (state.homeFavoriteFolders.isNotEmpty() || state.homeFavoriteFolderCount > 0) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "收藏",
                            count = state.homeFavoriteFolderCount.takeIf { it > 0 }
                                ?: state.homeFavoriteFolders.size,
                            onActionClick = { onMainTabSelected(SpaceMainTab.FAVORITE) }
                        )
                    }
                    items(
                        items = state.homeFavoriteFolders.take(1),
                        key = { "home_favorite_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { folder ->
                        SpaceFavoriteFolderRow(
                            folder = folder,
                            modifier = boundedListModifier,
                            onClick = {
                                onViewAllClick(
                                    "favorite",
                                    folder.id,
                                    state.userInfo.mid,
                                    folder.title,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }

                if (state.homeCoinVideos.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "最近投币的视频",
                            count = state.homeCoinVideoCount.takeIf { it > 0 } ?: state.homeCoinVideos.size,
                            actionLabel = "查看全部",
                            onActionClick = {
                                onViewAllClick(
                                    "coin",
                                    0L,
                                    state.userInfo.mid,
                                    "最近投币的视频",
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                    val aggregateItems = state.homeCoinVideos.take(2)
                    val aggregateKeys = aggregateItems.mapIndexed { index, item -> resolveSpaceAggregateLazyItemKey("coin", index, item) }
                    val alignAggregate = gridColumns > 1 && !homeSettings.showFullVideoCardContent
                    val renderAggregate: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                        val item = aggregateItems[index]
                        SpaceAggregateMediaCard(
                            item = item,
                            modifier = cardModifier,
                            onClick = {
                                handleAggregateArchiveClick(
                                    item = item,
                                    onVideoClick = playVideoFromSpace,
                                    onAudioClick = onAudioClick,
                                    onBangumiClick = onBangumiClick,
                                    onWebClick = onWebClick
                                )
                            },
                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(item.bvid),
                            sharedTransitionScope = lazyGridSharedTransitionScope,
                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                        )
                    }
                    if (!alignAggregate) {
                        itemsIndexed(aggregateItems, key = { index, _ -> aggregateKeys[index] }) { index, _ ->
                            renderAggregate(index, Modifier)
                        }
                    } else {
                        com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(aggregateItems.size, gridColumns)
                            .forEach { row ->
                                item(key = "space_coin_row_${aggregateKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                                    com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                        indices = row,
                                        itemKeys = aggregateKeys,
                                        columns = gridColumns,
                                        horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        content = renderAggregate,
                                    )
                                }
                            }
                    }
                }

                if (state.homeLikeVideos.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "最近点赞的视频",
                            count = state.homeLikeVideoCount.takeIf { it > 0 } ?: state.homeLikeVideos.size,
                            actionLabel = "查看全部",
                            onActionClick = {
                                if (onLikedVideosClick != null) {
                                    onLikedVideosClick(state.userInfo.mid, state.userInfo.name)
                                } else {
                                    onViewAllClick(
                                        "like",
                                        0L,
                                        state.userInfo.mid,
                                        "最近点赞的视频",
                                        state.userInfo.name
                                    )
                                }
                            }
                        )
                    }
                    val aggregateItems = state.homeLikeVideos.take(2)
                    val aggregateKeys = aggregateItems.mapIndexed { index, item -> resolveSpaceAggregateLazyItemKey("like", index, item) }
                    val alignAggregate = gridColumns > 1 && !homeSettings.showFullVideoCardContent
                    val renderAggregate: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                        val item = aggregateItems[index]
                        SpaceAggregateMediaCard(
                            item = item,
                            modifier = cardModifier,
                            onClick = {
                                handleAggregateArchiveClick(
                                    item = item,
                                    onVideoClick = playVideoFromSpace,
                                    onAudioClick = onAudioClick,
                                    onBangumiClick = onBangumiClick,
                                    onWebClick = onWebClick
                                )
                            },
                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(item.bvid),
                            sharedTransitionScope = lazyGridSharedTransitionScope,
                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                        )
                    }
                    if (!alignAggregate) {
                        itemsIndexed(aggregateItems, key = { index, _ -> aggregateKeys[index] }) { index, _ ->
                            renderAggregate(index, Modifier)
                        }
                    } else {
                        com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(aggregateItems.size, gridColumns)
                            .forEach { row ->
                                item(key = "space_like_row_${aggregateKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                                    com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                        indices = row,
                                        itemKeys = aggregateKeys,
                                        columns = gridColumns,
                                        horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        content = renderAggregate,
                                    )
                                }
                            }
                    }
                }

                if (state.articles.isNotEmpty() || state.totalArticles > 0) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "图文",
                            count = state.totalArticles.takeIf { it > 0 } ?: state.articles.size,
                            onActionClick = {
                                onMainTabSelected(SpaceMainTab.CONTRIBUTION)
                                state.contributionTabs.firstOrNull {
                                    it.subTab == SpaceSubTab.ARTICLE || it.subTab == SpaceSubTab.OPUS
                                }?.let { onContributionTabSelected(it.id) }
                            }
                        )
                    }
                    items(
                        items = state.articles.take(1),
                        key = { "home_article_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { article ->
                        SpaceArticleListItem(
                            article = article,
                            modifier = boundedListModifier,
                            onClick = {
                                dispatchSpaceArticleClick(
                                    article = article,
                                    onDynamicDetailClick = onDynamicDetailClick,
                                    onArticleClick = onArticleClick
                                )
                            }
                        )
                    }
                }

                if (state.audios.isNotEmpty() || state.totalAudios > 0) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "音频",
                            count = state.totalAudios.takeIf { it > 0 } ?: state.audios.size,
                            onActionClick = {
                                onMainTabSelected(SpaceMainTab.CONTRIBUTION)
                                state.contributionTabs.firstOrNull { it.subTab == SpaceSubTab.AUDIO }?.let {
                                    onContributionTabSelected(it.id)
                                }
                            }
                        )
                    }
                    items(
                        items = state.audios.take(1),
                        key = { "home_audio_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { audio ->
                        SpaceAudioListItem(
                            audio = audio,
                            modifier = boundedListModifier,
                            onClick = { onAudioClick(audio.id) }
                        )
                    }
                }

                if (state.homeBangumiItems.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "追番",
                            count = state.homeBangumiCount.takeIf { it > 0 } ?: state.homeBangumiItems.size,
                            onActionClick = { onMainTabSelected(SpaceMainTab.BANGUMI) }
                        )
                    }
                    items(
                        items = state.homeBangumiItems.take(3),
                        key = { "home_bangumi_${it.aid}_${it.param}" }
                    ) { item ->
                        SpaceAggregatePosterCard(
                            item = item,
                            onClick = {
                                handleAggregateArchiveClick(
                                    item = item,
                                    onVideoClick = playVideoFromSpace,
                                    onAudioClick = onAudioClick,
                                    onBangumiClick = onBangumiClick,
                                    onWebClick = onWebClick
                                )
                            }
                        )
                    }
                }

                if (state.homeComicItems.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "漫画",
                            count = state.homeComicCount.takeIf { it > 0 } ?: state.homeComicItems.size,
                            onActionClick = {
                                onMainTabSelected(SpaceMainTab.CONTRIBUTION)
                                state.contributionTabs.firstOrNull { it.subTab == SpaceSubTab.COMIC }?.let { onContributionTabSelected(it.id) }
                            }
                        )
                    }
                    items(
                        items = state.homeComicItems.take(1),
                        key = { "home_comic_${it.aid}_${it.param}" }
                    ) { item ->
                        SpaceAggregateMediaCard(
                            item = item,
                            onClick = {
                                handleAggregateArchiveClick(
                                    item = item,
                                    onVideoClick = playVideoFromSpace,
                                    onAudioClick = onAudioClick,
                                    onBangumiClick = onBangumiClick,
                                    onWebClick = onWebClick
                                )
                            },
                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(item.bvid),
                            sharedTransitionScope = lazyGridSharedTransitionScope,
                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                        )
                    }
                }

                if (
                    state.homeVideos.isEmpty() &&
                    state.homeFavoriteFolders.isEmpty() &&
                    state.homeCoinVideos.isEmpty() &&
                    state.homeLikeVideos.isEmpty() &&
                    state.articles.isEmpty() &&
                    state.audios.isEmpty() &&
                    state.homeBangumiItems.isEmpty() &&
                    state.homeComicItems.isEmpty()
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionEmptyState(
                            title = "主页空空的",
                            subtitle = "暂时没有可展示的主页内容"
                        )
                    }
                }
                val offlineRoom = state.userInfo.liveRoom
                if (offlineRoom != null && offlineRoom.roomStatus == 1 && offlineRoom.liveStatus != 1 && offlineRoom.roomId > 0L) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        AppTextButton(onClick = { onLiveClick(offlineRoom.roomId, offlineRoom.title, state.userInfo.name) },
                            modifier = Modifier.fillMaxWidth()) {
                            AppText("TA 现在没有直播，去 TA 的直播间")
                        }
                    }
                }
            }

            SpaceMainTab.DYNAMIC -> {
                val presentationState = resolveSpaceDynamicPresentationState(
                    itemCount = state.dynamics.size,
                    isLoading = state.isLoadingDynamics,
                    hasLoadedOnce = state.hasLoadedDynamicsOnce,
                    lastLoadFailed = state.lastDynamicLoadFailed
                )

                if (state.searchQuery.isNotBlank() && state.dynamics.isNotEmpty() && dynamicCardItems.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionEmptyState(
                            title = "没有结果",
                            subtitle = if (state.isLoadingDynamics) {
                                "正在自动加载更多动态…"
                            } else if (state.hasMoreDynamics) {
                                "未在近期动态中找到，可继续下滑加载更多"
                            } else {
                                "已加载的动态中没有匹配项"
                            }
                        )
                    }
                } else if (presentationState == SpaceDynamicPresentationState.EMPTY) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionEmptyState(
                            title = "暂无动态",
                            subtitle = "这个空间暂时没有可展示的动态内容"
                        )
                    }
                } else if (presentationState == SpaceDynamicPresentationState.ERROR) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceErrorSection(
                            message = "动态加载失败，请稍后重试",
                            onRetry = onLoadDynamic
                        )
                    }
                } else {
                    items(
                        items = dynamicCardItems,
                        key = { "space_dynamic_${it.id_str}" },
                        span = { StaggeredGridItemSpan.SingleLane }
                    ) { dynamic ->
                        DynamicCardV2(
                            item = dynamic,
                            gifImageLoader = context.imageLoader,
                            actions = DynamicCardActions(
                                navigation = DynamicCardNavigationActions(
                                    onVideoClick = playVideoFromSpace,
                                    onBangumiClick = { seasonId, _ -> onBangumiClick(seasonId) },
                                    onUserClick = onUserClick,
                                    onTopicClick = onTopicClick,
                                    onTopicKeywordClick = onTopicKeywordClick,
                                    onLiveClick = { roomId, title, uname ->
                                        onLiveClick(roomId, title, uname)
                                    },
                                    onMusicClick = onAudioClick,
                                    onCollectionClick = { mediaId, ownerMid, title, url ->
                                        if (mediaId > 0L && url.contains("medialist/detail/ml", ignoreCase = true)) {
                                            onViewAllClick("favorite", mediaId, ownerMid, title, "")
                                        } else if (url.isNotBlank()) {
                                            onWebClick(url, title)
                                        }
                                    },
                                    onCourseClick = { url, title ->
                                        val courseNav = com.android.purebilibili.feature.bangumi.policy.parseCourseNavigation(url)
                                        if (courseNav != null && courseNav.seasonId > 0L) {
                                            onCheeseClick(courseNav.seasonId)
                                        } else {
                                            onWebClick(url, title)
                                        }
                                    },
                                    onArticleClick = onArticleClick,
                                    onDynamicDetailClick = onDynamicDetailClick,
                                    onMatchClick = onMatchClick,
                                ),
                                interaction = DynamicCardInteractionActions(
                                    onCommentClick = { onDynamicDetailClick(dynamic.id_str) },
                                    onRepostClick = onSpaceDynamicRepostClick,
                                    onLikeClickWithState = { dynamicId, isLiked ->
                                        onSpaceDynamicLikeClick(dynamicId, isLiked)
                                    },
                                    onDeleteClick = onSpaceDynamicDeleteClick,
                                    onReserveClick = onSpaceDynamicReserveClick,
                                ),
                            ),
                            presentation = DynamicCardPresentation(
                                isDetail = true,
                                isLiked = likedDynamics.contains(dynamic.id_str),
                                likeOverride = likeOverrides[dynamic.id_str],
                                forwardCountDelta = forwardCountDeltas[dynamic.id_str] ?: 0,
                            ),
                        )
                    }

                    if (state.isLoadingDynamics && dynamicCardItems.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            SpaceLoadingFooter()
                        }
                    }

                    if (state.hasMoreDynamics && dynamicCardItems.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            LaunchedEffect(dynamicCardItems.size, isPageActive) {
                                if (isPageActive) onLoadMoreDynamic()
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }

            SpaceMainTab.CONTRIBUTION -> {
                when (selectedContributionTab.subTab) {
                    SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO -> {
                        // 开启渐进模糊时，数量与排序只属于列表，滚出后不创建顶栏悬浮副本。
                        // 关闭时仍由吸顶标签区域承接，玻璃、MD3 与 Miuix 共用此边界。
                        if ((!pinContentTabs || state.isSearchMode) && (state.videos.isNotEmpty() || state.totalVideos > 0)) {
                            item(key = "space_video_summary_bar", span = StaggeredGridItemSpan.FullLine) {
                                SpaceContributionVideoSummaryBar(
                                    totalCount = state.totalVideos.takeIf { it > 0 } ?: state.videos.size,
                                    currentOrder = state.sortOrder,
                                    onSelectSortOrder = onSelectSortOrder,
                                )
                            }
                        }

                        if (state.videos.isEmpty() && !state.isLoadingMore) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceSectionEmptyState(
                                    title = "暂无投稿",
                                    subtitle = "这个分区下暂时没有可展示的视频"
                                )
                            }
                        }

                        val contributionVideoKeys = state.videos.map {
                            resolveSpaceContributionVideoItemKey(contributionVideoLayoutMode, it.bvid, it.aid)
                        }
                        val alignContributionVideos = contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID &&
                            gridColumns > 1 && !homeSettings.showFullVideoCardContent
                        val renderContributionVideo: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                            val video = state.videos[index]
                            val localProgressMs = videoProgressLookup(video.bvid)
                            AnimatedVideoListItem(modifier = cardModifier, enabled = homeSettings.cardAnimationEnabled) {
                                when (contributionVideoLayoutMode) {
                                    SpaceContributionVideoLayoutMode.GRID -> {
                                        SpaceHomeVideoCard(
                                            video = video,
                                            modifier = if (alignContributionVideos) Modifier.fillMaxHeight() else Modifier,
                                            progressState = resolveSpaceVideoProgressState(
                                                video = video,
                                                localPositionMs = localProgressMs,
                                                syncedProgress = state.watchProgressByBvid[video.bvid]
                                            ),
                                            coverAspectRatio = spaceFeedCoverAspectRatio,
                                            isLocateHighlight = highlightedLocateBvid == video.bvid &&
                                                isLocateHighlightVisible,
                                            onClick = { playVideoFromSpace(video.bvid) },
                                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(video.bvid),
                                            sharedTransitionScope = lazyGridSharedTransitionScope,
                                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                                        )
                                    }
                                    SpaceContributionVideoLayoutMode.SINGLE_COLUMN -> {
                                        SpaceArchiveListItemRow(
                                            title = video.title,
                                            cover = video.pic,
                                            duration = video.length,
                                            publishTime = FormatUtils.formatPublishTime(video.created),
                                            play = video.play.toLong(),
                                            secondaryCount = video.comment.toLong(),
                                            progressState = resolveSpaceVideoProgressState(
                                                video = video,
                                                localPositionMs = localProgressMs,
                                                syncedProgress = state.watchProgressByBvid[video.bvid]
                                            ),
                                            statusLabels = video.statusLabels +
                                                listOfNotNull(rememberSponsorFeedBadgeLabel(video.bvid)),
                                            isLocateHighlight = highlightedLocateBvid == video.bvid &&
                                                isLocateHighlightVisible,
                                            modifier = boundedListModifier,
                                            onClick = { playVideoFromSpace(video.bvid) },
                                            sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(video.bvid),
                                            sharedTransitionScope = lazyGridSharedTransitionScope,
                                            animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                                        )
                                    }
                                }
                            }
                        }
                        if (!alignContributionVideos) {
                            itemsIndexed(
                                state.videos,
                                key = { index, _ -> contributionVideoKeys[index] },
                                span = { _, _ -> if (contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID) StaggeredGridItemSpan.SingleLane else StaggeredGridItemSpan.FullLine },
                            ) { index, _ ->
                                renderContributionVideo(index, videoListItemModifier(enabled = homeSettings.cardAnimationEnabled))
                            }
                        } else {
                            com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(state.videos.size, gridColumns)
                                .forEach { row ->
                                    item(key = "space_contribution_row_${contributionVideoKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                                        com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                            indices = row,
                                            itemKeys = contributionVideoKeys,
                                            columns = gridColumns,
                                            horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
                                            modifier = videoListItemModifier(enabled = homeSettings.cardAnimationEnabled).fillMaxWidth(),
                                            content = renderContributionVideo,
                                        )
                                    }
                                }
                        }

                        if (state.isLoadingMore) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceLoadingFooter()
                            }
                        }
                    }

                    SpaceSubTab.AUDIO -> {
                        if (state.audios.isEmpty() && !state.isLoadingAudios) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceSectionEmptyState(
                                    title = "暂无音频",
                                    subtitle = "这个 UP 还没有公开的音频作品"
                                )
                            }
                        }

                        items(
                            items = state.audios,
                            key = { "space_audio_${it.id}" },
                            span = { StaggeredGridItemSpan.FullLine }
                        ) { audio ->
                            SpaceAudioListItem(
                                audio = audio,
                                modifier = boundedListModifier,
                                onClick = { onAudioClick(audio.id) }
                            )
                        }

                        if (state.isLoadingAudios) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceLoadingFooter()
                            }
                        } else if (state.hasMoreAudios && state.audios.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                LaunchedEffect(state.audios.size, isPageActive) { if (isPageActive) onLoadMoreAudios() }
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }

                    SpaceSubTab.ARTICLE, SpaceSubTab.OPUS -> {
                        val isOpus = selectedContributionTab.subTab == SpaceSubTab.OPUS
                        val articles = if (isOpus) state.opusItems else state.articles
                        val loading = if (isOpus) state.isLoadingOpus else state.isLoadingArticles
                        val hasMore = if (isOpus) state.hasMoreOpus else state.hasMoreArticles
                        val loadMore = if (isOpus) onLoadMoreOpus else onLoadMoreArticles
                        if (isOpus && selectedContributionTab.filters.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                AppWindowActionMenu(groups = listOf(selectedContributionTab.filters.map { filter ->
                                    AppWindowAction(label = filter.text, selected = filter.meta == state.opusFilter,
                                        onClick = { onSelectOpusFilter(filter.meta) })
                                })) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppText(selectedContributionTab.filters.firstOrNull { it.meta == state.opusFilter }?.text ?: "全部图文")
                                        AppIcon(Icons.Outlined.MoreVert, contentDescription = "图文筛选")
                                    }
                                }
                            }
                        }
                        if (isOpus) {
                            items(state.opusCollections, key = { "opus_collection_${it.id}" }, span = { StaggeredGridItemSpan.FullLine }) { collection ->
                                SpaceCollectionSummaryCard(title = collection.title, subtitle = collection.subtitle,
                                    cover = collection.cover, total = null,
                                    onClick = { onArticleCollectionClick(collection.id, collection.title) })
                            }
                        }
                        val opusError = state.opusError.takeIf { isOpus }
                        if (opusError != null) {
                            item(span = StaggeredGridItemSpan.FullLine) { SpaceErrorSection(opusError, onRetry = loadMore) }
                        }
                        if (articles.isEmpty() && !loading && opusError == null) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceSectionEmptyState(
                                    title = "暂无图文",
                                    subtitle = "这个 UP 还没有公开的图文内容"
                                )
                            }
                        }

                        items(
                            items = articles,
                            key = { "space_article_${it.id}" },
                            span = { StaggeredGridItemSpan.FullLine }
                        ) { article ->
                            SpaceArticleListItem(
                                article = article,
                                modifier = boundedListModifier,
                                onClick = {
                                    dispatchSpaceArticleClick(
                                        article = article,
                                        onDynamicDetailClick = onDynamicDetailClick,
                                        onArticleClick = onArticleClick
                                    )
                                }
                            )
                        }

                        if (loading) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceLoadingFooter()
                            }
                        } else if (hasMore && articles.isNotEmpty() && opusError == null) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                LaunchedEffect(articles.size, isPageActive) { if (isPageActive) loadMore() }
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }

                    SpaceSubTab.SEASON_VIDEO -> {
                        val season = state.seasons.firstOrNull { it.meta.season_id == selectedContributionTab.seasonId }
                        val archives = state.seasonArchives[selectedContributionTab.seasonId].orEmpty()
                        item(span = StaggeredGridItemSpan.FullLine) {
                            SpaceCollectionSummaryCard(
                                title = season?.meta?.name ?: selectedContributionTab.title,
                                subtitle = season?.meta?.description.orEmpty(),
                                cover = season?.meta?.cover.orEmpty(),
                                total = season?.meta?.total ?: archives.size,
                                modifier = boundedListModifier,
                                onClick = {
                                    onViewAllClick(
                                        "season",
                                        selectedContributionTab.seasonId,
                                        state.userInfo.mid,
                                        selectedContributionTab.title,
                                        state.userInfo.name
                                    )
                                }
                            )
                        }
                        if (archives.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceSectionEmptyState(
                                    title = "暂无合集内容",
                                    subtitle = "这个合集暂时没有可展示的视频"
                                )
                            }
                        }
                        val archiveKeys = archives.map { "season_video_${it.aid}_${it.bvid}" }
                        val alignArchives = contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID &&
                            gridColumns > 1 && !homeSettings.showFullVideoCardContent
                        val renderArchive: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                            val archive = archives[index]
                            SpaceContributionArchiveVideoItem(
                                video = archive.toSpaceVideoItem(),
                                modifier = cardModifier,
                                layoutMode = contributionVideoLayoutMode,
                                coverAspectRatio = spaceFeedCoverAspectRatio,
                                boundedListModifier = boundedListModifier,
                                onClick = { playVideoFromSpace(archive.bvid) },
                                sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(archive.bvid),
                                sharedTransitionScope = lazyGridSharedTransitionScope,
                                animatedVisibilityScope = lazyGridAnimatedVisibilityScope,
                            )
                        }
                        if (!alignArchives) {
                            itemsIndexed(
                                archives,
                                key = { index, _ -> archiveKeys[index] },
                                span = { _, _ -> if (contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID) StaggeredGridItemSpan.SingleLane else StaggeredGridItemSpan.FullLine },
                            ) { index, _ -> renderArchive(index, Modifier) }
                        } else {
                            com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(archives.size, gridColumns)
                                .forEach { row ->
                                    item(key = "space_season_row_${archiveKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                                        com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                            indices = row,
                                            itemKeys = archiveKeys,
                                            columns = gridColumns,
                                            horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            content = renderArchive,
                                        )
                                    }
                                }
                        }
                    }

                    SpaceSubTab.SERIES -> {
                        val series = state.series.firstOrNull { it.meta.series_id == selectedContributionTab.seriesId }
                        val archives = state.seriesArchives[selectedContributionTab.seriesId].orEmpty()
                        item(span = StaggeredGridItemSpan.FullLine) {
                            SpaceCollectionSummaryCard(
                                title = series?.meta?.name ?: selectedContributionTab.title,
                                subtitle = series?.meta?.description.orEmpty(),
                                cover = series?.meta?.cover.orEmpty(),
                                total = series?.meta?.total ?: archives.size,
                                modifier = boundedListModifier,
                                onClick = {
                                    onViewAllClick(
                                        "series",
                                        selectedContributionTab.seriesId,
                                        state.userInfo.mid,
                                        selectedContributionTab.title,
                                        state.userInfo.name
                                    )
                                }
                            )
                        }
                        if (archives.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceSectionEmptyState(
                                    title = "暂无系列内容",
                                    subtitle = "这个系列暂时没有可展示的视频"
                                )
                            }
                        }
                        val archiveKeys = archives.map { "series_video_${it.aid}_${it.bvid}" }
                        val alignArchives = contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID &&
                            gridColumns > 1 && !homeSettings.showFullVideoCardContent
                        val renderArchive: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                            val archive = archives[index]
                            SpaceContributionArchiveVideoItem(
                                video = archive.toSpaceVideoItem(),
                                modifier = cardModifier,
                                layoutMode = contributionVideoLayoutMode,
                                coverAspectRatio = spaceFeedCoverAspectRatio,
                                boundedListModifier = boundedListModifier,
                                onClick = { playVideoFromSpace(archive.bvid) },
                                sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(archive.bvid),
                                sharedTransitionScope = lazyGridSharedTransitionScope,
                                animatedVisibilityScope = lazyGridAnimatedVisibilityScope,
                            )
                        }
                        if (!alignArchives) {
                            itemsIndexed(
                                archives,
                                key = { index, _ -> archiveKeys[index] },
                                span = { _, _ -> if (contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.GRID) StaggeredGridItemSpan.SingleLane else StaggeredGridItemSpan.FullLine },
                            ) { index, _ -> renderArchive(index, Modifier) }
                        } else {
                            com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(archives.size, gridColumns)
                                .forEach { row ->
                                    item(key = "space_series_row_${archiveKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                                        com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                            indices = row,
                                            itemKeys = archiveKeys,
                                            columns = gridColumns,
                                            horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            content = renderArchive,
                                        )
                                    }
                                }
                        }
                    }

                    SpaceSubTab.UGC_SEASON -> {
                        if (state.seasons.isEmpty() && state.series.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceSectionEmptyState("暂无合集或系列", "这个 UP 主还没有公开的合集或系列")
                            }
                        }
                        items(state.seasons, key = { "contribution_season_${it.meta.season_id}" }, span = { StaggeredGridItemSpan.FullLine }) { season ->
                            SpaceCollectionSummaryCard(season.meta.name, season.meta.description, season.meta.cover, season.meta.total,
                                onClick = { onViewAllClick("season", season.meta.season_id, state.userInfo.mid, season.meta.name, state.userInfo.name) })
                        }
                        items(state.series, key = { "contribution_series_${it.meta.series_id}" }, span = { StaggeredGridItemSpan.FullLine }) { series ->
                            SpaceCollectionSummaryCard(series.meta.name, series.meta.description, series.meta.cover, series.meta.total,
                                onClick = { onViewAllClick("series", series.meta.series_id, state.userInfo.mid, series.meta.name, state.userInfo.name) })
                        }
                    }
                    SpaceSubTab.COMIC -> {
                        if (state.isLoadingComics) {
                            item(span = StaggeredGridItemSpan.FullLine) { SpaceLoadingFooter() }
                        } else if (state.comicError != null) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SpaceErrorSection(message = state.comicError, onRetry = onLoadComics)
                            }
                        } else if (state.comics.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) { SpaceSectionEmptyState("暂无漫画", "这个 UP 主还没有公开的漫画") }
                        }
                        items(state.comics, key = { "comic_${it.param}" }) { comic ->
                            SpaceAggregatePosterCard(comic, onClick = { onWebClick("https://manga.bilibili.com/detail/mc${comic.param}", comic.title) })
                        }
                    }
                }
            }

            SpaceMainTab.FAVORITE -> {
                if (state.createdFavoriteFolders.isEmpty() && state.collectedFavoriteFolders.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionEmptyState(
                            title = "暂无收藏夹",
                            subtitle = "该用户还没有公开的收藏夹"
                        )
                    }
                }

                if (state.createdFavoriteFolders.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "创建的收藏夹",
                            count = state.createdFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.createdFavoriteFolders,
                        key = { "created_favorite_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { folder ->
                        SpaceFavoriteFolderRow(
                            folder = folder,
                            modifier = boundedListModifier,
                            onClick = {
                                onViewAllClick(
                                    "favorite",
                                    folder.id,
                                    state.userInfo.mid,
                                    folder.title,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }

                if (state.collectedFavoriteFolders.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "收藏的合集",
                            count = state.collectedFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.collectedFavoriteFolders,
                        key = { "collected_favorite_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { folder ->
                        SpaceFavoriteFolderRow(
                            folder = folder,
                            modifier = boundedListModifier,
                            onClick = {
                                onViewAllClick(
                                    "favorite",
                                    folder.id,
                                    state.userInfo.mid,
                                    folder.title,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }
            }

            SpaceMainTab.BANGUMI -> {
                if (state.bangumiItems.isEmpty() && !state.isLoadingBangumi) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionEmptyState(
                            title = "暂无追番",
                            subtitle = "这个 UP 还没有公开的追番内容"
                        )
                    }
                }
                items(state.bangumiItems, key = { "follow_bangumi_${it.seasonId}" }) { item ->
                    SpaceBangumiCard(
                        item = item,
                        onClick = { onBangumiClick(item.seasonId) }
                    )
                }
                if (state.isLoadingBangumi) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceLoadingFooter()
                    }
                } else if (state.hasMoreBangumi && state.bangumiItems.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        LaunchedEffect(state.bangumiItems.size, isPageActive) { if (isPageActive) onLoadMoreBangumi() }
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            SpaceMainTab.COLLECTIONS -> {
                if (
                    state.seasons.isEmpty() &&
                    state.series.isEmpty() &&
                    state.createdFavoriteFolders.isEmpty() &&
                    state.collectedFavoriteFolders.isEmpty()
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionEmptyState(
                            title = "暂无合集",
                            subtitle = "该用户还没有公开的系列、合集或收藏夹"
                        )
                    }
                }

                if (state.seasons.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "合集",
                            count = state.seasons.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.seasons,
                        key = { "season_${it.meta.season_id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { season ->
                        SpaceCollectionWithPreviewCard(
                            title = season.meta.name,
                            subtitle = season.meta.description,
                            cover = season.meta.cover,
                            total = season.meta.total,
                            previews = state.seasonArchives[season.meta.season_id]
                                .orEmpty()
                                .take(3)
                                .map { PreviewMedia(it.pic, it.title) },
                            onClick = {
                                onViewAllClick(
                                    "season",
                                    season.meta.season_id,
                                    state.userInfo.mid,
                                    season.meta.name,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }

                if (state.series.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "系列",
                            count = state.series.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.series,
                        key = { "series_${it.meta.series_id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { series ->
                        SpaceCollectionWithPreviewCard(
                            title = series.meta.name,
                            subtitle = series.meta.description,
                            cover = series.meta.cover,
                            total = series.meta.total,
                            previews = state.seriesArchives[series.meta.series_id]
                                .orEmpty()
                                .take(3)
                                .map { PreviewMedia(it.pic, it.title) },
                            onClick = {
                                onViewAllClick(
                                    "series",
                                    series.meta.series_id,
                                    state.userInfo.mid,
                                    series.meta.name,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }

                if (state.createdFavoriteFolders.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "创建的收藏夹",
                            count = state.createdFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.createdFavoriteFolders,
                        key = { "collection_created_favorite_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { folder ->
                        SpaceFavoriteFolderRow(
                            folder = folder,
                            modifier = boundedListModifier,
                            onClick = {
                                onViewAllClick(
                                    "favorite",
                                    folder.id,
                                    state.userInfo.mid,
                                    folder.title,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }

                if (state.collectedFavoriteFolders.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SpaceSectionHeader(
                            title = "收藏的合集",
                            count = state.collectedFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.collectedFavoriteFolders,
                        key = { "collection_collected_favorite_${it.id}" },
                        span = { StaggeredGridItemSpan.FullLine }
                    ) { folder ->
                        SpaceFavoriteFolderRow(
                            folder = folder,
                            modifier = boundedListModifier,
                            onClick = {
                                onViewAllClick(
                                    "favorite",
                                    folder.id,
                                    state.userInfo.mid,
                                    folder.title,
                                    state.userInfo.name
                                )
                            }
                        )
                    }
                }
            }

            SpaceMainTab.SHOP -> {
                val page = shopResult?.getOrNull()
                if (page == null) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            AppText(if (shopResult == null) "正在加载小店…" else shopResult?.exceptionOrNull()?.message ?: "小店加载失败")
                            if (shopResult != null) com.android.purebilibili.core.ui.components.AppTextButton(onClick = { shopReload++ }) { AppText("重试") }
                        }
                    }
                } else {
                    items(page.products.size) { index -> SpaceShopCard(page.products[index], onWebClick) }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (page.products.isEmpty()) AppText("小店暂无商品")
                            com.android.purebilibili.core.ui.components.AppTextButton(onClick = { shopReload++ }) { AppText("刷新小店") }
                            page.moreUrl?.let { url ->
                                com.android.purebilibili.core.ui.components.AppButton(onClick = { onWebClick(url, "小店") }) { AppText(page.moreLabel) }
                            }
                        }
                    }
                }
            }
            SpaceMainTab.CHEESE -> {
                if (state.isLoadingCheese && state.cheeseItems.isEmpty()) {
                    // 骨架行与真实行同 span、同 contentType，并各自挂 animateItem：
                    // 切换时只剩「行淡入」这一件事，列表不会被整棵重建。
                    items(
                        count = 6,
                        key = { index -> "space_cheese_skeleton_$index" },
                        span = { StaggeredGridItemSpan.FullLine },
                        contentType = { SPACE_CHEESE_ROW_CONTENT_TYPE },
                    ) {
                        SpaceCheeseSkeletonItem(
                            modifier = boundedListModifier.animateItem()
                        )
                    }
                } else if (state.cheeseItems.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        if (state.lastCheeseLoadFailed) {
                            SpaceErrorSection(
                                message = "加载课堂失败",
                                onRetry = onLoadCheese
                            )
                        } else {
                            SpaceSectionEmptyState(
                                title = "暂无课堂内容",
                                subtitle = "该 UP 还没有公开的课程或付费内容"
                            )
                        }
                    }
                } else {
                    items(
                        items = state.cheeseItems,
                        key = { "space_cheese_${it.seasonId}" },
                        span = { StaggeredGridItemSpan.FullLine },
                        contentType = { SPACE_CHEESE_ROW_CONTENT_TYPE }
                    ) { item ->
                        SpaceCheeseCard(
                            item = item,
                            modifier = boundedListModifier.animateItem(),
                            onClick = {
                                onCheeseClick(item.seasonId)
                            }
                        )
                    }
                    if (state.isLoadingCheese) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            SpaceLoadingFooter()
                        }
                    } else if (state.hasMoreCheese) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            LaunchedEffect(state.cheeseItems.size, isPageActive) { if (isPageActive) onLoadMoreCheese() }
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
        // Short overview/empty tabs must still have enough scroll travel to finish the
        // header-to-cutout handoff. Measure only the missing tail space, not a fixed footer.
        item(key = "space_header_collapse_tail", span = StaggeredGridItemSpan.FullLine) {
            // Keep the derived measurement stable when LazyGrid publishes a new layout result.
            val retainedHeight = remember(gridState) { intArrayOf(0) }
            val tailHeight = remember(
                gridState, headerCollapseRangePx, spaceSkeletonBannerHeightDp, density,
                pinContentTabs, spaceFeedCardLayout.verticalItemSpacingDp,
            ) {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val tail = info.visibleItemsInfo.firstOrNull { it.key == "space_header_collapse_tail" }
                    val requiredTravelPx = if (pinContentTabs) {
                        measuredHeaderHeightPx[0] + with(density) { spaceFeedCardLayout.verticalItemSpacingDp.dp.toPx() }
                    } else maxOf(
                        headerCollapseRangePx,
                        with(density) { spaceSkeletonBannerHeightDp.dp.toPx() },
                    )
                    if (gridState.firstVisibleItemIndex == 0) {
                        if (tail == null) requiredTravelPx.toInt() else {
                            val naturalTailTop = tail.offset.y + gridState.firstVisibleItemScrollOffset
                            (info.viewportEndOffset - info.afterContentPadding + requiredTravelPx - naturalTailTop)
                                .toInt().coerceAtLeast(0)
                        }
                    } else {
                        // Do not remove reserved travel as the header leaves the viewport.
                        retainedHeight[0]
                    }
                }
            }
            Spacer(
                Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val missingHeight = tailHeight.value
                    retainedHeight[0] = missingHeight
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = missingHeight, maxHeight = missingHeight)
                    )
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
            )
        }
    }

        if (tabsPinned) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = chromeTopInset, start = outerPaddingDp, end = outerPaddingDp)
                    .zIndex(1f)
                    .scrollable(
                        state = gridState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                    ),
            ) {
                pinnedTabsContent()
            }
        }

        // [新增] 双指缩放切换网格列数 HUD 胶囊 (自适应 MD3 / MIUIX)
        GridPinchColumnHudPill(
            visible = isPinchPillVisible,
            columns = gridColumns,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .padding(top = chromeTopInset + 16.dp)
        )
        } // 每页内容及吸顶层
        } // HorizontalPager
        } // SpaceMainTabsHost
        } // 小店刷新容器


    }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceHeader(
    userInfo: SpaceUserInfo,
    relationStat: RelationStatData?,
    upStat: UpStatData?,
    collapseFractionProvider: () -> Float,
    bannerScrollOffsetPxProvider: () -> Float = { 0f },
    bannerScrollState: androidx.compose.foundation.gestures.ScrollableState,
    chargeGroup: SpaceSupporterGroup? = null,
    guardGroup: SpaceSupporterGroup? = null,
    onWebClick: (String, String) -> Unit = { _, _ -> },
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onFollowedUsersClick: () -> Unit,
    onUpowerRankClick: (Long, String, Long) -> Unit = { _, _, _ -> },
    onMemberGuardClick: (Long, String, Long) -> Unit = { _, _, _ -> },
    onTopPhotoClick: (Rect?, String?) -> Unit,
    onAvatarClick: (Rect?) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    chromeTopInset: Dp = 0.dp,
    outerPadding: Dp = 16.dp,
    useExpandedLayout: Boolean = false,
    collapseAsUnit: Boolean = false,
    bannerPage: Int = 0,
    onBannerPageChange: (Int) -> Unit = {},
    bannerPageActive: Boolean = true,
    contentPageMoving: Boolean = false,
) {
    val context = LocalContext.current
    val isDarkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val resolvedPhoto = if (isDarkTheme && userInfo.nightTopPhoto.isNotBlank()) {
        userInfo.nightTopPhoto
    } else {
        userInfo.topPhoto
    }
    val topPhotoUrl = normalizeSpaceTopPhotoUrl(resolvedPhoto)
    // banner 组件上报的当前实际显示 URL（装扮头图可能不是 topPhoto），
    // 点击预览时优先用它，保证预览与所见一致。
    var currentBannerUrl by remember { mutableStateOf<String?>(null) }
    val displayAvatar = userInfo.face
    val avatarPreviewEnabled = displayAvatar.isNotBlank()
    val isOwner = userInfo.mid > 0L &&
        userInfo.mid == com.android.purebilibili.core.store.TokenManager.midCache
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val uiSkinState = com.android.purebilibili.core.plugin.skin.LocalUiSkinState.current
    val activeProfileSkin = uiSkinState.activeSkin?.takeIf {
        uiSkinState.enabled &&
            isOwner &&
            com.android.purebilibili.core.plugin.skin.UiSkinSurface.PROFILE in it.manifest.surfaces
    }
    val skinSpaceBackgroundPaths = activeProfileSkin?.manifest?.assets?.spaceBackgrounds.orEmpty()
        .mapNotNull { background ->
            val preferLandscape = windowSizeClass.widthDp > windowSizeClass.heightDp
            activeProfileSkin?.assetFilePath(
                if (preferLandscape) background.landscape ?: background.portrait
                else background.portrait ?: background.landscape
            )
        }
    val followLabel = resolveSpaceFollowActionLabel(
        isOwner = isOwner,
        relationStatus = userInfo.relationStatus,
        isFollowed = userInfo.isFollowed,
    )
    val officialBadge = remember(userInfo.official) {
        resolveOfficialVerifyBadge(
            type = userInfo.official.type,
            title = userInfo.official.spliceTitle.ifBlank { userInfo.official.title },
            desc = userInfo.official.desc
        )
    }
    val metrics = remember(relationStat, upStat) {
        resolveSpaceHeaderMetricItems(
            relationStat = relationStat,
            upStat = upStat
        )
    }
    val colorScheme = MaterialTheme.colorScheme
    val nativeFollowColors = com.android.purebilibili.core.ui.AppInteractionColors.nativeFollow(
        isFollowing = userInfo.isFollowed,
        isOwner = isOwner,
    )
    val followButtonColors = nativeFollowColors?.let { SpaceSelectionChipColors(it.container, it.content) }
        ?: resolveSpaceFollowButtonColors(
            isFollowed = userInfo.isFollowed,
            colorScheme = colorScheme,
            isOwner = isOwner,
        )

    // PiliPlus 风格头部结构：
    // - 窄屏 hero 按 1125:396 全宽展示；桌面/横屏窗口把高度钳到约 135dp，与 PiliPlus kHeaderHeight 对齐
    // - 头像 80dp（顶部 24dp 压在背景图上，底部 56dp 伸出背景，带 2dp 边框与认证标）
    // - 头像右侧独立区域：上层 3 项数据统计（粉丝/关注/获赞），下层私信与关注操作按钮
    // - 窄屏信息区位于头像下方；宽屏放入头像与操作区之间
    val avatarSize = 80.dp
    val avatarBannerOverlap = 20.dp
    val actionsTopMargin = 5.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // The hero is rendered beyond the grid's content padding. Use that exact rendered
        // width for both the banner height and avatar anchor so a wide window cannot create
        // phantom vertical space between them.
        val renderedBannerWidth = maxWidth + outerPadding.coerceAtLeast(0.dp) * 2
        val bannerMetrics = remember(
            renderedBannerWidth,
            windowSizeClass.widthDp,
            windowSizeClass.heightDp,
            chromeTopInset,
        ) {
            resolveSpaceBannerMetrics(
                renderedBannerWidthDp = renderedBannerWidth.value,
                windowWidthDp = windowSizeClass.widthDp.value,
                windowHeightDp = windowSizeClass.heightDp.value,
                topInsetDp = chromeTopInset.value,
            )
        }
        val bannerTotalHeightDp = bannerMetrics.heightDp.dp
        val heroHeight = bannerMetrics.heroHeightDp.dp
        val avatarTopPadding = (heroHeight - avatarBannerOverlap).coerceAtLeast(0.dp)
        val density = LocalDensity.current
        // 视差余量 = 背景图向上溢出窗口的量（chromeTopInset）。平移钳在该范围内，
        // 图片永远不会滑出窗口顶部露底；效果为背景以约 1/4 速率跟随滚动。
        val maxBannerParallaxTranslationPx = with(LocalDensity.current) {
            chromeTopInset.coerceAtLeast(0.dp).roundToPx().toFloat()
        }
        // Keep scroll reads in graphics layers so the identity/actions don't recompose per pixel.

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapseAsUnit) Modifier
                        .drawWithContent {
                            clipRect(
                                left = -outerPadding.toPx(),
                                top = -chromeTopInset.toPx(),
                                right = size.width + outerPadding.toPx(),
                                bottom = size.height,
                            ) { this@drawWithContent.drawContent() }
                        }
                        .graphicsLayer {
                            val scroll = bannerScrollOffsetPxProvider().coerceAtMost(size.height)
                            translationY = scroll * 0.75f
                            alpha = 1f - resolveSpaceHeaderClosingFraction(
                                remainingHeightPx = size.height - scroll,
                                toolbarHeightPx = 56.dp.toPx(),
                            )
                        }
                    else Modifier
                )
                .background(MaterialTheme.colorScheme.surface)
        ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 背景 hero（突破内边距全宽延伸至屏幕顶端，按标准比例完整呈现）
            val topPhotoRect = rememberImagePreviewSourceRect()
            val topPhotoHidden = isImagePreviewSourceHidden(topPhotoRect.value)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // graphicsLayer 置于 bounds 修饰符之前，让预览回位框把视差位移算进去。
                    .graphicsLayer {
                        translationY = if (collapseAsUnit) 0f else (bannerScrollOffsetPxProvider() * 0.75f)
                            .coerceIn(0f, maxBannerParallaxTranslationPx)
                    }
                    .layout { measurable, constraints ->
                        val horizontalInsetPx = outerPadding.coerceAtLeast(0.dp).roundToPx()
                        val topInsetPx = chromeTopInset.coerceAtLeast(0.dp).roundToPx()
                        val targetWidth = constraints.maxWidth + horizontalInsetPx * 2
                        val bannerTotalHeightPx = bannerTotalHeightDp.roundToPx()
                        val visibleHeightPx = heroHeight.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = targetWidth,
                                maxWidth = targetWidth,
                                minHeight = bannerTotalHeightPx,
                                maxHeight = bannerTotalHeightPx
                            )
                        )
                        layout(constraints.maxWidth, visibleHeightPx) {
                            placeable.placeRelative(-horizontalInsetPx, -topInsetPx)
                        }
                    }
                    .align(Alignment.TopCenter)
            ) {
                SpaceBannerFusionEffect(
                    fusionEnabled = !collapseAsUnit,
                    collapseFractionProvider = {
                        val rangePx = maxBannerParallaxTranslationPx + with(density) { heroHeight.toPx() }
                        (bannerScrollOffsetPxProvider() / rangePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    },
                    scrollOffsetPxProvider = bannerScrollOffsetPxProvider,
                    maxParallaxTranslationPx = maxBannerParallaxTranslationPx,
                    scrollState = bannerScrollState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .imagePreviewSourceBounds(topPhotoRect)
                            .alpha(if (topPhotoHidden) 0f else 1f)
                            .appActionClickable(
                                interactionSource = null,
                                indication = null,
                                enabled = skinSpaceBackgroundPaths.isEmpty() &&
                                    (shouldEnableSpaceTopPhotoPreview(topPhotoUrl) || userInfo.topImages.isNotEmpty()),
                                onClick = { onTopPhotoClick(topPhotoRect.value, currentBannerUrl) },
                            )
                    ) {
                        SpaceHeaderBanner(
                            topImages = userInfo.topImages,
                            fallbackTopPhotoUrl = topPhotoUrl,
                            onCurrentBannerUrlChange = { currentBannerUrl = it },
                            skinBackgroundPaths = skinSpaceBackgroundPaths,
                            isDarkTheme = isDarkTheme,
                            sharedPage = bannerPage,
                            onPageChange = onBannerPageChange,
                            pageActive = bannerPageActive,
                            contentPageMoving = contentPageMoving,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chromeTopInset.coerceAtLeast(48.dp))
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.18f),
                                        1.0f to Color.Transparent
                                    )
                                )
                        )
                    }
                }
            }

            // PiliPlus 风格头部结构：
            // 头像靠左（80dp，顶部 24dp 压在背景图上），右侧为两行区域（上行 3 项数据，下行私信+关注操作按钮）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - collapseFractionProvider() }
                    .padding(top = avatarTopPadding, start = 4.dp, end = 0.dp),
                verticalAlignment = Alignment.Top
            ) {
                val avatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = com.android.purebilibili.core.ui.transition.avatarSharedElementKey(userInfo.mid)),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(CircleShape)
                        )
                    }
                } else {
                    Modifier
                }

                val avatarRect = rememberImagePreviewSourceRect()
                val avatarHidden = isImagePreviewSourceHidden(avatarRect.value)
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .imagePreviewSourceBounds(avatarRect)
                        .alpha(if (avatarHidden) 0f else 1f)
                        .appActionClickable(
                            interactionSource = null,
                            indication = null,
                            enabled = avatarPreviewEnabled && !avatarHidden,
                        ) { onAvatarClick(avatarRect.value) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(if (displayAvatar.startsWith("file://") || displayAvatar.startsWith("content://")) displayAvatar else FormatUtils.buildSizedImageUrl(displayAvatar, width = 320, height = 320))
                            .memoryCacheKey(resolveImagePreviewPlaceholderCacheKey(displayAvatar) ?: displayAvatar)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(avatarModifier)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )

                    UserAvatarCornerMarkBadge(
                        mark = resolveUserAvatarCornerMark(
                            officialType = userInfo.official.type,
                            vipStatus = userInfo.vip.status,
                        ),
                        modifier = Modifier.align(Alignment.BottomEnd),
                        badgeSize = 20.dp,
                    )

                    val liveRoom = userInfo.liveRoom
                    val liveRoomId = liveRoom?.roomId?.takeIf { it > 0L }
                        ?: liveRoom?.url?.substringBefore('?')?.trimEnd('/')
                            ?.substringAfterLast('/')?.toLongOrNull()
                    if (liveRoom?.liveStatus == 1 && liveRoomId != null && liveRoomId > 0L) {
                        val headerVisible by remember(collapseFractionProvider) {
                            derivedStateOf { collapseFractionProvider() < 1f }
                        }
                        // 头像预览、隐藏的 Pager 页与完全折叠的头部不运行音柱。
                        // 音柱组件另行统一控制视口、窗口焦点、前台生命周期与减少动态效果。
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .clip(CircleShape)
                                .appActionClickable(enabled = !avatarHidden) {
                                    onLiveClick(
                                        liveRoomId,
                                        liveRoom.title.ifBlank { userInfo.name },
                                        userInfo.name,
                                    )
                                }
                                .padding(7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.android.purebilibili.core.ui.components.PlaybackBarsIndicator(
                                isPlaying = bannerPageActive && !contentPageMoving &&
                                    headerVisible && !avatarHidden && !topPhotoHidden,
                                color = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = "正在直播，点击进入直播间",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                if (useExpandedLayout) {
                    SpaceHeaderIdentityInfo(
                        userInfo = userInfo,
                        officialBadge = officialBadge,
                        onFollowedUsersClick = onFollowedUsersClick,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = 1f - collapseFractionProvider() }
                            .padding(top = avatarBannerOverlap),
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // 右侧操作区：上层数据统计，下层关注/私信按钮
                Column(
                    modifier = Modifier
                        .weight(if (useExpandedLayout) 0.8f else 1f, fill = !useExpandedLayout)
                        .widthIn(max = 480.dp)
                        .padding(top = (avatarBannerOverlap + actionsTopMargin).coerceAtLeast(0.dp)),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        metrics.forEachIndexed { index, metric ->
                            SpaceHeaderStat(
                                label = metric.label,
                                value = metric.value,
                                modifier = Modifier.weight(1f),
                                onClick = when (metric.label) {
                                    "粉丝" -> onFansClick
                                    "关注" -> onFollowingClick
                                    else -> null
                                },
                            )
                            if (index < metrics.lastIndex) {
                                SpaceHeaderMetricDivider()
                            }
                        }
                    }

                    SpaceHeaderRelationActions(
                        followLabel = followLabel,
                        isFollowed = userInfo.isFollowed,
                        followButtonColors = followButtonColors,
                        onMessageClick = onMessageClick,
                        onFollowClick = rememberFollowActionWithConfirmation(
                            targetKey = userInfo.mid,
                            userName = userInfo.name,
                            isFollowing = userInfo.isFollowed && !isOwner,
                            onFollowClick = {
                                onFollowClick()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        isOwner = isOwner,
                    )
                }
            }
        }

        if (!useExpandedLayout) {
            SpaceHeaderIdentityInfo(
                userInfo = userInfo,
                officialBadge = officialBadge,
                onFollowedUsersClick = onFollowedUsersClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - collapseFractionProvider() }
                    .padding(start = 4.dp, end = 0.dp, top = 10.dp, bottom = 8.dp),
            )
        }

        // 充电/大航海统计行（对齐 PiliPlus _buildChargeAndGuard）
        SpaceChargeGuardRow(
            chargeGroup = chargeGroup,
            guardGroup = guardGroup,
            onUpowerRankClick = {
                onUpowerRankClick(userInfo.mid, userInfo.name, chargeGroup?.count ?: 0L)
            },
            onMemberGuardClick = {
                onMemberGuardClick(userInfo.mid, userInfo.name, guardGroup?.count ?: 0L)
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - collapseFractionProvider() }
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
        )
        }
    }
}

@Composable
private fun SpaceHeaderIdentityInfo(
    userInfo: SpaceUserInfo,
    officialBadge: OfficialVerifyBadgeSpec?,
    onFollowedUsersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 信息区：名字 + 等级 + VIP 标识。
    // Row 的 weight 要传给 SelectionContainer 的顶层布局，不能只挂在其内部 Column。
    SelectionContainer(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppText(
                    text = userInfo.name,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .copyOnLongPress(userInfo.name, "UP主名称"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = resolveUpNameColor(
                        vipStatus = userInfo.vip.status,
                        vipType = userInfo.vip.type,
                        onSurface = AppContentColors.primaryText(),
                        secondary = com.android.purebilibili.core.ui.components.userVipPink(),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                UserLevelBadge(level = userInfo.level)
                if (userInfo.vip.status == 1) {
                    com.android.purebilibili.core.ui.components.UserVipBadge(
                        label = com.android.purebilibili.core.ui.components
                            .resolveUserVipBadgeLabel(
                                label = userInfo.vip.label.text,
                                vipType = userInfo.vip.type,
                            ),
                        compact = true,
                    )
                }
            }

            if (officialBadge != null) {
                Spacer(modifier = Modifier.height(10.dp))
                SpaceOfficialTag(badge = officialBadge)
            }

            if (userInfo.sign.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = userInfo.sign.trim(),
                    modifier = Modifier.copyOnLongPress(userInfo.sign, "UP主简介"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.primaryText(),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val displayTags = remember(userInfo.spaceTags, userInfo.ipLocation) {
                resolveSpaceDisplayTags(userInfo.spaceTags, userInfo.ipLocation)
            }
            if (userInfo.mid > 0L || displayTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (userInfo.mid > 0L) {
                        AppText(
                            text = "UID: ${userInfo.mid}",
                            modifier = Modifier.copyOnLongPress(userInfo.mid.toString(), "UID"),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                    displayTags.forEach { tag ->
                        val hasUri = tag.uri.isNotBlank()
                        val tagModifier = if (hasUri) {
                            Modifier
                                .appActionClickable {
                                    runCatching { uriHandler.openUri(tag.uri) }
                                }
                                .copyOnLongPress(tag.title, tag.title)
                        } else {
                            Modifier.copyOnLongPress(tag.title, tag.title)
                        }
                        AppText(
                            text = tag.title,
                            modifier = tagModifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasUri) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                AppContentColors.secondaryText().copy(alpha = 0.82f)
                            }
                        )
                    }
                }
            }

            userInfo.followingsFollowed?.let { followedUp ->
                if (followedUp.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SpaceFollowedUpSection(followedUp = followedUp, onClick = onFollowedUsersClick)
                }
            }

            if (userInfo.silence == 1) {
                Spacer(modifier = Modifier.height(8.dp))
                SpaceBanBanner()
            }
        }
    }
}

@Composable
private fun SpaceSearchEntryChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (label.isBlank()) return
    // Keep the entry capsule on the same Pill geometry as the space/home dock.
    // Using the Field token here made the search entry visibly flatter than the
    // segmented dock immediately above it.
    val shape = resolveSharedBottomBarCapsuleShape()
    AppSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = AppContentColors.secondaryText(),
                modifier = Modifier.size(18.dp)
            )
            AppText(
                text = label,
                color = AppContentColors.secondaryText(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SpaceContentTabs(
    state: SpaceUiState.Success,
    onMainTabSelected: (SpaceMainTab) -> Unit,
    onContributionTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    page: Int,
    onMainTabReselected: (() -> Unit)? = null,
    mainTabsSlot: (@Composable () -> Unit)? = null,
) {
    val selectedMainTab = state.tabShellState.selectedTab
    val hasCheese = state.hasCheeseTab || state.cheeseItems.isNotEmpty()
    val displayedMainTabs = remember(state.mainTabs, selectedMainTab, hasCheese) {
        resolveSpaceDisplayedMainTabs(
            tabs = state.mainTabs,
            selectedTab = selectedMainTab,
            hasCheese = hasCheese,
        )
    }
    val displayedContributionTabs = remember(state.contributionTabs, state.totalAudios) {
        resolveDisplayedSpaceContributionTabs(
            tabs = state.contributionTabs,
            totalAudios = state.totalAudios
        )
    }
    val secondarySwitchItems = remember(displayedContributionTabs) {
        resolveSpaceSecondarySwitchItems(
            contributionTabs = displayedContributionTabs,
            hasCheese = false,
        )
    }
    val selectedSecondarySwitchId = remember(
        selectedMainTab,
        state.selectedContributionTabId,
    ) {
        resolveSelectedSpaceSecondarySwitchId(
            selectedTab = selectedMainTab,
            selectedContributionTabId = state.selectedContributionTabId,
        )
    }
    val onSecondarySwitchSelected: (String) -> Unit = { id ->
        val item = secondarySwitchItems.firstOrNull { it.id == id }
        if (item != null) {
            when (item.targetTab) {
                SpaceMainTab.CONTRIBUTION -> {
                    onMainTabSelected(SpaceMainTab.CONTRIBUTION)
                    item.contributionTabId?.let(onContributionTabSelected)
                }
                else -> onMainTabSelected(item.targetTab)
            }
        }
    }
    Column(modifier = modifier) {
        if (mainTabsSlot != null) mainTabsSlot() else SpaceMainTabRow(
            tabs = displayedMainTabs,
            selectedTab = resolveSpacePrimaryTab(selectedMainTab),
            onSelect = { tab ->
                if (tab == resolveSpacePrimaryTab(selectedMainTab) && onMainTabReselected != null) {
                    onMainTabReselected()
                } else onMainTabSelected(tab)
            },
            modifier = Modifier.stationaryPagerHeader(pagerState, page),
            indicatorPositionProvider = { pagerState.tabIndicatorPosition },
            isScrollInProgressProvider = { pagerState.isScrollInProgress },
        )
        if (shouldShowSpaceSecondarySwitch(selectedMainTab) && secondarySwitchItems.isNotEmpty()) {
            SpaceSecondarySwitchRow(
                items = secondarySwitchItems,
                selectedId = selectedSecondarySwitchId,
                onSelect = onSecondarySwitchSelected,
            )
        }
    }
}

@Composable
private fun SpaceSecondarySwitchRow(
    items: List<SpaceSecondarySwitchItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiStyle = LocalAppUiStyle.current
    val spec = remember(items, selectedId, uiStyle) {
        resolveSpaceSecondarySwitchChromeSpec(
            items = items,
            selectedId = selectedId,
            uiStyle = uiStyle,
        )
    }
    val themeConfig = LocalAppThemeConfig.current
    // Contribution categories keep the same content-sized Miuix buttons with
    // either glass preference. Scope the override to this row only.
    val tabThemeConfig = if (uiStyle == AppUiStyle.MIUIX) {
        themeConfig.copy(liquidGlassEnabled = false, popupGlassEnabled = false, playerGlassEnabled = false)
    } else {
        themeConfig
    }
    CompositionLocalProvider(LocalAppThemeConfig provides tabThemeConfig) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spec.horizontalPaddingDp.dp, vertical = 6.dp),
        ) {
            AppNativeTabRow(
                options = items.map { AppSegmentOption(it.id, it.title) },
                selectedValue = selectedId,
                onSelectionChange = onSelect,
                modifier = Modifier.fillMaxWidth(),
                scrollable = shouldScrollSpaceSecondarySwitchForNonGlass(items.size),
                minTabWidth = resolveSpaceSecondarySwitchNonGlassMinTabWidthDp().dp,
                indicatorPresentation = AppTabRowIndicatorPresentation.TONAL_PILL,
                compactMiuixWhenTwoOptions = false,
                // Let the shared renderer size each Miuix item from its own label;
                // long labels remain fully visible inside the horizontal rail.
                allowLabelOverflow = true,
                miuixNonGlassItemWidthMode = MiuixNonGlassTabItemWidthMode.CONTENT,
                contentSizedMiuixNonGlassMaxItemWidth = Dp.Infinity,
                // 投稿分类按各项实际宽度横向定位；共享组件默认仍只保证选中项可见。
                centerSelectedTab = true,
            )
        }
    }
}

private val SpaceMainTabTopPadding = 6.dp
private val SpaceMainTabBottomPadding = 2.dp

@Composable
private fun SpaceMainTabRow(
    tabs: List<SpaceMainTabItem>,
    selectedTab: SpaceMainTab,
    onSelect: (SpaceMainTab) -> Unit,
    modifier: Modifier = Modifier,
    indicatorPositionProvider: () -> Float,
    isScrollInProgressProvider: () -> Boolean,
    isPagerDraggingProvider: (() -> Boolean)? = null,
    pagerTargetPositionProvider: (() -> Float)? = null,
) {
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val miuixNonGlass = com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()
    val spec = remember(tabs, selectedTab) {
        resolveSpaceMainTabChromeSpec(tabs = tabs, selectedTab = selectedTab)
    }
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SpaceMainTabTopPadding, bottom = SpaceMainTabBottomPadding)
    ) {
        AppThemeAdaptiveTabRow(
            // Glass indicator fills the measured slot; native geometry keeps its defaults.
            liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
            role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
            options = tabs.map { AppSegmentOption(it.tab, it.title) },
            selectedValue = tabs[selectedIndex].tab,
            onSelectionChange = onSelect,
            indicatorPositionProvider = indicatorPositionProvider,
            isScrollInProgressProvider = isScrollInProgressProvider,
            // 仅玻璃主栏提供分页交互身份；原生 MD3 / Miuix 保留既有动画路径。
            isPagerDraggingProvider = if (liquidGlassEnabled) isPagerDraggingProvider else null,
            pagerTargetPositionProvider = if (liquidGlassEnabled) pagerTargetPositionProvider else null,
            // 玻璃保留独立间距；非玻璃 Miuix 主导航放得下时均匀居中，
            // 空间不足时仍按完整文字宽度滚动。
            centerContent = liquidGlassEnabled || miuixNonGlass,
            forceEqualWidth = miuixNonGlass,
            minTabWidth = if (liquidGlassEnabled) 64.dp else androidx.compose.ui.unit.Dp.Unspecified,
            scrollable = spec.scrollable,
            dragSelectionEnabled = spec.dragSelectionEnabled,
            tapPressRefractionEnabled = true,
            height = spec.heightDp.dp,
            indicatorHeight = spec.indicatorHeightDp.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spec.horizontalPaddingDp.dp),
        )
    }
}

@Composable
private fun SpaceContributionVideoSummaryBar(
    totalCount: Int,
    currentOrder: VideoSortOrder,
    onSelectSortOrder: (VideoSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = "共${totalCount}视频",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText()
        )
        Spacer(modifier = Modifier.weight(1f))

        var sortMenuExpanded by remember { mutableStateOf(false) }
        com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { sortMenuExpanded = true }) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .appActionClickable { sortMenuExpanded = true }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppIcon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = "排序",
                    modifier = Modifier.size(16.dp),
                    tint = AppContentColors.secondaryText()
                )
                AppText(
                    text = resolveSpaceVideoSortCompactLabel(currentOrder) + "发布",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }
            val glassSortMenu = com.android.purebilibili.core.ui.components.AppGlassMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
                title = "视频排序",
            ) {
                Column {
                    VideoSortOrder.entries.forEach { order ->
                        com.android.purebilibili.core.ui.components.AppDropdownMenuItem(
                            text = { AppText(order.displayName) },
                            selected = order == currentOrder,
                            onClick = { onSelectSortOrder(order); sortMenuExpanded = false },
                        )
                    }
                }
            }
            if (!glassSortMenu) DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                VideoSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { AppText(order.displayName) },
                        onClick = {
                            onSelectSortOrder(order)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceChargeGuardRow(
    chargeGroup: SpaceSupporterGroup?,
    guardGroup: SpaceSupporterGroup?,
    onUpowerRankClick: () -> Unit,
    onMemberGuardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chargeGroup == null && guardGroup == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        chargeGroup?.let { group ->
            Row(
                modifier = Modifier.appActionClickable(onClick = onUpowerRankClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpaceSupporterAvatarStack(avatarUrls = group.avatarUrls)
                AppText(
                    text = "${FormatUtils.formatStat(group.count)}人为TA充电",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
                AppIcon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        guardGroup?.let { group ->
            Row(
                modifier = Modifier.appActionClickable(onClick = onMemberGuardClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpaceSupporterAvatarStack(avatarUrls = group.avatarUrls)
                AppText(
                    text = "${FormatUtils.formatStat(group.count)}人加入大航海",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }
        }
    }
}

@Composable
private fun SpaceSupporterAvatarStack(avatarUrls: List<String>) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        avatarUrls.take(3).forEachIndexed { index, url ->
            Box(
                modifier = Modifier
                    .offset(x = (-6 * index).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(FormatUtils.buildSizedImageUrl(url, width = 72, height = 72))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SpaceSectionHeader(
    title: String,
    count: Int,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = "查看更多"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = AppContentColors.primaryText()
        )
        Spacer(modifier = Modifier.width(8.dp))
        AppText(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = AppContentColors.secondaryText()
        )
        Spacer(modifier = Modifier.weight(1f))
        if (onActionClick != null && !actionLabel.isNullOrBlank()) {
            AppTextButton(onClick = onActionClick) {
                AppText(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.spaceVideoCoverSharedBounds(
    sharedTransitionKey: String? = null,
    coverShape: androidx.compose.ui.graphics.Shape,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
): Modifier {
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val cardSharedTransitionMotionSpec = remember(
        sourceRoute,
        sharedTransitionKey,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = sharedTransitionKey != null,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val sharedTransitionReady = sharedTransitionKey != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    if (!sharedTransitionReady) return this
    return with(requireNotNull(sharedTransitionScope)) {
        this@spaceVideoCoverSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = videoCoverSharedElementKey(
                    bvid = requireNotNull(sharedTransitionKey),
                    sourceRoute = sourceRoute
                )
            ),
            animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
            boundsTransform = { initialBounds, targetBounds ->
                if (cardSharedTransitionMotionSpec.enabled) {
                    videoSharedElementBoundsTransformSpec(
                        motion = cardSharedTransitionMotionSpec,
                        initialBounds = initialBounds,
                        targetBounds = targetBounds
                    )
                } else {
                    com.android.purebilibili.core.ui.motion.AppMotionTokens.spatialSpec()
                }
            },
            resizeMode = com.android.purebilibili.core.ui.transition
                .resolveVideoCardSharedBoundsResizeMode(),
            clipInOverlayDuringTransition = OverlayClip(coverShape)
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceHomeVideoCard(
    video: SpaceVideoItem,
    progressState: VideoProgressDisplayState,
    badgeLabel: String? = null,
    isLocateHighlight: Boolean = false,
    coverAspectRatio: Float = 16f / 9f,
    onClick: () -> Unit,
    sharedTransitionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sponsorStatusLabel = rememberSponsorFeedBadgeLabel(video.bvid)
    val locateHighlightColor by animateColorAsState(
        targetValue = if (isLocateHighlight) {
            AppContentColors.accent()
        } else {
            Color.Transparent
        },
        animationSpec = tween(120),
        label = "space-video-locate-highlight"
    )
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val densityValue = density.density
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(
        sharedTransitionKey ?: video.pic,
    )
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val stationaryCoverUrl = remember(video.pic) {
        FormatUtils.buildSizedImageUrl(video.pic, width = 640, height = 360)
    }
    val stationaryCoverRequest = remember(stationaryCoverUrl) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val cardCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Card).value.roundToInt()
    val coverShape = AppShapes.borderedMediaCover()
    val coverOverlayTextStyle = remember {
        androidx.compose.ui.text.TextStyle(
            shadow = resolveVideoCardCoverOverlayTextShadow()
        )
    }
    val sharedTransitionReady = sharedTransitionKey != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val cardSharedTransitionMotionSpec = remember(
        sourceRoute,
        sharedTransitionKey,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = sharedTransitionReady,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedTransitionReady
    )
    val coverModifier = if (useCardShellSharedBounds) {
        Modifier
    } else {
        Modifier.spaceVideoCoverSharedBounds(
            sharedTransitionKey = sharedTransitionKey,
            coverShape = coverShape,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    Column(
        modifier = modifier
            .videoCardSourceAnchor(sharedTransitionKey.orEmpty(), coverShape)
            .border(width = 3.dp, color = locateHighlightColor, shape = coverShape)
            .then(nativeCardSnapshot.modifier)
            .clip(coverShape)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .appActionClickable spaceCardClick@{
                if (CardPositionManager.isVideoNavigationDebounceActive()) {
                    // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
                    return@spaceCardClick
                }
                cardBounds?.let { bounds ->
                    CardPositionManager.recordVideoCardPosition(
                        bvid = sharedTransitionKey.orEmpty(),
                        sourceRoute = sourceRoute,
                        bounds = bounds,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx,
                        density = densityValue,
                        sourceCornerDp = cardCornerRadiusDp,
                        coverBounds = coverBounds,
                        sourceLayout = VideoCardSourceLayout.STACKED,
                        sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                            title = video.title,
                            ownerName = video.author,
                            ownerFaceUrl = "",
                            viewText = FormatUtils.formatStat(video.play.toLong()),
                            danmakuText = FormatUtils.formatStat(video.comment.toLong()),
                            durationText = video.length,
                            infoPresentation = com.android.purebilibili.core.ui.transition
                                .resolveVideoCardSourceInfoPresentation(
                                    publishTimeText = FormatUtils.formatPublishTime(video.created),
                                    showStatsInInfo = true,
                                ),
                            coverUrl = stationaryCoverUrl,
                            coverCacheKey = stationaryCoverUrl,
                        ).withMeasuredCoverDecodeSize(coverBounds),
                    )
                    nativeCardSnapshot.capture()
                }
                onClick()
            }
    ) {
        Box(
            modifier = coverModifier
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .fillMaxWidth()
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = stationaryCoverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(nativeCardSnapshot.coverOverlayModifier),
            ) {
            VideoStatusBadges(
                labels = video.statusLabels + listOfNotNull(sponsorStatusLabel),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            if (!badgeLabel.isNullOrBlank()) {
                AppSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = AppContentColors.accent()
                ) {
                    AppText(
                        text = badgeLabel,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AppContentColors.onAccent()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MediaContrastPalette.Scrim.copy(alpha = 0.30f),
                                MediaContrastPalette.Scrim.copy(alpha = 0.78f),
                            )
                        )
                    )
            )

            if (video.length.isNotBlank()) {
                AppText(
                    text = video.length,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.merge(coverOverlayTextStyle),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                )
            }

            if (progressState.showProgressBar) {
                AppLinearProgressIndicator(
                    progress = { progressState.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AppContentColors.accent(),
                    trackColor = Color.White.copy(alpha = 0.28f)
                )
            }
            }
        }

        Column(
            modifier = Modifier.videoCardShellReturnChromeAlpha(
                enabled = useCardShellSharedBounds,
                bvid = sharedTransitionKey.orEmpty(),
                sourceRoute = sourceRoute,
            )
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = video.title,
                style = feedContentTypography().title.copy(
                    color = AppContentColors.primaryText()
                ),
                minLines = 2,
                maxLines = videoCardTitleMaxLines(),
                overflow = videoCardTitleOverflow()
            )
            val metadata = remember(video.created, progressState.progressSec) {
                buildList {
                    if (video.created > 0L) add(FormatUtils.formatPublishTime(video.created))
                    if (progressState.progressSec == -1) add("已看完")
                }.joinToString(" · ")
            }
            if (metadata.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    overflow = TextOverflow.Visible
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalVideoStatRow(
                playText = FormatUtils.formatStat(video.play.toLong()),
                danmakuText = FormatUtils.formatStat(video.comment.toLong()),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceAggregateMediaCard(
    item: SpaceAggregateArchiveItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val sponsorStatusLabel = rememberSponsorFeedBadgeLabel(item.bvid)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val densityValue = density.density
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(
        sharedTransitionKey ?: item.cover,
    )
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val stationaryCoverUrl = remember(item.cover) {
        FormatUtils.buildSizedImageUrl(item.cover, width = 640, height = 360)
    }
    val stationaryCoverRequest = remember(stationaryCoverUrl) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val coverShape = AppShapes.mediaCover()
    val cardCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Card).value.roundToInt()
    val coverModifier = Modifier.spaceVideoCoverSharedBounds(
        sharedTransitionKey = sharedTransitionKey,
        coverShape = coverShape,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .videoCardSourceAnchor(sharedTransitionKey.orEmpty(), coverShape)
            .then(nativeCardSnapshot.modifier)
            .clip(coverShape)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .appActionClickable spaceCardClick@{
                if (CardPositionManager.isVideoNavigationDebounceActive()) {
                    // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
                    return@spaceCardClick
                }
                cardBounds?.let { bounds ->
                    CardPositionManager.recordVideoCardPosition(
                        bvid = sharedTransitionKey.orEmpty(),
                        sourceRoute = sourceRoute,
                        bounds = bounds,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx,
                        density = densityValue,
                        sourceCornerDp = cardCornerRadiusDp,
                        coverBounds = coverBounds,
                        sourceLayout = VideoCardSourceLayout.STACKED,
                        sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                            title = item.title,
                            ownerName = item.author,
                            ownerFaceUrl = "",
                            viewText = FormatUtils.formatStat(item.play.toLong()),
                            danmakuText = FormatUtils.formatStat(item.danmaku.toLong()),
                            durationText = item.length,
                            infoPresentation = com.android.purebilibili.core.ui.transition
                                .resolveVideoCardSourceInfoPresentation(
                                    publishTimeText = "",
                                    showStatsInInfo = true,
                                ),
                            coverUrl = stationaryCoverUrl,
                            coverCacheKey = stationaryCoverUrl,
                        ).withMeasuredCoverDecodeSize(coverBounds),
                    )
                    nativeCardSnapshot.capture()
                }
                onClick()
            }
    ) {
        Box(
            modifier = coverModifier
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .fillMaxWidth()
                .height(118.dp)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = stationaryCoverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            VideoStatusBadges(
                labels = item.statusLabels + listOfNotNull(sponsorStatusLabel),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            if (item.length.isNotBlank()) {
                VideoCardCoverDurationText(
                    text = item.length,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
            color = AppContentColors.primaryText()
        )
    }
}

@Composable
private fun SpaceAggregatePosterCard(
    item: SpaceAggregateArchiveItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .appActionClickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.buildSizedImageUrl(item.cover, width = 480, height = 720))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow()
        )
        if (item.subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                overflow = TextOverflow.Visible
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceTopVideoCard(
    video: SpaceTopArcData,
    onClick: () -> Unit,
    sharedTransitionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    // 与单列投稿同款横向视频卡：封面时长角标 + "置顶" badge，不再卡片套卡片
    SpaceArchiveListItemRow(
        title = video.title,
        cover = video.pic,
        duration = FormatUtils.formatDuration(video.duration),
        publishTime = video.reason.ifBlank { FormatUtils.formatPublishTime(video.pubdate) },
        play = video.stat.view,
        secondaryCount = video.stat.danmaku,
        statusLabels = listOfNotNull(rememberSponsorFeedBadgeLabel(video.bvid)),
        badgeLabel = "置顶",
        showTrailingMore = false,
        onClick = onClick,
        sharedTransitionKey = sharedTransitionKey,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@Composable
private fun SpaceNoticeCard(notice: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(14.dp)
    ) {
        AppText(
            text = "公告",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SelectionContainer {
            AppText(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText()
            )
        }
    }
}

private fun contributionLayoutToggleAction(
    layoutMode: SpaceContributionVideoLayoutMode,
    onToggle: () -> Unit,
): AppWindowAction {
    val isSingleColumn = layoutMode == SpaceContributionVideoLayoutMode.SINGLE_COLUMN
    return AppWindowAction(
        label = if (isSingleColumn) "切换为双列" else "切换为单列",
        icon = if (isSingleColumn) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda,
        onClick = onToggle,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceContributionArchiveVideoItem(
    video: SpaceVideoItem,
    modifier: Modifier = Modifier,
    layoutMode: SpaceContributionVideoLayoutMode,
    coverAspectRatio: Float,
    boundedListModifier: Modifier,
    onClick: () -> Unit,
    sharedTransitionKey: String?,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    when (layoutMode) {
        SpaceContributionVideoLayoutMode.GRID -> {
            SpaceHomeVideoCard(
                video = video,
                modifier = modifier,
                progressState = resolveSpaceVideoProgressState(
                    video = video,
                    localPositionMs = 0L,
                    syncedProgress = null,
                ),
                coverAspectRatio = coverAspectRatio,
                onClick = onClick,
                sharedTransitionKey = sharedTransitionKey,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
        SpaceContributionVideoLayoutMode.SINGLE_COLUMN -> {
            SpaceArchiveListItemRow(
                title = video.title,
                cover = video.pic,
                duration = video.length,
                statusLabels = video.statusLabels +
                    listOfNotNull(rememberSponsorFeedBadgeLabel(video.bvid)),
                publishTime = FormatUtils.formatPublishTime(video.created),
                play = video.play.toLong(),
                secondaryCount = video.comment.toLong(),
                modifier = boundedListModifier,
                onClick = onClick,
                sharedTransitionKey = sharedTransitionKey,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

private fun SeasonArchiveItem.toSpaceVideoItem(): SpaceVideoItem = SpaceVideoItem(
    aid = aid,
    bvid = bvid,
    title = title,
    pic = pic,
    play = stat.view.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    comment = stat.reply.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    length = FormatUtils.formatDuration(duration),
    created = pubdate,
    author = author,
)

private fun SeriesArchiveItem.toSpaceVideoItem(): SpaceVideoItem = SpaceVideoItem(
    aid = aid,
    bvid = bvid,
    title = title,
    pic = pic,
    play = stat.view.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    comment = stat.reply.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    length = FormatUtils.formatDuration(duration),
    created = pubdate,
    author = author,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceArchiveListItemRow(
    title: String,
    cover: String,
    duration: String,
    publishTime: String,
    play: Long,
    secondaryCount: Long,
    progressState: VideoProgressDisplayState? = null,
    badgeLabel: String? = null,
    statusLabels: List<String> = emptyList(),
    isLocateHighlight: Boolean = false,
    showTrailingMore: Boolean = true,
    onClick: () -> Unit,
    sharedTransitionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locateHighlightColor by animateColorAsState(
        targetValue = if (isLocateHighlight) {
            AppContentColors.accent()
        } else {
            Color.Transparent
        },
        animationSpec = tween(120),
        label = "space-video-locate-highlight"
    )
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val densityValue = density.density
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val stationaryCoverUrl = remember(cover) {
        FormatUtils.buildSizedImageUrl(cover, width = 560, height = 350)
    }
    val stationaryCoverRequest = remember(stationaryCoverUrl) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val cardSharedTransitionMotionSpec = remember(
        sourceRoute,
        sharedTransitionKey,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = sharedTransitionKey != null,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val cardCornerDp = AppShapes.containerCornerDp(ContainerLevel.Card)
    val cardCornerRadiusDp = cardCornerDp.value.roundToInt()
    val sharedTransitionReady = sharedTransitionKey != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedTransitionReady
    )
    val cardShellShape = AppShapes.container(ContainerLevel.Card)
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(
        sharedTransitionKey ?: title,
    )

    HorizontalVideoCardFrame(
        horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .videoCardSourceAnchor(sharedTransitionKey.orEmpty(), cardShellShape)
            .then(nativeCardSnapshot.modifier)
            .clip(cardShellShape)
            .background(AppSurfaceTokens.cardContainer())
            .border(width = 3.dp, color = locateHighlightColor, shape = cardShellShape)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .appActionClickable spaceCardClick@{
                if (CardPositionManager.isVideoNavigationDebounceActive()) {
                    // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
                    return@spaceCardClick
                }
                cardBounds?.let { bounds ->
                    CardPositionManager.recordVideoCardPosition(
                        bvid = sharedTransitionKey.orEmpty(),
                        sourceRoute = sourceRoute,
                        bounds = bounds,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx,
                        density = densityValue,
                        sourceCornerDp = cardCornerRadiusDp,
                        coverBounds = coverBounds,
                        sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                        sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                            title = title,
                            ownerName = "",
                            ownerFaceUrl = "",
                            viewText = FormatUtils.formatStat(play),
                            danmakuText = FormatUtils.formatStat(secondaryCount),
                            durationText = duration,
                            infoPresentation = com.android.purebilibili.core.ui.transition
                                .resolveVideoCardSourceInfoPresentation(
                                    publishTimeText = publishTime,
                                    showStatsInInfo = true,
                                    showOverflowMenu = true,
                                ),
                            coverPresentation = VideoCardSourceCoverPresentation(
                                showDurationOnCover = duration.isNotBlank(),
                                premiumBadgeText = badgeLabel.orEmpty(),
                                showHistoryProgressBar = progressState?.showProgressBar == true,
                                historyProgressFraction = progressState?.progressFraction ?: 0f,
                            ),
                            coverUrl = stationaryCoverUrl,
                            coverCacheKey = stationaryCoverUrl,
                        ).withMeasuredCoverDecodeSize(coverBounds),
                    )
                    nativeCardSnapshot.capture()
                }
                onClick()
            },
        coverModifier = Modifier
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                },
        coverContent = {
            AsyncImage(
                model = stationaryCoverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        },
        coverOverlayModifier = nativeCardSnapshot.coverOverlayModifier,
        coverOverlayContent = {
            VideoStatusBadges(
                labels = statusLabels,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            if (!badgeLabel.isNullOrBlank()) {
                AppSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = AppContentColors.accent()
                ) {
                    AppText(
                        text = badgeLabel,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().statistic.copy(fontWeight = FontWeight.SemiBold),
                        color = AppContentColors.onAccent()
                    )
                }
            }
            if (duration.isNotBlank()) {
                VideoCardCoverDurationText(
                    text = duration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
            if (progressState?.showProgressBar == true) {
                AppLinearProgressIndicator(
                    progress = { progressState.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AppContentColors.accent(),
                    trackColor = Color.White.copy(alpha = 0.28f)
                )
            }
        },
        infoModifier = Modifier.videoCardShellReturnChromeAlpha(
            enabled = useCardShellSharedBounds,
            bvid = sharedTransitionKey.orEmpty(),
            sourceRoute = sourceRoute,
        ),
        infoContent = {
            AppText(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().title,
                maxLines = videoCardTitleMaxLines(),
                overflow = videoCardTitleOverflow(),
                color = AppContentColors.primaryText()
            )
        },
        supportingContent = {
            AppText(
                text = if (progressState?.progressSec == -1) "$publishTime · 已看完" else publishTime,
                style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().author,
                color = AppContentColors.secondaryText()
            )
            HorizontalVideoStatRow(
                playText = FormatUtils.formatStat(play),
                danmakuText = FormatUtils.formatStat(secondaryCount),
            )
        },
        trailingContent = if (showTrailingMore) {
            {
                AppIcon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = AppContentColors.secondaryText().copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(24.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun SpaceAudioListItem(
    audio: SpaceAudioItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .appActionClickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.buildSizedImageUrl(audio.cover, width = 256, height = 256))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = audio.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            // song/upper 对他人空间的播放统计常为 0：显示「0播放」是噪音，
            // 只有时长；有真实播放数时才带前缀。
            val audioPlayCount =
                (audio.statistic?.play ?: audio.play_count.toLong()).coerceAtLeast(0L)
            AppText(
                text = if (audioPlayCount > 0L) {
                    "${FormatUtils.formatStat(audioPlayCount)}播放 · ${FormatUtils.formatDuration(audio.duration)}"
                } else {
                    FormatUtils.formatDuration(audio.duration)
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )
        }
        AppIcon(
            imageVector = Icons.Outlined.PlayCircleOutline,
            contentDescription = null,
            tint = AppContentColors.accent(),
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun dispatchSpaceArticleClick(
    article: SpaceArticleItem,
    onDynamicDetailClick: (String) -> Unit,
    onArticleClick: (Long, String) -> Unit
) {
    when (val action = resolveSpaceArticleClickAction(article)) {
        is SpaceDynamicClickAction.OpenDynamicDetail -> onDynamicDetailClick(action.dynamicId)
        is SpaceDynamicClickAction.OpenArticle -> onArticleClick(action.articleId, action.title)
        else -> Unit
    }
}

@Composable
private fun SpaceArticleListItem(
    article: SpaceArticleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = when (val action = resolveSpaceArticleClickAction(article)) {
        is SpaceDynamicClickAction.OpenArticle -> com.android.purebilibili.navigation3.BiliPaiNavKey.ArticleDetail(action.articleId, action.title)
        is SpaceDynamicClickAction.OpenDynamicDetail -> com.android.purebilibili.navigation3.BiliPaiNavKey.DynamicDetail(action.dynamicId)
        else -> null
    }
    val container = target?.let { com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(it, onClick) }
    val openCard = container?.onClick ?: onClick
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(container?.modifier ?: Modifier)
            .clip(AppShapes.container(ContainerLevel.Card))
            .appActionClickable { openCard() }
            .padding(vertical = 6.dp)
    ) {
        RichTextContent(
            desc = remember(article.title) { DynamicDesc(text = article.title) },
            onUserClick = {},
            onBlankTap = openCard,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.SemiBold,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val imageUrls = article.displayImageUrls(com.android.purebilibili.core.ui.isAppInDarkTheme())
        if (imageUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = imageUrls.take(3),
                    key = { it }
                ) { imageUrl ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(FormatUtils.buildSizedImageUrl(imageUrl, width = 480, height = 320))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(140.dp)
                            .height(92.dp)
                            .clip(AppShapes.container(ContainerLevel.Card))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        AppText(
            text = buildSpaceArticleStatsText(article),
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText()
        )
        AppHorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun SpaceFavoriteFolderRow(
    folder: FavFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = folder.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                AppText(
                    text = "${folder.media_count} 个视频",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }
            AppText(
                text = "查看",
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.accent()
            )
        }
    }
}

@Composable
private fun SpaceBangumiCard(
    item: FollowBangumiItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .appActionClickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(
                        FormatUtils.buildSizedImageUrl(
                            item.cover.ifBlank { item.squareCover },
                            width = 480,
                            height = 720
                        )
                    )
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow()
        )
        Spacer(modifier = Modifier.height(4.dp))
        AppText(
            text = item.newEp?.indexShow?.ifBlank { item.progress }.orEmpty().ifBlank { item.evaluate },
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpaceCheeseCard(
    item: SpaceCheeseItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .height(72.dp)
                    .clip(AppShapes.mediaCover())
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.buildSizedImageUrl(item.cover, width = 480, height = 300))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (!item.marks.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        AppText(
                            text = item.marks.orEmpty().joinToString(" · "),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                AppText(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.status.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = item.status.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val ctimeFormatted = remember(item.ctime) {
                    item.ctime?.toLongOrNull()?.let {
                        FormatUtils.formatPublishTime(it)
                    }
                }
                if (!ctimeFormatted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = "更新于 $ctimeFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceCheeseSkeletonItem(
    modifier: Modifier = Modifier,
) {
    // 固定色块而非呼吸脉冲：呼吸灰块叠上「行淡入」会被看成闪烁。
    val blockColor = rememberStaticSkeletonBlockColor()
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContentSkeletonBlock(
                modifier = Modifier
                    .width(116.dp)
                    .height(72.dp),
                shape = AppShapes.mediaCover(),
                color = blockColor,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                ContentSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(18.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = blockColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ContentSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = blockColor,
                )
                Spacer(modifier = Modifier.height(6.dp))
                ContentSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = blockColor,
                )
            }
        }
    }
}

@Composable
private fun SpaceCollectionSummaryCard(
    title: String,
    subtitle: String,
    cover: String,
    total: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .height(72.dp)
                    .clip(AppShapes.container(ContainerLevel.Card))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.buildSizedImageUrl(cover, width = 480, height = 300))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (total != null) {
                    AppText(text = "$total 个内容", style = MaterialTheme.typography.bodySmall, color = AppContentColors.accent())
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private data class PreviewMedia(
    val cover: String,
    val title: String
)

@Composable
private fun SpaceCollectionWithPreviewCard(
    title: String,
    subtitle: String,
    cover: String,
    total: Int,
    previews: List<PreviewMedia>,
    onClick: () -> Unit
) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(116.dp)
                        .height(72.dp)
                        .clip(AppShapes.container(ContainerLevel.Card))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(FormatUtils.buildSizedImageUrl(cover, width = 480, height = 300))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = "$total 个内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.accent()
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AppText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (previews.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(previews, key = { "${it.cover}_${it.title}" }) { preview ->
                        Column(modifier = Modifier.width(112.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(FormatUtils.buildSizedImageUrl(preview.cover, width = 320, height = 200))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .clip(AppShapes.container(ContainerLevel.Card))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AppText(
                                text = preview.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceOfficialTag(
    badge: OfficialVerifyBadgeSpec,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Pill),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSurface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = when (badge.tone) {
                        OfficialVerifyBadgeTone.PERSONAL -> Color(0xFFFFCC00)
                        OfficialVerifyBadgeTone.ORGANIZATION -> Color(0xFF40C4FF)
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            AppText(
                text = badge.text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AppContentColors.secondaryText(),
            )
        }
    }
}

@Composable
private fun SpaceBadgeChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)? = null
) {
    AppSurface(
        modifier = if (onClick != null) Modifier.appActionClickable { onClick() } else Modifier,
        shape = AppShapes.container(ContainerLevel.Pill),
        color = containerColor
    ) {
        AppText(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor
        )
    }
}

@Composable
private fun SpaceHeaderRelationActions(
    followLabel: String,
    isFollowed: Boolean,
    followButtonColors: SpaceSelectionChipColors,
    onMessageClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOwner: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isOwner) {
            AppSurface(
                onClick = onMessageClick,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .width(46.dp)
                    .height(36.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = "私信",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AppSurface(
            onClick = onFollowClick,
            shape = RoundedCornerShape(18.dp),
            color = followButtonColors.backgroundColor,
            border = if (isFollowed && !isOwner) {
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            } else null,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .then(if (isOwner) Modifier else Modifier.followActionAnchor())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isFollowed && !isOwner) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = null,
                        tint = followButtonColors.textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                AppText(
                    text = followLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = followButtonColors.textColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpaceHeaderStat(
    label: String,
    value: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = if (onClick != null) appActionWithFeedback(onClick) else ({}),
                onLongClick = {
                    android.widget.Toast.makeText(
                        context,
                        "$label: $value",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = FormatUtils.formatStat(value),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = AppContentColors.primaryText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        AppText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpaceHeaderMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(15.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    )
}

@Composable
private fun rememberSpaceBannerPager(
    pageCount: Int,
    sharedPage: Int,
    pageActive: Boolean,
    onPageChange: (Int) -> Unit,
): PagerState {
    val safePage = sharedPage.coerceIn(0, pageCount - 1)
    val pager = rememberPagerState(initialPage = safePage) { pageCount }
    val latestOnPageChange = androidx.compose.runtime.rememberUpdatedState(onPageChange)
    LaunchedEffect(pager, safePage, pageActive) {
        if (!pageActive && pager.currentPage != safePage) pager.scrollToPage(safePage)
    }
    LaunchedEffect(pager, pageActive) {
        if (!pageActive) return@LaunchedEffect
        var first = true
        snapshotFlow { pager.currentPage }.collect { page ->
            if (first) first = false else latestOnPageChange.value(page)
        }
    }
    return pager
}

@Composable
private fun SpaceHeaderBanner(
    topImages: List<com.android.purebilibili.data.model.response.SpaceTopImageItem>,
    fallbackTopPhotoUrl: String,
    onCurrentBannerUrlChange: (String?) -> Unit = {},
    skinBackgroundPaths: List<String> = emptyList(),
    isDarkTheme: Boolean,
    sharedPage: Int,
    onPageChange: (Int) -> Unit,
    pageActive: Boolean,
    contentPageMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 与 PiliPlus 一致：所有背景图统一做亮/暗色调色，保证顶栏与头像在任意封面上可读。
    val bannerColorFilter = resolveSpaceBannerColorFilter(isLight = !isDarkTheme)
    if (skinBackgroundPaths.isNotEmpty()) {
        LaunchedEffect(skinBackgroundPaths) { onCurrentBannerUrlChange(null) }
        val pagerState = rememberSpaceBannerPager(skinBackgroundPaths.size, sharedPage, pageActive, onPageChange)
        Box(modifier = modifier) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(skinBackgroundPaths[page])
                        .crossfade(true)
                        .buildForProfileFusion(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (skinBackgroundPaths.size > 1) {
                AppLinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1f) / skinBackgroundPaths.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .align(Alignment.BottomCenter),
                    color = AppContentColors.accent(),
                    trackColor = MediaContrastPalette.Track,
                )
            }
        }
    } else if (topImages.size > 1) {
        val pagerState = rememberSpaceBannerPager(topImages.size, sharedPage, pageActive, onPageChange)
        LaunchedEffect(pagerState.currentPage, topImages) {
            onCurrentBannerUrlChange(topImages.getOrNull(pagerState.currentPage)?.header)
        }
        // 多张头图自动轮播：用户拖动期间暂停，松手后重新计时
        val bannerDragging by pagerState.interactionSource.collectIsDraggedAsState()
        val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
        LaunchedEffect(pagerState, contentPageMoving) {
            if (contentPageMoving && pageActive) {
                pagerState.scrollToPage(sharedPage.coerceIn(0, topImages.lastIndex))
            }
        }
        LaunchedEffect(pagerState, bannerDragging, topImages.size, pageActive, contentPageMoving, animationGate.active) {
            if (bannerDragging || !pageActive || contentPageMoving || !animationGate.active) return@LaunchedEffect
            while (true) {
                delay(SPACE_BANNER_AUTO_ROTATE_INTERVAL_MS)
                val nextPage = (pagerState.currentPage + 1) % topImages.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
        Box(modifier = modifier.then(animationGate.modifier)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = topImages[page]
                val alignment = resolveSpaceBannerAlignment(item.dy)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.header)
                        .memoryCacheKey(resolveImagePreviewPlaceholderCacheKey(item.header) ?: item.header)
                        .crossfade(false)
                        .buildForProfileFusion(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = alignment,
                    colorFilter = bannerColorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val currentTitle = topImages.getOrNull(pagerState.currentPage)?.title
            if (currentTitle != null && currentTitle.title.isNotBlank()) {
                SpaceHeaderTitleBadge(
                    title = currentTitle,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp)
                )
            }

            AppLinearProgressIndicator(
                progress = { (pagerState.currentPage + 1f) / topImages.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .align(Alignment.BottomCenter),
                color = AppContentColors.accent(),
                trackColor = MediaContrastPalette.Track
            )
        }
    } else if (topImages.size == 1) {
        val item = topImages[0]
        LaunchedEffect(item.header) { onCurrentBannerUrlChange(item.header) }
        val alignment = resolveSpaceBannerAlignment(item.dy)
        Box(modifier = modifier) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.header)
                    .memoryCacheKey(resolveImagePreviewPlaceholderCacheKey(item.header) ?: item.header)
                    .crossfade(false)
                    .buildForProfileFusion(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                colorFilter = bannerColorFilter,
                modifier = Modifier.fillMaxSize()
            )
            if (item.title != null && requireNotNull(item.title).title.isNotBlank()) {
                val checkedItemTitle = requireNotNull(item.title)
                SpaceHeaderTitleBadge(
                    title = checkedItemTitle,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp)
                )
            }
        }
    } else if (fallbackTopPhotoUrl.isNotBlank()) {
        LaunchedEffect(fallbackTopPhotoUrl) { onCurrentBannerUrlChange(fallbackTopPhotoUrl) }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(fallbackTopPhotoUrl)
                .memoryCacheKey(
                    resolveImagePreviewPlaceholderCacheKey(fallbackTopPhotoUrl) ?: fallbackTopPhotoUrl
                )
                .crossfade(false)
                .buildForProfileFusion(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            colorFilter = bannerColorFilter,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
        )
    }
}

@Composable
private fun SpaceHeaderTitleBadge(
    title: com.android.purebilibili.data.model.response.SpaceCollectionTopTitle,
    modifier: Modifier = Modifier,
) {
    val subTitleColor = remember(title.subTitleColorFormat) {
        val colorHex = title.subTitleColorFormat?.colors?.lastOrNull()
        if (!colorHex.isNullOrBlank()) {
            try {
                val hex = colorHex.removePrefix("#")
                if (hex.length == 6) {
                    Color(hex.toLong(16) or 0xFF000000)
                } else if (hex.length == 8) {
                    Color(hex.toLong(16))
                } else Color.White
            } catch (_: Exception) {
                Color.White
            }
        } else {
            Color.White
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 140.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.12f),
                        Color.Black.copy(alpha = 0.38f),
                        Color.Black.copy(alpha = 0.45f),
                    )
                )
            )
            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            AppText(
                text = title.title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (title.subTitle.isNotBlank()) {
                AppText(
                    text = title.subTitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = subTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpaceFollowedUpSection(
    followedUp: com.android.purebilibili.data.model.response.SpaceFollowingsFollowedUpper,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = followedUp.items
    if (items.isEmpty()) return
    val displayUsers = items.take(3)
    val moreCount = items.size
    val namesText = displayUsers.joinToString("、") { it.name }
    val suffixText = if (items.size > 3) "等${moreCount}人也关注了TA" else "也关注了TA"

    Row(
        modifier = modifier.fillMaxWidth().appActionClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-6).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            displayUsers.forEach { user ->
                AsyncImage(
                    model = FormatUtils.buildSizedImageUrl(user.face, 64, 64),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        AppText(
            text = namesText,
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        AppText(
            text = suffixText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
        AppIcon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SpaceBanBanner(
    modifier: Modifier = Modifier
) {
    AppSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIcon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
            AppText(
                text = "该账号封禁中",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SpaceLoadingFooter() {
    val footerVertical = if (isMiuixNonGlassEnabled()) {
        AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall
    } else {
        18.dp
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = footerVertical),
        contentAlignment = Alignment.Center
    ) {
        AdaptiveLoadingIndicator(size = 24.dp)
    }
}

@Composable
private fun SpaceSectionEmptyState(
    title: String,
    subtitle: String
) {
    val horizontal = if (isMiuixNonGlassEnabled()) AppSpacingTokens.ExtraLarge else 24.dp
    val vertical = if (isMiuixNonGlassEnabled()) {
        AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Large
    } else {
        42.dp
    }
    val titleGap = if (isMiuixNonGlassEnabled()) AppSpacingTokens.Small else 8.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = vertical),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AppContentColors.secondaryText()
        )
        Spacer(modifier = Modifier.height(titleGap))
        AppText(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = AppContentColors.secondaryText()
        )
    }
}

@Composable
private fun SpaceErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    val horizontal = if (isMiuixNonGlassEnabled()) AppSpacingTokens.ExtraLarge else 24.dp
    val vertical = if (isMiuixNonGlassEnabled()) {
        AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Large
    } else {
        42.dp
    }
    val actionGap = if (isMiuixNonGlassEnabled()) AppSpacingTokens.Medium else 12.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = vertical),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = AppContentColors.secondaryText()
        )
        Spacer(modifier = Modifier.height(actionGap))
        AppButton(onClick = onRetry) {
            AppText("重试")
        }
    }
}

@Composable
private fun SpaceErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = AppContentColors.secondaryText()
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppButton(onClick = onRetry) {
                AppText("重试")
            }
        }
    }
}

private fun handleAggregateArchiveClick(
    item: SpaceAggregateArchiveItem,
    onVideoClick: (String) -> Unit,
    onAudioClick: (Long) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit
) {
    val videoId = resolveSpaceAggregateVideoId(item)
    when {
        item.goto.equals("comic", ignoreCase = true) -> onWebClick("https://manga.bilibili.com/detail/mc${item.param}", item.title)
        videoId != null -> onVideoClick(videoId)
        item.goto.contains("bangumi", ignoreCase = true) ||
            item.isPgc ||
            item.coverIcon.contains("bangumi", ignoreCase = true) -> {
            item.param.toLongOrNull()?.takeIf { it > 0L }?.let(onBangumiClick)
        }
        item.goto.contains("audio", ignoreCase = true) -> {
            item.param.toLongOrNull()?.takeIf { it > 0L }?.let(onAudioClick)
        }
        item.uri.isNotBlank() -> onWebClick(item.uri, item.title)
    }
}

// [重构] 空间页 header 折叠滚动范围约 320dp（具体像素在 SpaceContent 内按 density 换算）


