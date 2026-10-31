@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.theme.SeedPaletteStyle
import com.android.purebilibili.core.theme.SeedPresetColors
import com.android.purebilibili.core.theme.SeedPresetColorNames
import com.android.purebilibili.core.theme.rememberSeedStyleSwatches

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
import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
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
    val homeConfiguration = rememberSettingsHomeConfiguration()
    val effectiveLiquidGlassEnabled = resolveHomeChromeLiquidGlassEnabled(
        userEnabled = homeConfiguration.androidNativeLiquidGlassEnabled,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val coroutineScope = rememberCoroutineScope()
    var pendingLanguageRestart by remember { mutableStateOf<AppLanguage?>(null) }
    val backLabel = stringResource(R.string.common_back)
    val screenTitle = when (contentMode) {
        AppearanceSettingsContentMode.APPEARANCE -> stringResource(R.string.appearance_settings_title)
        AppearanceSettingsContentMode.HOME -> context.getString(R.string.appearance_home_and_dynamic_title)
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
    val homeConfiguration = rememberSettingsHomeConfiguration()
    val themeConfiguration = rememberSettingsThemeConfiguration()
    val liquidControlsUseThemeColor by remember(context) { SettingsManager.getLiquidControlsUseThemeColor(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val switchThumbMode by remember(context) { SettingsManager.getLiquidSwitchThumbMode(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.ui.LiquidSwitchThumbMode.THEME)
    val activity = context as? android.app.Activity
    val supportedDisplayModes = remember(activity) {
        activity?.supportedAppDisplayModes().orEmpty()
    }
    val storedScreenDisplayModeId by remember(context) { SettingsManager.getScreenDisplayModeId(context) }
        .collectAsStateWithLifecycle(initialValue = SYSTEM_AUTO_DISPLAY_MODE_ID)
    val selectedScreenDisplayModeId = remember(storedScreenDisplayModeId, supportedDisplayModes) {
        normalizePreferredDisplayModeId(
            preferredModeId = storedScreenDisplayModeId,
            supportedModes = supportedDisplayModes,
        )
    }
    val screenDisplayModeOptions = remember(supportedDisplayModes) {
        listOf(AppSegmentOption(SYSTEM_AUTO_DISPLAY_MODE_ID, context.getString(R.string.appearance_display_follow_system))) +
            supportedDisplayModes.map { mode ->
                AppSegmentOption(mode.modeId, displayModePreferenceLabel(mode))
            }
    }
    val selectedScreenDisplayModeLabel = screenDisplayModeOptions
        .firstOrNull { option -> option.value == selectedScreenDisplayModeId }
        ?.label
        ?: context.getString(R.string.appearance_display_follow_system)
    val singleChoicePresentation = themeConfiguration.singleChoicePresentation
    val pinchToChangeGridColumnsEnabled = homeConfiguration.pinchToChangeGridColumnsEnabled
    val appListItemStyleOptions = remember(context) {
        resolveAppListItemStyleOptions().map { option ->
            option.copy(label = context.getString(when (option.value) {
                AppListItemStyle.AUTO -> R.string.appearance_option_follow_preset
                AppListItemStyle.CUSTOM -> R.string.appearance_option_custom_list_items
                AppListItemStyle.NATIVE -> R.string.appearance_option_native_components
            }))
        }
    }
    val appIconStyleOptions = remember(context) {
        resolveAppIconStyleOptions().map { option ->
            option.copy(label = context.getString(when (option.value) {
                AppIconStyle.AUTO -> R.string.appearance_option_follow_preset
                AppIconStyle.SOFT_COLORFUL -> R.string.appearance_option_soft_colorful
                AppIconStyle.VIVID_COLORFUL -> R.string.appearance_option_vivid_colorful
                AppIconStyle.THEME_CONTAINER -> R.string.appearance_option_theme_container
                AppIconStyle.MD3_STANDARD -> R.string.appearance_option_monochrome_line
            }))
        }
    }
    val singleChoicePresentationOptions = remember(context) {
        listOf(
            AppSegmentOption(AppSingleChoicePresentation.WINDOW_POPUP, context.getString(R.string.appearance_display_choice_popup)),
            AppSegmentOption(AppSingleChoicePresentation.CENTERED_DIALOG, context.getString(R.string.appearance_display_choice_dialog)),
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
        AppearanceSettingsContentMode.APPEARANCE -> listOf("appearance_display_mode", "appearance_theme_color", "appearance_text_size", "appearance_splash_icon", "content_image_display", "appearance_language")
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
    val compactVideoStatsOnCover = homeConfiguration.compactVideoStatsOnCover
    val backToTopButtonEnabled by remember(context) { BackToTopSettingsStore.isEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_BACK_TO_TOP_BUTTON_ENABLED)
    val backToTopCustomOffset by remember(context) {
        BackToTopSettingsStore.getCustomOffsetDp(context)
    }.collectAsStateWithLifecycle(initialValue = BackToTopSettingsStore.getCachedOffsetDp())
    val hasCustomBackToTopOffset = backToTopCustomOffset.first != 0f || backToTopCustomOffset.second != 0f
    val homeWallpaperSelection by remember(context) { SettingsManager.getHomeWallpaperSelection(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.home.HomeWallpaperSelection())
    val dedicatedHomeWallpaperUri = homeWallpaperSelection.uri
    val wallpaperPreviewIsTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet
    val splashWallpaperFallbackUri by remember(context) { SettingsManager.getSplashWallpaperUri(context) }
        .collectAsStateWithLifecycle(initialValue = "")
    val resolvedHomeWallpaperUri = remember(dedicatedHomeWallpaperUri, splashWallpaperFallbackUri) {
        dedicatedHomeWallpaperUri.ifBlank { splashWallpaperFallbackUri }.trim()
    }
    val homeWallpaperFollowsSplash = dedicatedHomeWallpaperUri.isBlank() && splashWallpaperFallbackUri.isNotBlank()
    val homeWallpaperIsStatic = com.android.purebilibili.core.ui.wallpaper.rememberIsStaticWallpaper(resolvedHomeWallpaperUri)
    val homeWallpaperEffectMode = homeConfiguration.homeWallpaperEffectMode
    val homeWallpaperEffectScope = homeConfiguration.homeWallpaperEffectScope
    val homeWallpaperEffectOptions = remember {
        listOf(
            AppSegmentOption(HomeWallpaperEffectMode.OFF, context.getString(R.string.appearance_wallpaper_effect_off)),
            AppSegmentOption(HomeWallpaperEffectMode.SOFT_BLUR, context.getString(R.string.appearance_wallpaper_effect_soft)),
            AppSegmentOption(HomeWallpaperEffectMode.STRONG_BLUR, context.getString(R.string.appearance_wallpaper_effect_strong)),
            AppSegmentOption(HomeWallpaperEffectMode.ORIGINAL, context.getString(R.string.appearance_wallpaper_effect_original))
        )
    }
    val homeWallpaperEffectScopeOptions = remember {
        listOf(
            AppSegmentOption(HomeWallpaperEffectScope.HOME_ONLY, context.getString(R.string.appearance_wallpaper_scope_home)),
            AppSegmentOption(HomeWallpaperEffectScope.GLOBAL, context.getString(R.string.appearance_wallpaper_scope_global))
        )
    }
    val showPgcTimeline = homeConfiguration.showPgcTimeline
    val commonListHeaderCollapseMode = homeConfiguration.commonListHeaderCollapseMode
    val commonListHeaderCollapseOptions = remember {
        listOf(
            AppSegmentOption(CommonListHeaderCollapseMode.ALWAYS_VISIBLE, context.getString(R.string.appearance_header_always)),
            AppSegmentOption(CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL, context.getString(R.string.appearance_header_reverse_scroll)),
            AppSegmentOption(CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY, context.getString(R.string.appearance_header_top_only))
        )
    }
    val homeUpBadgesVisible = homeConfiguration.showHomeUpBadges
    val homeUpAvatarsVisible = homeConfiguration.showHomeUpAvatars
    val homeRefreshTipVisible = homeConfiguration.homeRefreshTipVisible
    val homePublishTimeVisible = homeConfiguration.showHomePublishTime
    val homeRefreshUndoVisible = homeConfiguration.homeRefreshUndoVisible
    val fullVideoCardContentVisible = homeConfiguration.showFullVideoCardContent
    val videoCardLongPressActionEnabled = homeConfiguration.videoCardLongPressActionEnabled
    val homeCardDynamicTintEnabled = homeConfiguration.homeCardDynamicTintEnabled
    val homeCardFrostedGlassEnabled = homeConfiguration.homeCardFrostedGlassEnabled
    val homeDurationStyle = homeConfiguration.homeDurationStyle
    val homeFeedCardStyle = homeConfiguration.homeFeedCardStyle
    val homeHeroCarouselEnabled = homeConfiguration.homeHeroCarouselEnabled
    val homeHeroCarouselAutoplayEnabled = homeConfiguration.homeHeroCarouselAutoplayEnabled
    val storedThemeRoleOverrides = themeConfiguration.themeRoleOverrides
    val baseThemeRoleOverrides = LocalBaseThemeRoleOverrides.current
    val themeRoleOverrides = remember(storedThemeRoleOverrides, baseThemeRoleOverrides, state.md3CustomColorHex) {
        if (storedThemeRoleOverrides.enabled) {
            com.android.purebilibili.core.theme.enableThemeRoleOverrides(
                storedThemeRoleOverrides, baseThemeRoleOverrides, state.md3CustomColorHex,
            )
        } else storedThemeRoleOverrides
    }
    val showOnlineCount by remember(context) { SettingsManager.getShowCardOnlineCount(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val showProfileEditButton by remember(context) { SettingsManager.getShowProfileEditButton(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val isLiquidGlassAvailable = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val effectiveLiquidGlassEnabled = resolveHomeChromeLiquidGlassEnabled(
        userEnabled = homeConfiguration.androidNativeLiquidGlassEnabled,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val wallpaperPreviewOverrides = themeConfiguration.wallpaperPaletteOverrides
    val palettePreviewConfiguration = ThemePreviewConfiguration(
        uiStyle = LocalAppUiStyle.current,
        source = state.md3ColorSource,
        liquidGlassEnabled = effectiveLiquidGlassEnabled,
        amoledDarkTheme = state.darkThemeStyle == DarkThemeStyle.AMOLED,
        roleOverrides = themeRoleOverrides,
        wallpaperOverrides = wallpaperPreviewOverrides,
        wallpaperSchemes = com.android.purebilibili.core.theme.LocalThemeWallpaperPreviewSchemes.current,
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
    val displayedThemeColors = if (state.seedPaletteStyle.enabled) SeedPresetColors else ThemeColors
    val displayedThemeColorNames = if (state.seedPaletteStyle.enabled) SeedPresetColorNames else ThemeColorNames
    val selectedThemeColorName = remember(selectedCustomThemeColor, state.seedPaletteStyle) {
        val selectedIndex = displayedThemeColors.indexOf(selectedCustomThemeColor)
        displayedThemeColorNames.getOrNull(selectedIndex) ?: context.getString(R.string.appearance_custom_theme_color_title)
    }
    var themeColorPaletteExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showThemeColorPicker) {
        if (!showThemeColorPicker) themeColorPaletteExpanded = false
    }
    val colorStyleOptions = remember { resolveColorStyleOptions() }
    val colorSpecOptions = remember(state.colorStyle, state.seedPaletteStyle) {
        resolveColorSpecOptions(state.colorStyle, state.seedPaletteStyle)
    }
    val effectiveColorSpec = resolveSelectedColorSpec(state.colorSpec, state.colorStyle, state.seedPaletteStyle)
    val colorContrastOptions = remember { resolveColorContrastOptions() }
    // 主题模式 / 深色主题风格选项示意图的种子与预览方案：与色板小样共用同一来源和
    // 配置推导，输入变化时一次性重算，后面嵌套的色板预览也复用这份种子。
    val themePreviewCompatibilitySeed = com.android.purebilibili.core.theme.LocalWallpaperColorCompatibility.current.seed
    val themePreviewSeed = remember(
        state.md3ColorSource, state.md3CustomColorHex, state.themeColorIndex,
        MaterialTheme.colorScheme.primary, themePreviewCompatibilitySeed,
    ) {
        resolveMd3ThemeSeedColor(
            state.md3ColorSource, state.md3CustomColorHex, state.themeColorIndex,
            wallpaperSeed = if (state.md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
                themePreviewCompatibilitySeed ?: com.android.purebilibili.core.theme.readSystemWallpaperSeed(context)
            } else null,
        )
    }
    val themeOptionPreviewPalettes = rememberThemeOptionPreviewPalettes(
        seed = themePreviewSeed,
        paletteStyle = state.colorStyle,
        colorSpec = effectiveColorSpec,
        colorContrast = state.colorContrast,
        seedPaletteStyle = state.seedPaletteStyle,
        configuration = palettePreviewConfiguration,
    )
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importAppFontFromUri(context, uri)
            .onSuccess { imported ->
                viewModel.setAppFontFile(imported.fileName, imported.displayName)
                val coverageNotice = resolveAppFontCoverageNotice(imported.coversCjk)
                val message = if (coverageNotice == null) {
                    context.getString(R.string.appearance_font_imported, imported.displayName)
                } else {
                    context.getString(R.string.appearance_font_imported_coverage, imported.displayName, coverageNotice)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            .onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: context.getString(R.string.appearance_font_import_failed),
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
                AppPreferenceSectionTitle(context.getString(R.string.appearance_section_interface_effects))
            }
        }
        item(key = "appearance_display_mode") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column {
                        SettingsSingleChoicePreference(
                            title = "${uiPresetTitle}：$selectedUiStyleLabel",
                            subtitle = uiPresetSubtitle,
                            options = uiStyleOptions,
                            selectedValue = state.themeSelection,
                            optionLeading = { value ->
                                SettingsOptionPreviewThumb(SettingsOptionPreview.UiPreset(miuix = value == AppUiStyle.MIUIX))
                            },
                            onSelectionChange = viewModel::setThemeSelection,
                        )

                        Column {
                            AppPreferenceDivider()
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_refresh_rate_title, selectedScreenDisplayModeLabel),
                                subtitle = context.getString(R.string.appearance_refresh_rate_summary),
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
                                        context.getString(R.string.appearance_liquid_glass_summary)
                                    } else {
                                        context.getString(R.string.appearance_liquid_glass_unsupported)
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
                                title = context.getString(R.string.appearance_liquid_controls_theme_title),
                                subtitle = context.getString(R.string.appearance_liquid_controls_theme_summary),
                                checked = liquidControlsUseThemeColor,
                                enabled = effectiveLiquidGlassEnabled && isLiquidGlassAvailable,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setLiquidControlsUseThemeColor(context, enabled) }
                                },
                            )
                        }
                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.switch_thumb_mode") {
                            SettingsSingleChoicePreference(
                                title = settingItemTitle("appearance.switch_thumb_mode"),
                                subtitle = "选择开关滑块的颜色",
                                options = com.android.purebilibili.core.ui.LiquidSwitchThumbMode.entries.map {
                                    AppSegmentOption(it, it.label)
                                },
                                selectedValue = switchThumbMode,
                                // 拇指强制色只作用于液态玻璃开启后的开关；关闭玻璃时不可选。
                                enabled = effectiveLiquidGlassEnabled && isLiquidGlassAvailable,
                                onSelectionChange = { mode ->
                                    scope.launch { SettingsManager.setLiquidSwitchThumbMode(context, mode) }
                                },
                            )
                        }
                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.app_list_item_style") {
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_list_item_style_title),
                                subtitle = context.getString(R.string.appearance_list_item_style_summary),
                                options = appListItemStyleOptions,
                                selectedValue = state.appListItemStyle,
                                optionLeading = { style ->
                                    SettingsOptionPreviewThumb(SettingsOptionPreview.ListItem(mode = when (style) {
                                        AppListItemStyle.AUTO -> 0
                                        AppListItemStyle.CUSTOM -> 1
                                        AppListItemStyle.NATIVE -> 2
                                    }))
                                },
                                onSelectionChange = { style ->
                                    viewModel.setAppListItemStyle(style)
                                }
                            )
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.app_icon_style") {
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_icon_style_title),
                                subtitle = context.getString(R.string.appearance_icon_style_summary),
                                options = appIconStyleOptions,
                                selectedValue = state.appIconStyle,
                                optionLeading = { style ->
                                    SettingsOptionPreviewThumb(SettingsOptionPreview.IconChip(mode = when (style) {
                                        AppIconStyle.AUTO -> 0
                                        AppIconStyle.THEME_CONTAINER -> 1
                                        AppIconStyle.MD3_STANDARD -> 2
                                        AppIconStyle.SOFT_COLORFUL -> 3
                                        AppIconStyle.VIVID_COLORFUL -> 4
                                    }))
                                },
                                onSelectionChange = { style ->
                                    viewModel.setAppIconStyle(style)
                                }
                            )
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.single_choice_presentation") {
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_single_choice_title),
                                subtitle = context.getString(R.string.appearance_single_choice_summary),
                                options = singleChoicePresentationOptions,
                                selectedValue = singleChoicePresentation,
                                optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Choice(
                                    anchored = value == com.android.purebilibili.core.ui.components.AppSingleChoicePresentation.WINDOW_POPUP,
                                )) },
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
                        SettingsNavigationContainerOrigin(
                            target = com.android.purebilibili.navigation3.BiliPaiNavKey.AnimationSettings,
                            onClick = onAnimationClick,
                            first = false,
                        ) { open ->
                            AppPreference(
                                title = context.getString(R.string.appearance_animation_glass_title),
                                onClick = open,
                            )
                        }
                    }
                }
            }
        }

        //  主题与色彩
        item(key = "appearance_theme_color_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle(themeSectionTitle)
            }
        }
        item(key = "appearance_theme_color") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column {
                        SettingsSingleChoicePreference(
                            title = "${themeModeTitle}：$selectedThemeModeLabel",
                            subtitle = themeModeSubtitle,
                            options = themeModeOptions,
                            // 每个选项配迷你屏幕示意图：跟随系统为浅深对角拼接，浅色/深色
                            // 各自按当前种子推导的方案着色，选中前即可看到实际观感。
                            optionLeading = { mode ->
                                ThemeOptionPreviewThumb(
                                    kind = when (mode) {
                                        AppThemeMode.FOLLOW_SYSTEM -> ThemeOptionPreviewKind.FOLLOW_SYSTEM
                                        AppThemeMode.LIGHT -> ThemeOptionPreviewKind.LIGHT
                                        AppThemeMode.DARK -> ThemeOptionPreviewKind.DARK
                                    },
                                    palettes = themeOptionPreviewPalettes,
                                )
                            },
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
                                    // 普通黑与 AMOLED 纯黑用底面明度区分的示意图，
                                    // 均按当前种子与色彩设置生成深色方案。
                                    optionLeading = { style ->
                                        ThemeOptionPreviewThumb(
                                            kind = when (style) {
                                                DarkThemeStyle.DEFAULT -> ThemeOptionPreviewKind.DARK
                                                DarkThemeStyle.AMOLED -> ThemeOptionPreviewKind.AMOLED
                                            },
                                            palettes = themeOptionPreviewPalettes,
                                        )
                                    },
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
                                    context.getString(R.string.appearance_md3_color_source_wallpaper_summary)
                                } else {
                                    context.getString(R.string.appearance_md3_color_source_fallback_summary)
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
                                com.android.purebilibili.feature.settings.WallpaperCompatibilitySettings()
                            }
                        }

                        AppPreferenceDivider()
                        AppPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.CUSTOM_MD3_COLOR),
                            title = context.getString(R.string.appearance_custom_theme_color_title),
                            subtitle = if (state.md3ColorSource == Md3ColorSource.CUSTOM) {
                                context.getString(R.string.appearance_custom_theme_color_summary)
                            } else {
                                context.getString(R.string.appearance_custom_theme_color_following_summary)
                            },
                            value = state.md3CustomColorHex,
                            onClick = { showMd3ColorPickerDialog = true },
                            iconTint = AppContentColors.accent()
                        )

                        AnimatedVisibility(
                            visible = showThemeColorPicker && !state.seedPaletteStyle.enabled,
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

                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    com.android.purebilibili.core.ui.components.AppTextButton(
                                        onClick = {
                                            roleColorTarget = null
                                            scope.launch {
                                                // 清除两种明暗模式的手动覆盖，让当前主题重新提供完整默认色板。
                                                SettingsManager.setThemeRoleOverrides(context, ThemeRoleOverrides())
                                            }
                                        },
                                        enabled = storedThemeRoleOverrides != ThemeRoleOverrides(),
                                    ) {
                                        AppText(stringResource(
                                            if (palettePreviewConfiguration.uiStyle == AppUiStyle.MIUIX) {
                                                R.string.appearance_theme_restore_miuix_palette
                                            } else {
                                                R.string.appearance_theme_restore_md3_palette
                                            }
                                        ))
                                    }
                                    AppText(
                                        text = stringResource(R.string.appearance_theme_restore_palette_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                    )
                                }

                                AnimatedVisibility(
                                    visible = themeRoleOverrides.enabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    ThemeRoleOverrideEditor(
                                        overrides = themeRoleOverrides,
                                        onColorClick = { roleColorTarget = it },
                                        configuration = palettePreviewConfiguration,
                                        seed = selectedCustomThemeColor,
                                        paletteStyle = state.colorStyle,
                                        colorSpec = state.colorSpec,
                                        colorContrast = state.colorContrast,
                                        onRepair = { dark ->
                                            scope.launch {
                                                SettingsManager.setThemeRoleOverrides(context, if (dark) {
                                                    themeRoleOverrides.copy(dark = repairThemeRoleTextContrast(themeRoleOverrides.dark, true))
                                                } else {
                                                    themeRoleOverrides.copy(light = repairThemeRoleTextContrast(themeRoleOverrides.light, false))
                                                })
                                            }
                                        },
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
                                // 种子已提升到内容顶部计算（themePreviewSeed），此处直接复用。
                                val seedPreviewColor = themePreviewSeed
                                val seedPreviewSwatches = rememberSeedStyleSwatches(
                                    seedColor = seedPreviewColor,
                                    isDark = resolveColorSchemeIsDark(MaterialTheme.colorScheme),
                                    colorSpec = state.colorSpec,
                                    configuration = palettePreviewConfiguration,
                                    paletteStyle = state.colorStyle,
                                    colorContrast = state.colorContrast,
                                )
                                val seedOptions = remember { resolveSeedPaletteStyleOptions() }
                                AppPreferenceDivider()
                                ThemePresetChoiceSetting(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SEED_PALETTE),
                                    title = context.getString(R.string.appearance_theme_palette_title),
                                    subtitle = context.getString(R.string.appearance_seed_palette_current, state.seedPaletteStyle.label, resolveSeedPaletteStyleDescription(state.seedPaletteStyle)),
                                    selectedValue = state.seedPaletteStyle,
                                    options = seedOptions,
                                    optionLeading = { style ->
                                        seedPreviewSwatches[style]?.let { ThemeStyleSwatch(it) }
                                    },
                                    onSelectionChange = viewModel::setSeedPaletteStyle,
                                    iconTint = AppContentColors.secondaryText(),
                                )

                                if (!state.seedPaletteStyle.enabled) {
                                    // 风格小样：用当前生效的种子色与色彩标准，预演每种风格的实际配色。
                                    val stylePreviewSeed = seedPreviewColor
                                    val stylePreviewSwatches = rememberThemeStylePaletteSwatches(
                                        seedColor = stylePreviewSeed,
                                        isDark = resolveColorSchemeIsDark(MaterialTheme.colorScheme),
                                        paletteStyles = colorStyleOptions.map { it.value },
                                        colorSpec = state.colorSpec,
                                        colorContrast = state.colorContrast,
                                        seedPaletteStyle = state.seedPaletteStyle,
                                        configuration = palettePreviewConfiguration,
                                    )
                                    val styleSwatchByStyle = remember(stylePreviewSwatches) {
                                        colorStyleOptions.map { it.value }.zip(stylePreviewSwatches).toMap()
                                    }

                                    AppPreferenceDivider()
                                    ThemePresetChoiceSetting(
                                        icon = rememberSettingsSemanticIcon(SettingsIconRole.COLOR_STYLE),
                                        title = context.getString(R.string.appearance_color_style_title),
                                        subtitle = resolveColorStyleSubtitle(state.colorStyle),
                                        selectedValue = state.colorStyle,
                                        options = colorStyleOptions,
                                        optionLeading = { style ->
                                            val swatch = styleSwatchByStyle[style]
                                            if (swatch != null) {
                                                ThemeStyleSwatch(swatch)
                                            }
                                        },
                                        onSelectionChange = viewModel::setThemeColorStyle,
                                        iconTint = AppContentColors.secondaryText()
                                    )

                                }
                                if (shouldShowColorSpecControl(state.seedPaletteStyle)) {
                                    AppPreferenceDivider()
                                    ThemePresetChoiceSetting(
                                        icon = rememberSettingsSemanticIcon(SettingsIconRole.COLOR_SPEC),
                                        title = context.getString(R.string.appearance_color_spec_title),
                                        subtitle = resolveSelectedColorSpecSubtitle(state.colorSpec, state.colorStyle, state.seedPaletteStyle),
                                        selectedValue = effectiveColorSpec,
                                        options = colorSpecOptions,
                                        onSelectionChange = viewModel::setThemeColorSpec,
                                        iconTint = AppContentColors.secondaryText()
                                    )

                                }
                                if (!state.seedPaletteStyle.enabled) {
                                    AppPreferenceDivider()
                                    ThemePresetChoiceSetting(
                                        icon = rememberMaterialSymbol(R.drawable.ms_brightness_medium_24),
                                        title = context.getString(R.string.appearance_color_contrast_title),
                                        subtitle = resolveColorContrastSubtitle(state.colorContrast),
                                        selectedValue = state.colorContrast,
                                        options = colorContrastOptions,
                                        onSelectionChange = viewModel::setThemeColorContrast,
                                        iconTint = AppContentColors.secondaryText()
                                    )
                                }
                                ThemePaletteContrastSummary(
                                    seed = seedPreviewColor,
                                    style = state.colorStyle,
                                    spec = state.colorSpec,
                                    contrast = state.colorContrast,
                                    seedStyle = state.seedPaletteStyle,
                                    configuration = palettePreviewConfiguration,
                                    modifier = Modifier.fillMaxWidth(),
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
                                    title = context.getString(R.string.appearance_theme_color_title, selectedThemeColorName),
                                    subtitle = if (themeColorPaletteExpanded) {
                                        context.getString(R.string.appearance_theme_color_palette_collapse, state.md3CustomColorHex)
                                    } else {
                                        context.getString(R.string.appearance_theme_color_palette_expand, state.md3CustomColorHex)
                                    },
                                    value = context.getString(if (themeColorPaletteExpanded) R.string.appearance_collapse else R.string.appearance_expand),
                                    onClick = { themeColorPaletteExpanded = !themeColorPaletteExpanded },
                                    iconTint = AppContentColors.accent(),
                                )

                                AnimatedVisibility(
                                    visible = themeColorPaletteExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)) {

                                // 预设色板每格的三色预览：跟随当前色彩风格 / 色彩标准与明暗，
                                // 展开时整组算一次，网格滚动不重复生成方案。
                                val palettePreviewIsDark =
                                    resolveColorSchemeIsDark(MaterialTheme.colorScheme)
                                val presetSwatches = rememberThemePresetPaletteSwatches(
                                    seedColors = displayedThemeColors,
                                    isDark = palettePreviewIsDark,
                                    paletteStyle = state.colorStyle,
                                    colorSpec = state.colorSpec,
                                    colorContrast = state.colorContrast,
                                    seedPaletteStyle = state.seedPaletteStyle,
                                    configuration = palettePreviewConfiguration.copy(
                                        source = Md3ColorSource.CUSTOM, syncControlAccentToSeed = true,
                                    ),
                                )

                                //  [Redesign] Theme Color Grid - Strict 2 Rows x 5 Columns
                                val spacing = 12.dp

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp) // 增加行间距以容纳文字
                                ) {
                                    displayedThemeColors.chunked(5).forEach { rowColors ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(spacing)
                                        ) {
                                            rowColors.forEach { color ->
                                                val index = displayedThemeColors.indexOf(color)
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

                                                    // 版式对齐 PiliPlus `common/widgets/color_palette.dart`：
                                                    // 圆角方形容器内放一个圆，圆内上半 primary、左下 tertiary、
                                                    // 右下 primaryContainer；选中时叠容器色圆盘 + primary 对勾。
                                                    val swatch = presetSwatches.getOrElse(index) {
                                                        // 预览数据缺失时退回种子色本身，避免色板出现空档。
                                                        ThemePresetPaletteSwatch(
                                                            primary = color,
                                                            tertiary = color,
                                                            primaryContainer = color
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .aspectRatio(1f) // Ensure square aspect ratio for perfect circles
                                                            .graphicsLayer {
                                                                scaleX = scale
                                                                scaleY = scale
                                                            }
                                                            .clip(AppShapes.container(ContainerLevel.Card))
                                                            .background(
                                                                if (state.seedPaletteStyle.enabled) swatch.inverseOnSurface
                                                                else AppSurfaceTokens.cardContainer()
                                                            )
                                                            .clickable {
                                                                if (state.seedPaletteStyle.enabled) {
                                                                    viewModel.applyMd3CustomColor(formatMd3CustomColorHex(color))
                                                                } else {
                                                                    viewModel.setThemeColorIndex(index)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(6.dp) // 方形容器与圆饼之间的留白
                                                                .clip(CircleShape)
                                                        ) {
                                                            Column(modifier = Modifier.fillMaxSize()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .weight(1f)
                                                                        .background(swatch.primary)
                                                                )
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .weight(1f)
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxHeight()
                                                                            .weight(1f)
                                                                            .background(swatch.tertiary)
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxHeight()
                                                                            .weight(1f)
                                                                            .background(swatch.primaryContainer)
                                                                    )
                                                                }
                                                            }

                                                            androidx.compose.animation.AnimatedVisibility(
                                                                visible = isSelected,
                                                                modifier = Modifier.align(Alignment.Center),
                                                                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                                                                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(23.dp)
                                                                        .background(
                                                                            if (state.seedPaletteStyle.enabled) swatch.surfaceContainer
                                                                            else AppSurfaceTokens.surfaceContainerHighest(),
                                                                            CircleShape
                                                                        ),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    AppIcon(
                                                                        com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_check_24),
                                                                        contentDescription = null,
                                                                        tint = swatch.primary,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 颜色名称
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    AppText(
                                                        text = displayedThemeColorNames.getOrElse(index) { "" },
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

        //  首页配色预览：不依赖颜色来源，始终展示当前主题的实际取色
        item(key = "appearance_theme_home_preview") {
            // 独立圆角分组之间保留外部间距，玻璃、MD3 与 Miuix 共用，避免两张卡片相贴。
            Box(modifier = Modifier.padding(top = 16.dp).entrance()) {
                // 交给设置页分组承载：圆角、水平边距与容器色由 AppPreferenceGroup 统一决定。
                AppPreferenceGroup {
                    ExpandableThemeHomePreview()
                }
            }
        }

        item(key = "appearance_text_size_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle(context.getString(R.string.appearance_section_text_size))
            }
        }
        item(key = "appearance_text_size") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    Column {
                        SettingsItemAnchor("appearance.app_font_size_preset") {
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_setting_font_size_title),
                                subtitle = context.getString(R.string.appearance_font_size_summary),
                                options = resolveAppFontSizeSegmentOptions().map { it.copy(label = context.getString(when (it.value) {
                                    AppFontSizePreset.SMALLEST -> R.string.appearance_font_size_smallest
                                    AppFontSizePreset.SMALLER -> R.string.appearance_font_size_smaller
                                    AppFontSizePreset.SMALL -> R.string.appearance_font_size_small
                                    AppFontSizePreset.DEFAULT -> R.string.appearance_font_size_default
                                    AppFontSizePreset.LARGE -> R.string.appearance_font_size_large
                                    AppFontSizePreset.LARGER -> R.string.appearance_font_size_larger
                                    AppFontSizePreset.EXTRA_LARGE -> R.string.appearance_font_size_extra_large
                                    AppFontSizePreset.EXTRA_EXTRA_LARGE -> R.string.appearance_font_size_extra_extra_large
                                })) },
                                selectedValue = state.appFontSizePreset,
                                optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Typography(scale = value.multiplier)) },
                                onSelectionChange = { preset ->
                                    viewModel.setAppFontSizePreset(preset)
                                }
                            )
                        }

                        SettingsItemAnchor("appearance.app_font_weight_preset") {
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_setting_font_weight_title),
                                subtitle = context.getString(R.string.appearance_font_weight_summary),
                                options = resolveAppFontWeightSegmentOptions().map { it.copy(label = context.getString(when (it.value) {
                                    AppFontWeightPreset.FOLLOW_THEME -> R.string.appearance_font_weight_follow
                                    AppFontWeightPreset.MEDIUM -> R.string.appearance_font_weight_medium
                                    AppFontWeightPreset.SEMI_BOLD -> R.string.appearance_font_weight_semibold
                                    AppFontWeightPreset.BOLD -> R.string.appearance_font_weight_bold
                                })) },
                                selectedValue = state.appFontWeightPreset,
                                optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Typography(weight = value.fontWeight)) },
                                onSelectionChange = { preset ->
                                    viewModel.setAppFontWeightPreset(preset)
                                }
                            )
                        }

                        AppPreferenceDivider()
	                        AppPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.FONT_FILE),
                            title = context.getString(R.string.appearance_font_file_title),
                            subtitle = if (state.appFontDisplayName.isBlank()) {
                                context.getString(R.string.appearance_font_file_import_summary)
                            } else {
                                context.getString(R.string.appearance_font_file_current_summary, state.appFontDisplayName)
                            },
                            value = context.getString(if (state.appFontDisplayName.isBlank()) R.string.appearance_font_default_value else R.string.appearance_font_change_value),
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
                                    title = context.getString(R.string.appearance_font_reset_title),
                                    subtitle = context.getString(R.string.appearance_font_reset_summary),
                                    onClick = {
                                        deleteStoredAppFont(context, state.appFontFileName)
                                        viewModel.clearAppFontFile()
                                        Toast.makeText(context, context.getString(R.string.appearance_font_reset_toast), Toast.LENGTH_SHORT).show()
                                    },
                                    iconTint = AppContentColors.secondaryText(),
                                    showChevron = false
                                )
                            }
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("appearance.app_ui_scale_preset") {
                            SettingsSingleChoicePreference(
                                title = context.getString(R.string.appearance_setting_ui_scale_title),
                                subtitle = context.getString(R.string.appearance_ui_scale_summary),
                                options = resolveAppUiScaleSegmentOptions().map { it.copy(label = context.getString(when (it.value) {
                                    AppUiScalePreset.COMPACT -> R.string.appearance_ui_scale_compact
                                    AppUiScalePreset.STANDARD -> R.string.appearance_ui_scale_standard
                                    AppUiScalePreset.COMFORTABLE -> R.string.appearance_ui_scale_comfortable
                                    AppUiScalePreset.LARGE -> R.string.appearance_ui_scale_large
                                })) },
                                selectedValue = state.appUiScalePreset,
                                optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Density(value.densityMultiplier)) },
                                onSelectionChange = { preset ->
                                    viewModel.setAppUiScalePreset(preset)
                                }
                            )
                        }

                        AppPreferenceDivider()

	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DISPLAY_SCALE),
                            title = context.getString(R.string.appearance_display_scale_title),
                            subtitle = resolveDpiOverrideSubtitle(
                                context = context,
                                systemDensityDpi = displayMetricsSnapshot.systemDensityDpi,
                                systemSmallestWidthDp = displayMetricsSnapshot.systemSmallestWidthDp,
                                currentOverridePercent = state.appDpiOverridePercent,
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
                                    title = context.getString(R.string.appearance_display_scale_title_percent, resolveDisplayedAppDpiPercent(state.appDpiOverridePercent)),
                                    subtitle = context.getString(R.string.appearance_display_scale_summary),
                                    options = resolveAppDpiOverrideSegmentOptions(),
                                    selectedValue = resolveDisplayedAppDpiPercent(state.appDpiOverridePercent),
                                    onSelectionChange = { percent ->
                                        viewModel.setAppDpiOverridePercent(percent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                AppText(
                                    text = resolveDisplayMetricsSummary(context, displayMetricsSnapshot),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                AppPreferenceSectionTitle(context.getString(R.string.appearance_section_splash_icon))
            }
        }
        item(key = "appearance_splash_icon") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    val isSplashEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.isSplashEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
                    val splashRandomEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getSplashRandomEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
                    val splashRandomPoolUris by remember(context) { com.android.purebilibili.core.store.SettingsManager.getSplashRandomPoolUris(context) }.collectAsStateWithLifecycle(initialValue = emptyList())
                    val splashIconAnimationEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getSplashIconAnimationEnabled(context) }.collectAsStateWithLifecycle(initialValue = true)
                    val startupAnimationStyle by remember(context) { SettingsManager.getStartupAnimationStyle(context) }
                        .collectAsStateWithLifecycle(initialValue = SettingsManager.getStartupAnimationStyleSync(context))
                    // 复用页面已订阅的开屏壁纸，列表项回收后不重新订阅和回退占位值。
                    val splashWallpaperUri = splashWallpaperFallbackUri
                    val hasSplashWallpaper = !splashWallpaperUri.isNullOrBlank()
                    val splashRandomPoolPreview = remember(splashRandomPoolUris) {
                        resolveSplashRandomPoolPreviewState(poolUris = splashRandomPoolUris)
                    }

                    // 图标设置
                    AppPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.APP_ICON),
                        title = context.getString(R.string.appearance_app_icon_title),
                        value = when(state.appIcon) {
                            "Blue Snow Maid", "蓝雪女仆", "icon_blue_snow_maid" -> context.getString(R.string.appearance_icon_blue_snow_maid)
                            "Blue Snow Maid Front", "蓝雪女仆·正面", "icon_blue_snow_maid_front" -> context.getString(R.string.appearance_icon_blue_snow_maid_front)
                            // 🎀 二次元少女系列
                            "BiliPai", "icon_bilipai" -> "BiliPai"
                            "BiliPai Pink", "icon_bilipai_pink" -> context.getString(R.string.appearance_icon_bilipai_pink)
                            "BiliPai White", "icon_bilipai_white" -> context.getString(R.string.appearance_icon_bilipai_white)
                            "BiliPai Monet", "icon_bilipai_monet" -> "BiliPai Monet"
                            "Yuki" -> context.getString(R.string.appearance_icon_yuki)
                            "Anime", "icon_anime" -> context.getString(R.string.appearance_icon_anime)
                            "Headphone" -> context.getString(R.string.appearance_icon_headphone)
                            // 经典系列
                            "3D", "icon_3d" -> context.getString(R.string.appearance_icon_3d)
                            "Flat", "icon_flat" -> context.getString(R.string.appearance_icon_flat)
                            "Telegram Blue", "icon_telegram_blue" -> context.getString(R.string.appearance_icon_telegram_blue)
                            "Dark", "icon_telegram_dark" -> context.getString(R.string.appearance_icon_telegram_dark)
                            else -> context.getString(R.string.appearance_icon_blue_snow_maid)
                        },
                        onClick = onNavigateToIconSettings,
                        iconTint = AppContentColors.secondaryText()
                    )

                    AppPreferenceDivider()

                    // 开关项
                    SettingsItemAnchor("appearance.is_splash_enabled") {
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.SPLASH_WALLPAPER),
                            title = context.getString(R.string.appearance_splash_enabled_title),
                            subtitle = context.getString(R.string.appearance_splash_enabled_summary),
                            checked = isSplashEnabled,
                            onCheckedChange = { viewModel.toggleSplashEnabled(it) },
                            iconTint = AppContentColors.secondaryText()
                        )
                    }

                    AppPreferenceDivider()
                    SettingsItemAnchor("appearance.splash_random_enabled") {
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.RANDOM_WALLPAPER),
                            title = context.getString(R.string.appearance_splash_random_title),
                            subtitle = context.getString(R.string.appearance_splash_random_summary),
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
                                    text = context.getString(R.string.appearance_splash_random_pool_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppContentColors.primaryText()
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                AppText(
                                    text = context.getString(R.string.appearance_splash_count, splashRandomPoolPreview.totalCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppContentColors.link()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (splashRandomPoolPreview.previewUris.isEmpty()) {
                                AppText(
                                    text = context.getString(R.string.appearance_splash_no_visible),
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
                                        text = context.getString(R.string.appearance_splash_remaining_count, splashRandomPoolPreview.totalCount - splashRandomPoolPreview.previewUris.size),
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
                            title = context.getString(R.string.appearance_splash_animation_title),
                            subtitle = context.getString(R.string.appearance_splash_animation_summary),
                            checked = splashIconAnimationEnabled,
                            onCheckedChange = { viewModel.toggleSplashIconAnimationEnabled(it) },
                            iconTint = AppContentColors.secondaryText()
                        )
                    }

                    SettingsItemAnchor("appearance.startup_animation_style") {
                        SettingsSingleChoicePreference(
                            title = context.getString(R.string.appearance_startup_animation_style_title),
                            subtitle = context.getString(R.string.appearance_startup_animation_style_summary),
                            options = com.android.purebilibili.core.store.StartupAnimationStyle.entries.map {
                                AppSegmentOption(
                                    it,
                                    context.getString(when (it) {
                                        com.android.purebilibili.core.store.StartupAnimationStyle.ICON_FLYOUT -> R.string.appearance_startup_animation_icon_flyout
                                        com.android.purebilibili.core.store.StartupAnimationStyle.BLUE_SNOW_MAID -> R.string.appearance_startup_animation_maid
                                    }),
                                )
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
                                        text = context.getString(R.string.appearance_splash_choose_wallpaper),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AppContentColors.primaryText()
                                    )
                                    AppText(
                                        text = context.getString(if (hasSplashWallpaper) R.string.appearance_splash_wallpaper_set_summary else R.string.appearance_splash_wallpaper_empty_summary),
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

        item(key = "content_image_display_title") {
            AppPreferenceSectionTitle("图片显示")
        }
        item(key = "content_image_display") {
            com.android.purebilibili.feature.settings.ContentImagePreferences()
        }
        //  语言
        item(key = "appearance_language_title") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle(context.getString(R.string.appearance_section_language))
            }
        }
        item(key = "appearance_language") {
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
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

        if (contentMode == AppearanceSettingsContentMode.HOME) {
            //  首页与列表
            item(key = "home_list_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(context.getString(R.string.home_settings_section_list))
                }
            }
            item(key = "home_list") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        val displayMode = state.displayMode
                        val currentDisplayMode = DisplayMode.entries
                            .firstOrNull { it.value == displayMode }
                        SettingsSingleChoicePreference(
                            title = context.getString(R.string.home_settings_display_style_title),
                            subtitle = context.getString(when (currentDisplayMode) {
                                DisplayMode.DoubleGrid -> R.string.home_settings_display_mode_grid_desc
                                DisplayMode.SingleColumn -> R.string.home_settings_display_mode_single_desc
                                null -> R.string.home_settings_display_style_summary
                            }),
                            options = DisplayMode.entries.map { mode ->
                                AppSegmentOption(mode.value, context.getString(when (mode) {
                                    DisplayMode.DoubleGrid -> R.string.home_settings_display_mode_grid
                                    DisplayMode.SingleColumn -> R.string.home_settings_display_mode_single
                                }))
                            },
                            selectedValue = displayMode,
                            optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Feed(
                                columns = if (value == DisplayMode.SingleColumn.value) 1 else 2,
                                horizontalCards = value == DisplayMode.SingleColumn.value,
                            )) },
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_FEED),
                            iconTint = displayModeTint,
                            onSelectionChange = viewModel::setDisplayMode,
                        )

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor(com.android.purebilibili.feature.settings.GRID_CUSTOMIZATION_SETTING_ID) {
                            AppSwitchPreference(
                                title = com.android.purebilibili.feature.settings.settingItemTitle(com.android.purebilibili.feature.settings.GRID_CUSTOMIZATION_SETTING_ID, context),
                                subtitle = context.getString(R.string.home_settings_grid_customization_summary),
                                checked = homeConfiguration.gridColumnCustomizationEnabled,
                                onCheckedChange = { scope.launch { SettingsManager.setGridColumnCustomizationEnabled(context, it) } },
                            )
                        }
                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.pinch_to_change_grid_columns_enabled") {
                            AppSwitchPreference(
                                title = com.android.purebilibili.feature.settings.settingItemTitle("appearance.pinch_to_change_grid_columns_enabled", context),
                                subtitle = context.getString(R.string.home_settings_pinch_columns_summary),
                                checked = pinchToChangeGridColumnsEnabled,
                                enabled = homeConfiguration.gridColumnCustomizationEnabled,
                                onCheckedChange = { scope.launch { SettingsManager.setPinchToChangeGridColumnsEnabled(context, it) } },
                            )
                        }
                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor(com.android.purebilibili.feature.settings.GRID_SHARE_SETTING_ID) {
                            AppSwitchPreference(
                                title = com.android.purebilibili.feature.settings.settingItemTitle(com.android.purebilibili.feature.settings.GRID_SHARE_SETTING_ID, context),
                                subtitle = context.getString(R.string.home_settings_grid_share_summary),
                                checked = homeConfiguration.gridColumnsSharedAcrossPages,
                                enabled = homeConfiguration.gridColumnCustomizationEnabled,
                                onCheckedChange = { scope.launch { SettingsManager.setGridColumnsSharedAcrossPages(context, it) } },
                            )
                        }
                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor(com.android.purebilibili.feature.settings.GRID_COMPACT_SETTING_ID) {
                            SettingsSingleChoicePreference(
                                title = com.android.purebilibili.feature.settings.settingItemTitle(com.android.purebilibili.feature.settings.GRID_COMPACT_SETTING_ID, context),
                                subtitle = context.getString(R.string.home_settings_grid_default_summary),
                                enabled = homeConfiguration.gridColumnCustomizationEnabled,
                                options = (0..4).map { count ->
                                    AppSegmentOption(
                                        value = count,
                                        label = if (count == 0) context.getString(R.string.home_settings_grid_columns_auto)
                                        else context.getString(R.string.home_settings_grid_columns_count, count),
                                    )
                                },
                                selectedValue = homeConfiguration.gridColumnCountCompact,
                                optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Feed(columns = value)) },
                                onSelectionChange = { count -> scope.launch { SettingsManager.setGridColumnCountCompact(context, count) } },
                            )
                        }
                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.grid_column_count") {
                            SettingsSingleChoicePreference(
                                title = com.android.purebilibili.feature.settings.settingItemTitle("appearance.grid_column_count", context),
                                subtitle = context.getString(R.string.home_settings_grid_default_summary),
                                enabled = homeConfiguration.gridColumnCustomizationEnabled,
                                options = (0..7).map { count ->
                                    AppSegmentOption(
                                        value = count,
                                        label = if (count == 0) context.getString(R.string.home_settings_grid_columns_auto)
                                        else context.getString(R.string.home_settings_grid_columns_count, count),
                                    )
                                },
                                selectedValue = homeConfiguration.gridColumnCount,
                                optionLeading = { value -> SettingsOptionPreviewThumb(SettingsOptionPreview.Feed(columns = value)) },
                                onSelectionChange = { count -> scope.launch { SettingsManager.setGridColumnCount(context, count) } },
                            )
                        }
                        if (isTablet && state.displayMode == 0) {
                            AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                            SettingsItemAnchor("appearance.home_feed_card_width_preset") {
                                SettingsSingleChoicePreference(
                                    title = context.getString(R.string.home_settings_card_width_title),
                                    subtitle = if (homeConfiguration.gridColumnCustomizationEnabled && homeConfiguration.gridColumnCount > 0) {
                                        context.getString(R.string.home_settings_card_width_current, homeConfiguration.gridColumnCount)
                                    } else {
                                        context.getString(R.string.home_settings_card_width_auto)
                                    },
                                    options = resolveHomeFeedCardWidthPresetSegmentOptions().map { option ->
                                        option.copy(label = context.getString(when (option.value) {
                                            HomeFeedCardWidthPreset.AUTO -> R.string.home_settings_card_width_auto_option
                                            HomeFeedCardWidthPreset.COMPACT -> R.string.home_settings_card_width_compact
                                            HomeFeedCardWidthPreset.BALANCED -> R.string.home_settings_card_width_balanced
                                            HomeFeedCardWidthPreset.WIDE -> R.string.home_settings_card_width_wide
                                            HomeFeedCardWidthPreset.ULTRA_WIDE -> R.string.home_settings_card_width_ultra_wide
                                        }))
                                    },
                                    selectedValue = state.homeFeedCardWidthPreset,
                                    onSelectionChange = viewModel::setHomeFeedCardWidthPreset,
                                )
                            }
                        }

                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.full_video_card_content_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULL_VIDEO_CARD_CONTENT),
                                title = context.getString(R.string.home_settings_full_card_title),
                                subtitle = context.getString(
                                    if (fullVideoCardContentVisible) R.string.home_settings_full_card_on
                                    else R.string.home_settings_full_card_off,
                                ),
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
                                title = context.getString(R.string.home_settings_card_ratio_title),
                                subtitle = context.getString(when (homeFeedCardStyle) {
                                    HomeFeedCardStyle.CURRENT -> R.string.home_settings_card_ratio_current
                                    HomeFeedCardStyle.OFFICIAL -> R.string.home_settings_card_ratio_official
                                    HomeFeedCardStyle.BILIPAI -> R.string.home_settings_card_ratio_bilipai
                                }) + context.getString(R.string.home_settings_card_ratio_sync),
                                options = HomeFeedCardStyle.entries.map {
                                    AppSegmentOption(it, context.getString(when (it) {
                                        HomeFeedCardStyle.CURRENT -> R.string.appearance_home_card_ratio_current_label
                                        HomeFeedCardStyle.OFFICIAL -> R.string.appearance_home_card_ratio_official_label
                                        HomeFeedCardStyle.BILIPAI -> R.string.appearance_home_card_ratio_bilipai_label
                                    }))
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
                                    context.getString(R.string.appearance_home_compact_stats_on)
                                } else {
                                    context.getString(R.string.appearance_home_compact_stats_off)
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
                                subtitle = context.getString(R.string.appearance_home_duration_subtitle),
                                options = HomeDurationStyle.entries.map {
                                    AppSegmentOption(it, context.getString(when (it) {
                                        HomeDurationStyle.OUTSIDE_COVER -> R.string.appearance_home_duration_outside
                                        HomeDurationStyle.OVERLAY_TEXT_ONLY -> R.string.appearance_home_duration_overlay
                                        HomeDurationStyle.HIDDEN -> R.string.appearance_home_duration_hidden
                                    }))
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
                                    context.getString(R.string.appearance_home_publish_on)
                                } else {
                                    context.getString(R.string.appearance_home_publish_off)
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
                                    context.getString(R.string.appearance_home_avatars_on)
                                } else {
                                    context.getString(R.string.appearance_home_avatars_off)
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
                                    context.getString(R.string.appearance_home_badges_on)
                                } else {
                                    context.getString(R.string.appearance_home_badges_off)
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
                        com.android.purebilibili.feature.settings.RecommendRetentionPreference()
                        AppPreferenceDivider(modifier = Modifier.padding(start = 16.dp))
                        SettingsItemAnchor("appearance.home_refresh_tip_visible") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_REFRESH_TIP),
                                title = settingItemTitle("appearance.home_refresh_tip_visible"),
                                subtitle = if (homeRefreshTipVisible) {
                                    context.getString(R.string.appearance_home_refresh_tip_on)
                                } else {
                                    context.getString(R.string.appearance_home_refresh_tip_off)
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
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_REFRESH_UNDO),
                                title = settingItemTitle("appearance.home_refresh_undo_visible"),
                                subtitle = context.getString(R.string.appearance_home_refresh_undo),
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
                                    context.getString(R.string.appearance_home_online_on)
                                } else {
                                    context.getString(R.string.appearance_home_online_off)
                                },
                                checked = showOnlineCount,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setShowCardOnlineCount(context, it)
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
                    AppPreferenceSectionTitle(context.getString(R.string.appearance_home_wallpaper_section))
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
                                    text = context.getString(R.string.appearance_home_wallpaper_choose),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppContentColors.primaryText()
                                )
                                AppText(
                                    text = when {
                                        dedicatedHomeWallpaperUri.isNotBlank() -> context.getString(R.string.appearance_home_wallpaper_dedicated)
                                        homeWallpaperFollowsSplash -> context.getString(R.string.appearance_home_wallpaper_follows_splash)
                                        else -> context.getString(R.string.appearance_home_wallpaper_source)
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
                            ) { AppText(context.getString(R.string.appearance_home_wallpaper_transform)) }
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
                                                wallpaperTransformError = error.message ?: context.getString(R.string.appearance_home_wallpaper_save_error)
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
                                    HomeWallpaperEffectMode.OFF -> context.getString(R.string.appearance_wallpaper_effect_off_summary)
                                    HomeWallpaperEffectMode.SOFT_BLUR -> context.getString(R.string.appearance_wallpaper_effect_soft_summary)
                                    HomeWallpaperEffectMode.STRONG_BLUR -> context.getString(R.string.appearance_wallpaper_effect_strong_summary)
                                    HomeWallpaperEffectMode.ORIGINAL -> context.getString(R.string.appearance_wallpaper_effect_original_summary)
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
                                            HomeWallpaperEffectScope.HOME_ONLY -> context.getString(R.string.appearance_wallpaper_scope_home_summary)
                                            HomeWallpaperEffectScope.GLOBAL -> if (
                                                homeWallpaperIsStatic
                                            ) {
                                                context.getString(R.string.appearance_wallpaper_scope_global_summary)
                                            } else {
                                                context.getString(R.string.appearance_wallpaper_motion_summary)
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
                                    context.getString(R.string.appearance_wallpaper_card_blur_on)
                                } else {
                                    context.getString(R.string.appearance_wallpaper_card_blur_off)
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
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_CARD_DYNAMIC_TINT),
                                title = settingItemTitle("appearance.home_card_dynamic_tint_enabled"),
                                subtitle = if (homeCardDynamicTintEnabled) {
                                    context.getString(R.string.appearance_wallpaper_card_tint_on)
                                } else {
                                    context.getString(R.string.appearance_wallpaper_card_tint_off)
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
                    AppPreferenceSectionTitle(context.getString(R.string.appearance_content_feed_section))
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
                                    context.getString(R.string.appearance_feed_carousel_on)
                                } else {
                                    context.getString(R.string.appearance_feed_carousel_off)
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
                                            context.getString(R.string.appearance_feed_autoplay_on)
                                        } else {
                                            context.getString(R.string.appearance_feed_autoplay_off)
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
                                subtitle = context.getString(R.string.appearance_pgc_timeline),
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
                    AppPreferenceSectionTitle(context.getString(R.string.appearance_browse_gestures_section))
                }
            }
            item(key = "home_browsing") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        SettingsItemAnchor("appearance.is_header_collapse_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_HEADER_COLLAPSE),
                                title = settingItemTitle("appearance.is_header_collapse_enabled"),
                                subtitle = context.getString(R.string.appearance_home_header_scroll),
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
                                        context.getString(R.string.appearance_common_header_always)
                                    CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL ->
                                        context.getString(R.string.appearance_common_header_reverse)
                                    CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY ->
                                        context.getString(R.string.appearance_common_header_top)
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
                                    context.getString(R.string.appearance_back_to_top_saved)
                                } else {
                                    context.getString(R.string.appearance_back_to_top_enabled)
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
                                title = context.getString(R.string.appearance_back_to_top_reset_title),
                                subtitle = context.getString(R.string.appearance_back_to_top_reset_subtitle),
                                onClick = {
                                    scope.launch {
                                        BackToTopSettingsStore.resetCustomOffset(context)
                                        android.widget.Toast.makeText(context, context.getString(R.string.appearance_back_to_top_reset_toast), android.widget.Toast.LENGTH_SHORT).show()
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
                                    context.getString(R.string.appearance_card_long_press_on)
                                } else {
                                    context.getString(R.string.appearance_card_long_press_off)
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
                                    context.getString(R.string.appearance_profile_edit_on)
                                } else {
                                    context.getString(R.string.appearance_profile_edit_off)
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
                    AppPreferenceSectionTitle(context.getString(R.string.appearance_recommend_dynamic_section))
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
            colorContrast = state.colorContrast,
            seedPaletteStyle = state.seedPaletteStyle,
            previewConfiguration = palettePreviewConfiguration,
            themeSeed = selectedCustomThemeColor,
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
            colorContrast = state.colorContrast,
            seedPaletteStyle = state.seedPaletteStyle,
            previewConfiguration = palettePreviewConfiguration,
            themeSeed = selectedCustomThemeColor,
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

internal enum class ThemeRoleColorTarget(val labelRes: Int) {
    LIGHT_BACKGROUND(R.string.appearance_theme_light_background),
    LIGHT_PRIMARY_TEXT(R.string.appearance_theme_light_primary_text),
    LIGHT_SECONDARY_TEXT(R.string.appearance_theme_light_secondary_text),
    LIGHT_CONTROL(R.string.appearance_theme_light_control),
    DARK_BACKGROUND(R.string.appearance_theme_dark_background),
    DARK_PRIMARY_TEXT(R.string.appearance_theme_dark_primary_text),
    DARK_SECONDARY_TEXT(R.string.appearance_theme_dark_secondary_text),
    DARK_CONTROL(R.string.appearance_theme_dark_control);

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
private fun ThemePaletteContrastSummary(
    seed: Color,
    style: com.materialkolor.PaletteStyle,
    spec: com.materialkolor.dynamiccolor.ColorSpec.SpecVersion,
    contrast: com.materialkolor.Contrast,
    seedStyle: SeedPaletteStyle,
    configuration: ThemePreviewConfiguration,
    modifier: Modifier = Modifier,
) {
    val lightMode = stringResource(R.string.appearance_theme_light_mode)
    val darkMode = stringResource(R.string.appearance_theme_dark_mode)
    val clearSummary = stringResource(R.string.appearance_theme_contrast_clear)
    val issueSummary = stringResource(R.string.appearance_theme_contrast_issues_summary)
    val summaries = remember(seed, style, spec, contrast, seedStyle, configuration, lightMode, darkMode, clearSummary, issueSummary) {
        listOf(false, true).map { dark ->
            val checks = assessThemeContrast(resolveThemePreviewPalette(
                seed, dark, style, spec, contrast, seedStyle, configuration,
            ).material)
            val issues = checks.filterNot { it.passes }
            val mode = if (dark) darkMode else lightMode
            if (issues.isEmpty()) "$mode: $clearSummary"
            else "$mode: $issueSummary"
        }
    }
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppText(stringResource(R.string.appearance_theme_contrast_readability), style = MaterialTheme.typography.titleSmall)
        summaries.forEach { AppText(it, style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText()) }
        AppText(stringResource(R.string.appearance_theme_contrast_summary),
            style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
    }
}

@Composable
private fun ThemeRoleOverrideEditor(
    overrides: ThemeRoleOverrides,
    onColorClick: (ThemeRoleColorTarget) -> Unit,
    configuration: ThemePreviewConfiguration,
    seed: Color,
    paletteStyle: com.materialkolor.PaletteStyle,
    colorSpec: com.materialkolor.dynamiccolor.ColorSpec.SpecVersion,
    colorContrast: com.materialkolor.Contrast,
    onRepair: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lightScheme = remember(seed, paletteStyle, colorSpec, colorContrast, configuration) {
        resolveThemePreviewPalette(seed, false, paletteStyle, colorSpec, colorContrast,
            SeedPaletteStyle.Standard, configuration).material
    }
    val darkScheme = remember(seed, paletteStyle, colorSpec, colorContrast, configuration) {
        resolveThemePreviewPalette(seed, true, paletteStyle, colorSpec, colorContrast,
            SeedPaletteStyle.Standard, configuration).material
    }
    Column(modifier = modifier.padding(top = 8.dp)) {
        ThemeRoleModeEditor(
            title = stringResource(R.string.appearance_theme_light_mode),
            roles = overrides.light,
            targets = ThemeRoleColorTarget.entries.take(4),
            onColorClick = onColorClick,
            finalScheme = lightScheme,
            onRepair = { onRepair(false) },
            repairAvailable = repairThemeRoleTextContrast(overrides.light, false) != overrides.light
        )
        AppPreferenceDivider()
        ThemeRoleModeEditor(
            title = stringResource(R.string.appearance_theme_dark_mode),
            roles = overrides.dark,
            targets = ThemeRoleColorTarget.entries.takeLast(4),
            onColorClick = onColorClick,
            finalScheme = darkScheme,
            onRepair = { onRepair(true) },
            repairAvailable = repairThemeRoleTextContrast(overrides.dark, true) != overrides.dark
        )
    }
}

@Composable
internal fun ThemeRoleModeEditor(
    title: String,
    roles: ThemeModeRoleOverrides,
    targets: List<ThemeRoleColorTarget>,
    onColorClick: (ThemeRoleColorTarget) -> Unit,
    finalScheme: ColorScheme? = null,
    onRepair: (() -> Unit)? = null,
    repairAvailable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val finalChecks = remember(finalScheme) { finalScheme?.let(::assessThemeContrast).orEmpty() }
    val finalIssues = finalChecks.filterNot { it.passes }
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
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                title = stringResource(target.labelRes),
                subtitle = colors[index],
                value = colors[index],
                onClick = { onColorClick(target) },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppText(
            text = stringResource(R.string.appearance_theme_contrast_ratio,
                primaryContrast,
                secondaryContrast
            ),
            color = AppContentColors.secondaryText(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (finalScheme != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(finalScheme.surface, AppShapes.container(ContainerLevel.Field))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.appearance_theme_preview_primary), color = finalScheme.onSurface)
                Text(stringResource(R.string.appearance_theme_preview_secondary), color = finalScheme.onSurfaceVariant)
                Text(stringResource(R.string.appearance_theme_preview_selected), color = finalScheme.onSecondaryContainer,
                    modifier = Modifier.background(finalScheme.secondaryContainer,
                        AppShapes.container(ContainerLevel.Field)).padding(8.dp))
            }
            AppText(
                if (finalIssues.isEmpty()) stringResource(R.string.appearance_theme_contrast_clear)
                else stringResource(
                    R.string.appearance_theme_contrast_issues,
                    finalIssues.size,
                    finalIssues.take(3).joinToString("; ") {
                        context.getString(R.string.appearance_theme_contrast_issue_item, it.label, it.ratio, it.required)
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (finalIssues.isEmpty()) AppContentColors.secondaryText() else AppContentColors.error(),
                modifier = Modifier.padding(top = 8.dp),
            )
            AppText(stringResource(R.string.appearance_theme_contrast_explain),
                style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        onRepair?.let { repair ->
            AppTextButton(onClick = repair, enabled = repairAvailable) { AppText(stringResource(R.string.appearance_theme_contrast_repair)) }
            AppText(stringResource(R.string.appearance_theme_contrast_repair_summary),
                style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        if (warning) {
            AppText(
                text = stringResource(R.string.appearance_theme_contrast_warning),
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
 *  动态取色预览组件
 * 显示从壁纸提取的 Material You 颜色
 */


@Composable
fun DynamicColorPreview(title: String? = null) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AppText(
            text = title ?: stringResource(R.string.appearance_dynamic_color_preview),
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
                label = stringResource(R.string.appearance_dynamic_color_primary),
                modifier = Modifier.weight(1f)
            )
            // Secondary
            ColorPreviewItem(
                color = colorScheme.secondary,
                label = stringResource(R.string.appearance_dynamic_color_secondary),
                modifier = Modifier.weight(1f)
            )
            // Tertiary
            ColorPreviewItem(
                color = colorScheme.tertiary,
                label = stringResource(R.string.appearance_dynamic_color_tertiary),
                modifier = Modifier.weight(1f)
            )
            // Primary Container
            ColorPreviewItem(
                color = colorScheme.primaryContainer,
                label = stringResource(R.string.appearance_dynamic_color_container),
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
    iconTint: Color,
    subtitle: String? = null,
    optionLeading: (@Composable (T) -> Unit)? = null,
) {
    SettingsSingleChoicePreference(
        title = title,
        subtitle = subtitle,
        options = options,
        selectedValue = selectedValue,
        icon = icon,
        iconTint = iconTint,
        optionLeading = optionLeading,
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
    context: android.content.Context,
    systemDensityDpi: Int,
    systemSmallestWidthDp: Int,
    currentOverridePercent: Int,
): String {
    val modeLabel = if (currentOverridePercent > 0) {
        context.getString(R.string.appearance_display_scale_current, currentOverridePercent)
    } else {
        context.getString(R.string.appearance_display_scale_follow_system)
    }
    return context.getString(
        R.string.appearance_display_metrics_system,
        systemDensityDpi,
        systemSmallestWidthDp,
        modeLabel,
    )
}

private fun resolveDisplayMetricsSummary(
    context: android.content.Context,
    snapshot: DisplayMetricsSnapshot,
): String {
    val dpiSuffix = snapshot.dpiOverridePercent?.let {
        context.getString(R.string.appearance_display_metrics_override, it)
    }.orEmpty()
    val narrowSuffix = if (snapshot.isNarrowWidth) {
        context.getString(R.string.appearance_display_metrics_narrow)
    } else ""
    return context.getString(
        R.string.appearance_display_metrics_applied,
        snapshot.effectiveDensityDpi,
        snapshot.effectiveSmallestWidthDp,
        dpiSuffix,
        narrowSuffix,
    )
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
