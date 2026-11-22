package com.android.purebilibili.feature.matchinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.MatchContestInfo
import com.android.purebilibili.data.model.response.MatchContestTeam
import com.android.purebilibili.data.model.response.MATCH_CONTEST_STATUS_FINISHED
import com.android.purebilibili.data.model.response.MATCH_CONTEST_STATUS_LIVING
import com.android.purebilibili.data.model.response.MATCH_CONTEST_STATUS_NOT_STARTED
import com.android.purebilibili.data.model.response.resolveMatchContestLogoUrl
import com.android.purebilibili.feature.video.ui.components.VideoCommentSheetHost
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MATCH_COMMENT_TYPE = 27

@Composable
fun MatchInfoScreen(
    cid: Long,
    onBack: () -> Unit,
    onLiveClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MatchInfoViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(cid) { viewModel.load(cid) }

    val commentViewModel: VideoCommentViewModel = viewModel()
    var showComments by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(cid, showComments) {
        if (showComments && cid > 0L) {
            commentViewModel.init(aid = cid, commentType = MATCH_COMMENT_TYPE)
        }
    }
    if (showComments) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.android.purebilibili.feature.share.LocalReplyShareContext provides
                com.android.purebilibili.feature.share.matchReplyShareContext(
                    cid = cid,
                    title = state.contest?.season?.title?.ifBlank { null } ?: "比赛详情",
                ),
        ) {
            VideoCommentSheetHost(
                mainSheetVisible = true,
                onDismiss = { showComments = false },
                commentViewModel = commentViewModel,
                aid = cid,
                commentType = MATCH_COMMENT_TYPE,
                onUserClick = onUserClick,
                onOpenBilibiliLink = onOpenLink,
            )
        }
    }

    ImmersiveAppScaffold(
        modifier = modifier,
        blurContentReady = !state.loading,
        topBar = {
            AppTopBar(
                title = "比赛详情",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = AppSurfaceTokens.background(),
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contest = state.contest
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppCircularProgressIndicator()
                }
                contest == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    AppText(state.error ?: "暂无比赛信息", color = AppContentColors.secondaryText())
                    AppButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        AppText(if (state.refreshing) "刷新中…" else "重试")
                    }
                }
                else -> AdaptivePullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MatchContestCard(
                        contest = contest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        onLiveClick = onLiveClick,
                        onPlaybackClick = { playback ->
                            val bvid = matchPlaybackBvid(playback)
                            if (bvid != null) onVideoClick(bvid) else onOpenLink(playback)
                        },
                        onCommentsClick = { showComments = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchContestCard(
    contest: MatchContestInfo,
    modifier: Modifier = Modifier,
    onLiveClick: (Long) -> Unit,
    onPlaybackClick: (String) -> Unit,
    onCommentsClick: () -> Unit,
) {
    AppCard(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(
                    contest.season?.title?.ifBlank { null } ?: "游戏赛事",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    buildString {
                        if (contest.gameStage.isNotBlank()) {
                            append(contest.gameStage)
                            append(" · ")
                        }
                        append(
                            when (contest.contestStatus) {
                                MATCH_CONTEST_STATUS_LIVING -> "直播中"
                                MATCH_CONTEST_STATUS_FINISHED ->
                                    "已结束 · " + formatMatchTime(contest.startTime, "yyyy-MM-dd")
                                MATCH_CONTEST_STATUS_NOT_STARTED ->
                                    "未开始 · " + formatMatchTime(contest.startTime, "yyyy-MM-dd HH:mm")
                                else -> "时间待定"
                            }
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (contest.contestStatus == MATCH_CONTEST_STATUS_LIVING) {
                        AppSurfaceTokens.primary()
                    } else {
                        AppContentColors.secondaryText()
                    },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatchTeamContent(contest.homeTeam, Modifier.weight(1f))
                Column(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val knownScores = contest.homeScore?.takeIf { it >= 0 }?.let { home ->
                        contest.awayScore?.takeIf { it >= 0 }?.let { away -> "$home : $away" }
                    }
                    AppText(
                        if (contest.contestStatus in MATCH_CONTEST_STATUS_LIVING..MATCH_CONTEST_STATUS_FINISHED) {
                            knownScores ?: "— : —"
                        } else {
                            "VS"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                MatchTeamContent(contest.awayTeam, Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (contest.contestStatus == MATCH_CONTEST_STATUS_LIVING && contest.liveRoom > 0L) {
                    AppButton(
                        onClick = { onLiveClick(contest.liveRoom) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppSurfaceTokens.primary(),
                            contentColor = AppContentColors.onAccent(),
                        ),
                    ) { AppText("观看直播") }
                } else if (contest.liveRoom > 0L) {
                    AppOutlinedButton(onClick = { onLiveClick(contest.liveRoom) }) { AppText("直播间") }
                }
                if (contest.playback.isNotBlank()) {
                    AppOutlinedButton(onClick = { onPlaybackClick(contest.playback) }) { AppText("回放") }
                }
                AppOutlinedButton(onClick = onCommentsClick) { AppText("评论") }
            }
        }
    }
}

@Composable
private fun MatchTeamContent(team: MatchContestTeam?, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val logo = team.resolveMatchContestLogoUrl()
        if (logo.isNotBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(50.dp),
            )
        } else {
            Spacer(Modifier.size(50.dp))
        }
        AppText(
            team?.title?.ifBlank { "待定" } ?: "待定",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatMatchTime(epochSeconds: Long, pattern: String): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern))

/** 与 LiveMatchScreen 的回放解析一致：playback 链接里提取 BV 号跳原生视频页。 */
private fun matchPlaybackBvid(url: String): String? =
    Regex("(?:^|/)BV[0-9A-Za-z]{10}(?=$|[/?#])").find(url)?.value?.substringAfterLast('/')
