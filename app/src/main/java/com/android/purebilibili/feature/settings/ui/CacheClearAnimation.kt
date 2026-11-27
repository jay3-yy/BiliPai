package com.android.purebilibili.feature.settings

import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.BlueSnowMaidAnimation
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalMaidLottieAnimationEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.MaidAnimation
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.core.util.CacheClearTarget
import com.android.purebilibili.core.util.CacheUtils
import kotlinx.coroutines.Dispatchers
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
    stage: CacheClearDialogStage,
    breakdown: CacheUtils.CacheBreakdown?,
    options: List<CacheClearOptionUiModel>,
    selectedTargets: Set<CacheClearTarget>,
    clearedTargets: Set<CacheClearTarget>,
    autoClearInterval: SettingsManager.AutoCacheClearInterval = SettingsManager.AutoCacheClearInterval.NEVER,
    autoClearThresholdGb: Int = SettingsManager.DEFAULT_AUTO_CACHE_CLEAR_THRESHOLD_GB,
    lastAutoClearAtMillis: Long = 0L,
    onTargetToggle: (CacheClearTarget, Boolean) -> Unit,
    onPresetSelect: (CacheClearPreset) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    progress: CacheClearProgress? = null,
    releasedBytes: Long = 0L,
    beforeDiskBytes: Long? = null,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onBackToSelect: () -> Unit = {}
) {
    val isClearing = stage == CacheClearDialogStage.CLEARING
    val segments = remember(breakdown, selectedTargets, options) {
        resolveCacheClearDonutSegments(breakdown, selectedTargets, options)
    }
    val palette = rememberCacheClearPalette()
    val defaultColorIndices = remember(options.size) { List(options.size) { it } }
    var colorIndices by remember(options.size) { mutableStateOf(defaultColorIndices) }
    val isDefaultColors = colorIndices == defaultColorIndices
    var highlightedTarget by remember { mutableStateOf<CacheClearTarget?>(null) }
    val context = LocalContext.current
    var deviceStorage by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(breakdown) {
        deviceStorage = withContext(Dispatchers.IO) {
            runCatching { StatFs(context.filesDir.path).let { it.totalBytes to it.availableBytes } }.getOrNull()
        }
    }
    val reduceMotion = LocalSystemReduceMotion.current

    Dialog(
        onDismissRequest = { if (!isClearing) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !isClearing,
            dismissOnClickOutside = !isClearing
        )
    ) {
        ModalWindowBlurBehindEffect(enabled = true)
        BackHandler(enabled = isClearing) { /* 实际任务完成前禁止取消，避免把部分清理显示为成功。 */ }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            val dialogMaxWidth = (maxWidth - 32.dp).coerceAtMost(460.dp)
            val dialogMaxHeight = (maxHeight - 32.dp).coerceAtLeast(1.dp)
            // 单容器内通过 AnimatedContent 切换选择/清理/完成/失败状态，两条材质路径共用结构。
            AppPopupSurface(
                type = AppPopupSurfaceType.DIALOG,
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight),
                shape = AppShapes.container(ContainerLevel.Dialog),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        if (reduceMotion) {
                            (fadeIn(snap()) togetherWith fadeOut(snap()))
                        } else {
                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.98f, animationSpec = tween(140)))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "cacheClearStage"
                ) { currentStage ->
                    when (currentStage) {
                        CacheClearDialogStage.SELECT -> {
                            CacheClearSelectContent(
                                dialogMaxWidth = dialogMaxWidth,
                                dialogMaxHeight = dialogMaxHeight,
                                breakdown = breakdown,
                                deviceStorage = deviceStorage,
                                autoClearInterval = autoClearInterval,
                                autoClearThresholdGb = autoClearThresholdGb,
                                lastAutoClearAtMillis = lastAutoClearAtMillis,
                                segments = segments,
                                palette = palette,
                                colorIndices = colorIndices,
                                isDefaultColors = isDefaultColors,
                                onShuffleColors = {
                                    colorIndices = resolveShuffledCacheColorIndices(
                                        count = options.size,
                                        paletteSize = palette.size,
                                        current = colorIndices
                                    )
                                },
                                onResetColors = { colorIndices = defaultColorIndices },
                                options = options,
                                selectedTargets = selectedTargets,
                                highlightedTarget = highlightedTarget,
                                onHighlightChange = { highlightedTarget = it },
                                onTargetToggle = onTargetToggle,
                                onPresetSelect = onPresetSelect,
                                onConfirm = onConfirm,
                                onDismiss = onDismiss
                            )
                        }
                        CacheClearDialogStage.CLEARING -> {
                            CacheClearProgressContent(
                                dialogMaxHeight = dialogMaxHeight,
                                progress = progress ?: CacheClearProgress(0L, clearedTargets.size.toLong())
                            )
                        }
                        CacheClearDialogStage.COMPLETE -> {
                            CacheClearCompleteContent(
                                dialogMaxHeight = dialogMaxHeight,
                                releasedBytes = releasedBytes,
                                beforeDiskBytes = beforeDiskBytes,
                                clearedTargets = clearedTargets,
                                options = options,
                                palette = palette,
                                colorIndices = colorIndices,
                                breakdown = breakdown,
                                onContinueSelect = onBackToSelect,
                                onDismiss = onDismiss
                            )
                        }
                        CacheClearDialogStage.ERROR -> {
                            CacheClearErrorContent(
                                errorMessage = errorMessage,
                                onRetry = onRetry,
                                onBackToSelect = onBackToSelect
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CacheClearSelectContent(
    dialogMaxWidth: Dp,
    dialogMaxHeight: Dp,
    breakdown: CacheUtils.CacheBreakdown?,
    deviceStorage: Pair<Long, Long>?,
    autoClearInterval: SettingsManager.AutoCacheClearInterval,
    autoClearThresholdGb: Int,
    lastAutoClearAtMillis: Long,
    segments: List<CacheClearDonutSegment>,
    palette: List<Color>,
    colorIndices: List<Int>,
    isDefaultColors: Boolean,
    onShuffleColors: () -> Unit,
    onResetColors: () -> Unit,
    options: List<CacheClearOptionUiModel>,
    selectedTargets: Set<CacheClearTarget>,
    highlightedTarget: CacheClearTarget?,
    onHighlightChange: (CacheClearTarget?) -> Unit,
    onTargetToggle: (CacheClearTarget, Boolean) -> Unit,
    onPresetSelect: (CacheClearPreset) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activePreset = remember(selectedTargets) { resolveCacheClearPreset(selectedTargets) }
    val haptics = rememberAppHapticFeedback(AppHapticScene.MENUS)
    val compactVertical = dialogMaxHeight < 640.dp
    val sideBySideHero = dialogMaxWidth >= 320.dp
    val donutSize = when {
        !sideBySideHero -> 136.dp
        compactVertical -> 112.dp
        else -> 128.dp
    }
    val sortedSegments = remember(segments) { segments.sortedByDescending { it.bytes } }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (sideBySideHero) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CacheClearUsageDonut(
                    segments = segments,
                    centerSize = resolveCacheClearDonutCenterSize(breakdown, selectedTargets),
                    centerCaption = resolveCacheClearDonutCenterCaption(breakdown, selectedTargets),
                    loading = breakdown == null,
                    complete = false,
                    active = true,
                    onSegmentClick = { target -> onTargetToggle(target, target !in selectedTargets) },
                    onSegmentPress = onHighlightChange,
                    donutSize = donutSize,
                    ringThickness = if (compactVertical) 19.dp else 22.dp,
                    compactText = true,
                    palette = palette,
                    colorIndices = colorIndices
                )
                Column(modifier = Modifier.weight(1f)) {
                    CacheClearHeroSummary(
                        breakdown = breakdown,
                        deviceStorage = deviceStorage,
                        centered = false
                    )
                }
            }
        } else {
            CacheClearUsageDonut(
                segments = segments,
                centerSize = resolveCacheClearDonutCenterSize(breakdown, selectedTargets),
                centerCaption = resolveCacheClearDonutCenterCaption(breakdown, selectedTargets),
                loading = breakdown == null,
                complete = false,
                active = true,
                onSegmentClick = { target -> onTargetToggle(target, target !in selectedTargets) },
                onSegmentPress = onHighlightChange,
                donutSize = donutSize,
                ringThickness = 22.dp,
                compactText = true,
                palette = palette,
                colorIndices = colorIndices
            )
            Spacer(Modifier.height(8.dp))
            CacheClearHeroSummary(
                breakdown = breakdown,
                deviceStorage = deviceStorage,
                centered = true
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppFilterChip(
                    selected = activePreset == CacheClearPreset.RECOMMENDED,
                    onClick = { onPresetSelect(CacheClearPreset.RECOMMENDED) },
                    leadingIcon = if (activePreset == CacheClearPreset.RECOMMENDED) {
                        { AppIcon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { AppText("推荐清理", style = MaterialTheme.typography.labelMedium) }
                )
                AppFilterChip(
                    selected = activePreset == CacheClearPreset.SPACE_SAVING,
                    onClick = { onPresetSelect(CacheClearPreset.SPACE_SAVING) },
                    leadingIcon = if (activePreset == CacheClearPreset.SPACE_SAVING) {
                        { AppIcon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { AppText("释放空间", style = MaterialTheme.typography.labelMedium) }
                )
                AppFilterChip(
                    selected = activePreset == CacheClearPreset.ALL,
                    onClick = { onPresetSelect(CacheClearPreset.ALL) },
                    leadingIcon = if (activePreset == CacheClearPreset.ALL) {
                        { AppIcon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { AppText("全部", style = MaterialTheme.typography.labelMedium) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = !isDefaultColors) {
                    AppIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResetColors()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "恢复默认配色",
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                AppIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onShuffleColors()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = "随机配色",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(18.dp)
                    )
                }
                AppText(
                    text = "已选 ${selectedTargets.size}/${options.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText()
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
        ) {
            sortedSegments.forEachIndexed { index, segment ->
                key(segment.target) {
                    val option = options.first { it.target == segment.target }
                    val colorIdx = colorIndices.getOrElse(segment.colorIndex) { segment.colorIndex }
                    val segmentColor = palette[colorIdx % palette.size]
                    CacheClearCategoryRow(
                        segment = segment,
                        segmentColor = segmentColor,
                        description = option.description,
                        enabled = true,
                        highlighted = highlightedTarget == segment.target,
                        onToggle = { onTargetToggle(segment.target, it) }
                    )
                    if (index < sortedSegments.lastIndex) {
                        AppHorizontalDivider(
                            modifier = Modifier.padding(start = 46.dp, end = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = AppContentColors.secondaryText(),
                modifier = Modifier.size(16.dp)
            )
            AppText(
                text = resolveAutoCacheClearStatusSummary(
                    interval = autoClearInterval,
                    thresholdGb = autoClearThresholdGb,
                    reclaimableDiskBytes = breakdown?.reclaimableDiskSize,
                    lastAutoClearAtMillis = lastAutoClearAtMillis
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        val buttonLabel = resolveCacheClearButtonLabel(breakdown, selectedTargets)
        val reduceMotion = LocalSystemReduceMotion.current
        AppButton(
            onClick = onConfirm,
            enabled = breakdown != null && selectedTargets.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
        ) {
            AnimatedContent(
                targetState = buttonLabel,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                    }
                },
                label = "cacheClearButtonLabel"
            ) { label ->
                AppText(label)
            }
        }
        Spacer(Modifier.height(6.dp))
        AppText(
            text = "可重建缓存会按需重新获取，不会删除离线缓存、下载内容和播放记录。",
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
            textAlign = TextAlign.Center
        )
        AppTextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            AppText("取消")
        }
    }
}

@Composable
private fun CacheClearHeroSummary(
    breakdown: CacheUtils.CacheBreakdown?,
    deviceStorage: Pair<Long, Long>?,
    centered: Boolean,
) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(horizontalAlignment = alignment) {
        AppText(
            text = "存储使用情况",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            text = if (breakdown == null) {
                "正在统计缓存占用…"
            } else {
                "可回收磁盘 ${breakdown.format()} · 内存约 ${formatCacheClearBytes(breakdown.memoryCache)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            textAlign = textAlign
        )
        CacheDeviceStorageBar(
            breakdown = breakdown,
            storage = deviceStorage,
            centered = centered
        )
    }
}

@Composable
private fun CacheDeviceStorageBar(
    breakdown: CacheUtils.CacheBreakdown?,
    storage: Pair<Long, Long>?,
    centered: Boolean,
) {
    val cacheFraction = if (breakdown != null && storage != null && storage.first > 0L) {
        (breakdown.reclaimableDiskSize.toDouble() / storage.first).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val usedFraction = storage?.let {
        if (it.first > 0L) (1.0 - it.second.toDouble() / it.first).toFloat().coerceIn(0f, 1f) else 0f
    } ?: 0f
    val reduced = LocalSystemReduceMotion.current
    val cache = animateFloatAsState(
        targetValue = cacheFraction,
        animationSpec = if (reduced) snap() else AppMotionTokens.standardSpec(),
        label = "cacheDeviceFraction"
    )
    val used = animateFloatAsState(
        targetValue = usedFraction,
        animationSpec = if (reduced) snap() else AppMotionTokens.standardSpec(),
        label = "cacheDeviceUsed"
    )
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth(if (centered) 0.8f else 1f)
            .height(5.dp)
            .semantics {
                contentDescription = if (storage == null || breakdown == null) {
                    "设备存储统计中"
                } else {
                    "${resolveDeviceStorageSummary(storage.first, storage.second)}；应用缓存 ${formatCacheClearBytes(breakdown.reclaimableDiskSize)}"
                }
            }
    ) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(track.copy(alpha = 0.1f), cornerRadius = corner)
        val cacheWidth = if (cache.value > 0f) maxOf(4.dp.toPx(), size.width * cache.value).coerceAtMost(size.width) else 0f
        val usedWidth = maxOf(cacheWidth, size.width * used.value)
        drawRoundRect(track.copy(alpha = 0.22f), size = Size(usedWidth, size.height), cornerRadius = corner)
        if (cacheWidth > 0f) drawRoundRect(color, size = Size(cacheWidth, size.height), cornerRadius = corner)
    }
    if (storage != null) {
        Spacer(Modifier.height(4.dp))
        AppText(
            text = resolveDeviceStorageSummary(storage.first, storage.second),
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CacheClearCategoryRow(
    segment: CacheClearDonutSegment,
    segmentColor: Color,
    description: String,
    enabled: Boolean,
    highlighted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val reduced = LocalSystemReduceMotion.current
    val color by animateColorAsState(
        targetValue = segmentColor,
        animationSpec = if (reduced) snap() else tween(350),
        label = "cacheRowColor-${segment.target}"
    )
    val haptics = rememberAppHapticFeedback(AppHapticScene.MENUS)
    val rowHighlightColor by animateColorAsState(
        targetValue = if (highlighted) color.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = if (reduced) snap() else tween(180),
        label = "cacheRowHighlight-${segment.target}"
    )
    val checked = animateFloatAsState(
        targetValue = if (segment.selected) 1f else 0f,
        animationSpec = if (reduced) snap() else AppMotionTokens.standardSpec(),
        label = "cacheChecked-${segment.target}"
    )
    val checkTint = if (color.luminance() > 0.45f) MaterialTheme.colorScheme.onSurface else Color.White
    val checkPath = remember { Path() }
    val unknown = !segment.sizeKnown
    Row(
        Modifier
            .fillMaxWidth()
            .background(rowHighlightColor)
            .toggleable(
                value = segment.selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { next ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(next)
                }
            )
            .semantics { contentDescription = "${segment.title}，$description" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) {
            val t = checked.value
            drawCircle(color.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
            if (t > 0f) {
                drawCircle(color, radius = size.minDimension / 2f * minOf(t * 2f, 1f))
                val p = ((t - 0.5f) * 2f).coerceIn(0f, 1f)
                if (p > 0f) {
                    checkPath.reset()
                    checkPath.moveTo(size.width * 0.25f, size.height * 0.5f)
                    val elbow = Offset(size.width * 0.43f, size.height * 0.68f)
                    if (p < 0.35f) {
                        checkPath.lineTo(
                            size.width * 0.25f + (elbow.x - size.width * 0.25f) * p / 0.35f,
                            size.height * 0.5f + (elbow.y - size.height * 0.5f) * p / 0.35f
                        )
                    } else {
                        checkPath.lineTo(elbow.x, elbow.y)
                        checkPath.lineTo(
                            elbow.x + size.width * 0.34f * (p - 0.35f) / 0.65f,
                            elbow.y - size.height * 0.4f * (p - 0.35f) / 0.65f
                        )
                    }
                    drawPath(checkPath, checkTint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = cacheClearCategoryTitle(segment.target),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val badgeText = if (segment.target == CacheClearTarget.APP_METADATA) "可重建" else segment.percentLabel
                AppText(
                    text = " · $badgeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1
                )
            }
            AppText(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        AppText(
            text = when {
                segment.target == CacheClearTarget.APP_METADATA -> "按需重置"
                unknown -> "统计中"
                else -> formatCacheClearBytes(segment.bytes)
            },
            color = if (segment.selected) AppContentColors.link() else AppContentColors.secondaryText(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun CacheClearProgressContent(
    dialogMaxHeight: Dp,
    progress: CacheClearProgress,
) {
    val gate = rememberDecorativeAnimationGate(
        respectComponentMotion = false,
        respectSystemReduceMotion = false
    )
    val maidLottieEnabled = LocalMaidLottieAnimationEnabled.current
    val reduceMotion = LocalSystemReduceMotion.current
    val value = cacheClearProgressFraction(progress.current, progress.total)?.let {
        if (progress.isComplete) 1f else it.coerceAtMost(0.99f)
    }
    val animated = animateFloatAsState(
        targetValue = if (progress.isComplete) 1f else value ?: 0f,
        animationSpec = if (reduceMotion) snap() else AppMotionTokens.standardSpec(),
        label = "cacheClearProgress"
    )
    val characterSize = if (dialogMaxHeight < 480.dp) 96.dp else 132.dp

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (maidLottieEnabled) {
            BlueSnowMaidAnimation(
                animation = MaidAnimation.CLEANING,
                modifier = Modifier
                    .size(characterSize)
                    .then(gate.modifier),
                isVisible = gate.active,
                reducedMotion = reduceMotion,
                staticDisplayDurationMs = 600L,
                completionHoldDurationMs = 0L
            )
        } else {
            val ringSize = if (dialogMaxHeight < 480.dp) 56.dp else 72.dp
            if (value != null) {
                AppCircularProgressIndicator(
                    progress = { animated.value },
                    modifier = Modifier.size(ringSize),
                    strokeWidth = 6.dp
                )
            } else {
                AppCircularProgressIndicator(
                    modifier = Modifier.size(ringSize),
                    strokeWidth = 6.dp
                )
            }
        }
        AppText(
            text = if (value == null) "准备中…" else "${ceil(value * 100).toInt()}%",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        if (value != null) {
            AppLinearProgressIndicator(
                progress = { animated.value },
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .fillMaxWidth()
                    .height(5.dp)
            )
        } else {
            Spacer(Modifier.height(5.dp))
        }
        AppText(
            text = "正在清理缓存",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        AppText(
            text = if (progress.total > 0L) {
                "${progress.message}（${progress.current}/${progress.total}）"
            } else {
                progress.message
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
            textAlign = TextAlign.Center
        )
        AppText(
            text = "请稍候，清理完成后会自动更新缓存详情",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CacheClearCompleteContent(
    dialogMaxHeight: Dp,
    releasedBytes: Long,
    beforeDiskBytes: Long?,
    clearedTargets: Set<CacheClearTarget>,
    options: List<CacheClearOptionUiModel>,
    palette: List<Color>,
    colorIndices: List<Int>,
    breakdown: CacheUtils.CacheBreakdown?,
    onContinueSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gate = rememberDecorativeAnimationGate(
        respectComponentMotion = false,
        respectSystemReduceMotion = false
    )
    val maidLottieEnabled = LocalMaidLottieAnimationEnabled.current
    val reduceMotion = LocalSystemReduceMotion.current
    val characterSize = if (dialogMaxHeight < 480.dp) 96.dp else 132.dp
    val deltaSummary = resolveCacheClearDeltaSummary(beforeDiskBytes, breakdown?.reclaimableDiskSize)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (maidLottieEnabled) {
            BlueSnowMaidAnimation(
                animation = MaidAnimation.CLEAN_COMPLETE,
                modifier = Modifier
                    .size(characterSize)
                    .then(gate.modifier),
                isVisible = gate.active,
                reducedMotion = reduceMotion,
                staticDisplayDurationMs = 600L,
                completionHoldDurationMs = 0L
            )
        } else {
            CacheClearCompletionBadge(
                badgeSize = if (dialogMaxHeight < 480.dp) 72.dp else 88.dp
            )
        }
        AppText(
            text = "清理完成",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        AppText(
            text = resolveCacheClearCompleteHeadline(releasedBytes, clearedTargets),
            style = MaterialTheme.typography.bodyLarge,
            color = AppContentColors.primaryText(),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        if (deltaSummary != null) {
            AppText(
                text = "磁盘缓存变化：$deltaSummary",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                textAlign = TextAlign.Center
            )
        } else if (breakdown != null) {
            AppText(
                text = if (breakdown.reclaimableDiskSize > 0L) {
                    "当前剩余可回收缓存 ${breakdown.format()}"
                } else {
                    "磁盘缓存已全部清空"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                textAlign = TextAlign.Center
            )
        }
        if (clearedTargets.isNotEmpty()) {
            val orderedTargets = options.map { it.target }.filter { it in clearedTargets }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orderedTargets.forEach { target ->
                    val optionIdx = options.indexOfFirst { it.target == target }.coerceAtLeast(0)
                    val colorIndex = colorIndices.getOrElse(optionIdx) { optionIdx }
                    val dotColor = palette[colorIndex % palette.size]
                    Row(
                        modifier = Modifier
                            .clip(AppShapes.container(ContainerLevel.Card))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Canvas(Modifier.size(7.dp)) {
                            drawCircle(dotColor)
                        }
                        AppText(
                            text = cacheClearCategoryTitle(target),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        AppButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
        ) {
            AppText("完成")
        }
        if (breakdown != null && breakdown.reclaimableDiskSize > 0L) {
            AppTextButton(
                onClick = onContinueSelect,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                AppText("继续清理其他项")
            }
        }
    }
}

@Composable
private fun CacheClearErrorContent(
    errorMessage: String?,
    onRetry: () -> Unit,
    onBackToSelect: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        AppText(
            text = "清理未完成",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        AppText(
            text = errorMessage ?: "清理缓存失败，请稍后重试",
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        AppButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
        ) {
            AppText("重试")
        }
        AppTextButton(
            onClick = onBackToSelect,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            AppText("返回选择")
        }
    }
}
