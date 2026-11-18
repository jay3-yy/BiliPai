package com.android.purebilibili.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.feature.plugin.CDN_SPEED_TEST_SAMPLE_BYTES
import com.android.purebilibili.feature.plugin.CdnSpeedResult
import com.android.purebilibili.feature.plugin.CdnSpeedTest
import com.android.purebilibili.feature.plugin.CdnSpeedTestUpdate
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

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
    val initialResults = remember(context) { CdnSpeedTest.currentResults(context).orEmpty() }
    val cached by remember(context) { CdnSpeedTest.observeResults(context) }
        .collectAsStateWithLifecycle(initialValue = initialResults)
    val options = remember { PlaybackCdnPreference.entries.map { AppSegmentOption(it, it.displayName) } }
    val tested = when (val current = update) {
        is CdnSpeedTestUpdate.Testing -> current.tested
        CdnSpeedTestUpdate.Preparing -> emptyList()
        else -> cached
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
        AppLazyColumn(
            modifier = Modifier.fillMaxWidth().then(
                if (running || tested.isNotEmpty()) Modifier.height(maxHeight) else Modifier.heightIn(max = maxHeight)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AppText(title, style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsSingleChoicePreference(
                    title = "视频线路",
                    subtitle = if (preference != PlaybackCdnPreference.AUTO) {
                        "手动固定，无法播放时使用备用线路"
                    } else if (best != null) {
                        "当前网络优先使用 ${best.preference.displayName}"
                    } else {
                        "测速后使用当前网络最快的线路"
                    },
                    options = options,
                    selectedValue = preference,
                    onSelectionChange = onPreferenceChange,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppText(
                        text = "同一视频片段逐条测试，样本最多 $maxDownloadMiB MiB",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                    AppButton(onClick = ::toggleMeasurement) {
                        AppText(if (running) "停止" else if (cached.isEmpty()) "开始测速" else "重新测速")
                    }
                }
            }
            when (val current = update) {
                CdnSpeedTestUpdate.Preparing -> item {
                    AppText("正在获取测速视频", style = MaterialTheme.typography.bodySmall)
                }
                is CdnSpeedTestUpdate.Testing -> item { CdnTestProgress(current) }
                is CdnSpeedTestUpdate.Failed -> item {
                    AppText(current.message, style = MaterialTheme.typography.bodySmall)
                }
                else -> if (tested.isNotEmpty() && best == null) item {
                    AppText("本次测速没有可用线路", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (tested.isNotEmpty()) {
                val scale = tested.maxOfOrNull { it.bytesPerSecond ?: 0L } ?: 0L
                item { AppText("线路下载速度", style = MaterialTheme.typography.titleSmall) }
                items(tested, key = { it.preference.storageValue }) { result ->
                    CdnSpeedResultRow(
                        result, scale,
                        fastest = !running && result == best,
                        selected = result.preference == preference ||
                            (preference == PlaybackCdnPreference.AUTO && !running && result == best),
                    )
                }
            }
        }
    }
}

@Composable
private fun CdnTestProgress(testing: CdnSpeedTestUpdate.Testing) {
    val gate = rememberDecorativeAnimationGate()
    val progress = animateFloatAsState(
        targetValue = (testing.tested.size + testing.downloaded.toFloat() / CDN_SPEED_TEST_SAMPLE_BYTES) / testing.total,
        animationSpec = if (gate.active) AppMotionTokens.standardSpec() else snap(),
        label = "CDN download progress",
    )
    Column(modifier = gate.modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppText(
            "正在测试 ${testing.tested.size + 1}/${testing.total} · ${testing.preference.displayName}",
            style = MaterialTheme.typography.bodySmall,
        )
        AppText(testing.bytesPerSecond?.let(::cdnSpeedLabel) ?: "等待响应", style = MaterialTheme.typography.titleMedium)
        AppLinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CdnSpeedResultRow(result: CdnSpeedResult, scale: Long, fastest: Boolean, selected: Boolean) {
    val gate = rememberDecorativeAnimationGate()
    val speed = result.bytesPerSecond
    val fraction = animateFloatAsState(
        targetValue = if (speed != null && scale > 0) speed.toFloat() / scale else 0f,
        animationSpec = if (gate.active) AppMotionTokens.standardSpec() else snap(),
        label = "CDN measured speed bar",
    )
    val fill = AppContentColors.accent()
    val track = AppContentColors.secondaryText().copy(alpha = 0.14f)
    Column(modifier = gate.modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(
            listOfNotNull(result.preference.displayName, "最快".takeIf { fastest }, "已选".takeIf { selected })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (speed != null) {
            AppText(
                "${cdnSpeedLabel(speed)} · 响应 ${result.responseMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val corners = CornerRadius(size.height / 2f)
                drawRoundRect(track, cornerRadius = corners)
                drawRoundRect(fill, size = Size(size.width * fraction.value.coerceIn(0f, 1f), size.height), cornerRadius = corners)
            }
        } else {
            AppText("测速失败", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
    }
}

private fun cdnSpeedLabel(bytesPerSecond: Long): String =
    String.format(Locale.ROOT, "%.1f Mbps", bytesPerSecond * 8.0 / 1_000_000)
