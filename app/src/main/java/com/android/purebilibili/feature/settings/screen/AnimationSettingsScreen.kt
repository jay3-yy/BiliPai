// 文件路径: feature/settings/AnimationSettingsScreen.kt
package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.settings.settingItemTitle
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.share.SettingsShareService
import com.android.purebilibili.feature.settings.share.SettingsShareImportSession
import com.android.purebilibili.feature.settings.share.flattenSettingsShareSections
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeed
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionDurationMillis
import com.android.purebilibili.core.ui.adaptive.toAdaptiveFoldPosture
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.ui.transition.normalizeVideoSharedTransitionCustomDurationMillis
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackExitDirection
import com.android.purebilibili.navigation.resolveVisibleBottomBarItems
import com.android.purebilibili.feature.home.components.resolveBottomBarVisibleItemsForSearchMode
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.animation.rememberEffectiveEntranceMotionSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
private fun localizedAnimationSettingTitle(settingId: String): String {
    val titleResId = when (settingId) {
            "animation.ui_entrance_animation_enabled" -> R.string.animation_title_ui_entrance
            "animation.global_drag_feedback_enabled" -> R.string.animation_title_drag_feedback
            "animation.haptic_feedback_enabled" -> R.string.animation_title_haptic
            "animation.global_text_tap_copy_enabled" -> R.string.animation_title_tap_copy
            "animation.maid_lottie_animation_enabled" -> R.string.animation_title_maid
            "animation.card_animation_enabled" -> R.string.animation_title_card_entrance
            "animation.card_transition_enabled" -> R.string.animation_title_card_transition
            "animation.live_surface_card_transition_enabled" -> R.string.animation_title_live_transition
            "animation.app_navigation_settings.video_return_content_follow_progress_enabled" -> R.string.animation_title_return_follow
            "animation.app_navigation_settings.video_shared_return_gesture_follow_enabled" -> R.string.animation_title_return_tilt
            "animation.app_navigation_settings.video_shared_return_gesture_translation_enabled" -> R.string.animation_title_return_translation
            "animation.video_transition_realtime_blur_enabled" -> R.string.animation_title_transition_blur
            "animation.app_navigation_settings.miuix_transition_blur_enabled" -> R.string.animation_title_back_blur
            "animation.full_screen_swipe_back_enabled" -> R.string.animation_title_fullscreen_swipe_back
            "animation.video_shared_transition_speed" -> R.string.animation_title_video_speed
            "animation.skeleton_breathing_enabled" -> R.string.animation_title_skeleton
            "animation.header_blur_enabled" -> R.string.animation_title_header_blur
            "animation.progressive_top_blur_enabled" -> R.string.animation_title_progressive_blur
            "animation.progressive_top_fade_enabled" -> R.string.animation_title_progressive_fade
            "animation.bottom_bar_blur_enabled" -> R.string.animation_title_bottom_blur
        else -> return settingItemTitle(settingId)
    }
    return stringResource(titleResId)
}

/**
 *  动画与效果设置二级页面
 * 管理卡片动画、过渡效果、磨砂效果等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    advancedOnly: Boolean = false,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val screenTitle = if (advancedOnly) stringResource(R.string.animation_glass_advanced_title) else stringResource(R.string.animation_effects_title)
    val backLabel = stringResource(R.string.common_back)
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
        externalContentHandlesTopPadding = true,
        topBarBlurEnabled = state.headerBlurEnabled,
    ) {
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides state.isLiquidGlassEnabled) {
            AnimationSettingsContent(
                state = state,
                viewModel = viewModel,
                advancedOnly = advancedOnly,
            )
        }
    }
}

@Composable
fun AnimationSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    advancedOnly: Boolean = false,
) {
    val context = LocalContext.current
    val previewWindowSize = LocalWindowSizeClass.current
    val previewWindowInfo = LocalAppWindowAdaptiveInfo.current
    val previewTransitionDurationMillis = resolveVideoSharedTransitionDurationMillis(
        VideoSharedTransitionSpeedSettings(state.videoSharedTransitionSpeed, state.videoSharedTransitionCustomDurationMillis),
        VideoTransitionAdaptiveInfo(
            widthSizeClass = previewWindowSize.widthSizeClass,
            foldPosture = previewWindowInfo.posture.toAdaptiveFoldPosture(),
        ),
    )
    val scope = rememberCoroutineScope()
    val liquidGlassShareService = remember(context.applicationContext) {
        SettingsShareService(context.applicationContext)
    }
    var pendingLiquidGlassImport by remember {
        mutableStateOf<SettingsShareImportSession?>(null)
    }
    var isLiquidGlassImporting by remember { mutableStateOf(false) }
    val liquidGlassImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLiquidGlassImporting = true
            try {
                liquidGlassShareService.readLiquidGlassImportSession(uri)
                    .onSuccess { pendingLiquidGlassImport = it }
                    .onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: context.getString(R.string.animation_glass_import_read_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
            } finally {
                isLiquidGlassImporting = false
            }
        }
    }
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    LaunchedEffect(focusRequest?.token, state.hapticFeedbackEnabled) {
        val request = focusRequest ?: return@LaunchedEffect
        if (!state.hapticFeedbackEnabled && (request.settingId == HAPTIC_SCENES_SETTING_ID ||
                request.settingId?.startsWith("animation.haptic_scene.") == true)) {
            SettingsSearchFocusController.submit(
                target = SettingsSearchTarget.ANIMATION,
                focusId = "animation_page",
                settingId = "animation.haptic_feedback_enabled",
                query = request.query,
            )
        }
    }
    val windowSizeClass = LocalWindowSizeClass.current
    val warningTint = AppContentColors.warning()
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardMotionTier = resolveAnimationSettingsCardMotionTier(
        baseTier = deviceUiProfile.motionTier,
        cardAnimationEnabled = state.cardAnimationEnabled
    )
    val motionTierLabel = when (cardMotionTier) {
            MotionTier.Reduced -> stringResource(R.string.animation_motion_tier_reduced)
            MotionTier.Normal -> stringResource(R.string.animation_motion_tier_standard)
            MotionTier.Enhanced -> stringResource(R.string.animation_motion_tier_enhanced)
    }
    val motionTierHint = when (cardMotionTier) {
            MotionTier.Reduced -> stringResource(R.string.animation_motion_tier_reduced_hint)
            MotionTier.Normal -> stringResource(R.string.animation_motion_tier_standard_hint)
            MotionTier.Enhanced -> stringResource(R.string.animation_motion_tier_enhanced_hint)
    }
    val homeConfiguration = rememberSettingsHomeConfiguration()
    val themeConfiguration = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val progressiveBlurRenderer by remember(context) {
        com.android.purebilibili.core.store.home.LiquidGlassSettingsStore.observeProgressiveBlurRenderer(context)
    }.collectAsStateWithLifecycle(
        initialValue = com.android.purebilibili.core.store.home.ProgressiveBlurRenderer.MIUIX,
    )
    val isLiquidGlassAvailable = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val liquidGlassPreviewImageUri by remember(context) { SettingsManager.getLiquidGlassPreviewImageUri(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val liquidGlassAdvancedSettings = homeConfiguration.liquidGlassAdvancedSettings
    val liquidGlassReadabilityMode = homeConfiguration.liquidGlassReadabilityMode
    val uiEntranceAnimationEnabled by remember(context) { SettingsManager.getUiEntranceAnimationEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = themeConfiguration.uiEntranceAnimationEnabled)
    val globalDragFeedbackEnabled by remember(context) { SettingsManager.getGlobalDragFeedbackEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = themeConfiguration.globalDragFeedbackEnabled)
    val maidLottieAnimationEnabled by remember(context) { SettingsManager.getMaidLottieAnimationEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val skeletonBreathingEnabled = com.android.purebilibili.core.ui.skeleton.rememberSkeletonBreathingEnabled()
    val globalTextTapCopyEnabled by remember(context) { SettingsManager.getGlobalTextTapCopyEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = themeConfiguration.globalTextTapCopyEnabled)
    val appNavigationSettings = rememberSettingsNavigationConfiguration()
    val previewBottomBarItems = remember(
        appNavigationSettings.orderedVisibleTabIds,
        homeConfiguration.isBottomBarSearchEnabled,
        homeConfiguration.bottomBarSearchLayoutMode,
    ) {
        resolveBottomBarVisibleItemsForSearchMode(
            visibleItems = resolveVisibleBottomBarItems(
                appNavigationSettings.orderedVisibleTabIds
            ),
            bottomBarSearchEnabled = homeConfiguration.isBottomBarSearchEnabled,
            searchLayoutMode = homeConfiguration.bottomBarSearchLayoutMode,
        )
    }
    val videoTransitionRealtimeBlurEnabled by remember(context) { SettingsManager.getVideoTransitionRealtimeBlurEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val settingsContainerRealtimeBlurEnabled by remember(context) { SettingsManager.getSettingsContainerRealtimeBlurEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val settingsContainerMorphEnabled by remember(context) { SettingsManager.getSettingsContainerMorphEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val liveSurfaceCardTransitionEnabled by remember(context) { SettingsManager.getLiveSurfaceCardTransitionEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val fullScreenSwipeBackEnabled by remember(context) { SettingsManager.getFullScreenSwipeBackEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val effectiveEntranceSpec = rememberEffectiveEntranceMotionSpec()
    // 开关开着、但有效参数被降级为不动画 → 系统减弱动效在生效。
    val entranceDowngradedBySystem = uiEntranceAnimationEnabled && !effectiveEntranceSpec.animate
    val sharedTransitionSpeedOptions = listOf(
            AppSegmentOption(VideoSharedTransitionSpeed.FAST, stringResource(R.string.animation_speed_fast)),
            AppSegmentOption(VideoSharedTransitionSpeed.STANDARD, stringResource(R.string.animation_speed_standard)),
            AppSegmentOption(VideoSharedTransitionSpeed.SLOW, stringResource(R.string.animation_speed_slow)),
            AppSegmentOption(VideoSharedTransitionSpeed.CUSTOM, stringResource(R.string.animation_speed_custom))
    )
    val predictiveBackStyle = remember(appNavigationSettings) {
        if (appNavigationSettings.predictiveBackEnabled) {
            BiliPaiPredictiveBackAnimationStyle.fromStorageValue(
                appNavigationSettings.predictiveBackAnimationStyle
            )
        } else {
            BiliPaiPredictiveBackAnimationStyle.NONE
        }
    }
    // 设置容器动画两档:容器形变(条目展开为页面)与默认导航转场。
    val settingsContainerMorphOptions = listOf(
        AppSegmentOption(true, stringResource(R.string.animation_settings_container_morph)),
        AppSegmentOption(false, stringResource(R.string.animation_settings_container_default)),
    )
    val predictiveBackStyleOptions = listOf(
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.NONE, stringResource(R.string.animation_back_style_none)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.AOSP, "AOSP"),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.MIUIX, "Miuix"),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.SCALE, stringResource(R.string.animation_back_style_scale)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.CLASSIC, stringResource(R.string.animation_back_style_classic)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.FADE, stringResource(R.string.animation_page_fade)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.FADE_IN, stringResource(R.string.animation_page_fade_in)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT, stringResource(R.string.animation_page_right_to_left)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.LEFT_TO_RIGHT, stringResource(R.string.animation_page_left_to_right)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.UP_TO_DOWN, stringResource(R.string.animation_page_up_to_down)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.DOWN_TO_UP, stringResource(R.string.animation_page_down_to_up)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT_FADE, stringResource(R.string.animation_page_right_to_left_fade)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.LEFT_TO_RIGHT_FADE, stringResource(R.string.animation_page_left_to_right_fade)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.ZOOM, stringResource(R.string.animation_page_zoom)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.CIRCULAR_REVEAL, stringResource(R.string.animation_page_circular_reveal)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.IOS, stringResource(R.string.animation_page_ios)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.SIZE, stringResource(R.string.animation_page_size)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.SHARED_AXIS, stringResource(R.string.animation_page_shared_axis)),
            AppSegmentOption(BiliPaiPredictiveBackAnimationStyle.NO_TRANSITION, stringResource(R.string.animation_page_instant)),
    )
    val predictiveBackExitDirection = remember(appNavigationSettings.predictiveBackExitDirection) {
        BiliPaiPredictiveBackExitDirection.fromStorageValue(
            appNavigationSettings.predictiveBackExitDirection
        )
    }
    val predictiveBackExitDirectionOptions = listOf(
            AppSegmentOption(BiliPaiPredictiveBackExitDirection.FOLLOW_GESTURE, stringResource(R.string.animation_exit_follow_gesture)),
            AppSegmentOption(BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT, stringResource(R.string.animation_exit_always_right)),
            AppSegmentOption(BiliPaiPredictiveBackExitDirection.ALWAYS_LEFT, stringResource(R.string.animation_exit_always_left)),
    )
    var customTransitionDurationMillis by remember(state.videoSharedTransitionCustomDurationMillis) {
        mutableIntStateOf(state.videoSharedTransitionCustomDurationMillis)
    }
    fun snapCustomTransitionDuration(value: Float): Int {
        val stepMillis = 20
        val min = VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS
        val snapped = min + (((value - min) / stepMillis).roundToInt() * stepMillis)
        return normalizeVideoSharedTransitionCustomDurationMillis(snapped)
    }
    SettingsSectionFocusEffect(
        listState = listState,
        target = SettingsSearchTarget.ANIMATION,
        sectionKeys = (if (advancedOnly) emptyList() else listOf("animation_page_title", "animation_page", "animation_maid_lottie_title", "animation_maid_lottie", "animation_card_title", "animation_card")) + listOf("animation_glass_title", "animation_glass"),
        legacyKeys = mapOf(
            SettingsSearchFocusIds.ANIMATION_START to "animation_page_title",
            SettingsSearchFocusIds.ANIMATION_VISUAL_EFFECTS to "animation_glass_title",
        ),
    )

    EntranceGroup {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = LocalSettingsTopContentPadding.current,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
    ) {

            //  界面动效（全 App 入场）
            if (!advancedOnly) {
            item(key = "animation_page_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(stringResource(R.string.animation_section_page))
                }
            }
            item(key = "animation_page") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        com.android.purebilibili.feature.settings.ScrollFeedbackPreference()
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.ui_entrance_animation_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.UI_ENTRANCE_ANIMATION),
                                title = localizedAnimationSettingTitle("animation.ui_entrance_animation_enabled"),
                                subtitle = stringResource(R.string.animation_ui_entrance_summary),
                                checked = uiEntranceAnimationEnabled,
                                onCheckedChange = { value ->
                                    scope.launch {
                                        SettingsManager.setUiEntranceAnimationEnabled(context, value)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.global_drag_feedback_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.DRAG_FEEDBACK),
                                title = localizedAnimationSettingTitle("animation.global_drag_feedback_enabled"),
                                subtitle = stringResource(R.string.animation_drag_feedback_summary),
                                checked = globalDragFeedbackEnabled,
                                onCheckedChange = { value ->
                                    scope.launch { SettingsManager.setGlobalDragFeedbackEnabled(context, value) }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.haptic_feedback_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HAPTIC_FEEDBACK),
                                title = localizedAnimationSettingTitle("animation.haptic_feedback_enabled"),
                                subtitle = stringResource(R.string.animation_haptic_summary),
                                checked = state.hapticFeedbackEnabled,
                                onCheckedChange = viewModel::toggleHapticFeedback,
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        if (state.hapticFeedbackEnabled) {
                            AppPreferenceDivider()
                            HapticScenePreference(enabled = true)
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.global_text_tap_copy_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.COPY_TEXT),
                                title = localizedAnimationSettingTitle("animation.global_text_tap_copy_enabled"),
                                subtitle = stringResource(R.string.animation_tap_copy_summary),
                                checked = globalTextTapCopyEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setGlobalTextTapCopyEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        if (entranceDowngradedBySystem) {
                            AppPreferenceDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                AppText(
                                    text = stringResource(R.string.animation_reduced_motion_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText()
                                )
                            }
                        }
                    }
                }
            }

            item(key = "animation_maid_lottie_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(stringResource(R.string.animation_section_maid))
                }
            }
            item(key = "animation_maid_lottie") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        SettingsItemAnchor("animation.maid_lottie_animation_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.ANIMATION),
                                title = localizedAnimationSettingTitle("animation.maid_lottie_animation_enabled"),
                                subtitle = stringResource(R.string.animation_maid_summary),
                                checked = maidLottieAnimationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setMaidLottieAnimationEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                    }
                }
            }

            //  卡片动画
            item(key = "animation_card_title") {
                Box(modifier = Modifier.entrance()) {
                    SettingsCardPreviewGroupHeader(
                        enabledStates = mapOf(
                            SettingsFeaturePreview.CARD_ENTRANCE to state.cardAnimationEnabled,
                            SettingsFeaturePreview.CARD_EXPAND to state.cardTransitionEnabled,
                            SettingsFeaturePreview.LIVE_FRAME to liveSurfaceCardTransitionEnabled,
                            SettingsFeaturePreview.RETURN_CONTENT to appNavigationSettings.videoReturnContentFollowProgressEnabled,
                            SettingsFeaturePreview.RETURN_TILT to appNavigationSettings.videoSharedReturnGestureFollowEnabled,
                            SettingsFeaturePreview.RETURN_FOLLOW to appNavigationSettings.videoSharedReturnGestureTranslationEnabled,
                            SettingsFeaturePreview.TRANSITION_BLUR to videoTransitionRealtimeBlurEnabled,
                        ),
                        transitionDurationMillis = previewTransitionDurationMillis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "animation_card") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                            SettingsItemAnchor("animation.card_animation_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.CARD_ENTRANCE_ANIMATION),
                                title = localizedAnimationSettingTitle("animation.card_animation_enabled"),
                                subtitle = stringResource(R.string.animation_card_entrance_summary),
                                checked = state.cardAnimationEnabled,
                                onCheckedChange = { viewModel.toggleCardAnimation(it) },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.card_transition_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.CARD_TRANSITION_ANIMATION),
                                title = localizedAnimationSettingTitle("animation.card_transition_enabled"),
                                subtitle = stringResource(R.string.animation_card_transition_summary),
                                checked = state.cardTransitionEnabled,
                                onCheckedChange = { viewModel.toggleCardTransition(it) },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.live_surface_card_transition_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.LIVE_SURFACE_TRANSITION),
                                title = localizedAnimationSettingTitle("animation.live_surface_card_transition_enabled"),
                                subtitle = stringResource(R.string.animation_live_transition_summary),
                                checked = liveSurfaceCardTransitionEnabled,
                                onCheckedChange = { viewModel.toggleLiveSurfaceCardTransition(it) },
                                enabled = state.cardTransitionEnabled,
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.app_navigation_settings.video_return_content_follow_progress_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.RETURN_CONTENT_FOLLOW),
                                title = localizedAnimationSettingTitle("animation.app_navigation_settings.video_return_content_follow_progress_enabled"),
                                subtitle = if (appNavigationSettings.videoReturnContentFollowProgressEnabled) {
                                    stringResource(R.string.animation_return_follow_on)
                                } else {
                                    stringResource(R.string.animation_return_follow_off)
                                },
                                checked = appNavigationSettings.videoReturnContentFollowProgressEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setVideoReturnContentFollowProgressEnabled(context, enabled)
                                    }
                                },
                                enabled = state.cardTransitionEnabled,
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.app_navigation_settings.video_shared_return_gesture_follow_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.RETURN_GESTURE_POSE),
                                title = localizedAnimationSettingTitle("animation.app_navigation_settings.video_shared_return_gesture_follow_enabled"),
                                subtitle = if (appNavigationSettings.videoSharedReturnGestureFollowEnabled) {
                                    stringResource(R.string.animation_return_tilt_on)
                                } else {
                                    stringResource(R.string.animation_return_tilt_off)
                                },
                                checked = appNavigationSettings.videoSharedReturnGestureFollowEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setVideoSharedReturnGestureFollowEnabled(
                                            context,
                                            enabled,
                                        )
                                    }
                                },
                                enabled = state.cardTransitionEnabled,
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.app_navigation_settings.video_shared_return_gesture_translation_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.RETURN_GESTURE_TRANSLATION),
                                title = localizedAnimationSettingTitle("animation.app_navigation_settings.video_shared_return_gesture_translation_enabled"),
                                subtitle = if (appNavigationSettings.videoSharedReturnGestureTranslationEnabled) {
                                    stringResource(R.string.animation_return_translation_on)
                                } else {
                                    stringResource(R.string.animation_return_translation_off)
                                },
                                checked = appNavigationSettings.videoSharedReturnGestureTranslationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setVideoSharedReturnGestureTranslationEnabled(context, enabled)
                                    }
                                },
                                enabled = state.cardTransitionEnabled,
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.video_transition_realtime_blur_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.WALLPAPER_EFFECT),
                                title = localizedAnimationSettingTitle("animation.video_transition_realtime_blur_enabled"),
                                subtitle = stringResource(R.string.animation_transition_blur_summary),
                                checked = videoTransitionRealtimeBlurEnabled,
                                onCheckedChange = { viewModel.toggleVideoTransitionRealtimeBlur(it) },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.app_navigation_settings.settings_container_morph_enabled") {
                            SettingsSingleChoicePreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.SETTINGS_CONTAINER_MORPH),
                                title = stringResource(R.string.animation_settings_container_title),
                                subtitle = stringResource(R.string.animation_settings_container_summary),
                                options = settingsContainerMorphOptions,
                                selectedValue = settingsContainerMorphEnabled,
                                onSelectionChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setSettingsContainerMorphEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.app_navigation_settings.settings_container_realtime_blur_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.SETTINGS_CONTAINER_BLUR),
                                title = settingItemTitle("animation.app_navigation_settings.settings_container_realtime_blur_enabled"),
                                subtitle = stringResource(R.string.animation_settings_container_blur_summary),
                                checked = settingsContainerRealtimeBlurEnabled,
                                enabled = settingsContainerMorphEnabled && android.os.Build.VERSION.SDK_INT >= 31,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setSettingsContainerRealtimeBlurEnabled(context, enabled) }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PREDICTIVE_BACK),
                            title = stringResource(R.string.animation_back_title),
                            subtitle = stringResource(R.string.animation_back_summary),
                            options = predictiveBackStyleOptions,
                            selectedValue = predictiveBackStyle,
                            onSelectionChange = { style ->
                                scope.launch {
                                    SettingsManager.setPredictiveBackEnabled(context, true)
                                    SettingsManager.setPredictiveBackAnimationStyle(
                                        context,
                                        style.storageValue,
                                    )
                                }
                            },
                            iconTint = AppContentColors.secondaryText()
                        )
                        if (predictiveBackStyle == BiliPaiPredictiveBackAnimationStyle.MIUIX) {
                            AppPreferenceDivider()
                            AppSliderDialogPreference(
                                title = stringResource(R.string.animation_back_preview_title),
                                subtitle = stringResource(R.string.animation_back_preview_summary),
                                value = appNavigationSettings
                                    .miuixPredictiveBackMaxProgressPercent
                                    .toFloat(),
                                onValueChange = { value ->
                                    scope.launch {
                                        SettingsManager.setMiuixPredictiveBackMaxProgressPercent(
                                            context,
                                            value.roundToInt(),
                                        )
                                    }
                                },
                                valueRange = 0f..100f,
                                steps = 99,
                                valueFormatter = { value -> "${value.roundToInt()}%" },
                            )
                        }
                        if (predictiveBackStyle != BiliPaiPredictiveBackAnimationStyle.NONE) {
                            AppPreferenceDivider()
                            SettingsItemAnchor("animation.app_navigation_settings.miuix_transition_blur_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(
                                        SettingsIconRole.MIUIX_TRANSITION_BLUR
                                    ),
                                    title = localizedAnimationSettingTitle("animation.app_navigation_settings.miuix_transition_blur_enabled"),
                                    subtitle = if (appNavigationSettings.miuixTransitionBlurEnabled) {
                                        stringResource(R.string.animation_back_blur_on)
                                    } else {
                                        stringResource(R.string.animation_back_blur_off)
                                    },
                                    checked = appNavigationSettings.miuixTransitionBlurEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            SettingsManager.setMiuixTransitionBlurEnabled(
                                                context,
                                                enabled,
                                            )
                                        }
                                    },
                                    iconTint = AppContentColors.secondaryText(),
                                )
                            }
                        }
                        if (predictiveBackStyle == BiliPaiPredictiveBackAnimationStyle.SCALE) {
                            AppPreferenceDivider()
                            SettingsSingleChoicePreference(
                                title = stringResource(R.string.animation_exit_direction_title),
                                subtitle = stringResource(R.string.animation_exit_direction_summary),
                                options = predictiveBackExitDirectionOptions,
                                selectedValue = predictiveBackExitDirection,
                                onSelectionChange = { direction ->
                                    scope.launch {
                                        SettingsManager.setPredictiveBackExitDirection(
                                            context,
                                            direction.storageValue,
                                        )
                                    }
                                },
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.full_screen_swipe_back_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_SWIPE_BACK),
                                title = localizedAnimationSettingTitle("animation.full_screen_swipe_back_enabled"),
                                subtitle = if (fullScreenSwipeBackEnabled) {
                                    stringResource(R.string.animation_fullscreen_swipe_back_on)
                                } else {
                                    stringResource(R.string.animation_fullscreen_swipe_back_off)
                                },
                                checked = fullScreenSwipeBackEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setFullScreenSwipeBackEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.video_shared_transition_speed") {
                            SettingsSingleChoicePreference(
                                title = localizedAnimationSettingTitle("animation.video_shared_transition_speed"),
                                subtitle = stringResource(R.string.animation_video_speed_summary),
                                options = sharedTransitionSpeedOptions,
                                selectedValue = state.videoSharedTransitionSpeed,
                                onSelectionChange = viewModel::setVideoSharedTransitionSpeed
                            )
                        }
                        if (state.videoSharedTransitionSpeed == VideoSharedTransitionSpeed.CUSTOM) {
                            AppPreferenceDivider()
                            AppSliderDialogPreference(
                                title = stringResource(R.string.animation_custom_duration_title),
                                subtitle = stringResource(R.string.animation_custom_duration_summary),
                                value = customTransitionDurationMillis.toFloat(),
                                onValueChange = { value ->
                                    val snappedValue = snapCustomTransitionDuration(value)
                                    customTransitionDurationMillis = snappedValue
                                    viewModel.setVideoSharedTransitionCustomDurationMillis(snappedValue)
                                },
                                valueRange = VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS.toFloat()..
                                    VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS.toFloat(),
                                steps = (
                                    (VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS -
                                        VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS) / 20
                                    ) - 1,
                                valueFormatter = { value -> "${value.roundToInt()}ms" },
                            )
                        }
                        AppPreferenceDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            AppText(
                                text = stringResource(R.string.animation_home_motion_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.secondaryText()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AppText(
                                text = motionTierLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = AppContentColors.primaryText()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AppText(
                                text = motionTierHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AppText(
                                text = stringResource(R.string.animation_home_motion_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }

            // ✨ 视觉效果
            }
            item(key = "animation_glass_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(if (advancedOnly) stringResource(R.string.animation_glass_advanced_title) else stringResource(R.string.animation_glass_section_title))
                }
            }
            item(key = "animation_glass") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        if (!isLiquidGlassAvailable || !state.androidNativeLiquidGlassEnabled) {
                            AppText(
                                text = if (!isLiquidGlassAvailable) stringResource(R.string.animation_glass_unavailable) else stringResource(R.string.animation_glass_disabled),
                                modifier = Modifier.padding(16.dp),
                                color = AppContentColors.secondaryText(),
                            )
                        }
                        SettingsItemAnchor("animation.app_navigation_settings.liquid_indicator_wobble_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.LIQUID_INDICATOR_WOBBLE),
                                title = stringResource(R.string.animation_indicator_wobble_title),
                                subtitle = stringResource(R.string.animation_indicator_wobble_summary),
                                checked = appNavigationSettings.liquidIndicatorWobbleEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setLiquidIndicatorWobbleEnabled(context, enabled) }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        LiquidGlassAdjustmentPanel(
                                alwaysShowAdvanced = false,
                                persistedProgress = state.liquidGlassProgress,
                                previewImageUri = liquidGlassPreviewImageUri,
                                persistedAdvancedSettings = liquidGlassAdvancedSettings,
                                persistedReadabilityMode = liquidGlassReadabilityMode,
                                bottomBarItems = previewBottomBarItems,
                                bottomBarSearchEnabled = state.bottomBarSearchEnabled,
                                onProgressCommitted = viewModel::setLiquidGlassProgress,
                                onPreviewImageChanged = viewModel::setLiquidGlassPreviewImageUri,
                                onAdvancedSettingsCommitted =
                                    viewModel::setLiquidGlassAdvancedSettings,
                                onReadabilityModeChanged =
                                    viewModel::setLiquidGlassReadabilityMode,
                                onImportSettings = {
                                    liquidGlassImportLauncher.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/json",
                                            "text/plain",
                                        )
                                    )
                                },
                                isImportingSettings = isLiquidGlassImporting,
                                onShareSettings = {
                                    scope.launch {
                                        liquidGlassShareService
                                            .createLiquidGlassShareUri()
                                            .onSuccess { shareUri ->
                                                runCatching {
                                                    val shareIntent = Intent(
                                                        Intent.ACTION_SEND
                                                    ).apply {
                                                        type = "application/json"
                                                        putExtra(
                                                            Intent.EXTRA_STREAM,
                                                            shareUri,
                                                        )
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            context.getString(R.string.animation_glass_share_text),
                                                        )
                                                        addFlags(
                                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        )
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            shareIntent,
                                                            context.getString(R.string.animation_glass_share_title),
                                                        )
                                                    )
                                                }.onFailure { error ->
                                                    Toast.makeText(
                                                        context,
                                                        error.message ?: context.getString(R.string.animation_glass_share_failed),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                            .onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    error.message ?: context.getString(R.string.animation_glass_export_failed),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                    }
                                },
                            )
                        AppPreferenceDivider()
                        if (!advancedOnly) {
                        SettingsItemAnchor("animation.skeleton_breathing_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.SKELETON_BREATHING),
                                title = localizedAnimationSettingTitle("animation.skeleton_breathing_enabled"),
                                subtitle = stringResource(R.string.animation_skeleton_summary),
                                checked = skeletonBreathingEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        com.android.purebilibili.core.store.SkeletonSettingsStore
                                            .setBreathingEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        // 磨砂效果 (始终显示)
                        SettingsItemAnchor("animation.header_blur_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.TOP_BAR_BLUR),
                                title = localizedAnimationSettingTitle("animation.header_blur_enabled"),
                                subtitle = stringResource(R.string.animation_header_blur_summary),
                                checked = state.headerBlurEnabled,
                                onCheckedChange = { viewModel.toggleHeaderBlur(it) },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.progressive_top_blur_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESSIVE_TOP_BLUR),
                                title = localizedAnimationSettingTitle("animation.progressive_top_blur_enabled"),
                                subtitle = stringResource(R.string.animation_progressive_blur_summary),
                                checked = state.progressiveTopBlurEnabled,
                                onCheckedChange = { viewModel.toggleProgressiveTopBlur(it) },
                                titleTrailingContent = {
                                    ProgressiveBlurComparisonPreviewEntry(liquidGlassAdvancedSettings)
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor(PROGRESSIVE_BLUR_RENDERER_SETTING_ID) {
                            SettingsSingleChoicePreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESSIVE_BLUR_RENDERER),
                                title = settingItemTitle(PROGRESSIVE_BLUR_RENDERER_SETTING_ID, context),
                                subtitle = stringResource(
                                    if (com.android.purebilibili.feature.home.components.isOfficialProgressiveBlurSupported())
                                        R.string.animation_progressive_renderer_summary
                                    else R.string.animation_progressive_renderer_fallback_summary,
                                ),
                                options = listOf(
                                    AppSegmentOption(
                                        com.android.purebilibili.core.store.home.ProgressiveBlurRenderer.MIUIX,
                                        "Miuix",
                                        description = stringResource(R.string.animation_progressive_renderer_miuix_description),
                                    ),
                                    AppSegmentOption(
                                        com.android.purebilibili.core.store.home.ProgressiveBlurRenderer.ANDROID,
                                        stringResource(R.string.animation_progressive_renderer_android),
                                        description = stringResource(R.string.animation_progressive_renderer_android_description),
                                    ),
                                ),
                                selectedValue = progressiveBlurRenderer,
                                onSelectionChange = { renderer ->
                                    scope.launch {
                                        com.android.purebilibili.core.store.home.LiquidGlassSettingsStore
                                            .setProgressiveBlurRenderer(context, renderer)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.progressive_top_fade_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESSIVE_TOP_FADE),
                                title = localizedAnimationSettingTitle("animation.progressive_top_fade_enabled"),
                                subtitle = stringResource(R.string.animation_progressive_fade_summary),
                                checked = state.progressiveTopFadeEnabled,
                                onCheckedChange = { viewModel.toggleProgressiveTopFade(it) },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("animation.bottom_bar_blur_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.BOTTOM_BAR_BLUR),
                                title = localizedAnimationSettingTitle("animation.bottom_bar_blur_enabled"),
                                subtitle = stringResource(R.string.animation_bottom_blur_summary),
                                checked = state.bottomBarBlurEnabled,
                                onCheckedChange = { viewModel.toggleBottomBarBlur(it) },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }

                        // 模糊强度（仅在任意模糊开启时显示）
                        if (state.headerBlurEnabled || state.progressiveTopBlurEnabled || state.bottomBarBlurEnabled) {
                            AppPreferenceDivider()
                            BlurIntensitySelector(
                                selectedIntensity = state.blurIntensity,
                                onIntensityChange = { viewModel.setBlurIntensity(it) }
                            )
                        }
                        }
                    }
                }
            }

            //  提示
            if (!advancedOnly) {
            item {
                Box(modifier = Modifier.entrance()) {
                    AppSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_lightbulb_24),
                                contentDescription = null,
                                tint = warningTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AppText(
                                text = stringResource(R.string.animation_performance_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }

            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    val importSession = pendingLiquidGlassImport
    if (importSession != null) {
        val importCount = remember(importSession) {
            flattenSettingsShareSections(importSession.profile.sections).size
        }
        AppAlertDialog(
            onDismissRequest = {
                if (!isLiquidGlassImporting) pendingLiquidGlassImport = null
            },
            title = {
                AppText(
                    text = stringResource(R.string.animation_import_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(
                        text = stringResource(R.string.animation_import_profile, importSession.profile.profileName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppText(
                        text = stringResource(R.string.animation_import_details, importCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppText(
                        text = stringResource(R.string.animation_import_unchanged),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.link(),
                    )
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        if (isLiquidGlassImporting) return@AppDialogAction
                        scope.launch {
                            isLiquidGlassImporting = true
                            try {
                                liquidGlassShareService.applyLiquidGlassImport(importSession)
                                    .onSuccess { result ->
                                        pendingLiquidGlassImport = null
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.animation_import_success, result.appliedKeys.size),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.message ?: context.getString(R.string.animation_glass_import_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                            } finally {
                                isLiquidGlassImporting = false
                            }
                        }
                    },
                ) {
                    AppText(if (isLiquidGlassImporting) stringResource(R.string.animation_import_applying) else stringResource(R.string.animation_import_confirm))
                }
            },
            dismissButton = {
                AppDialogAction(
                    onClick = {
                        if (!isLiquidGlassImporting) pendingLiquidGlassImport = null
                    },
                ) {
                    AppText(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
