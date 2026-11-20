package com.android.purebilibili.feature.live

import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.core.ui.AppSurfaceTokens

import com.android.purebilibili.core.ui.AppContentColors

import android.widget.Toast
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
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
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
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
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.NavigationControlMotionGroup
import com.android.purebilibili.navigation3.rememberSystemWindowNavigationControl
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun LiveAreaScreen(
    onBack: () -> Unit,
    onAreaClick: (Int, Int, String) -> Unit,
    onAreaContainerClick: ((com.android.purebilibili.navigation3.BiliPaiNavKey.LiveAreaDetail) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberLiveChromePalette()
    var areas by remember { mutableStateOf<List<LiveAreaParent>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<LiveFavoriteTagEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favoritesError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val accountIdentity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val canSync = (accountIdentity.mid ?: 0L) > 0L && !TokenManager.accessTokenCache.isNullOrBlank()
    val pager = rememberPagerState(pageCount = { areas.size.coerceAtLeast(1) })
    val pagerDragHeld by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(reload, accountIdentity) {
        val owner = accountIdentity
        saveJob?.cancel()
        editing = false
        saving = false
        loading = true
        error = null
        favoritesError = null
        favorites = SettingsManager.getLiveFavoriteTags(context, owner.mid).first()
        if (canSync) {
            LiveRepository.getLiveFavoriteTags().onSuccess {
                favorites = it
                SettingsManager.setLiveFavoriteTags(context, it, owner.mid)
            }.onFailure { favoritesError = it.message ?: "常用标签同步失败" }
        }
        LiveRepository.getLiveAreaIndex().onSuccess { areas = it }.onFailure { error = it.message ?: "加载标签失败" }
        loading = false
    }
    fun finishEditing() {
        if (saving) return
        saving = true
        val snapshot = favorites
        val owner = accountIdentity
        saveJob = scope.launch {
            try {
                val result = if (canSync) LiveRepository.setLiveFavoriteTags(snapshot) else Result.success(Unit)
                if (owner != TokenManager.accountIdentity.value) return@launch
                result.onSuccess {
                    SettingsManager.setLiveFavoriteTags(context, snapshot, owner.mid)
                    editing = false
                    Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(context, it.message ?: "保存失败，请重试", Toast.LENGTH_SHORT).show() }
            } finally { if (owner == TokenManager.accountIdentity.value) saving = false }
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
                    AppTextButton(enabled = !loading && !saving && favoritesError == null, onClick = { if (editing) finishEditing() else editing = true }) {
                        AppText(if (saving) "保存中…" else if (editing) "完成" else "编辑")
                    }
                },
            )
        },
    ) { padding ->
        AdaptivePullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { if (!saving && !editing) reload++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        Column(Modifier.fillMaxSize()) {
            if (!loading && error == null) {
                if (favoritesError != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText(favoritesError.orEmpty(), modifier = Modifier.weight(1f), color = AppContentColors.secondaryText())
                        AppTextButton(onClick = { reload++ }) { AppText("重试常用标签") }
                    }
                }
                key(accountIdentity) {
                LiveFavoriteTagsPanel(
                    favoriteTags = favorites,
                    isEditing = editing,
                    enabled = !saving && favoritesError == null,
                    onStartEditing = { if (favoritesError == null) editing = true },
                    onTagClick = { onAreaClick(it.parentAreaId, it.areaId, it.title) },
                    onRemove = { tag -> favorites = favorites.filterNot { it.areaId == tag.areaId && it.parentAreaId == tag.parentAreaId } },
                    onMove = { from, to -> favorites = favorites.toMutableList().apply { add(to, removeAt(from)) } },
                )
                }
                AppThemeAdaptiveTabRow(
                    // 玻璃随标签长短调整胶囊；原生分支保留自身尺寸和间距。
                    liquidGlassAdaptIndicatorToLabel = true,
                    liquidGlassAnimateLabelWidthChanges = true,
                    role = AppTabRowRole.SECONDARY,
                    options = areas.mapIndexed { index, parent -> AppSegmentOption(index, parent.name) },
                    selectedValue = pager.tabSelectedPage,
                    onSelectionChange = { target -> scope.launch { animatePagerSelection(pager, target) } },
                    scrollable = true,
                    centerContent = false,
                    indicatorPositionProvider = { pager.tabIndicatorPosition },
                    isScrollInProgressProvider = { pager.isScrollInProgress },
                    isPagerDraggingProvider = { pagerDragHeld },
                    pagerTargetPositionProvider = { pager.targetPage.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                loading -> ContentCategoryGridSkeleton(columns = (LocalWindowSizeClass.current.widthDp.value / 110).toInt().coerceAtLeast(1), modifier = Modifier.weight(1f).fillMaxWidth())
                error != null -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(error.orEmpty(), color = palette.secondaryText)
                    AppTextButton(onClick = { reload++ }) { AppText("重新加载") }
                }
                else -> AppHorizontalPager(
                    state = pager,
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f)
                        .verticalPriorityHorizontalPagerSwipe(pager),
                ) { page ->
                    areas.getOrNull(page)?.let { parent ->
                        LazyVerticalGrid(
                            columns = LiveRoomGridCells(100.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = maxOf(LocalBottomBarContentPadding.current, 100.dp)),
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
                                    parentAreaId = tag.parentAreaId,
                                    onAreaContainerClick = onAreaContainerClick,
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
) = NavigationControlMotionGroup {
    val latestTags by rememberUpdatedState(favoriteTags)
    val latestMove by rememberUpdatedState(onMove)
    val latestStart by rememberUpdatedState(onStartEditing)
    val positions = remember { mutableStateMapOf<Int, Rect>() }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val tagShape = RoundedCornerShape(4.dp)
    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText("我的常用标签", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            AppText(if (isEditing) "长按拖动排序，点击移除" else "点击进入标签", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            favoriteTags.forEach { tag ->
                key(tag.areaId) {
                    val openTag = if (enabled && !isEditing) {
                        rememberSystemWindowNavigationControl(
                            target = BiliPaiNavKey.LiveAreaDetail(tag.parentAreaId, tag.areaId, tag.title),
                            shape = tagShape,
                            onClick = { onTagClick(tag) },
                        )
                    } else null
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
                            onClick = openTag?.onClick ?: { if (isEditing) onRemove(tag) else onTagClick(tag) },
                            enabled = enabled,
                            shape = tagShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .graphicsLayer(translationX = offset.x, translationY = offset.y)
                                .then(openTag?.modifier ?: Modifier),
                        ) {
                            AppText(tag.title + if (isEditing) " −" else "", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
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
    parentAreaId: Int,
    onAreaContainerClick: ((com.android.purebilibili.navigation3.BiliPaiNavKey.LiveAreaDetail) -> Unit)?,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    // 每个图标生成自己的导航会话，捕获和实际入栈必须使用同一个目标身份。
    val target = remember(parentAreaId, child.id, child.name) {
        com.android.purebilibili.navigation3.BiliPaiNavKey.LiveAreaDetail(
            parentAreaId = parentAreaId,
            areaId = child.id.toIntOrNull() ?: 0,
            title = child.name,
            openId = android.os.SystemClock.elapsedRealtimeNanos(),
        )
    }
    val transition = rememberLiveCardContainerTransition(
        roomId = 0,
        title = child.name,
        hostName = "",
        color = AppSurfaceTokens.surfaceContainer(),
        cornerRadius = 8.dp,
        enabled = !isEditing && onAreaContainerClick != null,
        includeAvatarCompanions = true,
        containerTarget = target,
        onClick = { if (onAreaContainerClick != null) onAreaContainerClick(target) else onClick() },
    )
    Box(
        modifier = Modifier
            .height(80.dp)
            .clickable(onClick = if (isEditing || onAreaContainerClick == null) onClick else transition.onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LiveAreaIcon(
                imageUrl = child.pic,
                title = child.name,
                modifier = Modifier.size(45.dp).then(transition.modifier)
            )
            Spacer(Modifier.height(AppSpacingTokens.ExtraSmall))
            AppText(
                text = child.name,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = transition.companionModifier,
            )
        }
        if (isEditing && child.id != "0") {
            AppSurface(
                shape = CircleShape,
                color = if (isFavorite) colorScheme.surfaceVariant else colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .size(17.dp)
            ) {
                AppIcon(
                    imageVector = if (isFavorite) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏标签",
                    tint = if (isFavorite) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                    modifier = Modifier.padding(AppSpacingTokens.Micro)
                )
            }
        }
    }
}
