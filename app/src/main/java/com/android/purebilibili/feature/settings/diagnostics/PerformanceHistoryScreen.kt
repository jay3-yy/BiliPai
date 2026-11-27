package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.SettingsBottomBarScrollEffect
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.performance.*
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface PerformanceHistoryLoadState {
    data object Loading : PerformanceHistoryLoadState
    data class Failed(val message: String) : PerformanceHistoryLoadState
    data class Ready(val entries: List<PerformanceHistoryEntry>) : PerformanceHistoryLoadState
}

/** 文件读取留在入口；图表与排行只接收已解析的数据和交互回调。 */
@Composable
internal fun PerformanceHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PerformanceHistoryLoadState>(PerformanceHistoryLoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(context, reload) {
        state = PerformanceHistoryLoadState.Loading
        state = try {
            PerformanceHistoryLoadState.Ready(PerformanceCapture.readHistory(context))
        } catch (error: java.io.IOException) {
            PerformanceHistoryLoadState.Failed("读取历史失败：${error.localizedMessage}")
        } catch (error: org.json.JSONException) {
            PerformanceHistoryLoadState.Failed("历史报告无法解析：${error.localizedMessage}")
        }
    }
    val listState = rememberLazyListState()
    SettingsBottomBarScrollEffect(listState)
    val bottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 32.dp,
    )
    SettingsPageScaffold(
        title = PERFORMANCE_HISTORY_TITLE,
        onBack = onBack,
        backContentDescription = stringResource(R.string.common_back),
        bottomContentPadding = bottomPadding,
        listState = listState,
        scrollHost = SettingsPageScrollHost.External,
        externalContentHandlesTopPadding = true,
    ) {
        val contentPadding = settingsScrollContentPadding(
            extraTop = 16.dp, extraBottom = maxOf(bottomPadding, LocalBottomBarContentPadding.current), extraHorizontal = 16.dp,
        )
        when (val loaded = state) {
            PerformanceHistoryLoadState.Loading -> Box(Modifier.padding(contentPadding)) {
                AppText("正在读取本地历史…")
            }
            is PerformanceHistoryLoadState.Failed -> Column(Modifier.padding(contentPadding)) {
                AppText(loaded.message)
                AppTextButton(onClick = { reload++ }) { AppText("重新读取") }
            }
            is PerformanceHistoryLoadState.Ready -> PerformanceHistoryContent(
                entries = loaded.entries,
                listState = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal class PerformanceHistorySelection(private val entries: List<PerformanceHistoryEntry>) {
    var currentId by mutableStateOf(entries.first().id)
        private set
    var baselineId by mutableStateOf<String?>(null)
    var scope by mutableStateOf(preferredScope(entries.first()))
        private set
    var metric by mutableStateOf(PerformanceHistoryMetric.P95)
    val current: PerformanceHistoryEntry get() = entries.first { it.id == currentId }
    init { chooseBaseline() }

    fun selectCurrent(id: String) {
        currentId = id
        if (scope !in current.scopes) scope = preferredScope(current)
        chooseBaseline()
    }

    fun selectScope(value: String) {
        scope = value
        chooseBaseline()
    }

    private fun chooseBaseline() {
        baselineId = entries.firstOrNull {
            it.id != currentId && it.completed && it.conditions == current.conditions && scope in it.scopes
        }?.id
    }

    private fun preferredScope(entry: PerformanceHistoryEntry): String =
        entry.scopes.keys.firstOrNull { it.startsWith("navigation:video/home/") } ?: PERFORMANCE_SCOPE_ALL
}

@Composable
internal fun PerformanceHistoryContent(
    entries: List<PerformanceHistoryEntry>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Column(modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText("还没有性能历史", style = MaterialTheme.typography.titleMedium)
            AppText("完成一次性能采集后，这里会保留报告、显示排行与坐标图；两次记录可选择基准对比。")
        }
        return
    }
    val selection = rememberSaveable(entries, saver = listSaver<PerformanceHistorySelection, String>(
        save = { listOf(it.currentId, it.baselineId.orEmpty(), it.scope, it.metric.name) },
        restore = { saved ->
            PerformanceHistorySelection(entries).apply {
                selectCurrent(saved[0])
                selectScope(saved[2])
                metric = PerformanceHistoryMetric.valueOf(saved[3])
                baselineId = saved[1].takeIf(String::isNotEmpty)
            }
        },
    )) { PerformanceHistorySelection(entries) }
    val current = selection.current
    val baseline = entries.firstOrNull { it.id == selection.baselineId }
    val scope = selection.scope
    val metric = selection.metric
    val ranking = remember(entries, current, scope, metric) { rankPerformanceHistory(entries, current, scope, metric) }
    val trend = remember(entries, current, metric, scope) {
        comparablePerformanceHistory(entries, current).sortedBy { it.startedWallMs }.map { entry ->
            val measurement = entry.scopes[scope]
            PerformanceChartPoint(entry.startedWallMs, historyDate(entry, short = true), historyCaption(entry), measurement?.value(metric), measurement?.formatted(metric) ?: "未覆盖")
        }
    }
    val issues = baseline?.let {
        performanceComparisonIssues(current, it) + if (scope !in it.scopes) listOf("基准未覆盖所选场景") else emptyList()
    }.orEmpty()
    val secondary = AppContentColors.secondaryText()
    var summaryExpanded by rememberSaveable(current.id) { mutableStateOf(false) }
    val copy = rememberClipboardCopyHandler()
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "selection", contentType = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(ImageVector.vectorResource(R.drawable.ic_settings_performance_history_24), null, Modifier.size(24.dp))
                    AppText("最近 ${entries.size} 次采集", style = MaterialTheme.typography.titleMedium)
                }
                AppText("本地保留最近 $PERFORMANCE_HISTORY_LIMIT 次；同次采集的检查点更新同一条记录。", color = secondary, style = MaterialTheme.typography.bodySmall)
                PerformanceHistoryPicker("当前记录", current.id, entries.map { it.id to historyCaption(it) }, selection::selectCurrent, Modifier.fillMaxWidth())
                PerformanceHistoryPicker("场景", scope, current.scopeLabels.map { it.key to it.value }, selection::selectScope, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerformanceHistoryPicker("指标", metric.name, PerformanceHistoryMetric.entries.map { it.name to it.label },
                        { selection.metric = PerformanceHistoryMetric.valueOf(it) }, Modifier.weight(1f))
                    AppTextButton(onClick = { summaryExpanded = !summaryExpanded }, modifier = Modifier.weight(1f)) { AppText(if (summaryExpanded) "收起本次摘要" else "展开本次摘要") }
                }
                AppText(historyConditions(current), color = secondary, style = MaterialTheme.typography.bodySmall)
                if (!current.completed) AppText("当前是未正常结束的检查点，可查看数据，但不参与历史排行或差值比较。", color = secondary)
                if (!current.conditions.recorded) {
                    AppText("本次采集条件记录不完整，可查看原始数值，暂不参与同条件排行。", color = secondary)
                }
                val measurement = current.scopes[scope]
                measurement?.let {
                    AppText("${metric.label}：${it.formatted(metric)}", style = MaterialTheme.typography.titleLarge)
                    AppText("本次 UI ${it.frames} 帧 · ≥100ms 长帧 ${it.longFrames100Ms ?: "未采集"} · 截止预算可用 ${it.deadlineFrames} 帧", style = MaterialTheme.typography.bodySmall)
                }
                AppText(
                    if (scope == PERFORMANCE_SCOPE_ALL) "P95 为全程混合场景的 1ms 桶上界。"
                    else "分组 P95 取各次转场/各窗口中最慢的桶上界，不将百分位平均；导航只覆盖每窗最近 40 次完整转场。",
                    color = secondary, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (summaryExpanded) {
            item(key = "summary", contentType = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val summary = current.preview.summary ?: "这份报告未包含摘要。"
                    AppText(summary, modifier = Modifier.fillMaxWidth())
                    AppTextButton(onClick = { copy(summary, "性能摘要") }) { AppText("复制摘要") }
                }
            }
        }
        item(key = "trend", contentType = "chart") {
            PerformanceHistoryChart("同条件历史趋势 · ${metric.label}", metric.unit, trend, Modifier.fillMaxWidth())
            AppText("横轴：采集时间；纵轴：所选指标。点击、悬停或用左右键查看点值；下方排行保留已采集数值。", color = secondary, style = MaterialTheme.typography.bodySmall)
        }
        item(key = "comparison", contentType = "comparison") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(ImageVector.vectorResource(settingsIconResource(R.drawable.ph_arrows_horizontal_24, R.drawable.ms_compare_arrows_24)), null, Modifier.size(24.dp))
                    AppText("记录对比", style = MaterialTheme.typography.titleMedium)
                }
                PerformanceHistoryPicker("基准记录", selection.baselineId.orEmpty(),
                    listOf("" to "不对比") + entries.filter { it.id != current.id }.map { it.id to historyCaption(it) },
                    { selection.baselineId = it.takeIf(String::isNotEmpty) }, Modifier.fillMaxWidth())
                if (baseline == null) {
                    AppText("选择另一条记录作为基准；会优先选择同设备、同样式和同统计口径的记录。", color = secondary)
                } else {
                    if (issues.isNotEmpty()) AppText("仅并列展示，不计算差值：${issues.joinToString("；")}", color = secondary)
                    PerformanceComparisonContent(current, baseline, scope, metric, issues.isEmpty(), Modifier.fillMaxWidth())
                    AppText("差值仅描述两次记录；场景操作次数、内容和运行负载仍可能不同。P95 超过 500ms 时不计算精确差值。", color = secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "ranking_title", contentType = "heading") {
            AppText("同条件性能排行 · 高值优先排查", style = MaterialTheme.typography.titleMedium)
            AppText("按当前指标降序；不跨设备、系统、构建、材质、采集口径或面板刷新率范围排名。", color = secondary, style = MaterialTheme.typography.bodySmall)
            if (ranking.isEmpty()) AppText("所选场景暂无同条件的已完成记录；未知指标不参与排行。")
        }
        itemsIndexed(ranking, key = { _, entry -> entry.id }, contentType = { _, _ -> "history_record" }) { index, entry ->
            val measurement = entry.scopes.getValue(scope)
            Row(
                Modifier.fillMaxWidth().background(AppSurfaceTokens.surface(), RoundedCornerShape(12.dp))
                    .clickable { selection.selectCurrent(entry.id) }.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText("${index + 1}", style = MaterialTheme.typography.titleLarge, tapToCopyEnabled = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(historyCaption(entry), style = MaterialTheme.typography.bodyMedium, tapToCopyEnabled = false)
                    AppText("UI ${measurement.frames} 帧 · 前台 ${entry.foregroundMs / 1000} 秒${if (entry.id == current.id) " · 当前" else ""}", color = secondary, style = MaterialTheme.typography.bodySmall, tapToCopyEnabled = false)
                }
                AppText(measurement.formatted(metric), style = MaterialTheme.typography.labelLarge, tapToCopyEnabled = false)
            }
        }
    }
}

@Composable
private fun PerformanceHistoryPicker(
    label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppGlassMenuAnchor(onOpen = { expanded = true }) {
            AppTextButton(onClick = { expanded = true }) { AppText("$label：${options.firstOrNull { it.first == selected }?.second.orEmpty()} ▾") }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, text) ->
                    AppDropdownMenuItem(text = { AppText(text) }, selected = key == selected, onClick = { expanded = false; onSelect(key) })
                }
            }
        }
    }
}

@Composable
private fun PerformanceComparisonContent(
    current: PerformanceHistoryEntry, baseline: PerformanceHistoryEntry, scope: String,
    metric: PerformanceHistoryMetric, comparable: Boolean, modifier: Modifier = Modifier,
) {
    val a = current.scopes[scope]
    val b = baseline.scopes[scope]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (comparable) PerformanceHistoryChart("基准与当前 · ${metric.label}", metric.unit,
            listOf(
                PerformanceChartPoint(0, "基准", historyCaption(baseline), b?.value(metric), b?.formatted(metric) ?: "未覆盖"),
                PerformanceChartPoint(1, "当前", historyCaption(current), a?.value(metric), a?.formatted(metric) ?: "未覆盖"),
            ), Modifier.fillMaxWidth(), bars = true)
        AppText("指标　基准 → 当前　（差值：当前−基准）", style = MaterialTheme.typography.labelMedium)
        PerformanceHistoryMetric.entries.forEach { item ->
            val previous = b?.value(item)
            val now = a?.value(item)
            val delta = if (comparable && previous != null && now != null) {
                val difference = now - previous
                val unit = if (item in listOf(PerformanceHistoryMetric.JANK_RATE, PerformanceHistoryMetric.DEADLINE_RATE)) "百分点" else item.unit
                val decimals = if (item == PerformanceHistoryMetric.P95 || item == PerformanceHistoryMetric.MAX) 1 else 3
                "${if (difference > 0) "+" else ""}${performanceNumber(difference, decimals)} $unit"
            } else "不计算"
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText(item.label, style = MaterialTheme.typography.labelMedium)
                AppText("${b?.formatted(item) ?: "未覆盖"} → ${a?.formatted(item) ?: "未覆盖"}（$delta）", style = MaterialTheme.typography.bodySmall)
            }
        }
        AppText("样本：基准 ${b?.frames ?: "未覆盖"} 帧 / 当前 ${a?.frames ?: "未覆盖"} 帧", style = MaterialTheme.typography.bodySmall)
    }
}

private fun historyDate(entry: PerformanceHistoryEntry, short: Boolean = false): String =
    SimpleDateFormat(if (short) "MM/dd HH:mm" else "MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.startedWallMs))
private fun historyCaption(entry: PerformanceHistoryEntry): String =
    "${historyDate(entry)} · ${entry.version}${if (!entry.completed) " · 检查点" else ""}"
private fun historyConditions(entry: PerformanceHistoryEntry): String = entry.conditions.let {
    val style = when (it.uiStyle) { "MATERIAL3" -> "MD3"; "MIUIX" -> "Miuix"; else -> it.uiStyle }
    val display = if (it.panelRates.isEmpty()) "面板刷新率未采集" else "面板 ${it.panelRates.joinToString("/")} Hz"
    "${it.device} · Android API ${it.sdk} · ${it.buildType}\n$style · 玻璃${if (it.liquidGlass) "开启" else "关闭"} · $display"
}
