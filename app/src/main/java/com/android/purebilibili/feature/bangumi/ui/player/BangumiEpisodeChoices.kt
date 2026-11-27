package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppInteractionColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppLazyRow as LazyRow
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.feature.bangumi.ui.detail.rememberBangumiEpisodeProgressLookup
import com.android.purebilibili.feature.video.ui.components.PagePreviewNestedScrollConnection

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
    val selectionHaptic = rememberHapticFeedback(AppHapticScene.MENUS)
    val progressLookup = rememberBangumiEpisodeProgressLookup()
    val handleEpisodeClick: (BangumiEpisode) -> Unit = { episode ->
        if (episode.id != currentEpisodeId) {
            selectionHaptic(HapticType.LIGHT)
        }
        onEpisodeClick(episode)
    }

    if (!isLargeScreenLayout) {
        val density = LocalDensity.current
        val selectedIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        val listState = rememberLazyListState()
        var hasPositioned by remember(episodes) { mutableStateOf(false) }
        val itemWidth = if (isCourse) 180.dp else 140.dp
        LaunchedEffect(episodes, selectedIndex) {
            if (episodes.isEmpty()) return@LaunchedEffect
            val targetIndex = selectedIndex.coerceAtLeast(0)
            val halfCardPx = with(density) { itemWidth.toPx() / 2f }
            if (hasPositioned) {
                listState.animateScrollToItem(targetIndex)
                if (selectedIndex > 0) {
                    listState.animateScrollBy(-halfCardPx)
                }
            } else {
                listState.scrollToItem(targetIndex)
                if (selectedIndex > 0) {
                    listState.scrollBy(-halfCardPx)
                }
                hasPositioned = true
            }
        }
        val cardHeight = (60f * density.fontScale.coerceAtLeast(1f)).dp
        LazyRow(
            state = listState,
            modifier = modifier.nestedScroll(PagePreviewNestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = episodes,
                key = { it.id },
                contentType = { episode ->
                    when {
                        isCourse -> "course"
                        episode.id == currentEpisodeId -> "selected_episode"
                        else -> "episode"
                    }
                },
            ) { episode ->
                if (isCourse) {
                    EpisodeChipSelectable(
                        episode = episode,
                        isSelected = episode.id == currentEpisodeId,
                        isCourse = true,
                        onClick = { handleEpisodeClick(episode) },
                    )
                } else {
                    BangumiEpisodeGridCard(
                        episode = episode,
                        isSelected = episode.id == currentEpisodeId,
                        isCourse = false,
                        progressFraction = progressLookup(episode),
                        onClick = { handleEpisodeClick(episode) },
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
                    progressFraction = progressLookup(episode),
                    onClick = { handleEpisodeClick(episode) },
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
    progressFraction: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val selectedColors = AppInteractionColors.miuixContentSelection()
        ?: resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
    AppSurface(
        onClick = onClick,
        modifier = modifier.semantics { selected = isSelected },
        shape = AppShapes.container(ContainerLevel.Card),
        color = if (isSelected) selectedColors.backgroundColor else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = if (compact) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(min = 68.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(selectedColors.contentColor)
                        )
                    }
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
            if (progressFraction != null && progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            (if (isSelected) selectedColors.contentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                                .copy(alpha = 0.16f)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(if (isSelected) selectedColors.contentColor else AppContentColors.accent())
                    )
                }
            }
        }
    }
}
