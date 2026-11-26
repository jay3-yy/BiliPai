package com.android.purebilibili.feature.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.plugin.feed.FeedSource
import com.android.purebilibili.core.plugin.feed.loadEnabledFeedSources
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appHorizontalScroll
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.feature.personal.CompletionKeepsakeKind
import com.android.purebilibili.feature.personal.CompletionKeepsakePrompt
import com.android.purebilibili.feature.personal.CompletionKeepsakeRecord
import com.android.purebilibili.feature.personal.CompletionKeepsakeShareSheet
import com.android.purebilibili.feature.personal.CompletionKeepsakeSheet
import com.android.purebilibili.feature.personal.CompletionKeepsakeStore
import com.android.purebilibili.feature.personal.filterCompletionKeepsakes
import com.android.purebilibili.feature.personal.groupCompletionKeepsakesByMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val KEEPSAKE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/**
 * 独立的「观看与阅读统计」页面：
 * 整合阅读/观看概览、趋势图表、完播率、常看 UP 主、往日拾遗与完结纪念短评。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalRecapScreen(
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    onShowAllUps: (PersonalRecapWindow, PersonalVideoRecapStats) -> Unit,
    onVideoClick: (bvid: String, cid: Long, coverUrl: String) -> Unit,
    onBangumiClick: ((seasonId: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var window by rememberSaveable { mutableStateOf(PersonalRecapWindow.LAST_SEVEN_DAYS) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var sources by remember { mutableStateOf<List<FeedSource>>(emptyList()) }
    var rssStats by remember { mutableStateOf(PersonalRssRecapStats()) }
    var videoStats by remember { mutableStateOf<PersonalVideoRecapStats?>(null) }
    var memoryPick by remember { mutableStateOf<HistoryMemoryPick?>(null) }
    var keepsakes by remember { mutableStateOf<List<CompletionKeepsakeRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var videoUnavailable by remember { mutableStateOf(false) }
    var showShareCardSheet by remember { mutableStateOf(false) }
    var sharingKeepsake by remember { mutableStateOf<CompletionKeepsakeRecord?>(null) }
    var editingKeepsake by remember { mutableStateOf<CompletionKeepsakeRecord?>(null) }
    var keepsakeKindFilter by rememberSaveable { mutableStateOf<CompletionKeepsakeKind?>(null) }
    var keepsakeSearchOpen by rememberSaveable { mutableStateOf(false) }
    var keepsakeSearchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(window, refreshNonce) {
        loading = true
        videoUnavailable = false
        val start = resolvePersonalRecapWindowStart(System.currentTimeMillis(), window)
        try {
            val loadedSources = withContext(Dispatchers.IO) { loadEnabledFeedSources(context) }
            val loadedRss = PersonalRecapRepository.rssRecap(context, start)
            val loadedKeepsakes = CompletionKeepsakeStore.loadRecords(context)
            sources = loadedSources
            rssStats = loadedRss
            keepsakes = loadedKeepsakes

            val historyList = fetchPersonalVideoRecapHistory(start, maxPages = 10)
            videoStats = aggregatePersonalVideoRecap(
                items = historyList,
                windowStartSec = start / 1000L,
            ).copy(
                historyMayBeIncomplete = historyList.size >= 300 &&
                    (historyList.lastOrNull()?.view_at ?: 0L) >= start / 1000L,
            )
            memoryPick = pickHistoryMemoryFromData(historyList)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            videoUnavailable = videoStats == null
        } finally {
            loading = false
        }
    }

    AppScaffold(
        modifier = Modifier.globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer()),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.history_activity_stats),
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    AppIconButton(
                        enabled = !loading && ((videoStats?.videoCount ?: 0) > 0 || rssStats.readCount > 0),
                        onClick = { showShareCardSheet = true },
                    ) {
                        AppIcon(Icons.Outlined.IosShare, contentDescription = "生成时光纪念卡片")
                    }
                    AppIconButton(
                        enabled = !loading,
                        onClick = { refreshNonce++ },
                    ) {
                        AppIcon(Icons.Rounded.Refresh, contentDescription = "刷新统计")
                    }
                },
            )
        },
    ) { paddingValues ->
        val currentVideoStats = videoStats
        val emptyRss = rssStats.readCount <= 0
        val emptyVideo = videoUnavailable || currentVideoStats == null || currentVideoStats.videoCount <= 0
        val filteredKeepsakes = remember(keepsakes, keepsakeKindFilter, keepsakeSearchQuery) {
            filterCompletionKeepsakes(
                records = keepsakes,
                kindFilter = keepsakeKindFilter,
                query = keepsakeSearchQuery,
            )
        }
        val countByKind = remember(keepsakes) {
            keepsakes.groupingBy { it.kind }.eachCount()
        }
        val keepsakeMonthGroups = remember(filteredKeepsakes) {
            groupCompletionKeepsakesByMonth(filteredKeepsakes)
        }

        AppLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "window_selector", contentType = "window_selector") {
                PersonalRecapWindowSelector(
                    window = window,
                    onWindowChange = { window = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (loading && currentVideoStats == null && rssStats.readCount == 0) {
                item(key = "loading", contentType = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AdaptiveLoadingIndicator(size = 28.dp)
                    }
                }
            } else {
                item(key = "overview_card", contentType = "overview_card") {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = AppSurfaceTokens.cardContainer(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (emptyRss && emptyVideo) {
                                AppText(
                                    text = if (videoUnavailable) {
                                        "暂时无法读取观看记录，点击右上角可重新刷新。"
                                    } else {
                                        "这段时间还没有阅读或观看记录。"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppContentColors.secondaryText(),
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!emptyRss) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            AppIcon(
                                                Icons.AutoMirrored.Outlined.MenuBook,
                                                contentDescription = null,
                                                tint = AppContentColors.accent(),
                                                modifier = Modifier.size(24.dp),
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                AppText(
                                                    text = "已读 ${rssStats.readCount} 篇",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                val topSource = rssStats.topSourceKey
                                                    ?.let { key -> sources.firstOrNull { it.id == key }?.title }
                                                if (!topSource.isNullOrBlank()) {
                                                    AppText(
                                                        text = "最常读：$topSource",
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
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            AppIcon(
                                                Icons.Outlined.SmartDisplay,
                                                contentDescription = null,
                                                tint = AppContentColors.accent(),
                                                modifier = Modifier.size(24.dp),
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

                                if (currentVideoStats?.historyMayBeIncomplete == true) {
                                    AppText(
                                        text = "较早的观看记录尚未全部载入，视频统计仅包含已载入记录。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.secondaryText(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (currentVideoStats != null && currentVideoStats.topUps.isNotEmpty()) {
                item(key = "top_ups_card", contentType = "top_ups_card") {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = AppSurfaceTokens.cardContainer(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(
                                    text = "最近爱看的 UP 主",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (currentVideoStats.topUps.size > 5) {
                                    AppTextButton(onClick = { onShowAllUps(window, currentVideoStats) }) {
                                        AppText("查看全部")
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.appHorizontalScroll(rememberScrollState()),
                            ) {
                                currentVideoStats.topUps.take(10).forEach { up ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier
                                            .width(60.dp)
                                            .clickable { onUpClick(up.mid) },
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (up.face.isNotBlank()) {
                                                AsyncImage(
                                                    model = up.face,
                                                    contentDescription = up.name,
                                                    contentScale = ContentScale.Crop,
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

            memoryPick?.let { pick ->
                item(key = "memory_pick", contentType = "memory_pick") {
                    HistoryMemoryCard(
                        pick = pick,
                        onPlay = { onVideoClick(it.bvid, it.cid, it.coverUrl) },
                    )
                }
            }

            if (keepsakes.isNotEmpty()) {
                item(key = "keepsakes_header", contentType = "section_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = AppContentColors.accent(),
                            modifier = Modifier.size(16.dp),
                        )
                        AppText(
                            text = if (filteredKeepsakes.size == keepsakes.size) {
                                "完结纪念 (${keepsakes.size})"
                            } else {
                                "完结纪念 (${filteredKeepsakes.size}/${keepsakes.size})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppContentColors.primaryText(),
                            modifier = Modifier.weight(1f),
                        )
                        AppIconButton(
                            onClick = {
                                if (keepsakeSearchOpen) {
                                    keepsakeSearchOpen = false
                                    keepsakeSearchQuery = ""
                                } else {
                                    keepsakeSearchOpen = true
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            AppIcon(
                                imageVector = if (keepsakeSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = if (keepsakeSearchOpen) "关闭纪念搜索" else "搜索完结纪念",
                                tint = AppContentColors.secondaryText(),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                if (keepsakeSearchOpen) {
                    item(key = "keepsakes_search", contentType = "keepsakes_search") {
                        AppOutlinedTextField(
                            value = keepsakeSearchQuery,
                            onValueChange = { keepsakeSearchQuery = it.take(60) },
                            placeholder = { AppText("搜索作品名或完结短评…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(key = "keepsakes_filter", contentType = "keepsakes_filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .appHorizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppFilterChip(
                            selected = keepsakeKindFilter == null,
                            onClick = { keepsakeKindFilter = null },
                            label = { AppText("全部 (${keepsakes.size})") },
                        )
                        CompletionKeepsakeKind.entries.forEach { kind ->
                            val count = countByKind[kind] ?: 0
                            if (count > 0 || keepsakeKindFilter == kind) {
                                AppFilterChip(
                                    selected = keepsakeKindFilter == kind,
                                    onClick = {
                                        keepsakeKindFilter = if (keepsakeKindFilter == kind) null else kind
                                    },
                                    label = { AppText("${kind.badgeLabel} ($count)") },
                                )
                            }
                        }
                    }
                }
                if (filteredKeepsakes.isEmpty()) {
                    item(key = "keepsakes_filtered_empty", contentType = "empty_card") {
                        AppSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.container(ContainerLevel.Card),
                            color = AppSurfaceTokens.cardContainer(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(
                                    text = "没有匹配的完结纪念",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                                AppTextButton(
                                    onClick = {
                                        keepsakeKindFilter = null
                                        keepsakeSearchQuery = ""
                                    },
                                ) {
                                    AppText("重置筛选")
                                }
                            }
                        }
                    }
                } else {
                    val showMonthHeaders = keepsakeMonthGroups.size >= 2
                    keepsakeMonthGroups.forEach { group ->
                        if (showMonthHeaders) {
                            item(key = "keepsake_month_${group.monthKey}", contentType = "keepsake_month_header") {
                                AppText(
                                    text = "${group.monthLabel} · ${group.records.size} 部",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppContentColors.secondaryText(),
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                )
                            }
                        }
                        items(
                            items = group.records,
                            key = { "keepsake_${it.id}" },
                            contentType = { "keepsake_card" },
                        ) { record ->
                            CompletionKeepsakeArchiveCard(
                                record = record,
                                onOpen = {
                                    if (record.seasonId > 0L && record.bvid.isBlank() && onBangumiClick != null) {
                                        onBangumiClick(record.seasonId)
                                    } else if (record.bvid.isNotBlank()) {
                                        onVideoClick(record.bvid, 0L, record.coverUrl)
                                    }
                                },
                                onEdit = { editingKeepsake = record },
                                onShare = { sharingKeepsake = record },
                                onDelete = {
                                    scope.launch {
                                        CompletionKeepsakeStore.deleteRecord(context, record.id)
                                        keepsakes = keepsakes.filterNot { it.id == record.id }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareCardSheet) {
        val sharePayload = remember(window, videoStats, rssStats, keepsakes) {
            buildPersonalRecapCardPayload(
                window = window,
                videoStats = videoStats,
                rssStats = rssStats,
                keepsakes = keepsakes,
            )
        }
        PersonalRecapShareSheet(
            payload = sharePayload,
            onDismiss = { showShareCardSheet = false },
        )
    }

    sharingKeepsake?.let { record ->
        CompletionKeepsakeShareSheet(
            record = record,
            onDismiss = { sharingKeepsake = null },
        )
    }

    editingKeepsake?.let { record ->
        val editPrompt = remember(record) {
            CompletionKeepsakePrompt(
                id = record.id,
                kind = record.kind,
                title = record.title,
                episodeCount = record.episodeCount,
                coverUrl = record.coverUrl,
                bvid = record.bvid,
                seasonId = record.seasonId,
            )
        }
        CompletionKeepsakeSheet(
            prompt = editPrompt,
            initialReflection = record.reflection,
            isEditing = true,
            onSave = { updatedReflection ->
                val updated = record.copy(reflection = updatedReflection)
                editingKeepsake = null
                scope.launch {
                    CompletionKeepsakeStore.saveRecord(context, updated)
                    keepsakes = keepsakes.map { if (it.id == updated.id) updated else it }
                }
            },
            onDismiss = { editingKeepsake = null },
        )
    }
}

@Composable
private fun CompletionKeepsakeArchiveCard(
    record: CompletionKeepsakeRecord,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateLabel = remember(record.completedAtMs) {
        KEEPSAKE_DATE_FORMATTER.format(
            Instant.ofEpochMilli(record.completedAtMs).atZone(ZoneId.systemDefault())
        )
    }
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (record.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = record.coverUrl,
                        contentDescription = record.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 76.dp, height = 48.dp)
                            .aspectRatio(16f / 10f)
                            .clip(AppShapes.container(ContainerLevel.Chip)),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AppText(
                        text = "${record.kind.badgeLabel} · 全 ${record.episodeCount} 集 · $dateLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.accent(),
                    )
                    AppText(
                        text = record.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppContentColors.primaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppIconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(30.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑完结短评",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(15.dp),
                    )
                }
                AppIconButton(
                    onClick = onShare,
                    modifier = Modifier.size(30.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = "导出完结纪念卡片",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(15.dp),
                    )
                }
                AppIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移除纪念",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (record.reflection.isNotBlank()) {
                AppText(
                    text = "“${record.reflection}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
        }
    }
}
