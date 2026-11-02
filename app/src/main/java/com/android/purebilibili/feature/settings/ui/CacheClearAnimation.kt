package com.android.purebilibili.feature.settings

import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.util.CacheClearTarget
import com.android.purebilibili.core.util.CacheUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** current/total 表示已完成类别数，释放容量仅在重新统计后提供。 */
data class CacheClearProgress(
    val current: Long,
    val total: Long,
    val isComplete: Boolean = false,
    val clearedSize: String = "",
    val message: String = "正在准备清理"
)

@Composable
internal fun CacheClearConfirmDialog(
    breakdown: CacheUtils.CacheBreakdown?,
    selectedCacheSizeSummary: String,
    options: List<CacheClearOptionUiModel>,
    selectedTargets: Set<CacheClearTarget>,
    onTargetToggle: (CacheClearTarget, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    clearing: Boolean = false,
    progress: CacheClearProgress? = null,
    clearSucceeded: Boolean = false,
    releasedSize: String = "",
    onProgressDismiss: () -> Unit = {}
) {
    val segments = remember(breakdown, selectedTargets, options) {
        resolveCacheClearDonutSegments(breakdown, selectedTargets, options)
    }
    var showProgress by remember { mutableStateOf(false) }
    var highlightedTarget by remember { mutableStateOf<CacheClearTarget?>(null) }
    LaunchedEffect(clearing) {
        showProgress = false
        if (clearing) { delay(150); showProgress = true }
    }
    val emptyAfterClear = clearSucceeded && breakdown != null && breakdown.totalSize == 0L && !clearing
    val context = LocalContext.current
    var deviceStorage by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(breakdown) {
        deviceStorage = withContext(Dispatchers.IO) {
            runCatching { StatFs(context.filesDir.path).let { it.totalBytes to it.availableBytes } }.getOrNull()
        }
    }
    Dialog(
        onDismissRequest = { if (!clearing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = !clearing, dismissOnClickOutside = !clearing)
    ) {
        ModalWindowBlurBehindEffect(enabled = true)
        BackHandler(enabled = clearing) { /* 实际任务完成前禁止取消，避免把部分清理显示为成功。 */ }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            // 两条材质路径共用内容布局；玻璃外观由 AppPopupSurface 渲染，保留原生材质默认值。
            AppPopupSurface(
                type = AppPopupSurfaceType.DIALOG,
                modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth()
                    .heightIn(max = (maxHeight - 32.dp).coerceAtLeast(1.dp)),
                shape = AppShapes.container(ContainerLevel.Dialog),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CacheClearUsageDonut(
                        segments = segments,
                        centerSize = resolveCacheClearDonutCenterSize(breakdown, selectedTargets),
                        loading = breakdown == null,
                        complete = emptyAfterClear,
                        active = !clearing,
                        onSegmentClick = { target -> onTargetToggle(target, target !in selectedTargets) },
                        onSegmentPress = { highlightedTarget = it }
                    )
                    AppText(if (emptyAfterClear) "缓存已清空" else "存储使用情况",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        when {
                            clearSucceeded && !clearing -> "已释放 $releasedSize 磁盘空间"
                            breakdown == null -> "正在统计缓存…"
                            else -> "磁盘 ${breakdown.format()} · 内存约 ${formatCacheClearBytes(breakdown.memoryCache)}"
                        }, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText()
                    )
                    if (!emptyAfterClear) {
                        CacheDeviceStorageBar(breakdown, deviceStorage)
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.clip(AppShapes.container(ContainerLevel.Card))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .24f))) {
                            segments.sortedByDescending { it.bytes }.forEach { segment ->
                                key(segment.target) {
                                    val option = options.first { it.target == segment.target }
                                    CacheClearCategoryRow(segment, option.description, enabled = !clearing,
                                        highlighted = highlightedTarget == segment.target,
                                        onToggle = { onTargetToggle(segment.target, it) })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        AppText(selectedCacheSizeSummary, style = MaterialTheme.typography.bodyMedium,
                            color = AppContentColors.secondaryText())
                        Spacer(Modifier.height(12.dp))
                        AppButton(onClick = onConfirm,
                            enabled = !clearing && breakdown != null && selectedTargets.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            AppText(resolveCacheClearButtonLabel(breakdown, selectedTargets))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AppText("可重建的缓存会在需要时重新获取。不会删除离线缓存、下载内容和播放记录。",
                        style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                    AppTextButton(onClick = { if (!clearing) onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        AppText(if (clearSucceeded) "完成" else "取消")
                    }
                }
            }
            if (clearing) {
                // 延迟显示期间也拦截操作；底层图表显式停止循环，不依赖遮罩透明度。
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} })
                if (showProgress && progress != null) {
                    CacheClearAnimationDialog(progress, onProgressDismiss)
                }
            }
        }
    }
}

@Composable
private fun CacheDeviceStorageBar(breakdown: CacheUtils.CacheBreakdown?, storage: Pair<Long, Long>?) {
    val cacheFraction = if (breakdown != null && storage != null && storage.first > 0L)
        (breakdown.reclaimableDiskSize.toDouble() / storage.first).toFloat().coerceIn(0f, 1f) else 0f
    val usedFraction = storage?.let { if (it.first > 0L) (1.0 - it.second.toDouble() / it.first).toFloat().coerceIn(0f, 1f) else 0f } ?: 0f
    val reduced = LocalSystemReduceMotion.current
    val cache = animateFloatAsState(cacheFraction, if (reduced) snap() else AppMotionTokens.standardSpec(), label = "cacheDeviceFraction")
    val used = animateFloatAsState(usedFraction, if (reduced) snap() else AppMotionTokens.standardSpec(), label = "cacheDeviceUsed")
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface
    Spacer(Modifier.height(12.dp))
    Canvas(Modifier.widthIn(max = 174.dp).fillMaxWidth(.8f).height(4.dp).semantics {
        contentDescription = if (storage == null || breakdown == null) "设备存储统计中" else "设备存储占用；缓存 ${formatCacheClearBytes(breakdown.reclaimableDiskSize)}"
    }) {
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(track.copy(alpha = .1f), cornerRadius = corner)
        val cacheWidth = if (cache.value > 0f) maxOf(4.dp.toPx(), size.width * cache.value).coerceAtMost(size.width) else 0f
        val usedWidth = maxOf(cacheWidth, size.width * used.value)
        drawRoundRect(track.copy(alpha = .25f), size = Size(usedWidth, size.height), cornerRadius = corner)
        if (cacheWidth > 0) drawRoundRect(color, size = Size(cacheWidth, size.height), cornerRadius = corner)
    }
}

@Composable
private fun CacheClearCategoryRow(segment: CacheClearDonutSegment, description: String, enabled: Boolean, highlighted: Boolean, onToggle: (Boolean) -> Unit) {
    val color = CacheClearChartColors[segment.colorIndex % CacheClearChartColors.size]
    val reduced = LocalSystemReduceMotion.current
    val checked = animateFloatAsState(if (segment.selected) 1f else 0f,
        if (reduced) snap() else AppMotionTokens.standardSpec(), label = "cacheChecked-${segment.target}")
    val unknown = !segment.sizeKnown
    Row(Modifier.fillMaxWidth().background(if (highlighted) color.copy(alpha = .12f) else Color.Transparent).toggleable(segment.selected, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
        .semantics { contentDescription = "${segment.title}，$description" }
        .padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) {
            val t = checked.value
            drawCircle(color.copy(alpha = .5f), style = Stroke(1.5.dp.toPx()))
            if (t > 0f) {
                drawCircle(color, radius = size.minDimension / 2 * minOf(t * 2, 1f))
                val p = ((t - .5f) * 2).coerceIn(0f, 1f)
                val path = Path().apply {
                    moveTo(size.width * .25f, size.height * .5f)
                    val elbow = Offset(size.width * .43f, size.height * .68f)
                    if (p < .35f) lineTo(size.width * .25f + (elbow.x - size.width * .25f) * p / .35f,
                        size.height * .5f + (elbow.y - size.height * .5f) * p / .35f)
                    else { lineTo(elbow.x, elbow.y); lineTo(elbow.x + size.width * .34f * (p - .35f) / .65f,
                        elbow.y - size.height * .4f * (p - .35f) / .65f) }
                }
                if (p > 0) drawPath(path, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AppText(cacheClearCategoryTitle(segment.target), fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            AppText(if (segment.target == CacheClearTarget.APP_METADATA) "按需重新获取" else segment.percentLabel,
                style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText())
        }
        Spacer(Modifier.width(8.dp))
        AppText(when { segment.target == CacheClearTarget.APP_METADATA -> "可重建"; unknown -> "统计中"; else -> formatCacheClearBytes(segment.bytes) },
            color = AppContentColors.link(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 与缓存选择内容共用一个窗口，避免任务切换时先关闭选择窗再弹出清理窗。 */
@Composable
fun CacheClearAnimationDialog(progress: CacheClearProgress, onDismiss: () -> Unit) {
    val gate = rememberDecorativeAnimationGate(
        respectComponentMotion = false,
        respectSystemReduceMotion = false
    )
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val value = cacheClearProgressFraction(progress.current, progress.total)?.let { if (progress.isComplete) 1f else it.coerceAtMost(.99f) }
    val animated = animateFloatAsState(if (progress.isComplete) 1f else value ?: 0f,
        if (LocalSystemReduceMotion.current) snap() else AppMotionTokens.standardSpec(), label = "cacheClearProgress")
    val window = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.windowSizeClass
    val characterSize = if (window.heightDp < 480.dp) 96.dp else 150.dp
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)), contentAlignment = Alignment.BottomCenter) {
        AppPopupSurface(type = AppPopupSurfaceType.SHEET,
            modifier = Modifier.padding(12.dp).widthIn(max = 480.dp).fillMaxWidth()
                .heightIn(max = (window.heightDp * .8f).coerceAtLeast(96.dp)),
            shape = AppShapes.container(ContainerLevel.Sheet), containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BlueSnowMaidAnimation(
                    animation = if (progress.isComplete) MaidAnimation.CLEAN_COMPLETE else MaidAnimation.CLEANING,
                    modifier = Modifier.size(characterSize).then(gate.modifier),
                    isVisible = gate.active,
                    reducedMotion = LocalSystemReduceMotion.current,
                    staticDisplayDurationMs = 600L,
                    completionHoldDurationMs = if (progress.isComplete) 600L else 0L,
                    onFinished = { if (progress.isComplete) latestOnDismiss() }
                )
                // 完成时保留进度槽位，呈现 100% 并避免面板高度突然收缩；两条材质路径共用。
                AppText(if (progress.isComplete) "100%" else if (value == null) "准备中…" else "${ceil(value * 100).toInt()}%",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (value != null || progress.isComplete) {
                    AppLinearProgressIndicator(progress = { animated.value }, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().height(5.dp))
                } else {
                    Spacer(Modifier.height(5.dp))
                }
                AppText(if (progress.isComplete) "清理完成" else "正在清理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AppText(if (progress.isComplete) "已释放 ${progress.clearedSize} 磁盘空间" else progress.message,
                    style = MaterialTheme.typography.bodyMedium, color = AppContentColors.secondaryText())
                AppText(if (progress.isComplete) "即将返回缓存详情" else "请稍候，清理完成后会更新缓存详情",
                    style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
            }
        }
    }
}
