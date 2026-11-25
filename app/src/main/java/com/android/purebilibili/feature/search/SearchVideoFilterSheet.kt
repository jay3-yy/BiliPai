package com.android.purebilibili.feature.search

import androidx.compose.foundation.background

import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import com.android.purebilibili.core.ui.components.appActionClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
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
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.R
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
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
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
): Unit = AppExactTouchTargets {
    val orderOptions = remember { resolveSearchVideoOrderOptions() }
    val miuixNonGlass = com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()
    val centerWideFilters = LocalWindowSizeClass.current.let { it.isTabletDevice && it.isTablet }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = if (miuixNonGlass) AppSpacingTokens.ExtraSmall else 0.dp, bottom = 4.dp)
            .horizontalScroll(rememberScrollState()),
        // Row's viewport minimum centers fitting content; overflowing labels still scroll from the start.
        horizontalArrangement = if (centerWideFilters) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        orderOptions.forEach { order ->
            val selected = currentOrder == order
            Box(
                modifier = Modifier
                    .then(
                        if (miuixNonGlass) Modifier.heightIn(min = AppChromeSizeTokens.MiuixNativeCompactControlHeightDp.dp)
                        else Modifier.height(40.dp)
                    )
                    .semantics { this.selected = selected }
                    .clip(com.android.purebilibili.core.ui.AppShapes.container(
                        com.android.purebilibili.core.ui.ContainerLevel.Tag
                    ))
                    // 原生 Miuix 与上方搜索分类共用中性选中配色，玻璃与 MD3 保留各自样式。
                    .then(if (miuixNonGlass && selected) Modifier.background(
                        com.android.purebilibili.core.ui.AppSurfaceTokens.secondaryContainer()
                    ) else Modifier)
                    .appActionClickable(role = Role.Button, onClick = { onOrderChange(order) })
                    .padding(
                        horizontal = 11.dp,
                        vertical = if (miuixNonGlass) 6.dp else 0.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = localizedSearchOrderLabel(order),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected && miuixNonGlass) {
                        com.android.purebilibili.core.ui.AppSurfaceTokens.onSecondaryContainer()
                    } else if (selected) {
                        com.android.purebilibili.core.ui.AppSurfaceTokens.primary()
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
        // The dock owns deformation; inner click targets must not stretch separately.
        val filterInteraction = remember { MutableInteractionSource() }
        BottomBarMatchedReusableLiquidDock(
            shape = CircleShape,
            backdrop = miuixBackdrop,
            reuseEnabled = true,
            elasticFeedbackEnabled = true,
            useNeutralLiquidContainer = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(chromeSpec.clearActionSizeDp.toFloat()),
            modifier = Modifier.size(actionSize),
        ) { liquidChromeActive ->
            Box(
                modifier = Modifier
                    .size(actionSize)
                    .clip(CircleShape)
                    .appActionClickable(
                        interactionSource = filterInteraction,
                        indication = if (liquidChromeActive) null else androidx.compose.foundation.LocalIndication.current,
                        role = Role.Button,
                        onClick = { showFilterSheet = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "筛选",
                    tint = if (filterActive) {
                        AppContentColors.accent()
                    } else {
                        AppContentColors.primaryText()
                    },
                    modifier = Modifier.size(iconSize)
                )
            }
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
        AppText(stringResource(R.string.search_filter), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
        SearchFilterSectionTitle(text = stringResource(R.string.search_filter_publish_time))
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
                    label = localizedSearchPubTimeLabel(type),
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
                text = stringResource(R.string.search_filter_to),
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText()
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
        SearchFilterSectionTitle(text = stringResource(R.string.search_filter_duration))
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            resolveSearchVideoDurationOptions().forEach { duration ->
                SearchFilterSelectableChip(
                    label = localizedSearchDurationLabel(duration),
                    selected = selectedDuration == duration,
                    onClick = { onDurationSelect(duration) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SearchFilterSectionTitle(text = stringResource(R.string.search_filter_zone))
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            zoneOptions.forEach { zone ->
                SearchFilterSelectableChip(
                    label = localizedSearchZoneLabel(zone.tid),
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
    val selectedColors = resolveSearchSelectionChipColors(
        isSelected = true,
        colorScheme = MaterialTheme.colorScheme,
    )
    AppFilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            AppText(
                text = label,
                // 文字继承渲染器提供的配对前景色；原生 Miuix 统一使用淡色选中底，
                // MD3 与玻璃保留各自的填充及对比度规则。
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = if (center) Modifier.fillMaxWidth() else Modifier
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColors.backgroundColor,
            selectedLabelColor = selectedColors.textColor,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (glassEnabled) 0.88f else 0.55f
            ),
            labelColor = AppContentColors.secondaryText()
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


@Composable
internal fun localizedSearchOrderLabel(order: SearchOrder): String = stringResource(when (order) {
    SearchOrder.TOTALRANK -> R.string.search_order_default
    SearchOrder.CLICK -> R.string.search_order_views
    SearchOrder.PUBDATE -> R.string.search_order_newest
    SearchOrder.DM -> R.string.search_order_danmaku
    SearchOrder.STOW -> R.string.search_order_favorites
    SearchOrder.SCORES -> R.string.search_order_comments
    SearchOrder.ATTENTION -> R.string.search_order_likes
})

@Composable
private fun localizedSearchDurationLabel(duration: SearchDuration): String = stringResource(when (duration) {
    SearchDuration.ALL -> R.string.search_duration_all
    SearchDuration.UNDER_10MIN -> R.string.search_duration_under_10
    SearchDuration.TEN_TO_30MIN -> R.string.search_duration_10_30
    SearchDuration.THIRTY_TO_60MIN -> R.string.search_duration_30_60
    SearchDuration.OVER_60MIN -> R.string.search_duration_over_60
})

@Composable
private fun localizedSearchPubTimeLabel(type: SearchVideoPubTimeType): String = stringResource(when (type) {
    SearchVideoPubTimeType.ALL -> R.string.search_pub_all
    SearchVideoPubTimeType.DAY -> R.string.search_pub_day
    SearchVideoPubTimeType.WEEK -> R.string.search_pub_week
    SearchVideoPubTimeType.HALF_YEAR -> R.string.search_pub_half_year
    SearchVideoPubTimeType.CUSTOM -> R.string.search_pub_custom
})

@Composable
private fun localizedSearchZoneLabel(tid: Int): String = stringResource(when (tid) {
    0 -> R.string.search_zone_all
    1 -> R.string.search_zone_animation
    13 -> R.string.search_zone_anime
    167 -> R.string.search_zone_chinese_animation
    3 -> R.string.search_zone_music
    129 -> R.string.search_zone_dance
    4 -> R.string.search_zone_games
    36 -> R.string.search_zone_knowledge
    188 -> R.string.search_zone_technology
    234 -> R.string.search_zone_sports
    223 -> R.string.search_zone_auto
    160 -> R.string.search_zone_life
    221 -> R.string.search_zone_food
    217 -> R.string.search_zone_animals
    119 -> R.string.search_zone_kichiku
    115 -> R.string.search_zone_fashion
    202 -> R.string.search_zone_news
    5 -> R.string.search_zone_entertainment
    181 -> R.string.search_zone_film_tv
    177 -> R.string.search_zone_documentary
    23 -> R.string.search_zone_movie
    11 -> R.string.search_zone_television
    else -> R.string.search_zone_all
})
