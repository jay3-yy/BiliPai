@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.settings.screen

import com.android.purebilibili.R
import com.android.purebilibili.feature.settings.SettingsFunctionIcon
import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade

import com.android.purebilibili.core.plugin.skin.SkinCatalog
import com.android.purebilibili.core.plugin.skin.SkinCatalogEntry
import com.android.purebilibili.core.plugin.skin.SkinCatalogLoader
import com.android.purebilibili.core.plugin.skin.UiSkinImportPackageResolver
import com.android.purebilibili.core.plugin.skin.UiSkinInstallStore
import com.android.purebilibili.core.plugin.skin.UiSkinPackagePreview
import com.android.purebilibili.core.plugin.skin.UiSkinSelection
import com.android.purebilibili.core.plugin.skin.UiSkinSettingsStore
import com.android.purebilibili.core.plugin.skin.normalizeSkinPackageUrl
import com.android.purebilibili.feature.settings.UiSkinCompositionPreview
import com.android.purebilibili.feature.settings.UiSkinCompositionPreviewData
import com.android.purebilibili.feature.settings.downloadUiSkinRemotePackage
import com.android.purebilibili.feature.settings.parsePreviewColor
import com.android.purebilibili.feature.settings.rememberSettingsVector
import com.android.purebilibili.feature.settings.resolveUiSkinImportErrorMessage

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 浏览页 UI 状态（不可变快照）。screen composable 消费此状态 + lambda 事件。
 */
data class SkinCatalogUiState(
    val loading: Boolean = true,
    val catalog: SkinCatalog = SkinCatalog(catalogVersion = 0, sourceRepo = "", sourceBranch = ""),
    val previewing: SkinCatalogEntry? = null,
    val previewData: UiSkinCompositionPreviewData? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
    val installing: Boolean = false,
    val installError: String? = null,
    val installed: Boolean = false,
    val searchQuery: String = ""
) {
    val filteredThemes: List<SkinCatalogEntry>
        get() {
            if (searchQuery.isBlank()) return catalog.themes
            val q = searchQuery.trim()
            return catalog.themes.filter {
                it.displayName.contains(q, ignoreCase = true) || it.id.contains(q, ignoreCase = true)
            }
        }
}

/** 预览成功后缓存的待安装包数据，供一键导入复用，避免重复下载。 */
private data class PendingSkinInstall(
    val packageBytes: ByteArray,
    val preview: UiSkinPackagePreview
)

/**
 * 装扮目录浏览页。从 Rovniced/bilibili-skin 冻结快照索引展示主题列表，
 * 点击进入真实合成预览，确认后一键下载导入。
 */
@Composable
fun SkinCatalogScreen(
    onBack: () -> Unit,
    onInstalled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { SkinCatalogStateHolder(context) }
    val uiSkinStore = stateHolder.uiSkinStore
    val state by stateHolder.state.collectAsStateWithLifecycle()

    // 预览：下载主题资源包 → 转换为 .bpskin → 解压预览资源 → 构造合成预览数据
    fun preparePreview(entry: SkinCatalogEntry) {
        scope.launch {
            stateHolder.setPreviewLoading()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val packageUrl = entry.preferredPackageUrl()
                        ?: throw IllegalArgumentException("该主题没有可用的资源包链接")
                    val packageBytes = downloadUiSkinRemotePackage(packageUrl)
                    val themeMetadataBytes = entry.resolvedThemeMetadataUrl()?.let { metadataUrl ->
                        runCatching { downloadUiSkinRemotePackage(metadataUrl) }.getOrNull()
                    }
                    val importPackage = if (themeMetadataBytes != null) {
                        UiSkinImportPackageResolver.resolveBilibiliPackageWithMetadata(
                            packageBytes = packageBytes,
                            themeJsonBytes = themeMetadataBytes,
                        )
                    } else {
                        UiSkinImportPackageResolver.resolve(
                            inputBytes = packageBytes,
                            remotePackageFetcher = ::downloadUiSkinRemotePackage,
                        )
                    }.getOrThrow()
                    val supplementalAssets = downloadCatalogEffectAssets(entry)
                    val mergedPackageBytes = UiSkinImportPackageResolver.mergeSupplementalEffectAssets(
                        packageBytes = importPackage.packageBytes,
                        supplementalAssets = supplementalAssets,
                    ).getOrThrow()
                    val preview = uiSkinStore.previewPackage(mergedPackageBytes).getOrThrow()
                    val previewAssetFiles = uiSkinStore.extractPreviewAssetFiles(
                        preview = preview,
                        packageBytes = mergedPackageBytes
                    ).getOrThrow()
                    stateHolder.cachePendingInstall(mergedPackageBytes, preview)
                    UiSkinCompositionPreviewData(
                        displayName = entry.displayName,
                        manifest = preview.manifest,
                        assetFiles = previewAssetFiles,
                        darkMode = entry.isDark
                    )
                }
            }
            stateHolder.setPreviewResult(result)
        }
    }

    // 一键导入：复用预览阶段已下载并验证的包数据，避免重复网络请求
    fun confirmInstall(entry: SkinCatalogEntry, onInstalled: () -> Unit) {
        val pending = stateHolder.consumePendingInstall()
        if (pending == null) {
            stateHolder.setInstallResult(Result.failure(IllegalArgumentException("请先完成预览")))
            return
        }
        scope.launch {
            stateHolder.setInstalling()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val installed = uiSkinStore.installPreview(pending.preview, pending.packageBytes).getOrThrow()
                    UiSkinSettingsStore.setSelection(
                        context = context,
                        selection = UiSkinSelection(
                            enabled = true,
                            selectedSkinId = installed.skinId,
                            selectedInstallId = installed.installId
                        )
                    )
                    installed
                }
            }
            stateHolder.setInstallResult(result)
            if (result.isSuccess) onInstalled()
        }
    }

    SettingsPageScaffold(
        title = "在线装扮",
        onBack = onBack,
        backContentDescription = "返回插件中心",
        bottomContentPadding = 0.dp,
        scrollHost = SettingsPageScrollHost.External
    ) {
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AppCircularProgressIndicator()
            }
            return@SettingsPageScaffold
        }

        if (state.catalog.themes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = "装扮目录为空或加载失败",
                    color = AppContentColors.secondaryText()
                )
            }
            return@SettingsPageScaffold
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AppLiquidAwareSearchField(
                query = state.searchQuery,
                onQueryChange = stateHolder::setSearchQuery,
                placeholder = "搜索装扮名称",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = LocalSettingsTopContentPadding.current + 8.dp,
                        bottom = 8.dp,
                    )
            )
            if (state.filteredThemes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = "没有找到相关装扮",
                        color = AppContentColors.secondaryText()
                    )
                }
            } else if (com.android.purebilibili.core.theme.LocalAppUiStyle.current == com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3) {
                val carouselState = androidx.compose.material3.carousel.rememberCarouselState { state.filteredThemes.size }
                androidx.compose.material3.carousel.HorizontalUncontainedCarousel(
                    state = carouselState,
                    itemWidth = 240.dp,
                    itemSpacing = 12.dp,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { index ->
                    val entry = state.filteredThemes.getOrNull(index) ?: return@HorizontalUncontainedCarousel
                    SkinCatalogCard(
                        entry = entry,
                        onClick = {
                            stateHolder.openPreview(entry)
                            preparePreview(entry)
                        },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = state.filteredThemes, key = { it.id }) { entry ->
                        SkinCatalogCard(
                            entry = entry,
                            onClick = {
                                stateHolder.openPreview(entry)
                                preparePreview(entry)
                            }
                        )
                    }
                }
            }
        }
    }

    // 真实合成预览对话框
    state.previewing?.let { entry ->
        SkinCatalogPreviewDialog(
            entry = entry,
            previewData = state.previewData,
            loading = state.previewLoading,
            error = state.previewError,
            installing = state.installing,
            installError = state.installError,
            installed = state.installed,
            onDismiss = { stateHolder.closePreview() },
            onInstall = { confirmInstall(entry, onInstalled) }
        )
    }
}

private fun downloadCatalogEffectAssets(entry: SkinCatalogEntry): Map<String, ByteArray> {
    val effects = entry.effectAssets
    if (effects.isEmpty) return emptyMap()
    return buildMap {
        fun download(url: String?, targetPath: String) {
            if (url.isNullOrBlank()) return
            runCatching { downloadUiSkinRemotePackage(normalizeSkinPackageUrl(url)) }
                .getOrNull()
                ?.let { bytes -> put(targetPath, bytes) }
        }
        download(effects.loadingAnimationUrl, "assets/loading.webp")
        download(effects.loadingFrameUrl, "assets/loading_frame.png")
        effects.likeEffectAnimationUrl?.let { url ->
            val path = url.substringBefore('?')
            when {
                path.endsWith(".json", true) -> download(url, "assets/like_effect.json")
                path.endsWith(".webp", true) -> download(url, "assets/like_effect.webp")
                path.endsWith(".png", true) -> download(url, "assets/like_effect.png")
            }
        }
        effects.likeEffectPreviewUrl?.let { url ->
            val extension = if (url.substringBefore('?').endsWith(".png", true)) "png" else "jpg"
            download(url, "assets/like_effect_preview.$extension")
        }
        download(effects.playerProgressIconUrl, "assets/progress_icon.json")
        download(effects.playerProgressDraggingIconUrl, "assets/progress_drag_icon.json")
        download(effects.playerProgressStaticIconUrl, "assets/progress_static_icon.png")
    }
}

@Composable
private fun SkinCatalogCard(
    entry: SkinCatalogEntry,
    onClick: () -> Unit
) {
    AppSurface(
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 预览图（4:3）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.previewUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (entry.officialExample) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Chip),
                        color = AppContentColors.accent(),
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    ) {
                        AppText(
                            text = "官方示例",
                            color = AppContentColors.onAccent(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // 颜色色块预览（右上角）
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    entry.color?.let { ColorChip(it) }
                    entry.tailColor?.let { ColorChip(it) }
                }
                // 能力位标签（左下角）
                if (!entry.capabilities.isEmpty) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Chip),
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    ) {
                        AppText(
                            text = entry.capabilities.labels().take(2).joinToString("·"),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            AppText(
                text = entry.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = AppContentColors.primaryText()
            )
        }
    }
}

@Composable
private fun ColorChip(colorHex: String) {
    val color = parsePreviewColor(colorHex, Color.Transparent)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(AppShapes.container(ContainerLevel.Tag))
            .background(color)
    )
}

@Composable
private fun SkinCatalogPreviewDialog(
    entry: SkinCatalogEntry,
    previewData: UiSkinCompositionPreviewData?,
    loading: Boolean,
    error: String?,
    installing: Boolean,
    installError: String?,
    installed: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    com.android.purebilibili.core.ui.AppAlertDialog(
        onDismissRequest = { if (!installing) onDismiss() },
        icon = { SettingsFunctionIcon(
            rememberSettingsVector(R.drawable.ph_paint_brush_24, R.drawable.ms_brush_fill_24),
            title = entry.displayName,
        ) },
        title = { AppText(entry.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        AppText("正在下载并准备预览…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (error != null) {
                    AppText(error, color = AppContentColors.error(), style = MaterialTheme.typography.bodySmall)
                } else if (previewData != null) {
                    UiSkinCompositionPreview(data = previewData)
                    AppText(
                        text = "完整装扮使用通栏插画和大图标；实际显示会随导航布局调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                    AppText(
                        text = entry.licenseNote
                            ?: "素材来自 B 站官方付费/限定主题存档，仅供本地私用，不得作为社区包分发。",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText()
                    )
                }
                if (installError != null) {
                    AppText(installError, color = AppContentColors.error(), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (installed) {
                AppTextButton(onClick = onDismiss) { AppText("完成") }
            } else {
                AppTextButton(
                    onClick = onInstall,
                    enabled = !loading && !installing && previewData != null && error == null
                ) {
                    AppText(if (installing) "导入中…" else "下载并导入")
                }
            }
        },
        dismissButton = {
            if (!installing && !installed) {
                AppTextButton(onClick = onDismiss) { AppText("取消") }
            }
        }
    )
}

/**
 * 浏览页状态持有者：管理目录加载与同步状态。异步下载/导入由 screen scope 驱动。
 */
private class SkinCatalogStateHolder(context: Context) {
    val uiSkinStore = UiSkinInstallStore.createDefault(context)
    private val _state = MutableStateFlow(SkinCatalogUiState())
    val state: StateFlow<SkinCatalogUiState> = _state.asStateFlow()
    private var pendingInstall: PendingSkinInstall? = null

    init {
        val catalog = SkinCatalogLoader.loadOrDefault(context)
        _state.value = _state.value.copy(loading = false, catalog = catalog)
    }

    fun openPreview(entry: SkinCatalogEntry) {
        _state.value = _state.value.copy(
            previewing = entry,
            previewData = null,
            previewLoading = true,
            previewError = null,
            installed = false,
            installError = null
        )
    }

    fun setPreviewLoading() {
        _state.value = _state.value.copy(previewLoading = true, previewError = null)
    }

    fun setPreviewResult(result: Result<UiSkinCompositionPreviewData>) {
        result.onSuccess { data ->
            _state.value = _state.value.copy(previewLoading = false, previewData = data, previewError = null)
        }.onFailure { e ->
            _state.value = _state.value.copy(
                previewLoading = false,
                previewError = resolveUiSkinImportErrorMessage(e.message)
            )
        }
    }

    fun setInstalling() {
        _state.value = _state.value.copy(installing = true, installError = null, installed = false)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setInstallResult(result: Result<com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage>) {
        result.onSuccess {
            _state.value = _state.value.copy(installing = false, installed = true, installError = null)
        }.onFailure { e ->
            _state.value = _state.value.copy(
                installing = false,
                installError = resolveUiSkinImportErrorMessage(e.message)
            )
        }
    }

    fun closePreview() {
        pendingInstall = null
        _state.value = _state.value.copy(
            previewing = null,
            previewData = null,
            previewLoading = false,
            previewError = null,
            installError = null,
            installed = false
        )
    }

    fun cachePendingInstall(packageBytes: ByteArray, preview: UiSkinPackagePreview) {
        pendingInstall = PendingSkinInstall(packageBytes, preview)
    }

    fun consumePendingInstall(): PendingSkinInstall? {
        val pending = pendingInstall
        return pending
    }
}
