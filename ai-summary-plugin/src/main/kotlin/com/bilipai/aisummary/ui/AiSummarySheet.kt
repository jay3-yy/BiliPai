package com.bilipai.aisummary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.model.SummaryStyle
import com.bilipai.aisummary.model.SummaryUiState
import com.bilipai.aisummary.model.SubtitleChunk
import com.bilipai.aisummary.pipeline.SummaryRequest

/**
 * 「AI 总结」底部抽屉。
 *
 * 接入方式：在 BiliPai 视频详情页统计行行尾的 AI 总结图标点击时，
 * 把 [visible] 置 true 并传入当前视频的 [request]；[onSeekTo] 接播放器 seekTo(秒)。
 * 组件只用 Material3 基础组件，宿主若用 Miuix 主题，把 Card/Button 换成对应组件即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummarySheet(
    visible: Boolean,
    request: SummaryRequest,
    controller: AiSummaryController,
    onDismiss: () -> Unit,
    onSeekTo: (Double) -> Unit,
    onCopy: (String) -> Unit,
    onExport: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onChangeStyle: (SummaryStyle) -> Unit,
    currentStyle: SummaryStyle = SummaryStyle.FULL_NOTE,
    /** 抽屉容器可替换：宿主接入时可传自己的 AppModalBottomSheet（见 AiSummaryBuiltinPlugin）。 */
    sheetHost: @Composable (onDismiss: () -> Unit, content: @Composable () -> Unit) -> Unit =
        { dismiss, content -> ModalBottomSheet(onDismissRequest = dismiss) { content() } }
) {
    if (!visible) return
    val state by controller.state.collectAsState()
    var styleMenuOpen by remember { mutableStateOf(false) }

    sheetHost(onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // ---- 标题栏 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AI 总结",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    TextButton(onClick = { styleMenuOpen = true }) { Text(currentStyle.displayName) }
                    DropdownMenu(expanded = styleMenuOpen, onDismissRequest = { styleMenuOpen = false }) {
                        SummaryStyle.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.displayName) },
                                onClick = {
                                    styleMenuOpen = false
                                    onChangeStyle(s)
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = onOpenSettings) { Text("设置") }
            }
            Text(request.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)

            Spacer(Modifier.height(12.dp))

            // ---- 状态区 ----
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                when (val s = state) {
                    SummaryUiState.Idle -> Hint("点下方「开始总结」生成笔记。")
                    SummaryUiState.NeedConfig -> {
                        Hint("还没配置模型。本插件使用你自己的 API Key，配置只保存在本机。")
                        FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("去设置模型")
                        }
                    }
                    is SummaryUiState.Loading -> LoadingBlock(s)
                    is SummaryUiState.Error -> {
                        Hint("⚠️ ${s.message}")
                        if (s.retryable) {
                            FilledTonalButton(
                                onClick = { controller.start(request) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("重试") }
                        }
                    }
                    is SummaryUiState.Success -> ResultBlock(s.result, s.fromCache, onSeekTo)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 底部操作栏 ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is SummaryUiState.Success -> {
                        TextButton(onClick = { onCopy(s.result.toMarkdown()) }) { Text("复制") }
                        TextButton(onClick = {
                            onExport("${request.bvid}_AI总结.md", s.result.toMarkdown())
                        }) { Text("导出 MD") }
                        TextButton(onClick = { controller.refreshConfig(request) }) { Text("重新生成") }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = { controller.start(request) },
                            modifier = Modifier.weight(1f),
                            enabled = state !is SummaryUiState.Loading
                        ) { Text("开始总结") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun LoadingBlock(s: SummaryUiState.Loading) {
    Text(s.stage, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    if (s.totalSegments > 0) {
        LinearProgressIndicator(
            progress = { s.doneSegments.toFloat() / s.totalSegments },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${s.doneSegments}/${s.totalSegments} 段", style = MaterialTheme.typography.labelSmall)
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultBlock(result: SummaryResult, fromCache: Boolean, onSeekTo: (Double) -> Unit) {
    // 统一来源标签：官方 / 自定义模型 / 本地缓存（缓存会附带原始来源与生成时间）
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            "来源：" + result.sourceLabel,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
    if (result.isFallback) {
        Text(
            "⚠️ 未获取到字幕，此结果来自 B 站官方 AI 总结（未消耗你的模型额度）",
            style = MaterialTheme.typography.bodySmall
        )
    }

    SectionTitle("总览")
    Text(result.overview, style = MaterialTheme.typography.bodyMedium)

    if (result.chapters.isNotEmpty()) {
        SectionTitle("章节（点击时间跳转）")
        result.chapters.forEach { ch ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSeekTo(ch.startSec) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "[${SubtitleChunk.formatTime(ch.startSec)}] ${ch.title}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ch.points.forEach { p ->
                        val prefix = if (p.hasTime) "[${SubtitleChunk.formatTime(p.startSec)}] " else ""
                        Text("• $prefix${p.text}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (result.terms.isNotEmpty()) {
        SectionTitle("术语")
        result.terms.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
    if (result.quotes.isNotEmpty()) {
        SectionTitle("金句 / 关键论断")
        result.quotes.forEach { Text("「$it」", style = MaterialTheme.typography.bodySmall) }
    }
    if (result.verdict.isNotBlank()) {
        SectionTitle("观看建议")
        Text(result.verdict, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.size(8.dp))
    Text(
        "模型 ${result.model} · ${result.segmentCount} 段 · " +
            "tokens ${result.promptTokens}/${result.completionTokens}",
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}
