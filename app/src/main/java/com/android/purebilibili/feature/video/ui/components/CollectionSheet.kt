// 文件路径: feature/video/ui/components/CollectionSheet.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.UgcSection
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 合集底部弹窗：
 * - 工具栏：合集标题 + 订阅 + 跳顶/跳底/定位当前 + 排序切换 + 关闭
 * - 多 section 时展示分区切换行
 * - 条目：封面（时长角标）+ 标题（当前集高亮）+ 发布时间 + 播放/弹幕数
 * - 单集多分 P 时展示分 P chips，点击直达对应分 P
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSheet(
    ugcSeason: UgcSeason,
    currentBvid: String,
    currentCid: Long = 0L,
    currentVideoInfo: com.android.purebilibili.data.model.response.ViewInfo? = null,
    onDismiss: () -> Unit,
    onEpisodeClick: (UgcEpisode) -> Unit,
    inlinePanel: Boolean = false,
) {
    val selectionHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    val sheetMaxHeight = episodeSheetMaxHeight()
    val context = LocalContext.current
    val clearIcon = rememberAppClearIcon()
    val scope = rememberCoroutineScope()
    val collectionSubscriptionId = remember(ugcSeason) { resolveCollectionSubscriptionId(ugcSeason) }

    val sections = remember(ugcSeason.sections) { ugcSeason.sections }
    val initialSectionIndex = remember(ugcSeason, currentBvid, currentCid) {
        sections.indexOfFirst { section ->
            section.episodes.any { isCurrentUgcEpisode(currentBvid, currentCid, it) }
        }.takeIf { it >= 0 } ?: 0
    }
    var selectedSectionIndex by remember(ugcSeason) {
        mutableIntStateOf(
            if (sections.isEmpty()) 0 else initialSectionIndex.coerceIn(sections.indices)
        )
    }
    val activeSection: UgcSection = sections.getOrNull(selectedSectionIndex) ?: UgcSection()

    val storedSortMode by SettingsManager
        .getCollectionSortMode(context, collectionSubscriptionId)
        .collectAsStateWithLifecycle(initialValue = CollectionSortMode.ASCENDING)
    var localSortMode by remember(collectionSubscriptionId) {
        mutableStateOf<CollectionSortMode?>(null)
    }
    LaunchedEffect(storedSortMode) {
        if (localSortMode == storedSortMode) {
            localSortMode = null
        }
    }
    val sortMode = localSortMode ?: storedSortMode

    val sortedEpisodes = remember(activeSection, sortMode, currentBvid, currentCid) {
        sortCollectionEpisodes(
            episodes = activeSection.episodes,
            sortMode = sortMode,
            currentBvid = currentBvid,
            currentCid = currentCid
        )
    }
    val currentIndex = resolveCurrentUgcEpisodeIndex(
        episodes = sortedEpisodes,
        currentBvid = currentBvid,
        currentCid = currentCid
    )

    // 首帧直接定位当前集，面板位移不叠加列表滚动；手动定位按钮仍使用滚动动画。
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    LaunchedEffect(selectedSectionIndex, currentIndex, sortMode) {
        listState.scrollToItem(currentIndex.coerceAtLeast(0))
    }

    fun scrollToEpisode(index: Int) {
        if (index < 0 || sortedEpisodes.isEmpty()) return
        scope.launch {
            listState.animateScrollToItem(index.coerceIn(0, sortedEpisodes.lastIndex))
        }
    }

    fun cycleSortMode() {
        val nextMode = when (sortMode) {
            CollectionSortMode.ASCENDING -> CollectionSortMode.DESCENDING
            CollectionSortMode.DESCENDING -> CollectionSortMode.RECENT
            CollectionSortMode.RECENT -> CollectionSortMode.ASCENDING
        }
        localSortMode = nextMode
        scope.launch {
            SettingsManager.setCollectionSortMode(context, collectionSubscriptionId, nextMode)
        }
    }

    EpisodeSelectionSheet(onDismiss = onDismiss, inlinePanel = inlinePanel) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (inlinePanel) Modifier.fillMaxSize() else Modifier.heightIn(max = sheetMaxHeight))
        ) {
            // ── 工具栏（PiliPlus：标题 + 收藏 + 跳转/定位 + 排序 + 关闭）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = "合集",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppContentColors.primaryText()
                )
                Spacer(modifier = Modifier.width(8.dp))
                CollectionSubscriptionButton(
                    collectionId = collectionSubscriptionId,
                    currentBvid = currentBvid,
                    currentAid = resolveCurrentUgcEpisodeAid(
                        episodes = sections.flatMap { it.episodes },
                        currentBvid = currentBvid,
                        currentCid = currentCid
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                EpisodeListNavigationActions(
                    onTopClick = { scrollToEpisode(0) },
                    onBottomClick = { scrollToEpisode(sortedEpisodes.lastIndex) },
                    onCurrentClick = { scrollToEpisode(currentIndex) },
                    hasItems = sortedEpisodes.isNotEmpty(),
                    hasCurrent = currentIndex >= 0,
                )
                AppIconButton(
                    onClick = {
                        com.android.purebilibili.core.util.ShareUtils.shareCollection(
                            context = context,
                            title = ugcSeason.title,
                            mid = ugcSeason.mid,
                            seasonId = ugcSeason.id
                        )
                    }
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = "分享合集",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                AppIconButton(onClick = ::cycleSortMode) {
                    val sortIcon: ImageVector = when (sortMode) {
                        CollectionSortMode.ASCENDING -> Icons.Outlined.ArrowUpward
                        CollectionSortMode.DESCENDING -> Icons.Outlined.ArrowDownward
                        CollectionSortMode.RECENT -> Icons.Outlined.History
                    }
                    Icon(
                        sortIcon,
                        contentDescription = "排序：${sortMode.label}",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                AppIconButton(onClick = requestDismiss) {
                    AppIcon(
                        clearIcon,
                        contentDescription = "关闭",
                        tint = AppContentColors.secondaryText()
                    )
                }
            }

            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── 合集简介（接口 intro 字段，可展开）──
            val collectionIntro = ugcSeason.displayIntro
            if (collectionIntro.isNotBlank()) {
                var introExpanded by remember(ugcSeason.id) { mutableStateOf(false) }
                var introCanExpand by remember(ugcSeason.id, collectionIntro) { mutableStateOf(false) }
                val totalPlays = sections.sumOf { section ->
                    section.episodes.sumOf { it.arc?.stat?.view?.toLong() ?: 0L }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clickable(enabled = introCanExpand || introExpanded) { introExpanded = !introExpanded }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // 首行：简介标签与正文基线对齐
                        Row {
                            AppText(
                                text = "简介",
                                tapToCopyEnabled = false,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppContentColors.primaryText(),
                                modifier = Modifier.alignByBaseline()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppText(
                                text = collectionIntro,
                                tapToCopyEnabled = false,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                maxLines = if (introExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    if (!introExpanded) introCanExpand = result.hasVisualOverflow
                                },
                                modifier = Modifier.weight(1f).alignByBaseline()
                            )
                        }
                        // 次行：N 集 · 总播放
                        AppText(
                            text = "${ugcSeason.ep_count} 集 · 总播放 ${FormatUtils.formatStat(totalPlays)}",
                            tapToCopyEnabled = false,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppContentColors.primaryText()
                        )
                    }
                    if (introCanExpand || introExpanded) AppIconButton(
                        onClick = { introExpanded = !introExpanded },
                        modifier = Modifier.size(32.dp),
                    ) {
                        AppIcon(
                            imageVector = if (introExpanded) {
                                Icons.Outlined.KeyboardArrowUp
                            } else {
                                Icons.Outlined.KeyboardArrowDown
                            },
                            contentDescription = if (introExpanded) "收起简介" else "展开简介",
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── 分区切换（仅多 section 时展示）──
            // 液态玻璃开启时由 AppThemeAdaptiveTabRow 委托底栏同款玻璃分段控件
            // （胶囊指示器/拖拽选档/按压折射），关闭时回落主题原生样式
            if (sections.size > 1) {
                val glassTabsEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
                val sectionOptions = remember(sections) {
                    sections.mapIndexed { index, section ->
                        AppSegmentOption(index, section.title.ifBlank { "第${index + 1}季" })
                    }
                }
                AppThemeAdaptiveTabRow(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !glassTabsEnabled,
                    role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                    // 玻璃分组栏内容放得下时居中，超宽时沿用可滚动轨道；原生保持左对齐。
                    centerContent = glassTabsEnabled,
                    options = sectionOptions,
                    selectedValue = selectedSectionIndex,
                    onSelectionChange = { selectedSectionIndex = it },
                    scrollable = true,
                    dragSelectionEnabled = true,
                    tapPressRefractionEnabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        // 仅玻璃 dock 下移，原生分区栏保留原有间距。
                        .padding(start = 12.dp, end = 12.dp, top = if (glassTabsEnabled) 12.dp else 6.dp, bottom = 6.dp),
                )
            }

            // ── 视频列表 ──
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(top = 7.dp, bottom = 16.dp)
            ) {
                itemsIndexed(
                    sortedEpisodes,
                    key = { _, episode -> "${selectedSectionIndex}-${episode.bvid}-${episode.cid}-${episode.id}" }
                ) { _, episode ->
                    val isCurrentEpisode = isCurrentUgcEpisode(
                        currentBvid = currentBvid,
                        currentCid = currentCid,
                        episode = episode
                    )
                    CollectionEpisodeRow(
                        episode = episode,
                        knownDetail = currentVideoInfo,
                        isCurrentEpisode = isCurrentEpisode,
                        onClick = {
                            if (!isCurrentEpisode) {
                                selectionHaptic(HapticType.LIGHT)
                                onEpisodeClick(episode)
                            }
                        },
                        onPartClick = { part ->
                            // 分 P 直达：沿用当前集元信息，仅替换目标 cid
                            if (episode.bvid != currentBvid || part.cid != currentCid) {
                                selectionHaptic(HapticType.LIGHT)
                            }
                            onEpisodeClick(episode.copy(cid = part.cid))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 单集合条目：
 * 封面（时长角标）｜标题（当前集 primary + 加粗）、发布时间、播放/弹幕数
 * 单集多分 P 时在条目下方展示分 P chips。
 */
@Composable
internal fun CollectionEpisodeRow(
    episode: UgcEpisode,
    isCurrentEpisode: Boolean,
    onClick: () -> Unit,
    onPartClick: (Page) -> Unit,
    knownDetail: com.android.purebilibili.data.model.response.ViewInfo? = null,
) {
    val arc = episode.arc
    val publishTimeText = remember(episode.arc?.pubdate, episode.arc?.ctime) {
        resolveCollectionEpisodeAbsoluteTimeText(episode)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val coverWidth = minOf(160.dp, maxWidth * 0.40f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(coverWidth)
                    .height(100.dp)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                arc?.pic?.takeIf { it.isNotBlank() }?.let { pic ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(FormatUtils.resolveVideoCoverUrl(pic, useLowQuality = false))
                            .crossfade(true)
                            .build(),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CollectionAccessBadge(
                    episode = episode,
                    knownDetail = knownDetail,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
                VideoCardCoverDurationText(
                    text = FormatUtils.formatDuration(arc?.duration ?: 0),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).height(100.dp)) {
                AppText(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentEpisode) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentEpisode) {
                        AppContentColors.accent()
                    } else {
                        AppContentColors.primaryText()
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (publishTimeText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = publishTimeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                val stat = arc?.stat
                if (stat != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // 复用全局统计原子（图标/字号/间距与其他卡片一致）
                    HorizontalVideoStatRow(
                        playText = FormatUtils.formatStat(stat.view.toLong()),
                        danmakuText = FormatUtils.formatStat(stat.danmaku.toLong())
                    )
                }
            }
        }
        }

        // 分 P chips：单集多 P 时展示
        if (episode.pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .height(45.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                episode.pages.forEachIndexed { pageIndex, page ->
                    AppText(
                        text = "${pageIndex + 1}.${page.part}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = page.cid > 0) { onPartClick(page) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}



/** 绝对时间格式：2026-09-25 12:00 */
internal fun resolveCollectionEpisodeAbsoluteTimeText(episode: UgcEpisode): String {
    val timestamp = episode.arc?.pubdate?.takeIf { it > 0L } ?: return ""
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp * 1000L))
}
