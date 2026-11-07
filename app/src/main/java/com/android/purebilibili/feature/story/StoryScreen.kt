package com.android.purebilibili.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.android.purebilibili.feature.video.ui.pager.PortraitVideoPager
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementEvent
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel

@UnstableApi
@Composable
fun StoryScreen(
    seedBvid: String = "",
    seedCid: Long = 0L,
    seedCover: String = "",
    seedTitle: String = "",
    sourceRoute: String? = null,
    transitionEnabled: Boolean = true,
    viewModel: StoryViewModel = viewModel(),
    playerViewModel: VideoPlaybackViewModel = viewModel(),
    engagementViewModel: VideoEngagementViewModel = viewModel(),
    isActive: Boolean = true,
    isFeedEntrySelected: Boolean = true,
    onBack: () -> Unit,
    onVideoClick: (String, Long, String) -> Unit = { _, _, _ -> },
    onUserClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onRotateToLandscape: () -> Unit = {}
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedSourceRoute = sourceRoute
    @Suppress("UNUSED_PARAMETER")
    val unusedTransitionEnabled = transitionEnabled

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onlyVerticalRecommendations by com.android.purebilibili.core.store.SettingsManager
        .getPortraitOnlyVerticalRecommendations(context)
        .collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(context) {
        engagementViewModel.initWithContext(context)
    }
    val coinSuccessHaptic = androidx.compose.runtime.rememberUpdatedState(
        com.android.purebilibili.feature.video.ui.components.rememberVideoActionSuccessHaptic(isActive)
    )
    LaunchedEffect(engagementViewModel) {
        engagementViewModel.events.collect { event ->
            when (event) {
                is VideoEngagementEvent.Message -> playerViewModel.toast(event.text)
                is VideoEngagementEvent.CoinSucceeded -> {
                    if (engagementViewModel.uiState.value.subject == event.subject &&
                        com.android.purebilibili.feature.video.viewmodel.matchesVideoEngagementAccount(event.accountSession)
                    ) coinSuccessHaptic.value()
                }
                is VideoEngagementEvent.OpenFollowGroups ->
                    playerViewModel.showFollowGroupDialogForUser(event.mid)
                is VideoEngagementEvent.LoadVideo ->
                    playerViewModel.loadVideo(event.bvid, autoPlay = true)
                VideoEngagementEvent.InvalidateFavoriteFolders ->
                    playerViewModel.invalidateFavoriteFolderCache()
            }
        }
    }
    val seed = remember(seedBvid, seedCid, seedCover, seedTitle) {
        if (seedBvid.isNotBlank()) {
            StoryFeedSeed(
                bvid = seedBvid,
                cid = seedCid,
                cover = seedCover,
                title = seedTitle
            )
        } else {
            null
        }
    }
    val accountIdentity by com.android.purebilibili.core.store.TokenManager.accountIdentity
        .collectAsStateWithLifecycle()
    // 队列归属跟随已落定的标签页；转场暂时停工不能丢弃当前播放位置。
    DisposableEffect(viewModel, seedBvid, isFeedEntrySelected, accountIdentity) {
        if (isFeedEntrySelected) viewModel.startFeed(seedBvid)
        onDispose { viewModel.stopFeed() }
    }
    // 从账号用途设置等覆盖页返回时，重新核对推荐账号，未变化时保留当前队列。
    LaunchedEffect(viewModel, seedBvid, isActive, isFeedEntrySelected, accountIdentity) {
        if (isActive && isFeedEntrySelected) viewModel.startFeed(seedBvid)
    }
    val portraitFeed = remember(uiState.items, seed) {
        buildStoryPortraitFeed(uiState.items, seed = seed)
    }
    var latestExitSnapshot by remember(uiState.sessionId) { mutableStateOf<StoryPortraitExitSnapshot?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            portraitFeed == null && uiState.isLoading -> {
                com.android.purebilibili.core.ui.CutePersonLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            portraitFeed == null && uiState.error != null -> {
                StoryErrorState(
                    message = uiState.error ?: "加载失败",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            portraitFeed == null -> {
                StoryErrorState(
                    message = "暂时没有可播放的推荐视频",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                key(uiState.sessionId) {
                    StoryPagerScope {
                        PortraitVideoPager(
                            initialBvid = portraitFeed.initialInfo.bvid,
                            initialInfo = portraitFeed.initialInfo,
                            recommendations = portraitFeed.recommendations,
                            recommendationsManagedExternally = true,
                            recommendationLoadInProgress = uiState.isLoading,
                            recommendationLoadError = uiState.error,
                            onLoadMoreRecommendations = viewModel::loadMoreStories,
                            onlyVerticalRecommendations = onlyVerticalRecommendations,
                            isActive = isActive,
                            onBack = onBack,
                            onHomeClick = onBack,
                            onVideoChange = viewModel::updateCurrentVideo,
                            viewModel = playerViewModel,
                            engagementViewModel = engagementViewModel,
                            onExitSnapshot = { bvid, _, cid, coverUrl ->
                                latestExitSnapshot = StoryPortraitExitSnapshot(
                                    bvid = bvid,
                                    cid = cid,
                                    coverUrl = coverUrl,
                                )
                            },
                            onSearchClick = onSearchClick,
                            onUserClick = onUserClick,
                            onExitFullscreen = {
                                val snapshot = latestExitSnapshot
                                if (snapshot != null) {
                                    // 带上当前页封面，避免横屏详情首帧闪成 Story 入口种子封面
                                    onVideoClick(snapshot.bvid, snapshot.cid, snapshot.coverUrl)
                                } else {
                                    onRotateToLandscape()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Each resumed queue starts at its unread head, not a page saved for the consumed queue. */
@Composable
internal fun StoryPagerScope(content: @Composable () -> Unit) {
    val parentOwner = LocalViewModelStoreOwner.current
    val application = LocalContext.current.applicationContext as android.app.Application
    val owner = remember(parentOwner, application) {
        val defaults = parentOwner as? HasDefaultViewModelProviderFactory
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore = ViewModelStore()
            override val defaultViewModelProviderFactory =
                defaults?.defaultViewModelProviderFactory
                    ?: ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            override val defaultViewModelCreationExtras: CreationExtras =
                androidx.lifecycle.viewmodel.MutableCreationExtras(
                    defaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty
                ).apply {
                    set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
                }
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalSaveableStateRegistry provides null,
        content = content,
    )
}

@Composable
private fun StoryErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(message, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        AppButton(onClick = onRetry) {
            AppText("重试")
        }
    }
}

private data class StoryPortraitExitSnapshot(
    val bvid: String,
    val cid: Long,
    val coverUrl: String = "",
)
