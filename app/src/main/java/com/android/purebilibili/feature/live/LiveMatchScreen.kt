package com.android.purebilibili.feature.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.LiveMatchContest
import com.android.purebilibili.data.model.response.LiveMatchTeam
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid

@Composable
fun LiveMatchScreen(
    onBack: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    onCommentClick: (Long, Long) -> Unit,
    onEsportsHomeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LiveMatchViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    LiveMatchContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSelectGame = viewModel::selectGame,
        onSelectDate = viewModel::selectDate,
        onRefresh = viewModel::refresh,
        onRetrySeasons = viewModel::loadSeasons,
        onLoadMore = viewModel::loadMore,
        onLiveClick = onLiveClick,
        onVideoClick = onVideoClick,
        onLoginClick = onLoginClick,
        onCommentClick = onCommentClick,
        onEsportsHomeClick = onEsportsHomeClick,
    )
}

@Composable
private fun LiveMatchContent(
    state: LiveMatchUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSelectGame: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onRetrySeasons: () -> Unit,
    onLoadMore: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    onCommentClick: (Long, Long) -> Unit,
    onEsportsHomeClick: () -> Unit,
) {
    var selectedGradeContest by rememberSaveable { mutableLongStateOf(0L) }
    if (selectedGradeContest > 0L) {
        LiveMatchGradeDialog(
            contestId = selectedGradeContest,
            onDismiss = { selectedGradeContest = 0L },
            onLiveClick = onLiveClick,
            onVideoClick = onVideoClick,
            onLoginClick = onLoginClick,
            onCommentClick = onCommentClick,
        )
    }
    var seasonMenu by remember(state.gameId) { mutableStateOf(false) }
    var selectedSeasonId by rememberSaveable { mutableLongStateOf(0L) }
    if (selectedSeasonId > 0L) {
        LiveMatchSeasonSheet(
            seasonId = selectedSeasonId,
            onDismiss = { selectedSeasonId = 0L },
            onLiveClick = { room, title, name -> selectedSeasonId = 0L; onLiveClick(room, title, name) },
            onVideoClick = { bvid -> selectedSeasonId = 0L; onVideoClick(bvid) },
            onLoginClick = { selectedSeasonId = 0L; onLoginClick() },
            onCommentClick = { aid, root -> selectedSeasonId = 0L; onCommentClick(aid, root) },
        )
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.gameId, state.date) { gridState.scrollToItem(0) }
    ImmersiveAppScaffold(
        modifier = modifier,
        blurContentReady = !state.loading,
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                AppTopBar(
                    title = "游戏赛事",
                    navigationIcon = {
                        AppIconButton(onClick = onBack) {
                            AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        AppIconButton(onClick = onEsportsHomeClick) {
                            AppIcon(Icons.Outlined.Public, contentDescription = "官方赛事主页")
                        }
                        AppIconButton(onClick = onRefresh, enabled = !state.loading) {
                            AppIcon(Icons.Outlined.Refresh, contentDescription = "刷新赛程与分类")
                        }
                    },
                )
                // 赛事分类固定使用当前主题的原生组件，玻璃开关不改变分类栏材质和槽位。
                CompositionLocalProvider(
                    LocalAppThemeConfig provides LocalAppThemeConfig.current.copy(liquidGlassEnabled = false, popupGlassEnabled = false, playerGlassEnabled = false),
                ) {
                    AppNativeTabRow(
                        options = listOf(AppSegmentOption(0, "全部")) + state.games.map { AppSegmentOption(it.id, it.name) },
                        selectedValue = state.gameId,
                        onSelectionChange = onSelectGame,
                        scrollable = true,
                        indicatorPresentation = AppTabRowIndicatorPresentation.TONAL_PILL,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.gameId > 0) {
                    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(Modifier.padding(horizontal = 16.dp), onOpen = {
                        if (state.seasonsError != null) onRetrySeasons() else seasonMenu = true
                    }, enabled = !state.seasonsLoading) {
                        AppTextButton(enabled = !state.seasonsLoading, onClick = {
                            if (state.seasonsError != null) onRetrySeasons() else seasonMenu = true
                        }) {
                            AppText(when {
                                state.seasonsLoading -> "加载赛季…"
                                state.seasonsError != null -> "赛季列表加载失败，点击重试"
                                else -> "选择赛季与查看排名"
                            })
                        }
                        AppDropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                            if (state.seasons.isEmpty()) {
                                AppDropdownMenuItem(text = { AppText("暂无可用赛季") }, enabled = false, onClick = {})
                            }
                            state.seasons.forEach { season ->
                                AppDropdownMenuItem(text = { AppText(season.title.ifBlank { "赛季 ${season.id}" }) }, onClick = {
                                    seasonMenu = false
                                    selectedSeasonId = season.id
                                })
                            }
                        }
                    }
                }
                if (state.gamesError != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText("分类加载失败，可继续查看全部赛程", modifier = Modifier.weight(1f), color = AppContentColors.secondaryText())
                        AppTextButton(onClick = onRefresh) { AppText("重试") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = { onSelectDate(state.date.minusDays(1)) }) {
                        AppIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
                    }
                    AppText(
                        state.date.format(DateTimeFormatter.ofPattern("MM月dd日 E", Locale.SIMPLIFIED_CHINESE)),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    AppTextButton(onClick = { onSelectDate(LocalDate.now()) }, enabled = state.date != LocalDate.now()) {
                        AppText("今天")
                    }
                    AppIconButton(onClick = { onSelectDate(state.date.plusDays(1)) }) {
                        AppIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
                    }
                }
            }
        },
        containerColor = AppSurfaceTokens.background(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { AppCircularProgressIndicator() }
                state.contests.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    AppText(state.error ?: "当天暂无赛程", color = AppContentColors.secondaryText())
                    AppButton(onClick = onRefresh) { AppText(if (state.error != null) "重试" else "刷新赛程") }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    state = gridState,
                    modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = maxOf(innerPadding.calculateBottomPadding(), 24.dp),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.contests, key = { it.id }) { contest ->
                        LiveMatchCard(
                            contest = contest,
                            modifier = Modifier.fillMaxWidth(),
                            onLiveClick = onLiveClick,
                            onVideoClick = onVideoClick,
                            onSeasonClick = { selectedSeasonId = it },
                            onGradesClick = { selectedGradeContest = it },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                            state.error?.let { AppText(it, color = AppSurfaceTokens.error()) }
                            if (state.loadingMore) AppCircularProgressIndicator()
                            else if (state.hasMore) AppTextButton(onClick = onLoadMore) {
                                AppText(if (state.error != null) "重试加载更多" else "加载更多")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveMatchCard(
    contest: LiveMatchContest,
    modifier: Modifier = Modifier,
    onLiveClick: (Long, String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    onSeasonClick: ((Long) -> Unit)? = null,
    onGradesClick: ((Long) -> Unit)? = null,
) {
    val title = listOfNotNull(contest.season?.title?.takeIf { it.isNotBlank() }, contest.stage.takeIf { it.isNotBlank() }).joinToString(" · ")
    val playbackBvid = liveMatchPlaybackBvid(contest.playback)
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText(title.ifBlank { "游戏赛事" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val seasonId = contest.season?.id ?: 0L
            if (seasonId > 0L && onSeasonClick != null) {
                AppTextButton(onClick = { onSeasonClick(seasonId) }) { AppText("赛季详情与排名") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LiveMatchTeamContent(contest.home, Modifier.weight(1f))
                Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val knownScores = contest.homeScore?.takeIf { it >= 0 }?.let { home ->
                        contest.awayScore?.takeIf { it >= 0 }?.let { away -> "$home : $away" }
                    }
                    AppText(if (contest.status in 2..3) knownScores ?: "— : —" else "VS", style = MaterialTheme.typography.titleLarge)
                    AppText(
                        when (contest.status) { 1 -> "未开始"; 2 -> "进行中"; 3 -> "已结束"; else -> "待确认" },
                        color = if (contest.status == 2) AppSurfaceTokens.primary() else AppContentColors.secondaryText(),
                    )
                }
                LiveMatchTeamContent(contest.away, Modifier.weight(1f))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(
                    if (contest.startTime > 0) Instant.ofEpochSecond(contest.startTime).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm")) else "时间待定",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = AppContentColors.secondaryText(),
                )
                if (onGradesClick != null && contest.status in 2..3) AppTextButton(onClick = { onGradesClick(contest.id) }) { AppText("评分") }
                if (playbackBvid != null) AppTextButton(onClick = { onVideoClick(playbackBvid) }) { AppText("看回放") }
                if (contest.liveRoom > 0) AppTextButton(onClick = { onLiveClick(contest.liveRoom, title, "") }) {
                    AppText(if (contest.status == 2) "观看直播" else "直播间")
                }
            }
        }
    }
}

@Composable
private fun LiveMatchTeamContent(team: LiveMatchTeam?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val icon = team?.icon.orEmpty()
        if (icon.isNotBlank()) AsyncImage(
            model = liveMatchImageUrl(icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(40.dp),
        ) else Spacer(Modifier.size(40.dp))
        AppText(team?.name?.ifBlank { "待定" } ?: "待定", maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
