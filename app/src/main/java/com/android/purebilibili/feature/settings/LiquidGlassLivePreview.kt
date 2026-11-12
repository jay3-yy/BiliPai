package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.core.util.PickGalleryVisualMedia
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.android.purebilibili.core.store.home.GlassPopupPalette
import com.android.purebilibili.core.store.home.GlassMotionStyle
import com.android.purebilibili.core.store.home.LiquidGlassSettingsStore
import com.android.purebilibili.core.ui.components.AppSingleChoicePreference
import com.android.purebilibili.core.ui.components.AppChoiceOption
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.store.LiquidGlassAdvancedPreset
import com.android.purebilibili.core.store.LiquidGlassAdvancedSettings
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.store.resolveLiquidGlassAdvancedPreset
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.biliPaiProgressiveTopBlur
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.FloatingBottomBar
import com.android.purebilibili.feature.home.components.FloatingBottomBarColors
import com.android.purebilibili.feature.home.components.FloatingBottomBarItem
import com.android.purebilibili.feature.home.components.FloatingBottomBarMode
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import com.android.purebilibili.feature.home.components.resolveMaterialBottomBarIcon
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.feature.home.components.rememberLiquidGlassAdaptiveContentColor
import com.android.purebilibili.feature.home.components.rememberLiquidGlassAdaptiveReadabilityState
import com.android.purebilibili.feature.home.components.trackLiquidGlassAdaptiveReadability
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.ProgressiveBlur

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiquidGlassAdjustmentPanel(
    persistedProgress: Float,
    previewImageUri: String?,
    persistedAdvancedSettings: LiquidGlassAdvancedSettings,
    persistedReadabilityMode: LiquidGlassReadabilityMode,
    bottomBarItems: List<BottomNavItem>,
    bottomBarSearchEnabled: Boolean,
    onProgressCommitted: (Float) -> Unit,
    onPreviewImageChanged: (String?) -> Unit,
    onAdvancedSettingsCommitted: (LiquidGlassAdvancedSettings) -> Unit,
    onPresetCommitted: (LiquidGlassAdvancedPreset) -> Unit,
    onReadabilityModeChanged: (LiquidGlassReadabilityMode) -> Unit,
    onImportSettings: () -> Unit,
    onShareSettings: () -> Unit,
    isImportingSettings: Boolean,
    modifier: Modifier = Modifier,
    alwaysShowAdvanced: Boolean = false,
) {
    val context = LocalContext.current
    val paletteFlow = remember(context) { LiquidGlassSettingsStore.observePopupPalette(context) }
    val popupPalette by paletteFlow.collectAsStateWithLifecycle(initialValue = GlassPopupPalette.DOCK)
    val motionStyleFlow = remember(context) { LiquidGlassSettingsStore.observePopupMotionStyle(context) }
    val popupMotionStyle by motionStyleFlow.collectAsStateWithLifecycle(initialValue = GlassMotionStyle.STANDARD)
    val dockMotionFlow = remember(context) { LiquidGlassSettingsStore.observeDockMotionStyle(context) }
    val dockMotionStyle by dockMotionFlow.collectAsStateWithLifecycle(initialValue = GlassMotionStyle.STANDARD)
    val paletteScope = rememberCoroutineScope()
    val previewImagePicker = rememberLauncherForActivityResult(
        contract = PickGalleryVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onPreviewImageChanged(uri.toString())
    }
    var previewProgress by remember(persistedProgress) {
        mutableFloatStateOf(persistedProgress.coerceIn(0f, 1f))
    }
    var advancedSettings by remember(persistedAdvancedSettings) {
        mutableStateOf(persistedAdvancedSettings)
    }
    var presetSliderValue by remember(persistedAdvancedSettings) {
        mutableFloatStateOf(liquidGlassPresetSliderValue(persistedAdvancedSettings))
    }
    var readabilityMode by remember(persistedReadabilityMode) {
        mutableStateOf(persistedReadabilityMode)
    }
    val previewArtworkPagerState = rememberPagerState(
        pageCount = { LiquidGlassPreviewArtwork.entries.size },
    )
    var presetMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    val tuning = remember(previewProgress, advancedSettings, readabilityMode) {
        resolveLiquidGlassTuning(previewProgress, advancedSettings, readabilityMode)
    }
    val modeLabel = when (tuning.mode) {
        LiquidGlassMode.CLEAR -> "通透"
        LiquidGlassMode.BALANCED -> "平衡"
        LiquidGlassMode.FROSTED -> "磨砂"
    }
    val percentage = (previewProgress * 100f).roundToInt()

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "实时预览与调节",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                AppText(
                    text = "先选择一个预设，再按需要微调；修改会自动保存并应用到首页",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
            AppText(
                text = "$modeLabel · $percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.link(),
                maxLines = 1,
                softWrap = false,
            )
        }

        LiquidGlassHomeSample(
            progress = previewProgress,
            previewImageUri = previewImageUri,
            previewArtworkPagerState = previewArtworkPagerState,
            advancedSettings = advancedSettings,
            readabilityMode = readabilityMode,
            bottomBarItems = bottomBarItems,
            bottomBarSearchEnabled = bottomBarSearchEnabled,
            modifier = Modifier.fillMaxWidth(),
        )

        AppText(
            text = "图标与文字颜色",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        AppSegmentedControl(
            options = remember {
                listOf(
                    com.android.purebilibili.core.ui.components.AppSegmentOption(
                        LiquidGlassReadabilityMode.STABLE,
                        "固定主题色",
                    ),
                    com.android.purebilibili.core.ui.components.AppSegmentOption(
                        LiquidGlassReadabilityMode.ADAPTIVE,
                        "随背景调整",
                    ),
                )
            },
            selectedValue = readabilityMode,
            onSelectionChange = { mode ->
                readabilityMode = mode
                onReadabilityModeChanged(mode)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AppText(
            text = if (readabilityMode == LiquidGlassReadabilityMode.STABLE) {
                "推荐：始终使用主题文字色，显示稳定，也更省电。"
            } else {
                "根据当前显示区域的明暗自动切换文字颜色，复杂背景下更易辨认。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )

        SettingsItemAnchor("glass.dock_motion_style") {
            AppSingleChoicePreference(
                title = settingItemTitle("glass.dock_motion_style"),
                subtitle = "控制底栏合体、解体与搜索框伸缩；标准保留当前节奏，弹性让展开与收回分别回弹",
                selectedValue = dockMotionStyle,
                options = remember { GlassMotionStyle.entries.map { AppChoiceOption(it, it.label) } },
                onValueChange = { style ->
                    paletteScope.launch { LiquidGlassSettingsStore.setDockMotionStyle(context, style) }
                },
            )
        }

        SettingsItemAnchor("glass.popup_motion_style") {
            AppSingleChoicePreference(
                title = settingItemTitle("glass.popup_motion_style"),
                subtitle = "控制玻璃菜单的展开与收回：标准保留当前动画，弹性让展开与收回更柔和。下次打开菜单时生效",
                selectedValue = popupMotionStyle,
                options = remember { GlassMotionStyle.entries.map { AppChoiceOption(it, it.label) } },
                onValueChange = { style ->
                    paletteScope.launch { LiquidGlassSettingsStore.setPopupMotionStyle(context, style) }
                },
            )
        }

        SettingsItemAnchor("glass.popup_palette") {
            AppSingleChoicePreference(
                title = settingItemTitle("glass.popup_palette"),
                subtitle = "原有底色保持当前效果；主题磨砂随主题切换深浅底色",
                selectedValue = popupPalette,
                options = remember { GlassPopupPalette.entries.map { AppChoiceOption(it, it.label) } },
                onValueChange = { palette ->
                    paletteScope.launch { LiquidGlassSettingsStore.setPopupPalette(context, palette) }
                },
            )
        }

        if (previewImageUri == null) {
            val selectedArtwork = LiquidGlassPreviewArtwork.entries[
                previewArtworkPagerState.currentPage.coerceIn(
                    0,
                    LiquidGlassPreviewArtwork.entries.lastIndex,
                )
            ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassPreviewArtwork.entries.forEachIndexed { index, _ ->
                    val selected = index == previewArtworkPagerState.currentPage
                    Box(
                        modifier = Modifier
                            .width(if (selected) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    AppContentColors.accent()
                                } else {
                                    AppContentColors.secondaryText().copy(alpha = 0.32f)
                                }
                            )
                    )
                    if (index != LiquidGlassPreviewArtwork.entries.lastIndex) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                AppText(
                    text = "${selectedArtwork.label} · 左右滑动切换",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextButton(
                onClick = {
                    previewImagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_photo_library_24), contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                AppText(if (previewImageUri == null) "选择相册图片" else "更换图片")
            }
            if (previewImageUri != null) {
                AppTextButton(onClick = { onPreviewImageChanged(null) }) {
                    Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_restore_24), contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText("恢复默认")
                }
            }
        }
        AppText(
            text = if (previewImageUri == null) {
                "左右滑动可更换测试背景，上下拖动可检查不同内容区域。"
            } else {
                "这张图片只用于效果预览，不会设为首页背景或分享给别人。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )

        LiquidGlassSavedPresets(modifier = Modifier.fillMaxWidth())

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = "效果预设",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            // 锚点玻璃弹窗：触发按钮录制进来源图层，弹窗右缘对齐并向上覆盖按钮；
            // 弹窗玻璃关闭或无同窗宿主时 AppDropdownMenu 自动回退原生路径。
            com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(
                onOpen = { presetMenuExpanded = true },
            ) {
                AppTextButton(onClick = { presetMenuExpanded = true }) {
                    AppText(if (advancedSettings.preset == LiquidGlassAdvancedPreset.CUSTOM) {
                        "自定 · ${(presetSliderValue * 100f).roundToInt()}% ▾"
                    } else "${advancedSettings.preset.label} ▾")
                }
                com.android.purebilibili.core.ui.components.AppDropdownMenu(
                    expanded = presetMenuExpanded,
                    onDismissRequest = { presetMenuExpanded = false },
                ) {
                    LiquidGlassAdvancedPreset.entries.filter { it != LiquidGlassAdvancedPreset.CUSTOM }.forEach { preset ->
                        com.android.purebilibili.core.ui.components.AppDropdownMenuItem(
                            text = { AppText(preset.label) },
                            onClick = {
                                advancedSettings = resolveLiquidGlassAdvancedPreset(preset)
                                presetSliderValue = liquidGlassPresetSliderValue(advancedSettings)
                                if (preset == LiquidGlassAdvancedPreset.CLASSIC_FROSTED) {
                                    previewProgress = com.android.purebilibili.core.store.CLASSIC_FROSTED_GLASS_PROGRESS
                                }
                                presetMenuExpanded = false
                                onPresetCommitted(preset)
                            },
                        )
                    }
                }
            }
        }

        // 经典磨砂是独立配方，不伪装成清晰到棱镜插值轴上的某个百分比。
        if (advancedSettings.preset != LiquidGlassAdvancedPreset.CLASSIC_FROSTED) {
            AppSlider(
                value = presetSliderValue,
                onValueChange = { value ->
                    presetSliderValue = value.coerceIn(0f, 1f)
                    advancedSettings = resolveLiquidGlassPresetSliderSettings(value)
                },
                onValueChangeFinished = {
                    onAdvancedSettingsCommitted(advancedSettings)
                },
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "液态玻璃效果预设"
                        stateDescription = if (
                            advancedSettings.preset == LiquidGlassAdvancedPreset.CUSTOM
                        ) {
                            "自定 ${(presetSliderValue * 100f).roundToInt()}%"
                        } else {
                            advancedSettings.preset.label
                        }
                    },
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                LIQUID_GLASS_PRESET_SLIDER_ANCHORS.forEach { preset ->
                    AppText(
                        text = preset.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (preset == advancedSettings.preset) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (preset == advancedSettings.preset) {
                            AppContentColors.accent()
                        } else {
                            AppContentColors.secondaryText()
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        AppText(
            text = when (advancedSettings.preset) {
                LiquidGlassAdvancedPreset.READABLE -> "清晰：通透玻璃质感并保留折射和彩光，选中时同时切换到通透端材质"
                LiquidGlassAdvancedPreset.BALANCED -> "均衡：兼顾通透感、可读性和运行性能，选中时同时回到中点推荐材质"
                LiquidGlassAdvancedPreset.PRISM -> "棱镜：加强边缘彩光和折射，效果更醒目"
                LiquidGlassAdvancedPreset.CLASSIC_FROSTED -> "经典磨砂：均匀重模糊背景并加厚奶感底色，无折射和彩光；下方滑块调节模糊强度"
                LiquidGlassAdvancedPreset.CUSTOM -> "自定：已使用下方的高级参数"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
        AppText(
            text = "导入与分享",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        // Size actions by their content; narrow windows move the second action to its own row.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppTextButton(
                onClick = onImportSettings,
                enabled = !isImportingSettings,
            ) {
                Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_file_download_24), contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    text = if (isImportingSettings) "正在读取" else "导入他人设置",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppTextButton(
                onClick = onShareSettings,
                enabled = !isImportingSettings,
            ) {
                Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_share_24), contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    text = "分享我的设置",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppText(
            text = "导入前会先确认，只替换液态玻璃参数；分享文件不包含预览图片和其他设置。",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
        if (!alwaysShowAdvanced) AppTextButton(
            onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
        ) {
            Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_tune_24), contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            AppText(if (advancedSettingsExpanded) "收起高级调节" else "展开高级调节")
        }
        AnimatedVisibility(
            visible = alwaysShowAdvanced || advancedSettingsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassAdvancedSlider(
                    settingId = "glass.progressive_blur_radius",
                    title = settingItemTitle("glass.progressive_blur_radius"),
                    description = "数值越高，顶部背景越柔和；0% 关闭顶部模糊",
                    value = advancedSettings.progressiveBlurRadius,
                    valueText = if (advancedSettings.progressiveBlurRadius <= 0.001f) {
                        "关闭"
                    } else {
                        "${(advancedSettings.progressiveBlurRadius * 100f).roundToInt()}%"
                    },
                    onValueChange = { value ->
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            progressiveBlurRadius = value,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                    },
                    onValueChangeFinished = { onAdvancedSettingsCommitted(advancedSettings) },
                )
                LiquidGlassAdvancedSlider(
                    settingId = "glass.progressive_blur_extent",
                    title = settingItemTitle("glass.progressive_blur_extent"),
                    description = "数值越高，顶部有更多区域保持模糊",
                    value = advancedSettings.progressiveBlurExtent,
                    onValueChange = { value ->
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            progressiveBlurExtent = value,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                    },
                    onValueChangeFinished = { onAdvancedSettingsCommitted(advancedSettings) },
                )
                LiquidGlassAdvancedSlider(
                    settingId = "glass.progressive_blur_curve",
                    title = settingItemTitle("glass.progressive_blur_curve"),
                    description = "向左过渡更快，向右过渡更柔和",
                    value = advancedSettings.progressiveBlurCurve,
                    onValueChange = { value ->
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            progressiveBlurCurve = value,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                    },
                    onValueChangeFinished = { onAdvancedSettingsCommitted(advancedSettings) },
                )
                LiquidGlassAdvancedSlider(
                    settingId = "glass.content_readability",
                    title = settingItemTitle("glass.content_readability"),
                    description = "调高后，文字和图标更容易看清",
                    value = advancedSettings.contentReadability,
                    onValueChange = { value ->
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            contentReadability = value,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                    },
                    onValueChangeFinished = {
                        onAdvancedSettingsCommitted(advancedSettings)
                    },
                )
                LiquidGlassAdvancedSlider(
                    settingId = "glass.chromatic_aberration",
                    title = settingItemTitle("glass.chromatic_aberration"),
                    description = "调整玻璃边缘彩色光晕的强弱",
                    value = advancedSettings.chromaticAberration,
                    onValueChange = { value ->
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            chromaticAberration = value,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                    },
                    onValueChangeFinished = {
                        onAdvancedSettingsCommitted(advancedSettings)
                    },
                )
                LiquidGlassAdvancedSlider(
                    settingId = "glass.content_distortion",
                    title = settingItemTitle("glass.content_distortion"),
                    description = "调整文字和图标的变形程度；0% 关闭",
                    value = advancedSettings.contentDistortion,
                    valueText = if (advancedSettings.contentDistortion <= 0.001f) {
                        "关闭"
                    } else {
                        "${(advancedSettings.contentDistortion * 100f).roundToInt()}%"
                    },
                    onValueChange = { value ->
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            contentDistortion = value,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                    },
                    onValueChangeFinished = {
                        onAdvancedSettingsCommitted(advancedSettings)
                    },
                )
                AppTextButton(
                    onClick = {
                        val updatedSettings = advancedSettings.copy(
                            preset = LiquidGlassAdvancedPreset.CUSTOM,
                            contentDistortion = 0f,
                        )
                        advancedSettings = updatedSettings
                        presetSliderValue = liquidGlassPresetSliderValue(updatedSettings)
                        onAdvancedSettingsCommitted(updatedSettings)
                    },
                    enabled = advancedSettings.contentDistortion > 0.001f,
                ) {
                    Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_restore_24), contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText("关闭玻璃形变")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = "通透",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
            )
            AppSlider(
                value = previewProgress,
                onValueChange = { previewProgress = it.coerceIn(0f, 1f) },
                onValueChangeFinished = { onProgressCommitted(previewProgress) },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics {
                        contentDescription = "液态玻璃质感"
                        stateDescription = "$modeLabel，$percentage%"
                    },
            )
            AppText(
                text = "磨砂",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
            )
        }

        AppText(
            text = "拖动时实时预览，松手后自动保存；中间位置是推荐强度。",
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
    }
}

private const val LIQUID_GLASS_PRESET_BALANCED_POSITION = 0.5f
private const val LIQUID_GLASS_PRESET_ANCHOR_EPSILON = 0.001f
private val LIQUID_GLASS_PRESET_SLIDER_ANCHORS = listOf(
    LiquidGlassAdvancedPreset.READABLE,
    LiquidGlassAdvancedPreset.BALANCED,
    LiquidGlassAdvancedPreset.PRISM,
)

internal fun resolveLiquidGlassPresetSliderSettings(
    value: Float,
): LiquidGlassAdvancedSettings {
    val position = value.coerceIn(0f, 1f)
    val readable = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.READABLE)
    val balanced = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.BALANCED)
    val prism = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.PRISM)
    val (start, end, fraction) = if (position <= LIQUID_GLASS_PRESET_BALANCED_POSITION) {
        Triple(readable, balanced, position / LIQUID_GLASS_PRESET_BALANCED_POSITION)
    } else {
        Triple(
            balanced,
            prism,
            (position - LIQUID_GLASS_PRESET_BALANCED_POSITION) /
                LIQUID_GLASS_PRESET_BALANCED_POSITION,
        )
    }
    val preset = when {
        position <= LIQUID_GLASS_PRESET_ANCHOR_EPSILON ->
            LiquidGlassAdvancedPreset.READABLE
        abs(position - LIQUID_GLASS_PRESET_BALANCED_POSITION) <=
            LIQUID_GLASS_PRESET_ANCHOR_EPSILON -> LiquidGlassAdvancedPreset.BALANCED
        position >= 1f - LIQUID_GLASS_PRESET_ANCHOR_EPSILON ->
            LiquidGlassAdvancedPreset.PRISM
        else -> LiquidGlassAdvancedPreset.CUSTOM
    }
    return LiquidGlassAdvancedSettings(
        preset = preset,
        progressiveBlurRadius = lerpLiquidGlassPresetValue(
            start.progressiveBlurRadius,
            end.progressiveBlurRadius,
            fraction,
        ),
        progressiveBlurExtent = lerpLiquidGlassPresetValue(
            start.progressiveBlurExtent,
            end.progressiveBlurExtent,
            fraction,
        ),
        progressiveBlurCurve = lerpLiquidGlassPresetValue(
            start.progressiveBlurCurve,
            end.progressiveBlurCurve,
            fraction,
        ),
        contentReadability = lerpLiquidGlassPresetValue(
            start.contentReadability,
            end.contentReadability,
            fraction,
        ),
        chromaticAberration = lerpLiquidGlassPresetValue(
            start.chromaticAberration,
            end.chromaticAberration,
            fraction,
        ),
        contentDistortion = lerpLiquidGlassPresetValue(
            start.contentDistortion,
            end.contentDistortion,
            fraction,
        ),
    )
}

internal fun liquidGlassPresetSliderValue(settings: LiquidGlassAdvancedSettings): Float =
    when (settings.preset) {
        LiquidGlassAdvancedPreset.CLASSIC_FROSTED -> 0f
        LiquidGlassAdvancedPreset.READABLE -> 0f
        LiquidGlassAdvancedPreset.BALANCED -> LIQUID_GLASS_PRESET_BALANCED_POSITION
        LiquidGlassAdvancedPreset.PRISM -> 1f
        LiquidGlassAdvancedPreset.CUSTOM -> {
            val readable = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.READABLE)
            val balanced = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.BALANCED)
            val prism = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.PRISM)
            val readableToBalanced = projectLiquidGlassSettingsOntoPresetSegment(
                settings = settings,
                start = readable,
                end = balanced,
            )
            val balancedToPrism = projectLiquidGlassSettingsOntoPresetSegment(
                settings = settings,
                start = balanced,
                end = prism,
            )
            if (readableToBalanced.second <= balancedToPrism.second) {
                readableToBalanced.first * LIQUID_GLASS_PRESET_BALANCED_POSITION
            } else {
                LIQUID_GLASS_PRESET_BALANCED_POSITION +
                    balancedToPrism.first * LIQUID_GLASS_PRESET_BALANCED_POSITION
            }
        }
    }

/** Returns the nearest fraction on a preset segment and its squared six-parameter distance. */
private fun projectLiquidGlassSettingsOntoPresetSegment(
    settings: LiquidGlassAdvancedSettings,
    start: LiquidGlassAdvancedSettings,
    end: LiquidGlassAdvancedSettings,
): Pair<Float, Float> {
    val value = settings.asLiquidGlassPresetVector()
    val startVector = start.asLiquidGlassPresetVector()
    val endVector = end.asLiquidGlassPresetVector()
    var dot = 0f
    var lengthSquared = 0f
    for (index in value.indices) {
        val direction = endVector[index] - startVector[index]
        dot += (value[index] - startVector[index]) * direction
        lengthSquared += direction * direction
    }
    val fraction = if (lengthSquared > 0f) (dot / lengthSquared).coerceIn(0f, 1f) else 0f
    var distanceSquared = 0f
    for (index in value.indices) {
        val projected = startVector[index] + (endVector[index] - startVector[index]) * fraction
        val delta = value[index] - projected
        distanceSquared += delta * delta
    }
    return fraction to distanceSquared
}

private fun LiquidGlassAdvancedSettings.asLiquidGlassPresetVector(): FloatArray = floatArrayOf(
    progressiveBlurRadius,
    progressiveBlurExtent,
    progressiveBlurCurve,
    contentReadability,
    chromaticAberration,
    contentDistortion,
)

private fun lerpLiquidGlassPresetValue(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private enum class LiquidGlassPreviewArtwork(
    val label: String,
    val drawableResId: Int,
) {
    SKY(
        label = "蓝天白云",
        drawableResId = R.drawable.liquid_glass_preview_sky,
    ),
    PRISMATIC_GLASS(
        label = "彩色玻璃",
        drawableResId = R.drawable.liquid_glass_preview_prismatic,
    ),
}

@Composable
private fun LiquidGlassHomeSample(
    progress: Float,
    previewImageUri: String?,
    previewArtworkPagerState: PagerState,
    advancedSettings: LiquidGlassAdvancedSettings,
    readabilityMode: LiquidGlassReadabilityMode,
    bottomBarItems: List<BottomNavItem>,
    bottomBarSearchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val backdropSource = rememberChromeBackdropSource()
    val backdrop = backdropSource.backdrop
    val tuning = remember(progress, advancedSettings, readabilityMode) {
        resolveLiquidGlassTuning(progress, advancedSettings, readabilityMode)
    }
    val sampleShape = RoundedCornerShape(24.dp)
    val glassColor = MaterialTheme.colorScheme.surfaceContainer
    val contentColor = AppContentColors.primaryText()
    val adaptiveReadabilityEnabled = readabilityMode == LiquidGlassReadabilityMode.ADAPTIVE
    val topReadabilityState = rememberLiquidGlassAdaptiveReadabilityState(
        enabled = adaptiveReadabilityEnabled,
    )
    val bottomReadabilityState = rememberLiquidGlassAdaptiveReadabilityState(
        enabled = adaptiveReadabilityEnabled,
    )
    val topContentColor = rememberLiquidGlassAdaptiveContentColor(
        stableColor = contentColor,
        state = topReadabilityState,
        enabled = adaptiveReadabilityEnabled,
    )
    val bottomContentColor = rememberLiquidGlassAdaptiveContentColor(
        stableColor = contentColor,
        state = bottomReadabilityState,
        enabled = adaptiveReadabilityEnabled,
    )
    val density = LocalDensity.current
    val previewPanLimitPx = remember(density) { with(density) { 280.dp.toPx() } }
    val sliderFollowRangePx = remember(density) { with(density) { 80.dp.toPx() } }
    val previewArtwork = LiquidGlassPreviewArtwork.entries[
        previewArtworkPagerState.currentPage.coerceIn(
            0,
            LiquidGlassPreviewArtwork.entries.lastIndex,
        )
    ]
    var customImageFailed by remember(previewImageUri) { mutableStateOf(false) }
    var previewPanOffsetPx by remember(previewImageUri, previewArtwork) {
        mutableFloatStateOf(0f)
    }
    val previewBottomBarItems = remember(bottomBarItems) {
        bottomBarItems.ifEmpty { listOf(BottomNavItem.HOME) }
    }
    var previewSelectedBottomBarIndex by remember(previewBottomBarItems) {
        mutableIntStateOf(
            previewBottomBarItems.indexOf(BottomNavItem.HOME).takeIf { it >= 0 } ?: 0
        )
    }
    val previewSearchHeight = 40.dp

    Box(
        modifier = modifier
            .height(360.dp)
            .clip(sampleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(previewImageUri, previewArtwork, previewPanLimitPx) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    previewPanOffsetPx = (previewPanOffsetPx + dragAmount)
                        .coerceIn(-previewPanLimitPx, previewPanLimitPx)
                }
            }
            .semantics {
                val panPercentage =
                    (previewPanOffsetPx / previewPanLimitPx * 100f).roundToInt()
                if (previewImageUri == null || customImageFailed) {
                    contentDescription = "首页效果预览，可左右切换、上下拖动图片"
                    stateDescription = "${previewArtwork.label}，图片位置 $panPercentage%"
                } else {
                    contentDescription = "首页效果预览，可上下拖动图片"
                    stateDescription = "相册图片，图片位置 $panPercentage%"
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropSource.modifier)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .requiredHeight(920.dp)
                    .graphicsLayer {
                        val sliderFollowOffset = (progress - 0.5f) * sliderFollowRangePx
                        translationY = (previewPanOffsetPx + sliderFollowOffset)
                            .coerceIn(-previewPanLimitPx, previewPanLimitPx)
                    }
            ) {
                if (previewImageUri != null && !customImageFailed) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { customImageFailed = true },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.08f))
                    )
                } else {
                    HorizontalPager(
                        state = previewArtworkPagerState,
                        key = { page -> LiquidGlassPreviewArtwork.entries[page].name },
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = LiquidGlassPreviewArtwork.entries[page].drawableResId,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            LiquidGlassOpenSourceAcknowledgements(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(132.dp)
                .biliPaiProgressiveTopBlur(
                    backdrop = backdrop,
                    enabled = true,
                    blurRadiusDp = tuning.progressiveBlurRadius,
                    gradient = ProgressiveBlur.Top.copy(
                        startFraction = tuning.progressiveBlurStartFraction,
                        endFraction = tuning.progressiveBlurEndFraction,
                        curve = tuning.progressiveBlurCurve,
                    ),
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .height(previewSearchHeight)
                .trackLiquidGlassAdaptiveReadability(
                    state = topReadabilityState,
                    enabled = adaptiveReadabilityEnabled,
                )
                .biliPaiFloatingDockShell(
                    backdrop = backdrop,
                    containerColor = glassColor,
                    pressProgress = 0f,
                    shape = CircleShape,
                    drawLens = true,
                    lensIntensity = resolveFloatingDockGeometryScale(
                        previewSearchHeight.value
                    ),
                    liquidGlassTuning = tuning,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_search_24), contentDescription = null, tint = topContentColor)
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = "搜索感兴趣的视频",
                style = MaterialTheme.typography.bodySmall,
                color = topContentColor.copy(alpha = 0.8f),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                .trackLiquidGlassAdaptiveReadability(
                    state = bottomReadabilityState,
                    enabled = adaptiveReadabilityEnabled,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingBottomBar(
                selectedIndex = { previewSelectedBottomBarIndex },
                onSelected = { previewSelectedBottomBarIndex = it },
                onReselected = {},
                backdrop = backdrop,
                tabsCount = previewBottomBarItems.size,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                mode = FloatingBottomBarMode.LiquidGlass,
                contentHorizontalPadding = com.android.purebilibili.core.ui.resolveLiquidGlassIndicatorHorizontalInsetDp(
                    requestedInsetDp = 4f,
                    liquidGlassEnabled = true,
                ).dp,
                colors = FloatingBottomBarColors(
                    containerColor = glassColor,
                    indicatorColor = AppContentColors.accent(),
                    contentColor = bottomContentColor,
                    activeContentColor = AppContentColors.accent(),
                ),
                shellHeight = 48.dp,
                indicatorHeight = 44.dp,
                liquidGlassTuning = tuning,
            ) {
                previewBottomBarItems.forEachIndexed { index, item ->
                    FloatingBottomBarItem(
                        onClick = { previewSelectedBottomBarIndex = index },
                        selected = index == previewSelectedBottomBarIndex,
                        itemIndex = index,
                    ) {
                        Icon(
                            imageVector = resolveMaterialBottomBarIcon(
                                item = item,
                                selected = index == previewSelectedBottomBarIndex,
                            ),
                            contentDescription = item.label,
                            tint = if (index == previewSelectedBottomBarIndex) {
                                AppContentColors.accent()
                            } else {
                                bottomContentColor
                            },
                        )
                    }
                }
            }
            if (bottomBarSearchEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
                        .biliPaiFloatingDockShell(
                            backdrop = backdrop,
                            containerColor = glassColor,
                            pressProgress = 0f,
                            shape = CircleShape,
                            liquidGlassTuning = tuning,
                        )
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_search_24),
                        contentDescription = "底栏搜索",
                        tint = bottomContentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassOpenSourceAcknowledgements(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppText(
            text = "感谢开源社区",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        AppText(
            text = "Kotlin · Jetpack Compose · Miuix\n" +
                "AndroidX Media3 · Coil · kotlinx.serialization",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
        )
        AppText(
            text = "以及每一位开源贡献者",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.76f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LiquidGlassAdvancedSlider(
    settingId: String,
    title: String,
    description: String,
    value: Float,
    valueText: String = "${(value * 100f).roundToInt()}%",
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    SettingsItemAnchor(settingId) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                AppText(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
            AppText(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.link(),
            )
        }
        AppSlider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = title
                    stateDescription = valueText
                },
        )
    }
}
}
