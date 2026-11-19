package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.navigation.animatePagerSelection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.feature.audio.library.ListenVideoAlbum
import com.android.purebilibili.feature.audio.library.ListenVideoArtist
import com.android.purebilibili.feature.audio.library.ListenVideoPlaylist
import com.android.purebilibili.feature.audio.library.ListenVideoSection
import com.android.purebilibili.feature.audio.library.ListenVideoTrack
import com.android.purebilibili.feature.audio.viewmodel.ListenVideoUiState
import com.android.purebilibili.feature.audio.viewmodel.ListenVideoViewModel
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.video.player.PlaylistManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

internal data class ListenVideoNowPlaying(
    val bvid: String,
    val title: String,
    val artist: String,
    val coverUrl: String
)

@Composable
internal fun ListenVideoRoute(
    onPlayTracks: (List<ListenVideoTrack>, String) -> Unit,
    onNowPlayingClick: (bvid: String, coverUrl: String) -> Unit,
    onLogin: () -> Unit,
    onPlayCollection: (mediaId: Long, seasonId: Long, title: String) -> Unit,
    onPlayFromHistory: (bvid: String, cid: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListenVideoViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPlayHistory by rememberSaveable { mutableStateOf(false) }
    val playlistState by PlaylistManager.uiState.collectAsStateWithLifecycle(
        initialValue = com.android.purebilibili.feature.video.player.PlaylistUiState()
    )
    val nowPlaying = remember(playlistState.playlist, playlistState.currentIndex) {
        playlistState.playlist.getOrNull(playlistState.currentIndex)?.let { item ->
            ListenVideoNowPlaying(
                bvid = item.bvid,
                title = item.title,
                artist = item.owner,
                coverUrl = item.cover
            )
        }
    }

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.resumeBackgroundLoading()
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        viewModel.pauseBackgroundLoading()
    }

    ListenVideoScreen(
        state = state,
        nowPlaying = nowPlaying,
        onNowPlayingClick = onNowPlayingClick,
        onRefresh = viewModel::refresh,
        onOpenHistory = { showPlayHistory = true },
        onSectionSelected = viewModel::selectSection,
        onPlaylistSelected = { playlist ->
            viewModel.pauseBackgroundLoading()
            onPlayCollection(playlist.mediaId, 0L, playlist.title)
        },
        onAlbumSelected = { album ->
            viewModel.pauseBackgroundLoading()
            onPlayCollection(0L, album.seasonId, album.title)
        },
        onArtistSelected = { artist ->
            artist.tracks.firstOrNull()?.let { first ->
                viewModel.pauseBackgroundLoading()
                onPlayTracks(artist.tracks, first.bvid)
            }
        },
        onCloseDetail = viewModel::closeDetail,
        onRetryDetail = viewModel::retryDetail,
        onRetryIndex = viewModel::retryFailedIndex,
        onTrackSelected = onPlayTracks,
        onLogin = onLogin,
        modifier = modifier
    )

    if (showPlayHistory) {
        PlayHistorySheet(
            onPlay = { entry ->
                showPlayHistory = false
                viewModel.pauseBackgroundLoading()
                onPlayFromHistory(entry.bvid, entry.cid)
            },
            onDismiss = { showPlayHistory = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListenVideoScreen(
    state: ListenVideoUiState,
    nowPlaying: ListenVideoNowPlaying?,
    onNowPlayingClick: (bvid: String, coverUrl: String) -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    onSectionSelected: (ListenVideoSection) -> Unit,
    onPlaylistSelected: (ListenVideoPlaylist) -> Unit,
    onAlbumSelected: (ListenVideoAlbum) -> Unit,
    onArtistSelected: (ListenVideoArtist) -> Unit,
    onCloseDetail: () -> Unit,
    onRetryDetail: () -> Unit,
    onRetryIndex: () -> Unit,
    onTrackSelected: (List<ListenVideoTrack>, String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionLabels = remember { listOf("播放列表", "专辑", "歌手") }
    val pagerState = rememberPagerState(
        initialPage = state.section.ordinal,
        pageCount = { ListenVideoSection.entries.size }
    )
    val scope = rememberCoroutineScope()
    val listenBackdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val homeSettings by remember(context) { SettingsManager.getHomeSettings(context) }
        .collectAsStateWithLifecycle(initialValue = HomeSettings())

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> onSectionSelected(ListenVideoSection.entries[page]) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                        AppSurfaceTokens.chromeBackground(),
                        AppSurfaceTokens.chromeBackground()
                    )
                )
            )
    ) {
        val layout = resolveListenVideoLayout(maxWidth.value.toInt())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(listenBackdrop)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                            AppSurfaceTokens.chromeBackground(),
                            AppSurfaceTokens.chromeBackground(),
                        )
                    )
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ListenVideoHeader(
                nowPlaying = nowPlaying,
                onNowPlayingClick = onNowPlayingClick,
                onRefresh = onRefresh,
                onOpenHistory = onOpenHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
            BottomBarLiquidSegmentedControl(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                items = sectionLabels,
                selectedIndex = pagerState.tabSelectedPage,
                onSelected = { index ->
                    scope.launch { animatePagerSelection(pagerState, index) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
                indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
                // Center only the compact glass rail; native tabs retain their layout.
                centerGlassContent = true,
                preferInlineContentStyle = false,
                miuixBackdrop = listenBackdrop,
                liquidGlassEffectsEnabled = true,
                dragSelectionEnabled = sectionLabels.size > 1,
                tapPressRefractionEnabled = true,
                indicatorPositionProvider = {
                    pagerState.tabIndicatorPosition
                },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
                externalPagerMotionEffectsEnabled = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f)
                    .verticalPriorityHorizontalPagerSwipe(pagerState),
            ) { page ->
                ListenVideoPage(
                    section = ListenVideoSection.entries[page],
                    state = state,
                    layout = layout,
                    onPlaylistSelected = onPlaylistSelected,
                    onAlbumSelected = onAlbumSelected,
                    onArtistSelected = onArtistSelected,
                    onRetryIndex = onRetryIndex,
                    onRefresh = onRefresh,
                    onLogin = onLogin,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (state.selectedTitle.isNotBlank()) {
        AppModalBottomSheet(
            onDismissRequest = onCloseDetail,
            containerColor = AppSurfaceTokens.surface().copy(alpha = 0.96f)
        ) {
            ListenVideoTrackSheet(
                title = state.selectedTitle,
                tracks = state.selectedTracks,
                isLoading = state.isDetailLoading,
                error = state.detailError,
                onClose = onCloseDetail,
                onRetry = onRetryDetail,
                onTrackSelected = onTrackSelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ListenVideoHeader(
    nowPlaying: ListenVideoNowPlaying?,
    onNowPlayingClick: (bvid: String, coverUrl: String) -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "听视频",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                AppText(
                    text = "收藏夹是播放列表，视频集是专辑，UP 主是歌手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText()
                )
            }
            AppIconButton(onClick = onOpenHistory, modifier = Modifier.size(40.dp)) {
                AppIcon(Icons.Filled.History, contentDescription = "最近播放")
            }
            Spacer(modifier = Modifier.width(4.dp))
            AppIconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                AppIcon(Icons.Filled.Refresh, contentDescription = "刷新音乐资料")
            }
        }
        nowPlaying?.let { item ->
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNowPlayingClick(item.bvid, item.coverUrl) },
                shape = AppShapes.container(ContainerLevel.Card),
                color = AppSurfaceTokens.surface().copy(alpha = 0.58f),
                // primary 描边区分「正在播放」与下方实体卡
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    AppContentColors.accent().copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(AppShapes.container(ContainerLevel.Field)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText(
                                text = "正在播放",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.accent()
                            )
                        }
                        AppText(
                            text = item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        AppText(
                            text = item.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListenVideoPage(
    section: ListenVideoSection,
    state: ListenVideoUiState,
    layout: ListenVideoLayout,
    onPlaylistSelected: (ListenVideoPlaylist) -> Unit,
    onAlbumSelected: (ListenVideoAlbum) -> Unit,
    onArtistSelected: (ListenVideoArtist) -> Unit,
    onRetryIndex: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        !state.isLoggedIn -> ListenVideoMessage(
            title = "登录后查看音乐资料",
            actionLabel = "去登录",
            onAction = onLogin,
            modifier = modifier
        )

        state.isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AdaptiveLoadingIndicator()
        }

        state.error != null && state.playlists.isEmpty() -> ListenVideoMessage(
            title = state.error,
            actionLabel = "重试",
            onAction = onRefresh,
            modifier = modifier
        )

        else -> when (section) {
            ListenVideoSection.PLAYLISTS -> ListenVideoPlaylistPage(
                playlists = state.playlists,
                layout = layout,
                onSelected = onPlaylistSelected,
                modifier = modifier
            )

            ListenVideoSection.ALBUMS -> ListenVideoAlbumPage(
                albums = state.albums,
                layout = layout,
                isIndexing = state.isIndexing,
                indexedCount = state.indexedFolderCount,
                totalCount = state.totalFolderCount,
                onSelected = onAlbumSelected,
                modifier = modifier
            )

            ListenVideoSection.ARTISTS -> ListenVideoArtistPage(
                artists = state.artists,
                layout = layout,
                isIndexing = state.isIndexing,
                indexedCount = state.indexedFolderCount,
                totalCount = state.totalFolderCount,
                failedFolderCount = state.failedFolderIds.size,
                onRetryIndex = onRetryIndex,
                onSelected = onArtistSelected,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ListenVideoPlaylistPage(
    playlists: List<ListenVideoPlaylist>,
    layout: ListenVideoLayout,
    onSelected: (ListenVideoPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        ListenVideoMessage("还没有可播放的收藏夹", modifier = modifier)
        return
    }
    if (layout == ListenVideoLayout.WIDE_GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = modifier,
            contentPadding = libraryContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(playlists, key = ListenVideoPlaylist::mediaId) { playlist ->
                MusicEntityCard(
                    title = playlist.title,
                    subtitle = "${playlist.trackCount} 首 · 播放列表",
                    coverUrl = playlist.coverUrl,
                    fallbackIcon = Icons.Filled.LibraryMusic,
                    onClick = { onSelected(playlist) },
                    alignTitleLines = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = libraryContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = ListenVideoPlaylist::mediaId) { playlist ->
                MusicEntityCard(
                    title = playlist.title,
                    subtitle = "${playlist.trackCount} 首 · 播放列表",
                    coverUrl = playlist.coverUrl,
                    fallbackIcon = Icons.Filled.LibraryMusic,
                    onClick = { onSelected(playlist) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ListenVideoAlbumPage(
    albums: List<ListenVideoAlbum>,
    layout: ListenVideoLayout,
    isIndexing: Boolean,
    indexedCount: Int,
    totalCount: Int,
    onSelected: (ListenVideoAlbum) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty() && !isIndexing) {
        ListenVideoMessage("收藏夹中还没有视频合集", modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        ListenVideoIndexProgress(isIndexing, indexedCount, totalCount)
        if (layout == ListenVideoLayout.WIDE_GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.weight(1f),
                contentPadding = libraryContentPadding(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(albums, key = ListenVideoAlbum::seasonId) { album ->
                    MusicEntityCard(
                        title = album.title,
                        subtitle = album.artistName.ifBlank { "${album.trackCount} 首 · 专辑" },
                        coverUrl = album.coverUrl,
                        fallbackIcon = Icons.Filled.Album,
                        onClick = { onSelected(album) },
                        alignTitleLines = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = libraryContentPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums, key = ListenVideoAlbum::seasonId) { album ->
                    MusicEntityCard(
                        title = album.title,
                        subtitle = album.artistName.ifBlank { "${album.trackCount} 首 · 专辑" },
                        coverUrl = album.coverUrl,
                        fallbackIcon = Icons.Filled.Album,
                        onClick = { onSelected(album) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenVideoArtistPage(
    artists: List<ListenVideoArtist>,
    layout: ListenVideoLayout,
    isIndexing: Boolean,
    indexedCount: Int,
    totalCount: Int,
    failedFolderCount: Int,
    onRetryIndex: () -> Unit,
    onSelected: (ListenVideoArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty() && !isIndexing) {
        ListenVideoMessage(
            title = if (failedFolderCount > 0) "部分收藏夹索引失败" else "收藏夹中还没有可识别的 UP 主",
            actionLabel = if (failedFolderCount > 0) "重试" else null,
            onAction = onRetryIndex,
            modifier = modifier
        )
        return
    }
    Column(modifier = modifier) {
        ListenVideoIndexProgress(isIndexing, indexedCount, totalCount)
        if (layout == ListenVideoLayout.WIDE_GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.weight(1f),
                contentPadding = libraryContentPadding(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(artists, key = ListenVideoArtist::mid) { artist ->
                    MusicEntityCard(
                        title = artist.name,
                        subtitle = "${artist.tracks.size} 首 · 歌手",
                        coverUrl = artist.avatarUrl,
                        fallbackIcon = Icons.Filled.Person,
                        circularCover = true,
                        onClick = { onSelected(artist) },
                        alignTitleLines = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = libraryContentPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(artists, key = ListenVideoArtist::mid) { artist ->
                    MusicEntityCard(
                        title = artist.name,
                        subtitle = "${artist.tracks.size} 首 · 歌手",
                        coverUrl = artist.avatarUrl,
                        fallbackIcon = Icons.Filled.Person,
                        circularCover = true,
                        onClick = { onSelected(artist) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenVideoIndexProgress(
    isIndexing: Boolean,
    indexedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    if (!isIndexing) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        AppText(
            text = "正在整理收藏夹 $indexedCount/$totalCount",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText()
        )
    }
}

@Composable
private fun MusicEntityCard(
    title: String,
    subtitle: String,
    coverUrl: String,
    fallbackIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circularCover: Boolean = false,
    alignTitleLines: Boolean = false,
) {
    AppSurface(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clickable(onClick = onClick),
        shape = AppShapes.borderedContainer(ContainerLevel.Card),
        color = AppSurfaceTokens.surface().copy(alpha = 0.62f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            AppContentColors.primaryText().copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(
                        if (circularCover) CircleShape else AppShapes.container(ContainerLevel.Dialog)
                    )
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AppIcon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    minLines = if (alignTitleLines) 2 else 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AppText(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }
            AppIcon(Icons.Filled.PlayArrow, contentDescription = null)
        }
    }
}

@Composable
private fun ListenVideoMessage(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(
            Icons.Filled.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = AppContentColors.accent()
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium)
        actionLabel?.let { label ->
            Spacer(modifier = Modifier.height(16.dp))
            AppButton(onClick = onAction, modifier = Modifier.height(48.dp)) {
                AppText(label)
            }
        }
    }
}

@Composable
private fun ListenVideoTrackSheet(
    title: String,
    tracks: List<ListenVideoTrack>,
    isLoading: Boolean,
    error: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onTrackSelected: (List<ListenVideoTrack>, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppIconButton(onClick = onClose) {
                AppIcon(Icons.Filled.Close, contentDescription = "关闭曲目列表")
            }
        }
        when {
            isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                AdaptiveLoadingIndicator()
            }

            error != null -> ListenVideoMessage(
                title = error,
                actionLabel = "重试",
                onAction = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            tracks.isEmpty() -> ListenVideoMessage(
                title = "这个列表中没有可播放的视频",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(tracks, key = ListenVideoTrack::bvid) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackSelected(tracks, track.bvid) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(AppShapes.container(ContainerLevel.Field)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            AppText(
                                track.artistName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText()
                            )
                        }
                        AppIcon(Icons.Filled.PlayArrow, contentDescription = "播放 ${track.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun libraryContentPadding(): PaddingValues {
    val bottomDockClearance = com.android.purebilibili.core.ui.LocalBottomBarContentPadding.current
    return PaddingValues(
        start = 20.dp,
        top = 10.dp,
        end = 20.dp,
        bottom = maxOf(120.dp, bottomDockClearance),
    )
}
