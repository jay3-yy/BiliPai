package com.android.purebilibili.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.android.purebilibili.feature.home.components.cards.WallpaperPaletteRequest
import com.android.purebilibili.feature.home.components.cards.WallpaperPaletteStore
import com.android.purebilibili.core.ui.wallpaper.WallpaperReadabilityProfile
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.wallpaper.WallpaperMediaLoadState
import com.android.purebilibili.core.ui.wallpaper.WallpaperTransform
import com.android.purebilibili.core.ui.wallpaper.applyWallpaperGesture
import com.android.purebilibili.core.ui.wallpaper.sanitizeWallpaperTransform
import com.android.purebilibili.feature.home.HomeWallpaperBackdrop
import com.android.purebilibili.feature.home.resolveHomeWallpaperBackdropAppearance

/** Draft-only editing; this sheet never writes settings or replaces the current wallpaper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeWallpaperAdjustmentSheet(
    uri: String,
    initialMobileTransform: WallpaperTransform = WallpaperTransform(),
    initialTabletTransform: WallpaperTransform = WallpaperTransform(),
    effectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.ORIGINAL,
    saving: Boolean = false,
    saveError: String? = null,
    onDismiss: () -> Unit,
    onSave: (WallpaperTransform, WallpaperTransform) -> Unit,
) {
    val context = LocalContext.current
    val dataSaver = remember(context) { com.android.purebilibili.core.store.SettingsManager.isDataSaverActive(context) }
    val configuration = LocalConfiguration.current
    val currentIsTablet = configuration.screenWidthDp >= 600
    var mobile by remember(uri) { mutableStateOf(sanitizeWallpaperTransform(initialMobileTransform)) }
    var tablet by remember(uri) { mutableStateOf(sanitizeWallpaperTransform(initialTabletTransform)) }
    var selectedTab by remember(uri) { mutableIntStateOf(if (currentIsTablet) 1 else 0) }
    var loadState by remember(uri) { mutableStateOf(WallpaperMediaLoadState.LOADING) }
    var retry by remember(uri) { mutableIntStateOf(0) }
    val sourceSize = remember(uri, selectedTab) { mutableStateOf(IntSize.Zero) }
    val viewportSize = remember(uri, selectedTab) { mutableStateOf(IntSize.Zero) }
    val transform = remember(uri) { { if (selectedTab == 0) mobile else tablet } }
    val update: (WallpaperTransform) -> Unit = {
        if (selectedTab == 0) mobile = sanitizeWallpaperTransform(it)
        else tablet = sanitizeWallpaperTransform(it)
    }
    val currentUpdate by rememberUpdatedState(update)
    val canEdit by rememberUpdatedState(!saving && loadState == WallpaperMediaLoadState.READY)
    val actualWindow = (selectedTab == 1) == currentIsTablet
    val logicalWidth = if (actualWindow) configuration.screenWidthDp.coerceAtLeast(1).toFloat()
        else if (selectedTab == 0) 400f else 960f
    val logicalHeight = if (actualWindow) configuration.screenHeightDp.coerceAtLeast(1).toFloat()
        else if (selectedTab == 0) 800f else 600f
    val aspectRatio = logicalWidth / logicalHeight
    val baseColor = MaterialTheme.colorScheme.background
    val staticWallpaper = com.android.purebilibili.core.ui.wallpaper.rememberIsStaticWallpaper(uri)
    var previewReadability by remember(uri, selectedTab) { mutableStateOf<WallpaperReadabilityProfile?>(null) }
    LaunchedEffect(uri, selectedTab, baseColor, staticWallpaper) {
        if (!staticWallpaper) return@LaunchedEffect
        snapshotFlow { transform() to viewportSize.value }.collectLatest { (draftTransform, viewport) ->
            if (viewport.width <= 0 || viewport.height <= 0) return@collectLatest
            delay(140)
            val palette = WallpaperPaletteStore.loadWallpaperPalette(context,
                WallpaperPaletteRequest(uri, viewport, draftTransform, baseColor))
            currentCoroutineContext().ensureActive()
            previewReadability = palette?.readability
        }
    }
    val appearance = resolveHomeWallpaperBackdropAppearance(
        hasWallpaper = true,
        effectMode = effectMode.takeUnless { it == HomeWallpaperEffectMode.OFF } ?: HomeWallpaperEffectMode.ORIGINAL,
        isDarkTheme = baseColor.luminance() < 0.5f,
        isDataSaverActive = dataSaver,
    )
    AppModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppTextButton(onClick = onDismiss, enabled = !saving) { AppText("取消") }
                AppText("调整壁纸", style = MaterialTheme.typography.titleMedium)
                AppTextButton(
                    onClick = { onSave(mobile, tablet) },
                    enabled = !saving && loadState == WallpaperMediaLoadState.READY,
                ) {
                    if (saving) AppCircularProgressIndicator(Modifier.size(18.dp)) else AppText("保存")
                }
            }
            WallpaperDeviceTabRow(
                selectedTab = selectedTab,
                onSelectedTabChange = {
                    if (!saving && selectedTab != it) {
                        loadState = WallpaperMediaLoadState.LOADING
                        selectedTab = it
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(300.dp).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val previewHeight = minOf(maxHeight, maxWidth / aspectRatio)
                val previewWidth = previewHeight * aspectRatio
                Box(
                    Modifier
                        .size(previewWidth, previewHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { viewportSize.value = it }
                        .pointerInput(uri, selectedTab) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (canEdit) {
                                    val viewport = viewportSize.value
                                    val source = sourceSize.value
                                    currentUpdate(applyWallpaperGesture(
                                        current = transform(),
                                        panX = pan.x, panY = pan.y, zoomChange = zoom,
                                        viewportWidth = viewport.width.toFloat(),
                                        viewportHeight = viewport.height.toFloat(),
                                        sourceWidth = source.width.toFloat(), sourceHeight = source.height.toFloat(),
                                    ))
                                }
                            }
                        },
                ) {
                    key(uri, selectedTab, retry) {
                        HomeWallpaperBackdrop(
                            wallpaperUri = uri,
                            appearance = appearance.copy(blurRadiusDp = appearance.blurRadiusDp * previewWidth.value / logicalWidth),
                            baseColor = baseColor,
                            isDataSaverActive = dataSaver,
                            reuseGlobalSource = false,
                            transformProvider = transform,
                            playbackPriority = 10,
                            readabilityOverride = previewReadability,
                            localProtectionScale = previewWidth.value / logicalWidth,
                            onMediaLoadState = { loadState = it },
                            onMediaSourceSize = { sourceSize.value = it },
                        )
                    }
                    if (loadState == WallpaperMediaLoadState.LOADING) {
                        AppCircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else if (loadState == WallpaperMediaLoadState.ERROR) {
                        Column(
                            Modifier.align(Alignment.Center).background(baseColor).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppText("壁纸加载失败")
                            AppTextButton(onClick = {
                                loadState = WallpaperMediaLoadState.LOADING
                                retry++
                            }) { AppText("重试") }
                        }
                    }
                }
            }
            if (!saveError.isNullOrBlank()) {
                AppText(
                    saveError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            WallpaperZoomControls(
                transformProvider = transform,
                enabled = !saving && loadState == WallpaperMediaLoadState.READY,
                onChange = update,
            )
            if (effectMode == HomeWallpaperEffectMode.OFF) {
                AppText(
                    "当前壁纸效果已关闭，保存位置后可在壁纸效果中开启。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            AppText(
                "双指缩放、单指拖动；手机和平板分别保存。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun WallpaperZoomControls(
    transformProvider: () -> WallpaperTransform,
    enabled: Boolean,
    onChange: (WallpaperTransform) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("缩放", style = MaterialTheme.typography.labelLarge)
            AppTextButton(onClick = { onChange(WallpaperTransform()) }, enabled = enabled) { AppText("重置位置") }
        }
        AppSlider(
            value = transformProvider().scale,
            onValueChange = { onChange(transformProvider().copy(scale = it)) },
            valueRange = 1f..3f,
            enabled = enabled,
        )
    }
}
