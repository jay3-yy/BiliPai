package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode

/** 竖屏番剧简介；材质由应用组件决定，课程与大屏继续使用各自布局。 */
@Composable
internal fun BangumiPortraitIntroduction(
    detail: BangumiDetail,
    heading: @Composable () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(112.dp).aspectRatio(0.75f)) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(detail.cover),
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(AppShapes.container(ContainerLevel.Card)),
            )
            detail.rating?.let { rating ->
                AppStatusBadge(
                    label = "评分 ${rating.score}", emphasized = true,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                )
            }
        }
        Column(Modifier.weight(1f).clickable(onClick = onShowDetails), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            heading()
            detail.stat?.let { stat ->
                AppText(
                    "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.danmakus)}弹幕",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
            detail.newEp?.desc?.takeIf { it.isNotBlank() }?.let {
                AppText(it, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
            }
            AppText(
                listOf(
                    detail.areas.orEmpty().joinToString(" ") { it.name },
                    detail.publish?.pubTimeShow.orEmpty().ifBlank { detail.publish?.pubTime.orEmpty() },
                ).filter { it.isNotBlank() }.joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
            if (detail.evaluate.isNotBlank()) {
                AppText(
                    "简介：${detail.evaluate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BangumiPortraitSections(
    detail: BangumiDetail,
    currentEpisodeId: Long,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = detail.section.orEmpty().filter { it.episodes.orEmpty().isNotEmpty() }
    if (sections.isEmpty()) return
    var selectedSection by rememberSaveable(detail.seasonId) { mutableIntStateOf(0) }
    val selectedIndex = selectedSection.coerceIn(sections.indices)
    LaunchedEffect(currentEpisodeId) {
        val index = sections.indexOfFirst { it.episodes.orEmpty().any { episode -> episode.id == currentEpisodeId } }
        if (index >= 0) selectedSection = index
    }
    val selectedEpisodes = sections[selectedIndex].episodes.orEmpty()
    val episodeListState = key(selectedIndex) { rememberLazyListState() }
    LaunchedEffect(currentEpisodeId, selectedIndex) {
        val index = selectedEpisodes.indexOfFirst { it.id == currentEpisodeId }
        episodeListState.scrollToItem(index.coerceAtLeast(0))
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sections.size) { index ->
                AppTextButton(onClick = { selectedSection = index }) {
                    AppText(
                        sections[index].title.ifBlank { "附加内容" },
                        color = if (index == selectedIndex) AppContentColors.accent() else AppContentColors.secondaryText(),
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        LazyRow(
            state = episodeListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(selectedEpisodes, key = { it.id }) { episode ->
                Column(Modifier.width(144.dp).clickable { onEpisodeClick(episode) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        AsyncImage(
                            model = FormatUtils.fixImageUrl(episode.cover), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(AppShapes.container(ContainerLevel.Card)),
                        )
                        if (episode.badge.isNotBlank()) {
                            com.android.purebilibili.feature.bangumi.ui.list.BangumiBadge(
                                text = episode.badge,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            )
                        }
                        if (episode.duration > 0L) {
                            AppStatusBadge(
                                label = FormatUtils.formatDuration((episode.duration / 1000L).toInt()),
                                emphasized = false,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                            )
                        }
                    }
                    AppText(
                        listOf(episode.title, episode.longTitle).filter { it.isNotBlank() }.joinToString(" "),
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
