package com.android.purebilibili.core.performance

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10

internal const val PERFORMANCE_HISTORY_SETTING_ID = "diagnostics.performance_history"
internal const val PERFORMANCE_HISTORY_TITLE = "性能历史与对比"
internal const val PERFORMANCE_HISTORY_LIMIT = 30
internal const val PERFORMANCE_SCOPE_ALL = "all"

internal enum class PerformanceHistoryMetric(val label: String, val unit: String) {
    P95("UI P95", "ms"), MAX("最长 UI 帧", "ms"),
    LONG_RATE("每千帧长帧", "帧/千帧"), JANK_RATE("UI 卡顿率", "%"), DEADLINE_RATE("HWUI 超期率", "%"),
}

internal data class PerformanceHistoryMeasurement(
    val frames: Long,
    val jankyFrames: Long,
    val longFrames100Ms: Long?,
    val p95Ms: Int?,
    val maxUiMs: Double?,
    val deadlineFrames: Long,
    val missedDeadlines: Long,
) {
    fun value(metric: PerformanceHistoryMetric): Double? = when (metric) {
        PerformanceHistoryMetric.P95 -> p95Ms?.takeUnless { it == 501 }?.toDouble()
        PerformanceHistoryMetric.MAX -> maxUiMs
        PerformanceHistoryMetric.LONG_RATE -> longFrames100Ms?.let { ratio(it, frames, 1000.0) }
        PerformanceHistoryMetric.JANK_RATE -> ratio(jankyFrames, frames, 100.0)
        PerformanceHistoryMetric.DEADLINE_RATE -> ratio(missedDeadlines, deadlineFrames, 100.0)
    }

    fun formatted(metric: PerformanceHistoryMetric): String =
        if (metric == PerformanceHistoryMetric.P95 && p95Ms == 501) ">500 ms"
        else value(metric)?.let {
            val decimals = if (metric == PerformanceHistoryMetric.P95 || metric == PerformanceHistoryMetric.MAX) 1 else 3
            "${performanceNumber(it, decimals)} ${metric.unit}"
        } ?: "未采集"

    // 溢出桶只用于排序，不能作为 501ms 的精确点值或差值。
    fun rankingValue(metric: PerformanceHistoryMetric): Double? =
        if (metric == PerformanceHistoryMetric.P95) p95Ms?.toDouble() else value(metric)
}

private fun ratio(numerator: Long, denominator: Long, scale: Double): Double? =
    if (denominator == 0L) null else numerator * scale / denominator

internal data class PerformanceHistoryConditions(
    val device: String, val sdk: Int, val buildType: String, val debug: Boolean,
    val uiStyle: String, val liquidGlass: Boolean, val schema: Int, val frameSources: List<String>,
    val panelRates: List<Int>,
) {
    val recorded: Boolean get() = panelRates.isNotEmpty() && frameSources.isNotEmpty() && "unknown" !in frameSources
}

internal data class PerformanceHistoryEntry(
    val id: String,
    val startedWallMs: Long,
    val elapsedMs: Long,
    val foregroundMs: Long,
    val version: String,
    val stopReason: String,
    val conditions: PerformanceHistoryConditions,
    val scopes: Map<String, PerformanceHistoryMeasurement>,
    val scopeLabels: Map<String, String>,
    val preview: PerformanceReportPreview,
) {
    val completed: Boolean get() = stopReason == "user" || stopReason == "time_limit"
}

internal fun performanceHistoryId(report: JSONObject): String =
    "${report.getLong("startedWallMs")}-${report.getLong("startedMonotonicNs")}"

internal fun parsePerformanceHistory(report: JSONObject): PerformanceHistoryEntry {
    val env = report.getJSONObject("environment")
    val schema = report.getInt("schema")
    val windows = report.getJSONArray("windows").objects()
    val scenes = report.getJSONObject("scenes")
    val hardwareScenes = report.getJSONObject("hwuiScenes")
    val scopeLabels = linkedMapOf(PERFORMANCE_SCOPE_ALL to "全程（混合场景）")
    val scopes = linkedMapOf(PERFORMANCE_SCOPE_ALL to historyMeasurement(report.getJSONObject("frames"), report.getJSONObject("hwui")))
    val sceneInputs = scenes.keys().asSequence().filter { it != "other" }.map { key ->
        val parts = key.split('/')
        val scope = "scene:${parts[1]}/${parts[2]}"
        val operation = when (parts[2]) { "scroll" -> "滚动"; "transition" -> "页面切换"; "idle" -> "停留"; else -> "未分类" }
        scopeLabels[scope] = "${performancePageLabel(parts[1])} · $operation"
        scope to historyMeasurement(scenes.getJSONObject(key), hardwareScenes.optJSONObject(key))
    }.toList()
    sceneInputs.groupBy({ it.first }, { it.second }).forEach { (key, entries) -> scopes[key] = mergeHistoryMeasurements(entries) }

    val navigationInputs = windows.flatMap { window -> window.getJSONArray("navigation").objects() }
        .filter { it.getBoolean("completeMotionBoundaries") && it.getString("outcome") in setOf("settled", "cancelled") }
        .map { motion ->
            val from = motion.getString("fromPage")
            val to = motion.getString("toPage")
            val kind = motion.getString("kind")
            val outcome = motion.getString("outcome")
            val scope = "navigation:$from/$to/$kind/$outcome"
            val action = when (kind) { "gesture_back" -> "手势返回"; "pop", "multi_pop" -> "返回"; "push", "multi_push" -> "进入"; else -> kind }
            scopeLabels[scope] = "${performancePageLabel(from)} → ${performancePageLabel(to)} · $action${if (outcome == "cancelled") "（取消）" else ""}"
            scope to historyMeasurement(motion.getJSONObject("ui"), motion.getJSONObject("hwui"))
        }
    navigationInputs.groupBy({ it.first }, { it.second }).forEach { (key, entries) -> scopes[key] = mergeHistoryMeasurements(entries) }

    val conditions = PerformanceHistoryConditions(
        "${env.getString("manufacturer")} ${env.getString("model")}", env.getInt("sdk"), env.getString("buildType"),
        env.getBoolean("debug"), env.getString("uiStyle"), env.getBoolean("liquidGlass"), schema,
        windows.filter { it.getJSONObject("frames").getLong("frames") > 0 }
            .map { it.optString("uiFrameSource", if (schema < 5) "jankstats_schema_$schema" else "unknown") }.distinct().sorted(),
        report.getJSONArray("samples").objects().mapNotNull { sample ->
            sample.getJSONObject("display").let { if (it.isNull("panelRefreshRateHz")) null else it.getDouble("panelRefreshRateHz").roundToInt() }
        }.distinct().sorted(),
    )
    val started = report.getLong("startedWallMs")
    val elapsed = report.getLong("elapsedMs")
    val stop = report.getString("stopReason")
    val version = env.getString("version")
    return PerformanceHistoryEntry(performanceHistoryId(report), started, elapsed, report.getLong("foregroundMs"), version, stop,
        conditions, scopes, scopeLabels,
        PerformanceReportPreview(version, conditions.buildType, conditions.device, started, elapsed, stop, savedPerformanceSummary(report)))
}

private fun historyMeasurement(ui: JSONObject, hardware: JSONObject?): PerformanceHistoryMeasurement {
    val deadlineFrames = hardware?.getLong("deadlineAvailableFrames") ?: 0
    return PerformanceHistoryMeasurement(
        ui.getLong("frames"), ui.getLong("jankyFrames"),
        if (ui.isNull("longFrames100Ms")) null else ui.getLong("longFrames100Ms"),
        if (ui.isNull("p95UiMsUpperBound")) null else ui.getInt("p95UiMsUpperBound"),
        if (ui.isNull("maxUiMs")) null else ui.getDouble("maxUiMs"),
        deadlineFrames, if (deadlineFrames == 0L) 0 else hardware!!.getLong("missedDeadlineFrames"),
    )
}

/** JSON 没有直方图，不能合成总体百分位；分组场景只展示各次/各窗 P95 中最慢的一个。 */
internal fun mergeHistoryMeasurements(entries: List<PerformanceHistoryMeasurement>) = PerformanceHistoryMeasurement(
    entries.sumOf { it.frames }, entries.sumOf { it.jankyFrames },
    if (entries.any { it.longFrames100Ms == null }) null else entries.sumOf { it.longFrames100Ms!! },
    entries.mapNotNull { it.p95Ms }.maxOrNull(), entries.mapNotNull { it.maxUiMs }.maxOrNull(),
    entries.sumOf { it.deadlineFrames }, entries.sumOf { it.missedDeadlines },
)

internal fun performanceComparisonIssues(current: PerformanceHistoryEntry, baseline: PerformanceHistoryEntry): List<String> = buildList {
    val a = current.conditions
    val b = baseline.conditions
    if (current.id == baseline.id) add("请选择两次不同的采集")
    if (!current.completed || !baseline.completed) add("包含尚未正常结束的检查点")
    if (a.device != b.device || a.sdk != b.sdk) add("设备或系统版本不同")
    if (a.buildType != b.buildType || a.debug != b.debug) add("构建类型不同")
    if (a.uiStyle != b.uiStyle || a.liquidGlass != b.liquidGlass) add("界面样式或玻璃开关不同")
    if (a.schema != b.schema || a.frameSources != b.frameSources) add("采集算法或 UI 帧来源不同")
    if ("unknown" in a.frameSources || "unknown" in b.frameSources || a.frameSources.isEmpty() || b.frameSources.isEmpty()) add("缺少 UI 帧来源记录")
    if (a.panelRates.isEmpty() || b.panelRates.isEmpty()) add("缺少面板刷新率采样")
    if (a.panelRates != b.panelRates) add("面板刷新率采样范围不同")
}

internal fun rankPerformanceHistory(
    entries: List<PerformanceHistoryEntry>, reference: PerformanceHistoryEntry, scope: String, metric: PerformanceHistoryMetric,
): List<PerformanceHistoryEntry> = comparablePerformanceHistory(entries, reference).filter {
    it.scopes[scope]?.rankingValue(metric) != null
}.sortedWith(compareByDescending<PerformanceHistoryEntry> { it.scopes.getValue(scope).rankingValue(metric) }
    .thenByDescending { it.startedWallMs })

/** 场景未覆盖的记录仍属于时间序列；图表保留缺口，排行再排除未知指标。 */
internal fun comparablePerformanceHistory(entries: List<PerformanceHistoryEntry>, reference: PerformanceHistoryEntry): List<PerformanceHistoryEntry> =
    entries.filter { it.completed && it.conditions.recorded && it.conditions == reference.conditions }

internal fun performanceNumber(value: Double, minimumDecimals: Int = 1): String {
    val decimals = maxOf(minimumDecimals, if (value == 0.0 || abs(value) >= 0.1) 1 else ceil(-log10(abs(value))).toInt() + 1)
    return String.format(Locale.ROOT, "%.${decimals}f", value)
}
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
