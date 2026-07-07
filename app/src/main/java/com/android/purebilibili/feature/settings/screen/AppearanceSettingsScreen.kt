@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.settings

import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.animation.core.*
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.store.BottomBarSearchLayoutMode
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import coil.compose.AsyncImage
import com.android.purebilibili.core.theme.deleteStoredAppFont
import com.android.purebilibili.core.theme.importAppFontFromUri
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor
import com.android.purebilibili.core.ui.getWindowNavigationBarColor
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppSparklesIcon
import com.android.purebilibili.core.ui.setWindowNavigationBarColor
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.rememberHapticFeedback
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.HueSlider
import com.github.skydoves.colorpicker.compose.SaturationSlider
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 *  外观设置二级页面
 * iOS 风格设计
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToIconSettings: () -> Unit = {},  //  [新增] 图标设置导航
    onNavigateToAnimationSettings: () -> Unit = {}  //  [新增] 动画设置导航
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var pendingLanguageRestart by remember { mutableStateOf<AppLanguage?>(null) }
    val backLabel = stringResource(R.string.common_back)
    val screenTitle = stringResource(R.string.appearance_settings_title)
    val restartDialogTitle = stringResource(R.string.app_language_restart_dialog_title)
    val restartDialogMessage = stringResource(R.string.app_language_restart_dialog_message)
    val restartDialogConfirm = stringResource(R.string.app_language_restart_dialog_confirm)
    val displayLevel = when (state.displayMode) {
        0 -> 0.35f
        1 -> 0.6f
        else -> 0.85f
    }
    val appearanceInteractionLevel = (
        displayLevel +
            if (state.headerBlurEnabled) 0.1f else 0f +
            if (state.isBottomBarFloating) 0.1f else 0f
        ).coerceIn(0f, 1f)
    val appearanceAnimationSpeed = if (state.dynamicColor) 1.1f else 1f
    
    //  [修复] 设置导航栏透明，确保底部手势栏沉浸式效果
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val originalNavBarColor = window?.let(::getWindowNavigationBarColor)
            ?: android.graphics.Color.TRANSPARENT
        
        if (window != null) {
            setWindowNavigationBarColor(window, android.graphics.Color.TRANSPARENT)
        }
        
        onDispose {
            if (window != null) {
                setWindowNavigationBarColor(window, originalNavBarColor)
            }
        }
    }
    
    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = screenTitle,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = globalWallpaperAwareChromeColor(AppSurfaceTokens.groupedListContainer())
                )
            )
        },
        containerColor = globalWallpaperAwareChromeColor(AppSurfaceTokens.groupedListContainer()),
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides state.isLiquidGlassEnabled) {
            AppearanceSettingsContent(
                modifier = Modifier.padding(padding),
                state = state,
                onNavigateToIconSettings = onNavigateToIconSettings,
                onNavigateToAnimationSettings = onNavigateToAnimationSettings,
                viewModel = viewModel,
                context = context,
                onAppLanguageChange = { language ->
                    if (shouldPromptAppRestartForLanguageChange(state.appLanguage, language)) {
                        pendingLanguageRestart = language
                    }
                }
            )
        }
    }

    pendingLanguageRestart?.let { pendingLanguage ->
        AlertDialog(
            onDismissRequest = { pendingLanguageRestart = null },
            title = { Text(restartDialogTitle) },
            text = { Text(restartDialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLanguageRestart = null
                        coroutineScope.launch {
                            persistAndApplyAppLanguageBeforeRestart(
                                appLanguage = pendingLanguage,
                                persist = { SettingsManager.setAppLanguage(context, it) },
                                restart = { restartApp(context) }
                            )
                        }
                    }
                ) {
                    Text(restartDialogConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLanguageRestart = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}



@Composable
fun AppearanceSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onNavigateToIconSettings: () -> Unit,
    onNavigateToAnimationSettings: () -> Unit,
    viewModel: SettingsViewModel,
    context: android.content.Context,
    onAppLanguageChange: (AppLanguage) -> Unit
) {
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    // Animation Trigger
    val displayModeTint = rememberAdaptiveSemanticIconTint(iOSBlue)

    val configuration = LocalConfiguration.current
    val displayMetricsSnapshot = LocalDisplayMetricsSnapshot.current
    val isTablet = configuration.screenWidthDp >= 600 // Material Design 3 中型屏幕断点
    LaunchedEffect(focusRequest?.token, isTablet) {
        val request = focusRequest ?: return@LaunchedEffect
        if (request.target != SettingsSearchTarget.APPEARANCE) return@LaunchedEffect
        val index = resolveAppearanceSettingsScrollIndex(request.focusId, isTablet) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        SettingsSearchFocusController.clear(request.token)
    }
    val windowSizeClass = LocalWindowSizeClass.current
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val scope = rememberCoroutineScope()
    val themeSectionTitle = stringResource(R.string.appearance_theme_color_section)
    val uiPresetTitle = stringResource(R.string.appearance_ui_preset_title)
    val uiPresetSubtitle = stringResource(R.string.appearance_ui_preset_subtitle)
    val uiPresetIosLabel = stringResource(R.string.ui_preset_ios)
    val uiPresetAndroidLabel = stringResource(R.string.ui_preset_android_native)
    val uiPresetOptions = remember(uiPresetIosLabel, uiPresetAndroidLabel) {
        resolveUiPresetSegmentOptions(
            iosLabel = uiPresetIosLabel,
            androidNativeLabel = uiPresetAndroidLabel
        )
    }
    val uiPresetIosTitle = stringResource(R.string.appearance_ui_preset_ios_title)
    val uiPresetIosSummary = stringResource(R.string.appearance_ui_preset_ios_summary)
    val uiPresetAndroidMaterialTitle = stringResource(R.string.appearance_ui_preset_android_material_title)
    val uiPresetAndroidMaterialSummary = stringResource(R.string.appearance_ui_preset_android_material_summary)
    val uiPresetAndroidMiuixTitle = stringResource(R.string.appearance_ui_preset_android_miuix_title)
    val uiPresetAndroidMiuixSummary = stringResource(R.string.appearance_ui_preset_android_miuix_summary)
    val androidNativeVariantTitle = stringResource(R.string.appearance_android_native_variant_title)
    val androidNativeVariantSubtitle = stringResource(R.string.appearance_android_native_variant_subtitle)
    val androidNativeVariantMaterialLabel = stringResource(R.string.appearance_android_native_variant_material3)
    val androidNativeVariantMiuixLabel = stringResource(R.string.appearance_android_native_variant_miuix)
    val androidNativeVariantOptions = remember(
        androidNativeVariantMaterialLabel,
        androidNativeVariantMiuixLabel
    ) {
        resolveAndroidNativeVariantSegmentOptions(
            material3Label = androidNativeVariantMaterialLabel,
            miuixLabel = androidNativeVariantMiuixLabel
        )
    }
    val uiPresetDescription = remember(
        state.uiPreset,
        state.androidNativeVariant,
        uiPresetIosTitle,
        uiPresetIosSummary,
        uiPresetAndroidMaterialTitle,
        uiPresetAndroidMaterialSummary,
        uiPresetAndroidMiuixTitle,
        uiPresetAndroidMiuixSummary
    ) {
        resolveAppearanceUiPresetDescription(
            preset = state.uiPreset,
            androidNativeVariant = state.androidNativeVariant,
            iosTitle = uiPresetIosTitle,
            iosSummary = uiPresetIosSummary,
            materialTitle = uiPresetAndroidMaterialTitle,
            materialSummary = uiPresetAndroidMaterialSummary,
            miuixTitle = uiPresetAndroidMiuixTitle,
            miuixSummary = uiPresetAndroidMiuixSummary
        )
    }
    val selectedUiPresetLabel =
        uiPresetOptions.firstOrNull { it.value == state.uiPreset }?.label ?: state.uiPreset.label
    val selectedAndroidNativeVariantLabel = androidNativeVariantOptions
        .firstOrNull { it.value == state.androidNativeVariant }
        ?.label ?: state.androidNativeVariant.label
    val themeModeTitle = stringResource(R.string.appearance_theme_mode_title)
    val themeModeSubtitle = stringResource(R.string.appearance_theme_mode_subtitle)
    val themeModeFollowSystemLabel = stringResource(R.string.theme_mode_follow_system)
    val themeModeLightLabel = stringResource(R.string.theme_mode_light)
    val themeModeDarkLabel = stringResource(R.string.theme_mode_dark)
    val themeModeOptions = remember(
        themeModeFollowSystemLabel,
        themeModeLightLabel,
        themeModeDarkLabel
    ) {
        resolveThemeModeSegmentOptions(
            followSystemLabel = themeModeFollowSystemLabel,
            lightLabel = themeModeLightLabel,
            darkLabel = themeModeDarkLabel
        )
    }
    val selectedThemeModeLabel =
        themeModeOptions.firstOrNull { it.value == state.themeMode }?.label ?: state.themeMode.label
    val darkThemeStyleTitle = stringResource(R.string.appearance_dark_theme_style_title)
    val darkThemeStyleSubtitle = stringResource(R.string.appearance_dark_theme_style_subtitle)
    val darkThemeStyleDefaultLabel = stringResource(R.string.dark_theme_style_default)
    val darkThemeStyleAmoledLabel = stringResource(R.string.dark_theme_style_amoled)
    val darkThemeStyleOptions = remember(
        darkThemeStyleDefaultLabel,
        darkThemeStyleAmoledLabel
    ) {
        resolveDarkThemeStyleSegmentOptions(
            defaultLabel = darkThemeStyleDefaultLabel,
            amoledLabel = darkThemeStyleAmoledLabel
        )
    }
    val selectedDarkThemeStyleLabel = darkThemeStyleOptions
        .firstOrNull { it.value == state.darkThemeStyle }
        ?.label ?: state.darkThemeStyle.label
    val appLanguageTitle = stringResource(R.string.appearance_app_language_title)
    val appLanguageSubtitle = stringResource(R.string.appearance_app_language_subtitle)
    val appLanguageFollowSystemLabel = stringResource(R.string.app_language_follow_system)
    val appLanguageSimplifiedLabel = stringResource(R.string.app_language_simplified_chinese)
    val appLanguageTraditionalLabel = stringResource(R.string.app_language_traditional_chinese)
    val appLanguageEnglishLabel = stringResource(R.string.app_language_english)
    val appLanguageOptions = remember(
        appLanguageFollowSystemLabel,
        appLanguageSimplifiedLabel,
        appLanguageTraditionalLabel,
        appLanguageEnglishLabel
    ) {
        resolveAppLanguageSegmentOptions(
            followSystemLabel = appLanguageFollowSystemLabel,
            simplifiedChineseLabel = appLanguageSimplifiedLabel,
            traditionalChineseLabel = appLanguageTraditionalLabel,
            englishLabel = appLanguageEnglishLabel
        )
    }
    val selectedAppLanguageLabel = appLanguageOptions
        .firstOrNull { it.value == state.appLanguage }
        ?.label ?: state.appLanguage.name
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = resolveAppearanceBottomPadding(
        navigationBarsBottom = navigationBarBottomPadding,
        expandableSectionEnabled = true
    )
    val compactVideoStatsOnCover by SettingsManager
        .getCompactVideoStatsOnCover(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val dedicatedHomeWallpaperUri by SettingsManager
        .getHomeWallpaperUri(context)
        .collectAsStateWithLifecycle(initialValue = "")
    val splashWallpaperFallbackUri by SettingsManager
        .getSplashWallpaperUri(context)
        .collectAsStateWithLifecycle(initialValue = "")
    val resolvedHomeWallpaperUri = remember(dedicatedHomeWallpaperUri, splashWallpaperFallbackUri) {
        dedicatedHomeWallpaperUri.ifBlank { splashWallpaperFallbackUri }.trim()
    }
    val homeWallpaperFollowsSplash = dedicatedHomeWallpaperUri.isBlank() && splashWallpaperFallbackUri.isNotBlank()
    val homeWallpaperEffectMode by SettingsManager
        .getHomeWallpaperEffectMode(context)
        .collectAsStateWithLifecycle(initialValue = HomeWallpaperEffectMode.SOFT_BLUR)
    val homeWallpaperEffectScope by SettingsManager
        .getHomeWallpaperEffectScope(context)
        .collectAsStateWithLifecycle(initialValue = HomeWallpaperEffectScope.HOME_ONLY)
    val homeWallpaperEffectOptions = remember {
        listOf(
            PlaybackSegmentOption(HomeWallpaperEffectMode.OFF, "关闭"),
            PlaybackSegmentOption(HomeWallpaperEffectMode.SOFT_BLUR, "柔和"),
            PlaybackSegmentOption(HomeWallpaperEffectMode.STRONG_BLUR, "强模糊"),
            PlaybackSegmentOption(HomeWallpaperEffectMode.ORIGINAL, "原图")
        )
    }
    val homeWallpaperEffectScopeOptions = remember {
        listOf(
            PlaybackSegmentOption(HomeWallpaperEffectScope.HOME_ONLY, "仅首页"),
            PlaybackSegmentOption(HomeWallpaperEffectScope.GLOBAL, "全局")
        )
    }
    val bottomBarSearchAutoExpandOptions = remember {
        listOf(
            PlaybackSegmentOption(BottomBarSearchAutoExpandMode.DISABLED, "不自动"),
            PlaybackSegmentOption(BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN, "下滑展开"),
            PlaybackSegmentOption(BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP, "顶部展开")
        )
    }
    val bottomBarSearchLayoutOptions = remember {
        listOf(
            PlaybackSegmentOption(BottomBarSearchLayoutMode.FULL_DOCK, "完整底栏"),
            PlaybackSegmentOption(BottomBarSearchLayoutMode.HOME_AND_SEARCH, "首页+搜索")
        )
    }
    val homeUpBadgesVisible by SettingsManager
        .getHomeUpBadgesVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homeDurationStyle by SettingsManager
        .getHomeDurationStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeDurationStyle.OUTSIDE_COVER)
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.CURRENT)
    val homeHeroCarouselEnabled by SettingsManager
        .getHomeHeroCarouselEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homeHeroCarouselAutoplayEnabled by SettingsManager
        .getHomeHeroCarouselAutoplayEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val commonListHeaderCollapseMode by SettingsManager
        .getCommonListHeaderCollapseMode(context)
        .collectAsStateWithLifecycle(
            initialValue = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
        )
    val commonListHeaderCollapseOptions = remember {
        CommonListHeaderCollapseMode.entries.map { mode ->
            PlaybackSegmentOption(mode, mode.label)
        }
    }
    val themeRoleOverrides by SettingsManager
        .getThemeRoleOverrides(context)
        .collectAsStateWithLifecycle(initialValue = ThemeRoleOverrides())
    val baseThemeRoleOverrides = LocalBaseThemeRoleOverrides.current
    val showOnlineCount by SettingsManager
        .getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val isLiquidGlassAvailable = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val showThemeColorPicker = state.md3ColorSource == Md3ColorSource.CUSTOM
    var showMd3ColorPickerDialog by remember { mutableStateOf(false) }
    var roleColorTarget by remember { mutableStateOf<ThemeRoleColorTarget?>(null) }
    val md3ColorSourceOptions = remember { resolveMd3ColorSourceOptions() }
    val selectedMd3ColorSourceLabel = md3ColorSourceOptions
        .firstOrNull { it.value == state.md3ColorSource }
        ?.label ?: state.md3ColorSource.label
    val selectedCustomThemeColor = remember(state.md3CustomColorHex) {
        parseMd3CustomColorHex(state.md3CustomColorHex)
    }
    val colorStyleOptions = remember { resolveColorStyleOptions() }
    val colorSpecOptions = remember { resolveColorSpecOptions() }
    val selectedColorStyleLabel = colorStyleOptions
        .firstOrNull { it.value == state.colorStyle }
        ?.label ?: state.colorStyle.name
    val selectedColorSpecLabel = colorSpecOptions
        .firstOrNull { it.value == state.colorSpec }
        ?.label ?: state.colorSpec.name
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importAppFontFromUri(context, uri)
            .onSuccess { imported ->
                viewModel.setAppFontFile(imported.fileName, imported.displayName)
                Toast.makeText(context, "已导入字体：${imported.displayName}", Toast.LENGTH_SHORT).show()
            }
            .onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: "字体导入失败，请选择 .ttf / .otf / .ttc 文件",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    EntranceGroup {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize(),
        // [Fix] 为可展开配置项增加安全底部留白，避免“小屏+展开”时显示不全
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        
        //  主题与颜色
        item { 
            Box(modifier = Modifier.entrance()) {
                IOSSectionTitle("显示模式")
            }
        }
        item {
            Box(modifier = Modifier.entrance()) {
                IOSGroup {
                    // 主题模式选择 (横向卡片)
                    Column(modifier = Modifier.padding(16.dp)) {
                        IOSSlidingSegmentedSetting(
                            title = "${uiPresetTitle}：$selectedUiPresetLabel",
                            subtitle = uiPresetSubtitle,
                            options = uiPresetOptions,
                            selectedValue = state.uiPreset,
                            onSelectionChange = { preset ->
                                viewModel.setUiPreset(preset)
                            }
                        )

                        AnimatedVisibility(
                            visible = state.uiPreset == UiPreset.MD3,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                IOSDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                IOSSlidingSegmentedSetting(
                                    title = "${androidNativeVariantTitle}：$selectedAndroidNativeVariantLabel",
                                    subtitle = androidNativeVariantSubtitle,
                                    options = androidNativeVariantOptions,
                                    selectedValue = state.androidNativeVariant,
                                    onSelectionChange = { variant ->
                                        viewModel.setAndroidNativeVariant(variant)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                IOSDivider()
	                             IOSSwitchItem(
	                                icon = rememberSettingsSemanticIcon(SettingsIconRole.ANDROID_LIQUID_GLASS),
                                    title = "安卓原生液态玻璃",
                                    subtitle = if (isLiquidGlassAvailable) {
                                        "全局启用顶部、底栏和评论区复用的液态分段控件"
                                    } else {
                                        "当前 Android 版本暂不支持液态玻璃效果"
                                    },
                                    checked = state.androidNativeLiquidGlassEnabled,
                                    onCheckedChange = { viewModel.toggleAndroidNativeLiquidGlass(it) },
                                    enabled = isLiquidGlassAvailable,
                                    iconTint = iOSBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        AppearanceUiPresetDescriptionCard(
                            title = uiPresetDescription.title,
                            summary = uiPresetDescription.summary
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        IOSDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        IOSSlidingSegmentedSetting(
                            title = "${themeModeTitle}：$selectedThemeModeLabel",
                            subtitle = themeModeSubtitle,
                            options = themeModeOptions,
                            selectedValue = state.themeMode,
                            onSelectionChange = { mode ->
                                viewModel.setThemeMode(mode)
                            }
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.themeMode != AppThemeMode.LIGHT,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                IOSDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                IOSSlidingSegmentedSetting(
                                    title = "${darkThemeStyleTitle}：$selectedDarkThemeStyleLabel",
                                    subtitle = darkThemeStyleSubtitle,
                                    options = darkThemeStyleOptions,
                                    selectedValue = state.darkThemeStyle,
                                    onSelectionChange = { style ->
                                        viewModel.setDarkThemeStyle(style)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        IOSDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        IOSSlidingSegmentedSetting(
                            title = "${appLanguageTitle}：$selectedAppLanguageLabel",
                            subtitle = appLanguageSubtitle,
                            options = appLanguageOptions,
                            selectedValue = state.appLanguage,
                            onSelectionChange = { language ->
                                onAppLanguageChange(language)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        IOSDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        IOSSlidingSegmentedSetting(
                            title = "MD3 颜色来源：$selectedMd3ColorSourceLabel",
                            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "可跟随系统壁纸，也可使用自定义主题色"
                            } else {
                                "当前系统不支持 Monet 壁纸取色，可使用自定义主题色"
                            },
                            options = md3ColorSourceOptions,
                            selectedValue = state.md3ColorSource,
                            onSelectionChange = viewModel::setMd3ColorSource
                        )

                        AnimatedVisibility(
                            visible = state.md3ColorSource == Md3ColorSource.FOLLOW_WALLPAPER,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                DynamicColorPreview()
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        IOSDivider()
                        IOSClickableItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_COLOR),
                            title = "自定义 MD3 颜色",
                            subtitle = if (state.md3ColorSource == Md3ColorSource.CUSTOM) {
                                "使用 HSV 取色器或 HEX 输入精确选择"
                            } else {
                                "当前跟随系统壁纸；确认后切换为自定义颜色"
                            },
                            value = state.md3CustomColorHex,
                            onClick = { showMd3ColorPickerDialog = true },
                            iconTint = selectedCustomThemeColor
                        )

                        IOSDivider()
                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.ADVANCED_COLOR),
                            title = "高级配色",
                            subtitle = "分别覆盖明暗模式的背景、文字与控件色",
                            checked = themeRoleOverrides.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SettingsManager.setThemeRoleOverrides(
                                        context,
                                        if (enabled) {
                                            baseThemeRoleOverrides.copy(enabled = true)
                                        } else {
                                            themeRoleOverrides.copy(enabled = false)
                                        }
                                    )
                                }
                            },
                            iconTint = MaterialTheme.colorScheme.primary
                        )

                        AnimatedVisibility(
                            visible = themeRoleOverrides.enabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            ThemeRoleOverrideEditor(
                                overrides = themeRoleOverrides,
                                onColorClick = { roleColorTarget = it }
                            )
                        }

                        IOSDivider()
	                        ThemePresetDropdownSetting(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.COLOR_STYLE),
                            title = "色彩风格",
                            selectedLabel = selectedColorStyleLabel,
                            options = colorStyleOptions,
                            onSelectionChange = viewModel::setThemeColorStyle,
                            iconTint = iOSPurple
                        )

                        IOSDivider()
	                        ThemePresetDropdownSetting(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.COLOR_SPEC),
                            title = "色彩标准",
                            selectedLabel = selectedColorSpecLabel,
                            options = colorSpecOptions,
                            onSelectionChange = viewModel::setThemeColorSpec,
                            iconTint = iOSBlue
                        )

                        // 主题色选择 (仅当动态取色关闭时显示)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showThemeColorPicker,
                            enter =   androidx.compose.animation.expandVertically() +   androidx.compose.animation.fadeIn(),
                            exit =   androidx.compose.animation.shrinkVertically() +   androidx.compose.animation.fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                //  Theme Color Label
                                Text(
                                    "主题色", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                //  [新增] 实时主题色预览
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 24.dp)
                                        .height(140.dp)
                                        .clip(AppShapes.container(ContainerLevel.Sheet))
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    selectedCustomThemeColor.copy(alpha = 0.15f),
                                                    selectedCustomThemeColor.copy(alpha = 0.05f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = selectedCustomThemeColor.copy(alpha = 0.3f),
                                            shape = AppShapes.container(ContainerLevel.Sheet)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        // 模拟应用图标/Logo
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .padding(bottom = 12.dp)
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                        colors = listOf(
                                                            selectedCustomThemeColor,
                                                            selectedCustomThemeColor.copy(alpha = 0.8f)
                                                        )
                                                    ),
                                                    shape = AppShapes.container(ContainerLevel.Dialog)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                CupertinoIcons.Filled.Play,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        
                                        // 当前选中颜色名称
                                        Text(
                                            text = state.md3CustomColorHex,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "正在预览自定义 MD3 主题色",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                //  [Redesign] Theme Color Grid - Strict 2 Rows x 5 Columns
                                val spacing = 12.dp
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp) // 增加行间距以容纳文字
                                ) {
                                    ThemeColors.chunked(5).forEach { rowColors ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(spacing)
                                        ) {
                                            rowColors.forEach { color ->
                                                val index = ThemeColors.indexOf(color)
                                                val isSelected = selectedCustomThemeColor == color
                                                
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    // 选中状态动画
                                                    val scale by androidx.compose.animation.core.animateFloatAsState(
                                                        targetValue = if (isSelected) 1.1f else 1.0f,
                                                        label = "scale",
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                    )
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .aspectRatio(1f) // Ensure square aspect ratio for perfect circles
                                                            .graphicsLayer {
                                                                scaleX = scale
                                                                scaleY = scale
                                                            }
                                                            // 选中时的外光环 (圆形)
                                                            .border(
                                                                width = if (isSelected) 2.dp else 0.dp,
                                                                color = if (isSelected) color.copy(alpha = 0.5f) else Color.Transparent,
                                                                shape = CircleShape
                                                            )
                                                            .padding(3.dp) // 光环与色块的间距
                                                            .clip(CircleShape) // 裁剪为圆形
                                                            .background(
                                                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                                                    colors = listOf(
                                                                        color.copy(alpha = 0.9f), // 中心稍亮
                                                                        color // 边缘原色
                                                                    ),
                                                                    center = androidx.compose.ui.geometry.Offset.Unspecified,
                                                                    radius = Float.POSITIVE_INFINITY
                                                                )
                                                            )
                                                            // 添加个内部高光，增加球体质感
                                                            .background(
                                                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                                    colors = listOf(
                                                                        Color.White.copy(alpha = 0.2f),
                                                                        Color.Transparent
                                                                    ),
                                                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                                    end = androidx.compose.ui.geometry.Offset(100f, 100f)
                                                                )
                                                            )
                                                            .clickable {
                                                                viewModel.setThemeColorIndex(index)
                                                                viewModel.setMd3CustomColorHex(formatMd3CustomColorHex(color))
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        androidx.compose.animation.AnimatedVisibility(
                                                            visible = isSelected,
                                                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                                                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                                                        ) {
                                                            Icon(
                                                                CupertinoIcons.Default.Checkmark,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    // 颜色名称
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = ThemeColorNames.getOrElse(index) { "" },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            
                                            // Fill empty spots if last row has fewer than 5 items
                                            if (rowColors.size < 5) {
                                                repeat(5 - rowColors.size) {
                                                     Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.entrance()) {
                IOSSectionTitle("字体与密度")
            }
        }
        item {
            Box(modifier = Modifier.entrance()) {
                IOSGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        IOSSlidingSegmentedSetting(
                            title = "字体大小：${state.appFontSizePreset.label}",
                            subtitle = "仅调整应用内文字比例",
                            options = resolveAppFontSizeSegmentOptions(),
                            selectedValue = state.appFontSizePreset,
                            onSelectionChange = { preset ->
                                viewModel.setAppFontSizePreset(preset)
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        IOSDivider()
	                        IOSClickableItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.FONT_FILE),
                            title = "应用字体",
                            subtitle = if (state.appFontDisplayName.isBlank()) {
                                "使用系统默认字体，或从本地导入 .ttf / .otf / .ttc"
                            } else {
                                "当前：${state.appFontDisplayName}"
                            },
                            value = if (state.appFontDisplayName.isBlank()) "默认" else "更换",
                            onClick = {
                                fontPickerLauncher.launch(arrayOf("*/*"))
                            },
                            iconTint = iOSPurple
                        )

                        AnimatedVisibility(
                            visible = state.appFontFileName.isNotBlank(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                IOSDivider()
	                                IOSClickableItem(
	                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.REPLAY_ONBOARDING),
                                    title = "恢复默认字体",
                                    subtitle = "移除已导入字体文件，立即回到系统字体",
                                    onClick = {
                                        deleteStoredAppFont(context, state.appFontFileName)
                                        viewModel.clearAppFontFile()
                                        Toast.makeText(context, "已恢复默认字体", Toast.LENGTH_SHORT).show()
                                    },
                                    iconTint = iOSOrange,
                                    showChevron = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        IOSDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        IOSSlidingSegmentedSetting(
                            title = "界面缩放：${state.appUiScalePreset.label}",
                            subtitle = "调整列表、卡片与控件的整体密度",
                            options = resolveAppUiScaleSegmentOptions(),
                            selectedValue = state.appUiScalePreset,
                            onSelectionChange = { preset ->
                                viewModel.setAppUiScalePreset(preset)
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        IOSDivider()
                        Spacer(modifier = Modifier.height(8.dp))

	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DISPLAY_STYLE),
                            title = "应用内 DPI 覆盖",
                            subtitle = resolveDpiOverrideSubtitle(
                                systemDensityDpi = displayMetricsSnapshot.systemDensityDpi,
                                systemSmallestWidthDp = displayMetricsSnapshot.systemSmallestWidthDp,
                                currentOverridePercent = state.appDpiOverridePercent
                            ),
                            checked = state.appDpiOverridePercent > 0,
                            onCheckedChange = { enabled ->
                                viewModel.setAppDpiOverridePercent(
                                    if (enabled) DEFAULT_APP_DPI_OVERRIDE_PERCENT else 0
                                )
                            },
                            iconTint = iOSTeal
                        )

                        AnimatedVisibility(
                            visible = state.appDpiOverridePercent > 0,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                IOSSlidingSegmentedSetting(
                                    title = "应用 DPI：${resolveDisplayedAppDpiPercent(state.appDpiOverridePercent)}%",
                                    subtitle = "按当前设备 DPI 进行应用内覆盖，不修改系统设置",
                                    options = resolveAppDpiOverrideSegmentOptions(),
                                    selectedValue = resolveDisplayedAppDpiPercent(state.appDpiOverridePercent),
                                    onSelectionChange = { percent ->
                                        viewModel.setAppDpiOverridePercent(percent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = resolveDisplayMetricsSummary(displayMetricsSnapshot),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        //  启动画面
        item { 
            Box(modifier = Modifier.entrance()) {
                IOSSectionTitle("启动画面") 
            }
        }
        item {
            Box(modifier = Modifier.entrance()) {
                IOSGroup {
                    val isSplashEnabled by com.android.purebilibili.core.store.SettingsManager.isSplashEnabled(context).collectAsStateWithLifecycle(initialValue = false)
                    val splashRandomEnabled by com.android.purebilibili.core.store.SettingsManager.getSplashRandomEnabled(context).collectAsStateWithLifecycle(initialValue = false)
                    val splashRandomPoolUris by com.android.purebilibili.core.store.SettingsManager.getSplashRandomPoolUris(context).collectAsStateWithLifecycle(initialValue = emptyList())
                    val splashIconAnimationEnabled by com.android.purebilibili.core.store.SettingsManager.getSplashIconAnimationEnabled(context).collectAsStateWithLifecycle(initialValue = true)
                    val splashWallpaperUri by com.android.purebilibili.core.store.SettingsManager.getSplashWallpaperUri(context).collectAsStateWithLifecycle(initialValue = null)
                    val hasSplashWallpaper = !splashWallpaperUri.isNullOrBlank()
                    val splashRandomPoolPreview = remember(splashRandomPoolUris) {
                        resolveSplashRandomPoolPreviewState(poolUris = splashRandomPoolUris)
                    }
                    
                    // 开关项
	                    IOSSwitchItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.SPLASH_WALLPAPER),
                        title = "使用开屏壁纸",
                        subtitle = "应用启动时显示官方或相册壁纸",
                        checked = isSplashEnabled,
                        onCheckedChange = { viewModel.toggleSplashEnabled(it) },
                        iconTint = com.android.purebilibili.core.theme.iOSBlue
                    )

                    IOSDivider()
	                    IOSSwitchItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.RANDOM_WALLPAPER),
                        title = "随机展示开屏壁纸",
                        subtitle = "启动时从可见官方壁纸中随机展示",
                        checked = splashRandomEnabled,
                        onCheckedChange = { viewModel.toggleSplashRandomEnabled(it) },
                        iconTint = com.android.purebilibili.core.theme.iOSGreen
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSplashEnabled && splashRandomEnabled,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "随机池预览",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${splashRandomPoolPreview.totalCount} 张",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (splashRandomPoolPreview.previewUris.isEmpty()) {
                                Text(
                                    text = "暂无可见壁纸，请先进入“选择开屏壁纸”加载列表",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    splashRandomPoolPreview.previewUris.forEach { previewUri ->
                                        AsyncImage(
                                            model = coil.request.ImageRequest.Builder(context)
                                                .data(previewUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 42.dp, height = 72.dp)
                                                .clip(AppShapes.container(ContainerLevel.Field))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                }
                                if (splashRandomPoolPreview.totalCount > splashRandomPoolPreview.previewUris.size) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "还有 ${splashRandomPoolPreview.totalCount - splashRandomPoolPreview.previewUris.size} 张",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    IOSDivider()
	                    IOSSwitchItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.ANIMATION),
                        title = "开屏图标遮罩动画",
                        subtitle = "关闭后不保留图标页，不播放遮罩和飞出动画",
                        checked = splashIconAnimationEnabled,
                        onCheckedChange = { viewModel.toggleSplashIconAnimationEnabled(it) },
                        iconTint = com.android.purebilibili.core.theme.iOSPink
                    )
                    
                    // 当开启时，显示选择壁纸入口
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSplashEnabled,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column {
                            IOSDivider()
                            
                            var showWallpaperPicker by remember { mutableStateOf(false) }
                            
                            // 选择壁纸按钮
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showWallpaperPicker = true }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 壁纸缩略图预览
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(AppShapes.container(ContainerLevel.Field))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (hasSplashWallpaper) {
                                        AsyncImage(
                                            model = coil.request.ImageRequest.Builder(context)
                                                .data(splashWallpaperUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                CupertinoIcons.Default.Photo,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "选择开屏壁纸",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (hasSplashWallpaper) "已设置壁纸，可从官方库或相册更换" else "从官方壁纸库或相册选择",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Icon(
                                    CupertinoIcons.Default.ChevronForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            // 壁纸选择 Sheet
                            if (showWallpaperPicker) {
                                com.android.purebilibili.feature.profile.SplashWallpaperPickerSheet(
                                    onDismiss = { showWallpaperPicker = false }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        //  个性化
        item { 
            Box(modifier = Modifier.entrance()) {
                IOSSectionTitle("开屏与图标")
            }
        }
        item {
            Box(modifier = Modifier.entrance()) {
                IOSGroup {
                    // 图标设置
	                    IOSClickableItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.APP_ICON),
                        title = "应用图标",
                        value = when(state.appIcon) {
                            // 🎀 二次元少女系列
                            "BiliPai", "icon_bilipai" -> "BiliPai"
                            "BiliPai Pink", "icon_bilipai_pink" -> "BiliPai 粉"
                            "BiliPai White", "icon_bilipai_white" -> "BiliPai 白"
                            "BiliPai Monet", "icon_bilipai_monet" -> "BiliPai Monet"
                            "Yuki" -> "比心少女"
                            "Anime", "icon_anime" -> "蓝发电视"
                            "Headphone" -> "耳机少女"
                            // 经典系列
                            "3D", "icon_3d" -> "3D立体"
                            "Flat", "icon_flat" -> "扁平现代"
                            "Telegram Blue", "icon_telegram_blue" -> "纸飞机蓝"
                            "Dark", "icon_telegram_dark" -> "暗夜蓝"
                            else -> "3D立体"  // 默认显示 3D立体 (对应默认 icon_3d)
                        },
                        onClick = onNavigateToIconSettings,
                        iconTint = iOSPurple
                    )
                    IOSDivider()
                    // 动画设置
	                    IOSClickableItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.ANIMATION),
                        title = "动画与效果",
                        value = if (state.cardAnimationEnabled) "已开启" else "已关闭",
                        onClick = onNavigateToAnimationSettings,
                        iconTint = iOSPink
                    )

                    IOSDivider()
	                    IOSSwitchItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.OPEN_LINKS),
                        title = "底栏搜索入口",
                        subtitle = "在悬浮底栏右侧显示搜索入口",
                        checked = state.bottomBarSearchEnabled,
                        onCheckedChange = { viewModel.toggleBottomBarSearch(it) },
                        iconTint = iOSTeal
                    )

                    IOSDivider()
                    IOSSlidingSegmentedSetting(
                        title = "底栏搜索布局",
                        subtitle = "完整底栏保留全部入口；首页+搜索只保留首页刷新和搜索",
                        options = bottomBarSearchLayoutOptions,
                        selectedValue = state.bottomBarSearchLayoutMode,
                        enabled = state.bottomBarSearchEnabled,
                        onSelectionChange = { viewModel.setBottomBarSearchLayoutMode(it) }
                    )

                    IOSDivider()
                    IOSSlidingSegmentedSetting(
                        title = "搜索框自动展开",
                        subtitle = "选择回到首页顶部或向下浏览时自动展开",
                        options = bottomBarSearchAutoExpandOptions,
                        selectedValue = state.bottomBarSearchAutoExpandMode,
                        enabled = state.bottomBarSearchEnabled,
                        onSelectionChange = { viewModel.setBottomBarSearchAutoExpandMode(it) }
                    )

                    IOSDivider()
                    // 触感反馈
	                    IOSSwitchItem(
	                        icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_GESTURE),
                        title = "触感反馈",
                        checked = state.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.toggleHapticFeedback(it) },
                        iconTint = iOSBlue
                    )
                }
            }
        } // End of Personalization item

            //  首页与列表
            item { 
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("首页与列表")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
                        val displayMode = state.displayMode
                        var isExpanded by remember { mutableStateOf(false) }
                        val displayModeBringIntoViewRequester = remember { BringIntoViewRequester() }
                        LaunchedEffect(isExpanded) {
                            if (shouldBringDisplayModeIntoView(isExpanded)) {
                                delay(120)
                                displayModeBringIntoViewRequester.bringIntoView()
                            }
                        }
                        
                        // 当前选中模式的名称
                        val currentModeName = DisplayMode.entries.find { it.value == displayMode }?.title ?: "双列网格"
                        
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .bringIntoViewRequester(displayModeBringIntoViewRequester)
                        ) {
                            // 标题行 - 可点击展开/收起
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.container(ContainerLevel.Field))
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
	                                Icon(
	                                    rememberSettingsSemanticIcon(SettingsIconRole.DISPLAY_STYLE),
                                    contentDescription = null,
                                    tint = displayModeTint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "展示样式",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currentModeName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // 展开后的选项 - 带动画
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isExpanded,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DisplayMode.entries.forEach { mode ->
                                        val isSelected = displayMode == mode.value
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(AppShapes.container(ContainerLevel.Field))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                                .clickable {
                                                    viewModel.setDisplayMode(mode.value)
                                                    isExpanded = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    mode.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                            else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    mode.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    CupertinoIcons.Default.Checkmark,
                                                    contentDescription = "已选择",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
                        Column(modifier = Modifier.padding(16.dp)) {
                            IOSSlidingSegmentedSetting(
                                title = "列表顶部栏：${commonListHeaderCollapseMode.label}",
                                subtitle = commonListHeaderCollapseMode.description,
                                options = commonListHeaderCollapseOptions,
                                selectedValue = commonListHeaderCollapseMode,
                                onSelectionChange = { mode ->
                                    scope.launch {
                                        SettingsManager.setCommonListHeaderCollapseMode(context, mode)
                                    }
                                }
                            )
                        }

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_CARD_STATS_COMPACT),
                            title = "统计信息贴封面（紧凑）",
                            subtitle = if (compactVideoStatsOnCover) {
                                "播放量和评论数显示在封面底部，缩小卡片间距"
                            } else {
                                "播放量和评论数显示在封面外部"
                            },
                            checked = compactVideoStatsOnCover,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setCompactVideoStatsOnCover(context, it)
                                }
                            },
                            iconTint = iOSTeal
                        )

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DISPLAY_STYLE),
                            title = "首页顶部轮播封面",
                            subtitle = if (homeHeroCarouselEnabled) {
                                "推荐页顶部显示官方比例的视频封面轮播"
                            } else {
                                "推荐页直接显示普通视频流"
                            },
                            checked = homeHeroCarouselEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setHomeHeroCarouselEnabled(context, it)
                                }
                            },
                            iconTint = iOSBlue
                        )

                        AnimatedVisibility(visible = homeHeroCarouselEnabled) {
                            Column {
                                IOSDivider(modifier = Modifier.padding(start = 16.dp))
                                IOSSwitchItem(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_PLAY_ON_OPEN),
                                    title = "轮播默认播放",
                                    subtitle = if (homeHeroCarouselAutoplayEnabled) {
                                        "当前轮播项进入视野后静音循环播放"
                                    } else {
                                        "默认只展示封面，点开后进入视频详情"
                                    },
                                    checked = homeHeroCarouselAutoplayEnabled,
                                    onCheckedChange = {
                                        scope.launch {
                                            SettingsManager.setHomeHeroCarouselAutoplayEnabled(context, it)
                                        }
                                    },
                                    iconTint = iOSBlue
                                )
                            }
                        }

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
                        Column(modifier = Modifier.padding(16.dp)) {
                            IOSSlidingSegmentedSetting(
                                title = "首页卡片样式：${homeFeedCardStyle.label}",
                                subtitle = "当前样式使用 16:9；官方样式使用更宽的 4:3 卡片与紧凑信息区",
                                options = HomeFeedCardStyle.entries.map {
                                    PlaybackSegmentOption(it, it.label)
                                },
                                selectedValue = homeFeedCardStyle,
                                onSelectionChange = {
                                    scope.launch {
                                        SettingsManager.setHomeFeedCardStyle(context, it)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            IOSSlidingSegmentedSetting(
                                title = "首页视频时长：${homeDurationStyle.label}",
                                subtitle = "可移到封面外、仅显示无底色文字或完全隐藏",
                                options = HomeDurationStyle.entries.map {
                                    PlaybackSegmentOption(it, it.label)
                                },
                                selectedValue = homeDurationStyle,
                                onSelectionChange = {
                                    scope.launch {
                                        SettingsManager.setHomeDurationStyle(context, it)
                                    }
                                }
                            )
                        }

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
                        var showHomeWallpaperPicker by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHomeWallpaperPicker = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(AppShapes.container(ContainerLevel.Field))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (resolvedHomeWallpaperUri.isNotBlank()) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(resolvedHomeWallpaperUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
	                                        Icon(
	                                            rememberSettingsSemanticIcon(SettingsIconRole.HOME_WALLPAPER),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "选择首页壁纸",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        dedicatedHomeWallpaperUri.isNotBlank() -> "已单独设置首页壁纸"
                                        homeWallpaperFollowsSplash -> "未单独设置，当前跟随开屏壁纸"
                                        else -> "从官方壁纸库或相册选择"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                CupertinoIcons.Default.ChevronForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showHomeWallpaperPicker) {
                            com.android.purebilibili.feature.profile.SplashWallpaperPickerSheet(
                                target = com.android.purebilibili.feature.profile.WallpaperPickerTarget.HOME,
                                onDismiss = { showHomeWallpaperPicker = false }
                            )
                        }

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
                        IOSSlidingSegmentedSetting(
                            title = "首页壁纸效果",
                            subtitle = when (homeWallpaperEffectMode) {
                                HomeWallpaperEffectMode.OFF -> "首页不使用开屏壁纸作为背景"
                                HomeWallpaperEffectMode.SOFT_BLUR -> "真实壁纸轻微模糊，卡片信息区半透明接入壁纸"
                                HomeWallpaperEffectMode.STRONG_BLUR -> "更强模糊和更稳遮罩，保留壁纸色彩但降低细节干扰"
                                HomeWallpaperEffectMode.ORIGINAL -> "直接接入真实壁纸，文字区使用更轻的保护层"
                            },
                            options = homeWallpaperEffectOptions,
                            selectedValue = homeWallpaperEffectMode,
                            onSelectionChange = { mode ->
                                scope.launch {
                                    SettingsManager.setHomeWallpaperEffectMode(context, mode)
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = homeWallpaperEffectMode != HomeWallpaperEffectMode.OFF,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                IOSDivider(modifier = Modifier.padding(start = 16.dp))
                                IOSSlidingSegmentedSetting(
                                    title = "壁纸作用范围",
                                    subtitle = when (homeWallpaperEffectScope) {
                                        HomeWallpaperEffectScope.HOME_ONLY -> "仅首页使用该壁纸背景效果"
                                        HomeWallpaperEffectScope.GLOBAL -> "全局页面复用同一壁纸背景，默认背景层会半透明保护文字"
                                    },
                                    options = homeWallpaperEffectScopeOptions,
                                    selectedValue = homeWallpaperEffectScope,
                                    onSelectionChange = { scopeValue ->
                                        scope.launch {
                                            SettingsManager.setHomeWallpaperEffectScope(context, scopeValue)
                                        }
                                    }
                                )
                            }
                        }

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_UP_BADGES),
                            title = "UP主标识",
                            subtitle = if (homeUpBadgesVisible) {
                                "首页和相关推荐显示 UP 标识"
                            } else {
                                "首页和相关推荐隐藏 UP 标识"
                            },
                            checked = homeUpBadgesVisible,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setHomeUpBadgesVisible(context, it)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSBlue
                        )

                        IOSDivider(modifier = Modifier.padding(start = 16.dp))
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.ONLINE_COUNT),
                            title = "卡片与视频页观看人数",
                            subtitle = if (showOnlineCount) {
                                "首页、搜索等视频卡片和视频页显示“xx人正在看”"
                            } else {
                                "关闭后隐藏卡片和视频页的同时观看人数"
                            },
                            checked = showOnlineCount,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setShowOnlineCount(context, it)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSPurple
                        )
                        
                        // 网格列数设置 (仅在双列网格模式下显示)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isTablet && state.displayMode == 0,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column {
                                IOSDivider(modifier = Modifier.padding(start = 16.dp))
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Icon(
                                            CupertinoIcons.Default.ListBullet,
                                            contentDescription = null,
                                            tint = com.android.purebilibili.core.theme.iOSBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = "网格列数",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (state.gridColumnCount == 0) "自适应 (默认)" else "固定 ${state.gridColumnCount} 列",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    // 列数选择器
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        item {
                                            // 自动
                                            val isSelected = state.gridColumnCount == 0
                                            Box(
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .clip(AppShapes.container(ContainerLevel.Field))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                    .clickable { viewModel.setGridColumnCount(0) }
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "自动",
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                        items(6, key = { it }) { i ->
                                            val count = i + 1
                                            val isSelected = state.gridColumnCount == count
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp) // Square for numbers
                                                    .clip(AppShapes.container(ContainerLevel.Field))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                    .clickable { viewModel.setGridColumnCount(count) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$count",
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    IOSSlidingSegmentedSetting(
                                        title = "推荐流卡片宽度：${state.homeFeedCardWidthPreset.label}",
                                        subtitle = if (state.gridColumnCount > 0) {
                                            "当前固定 ${state.gridColumnCount} 列优先生效，自动列数时使用该宽度"
                                        } else {
                                            "自动列数时控制首页推荐卡片的最小宽度"
                                        },
                                        options = resolveHomeFeedCardWidthPresetSegmentOptions(),
                                        selectedValue = state.homeFeedCardWidthPreset,
                                        onSelectionChange = viewModel::setHomeFeedCardWidthPreset
                                    )
                                }
                            }
                        }
                    }
                }
            }
        

    }
    }

    if (showMd3ColorPickerDialog) {
        Md3CustomColorPickerDialog(
            initialHex = state.md3CustomColorHex,
            onDismiss = { showMd3ColorPickerDialog = false },
            onConfirm = { hex ->
                viewModel.setMd3ColorSource(Md3ColorSource.CUSTOM)
                viewModel.setMd3CustomColorHex(hex)
                showMd3ColorPickerDialog = false
            }
        )
    }

    roleColorTarget?.let { target ->
        Md3CustomColorPickerDialog(
            initialHex = target.read(themeRoleOverrides),
            onDismiss = { roleColorTarget = null },
            onConfirm = { hex ->
                scope.launch {
                    SettingsManager.setThemeRoleOverrides(
                        context,
                        target.write(themeRoleOverrides, hex)
                    )
                }
                roleColorTarget = null
            }
        )
    }
}

internal enum class ThemeRoleColorTarget(val label: String) {
    LIGHT_BACKGROUND("浅色背景"),
    LIGHT_PRIMARY_TEXT("浅色主要文字"),
    LIGHT_SECONDARY_TEXT("浅色次要文字"),
    LIGHT_CONTROL("浅色控件"),
    DARK_BACKGROUND("深色背景"),
    DARK_PRIMARY_TEXT("深色主要文字"),
    DARK_SECONDARY_TEXT("深色次要文字"),
    DARK_CONTROL("深色控件");

    fun read(overrides: ThemeRoleOverrides): String = when (this) {
        LIGHT_BACKGROUND -> overrides.light.backgroundHex
        LIGHT_PRIMARY_TEXT -> overrides.light.primaryTextHex
        LIGHT_SECONDARY_TEXT -> overrides.light.secondaryTextHex
        LIGHT_CONTROL -> overrides.light.controlAccentHex
        DARK_BACKGROUND -> overrides.dark.backgroundHex
        DARK_PRIMARY_TEXT -> overrides.dark.primaryTextHex
        DARK_SECONDARY_TEXT -> overrides.dark.secondaryTextHex
        DARK_CONTROL -> overrides.dark.controlAccentHex
    }

    fun write(overrides: ThemeRoleOverrides, hex: String): ThemeRoleOverrides {
        return when (this) {
            LIGHT_BACKGROUND -> overrides.copy(light = overrides.light.copy(backgroundHex = hex))
            LIGHT_PRIMARY_TEXT -> overrides.copy(light = overrides.light.copy(primaryTextHex = hex))
            LIGHT_SECONDARY_TEXT -> overrides.copy(light = overrides.light.copy(secondaryTextHex = hex))
            LIGHT_CONTROL -> overrides.copy(light = overrides.light.copy(controlAccentHex = hex))
            DARK_BACKGROUND -> overrides.copy(dark = overrides.dark.copy(backgroundHex = hex))
            DARK_PRIMARY_TEXT -> overrides.copy(dark = overrides.dark.copy(primaryTextHex = hex))
            DARK_SECONDARY_TEXT -> overrides.copy(dark = overrides.dark.copy(secondaryTextHex = hex))
            DARK_CONTROL -> overrides.copy(dark = overrides.dark.copy(controlAccentHex = hex))
        }
    }
}

@Composable
private fun ThemeRoleOverrideEditor(
    overrides: ThemeRoleOverrides,
    onColorClick: (ThemeRoleColorTarget) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        ThemeRoleModeEditor(
            title = "浅色模式",
            roles = overrides.light,
            targets = ThemeRoleColorTarget.entries.take(4),
            onColorClick = onColorClick
        )
        IOSDivider()
        ThemeRoleModeEditor(
            title = "深色模式",
            roles = overrides.dark,
            targets = ThemeRoleColorTarget.entries.takeLast(4),
            onColorClick = onColorClick
        )
    }
}

@Composable
internal fun ThemeRoleModeEditor(
    title: String,
    roles: ThemeModeRoleOverrides,
    targets: List<ThemeRoleColorTarget>,
    onColorClick: (ThemeRoleColorTarget) -> Unit
) {
    val warning = remember(roles) { hasThemeRoleContrastWarning(roles) }
    val primaryContrast = remember(roles) {
        themeRoleContrastRatio(roles.primaryTextHex, roles.backgroundHex)
    }
    val secondaryContrast = remember(roles) {
        themeRoleContrastRatio(roles.secondaryTextHex, roles.backgroundHex)
    }
    val colors = listOf(
        roles.backgroundHex,
        roles.primaryTextHex,
        roles.secondaryTextHex,
        roles.controlAccentHex
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { hex ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(AppShapes.container(ContainerLevel.Field))
                        .background(parseMd3CustomColorHex(hex))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            AppShapes.container(ContainerLevel.Field)
                        )
                )
            }
        }
        targets.forEachIndexed { index, target ->
            IOSClickableItem(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_COLOR),
                title = target.label,
                subtitle = colors[index],
                value = colors[index],
                onClick = { onColorClick(target) },
                iconTint = parseMd3CustomColorHex(colors[index])
            )
        }
        Text(
            text = "对比度：主要文字 %.2f:1，次要文字 %.2f:1".format(
                primaryContrast,
                secondaryContrast
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (warning) {
            Text(
                text = "当前文字与背景对比度偏低，仍可按精确颜色保存。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun Md3CustomColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val controller = rememberColorPickerController()
    val haptic = rememberHapticFeedback()
    var pendingHex by remember(initialHex) { mutableStateOf(normalizeMd3CustomColorHex(initialHex)) }
    var lastSelectionHapticAtMs by remember { mutableLongStateOf(0L) }
    val pendingColor = remember(pendingHex) { parseMd3CustomColorHex(pendingHex) }
    val invalidInput = normalizeMd3CustomColorHex(pendingHex) != pendingHex.uppercase()
    val sliderPositions = remember(pendingColor) { resolveMd3ColorPickerSliderPositions(pendingColor) }

    fun emitSelectionHapticIfNeeded() {
        val nowMs = SystemClock.elapsedRealtime()
        if (shouldEmitMd3ColorPickerSelectionHaptic(lastSelectionHapticAtMs, nowMs)) {
            haptic(HapticType.SELECTION)
            lastSelectionHapticAtMs = nowMs
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    haptic(HapticType.LIGHT)
                    onConfirm(normalizeMd3CustomColorHex(pendingHex))
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic(HapticType.LIGHT)
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        },
        title = { Text("自定义 MD3 颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(AppShapes.container(ContainerLevel.Dialog))
                        .background(pendingColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = normalizeMd3CustomColorHex(pendingHex),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (pendingColor.luminance() < 0.5f) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    controller = controller,
                    initialColor = pendingColor,
                    onStart = { emitSelectionHapticIfNeeded() },
                    onColorChanged = { envelope ->
                        if (envelope.fromUser) {
                            val nextHex = formatMd3CustomColorHex(envelope.color)
                            if (nextHex != pendingHex) {
                                pendingHex = nextHex
                                emitSelectionHapticIfNeeded()
                            }
                        }
                    }
                )

                Md3ColorPickerSliderFrame(position = sliderPositions.hue) {
                    HueSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(resolveMd3ColorPickerSliderLayout().trackHeight),
                        controller = controller,
                        wheelRadius = 0.dp,
                        wheelAlpha = 0f,
                        onStart = { emitSelectionHapticIfNeeded() }
                    )
                }
                Md3ColorPickerSliderFrame(position = sliderPositions.saturation) {
                    SaturationSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(resolveMd3ColorPickerSliderLayout().trackHeight),
                        controller = controller,
                        wheelRadius = 0.dp,
                        wheelAlpha = 0f,
                        onStart = { emitSelectionHapticIfNeeded() }
                    )
                }
                Md3ColorPickerSliderFrame(position = sliderPositions.brightness) {
                    BrightnessSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(resolveMd3ColorPickerSliderLayout().trackHeight),
                        controller = controller,
                        wheelRadius = 0.dp,
                        wheelAlpha = 0f,
                        onStart = { emitSelectionHapticIfNeeded() }
                    )
                }

                IOSAdaptiveTextField(
                    value = pendingHex,
                    onValueChange = { pendingHex = it.uppercase().take(9) },
                    label = "HEX",
                    singleLine = true,
                    isError = invalidInput,
                    supportingText = {
                        if (invalidInput) {
                            Text("请输入 #RRGGBB 格式")
                        }
                    }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ThemeColors.size, key = { it }) { index ->
                        val color = ThemeColors[index]
                        val hex = formatMd3CustomColorHex(color)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (normalizeMd3CustomColorHex(pendingHex) == hex) 2.dp else 1.dp,
                                    color = if (normalizeMd3CustomColorHex(pendingHex) == hex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .clickable {
                                    pendingHex = hex
                                    haptic(HapticType.SELECTION)
                                }
                        )
                    }
                }
            }
        }
    )
}

internal data class Md3ColorPickerSliderLayout(
    val trackHeight: Dp,
    val frameHeight: Dp,
    val thumbRadius: Dp,
    val horizontalPadding: Dp
)

internal fun resolveMd3ColorPickerSliderLayout(): Md3ColorPickerSliderLayout =
    Md3ColorPickerSliderLayout(
        trackHeight = 28.dp,
        frameHeight = 36.dp,
        thumbRadius = 14.dp,
        horizontalPadding = 14.dp
    )

private const val MD3_COLOR_PICKER_HAPTIC_MIN_INTERVAL_MS = 72L

internal fun shouldEmitMd3ColorPickerSelectionHaptic(
    lastFeedbackAtMs: Long,
    nowMs: Long,
    minIntervalMs: Long = MD3_COLOR_PICKER_HAPTIC_MIN_INTERVAL_MS
): Boolean {
    return lastFeedbackAtMs <= 0L || nowMs - lastFeedbackAtMs >= minIntervalMs
}

private data class Md3ColorPickerSliderPositions(
    val hue: Float,
    val saturation: Float,
    val brightness: Float
)

private fun resolveMd3ColorPickerSliderPositions(color: Color): Md3ColorPickerSliderPositions {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return Md3ColorPickerSliderPositions(
        hue = (hsv[0] / 360f).coerceIn(0f, 1f),
        saturation = hsv[1].coerceIn(0f, 1f),
        brightness = hsv[2].coerceIn(0f, 1f)
    )
}

@Composable
private fun Md3ColorPickerSliderFrame(
    position: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layout = resolveMd3ColorPickerSliderLayout()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(layout.frameHeight)
    ) {
        val thumbDiameter = layout.thumbRadius * 2
        val thumbTravelWidth = if (maxWidth > thumbDiameter) maxWidth - thumbDiameter else 0.dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = layout.horizontalPadding)
                .fillMaxWidth()
                .height(layout.trackHeight)
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbTravelWidth * position.coerceIn(0f, 1f))
                .size(thumbDiameter)
                .background(Color.White, CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun AppearanceUiPresetDescriptionCard(
    title: String,
    summary: String
) {
    val icon = rememberAppSparklesIcon()
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Surface(
        shape = AppShapes.container(ContainerLevel.Dialog),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f)
                )
            }
        }
    }
}

internal fun restartApp(context: android.content.Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    (context as? android.app.Activity)?.finishAffinity()
    context.startActivity(launchIntent)
}


/**
 *  动态取色预览组件
 * 显示从壁纸提取的 Material You 颜色
 */


@Composable
fun DynamicColorPreview() {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "当前取色预览",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary
            ColorPreviewItem(
                color = colorScheme.primary,
                label = "主色",
                modifier = Modifier.weight(1f)
            )
            // Secondary
            ColorPreviewItem(
                color = colorScheme.secondary,
                label = "辅色",
                modifier = Modifier.weight(1f)
            )
            // Tertiary
            ColorPreviewItem(
                color = colorScheme.tertiary,
                label = "第三色",
                modifier = Modifier.weight(1f)
            )
            // Primary Container
            ColorPreviewItem(
                color = colorScheme.primaryContainer,
                label = "容器",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ColorPreviewItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShapes.container(ContainerLevel.Field))
                .background(color)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun <T> ThemePresetDropdownSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedLabel: String,
    options: List<PlaybackSegmentOption<T>>,
    onSelectionChange: (T) -> Unit,
    iconTint: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val effectiveIconTint = rememberAdaptiveSemanticIconTint(iconTint)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(AppShapes.container(ContainerLevel.Field))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveIconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = CupertinoIcons.Default.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelectionChange(option.value)
                    }
                )
            }
        }
    }
}

private const val DEFAULT_APP_DPI_OVERRIDE_PERCENT = 100

private fun resolveAppFontSizeSegmentOptions(): List<PlaybackSegmentOption<AppFontSizePreset>> {
    return AppFontSizePreset.entries.map { preset ->
        PlaybackSegmentOption(value = preset, label = preset.label)
    }
}

private fun resolveAppUiScaleSegmentOptions(): List<PlaybackSegmentOption<AppUiScalePreset>> {
    return AppUiScalePreset.entries.map { preset ->
        PlaybackSegmentOption(value = preset, label = preset.label)
    }
}

private fun resolveAppDpiOverrideSegmentOptions(): List<PlaybackSegmentOption<Int>> {
    return listOf(90, 95, 100, 105, 110).map { percent ->
        PlaybackSegmentOption(value = percent, label = "$percent%")
    }
}

private fun resolveDpiOverrideSubtitle(
    systemDensityDpi: Int,
    systemSmallestWidthDp: Int,
    currentOverridePercent: Int
): String {
    val modeLabel = if (currentOverridePercent > 0) {
        "当前 ${currentOverridePercent}%"
    } else {
        "当前跟随系统"
    }
    return "系统 ${systemDensityDpi}dpi / 最小宽度 ${systemSmallestWidthDp}dp，$modeLabel"
}

private fun resolveDisplayMetricsSummary(
    snapshot: DisplayMetricsSnapshot
): String {
    val dpiSuffix = snapshot.dpiOverridePercent?.let { "，覆盖 ${it}%" } ?: ""
    val narrowSuffix = if (snapshot.isNarrowWidth) "，已进入小屏紧凑适配" else ""
    return "应用生效后约 ${snapshot.effectiveDensityDpi}dpi / ${snapshot.effectiveSmallestWidthDp}dp$dpiSuffix$narrowSuffix"
}

internal fun resolveDisplayedAppDpiPercent(
    currentOverridePercent: Int
): Int {
    return if (currentOverridePercent > 0) {
        currentOverridePercent
    } else {
        DEFAULT_APP_DPI_OVERRIDE_PERCENT
    }
}
