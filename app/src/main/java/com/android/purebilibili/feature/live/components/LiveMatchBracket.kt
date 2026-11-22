package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.Canvas
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.LiveMatchBracketNode
import com.android.purebilibili.feature.live.liveMatchBracketLayout

@Composable
internal fun LiveMatchBracket(
    roots: List<LiveMatchBracketNode>,
    onOpenGrades: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(roots) { liveMatchBracketLayout(roots) }
    if (layout.nodes.isEmpty()) return
    val nodeWidth = 208.dp
    val nodeHeight = 184.dp
    val columnPitch = 240.dp
    val rowPitch = 200.dp
    val lineColor = AppContentColors.secondaryText().copy(alpha = 0.5f)
    val scrollState = rememberScrollState()
    LaunchedEffect(roots) { scrollState.scrollTo(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layout.truncated) AppText("对阵图仅显示部分轮次，完整比赛请查看全部赛程。", color = AppContentColors.secondaryText())
        Box(Modifier.horizontalScroll(scrollState)) {
            Box(Modifier.size(
                width = columnPitch * (layout.columns - 1) + nodeWidth,
                height = rowPitch * (layout.rows - 1).coerceAtLeast(0) + nodeHeight,
            )) {
                Canvas(Modifier.matchParentSize()) {
                    layout.edges.forEach { edge ->
                        val child = layout.nodes[edge.child]
                        val parent = layout.nodes[edge.parent]
                        val from = Offset(columnPitch.toPx() * child.column + nodeWidth.toPx(), rowPitch.toPx() * child.row + nodeHeight.toPx() / 2)
                        val to = Offset(columnPitch.toPx() * parent.column, rowPitch.toPx() * parent.row + nodeHeight.toPx() / 2)
                        val midX = (from.x + to.x) / 2
                        drawPath(Path().apply { moveTo(from.x, from.y); lineTo(midX, from.y); lineTo(midX, to.y); lineTo(to.x, to.y) }, lineColor, style = Stroke(1.5.dp.toPx()))
                    }
                }
                layout.nodes.forEach { placed ->
                    val node = placed.node
                    val scoresKnown = node.contest?.status?.let { it in 2..3 } == true || node.winnerId > 0
                    AppCard(Modifier.offset(columnPitch * placed.column, rowPitch * placed.row).size(nodeWidth, nodeHeight)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText(node.stage.ifBlank { "对阵" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BracketTeam(node.homeName, node.homeScore.takeIf { scoresKnown }, node.homeId > 0 && node.homeId == node.winnerId)
                            BracketTeam(node.awayName, node.awayScore.takeIf { scoresKnown }, node.awayId > 0 && node.awayId == node.winnerId)
                            if (node.contestId > 0) AppTextButton(onClick = { onOpenGrades(node.contestId) }) { AppText("选手评分") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BracketTeam(name: String, score: Int?, winner: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(name.ifBlank { "待定" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal)
        AppText(score?.takeIf { it >= 0 }?.toString() ?: "—")
    }
}
