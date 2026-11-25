@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.settings.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.R
import com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
import com.android.purebilibili.core.plugin.skin.SkinCatalog
import com.android.purebilibili.core.plugin.skin.SkinCatalogEntry
import com.android.purebilibili.core.plugin.skin.SkinCatalogLoader
import com.android.purebilibili.core.plugin.skin.UiSkinImportPackageResolver
import com.android.purebilibili.core.plugin.skin.UiSkinInstallStore
import com.android.purebilibili.core.plugin.skin.UiSkinPackagePreview
import com.android.purebilibili.core.plugin.skin.UiSkinSelection
import com.android.purebilibili.core.plugin.skin.UiSkinSettingsStore
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.plugin.skin.normalizeSkinPackageUrl
import com.android.purebilibili.core.plugin.skin.rememberUiSkinState
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.settings.SettingsFunctionIcon
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.settings.UiSkinCompositionPreview
import com.android.purebilibili.feature.settings.UiSkinCompositionPreviewData
import com.android.purebilibili.feature.settings.downloadUiSkinRemotePackage
import com.android.purebilibili.feature.settings.parsePreviewColor
import com.android.purebilibili.feature.settings.rememberSettingsVector
import com.android.purebilibili.feature.settings.resolveUiSkinImportErrorMessage
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class SkinCatalogFilter(val label: String) {
    ALL("全部"),
    LIGHT("浅色系"),
    DARK("深色系"),
    DYNAMIC_EFFECT("含动效"),
    INSTALLED("已安装"),
}

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
    val searchQuery: String = "",
    val filter: SkinCatalogFilter = SkinCatalogFilter.ALL,
) {
    val filteredThemes: List<SkinCatalogEntry>
        get() = resolveFilteredThemes(emptySet())

    fun resolveFilteredThemes(installedNameSet: Set<String>): List<SkinCatalogEntry> {
        val q = searchQuery.trim()
        return catalog.themes.filter { entry ->
            val matchesQuery = q.isEmpty() ||
                entry.displayName.contains(q, ignoreCase = true) ||
                entry.id.contains(q, ignoreCase = true)
            val matchesFilter = when (filter) {
                SkinCatalogFilter.ALL -> true
                SkinCatalogFilter.LIGHT -> !entry.isDark
                SkinCatalogFilter.DARK -> entry.isDark
                SkinCatalogFilter.DYNAMIC_EFFECT -> {
                    entry.capabilities.animatedIcons ||
                        entry.capabilities.profileVideo ||
                        entry.capabilities.loadingAnimation ||
                        entry.capabilities.likeEffect ||
                        entry.capabilities.playerProgress
                }
                SkinCatalogFilter.INSTALLED -> entry.displayName in installedNameSet || entry.id in installedNameSet
            }
            matchesQuery && matchesFilter
        }
    }
}

private data class PendingSkinInstall(
    val packageBytes: ByteArray,
    val preview: UiSkinPackagePreview,
)

/**
 * 合成离线目录中已内置的颜色与明暗元数据，防止网络仅拉取到纯资源 ZIP 时丢失 `color_mode` / `tail_color`。
 */
private fun buildCatalogFallbackMetadataJson(entry: SkinCatalogEntry): ByteArray {
    val obj = buildJsonObject {
        put("id", JsonPrimitive(entry.id))
        put("name", JsonPrimitive(entry.displayName))
        put(
            "properties",
            buildJsonObject {
                entry.colorMode?.takeIf { it.isNotBlank() }?.let { put("color_mode", JsonPrimitive(it)) }
                entry.color?.takeIf { it.isNotBlank() }?.let { put("color", JsonPrimitive(it)) }
                entry.colorSecondPage?.takeIf { it.isNotBlank() }?.let { put("color_second_page", JsonPrimitive(it)) }
                entry.tailColor?.takeIf { it.isNotBlank() }?.let { put("tail_color", JsonPrimitive(it)) }
            }
        )
    }
    return obj.toString().encodeToByteArray()
}

@Composable
fun SkinCatalogScreen(
    onBack: () -> Unit,
    onInstalled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { SkinCatalogStateHolder(context) }
    val uiSkinStore = stateHolder.uiSkinStore
    val state by stateHolder.state.collectAsStateWithLifecycle()
    val uiSkinState by rememberUiSkinState(context)
    var installedPackages by remember { mutableStateOf(uiSkinStore.listInstalledPackages()) }

    val installedByDisplayName = remember(installedPackages) {
        installedPackages.associateBy { it.displayName }
    }
    val installedMatchKeys = remember(installedPackages) {
        buildSet {
            installedPackages.forEach { pkg ->
                add(pkg.displayName)
                add(pkg.skinId)
            }
        }
    }
    val filteredThemes = remember(state.catalog, state.searchQuery, state.filter, installedMatchKeys) {
        state.resolveFilteredThemes(installedMatchKeys)
    }

    fun preparePreview(entry: SkinCatalogEntry) {
        scope.launch {
            stateHolder.setPreviewLoading()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val packageUrl = entry.preferredPackageUrl()
                        ?: throw IllegalArgumentException("该主题没有可用的资源包链接")
                    val packageBytes = downloadUiSkinRemotePackage(packageUrl)
                    val remoteMetadataBytes = entry.resolvedThemeMetadataUrl()?.let { metadataUrl ->
                        runCatching { downloadUiSkinRemotePackage(metadataUrl) }.getOrNull()
                    }
                    val metadataBytes = remoteMetadataBytes ?: buildCatalogFallbackMetadataJson(entry)
                    val importPackage = UiSkinImportPackageResolver.resolveBilibiliPackageWithMetadata(
                        packageBytes = packageBytes,
                        themeJsonBytes = metadataBytes,
                    ).getOrElse {
                        UiSkinImportPackageResolver.resolve(
                            inputBytes = packageBytes,
                            remotePackageFetcher = ::downloadUiSkinRemotePackage,
                        ).getOrThrow()
                    }
                    val supplementalAssets = downloadCatalogEffectAssets(entry)
                    val mergedPackageBytes = UiSkinImportPackageResolver.mergeSupplementalEffectAssets(
                        packageBytes = importPackage.packageBytes,
                        supplementalAssets = supplementalAssets,
                    ).getOrThrow()
                    val preview = uiSkinStore.previewPackage(mergedPackageBytes).getOrThrow()
                    val previewAssetFiles = uiSkinStore.extractPreviewAssetFiles(
                        preview = preview,
                        packageBytes = mergedPackageBytes,
                    ).getOrThrow()
                    stateHolder.cachePendingInstall(mergedPackageBytes, preview)
                    UiSkinCompositionPreviewData(
                        displayName = entry.displayName,
                        manifest = preview.manifest,
                        assetFiles = previewAssetFiles,
                        darkMode = entry.isDark,
                    )
                }
            }
            stateHolder.setPreviewResult(result)
        }
    }

    fun confirmInstall(entry: SkinCatalogEntry, onInstalledCallback: () -> Unit) {
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
                            selectedInstallId = installed.installId,
                        )
                    )
                    if (UiSkinSurface.PROFILE in installed.manifest.surfaces) {
                        SettingsManager.setProfileBgUseSkin(context, true)
                    }
                    installed
                }
            }
            stateHolder.setInstallResult(result)
            if (result.isSuccess) {
                installedPackages = uiSkinStore.listInstalledPackages()
                onInstalledCallback()
            }
        }
    }

    SettingsPageScaffold(
        title = "在线装扮 (${state.catalog.themes.size})",
        onBack = onBack,
        backContentDescription = "返回插件中心",
        bottomContentPadding = 0.dp,
        scrollHost = SettingsPageScrollHost.External,
    ) {
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
            return@SettingsPageScaffold
        }

        if (state.catalog.themes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = "装扮目录为空或加载失败",
                    color = AppContentColors.secondaryText(),
                )
            }
            return@SettingsPageScaffold
        }

        val bottomNavPadding = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
        Column(modifier = Modifier.fillMaxSize()) {
            AppLiquidAwareSearchField(
                query = state.searchQuery,
                onQueryChange = stateHolder::setSearchQuery,
                placeholder = "搜索装扮名称（共 ${state.catalog.themes.size} 款）",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = LocalSettingsTopContentPadding.current + 8.dp,
                        bottom = 6.dp,
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinCatalogFilter.entries.forEach { filter ->
                    val selected = state.filter == filter
                    AppSurface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { stateHolder.setFilter(filter) },
                    ) {
                        AppText(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                AppContentColors.secondaryText()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (filteredThemes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = "没有找到符合条件的装扮",
                        color = AppContentColors.secondaryText(),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp + bottomNavPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = filteredThemes,
                        key = { it.id },
                        contentType = { "skin_catalog_card" },
                    ) { entry ->
                        val installedPkg = installedByDisplayName[entry.displayName]
                        val isActive = installedPkg != null &&
                            uiSkinState.enabled &&
                            uiSkinState.activeSkin?.installId == installedPkg.installId
                        SkinCatalogCard(
                            entry = entry,
                            installedPkg = installedPkg,
                            isActive = isActive,
                            onClick = {
                                if (installedPkg != null) {
                                    // 已本地安装的装扮直接秒开预览，无需重复联网下载
                                    stateHolder.openInstalledPreview(entry, installedPkg)
                                } else {
                                    stateHolder.openPreview(entry)
                                    preparePreview(entry)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    state.previewing?.let { entry ->
        val existingInstalled = installedByDisplayName[entry.displayName]
        val isActive = existingInstalled != null &&
            uiSkinState.enabled &&
            uiSkinState.activeSkin?.installId == existingInstalled.installId
        SkinCatalogPreviewDialog(
            entry = entry,
            previewData = state.previewData,
            loading = state.previewLoading,
            error = state.previewError,
            installing = state.installing,
            installError = state.installError,
            installed = state.installed || existingInstalled != null,
            isActive = isActive,
            onDismiss = { stateHolder.closePreview() },
            onActivateExisting = if (existingInstalled != null && !isActive) {
                {
                    UiSkinSettingsStore.setSelection(
                        context = context,
                        selection = UiSkinSelection(
                            enabled = true,
                            selectedSkinId = existingInstalled.skinId,
                            selectedInstallId = existingInstalled.installId,
                        )
                    )
                    if (UiSkinSurface.PROFILE in existingInstalled.manifest.surfaces) {
                        scope.launch { SettingsManager.setProfileBgUseSkin(context, true) }
                    }
                    stateHolder.closePreview()
                    onInstalled()
                }
            } else null,
            onInstall = { confirmInstall(entry, onInstalled) },
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
        effects.loadingAnimationUrl?.let { url ->
            val clean = url.substringBefore('?').lowercase()
            val ext = when {
                clean.endsWith(".json") -> "json"
                clean.endsWith(".png") -> "png"
                else -> "webp"
            }
            download(url, "assets/loading.$ext")
        }
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
        effects.playerProgressIconUrl?.let { url ->
            val clean = url.substringBefore('?').lowercase()
            val ext = when {
                clean.endsWith(".webp") -> "webp"
                clean.endsWith(".png") -> "png"
                else -> "json"
            }
            download(url, "assets/progress_icon.$ext")
        }
        effects.playerProgressDraggingIconUrl?.let { url ->
            val clean = url.substringBefore('?').lowercase()
            val ext = when {
                clean.endsWith(".webp") -> "webp"
                clean.endsWith(".png") -> "png"
                else -> "json"
            }
            download(url, "assets/progress_drag_icon.$ext")
        }
        download(effects.playerProgressStaticIconUrl, "assets/progress_static_icon.png")
    }
}

@Composable
private fun SkinCatalogCard(
    entry: SkinCatalogEntry,
    installedPkg: InstalledUiSkinPackage?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    AppSurface(
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.previewUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isActive) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Chip),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) {
                        AppText(
                            text = "使用中",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else if (installedPkg != null) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Chip),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) {
                        AppText(
                            text = "已安装",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else if (entry.officialExample) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Chip),
                        color = AppContentColors.accent(),
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) {
                        AppText(
                            text = "官方示例",
                            color = AppContentColors.onAccent(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    entry.colorSecondPage?.let { ColorChip(it) }
                    entry.tailColor?.let { ColorChip(it) }
                }
                if (!entry.capabilities.isEmpty) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Chip),
                        color = Color.Black.copy(alpha = 0.52f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                    ) {
                        AppText(
                            text = entry.capabilities.labels().take(3).joinToString(" · "),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = AppContentColors.primaryText(),
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = if (entry.isDark) "深色" else "浅色",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
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
    isActive: Boolean,
    onDismiss: () -> Unit,
    onActivateExisting: (() -> Unit)? = null,
    onInstall: () -> Unit,
) {
    com.android.purebilibili.core.ui.AppAlertDialog(
        onDismissRequest = { if (!installing) onDismiss() },
        icon = {
            SettingsFunctionIcon(
                rememberSettingsVector(R.drawable.ph_paint_brush_24, R.drawable.ms_brush_fill_24),
                title = entry.displayName,
            )
        },
        title = { AppText(entry.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        AppText("正在下载并生成多场景预览…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (error != null) {
                    AppText(error, color = AppContentColors.error(), style = MaterialTheme.typography.bodySmall)
                } else if (previewData != null) {
                    UiSkinCompositionPreview(data = previewData)
                    AppText(
                        text = "可点击上方胶囊切换预览「沉底原画」「悬浮液态玻璃」与「渐进模糊顶栏」三种实际装扮效果；安装后还可在插件中心单独开关任一部件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                    AppText(
                        text = entry.licenseNote
                            ?: "素材来自 B 站官方付费/限定主题存档，仅供本地私用，不得作为社区包分发。",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                if (installError != null) {
                    AppText(installError, color = AppContentColors.error(), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when {
                isActive -> AppTextButton(onClick = onDismiss) { AppText("当前生效中") }
                onActivateExisting != null -> AppTextButton(onClick = onActivateExisting) { AppText("立即启用") }
                installed -> AppTextButton(onClick = onDismiss) { AppText("完成") }
                else -> AppTextButton(
                    onClick = onInstall,
                    enabled = !loading && !installing && previewData != null && error == null,
                ) {
                    AppText(if (installing) "导入中…" else "下载并启用")
                }
            }
        },
        dismissButton = {
            if (!installing) {
                AppTextButton(onClick = onDismiss) { AppText("关闭") }
            }
        }
    )
}

private class SkinCatalogStateHolder(context: Context) {
    val uiSkinStore = UiSkinInstallStore.createDefault(context)
    private val _state = MutableStateFlow(SkinCatalogUiState())
    val state: StateFlow<SkinCatalogUiState> = _state.asStateFlow()
    private var pendingInstall: PendingSkinInstall? = null

    init {
        val catalog = SkinCatalogLoader.loadOrDefault(context)
        _state.update { it.copy(loading = false, catalog = catalog) }
    }

    fun openPreview(entry: SkinCatalogEntry) {
        _state.update {
            it.copy(
                previewing = entry,
                previewData = null,
                previewLoading = true,
                previewError = null,
                installed = false,
                installError = null,
            )
        }
    }

    fun openInstalledPreview(entry: SkinCatalogEntry, installedPkg: InstalledUiSkinPackage) {
        _state.update {
            it.copy(
                previewing = entry,
                previewData = UiSkinCompositionPreviewData(
                    displayName = installedPkg.displayName,
                    manifest = installedPkg.manifest,
                    assetFiles = installedPkg.assetFiles,
                    darkMode = installedPkg.manifest.colors.colorMode.equals("dark", ignoreCase = true),
                ),
                previewLoading = false,
                previewError = null,
                installed = true,
                installError = null,
            )
        }
    }

    fun setPreviewLoading() {
        _state.update { it.copy(previewLoading = true, previewError = null) }
    }

    fun setPreviewResult(result: Result<UiSkinCompositionPreviewData>) {
        result.onSuccess { data ->
            _state.update { it.copy(previewLoading = false, previewData = data, previewError = null) }
        }.onFailure { e ->
            val errorMessage = resolveUiSkinImportErrorMessage(e.message)
            _state.update {
                it.copy(
                    previewLoading = false,
                    previewError = errorMessage,
                )
            }
        }
    }

    fun setInstalling() {
        _state.update { it.copy(installing = true, installError = null, installed = false) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: SkinCatalogFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setInstallResult(result: Result<InstalledUiSkinPackage>) {
        result.onSuccess {
            _state.update { it.copy(installing = false, installed = true, installError = null) }
        }.onFailure { e ->
            val errorMessage = resolveUiSkinImportErrorMessage(e.message)
            _state.update {
                it.copy(
                    installing = false,
                    installError = errorMessage,
                )
            }
        }
    }

    fun closePreview() {
        pendingInstall = null
        _state.update {
            it.copy(
                previewing = null,
                previewData = null,
                previewLoading = false,
                previewError = null,
                installError = null,
                installed = false,
            )
        }
    }

    fun cachePendingInstall(packageBytes: ByteArray, preview: UiSkinPackagePreview) {
        pendingInstall = PendingSkinInstall(packageBytes, preview)
    }

    fun consumePendingInstall(): PendingSkinInstall? = pendingInstall
}
