package com.android.purebilibili.feature.space

import com.android.purebilibili.core.ui.components.resolveVideoListColumns
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.core.ui.components.AnimatedVideoListItem
import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.android.purebilibili.core.ui.components.liquidDockViewport
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import androidx.compose.material3.ButtonDefaults
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.OfficialVerifyBadgeSpec
import com.android.purebilibili.core.ui.OfficialVerifyBadgeTone
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.resolveOfficialVerifyBadge
import com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField
import com.android.purebilibili.core.ui.components.AppNativeTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.KeepScrollableTabSelectionVisible
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.feature.home.components.cards.HORIZONTAL_VIDEO_CARD_COVER_ASPECT_RATIO
import com.android.purebilibili.feature.home.components.cards.HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardCoverOverlayTextShadow
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.videoCoverSharedElementKey
import com.android.purebilibili.core.ui.transition.videoSharedElementBoundsTransformSpec
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.feature.home.components.cards.videoCardShellReturnChromeAlpha
import com.android.purebilibili.feature.home.resolveHomeFeedCardLayout
import com.android.purebilibili.core.ui.components.UserLevelBadge
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.DynamicDesc
import kotlin.math.roundToInt
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceArticleItem
import com.android.purebilibili.data.model.response.displayImageUrls
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
import com.android.purebilibili.feature.dynamic.components.RichTextContent
import com.android.purebilibili.feature.dynamic.components.DynamicCommentOverlayHost
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.RepostDialog
import com.android.purebilibili.feature.list.VideoProgressDisplayState
import com.android.purebilibili.feature.video.controller.PlaybackProgressManager
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SpaceScreen(
    mid: Long,
    targetBvid: String? = null,
    onBack: () -> Unit,
    onVideoClick: (String, Long, Long) -> Unit,
    onAudioClick: (Long) -> Unit = {},
    onBangumiClick: (Long) -> Unit = {},
    onWebClick: (String, String) -> Unit = { _, _ -> },
    onLiveClick: (Long, String, String) -> Unit = { _, _, _ -> },
    onUserClick: (Long) -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    onDynamicDetailClick: (String) -> Unit = {},
    onArticleClick: (Long, String) -> Unit = { _, _ -> },
    onViewAllClick: (String, Long, Long, String, String) -> Unit = { _, _, _, _, _ -> },
    onMessageClick: (Long, String, String) -> Unit = { _, _, _ -> },
    onFollowingClick: (Long) -> Unit = {},
    onFansClick: (Long) -> Unit = {},
    viewModel: SpaceViewModel = viewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
    var showAvatarPreview by remember(mid) { mutableStateOf(false) }
    var repostDynamicId by remember { mutableStateOf<String?>(null) }
    val hazeState = rememberRecoverableHazeState()
    val gridState = rememberLazyGridState()
    val isSpaceScrolling by remember {
        derivedStateOf { gridState.isScrollInProgress }
    }
    val pinnedTopChromeScrim by remember {
        derivedStateOf {
            resolveSpacePinnedTopChromeScrim(
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
    val playAllSpaceVideos: () -> Unit = playAll@{
        val state = currentSuccessState ?: return@playAll
        val startBvid = resolveSpacePlayAllStartTarget(state.videos) ?: return@playAll
        val playlist = buildExternalPlaylistFromSpaceVideos(
            videos = state.videos,
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
        onVideoClick(startBvid, playbackTarget.cid, playbackTarget.resumePositionMs)
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

    AppScaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .unifiedBlur(
                        hazeState = hazeState,
                        surfaceType = BlurSurfaceType.HEADER,
                        isScrolling = isSpaceScrolling
                    )
            ) {
                AppTopBar(
                    title = screenTitle,
                    navigationIcon = {
                        AppIconButton(onClick = onBack) {
                            AppIcon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = backLabel
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = pinnedTopChromeScrim
                        ),
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = pinnedTopChromeScrim
                        )
                    ),
                    actions = {
                        if (canSearch) {
                            AppIconButton(onClick = { viewModel.setSearchMode(!isSearchMode) }) {
                                AppIcon(
                                    imageVector = if (isSearchMode) Icons.Outlined.Close else Icons.Outlined.Search,
                                    contentDescription = if (isSearchMode) "关闭搜索" else "搜索"
                                )
                            }
                        }
                        AppWindowActionMenu(
                            groups = buildList {
                                if (showContributionVideoMenuActions) {
                                    add(
                                        listOf(
                                            AppWindowAction(
                                                label = "播放全部",
                                                icon = Icons.Outlined.PlayCircleOutline,
                                                onClick = playAllSpaceVideos,
                                            ),
                                            AppWindowAction(
                                                label = if (
                                                    contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.SINGLE_COLUMN
                                                ) {
                                                    "切换为双列"
                                                } else {
                                                    "切换为单列"
                                                },
                                                icon = if (
                                                    contributionVideoLayoutMode == SpaceContributionVideoLayoutMode.SINGLE_COLUMN
                                                ) {
                                                    Icons.Outlined.GridView
                                                } else {
                                                    Icons.Outlined.ViewAgenda
                                                },
                                                onClick = {
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
                                            iconTint = MaterialTheme.colorScheme.error,
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
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
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
                )
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

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSourceCompat(state = hazeState)
            ) {
                when (val state = uiState) {
                    SpaceUiState.Loading -> {
                        SpaceLoadingSkeleton(modifier = Modifier.fillMaxSize())
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

                        SpaceContent(
                            state = state,
                            gridState = gridState,
                            onVideoClick = onVideoClick,
                            videoProgressLookup = videoProgressLookup,
                            onAudioClick = onAudioClick,
                            onBangumiClick = onBangumiClick,
                            onWebClick = onWebClick,
                            onLiveClick = onLiveClick,
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
                            onDynamicDetailClick = onDynamicDetailClick,
                            onArticleClick = onArticleClick,
                            onViewAllClick = onViewAllClick,
                            onMainTabSelected = viewModel::selectMainTab,
                            onContributionTabSelected = viewModel::selectContributionTab,
                            onCategorySelected = viewModel::selectCategory,
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
                            onTopPhotoClick = { showTopPhotoPreview = true },
                            onAvatarClick = { showAvatarPreview = true },
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
                            animatedVisibilityScope = animatedVisibilityScope
                        )

                        DynamicCommentOverlayHost(
                            viewModel = dynamicInteractionViewModel,
                            primaryItems = dynamicCardItems,
                            toastContext = context,
                            onUserClick = onUserClick,
                        )
                    }
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
        }
    }

    val previewUrl = normalizeSpaceTopPhotoUrl(
        currentSuccessState?.userInfo?.topPhoto.orEmpty()
    )
    val avatarPreviewUrl = currentSuccessState?.userInfo?.face.orEmpty()
    if (showTopPhotoPreview && shouldEnableSpaceTopPhotoPreview(previewUrl)) {
        ImagePreviewDialog(
            images = listOf(previewUrl),
            initialIndex = 0,
            onDismiss = { showTopPhotoPreview = false }
        )
    }
    if (showAvatarPreview && avatarPreviewUrl.isNotBlank()) {
        ImagePreviewDialog(
            images = listOf(avatarPreviewUrl),
            initialIndex = 0,
            onDismiss = { showAvatarPreview = false }
        )
    }

    repostDynamicId?.let { dynamicId ->
        RepostDialog(
            onDismiss = { repostDynamicId = null },
            onRepost = { content: String, onComplete: (Boolean) -> Unit ->
                dynamicInteractionViewModel.repostDynamic(dynamicId, content) { success, message ->
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
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
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        AppText(
                            text = "可多选，确定后覆盖原分组设置。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
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
                            color = MaterialTheme.colorScheme.onPrimary
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                AppText(
                    text = "是否定位到视频投稿",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onVideoClick: (String, Long, Long) -> Unit,
    videoProgressLookup: (String) -> Long,
    onAudioClick: (Long) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit,
    onDynamicDetailClick: (String) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onViewAllClick: (String, Long, Long, String, String) -> Unit,
    onMainTabSelected: (SpaceMainTab) -> Unit,
    onContributionTabSelected: (String) -> Unit,
    onCategorySelected: (Int) -> Unit,
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
    onSearchQueryChange: (String) -> Unit,
    onSearchEntryClick: () -> Unit,
    onLocateTargetConsumed: (String) -> Unit,
    onLocateTargetMissing: (String) -> Unit,
    onLocateTargetLoadFailed: (String) -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onTopPhotoClick: () -> Unit,
    onAvatarClick: () -> Unit,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 投稿网格跟随首页信息流设置（固定列数 / 卡宽预设 / 卡片风格），保证排版与首页 feed 一致。
    val homeSettings by com.android.purebilibili.core.store.SettingsManager
        .getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings())
    val selectedMainTab = state.tabShellState.selectedTab
    val displayedMainTabs = remember(state.mainTabs, selectedMainTab) {
        resolveSpaceDisplayedMainTabs(
            tabs = state.mainTabs,
            selectedTab = selectedMainTab
        )
    }
    val displayedContributionTabs = remember(state.contributionTabs, state.totalAudios) {
        resolveDisplayedSpaceContributionTabs(
            tabs = state.contributionTabs,
            totalAudios = state.totalAudios
        )
    }
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
    val secondarySwitchItems = remember(displayedContributionTabs) {
        resolveSpaceSecondarySwitchItems(displayedContributionTabs)
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
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
    LaunchedEffect(state.userInfo.mid) {
        onLoadHome()
    }

    val bangumiTabState = state.tabShellState.tabStates[SpaceMainTab.BANGUMI] ?: SpaceTabContentState()
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

    LaunchedEffect(shouldLoadMoreVideos) {
        if (shouldLoadMoreVideos) {
            onLoadMoreVideos()
        }
    }

    val contributionVideoItemStartIndex = remember(
        selectedMainTab,
        displayedContributionTabs,
        selectedContributionTab,
        secondarySwitchItems,
        state.isSearchMode,
        currentSearchScope
    ) {
        if (selectedMainTab != SpaceMainTab.CONTRIBUTION) {
            0
        } else {
            2 +
                (if (shouldShowSpaceSecondarySwitch(selectedMainTab) && secondarySwitchItems.isNotEmpty()) 1 else 0) +
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .responsiveContentWidth(maxWidth = SPACE_CONTENT_MAX_WIDTH_DP.dp)
            .then(modifier)
    ) {
        // [重构] 折叠进度：header 是 index 0，滚动偏移驱动 header 内容上移淡出（视差折叠）；
        // 完全滚出后主 tab overlay 吸顶显示
        // 折叠范围用 dp 换算，避免固定像素在不同 density 下曲线不一致
        val headerCollapseRangePx = with(LocalDensity.current) { 320.dp.toPx() }
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

        // 投稿视频使用独立单双列选择；其他空间网格继续跟随首页的自适应列数。
        // 间距与封面比例仍沿用首页卡片风格。
        val preferredGridColumns = resolveSpaceContentGridColumnCount(
            widthDp = LocalConfiguration.current.screenWidthDp,
            fixedColumnCount = homeSettings.gridColumnCount,
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
            widthSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current.widthSizeClass,
        )
        val gridColumns = if (
            state.tabShellState.selectedTab == SpaceMainTab.CONTRIBUTION &&
            state.selectedSubTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO)
        ) {
            resolveVideoListColumns(
                singleColumn = false,
                availableWidthDp = LocalConfiguration.current.screenWidthDp.toFloat(),
            )
        } else preferredGridColumns
        val spaceFeedCardLayout = resolveHomeFeedCardLayout(
            style = homeSettings.homeFeedCardStyle,
            gridColumns = gridColumns,
            widthSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current.widthSizeClass,
        )
        val spaceFeedCoverAspectRatio = spaceFeedCardLayout.coverAspectRatio
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = maxOf(16, spaceFeedCardLayout.outerPaddingDp).dp,
                end = maxOf(16, spaceFeedCardLayout.outerPaddingDp).dp,
                bottom = bottomInset + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.itemSpacingDp.dp),
            verticalArrangement = Arrangement.spacedBy(spaceFeedCardLayout.verticalItemSpacingDp.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SpaceHeader(
                    userInfo = state.headerState.userInfo ?: state.userInfo,
                    relationStat = state.headerState.relationStat ?: state.relationStat,
                    upStat = state.headerState.upStat ?: state.upStat,
                    collapseFraction = headerCollapseFraction.value,
                    onFollowClick = onFollowClick,
                    onMessageClick = onMessageClick,
                    onFollowingClick = onFollowingClick,
                    onFansClick = onFansClick,
                    onTopPhotoClick = onTopPhotoClick,
                    onAvatarClick = onAvatarClick,
                    onLiveClick = { roomId, title, uname -> onLiveClick(roomId, title, uname) },
                    sharedTransitionScope = lazyGridSharedTransitionScope,
                    animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SpaceMainTabRow(
                    tabs = displayedMainTabs,
                    selectedTab = resolveSpacePrimaryTab(selectedMainTab),
                    onSelect = onMainTabSelected,
                )
            }
            if (shouldShowSpaceSecondarySwitch(selectedMainTab) && secondarySwitchItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SpaceSecondarySwitchRow(
                        items = secondarySwitchItems,
                        selectedId = selectedSecondarySwitchId,
                        onSelect = onSecondarySwitchSelected,
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
                            actionLabel = null
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
                            actionLabel = null
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
                if (shouldShowSpaceSearchEntry(currentSearchScope, state.isSearchMode)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSearchEntryChip(
                            label = resolveSpaceSearchEntryLabel(currentSearchScope),
                            onClick = onSearchEntryClick
                        )
                    }
                }
                if (state.isSearchMode && currentSearchScope == SpaceSearchScope.DYNAMIC) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(state.isSearchMode, currentSearchScope) {
                            searchFocusRequester.requestFocus()
                        }
                        AppLiquidAwareSearchField(
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            placeholder = resolveSpaceSearchPlaceholder(currentSearchScope),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusRequester(searchFocusRequester)
                        )
                    }
                }

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
                        span = { GridItemSpan(maxLineSpan) }
                    ) { dynamic ->
                        DynamicCardV2(
                            item = dynamic,
                            onVideoClick = playVideoFromSpace,
                            onBangumiClick = { seasonId, _ -> onBangumiClick(seasonId) },
                            onUserClick = onUserClick,
                            onTopicClick = onTopicClick,
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
                            onCourseClick = onWebClick,
                            onArticleClick = onArticleClick,
                            onDynamicDetailClick = onDynamicDetailClick,
                            gifImageLoader = context.imageLoader,
                            onCommentClick = { onDynamicDetailClick(dynamic.id_str) },
                            onRepostClick = onSpaceDynamicRepostClick,
                            onLikeClickWithState = { dynamicId, isLiked ->
                                onSpaceDynamicLikeClick(dynamicId, isLiked)
                            },
                            onDeleteClick = onSpaceDynamicDeleteClick,
                            onReserveClick = onSpaceDynamicReserveClick,
                            isLiked = likedDynamics.contains(dynamic.id_str),
                            likeOverride = likeOverrides[dynamic.id_str],
                            forwardCountDelta = forwardCountDeltas[dynamic.id_str] ?: 0
                        )
                    }

                    if (state.isLoadingDynamics && dynamicCardItems.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceLoadingFooter()
                        }
                    }

                    if (state.hasMoreDynamics && dynamicCardItems.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(dynamicCardItems.size) {
                                onLoadMoreDynamic()
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }

            SpaceMainTab.CONTRIBUTION -> {
                if (
                    shouldShowSpaceSearchEntry(currentSearchScope, state.isSearchMode) &&
                    selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpaceSearchEntryChip(
                            label = resolveSpaceSearchEntryLabel(currentSearchScope),
                            onClick = onSearchEntryClick
                        )
                    }
                }

                if (
                    state.isSearchMode &&
                    currentSearchScope == SpaceSearchScope.VIDEO &&
                    selectedContributionTab.subTab in setOf(SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(state.isSearchMode, currentSearchScope) {
                            searchFocusRequester.requestFocus()
                        }
                        AppLiquidAwareSearchField(
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            placeholder = resolveSpaceSearchPlaceholder(currentSearchScope),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .focusRequester(searchFocusRequester)
                        )
                    }
                }

                when (selectedContributionTab.subTab) {
                    SpaceSubTab.VIDEO, SpaceSubTab.CHARGING_VIDEO -> {
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
                                            badgeLabel = resolveSpaceVideoChargeBadgeLabel(video),
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
                                            badgeLabel = resolveSpaceVideoChargeBadgeLabel(video),
                                            isLocateHighlight = highlightedLocateBvid == video.bvid &&
                                                isLocateHighlightVisible,
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
                                onClick = { onAudioClick(audio.id) }
                            )
                        }

                        if (state.isLoadingAudios) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SpaceLoadingFooter()
                            }
                        } else if (state.hasMoreAudios && state.audios.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(state.audios.size) { onLoadMoreAudios() }
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
                                LaunchedEffect(state.articles.size) { onLoadMoreArticles() }
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }

                    SpaceSubTab.SEASON_VIDEO -> {
                        val season = state.seasons.firstOrNull { it.meta.season_id == selectedContributionTab.seasonId }
                        val archives = state.seasonArchives[selectedContributionTab.seasonId].orEmpty()
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceCollectionSummaryCard(
                                title = season?.meta?.name ?: selectedContributionTab.title,
                                subtitle = season?.meta?.description.orEmpty(),
                                cover = season?.meta?.cover.orEmpty(),
                                total = season?.meta?.total ?: archives.size,
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
                            span = { GridItemSpan(maxLineSpan) }
                        ) { archive ->
                            SpaceArchiveListItemRow(
                                title = archive.title,
                                cover = archive.pic,
                                duration = FormatUtils.formatDuration(archive.duration),
                                publishTime = FormatUtils.formatPublishTime(archive.pubdate),
                                play = archive.stat.view,
                                secondaryCount = archive.stat.danmaku,
                                onClick = { playVideoFromSpace(archive.bvid) },
                                sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(archive.bvid),
                                sharedTransitionScope = lazyGridSharedTransitionScope,
                                animatedVisibilityScope = lazyGridAnimatedVisibilityScope
                            )
                        }
                    }

                    SpaceSubTab.SERIES -> {
                        val series = state.series.firstOrNull { it.meta.series_id == selectedContributionTab.seriesId }
                        val archives = state.seriesArchives[selectedContributionTab.seriesId].orEmpty()
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpaceCollectionSummaryCard(
                                title = series?.meta?.name ?: selectedContributionTab.title,
                                subtitle = series?.meta?.description.orEmpty(),
                                cover = series?.meta?.cover.orEmpty(),
                                total = series?.meta?.total ?: archives.size,
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
                            span = { GridItemSpan(maxLineSpan) }
                        ) { archive ->
                            SpaceArchiveListItemRow(
                                title = archive.title,
                                cover = archive.pic,
                                duration = FormatUtils.formatDuration(archive.duration),
                                publishTime = FormatUtils.formatPublishTime(archive.pubdate),
                                play = archive.stat.view,
                                secondaryCount = archive.stat.danmaku,
                                onClick = { playVideoFromSpace(archive.bvid) },
                                sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(archive.bvid),
                                sharedTransitionScope = lazyGridSharedTransitionScope,
                                animatedVisibilityScope = lazyGridAnimatedVisibilityScope
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
                        LaunchedEffect(state.bangumiItems.size) { onLoadMoreBangumi() }
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
        }
    }

        // [重构] 吸顶主 tab overlay：header 完全滚出后淡入显示，覆盖在内容上方
        // （BiliPai TabBar pinned 语义；grid 无跨列 stickyHeader，故用浮层实现）
        val tabPinned = gridState.firstVisibleItemIndex > 0
        AnimatedVisibility(
            visible = tabPinned,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(120)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SpaceMainTabRow(
                    tabs = displayedMainTabs,
                    selectedTab = resolveSpacePrimaryTab(selectedMainTab),
                    onSelect = onMainTabSelected,
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
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpaceHeader(
    userInfo: SpaceUserInfo,
    relationStat: RelationStatData?,
    upStat: UpStatData?,
    collapseFraction: Float,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFansClick: () -> Unit,
    onTopPhotoClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val context = LocalContext.current
    val topPhotoUrl = normalizeSpaceTopPhotoUrl(userInfo.topPhoto)
    val avatarPreviewEnabled = userInfo.face.isNotBlank()
    val followLabel = if (userInfo.isFollowed) "已关注" else "关注"
    val isOwner = userInfo.mid > 0L &&
        userInfo.mid == com.android.purebilibili.core.store.TokenManager.midCache
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
        colorScheme = colorScheme
    )

    // BiliPai 式布局：
    // - hero 背景 156dp
    // - 头像 80dp 左下，底部 32dp 伸出背景；stats 独占头像右侧
    // - 名字 + 等级 + 私信/关注同一行垂直居中对齐；名字可收缩，按钮固定在行尾
    // - 徽标 / sign / UID 继续在信息区下方
    // - 返回按钮和 UP 名由外层 AppTopBar 钉住，header 不再整块上移把名字带走
    val heroHeight = 156.dp
    val avatarSize = 80.dp
    val avatarOverlap = 32.dp
    val density = LocalDensity.current
    val bannerParallaxPx = with(density) {
        (heroHeight.value * collapseFraction * 0.35f).dp.roundToPx()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight + avatarOverlap)
        ) {
            // 背景 hero（头像伸出部分下方为 surface 色）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .align(Alignment.TopCenter)
                    .clickable(
                        enabled = shouldEnableSpaceTopPhotoPreview(topPhotoUrl),
                        onClick = onTopPhotoClick
                    )
            ) {
                if (topPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(topPhotoUrl)
                            .size(1440, 900)
                            .scale(Scale.FILL)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, bannerParallaxPx) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        colorScheme.surfaceVariant.copy(alpha = 0.86f),
                                        colorScheme.secondaryContainer.copy(alpha = 0.56f),
                                        colorScheme.surface
                                    )
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colorScheme.surface.copy(alpha = 0.04f),
                                    Color.Transparent,
                                    colorScheme.surface.copy(alpha = 0.55f),
                                    colorScheme.surface.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
            }

            // 头像（左下）+ stats（右侧均分，不再和操作按钮抢宽度）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom
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

                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clickable(enabled = avatarPreviewEnabled, onClick = onAvatarClick)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(FormatUtils.buildSizedImageUrl(userInfo.face, width = 320, height = 320))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(avatarModifier)
                            .clip(CircleShape)
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
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 6.dp),
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
            }
        }

        // 信息区：名字 + 等级 + 私信/关注同一行垂直居中对齐
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppText(
                        text = userInfo.name,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .copyOnLongPress(userInfo.name, "UP主名称"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        // VIP name tint uses theme secondary (readable on light/dark).
                        color = if (userInfo.vip.status == 1) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
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
                if (!isOwner) {
                    SpaceHeaderRelationActions(
                        followLabel = followLabel,
                        isFollowed = userInfo.isFollowed,
                        followButtonColors = followButtonColors,
                        onMessageClick = onMessageClick,
                        onFollowClick = onFollowClick
                    )
                }
            }

            if (userInfo.liveRoom?.liveStatus == 1 && userInfo.liveRoom.url.isNotBlank()) {
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
                            val roomId = userInfo.liveRoom.roomId.takeIf { it > 0L }
                                ?: userInfo.liveRoom.url
                                    .substringAfterLast('/')
                                    .substringBefore('?')
                                    .toLongOrNull()
                                ?: 0L
                            onLiveClick(
                                roomId,
                                userInfo.liveRoom.title.ifBlank { userInfo.name },
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
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val ipLocation = userInfo.ipLocation?.takeIf { it.isNotBlank() }
            if (userInfo.mid > 0L || ipLocation != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (userInfo.mid > 0L) {
                        AppText(
                            text = "UID: ${userInfo.mid}",
                            modifier = Modifier.copyOnLongPress(userInfo.mid.toString(), "UID"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ipLocation?.let { location ->
                        AppText(
                            text = "IP 属地 · $location",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                        )
                    }
                }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            AppText(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
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
    val liquidGlassEnabled =
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val spec = remember(items, selectedId) {
        resolveSpaceSecondarySwitchChromeSpec(items = items, selectedId = selectedId)
    }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spec.horizontalPaddingDp.dp, vertical = 6.dp),
    ) {
        val containerHorizontalPaddingDp = AppSpacingTokens.ExtraSmall.value.roundToInt()
        val preferredItemWidthDp = spec.itemWidthDp ?: 104
        val useScrollableRail = shouldScrollSpaceSecondarySwitch(
            itemCount = items.size,
            itemWidthDp = preferredItemWidthDp,
            viewportWidthDp = maxWidth.value.roundToInt(),
            containerHorizontalPaddingDp = containerHorizontalPaddingDp
        )
        // Keep three slots visible in the viewport even when later library entries
        // make the rail scrollable; long contribution titles then use the same
        // compact width as the legacy three-tab dock.
        val itemWidthDp = resolveSpaceSecondarySwitchAdaptiveItemWidthDp(
            preferredItemWidthDp = preferredItemWidthDp,
            itemCount = items.size,
            viewportWidthDp = maxWidth.value.roundToInt(),
            containerHorizontalPaddingDp = containerHorizontalPaddingDp
        )
        val itemWidth = itemWidthDp.dp
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val containerHorizontalPaddingPx = with(density) { AppSpacingTokens.ExtraSmall.toPx() }
        val dragFollowEdgePaddingPx = with(density) { 12.dp.toPx() }

        KeepScrollableTabSelectionVisible(
            scrollState = scrollState,
            selectedIndex = if (useScrollableRail) spec.selectedIndex else 0,
            itemWidthPx = itemWidthPx,
            viewportWidthPx = viewportWidthPx,
            contentPaddingPx = containerHorizontalPaddingPx,
        )

        if (liquidGlassEnabled) {
            BottomBarLiquidSegmentedControl(
                items = items.map { it.title },
                selectedIndex = spec.selectedIndex,
                onSelected = { index -> items.getOrNull(index)?.id?.let(onSelect) },
                itemWidth = itemWidth.takeIf { useScrollableRail },
                height = spec.heightDp.dp,
                indicatorHeight = spec.indicatorHeightDp.dp,
                labelFontSize = 14.sp,
                liquidGlassEffectsEnabled = spec.liquidGlassEffectsEnabled,
                dragSelectionEnabled = spec.dragSelectionEnabled || useScrollableRail,
                onIndicatorPositionChanged = { position ->
                    if (useScrollableRail) {
                        scrollState.dispatchRawDelta(
                            resolveSpaceSecondarySwitchDragScrollDeltaPx(
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
                tapPressRefractionEnabled = !useScrollableRail,
                modifier = if (useScrollableRail) {
                    Modifier
                        .liquidDockViewport()
                        .horizontalScroll(scrollState)
                } else {
                    Modifier.fillMaxWidth()
                }
            )
        } else {
            AppNativeTabRow(
                options = items.map { AppSegmentOption(it.id, it.title) },
                selectedValue = selectedId,
                onSelectionChange = onSelect,
                modifier = Modifier.fillMaxWidth(),
                scrollable = useScrollableRail,
                minTabWidth = itemWidth,
            )
        }
    }
}

@Composable
private fun SpaceMainTabRow(
    tabs: List<SpaceMainTabItem>,
    selectedTab: SpaceMainTab,
    onSelect: (SpaceMainTab) -> Unit,
) {
    val spec = remember(tabs, selectedTab) {
        resolveSpaceMainTabChromeSpec(tabs = tabs, selectedTab = selectedTab)
    }
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        AppThemeAdaptiveTabRow(
            options = tabs.map { AppSegmentOption(it.tab, it.title) },
            selectedValue = tabs[selectedIndex].tab,
            onSelectionChange = onSelect,
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        AppText(
            text = count.toString(),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            MaterialTheme.colorScheme.primary
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
            .clip(coverShape)
            .then(nativeCardSnapshot.modifier)
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
                        ),
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
            if (!badgeLabel.isNullOrBlank()) {
                AppSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    AppText(
                        text = badgeLabel,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    style = coverOverlayTextStyle,
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
                    color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurface
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .clip(coverShape)
            .then(nativeCardSnapshot.modifier)
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
                        ),
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
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
            color = MaterialTheme.colorScheme.onSurface
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
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow()
        )
        if (item.subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = item.subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        sharedTransitionKey ?: video.pic,
    )
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val stationaryCoverUrl = remember(video.pic) {
        FormatUtils.buildSizedImageUrl(video.pic, width = 560, height = 352)
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
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .then(nativeCardSnapshot.modifier)
            .background(AppSurfaceTokens.cardContainer())
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
                            title = video.title,
                            ownerName = "",
                            ownerFaceUrl = "",
                            viewText = FormatUtils.formatStat(video.stat.view),
                            danmakuText = FormatUtils.formatStat(video.stat.danmaku),
                            durationText = FormatUtils.formatDuration(video.duration),
                            infoPresentation = com.android.purebilibili.core.ui.transition
                                .resolveVideoCardSourceInfoPresentation(
                                    publishTimeText = "",
                                    showStatsInInfo = true,
                                ),
                            coverUrl = stationaryCoverUrl,
                            coverCacheKey = stationaryCoverUrl,
                        ),
                    )
                    nativeCardSnapshot.capture()
                }
                onClick()
            }
            .padding(14.dp)
    ) {
        AppText(
            text = "置顶视频",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = coverModifier
                    .onGloballyPositioned { coordinates ->
                        coverBounds = coordinates.boundsInRoot()
                    }
                    .width(144.dp)
                    .height(90.dp)
                    .clip(coverShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = stationaryCoverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = video.title,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = videoCardTitleMaxLines(),
                    overflow = videoCardTitleOverflow()
                )
                AppText(
                    text = video.reason.ifBlank { FormatUtils.formatPublishTime(video.pubdate) },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                AppText(
                    text = "${FormatUtils.formatStat(video.stat.view)}播放 · ${FormatUtils.formatStat(video.stat.like)}点赞",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        AppText(
            text = notice,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
    isLocateHighlight: Boolean = false,
    onClick: () -> Unit,
    sharedTransitionKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locateHighlightColor by animateColorAsState(
        targetValue = if (isLocateHighlight) {
            MaterialTheme.colorScheme.primary
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
    val coverWidth = HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP.dp
    val coverShape = AppShapes.mediaCover()
    val cardCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Card).value.roundToInt()
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

    Row(
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
            .clip(cardShellShape)
            .then(nativeCardSnapshot.modifier)
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
                            coverUrl = stationaryCoverUrl,
                            coverCacheKey = stationaryCoverUrl,
                        ),
                    )
                    nativeCardSnapshot.capture()
                }
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .width(coverWidth)
                .aspectRatio(HORIZONTAL_VIDEO_CARD_COVER_ASPECT_RATIO)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = stationaryCoverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!badgeLabel.isNullOrBlank()) {
                AppSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    AppText(
                        text = badgeLabel,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
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
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.28f)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = coverWidth / HORIZONTAL_VIDEO_CARD_COVER_ASPECT_RATIO)
                .videoCardShellReturnChromeAlpha(
                    enabled = useCardShellSharedBounds,
                    bvid = sharedTransitionKey.orEmpty(),
                    sourceRoute = sourceRoute,
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AppText(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = videoCardTitleMaxLines(),
                overflow = videoCardTitleOverflow(),
                color = MaterialTheme.colorScheme.onSurface
            )
            AppText(
                text = if (progressState?.progressSec == -1) "$publishTime · 已看完" else publishTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                HorizontalVideoStatRow(
                    playText = FormatUtils.formatStat(play),
                    danmakuText = FormatUtils.formatStat(secondaryCount),
                    modifier = Modifier.weight(1f),
                )
                AppIcon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SpaceAudioListItem(
    audio: SpaceAudioItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            AppText(
                text = "${FormatUtils.formatStat(audio.play_count.toLong())}播放 · ${FormatUtils.formatDuration(audio.duration)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppIcon(
            imageVector = Icons.Outlined.PlayCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
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
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
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
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onClick: () -> Unit
) {
    AppSurface(
        modifier = Modifier
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                AppText(
                    text = "${folder.media_count} 个视频",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppText(
                text = "查看",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
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
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow()
        )
        Spacer(modifier = Modifier.height(4.dp))
        AppText(
            text = item.newEp?.indexShow?.ifBlank { item.progress }.orEmpty().ifBlank { item.evaluate },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpaceCollectionSummaryCard(
    title: String,
    subtitle: String,
    cover: String,
    total: Int,
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                AppText(
                    text = "$total 个内容",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = subtitle,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = "$total 个内容",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AppText(
                            text = subtitle,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
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
    modifier: Modifier = Modifier
) {
    // Same-row chips with the name/level line; fixed height for vertical center alignment.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppSurface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            AppIconButton(
                modifier = Modifier.size(32.dp),
                onClick = onMessageClick
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = "私信",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AppButton(
            onClick = onFollowClick,
            modifier = Modifier
                .widthIn(min = 80.dp, max = 100.dp)
                .height(32.dp),
            shape = AppShapes.container(ContainerLevel.Pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = followButtonColors.backgroundColor,
                contentColor = followButtonColors.textColor
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isFollowed) {
                    AppIcon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                AppText(
                    text = followLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SpaceHeaderStat(
    label: String,
    value: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = FormatUtils.formatStat(value),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        AppText(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    )
}

@Composable
private fun SpaceLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = subtitle,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpaceErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = message,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
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
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    when {
        item.bvid.isNotBlank() -> onVideoClick(item.bvid)
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
