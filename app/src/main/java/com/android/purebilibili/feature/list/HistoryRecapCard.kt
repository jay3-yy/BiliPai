package com.android.purebilibili.feature.list

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.plugin.feed.FeedSource
import com.android.purebilibili.core.plugin.feed.loadEnabledFeedSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class HistoryRecapSnapshot(
    val sources: List<FeedSource>,
    val rssStats: PersonalRssRecapStats,
    val videoStats: PersonalVideoRecapStats,
)

@Composable
internal fun HistoryRecapCard(
    refreshToken: Any,
    active: Boolean,
    onUpClick: ((Long) -> Unit)?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    snapshotCache: MutableMap<PersonalRecapWindow, HistoryRecapSnapshot>? = null,
    onShowAllUps: ((PersonalRecapWindow, PersonalVideoRecapStats) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var window by rememberSaveable { mutableStateOf(PersonalRecapWindow.TODAY) }
    // 数据随历史页 ViewModel 保留，Lazy item 重挂载/从 UP 空间返回时直接显示旧快照。
    var snapshot by remember(window, snapshotCache) { mutableStateOf(snapshotCache?.get(window)) }
    var loading by remember(window) { mutableStateOf(snapshot == null) }
    var videoUnavailable by remember(window) { mutableStateOf(false) }
    var resumeRevision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeRevision++ }
    LaunchedEffect(window, refreshToken, active, resumeRevision) {
        if (!active) return@LaunchedEffect
        loading = snapshot == null
        videoUnavailable = false
        val start = resolvePersonalRecapWindowStart(System.currentTimeMillis(), window)
        try {
            val sources = withContext(Dispatchers.IO) { loadEnabledFeedSources(context) }
            val rssStats = PersonalRecapRepository.rssRecap(context, start)
            val videoStats = PersonalRecapRepository.videoRecap(start)
            val refreshed = HistoryRecapSnapshot(sources, rssStats, videoStats)
            snapshotCache?.set(window, refreshed)
            snapshot = refreshed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 后台刷新失败仍保留上次统计，不把已展示的图表/头像切成空态。
            videoUnavailable = snapshot == null
        } finally {
            loading = false
        }
    }
    HistoryRecapCardContent(
        sources = snapshot?.sources.orEmpty(),
        window = window,
        rssStats = snapshot?.rssStats ?: PersonalRssRecapStats(),
        videoStats = snapshot?.videoStats,
        loading = loading,
        videoUnavailable = videoUnavailable,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onWindowChange = { window = it },
        onUpClick = onUpClick,
        onShowAllUps = onShowAllUps,
        modifier = modifier,
    )
}

/** 回顾板块：RSS 阅读统计 + 视频观看统计 + 最近爱看的 UP 主，按时间窗切换。 */
@Composable
private fun HistoryRecapCardContent(
    sources: List<FeedSource>,
    window: PersonalRecapWindow,
    rssStats: PersonalRssRecapStats,
    videoStats: PersonalVideoRecapStats?,
    loading: Boolean,
    videoUnavailable: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onWindowChange: (PersonalRecapWindow) -> Unit,
    onUpClick: ((Long) -> Unit)?,
    onShowAllUps: ((PersonalRecapWindow, PersonalVideoRecapStats) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        "我的回顾",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AppTextButton(
                        onClick = { onExpandedChange(!expanded) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { stateDescription = if (expanded) "回顾详情已展开" else "回顾详情已收起" },
                    ) {
                        AppText(if (expanded) "收起" else "展开")
                        Spacer(Modifier.width(4.dp))
                        AppIcon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                PersonalRecapWindowSelector(
                    window = window,
                    onWindowChange = onWindowChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val currentVideoStats = videoStats
            val emptyRss = rssStats.readCount <= 0
            val emptyVideo = videoUnavailable || currentVideoStats == null || currentVideoStats.videoCount <= 0
            if (loading) {
                AppText("正在整理阅读与观看记录…", style = MaterialTheme.typography.bodySmall)
            } else if (emptyRss && emptyVideo) {
                AppText(
                    text = if (videoUnavailable) "暂时无法读取观看记录，可稍后刷新历史记录重试。"
                    else "这段时间还没有阅读或观看记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!emptyRss) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppIcon(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                                modifier = Modifier.size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = "已读 ${rssStats.readCount} 篇",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val sourceTitle = rssStats.topSourceKey
                                    ?.let { key -> sources.firstOrNull { it.id == key }?.title }
                                if (expanded && !sourceTitle.isNullOrBlank()) {
                                    AppText(
                                        text = "最常读：$sourceTitle",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.secondaryText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (!emptyVideo && currentVideoStats != null) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppIcon(
                                Icons.Outlined.SmartDisplay,
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                                modifier = Modifier.size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = "看了 ${currentVideoStats.videoCount} 个视频",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                AppText(
                                    text = formatRecapDuration(currentVideoStats.totalDurationSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                }
                if (currentVideoStats?.historyMayBeIncomplete == true) {
                    AppText(
                        "较早的观看记录尚未全部载入，视频统计仅包含已载入记录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                // 摘要与时间窗始终保留；收起后移除详情组合，不保留隐藏图表与头像。
                // 三种材质共用展开行为，材质参数仍由现有 AppSurface 决定。
                if (expanded) {
                    PersonalRecapActivityChart(
                        window = window,
                        rssStats = rssStats,
                        videoStats = currentVideoStats,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (currentVideoStats != null && currentVideoStats.videoCount > 0) {
                        PersonalRecapProgress(
                            label = "视频看完比例",
                            detail = "${currentVideoStats.finishedCount} / ${currentVideoStats.videoCount}",
                            fraction = currentVideoStats.finishedCount.toFloat() / currentVideoStats.videoCount,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (currentVideoStats != null && currentVideoStats.topUps.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(
                                    text = "最近爱看",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppContentColors.secondaryText(),
                                )
                                if (currentVideoStats.topUps.size > 5 && onShowAllUps != null) {
                                    AppTextButton(onClick = { onShowAllUps(window, currentVideoStats) }) {
                                        AppText("查看更多")
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                currentVideoStats.topUps.take(5).forEach { up ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .width(56.dp)
                                            .then(
                                                if (onUpClick != null) {
                                                    Modifier.clickable { onUpClick(up.mid) }
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (up.face.isNotBlank()) {
                                                coil3.compose.AsyncImage(
                                                    model = up.face,
                                                    contentDescription = up.name,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                AppIcon(
                                                    Icons.Outlined.PersonOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        AppText(
                                            text = up.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppContentColors.secondaryText(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatRecapDuration(totalSec: Long): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    return when {
        hours > 0 -> "约 $hours 小时"
        minutes > 0 -> "约 $minutes 分钟"
        totalSec > 0 -> "不足 1 分钟"
        else -> ""
    }
}
