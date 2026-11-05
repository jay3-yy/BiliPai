package com.android.purebilibili.feature.live

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.feature.live.components.LiveMatchBracket
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveMatchSeasonSheet(
    seasonId: Long,
    onDismiss: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    onCommentClick: (Long, Long) -> Unit,
) {
    val model: LiveMatchSeasonViewModel = viewModel(key = "live-match-season")
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model, seasonId) { model.start(seasonId) }
    DisposableEffect(model) { onDispose { model.stop() } }
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        LiveMatchSeasonContent(
            state = state,
            onRefresh = model::refresh,
            onLoadMore = model::loadMore,
            onRetrySchedule = model::retrySchedule,
            onDismiss = onDismiss,
            onLiveClick = onLiveClick,
            onVideoClick = onVideoClick,
            onLoginClick = onLoginClick,
            onCommentClick = onCommentClick,
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun LiveMatchSeasonContent(
    state: LiveMatchSeasonUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetrySchedule: () -> Unit,
    onDismiss: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    onCommentClick: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedGradeContest by rememberSaveable { mutableLongStateOf(0L) }
    if (selectedGradeContest > 0L) {
        LiveMatchGradeDialog(
            contestId = selectedGradeContest,
            onDismiss = { selectedGradeContest = 0L },
            onLiveClick = { room, title, name -> onDismiss(); onLiveClick(room, title, name) },
            onVideoClick = { bvid -> onDismiss(); onVideoClick(bvid) },
            onLoginClick = { onDismiss(); onLoginClick() },
            onCommentClick = { aid, root -> onDismiss(); onCommentClick(aid, root) },
        )
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    val stages = state.detail?.list.orEmpty().filter { it.series?.visible == true }
    var selectedStage by rememberSaveable(state.detail?.season?.id) { mutableLongStateOf(0L) }
    var stageMenu by remember { mutableStateOf(false) }
    val active = stages.firstOrNull { it.series?.id == selectedStage } ?: stages.firstOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppText(state.detail?.season?.title?.ifBlank { "赛季详情" } ?: "赛季详情", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            AppTextButton(enabled = !state.loading && !state.scheduleLoading, onClick = onRefresh) { AppText("刷新") }
            AppTextButton(onClick = onDismiss) { AppText("关闭") }
        }
        state.detail?.season?.let { season ->
            if (season.startTime > 0 && season.endTime > 0) {
                val format = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())
                AppText("${format.format(Instant.ofEpochSecond(season.startTime))} — ${format.format(Instant.ofEpochSecond(season.endTime))}",
                    modifier = Modifier.padding(horizontal = 16.dp), color = AppContentColors.secondaryText())
            }
        }
        // 赛季筛选与赛事分类共用原生材质边界，不随玻璃开关转换为 dock。
        CompositionLocalProvider(LocalAppThemeConfig provides LocalAppThemeConfig.current.copy(liquidGlassEnabled = false, popupGlassEnabled = false, playerGlassEnabled = false)) {
            AppNativeTabRow(
                options = listOf(AppSegmentOption(0, "晋级与积分"), AppSegmentOption(1, "全部赛程")),
                selectedValue = tab, onSelectionChange = { tab = it },
                modifier = Modifier.fillMaxWidth(),
                indicatorPresentation = AppTabRowIndicatorPresentation.TONAL_PILL,
            )
        }
        if (tab == 0) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { AppCircularProgressIndicator() }
                state.error != null -> Column(Modifier.padding(16.dp)) {
                    AppText(state.error, color = AppContentColors.secondaryText())
                    AppTextButton(onClick = onRefresh) { AppText("重试") }
                }
                stages.isEmpty() -> AppText("该赛季暂无阶段与积分数据", modifier = Modifier.padding(16.dp))
                else -> {
                    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(Modifier.padding(horizontal = 16.dp), onOpen = { stageMenu = true }) {
                        AppTextButton(onClick = { stageMenu = true }) { AppText(stageTitle(active?.series)) }
                        AppDropdownMenu(expanded = stageMenu, onDismissRequest = { stageMenu = false }) {
                            stages.forEach { entry ->
                                AppDropdownMenuItem(text = { AppText(stageTitle(entry.series)) }, onClick = {
                                    selectedStage = entry.series?.id ?: 0L
                                    stageMenu = false
                                })
                            }
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        active?.series?.description?.takeIf { it.isNotBlank() }?.let { description -> item { AppText(description) } }
                        val standing = active?.point
                        val groups = standing?.let(::liveMatchStandingGroups).orEmpty()
                        val bracket = active?.knockout?.groups.orEmpty()
                        if (bracket.any { it.children.isNotEmpty() }) {
                            item {
                                AppText("对阵与晋级 · 横向查看完整轮次", color = AppContentColors.secondaryText())
                                LiveMatchBracket(bracket, onOpenGrades = { selectedGradeContest = it }, modifier = Modifier.fillMaxWidth())
                            }
                        } else if (bracket.isNotEmpty()) {
                            item { AppText("阶段对阵", fontWeight = FontWeight.SemiBold) }
                            items(bracket) { node ->
                                val contest = node.contest
                                if (contest != null) LiveMatchCard(
                                    contest = contest, onLiveClick = onLiveClick, onVideoClick = onVideoClick,
                                    onGradesClick = { selectedGradeContest = it }, modifier = Modifier.fillMaxWidth(),
                                )
                                else AppText("${node.homeName.ifBlank { "待定" }} VS ${node.awayName.ifBlank { "待定" }}")
                            }
                        }
                        if (groups.all { it.teams.isEmpty() }) {
                            if (bracket.isEmpty()) item { AppText("当前阶段未提供对阵与积分榜，可在全部赛程查看结果。", color = AppContentColors.secondaryText()) }
                        } else groups.forEachIndexed { index, group ->
                            item(key = "group:$index") {
                                LiveMatchStandingTable(group, standing!!, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.contests, key = { it.id }) { contest ->
                    LiveMatchCard(contest = contest, onLiveClick = onLiveClick, onVideoClick = onVideoClick,
                        onGradesClick = { selectedGradeContest = it }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.scheduleLoading) AppCircularProgressIndicator()
                        else if (state.scheduleError != null) {
                            AppText(state.scheduleError, color = AppContentColors.secondaryText())
                            AppTextButton(onClick = onRetrySchedule) { AppText("重试赛程") }
                        } else if (state.contests.isEmpty()) AppText("该赛季暂无赛程")
                        else if (state.hasMore) AppTextButton(onClick = onLoadMore) { AppText("加载更多") }
                    }
                }
            }
        }
    }
}

private fun stageTitle(stage: LiveMatchStage?): String =
    listOfNotNull(stage?.title?.takeIf { it.isNotBlank() }, stage?.subtitle?.takeIf { it.isNotBlank() })
        .distinct().joinToString(" · ").ifBlank { "比赛阶段" }

@Composable
private fun LiveMatchStandingTable(group: LiveMatchStandingGroup, standing: LiveMatchStanding, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (group.name.isNotBlank()) AppText(group.name, fontWeight = FontWeight.SemiBold)
            // 表格独立横向浏览；窄窗口保留完整列宽，不压缩队名与比分。
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                StandingRow("排名", "队伍", "胜", "负", if (standing.showDraws) "平" else null, "积分", if (standing.showSmallScore) "净胜分" else null)
                group.teams.forEach { team ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        AppText(team.rank?.toString() ?: "—", modifier = Modifier.width(44.dp))
                        Row(Modifier.width(144.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = liveMatchImageUrl(team.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            AppText(team.name.ifBlank { "待定" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        StandingValue(team.wins)
                        StandingValue(team.losses)
                        if (standing.showDraws) StandingValue(team.draws)
                        StandingValue(team.score)
                        if (standing.showSmallScore) StandingValue(team.smallScore)
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingRow(rank: String, team: String, wins: String, losses: String, draws: String?, score: String, smallScore: String?) {
    Row {
        AppText(rank, modifier = Modifier.width(44.dp))
        AppText(team, modifier = Modifier.width(144.dp))
        listOfNotNull(wins, losses, draws, score, smallScore).forEach { AppText(it, modifier = Modifier.width(56.dp)) }
    }
}

@Composable
private fun StandingValue(value: Int?) { AppText(value?.toString() ?: "—", modifier = Modifier.width(56.dp)) }
