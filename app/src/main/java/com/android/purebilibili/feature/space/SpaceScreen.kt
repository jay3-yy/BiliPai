package com.android.purebilibili.feature.space

import com.android.purebilibili.core.ui.components.rememberFollowActionWithConfirmation
import com.android.purebilibili.core.ui.components.VideoStatusBadges
import com.android.purebilibili.core.ui.AppContentColors

import android.os.Build
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.core.ui.components.AnimatedVideoListItem
import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.android.purebilibili.core.ui.common.contentTabPagerGestures
import com.android.purebilibili.core.ui.common.rememberContentTabPager
import com.android.purebilibili.core.ui.common.stationaryPagerHeader
import androidx.compose.foundation.pager.PagerState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.android.purebilibili.core.ui.components.liquidDockViewport
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.IntOffset
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
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
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
    onLiveClick: (Long, String, String) -> Unit = { _, _, _ -> },
    onUserClick: (Long) -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onPlayAllAudioClick: ((String, Long) -> Unit)? = null,
    onDynamicDetailClick: (String) -> Unit = {},
    onArticleClick: (Long, String) -> Unit = { _, _ -> },
    onViewAllClick: (String, Long, Long, String, String) -> Unit = { _, _, _, _, _ -> },
    onLikedVideosClick: ((Long, String) -> Unit)? = null,
    onMessageClick: (Long, String, String) -> Unit = { _, _, _ -> },
    onFollowingClick: (Long) -> Unit = {},
    onFansClick: (Long) -> Unit = {},
    onUpowerRankClick: ((Long, String, Long) -> Unit)? = null,
    onMemberGuardClick: ((Long, String, Long) -> Unit)? = null,
    viewModel: SpaceViewModel = viewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    contentTransitionReady: Boolean = true,
) {
    val context = LocalContext.current
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
    val isBlocked by blockedUpRepository.isBlocked(mid).collectAsStateWithLifecycle(initialValue = false)
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
    val hazeState = if (
        spaceThemeConfig.headerBlurEnabled &&
            shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
            !isLowBlurBudgetForced()
    ) rememberRecoverableHazeState() else null
    val pageGridStates = SpaceMainTab.entries.associateWith { rememberLazyGridState() }
    val activePrimaryTab =
        (uiState as? SpaceUiState.Success)?.tabShellState?.selectedTab ?: SpaceMainTab.HOME
    val gridState = pageGridStates.getValue(activePrimaryTab)
    val isSpaceScrolling by remember(gridState) {
        derivedStateOf { gridState.isScrollInProgress }
    }
    val pinnedTopChromeScrim by remember(gridState) {
        derivedStateOf {
            resolveSpacePinnedTopChromeScrim(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
        }
    }
    // Glass-only compact browsing chrome. Observe user gestures rather than item offsets:
    // sticky-header relayout and programmatic search/locate scrolling must not toggle the bar.
    var spaceTopBarExpanded by remember(mid, gridState, spaceThemeConfig.liquidGlassEnabled) {
        mutableStateOf(true)
    }
    val spaceHeaderVisible by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex == 0 }
    }
    val chromeDirectionThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val spaceChromeScrollConnection = remember(
        gridState, spaceThemeConfig.liquidGlassEnabled, chromeDirectionThresholdPx
    ) {
        object : NestedScrollConnection {
            private var directionalTravel = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!spaceThemeConfig.liquidGlassEnabled || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                if (directionalTravel * dy < 0f) directionalTravel = 0f
                directionalTravel += dy
                if (gridState.firstVisibleItemIndex == 0) {
                    spaceTopBarExpanded = true
                    directionalTravel = 0f
                } else if (kotlin.math.abs(directionalTravel) >= chromeDirectionThresholdPx) {
                    spaceTopBarExpanded = directionalTravel > 0f
                    directionalTravel = 0f
                }
                return Offset.Zero
            }
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

    LaunchedEffect(mid) {
        viewModel.loadSpaceInfo(mid)
    }

    val currentSuccessState = uiState as? SpaceUiState.Success
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
    val playAllSpaceVideos: () -> Unit = playAll@{
        val state = currentSuccessState ?: return@playAll
        val contribution = resolveSelectedContributionTab(
            tabs = state.contributionTabs,
            selectedTabId = state.selectedContributionTabId,
            selectedSubTab = state.selectedSubTab,
        )
        val videos = when (contribution.subTab) {
            SpaceSubTab.SEASON_VIDEO -> state.seasonArchives[contribution.seasonId]
                .orEmpty().map { it.toSpaceVideoItem() }
            SpaceSubTab.SERIES -> state.seriesArchives[contribution.seriesId]
                .orEmpty().map { it.toSpaceVideoItem() }
            else -> state.videos
        }
        val startBvid = resolveSpacePlayAllStartTarget(videos) ?: return@playAll
        val playlist = buildExternalPlaylistFromSpaceVideos(
            videos = videos,
            clickedBvid = startBvid
        ) ?: return@playAll
        com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
            playlist.playlistItems,
            playlist.startIndex,
            source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.SPACE
        )
        val playbackTarget = resolveSpacePlaybackTarget(
            syncedProgress = state.watchProgressByBvid[startBvid],
            localPositionMs = videoProgressLookup(startBvid)
        )
        com.android.purebilibili.feature.video.player.PlaylistManager
            .setPlayMode(com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL)
        onPlayAllAudioClick?.invoke(startBvid, playbackTarget.resumePositionMs)
            ?: onVideoClick(startBvid, playbackTarget.cid, playbackTarget.resumePositionMs)
    }
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
    AppScaffold(
        modifier = Modifier.nestedScroll(spaceChromeScrollConnection),
        topBar = {
            BiliPaiImmersiveTopBar(
                backdrop = spaceChromeBackdrop,
                enabled = spaceProgressiveBlur,
                headerBlurActive = spaceHeaderBlurActive,
                surfaceColor = com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.surface),
                fadeEnabled = spaceFadeActive,
                opaqueBackgroundFallback = false,
                modifier = Modifier.background(
                    if (spaceProgressiveBlur || spaceHeaderBlurActive || spaceFadeActive) Color.Transparent
                    else com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.surface)
                        .copy(alpha = pinnedTopChromeScrim)
                ),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (spaceThemeConfig.liquidGlassEnabled) Modifier.statusBarsPadding() else Modifier)
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
                AnimatedVisibility(
                    visible = !spaceThemeConfig.liquidGlassEnabled ||
                        spaceTopBarExpanded || spaceHeaderVisible || isSearchMode,
                    enter = if (com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current) {
                        expandVertically(expandFrom = Alignment.Top) + fadeIn()
                    } else androidx.compose.animation.EnterTransition.None,
                    exit = if (com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current) {
                        shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    } else androidx.compose.animation.ExitTransition.None,
                ) {
                    AppTopBar(
                        title = if (pinnedTopChromeScrim > 0.4f) screenTitle else "",
                        navigationIcon = {
                            CompositionLocalProvider(LocalAppTopBarIconBackdrop provides spaceChromeBackdrop) {
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
                                CompositionLocalProvider(LocalAppTopBarIconBackdrop provides spaceChromeBackdrop) {
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
                                                        AppWindowAction(
                                                            label = "播放全部",
                                                            icon = Icons.Outlined.PlayCircleOutline,
                                                            onClick = playAllSpaceVideos,
                                                        ),
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
                                            add(
                                                listOf(
                                                    AppWindowAction(
                                                        label = "举报",
                                                        icon = Icons.Outlined.VisibilityOff,
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
            searchBarRevealScrollOffsetPx
        ) {
            if (!isSearchMode) return@LaunchedEffect
            resolveSpaceSearchBarGridItemIndex(
                scope = currentSearchScope,
                hasContributionToolbar = hasContributionToolbarForSearch
            )?.let { searchBarIndex ->
                gridState.animateScrollToItem(
                    index = searchBarIndex,
                    scrollOffset = searchBarRevealScrollOffsetPx
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = if (uiState is SpaceUiState.Loading) 1f else contentRevealAlpha.value
        }) {
                // Loading 不再单独渲染一棵骨架网格：它和 Success 共用同一个 SpaceContent
                // 调用点（同一棵 LazyVerticalGrid）。Loading 时喂一个占位 Success 并把
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
                            onPlayAllAudioClick = onPlayAllAudioClick,
                            onDynamicDetailClick = onDynamicDetailClick,
                            onArticleClick = onArticleClick,
                            onViewAllClick = onViewAllClick,
                            onLikedVideosClick = onLikedVideosClick,
                            onMainTabSelected = viewModel::selectMainTab,
                            onContributionTabSelected = viewModel::selectContributionTab,
                            onCategorySelected = viewModel::selectCategory,
                            onSelectSortOrder = viewModel::selectSortOrder,
                            contributionVideoLayoutMode = contributionVideoLayoutMode,
                            onPlayAllVideos = playAllSpaceVideos,
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
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSearchEntryClick = { viewModel.setSearchMode(true) },
                            onLocateTargetConsumed = viewModel::consumePendingLocateBvid,
                            onLocateTargetMissing = viewModel::reportPendingLocateBvidMissing,
                            onLocateTargetLoadFailed = viewModel::reportPendingLocateBvidLoadFailed,
                            onFollowClick = viewModel::toggleFollow,
                            onMessageClick = {
                                val user = state.userInfo
                                onMessageClick(user.mid, user.name, user.face)
                            },
                            onFollowingClick = { onFollowingClick(state.userInfo.mid) },
                            onFansClick = { onFansClick(state.userInfo.mid) },
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
            onDismiss = { repostDynamicId = null },
            onRepost = { content: String, alsoComment: Boolean, onComplete: (Boolean) -> Unit ->
                dynamicInteractionViewModel.repostDynamic(dynamicId, content, alsoComment) { success, message ->
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
                                        .clickable { viewModel.toggleFollowGroupSelection(tag.tagid) }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceContent(
    state: SpaceUiState.Success,
    gridState: LazyGridState,
    pageGridStates: Map<SpaceMainTab, LazyGridState>,
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
    onPlayAllAudioClick: ((String, Long) -> Unit)?,
    onDynamicDetailClick: (String) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onViewAllClick: (String, Long, Long, String, String) -> Unit,
    onLikedVideosClick: ((Long, String) -> Unit)? = null,
    onMainTabSelected: (SpaceMainTab) -> Unit,
    onContributionTabSelected: (String) -> Unit,
    onCategorySelected: (Int) -> Unit,
    onSelectSortOrder: (VideoSortOrder) -> Unit = {},
    contributionVideoLayoutMode: SpaceContributionVideoLayoutMode,
    onPlayAllVideos: () -> Unit = {},
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
    onSearchQueryChange: (String) -> Unit,
    onSearchEntryClick: () -> Unit,
    onLocateTargetConsumed: (String) -> Unit,
    onLocateTargetMissing: (String) -> Unit,
    onLocateTargetLoadFailed: (String) -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
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
    /**
     * 首屏加载中：在同一个内容网格里铺骨架 item，而不是另起一棵独立的骨架网格。
     * 骨架与真实卡片共用同一个 LazyVerticalGrid / gridState，数据到达时是 item
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
    val selectedMainTab = state.tabShellState.selectedTab
    val singleGlassPrimaryRail = LocalAppThemeConfig.current.liquidGlassEnabled
    // Glass owns one drag/press lifecycle outside the pager. Per-page native rails keep
    // their existing sticky layout and animation; only glass pages reserve an empty slot.
    var glassPrimaryRailHeightPx by remember { mutableStateOf(0) }
    val displayedMainTabs = resolveSpaceDisplayedMainTabs(
        tabs = state.mainTabs,
        selectedTab = selectedMainTab,
        hasCheese = state.hasCheeseTab || state.cheeseItems.isNotEmpty(),
    )
    val selectedPage = displayedMainTabs.indexOfFirst {
        it.tab == selectedMainTab
    }.coerceAtLeast(0)
    val pagerState = rememberContentTabPager(
        pageCount = displayedMainTabs.size,
        selectedPage = selectedPage,
        onPageSelected = { page ->
            displayedMainTabs.getOrNull(page)?.tab?.let { tab ->
                if (tab != selectedMainTab) onMainTabSelected(tab)
            }
        },
        prepareNeighbors = { sourcePage ->
            val source = pageGridStates.getValue(displayedMainTabs[sourcePage].tab)
            // All pages share the hero and primary rail. Match their vertical anchor before
            // exposing a neighbor; content history is kept when the hero is already collapsed.
            displayedMainTabs.forEachIndexed { index, tab ->
                if (index != sourcePage) {
                    val target = pageGridStates.getValue(tab.tab)
                    if (source.firstVisibleItemIndex == 0) {
                        target.requestScrollToItem(0, source.firstVisibleItemScrollOffset)
                    } else if (target.firstVisibleItemIndex == 0) {
                        target.requestScrollToItem(1)
                    }
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
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
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
        currentSearchScope
    ) {
        if (selectedMainTab != SpaceMainTab.CONTRIBUTION) {
            0
        } else {
            1 +
                (if (shouldShowSpaceSearchEntry(currentSearchScope, state.isSearchMode)) 1 else 0) +
                (if (state.isSearchMode && currentSearchScope == SpaceSearchScope.VIDEO) 1 else 0)
        }
    }
    LaunchedEffect(
        state.pendingLocateBvid,
        selectedMainTab,
        selectedContributionTab,
        contributionVideoItemStartIndex,
        state.videos,
        state.hasMoreVideos,
        state.videoPageLoadCompletionVersion,
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

            gridState.animateScrollToItem(contributionVideoItemStartIndex + targetVideoIndex)
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

    // 吸顶 Tab 行在窗口根坐标系中的底边，用于把投稿悬浮工具条 dock 在它正下方
    // （推算 chromeTopInset+高度会双算/漏算 chrome，导致悬浮条压到封面上）。
    // 声明须在根 Box 之前：其 onGloballyPositioned 回调要写入这些状态。
    var pinnedTabsRootBottomPx by remember { mutableStateOf(0f) }
    // 网格容器在根坐标系中的顶边（悬浮条的父容器），用于换算相对 padding。
    var gridContainerRootTopPx by remember { mutableStateOf(0f) }

    // 遮挡铰链时整页内容落入最大安全区，软折痕允许跨越（与首页/动态/搜索一致）。
    com.android.purebilibili.core.ui.adaptive.AppHingeSafeContent(
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .responsiveContentWidth(maxWidth = adaptiveLayoutSpec.contentMaxWidthDp.dp)
            .then(modifier)
            .onGloballyPositioned { gridContainerRootTopPx = it.boundsInRoot().top }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            key = { displayedMainTabs[it].tab },
            modifier = Modifier.fillMaxSize().contentTabPagerGestures(
                pager = pagerState,
                enabled = !isInitialLoading,
                contentTopPx = {
                    if (pinnedTabsRootBottomPx > 0f) pinnedTabsRootBottomPx - gridContainerRootTopPx
                    else Float.POSITIVE_INFINITY
                },
            ),
        ) { page ->
        val primaryTab = displayedMainTabs[page].tab
        val isPageActive = page == selectedPage
        val state = state.copy(
            tabShellState = state.tabShellState.withSelectedTab(primaryTab),
            isSearchMode = state.isSearchMode && isPageActive,
        )
        val selectedMainTab = primaryTab
        val gridState = pageGridStates.getValue(primaryTab)
        val lazyGridSharedTransitionScope = lazyGridSharedTransitionScope.takeIf { isPageActive }
        val lazyGridAnimatedVisibilityScope = lazyGridAnimatedVisibilityScope.takeIf { isPageActive }
        val density = LocalDensity.current
        // [重构] 折叠进度：header 是 index 0，滚动偏移驱动 header 内容上移淡出（视差折叠）。
        // 折叠范围用 dp 换算，避免固定像素在不同 density 下曲线不一致
        val headerCollapseRangePx = with(density) { 320.dp.toPx() }
        val headerCollapseFraction = remember(headerCollapseRangePx) {
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
        val bannerScrollOffsetPx = remember {
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
                compactColumnCount = homeSettings.gridColumnCountCompact,
                defaultColumnCount = homeSettings.gridColumnCount,
            ),
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
            contentMaxWidthDp = adaptiveLayoutSpec.contentMaxWidthDp,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
        var interactiveColumns by remember { mutableStateOf<Int?>(null) }
        var isPinchPillVisible by remember { mutableStateOf(false) }
        var pinchPillDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val haptic = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        val effectivePreferredColumns = interactiveColumns ?: preferredGridColumns
        val gridColumns = if (selectedMainTab == SpaceMainTab.DYNAMIC) {
            adaptiveLayoutSpec.dynamicColumns
        } else {
            effectivePreferredColumns
        }
        val pinchColumnBounds = remember(windowSizeClass.widthSizeClass, windowWidthDp) {
            resolveHomeFeedPinchColumnBounds(
                widthSizeClass = windowSizeClass.widthSizeClass,
                contentWidthDp = windowWidthDp,
            )
        }
        LaunchedEffect(homeSettings.gridColumnCount, homeSettings.gridColumnCountCompact) {
            interactiveColumns = null
        }
        val spaceFeedCardLayout = resolveHomeFeedCardLayout(
            style = homeSettings.homeFeedCardStyle,
            gridColumns = gridColumns,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
        val spaceFeedCoverAspectRatio = spaceFeedCardLayout.coverAspectRatio
        val outerPaddingDp = maxOf(16, spaceFeedCardLayout.outerPaddingDp).dp
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .homeFeedPinchZoom(
                    enabled = selectedMainTab != SpaceMainTab.DYNAMIC && homeSettings.pinchToChangeGridColumnsEnabled,
                    currentColumns = gridColumns,
                    bounds = pinchColumnBounds,
                    onColumnsChange = { newColumns ->
                        interactiveColumns = newColumns
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isPinchPillVisible = true
                        pinchPillDismissJob?.cancel()
                    },
                    onGestureEnd = { finalColumns ->
                        coroutineScope.launch {
                            if (isCompactHomeFeedScreen(windowSizeClass.widthSizeClass)) {
                                SettingsManager.setGridColumnCountCompact(context, finalColumns)
                            } else {
                                SettingsManager.setGridColumnCount(context, finalColumns)
                            }
                        }
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
            verticalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.verticalItemSpacingDp.dp)
        ) LazyVerticalGrid@{
            // 首屏骨架直接铺在内容网格里：与真实卡片共用同一个 LazyVerticalGrid 与同一个
            // gridState，数据到达后是 item 原地替换，网格本身不重建、也不丢滚动锚点。
            // （显式标签：LazyVerticalGrid 是 AppLazyVerticalGrid 的导入别名，避免隐式
            // 标签名与别名不一致导致 return@ 解析到别处。）
            if (isInitialLoading) {
                SpaceLoadingSkeleton(
                    columns = gridColumns,
                    coverAspectRatio = spaceFeedCoverAspectRatio,
                    bannerHeightDp = spaceSkeletonBannerHeightDp,
                    useExpandedHeader = adaptiveLayoutSpec.useExpandedHeader,
                    blockColor = spaceSkeletonBlockColor,
                )
                return@LazyVerticalGrid
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.stationaryPagerHeader(pagerState, page)) {
                SpaceHeader(
                    userInfo = state.headerState.userInfo ?: state.userInfo,
                    relationStat = state.headerState.relationStat ?: state.relationStat,
                    upStat = state.headerState.upStat ?: state.upStat,
                    collapseFractionProvider = { headerCollapseFraction.value },
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
                    onTopPhotoClick = onTopPhotoClick,
                    onAvatarClick = onAvatarClick,
                    onLiveClick = { roomId, title, uname -> onLiveClick(roomId, title, uname) },
                    sharedTransitionScope = lazyGridSharedTransitionScope,
                    animatedVisibilityScope = lazyGridAnimatedVisibilityScope,
                    chromeTopInset = chromeTopInset,
                    outerPadding = outerPaddingDp,
                    useExpandedLayout = adaptiveLayoutSpec.useExpandedHeader,
                )
                }
            }

            // Glass rails scroll away with their category row. Native MD3/Miuix
            // retain the original sticky header and its chrome inset compensation.
            val spaceTabsContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (singleGlassPrimaryRail) Modifier else Modifier.offset {
                            val minimumTop = chromeTopInset.roundToPx()
                            val layoutInfo = gridState.layoutInfo
                            val headerTop = layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == "space_tabs" }
                                // Item offsets exclude the grid's before-content padding.
                                ?.offset?.y?.plus(layoutInfo.beforeContentPadding) ?: minimumTop
                            IntOffset(0, (minimumTop - headerTop).coerceAtLeast(0))
                        })
                        .background(
                            // 吸顶行需要足够不透明：壁纸模式下 74% 会让「主页/动态/投稿」
                            // 变成叠在亮封面上的幽灵文字。
                            com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(
                                MaterialTheme.colorScheme.surface
                            ).let { if (it.alpha < 0.97f) it.copy(alpha = 0.97f) else it }
                        )
                        .onGloballyPositioned {
                            if (isPageActive) pinnedTabsRootBottomPx = it.boundsInRoot().bottom
                        }
                ) {
                    SpaceContentTabs(
                        state = state,
                        onMainTabSelected = onMainTabSelected,
                        pagerState = pagerState,
                        page = page,
                        primaryTabHeightProvider = if (singleGlassPrimaryRail) {
                            { glassPrimaryRailHeightPx }
                        } else null,
                        onContributionTabSelected = onContributionTabSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (singleGlassPrimaryRail) {
                item(key = "space_tabs", span = { GridItemSpan(maxLineSpan) }) {
                    spaceTabsContent()
                }
            } else {
                stickyHeader(key = "space_tabs") { spaceTabsContent() }
            }

            val showSearch = selectedMainTab == SpaceMainTab.DYNAMIC ||
                (selectedMainTab == SpaceMainTab.CONTRIBUTION &&
                    selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO))
            if (showSearch && state.isSearchMode) {
                item(key = "space_search", span = { GridItemSpan(maxLineSpan) }) {
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
                item(key = "space_search_entry", span = { GridItemSpan(maxLineSpan) }) {
                    SpaceSearchEntryChip(
                        label = resolveSpaceSearchEntryLabel(currentSearchScope),
                        onClick = onSearchEntryClick,
                    )
                }
            }

        when (selectedMainTab) {
            SpaceMainTab.HOME -> {
                state.topVideo?.let { topVideo ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceNoticeCard(notice = state.notice)
                    }
                }

                if (state.videos.isNotEmpty() || state.totalVideos > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "视频",
                            count = state.totalVideos.takeIf { it > 0 } ?: state.videos.size,
                            onActionClick = {
                                onMainTabSelected(SpaceMainTab.CONTRIBUTION)
                                state.contributionTabs.firstOrNull { it.subTab == SpaceSubTab.VIDEO }?.let {
                                    onContributionTabSelected(it.id)
                                }
                            }
                        )
                    }
                    items(state.videos.take(4), key = { "home_video_${it.bvid}" }) { video ->
                        val localProgressMs = videoProgressLookup(video.bvid)
                        SpaceHomeVideoCard(
                            video = video,
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
                }

                if (state.homeFavoriteFolders.isNotEmpty() || state.homeFavoriteFolderCount > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                        span = { GridItemSpan(maxLineSpan) }
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    itemsIndexed(
                        items = state.homeCoinVideos.take(2),
                        key = { index, item ->
                            resolveSpaceAggregateLazyItemKey("coin", index, item)
                        }
                    ) { _, item ->
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

                if (state.homeLikeVideos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    itemsIndexed(
                        items = state.homeLikeVideos.take(2),
                        key = { index, item ->
                            resolveSpaceAggregateLazyItemKey("like", index, item)
                        }
                    ) { _, item ->
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

                if (state.articles.isNotEmpty() || state.totalArticles > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                        span = { GridItemSpan(maxLineSpan) }
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                        span = { GridItemSpan(maxLineSpan) }
                    ) { audio ->
                        SpaceAudioListItem(
                            audio = audio,
                            modifier = boundedListModifier,
                            onClick = { onAudioClick(audio.id) }
                        )
                    }
                }

                if (state.homeBangumiItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "漫画",
                            count = state.homeComicCount.takeIf { it > 0 } ?: state.homeComicItems.size,
                            actionLabel = null
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
                    state.videos.isEmpty() &&
                    state.homeFavoriteFolders.isEmpty() &&
                    state.homeCoinVideos.isEmpty() &&
                    state.homeLikeVideos.isEmpty() &&
                    state.articles.isEmpty() &&
                    state.audios.isEmpty() &&
                    state.homeBangumiItems.isEmpty() &&
                    state.homeComicItems.isEmpty()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionEmptyState(
                            title = "主页空空的",
                            subtitle = "暂时没有可展示的主页内容"
                        )
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionEmptyState(
                            title = "暂无动态",
                            subtitle = "这个空间暂时没有可展示的动态内容"
                        )
                    }
                } else if (presentationState == SpaceDynamicPresentationState.ERROR) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceErrorSection(
                            message = "动态加载失败，请稍后重试",
                            onRetry = onLoadDynamic
                        )
                    }
                } else {
                    items(
                        items = dynamicCardItems,
                        key = { "space_dynamic_${it.id_str}" },
                        span = { GridItemSpan(1) }
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
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceLoadingFooter()
                        }
                    }

                    if (state.hasMoreDynamics && dynamicCardItems.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
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
                        if (state.videos.isNotEmpty() || state.totalVideos > 0) {
                            item(key = "space_video_summary_bar", span = { GridItemSpan(maxLineSpan) }) {
                                SpaceContributionVideoSummaryBar(
                                    totalCount = state.totalVideos.takeIf { it > 0 } ?: state.videos.size,
                                    currentOrder = state.sortOrder,
                                    onSelectSortOrder = onSelectSortOrder,
                                    onPlayAll = onPlayAllVideos,
                                )
                            }
                        }

                        if (state.videos.isEmpty() && !state.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceSectionEmptyState(
                                    title = "暂无投稿",
                                    subtitle = "这个分区下暂时没有可展示的视频"
                                )
                            }
                        }

                        items(
                            items = state.videos,
                            key = {
                                resolveSpaceContributionVideoItemKey(
                                    layoutMode = contributionVideoLayoutMode,
                                    bvid = it.bvid,
                                    aid = it.aid
                                )
                            },
                            span = {
                                GridItemSpan(
                                    resolveSpaceContributionVideoGridSpan(
                                        layoutMode = contributionVideoLayoutMode,
                                        maxLineSpan = maxLineSpan
                                    )
                                )
                            }
                        ) { video ->
                            val localProgressMs = videoProgressLookup(video.bvid)
                            AnimatedVideoListItem(modifier = videoListItemModifier(enabled = homeSettings.cardAnimationEnabled), enabled = homeSettings.cardAnimationEnabled) {
                                when (contributionVideoLayoutMode) {
                                    SpaceContributionVideoLayoutMode.GRID -> {
                                        SpaceHomeVideoCard(
                                            video = video,
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
                                            statusLabels = video.statusLabels,
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

                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceLoadingFooter()
                            }
                        }
                    }

                    SpaceSubTab.AUDIO -> {
                        if (state.audios.isEmpty() && !state.isLoadingAudios) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceSectionEmptyState(
                                    title = "暂无音频",
                                    subtitle = "这个 UP 还没有公开的音频作品"
                                )
                            }
                        }

                        items(
                            items = state.audios,
                            key = { "space_audio_${it.id}" },
                            span = { GridItemSpan(maxLineSpan) }
                        ) { audio ->
                            SpaceAudioListItem(
                                audio = audio,
                                modifier = boundedListModifier,
                                onClick = { onAudioClick(audio.id) }
                            )
                        }

                        if (state.isLoadingAudios) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceLoadingFooter()
                            }
                        } else if (state.hasMoreAudios && state.audios.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(state.audios.size, isPageActive) { if (isPageActive) onLoadMoreAudios() }
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }

                    SpaceSubTab.ARTICLE, SpaceSubTab.OPUS -> {
                        if (state.articles.isEmpty() && !state.isLoadingArticles) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceSectionEmptyState(
                                    title = "暂无图文",
                                    subtitle = "这个 UP 还没有公开的图文内容"
                                )
                            }
                        }

                        items(
                            items = state.articles,
                            key = { "space_article_${it.id}" },
                            span = { GridItemSpan(maxLineSpan) }
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

                        if (state.isLoadingArticles) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceLoadingFooter()
                            }
                        } else if (state.hasMoreArticles && state.articles.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(state.articles.size, isPageActive) { if (isPageActive) onLoadMoreArticles() }
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }

                    SpaceSubTab.SEASON_VIDEO -> {
                        val season = state.seasons.firstOrNull { it.meta.season_id == selectedContributionTab.seasonId }
                        val archives = state.seasonArchives[selectedContributionTab.seasonId].orEmpty()
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceCollectionSummaryBar(
                                total = season?.meta?.total ?: archives.size,
                                modifier = boundedListModifier,
                                playAllEnabled = archives.isNotEmpty(),
                                onPlayAll = onPlayAllVideos,
                            )
                        }
                        if (archives.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceSectionEmptyState(
                                    title = "暂无合集内容",
                                    subtitle = "这个合集暂时没有可展示的视频"
                                )
                            }
                        }
                        items(
                            items = archives,
                            key = { "season_video_${it.aid}_${it.bvid}" },
                            span = {
                                GridItemSpan(
                                    resolveSpaceContributionVideoGridSpan(
                                        layoutMode = contributionVideoLayoutMode,
                                        maxLineSpan = maxLineSpan,
                                    )
                                )
                            }
                        ) { archive ->
                            SpaceContributionArchiveVideoItem(
                                video = archive.toSpaceVideoItem(),
                                layoutMode = contributionVideoLayoutMode,
                                coverAspectRatio = spaceFeedCoverAspectRatio,
                                boundedListModifier = boundedListModifier,
                                onClick = { playVideoFromSpace(archive.bvid) },
                                sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(archive.bvid),
                                sharedTransitionScope = lazyGridSharedTransitionScope,
                                animatedVisibilityScope = lazyGridAnimatedVisibilityScope,
                            )
                        }
                    }

                    SpaceSubTab.SERIES -> {
                        val series = state.series.firstOrNull { it.meta.series_id == selectedContributionTab.seriesId }
                        val archives = state.seriesArchives[selectedContributionTab.seriesId].orEmpty()
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceCollectionSummaryBar(
                                total = series?.meta?.total ?: archives.size,
                                modifier = boundedListModifier,
                                playAllEnabled = archives.isNotEmpty(),
                                onPlayAll = onPlayAllVideos,
                            )
                        }
                        if (archives.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceSectionEmptyState(
                                    title = "暂无系列内容",
                                    subtitle = "这个系列暂时没有可展示的视频"
                                )
                            }
                        }
                        items(
                            items = archives,
                            key = { "series_video_${it.aid}_${it.bvid}" },
                            span = {
                                GridItemSpan(
                                    resolveSpaceContributionVideoGridSpan(
                                        layoutMode = contributionVideoLayoutMode,
                                        maxLineSpan = maxLineSpan,
                                    )
                                )
                            }
                        ) { archive ->
                            SpaceContributionArchiveVideoItem(
                                video = archive.toSpaceVideoItem(),
                                layoutMode = contributionVideoLayoutMode,
                                coverAspectRatio = spaceFeedCoverAspectRatio,
                                boundedListModifier = boundedListModifier,
                                onClick = { playVideoFromSpace(archive.bvid) },
                                sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(archive.bvid),
                                sharedTransitionScope = lazyGridSharedTransitionScope,
                                animatedVisibilityScope = lazyGridAnimatedVisibilityScope,
                            )
                        }
                    }

                    SpaceSubTab.UGC_SEASON, SpaceSubTab.COMIC -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceSectionEmptyState(
                                title = "该分类暂未开放",
                                subtitle = "当前仓库还没有为这个投稿分类补齐独立列表视图"
                            )
                        }
                    }
                }
            }

            SpaceMainTab.FAVORITE -> {
                if (state.createdFavoriteFolders.isEmpty() && state.collectedFavoriteFolders.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionEmptyState(
                            title = "暂无收藏夹",
                            subtitle = "该用户还没有公开的收藏夹"
                        )
                    }
                }

                if (state.createdFavoriteFolders.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "创建的收藏夹",
                            count = state.createdFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.createdFavoriteFolders,
                        key = { "created_favorite_${it.id}" },
                        span = { GridItemSpan(maxLineSpan) }
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "收藏的合集",
                            count = state.collectedFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.collectedFavoriteFolders,
                        key = { "collected_favorite_${it.id}" },
                        span = { GridItemSpan(maxLineSpan) }
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceLoadingFooter()
                    }
                } else if (state.hasMoreBangumi && state.bangumiItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionEmptyState(
                            title = "暂无合集",
                            subtitle = "该用户还没有公开的系列、合集或收藏夹"
                        )
                    }
                }

                if (state.seasons.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "合集",
                            count = state.seasons.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.seasons,
                        key = { "season_${it.meta.season_id}" },
                        span = { GridItemSpan(maxLineSpan) }
                    ) { season ->
                        SpaceCollectionRow(
                            title = season.meta.name,
                            total = season.meta.total,
                            modifier = boundedListModifier,
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "系列",
                            count = state.series.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.series,
                        key = { "series_${it.meta.series_id}" },
                        span = { GridItemSpan(maxLineSpan) }
                    ) { series ->
                        SpaceCollectionRow(
                            title = series.meta.name,
                            total = series.meta.total,
                            modifier = boundedListModifier,
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "创建的收藏夹",
                            count = state.createdFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.createdFavoriteFolders,
                        key = { "collection_created_favorite_${it.id}" },
                        span = { GridItemSpan(maxLineSpan) }
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSectionHeader(
                            title = "收藏的合集",
                            count = state.collectedFavoriteFolders.size,
                            actionLabel = null
                        )
                    }
                    items(
                        items = state.collectedFavoriteFolders,
                        key = { "collection_collected_favorite_${it.id}" },
                        span = { GridItemSpan(maxLineSpan) }
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

            SpaceMainTab.CHEESE -> {
                if (state.isLoadingCheese && state.cheeseItems.isEmpty()) {
                    // 骨架行与真实行同 span、同 contentType，并各自挂 animateItem：
                    // 切换时只剩「行淡入」这一件事，列表不会被整棵重建。
                    items(
                        count = 6,
                        key = { index -> "space_cheese_skeleton_$index" },
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = { SPACE_CHEESE_ROW_CONTENT_TYPE },
                    ) {
                        SpaceCheeseSkeletonItem(
                            modifier = boundedListModifier.animateItem()
                        )
                    }
                } else if (state.cheeseItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                        span = { GridItemSpan(maxLineSpan) },
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
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceLoadingFooter()
                        }
                    } else if (state.hasMoreCheese) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(state.cheeseItems.size, isPageActive) { if (isPageActive) onLoadMoreCheese() }
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
        // Short overview/empty tabs must still have enough scroll travel to finish the
        // header-to-cutout handoff. Measure only the missing tail space, not a fixed footer.
        item(key = "space_header_collapse_tail", span = { GridItemSpan(maxLineSpan) }) {
            // Keep the derived measurement stable when LazyGrid publishes a new layout result.
            val retainedHeight = remember { intArrayOf(0) }
            val tailHeight = remember(gridState, headerCollapseRangePx, spaceSkeletonBannerHeightDp, density) {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val tail = info.visibleItemsInfo.firstOrNull { it.key == "space_header_collapse_tail" }
                    val requiredTravelPx = maxOf(
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

        // 投稿视频悬浮工具条（对齐 PiliPlus 的 SliverFloatingHeaderWidget）：
        // 统计条随内容滚走后，在吸顶 Tab 行正下方悬浮同一组操作。
        val isContributionVideoTab = selectedMainTab == SpaceMainTab.CONTRIBUTION &&
            selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO)
        val isContributionSummaryScrolledAway by remember {
            derivedStateOf {
                gridState.firstVisibleItemIndex > 2 ||
                    (gridState.firstVisibleItemIndex == 2 && gridState.firstVisibleItemScrollOffset > 0)
            }
        }
        if (!singleGlassPrimaryRail && isContributionVideoTab && !state.isSearchMode &&
            (state.videos.isNotEmpty() || state.totalVideos > 0)
        ) {
            // 实测 dock：吸顶 Tab 行底边（根坐标）− 父容器顶边（根坐标）。
            val pinnedTabsTopPadding = with(density) {
                (pinnedTabsRootBottomPx - gridContainerRootTopPx).coerceAtLeast(0f).toDp()
            }
            AnimatedVisibility(
                visible = isContributionSummaryScrolledAway,
                enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { -it / 2 },
                exit = fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = pinnedTabsTopPadding)
                        .background(
                            com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(
                                MaterialTheme.colorScheme.surface
                            ).let { if (it.alpha < 0.97f) it.copy(alpha = 0.97f) else it }
                        )
                ) {
                    SpaceContributionVideoSummaryBar(
                        modifier = Modifier.padding(horizontal = outerPaddingDp),
                        totalCount = state.totalVideos.takeIf { it > 0 } ?: state.videos.size,
                        currentOrder = state.sortOrder,
                        onSelectSortOrder = onSelectSortOrder,
                        onPlayAll = onPlayAllVideos,
                    )
                }
            }
        }

        // [新增] 双指缩放切换网格列数 HUD 胶囊 (自适应 MD3 / MIUIX)
        GridPinchColumnHudPill(
            visible = isPinchPillVisible,
            columns = gridColumns,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = chromeTopInset + 16.dp)
        )
        } // HorizontalPager

        if (singleGlassPrimaryRail) {
            // A single fixed owner survives page changes, so the incoming page cannot
            // start a second pager-driven bloom while the dragged capsule is releasing.
            // Read geometry in measurement/placement rather than recomposing on scroll.
            SpaceMainTabRow(
                tabs = displayedMainTabs,
                selectedTab = selectedMainTab,
                onSelect = onMainTabSelected,
                indicatorPositionProvider = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
                modifier = Modifier
                    .layout { measurable, constraints ->
                        // Read the same grid frame for width and placement. A root-coordinate
                        // callback arrives after placement and makes this sibling trail scroll.
                        val activeGrid = pageGridStates.getValue(selectedMainTab)
                        val tabItem = activeGrid.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == "space_tabs" }
                        val width = (tabItem?.size?.width ?: constraints.maxWidth)
                            .coerceIn(constraints.minWidth, constraints.maxWidth)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width)
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            val info = activeGrid.layoutInfo
                            val currentTabItem = info.visibleItemsInfo
                                .firstOrNull { it.key == "space_tabs" }
                            if (currentTabItem != null) {
                                val top = currentTabItem.offset.y + info.beforeContentPadding
                                if (top >= chromeTopInset.roundToPx()) {
                                    // Preserve the single owner across horizontal page changes.
                                    placeable.placeRelative(
                                        (constraints.maxWidth - placeable.width) / 2,
                                        top,
                                    )
                                }
                            }
                        }
                    }
                    .onSizeChanged {
                        if (glassPrimaryRailHeightPx != it.height) glassPrimaryRailHeightPx = it.height
                    }
                    .background(
                        com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor(
                            MaterialTheme.colorScheme.surface
                        ).let { if (it.alpha < 0.97f) it.copy(alpha = 0.97f) else it }
                    ),
            )
        }

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
    val avatarPreviewEnabled = userInfo.face.isNotBlank()
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
    val followButtonColors = resolveSpaceFollowButtonColors(
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
                        translationY = (bannerScrollOffsetPxProvider() * 0.75f)
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
                            .clickable(
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
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = avatarPreviewEnabled && !avatarHidden,
                        ) { onAvatarClick(avatarRect.value) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(FormatUtils.buildSizedImageUrl(userInfo.face, width = 320, height = 320))
                            .memoryCacheKey(resolveImagePreviewPlaceholderCacheKey(userInfo.face) ?: userInfo.face)
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

                    if (userInfo.liveRoom?.liveStatus == 1) {
                        AppSurface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFC107)
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    } else {
                        UserAvatarCornerMarkBadge(
                            mark = resolveUserAvatarCornerMark(
                                officialType = userInfo.official.type,
                                vipStatus = userInfo.vip.status,
                            ),
                            modifier = Modifier.align(Alignment.BottomEnd),
                            badgeSize = 20.dp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                if (useExpandedLayout) {
                    SpaceHeaderIdentityInfo(
                        userInfo = userInfo,
                        officialBadge = officialBadge,
                        onLiveClick = onLiveClick,
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
                                if (isOwner) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "可在「我的」页面编辑个人资料",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    onFollowClick()
                                }
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
                onLiveClick = onLiveClick,
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
internal fun SpaceHeaderIdentityInfo(
    userInfo: SpaceUserInfo,
    officialBadge: OfficialVerifyBadgeSpec?,
    onLiveClick: (Long, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 名称先按可用宽度测量，等级与会员标签放不下时换行，避免固定徽章挤占名称。
    SelectionContainer(modifier = modifier) {
        Column {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = userInfo.name,
                    modifier = Modifier.copyOnLongPress(userInfo.name, "UP主名称"),
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

            val liveRoom = userInfo.liveRoom
            if (liveRoom?.liveStatus == 1 && liveRoom.url.isNotBlank()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpaceBadgeChip(
                        text = "直播中",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {
                            val roomId = liveRoom.roomId.takeIf { it > 0L }
                                ?: liveRoom.url
                                    .substringAfterLast('/')
                                    .substringBefore('?')
                                    .toLongOrNull()
                                ?: 0L
                            onLiveClick(
                                roomId,
                                liveRoom.title.ifBlank { userInfo.name },
                                userInfo.name
                            )
                        }
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
                                .clickable {
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
                    SpaceFollowedUpSection(followedUp = followedUp)
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
    primaryTabHeightProvider: (() -> Int)? = null,
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
    Column(modifier = modifier) {
        if (primaryTabHeightProvider != null) {
            Spacer(
                Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val height = primaryTabHeightProvider().coerceIn(constraints.minHeight, constraints.maxHeight)
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
            )
        } else {
            SpaceMainTabRow(
                tabs = displayedMainTabs,
                selectedTab = selectedMainTab,
                onSelect = onMainTabSelected,
                modifier = Modifier.stationaryPagerHeader(pagerState, page),
                indicatorPositionProvider = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
            )
        }
        if (selectedMainTab == SpaceMainTab.CONTRIBUTION && displayedContributionTabs.isNotEmpty()) {
            SpaceSecondarySwitchRow(
                items = displayedContributionTabs,
                selectedId = state.selectedContributionTabId,
                onSelect = onContributionTabSelected,
            )
        }
    }
}

@Composable
private fun SpaceSecondarySwitchRow(
    items: List<SpaceContributionTab>,
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
        themeConfig.copy(liquidGlassEnabled = false)
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
            )
        }
    }
}

@Composable
private fun SpaceMainTabRow(
    tabs: List<SpaceMainTabItem>,
    selectedTab: SpaceMainTab,
    onSelect: (SpaceMainTab) -> Unit,
    modifier: Modifier = Modifier,
    indicatorPositionProvider: () -> Float,
    isScrollInProgressProvider: () -> Boolean,
) {
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val spec = remember(tabs, selectedTab) {
        resolveSpaceMainTabChromeSpec(tabs = tabs, selectedTab = selectedTab)
    }
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
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
            // Give the glass rail modest breathing room without returning to full-page slots.
            // Native alignment, minimum width and typography keep their original defaults.
            centerContent = liquidGlassEnabled,
            minTabWidth = if (liquidGlassEnabled) 64.dp else androidx.compose.ui.unit.Dp.Unspecified,
            labelFontSize = if (liquidGlassEnabled) 15.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
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
    onPlayAll: () -> Unit,
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
        Spacer(modifier = Modifier.width(14.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onPlayAll)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppIcon(
                imageVector = Icons.Outlined.PlayCircleOutline,
                contentDescription = "播放全部",
                modifier = Modifier.size(16.dp),
                tint = AppContentColors.secondaryText()
            )
            AppText(
                text = "播放全部",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        var sortMenuExpanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { sortMenuExpanded = true }
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
            DropdownMenu(
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
                modifier = Modifier.clickable(onClick = onUpowerRankClick),
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
                modifier = Modifier.clickable(onClick = onMemberGuardClick),
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
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useCardShellSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = sharedTransitionKey.orEmpty(),
                sourceRoute = sourceRoute,
                motionSpec = cardSharedTransitionMotionSpec,
                clipShape = coverShape
            )
            .border(width = 3.dp, color = locateHighlightColor, shape = coverShape)
            .then(nativeCardSnapshot.modifier)
            .clip(coverShape)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .clickable {
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
                labels = video.statusLabels,
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
    sharedTransitionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
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
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .then(nativeCardSnapshot.modifier)
            .clip(coverShape)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .clickable {
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
                labels = item.statusLabels,
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
            .clickable { onClick() }
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
                statusLabels = video.statusLabels,
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useCardShellSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = sharedTransitionKey.orEmpty(),
                sourceRoute = sourceRoute,
                motionSpec = cardSharedTransitionMotionSpec,
                clipShape = cardShellShape,
                crossfadeSourceContent = true,
            )
            .then(nativeCardSnapshot.modifier)
            .clip(cardShellShape)
            .background(AppSurfaceTokens.cardContainer())
            .border(width = 3.dp, color = locateHighlightColor, shape = cardShellShape)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .clickable {
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
            .clickable { onClick() }
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .clickable { onClick() }
            .padding(vertical = 6.dp)
    ) {
        RichTextContent(
            desc = remember(article.title) { DynamicDesc(text = article.title) },
            onUserClick = {},
            onBlankTap = onClick,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.SemiBold,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val imageUrls = article.displayImageUrls()
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
            .clickable { onClick() }
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
private fun SpaceCollectionSummaryBar(
    total: Int,
    playAllEnabled: Boolean,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = "$total 个内容",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
        Spacer(modifier = Modifier.weight(1f))
        AppTextButton(onClick = onPlayAll, enabled = playAllEnabled) {
            AppIcon(
                imageVector = Icons.Outlined.PlayCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            AppText("播放全部")
        }
    }
}


@Composable
private fun SpaceCollectionRow(
    title: String,
    total: Int,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = "$total 个内容",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
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
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
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
                onClick = { onClick?.invoke() },
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
private fun SpaceHeaderBanner(
    topImages: List<com.android.purebilibili.data.model.response.SpaceTopImageItem>,
    fallbackTopPhotoUrl: String,
    onCurrentBannerUrlChange: (String?) -> Unit = {},
    skinBackgroundPaths: List<String> = emptyList(),
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 与 PiliPlus 一致：所有背景图统一做亮/暗色调色，保证顶栏与头像在任意封面上可读。
    val bannerColorFilter = resolveSpaceBannerColorFilter(isLight = !isDarkTheme)
    if (skinBackgroundPaths.isNotEmpty()) {
        LaunchedEffect(skinBackgroundPaths) { onCurrentBannerUrlChange(null) }
        val pagerState = rememberPagerState { skinBackgroundPaths.size }
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
                    color = Color.White,
                    trackColor = MediaContrastPalette.Track,
                )
            }
        }
    } else if (topImages.size > 1) {
        val pagerState = rememberPagerState { topImages.size }
        LaunchedEffect(pagerState.currentPage, topImages) {
            onCurrentBannerUrlChange(topImages.getOrNull(pagerState.currentPage)?.header)
        }
        // 多张头图自动轮播：用户拖动期间暂停，松手后重新计时
        val bannerDragging by pagerState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(pagerState, bannerDragging, topImages.size) {
            if (bannerDragging) return@LaunchedEffect
            while (true) {
                delay(SPACE_BANNER_AUTO_ROTATE_INTERVAL_MS)
                val nextPage = (pagerState.currentPage + 1) % topImages.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
        Box(modifier = modifier) {
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
                color = Color.White,
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
    modifier: Modifier = Modifier,
) {
    val items = followedUp.items
    if (items.isEmpty()) return
    val displayUsers = items.take(3)
    val moreCount = items.size
    val namesText = displayUsers.joinToString("、") { it.name }
    val suffixText = if (items.size > 3) "等${moreCount}人也关注了TA" else "也关注了TA"

    Row(
        modifier = modifier.fillMaxWidth(),
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


