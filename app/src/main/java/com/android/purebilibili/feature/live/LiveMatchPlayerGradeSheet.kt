package com.android.purebilibili.feature.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.LiveMatchPlayerGrade
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveMatchPlayerGradeSheet(
    player: LiveMatchPlayerGrade,
    state: LiveMatchGradeState,
    comments: LiveMatchGradeComments,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
    onLoadComments: (Int, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onCommentClick: (Long) -> Unit,
) {
    var score by rememberSaveable(player.gradeId, state.game, state.account) { mutableIntStateOf(player.userScore?.takeIf { player.rated } ?: -1) }
    var draft by rememberSaveable(player.gradeId, state.game, state.account) { mutableStateOf("") }
    var scoreMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val loggedIn = state.account.mid != null && !state.requiresLogin
    val canComment = state.data?.aid?.let { it > 0 } == true && player.gradeId.toLongOrNull()?.let { it > 0 } == true
    // 评论发布确认后清空草稿；单独打分及网络失败均保留草稿。
    var observedVersion by remember { mutableIntStateOf(state.submissionVersion) }
    LaunchedEffect(state.submissionVersion) {
        if (state.submissionVersion != observedVersion) {
            if (state.feedbackPlayerId == player.playerId) draft = ""
            observedVersion = state.submissionVersion
        }
    }
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)
                .imePadding().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppText("${player.nickname} · 第 ${state.game} 局", fontWeight = FontWeight.SemiBold)
                        AppText("均分 ${liveMatchGradeLabel(player.average, player.users)} · ${player.users?.let { "${it}人评分" } ?: "人数未知"}", color = AppContentColors.secondaryText())
                    }
                    AppTextButton(onClick = onDismiss) { AppText("返回") }
                }
            }
            item {
                AppText(if (player.rated) "我的评分：${player.userScore ?: "—"} 分" else "尚未评分", color = AppContentColors.secondaryText())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(Modifier.weight(1f), onOpen = { scoreMenu = true }, enabled = loggedIn && !state.submitting) {
                        AppTextButton(enabled = loggedIn && !state.submitting, onClick = { scoreMenu = true }) {
                            AppText(if (score < 0) "选择分数" else "$score 分")
                        }
                        AppDropdownMenu(expanded = scoreMenu, onDismissRequest = { scoreMenu = false }) {
                            liveMatchRatingScores.forEach { value ->
                                AppDropdownMenuItem(text = { AppText("$value 分") }, onClick = { score = value; scoreMenu = false })
                            }
                        }
                    }
                    if (loggedIn) AppTextButton(enabled = score in liveMatchRatingScores && !state.submitting, onClick = { onSubmit(score, "") }) { AppText(if (state.submitting) "提交中…" else "提交评分") }
                    else AppTextButton(onClick = onLoginClick) { AppText("登录后评分") }
                }
                state.feedback?.takeIf { state.feedbackPlayerId == player.playerId }?.let { AppText(it, color = AppContentColors.secondaryText()) }
            }
            if (canComment) {
                item {
                    AppOutlinedTextField(value = draft, onValueChange = { if (it.length <= 1000) draft = it },
                        modifier = Modifier.fillMaxWidth(), enabled = loggedIn && !state.submitting,
                        label = { AppText("评分评论") }, placeholder = { AppText("说说对本局选手表现的看法") },
                        minLines = 2, maxLines = 5,
                        supportingText = { AppText("${draft.length}/1000") })
                    AppTextButton(enabled = loggedIn && score in liveMatchRatingScores && draft.isNotBlank() && !state.submitting,
                        onClick = { onSubmit(score, draft) }) { AppText("发布评分评论") }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText("选手评分评论", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { sortMenu = true }, enabled = !comments.loading) {
                            AppTextButton(enabled = !comments.loading, onClick = { sortMenu = true }) { AppText(if (comments.mode == 2) "最新" else "热门") }
                            AppDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf(3 to "热门", 2 to "最新").forEach { (mode, label) ->
                                    AppDropdownMenuItem(text = { AppText(label) }, onClick = { sortMenu = false; onLoadComments(mode, false) })
                                }
                            }
                        }
                        AppTextButton(enabled = !comments.loading, onClick = { onLoadComments(comments.mode, false) }) { AppText("刷新") }
                    }
                }
                items(comments.items, key = { it.rpid }) { reply ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppText(reply.member.uname, fontWeight = FontWeight.SemiBold)
                            AppText(reply.content.message)
                            AppTextButton(onClick = { onCommentClick(reply.rpid) }) { AppText("查看回复（${reply.rcount}）") }
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        when {
                            comments.loading -> AppCircularProgressIndicator()
                            comments.error != null -> AppErrorState(
                                title = "加载失败",
                                message = comments.error,
                                presentation = AppContentStatePresentation.INLINE,
                                primaryAction = AppContentStateAction("重试") { onLoadComments(comments.mode, comments.items.isNotEmpty()) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            comments.items.isEmpty() -> AppEmptyState(
                                title = "暂无该选手的评分评论",
                                presentation = AppContentStatePresentation.INLINE,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            !comments.end -> AppTextButton(onClick = { onLoadComments(comments.mode, true) }) { AppText("加载更多") }
                            else -> AppText("已显示全部评分评论", color = AppContentColors.secondaryText())
                        }
                    }
                }
            } else item {
                AppEmptyState(
                    title = "本局暂未开放选手评分评论",
                    presentation = AppContentStatePresentation.INLINE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
