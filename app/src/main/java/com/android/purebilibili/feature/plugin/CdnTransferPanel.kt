package com.android.purebilibili.feature.plugin

import com.android.purebilibili.plugin.sdk.CdnLineDiagnostic

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.settings.AppSegmentedControl
import java.util.Locale

/** Plain native UI: no network, player or storage access inside this section. */
@Composable
internal fun CdnTransferPanel(
    state: CdnTransferSnapshot,
    diagnostics: List<CdnLineDiagnostic>,
    checking: Boolean,
    canCheck: Boolean,
    parallelEnabled: Boolean,
    experimentalRewriteEnabled: Boolean,
    onParallelChange: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onAvoid: (String) -> Unit,
    onRestore: () -> Unit,
    parallelUnavailableReason: String? = null,
    modifier: Modifier = Modifier
) {
    var expandedHost by remember { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(CdnResultSort.FASTEST) }
    val active = state.nodes.sumOf { it.active }
    val nodeByHost = state.nodes.associateBy { it.host }
    val diagnosticByHost = diagnostics.distinctBy { it.host }.associateBy { it.host }
    val hosts = (diagnosticByHost.keys + nodeByHost.keys).sortedWith(
        cdnResultComparator(
            sort = sort,
            completedRequests = { nodeByHost[it]?.completed ?: 0 },
            bytesPerSecond = {
                nodeByHost[it]?.speedBps?.takeIf { speed -> speed > 0 }
                    ?: diagnosticByHost[it]?.speedKbps?.times(125L)
            },
        ),
    )
    val highestSpeed = state.nodes.maxOfOrNull { it.speedBps }?.coerceAtLeast(1) ?: 1L
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText("播放加速", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CdnMetric("活动请求", "$active", Modifier.weight(1f))
                CdnMetric("前方缓冲", "${state.bufferMs / 1_000} 秒", Modifier.weight(1f))
                CdnMetric("有效读取", cdnBytes(state.deliveredBytes), Modifier.weight(1f))
            }
            AppLinearProgressIndicator(
                progress = { (state.bufferMs / 30_000f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = "前方缓冲 ${state.bufferMs / 1_000} 秒" }
            )
            AppText(
                if (active > 0) "正在取流 · ${cdnSpeed(state.speedHistory.lastOrNull() ?: 0)}" else "等待媒体请求 · 已缓存内容播放时无需下载",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText("并发下载", style = MaterialTheme.typography.bodyMedium)
                    AppText(
                        if (experimentalRewriteEnabled) parallelUnavailableReason ?: "先关闭实验性 URL 改写，再使用并发下载" else "Wi-Fi 下按缓冲自动调节，最多 4 个分块请求；失败自动回退",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText()
                    )
                }
                AppSwitch(
                    checked = parallelEnabled,
                    enabled = !experimentalRewriteEnabled,
                    onCheckedChange = onParallelChange,
                    modifier = Modifier.semantics { stateDescription = if (parallelEnabled) "并发下载已开启" else "并发下载已关闭" }
                )
            }
            AppText(
                "网络读取 ${cdnBytes(state.networkBytes)} · 慢块补救 ${state.rescueCount} 次 · 续传 ${state.resumedCount} 次 · 单连接回退 ${state.fallbackCount} 次",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
            if (state.speedHistory.isNotEmpty()) {
                var showHistory by remember { mutableStateOf(false) }
                AppButton(onClick = { showHistory = !showHistory }) {
                    AppText(if (showHistory) "收起速度趋势" else "查看速度趋势")
                }
                if (showHistory) {
                    val maximum = state.speedHistory.maxOrNull()?.coerceAtLeast(1) ?: 1L
                    AppText("最近 12 个采样区间 · 从旧到新", style = MaterialTheme.typography.labelSmall)
                    state.speedHistory.forEachIndexed { index, speed ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AppLinearProgressIndicator(
                                progress = { speed.toFloat() / maximum },
                                modifier = Modifier.weight(1f).semantics { stateDescription = "第 ${index + 1} 个采样 ${cdnSpeed(speed)}" }
                            )
                            AppText(cdnSpeed(speed), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            AppHorizontalDivider()
            AppText("授权节点", style = MaterialTheme.typography.titleSmall)
            if (hosts.isEmpty()) {
                AppText("播放视频后显示节点表现；缓存命中时不会额外测速。", style = MaterialTheme.typography.bodySmall)
            } else {
                CdnResultSortSelector(sort) { sort = it }
            }
            hosts.forEach { host ->
                key(host) {
                    val node = nodeByHost[host]
                    val diagnostic = diagnosticByHost[host]
                    val cooling = (node?.cooldownUntilMs ?: 0) > state.updatedAtMs
                    AppCard(
                        onClick = { expandedHost = if (expandedHost == host) null else host },
                        modifier = Modifier.fillMaxWidth().semantics {
                            stateDescription = if (expandedHost == host) "节点详情已展开" else "点按查看节点详情"
                        }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppText(host, style = MaterialTheme.typography.bodySmall)
                            AppText(
                                when {
                                    node != null && node.active > 0 -> "取流中 · ${node.active} 个请求 · ${cdnSpeed(node.speedBps)}"
                                    cooling -> "暂时降低优先级"
                                    node != null && node.speedBps > 0 -> "可用 · 最近 ${cdnSpeed(node.speedBps)}"
                                    diagnostic?.speedKbps != null -> "手动测速 ${diagnostic.speedKbps} Kbps"
                                    node != null && node.completed > 0 -> "可用 · 当前网络未测速"
                                    else -> "等待传输样本"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cooling) AppContentColors.error() else AppContentColors.secondaryText()
                            )
                            if (sort == CdnResultSort.MOST_USED) CdnCompletedRequestsLabel(node?.completed)
                            AppLinearProgressIndicator(
                                progress = { ((node?.speedBps ?: 0).toFloat() / highestSpeed).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (expandedHost == host) {
                                AppText(
                                    "首字节 ${node?.firstByteMs?.let { "$it ms" } ?: "未采样"} · 完成 ${node?.completed ?: 0} · 失败 ${node?.failures ?: 0}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                AppText(
                                    "累计 ${cdnBytes(node?.bytes ?: 0)}${node?.lastStatus?.let { " · 最近响应 HTTP $it" }.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                AppButton(onClick = { onAvoid(host) }, enabled = !cooling) { AppText("降低优先级 1 分钟") }
                            }
                        }
                    }
                }
            }
            // Stack actions so large fonts and narrow plugin sheets keep usable touch targets.
            AppButton(onClick = onCheck, enabled = canCheck && !checking, modifier = Modifier.fillMaxWidth()) {
                AppText(when { checking -> "检测中…"; canCheck -> "检测当前线路"; else -> "暂无可检测的播放线路" })
            }
            if (checking) AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (!canCheck) {
                AppText(
                    "请先播放 B 站视频或音乐，让播放器获取媒体地址；已缓存的内容可能需要继续播放或拖到未缓存的位置。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText()
                )
            }
            AppButton(onClick = onRestore, enabled = state.nodes.any { it.cooldownUntilMs > state.updatedAtMs }, modifier = Modifier.fillMaxWidth()) {
                AppText(if (state.nodes.any { it.cooldownUntilMs > state.updatedAtMs }) "恢复自动选线" else "当前选线正常，无需恢复")
            }
            AppText(
                "统计从本次应用启动累计，仅保留节点域名。手动检测每条线路最多读取 32 KiB；降低优先级将在后续请求生效。",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
        }
    }
}

@Composable
internal fun CdnResultSortSelector(sort: CdnResultSort, onSortChange: (CdnResultSort) -> Unit) {
    AppSegmentedControl(
        options = remember { CdnResultSort.entries.map { AppSegmentOption(it, it.label) } },
        selectedValue = sort,
        modifier = Modifier.fillMaxWidth(),
        onSelectionChange = onSortChange,
    )
}

@Composable
internal fun CdnCompletedRequestsLabel(completed: Int?) {
    AppText(
        completed?.let { "本次运行已完成 $it 次播放传输请求" } ?: "暂无播放传输记录",
        style = MaterialTheme.typography.labelSmall,
        color = AppContentColors.secondaryText(),
    )
}

@Composable
private fun CdnMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        AppText(value, style = MaterialTheme.typography.titleSmall)
        AppText(label, style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText())
    }
}

private fun cdnSpeed(bytesPerSecond: Long): String = String.format(Locale.ROOT, "%.1f Mbps", bytesPerSecond * 8.0 / 1_000_000)
private fun cdnBytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MiB", bytes / 1_048_576.0)
