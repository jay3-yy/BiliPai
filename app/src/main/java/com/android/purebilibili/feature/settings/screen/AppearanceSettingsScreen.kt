@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.settings

import com.android.purebilibili.feature.settings.screen.DarkThemeSchedulePreference

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.AppColorPicker
import coil3.request.crossfade
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.components.AppSegmentOption

import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AdaptivePlainTooltipBox
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.store.BackToTopSettingsStore
import com.android.purebilibili.core.store.DEFAULT_BACK_TO_TOP_BUTTON_ENABLED
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import coil3.compose.AsyncImage
import com.android.purebilibili.core.theme.deleteStoredAppFont
import com.android.purebilibili.core.theme.AppFontWeightPreset
import com.android.purebilibili.core.theme.importAppFontFromUri
import com.android.purebilibili.core.theme.resolveAppFontCoverageNotice
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.resolveHomeChromeLiquidGlassEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.performance.SYSTEM_AUTO_DISPLAY_MODE_ID
import com.android.purebilibili.core.ui.performance.applyPreferredDisplayMode
import com.android.purebilibili.core.ui.performance.displayModePreferenceLabel
import com.android.purebilibili.core.ui.performance.normalizePreferredDisplayModeId
import com.android.purebilibili.core.ui.performance.supportedAppDisplayModes
import com.android.purebilibili.core.ui.getWindowNavigationBarColor
import com.android.purebilibili.core.ui.rememberAppSparklesIcon
import com.android.purebilibili.core.ui.setWindowNavigationBarColor
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.rememberHapticFeedback
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 *  外观设置二级页面
 * iOS 风格设计
 */
enum class AppearanceSettingsContentMode {
    APPEARANCE,
    HOME,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToIconSettings: () -> Unit = {},
    contentMode: AppearanceSettingsContentMode = AppearanceSettingsContentMode.APPEARANCE,
    onAnimationClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val effectiveLiquidGlassEnabled = resolveHomeChromeLiquidGlassEnabled(
        userEnabled = state.androidNativeLiquidGlassEnabled,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val coroutineScope = rememberCoroutineScope()
    var pendingLanguageRestart by remember { mutableStateOf<AppLanguage?>(null) }
    val backLabel = stringResource(R.string.common_back)
    val screenTitle = when (contentMode) {
        AppearanceSettingsContentMode.APPEARANCE -> stringResource(R.string.appearance_settings_title)
        AppearanceSettingsContentMode.HOME -> "首页与动态"
    }
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

    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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

    SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
        externalContentHandlesTopPadding = true,
        topBarBlurEnabled = state.headerBlurEnabled,
    ) {
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides effectiveLiquidGlassEnabled) {
            AppearanceSettingsContent(
                onAnimationClick = onAnimationClick,
                state = state,
                onNavigateToIconSettings = onNavigateToIconSettings,
                contentMode = contentMode,
                viewModel = viewModel,
                context = context,
                onAppLanguageChange = { language ->
                    if (shouldPromptAppRestartForLanguageChange(state.appLanguage, language)) {
                        pendingLanguageRestart = language
                    }
                },
            )
        }
    }

    pendingLanguageRestart?.let { pendingLanguage ->
        AppAlertDialog(
            onDismissRequest = { pendingLanguageRestart = null },
            title = { AppText(restartDialogTitle) },
            text = { AppText(restartDialogMessage) },
            confirmButton = {
                AppTextButton(
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
                    AppText(restartDialogConfirm)
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingLanguageRestart = null }) {
                    AppText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun HomeSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
) {
    AppearanceSettingsScreen(
        viewModel = viewModel,
        onBack = onBack,
        contentMode = AppearanceSettingsContentMode.HOME,
    )
}

@Composable
fun AppearanceSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onNavigateToIconSettings: () -> Unit,
    contentMode: AppearanceSettingsContentMode,
    viewModel: SettingsViewModel,
    context: android.content.Context,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onAnimationClick: () -> Unit = {},
) {
    val liquidControlsUseThemeColor by SettingsManager.getLiquidControlsUseThemeColor(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val activity = context as? android.app.Activity
    val supportedDisplayModes = remember(activity) {
        activity?.supportedAppDisplayModes().orEmpty()
    }
    val storedScreenDisplayModeId by SettingsManager
        .getScreenDisplayModeId(context)
        .collectAsStateWithLifecycle(initialValue = SYSTEM_AUTO_DISPLAY_MODE_ID)
    val selectedScreenDisplayModeId = remember(storedScreenDisplayModeId, supportedDisplayModes) {
        normalizePreferredDisplayModeId(
            preferredModeId = storedScreenDisplayModeId,
            supportedModes = supportedDisplayModes,
        )
    }
    val screenDisplayModeOptions = remember(supportedDisplayModes) {
        listOf(AppSegmentOption(SYSTEM_AUTO_DISPLAY_MODE_ID, "跟随系统")) +
            supportedDisplayModes.map { mode ->
                AppSegmentOption(mode.modeId, displayModePreferenceLabel(mode))
            }
    }
    val selectedScreenDisplayModeLabel = screenDisplayModeOptions
        .firstOrNull { option -> option.value == selectedScreenDisplayModeId }
        ?.label
        ?: "跟随系统"
    val singleChoicePresentation by SettingsManager
        .getSingleChoicePresentation(context)
        .collectAsStateWithLifecycle(AppSingleChoicePresentation.WINDOW_POPUP)
    val pinchToChangeGridColumnsEnabled by SettingsManager
        .getPinchToChangeGridColumnsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val singleChoicePresentationOptions = remember {
        listOf(
            AppSegmentOption(AppSingleChoicePresentation.WINDOW_POPUP, "选项旁展开"),
            AppSegmentOption(AppSingleChoicePresentation.CENTERED_DIALOG, "居中弹窗"),
        )
    }
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    // Animation Trigger
    val displayModeTint = rememberAdaptiveSemanticIconTint(AppContentColors.accent())

    val displayMetricsSnapshot = LocalDisplayMetricsSnapshot.current
    val windowSizeClass = LocalWindowSizeClass.current
    val isTablet = windowSizeClass.isTablet
    val sectionKeys = when (contentMode) {
        AppearanceSettingsContentMode.APPEARANCE -> listOf("appearance_display_mode", "appearance_theme_color", "appearance_text_size", "appearance_splash_icon", "appearance_language")
        AppearanceSettingsContentMode.HOME -> listOf("home_list", "home_wallpaper", "home_recommendation", "home_browsing")
    }.flatMap { listOf(it + "_title", it) }
    SettingsSectionFocusEffect(
        listState = listState,
        target = if (contentMode == AppearanceSettingsContentMode.HOME) SettingsSearchTarget.HOME_FEED else SettingsSearchTarget.APPEARANCE,
        sectionKeys = if (contentMode == AppearanceSettingsContentMode.APPEARANCE) sectionKeys else sectionKeys + "home_options",
        legacyKeys = mapOf(
            SettingsSearchFocusIds.APPEARANCE_THEME to "appearance_display_mode_title",
            "appearance.dark_theme_schedule" to "appearance_theme_color",
            SettingsSearchFocusIds.APPEARANCE_DISPLAY to "appearance_text_size_title",
            SettingsSearchFocusIds.APPEARANCE_SPLASH to "appearance_splash_icon_title",
            SettingsSearchFocusIds.APPEARANCE_PERSONALIZATION to "appearance_splash_icon_title",
            SettingsSearchFocusIds.HOME_OVERVIEW to "home_list_title",
        ),
    )
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val scope = rememberCoroutineScope()
    val themeSectionTitle = stringResource(R.string.appearance_theme_color_section)
    val uiPresetTitle = stringResource(R.string.appearance_ui_preset_title)
    val uiPresetSubtitle = stringResource(R.string.appearance_ui_preset_subtitle)
    val uiStyleMaterialLabel = stringResource(R.string.appearance_android_native_variant_material3)
    val uiStyleMiuixLabel = stringResource(R.string.appearance_android_native_variant_miuix)
    val uiStyleOptions = remember(uiStyleMaterialLabel, uiStyleMiuixLabel) {
        resolveThemeSelectionOptions(
            material3Label = uiStyleMaterialLabel,
            miuixLabel = uiStyleMiuixLabel,
        )
    }
    val selectedUiStyleLabel = uiStyleOptions
        .first { it.value == state.themeSelection }
        .label
    val themeModeTitle = stringResource(R.string.appearance_theme_mode_title)
    val themeModeSubtitle = stringResource(R.string.appearance_theme_mode_subtitle)
    val themeModeFollowSystemLabel = stringResource(R.string.theme_mode_follow_system)
    val themeModeLightLabel = stringResource(R.string.theme_mode_light)
    val themeModeDarkLabel = stringResource(R.string.theme_mode_dark)
    val themeModeFollowSystemShortLabel = stringResource(R.string.theme_mode_follow_system_short)
    val themeModeLightShortLabel = stringResource(R.string.theme_mode_light_short)
    val themeModeDarkShortLabel = stringResource(R.string.theme_mode_dark_short)
    val themeModeOptions = remember(
        themeModeFollowSystemShortLabel,
        themeModeLightShortLabel,
        themeModeDarkShortLabel
    ) {
        resolveThemeModeSegmentOptions(
            followSystemLabel = themeModeFollowSystemShortLabel,
            lightLabel = themeModeLightShortLabel,
            darkLabel = themeModeDarkShortLabel
        )
    }
    val selectedThemeModeLabel = remember(
        state.themeMode,
        themeModeFollowSystemLabel,
        themeModeLightLabel,
        themeModeDarkLabel
    ) {
        when (state.themeMode) {
            AppThemeMode.FOLLOW_SYSTEM -> themeModeFollowSystemLabel
            AppThemeMode.LIGHT -> themeModeLightLabel
            AppThemeMode.DARK -> themeModeDarkLabel
        }
    }
    val darkThemeStyleTitle = stringResource(R.string.appearance_dark_theme_style_title)
    val darkThemeStyleSubtitle = stringResource(R.string.appearance_dark_theme_style_subtitle)
    val darkThemeStyleDefaultLabel = stringResource(R.string.dark_theme_style_default)
    val darkThemeStyleAmoledLabel = stringResource(R.string.dark_theme_style_amoled)
    val darkThemeStyleDefaultShortLabel = stringResource(R.string.dark_theme_style_default_short)
    val darkThemeStyleAmoledShortLabel = stringResource(R.string.dark_theme_style_amoled_short)
    val darkThemeStyleOptions = remember(
        darkThemeStyleDefaultShortLabel,
        darkThemeStyleAmoledShortLabel
    ) {
        resolveDarkThemeStyleSegmentOptions(
            defaultLabel = darkThemeStyleDefaultShortLabel,
            amoledLabel = darkThemeStyleAmoledShortLabel
        )
    }
    val selectedDarkThemeStyleLabel = remember(
        state.darkThemeStyle,
        darkThemeStyleDefaultLabel,
        darkThemeStyleAmoledLabel
    ) {
        when (state.darkThemeStyle) {
            DarkThemeStyle.DEFAULT -> darkThemeStyleDefaultLabel
            DarkThemeStyle.AMOLED -> darkThemeStyleAmoledLabel
        }
    }
    val appLanguageTitle = stringResource(R.string.appearance_app_language_title)
    val appLanguageSubtitle = stringResource(R.string.appearance_app_language_subtitle)
    val appLanguageFollowSystemLabel = stringResource(R.string.app_language_follow_system)
    val appLanguageSimplifiedLabel = stringResource(R.string.app_language_simplified_chinese)
    val appLanguageTraditionalLabel = stringResource(R.string.app_language_traditional_chinese)
    val appLanguageEnglishLabel = stringResource(R.string.app_language_english)
    val appLanguageFollowSystemShortLabel = stringResource(R.string.app_language_follow_system_short)
    val appLanguageSimplifiedShortLabel = stringResource(R.string.app_language_simplified_chinese_short)
    val appLanguageTraditionalShortLabel = stringResource(R.string.app_language_traditional_chinese_short)
    val appLanguageEnglishShortLabel = stringResource(R.string.app_language_english_short)
    val appLanguageOptions = remember(
        appLanguageFollowSystemShortLabel,
        appLanguageSimplifiedShortLabel,
        appLanguageTraditionalShortLabel,
        appLanguageEnglishShortLabel
    ) {
        resolveAppLanguageSegmentOptions(
            followSystemLabel = appLanguageFollowSystemShortLabel,
            simplifiedChineseLabel = appLanguageSimplifiedShortLabel,
            traditionalChineseLabel = appLanguageTraditionalShortLabel,
            englishLabel = appLanguageEnglishShortLabel
        )
    }
    val selectedAppLanguageLabel = remember(
        state.appLanguage,
        appLanguageFollowSystemLabel,
        appLanguageSimplifiedLabel,
        appLanguageTraditionalLabel,
        appLanguageEnglishLabel
    ) {
        when (state.appLanguage) {
            AppLanguage.FOLLOW_SYSTEM -> appLanguageFollowSystemLabel
            AppLanguage.SIMPLIFIED_CHINESE -> appLanguageSimplifiedLabel
            AppLanguage.TRADITIONAL_CHINESE_TAIWAN -> appLanguageTraditionalLabel
            AppLanguage.ENGLISH -> appLanguageEnglishLabel
        }
    }
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = resolveAppearanceBottomPadding(
        navigationBarsBottom = navigationBarBottomPadding,
        expandableSectionEnabled = true
    )
    val compactVideoStatsOnCover by SettingsManager
        .getCompactVideoStatsOnCover(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val backToTopButtonEnabled by BackToTopSettingsStore
        .isEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_BACK_TO_TOP_BUTTON_ENABLED)
    val backToTopCustomOffset by remember(context) {
        BackToTopSettingsStore.getCustomOffsetDp(context)
    }.collectAsStateWithLifecycle(initialValue = BackToTopSettingsStore.getCachedOffsetDp())
    val hasCustomBackToTopOffset = backToTopCustomOffset.first != 0f || backToTopCustomOffset.second != 0f
    val homeWallpaperSelection by SettingsManager.getHomeWallpaperSelection(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.home.HomeWallpaperSelection())
    val dedicatedHomeWallpaperUri = homeWallpaperSelection.uri
    val wallpaperPreviewIsTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet
    val splashWallpaperFallbackUri by SettingsManager
        .getSplashWallpaperUri(context)
        .collectAsStateWithLifecycle(initialValue = "")
    val resolvedHomeWallpaperUri = remember(dedicatedHomeWallpaperUri, splashWallpaperFallbackUri) {
        dedicatedHomeWallpaperUri.ifBlank { splashWallpaperFallbackUri }.trim()
    }
    val homeWallpaperFollowsSplash = dedicatedHomeWallpaperUri.isBlank() && splashWallpaperFallbackUri.isNotBlank()
    val homeWallpaperIsStatic = com.android.purebilibili.core.ui.wallpaper.rememberIsStaticWallpaper(resolvedHomeWallpaperUri)
    val homeWallpaperEffectMode by SettingsManager
        .getHomeWallpaperEffectMode(context)
        .collectAsStateWithLifecycle(initialValue = HomeWallpaperEffectMode.SOFT_BLUR)
    val homeWallpaperEffectScope by SettingsManager
        .getHomeWallpaperEffectScope(context)
        .collectAsStateWithLifecycle(initialValue = HomeWallpaperEffectScope.HOME_ONLY)
    val homeWallpaperEffectOptions = remember {
        listOf(
            AppSegmentOption(HomeWallpaperEffectMode.OFF, "关闭"),
            AppSegmentOption(HomeWallpaperEffectMode.SOFT_BLUR, "柔和"),
            AppSegmentOption(HomeWallpaperEffectMode.STRONG_BLUR, "强模糊"),
            AppSegmentOption(HomeWallpaperEffectMode.ORIGINAL, "原图")
        )
    }
    val homeWallpaperEffectScopeOptions = remember {
        listOf(
            AppSegmentOption(HomeWallpaperEffectScope.HOME_ONLY, "仅首页"),
            AppSegmentOption(HomeWallpaperEffectScope.GLOBAL, "全局页面")
        )
    }
    val showPgcTimeline by SettingsManager
        .getShowPgcTimeline(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val commonListHeaderCollapseMode by SettingsManager
        .getCommonListHeaderCollapseMode(context)
        .collectAsStateWithLifecycle(initialValue = CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY)
    val commonListHeaderCollapseOptions = remember {
        listOf(
            AppSegmentOption(CommonListHeaderCollapseMode.ALWAYS_VISIBLE, "始终显示"),
            AppSegmentOption(CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL, "上滑时显示"),
            AppSegmentOption(CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY, "仅回顶显示")
        )
    }
    val homeUpBadgesVisible by SettingsManager
        .getHomeUpBadgesVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homeUpAvatarsVisible by SettingsManager
        .getHomeUpAvatarsVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homeRefreshTipVisible by SettingsManager
        .getHomeRefreshTipVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homePublishTimeVisible by SettingsManager
        .getHomePublishTimeVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homeRefreshUndoVisible by SettingsManager
        .getHomeRefreshUndoVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val fullVideoCardContentVisible by SettingsManager
        .getFullVideoCardContentVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val videoCardLongPressActionEnabled by SettingsManager
        .getVideoCardLongPressActionEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeCardDynamicTintEnabled by SettingsManager
        .getHomeCardDynamicTintEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeCardFrostedGlassEnabled by SettingsManager
        .getHomeCardFrostedGlassEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeDurationStyle by SettingsManager
        .getHomeDurationStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeDurationStyle.OUTSIDE_COVER)
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.BILIPAI)
    val homeHeroCarouselEnabled by SettingsManager
        .getHomeHeroCarouselEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val homeHeroCarouselAutoplayEnabled by SettingsManager
        .getHomeHeroCarouselAutoplayEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val storedThemeRoleOverrides by SettingsManager
        .getThemeRoleOverrides(context)
        .collectAsStateWithLifecycle(initialValue = ThemeRoleOverrides())
    val baseThemeRoleOverrides = LocalBaseThemeRoleOverrides.current
    val themeRoleOverrides = remember(storedThemeRoleOverrides, baseThemeRoleOverrides, state.md3CustomColorHex) {
        if (storedThemeRoleOverrides.enabled) {
            com.android.purebilibili.core.theme.enableThemeRoleOverrides(
                storedThemeRoleOverrides, baseThemeRoleOverrides, state.md3CustomColorHex,
            )
        } else storedThemeRoleOverrides
    }
    val showOnlineCount by SettingsManager
        .getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val showProfileEditButton by SettingsManager
        .getShowProfileEditButton(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val isLiquidGlassAvailable = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val effectiveLiquidGlassEnabled = resolveHomeChromeLiquidGlassEnabled(
        userEnabled = state.androidNativeLiquidGlassEnabled,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val showThemeColorPicker = shouldShowMd3CustomColorControls(state.md3ColorSource)
    var showMd3ColorPickerDialog by rememberSaveable { mutableStateOf(false) }
    var roleColorTarget by remember { mutableStateOf<ThemeRoleColorTarget?>(null) }
    val md3ColorSourceOptions = remember { resolveMd3ColorSourceOptions() }
    val selectedMd3ColorSourceLabel = md3ColorSourceOptions
        .firstOrNull { it.value == state.md3ColorSource }
        ?.label ?: state.md3ColorSource.label
    val selectedCustomThemeColor = remember(state.md3CustomColorHex) {
        parseMd3CustomColorHex(state.md3CustomColorHex)
    }
    val selectedThemeColorName = remember(selectedCustomThemeColor) {
        val selectedIndex = ThemeColors.indexOf(selectedCustomThemeColor)
        ThemeColorNames.getOrNull(selectedIndex) ?: "自定义"
    }
    var themeColorPaletteExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showThemeColorPicker) {
        if (!showThemeColorPicker) themeColorPaletteExpanded = false
    }
    val colorStyleOptions = remember { resolveColorStyleOptions() }
    val colorSpecOptions = remember { resolveColorSpecOptions() }
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importAppFontFromUri(context, uri)
            .onSuccess { imported ->
                viewModel.setAppFontFile(imported.fileName, imported.displayName)
                val coverageNotice = resolveAppFontCoverageNotice(imported.coversCjk)
                val message = if (coverageNotice == null) {
                    "已导入字体：${imported.displayName}"
                } else {
                    "已导入字体：${imported.displayName}（$coverageNotice）"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
        contentPadding = PaddingValues(top = LocalSettingsTopContentPadding.current, bottom = contentBottomPadding)
    ) {
        if (contentMode == AppearanceSettingsContentMode.APPEARANCE) {
        //  显示模式
        item(key = "appearance_display_mode_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("界面与效果")
            }
        }
        item(key = "appearance_display_mode") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSingleChoicePreference(
                            title = "${uiPresetTitle}：$selectedUiStyleLabel",
                            subtitle = uiPresetSubtitle,
                            options = uiStyleOptions,
                            selectedValue = state.themeSelection,
                            onSelectionChange = viewModel::setThemeSelection,
                        )

                        Column {
                            AppPreferenceDivider()
                            SettingsSingleChoicePreference(
                                title = "屏幕刷新率：$selectedScreenDisplayModeLabel",
                                subtitle = "手动固定刷新率后，LTPO 屏幕不再自动调节，可能增加耗电",
                                options = screenDisplayModeOptions,
                                selectedValue = selectedScreenDisplayModeId,
                                enabled = activity != null && supportedDisplayModes.isNotEmpty(),
                                onSelectionChange = { modeId ->
                                    scope.launch {
                                        SettingsManager.setScreenDisplayModeId(context, modeId)
                                        activity?.applyPreferredDisplayMode(modeId)
                                    }
                                },
                            )
                        }

                        Column {
                            AppPreferenceDivider()
                            SettingsItemAnchor("appearance.effective_liquid_glass_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.ANDROID_LIQUID_GLASS),
                                    title = settingItemTitle("appearance.effective_liquid_glass_enabled"),
                                    subtitle = if (isLiquidGlassAvailable) {
                                        "首页顶栏、搜索框、底栏和评论底栏使用玻璃效果"
                                    } else {
                                        "当前 Android 版本暂不支持液态玻璃效果"
                                    },
                                    checked = effectiveLiquidGlassEnabled,
                                    onCheckedChange = { viewModel.toggleAndroidNativeLiquidGlass(it) },
                                    enabled = isLiquidGlassAvailable,
                                    iconTint = AppContentColors.secondaryText()
                                )
                            }
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.liquid_controls_use_theme_color") {
                            AppSwitchPreference(
                                title = "液态控件跟随主题色",
                                subtitle = "开启后开关和滑块使用主题色；关闭保留默认绿色开关、蓝色滑块",
                                checked = liquidControlsUseThemeColor,
                                enabled = effectiveLiquidGlassEnabled && isLiquidGlassAvailable,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setLiquidControlsUseThemeColor(context, enabled) }
                                },
                            )
                        }
                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.app_list_item_style") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.app_list_item_style"),
                                subtitle = "统一圆角条目，或跟随当前界面预设",
                                options = resolveAppListItemStyleOptions(),
                                selectedValue = state.appListItemStyle,
                                onSelectionChange = { style ->
                                    viewModel.setAppListItemStyle(style)
                                }
                            )
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.app_icon_style") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.app_icon_style"),
                                subtitle = "彩色底图或单色图标，全局生效",
                                options = resolveAppIconStyleOptions(),
                                selectedValue = state.appIconStyle,
                                onSelectionChange = { style ->
                                    viewModel.setAppIconStyle(style)
                                }
                            )
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.single_choice_presentation") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.single_choice_presentation"),
                                subtitle = "从条目旁展开，或居中弹出",
                                options = singleChoicePresentationOptions,
                                selectedValue = singleChoicePresentation,
                                onSelectionChange = { presentation ->
                                    scope.launch {
                                        SettingsManager.setSingleChoicePresentation(
                                            context = context,
                                            presentation = presentation,
                                        )
                                    }
                                },
                            )
                        }
                        AppPreferenceDivider()
                        AppPreference(
                            title = "动画、振动与玻璃效果",
                            onClick = onAnimationClick,
                        )
                    }
                }
            }
        }

        //  主题与色彩
        item(key = "appearance_theme_color_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("主题与色彩")
            }
        }
        item(key = "appearance_theme_color") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSingleChoicePreference(
                            title = "${themeModeTitle}：$selectedThemeModeLabel",
                            subtitle = themeModeSubtitle,
                            options = themeModeOptions,
                            selectedValue = state.themeMode,
                            onSelectionChange = { mode ->
                                viewModel.setThemeMode(mode)
                            }
                        )

                        AppPreferenceDivider()
                        SettingsItemAnchor("appearance.dark_theme_schedule") {
                            DarkThemeSchedulePreference(
                                schedule = state.darkThemeSchedule,
                                onChange = viewModel::setDarkThemeSchedule,
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.themeMode != AppThemeMode.LIGHT || state.darkThemeSchedule.enabled,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider()
                                SettingsSingleChoicePreference(
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

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.md3_color_source") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.md3_color_source"),
                                subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    "跟随模式使用系统原配色；重新调色可自选壁纸色系的风格与标准"
                                } else {
                                    "壁纸取色需要 Android 12 及以上；当前使用预设色作为后备，可选择自定义颜色"
                                },
                                options = md3ColorSourceOptions,
                                selectedValue = state.md3ColorSource,
                                onSelectionChange = viewModel::setMd3ColorSource
                            )
                        }

                        AnimatedVisibility(
                            visible = state.md3ColorSource != Md3ColorSource.CUSTOM,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                if (state.md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
                                    WallpaperPaletteEditor(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                                } else {
                                    DynamicColorPreview()
                                }
                            }
                        }

                        AppPreferenceDivider()
                        AppPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.CUSTOM_MD3_COLOR),
                            title = "自定义主题颜色",
                            subtitle = if (state.md3ColorSource == Md3ColorSource.CUSTOM) {
                                "可直接使用取色器，也可输入 #RRGGBB 色值"
                            } else {
                                "当前跟随系统壁纸；确认后切换为自定义颜色"
                            },
                            value = state.md3CustomColorHex,
                            onClick = { showMd3ColorPickerDialog = true },
                            iconTint = selectedCustomThemeColor
                        )

                        AnimatedVisibility(
                            visible = showThemeColorPicker,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider()
                                SettingsItemAnchor("appearance.theme_role_overrides.enabled") {
                                    AppSwitchPreference(
                                        icon = rememberSettingsSemanticIcon(SettingsIconRole.ADVANCED_COLOR),
                                        title = settingItemTitle("appearance.theme_role_overrides.enabled"),
                                        subtitle = resolveAdvancedPaletteSubtitle(state.themeSelection),
                                        checked = themeRoleOverrides.enabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                SettingsManager.setThemeRoleOverrides(
                                                    context,
                                                    if (enabled) {
                                                        com.android.purebilibili.core.theme.enableThemeRoleOverrides(
                                                            current = themeRoleOverrides,
                                                            base = baseThemeRoleOverrides,
                                                            customColorHex = state.md3CustomColorHex,
                                                        )
                                                    } else {
                                                        themeRoleOverrides.copy(enabled = false)
                                                    }
                                                )
                                            }
                                        },
                                        iconTint = AppContentColors.accent()
                                    )
                                }

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
                            }
                        }

                        AnimatedVisibility(
                            visible = shouldShowMd3PaletteStyleControls(state.md3ColorSource),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider()
                                ThemePresetChoiceSetting(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COLOR_STYLE),
                                    title = "色彩风格",
                                    selectedValue = state.colorStyle,
                                    options = colorStyleOptions,
                                    onSelectionChange = viewModel::setThemeColorStyle,
                                    iconTint = AppContentColors.secondaryText()
                                )

                                AppPreferenceDivider()
                                ThemePresetChoiceSetting(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COLOR_SPEC),
                                    title = "色彩标准",
                                    selectedValue = state.colorSpec,
                                    options = colorSpecOptions,
                                    onSelectionChange = viewModel::setThemeColorSpec,
                                    iconTint = AppContentColors.secondaryText()
                                )
                            }
                        }

                        // 主题色选择 (仅当动态取色关闭时显示)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showThemeColorPicker,
                            enter =   androidx.compose.animation.expandVertically() +   androidx.compose.animation.fadeIn(),
                            exit =   androidx.compose.animation.shrinkVertically() +   androidx.compose.animation.fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider()
                                AppPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.THEME_COLOR_PICKER),
                                    title = "主题色：$selectedThemeColorName",
                                    subtitle = if (themeColorPaletteExpanded) {
                                        "当前 ${state.md3CustomColorHex}；点按收起色板"
                                    } else {
                                        "当前 ${state.md3CustomColorHex}；点按展开预设色板"
                                    },
                                    value = if (themeColorPaletteExpanded) "收起" else "展开",
                                    onClick = { themeColorPaletteExpanded = !themeColorPaletteExpanded },
                                    iconTint = selectedCustomThemeColor,
                                )

                                AnimatedVisibility(
                                    visible = themeColorPaletteExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {

                                // 直接预览最终 MaterialTheme ColorScheme，不再用原始种子色手工画渐变。
                                Md3ThemeColorPreview(colorHex = state.md3CustomColorHex)

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
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        androidx.compose.animation.AnimatedVisibility(
                                                            visible = isSelected,
                                                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                                                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                                                        ) {
                                                            AppIcon(
                                                                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_check_24),
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    // 颜色名称
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    AppText(
                                                        text = ThemeColorNames.getOrElse(index) { "" },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSelected) AppContentColors.accent() else AppContentColors.secondaryText(),
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
            }
        }

        item(key = "appearance_text_size_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("文字与大小")
            }
        }
        item(key = "appearance_text_size") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsItemAnchor("appearance.app_font_size_preset") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.app_font_size_preset"),
                                subtitle = "仅调整应用内文字比例",
                                options = resolveAppFontSizeSegmentOptions(),
                                selectedValue = state.appFontSizePreset,
                                onSelectionChange = { preset ->
                                    viewModel.setAppFontSizePreset(preset)
                                }
                            )
                        }

                        SettingsItemAnchor("appearance.app_font_weight_preset") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.app_font_weight_preset"),
                                subtitle = "统一调整全部文字的粗细",
                                options = resolveAppFontWeightSegmentOptions(),
                                selectedValue = state.appFontWeightPreset,
                                onSelectionChange = { preset ->
                                    viewModel.setAppFontWeightPreset(preset)
                                }
                            )
                        }

                        AppPreferenceDivider()
	                        AppPreference(
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
                            iconTint = AppContentColors.secondaryText()
                        )

                        AnimatedVisibility(
                            visible = state.appFontFileName.isNotBlank(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider()
	                                AppPreference(
	                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.REPLAY_ONBOARDING),
                                    title = "恢复默认字体",
                                    subtitle = "删除已导入的字体文件",
                                    onClick = {
                                        deleteStoredAppFont(context, state.appFontFileName)
                                        viewModel.clearAppFontFile()
                                        Toast.makeText(context, "已恢复默认字体", Toast.LENGTH_SHORT).show()
                                    },
                                    iconTint = AppContentColors.secondaryText(),
                                    showChevron = false
                                )
                            }
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.app_ui_scale_preset") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.app_ui_scale_preset"),
                                subtitle = "整体放大或缩小列表、卡片和按钮",
                                options = resolveAppUiScaleSegmentOptions(),
                                selectedValue = state.appUiScalePreset,
                                onSelectionChange = { preset ->
                                    viewModel.setAppUiScalePreset(preset)
                                }
                            )
                        }

                        AppPreferenceDivider()

	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DISPLAY_SCALE),
                            title = "显示大小微调",
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
                            iconTint = AppContentColors.secondaryText()
                        )

                        AnimatedVisibility(
                            visible = state.appDpiOverridePercent > 0,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                SettingsSingleChoicePreference(
                                    title = "显示缩放：${resolveDisplayedAppDpiPercent(state.appDpiOverridePercent)}%",
                                    subtitle = "只影响 BiliPai，不改变系统显示设置",
                                    options = resolveAppDpiOverrideSegmentOptions(),
                                    selectedValue = resolveDisplayedAppDpiPercent(state.appDpiOverridePercent),
                                    onSelectionChange = { percent ->
                                        viewModel.setAppDpiOverridePercent(percent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                AppText(
                                    text = resolveDisplayMetricsSummary(displayMetricsSnapshot),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText()
                                )
                            }
                        }
                        AppPreferenceDivider()
                        SettingsTextCopyPreference()
                    }
                }
            }
        }

        //  开屏与图标
        item(key = "appearance_splash_icon_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("开屏与图标")
            }
        }
        item(key = "appearance_splash_icon") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    val isSplashEnabled by com.android.purebilibili.core.store.SettingsManager.isSplashEnabled(context).collectAsStateWithLifecycle(initialValue = false)
                    val splashRandomEnabled by com.android.purebilibili.core.store.SettingsManager.getSplashRandomEnabled(context).collectAsStateWithLifecycle(initialValue = false)
                    val splashRandomPoolUris by com.android.purebilibili.core.store.SettingsManager.getSplashRandomPoolUris(context).collectAsStateWithLifecycle(initialValue = emptyList())
                    val splashIconAnimationEnabled by com.android.purebilibili.core.store.SettingsManager.getSplashIconAnimationEnabled(context).collectAsStateWithLifecycle(initialValue = true)
                    val startupAnimationStyle by SettingsManager.getStartupAnimationStyle(context)
                        .collectAsStateWithLifecycle(initialValue = SettingsManager.getStartupAnimationStyleSync(context))
                    val splashWallpaperUri by com.android.purebilibili.core.store.SettingsManager.getSplashWallpaperUri(context).collectAsStateWithLifecycle(initialValue = null)
                    val hasSplashWallpaper = !splashWallpaperUri.isNullOrBlank()
                    val splashRandomPoolPreview = remember(splashRandomPoolUris) {
                        resolveSplashRandomPoolPreviewState(poolUris = splashRandomPoolUris)
                    }

                    // 图标设置
                    AppPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.APP_ICON),
                        title = "应用图标",
                        value = when(state.appIcon) {
                            "Blue Snow Maid", "蓝雪女仆", "icon_blue_snow_maid" -> "蓝雪女仆"
                            "Blue Snow Maid Front", "蓝雪女仆·正面", "icon_blue_snow_maid_front" -> "蓝雪女仆·正面"
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
                            else -> "蓝雪女仆"
                        },
                        onClick = onNavigateToIconSettings,
                        iconTint = AppContentColors.secondaryText()
                    )

                    AppPreferenceDivider()

                    // 开关项
                    SettingsItemAnchor("appearance.is_splash_enabled") {
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.SPLASH_WALLPAPER),
                            title = settingItemTitle("appearance.is_splash_enabled"),
                            subtitle = "应用启动时显示官方或相册壁纸",
                            checked = isSplashEnabled,
                            onCheckedChange = { viewModel.toggleSplashEnabled(it) },
                            iconTint = AppContentColors.secondaryText()
                        )
                    }

                    AppPreferenceDivider()
                    SettingsItemAnchor("appearance.splash_random_enabled") {
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.RANDOM_WALLPAPER),
                            title = settingItemTitle("appearance.splash_random_enabled"),
                            subtitle = "启动时从可见官方壁纸中随机展示",
                            checked = splashRandomEnabled,
                            onCheckedChange = { viewModel.toggleSplashRandomEnabled(it) },
                            iconTint = AppContentColors.secondaryText()
                        )
                    }

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
                                AppText(
                                    text = "参与随机的壁纸",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppContentColors.primaryText()
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                AppText(
                                    text = "${splashRandomPoolPreview.totalCount} 张",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppContentColors.accent()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (splashRandomPoolPreview.previewUris.isEmpty()) {
                                AppText(
                                    text = "暂无可见壁纸，请先进入“选择开屏壁纸”加载列表",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText()
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    splashRandomPoolPreview.previewUris.forEach { previewUri ->
                                        AsyncImage(
                                            model = coil3.request.ImageRequest.Builder(context)
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
                                    AppText(
                                        text = "还有 ${splashRandomPoolPreview.totalCount - splashRandomPoolPreview.previewUris.size} 张",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                            }
                        }
                    }

                    AppPreferenceDivider()
                    SettingsItemAnchor("appearance.splash_icon_animation_enabled") {
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.SPLASH_ICON_ANIMATION),
                            title = settingItemTitle("appearance.splash_icon_animation_enabled"),
                            subtitle = "关闭后不播放图标飞出或蓝雪女仆动画",
                            checked = splashIconAnimationEnabled,
                            onCheckedChange = { viewModel.toggleSplashIconAnimationEnabled(it) },
                            iconTint = AppContentColors.secondaryText()
                        )
                    }

                    SettingsItemAnchor("appearance.startup_animation_style") {
                        SettingsSingleChoicePreference(
                            title = settingItemTitle("appearance.startup_animation_style"),
                            subtitle = "蓝雪女仆与自定义启动壁纸可同时展示",
                            options = com.android.purebilibili.core.store.StartupAnimationStyle.entries.map {
                                AppSegmentOption(it, it.label)
                            },
                            selectedValue = startupAnimationStyle,
                            enabled = splashIconAnimationEnabled,
                            onSelectionChange = viewModel::setStartupAnimationStyle
                        )
                    }

                    // 当开启时，显示选择壁纸入口
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSplashEnabled,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column {
                            AppPreferenceDivider()

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
                                            model = coil3.request.ImageRequest.Builder(context)
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
                                            AppIcon(
                                                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_photo_24),
                                                contentDescription = null,
                                                tint = AppContentColors.secondaryText(),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    AppText(
                                        text = "选择开屏壁纸",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AppContentColors.primaryText()
                                    )
                                    AppText(
                                        text = if (hasSplashWallpaper) "已设置壁纸，可从官方库或相册更换" else "从官方壁纸库或相册选择",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText()
                                    )
                                }

                                AppIcon(
                                    com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_right_24),
                                    contentDescription = null,
                                    tint = AppContentColors.secondaryText().copy(alpha = 0.5f),
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

        //  语言
        item(key = "appearance_language_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("语言")
            }
        }
        item(key = "appearance_language") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.APP_LANGUAGE),
                            iconTint = AppContentColors.secondaryText(),
                            title = "${appLanguageTitle}：$selectedAppLanguageLabel",
                            subtitle = appLanguageSubtitle,
                            options = appLanguageOptions,
                            selectedValue = state.appLanguage,
                            onSelectionChange = { language ->
                                onAppLanguageChange(language)
                            }
                        )
                    }
                }
            }
        }
        }

        if (contentMode == AppearanceSettingsContentMode.HOME) {
            //  首页与列表
            item(key = "home_list_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("首页与列表")
                }
            }
            item(key = "home_list") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        val displayMode = state.displayMode
                        val currentDisplayMode = DisplayMode.entries
                            .firstOrNull { it.value == displayMode }
                        SettingsSingleChoicePreference(
                            title = "展示样式",
                            subtitle = currentDisplayMode?.description ?: "首页视频排列方式",
                            options = DisplayMode.entries.map { mode ->
                                AppSegmentOption(mode.value, mode.title)
                            },
                            selectedValue = displayMode,
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_FEED),
                            iconTint = displayModeTint,
                            onSelectionChange = viewModel::setDisplayMode,
                        )

                        // 网格列数设置 (仅在双列网格模式下显示)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isTablet && state.displayMode == 0,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                                SettingsItemAnchor("appearance.grid_column_count") {
                                    SettingsSingleChoicePreference(
                                        icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_format_list_bulleted_24),
                                        iconTint = AppContentColors.secondaryText(),
                                        title = settingItemTitle("appearance.grid_column_count"),
                                        subtitle = if (state.gridColumnCount == 0) {
                                            "自适应（默认）；折叠屏外屏与窄屏独立记忆，可用双指缩放调整"
                                        } else {
                                            "宽屏固定 ${state.gridColumnCount} 列；折叠屏外屏与窄屏独立记忆"
                                        },
                                        options = (0..6).map { count ->
                                            AppSegmentOption(
                                                value = count,
                                                label = if (count == 0) "自动" else "$count 列",
                                            )
                                        },
                                        selectedValue = state.gridColumnCount,
                                        onSelectionChange = viewModel::setGridColumnCount,
                                    )
                                }
                                AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                                SettingsItemAnchor("appearance.home_feed_card_width_preset") {
                                    SettingsSingleChoicePreference(
                                        title = settingItemTitle("appearance.home_feed_card_width_preset"),
                                        subtitle = if (state.gridColumnCount > 0) {
                                            "当前固定 ${state.gridColumnCount} 列优先生效，自动列数时使用该宽度"
                                        } else {
                                            "自动列数时控制首页推荐卡片的最小宽度"
                                        },
                                        options = resolveHomeFeedCardWidthPresetSegmentOptions(),
                                        selectedValue = state.homeFeedCardWidthPreset,
                                        onSelectionChange = viewModel::setHomeFeedCardWidthPreset,
                                    )
                                }
                                AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                                SettingsItemAnchor("appearance.pinch_to_change_grid_columns_enabled") {
                                    AppSwitchPreference(
                                        title = settingItemTitle("appearance.pinch_to_change_grid_columns_enabled"),
                                        subtitle = "在视频列表上双指捏合即可调节列数",
                                        checked = pinchToChangeGridColumnsEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                SettingsManager.setPinchToChangeGridColumnsEnabled(context, enabled)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.full_video_card_content_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULL_VIDEO_CARD_CONTENT),
                                title = settingItemTitle("appearance.full_video_card_content_visible"),
                                subtitle = if (fullVideoCardContentVisible) {
                                    "标题不截断，卡片高度可能不一致"
                                } else {
                                    "标题最多两行，其余信息不变"
                                },
                                checked = fullVideoCardContentVisible,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setFullVideoCardContentVisible(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_feed_card_style") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.home_feed_card_style"),
                                subtitle = homeFeedCardStyle.subtitle + "（首页、搜索、列表、相关推荐等同步）",
                                options = HomeFeedCardStyle.entries.map {
                                    AppSegmentOption(it, it.label)
                                },
                                selectedValue = homeFeedCardStyle,
                                onSelectionChange = {
                                    scope.launch {
                                        SettingsManager.setHomeFeedCardStyle(context, it)
                                    }
                                }
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.compact_video_stats_on_cover") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_CARD_STATS_COMPACT),
                                title = settingItemTitle("appearance.compact_video_stats_on_cover"),
                                subtitle = if (compactVideoStatsOnCover) {
                                    "播放量和弹幕显示在封面底部"
                                } else {
                                    "默认显示在标题下方"
                                },
                                checked = compactVideoStatsOnCover,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setCompactVideoStatsOnCover(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_duration_style") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.home_duration_style"),
                                subtitle = "选择视频时长的显示方式",
                                options = HomeDurationStyle.entries.map {
                                    AppSegmentOption(it, it.label)
                                },
                                selectedValue = homeDurationStyle,
                                onSelectionChange = {
                                    scope.launch {
                                        SettingsManager.setHomeDurationStyle(context, it)
                                    }
                                }
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_publish_time_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.TIME_STATUS),
                                title = settingItemTitle("appearance.home_publish_time_visible"),
                                subtitle = if (homePublishTimeVisible) {
                                    "首页视频卡片显示发布时间"
                                } else {
                                    "首页视频卡片不显示发布时间"
                                },
                                checked = homePublishTimeVisible,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setHomePublishTimeVisible(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_up_avatars_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_UP_AVATAR),
                                title = settingItemTitle("appearance.home_up_avatars_visible"),
                                subtitle = if (homeUpAvatarsVisible) {
                                    "首页视频卡片显示 UP 主头像"
                                } else {
                                    "隐藏头像，为 UP 主名称留出更多空间"
                                },
                                checked = homeUpAvatarsVisible,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setHomeUpAvatarsVisible(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_up_badges_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_UP_BADGES),
                                title = settingItemTitle("appearance.home_up_badges_visible"),
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
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_refresh_tip_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_UP_BADGES),
                                title = settingItemTitle("appearance.home_refresh_tip_visible"),
                                subtitle = if (homeRefreshTipVisible) {
                                    "推荐流刷新后保留旧内容，并在刷新位置显示提示"
                                } else {
                                    "关闭后刷新直接替换推荐内容，不显示刷新位置提示"
                                },
                                checked = homeRefreshTipVisible,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setHomeRefreshTipVisible(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_refresh_undo_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_UP_BADGES),
                                title = settingItemTitle("appearance.home_refresh_undo_visible"),
                                subtitle = "刷新推荐流后显示撤销按钮，可恢复刷新前的内容",
                                checked = homeRefreshUndoVisible,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setHomeRefreshUndoVisible(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.show_online_count") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_ONLINE_COUNT),
                                title = settingItemTitle("appearance.show_online_count"),
                                subtitle = if (showOnlineCount) {
                                    "在视频卡片和视频页显示“xx 人正在看”"
                                } else {
                                    "关闭后隐藏卡片和视频页的同时观看人数"
                                },
                                checked = showOnlineCount,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setShowOnlineCount(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }

            //  首页壁纸与氛围
            item(key = "home_wallpaper_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("首页壁纸")
                }
            }
            item(key = "home_wallpaper") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        var showHomeWallpaperPicker by remember { mutableStateOf(false) }
                        var editingWallpaper by remember {
                            mutableStateOf<com.android.purebilibili.core.store.home.HomeWallpaperSelection?>(null)
                        }
                        var editingWallpaperPreviewUri by remember { mutableStateOf("") }
                        var savingWallpaperTransform by remember { mutableStateOf(false) }
                        var wallpaperTransformError by remember { mutableStateOf<String?>(null) }
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
                                    com.android.purebilibili.core.ui.wallpaper.WallpaperMedia(
                                        uri = resolvedHomeWallpaperUri,
                                        playbackEnabled = false,
                                        transformProvider = {
                                            if (wallpaperPreviewIsTablet)
                                                homeWallpaperSelection.tabletTransform else homeWallpaperSelection.mobileTransform
                                        },
                                        imageModel = coil3.request.ImageRequest.Builder(context)
                                            .data(resolvedHomeWallpaperUri)
                                            .crossfade(true)
                                            .build(),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIcon(
                                            rememberSettingsSemanticIcon(SettingsIconRole.HOME_WALLPAPER),
                                            contentDescription = null,
                                            tint = AppContentColors.secondaryText(),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = "选择首页壁纸",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppContentColors.primaryText()
                                )
                                AppText(
                                    text = when {
                                        dedicatedHomeWallpaperUri.isNotBlank() -> "已单独设置首页壁纸"
                                        homeWallpaperFollowsSplash -> "未单独设置，当前跟随开屏壁纸"
                                        else -> "从官方壁纸库或相册选择"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText()
                                )
                            }

                            AppIcon(
                                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_right_24),
                                contentDescription = null,
                                tint = AppContentColors.secondaryText().copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (resolvedHomeWallpaperUri.isNotBlank()) {
                            AppTextButton(
                                onClick = {
                                    wallpaperTransformError = null
                                    editingWallpaperPreviewUri = resolvedHomeWallpaperUri
                                    editingWallpaper = homeWallpaperSelection
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) { AppText("调整位置与缩放") }
                        }
                        editingWallpaper?.let { draft ->
                            com.android.purebilibili.feature.profile.HomeWallpaperAdjustmentSheet(
                                uri = editingWallpaperPreviewUri,
                                initialMobileTransform = draft.mobileTransform,
                                initialTabletTransform = draft.tabletTransform,
                                effectMode = homeWallpaperEffectMode,
                                saving = savingWallpaperTransform,
                                saveError = wallpaperTransformError,
                                onDismiss = { editingWallpaper = null },
                                onSave = { mobile, tablet ->
                                    if (!savingWallpaperTransform) {
                                        savingWallpaperTransform = true
                                        wallpaperTransformError = null
                                        scope.launch {
                                            try {
                                                SettingsManager.setHomeWallpaperSelection(context, draft.copy(
                                                    mobileTransform = mobile,
                                                    tabletTransform = tablet,
                                                ))
                                                editingWallpaper = null
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                wallpaperTransformError = error.message ?: "位置保存失败，请重试"
                                            } finally {
                                                savingWallpaperTransform = false
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        if (showHomeWallpaperPicker) {
                            com.android.purebilibili.feature.profile.SplashWallpaperPickerSheet(
                                target = com.android.purebilibili.feature.profile.WallpaperPickerTarget.HOME,
                                onDismiss = { showHomeWallpaperPicker = false }
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_wallpaper_effect_mode") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.home_wallpaper_effect_mode"),
                                subtitle = when (homeWallpaperEffectMode) {
                                    HomeWallpaperEffectMode.OFF -> "关闭所选页面范围的壁纸背景"
                                    HomeWallpaperEffectMode.SOFT_BLUR -> "轻微模糊壁纸，卡片背景半透明"
                                    HomeWallpaperEffectMode.STRONG_BLUR -> "加深模糊，保留颜色并减少背景干扰"
                                    HomeWallpaperEffectMode.ORIGINAL -> "显示原图，文字背景略微遮挡"
                                },
                                options = homeWallpaperEffectOptions,
                                selectedValue = homeWallpaperEffectMode,
                                onSelectionChange = { mode ->
                                    scope.launch {
                                        SettingsManager.setHomeWallpaperEffectMode(context, mode)
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = homeWallpaperEffectMode != HomeWallpaperEffectMode.OFF,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                                SettingsItemAnchor("appearance.home_wallpaper_effect_scope") {
                                    SettingsSingleChoicePreference(
                                        title = settingItemTitle("appearance.home_wallpaper_effect_scope"),
                                        subtitle = when (homeWallpaperEffectScope) {
                                            HomeWallpaperEffectScope.HOME_ONLY -> "仅首页使用该壁纸背景效果"
                                            HomeWallpaperEffectScope.GLOBAL -> if (
                                                homeWallpaperIsStatic
                                            ) {
                                                "首页、动态、个人页、设置及消息等页面使用同一壁纸"
                                            } else {
                                                "动态壁纸用于首页、聊天及部分列表页面"
                                            }
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
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_card_frosted_glass_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_INFO_GLASS),
                                title = settingItemTitle("appearance.home_card_frosted_glass_enabled"),
                                subtitle = if (homeCardFrostedGlassEnabled) {
                                    "模糊卡片文字背后的壁纸，全局壁纸下同步到追番等列表"
                                } else {
                                    "卡片不使用实时模糊"
                                },
                                checked = homeCardFrostedGlassEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setHomeCardFrostedGlassEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_card_dynamic_tint_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_INFO_GLASS),
                                title = settingItemTitle("appearance.home_card_dynamic_tint_enabled"),
                                subtitle = if (homeCardDynamicTintEnabled) {
                                    "卡片信息区跟随壁纸或封面颜色"
                                } else {
                                    "卡片不跟随壁纸或封面变色"
                                },
                                checked = homeCardDynamicTintEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setHomeCardDynamicTintEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }

            //  内容与推荐流
            item(key = "home_recommendation_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("内容与推荐流")
                }
            }
            item(key = "home_recommendation") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        SettingsItemAnchor("appearance.home_hero_carousel_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_HERO_CAROUSEL),
                                title = settingItemTitle("appearance.home_hero_carousel_enabled"),
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
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AnimatedVisibility(visible = homeHeroCarouselEnabled) {
                            Column {
                                AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                                SettingsItemAnchor("appearance.home_hero_carousel_autoplay_enabled") {
                                    AppSwitchPreference(
                                        icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_HERO_AUTOPLAY),
                                        title = settingItemTitle("appearance.home_hero_carousel_autoplay_enabled"),
                                        subtitle = if (homeHeroCarouselAutoplayEnabled) {
                                            "轮播视频出现在屏幕上时静音循环播放"
                                        } else {
                                            "默认只展示封面，点开后进入视频详情"
                                        },
                                        checked = homeHeroCarouselAutoplayEnabled,
                                        onCheckedChange = {
                                            scope.launch {
                                                SettingsManager.setHomeHeroCarouselAutoplayEnabled(context, it)
                                            }
                                        },
                                        iconTint = AppContentColors.secondaryText()
                                    )
                                }
                            }
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.show_pgc_timeline") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.PGC_TIMELINE),
                                title = settingItemTitle("appearance.show_pgc_timeline"),
                                subtitle = "番剧与影视页显示最近更新时间表",
                                checked = showPgcTimeline,
                                onCheckedChange = { value ->
                                    scope.launch {
                                        SettingsManager.setShowPgcTimeline(context, value)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }

            //  浏览交互与手势
            item(key = "home_browsing_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("浏览交互与手势")
                }
            }
            item(key = "home_browsing") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        SettingsItemAnchor("appearance.is_header_collapse_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_HEADER_COLLAPSE),
                                title = settingItemTitle("appearance.is_header_collapse_enabled"),
                                subtitle = "向下滚动时收起搜索框和标签，点底栏「首页」回顶后重新出现",
                                checked = state.isHeaderCollapseEnabled,
                                onCheckedChange = { value ->
                                    viewModel.toggleHeaderCollapse(value)
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.common_list_header_collapse_mode") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.common_list_header_collapse_mode"),
                                subtitle = when (commonListHeaderCollapseMode) {
                                    CommonListHeaderCollapseMode.ALWAYS_VISIBLE ->
                                        "搜索、收藏、历史、稍后再看、我的点赞顶栏保持展开，与首页设置独立"
                                    CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL ->
                                        "搜索、收藏、历史、稍后再看、我的点赞向下浏览时折叠顶栏，反向上滑时恢复"
                                    CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY ->
                                        "搜索、收藏、历史、稍后再看、我的点赞向下浏览时折叠顶栏，仅回到顶部时恢复"
                                },
                                options = commonListHeaderCollapseOptions,
                                selectedValue = commonListHeaderCollapseMode,
                                onSelectionChange = { mode ->
                                    scope.launch {
                                        SettingsManager.setCommonListHeaderCollapseMode(context, mode)
                                    }
                                }
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.back_to_top_button_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.BACK_TO_TOP),
                                title = settingItemTitle("appearance.back_to_top_button_enabled"),
                                subtitle = if (hasCustomBackToTopOffset) {
                                    "已保存按钮位置；长按可拖动"
                                } else {
                                    "在长页面显示回顶按钮；长按可拖动"
                                },
                                checked = backToTopButtonEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        BackToTopSettingsStore.setEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        if (backToTopButtonEnabled && hasCustomBackToTopOffset) {
                            AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                            AppPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.BACK_TO_TOP),
                                title = "重置回顶按钮位置",
                                subtitle = "恢复默认右下角位置",
                                onClick = {
                                    scope.launch {
                                        BackToTopSettingsStore.resetCustomOffset(context)
                                        android.widget.Toast.makeText(context, "已恢复回顶按钮默认位置", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.video_card_long_press_action_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_GESTURE),
                                title = settingItemTitle("appearance.video_card_long_press_action_enabled"),
                                subtitle = if (videoCardLongPressActionEnabled) {
                                    "长按卡片显示快捷操作与预览"
                                } else {
                                    "已关闭长按手势，避免误触（默认关闭）"
                                },
                                checked = videoCardLongPressActionEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setVideoCardLongPressActionEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.show_profile_edit_button") {
                            AppSwitchPreference(
                                icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_edit_note_24),
                                title = settingItemTitle("appearance.show_profile_edit_button"),
                                subtitle = if (showProfileEditButton) {
                                    "个人主页显示“编辑资料”按钮"
                                } else {
                                    "个人主页隐藏“编辑资料”按钮"
                                },
                                checked = showProfileEditButton,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setShowProfileEditButton(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }
        }
        if (contentMode == AppearanceSettingsContentMode.HOME) {
            item(key = "home_options") {
                Column {
                    AppPreferenceSectionTitle("推荐与动态")
                    HomeFeedSettingsSection()
                }
            }
        }

    }
    }

    if (showMd3ColorPickerDialog) {
        ThemeCustomColorPickerDialog(
            initialHex = state.md3CustomColorHex,
            paletteStyle = state.colorStyle,
            colorSpec = state.colorSpec,
            onDismiss = { showMd3ColorPickerDialog = false },
            onConfirm = { hex ->
                viewModel.applyMd3CustomColor(hex)
                showMd3ColorPickerDialog = false
            }
        )
    }

    roleColorTarget?.let { target ->
        ThemeCustomColorPickerDialog(
            initialHex = target.read(themeRoleOverrides),
            roleTarget = target,
            paletteStyle = state.colorStyle,
            colorSpec = state.colorSpec,
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

internal fun resolveThemeRoleColorIconRole(
    target: ThemeRoleColorTarget,
): SettingsIconRole = when (target) {
    ThemeRoleColorTarget.LIGHT_BACKGROUND -> SettingsIconRole.THEME_LIGHT_BACKGROUND
    ThemeRoleColorTarget.LIGHT_PRIMARY_TEXT -> SettingsIconRole.THEME_LIGHT_PRIMARY_TEXT
    ThemeRoleColorTarget.LIGHT_SECONDARY_TEXT -> SettingsIconRole.THEME_LIGHT_SECONDARY_TEXT
    ThemeRoleColorTarget.LIGHT_CONTROL -> SettingsIconRole.THEME_LIGHT_CONTROL
    ThemeRoleColorTarget.DARK_BACKGROUND -> SettingsIconRole.THEME_DARK_BACKGROUND
    ThemeRoleColorTarget.DARK_PRIMARY_TEXT -> SettingsIconRole.THEME_DARK_PRIMARY_TEXT
    ThemeRoleColorTarget.DARK_SECONDARY_TEXT -> SettingsIconRole.THEME_DARK_SECONDARY_TEXT
    ThemeRoleColorTarget.DARK_CONTROL -> SettingsIconRole.THEME_DARK_CONTROL
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
        AppPreferenceDivider()
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
        AppText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
            AppPreference(
                icon = rememberSettingsSemanticIcon(resolveThemeRoleColorIconRole(target)),
                title = target.label,
                subtitle = colors[index],
                value = colors[index],
                onClick = { onColorClick(target) },
                iconTint = parseMd3CustomColorHex(colors[index])
            )
        }
        AppText(
            text = "对比度：主要文字 %.2f:1，次要文字 %.2f:1".format(
                primaryContrast,
                secondaryContrast
            ),
            color = AppContentColors.secondaryText(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (warning) {
            AppText(
                text = "文字与背景过于接近，可能难以阅读；仍可保存。",
                color = AppContentColors.error(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
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


internal fun restartApp(context: android.content.Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    (context as? android.app.Activity)?.finishAffinity()
    context.startActivity(launchIntent)
}

/**
 * Material 3 native preview of the generated theme roles.
 *
 * The picker value is only a seed. This preview deliberately consumes the active
 * [MaterialTheme.colorScheme], so its container, control and text colors match the roles that
 * the rest of the app receives after MaterialKolor tone mapping.
 */
@Composable
private fun Md3ThemeColorPreview(
    colorHex: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow,
            contentColor = colorScheme.onSurface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_play_arrow_fill_24),
                        contentDescription = null,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = colorHex,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "Material 3 原生配色预览",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/**
 *  动态取色预览组件
 * 显示从壁纸提取的 Material You 颜色
 */


@Composable
fun DynamicColorPreview(title: String = "当前取色预览") {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = AppContentColors.secondaryText(),
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
        AppText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText()
        )
    }
}

@Composable
private fun <T> ThemePresetChoiceSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedValue: T,
    options: List<AppSegmentOption<T>>,
    onSelectionChange: (T) -> Unit,
    iconTint: Color
) {
    SettingsSingleChoicePreference(
        title = title,
        options = options,
        selectedValue = selectedValue,
        icon = icon,
        iconTint = iconTint,
        onSelectionChange = onSelectionChange,
    )
}

private const val DEFAULT_APP_DPI_OVERRIDE_PERCENT = 100

private fun resolveAppFontSizeSegmentOptions(): List<AppSegmentOption<AppFontSizePreset>> {
    return AppFontSizePreset.entries.map { preset ->
        AppSegmentOption(value = preset, label = preset.label)
    }
}

private fun resolveAppFontWeightSegmentOptions(): List<AppSegmentOption<AppFontWeightPreset>> {
    return AppFontWeightPreset.entries.map { preset ->
        AppSegmentOption(value = preset, label = preset.label)
    }
}

private fun resolveAppUiScaleSegmentOptions(): List<AppSegmentOption<AppUiScalePreset>> {
    return AppUiScalePreset.entries.map { preset ->
        AppSegmentOption(value = preset, label = preset.label)
    }
}

private fun resolveAppDpiOverrideSegmentOptions(): List<AppSegmentOption<Int>> {
    return listOf(90, 95, 100, 105, 110).map { percent ->
        AppSegmentOption(value = percent, label = "$percent%")
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
