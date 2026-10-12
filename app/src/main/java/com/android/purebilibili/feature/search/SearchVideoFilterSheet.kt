package com.android.purebilibili.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.KeepScrollableTabSelectionVisible
import com.android.purebilibili.core.ui.components.liquidDockViewport
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.VideoListLayoutToggle
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * BiliPai-style video filter chrome:
 * Content-width order labels. Filter and layout actions live in the top bar.
 */
@Composable
fun SearchVideoFilterBar(
    currentOrder: SearchOrder,
    onOrderChange: (SearchOrder) -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
) {
    val orderOptions = remember { resolveSearchVideoOrderOptions() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 4.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        orderOptions.forEach { order ->
            val selected = currentOrder == order
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .semantics { this.selected = selected }
                    .clip(com.android.purebilibili.core.ui.AppShapes.container(
                        com.android.purebilibili.core.ui.ContainerLevel.Tag
                    ))
                    .clickable(role = Role.Button, onClick = { onOrderChange(order) })
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = resolveSearchOrderChipLabel(order),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        if (LocalAppUiStyle.current == com.android.purebilibili.core.theme.AppUiStyle.MIUIX) {
                            com.android.purebilibili.core.ui.AppSurfaceTokens.onSurface()
                        } else {
                            com.android.purebilibili.core.ui.AppSurfaceTokens.primary()
                        }
                    } else {
                        com.android.purebilibili.core.ui.AppSurfaceTokens.onSurfaceVariantSummary()
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SearchVideoFilterActions(
    currentDurations: Set<SearchDuration>,
    currentVideoTid: Int,
    currentPubTimeType: SearchVideoPubTimeType,
    currentPubBegin: Long?,
    currentPubEnd: Long?,
    onDurationSelect: (SearchDuration) -> Unit,
    onVideoTidChange: (Int) -> Unit,
    onPubTimeTypeChange: (SearchVideoPubTimeType) -> Unit,
    onCustomPubTimeRange: (Long, Long) -> Unit,
    singleColumn: Boolean,
    onLayoutToggle: () -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
) {
    val componentSpacing = resolveSearchComponentSpacingSpec(LocalAppUiStyle.current)
    val chromeSpec = resolveSearchChromeVisualSpec(
        com.android.purebilibili.core.ui.rememberAppTopChromePolicy()
    )
    val actionSize = chromeSpec.clearActionSizeDp.dp
    val iconSize = chromeSpec.actionIconSizeDp.dp
    var showFilterSheet by remember { mutableStateOf(false) }
    val filterActive = hasActiveSearchVideoFilters(
        durations = currentDurations,
        videoTid = currentVideoTid,
        pubTimeType = currentPubTimeType
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(componentSpacing.actionGapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Always circular Material ripple. Miuix IconButton indication is square;
        // clip + unbounded radius keeps the wave circular under every preset.
        val filterInteraction = remember { MutableInteractionSource() }
        BottomBarMatchedReusableLiquidDock(
            shape = CircleShape,
            backdrop = miuixBackdrop,
            reuseEnabled = true,
            useNeutralLiquidContainer = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(chromeSpec.clearActionSizeDp.toFloat()),
            modifier = Modifier.size(actionSize),
        ) { _ ->
            Box(
                modifier = Modifier
                    .size(actionSize)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = filterInteraction,
                        indication = ripple(
                            bounded = false,
                            radius = 20.dp
                        ),
                        role = Role.Button,
                        onClick = { showFilterSheet = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "筛选",
                    tint = if (filterActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        BottomBarMatchedReusableLiquidDock(
            shape = CircleShape,
            backdrop = miuixBackdrop,
            reuseEnabled = true,
            useNeutralLiquidContainer = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(chromeSpec.clearActionSizeDp.toFloat()),
            modifier = Modifier.size(actionSize),
        ) { _ ->
            VideoListLayoutToggle(
                singleColumn = singleColumn,
                onClick = onLayoutToggle,
                modifier = Modifier.size(actionSize),
                iconModifier = Modifier.size(iconSize),
            )
        }
    }

    if (showFilterSheet) {
        SearchVideoFilterSheetHost(
            currentDurations = currentDurations,
            currentVideoTid = currentVideoTid,
            currentPubTimeType = currentPubTimeType,
            currentPubBegin = currentPubBegin,
            currentPubEnd = currentPubEnd,
            onDismiss = { showFilterSheet = false },
            onDurationSelect = {
                onDurationSelect(it)
                showFilterSheet = false
            },
            onVideoTidChange = {
                onVideoTidChange(it)
                showFilterSheet = false
            },
            onPubTimeTypeChange = {
                onPubTimeTypeChange(it)
                showFilterSheet = false
            },
            onCustomPubTimeRange = { begin, end ->
                onCustomPubTimeRange(begin, end)
                showFilterSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchVideoFilterSheetHost(
    currentDurations: Set<SearchDuration>,
    currentVideoTid: Int,
    currentPubTimeType: SearchVideoPubTimeType,
    currentPubBegin: Long?,
    currentPubEnd: Long?,
    onDismiss: () -> Unit,
    onDurationSelect: (SearchDuration) -> Unit,
    onVideoTidChange: (Int) -> Unit,
    onPubTimeTypeChange: (SearchVideoPubTimeType) -> Unit,
    onCustomPubTimeRange: (Long, Long) -> Unit
) {
    val sheetContent: @Composable () -> Unit = {
        SearchVideoFilterSheetContent(
            currentDurations = currentDurations,
            currentVideoTid = currentVideoTid,
            currentPubTimeType = currentPubTimeType,
            currentPubBegin = currentPubBegin,
            currentPubEnd = currentPubEnd,
            onDurationSelect = onDurationSelect,
            onVideoTidChange = onVideoTidChange,
            onPubTimeTypeChange = onPubTimeTypeChange,
            onCustomPubTimeRange = onCustomPubTimeRange
        )
    }
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        AppText("筛选", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        sheetContent()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchVideoFilterSheetContent(
    currentDurations: Set<SearchDuration>,
    currentVideoTid: Int,
    currentPubTimeType: SearchVideoPubTimeType,
    currentPubBegin: Long?,
    currentPubEnd: Long?,
    onDurationSelect: (SearchDuration) -> Unit,
    onVideoTidChange: (Int) -> Unit,
    onPubTimeTypeChange: (SearchVideoPubTimeType) -> Unit,
    onCustomPubTimeRange: (Long, Long) -> Unit
) {
    val selectedDuration = resolveSelectedSearchDuration(currentDurations)
    val zoneOptions = remember { resolveSearchVideoZoneOptions() }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    var showBeginPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val defaultBegin = currentPubBegin
        ?: TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
    val defaultEnd = currentPubEnd
        ?: TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        SearchFilterSectionTitle(text = "发布时间")
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                SearchVideoPubTimeType.ALL,
                SearchVideoPubTimeType.DAY,
                SearchVideoPubTimeType.WEEK,
                SearchVideoPubTimeType.HALF_YEAR
            ).forEach { type ->
                SearchFilterSelectableChip(
                    label = type.label,
                    selected = currentPubTimeType == type,
                    onClick = { onPubTimeTypeChange(type) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchFilterSelectableChip(
                label = dateFormat.format(Date(TimeUnit.SECONDS.toMillis(defaultBegin))),
                selected = currentPubTimeType == SearchVideoPubTimeType.CUSTOM,
                onClick = { showBeginPicker = true },
                modifier = Modifier.weight(1f),
                center = true
            )
            AppText(
                text = "至",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SearchFilterSelectableChip(
                label = dateFormat.format(Date(TimeUnit.SECONDS.toMillis(defaultEnd))),
                selected = currentPubTimeType == SearchVideoPubTimeType.CUSTOM,
                onClick = { showEndPicker = true },
                modifier = Modifier.weight(1f),
                center = true
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SearchFilterSectionTitle(text = "内容时长")
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            resolveSearchVideoDurationOptions().forEach { duration ->
                SearchFilterSelectableChip(
                    label = resolveSearchDurationChipLabel(duration),
                    selected = selectedDuration == duration,
                    onClick = { onDurationSelect(duration) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SearchFilterSectionTitle(text = "内容分区")
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            zoneOptions.forEach { zone ->
                SearchFilterSelectableChip(
                    label = zone.label,
                    selected = currentVideoTid == zone.tid,
                    onClick = { onVideoTidChange(zone.tid) }
                )
            }
        }
    }

    if (showBeginPicker) {
        SearchDatePickerDialog(
            initialEpochMillis = TimeUnit.SECONDS.toMillis(defaultBegin),
            onDismiss = { showBeginPicker = false },
            onConfirm = { millis ->
                val begin = TimeUnit.MILLISECONDS.toSeconds(millis)
                val end = currentPubEnd ?: defaultEnd
                onCustomPubTimeRange(begin, end)
                showBeginPicker = false
            }
        )
    }
    if (showEndPicker) {
        SearchDatePickerDialog(
            initialEpochMillis = TimeUnit.SECONDS.toMillis(defaultEnd),
            onDismiss = { showEndPicker = false },
            onConfirm = { millis ->
                val end = TimeUnit.MILLISECONDS.toSeconds(millis)
                val begin = currentPubBegin ?: defaultBegin
                onCustomPubTimeRange(begin, end)
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun SearchFilterSectionTitle(
    text: String
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun SearchFilterSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    center: Boolean = false
) {
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    AppFilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            AppText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = if (center) Modifier.fillMaxWidth() else Modifier
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (glassEnabled) 0.88f else 0.55f
            ),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDatePickerDialog(
    initialEpochMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppTextButton(
                onClick = {
                    val selected = state.selectedDateMillis ?: return@AppTextButton
                    onConfirm(selected)
                }
            ) {
                AppText("确定")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("取消")
            }
        }
    ) {
        DatePicker(state = state)
    }
}
