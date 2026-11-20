package com.android.purebilibili.feature.settings

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppPopupWindowHost
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.LocalAppGlassMenuPresenter
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalAppPopupWindowHostRenderer
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import com.android.purebilibili.feature.plugin.CDN_SPEED_TEST_SAMPLE_BYTES
import com.android.purebilibili.feature.plugin.CdnCompletedRequestsLabel
import com.android.purebilibili.feature.plugin.CdnResultSort
import com.android.purebilibili.feature.plugin.CdnResultSortSelector
import com.android.purebilibili.feature.plugin.CdnSpeedResult
import com.android.purebilibili.feature.plugin.CdnSpeedTest
import com.android.purebilibili.feature.plugin.CdnSpeedTestUpdate
import com.android.purebilibili.feature.plugin.CdnTransferRuntime
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.plugin.rankCdnSpeedResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun PlaybackCdnPreferenceMenu(
    title: String,
    icon: ImageVector,
    preference: PlaybackCdnPreference,
    onPreferenceChange: (PlaybackCdnPreference) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    AppPreference(
        title = title,
        icon = icon,
        subtitle = "测速、比较并选择新视频使用的线路",
        value = preference.displayName,
        onClick = { open = true },
    )
    if (open) {
        PlaybackCdnSpeedSheet(title, preference, onPreferenceChange, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackCdnSpeedSheet(
    title: String,
    preference: PlaybackCdnPreference,
    onPreferenceChange: (PlaybackCdnPreference) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<CdnSpeedTestUpdate?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var sort by rememberSaveable { mutableStateOf(CdnResultSort.FASTEST) }
    val completedByHost = if (sort == CdnResultSort.MOST_USED) {
        val initialCounts = remember {
            CdnTransferRuntime.state.value.nodes.associate { it.host to it.completed }
        }
        remember {
            CdnTransferRuntime.state.map { snapshot -> snapshot.nodes.associate { it.host to it.completed } }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = initialCounts).value
    } else emptyMap()
    val initialResults = remember(context) { CdnSpeedTest.currentResults(context).orEmpty() }
    val cached by remember(context) { CdnSpeedTest.observeResults(context) }
        .collectAsStateWithLifecycle(initialValue = initialResults)
    val options = remember { PlaybackCdnPreference.entries.map { AppSegmentOption(it, it.displayName) } }
    val results = when (val current = update) {
        is CdnSpeedTestUpdate.Testing -> current.tested + current.currentResult
        CdnSpeedTestUpdate.Preparing -> emptyList()
        else -> cached
    }
    val ranked = remember(results, sort, completedByHost) {
        rankCdnSpeedResults(results, sort, completedByHost)
    }
    val testing = update as? CdnSpeedTestUpdate.Testing
    val running = testing != null || update == CdnSpeedTestUpdate.Preparing
    val best = cached.firstOrNull()?.takeIf { it.bytesPerSecond != null }
    val maxDownloadMiB = (PlaybackCdnPreference.entries.size - 1) * CDN_SPEED_TEST_SAMPLE_BYTES / (1024 * 1024)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp

    fun toggleMeasurement() {
        if (job?.isActive == true) {
            job?.cancel()
            update = null
            return
        }
        update = CdnSpeedTestUpdate.Preparing
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                CdnSpeedTest.measure(context).collect { update = it }
            } finally {
                if (job === currentCoroutineContext()[Job]) {
                    if (update == CdnSpeedTestUpdate.Preparing || update is CdnSpeedTestUpdate.Testing) update = null
                    job = null
                }
            }
        }
        job?.start()
    }

    AppModalBottomSheet(onDismissRequest = onDismiss, onDismissStarted = { job?.cancel() }) {
        CdnSheetPopupHost(maxHeight) {
            val animateRanking = LocalComponentMotionEnabled.current &&
                !LocalSystemReduceMotion.current && rememberVisualWorkActive()
            val placementSpec = AppMotionTokens.standardSpec<IntOffset>()
            AppLazyColumn(
                modifier = Modifier.fillMaxWidth().then(
                    if (running || results.isNotEmpty()) Modifier.height(maxHeight) else Modifier.heightIn(max = maxHeight)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    AppText(
                        text = if (title == "CDN") "CDN 测速" else title,
                        modifier = Modifier.padding(bottom = 2.dp),
                        style = MaterialTheme.typography.titleLarge,
                        tapToCopyEnabled = false,
                    )
                }
                item(key = "cdn_live_speed_meter") {
                    val status = when (val current = update) {
                        CdnSpeedTestUpdate.Preparing -> "正在获取测速视频"
                        is CdnSpeedTestUpdate.Testing ->
                            "正在测试 ${current.tested.size + 1}/${current.total} · ${current.preference.displayName}"
                        is CdnSpeedTestUpdate.Failed -> "测速中断"
                        else -> best?.let { "最快线路 · ${it.preference.displayName}" }
                            ?: if (cached.isNotEmpty()) "测速结束" else "准备就绪"
                    }
                    val speed = if (running || update is CdnSpeedTestUpdate.Failed) {
                        testing?.bytesPerSecond
                    } else best?.bytesPerSecond
                    CdnLiveSpeedCard(
                        speed = speed,
                        status = status,
                        waitingForFirstSample = running && speed == null &&
                            (testing == null || testing.tested.isEmpty()),
                        overallProgress = when {
                            testing != null -> (testing.tested.size +
                                testing.downloaded.toFloat() / CDN_SPEED_TEST_SAMPLE_BYTES) / testing.total
                            !running && update !is CdnSpeedTestUpdate.Failed && cached.isNotEmpty() -> 1f
                            else -> 0f
                        },
                        buttonLabel = if (running) "停止测速" else if (cached.isEmpty()) "开始测速" else "重新测速",
                        onButtonClick = ::toggleMeasurement,
                    )
                }
                item {
                    SettingsSingleChoicePreference(
                        title = "视频线路",
                        subtitle = if (preference != PlaybackCdnPreference.AUTO) {
                            "线路不可用时自动切换备用"
                        } else if (best != null) {
                            "当前网络优先使用 ${best.preference.displayName}"
                        } else "测速后优先使用最快线路",
                        options = options,
                        selectedValue = preference,
                        onSelectionChange = onPreferenceChange,
                    )
                }
                item {
                    AppText(
                        text = "逐条下载同一视频片段，最多使用 $maxDownloadMiB MiB",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        tapToCopyEnabled = false,
                    )
                }
                when (val current = update) {
                    is CdnSpeedTestUpdate.Failed -> item {
                        AppText(current.message, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> if (!running && results.isNotEmpty() && best == null) item {
                        AppText("本次测速没有可用线路", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (ranked.isNotEmpty()) {
                    val scale = ranked.maxOf { it.bytesPerSecond ?: 0L }
                    item(key = "cdn_result_sort") {
                        CdnResultSortSelector(sort) { sort = it }
                    }
                    itemsIndexed(ranked, key = { _, result -> result.preference.storageValue }) { rank, result ->
                        CdnSpeedResultRow(
                            result = result,
                            scale = scale,
                            rank = rank,
                            isTesting = result.preference == testing?.preference,
                            blurRanking = testing != null,
                            showUsage = sort == CdnResultSort.MOST_USED,
                            completedRequests = result.host?.let(completedByHost::get),
                            fastest = !running && result == best,
                            selected = result.preference == preference ||
                                (preference == PlaybackCdnPreference.AUTO && !running && result == best),
                            modifier = if (animateRanking) {
                                Modifier.animateItem(fadeInSpec = null, placementSpec = placementSpec, fadeOutSpec = null)
                            } else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CdnSheetPopupHost(maxHeight: Dp, content: @Composable () -> Unit) {
    val presenter = LocalAppGlassMenuPresenter.current
    val needsWindowHost = LocalAppThemeConfig.current.popupGlassEnabled &&
        LocalAppPopupWindowHostRenderer.current != null &&
        presenter?.ownerView !== LocalView.current
    if (needsWindowHost) {
        // 菜单在测速面板所在窗口里读取面板背景和线路选项锚点。
        Box(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            AppPopupWindowHost(enableWindowMaterial = false, content = content)
        }
    } else {
        content()
    }
}

@Composable
private fun CdnLiveSpeedCard(
    speed: Long?,
    status: String,
    waitingForFirstSample: Boolean,
    overallProgress: Float,
    buttonLabel: String,
    onButtonClick: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        CdnLiveSpeedMeter(speed, status, waitingForFirstSample, overallProgress, buttonLabel, onButtonClick)
    }
}

@Composable
private fun CdnLiveSpeedMeter(
    speed: Long?,
    status: String,
    waitingForFirstSample: Boolean,
    overallProgress: Float,
    buttonLabel: String,
    onButtonClick: () -> Unit,
) {
    val gate = rememberDecorativeAnimationGate()
    val motionSpec = if (gate.active) AppMotionTokens.standardSpec<Float>() else snap()
    val sweep = animateFloatAsState(
        targetValue = cdnSpeedMeterFraction((speed ?: 0L) * 8.0 / 1_000_000),
        animationSpec = if (gate.active) emphasizedEnterTween(340) else snap(),
        label = "CDN live download speed",
    )
    val connectionSweep = remember(waitingForFirstSample) { Animatable(0f) }
    var connectionSweepPlayed by remember(waitingForFirstSample) { mutableStateOf(false) }
    LaunchedEffect(waitingForFirstSample, gate.active) {
        if (waitingForFirstSample && gate.active && !connectionSweepPlayed) {
            connectionSweepPlayed = true
            connectionSweep.animateTo(1f, emphasizedEnterTween(760))
        }
    }
    val progress = animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = motionSpec,
        label = "CDN download progress",
    )
    val measurer = rememberTextMeasurer()
    val axisColor = AppContentColors.secondaryText()
    val reachedLabelColor = AppContentColors.primaryText()
    val axisStyle = MaterialTheme.typography.labelSmall
    val accent = AppContentColors.accent()
    val track = accent.copy(alpha = 0.22f)
    val minorTickColor = axisColor.copy(alpha = 0.48f)
    val dialMaxWidth = if (LocalConfiguration.current.screenHeightDp < 480) 212.dp else 280.dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = dialMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .aspectRatio(1.18f)
                .then(gate.modifier)
                .drawWithCache {
                    val lastTick = CdnSpeedMeterTicks.lastIndex
                    val labels = CdnSpeedMeterTicks.map { value ->
                        val label = if (value == CdnSpeedMeterTicks.last()) "1G+" else value.toString()
                        measurer.measure(label, style = axisStyle, maxLines = 1)
                    }
                    val labelHeight = labels.maxOf { it.size.height }.toFloat()
                    val labelWidth = labels.maxOf { it.size.width }.toFloat()
                    val arcStroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    val edgeInset = arcStroke.width / 2f + 4.dp.toPx()
                    val startAngle = 135f
                    val totalAngle = 270f
                    val endDrop = sin(Math.PI / 4).toFloat()
                    val radius = minOf(
                        size.width / 2f - edgeInset,
                        (size.height - 2 * edgeInset) / (1f + endDrop),
                    ).coerceAtLeast(0f)
                    val center = Offset(size.width / 2f, (size.height + radius * (1f - endDrop)) / 2f)
                    val diameter = Size(radius * 2f, radius * 2f)
                    val arcTopLeft = center - Offset(radius, radius)
                    fun directionAt(tick: Float): Offset {
                        val theta = Math.toRadians((startAngle + totalAngle * tick / lastTick).toDouble())
                        return Offset(cos(theta).toFloat(), sin(theta).toFloat())
                    }
                    val majorDirections = labels.indices.map { directionAt(it.toFloat()) }
                    val minorDirections = (0 until lastTick).map { directionAt(it + 0.5f) }
                    val labelRadius = (radius - arcStroke.width / 2f - 11.dp.toPx() -
                        maxOf(labelWidth, labelHeight) / 2f).coerceAtLeast(0f)
                    val labelSpacing = 2 * labelRadius * sin(Math.PI * totalAngle / (360 * lastTick)).toFloat()
                    val labelStep = if (labelSpacing >= labelWidth + 2.dp.toPx()) 1 else 2
                    val labelOffsets = majorDirections.mapIndexed { index, direction ->
                        val anchor = center + direction * labelRadius
                        val label = labels[index].size
                        anchor - Offset(label.width / 2f, label.height / 2f)
                    }
                    val tickOuterRadius = radius - arcStroke.width / 2f - 3.dp.toPx()
                    val majorTickLength = 4.dp.toPx()
                    val minorTickLength = 2.dp.toPx()
                    val majorTickWidth = 1.dp.toPx()
                    val minorTickWidth = 1.dp.toPx()
                    onDrawBehind {
                        if (radius == 0f) return@onDrawBehind
                        drawArc(track, startAngle, totalAngle, false, arcTopLeft, diameter, style = arcStroke)
                        val fraction = sweep.value
                        val angle = fraction * totalAngle
                        if (angle > 0f) {
                            drawArc(accent, startAngle, angle, false, arcTopLeft, diameter, style = arcStroke)
                        }
                        if (waitingForFirstSample && gate.active && connectionSweep.isRunning) {
                            val length = 0.18f
                            val head = connectionSweep.value * (1f + length)
                            val leadingEdge = head.coerceAtMost(1f)
                            val trailingEdge = (head - length).coerceAtLeast(0f)
                            if (leadingEdge > trailingEdge) {
                                drawArc(
                                    color = accent,
                                    startAngle = startAngle + trailingEdge * totalAngle,
                                    sweepAngle = (leadingEdge - trailingEdge) * totalAngle,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = diameter,
                                    style = arcStroke,
                                )
                            }
                        }
                        if (tickOuterRadius > majorTickLength) {
                            minorDirections.forEach { direction ->
                                drawLine(
                                    color = minorTickColor,
                                    start = center + direction * (tickOuterRadius - minorTickLength),
                                    end = center + direction * tickOuterRadius,
                                    strokeWidth = minorTickWidth,
                                    cap = StrokeCap.Round,
                                )
                            }
                            majorDirections.forEach { direction ->
                                drawLine(
                                    color = axisColor,
                                    start = center + direction * (tickOuterRadius - majorTickLength),
                                    end = center + direction * tickOuterRadius,
                                    strokeWidth = majorTickWidth,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        labels.forEachIndexed { index, layout ->
                            if (index % labelStep == 0) {
                                val reached = speed != null && fraction >= index.toFloat() / lastTick
                                drawText(
                                    layout,
                                    color = if (reached) reachedLabelColor else axisColor,
                                    topLeft = labelOffsets[index],
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(
                    text = speed?.let(::cdnSpeedValue) ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    tapToCopyEnabled = false,
                )
                AppText("Mbps", style = axisStyle, color = axisColor, tapToCopyEnabled = false)
            }
        }
        AppText(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = axisColor,
            textAlign = TextAlign.Center,
            tapToCopyEnabled = false,
        )
        AppLinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().semantics {
                contentDescription = "线路测速总进度"
            },
        )
        AppButton(
            onClick = onButtonClick,
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().heightIn(min = 48.dp),
        ) {
            AppText(buttonLabel, tapToCopyEnabled = false)
        }
    }
}

private val CdnSpeedMeterTicks = listOf(0, 5, 10, 50, 100, 250, 500, 750, 1_000)

// Each tick-to-tick segment spans the same angle and interpolates within its own speed range.
private fun cdnSpeedMeterFraction(mbps: Double): Float {
    val upper = CdnSpeedMeterTicks.indexOfFirst { mbps < it }
    if (upper <= 0) return if (upper == 0) 0f else 1f
    val lowerSpeed = CdnSpeedMeterTicks[upper - 1]
    val withinSegment = (mbps - lowerSpeed) / (CdnSpeedMeterTicks[upper] - lowerSpeed)
    return ((upper - 1 + withinSegment) / CdnSpeedMeterTicks.lastIndex).toFloat()
}

@Composable
private fun CdnSpeedResultRow(
    result: CdnSpeedResult,
    scale: Long,
    rank: Int,
    isTesting: Boolean,
    blurRanking: Boolean,
    showUsage: Boolean,
    completedRequests: Int?,
    fastest: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val gate = rememberDecorativeAnimationGate()
    val speed = result.bytesPerSecond
    val fraction = animateFloatAsState(
        targetValue = if (speed != null && scale > 0) speed.toFloat() / scale else 0f,
        animationSpec = if (gate.active) AppMotionTokens.standardSpec() else snap(),
        label = "CDN measured speed bar",
    )
    val fill = AppContentColors.accent()
    val track = AppContentColors.secondaryText().copy(alpha = 0.14f)
    val canBlur = blurRanking && gate.active &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isLowBlurBudgetForced()
    val rankEffect = remember { BlurRenderEffectCache() }
    val blur = remember { Animatable(0f) }
    val previousRank = remember { intArrayOf(rank) }
    val motionSpec = AppMotionTokens.standardSpec<Float>()
    LaunchedEffect(rank, canBlur) {
        val moved = previousRank[0] != rank
        previousRank[0] = rank
        if (!canBlur) {
            blur.snapTo(0f)
        } else if (moved) {
            blur.snapTo(1f)
            blur.animateTo(0f, motionSpec)
        }
    }
    Column(
        modifier = modifier.then(gate.modifier).then(
            if (canBlur && blur.isRunning) Modifier.graphicsLayer {
                val radius = (blur.value * 2.dp.toPx()).roundToInt().toFloat()
                renderEffect = rankEffect.resolve(radius)
            } else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppText(
            listOfNotNull(result.preference.displayName, "最快".takeIf { fastest }, "已选".takeIf { selected })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        AppText(
            text = when {
                isTesting -> speed?.let { "${cdnSpeedLabel(it)} · 测速中" } ?: "等待响应"
                speed != null -> "${cdnSpeedLabel(speed)} · 响应 ${result.responseMs} ms"
                else -> "测速失败"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val corners = CornerRadius(size.height / 2f)
            drawRoundRect(track, cornerRadius = corners)
            drawRoundRect(fill, size = Size(size.width * fraction.value.coerceIn(0f, 1f), size.height), cornerRadius = corners)
        }
        if (showUsage) CdnCompletedRequestsLabel(completedRequests)
    }
}

private fun cdnSpeedValue(bytesPerSecond: Long): String =
    String.format(Locale.ROOT, "%.1f", bytesPerSecond * 8.0 / 1_000_000)

private fun cdnSpeedLabel(bytesPerSecond: Long): String = "${cdnSpeedValue(bytesPerSecond)} Mbps"
