package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppTabRowRole
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import com.android.purebilibili.core.ui.components.AppIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText
import kotlinx.coroutines.launch

/** Keep current-video parts separate from a surrounding watch-later/favorite/UP queue. */
@Composable
fun PlayerEpisodeSourceMenu(
    externalPlaylistTitle: String,
    onDismiss: () -> Unit,
    onPartsClick: () -> Unit,
    onExternalPlaylistClick: () -> Unit,
    isFullscreen: Boolean = false,
    anchorBounds: IntRect? = null,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
) {
    PlayerSelectionPopup(
        title = "选集",
        showTitle = false,
        placement = if (anchorBounds != null) PlayerListPopupPlacement.ANCHOR_END
            else if (isFullscreen) PlayerListPopupPlacement.END_BOTTOM else PlayerListPopupPlacement.CENTER,
        anchorBounds = anchorBounds,
        source = source,
        onDismissRequest = onDismiss,
    ) {
        PlayerSelectionItem(
            index = 0, optionCount = 2, text = "分P选集", selected = false,
            onClick = { onDismiss(); onPartsClick() },
        )
        PlayerSelectionItem(
            index = 1, optionCount = 2, text = externalPlaylistTitle, selected = false,
            onClick = { onDismiss(); onExternalPlaylistClick() },
        )
    }
}

/** One bounded lazy viewport in the native page/detail region in every player mode. */
@Composable
fun PlayerEpisodeMenu(
    pages: List<Page>, currentPageIndex: Int, ugcSeason: UgcSeason? = null,
    currentBvid: String = "", currentCid: Long = 0L,
    onDismiss: () -> Unit, onPageSelect: (Int) -> Unit, onEpisodeSelect: ((UgcEpisode) -> Unit)? = null,
    isFullscreen: Boolean = false, playerBounds: IntRect? = null,
    cover: String = "", isReversed: Boolean = false, onReverse: (() -> Unit)? = null,
    isSubscribed: Boolean? = null, onSubscriptionChange: ((Boolean) -> Unit)? = null,
    onCoverLongPress: ((String) -> Unit)? = null,
    inlinePanel: Boolean = false,
) {
    val selectionHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    val content = remember(pages, currentPageIndex, ugcSeason, currentBvid, currentCid) {
        resolvePlayerEpisodeMenuContent(pages, currentPageIndex, ugcSeason, currentBvid, currentCid)
    }
    if (content.sections.isEmpty()) return
    var sectionIndex by remember(content) { mutableIntStateOf(content.currentSectionIndex.coerceAtLeast(0)) }
    val reverseSections = remember(content) { mutableStateMapOf<Int, Boolean>() }
    val reversed = reverseSections[sectionIndex] == true
    val section = content.sections[sectionIndex]
    val entries = remember(section, reversed) {
        val grouped = groupPlayerEpisodeEntries(section.entries)
        if (reversed) grouped.asReversed() else grouped
    }
    fun currentIndex(): Int {
        val current = content.sections.getOrNull(content.currentSectionIndex)?.entries?.getOrNull(content.currentEntryIndex)
        return entries.indexOfFirst { current in it }
    }
    val initialIndex = if (sectionIndex == content.currentSectionIndex) currentIndex().coerceAtLeast(0) else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(content, sectionIndex, reversed) {
        val index = if (sectionIndex == content.currentSectionIndex) currentIndex() else 0
        // 自动定位不与面板入场叠加滚动动画，首帧及分区/顺序变化后保持当前集完整可见。
        if (index in entries.indices) listState.scrollToItem(index)
    }
    PlayerEpisodeSelectionHost(title = if (pages.size > 1) "分P" else "合集", isFullscreen = isFullscreen,
        playerBounds = playerBounds, onDismiss = onDismiss, inlinePanel = inlinePanel) { requestPanelDismiss ->
        Row(Modifier.fillMaxWidth().height(45.dp).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (pages.size > 1) "分P" else "合集", fontSize = 16.sp)
            if (ugcSeason != null && isSubscribed != null && onSubscriptionChange != null) {
                PanelAction(if (isSubscribed) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                    if (isSubscribed) "取消订阅" else "订阅") { onSubscriptionChange(!isSubscribed) }
            }
            PanelAction(Icons.Default.VerticalAlignTop, "跳至顶部") { scope.launch { listState.animateScrollToItem(0) } }
            PanelAction(Icons.Default.VerticalAlignBottom, "跳至底部") { scope.launch { if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex) } }
            PanelAction(Icons.Default.MyLocation, "跳至当前") {
                if (content.currentSectionIndex >= 0) {
                    if (sectionIndex != content.currentSectionIndex) sectionIndex = content.currentSectionIndex
                    else scope.launch { val index = currentIndex(); if (index in entries.indices) listState.animateScrollToItem(index) }
                }
            }
            if (onReverse != null && sectionIndex == content.currentSectionIndex) {
                PanelAction(if (isReversed) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    if (isReversed) "正序播放" else "倒序播放", onReverse)
            }
            Spacer(Modifier.weight(1f))
            PanelAction(Icons.Default.Sort, if (reversed) "顺序" else "倒序") { reverseSections[sectionIndex] = !reversed }
            PanelAction(Icons.Default.Close, "关闭", requestPanelDismiss)
        }
        com.android.purebilibili.core.ui.components.AppHorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        if (content.sections.size > 1) {
            // 玻璃分区复用共享 dock 与完整槽位指示器，原生保留原有文字轨道。
            if (LocalAppThemeConfig.current.liquidGlassEnabled) {
                AppThemeAdaptiveTabRow(
                    options = content.sections.mapIndexed { index, item ->
                        AppSegmentOption(index, item.title)
                    },
                    selectedValue = sectionIndex,
                    onSelectionChange = { sectionIndex = it },
                    role = AppTabRowRole.SECONDARY,
                    scrollable = true,
                    centerContent = true,
                    liquidGlassAdaptIndicatorToLabel = false,
                    dragSelectionEnabled = true,
                    tapPressRefractionEnabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    content.sections.forEachIndexed { index, item ->
                        Text(item.title, color = if (index == sectionIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.appActionClickable { sectionIndex = index }.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(), overscrollEffect = null,
            contentPadding = PaddingValues(top = 7.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(entries) { _, group ->
                val current = content.sections.getOrNull(content.currentSectionIndex)?.entries?.getOrNull(content.currentEntryIndex)
                val selected = sectionIndex == content.currentSectionIndex && current in group
                val entry = group.first()
                val episode = (entry.target as? PlayerEpisodeMenuTarget.Episode)?.episode
                fun select(part: PlayerEpisodeMenuEntry) {
                    if (part == current) return
                    selectionHaptic(HapticType.LIGHT)
                    requestPanelDismiss()
                    when (val target = part.target) {
                        is PlayerEpisodeMenuTarget.Part -> onPageSelect(target.pageIndex)
                        is PlayerEpisodeMenuTarget.Episode -> onEpisodeSelect?.invoke(target.episode)
                    }
                }
                Column {
                    PlayerPanelMediaRow(
                        title = if (group.size > 1) episode?.title.orEmpty().ifBlank { entry.title } else entry.title,
                        cover = entry.cover.ifBlank { cover },
                        durationSeconds = if (group.size > 1) episode?.arc?.duration?.toLong() ?: entry.durationSeconds else entry.durationSeconds,
                        selected = selected,
                        indexLabel = entry.indexLabel.substringBefore(" · "),
                        partCoverIndex = if (entry.target is PlayerEpisodeMenuTarget.Part) entry.indexLabel else null,
                        episodePresentation = true,
                        episodePubdate = entry.pubdateSeconds,
                        episodeViewCount = entry.viewCount,
                        episodeDanmakuCount = entry.danmakuCount,
                        onCoverLongPress = onCoverLongPress,
                        accessEpisode = episode,
                    ) { if (!selected) select(entry) }
                    if (group.size > 1) Row(Modifier.fillMaxWidth().height(55.dp)
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                        .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { part ->
                            val active = part == current
                            Text(part.title.substringAfterLast(" · "),
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.playerPopupSelectedOption(active).height(45.dp).semantics { this.selected = active }
                                    .appActionClickable { select(part) }.padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerEpisodeSelectionHost(
    title: String,
    isFullscreen: Boolean,
    playerBounds: IntRect?,
    onDismiss: () -> Unit,
    inlinePanel: Boolean = false,
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
    if (inlinePanel) {
        EpisodeSelectionSheet(onDismiss = onDismiss, inlinePanel = true, content = content)
        return
    }
    val window = LocalWindowInfo.current.containerSize
    if (isFullscreen && window.width > window.height) {
        // 横屏全屏沿用播放器侧栏和画面避让；其余入口与合集共用自适应面板。
        PlayerDetailPanel(title, isFullscreen, playerBounds, onDismiss, compactHeight = true, content = content)
    } else {
        val maxHeight = episodeSheetMaxHeight()
        EpisodeSelectionSheet(onDismiss = onDismiss) { requestDismiss ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .semantics { paneTitle = title },
                content = { content(requestDismiss) },
            )
        }
    }
}

@Composable
internal fun PanelAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(35.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun playerPanelSafeWindowBounds(): IntRect {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val size = LocalWindowInfo.current.containerSize
    val insets = WindowInsets.safeDrawing
    val left = insets.getLeft(density, direction).coerceIn(0, size.width)
    val top = insets.getTop(density).coerceIn(0, size.height)
    return IntRect(
        left, top,
        (size.width - insets.getRight(density, direction)).coerceIn(left, size.width),
        (size.height - insets.getBottom(density)).coerceIn(top, size.height),
    )
}

/** Ordinary native page surface, independent of the application's Miuix/glass setting. */
@Composable
internal fun PlayerDetailPanel(title: String, isFullscreen: Boolean, playerBounds: IntRect?,
    onDismiss: () -> Unit, modifier: Modifier = Modifier,
    compactHeight: Boolean = false, useGlassSurface: Boolean = false,
    draggable: Boolean = false,
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit) {
    if (!rememberPlayerPopupHostActive(onDismiss)) return
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    val safe = playerPanelSafeWindowBounds()
    val landscape = size.width > size.height
    val compactMaxHeight = if (compactHeight) with(density) { episodeSheetMaxHeight().roundToPx() } else null
    val bounds = resolvePlayerPanelBounds(safe, playerBounds, isFullscreen, landscape,
        with(density) { 500.dp.roundToPx() }, maxBottomHeight = compactMaxHeight)
    if (bounds.width <= 0 || bounds.height <= 0) return
    // 底板延伸到窗口底边，列表仍避让导航区；不能把透明导航区留给后面的详情页。
    val windowBounds = if (landscape) bounds else IntRect(bounds.left, bounds.top, bounds.right, size.height)
    val bottomContentInset = with(density) { (windowBounds.bottom - bounds.bottom).coerceAtLeast(0).toDp() }
    val dragEnabled = draggable && isFullscreen && landscape
    // 退出位移包含窗口右侧安全边距，画面和面板一直共用相同的可见边界。
    val dragDistancePx = (size.width - bounds.left).coerceAtLeast(bounds.width)
    val closeProgress = remember(bounds) { mutableFloatStateOf(1f) }
    val dismissAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    ReservePlayerPanelViewport(
        bounds,
        enabled = isFullscreen && landscape,
        dragDistancePx = dragDistancePx,
        closeProgressProvider = { closeProgress.floatValue },
    )
    val provider = remember(windowBounds, landscape) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
            popupContentSize: IntSize) = resolvePlayerPanelPopupPosition(
                windowBounds, windowSize, popupContentSize, bottomAligned = !landscape,
            )
    } }
    val surfaceContent: @Composable (requestDismiss: () -> Unit) -> Unit = { requestDismiss ->
        val panelContent: @Composable () -> Unit = {
            Column(
                modifier = modifier
                    // 弹窗在旧系统上可能比宿主窗口小；遵守实际测量约束，避免子项被挤出屏幕。
                    .size(with(density) { windowBounds.width.toDp() }, with(density) { windowBounds.height.toDp() })
                    .clipToBounds()
                    .padding(bottom = bottomContentInset)
                    .semantics { paneTitle = title },
                content = { content(requestDismiss) },
            )
        }
        // 仅玻璃入口显式选择弹窗材质，原生面板保留原有 Surface 与默认参数。
        if (useGlassSurface && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
            com.android.purebilibili.core.ui.AppPopupSurface(
                type = com.android.purebilibili.core.ui.AppPopupSurfaceType.SHEET,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                content = panelContent,
            )
        } else {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                content = panelContent,
            )
        }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = { dismissAction.value?.invoke() ?: onDismiss() },
        // 返回由 Popup 自己的窗口回调处理，保留手势进度与取消恢复。
        // 与宿主的 edge-to-edge 窗口使用同一坐标范围，避免系统可见框再次扣除状态栏。
        // 安全区由上面的 bounds/底部内边距处理，绘制边界由面板自身裁剪。
        properties = PopupProperties(focusable = true, clippingEnabled = false, dismissOnBackPress = false),
    ) {
        // 玻璃菜单宿主会填满父约束，必须在宿主外限定面板尺寸，避免 Popup 按整窗大小定位到顶部。
        // 两种材质共用原有面板边界，尺寸遵守 Popup 实际可用空间。
        Box(Modifier
            .size(with(density) { windowBounds.width.toDp() }, with(density) { windowBounds.height.toDp() })
            .clipToBounds()
        ) {
            com.android.purebilibili.core.ui.AppPopupWindowHost(enableWindowMaterial = false) {
                PlayerDetailPanelMotion(
                    sidePanel = landscape,
                    dragEnabled = dragEnabled,
                    distancePx = if (landscape) dragDistancePx.toFloat() else (size.height - bounds.top).toFloat(),
                    closeProgress = closeProgress,
                    onDismiss = onDismiss,
                    onDismissAction = { dismissAction.value = it },
                    content = surfaceContent,
                )
            }
        }
    }
}

@Composable
internal fun PlayerPanelMediaRow(title: String, cover: String, durationSeconds: Long, selected: Boolean,
    indexLabel: String = "", onCoverLongPress: ((String) -> Unit)? = null,
    accessLabel: String? = null, accessEpisode: com.android.purebilibili.data.model.response.UgcEpisode? = null,
    expandTitle: Boolean = false, metadataText: String? = null,
    durationBesideIndex: Boolean = false,
    partCoverIndex: String? = null,
    episodePresentation: Boolean = false,
    episodePubdate: Long = 0L,
    episodeViewCount: Long? = null,
    episodeDanmakuCount: Long? = null,
    onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val durationText = if (durationSeconds > 0) {
        FormatUtils.formatDuration(durationSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    } else ""
    // 分 P 时长放在编号后；章节、合集等调用方保留原有封面时长位置。
    val indexAndDuration = if (durationBesideIndex) {
        listOf(indexLabel, durationText).filter { it.isNotBlank() }.joinToString("  ")
    } else indexLabel
    // 章节列表按完整标题增高；其他媒体列表继续使用原有两行与固定行高。
    Row(Modifier.fillMaxWidth()
        .then(if (expandTitle) Modifier.heightIn(min = 110.dp) else Modifier.height(110.dp))
        .semantics(mergeDescendants = true) { this.selected = selected }
        .combinedClickable(onClick = onClick,
            onLongClick = if (cover.isNotBlank() && onCoverLongPress != null) ({ onCoverLongPress(cover) }) else null)
        .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cover.isNotBlank()) Box(Modifier.then(if (episodePresentation) Modifier.weight(0.67f, fill = false).widthIn(max = 160.dp) else Modifier.width(160.dp)).height(100.dp)) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (episodePresentation) Modifier.clip(com.android.purebilibili.core.ui.AppShapes.container(com.android.purebilibili.core.ui.ContainerLevel.Chip)) else Modifier))
            if (partCoverIndex != null) Text(
                text = partCoverIndex.removePrefix("P"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.secondaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 6.dp, bottomStart = 4.dp))
                    .padding(horizontal = 4.dp),
            )
            if (accessEpisode != null) {
                CollectionAccessBadge(episode = accessEpisode, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            } else {
                CollectionAccessBadge(label = accessLabel, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            if (!durationBesideIndex && durationText.isNotBlank()) VideoCardCoverDurationText(
                text = durationText,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
        if (cover.isBlank() && selected && episodePresentation) {
            Icon(Icons.Default.Equalizer, contentDescription = "正在播放", tint = color, modifier = Modifier.size(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f).then(if (episodePresentation) Modifier.height(100.dp) else Modifier),
            verticalArrangement = if (episodePresentation) Arrangement.Top else Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 14.sp, lineHeight = 20.sp, color = color,
                maxLines = if (expandTitle) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = if (episodePresentation) Modifier.weight(1f) else Modifier)
            if (episodePresentation && episodePubdate > 0L) {
                Text(java.time.Instant.ofEpochSecond(episodePubdate).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (episodePresentation && episodeViewCount != null && episodeViewCount >= 0L) {
                com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow(
                    playText = FormatUtils.formatStat(episodeViewCount),
                    danmakuText = episodeDanmakuCount?.takeIf { it >= 0L }?.let { FormatUtils.formatStat(it) }.orEmpty(),
                )
            }
            if (cover.isBlank()) {
                if (accessEpisode != null) CollectionAccessBadge(episode = accessEpisode)
                else CollectionAccessBadge(label = accessLabel)
            }
            if (!episodePresentation && indexAndDuration.isNotBlank()) Text(indexAndDuration, fontSize = 12.sp, color = color)
            if (!metadataText.isNullOrBlank()) Text(metadataText, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!durationBesideIndex && cover.isBlank() && durationText.isNotBlank()) Text(durationText, fontSize = 12.sp)
            if (selected && !episodePresentation) Text("正在播放", fontSize = 12.sp, color = color)
        }
    }
}
