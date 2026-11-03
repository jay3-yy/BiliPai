// 文件路径: feature/bangumi/ui/detail/BangumiDetailComponents.kt
package com.android.purebilibili.feature.bangumi.ui.detail

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.data.model.response.SeasonInfo
import com.android.purebilibili.core.player.PlaybackProgressManager

/** 每集本地观看进度（0..1）；无 bvid / 无时长 / 无观看记录时返回 null。 */
@Composable
fun rememberBangumiEpisodeProgressLookup(): (BangumiEpisode) -> Float? {
    val context = LocalContext.current
    val manager = remember { PlaybackProgressManager.getInstance(context) }
    return remember(manager) {
        { episode: BangumiEpisode ->
            val bvid = episode.bvid
            val durationMs = episode.duration
            if (bvid.isBlank() || durationMs <= 0L) {
                null
            } else {
                manager.getCachedPosition(bvid)
                    .takeIf { it > 0L }
                    ?.div(durationMs.toFloat())
                    ?.coerceIn(0f, 1f)
            }
        }
    }
}


/**
 * 番剧详情头部组件 - 手机端
 */
@Composable
fun BangumiDetailHeader(
    detail: BangumiDetail,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        // 封面背景（模糊）
        AsyncImage(
            model = FormatUtils.fixImageUrl(detail.cover),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // 信息区域
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 封面图
            AsyncImage(
                model = FormatUtils.fixImageUrl(detail.cover),
                contentDescription = detail.title,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(0.75f)
                    .clip(AppShapes.container(ContainerLevel.Chip)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 标题和信息
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = detail.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 评分
                detail.rating?.let { rating ->
                    if (rating.score > 0) {
                        RatingRow(score = rating.score, count = rating.count)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 更新状态
                detail.newEp?.desc?.let { desc ->
                    AppText(
                        text = desc,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 播放量
                detail.stat?.let { stat ->
                    AppText(
                        text = "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.favorites)}追番",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 评分行组件
 */
@Composable
fun RatingRow(
    score: Float,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        AppIcon(
            Icons.Outlined.Star,
            contentDescription = null,
            tint = AppContentColors.rating(),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        AppText(
            text = String.format("%.1f", score),
            color = AppContentColors.rating(),
            fontWeight = FontWeight.Bold
        )
        AppText(
            text = " (${count}人评分)",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 追番按钮组件
 */
@Composable
fun FollowButton(
    isFollowing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    if (isFollowing) {
        AppOutlinedButton(
            onClick = onToggle,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AppContentColors.accent()
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                AppContentColors.accent()
            ),
            modifier = modifier
        ) {
            AppIcon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            AppText("已追番", maxLines = 1, softWrap = false)
        }
    } else {
        AppButton(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
            ),
            modifier = modifier
        ) {
            AppIcon(
                Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            AppText("追番", maxLines = 1, softWrap = false)
        }
    }
}

/**
 * 季度切换选择器
 */
@Composable
fun SeasonSelector(
    seasons: List<SeasonInfo>,
    currentSeasonId: Long,
    onSeasonClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (seasons.size <= 1) return

    Column(modifier = modifier) {
        AppText(
            text = "相关季度",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seasons, key = { it.seasonId }) { season ->
                val isCurrentSeason = season.seasonId == currentSeasonId
                AppSurface(
                    modifier = Modifier.clickable {
                        if (!isCurrentSeason) {
                            onSeasonClick(season.seasonId)
                        }
                    },
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = if (isCurrentSeason) {
                        AppContentColors.accent()
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    AppText(
                        text = season.seasonTitle.ifEmpty { season.title },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentSeason) {
                            AppContentColors.onAccent()
                        } else {
                            AppContentColors.secondaryText()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 选集 Chip 组件
 */
@Composable
fun EpisodeChip(
    episode: BangumiEpisode,
    isSelected: Boolean = false,
    progressFraction: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppSurface(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .aspectRatio(16f / 9f),
        shape = AppShapes.container(ContainerLevel.Chip),
        color = if (isSelected) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box {
            // 封面
            AsyncImage(
                model = FormatUtils.fixImageUrl(episode.cover),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 30f
                        )
                    )
            )

            // 角标
            if (episode.badge.isNotEmpty()) {
                com.android.purebilibili.feature.bangumi.ui.list.BangumiBadge(
                    text = episode.badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }

            // 集数和标题
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                AppText(
                    text = episode.title.ifEmpty { "第${episode.id}话" },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.longTitle.isNotEmpty() && episode.longTitle != episode.title) {
                    AppText(
                        text = episode.longTitle,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 时长（右下角，毫秒 → m:ss）
            if (episode.duration > 0L) {
                AppText(
                    text = FormatUtils.formatDuration((episode.duration / 1000L).toInt()),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }

            // 单集观看进度条
            if (progressFraction != null && progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(AppContentColors.accent())
                    )
                }
            }
        }
    }
}

/**
 * 选集预览列表（显示前几集+更多按钮）
 */
@Composable
fun EpisodePreviewRow(
    episodes: List<BangumiEpisode>,
    maxPreviewCount: Int = 6,
    progressLookup: ((BangumiEpisode) -> Float?)? = null,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewEpisodes = episodes.take(maxPreviewCount)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(previewEpisodes, key = { it.id }) { episode ->
            EpisodeChip(
                episode = episode,
                progressFraction = progressLookup?.invoke(episode),
                onClick = { onEpisodeClick(episode) }
            )
        }

        // 更多按钮
        if (episodes.size > maxPreviewCount) {
            item {
                AppSurface(
                    onClick = onShowAll,
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(16f / 9f),
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "更多",
                                tint = AppContentColors.secondaryText()
                            )
                            AppText(
                                text = "全部${episodes.size}集",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }
        }
    }
}
