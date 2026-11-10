// 文件路径: feature/bangumi/BangumiTimelineScreen.kt
package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonPulseState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
//  已改用 AppContentColors.accent()
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.data.model.response.TimelineEpisode

/**
 * 番剧时间表组件
 */
@Composable
fun BangumiTimelineContent(
    timelineState: TimelineState,
    onRetry: () -> Unit,
    onBangumiClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    when (timelineState) {
        is TimelineState.Loading -> {
            BangumiTimelineScreenSkeleton(modifier = modifier)
        }
        is TimelineState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(
                        text = timelineState.message,
                        color = AppContentColors.error()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AppButton(onClick = onRetry) {
                        AppText("重试")
                    }
                }
            }
        }
        is TimelineState.Success -> {
            TimelineView(
                days = timelineState.days,
                onBangumiClick = onBangumiClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun BangumiTimelineScreenSkeleton(modifier: Modifier = Modifier) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val blockColor = rememberContentSkeletonBlockColor(rememberContentSkeletonPulseState(animated = animationGate.active))
    Column(modifier = modifier.then(animationGate.modifier).fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(5) {
                ContentSkeletonBlock(
                    color = blockColor,
                    shape = AppShapes.container(ContainerLevel.Pill),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
            }
        }
        AppHorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(7) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContentSkeletonBlock(
                            color = blockColor,
                            shape = AppShapes.container(ContainerLevel.Field),
                            modifier = Modifier.size(80.dp, 60.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ContentSkeletonBlock(
                                color = blockColor,
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .height(15.dp),
                            )
                            ContentSkeletonBlock(
                                color = blockColor,
                                modifier = Modifier
                                    .fillMaxWidth(0.56f)
                                    .height(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineView(
    days: List<TimelineDay>,
    onBangumiClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 找到今天的索引
    val todayIndex = days.indexOfFirst { it.isToday == 1 }.coerceAtLeast(0)
    var selectedDayIndex by remember { mutableIntStateOf(todayIndex) }

    val dayListState = rememberLazyListState()

    // 自动滚动到今天
    LaunchedEffect(todayIndex) {
        dayListState.animateScrollToItem(maxOf(0, todayIndex - 2))
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 日期选择器
        LazyRow(
            state = dayListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(days.size, key = { index -> days[index].date }) { index ->
                val day = days[index]
                DayChip(
                    day = day,
                    isSelected = index == selectedDayIndex,
                    onClick = { selectedDayIndex = index }
                )
            }
        }

        AppHorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 当天番剧列表
        val selectedDay = days.getOrNull(selectedDayIndex)
        val episodes = selectedDay?.episodes.orEmpty()
        if (episodes.isNotEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = episodes,
                    key = { index, episode -> resolveTimelineEpisodeLazyKey(index, episode) }
                ) { _, episode ->
                    TimelineEpisodeCard(
                        episode = episode,
                        onClick = { onBangumiClick(episode.seasonId) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    "今日无更新",
                    color = AppContentColors.secondaryText()
                )
            }
        }
    }
}

@Composable
private fun DayChip(
    day: TimelineDay,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isToday = day.isToday == 1
    val weekDays = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val weekDay = weekDays.getOrElse(day.dayOfWeek) { "" }

    // 解析日期获取月日
    val dateParts = day.date.split("-")
    val displayDate = if (dateParts.size >= 3) {
        "${dateParts[1].toIntOrNull() ?: 0}/${dateParts[2].toIntOrNull() ?: 0}"
    } else {
        day.date
    }

    AppFilterChip(
        selected = isSelected,
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Card),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (isToday) {
                AppContentColors.accent().copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            labelColor = if (isToday) {
                AppContentColors.accent()
            } else {
                AppContentColors.primaryText()
            },
            selectedContainerColor = AppContentColors.accent(),
            selectedLabelColor = AppContentColors.onAccent(),
        ),
        border = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(
                    text = if (isToday) "今天" else weekDay,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> AppContentColors.onAccent()
                        isToday -> AppContentColors.accent()
                        else -> AppContentColors.primaryText()
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = displayDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isSelected -> AppContentColors.onAccent().copy(alpha = 0.8f)
                        isToday -> AppContentColors.accent().copy(alpha = 0.7f)
                        else -> AppContentColors.secondaryText()
                    }
                )
            }
        },
    )
}

@Composable
private fun TimelineEpisodeCard(
    episode: TimelineEpisode,
    onClick: () -> Unit
) {
    val isFollowed = episode.follow == 1
    val isDelayed = episode.delay == 1

    AppSurface(
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .clip(AppShapes.container(ContainerLevel.Field))
            ) {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(
                        resolveTimelineEpisodeCover(episode, preferEpisodeCover = true)
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 已追番标记
                if (isFollowed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(AppContentColors.accent(), AppShapes.container(ContainerLevel.Tag))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        AppText(
                            "追番",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.onAccent()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 信息区域
            Column(
                modifier = Modifier.weight(1f)
            ) {
                AppText(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 更新集数
                    AppText(
                        text = resolveTimelineEpisodeUpdateLabel(episode),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.accent()
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 更新时间
                    AppText(
                        text = episode.pubTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                }

                // 延迟信息
                val scheduleLabel = resolveTimelineEpisodeScheduleLabel(episode)
                if (isDelayed && scheduleLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = scheduleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.error()
                    )
                }
            }
        }
    }
}
