package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.BangumiEpisode

/** 大屏按实际内容栏宽度排选集，每组最多 50 集；手机保留横向选集。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BangumiEpisodeChoices(
    episodes: List<BangumiEpisode>,
    currentEpisodeId: Long,
    isCourse: Boolean,
    isLargeScreenLayout: Boolean,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLargeScreenLayout) {
        val selectedIndex = episodes.indexOfFirst { it.id == currentEpisodeId }.coerceAtLeast(0)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
        LaunchedEffect(currentEpisodeId) { listState.animateScrollToItem(selectedIndex) }
        val cardHeight = (60f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
        LazyRow(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(episodes, key = { it.id }) { episode ->
                if (isCourse) {
                    EpisodeChipSelectable(
                        episode = episode,
                        isSelected = episode.id == currentEpisodeId,
                        isCourse = true,
                        onClick = { onEpisodeClick(episode) },
                    )
                } else {
                    BangumiEpisodeGridCard(
                        episode = episode,
                        isSelected = episode.id == currentEpisodeId,
                        isCourse = false,
                        onClick = { onEpisodeClick(episode) },
                        compact = true,
                        modifier = Modifier.width(140.dp).height(cardHeight),
                    )
                }
            }
        }
        return
    }
    BoxWithConstraints(modifier = modifier.padding(horizontal = 16.dp)) {
        val minCardWidth = if (isCourse) 180.dp else 140.dp
        val columns = ((maxWidth + 8.dp) / (minCardWidth + 8.dp)).toInt().coerceAtLeast(1)
        val cardWidth = (maxWidth - 8.dp * (columns - 1)) / columns
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            episodes.forEach { episode ->
                BangumiEpisodeGridCard(
                    episode = episode,
                    isSelected = episode.id == currentEpisodeId,
                    isCourse = isCourse,
                    onClick = { onEpisodeClick(episode) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun BangumiEpisodeGridCard(
    episode: BangumiEpisode,
    isSelected: Boolean,
    isCourse: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val selectedColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
    AppSurface(
        onClick = onClick,
        modifier = modifier.semantics { selected = isSelected },
        shape = AppShapes.container(ContainerLevel.Card),
        color = if (isSelected) selectedColors.backgroundColor else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .then(if (compact) Modifier else Modifier.heightIn(min = 68.dp))
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppText(
                    text = episode.title.ifEmpty { if (isCourse) "第${episode.id}讲" else "第${episode.id}话" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) selectedColors.contentColor else AppContentColors.primaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.badge.isNotBlank()) {
                    BangumiEpisodeBadge(episode.badge)
                }
            }
            AppText(
                text = episode.longTitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) selectedColors.contentColor else AppContentColors.secondaryText(),
                minLines = if (compact) 1 else 2,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
