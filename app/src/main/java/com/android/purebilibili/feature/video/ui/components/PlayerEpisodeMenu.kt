package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

/** One bounded lazy viewport in the native page/detail region in every player mode. */
@Composable
fun PlayerEpisodeMenu(
    pages: List<Page>, currentPageIndex: Int, ugcSeason: UgcSeason?,
    currentBvid: String, currentCid: Long, anchorBounds: IntRect?,
    onDismiss: () -> Unit, onPageSelect: (Int) -> Unit, onEpisodeSelect: (UgcEpisode) -> Unit,
    isFullscreen: Boolean = false, playerBounds: IntRect? = null,
    cover: String = "", isReversed: Boolean = false, onReverse: (() -> Unit)? = null,
    isSubscribed: Boolean? = null, onSubscriptionChange: ((Boolean) -> Unit)? = null,
    onCoverLongPress: ((String) -> Unit)? = null,
) {
    val selectionHaptic = rememberHapticFeedback()
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun currentIndex(): Int {
        val current = content.sections.getOrNull(content.currentSectionIndex)?.entries?.getOrNull(content.currentEntryIndex)
        return entries.indexOfFirst { current in it }
    }
    LaunchedEffect(content, sectionIndex, reversed) {
        val index = if (sectionIndex == content.currentSectionIndex) currentIndex() else 0
        // 面板展开/切换分区后动画定位当前集，与面板入场衔接；越界保持原位。
        if (index in entries.indices) listState.animateScrollToItem(index)
    }
    PlayerDetailPanel(title = if (pages.size > 1) "分P" else "合集", isFullscreen = isFullscreen,
        playerBounds = playerBounds, onDismiss = onDismiss, compactHeight = true) {
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
            PanelAction(Icons.Default.Close, "关闭", onDismiss)
        }
        if (content.sections.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            content.sections.forEachIndexed { index, item ->
                Text(item.title, color = if (index == sectionIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { sectionIndex = index }.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), overscrollEffect = null,
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
                    onDismiss()
                    when (val target = part.target) {
                        is PlayerEpisodeMenuTarget.Part -> onPageSelect(target.pageIndex)
                        is PlayerEpisodeMenuTarget.Episode -> onEpisodeSelect(target.episode)
                    }
                }
                Column {
                    PlayerPanelMediaRow(
                        title = if (group.size > 1) episode?.title.orEmpty().ifBlank { entry.title } else entry.title,
                        cover = entry.cover.ifBlank { cover },
                        durationSeconds = if (group.size > 1) episode?.arc?.duration?.toLong() ?: entry.durationSeconds else entry.durationSeconds,
                        selected = selected,
                        indexLabel = entry.indexLabel.substringBefore(" · "),
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
                                    .clickable { select(part) }.padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PanelAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(35.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

/** Ordinary native page surface, independent of the application's Miuix/glass setting. */
@Composable
internal fun PlayerDetailPanel(title: String, isFullscreen: Boolean, playerBounds: IntRect?,
    onDismiss: () -> Unit, modifier: Modifier = Modifier,
    compactHeight: Boolean = false,
    draggable: Boolean = false,
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val size = LocalWindowInfo.current.containerSize
    val insets = WindowInsets.safeDrawing
    val left = insets.getLeft(density, direction).coerceIn(0, size.width)
    val top = insets.getTop(density).coerceIn(0, size.height)
    val safe = IntRect(left, top, (size.width - insets.getRight(density, direction)).coerceIn(left, size.width),
        (size.height - insets.getBottom(density)).coerceIn(top, size.height))
    val landscape = size.width > size.height
    val compactMaxHeight = if (compactHeight) with(density) { episodeSheetMaxHeight().roundToPx() } else null
    val bounds = resolvePlayerPanelBounds(safe, playerBounds, isFullscreen, landscape,
        with(density) { 500.dp.roundToPx() }, maxBottomHeight = compactMaxHeight)
    if (bounds.width <= 0 || bounds.height <= 0) return
    val dragEnabled = draggable && isFullscreen && landscape
    // 退出位移包含窗口右侧安全边距，画面和面板一直共用相同的可见边界。
    val dragDistancePx = (size.width - bounds.left).coerceAtLeast(bounds.width)
    val closeProgress = remember(bounds) { mutableFloatStateOf(0f) }
    val dismissAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    ReservePlayerPanelViewport(
        bounds,
        enabled = isFullscreen && landscape,
        dragDistancePx = dragDistancePx,
        closeProgressProvider = { if (dragEnabled) closeProgress.floatValue else 0f },
    )
    val provider = remember(bounds) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
            popupContentSize: IntSize) = IntOffset(bounds.left, bounds.top)
    } }
    val surfaceContent: @Composable (requestDismiss: () -> Unit) -> Unit = { requestDismiss ->
        val panelContent: @Composable () -> Unit = {
            Column(
                modifier = modifier
                    .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .semantics { paneTitle = title },
                content = { content(requestDismiss) },
            )
        }
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = panelContent,
        )
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = { dismissAction.value?.invoke() ?: onDismiss() },
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        if (dragEnabled) {
            LandscapeSidePanel(
                visible = true,
                edge = LandscapeSidePanelEdge.End,
                width = with(density) { bounds.width.toDp() },
                closeProgress = closeProgress,
                dragDistance = with(density) { dragDistancePx.toDp() },
                onDismiss = onDismiss,
            ) { requestDismiss ->
                SideEffect { dismissAction.value = requestDismiss }
                surfaceContent(requestDismiss)
            }
        } else {
            SideEffect { dismissAction.value = null }
            surfaceContent(onDismiss)
        }
    }
}

@Composable
internal fun PlayerPanelMediaRow(title: String, cover: String, durationSeconds: Long, selected: Boolean,
    indexLabel: String = "", onCoverLongPress: ((String) -> Unit)? = null,
    accessLabel: String? = null, accessEpisode: com.android.purebilibili.data.model.response.UgcEpisode? = null,
    expandTitle: Boolean = false,
    onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    // 章节列表按完整标题增高；其他媒体列表继续使用原有两行与固定行高。
    Row(Modifier.fillMaxWidth()
        .then(if (expandTitle) Modifier.heightIn(min = 110.dp) else Modifier.height(110.dp))
        .semantics(mergeDescendants = true) { this.selected = selected }
        .combinedClickable(onClick = onClick,
            onLongClick = if (cover.isNotBlank() && onCoverLongPress != null) ({ onCoverLongPress(cover) }) else null)
        .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cover.isNotBlank()) Box(Modifier.width(160.dp).height(100.dp)) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (accessEpisode != null) {
                CollectionAccessBadge(episode = accessEpisode, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            } else {
                CollectionAccessBadge(label = accessLabel, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            if (durationSeconds > 0) Text(FormatUtils.formatDuration(durationSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                fontSize = 12.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 3.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 20.sp, color = color,
                maxLines = if (expandTitle) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (cover.isBlank()) {
                if (accessEpisode != null) CollectionAccessBadge(episode = accessEpisode)
                else CollectionAccessBadge(label = accessLabel)
            }
            if (indexLabel.isNotBlank()) Text(indexLabel, fontSize = 12.sp, color = color)
            if (cover.isBlank() && durationSeconds > 0) Text(FormatUtils.formatDuration(durationSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), fontSize = 12.sp)
            if (selected) Text("正在播放", fontSize = 12.sp, color = color)
        }
    }
}
