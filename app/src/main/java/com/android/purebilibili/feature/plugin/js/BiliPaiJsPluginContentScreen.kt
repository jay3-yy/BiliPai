package com.android.purebilibili.feature.plugin.js

import com.android.purebilibili.feature.home.rememberFeedGridColumnPreference
import com.android.purebilibili.feature.home.effectiveGridColumnCountForWindow
import com.android.purebilibili.feature.home.effectiveGridColumnCountCompact
import com.android.purebilibili.feature.home.gridColumnPinchEnabled
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.android.purebilibili.core.ui.components.AppLazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import com.android.purebilibili.core.ui.rememberCompatibleStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.core.plugin.js.BiliPaiJsLayouts
import com.android.purebilibili.core.plugin.js.BiliPaiJsMediaItem
import com.android.purebilibili.core.plugin.js.BiliPaiJsModule
import com.android.purebilibili.core.plugin.js.BiliPaiJsParam
import com.android.purebilibili.core.plugin.js.BiliPaiJsParamTypes
import com.android.purebilibili.core.plugin.js.BiliPaiJsPluginInstallStore
import com.android.purebilibili.core.plugin.js.BiliPaiJsRuntime
import com.android.purebilibili.core.plugin.js.ExternalMediaLaunchStore
import com.android.purebilibili.core.plugin.js.InstalledBiliPaiJsPlugin
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.imagePreviewSourceBounds
import com.android.purebilibili.feature.dynamic.components.rememberImagePreviewSourceRect
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import com.android.purebilibili.core.plugin.js.hasSelectableOptions
import com.android.purebilibili.core.plugin.js.isHostDriven
import com.android.purebilibili.core.plugin.js.isPlayable
import com.android.purebilibili.core.plugin.js.resolveBiliPaiJsMediaImageCandidates
import com.android.purebilibili.core.plugin.js.resolveBiliPaiJsMediaStreams
import com.android.purebilibili.core.plugin.js.supportsPagination
import com.android.purebilibili.core.ui.rememberAppBackIcon
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 详情导航栈中的一层：`link` 原样回传给插件详情函数。 */
data class BiliPaiJsDetailEntry(
    val link: String,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliPaiJsPluginContentScreen(
    pluginId: String,
    onBack: () -> Unit,
    onPlayMedia: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { BiliPaiJsPluginInstallStore.createDefault(context) }
    val runtime = remember(context) { BiliPaiJsRuntime(context) }
    val scope = rememberCoroutineScope()
    var installed by remember(pluginId) {
        mutableStateOf(store.listInstalledPlugins().firstOrNull { it.manifest.id == pluginId })
    }
    var selectedModule by remember(installed) {
        mutableStateOf(installed?.manifest?.modules?.firstOrNull())
    }
    val preset = remember(pluginId, selectedModule) {
        val module = selectedModule ?: return@remember null
        BiliPaiJsLayoutPresetStore.readPreset(context, pluginId, resolveBiliPaiJsModuleId(module))
    }
    val paramValues = remember(pluginId, selectedModule) {
        mutableStateMapOf<String, String>().apply {
            val module = selectedModule ?: return@apply
            val presetParams = preset?.params.orEmpty()
            putAll(
                resolveBiliPaiJsInitialParamValues(
                    params = module.params,
                    savedValues = readBiliPaiJsParamValues(context, pluginId, module),
                    presetValues = presetParams
                )
            )
        }
    }
    val effectiveLayout = remember(pluginId, selectedModule, preset) {
        preset?.layout ?: selectedModule?.layout ?: BiliPaiJsLayouts.LIST
    }
    var items by remember { mutableStateOf<List<BiliPaiJsMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var page by remember(selectedModule) {
        mutableIntStateOf(resolveBiliPaiJsInitialPage(selectedModule))
    }
    var detailStack by remember(pluginId) { mutableStateOf<List<BiliPaiJsDetailEntry>>(emptyList()) }
    var detailItems by remember(pluginId) { mutableStateOf<List<BiliPaiJsMediaItem>>(emptyList()) }
    var isDetailLoading by remember(pluginId) { mutableStateOf(false) }
    var detailError by remember(pluginId) { mutableStateOf<String?>(null) }
    val homeSettings by SettingsManager.getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings())
    val windowSizeClass = LocalWindowSizeClass.current
    val gridColumnPreference = androidx.compose.runtime.key(pluginId) {
        rememberFeedGridColumnPreference(
            settings = homeSettings,
            widthSizeClass = windowSizeClass.widthSizeClass,
            defaultColumns = com.android.purebilibili.feature.home.resolveHomeFeedStoredColumnCount(
                windowSizeClass.widthSizeClass,
                homeSettings.effectiveGridColumnCountCompact,
                homeSettings.effectiveGridColumnCountForWindow(windowSizeClass),
            ).takeIf { it > 0 } ?: BILI_PAI_JS_GRID_COLUMNS,
            bounds = BILI_PAI_JS_PINCH_COLUMN_BOUNDS,
            minimumColumns = BILI_PAI_JS_PINCH_COLUMN_BOUNDS.first,
        )
    }
    var imagePreview by remember { mutableStateOf<BiliPaiJsImagePreviewRequest?>(null) }

    imagePreview?.let { request ->
        ImagePreviewDialog(
            images = request.images,
            initialIndex = request.initialIndex,
            sourceRect = request.sourceRect,
            onDismiss = { imagePreview = null }
        )
    }

    fun loadModule(module: BiliPaiJsModule) {
        val current = installed ?: return
        selectedModule = module
        persistBiliPaiJsParamValues(context, current.manifest.id, module, paramValues)
        page = resolveBiliPaiJsInitialPage(module)
        isLoading = true
        errorMessage = null
        hasMore = false
        detailStack = emptyList()
        detailItems = emptyList()
        detailError = null
        scope.launch {
            val result = runtime.loadModuleItems(
                installed = current,
                module = module,
                paramsJson = buildBiliPaiJsModuleParamsJson(module, paramValues, page = page, loadedCount = 0)
            )
            isLoading = false
            result.onSuccess { loadedItems ->
                items = loadedItems
                hasMore = module.supportsPagination && loadedItems.isNotEmpty()
                errorMessage = if (loadedItems.isEmpty()) "插件没有返回媒体内容" else null
            }.onFailure { error ->
                items = emptyList()
                errorMessage = error.message ?: "插件内容加载失败"
            }
        }
    }

    fun loadMoreItems() {
        val current = installed ?: return
        val module = selectedModule ?: return
        if (isLoadingMore || isLoading || !hasMore) return
        isLoadingMore = true
        val nextPage = page + 1
        scope.launch {
            val result = runtime.loadModuleItems(
                installed = current,
                module = module,
                paramsJson = buildBiliPaiJsModuleParamsJson(
                    module,
                    paramValues,
                    page = nextPage,
                    loadedCount = items.size
                )
            )
            isLoadingMore = false
            result.onSuccess { loadedItems ->
                if (loadedItems.isEmpty()) {
                    hasMore = false
                } else {
                    page = nextPage
                    items = items + loadedItems
                }
            }.onFailure { error ->
                hasMore = false
                errorMessage = error.message ?: "加载更多失败"
            }
        }
    }

    LaunchedEffect(selectedModule, installed?.enabled) {
        val module = selectedModule
        val current = installed
        if (module != null && current?.enabled == true) {
            loadModule(module)
        }
    }

    LaunchedEffect(detailStack.lastOrNull(), installed?.enabled) {
        val entry = detailStack.lastOrNull() ?: return@LaunchedEffect
        val current = installed ?: return@LaunchedEffect
        if (current.enabled != true) return@LaunchedEffect
        isDetailLoading = true
        detailError = null
        val result = runtime.loadDetailItems(installed = current, link = entry.link)
        isDetailLoading = false
        result.onSuccess { loadedItems ->
            detailItems = loadedItems
            if (loadedItems.isEmpty()) detailError = "详情没有返回内容"
        }.onFailure { error ->
            detailItems = emptyList()
            detailError = error.message ?: "详情加载失败"
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = installed?.manifest?.title ?: "JS 插件内容",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val current = installed
        when {
            current == null -> EmptyState(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                text = "插件不存在或已删除"
            )
            !current.enabled -> EmptyState(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                text = "插件已禁用，请先在插件中心启用"
            )
            else -> PluginContent(
                modifier = Modifier.padding(padding),
                installed = current,
                selectedModule = selectedModule,
                paramValues = paramValues,
                mediaItems = items,
                isLoading = isLoading,
                isLoadingMore = isLoadingMore,
                canLoadMore = hasMore,
                errorMessage = errorMessage,
                detailStack = detailStack,
                detailItems = detailItems,
                isDetailLoading = isDetailLoading,
                detailError = detailError,
                detailSupported = current.manifest.supportsDetail,
                isGridLayout = effectiveLayout == BiliPaiJsLayouts.GRID,
                gridColumns = gridColumnPreference.columns,
                pinchZoomEnabled = homeSettings.gridColumnPinchEnabled,
                onGridColumnsChange = gridColumnPreference.onChange,
                onGridColumnsEnd = gridColumnPreference.onGestureEnd,
                onPreviewImage = { images, index, rect ->
                    imagePreview = resolveBiliPaiJsImagePreviewRequest(images, index, rect)
                },
                onShareLayout = { selectedModule?.let { module -> shareLayoutPreset(context, current, module, paramValues, effectiveLayout) } },
                onSelectModule = { module -> loadModule(module) },
                onReload = { selectedModule?.let(::loadModule) },
                onLoadMore = ::loadMoreItems,
                onOpenLink = { entry -> detailStack = detailStack + entry },
                onBackFromDetail = { detailStack = detailStack.dropLast(1) },
                onPlayItem = { item ->
                    val streams = resolveBiliPaiJsMediaStreams(item)
                    if (streams.isNotEmpty()) {
                        val danmakuPluginId = if (
                            current.manifest.supportsDanmaku &&
                            PluginCapability.DANMAKU_STREAM in current.grantedCapabilities
                        ) {
                            current.manifest.id
                        } else {
                            null
                        }
                        onPlayMedia(
                            ExternalMediaLaunchStore.put(
                                title = item.title,
                                coverUrl = item.coverUrl,
                                streams = streams,
                                danmakuPluginId = danmakuPluginId
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun PluginContent(
    modifier: Modifier,
    installed: InstalledBiliPaiJsPlugin,
    selectedModule: BiliPaiJsModule?,
    paramValues: MutableMap<String, String>,
    mediaItems: List<BiliPaiJsMediaItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    errorMessage: String?,
    detailStack: List<BiliPaiJsDetailEntry>,
    detailItems: List<BiliPaiJsMediaItem>,
    isDetailLoading: Boolean,
    detailError: String?,
    detailSupported: Boolean,
    isGridLayout: Boolean,
    gridColumns: Int,
    pinchZoomEnabled: Boolean,
    onGridColumnsChange: (Int) -> Unit,
    onGridColumnsEnd: (Int) -> Unit,
    onPreviewImage: (List<String>, Int, Rect?) -> Unit,
    onShareLayout: () -> Unit,
    onSelectModule: (BiliPaiJsModule) -> Unit,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLink: (BiliPaiJsDetailEntry) -> Unit,
    onBackFromDetail: () -> Unit,
    onPlayItem: (BiliPaiJsMediaItem) -> Unit
) {
    val listState = rememberCompatibleStaggeredGridState()
    val inDetailView = detailStack.isNotEmpty()
    val shouldLoadMore by remember(canLoadMore, isLoadingMore, inDetailView) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            canLoadMore && !isLoadingMore && !inDetailView && layoutInfo.totalItemsCount > 0 &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    AppLazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (isGridLayout && !inDetailView) gridColumns.coerceAtLeast(1) else 1),
        modifier = modifier.homeFeedPinchZoom(
            enabled = pinchZoomEnabled && isGridLayout && !inDetailView,
            currentColumns = gridColumns,
            bounds = BILI_PAI_JS_PINCH_COLUMN_BOUNDS,
            onColumnsChange = onGridColumnsChange,
            onGestureEnd = onGridColumnsEnd,
        ),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            AppText(
                text = installed.manifest.description.ifBlank { "${installed.manifest.id} · v${installed.manifest.version}" },
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText()
            )
        }
        if (inDetailView) {
            item(key = "js_detail_header", span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppAssistChip(
                        onClick = onBackFromDetail,
                        label = { AppText(if (detailStack.size > 1) "← 返回上一层" else "← 返回列表") }
                    )
                    AppText(
                        text = detailStack.last().title,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppContentColors.primaryText()
                    )
                }
            }
        } else {
            item(span = StaggeredGridItemSpan.FullLine) {
                val modules = installed.manifest.modules
                if (modules.isNotEmpty() && selectedModule != null) {
                    AppThemeAdaptiveTabRow(
                        // Glass indicator fills the measured slot; native geometry keeps its defaults.
                        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                        options = modules.map { module -> AppSegmentOption(module, module.title) },
                        selectedValue = selectedModule,
                        onSelectionChange = onSelectModule,
                        scrollable = modules.size > 4,
                        dragSelectionEnabled = modules.size > 1,
                        tapPressRefractionEnabled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AppAssistChip(onClick = onShareLayout, label = { AppText("分享布局（.bplayout）") })
                }
            }
            selectedModule?.params?.filterNot { it.isHostDriven }?.takeIf { it.isNotEmpty() }?.let { params ->
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        params.forEach { param ->
                            if (param.hasSelectableOptions) {
                                BiliPaiJsParamOptionChips(
                                    param = param,
                                    selectedValue = paramValues[param.name] ?: param.defaultValue,
                                    onSelect = { value ->
                                        paramValues[param.name] = value
                                        onReload()
                                    }
                                )
                            } else {
                                AppOutlinedTextField(
                                    value = paramValues[param.name] ?: param.defaultValue,
                                    onValueChange = { paramValues[param.name] = it },
                                    label = { AppText(param.title) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                        AppAssistChip(onClick = onReload, label = { AppText("重新加载") })
                    }
                }
            }
        }
        val activeLoading = if (inDetailView) isDetailLoading else isLoading
        if (activeLoading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdaptiveLoadingIndicator(size = 22.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    AppText(if (inDetailView) "正在加载详情" else "正在加载插件内容")
                }
            }
        }
        val activeError = if (inDetailView) detailError else errorMessage
        activeError?.let { message ->
            item(span = StaggeredGridItemSpan.FullLine) {
                AppText(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.error()
                )
            }
        }
        val activeItems = if (inDetailView) detailItems else mediaItems
        val flattenedItems = flattenMediaItems(activeItems)
        if (isGridLayout && !inDetailView) {
            val posterKeys = flattenedItems.mapIndexed { index, item -> "js_grid_${index}_${item.id}" }
            val columns = gridColumns.coerceAtLeast(1)
            com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(flattenedItems.size, columns)
                .forEach { row ->
                    item(key = "js_poster_row_${posterKeys[row.first]}", span = StaggeredGridItemSpan.FullLine) {
                        com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                            indices = row,
                            itemKeys = posterKeys,
                            columns = columns,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { index, cardModifier ->
                            val rowItem = flattenedItems[index]
                            BiliPaiJsPosterCard(
                                item = rowItem,
                                modifier = cardModifier,
                                onPlay = { onPlayItem(rowItem) },
                                onOpen = {
                                    rowItem.link?.takeIf { it.isNotBlank() }?.let { link ->
                                        onOpenLink(BiliPaiJsDetailEntry(link = link, title = rowItem.title))
                                    }
                                },
                                onPreviewImage = onPreviewImage,
                            )
                        }
                    }
                }
        } else {
            items(
                count = flattenedItems.size,
                key = { index ->
                    val prefix = if (inDetailView) "js_detail" else "js_media"
                    "$prefix${index}_${flattenedItems[index].id}"
                }
            ) { index ->
                val item = flattenedItems[index]
                BiliPaiJsMediaItemRow(
                    item = item,
                    detailSupported = detailSupported,
                    onPlay = { onPlayItem(item) },
                    onOpen = {
                        item.link?.takeIf { it.isNotBlank() }?.let { link ->
                            onOpenLink(BiliPaiJsDetailEntry(link = link, title = item.title))
                        }
                    },
                    onPreviewImage = onPreviewImage
                )
            }
        }
        if (!inDetailView && (canLoadMore || isLoadingMore)) {
            item(key = "js_media_load_more", span = StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdaptiveLoadingIndicator(size = 18.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    AppText("正在加载更多")
                }
            }
        }
    }
}

private const val BILI_PAI_JS_GRID_COLUMNS = 2

/** 插件网格双指缩放的列数范围；最少 2 列保证海报网格形态。 */
private val BILI_PAI_JS_PINCH_COLUMN_BOUNDS = 2..4

/** 长按查看大图的请求：候选图列表、当前索引和来源卡片矩形（窗口坐标）。 */
private data class BiliPaiJsImagePreviewRequest(
    val images: List<String>,
    val initialIndex: Int,
    val sourceRect: Rect?
)

private fun resolveBiliPaiJsImagePreviewRequest(
    images: List<String>,
    index: Int,
    sourceRect: Rect?
): BiliPaiJsImagePreviewRequest? {
    val candidates = images.filter { it.isNotBlank() }
    if (candidates.isEmpty()) return null
    return BiliPaiJsImagePreviewRequest(
        images = candidates,
        initialIndex = index.coerceIn(0, candidates.lastIndex),
        sourceRect = sourceRect
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BiliPaiJsPosterCard(
    item: BiliPaiJsMediaItem,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onPreviewImage: (List<String>, Int, Rect?) -> Unit
) {
    val imageCandidates = remember(item) { resolveBiliPaiJsMediaImageCandidates(item) }
    var imageCandidateIndex by remember(imageCandidates) { mutableIntStateOf(0) }
    val imageUrl = imageCandidates.getOrNull(imageCandidateIndex)
    val imageState = when {
        imageCandidates.isEmpty() -> PluginMediaImageState.NO_IMAGE
        imageUrl == null -> PluginMediaImageState.LOAD_FAILED
        else -> PluginMediaImageState.LOADING
    }
    val canOpenDetail = !item.isPlayable && !item.link.isNullOrBlank()
    val sourceRectState = rememberImagePreviewSourceRect()
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .combinedClickable(
                enabled = item.isPlayable || canOpenDetail || imageCandidates.isNotEmpty(),
                onClick = { if (item.isPlayable) onPlay() else onOpen() },
                onLongClick = {
                    if (imageCandidates.isNotEmpty()) {
                        val safeIndex = imageCandidateIndex.coerceIn(0, imageCandidates.lastIndex)
                        onPreviewImage(imageCandidates, safeIndex, sourceRectState.value)
                    }
                }
            ),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 1.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .imagePreviewSourceBounds(sourceRectState)
            ) {
                PluginMediaImagePlaceholder(
                    title = item.title,
                    state = imageState,
                    modifier = Modifier.fillMaxSize()
                )
                imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { imageCandidateIndex += 1 }
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AppText(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppContentColors.primaryText(),
                    maxLines = 1
                )
                if (item.description.isNotBlank()) {
                    AppText(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun shareLayoutPreset(
    context: android.content.Context,
    installed: InstalledBiliPaiJsPlugin,
    module: BiliPaiJsModule,
    paramValues: Map<String, String>,
    effectiveLayout: String
) {
    val shareableParams = module.params
        .filterNot { it.isHostDriven }
        .associate { param -> param.name to (paramValues[param.name] ?: param.defaultValue) }
    val preset = BiliPaiJsLayoutPreset(
        name = "${installed.manifest.title} · ${module.title}",
        pluginId = installed.manifest.id,
        moduleId = resolveBiliPaiJsModuleId(module),
        layout = effectiveLayout,
        params = shareableParams
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "BiliPai 布局：${preset.name}")
        putExtra(android.content.Intent.EXTRA_TEXT, BiliPaiJsLayoutPresetStore.encodePreset(preset))
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享布局"))
}

@Composable
private fun BiliPaiJsParamOptionChips(
    param: BiliPaiJsParam,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(
            text = param.title,
            style = MaterialTheme.typography.labelMedium,
            color = AppContentColors.secondaryText()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            param.options.forEach { option ->
                AppFilterChip(
                    selected = selectedValue == option.value,
                    onClick = { onSelect(option.value) },
                    label = { AppText(option.title) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BiliPaiJsMediaItemRow(
    item: BiliPaiJsMediaItem,
    detailSupported: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onPreviewImage: (List<String>, Int, Rect?) -> Unit
) {
    val streams = remember(item) { resolveBiliPaiJsMediaStreams(item) }
    val imageCandidates = remember(item) { resolveBiliPaiJsMediaImageCandidates(item) }
    var imageCandidateIndex by remember(imageCandidates) { mutableIntStateOf(0) }
    val imageUrl = imageCandidates.getOrNull(imageCandidateIndex)
    val imageState = when {
        imageCandidates.isEmpty() -> PluginMediaImageState.NO_IMAGE
        imageUrl == null -> PluginMediaImageState.LOAD_FAILED
        else -> PluginMediaImageState.LOADING
    }
    val canOpenDetail = detailSupported && !item.isPlayable && !item.link.isNullOrBlank()
    val actionLabel = when {
        item.isPlayable -> "播放"
        canOpenDetail -> "打开"
        else -> null
    }
    val sourceRectState = rememberImagePreviewSourceRect()
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Field))
            .combinedClickable(
                enabled = item.isPlayable || canOpenDetail || imageCandidates.isNotEmpty(),
                onClick = { if (item.isPlayable) onPlay() else onOpen() },
                onLongClick = {
                    if (imageCandidates.isNotEmpty()) {
                        val safeIndex = imageCandidateIndex.coerceIn(0, imageCandidates.lastIndex)
                        onPreviewImage(imageCandidates, safeIndex, sourceRectState.value)
                    }
                }
            ),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl.isNullOrBlank()) {
                PluginMediaImagePlaceholder(
                    title = item.title,
                    state = imageState,
                    modifier = Modifier
                        .size(width = 96.dp, height = 56.dp)
                        .imagePreviewSourceBounds(sourceRectState)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 56.dp)
                        .imagePreviewSourceBounds(sourceRectState)
                ) {
                    PluginMediaImagePlaceholder(
                        title = item.title,
                        state = PluginMediaImageState.LOADING,
                        modifier = Modifier.fillMaxSize()
                    )
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(AppShapes.container(ContainerLevel.Chip)),
                        contentScale = ContentScale.Crop,
                        onError = {
                            imageCandidateIndex += 1
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppContentColors.primaryText()
                )
                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 2
                    )
                }
                if (streams.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AppText(
                        text = "线路 ${streams.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.accent()
                    )
                }
            }
            actionLabel?.let { label ->
                AppText(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.accent()
                )
            }
        }
    }
}

@Composable
private fun PluginMediaImagePlaceholder(title: String) {
    PluginMediaImagePlaceholder(title = title, state = PluginMediaImageState.LOADING, modifier = Modifier)
}

@Composable
private fun PluginMediaImagePlaceholder(
    title: String,
    state: PluginMediaImageState,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Chip)),
        contentAlignment = Alignment.Center
    ) {
        AppSurface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {}
        AppText(
            text = when (state) {
                PluginMediaImageState.NO_IMAGE -> "无图"
                PluginMediaImageState.LOAD_FAILED -> "失败"
                PluginMediaImageState.LOADING -> title.take(2).ifBlank { "TV" }
            },
            style = MaterialTheme.typography.labelMedium,
            color = AppContentColors.secondaryText()
        )
    }
}

private enum class PluginMediaImageState {
    LOADING,
    NO_IMAGE,
    LOAD_FAILED
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    text: String
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText()
        )
    }
}

private fun buildBiliPaiJsModuleParamsJson(
    module: BiliPaiJsModule,
    values: Map<String, String>,
    page: Int,
    loadedCount: Int
): String {
    return buildJsonObject {
        module.params.forEach { param ->
            when (param.type) {
                BiliPaiJsParamTypes.PAGE -> put(param.name, JsonPrimitive(page))
                BiliPaiJsParamTypes.OFFSET -> put(param.name, JsonPrimitive(loadedCount))
                else -> put(param.name, JsonPrimitive(values[param.name] ?: param.defaultValue))
            }
        }
    }.toString()
}

/** 模块没有声明 page 参数时页码从 0 起，声明了则默认从 1 起，更符合常见分页 API。 */
internal fun resolveBiliPaiJsInitialPage(module: BiliPaiJsModule?): Int {
    val hasPageParam = module?.params?.any { it.type == BiliPaiJsParamTypes.PAGE } == true
    return if (hasPageParam) 1 else 0
}

internal fun resolveBiliPaiJsInitialParamValues(
    params: List<BiliPaiJsParam>,
    savedValues: Map<String, String>,
    presetValues: Map<String, String> = emptyMap()
): Map<String, String> {
    return params.associate { param ->
        param.name to (
            savedValues[param.name]
                ?: presetValues[param.name]
                ?: param.defaultValue
            )
    }
}

internal fun resolveBiliPaiJsModuleId(module: BiliPaiJsModule): String {
    return module.id.ifBlank { module.functionName }
}

internal fun buildBiliPaiJsParamPreferenceKey(
    pluginId: String,
    moduleId: String,
    paramName: String
): String {
    return "js_param_${safePreferencePart(pluginId)}_${safePreferencePart(moduleId)}_${safePreferencePart(paramName)}"
}

private fun readBiliPaiJsParamValues(
    context: android.content.Context,
    pluginId: String,
    module: BiliPaiJsModule
): Map<String, String> {
    val prefs = context.getSharedPreferences("bilipai_js_plugin_params", android.content.Context.MODE_PRIVATE)
    val moduleId = module.id.ifBlank { module.functionName }
    return module.params.associate { param ->
        val key = buildBiliPaiJsParamPreferenceKey(pluginId, moduleId, param.name)
        param.name to prefs.getString(key, param.defaultValue).orEmpty()
    }
}

private fun persistBiliPaiJsParamValues(
    context: android.content.Context,
    pluginId: String,
    module: BiliPaiJsModule,
    values: Map<String, String>
) {
    val prefs = context.getSharedPreferences("bilipai_js_plugin_params", android.content.Context.MODE_PRIVATE)
    val moduleId = module.id.ifBlank { module.functionName }
    prefs.edit().apply {
        module.params.filterNot { it.isHostDriven }.forEach { param ->
            putString(
                buildBiliPaiJsParamPreferenceKey(pluginId, moduleId, param.name),
                values[param.name] ?: param.defaultValue
            )
        }
    }.apply()
}

private fun safePreferencePart(value: String): String {
    return value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}

private fun flattenMediaItems(items: List<BiliPaiJsMediaItem>): List<BiliPaiJsMediaItem> {
    return buildList {
        items.forEach { item ->
            add(item)
            addAll(item.childItems)
        }
    }
}

internal fun resolveBiliPaiJsMediaItemLazyKey(index: Int, item: BiliPaiJsMediaItem): String {
    return "js_media_${index}_${item.id}"
}
