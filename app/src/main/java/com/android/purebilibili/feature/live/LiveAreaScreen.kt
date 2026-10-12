package com.android.purebilibili.feature.live

import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.skeleton.ContentCategoryGridSkeleton
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.LiveAreaChild
import com.android.purebilibili.data.model.response.LiveAreaParent
import com.android.purebilibili.data.model.response.LiveFavoriteTagEntry
import com.android.purebilibili.data.repository.LiveRepository
import com.android.purebilibili.navigation.animatePagerSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LiveAreaScreen(onBack: () -> Unit, onAreaClick: (Int, Int, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberLiveChromePalette()
    var areas by remember { mutableStateOf<List<LiveAreaParent>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<LiveFavoriteTagEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val canSync = !TokenManager.accessTokenCache.isNullOrBlank()
    val pager = rememberPagerState(pageCount = { areas.size.coerceAtLeast(1) })
    LaunchedEffect(reload, canSync) {
        loading = true
        error = null
        favorites = SettingsManager.getLiveFavoriteTags(context).first()
        if (canSync) {
            LiveRepository.getLiveFavoriteTags().onSuccess {
                favorites = it
                SettingsManager.setLiveFavoriteTags(context, it)
            }.onFailure { Toast.makeText(context, it.message ?: "常用标签同步失败", Toast.LENGTH_SHORT).show() }
        }
        LiveRepository.getLiveAreaIndex().onSuccess { areas = it }.onFailure { error = it.message ?: "加载标签失败" }
        loading = false
    }
    fun finishEditing() {
        if (saving) return
        saving = true
        val snapshot = favorites
        scope.launch {
            try {
                val result = if (canSync) LiveRepository.setLiveFavoriteTags(snapshot) else Result.success(Unit)
                result.onSuccess {
                    SettingsManager.setLiveFavoriteTags(context, snapshot)
                    editing = false
                    Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(context, it.message ?: "保存失败，请重试", Toast.LENGTH_SHORT).show() }
            } finally { saving = false }
        }
    }
    AppScaffold(
        blurContentReady = !loading,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "全部标签",
                navigationIcon = { AppIconButton(onClick = onBack) { AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                actions = {
                    AppTextButton(enabled = !loading && !saving, onClick = { if (editing) finishEditing() else editing = true }) {
                        AppText(if (saving) "保存中…" else if (editing) "完成" else "编辑")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!loading && error == null) {
                LiveFavoriteTagsPanel(
                    favoriteTags = favorites,
                    isEditing = editing,
                    enabled = !saving,
                    onStartEditing = { editing = true },
                    onTagClick = { onAreaClick(it.parentAreaId, it.areaId, it.title) },
                    onRemove = { tag -> favorites = favorites.filterNot { it.areaId == tag.areaId && it.parentAreaId == tag.parentAreaId } },
                    onMove = { from, to -> favorites = favorites.toMutableList().apply { add(to, removeAt(from)) } },
                )
                AppThemeAdaptiveTabRow(
                    role = AppTabRowRole.SECONDARY,
                    options = areas.mapIndexed { index, parent -> AppSegmentOption(index, parent.name) },
                    selectedValue = pager.currentPage,
                    onSelectionChange = { target -> scope.launch { animatePagerSelection(pager, target) } },
                    scrollable = true,
                    centerContent = false,
                    indicatorPositionProvider = { pager.currentPage + pager.currentPageOffsetFraction },
                    isScrollInProgressProvider = { pager.isScrollInProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                loading -> ContentCategoryGridSkeleton(columns = (LocalWindowSizeClass.current.widthDp.value / 110).toInt().coerceAtLeast(1), modifier = Modifier.weight(1f).fillMaxWidth())
                error != null -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(error.orEmpty(), color = palette.secondaryText)
                    AppTextButton(onClick = { reload++ }) { AppText("重新加载") }
                }
                else -> AppHorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    areas.getOrNull(page)?.let { parent ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = maxOf(LocalBottomBarContentPadding.current, 24.dp)),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(parent.list.orEmpty(), key = { it.id }) { child ->
                                val tag = child.toLiveFavoriteTagEntry(parent)
                                LiveAreaGridItem(
                                    child = child,
                                    isEditing = editing,
                                    isFavorite = favorites.any { it.areaId == tag.areaId && it.parentAreaId == tag.parentAreaId },
                                    onClick = {
                                        if (saving) return@LiveAreaGridItem
                                        if (editing && tag.areaId > 0) favorites = toggleLiveFavoriteTag(favorites, tag)
                                        else onAreaClick(tag.parentAreaId, tag.areaId, tag.title)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveFavoriteTagsPanel(
    favoriteTags: List<LiveFavoriteTagEntry>,
    isEditing: Boolean,
    enabled: Boolean,
    onStartEditing: () -> Unit,
    onTagClick: (LiveFavoriteTagEntry) -> Unit,
    onRemove: (LiveFavoriteTagEntry) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val latestTags by rememberUpdatedState(favoriteTags)
    val latestMove by rememberUpdatedState(onMove)
    val latestStart by rememberUpdatedState(onStartEditing)
    val positions = remember { mutableStateMapOf<Int, Rect>() }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText("我的常用标签", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            AppText(if (isEditing) "长按拖动排序，点击移除" else "点击进入标签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            favoriteTags.forEach { tag ->
                key(tag.areaId) {
                    val bounds = positions[tag.areaId]
                    val offset = if (draggedId == tag.areaId && bounds != null) dragPosition - bounds.center else Offset.Zero
                    Box(
                        Modifier
                            .zIndex(if (draggedId == tag.areaId) 1f else 0f)
                            .onGloballyPositioned { coordinates ->
                                val rect = coordinates.boundsInParent()
                                if (positions[tag.areaId] != rect) positions[tag.areaId] = rect
                            }
                            .pointerInput(tag.areaId, enabled) {
                                if (enabled) detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        latestStart()
                                        draggedId = tag.areaId
                                        dragPosition = positions[tag.areaId]?.center ?: Offset.Zero
                                    },
                                    onDragEnd = { draggedId = null },
                                    onDragCancel = { draggedId = null },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragPosition += amount
                                        val targetId = positions.entries.firstOrNull { it.key != tag.areaId && latestTags.any { tag -> tag.areaId == it.key } && it.value.contains(dragPosition) }?.key
                                        val from = latestTags.indexOfFirst { it.areaId == tag.areaId }
                                        val to = latestTags.indexOfFirst { it.areaId == targetId }
                                        if (from >= 0 && to >= 0 && from != to) latestMove(from, to)
                                    },
                                )
                            },
                    ) {
                        AppSurface(
                            onClick = { if (isEditing) onRemove(tag) else onTagClick(tag) },
                            enabled = enabled,
                            shape = AppShapes.container(ContainerLevel.Pill),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.graphicsLayer(translationX = offset.x, translationY = offset.y),
                        ) {
                            AppText(tag.title + if (isEditing) " −" else "", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAreaIcon(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    if (imageUrl.isBlank()) {
        AppSurface(
            color = AppSurfaceTokens.surfaceContainer(),
            shape = AppShapes.container(ContainerLevel.Tag),
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppText(
                    text = title.take(1),
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = "$title 图标",
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(AppShapes.container(ContainerLevel.Tag))
        )
    }
}

@Composable
private fun LiveAreaGridItem(
    child: LiveAreaChild,
    isEditing: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.DoubleExtraLarge)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LiveAreaIcon(
                imageUrl = child.pic,
                title = child.name,
                modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge)
            )
            Spacer(Modifier.height(AppSpacingTokens.ExtraSmall))
            AppText(
                text = child.name,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (isEditing && child.id != "0") {
            AppSurface(
                shape = CircleShape,
                color = if (isFavorite) colorScheme.surfaceVariant else colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = AppSpacingTokens.Large)
                    .size(AppSpacingTokens.Large)
            ) {
                AppIcon(
                    imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏标签",
                    tint = if (isFavorite) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                    modifier = Modifier.padding(AppSpacingTokens.Micro)
                )
            }
        }
    }
}
