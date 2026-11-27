// 文件路径: feature/settings/PlaybackSettingsScreen.kt
package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.components.AppSegmentOption
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.purebilibili.R
import com.android.purebilibili.core.store.DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED
import com.android.purebilibili.core.store.DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
import com.android.purebilibili.core.store.DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED
import com.android.purebilibili.core.store.DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED
import com.android.purebilibili.core.store.DEFAULT_PLAYBACK_SPEED_OPTIONS
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_ALPHA_MAX
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_ALPHA_MIN
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_SCALE_MAX
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_SCALE_MIN
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_STEP
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.store.player.DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.store.ChapterDisplayMode
import com.android.purebilibili.core.store.BottomProgressBehavior
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.store.PortraitPlayerCollapseMode
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.feature.screenshot.AppScreenshotCaptureMode
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureMode
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.settings.PlaybackCdnPreferenceMenu
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 *  播放设置二级页面
 * iOS 风格设计
 */
enum class PlaybackSettingsPage(val title: String) {
    PLAYBACK(settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title),
    FULLSCREEN("全屏与手势"), COMMENTS("评论与内容"),
    DECODER("视频解码"), DIAGNOSTICS("播放器诊断")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlaybackSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    page: PlaybackSettingsPage = PlaybackSettingsPage.PLAYBACK,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeConfiguration = rememberSettingsHomeConfiguration()
    val effectivePage = page
    val screenTitle = effectivePage.title
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
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides homeConfiguration.isLiquidGlassEnabled) {
            PlaybackSettingsContent(viewModel = viewModel, state = state, page = effectivePage)
        }
    }
}

/**
 * 播放设置内容 - 可在 BottomSheet 中或分栏布局中复用
 */
@Composable
fun PlaybackSettingsContent(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    page: PlaybackSettingsPage = PlaybackSettingsPage.PLAYBACK,
) {
    val preferencesState by viewModel.playbackPreferences.collectAsStateWithLifecycle()
    val playbackPreferences = preferencesState
    if (playbackPreferences == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppCircularProgressIndicator()
        }
        return
    }
    val playerConfiguration = rememberSettingsPlayerConfiguration()
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val warningTint = AppContentColors.warning()
    val windowSizeClass = LocalWindowSizeClass.current
    // val state by viewModel.state.collectAsStateWithLifecycle() // Moved to parameter
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    LaunchedEffect(focusRequest?.token, page) {
        val request = focusRequest ?: return@LaunchedEffect
        val playbackFocusId = when (request.target) {
            SettingsSearchTarget.PLAYBACK -> request.focusId
            else -> resolveSettingsSceneDetailFocus(request.target)
                ?.takeIf { it.target == SettingsSearchTarget.PLAYBACK }
                ?.focusId
        } ?: return@LaunchedEffect
        val index = resolvePlaybackSettingsItemScrollIndex(page, playbackFocusId, request.settingId)
        if (index == null) {
            SettingsSearchFocusController.clear(request.token)
            return@LaunchedEffect
        }
        androidx.compose.runtime.withFrameNanos { }
        if (request.settingId != null) listState.scrollToItem(index) else listState.animateScrollToItem(index)
        if (request.settingId == null) SettingsSearchFocusController.clear(request.token)
        else clearUnclaimedSettingsFocusRequest(request.token)
    }


    var showPipPermissionDialog by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()
    val fullscreenBrightness = if (page == PlaybackSettingsPage.PLAYBACK || page == PlaybackSettingsPage.FULLSCREEN) {
        // 授权结果和设置写入由页面持有，滚动回收亮度所在项时仍可完成。
        rememberPlaybackSystemBrightnessControl(
            context,
            settingsScope,
            playerConfiguration.setSystemBrightnessEnabled,
        )
    } else {
        null
    }

    val miniPlayerMode = playbackPreferences.miniPlayerMode
    val stopPlaybackOnExit = playbackPreferences.stopPlaybackOnExit
    val backgroundPlaybackEnabled = playbackPreferences.backgroundPlaybackEnabled
    val modeControlsEnabled = !stopPlaybackOnExit && backgroundPlaybackEnabled
    var pipPermissionGranted by remember(context) { mutableStateOf<Boolean?>(null) }
    val audioFocusEnabled = playbackPreferences.audioFocusEnabled
    val audioModeAutoPipEnabled = playbackPreferences.audioModeAutoPipEnabled
    val audioNowPlayingBarEnabled = playbackPreferences.audioNowPlayingBarEnabled
    val audioNowPlayingBarDeleteButton by remember(context) {
        SettingsManager.getAudioNowPlayingBarDeleteButton(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val audioNowPlayingBarImmersiveEnabled = playbackPreferences.audioNowPlayingBarImmersiveEnabled
    val audioNowPlayingBarOpensAudioMode = playbackPreferences.audioNowPlayingBarOpensAudioMode
    val musicLyricsUiStyle = playbackPreferences.musicLyricsUiStyle
    val loudnessNormalizationEnabled = playbackPreferences.loudnessNormalizationEnabled
    val audioSpectrumEnabled = playbackPreferences.audioSpectrumEnabled
    val startupAutoPlayEnabled = playbackPreferences.startupAutoPlayEnabled
    val playerDiagnosticLoggingEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPlayerDiagnosticLoggingEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED)
    val playerInsightMode by remember(context) { SettingsManager.getPlayerInsightMode(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getPlayerInsightModeSync(context))
    val dashSegmentRequestsEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDashSegmentRequestsEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED)
    val qualitySwitchFailureDialogEnabled by remember(context) { SettingsManager.getQualitySwitchFailureDialogEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED)
    val qualitySwitchFailureDialogOnceEnabled by remember(context) { SettingsManager.getQualitySwitchFailureDialogOnceEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED)
    val playbackSpeedOptions by remember(context) { SettingsManager.getPlaybackSpeedOptions(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_PLAYBACK_SPEED_OPTIONS)
    val longPressSpeed = playerConfiguration.longPressSpeed
    val defaultPlaybackSpeed by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDefaultPlaybackSpeed(context) }.collectAsStateWithLifecycle(initialValue = 1.0f)
    val rememberLastPlaybackSpeed by remember(context) { com.android.purebilibili.core.store.SettingsManager.getRememberLastPlaybackSpeed(context) }.collectAsStateWithLifecycle(initialValue = false)
    val longPressSpeedHintHidden = playerConfiguration.longPressSpeedHintHidden
    val longPressSpeedHintScale = playerConfiguration.longPressSpeedHintScale
    val longPressSpeedHintAlpha = playerConfiguration.longPressSpeedHintAlpha
    val videoCodecPreference = playbackPreferences.videoCodecPreference
    val videoSecondCodecPreference = playbackPreferences.videoSecondCodecPreference
    val playbackCdnPreferenceValue by remember(context) { SettingsManager.getPlaybackCdnPreference(context) }
        .collectAsStateWithLifecycle(initialValue = PlaybackCdnPreference.DEFAULT.storageValue)
    val playbackCdnPreference = remember(playbackCdnPreferenceValue) {
        PlaybackCdnPreference.fromStorageValue(playbackCdnPreferenceValue)
    }

    // ... [保留原有逻辑: checkPipPermission, gotoPipSettings] ...

    // 检查画中画权限
    fun checkPipPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        // 设备本身要支持画中画特性
        if (!context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
        ) return false
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    context.applicationInfo.uid,
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    context.applicationInfo.uid,
                    context.packageName
                )
            }
            // MODE_DEFAULT 表示"跟随系统默认"，实际是允许的：部分 ROM（MIUI/澎湃等）
            // 在用户授权后仍返回 MODE_DEFAULT，只认 MODE_ALLOWED 会误判成未授权，
            // 导致设置里的小窗相关开关被禁用。
            mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
        } catch (_: Exception) {
            false
        }
    }

    if (page == PlaybackSettingsPage.PLAYBACK && modeControlsEnabled && miniPlayerMode.supportsSystemPip) {
        // 权限跟随页面前台生命周期，滚动回收画中画列表项时无需重新查询系统。
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            pipPermissionGranted = checkPipPermission()
        }
    }

    // 跳转到系统设置
    fun gotoPipSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    "android.settings.PICTURE_IN_PICTURE_SETTINGS",
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        }
    }

    // 权限弹窗逻辑
    if (showPipPermissionDialog) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showPipPermissionDialog = false },
            title = { AppText("权限申请", color = AppContentColors.primaryText()) },
            text = { AppText("检测到未开启「画中画」权限。请在设置中开启该权限，否则无法使用小窗播放。", color = AppContentColors.secondaryText()) },
            confirmButton = {
                com.android.purebilibili.core.ui.AppDialogAction(
                    onClick = {
                        gotoPipSettings()
                        showPipPermissionDialog = false
                    }
                ) { AppText("去设置") }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = { showPipPermissionDialog = false }) {
                    AppText("暂不开启", color = AppContentColors.secondaryText())
                }
            }
        )
    }

    EntranceGroup {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = LocalSettingsTopContentPadding.current,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
    ) {

            if (page == PlaybackSettingsPage.PLAYBACK || page == PlaybackSettingsPage.DECODER) {
            item(key = SettingsSearchFocusIds.PLAYBACK_DECODER + "_title") {
                Column(modifier = Modifier.entrance()) {
                    if (page == PlaybackSettingsPage.PLAYBACK) {
                        PlaybackSceneRecipesSection()
                        PlaybackSectionQuickJumpBar(
                            onSelectSection = { focusId ->
                                com.android.purebilibili.core.util.AnalyticsHelper.logFeatureAction(
                                    feature = "playback_settings",
                                    action = "quick_jump:$focusId",
                                )
                                val targetIndex = resolvePlaybackSettingsItemScrollIndex(
                                    page = page,
                                    focusId = focusId,
                                    settingId = null,
                                )
                                if (targetIndex != null) {
                                    settingsScope.launch {
                                        listState.animateScrollToItem((targetIndex - 1).coerceAtLeast(0))
                                    }
                                }
                            },
                        )
                    }
                    AppPreferenceSectionTitle("视频解码")
                }
            }
            item(key = SettingsSearchFocusIds.PLAYBACK_DECODER) {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val codecOptions = listOf(
                        AppSegmentOption("avc1", "AVC"),
                        AppSegmentOption("hev1", "HEVC"),
                        AppSegmentOption("av01", "AV1")
                    )
                    fun codecDescription(codec: String): String = when (codec) {
                        "avc1" -> "兼容设备最多，其他编码无法播放时优先尝试"
                        "hev1" -> "画质与流量更平衡，多数新设备推荐"
                        "av01" -> "更节省流量，但需要较新的设备支持"
                        else -> "未知"
                    }
                    AppPreferenceGroup {
                                SettingsItemAnchor("playback.hw_decode") {
                                    AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.HARDWARE_DECODER),
                                title = playbackSettingTitle("playback.hw_decode"),
                                subtitle = "开启优先硬解，遇到绿屏可尝试关闭以优先系统软解；不可用时尝试其他解码器。软解可能更耗电，下次创建播放器时生效",
                                checked = state.hwDecode,
                                onCheckedChange = {
                                    viewModel.toggleHwDecode(it)
                                    //  [埋点] 设置变更追踪
                                    com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange("hw_decode", it.toString())
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                                }
                        AppPreferenceDivider()
SettingsItemAnchor("playback.video_codec_preference") {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_CODEC),
                            title = settingItemTitle("playback.video_codec_preference"),
                            subtitle = codecDescription(videoCodecPreference),
                            options = codecOptions,
                            selectedValue = videoCodecPreference,
                            onSelectionChange = { codec ->
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setVideoCodec(context, codec)
                                }
                            }
                        )
}
                        AppPreferenceDivider()
SettingsItemAnchor("playback.video_second_codec_preference") {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.FALLBACK_VIDEO_CODEC),
                            title = settingItemTitle("playback.video_second_codec_preference"),
                            subtitle = codecDescription(videoSecondCodecPreference),
                            options = codecOptions,
                            selectedValue = videoSecondCodecPreference,
                            onSelectionChange = { codec ->
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setVideoSecondCodec(context, codec)
                                }
                            }
                        )
}
                    }
                }
            }

            }

            if (page == PlaybackSettingsPage.PLAYBACK) {
            item(key = SettingsSearchFocusIds.PLAYBACK_SPEED + "_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("播放速度")
                }
            }
            item(key = SettingsSearchFocusIds.PLAYBACK_SPEED) {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    AppPreferenceGroup {
                                SettingsItemAnchor("playback.remember_last_playback_speed") {
                                    AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.REMEMBER_PLAYBACK_SPEED),
                                title = playbackSettingTitle("playback.remember_last_playback_speed"),
                                subtitle = if (rememberLastPlaybackSpeed) {
                                    "新视频沿用上次手动选择的倍速"
                                } else {
                                    "关闭时将使用默认播放速度"
                                },
                                checked = rememberLastPlaybackSpeed,
                                onCheckedChange = {
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setRememberLastPlaybackSpeed(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                                }
                        AppPreferenceDivider()
                        val longPressSpeedSlideMode by remember(context) {
                            SettingsManager.getLongPressSpeedSlideMode(context)
                        }.collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.LongPressSpeedSlideMode.PRESETS)
                        SettingsItemAnchor("playback.long_press_speed_slide_adjust_enabled") {
                            SettingsSingleChoicePreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.LONG_PRESS_SPEED_SLIDE),
                                title = playbackSettingTitle("playback.long_press_speed_slide_adjust_enabled"),
                                subtitle = when (longPressSpeedSlideMode) {
                                    com.android.purebilibili.core.store.LongPressSpeedSlideMode.OFF ->
                                        "关闭后长按始终使用固定预设倍速"
                                    com.android.purebilibili.core.store.LongPressSpeedSlideMode.PRESETS ->
                                        "长按左右滑动按档位切换 0.5x–5.0x 倍率"
                                    com.android.purebilibili.core.store.LongPressSpeedSlideMode.CONTINUOUS ->
                                        "长按左右滑动按 0.1x 无极调节 0.5x–5.0x 倍率"
                                },
                                options = listOf(
                                    AppSegmentOption(
                                        com.android.purebilibili.core.store.LongPressSpeedSlideMode.OFF,
                                        com.android.purebilibili.core.store.LongPressSpeedSlideMode.OFF.label,
                                    ),
                                    AppSegmentOption(
                                        com.android.purebilibili.core.store.LongPressSpeedSlideMode.PRESETS,
                                        com.android.purebilibili.core.store.LongPressSpeedSlideMode.PRESETS.label,
                                    ),
                                    AppSegmentOption(
                                        com.android.purebilibili.core.store.LongPressSpeedSlideMode.CONTINUOUS,
                                        com.android.purebilibili.core.store.LongPressSpeedSlideMode.CONTINUOUS.label,
                                    ),
                                ),
                                selectedValue = longPressSpeedSlideMode,
                                onSelectionChange = { mode ->
                                    scope.launch {
                                        SettingsManager.setLongPressSpeedSlideMode(context, mode)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                            LongPressSlideSpeedLivePreview(mode = longPressSpeedSlideMode)
                        }
                        AppPreferenceDivider()
                        val longPressSpeedLockEnabled by remember(context) {
                            SettingsManager.getLongPressSpeedLockEnabled(context)
                        }.collectAsStateWithLifecycle(initialValue = SettingsManager.getLongPressSpeedLockEnabledSync(context))
                        SettingsItemAnchor("playback.long_press_speed_lock_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_LOCK),
                                title = playbackSettingTitle("playback.long_press_speed_lock_enabled"),
                                subtitle = "长按倍速后向下滑动锁定当前倍速，松手继续保持加速",
                                checked = longPressSpeedLockEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setLongPressSpeedLockEnabled(context, enabled) }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        val twoFingerVerticalSpeedEnabled by remember(context) {
                            SettingsManager.getTwoFingerVerticalSpeedEnabled(context)
                        }.collectAsStateWithLifecycle(initialValue = false)
                        SettingsItemAnchor("playback.two_finger_vertical_speed_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.LONG_PRESS_SPEED_SLIDE),
                                title = playbackSettingTitle("playback.two_finger_vertical_speed_enabled"),
                                subtitle = "在视频画面中双指上下滑动，随时调节播放倍速",
                                checked = twoFingerVerticalSpeedEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setTwoFingerVerticalSpeedEnabled(context, enabled) }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("playback.long_press_speed_hint_hidden") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.LONG_PRESS_SPEED_HINT),
                                title = playbackSettingTitle("playback.long_press_speed_hint_hidden"),
                                subtitle = if (longPressSpeedHintHidden) {
                                    "长按临时加速仍会生效，但不再显示倍速浮层"
                                } else {
                                    "长按临时加速时显示当前倍速"
                                },
                                checked = longPressSpeedHintHidden,
                                onCheckedChange = { hidden ->
                                    scope.launch {
                                        SettingsManager.setLongPressSpeedHintHidden(context, hidden)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val hintScaleSteps = (
                                (LONG_PRESS_SPEED_HINT_SCALE_MAX - LONG_PRESS_SPEED_HINT_SCALE_MIN) /
                                    LONG_PRESS_SPEED_HINT_STEP
                                ).roundToInt() - 1
                            var hintScale by remember { mutableFloatStateOf(longPressSpeedHintScale) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "倍速提示大小",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AppContentColors.primaryText()
                                    )
                                    Text(
                                        text = "调整长按加速提示的整体大小",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                                Surface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${(hintScale * 100f).roundToInt()}%",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            AppSlider(
                                value = hintScale,
                                onValueChange = { hintScale = it },
                                onValueChangeFinished = {
                                    scope.launch {
                                        SettingsManager.setLongPressSpeedHintScale(context, hintScale)
                                    }
                                },
                                valueRange = LONG_PRESS_SPEED_HINT_SCALE_MIN..LONG_PRESS_SPEED_HINT_SCALE_MAX,
                                steps = hintScaleSteps
                            )

                            val hintAlphaSteps = (
                                (LONG_PRESS_SPEED_HINT_ALPHA_MAX - LONG_PRESS_SPEED_HINT_ALPHA_MIN) /
                                    LONG_PRESS_SPEED_HINT_STEP
                                ).roundToInt() - 1
                            var hintAlpha by remember { mutableFloatStateOf(longPressSpeedHintAlpha) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "播放浮层背景深浅",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AppContentColors.primaryText()
                                    )
                                    Text(
                                        text = "调高后，倍速提示与播放器提示浮层的背景更深",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                                Surface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${(hintAlpha * 100f).roundToInt()}%",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            AppSlider(
                                value = hintAlpha,
                                onValueChange = { hintAlpha = it },
                                onValueChangeFinished = {
                                    scope.launch {
                                        SettingsManager.setLongPressSpeedHintAlpha(context, hintAlpha)
                                    }
                                },
                                valueRange = LONG_PRESS_SPEED_HINT_ALPHA_MIN..LONG_PRESS_SPEED_HINT_ALPHA_MAX,
                                steps = hintAlphaSteps
                            )
                        }
                        AppPreferenceDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlaybackSpeedOptionsMenuPreference(
                                options = playbackSpeedOptions,
                                defaultSpeed = defaultPlaybackSpeed,
                                onAddSpeed = { speed ->
                                    scope.launch {
                                        SettingsManager.addPlaybackSpeedOption(context, speed)
                                    }
                                },
                                onRemoveSpeed = { speed ->
                                    scope.launch {
                                        SettingsManager.removePlaybackSpeedOption(context, speed)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            AppPreferenceDivider()
                            AppSingleChoicePreference(
                                selectedValue = defaultPlaybackSpeed,
                                options = playbackSpeedOptions.map { AppChoiceOption(it, formatPlaybackSpeed(it)) },
                                presentation = AppSingleChoicePresentation.WINDOW_POPUP,
                                onValueChange = { speed ->
                                    scope.launch {
                                        SettingsManager.setDefaultPlaybackSpeed(context, speed)
                                    }
                                },
                                title = "默认播放速度",
                                subtitle = "新视频使用此倍速；“记住上次倍速”优先",
                                modifier = Modifier.fillMaxWidth()
                            )
                            AppPreferenceDivider()
                            LongPressSpeedPreferenceControl(
                                currentSpeed = longPressSpeed,
                                onSpeedChange = { speed ->
                                    scope.launch {
                                        SettingsManager.setLongPressSpeed(context, speed)
                                    }
                                },
                                title = "长按临时加速",
                                subtitle = "点按右侧倍速，选择预设或输入 1–8x",
                                titleTrailingContent = {
                                    SettingsFeaturePreviewEntry(
                                        feature = SettingsFeaturePreview.LONG_PRESS,
                                        currentEnabled = true,
                                        speed = longPressSpeed,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            //  小窗播放
            }

            if (page == PlaybackSettingsPage.PLAYBACK) {
            item(key = SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER + "_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("小窗与后台")
                }
            }
            miniPlayerPreferenceGroup(PlaybackMiniPlayerGroup.BACKGROUND) {
                val miniPlayerOptions = remember {
                    listOf(
                        AppSegmentOption(SettingsManager.MiniPlayerMode.OFF, "默认"),
                        AppSegmentOption(SettingsManager.MiniPlayerMode.IN_APP_ONLY, "小窗"),
                        AppSegmentOption(SettingsManager.MiniPlayerMode.SYSTEM_PIP, "画中画"),
                        AppSegmentOption(SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP, "两种小窗"),
                    )
                }
                SettingsItemAnchor("playback.stop_playback_on_exit") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.STOP_ON_EXIT),
                        title = playbackSettingTitle("playback.stop_playback_on_exit"),
                        subtitle = "开启后，返回其他页面时立即停止，也不会进入小窗或后台播放",
                        checked = stopPlaybackOnExit,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setStopPlaybackOnExit(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.background_playback_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.BACKGROUND_PLAYBACK),
                        title = playbackSettingTitle("playback.background_playback_enabled"),
                        subtitle = if (backgroundPlaybackEnabled) {
                            "离开应用或锁屏后继续播放"
                        } else {
                            "关闭后离开应用或锁屏时停止播放"
                        },
                        checked = backgroundPlaybackEnabled,
                        onCheckedChange = { enabled ->
                            settingsScope.launch {
                                if (enabled && stopPlaybackOnExit) {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setStopPlaybackOnExit(context, false)
                                }
                                com.android.purebilibili.core.store.SettingsManager
                                    .setBackgroundPlaybackEnabled(context, enabled)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_focus_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_FOCUS),
                        title = playbackSettingTitle("playback.audio_focus_enabled"),
                        subtitle = if (audioFocusEnabled) {
                            "播放视频时会请求其他音乐或视频应用暂停"
                        } else {
                            "关闭后可能与其他应用同时发声"
                        },
                        checked = audioFocusEnabled,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setAudioFocusEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.mini_player_mode") {
                    SettingsSingleChoicePreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.EXIT_PLAYER_MODE),
                        title = settingItemTitle("playback.mini_player_mode"),
                        subtitle = if (stopPlaybackOnExit) {
                            "当前离开视频页会停止播放；直接选择小窗或画中画将自动开启继续播放"
                        } else if (!backgroundPlaybackEnabled) {
                            "当前未开启后台播放；直接选择小窗或画中画将自动为你开启"
                        } else {
                            miniPlayerMode.description
                        },
                        options = miniPlayerOptions,
                        selectedValue = miniPlayerMode,
                        enabled = true,
                        onSelectionChange = { mode ->
                            settingsScope.launch {
                                if (mode != SettingsManager.MiniPlayerMode.OFF) {
                                    if (stopPlaybackOnExit) {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setStopPlaybackOnExit(context, false)
                                    }
                                    if (!backgroundPlaybackEnabled) {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setBackgroundPlaybackEnabled(context, true)
                                    }
                                }
                                com.android.purebilibili.core.store.SettingsManager
                                    .setMiniPlayerMode(context, mode)
                            }
                            if (mode.supportsSystemPip &&
                                !checkPipPermission()
                            ) {
                                showPipPermissionDialog = true
                            }
                        }
                    )
                }
            }

            miniPlayerPreferenceGroup(PlaybackMiniPlayerGroup.NOW_PLAYING_BAR) {
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_now_playing_bar_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOW_PLAYING_BAR),
                        title = playbackSettingTitle("playback.audio_now_playing_bar_enabled"),
                        subtitle = if (audioNowPlayingBarEnabled) {
                            "离开视频页后，底部保留当前视频入口"
                        } else {
                            "关闭后返回首页等页面时不显示底部视频入口"
                        },
                        checked = audioNowPlayingBarEnabled,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setAudioNowPlayingBarEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_now_playing_bar_opens_audio_mode") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_MODE_ENTRY),
                        title = playbackSettingTitle("playback.audio_now_playing_bar_opens_audio_mode"),
                        subtitle = if (audioNowPlayingBarOpensAudioMode) {
                            "点击底部视频入口时跳转到听视频"
                        } else {
                            "关闭后点击底部视频入口时跳转到视频详情页（默认）"
                        },
                        checked = audioNowPlayingBarOpensAudioMode,
                        onCheckedChange = {
                            settingsScope.launch {
                                SettingsManager.setAudioNowPlayingBarOpensAudioMode(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_now_playing_bar_delete_button") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_BAR_DELETE_BUTTON),
                        title = playbackSettingTitle("playback.audio_now_playing_bar_delete_button"),
                        subtitle = "在底部播放条右上角显示关闭按钮，点击停止播放并移除",
                        checked = audioNowPlayingBarDeleteButton,
                        onCheckedChange = { enabled ->
                            settingsScope.launch { SettingsManager.setAudioNowPlayingBarDeleteButton(context, enabled) }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_now_playing_bar_immersive_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_TITLE_AUTO_HIDE),
                        title = playbackSettingTitle("playback.audio_now_playing_bar_immersive_enabled"),
                        subtitle = if (audioNowPlayingBarImmersiveEnabled) {
                            "播放中 5 秒不操作时收起标题；点底部播放条恢复"
                        } else {
                            "关闭后底部播放条标题始终显示"
                        },
                        checked = audioNowPlayingBarImmersiveEnabled,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setAudioNowPlayingBarImmersiveEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
            }

            miniPlayerPreferenceGroup(PlaybackMiniPlayerGroup.AUDIO) {
                AppPreferenceDivider()
                SettingsItemAnchor("playback.loudness_normalization_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.LOUDNESS_NORMALIZATION),
                        title = playbackSettingTitle("playback.loudness_normalization_enabled"),
                        subtitle = if (loudnessNormalizationEnabled) {
                            "自动拉平不同曲目间的响度差异；切换后重新开始播放生效"
                        } else {
                            "关闭后保留各视频原始响度"
                        },
                        checked = loudnessNormalizationEnabled,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setLoudnessNormalizationEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                val sleepTimerFadeOutEnabled by remember(context) {
                    SettingsManager.getSleepTimerFadeOutEnabled(context)
                }.collectAsStateWithLifecycle(initialValue = SettingsManager.getSleepTimerFadeOutEnabledSync(context))
                val sleepTimerFadeDurationSec by remember(context) {
                    SettingsManager.getSleepTimerFadeDurationSec(context)
                }.collectAsStateWithLifecycle(initialValue = SettingsManager.getSleepTimerFadeDurationSecSync(context))
                SettingsItemAnchor("playback.sleep_timer_fade_out_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.LOUDNESS_NORMALIZATION),
                        title = playbackSettingTitle("playback.sleep_timer_fade_out_enabled"),
                        subtitle = if (sleepTimerFadeOutEnabled) {
                            "定时关闭前平滑降低音量，避免突然中断"
                        } else {
                            "到达定时后立即停止播放"
                        },
                        checked = sleepTimerFadeOutEnabled,
                        onCheckedChange = { enabled ->
                            settingsScope.launch { SettingsManager.setSleepTimerFadeOutEnabled(context, enabled) }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                if (sleepTimerFadeOutEnabled) {
                    AppPreferenceDivider()
                    SettingsItemAnchor("playback.sleep_timer_fade_duration_sec") {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.TIME_STATUS),
                            title = playbackSettingTitle("playback.sleep_timer_fade_duration_sec"),
                            subtitle = "定时结束前开始平滑降音的时长",
                            options = listOf(5, 12, 20, 30).map { sec -> AppSegmentOption(sec, "${sec} 秒") },
                            selectedValue = sleepTimerFadeDurationSec,
                            onSelectionChange = { sec ->
                                settingsScope.launch { SettingsManager.setSleepTimerFadeDurationSec(context, sec) }
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_spectrum_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_SPECTRUM),
                        title = playbackSettingTitle("playback.audio_spectrum_enabled"),
                        subtitle = "听视频时让封面背景光晕随声音变化；无需录音权限",
                        checked = audioSpectrumEnabled,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setAudioSpectrumEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.startup_auto_play_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.STARTUP_RESUME),
                        title = playbackSettingTitle("playback.startup_auto_play_enabled"),
                        subtitle = if (startupAutoPlayEnabled) {
                            "重新打开应用并进入播放器后，继续上次播放"
                        } else {
                            "关闭后需要手动恢复播放"
                        },
                        checked = startupAutoPlayEnabled,
                        onCheckedChange = {
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setStartupAutoPlayEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.music_lyrics_ui_style") {
                    SettingsSingleChoicePreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_LYRICS),
                        title = settingItemTitle("playback.music_lyrics_ui_style"),
                        subtitle = when (musicLyricsUiStyle) {
                            SettingsManager.MusicLyricsUiStyle.CLASSIC ->
                                "经典全屏歌词；可在听视频右上角更多菜单临时切换"
                            SettingsManager.MusicLyricsUiStyle.IMMERSIVE ->
                                "大字歌词，随播放逐字高亮"
                            SettingsManager.MusicLyricsUiStyle.ARC ->
                                "左对齐 3D 弧面倾斜歌词，当前句居中高亮"
                        },
                        options = listOf(
                            AppSegmentOption(
                                SettingsManager.MusicLyricsUiStyle.CLASSIC,
                                SettingsManager.MusicLyricsUiStyle.CLASSIC.label
                            ),
                            AppSegmentOption(
                                SettingsManager.MusicLyricsUiStyle.IMMERSIVE,
                                SettingsManager.MusicLyricsUiStyle.IMMERSIVE.label
                            ),
                            AppSegmentOption(
                                SettingsManager.MusicLyricsUiStyle.ARC,
                                SettingsManager.MusicLyricsUiStyle.ARC.label
                            ),
                        ),
                        selectedValue = musicLyricsUiStyle,
                        onSelectionChange = { style ->
                            settingsScope.launch {
                                SettingsManager.setMusicLyricsUiStyle(context, style)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                    MusicLyricsStyleLivePreview(style = musicLyricsUiStyle)
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.music_theme_color_mode") {
                    val musicThemeColorMode by remember(context) {
                        com.android.purebilibili.core.store.AudioLyricsSettingsStore.observeThemeColorMode(context)
                    }.collectAsStateWithLifecycle(
                        initialValue = com.android.purebilibili.core.store.MusicThemeColorMode.FOLLOW_COVER
                    )
                    val musicCustomHex by remember(context) {
                        com.android.purebilibili.core.store.AudioLyricsSettingsStore.observeCustomThemeColorHex(context)
                    }.collectAsStateWithLifecycle(
                        initialValue = com.android.purebilibili.core.store.AudioLyricsSettingsStore.DEFAULT_CUSTOM_THEME_COLOR_HEX
                    )
                    Column {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_SPECTRUM),
                            title = "听视频背景主题色",
                            subtitle = when (musicThemeColorMode) {
                                com.android.purebilibili.core.store.MusicThemeColorMode.FOLLOW_COVER ->
                                    "跟随当前视频封面自动取色；也可切换为固定氛围色或自定义色"
                                com.android.purebilibili.core.store.MusicThemeColorMode.FOLLOW_APP_THEME ->
                                    "与当前应用的全局主题色保持一致"
                                com.android.purebilibili.core.store.MusicThemeColorMode.CUSTOM ->
                                    "使用自定义十六进制色值：$musicCustomHex"
                                else -> "当前使用「${musicThemeColorMode.label}」沉浸氛围背景"
                            },
                            options = com.android.purebilibili.core.store.MusicThemeColorMode.entries.map {
                                AppSegmentOption(it, it.label)
                            },
                            selectedValue = musicThemeColorMode,
                            onSelectionChange = { mode ->
                                settingsScope.launch {
                                    com.android.purebilibili.core.store.AudioLyricsSettingsStore
                                        .setThemeColorMode(context, mode)
                                }
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        if (musicThemeColorMode == com.android.purebilibili.core.store.MusicThemeColorMode.CUSTOM) {
                            var hexInput by remember(musicCustomHex) { mutableStateOf(musicCustomHex) }
                            val previewArgb = com.android.purebilibili.core.store.AudioLyricsSettingsStore
                                .parseHexColorArgb(hexInput)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color(previewArgb ?: 0xFF2F7DFF))
                                )
                                com.android.purebilibili.core.ui.components.AppOutlinedTextField(
                                    value = hexInput,
                                    onValueChange = { hexInput = it.take(7) },
                                    label = { AppText("色值 (#RRGGBB)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                com.android.purebilibili.core.ui.components.AppButton(
                                    onClick = {
                                        settingsScope.launch {
                                            com.android.purebilibili.core.store.AudioLyricsSettingsStore
                                                .setCustomThemeColorHex(context, hexInput)
                                        }
                                    },
                                    enabled = previewArgb != null,
                                ) {
                                    AppText("应用")
                                }
                            }
                        }
                    }
                }
                if (musicLyricsUiStyle == SettingsManager.MusicLyricsUiStyle.ARC) {
                    val arcTiltIntensity by remember(context) {
                        com.android.purebilibili.core.store.AudioLyricsSettingsStore.observeArcTiltIntensity(context)
                    }.collectAsStateWithLifecycle(
                        initialValue = com.android.purebilibili.core.store.ArcLyricsTiltIntensity.STANDARD
                    )
                    val arcCurveDirection by remember(context) {
                        com.android.purebilibili.core.store.AudioLyricsSettingsStore.observeArcCurveDirection(context)
                    }.collectAsStateWithLifecycle(
                        initialValue = com.android.purebilibili.core.store.ArcLyricsCurveDirection.LEFT
                    )
                    AppPreferenceDivider()
                    SettingsSingleChoicePreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_LYRICS_ARC_TILT),
                        title = "弧面倾斜强度",
                        subtitle = "调节 3D 弧面歌词的倾斜角度与纵深收拢",
                        options = com.android.purebilibili.core.store.ArcLyricsTiltIntensity.entries.map {
                            AppSegmentOption(it, it.label)
                        },
                        selectedValue = arcTiltIntensity,
                        onSelectionChange = { intensity ->
                            settingsScope.launch {
                                com.android.purebilibili.core.store.AudioLyricsSettingsStore.setArcTiltIntensity(
                                    context,
                                    intensity,
                                )
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                    AppPreferenceDivider()
                    SettingsSingleChoicePreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_LYRICS_ARC_DIRECTION),
                        title = "弧面偏移方向",
                        subtitle = "控制上下歌词向左侧或右侧弯曲收拢",
                        options = com.android.purebilibili.core.store.ArcLyricsCurveDirection.entries.map {
                            AppSegmentOption(it, it.label)
                        },
                        selectedValue = arcCurveDirection,
                        onSelectionChange = { direction ->
                            settingsScope.launch {
                                com.android.purebilibili.core.store.AudioLyricsSettingsStore.setArcCurveDirection(
                                    context,
                                    direction,
                                )
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
            }

            miniPlayerPreferenceGroup(PlaybackMiniPlayerGroup.PICTURE_IN_PICTURE) {
                val pipNoDanmakuEnabled = playerConfiguration.pipNoDanmakuEnabled
                val audioModeAutoPipToggleEnabled =
                    SettingsManager.shouldEnableAudioModeAutoPipToggle(miniPlayerMode) && backgroundPlaybackEnabled
                val pipDanmakuToggleEnabled =
                    backgroundPlaybackEnabled && miniPlayerMode != SettingsManager.MiniPlayerMode.OFF

                if (modeControlsEnabled && miniPlayerMode.supportsSystemPip && pipPermissionGranted == false) {
                    AppPreferenceDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPipPermissionDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            rememberSettingsVector(R.drawable.ph_warning_24, R.drawable.ms_warning_24),
                            contentDescription = null,
                            tint = warningTint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                "画中画权限未开启",
                                style = MaterialTheme.typography.bodyMedium,
                                color = warningTint
                            )
                            AppText(
                                "点击前往系统设置开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText()
                            )
                        }
                        AppIcon(
                            rememberSettingsVector(R.drawable.ph_caret_right_24, R.drawable.ms_keyboard_arrow_right_24),
                            contentDescription = null,
                            tint = AppContentColors.secondaryText().copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.pip_no_danmaku_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.PIP_DANMAKU),
                        title = playbackSettingTitle("playback.pip_no_danmaku_enabled"),
                        subtitle = if (!backgroundPlaybackEnabled) {
                            "需先开启后台播放"
                        } else if (miniPlayerMode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF) {
                            if (pipNoDanmakuEnabled) "小窗和画中画中不显示弹幕" else "小窗和画中画中显示弹幕"
                        } else {
                            "选择小窗或画中画模式后生效"
                        },
                        checked = pipNoDanmakuEnabled,
                        enabled = pipDanmakuToggleEnabled,
                        onCheckedChange = {
                            if (!pipDanmakuToggleEnabled) {
                                return@AppSwitchPreference
                            }
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setPipNoDanmakuEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("playback.audio_mode_auto_pip_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_MODE_PIP),
                        title = playbackSettingTitle("playback.audio_mode_auto_pip_enabled"),
                        subtitle = if (audioModeAutoPipToggleEnabled) {
                            if (audioModeAutoPipEnabled) {
                                "回到桌面或使用离开手势时，自动进入画中画"
                            } else {
                                "关闭后仅保留听视频页内的画中画按钮"
                            }
                        } else {
                            "仅支持系统画中画的模式下生效"
                        },
                        checked = audioModeAutoPipEnabled,
                        enabled = audioModeAutoPipToggleEnabled,
                        onCheckedChange = {
                            if (!audioModeAutoPipToggleEnabled) {
                                return@AppSwitchPreference
                            }
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setAudioModeAutoPipEnabled(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
            }

            //  手势设置
            }

            if (page == PlaybackSettingsPage.PLAYBACK && com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED) {
                item(key = "playback_nearby_handoff_title") {
                    Box(modifier = Modifier.entrance()) {
                        AppPreferenceSectionTitle("跨设备播放")
                    }
                }
                item(key = "playback_nearby_handoff") {
                    Box(modifier = Modifier.entrance()) {
                        val nearbyHandoff = com.android.purebilibili.feature.nearby.LocalNearbyHandoff.current
                        val copy = com.android.purebilibili.feature.settings.settingsDestinationCopy(
                            com.android.purebilibili.feature.settings.SettingsSearchTarget.NEARBY_HANDOFF
                        )
                        AppPreferenceGroup {
                            SettingsItemAnchor("playback.nearby_handoff") {
                                com.android.purebilibili.core.ui.components.AppPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.NEARBY_HANDOFF),
                                    title = copy.title,
                                    subtitle = copy.summary,
                                    onClick = { nearbyHandoff?.openDeviceManager?.invoke() },
                                )
                            }
                        }
                    }
                }
            }

            if (page == PlaybackSettingsPage.PLAYBACK || page == PlaybackSettingsPage.DIAGNOSTICS) {
            item(key = SettingsSearchFocusIds.PLAYBACK_DEBUG + "_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("诊断")
                }
            }
            item(key = SettingsSearchFocusIds.PLAYBACK_DEBUG) {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    PlaybackDiagnosticsSection()
                }
            }

            //  网络与画质
            }

            if (page == PlaybackSettingsPage.PLAYBACK) {
            item(key = SettingsSearchFocusIds.PLAYBACK_NETWORK + "_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("网络与画质")
                }
            }
            item(key = SettingsSearchFocusIds.PLAYBACK_NETWORK) {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val wifiQuality = playbackPreferences.wifiQuality
                    val mobileQuality = playbackPreferences.mobileQuality
                    val defaultAudioQuality = playbackPreferences.defaultAudioQuality
                    val autoHighestQualityEnabled = playbackPreferences.autoHighestQualityEnabled
                    val directedTrafficEnabled = playbackPreferences.directedTrafficEnabled
                    val resilientVideoBuffer by remember(context) { SettingsManager.getResilientVideoBuffer(context) }
                        .collectAsStateWithLifecycle(initialValue = SettingsManager.getResilientVideoBufferSync(context))
                    val isLoggedIn = com.android.purebilibili.data.repository.VideoRepository.isPlaybackLoggedIn()
                    val isVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip()

                    val qualityOptions = resolveDefaultPlaybackQualityOptions()
                    val audioQualityOptions = resolveDefaultAudioQualityOptions(defaultAudioQuality)
                    val normalizedDefaultAudioQuality =
                        normalizeDefaultAudioQualityOption(defaultAudioQuality)

                    fun getQualityLabel(id: Int): String = resolveSelectionLabel(
                        options = qualityOptions,
                        selectedValue = id,
                        fallbackLabel = "720P"
                    )

                    fun getAudioQualityLabel(id: Int): String = resolveSelectionLabel(
                        options = audioQualityOptions,
                        selectedValue = id,
                        fallbackLabel = "跟随上次"
                    )

                    AppPreferenceGroup {
                        SettingsItemAnchor("playback.playback_cdn_preference") {
                            PlaybackCdnPreferenceMenu(
                                icon = rememberSettingsVector(R.drawable.ms_router_24),
                                title = settingItemTitle("playback.playback_cdn_preference"),
                                preference = playbackCdnPreference,
                                onPreferenceChange = { preference ->
                                    scope.launch {
                                        SettingsManager.setPlaybackCdnPreference(context, preference.storageValue)
                                    }
                                },
                            )
                        }

                        AppPreferenceDivider()

                        SettingsItemAnchor("playback.resilient_video_buffer") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.RESILIENT_VIDEO_BUFFER),
                                title = playbackSettingTitle("playback.resilient_video_buffer"),
                                subtitle = "增加视频预缓冲以应对网络波动，可能消耗更多流量；新播放器生效",
                                checked = resilientVideoBuffer,
                                onCheckedChange = { enabled ->
                                    scope.launch { SettingsManager.setResilientVideoBuffer(context, enabled) }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                        AppPreferenceDivider()

                                SettingsItemAnchor("playback.directed_traffic_enabled") {
                                    AppSwitchPreference(
                                        icon = rememberSettingsSemanticIcon(SettingsIconRole.DIRECTED_TRAFFIC),
                                title = playbackSettingTitle("playback.directed_traffic_enabled"),
                                subtitle = if (directedTrafficEnabled) {
                                    "移动网络下尝试使用套餐内定向流量（实验性）"
                                } else {
                                    "若套餐含 B 站定向流量，建议开启"
                                },
                                checked = directedTrafficEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setBiliDirectedTrafficEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                                }

                        AppPreferenceDivider()

                            SettingsItemAnchor("playback.auto_highest_quality_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_HIGHEST_QUALITY),
                                title = playbackSettingTitle("playback.auto_highest_quality_enabled"),
                                subtitle = if (autoHighestQualityEnabled) {
                                    "自动选择账号和设备支持的最高画质"
                                } else {
                                    "关闭后按下方的无线网络和移动网络默认画质播放"
                                },
                                checked = autoHighestQualityEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setAutoHighestQuality(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }

                        AppPreferenceDivider()

SettingsItemAnchor("playback.wifi_quality") {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.WIFI_PLAYBACK_QUALITY),
                            title = settingItemTitle("playback.wifi_quality"),
                            subtitle = if (autoHighestQualityEnabled) {
                                "关闭“自动最高画质”后生效"
                            } else {
                                resolveDefaultQualitySubtitle(
                                    rawQuality = wifiQuality,
                                    fallbackSubtitle = "仅无线网络环境生效",
                                    isLoggedIn = isLoggedIn,
                                    isVip = isVip
                                )
                            },
                            options = qualityOptions,
                            selectedValue = wifiQuality,
                            enabled = !autoHighestQualityEnabled,
                            onSelectionChange = { qualityId ->
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setWifiQuality(context, qualityId)
                                }
                            }
                        )
}

                        AppPreferenceDivider()

                        // 📉 读取省流量模式，用于显示提示
                        val dataSaverModeForHint = playbackPreferences.dataSaverMode
                        val isDataSaverActive = dataSaverModeForHint != com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF
                        val effectiveQuality = resolveEffectiveMobileQuality(
                            rawMobileQuality = mobileQuality,
                            isDataSaverActive = isDataSaverActive
                        )
                        val effectiveQualityLabel = getQualityLabel(effectiveQuality)

SettingsItemAnchor("playback.mobile_quality") {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.MOBILE_PLAYBACK_QUALITY),
                            title = settingItemTitle("playback.mobile_quality"),
                            subtitle = when {
                                autoHighestQualityEnabled ->
                                    "关闭“自动最高画质”后生效"
                                isDataSaverActive && mobileQuality > effectiveQuality ->
                                    "省流量模式当前实际最高为 $effectiveQualityLabel"
                                else -> resolveDefaultQualitySubtitle(
                                    rawQuality = mobileQuality,
                                    fallbackSubtitle = "仅移动网络环境生效",
                                    isLoggedIn = isLoggedIn,
                                    isVip = isVip
                                )
                            },
                            options = qualityOptions,
                            selectedValue = mobileQuality,
                            enabled = !autoHighestQualityEnabled,
                            onSelectionChange = { qualityId ->
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setMobileQuality(context, qualityId)
                                }
                            }
                        )
}

                        AppPreferenceDivider()

                        SettingsItemAnchor("playback.default_audio_quality") {
SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_QUALITY),
                            title = settingItemTitle("playback.default_audio_quality"),
                            subtitle = if (
                                normalizedDefaultAudioQuality ==
                                DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
                            ) {
                                "新视频跟随播放器上次手动选择"
                            } else {
                                "新视频使用所选音质，播放时可临时切换"
                            },
                            options = audioQualityOptions,
                            selectedValue = normalizedDefaultAudioQuality,
                            onSelectionChange = { audioQuality ->
                                scope.launch {
                                    PlayerSettingsStore
                                        .setDefaultAudioQuality(context, audioQuality)
                                }
                            }
                        )
}

                        if (isDataSaverActive && mobileQuality > effectiveQuality) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(
                                    text = "省流量模式下，画质最高为 480P",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppContentColors.success().copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 📉 省流量模式
            }

            if (page == PlaybackSettingsPage.PLAYBACK) {
            item(key = SettingsSearchFocusIds.PLAYBACK_DATA_SAVER + "_title") {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("省流量")
                }
            }
            item(key = SettingsSearchFocusIds.PLAYBACK_DATA_SAVER) {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val dataSaverMode = playbackPreferences.dataSaverMode
                    val homeSettings = rememberSettingsHomeConfiguration()
                    val dataSaverModeOptions = listOf(
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF, "关闭"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY, "仅移动数据"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.ALWAYS, "始终开启")
                    )

                    AppPreferenceGroup {
SettingsItemAnchor("playback.data_saver_mode") {
                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DATA_SAVER_MODE),
                            title = settingItemTitle("playback.data_saver_mode"),
                            subtitle = dataSaverMode.description,
                            options = dataSaverModeOptions,
                            selectedValue = dataSaverMode,
                            onSelectionChange = { mode ->
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setDataSaverMode(context, mode)
                                }
                            }
                        )
}

                        AppPreferenceDivider()

                            SettingsItemAnchor("playback.home_settings.low_quality_home_cover_in_data_saver") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.DATA_SAVER_COVER_QUALITY),
                                title = playbackSettingTitle("playback.home_settings.low_quality_home_cover_in_data_saver"),
                                subtitle = if (homeSettings.lowQualityHomeCoverInDataSaver) {
                                    "省流量时进一步降低首页封面分辨率"
                                } else {
                                    "保留高清尺寸，图片压缩质量仍遵循省流量模式"
                                },
                                checked = homeSettings.lowQualityHomeCoverInDataSaver,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setLowQualityHomeCoverInDataSaver(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                    }
                }
            }
            }

            if (page == PlaybackSettingsPage.PLAYBACK) {
                item(key = SettingsSearchFocusIds.PLAYBACK_INTERACTION + "_title") {
                    AppPreferenceSectionTitle("字幕与连播")
                }
                item(key = SettingsSearchFocusIds.PLAYBACK_INTERACTION) {
                    PlaybackInteractionSettingsSection(
                        context = context,
                        state = state,
                        viewModel = viewModel,
                        playbackControls = true,
                    )
                }
            }
            if (page == PlaybackSettingsPage.PLAYBACK || page == PlaybackSettingsPage.COMMENTS) {
                item(key = SettingsSearchFocusIds.PLAYBACK_COMMENTS + "_title") {
                    AppPreferenceSectionTitle("互动与评论")
                }
                item(key = SettingsSearchFocusIds.PLAYBACK_COMMENTS) {
                    PlaybackInteractionSettingsSection(
                        context = context,
                        state = state,
                        viewModel = viewModel,
                        playbackControls = false,
                    )
                }
            }
            if (fullscreenBrightness != null) {
                item(key = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN + "_title") {
                    AppPreferenceSectionTitle("全屏与手势")
                }
                playbackFullscreenGestureSettings(
                    context = context,
                    viewModel = viewModel,
                    scope = settingsScope,
                    brightness = fullscreenBrightness,
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
}
}
}

@Composable
private fun PlaybackInteractionSettingsSection(
    context: Context,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    playbackControls: Boolean,
) {
    val playerConfiguration = rememberSettingsPlayerConfiguration()
    val scope = rememberCoroutineScope()
    val danmakuTapInteractionEnabled by remember(context) { SettingsManager.getDanmakuTapInteractionEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val hideInteractiveCommandDanmaku by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDanmakuHideInteractiveCommands(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    //  [新增] 自动播放下一个
    val autoPlayEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getAutoPlay(context) }.collectAsStateWithLifecycle(initialValue = true)
    val showDetailRelatedVideos by remember(context) { SettingsManager.getShowDetailRelatedVideos(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val nextWatchHintEnabled by remember(context) { SettingsManager.getNextWatchHintEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val externalPlaylistAutoContinueEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getExternalPlaylistAutoContinue(context) }.collectAsStateWithLifecycle(initialValue = true)
    val resumePlaybackPromptEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getResumePlaybackPromptEnabled(context) }.collectAsStateWithLifecycle(initialValue = true)
    val spacePlayedVideoLocatePromptEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getSpacePlayedVideoLocatePromptEnabled(context) }.collectAsStateWithLifecycle(initialValue = true)
    val playbackCompletionBehavior by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPlaybackCompletionBehavior(context) }
        .collectAsStateWithLifecycle(initialValue = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC)
    val subtitleFeatureEnabled = isSubtitleFeatureEnabledForUser()
    val subtitleAutoPreference = playerConfiguration.subtitleAutoPreference
    var showSubtitleAppearance by remember { mutableStateOf(false) }
    val subtitleAppearance by remember(context) {
        com.android.purebilibili.feature.video.subtitle.SubtitleToolsStore.appearance(context)
    }.collectAsStateWithLifecycle(initialValue = com.android.purebilibili.feature.video.ui.components.PlayerSubtitleAppearance())
    if (showSubtitleAppearance) {
        com.android.purebilibili.feature.video.ui.components.PlayerSubtitleSettingsPanel(
            appearance = subtitleAppearance,
            onAppearanceChange = { value -> scope.launch {
                com.android.purebilibili.feature.video.subtitle.SubtitleToolsStore.setAppearance(context, value)
            } },
            onDismiss = { showSubtitleAppearance = false },
        )
    }

    val videoAiSummaryEntryEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoAiSummaryEntryEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val videoDetailBgmVisible by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoDetailBgmVisible(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val videoNoteEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoNoteEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val videoNoteDefaultCollapsed by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoNoteDefaultCollapsed(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val videoInfoDefaultExpanded by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoInfoDefaultExpanded(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val videoArgueMsgShown by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoArgueMsgShown(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val showVideoDetailCommentCount by remember(context) { SettingsManager.getShowVideoDetailCommentCount(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val commentFraudDetectionEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getCommentFraudDetectionEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val commentAvatarPushEnabled by remember(context) { SettingsManager.getCommentAvatarPushEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val subReplyPreviewLikesEnabled by remember(context) { SettingsManager.getSubReplyPreviewLikesEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val commentMemberDecorationsEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getCommentMemberDecorationsEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val commentHideSearchKeywords by remember(context) { SettingsManager.getCommentHideSearchKeywords(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val commentIpLocationEnabled by remember(context) { SettingsManager.getCommentIpLocationEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val commentSexEnabled by remember(context) { SettingsManager.getCommentSexEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val detailedCommentTimeEnabled by remember(context) { SettingsManager.getDetailedCommentTimeEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val subReplyThreadedEnabled by remember(context) { SettingsManager.getSubReplyThreadedEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val subReplyLoadedCountEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getSubReplyLoadedCountEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val imagePreviewLongPressSaveEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getImagePreviewLongPressSaveEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val imagePreview3dPageEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getImagePreview3dPageEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val commentCollapsedReplyPreviewLimit by remember(context) { com.android.purebilibili.core.store.SettingsManager.getCommentCollapsedReplyPreviewLimit(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager
                .DEFAULT_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT
        )
    val subtitlePreferenceDescription = when (subtitleAutoPreference) {
        SubtitleAutoPreference.OFF -> "默认关闭字幕"
        SubtitleAutoPreference.ON -> "自动开启可用字幕"
        SubtitleAutoPreference.WITHOUT_AI -> "仅自动启用非 AI 字幕"
        SubtitleAutoPreference.AUTO -> "静音时可自动启用 AI 字幕"
        SubtitleAutoPreference.CHINESE_ONLY -> "只开启中文字幕，含 AI 中文；没有中文字幕时不开启"
    }

    AppPreferenceGroup {
        // --- Click to Play ---
        val clickToPlayEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getClickToPlay(context) }.collectAsStateWithLifecycle(initialValue = true)

            if (playbackControls) {
        SettingsItemAnchor("playback.click_to_play_enabled") {
            AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_PLAY_ON_OPEN),
                title = playbackSettingTitle("playback.click_to_play_enabled"),
                subtitle = if (clickToPlayEnabled) {
                    "进入视频详情页时自动开始播放；不影响已开始的连续播放"


        } else {
                    "进入视频详情页需手动播放；开始播放后仍可按播完策略连续播放"
                },
                checked = clickToPlayEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setClickToPlay(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.resume_playback_prompt_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.RESUME_PLAYBACK_PROMPT),
                title = playbackSettingTitle("playback.resume_playback_prompt_enabled"),
                subtitle = if (resumePlaybackPromptEnabled) {
                    "检测到历史进度时仅提醒一次"
                } else {
                    "关闭后不再弹出“继续播放”提示"
                },
                checked = resumePlaybackPromptEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setResumePlaybackPromptEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
        val showContinuePartNotice = com.android.purebilibili.core.store.SettingsManager
            .getShowContinuePartNotice(context)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.SettingsManager
                    .getShowContinuePartNoticeSync(context)
            ).value
        SettingsItemAnchor("playback.show_continue_part_notice") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.CONTINUE_PART_NOTICE),
                title = playbackSettingTitle("playback.show_continue_part_notice"),
                subtitle = if (showContinuePartNotice) {
                    "显示“上次看到第 N P”，点击跳转；仅在上次观看的是其他分P时出现"
                } else {
                    "关闭后不再展示分P继续播放提示"
                },
                checked = showContinuePartNotice,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setShowContinuePartNotice(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.space_played_video_locate_prompt_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SPACE_PLAYED_VIDEO_LOCATE),
                title = playbackSettingTitle("playback.space_played_video_locate_prompt_enabled"),
                subtitle = if (spacePlayedVideoLocatePromptEnabled) {
                    "从视频进入 UP 主页时，显示刚看过的视频入口"
                } else {
                    "关闭后不再显示“刚刚看过”的定位提示"
                },
                checked = spacePlayedVideoLocatePromptEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setSpacePlayedVideoLocatePromptEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.show_detail_related_videos") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.RECOMMEND_RETENTION),
                title = playbackSettingTitle("playback.show_detail_related_videos"),
                subtitle = if (showDetailRelatedVideos) {
                    "在视频详情页下方显示相关推荐视频"
                } else {
                    "关闭后隐藏视频详情页相关推荐区域，不影响自动连播"
                },
                checked = showDetailRelatedVideos,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setShowDetailRelatedVideos(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.next_watch_hint_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.NEXT_VIDEO_HINT),
                title = playbackSettingTitle("playback.next_watch_hint_enabled"),
                subtitle = "视频最后 5 秒显示“接下来观看”提示条（与全屏片尾倒计时浮卡独立控制），关闭不影响自动续播",
                checked = nextWatchHintEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setNextWatchHintEnabled(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        val playbackOrderOptions = listOf(
            AppSegmentOption(PlaybackCompletionBehavior.STOP_AFTER_CURRENT, "暂停"),
            AppSegmentOption(PlaybackCompletionBehavior.PLAY_IN_ORDER, "顺序"),
            AppSegmentOption(PlaybackCompletionBehavior.REPEAT_ONE, "单个循环"),
            AppSegmentOption(PlaybackCompletionBehavior.LOOP_PLAYLIST, "列表循环"),
            AppSegmentOption(PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC, "自动")
        )
        val autoContinueControlsEnabled =
            playbackCompletionBehavior == PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC
        SettingsItemAnchor("playback.playback_completion_behavior") {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK_COMPLETION),
                title = settingItemTitle("playback.playback_completion_behavior"),
                subtitle = "选择播完后行为；选“自动”时按下方两个续播开关决定是否继续播放",
                options = playbackOrderOptions,
                selectedValue = playbackCompletionBehavior,
                onSelectionChange = { behavior ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setPlaybackCompletionBehavior(context, behavior)
                    }
                }
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.auto_play_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_PLAY_NEXT),
                title = playbackSettingTitle("playback.auto_play_enabled"),
                subtitle = if (autoContinueControlsEnabled) {
                    "“自动”策略下控制当前视频下一 P；非外部列表播放时，也控制视频合集续播"
                } else {
                    "仅在“播完后操作”设为“自动”时生效"
                },
                checked = autoPlayEnabled,
                enabled = autoContinueControlsEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setAutoPlay(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.external_playlist_auto_continue_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYLIST_AUTO_CONTINUE),
                title = playbackSettingTitle("playback.external_playlist_auto_continue_enabled"),
                subtitle = if (autoContinueControlsEnabled) {
                    "“自动”策略下，当前视频最后一 P 播完后，控制稍后再看、收藏夹等队列是否播放下一视频"
                } else {
                    "仅在“播完后操作”设为“自动”时生效"
                },
                checked = externalPlaylistAutoContinueEnabled,
                enabled = autoContinueControlsEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setExternalPlaylistAutoContinue(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        if (subtitleFeatureEnabled) {
            AppPreferenceDivider()
            SettingsItemAnchor("playback.subtitle_appearance") {
                com.android.purebilibili.core.ui.components.AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SUBTITLE),
                    title = settingItemTitle("playback.subtitle_appearance"),
                    subtitle = "字号、颜色、描边与独立字体导入",
                    onClick = { showSubtitleAppearance = true },
                )
            }
            AppPreferenceDivider()
SettingsItemAnchor("playback.subtitle_auto_preference") {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SUBTITLE_TRACK),
                title = settingItemTitle("playback.subtitle_auto_preference"),
                subtitle = subtitlePreferenceDescription,
                options = listOf(
                    AppSegmentOption(SubtitleAutoPreference.OFF, "关闭"),
                    AppSegmentOption(SubtitleAutoPreference.ON, "开启"),
                    AppSegmentOption(SubtitleAutoPreference.WITHOUT_AI, "非 AI"),
                    AppSegmentOption(SubtitleAutoPreference.AUTO, "自动"),
                    AppSegmentOption(SubtitleAutoPreference.CHINESE_ONLY, "仅中文")
                ),
                selectedValue = subtitleAutoPreference,
                onSelectionChange = { preference ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setSubtitleAutoPreference(context, preference)
                    }
                }
            )
}
            AppPreferenceDivider()
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.auto_skip_op_ed") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_SKIP_OP_ED),
                title = playbackSettingTitle("playback.auto_skip_op_ed"),
                subtitle = if (state.autoSkipOpEd) {
                    "番剧提供跳过区间时，播放中自动跳过片头和片尾"
                } else {
                    "保留完整片头片尾播放"
                },
                checked = state.autoSkipOpEd,
                onCheckedChange = {
                    viewModel.toggleAutoSkipOpEd(it)
                    com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange(
                        "auto_skip_op_ed",
                        it.toString()
                    )
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        } else {
        SettingsItemAnchor("playback.danmaku_tap_interaction_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.DANMAKU_TAP),
                title = playbackSettingTitle("playback.danmaku_tap_interaction_enabled"),
                subtitle = "点击弹幕可悬停并打开点赞、复制等操作；关闭不影响弹幕显示",
                checked = danmakuTapInteractionEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setDanmakuTapInteractionEnabled(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.hide_interactive_command_danmaku") {
    AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTIVE_COMMANDS),
                title = playbackSettingTitle("playback.hide_interactive_command_danmaku"),
                subtitle = if (hideInteractiveCommandDanmaku) {
                    "隐藏画面内的关注、三连、UP 提示和投票"
                } else {
                    "互动提示照常显示在画面中"
                },
                checked = hideInteractiveCommandDanmaku,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setDanmakuHideInteractiveCommands(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
}
        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_info_default_expanded") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_DESCRIPTION),
                title = playbackSettingTitle("playback.video_info_default_expanded"),
                subtitle = if (videoInfoDefaultExpanded) {
                    "进入视频页时默认展开标题、简介和标签"
                } else {
                    "进入视频页时默认收起简介，点击标题区域后展开"
                },
                checked = videoInfoDefaultExpanded,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setVideoInfoDefaultExpanded(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_argue_msg_shown") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_CONTENT_NOTICE),
                title = playbackSettingTitle("playback.video_argue_msg_shown"),
                subtitle = if (videoArgueMsgShown) {
                    "显示视频警告、争议信息及禁止转载声明"
                } else {
                    "视频页不显示警告与争议提示"
                },
                checked = videoArgueMsgShown,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setVideoArgueMsgShown(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()

        SettingsItemAnchor("playback.comment_ip_location_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_IP_LOCATION),
                title = playbackSettingTitle("playback.comment_ip_location_enabled"),
                subtitle = "在评论时间旁显示 IP 归属地，部分评论没有属地数据",
                checked = commentIpLocationEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setCommentIpLocationEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.comment_sex_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_GENDER),
                title = playbackSettingTitle("playback.comment_sex_enabled"),
                subtitle = if (commentSexEnabled) {
                    "在评论和 UP 主页的 IP 归属地后显示；未公开性别的用户不显示"
                } else {
                    "关闭后评论和 UP 主页不显示性别"
                },
                checked = commentSexEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setCommentSexEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.detailed_comment_time_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_EXACT_TIME),
                title = playbackSettingTitle("playback.detailed_comment_time_enabled"),
                subtitle = "显示年月日和时分秒；关闭后显示“多久前”",
                checked = detailedCommentTimeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setDetailedCommentTimeEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.show_video_detail_comment_count") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTION_COMMENT),
                title = playbackSettingTitle("playback.show_video_detail_comment_count"),
                subtitle = if (showVideoDetailCommentCount) {
                    "在视频详情页“评论”标签旁显示视频评论总数"
                } else {
                    "关闭后只显示“评论”"
                },
                checked = showVideoDetailCommentCount,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setShowVideoDetailCommentCount(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        val videoTagSizePreset by remember(context) { com.android.purebilibili.core.store.SettingsManager.getVideoTagSizePreset(context) }
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.ui.components.AppTagChipSize.STANDARD
            )
SettingsItemAnchor("playback.video_tag_size_preset") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_TAG_SIZE),
            title = settingItemTitle("playback.video_tag_size_preset"),
            subtitle = "调整视频简介区标签的字号与间距",
            options = resolveVideoTagSizeSegmentOptions(),
            selectedValue = videoTagSizePreset,
            onSelectionChange = { size ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setVideoTagSizePreset(context, size)
                }
            }
        )
}
        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_detail_bgm_visible") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_DETAIL_BGM),
                title = playbackSettingTitle("playback.video_detail_bgm_visible"),
                subtitle = if (videoDetailBgmVisible) {
                    "视频包含背景音乐时，在详情页显示 BGM 条目与音乐标签"
                } else {
                    "关闭后隐藏详情页的 BGM 条目与音乐标签"
                },
                checked = videoDetailBgmVisible,
                onCheckedChange = { visible ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setVideoDetailBgmVisible(context, visible)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_ai_summary_entry_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.AI_SUMMARY),
                title = playbackSettingTitle("playback.video_ai_summary_entry_enabled"),
                subtitle = if (videoAiSummaryEntryEnabled) {
                    "视频简介区展示 AI 总结按钮，点按后展开内容"
                } else {
                    "关闭后隐藏视频简介区的 AI 总结入口"
                },
                checked = videoAiSummaryEntryEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setVideoAiSummaryEntryEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_note_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_NOTE),
                title = playbackSettingTitle("playback.video_note_enabled"),
                subtitle = if (videoNoteEnabled) {
                    "视频简介区展示笔记入口，并加载私有笔记和公开笔记"
                } else {
                    "隐藏笔记入口，也不加载笔记"
                },
                checked = videoNoteEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setVideoNoteEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }

            AppPreferenceDivider()
            SettingsItemAnchor("playback.video_note_default_collapsed") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_NOTE_COLLAPSE),
                    title = playbackSettingTitle("playback.video_note_default_collapsed"),
                    enabled = videoNoteEnabled,
                    subtitle = if (!videoNoteEnabled) "先开启“显示视频笔记”" else if (videoNoteDefaultCollapsed) {
                        "进入视频页时先显示笔记摘要，需要时再展开"
                    } else {
                        "进入视频页时直接展开视频笔记内容和操作"
                    },
                    checked = videoNoteDefaultCollapsed,
                    onCheckedChange = {
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setVideoNoteDefaultCollapsed(context, it)
                        }
                    },
                    iconTint = AppContentColors.secondaryText()
                )
            }

        AppPreferenceDivider()
        SettingsItemAnchor("playback.double_tap_like") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.LIKE_INTERACTION),
                title = playbackSettingTitle("playback.double_tap_like"),
                subtitle = "双击视频画面快捷点赞；与“双击快进或后退”同时开启时，两侧快进/后退、中部点赞",
                checked = playerConfiguration.doubleTapLikeEnabled,
                onCheckedChange = {
                    viewModel.toggleDoubleTapLike(it)
                    //  [埋点] 设置变更追踪
                    com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange("double_tap_like", it.toString())
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        val favoriteQuickSaveDefaultFolder by com.android.purebilibili.core.store.FavoriteInteractionSettingsStore
            .getQuickSaveDefaultFolder(context)
            .collectAsStateWithLifecycle(initialValue = false)
        SettingsItemAnchor("playback.favorite_quick_save_default_folder") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.FAVORITE_TAP_MODE),
                title = playbackSettingTitle("playback.favorite_quick_save_default_folder"),
                subtitle = if (favoriteQuickSaveDefaultFolder) {
                    "点按直接收藏到默认收藏夹，长按可选择收藏夹"
                } else {
                    "点按打开收藏夹选择"
                },
                checked = favoriteQuickSaveDefaultFolder,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.FavoriteInteractionSettingsStore
                            .setQuickSaveDefaultFolder(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()

SettingsItemAnchor("playback.comment_collapsed_reply_preview_limit") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_REPLY_PREVIEW),
            title = settingItemTitle("playback.comment_collapsed_reply_preview_limit"),
            subtitle = "评论回复收起时，仍显示的条数",
            options = listOf(
                AppSegmentOption(3, "3条"),
                AppSegmentOption(5, "5条"),
                AppSegmentOption(8, "8条"),
                AppSegmentOption(10, "10条")
            ),
            selectedValue = commentCollapsedReplyPreviewLimit,
            onSelectionChange = { limit ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setCommentCollapsedReplyPreviewLimit(context, limit)
                }
            }
        )
}
        AppPreferenceDivider()
        SettingsItemAnchor(SUB_REPLY_STYLE_SETTING_ID) {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SUB_REPLY_THREADED),
                title = stringResource(R.string.sub_reply_style_title),
                subtitle = stringResource(R.string.sub_reply_style_summary),
                options = listOf(
                    AppSegmentOption(true, stringResource(R.string.sub_reply_style_tree)),
                    AppSegmentOption(false, stringResource(R.string.sub_reply_style_classic)),
                ),
                selectedValue = subReplyThreadedEnabled,
                onSelectionChange = { threaded ->
                    scope.launch { SettingsManager.setSubReplyThreadedEnabled(context, threaded) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.sub_reply_loaded_count_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SUB_REPLY_LOADED_COUNT),
                title = playbackSettingTitle("playback.sub_reply_loaded_count_enabled"),
                subtitle = "在回复总数后显示当前已加载的条数",
                checked = subReplyLoadedCountEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setSubReplyLoadedCountEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.comment_fraud_detection_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_VISIBILITY_CHECK),
                title = playbackSettingTitle("playback.comment_fraud_detection_enabled"),
                subtitle = "评论发送成功 8 秒后自动检查可见性",
                checked = commentFraudDetectionEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setCommentFraudDetectionEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.comment_avatar_push_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_AVATAR_PUSH),
                    title = playbackSettingTitle("playback.comment_avatar_push_enabled"),
                    subtitle = "滚动长评论时，作者头像吸顶跟随显示；支持评论、回复与搜索结果",
                    checked = commentAvatarPushEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { SettingsManager.setCommentAvatarPushEnabled(context, enabled) }
                    },
                    iconTint = AppContentColors.secondaryText()
                )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.sub_reply_preview_likes_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SUB_REPLY_PREVIEW_LIKES),
                    title = playbackSettingTitle("playback.sub_reply_preview_likes_enabled"),
                    subtitle = "在楼中楼预览正文末尾显示点赞图标和数量，点击可点赞或取消",
                    checked = subReplyPreviewLikesEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { SettingsManager.setSubReplyPreviewLikesEnabled(context, enabled) }
                    },
                    iconTint = AppContentColors.secondaryText()
                )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.comment_member_decorations_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_DECORATION),
                title = playbackSettingTitle("playback.comment_member_decorations_enabled"),
                subtitle = "显示粉丝牌、铭牌和装扮卡片；关闭后评论区更清爽",
                checked = commentMemberDecorationsEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setCommentMemberDecorationsEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.comment_hide_search_keywords") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_SEARCH_KEYWORDS),
                    title = playbackSettingTitle("playback.comment_hide_search_keywords"),
                    subtitle = if (commentHideSearchKeywords) {
                        "已隐藏评论中的蓝色搜索词与放大镜图标，还原为普通文本"
                    } else {
                        "关闭后显示蓝色搜索词，点击可直接跳转到站内搜索"
                    },
                    checked = commentHideSearchKeywords,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            SettingsManager.setCommentHideSearchKeywords(context, enabled)
                        }
                    },
                    iconTint = AppContentColors.secondaryText()
                )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.image_preview_long_press_save_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.IMAGE_LONG_PRESS_ACTION),
                title = playbackSettingTitle("playback.image_preview_long_press_save_enabled"),
                subtitle = if (imagePreviewLongPressSaveEnabled) {
                    "查看图片时长按可分享、复制链接或保存"
                } else {
                    "关闭后不响应图片长按操作"
                },
                checked = imagePreviewLongPressSaveEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setImagePreviewLongPressSaveEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.image_preview3d_page_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.IMAGE_3D_PAGE),
                title = playbackSettingTitle("playback.image_preview3d_page_enabled"),
                subtitle = if (imagePreview3dPageEnabled) {
                    "滑动图片时，使用立体翻页效果"
                } else {
                    "普通图片浏览使用平面横滑"
                },
                checked = imagePreview3dPageEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setImagePreview3dPageEnabled(context, enabled)
                    }
                },
                titleTrailingContent = {
                    SettingsFeaturePreviewEntry(
                        feature = SettingsFeaturePreview.IMAGE_PAGE,
                        currentEnabled = imagePreview3dPageEnabled,
                    )
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
    }

    }
}

// 搜索先定位懒列表分组，再由组内的设置行处理高亮和精确滚动。
internal enum class PlaybackMiniPlayerGroup(
    val segment: AppPreferenceGroupSegment,
    private vararg val settingIds: String,
) {
    BACKGROUND(
        AppPreferenceGroupSegment.FIRST,
        "playback.stop_playback_on_exit",
        "playback.background_playback_enabled",
        "playback.audio_focus_enabled",
        "playback.mini_player_mode",
    ),
    NOW_PLAYING_BAR(
        AppPreferenceGroupSegment.MIDDLE,
        "playback.audio_now_playing_bar_enabled",
        "playback.audio_now_playing_bar_opens_audio_mode",
        "playback.audio_now_playing_bar_delete_button",
        "playback.audio_now_playing_bar_immersive_enabled",
    ),
    AUDIO(
        AppPreferenceGroupSegment.MIDDLE,
        "playback.loudness_normalization_enabled",
        "playback.sleep_timer_fade_out_enabled",
        "playback.sleep_timer_fade_duration_sec",
        "playback.audio_spectrum_enabled",
        "playback.startup_auto_play_enabled",
        "playback.music_lyrics_ui_style",
        "playback.music_theme_color_mode",
    ),
    PICTURE_IN_PICTURE(
        AppPreferenceGroupSegment.LAST,
        "playback.pip_no_danmaku_enabled",
        "playback.audio_mode_auto_pip_enabled",
    );

    val itemKey: String
        get() = if (this == BACKGROUND) SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER
        else "${SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER}_${name.lowercase()}"

    companion object {
        fun forSetting(settingId: String?): PlaybackMiniPlayerGroup =
            entries.firstOrNull { settingId != null && settingId in it.settingIds } ?: BACKGROUND
    }
}

private fun LazyListScope.miniPlayerPreferenceGroup(
    group: PlaybackMiniPlayerGroup,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = group.itemKey, contentType = "settings_preference_group") {
        Box(modifier = Modifier.entrance()) {
            AppPreferenceGroup(segment = group.segment, content = content)
        }
    }
}

internal enum class PlaybackFullscreenGroup(private vararg val settingIds: String) {
    DOUBLE_TAP(
        "playback.seek_forward_seconds",
        "playback.seek_backward_seconds",
        "playback.double_tap_jump_ahead_enabled",
        "playback.two_finger_chapter_skip_enabled",
    ),
    PORTRAIT(
        "playback.portrait_player_collapse_mode",
        "playback.video_detail_chrome_scroll_hide_enabled",
        "playback.pause_on_player_collapse_enabled",
        "playback.portrait_swipe_to_fullscreen_enabled",
        "playback.direct_portrait_story_entry",
        "playback.portrait_only_vertical_recommendations",
        "playback.launch_to_portrait_feed_on_startup",
        "playback.portrait_swipe_down_minimize_enabled",
    ),
    SWIPE(
        "playback.center_swipe_to_fullscreen_enabled",
        "playback.slide_volume_brightness_enabled",
        "playback.set_system_brightness_enabled",
        "playback.fullscreen_swipe_seek_enabled",
        "playback.swipe_seek_seconds",
        "playback.fullscreen_swipe_up_more_enabled",
        "playback.pinch_zoom_snap_and_badge_enabled",
    ),
    ORIENTATION(
        "playback.auto_rotate_enabled",
        "playback.landscape_hold_enabled",
        "playback.horizontal_adaptation_enabled",
        "playback.landscape_back_shows_comments",
    ),
    CONTROLS(
        "playback.player_control_visibility.show_cast_button",
        "playback.player_control_visibility.show_follow_button",
        "playback.player_control_visibility.compact_player_chrome",
        ContentDisplaySetting.REMEMBER_PLAYER_ADJUSTMENTS,
        ContentDisplaySetting.ROTATION_SENSITIVITY,
        ContentDisplaySetting.PLAYER_CONTROLS_TIMEOUT,
    ),
    LAYOUT(
        "playback.tablet_comment_panel_width_preset",
        "playback.tablet_secondary_default_tab",
        "playback.fullscreen_mode",
        "playback.fullscreen_aspect_ratio",
        "playback.fullscreen_gesture_reverse",
    ),
    AMBIENT_AND_AUTO_FULLSCREEN(
        "playback.video_ambient_settings.enabled",
        "playback.video_ambient_settings.strength",
        "playback.video_ambient_settings.power_saving",
        "playback.portrait_letterbox_ambient_haze",
        "playback.immersive_video_page_status_bar",
        "playback.auto_enter_fullscreen",
        "playback.auto_exit_fullscreen_mode",
    ),
    BUTTONS_AND_SCREENSHOT(
        "playback.show_center_playback_button",
        "playback.show_fullscreen_lock_button",
        "playback.show_fullscreen_screenshot_button",
        "playback.app_gesture_screenshot_enabled",
        "playback.app_screenshot_gesture_mode",
        "playback.app_screenshot_capture_mode",
        "playback.app_screenshot_watermark_enabled",
        "playback.global_screenshot_share_overlay_enabled",
    ),
    PLAYBACK_INFO(
        "playback.show_online_count",
        "playback.show_fullscreen_battery_level",
        "playback.show_fullscreen_time",
        "playback.fullscreen_action_counts",
        "playback.show_fullscreen_action_items",
        "playback.end_screen_next_countdown_enabled",
    ),
    PROGRESS(
        "playback.chapter_display_mode",
        "playback.bottom_progress_behavior",
        "playback.progress_peak_danmaku_enabled",
        "playback.progress_fine_scrubbing_enabled",
        "playback.progress_snap_and_origin_cancel_enabled",
    );

    val itemKey: String
        get() = if (this == DOUBLE_TAP) SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
        else "${SettingsSearchFocusIds.PLAYBACK_FULLSCREEN}_${name.lowercase()}"

    companion object {
        fun forSetting(settingId: String?): PlaybackFullscreenGroup =
            entries.firstOrNull { settingId != null && settingId in it.settingIds } ?: DOUBLE_TAP
    }
}

private class PlaybackSystemBrightnessControl(
    val canWriteSystemSettings: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

@Composable
private fun rememberPlaybackSystemBrightnessControl(
    context: Context,
    scope: CoroutineScope,
    setSystemBrightnessEnabled: Boolean,
): PlaybackSystemBrightnessControl {
    val lifecycleOwner = LocalLifecycleOwner.current
    var canWriteSystemSettings by remember(context) {
        mutableStateOf(Settings.System.canWrite(context))
    }
    var showSystemBrightnessPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var awaitingSystemBrightnessPermission by rememberSaveable { mutableStateOf(false) }
    fun persistSystemBrightnessSetting(enabled: Boolean) {
        scope.launch {
            com.android.purebilibili.core.store.SettingsManager
                .setSetSystemBrightnessEnabled(context, enabled)
        }
    }
    fun refreshSystemBrightnessPermission(resolvePendingRequest: Boolean) {
        val granted = Settings.System.canWrite(context)
        canWriteSystemSettings = granted
        when {
            resolvePendingRequest -> persistSystemBrightnessSetting(granted)
            setSystemBrightnessEnabled && !granted -> persistSystemBrightnessSetting(false)
        }
    }
    val systemBrightnessPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshSystemBrightnessPermission(resolvePendingRequest = awaitingSystemBrightnessPermission)
        awaitingSystemBrightnessPermission = false
    }
    LaunchedEffect(setSystemBrightnessEnabled, canWriteSystemSettings) {
        val normalizedSetting = normalizeSystemBrightnessSetting(
            storedEnabled = setSystemBrightnessEnabled,
            canWriteSystemSettings = canWriteSystemSettings
        )
        if (normalizedSetting != setSystemBrightnessEnabled) {
            com.android.purebilibili.core.store.SettingsManager
                .setSetSystemBrightnessEnabled(context, normalizedSetting)
        }
    }
    DisposableEffect(
        lifecycleOwner,
        context,
        setSystemBrightnessEnabled,
        awaitingSystemBrightnessPermission
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val resolvePendingRequest = awaitingSystemBrightnessPermission
                refreshSystemBrightnessPermission(resolvePendingRequest)
                if (resolvePendingRequest) {
                    awaitingSystemBrightnessPermission = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    if (showSystemBrightnessPermissionDialog) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showSystemBrightnessPermissionDialog = false },
            title = {
                AppText(
                    "允许调节系统亮度",
                    color = AppContentColors.primaryText()
                )
            },
            text = {
                AppText(
                    "开启后，播放器亮度手势会先调节当前窗口亮度，并同步系统亮度。Android 需要你在系统设置中单独允许 BiliPai 修改系统设置。",
                    color = AppContentColors.secondaryText()
                )
            },
            confirmButton = {
                com.android.purebilibili.core.ui.AppDialogAction(
                    onClick = {
                        showSystemBrightnessPermissionDialog = false
                        awaitingSystemBrightnessPermission = true
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        runCatching {
                            systemBrightnessPermissionLauncher.launch(intent)
                        }.onFailure {
                            awaitingSystemBrightnessPermission = false
                            persistSystemBrightnessSetting(false)
                        }
                    }
                ) { AppText("去授权") }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.AppDialogAction(
                    onClick = { showSystemBrightnessPermissionDialog = false }
                ) {
                    AppText(
                        "取消",
                        color = AppContentColors.secondaryText()
                    )
                }
            }
        )
    }

    return PlaybackSystemBrightnessControl(canWriteSystemSettings) { requestedEnabled ->
        when (
            resolveSystemBrightnessToggleAction(
                requestedEnabled = requestedEnabled,
                canWriteSystemSettings = Settings.System.canWrite(context)
            )
        ) {
            SystemBrightnessToggleAction.ENABLE -> {
                canWriteSystemSettings = true
                persistSystemBrightnessSetting(true)
            }
            SystemBrightnessToggleAction.DISABLE -> {
                persistSystemBrightnessSetting(false)
            }
            SystemBrightnessToggleAction.REQUEST_PERMISSION -> {
                canWriteSystemSettings = false
                showSystemBrightnessPermissionDialog = true
            }
        }
    }
}

private fun LazyListScope.fullscreenPreferenceGroup(
    group: PlaybackFullscreenGroup,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = group.itemKey, contentType = "fullscreen_preference_group") {
        val segment = when (group) {
            PlaybackFullscreenGroup.DOUBLE_TAP -> AppPreferenceGroupSegment.FIRST
            PlaybackFullscreenGroup.PROGRESS -> AppPreferenceGroupSegment.LAST
            else -> AppPreferenceGroupSegment.MIDDLE
        }
        AppPreferenceGroup(segment = segment, content = content)
    }
}

private fun LazyListScope.playbackFullscreenGestureSettings(
    context: Context,
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    brightness: PlaybackSystemBrightnessControl,
) {
    fullscreenPreferenceGroup(PlaybackFullscreenGroup.DOUBLE_TAP) {
        val playerConfiguration = rememberSettingsPlayerConfiguration()
        val doubleTapSeekEnabled = playerConfiguration.doubleTapSeekEnabled
        val seekForwardSeconds = playerConfiguration.seekForwardSeconds
        val seekBackwardSeconds = playerConfiguration.seekBackwardSeconds

        AppSliderDialogPreference(
            title = "手势灵敏度",
            subtitle = "调整快进、音量和亮度手势的响应速度",
            value = playerConfiguration.gestureSensitivity,
            onValueChange = viewModel::setGestureSensitivity,
            valueRange = 0.5f..2.0f,
            steps = 5,
            icon = rememberSettingsVector(R.drawable.ph_scribble_loop_24, R.drawable.ms_gesture_24),
            iconTint = AppContentColors.warning(),
            valueFormatter = { value -> "${(value * 100).toInt()}%" },
        )
        AppPreferenceDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "双击快进或后退",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppContentColors.primaryText()
                    )
                    AppText(
                        text = if (doubleTapSeekEnabled) {
                            "双击右侧快进 ${seekForwardSeconds} 秒，双击左侧后退 ${seekBackwardSeconds} 秒"
                        } else {
                            "双击只切换播放或暂停"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                }
                AppAdaptiveSwitch(
                    checked = doubleTapSeekEnabled,
                    onCheckedChange = {
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setDoubleTapSeekEnabled(context, it)
                        }
                    }
                )
            }
            if (doubleTapSeekEnabled) {
                val doubleTapSeekOptions = listOf(
                    AppSegmentOption(5, "5秒"),
                    AppSegmentOption(10, "10秒"),
                    AppSegmentOption(15, "15秒"),
                    AppSegmentOption(30, "30秒"),
                    AppSegmentOption(60, "60秒")
                )
                AppPreferenceDivider()
SettingsItemAnchor("playback.seek_forward_seconds") {
                SettingsSingleChoicePreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SEEK_FORWARD),
                    title = settingItemTitle("playback.seek_forward_seconds"),
                    subtitle = "调整右侧双击快进幅度",
                    options = doubleTapSeekOptions,
                    selectedValue = seekForwardSeconds,
                    onSelectionChange = { seconds ->
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setSeekForwardSeconds(context, seconds)
                        }
                    }
                )
}
                AppPreferenceDivider()
SettingsItemAnchor("playback.seek_backward_seconds") {
                SettingsSingleChoicePreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SEEK_BACKWARD),
                    title = settingItemTitle("playback.seek_backward_seconds"),
                    subtitle = "调整左侧双击后退幅度",
                    options = doubleTapSeekOptions,
                    selectedValue = seekBackwardSeconds,
                    onSelectionChange = { seconds ->
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setSeekBackwardSeconds(context, seconds)
                        }
                    }
                )
}
                val doubleTapJumpAheadEnabled by remember(context) {
                    SettingsManager.getDoubleTapJumpAheadEnabled(context)
                }.collectAsStateWithLifecycle(initialValue = true)
                AppPreferenceDivider()
                SettingsItemAnchor("playback.double_tap_jump_ahead_enabled") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.DOUBLE_TAP_JUMP_AHEAD),
                        title = playbackSettingTitle("playback.double_tap_jump_ahead_enabled"),
                        subtitle = if (doubleTapJumpAheadEnabled) {
                            "双击快进时若前方有高能峰值或下一章节，浮出快捷空降按钮"
                        } else {
                            "关闭后双击快进不再弹出高能空降建议"
                        },
                        checked = doubleTapJumpAheadEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                SettingsManager.setDoubleTapJumpAheadEnabled(context, enabled)
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
            }
            val twoFingerChapterSkipEnabled by remember(context) {
                SettingsManager.getTwoFingerChapterSkipEnabled(context)
            }.collectAsStateWithLifecycle(initialValue = true)
            AppPreferenceDivider()
            SettingsItemAnchor("playback.two_finger_chapter_skip_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.TWO_FINGER_CHAPTER_SKIP),
                    title = playbackSettingTitle("playback.two_finger_chapter_skip_enabled"),
                    subtitle = if (twoFingerChapterSkipEnabled) {
                        "有章节标记时，双指双击右侧跳下一章、左侧回上一章"
                    } else {
                        "关闭后双指双击不再切换章节"
                    },
                    checked = twoFingerChapterSkipEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            SettingsManager.setTwoFingerChapterSkipEnabled(context, enabled)
                        }
                    },
                    iconTint = AppContentColors.secondaryText(),
                )
            }
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.PORTRAIT) {
        val playerConfiguration = rememberSettingsPlayerConfiguration()
        val portraitPlayerCollapseMode by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPortraitPlayerCollapseMode(context) }
        .collectAsStateWithLifecycle(initialValue = PortraitPlayerCollapseMode.INTRO_ONLY)
        val videoDetailChromeScrollHideEnabled by remember(context) { SettingsManager.getVideoDetailChromeScrollHideEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
        val portraitSwipeToFullscreenEnabled = playerConfiguration.portraitSwipeToFullscreenEnabled
        val directPortraitStoryEntry = playerConfiguration.directPortraitStoryEntry
        val portraitOnlyVerticalRecommendations by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPortraitOnlyVerticalRecommendations(context) }
        .collectAsStateWithLifecycle(initialValue = false)
        val launchToPortraitFeedOnStartup = playerConfiguration.launchToPortraitFeedOnStartup

        AppPreferenceDivider()
SettingsItemAnchor("playback.portrait_player_collapse_mode") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_COLLAPSE_MODE),
            title = settingItemTitle("playback.portrait_player_collapse_mode"),
            subtitle = portraitPlayerCollapseMode.description,
            options = resolvePortraitPlayerCollapseModeSegmentOptions(),
            selectedValue = portraitPlayerCollapseMode,
            onSelectionChange = { mode ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setPortraitPlayerCollapseMode(context, mode)
                }
            }
        )
        PortraitPlayerCollapseLivePreview(mode = portraitPlayerCollapseMode)
}

        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_detail_chrome_scroll_hide_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_HEADER_COLLAPSE),
                title = playbackSettingTitle("playback.video_detail_chrome_scroll_hide_enabled"),
                subtitle = if (videoDetailChromeScrollHideEnabled) {
                    "收起最热／最新排序与搜索入口，回到列表顶部恢复；最热评论标题始终随下滑收起"
                } else {
                    "排序与搜索入口始终显示；最热评论标题仍随下滑收起、回顶出现"
                },
                checked = videoDetailChromeScrollHideEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setVideoDetailChromeScrollHideEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }

        val pauseOnPlayerCollapseEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPauseOnPlayerCollapseEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = true)
        AppPreferenceDivider()
        SettingsItemAnchor("playback.pause_on_player_collapse_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_COLLAPSE_PAUSE),
                title = playbackSettingTitle("playback.pause_on_player_collapse_enabled"),
                subtitle = if (pauseOnPlayerCollapseEnabled) {
                    "浏览推荐并缩小画面时暂停，展开后恢复"
                } else {
                    "关闭后缩小播放器时仍继续播放"
                },
                checked = pauseOnPlayerCollapseEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setPauseOnPlayerCollapseEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }

        AppPreferenceDivider()
            SettingsItemAnchor("playback.portrait_swipe_to_fullscreen_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_SWIPE_FULLSCREEN),
                title = playbackSettingTitle("playback.portrait_swipe_to_fullscreen_enabled"),
                subtitle = if (portraitSwipeToFullscreenEnabled) {
                    "开启后在竖屏下向上滑动可快速进入全屏"
                } else {
                    "关闭后竖屏上滑不再触发进入全屏"
                },
                checked = portraitSwipeToFullscreenEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setPortraitSwipeToFullscreenEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }

        AppPreferenceDivider()
        SettingsItemAnchor("playback.direct_portrait_story_entry") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_STORY_ENTRY),
                title = playbackSettingTitle("playback.direct_portrait_story_entry"),
                subtitle = if (directPortraitStoryEntry) {
                    "点开竖屏视频即全屏播放，可上下滑动切换"
                } else {
                    "先进入视频页，再点“竖屏”进入刷视频"
                },
                checked = directPortraitStoryEntry,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setAutoPortraitFullscreen(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }

        AppPreferenceDivider()
        SettingsItemAnchor("playback.portrait_only_vertical_recommendations") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VERTICAL_RECOMMENDATIONS),
                title = playbackSettingTitle("playback.portrait_only_vertical_recommendations"),
                subtitle = if (portraitOnlyVerticalRecommendations) {
                    "仅推荐竖屏画面的视频"
                } else {
                    "同时推荐横屏和竖屏视频"
                },
                checked = portraitOnlyVerticalRecommendations,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setPortraitOnlyVerticalRecommendations(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }

        AppPreferenceDivider()
        SettingsItemAnchor("playback.launch_to_portrait_feed_on_startup") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.STARTUP_PORTRAIT_FEED),
                title = playbackSettingTitle("playback.launch_to_portrait_feed_on_startup"),
                subtitle = if (launchToPortraitFeedOnStartup) {
                    "打开应用即进入刷视频，不受上方开关影响"
                } else {
                    "关闭后仍从首页进入应用"
                },
                checked = launchToPortraitFeedOnStartup,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setLaunchToPortraitFeedOnStartup(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        val portraitSwipeDownMinimizeEnabled by remember(context) {
            SettingsManager.getPortraitSwipeDownMinimizeEnabled(context)
        }.collectAsStateWithLifecycle(initialValue = true)
        AppPreferenceDivider()
        SettingsItemAnchor("playback.portrait_swipe_down_minimize_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_SWIPE_DOWN_MINIMIZE),
                title = playbackSettingTitle("playback.portrait_swipe_down_minimize_enabled"),
                subtitle = if (portraitSwipeDownMinimizeEnabled) {
                    "竖屏播放画面中部下拉收起页面（开启手势反向时自动让位）"
                } else {
                    "关闭后竖屏播放器中部下拉不再收起页面"
                },
                checked = portraitSwipeDownMinimizeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setPortraitSwipeDownMinimizeEnabled(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }

    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.SWIPE) {
        val playerConfiguration = rememberSettingsPlayerConfiguration()
        val centerSwipeToFullscreenEnabled = playerConfiguration.centerSwipeToFullscreenEnabled
        val slideVolumeBrightnessEnabled = playerConfiguration.slideVolumeBrightnessEnabled
        val setSystemBrightnessEnabled = playerConfiguration.setSystemBrightnessEnabled
        val fullscreenSwipeSeekEnabled = playerConfiguration.fullscreenSwipeSeekEnabled

        AppPreferenceDivider()
            SettingsItemAnchor("playback.center_swipe_to_fullscreen_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.CENTER_SWIPE_FULLSCREEN),
                title = playbackSettingTitle("playback.center_swipe_to_fullscreen_enabled"),
                subtitle = if (centerSwipeToFullscreenEnabled) {
                    "在画面中部上下滑动，进入或退出全屏"
                } else {
                    "关闭后：中部纵向滑动不再触发全屏切换"
                },
                checked = centerSwipeToFullscreenEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setCenterSwipeToFullscreenEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }

        AppPreferenceDivider()
            SettingsItemAnchor("playback.slide_volume_brightness_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SLIDE_VOLUME_BRIGHTNESS),
                title = playbackSettingTitle("playback.slide_volume_brightness_enabled"),
                subtitle = if (slideVolumeBrightnessEnabled) {
                    "左侧上下滑调亮度，右侧上下滑调音量"
                } else {
                    "关闭后仅保留中部全屏手势和左右拖动进度"
                },
                checked = slideVolumeBrightnessEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setSlideVolumeBrightnessEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.set_system_brightness_enabled") {
                AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SYSTEM_BRIGHTNESS),
                title = playbackSettingTitle("playback.set_system_brightness_enabled"),
                subtitle = when {
                    !slideVolumeBrightnessEnabled -> "依赖“滑动调亮度和音量”开关"
                    setSystemBrightnessEnabled && brightness.canWriteSystemSettings ->
                        "亮度手势会同时修改当前画面和设备系统亮度"
                    else -> "关闭时只临时调整当前播放画面；开启需要系统授权"
                },
                checked = setSystemBrightnessEnabled && brightness.canWriteSystemSettings,
                enabled = slideVolumeBrightnessEnabled,
                onCheckedChange = { requestedEnabled ->
                    if (!slideVolumeBrightnessEnabled) return@AppSwitchPreference
                    brightness.onCheckedChange(requestedEnabled)
                },
                iconTint = AppContentColors.secondaryText()
            )
            }

        AppPreferenceDivider()
        SettingsItemAnchor("playback.fullscreen_swipe_seek_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SWIPE_SEEK),
                title = playbackSettingTitle("playback.fullscreen_swipe_seek_enabled"),
                subtitle = if (fullscreenSwipeSeekEnabled) {
                    "滑动一屏跳转 ${playerConfiguration.swipeSeekSeconds} 秒，不随视频时长变化"
                } else {
                    "滑动幅度随视频总时长变化"
                },
                checked = fullscreenSwipeSeekEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setFullscreenSwipeSeekEnabled(context, it)
                    }
                },
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.swipe_seek_seconds") {
            SettingsSingleChoicePreference(
                title = playbackSettingTitle("playback.swipe_seek_seconds"),
                subtitle = "固定模式下滑动一屏的跳转秒数，实际幅度同时受手势灵敏度影响",
                enabled = fullscreenSwipeSeekEnabled,
                options = listOf(5, 10, 15, 30, 60, 90, 120, 300).map { AppSegmentOption(it, "${it}秒") },
                selectedValue = playerConfiguration.swipeSeekSeconds,
                onSelectionChange = { seconds ->
                    scope.launch { SettingsManager.setSwipeSeekSeconds(context, seconds) }
                },
            )
        }
        val fullscreenSwipeUpMoreEnabled by remember(context) {
            SettingsManager.getFullscreenSwipeUpMoreEnabled(context)
        }.collectAsStateWithLifecycle(initialValue = true)
        AppPreferenceDivider()
        SettingsItemAnchor("playback.fullscreen_swipe_up_more_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_SWIPE_UP_MORE),
                title = playbackSettingTitle("playback.fullscreen_swipe_up_more_enabled"),
                subtitle = if (fullscreenSwipeUpMoreEnabled) {
                    "横屏全屏底部上滑可呼出横向推荐视频抽屉"
                } else {
                    "关闭后全屏底部上滑不再呼出推荐抽屉"
                },
                checked = fullscreenSwipeUpMoreEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setFullscreenSwipeUpMoreEnabled(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        val pinchZoomSnapAndBadgeEnabled by remember(context) {
            SettingsManager.getPinchZoomSnapAndBadgeEnabled(context)
        }.collectAsStateWithLifecycle(initialValue = true)
        AppPreferenceDivider()
        SettingsItemAnchor("playback.pinch_zoom_snap_and_badge_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PINCH_ZOOM_SNAP_BADGE),
                title = playbackSettingTitle("playback.pinch_zoom_snap_and_badge_enabled"),
                subtitle = if (pinchZoomSnapAndBadgeEnabled) {
                    "双指接近铺满倍率时自动吸附并提示，还原按钮显示实时放大倍数"
                } else {
                    "关闭后保持原有纯自由缩放与还原按钮"
                },
                checked = pinchZoomSnapAndBadgeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setPinchZoomSnapAndBadgeEnabled(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.ORIENTATION) {
        val autoRotateEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getAutoRotateEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val landscapeHoldEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getLandscapeHoldEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val windowSizeClass = LocalWindowSizeClass.current
        val displayContext = LocalAppWindowAdaptiveInfo.current.displayContext
        val isLargeScreenDevice = windowSizeClass.isTabletDevice ||
            displayContext.isKnownFoldableDevice
        val horizontalAdaptationEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getHorizontalAdaptationEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = isLargeScreenDevice)
        val landscapeBackShowsComments by remember(context) { SettingsManager.getLandscapeBackShowsComments(context) }
            .collectAsStateWithLifecycle(initialValue = false)
        val autoRotateSubtitle = if (isLargeScreenDevice) {
            "跟随设备方向旋转播放页，并保留平板/展开态折叠屏分栏布局"
        } else {
            "跟随设备方向自动进入/退出全屏，不受系统旋转锁影响"
        }
        val horizontalAdaptationSubtitle = if (isLargeScreenDevice) {
            "横屏时使用大屏布局，适合平板和折叠屏"
        } else {
            "主要适用于平板和折叠屏"
        }

        AppPreferenceDivider()
            SettingsItemAnchor("playback.auto_rotate_enabled") {
                AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_ORIENTATION),
                title = playbackSettingTitle("playback.auto_rotate_enabled"),
                subtitle = autoRotateSubtitle,
                checked = autoRotateEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setAutoRotateEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.landscape_hold_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.LANDSCAPE_HOLD),
                    title = playbackSettingTitle("playback.landscape_hold_enabled"),
                    subtitle = "躺卧场景：自动进入横屏后不再因倾斜回正退出全屏，需手动退出",
                    checked = landscapeHoldEnabled,
                    enabled = autoRotateEnabled,
                    onCheckedChange = {
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setLandscapeHoldEnabled(context, it)
                        }
                    },
                )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.horizontal_adaptation_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.HORIZONTAL_ADAPTATION),
                title = playbackSettingTitle("playback.horizontal_adaptation_enabled"),
                subtitle = horizontalAdaptationSubtitle,
                checked = horizontalAdaptationEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setHorizontalAdaptationEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.landscape_back_shows_comments") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.LANDSCAPE_BACK_COMMENTS),
                title = playbackSettingTitle("playback.landscape_back_shows_comments"),
                subtitle = "横屏全屏时首次返回打开评论，再次返回上一级并保持横屏",
                checked = landscapeBackShowsComments,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setLandscapeBackShowsComments(context, enabled) }
                },
            )
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.CONTROLS) {
        val playerControlVisibility by remember(context) { SettingsManager.getPlayerControlVisibilitySettings(context) }
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.PlayerControlVisibilitySettings()
            )
        val autoRotateEnabledForSensitivity by remember(context) { SettingsManager.getAutoRotateEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = false)

        AppPreferenceDivider()
        SettingsItemAnchor("playback.player_control_visibility.show_cast_button") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.CAST_BUTTON),
                title = playbackSettingTitle("playback.player_control_visibility.show_cast_button"),
                subtitle = "同时控制半屏、横屏全屏和竖屏全屏的投屏入口",
                checked = playerControlVisibility.showCastButton,
                onCheckedChange = {
                    scope.launch {
                        SettingsManager.setShowPlayerCastButton(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        com.android.purebilibili.feature.settings.PlayerAdjustmentPersistencePreference()
        AppPreferenceDivider()
        com.android.purebilibili.feature.settings.PlayerRotationSensitivityPreference(
            enabled = autoRotateEnabledForSensitivity,
        )
        AppPreferenceDivider()
        com.android.purebilibili.feature.settings.PlayerControlsTimeoutPreference()
        AppPreferenceDivider()
        SettingsItemAnchor("playback.player_control_visibility.show_follow_button") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.FOLLOW_BUTTON),
                title = playbackSettingTitle("playback.player_control_visibility.show_follow_button"),
                subtitle = "关闭后保留 UP 主头像、名称和主页入口",
                checked = playerControlVisibility.showFollowButton,
                onCheckedChange = {
                    scope.launch {
                        SettingsManager.setShowVideoFollowButton(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.player_control_visibility.compact_player_chrome") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.COMPACT_PLAYER_CONTROLS),
                title = playbackSettingTitle("playback.player_control_visibility.compact_player_chrome"),
                subtitle = "减少按钮间距和遮罩；分享移至“更多”",
                checked = playerControlVisibility.compactPlayerChrome,
                onCheckedChange = {
                    scope.launch {
                        SettingsManager.setCompactPlayerChrome(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.LAYOUT) {
        val playerConfiguration = rememberSettingsPlayerConfiguration()
        val autoRotateEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getAutoRotateEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val fullscreenGestureReverse = playerConfiguration.fullscreenGestureReverse
        val windowSizeClass = LocalWindowSizeClass.current
        val displayContext = LocalAppWindowAdaptiveInfo.current.displayContext
        val isLargeScreenDevice = windowSizeClass.isTabletDevice ||
            displayContext.isKnownFoldableDevice
        val horizontalAdaptationEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getHorizontalAdaptationEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = isLargeScreenDevice)
        val tabletCommentPanelWidthPreset = playerConfiguration.tabletCommentPanelWidthPreset
        val tabletSecondaryDefaultTab by remember(context) { com.android.purebilibili.core.store.SettingsManager.getTabletSecondaryDefaultTab(context) }
            .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.TabletSecondaryDefaultTab.RELATED)
        val fullscreenMode by remember(context) { com.android.purebilibili.core.store.SettingsManager.getFullscreenMode(context) }
            .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.FullscreenMode.AUTO)
        val fullscreenAspectRatio = playerConfiguration.fixedFullscreenAspectRatio
        val fullscreenModeSubtitle = when {
            !autoRotateEnabled -> fullscreenMode.description
            isLargeScreenDevice ->
                "${fullscreenMode.description}；自动横竖屏仅旋转播放页，手动全屏时使用此方向"
            else ->
                "${fullscreenMode.description}；已开启自动横竖屏，将跟随设备方向自动进退全屏"
        }

        AppPreferenceDivider()
SettingsItemAnchor("playback.tablet_comment_panel_width_preset") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.TABLET_COMMENT_PANEL_WIDTH),
            title = settingItemTitle("playback.tablet_comment_panel_width_preset"),
            subtitle = if (horizontalAdaptationEnabled) {
                "调整横屏适配下右侧评论/推荐栏宽度"
            } else {
                "开启横屏适配后生效"
            },
            options = resolveTabletCommentPanelWidthSegmentOptions(),
            selectedValue = tabletCommentPanelWidthPreset,
            enabled = horizontalAdaptationEnabled,
            onSelectionChange = { preset ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setTabletCommentPanelWidthPreset(context, preset)
                }
            }
        )
}
        AppPreferenceDivider()
SettingsItemAnchor("playback.tablet_secondary_default_tab") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.TABLET_SECONDARY_TAB),
            title = settingItemTitle("playback.tablet_secondary_default_tab"),
            subtitle = if (horizontalAdaptationEnabled) {
                "选择首次打开时，右栏显示推荐还是评论"
            } else {
                "开启横屏适配后生效"
            },
            options = resolveTabletSecondaryDefaultTabOptions(),
            selectedValue = tabletSecondaryDefaultTab,
            enabled = horizontalAdaptationEnabled,
            onSelectionChange = { tab ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setTabletSecondaryDefaultTab(context, tab)
                }
            }
        )
}
        AppPreferenceDivider()
SettingsItemAnchor("playback.fullscreen_mode") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_ORIENTATION),
            title = settingItemTitle("playback.fullscreen_mode"),
            subtitle = fullscreenModeSubtitle,
            options = resolveFullscreenModeSegmentOptions(),
            selectedValue = fullscreenMode,
            onSelectionChange = { mode ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setFullscreenMode(context, mode)
                }
            }
        )
}
        AppPreferenceDivider()
SettingsItemAnchor("playback.fullscreen_aspect_ratio") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.HORIZONTAL_ADAPTATION),
            title = settingItemTitle("playback.fullscreen_aspect_ratio"),
            subtitle = fullscreenAspectRatio.description,
            options = resolveFullscreenAspectRatioSegmentOptions(),
            selectedValue = fullscreenAspectRatio,
            onSelectionChange = { ratio ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setFullscreenAspectRatio(context, ratio)
                }
            }
        )
}
        AppPreferenceDivider()
            SettingsItemAnchor("playback.fullscreen_gesture_reverse") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_GESTURE_REVERSE),
                title = playbackSettingTitle("playback.fullscreen_gesture_reverse"),
                subtitle = "默认上滑进全屏、下滑退全屏；开启后方向反转",
                checked = fullscreenGestureReverse,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setFullscreenGestureReverse(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.AMBIENT_AND_AUTO_FULLSCREEN) {
        val playerConfiguration = rememberSettingsPlayerConfiguration()
        val autoEnterFullscreen = playerConfiguration.autoEnterFullscreenEnabled
        val videoAmbientSettings by remember(context) { SettingsManager.getVideoAmbientSettings(context) }
            .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.feature.video.ambient.AmbientSettings())
        val immersiveVideoPageStatusBar by remember(context) { com.android.purebilibili.core.store.SettingsManager.getHideVideoPageStatusBar(context) }
            .collectAsStateWithLifecycle(initialValue = false)

        AppPreferenceDivider()
        SettingsItemAnchor("playback.video_ambient_settings.enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_AMBIENT_LIGHT),
                title = playbackSettingTitle("playback.video_ambient_settings.enabled"),
                subtitle = "在横竖屏播放器留白处显示随画面变化的柔和光晕；HDR、Anime4K 和小窗下不启用",
                checked = videoAmbientSettings.enabled,
                onCheckedChange = { enabled -> scope.launch { SettingsManager.setVideoAmbientEnabled(context, enabled) } },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        val animateAmbientOptions = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
            !com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()
        androidx.compose.animation.AnimatedVisibility(
            visible = videoAmbientSettings.enabled,
            enter = if (animateAmbientOptions) androidx.compose.animation.expandVertically(
                animationSpec = com.android.purebilibili.core.ui.motion.continuityTween(180),
            ) else androidx.compose.animation.EnterTransition.None,
            exit = if (animateAmbientOptions) androidx.compose.animation.shrinkVertically(
                animationSpec = com.android.purebilibili.core.ui.motion.continuityTween(140),
            ) else androidx.compose.animation.ExitTransition.None,
        ) {
            Column {
            AppPreferenceDivider()
SettingsItemAnchor("playback.video_ambient_settings.strength") {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.AMBIENT_STRENGTH),
                title = settingItemTitle("playback.video_ambient_settings.strength"),
                subtitle = "共用横屏和竖屏留白的光晕亮度，不改变视频画面",
                options = listOf(
                    com.android.purebilibili.core.ui.components.AppSegmentOption(0, "柔和"),
                    com.android.purebilibili.core.ui.components.AppSegmentOption(1, "标准"),
                    com.android.purebilibili.core.ui.components.AppSegmentOption(2, "强烈"),
                ),
                selectedValue = videoAmbientSettings.strength,
                onSelectionChange = { value -> scope.launch { SettingsManager.setVideoAmbientStrength(context, value) } },
            )
}
            AppPreferenceDivider()
SettingsItemAnchor("playback.video_ambient_settings.power_saving") {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.AMBIENT_POWER_SAVING),
                title = settingItemTitle("playback.video_ambient_settings.power_saving"),
                subtitle = "自动调节流畅度；省电模式减少刷新",
                options = listOf(
                    com.android.purebilibili.core.ui.components.AppSegmentOption(false, "自动"),
                    com.android.purebilibili.core.ui.components.AppSegmentOption(true, "省电"),
                ),
                selectedValue = videoAmbientSettings.powerSaving,
                onSelectionChange = { value -> scope.launch { SettingsManager.setVideoAmbientPowerSaving(context, value) } },
            )
}
                AppPreferenceDivider()
                val portraitLetterboxAmbientHaze = playerConfiguration.portraitLetterboxAmbientHaze
                SettingsItemAnchor("playback.portrait_letterbox_ambient_haze") {
                    AppSwitchPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_AMBIENT_HAZE),
                        title = playbackSettingTitle("playback.portrait_letterbox_ambient_haze"),
                        subtitle = if (portraitLetterboxAmbientHaze) {
                            "竖屏全屏时，上下留白显示光晕，使用上方的环境光强度"
                        } else {
                            "竖屏全屏时，上下留白保持纯黑"
                        },
                        checked = portraitLetterboxAmbientHaze,
                        onCheckedChange = {
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setPortraitLetterboxAmbientHaze(context, it)
                            }
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
        }
        }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.immersive_video_page_status_bar") {
                AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.IMMERSIVE_STATUS_BAR),
                title = playbackSettingTitle("playback.immersive_video_page_status_bar"),
                subtitle = if (immersiveVideoPageStatusBar) {
                    "状态栏背景随画面模糊，保留系统图标和手势条"
                } else {
                    "状态栏使用黑色背景，保留系统图标和手势条"
                },
                checked = immersiveVideoPageStatusBar,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setHideVideoPageStatusBar(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.auto_enter_fullscreen") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_ENTER_FULLSCREEN),
                title = playbackSettingTitle("playback.auto_enter_fullscreen"),
                subtitle = "视频开始播放后自动切到全屏",
                checked = autoEnterFullscreen,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setAutoEnterFullscreen(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
        val autoExitFullscreenMode = playerConfiguration.autoExitFullscreenMode
            SettingsItemAnchor("playback.auto_exit_fullscreen_mode") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_EXIT_FULLSCREEN),
                title = playbackSettingTitle("playback.auto_exit_fullscreen_mode"),
                subtitle = autoExitFullscreenMode.subtitle,
                checked = autoExitFullscreenMode !=
                    com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF,
                onCheckedChange = { enabled ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setAutoExitFullscreenMode(
                            context,
                            if (enabled) {
                                com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS
                            } else {
                                com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF
                            },
                        )
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        if (
            autoExitFullscreenMode !=
            com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF
        ) {
            AppPreferenceDivider()
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_EXIT_FULLSCREEN),
                title = "退出时机：${autoExitFullscreenMode.label}",
                subtitle = autoExitFullscreenMode.subtitle,
                options = listOf(
                    AppSegmentOption(
                        com.android.purebilibili.core.store.AutoExitFullscreenMode.CURRENT_PART,
                        "当前视频",
                    ),
                    AppSegmentOption(
                        com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS,
                        "全部播完",
                    ),
                ),
                selectedValue = autoExitFullscreenMode,
                onSelectionChange = { mode ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setAutoExitFullscreenMode(context, mode)
                    }
                },
            )
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.BUTTONS_AND_SCREENSHOT) {
        val hideCenterPlayback by remember(context) {
            com.android.purebilibili.core.store.player.PlayerSettingsStore.getHideCenterPlayback(context)
        }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenLockButton by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenLockButton(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenScreenshotButton by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenScreenshotButton(context) }.collectAsStateWithLifecycle(initialValue = true)
        val appGestureScreenshotEnabled by remember(context) { SettingsManager.getAppGestureScreenshotEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val appScreenshotGestureMode by remember(context) { SettingsManager.getAppScreenshotGestureMode(context) }
            .collectAsStateWithLifecycle(initialValue = AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS)
        val appScreenshotCaptureMode by remember(context) { SettingsManager.getAppScreenshotCaptureMode(context) }
            .collectAsStateWithLifecycle(initialValue = AppScreenshotCaptureMode.FULL_WINDOW)
        val appScreenshotWatermarkEnabled by remember(context) {
            SettingsManager.getAppScreenshotWatermarkEnabled(context)
        }.collectAsStateWithLifecycle(initialValue = false)
        val globalScreenshotShareOverlayEnabled by remember(context) {
            SettingsManager.getGlobalScreenshotShareOverlayEnabled(context)
        }.collectAsStateWithLifecycle(initialValue = true)

        AppPreferenceDivider()
        SettingsItemAnchor("playback.show_center_playback_button") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK),
                title = playbackSettingTitle("playback.show_center_playback_button"),
                subtitle = "横竖屏共用，关闭后在左下角显示播放/暂停按钮",
                checked = !hideCenterPlayback,
                onCheckedChange = { visible ->
                    scope.launch {
                        com.android.purebilibili.core.store.player.PlayerSettingsStore
                            .setHideCenterPlayback(context, !visible)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.show_fullscreen_lock_button") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_LOCK),
                title = playbackSettingTitle("playback.show_fullscreen_lock_button"),
                subtitle = "显示锁定按钮，避免误触",
                checked = showFullscreenLockButton,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setShowFullscreenLockButton(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.show_fullscreen_screenshot_button") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_SCREENSHOT),
                title = playbackSettingTitle("playback.show_fullscreen_screenshot_button"),
                subtitle = "显示截图按钮，保存视频画面",
                checked = showFullscreenScreenshotButton,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setShowFullscreenScreenshotButton(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.app_gesture_screenshot_enabled") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.CLEAN_SCREENSHOT),
                title = playbackSettingTitle("playback.app_gesture_screenshot_enabled"),
                subtitle = "用手势保存当前应用画面为 PNG 图片",
                checked = appGestureScreenshotEnabled,
                onCheckedChange = {
                    scope.launch {
                        SettingsManager.setAppGestureScreenshotEnabled(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
SettingsItemAnchor("playback.app_screenshot_gesture_mode") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.SCREENSHOT_GESTURE),
            title = settingItemTitle("playback.app_screenshot_gesture_mode"),
            subtitle = if (appGestureScreenshotEnabled) {
                appScreenshotGestureMode.description
            } else {
                "开启“应用内截图”后生效"
            },
            options = resolveAppScreenshotGestureModeSegmentOptions(),
            selectedValue = appScreenshotGestureMode,
            enabled = appGestureScreenshotEnabled,
            onSelectionChange = { mode ->
                scope.launch {
                    SettingsManager.setAppScreenshotGestureMode(context, mode)
                }
            }
        )
}
        AppPreferenceDivider()
SettingsItemAnchor("playback.app_screenshot_capture_mode") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.APP_SCREENSHOT),
            title = settingItemTitle("playback.app_screenshot_capture_mode"),
            subtitle = if (appGestureScreenshotEnabled) {
                appScreenshotCaptureMode.description
            } else {
                "开启“应用内截图”后生效"
            },
            options = resolveAppScreenshotCaptureModeSegmentOptions(),
            selectedValue = appScreenshotCaptureMode,
            enabled = appGestureScreenshotEnabled,
            onSelectionChange = { mode ->
                scope.launch {
                    SettingsManager.setAppScreenshotCaptureMode(context, mode)
                }
            }
        )
}
        AppPreferenceDivider()
        SettingsItemAnchor("playback.app_screenshot_watermark_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.APP_SCREENSHOT_WATERMARK),
                title = playbackSettingTitle("playback.app_screenshot_watermark_enabled"),
                subtitle = if (appGestureScreenshotEnabled) {
                    "开启后，应用内手势截图或区域截图保存时将在顶部拼接 BiliPai 品牌水印条"
                } else {
                    "开启“应用内截图”后生效"
                },
                checked = appScreenshotWatermarkEnabled,
                enabled = appGestureScreenshotEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setAppScreenshotWatermarkEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.global_screenshot_share_overlay_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.SETTINGS_SHARE),
                title = playbackSettingTitle("playback.global_screenshot_share_overlay_enabled"),
                subtitle = "系统截屏后弹出快捷浮层，一键生成带顶部 BiliPai 水印的分享图（Android 14+）",
                checked = globalScreenshotShareOverlayEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setGlobalScreenshotShareOverlayEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.PLAYBACK_INFO) {
        val showFullscreenBatteryLevel by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenBatteryLevel(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenTime by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenTime(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenActionItems by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenActionItems(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showOnlineCount by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowOnlineCount(context) }.collectAsStateWithLifecycle(initialValue = false)

        AppPreferenceDivider()
        SettingsItemAnchor("playback.show_online_count") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.ONLINE_COUNT),
                title = playbackSettingTitle("playback.show_online_count"),
                subtitle = "在视频详情和播放器显示同时观看人数，不影响卡片",
                checked = showOnlineCount,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setShowOnlineCount(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.show_fullscreen_battery_level") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.BATTERY_STATUS),
                title = playbackSettingTitle("playback.show_fullscreen_battery_level"),
                subtitle = "在横屏左上角展示电池图标和电量百分比",
                checked = showFullscreenBatteryLevel,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setShowFullscreenBatteryLevel(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.show_fullscreen_time") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.TIME_STATUS),
                title = playbackSettingTitle("playback.show_fullscreen_time"),
                subtitle = "在横屏左上角单独展示当前时间",
                checked = showFullscreenTime,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setShowFullscreenTime(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.fullscreen_action_counts") {
                com.android.purebilibili.feature.settings.FullscreenActionCountPreference()
            }
        AppPreferenceDivider()
            SettingsItemAnchor("playback.show_fullscreen_action_items") {
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_ACTIONS),
                title = playbackSettingTitle("playback.show_fullscreen_action_items"),
                subtitle = if (showFullscreenActionItems) {
                    "横屏顶部显示点赞/投币/分享等快捷操作"
                } else {
                    "关闭后隐藏横屏顶部互动按钮，保留返回与更多入口"
                },
                checked = showFullscreenActionItems,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setShowFullscreenActionItems(context, it)
                    }
                },
                iconTint = AppContentColors.secondaryText()
            )
            }
        val endScreenNextCountdownEnabled by remember(context) {
            SettingsManager.getEndScreenNextCountdownEnabled(context)
        }.collectAsStateWithLifecycle(initialValue = true)
        AppPreferenceDivider()
        SettingsItemAnchor("playback.end_screen_next_countdown_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.END_SCREEN_NEXT_COUNTDOWN),
                title = playbackSettingTitle("playback.end_screen_next_countdown_enabled"),
                subtitle = if (endScreenNextCountdownEnabled) {
                    "视频临近结束时在画面右下角浮出即将播放倒计时卡片（与“接下来观看卡片”独立控制）"
                } else {
                    "关闭后片尾不再浮出即将播放倒计时卡片"
                },
                checked = endScreenNextCountdownEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { SettingsManager.setEndScreenNextCountdownEnabled(context, enabled) }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
    }

    fullscreenPreferenceGroup(PlaybackFullscreenGroup.PROGRESS) {
        val chapterDisplayMode by remember(context) { SettingsManager.getChapterDisplayMode(context) }
            .collectAsStateWithLifecycle(initialValue = ChapterDisplayMode.WITH_CONTROLS)
        val bottomProgressBehavior by remember(context) { com.android.purebilibili.core.store.SettingsManager.getBottomProgressBehavior(context) }
            .collectAsStateWithLifecycle(initialValue = BottomProgressBehavior.ALWAYS_HIDE)
        val progressPeakDanmakuEnabled by remember(context) { SettingsManager.getProgressPeakDanmakuEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = false)
        val progressFineScrubbingEnabled by remember(context) { SettingsManager.getProgressFineScrubbingEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = true)
        val progressSnapAndOriginCancelEnabled by remember(context) { SettingsManager.getProgressSnapAndOriginCancelEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = true)

        AppPreferenceDivider()
        SettingsItemAnchor("playback.chapter_display_mode") {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.CHAPTER_DISPLAY),
                title = playbackSettingTitle("playback.chapter_display_mode"),
                subtitle = chapterDisplayMode.description,
                options = ChapterDisplayMode.entries.map { AppSegmentOption(it, it.label) },
                selectedValue = chapterDisplayMode,
                onSelectionChange = { mode ->
                    scope.launch { SettingsManager.setChapterDisplayMode(context, mode) }
                },
            )
        }
        AppPreferenceDivider()
SettingsItemAnchor("playback.bottom_progress_behavior") {
        SettingsSingleChoicePreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.BOTTOM_PROGRESS),
            title = settingItemTitle("playback.bottom_progress_behavior"),
            subtitle = bottomProgressBehavior.description,
            options = listOf(
                AppSegmentOption(BottomProgressBehavior.ALWAYS_SHOW, "始终展示"),
                AppSegmentOption(BottomProgressBehavior.ALWAYS_HIDE, "始终隐藏"),
                AppSegmentOption(BottomProgressBehavior.ONLY_SHOW_FULLSCREEN, "仅全屏展示"),
                AppSegmentOption(BottomProgressBehavior.ONLY_HIDE_FULLSCREEN, "仅全屏隐藏")
            ),
            selectedValue = bottomProgressBehavior,
            onSelectionChange = { behavior ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setBottomProgressBehavior(context, behavior)
                }
            }
        )
}
        AppPreferenceDivider()
        SettingsItemAnchor("playback.progress_peak_danmaku_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESS_PEAK_DANMAKU),
                title = playbackSettingTitle("playback.progress_peak_danmaku_enabled"),
                subtitle = if (progressPeakDanmakuEnabled) {
                    "在进度条上标出弹幕集中的位置"
                } else {
                    "进度条不显示弹幕热度"
                },
                checked = progressPeakDanmakuEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setProgressPeakDanmakuEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.progress_fine_scrubbing_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESS_FINE_SCRUBBING),
                title = playbackSettingTitle("playback.progress_fine_scrubbing_enabled"),
                subtitle = if (progressFineScrubbingEnabled) {
                    "拖动进度条时上滑展开连续缩略图胶片带并进入精细时间轴"
                } else {
                    "关闭后仅保留原有单帧预览气泡"
                },
                checked = progressFineScrubbingEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setProgressFineScrubbingEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor("playback.progress_snap_and_origin_cancel_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESS_SNAP_CANCEL),
                title = playbackSettingTitle("playback.progress_snap_and_origin_cancel_enabled"),
                subtitle = if (progressSnapAndOriginCancelEnabled) {
                    "经过章节或高能峰值时轻振磁吸，拖回起点附近松手取消跳转"
                } else {
                    "关闭后保持原有线性拖动"
                },
                checked = progressSnapAndOriginCancelEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setProgressSnapAndOriginCancelEnabled(context, enabled)
                    }
                },
                iconTint = AppContentColors.secondaryText(),
            )
        }
    }
}

@Composable
private fun PlaybackSectionQuickJumpBar(
    onSelectSection: (String) -> Unit,
) {
    val entries = remember {
        listOf(
            SettingsSearchFocusIds.PLAYBACK_DECODER to "解码",
            SettingsSearchFocusIds.PLAYBACK_SPEED to "倍速",
            SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER to "小窗与听视频",
            SettingsSearchFocusIds.PLAYBACK_NETWORK to "画质与网络",
            SettingsSearchFocusIds.PLAYBACK_DATA_SAVER to "省流量",
            SettingsSearchFocusIds.PLAYBACK_INTERACTION to "字幕与连播",
            SettingsSearchFocusIds.PLAYBACK_COMMENTS to "互动与评论",
            SettingsSearchFocusIds.PLAYBACK_FULLSCREEN to "全屏与手势",
            SettingsSearchFocusIds.PLAYBACK_DEBUG to "诊断",
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { (focusId, label) ->
            AppSurface(
                onClick = { onSelectSection(focusId) },
                shape = AppShapes.container(ContainerLevel.Pill),
                color = AppSurfaceTokens.cardContainer(),
            ) {
                AppText(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.primaryText(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlaybackDiagnosticsSection() {
    val context = LocalContext.current
    val playbackInsightScope = rememberCoroutineScope()
    val scope = playbackInsightScope
    val playerInsightMode by remember(context) { SettingsManager.getPlayerInsightMode(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getPlayerInsightModeSync(context))
    val playerDiagnosticLoggingEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPlayerDiagnosticLoggingEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED)
    val dashSegmentRequestsEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDashSegmentRequestsEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED)
    val qualitySwitchFailureDialogEnabled by remember(context) { SettingsManager.getQualitySwitchFailureDialogEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED)
    val qualitySwitchFailureDialogOnceEnabled by remember(context) { SettingsManager.getQualitySwitchFailureDialogOnceEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED)
    AppPreferenceGroup {
SettingsItemAnchor("playback.player_insight_mode") {
                            SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_STATS),
                            title = settingItemTitle("playback.player_insight_mode"),
                            subtitle = when (playerInsightMode) {
                                PlayerSettingsStore.PlayerInsightMode.OFF -> "不显示播放状态信息"
                                PlayerSettingsStore.PlayerInsightMode.SMART -> "打开控制栏时显示；发生掉帧或软件解码时保持可见"
                                PlayerSettingsStore.PlayerInsightMode.ALWAYS -> "始终显示编码、码率、掉帧等播放信息"
                            },
                            options = listOf(
                                AppSegmentOption(PlayerSettingsStore.PlayerInsightMode.OFF, "关闭"),
                                AppSegmentOption(PlayerSettingsStore.PlayerInsightMode.SMART, "智能显示"),
                                AppSegmentOption(PlayerSettingsStore.PlayerInsightMode.ALWAYS, "始终显示"),
                            ),
                            selectedValue = playerInsightMode,
                            onSelectionChange = { mode ->
                                playbackInsightScope.launch {
                                    SettingsManager.setPlayerInsightMode(context, mode)
                                }
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
}
                        AppPreferenceDivider()
                            SettingsItemAnchor("playback.player_diagnostic_logging_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_DIAGNOSTIC_LOGS),
                                title = playbackSettingTitle("playback.player_diagnostic_logging_enabled"),
                                subtitle = "遇到黑屏、卡顿或无响应时记录排查信息；反馈问题后可关闭",
                                checked = playerDiagnosticLoggingEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setPlayerDiagnosticLoggingEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                        AppPreferenceDivider()
                            SettingsItemAnchor("playback.dash_segment_requests_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.SEGMENT_LOADING_COMPATIBILITY),
                                title = playbackSettingTitle("playback.dash_segment_requests_enabled"),
                                subtitle = if (dashSegmentRequestsEnabled) {
                                    "用于部分视频的分段加载；若出现无法播放或卡住，请关闭此项"
                                } else {
                                    "默认关闭，使用兼容性更好的常规加载方式"
                                },
                                checked = dashSegmentRequestsEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsManager.setDashSegmentRequestsEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                        AppPreferenceDivider()
                            SettingsItemAnchor("playback.quality_switch_failure_dialog_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.QUALITY_WARNING),
                                title = playbackSettingTitle("playback.quality_switch_failure_dialog_enabled"),
                                subtitle = "仅画质切换失败或权限异常时提示",
                                checked = qualitySwitchFailureDialogEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setQualitySwitchFailureDialogEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                        AppPreferenceDivider()
                            SettingsItemAnchor("playback.quality_switch_failure_dialog_once_enabled") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.QUALITY_WARNING_ONCE),
                                title = playbackSettingTitle("playback.quality_switch_failure_dialog_once_enabled"),
                            enabled = qualitySwitchFailureDialogEnabled,
                                subtitle = if (qualitySwitchFailureDialogEnabled) {
                                    "首次提醒后不再弹出；关闭此项会重新计数"
                                } else {
                                    "开启画质切换失败时提示后生效"
                                },
                                checked = qualitySwitchFailureDialogOnceEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SettingsManager.setQualitySwitchFailureDialogOnceEnabled(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                    }
}

@Composable
private fun LongPressSlideSpeedLivePreview(mode: com.android.purebilibili.core.store.LongPressSpeedSlideMode) {
    val enabled = mode != com.android.purebilibili.core.store.LongPressSpeedSlideMode.OFF
    val isContinuous = mode == com.android.purebilibili.core.store.LongPressSpeedSlideMode.CONTINUOUS
    androidx.compose.animation.AnimatedVisibility(visible = enabled) {
        val gate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
        val phaseState = remember { androidx.compose.animation.core.Animatable(0.35f) }
        var previewSpeed by remember(mode) { mutableFloatStateOf(2.0f) }
        LaunchedEffect(gate.active, mode) {
            if (!gate.active) {
                phaseState.snapTo(0.35f)
                previewSpeed = 2.0f
                return@LaunchedEffect
            }
            if (isContinuous) {
                val waypoints = listOf(2.0f, 3.4f, 4.6f, 2.3f, 0.8f, 1.5f)
                var idx = 0
                while (true) {
                    val next = waypoints[idx % waypoints.size]
                    idx += 1
                    val start = previewSpeed
                    val steps = kotlin.math.abs(kotlin.math.round((next - start) * 10f).toInt()).coerceAtLeast(1)
                    val targetFraction = ((next - 0.5f) / 4.5f).coerceIn(0f, 1f)
                    val startFraction = phaseState.value
                    for (step in 1..steps) {
                        val t = step.toFloat() / steps.toFloat()
                        val stepped = kotlin.math.round((start + (next - start) * t) * 10f) / 10f
                        previewSpeed = stepped.coerceIn(0.5f, 5.0f)
                        phaseState.animateTo(
                            targetValue = startFraction + (targetFraction - startFraction) * t,
                            animationSpec = androidx.compose.animation.core.tween(95),
                        )
                    }
                    kotlinx.coroutines.delay(420)
                }
            } else {
                val presetStops = listOf(2.0f, 3.0f, 5.0f, 2.5f, 1.5f, 0.5f, 1.25f)
                var idx = 0
                while (true) {
                    val target = presetStops[idx % presetStops.size]
                    idx += 1
                    previewSpeed = target
                    phaseState.animateTo(
                        targetValue = ((target - 0.5f) / 4.5f).coerceIn(0f, 1f),
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.78f,
                            stiffness = 380f,
                        ),
                    )
                    kotlinx.coroutines.delay(560)
                }
            }
        }
        val primary = MaterialTheme.colorScheme.primary
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        val containerColor = AppSurfaceTokens.groupedListContainer()
        val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        val previewShape = AppShapes.container(ContainerLevel.Field)
        val scaleLabels = remember { listOf("0.5x", "1.5x", "2.5x", "4.0x", "5.0x") }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(gate.modifier)
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp)
                .clip(previewShape)
                .background(containerColor)
                .border(1.dp, borderColor, previewShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = if (isContinuous) "左右滑动按 0.1x 无极微调" else "左右滑动在预设档位间切换",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    com.android.purebilibili.feature.video.ui.section.AnimatedBlurredSpeedText(
                        speed = previewSpeed,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primary,
                    )
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val trackWidthPx = constraints.maxWidth.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(primary.copy(alpha = 0.18f))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tickCount = if (isContinuous) 11 else 5
                        repeat(tickCount) {
                            Box(
                                modifier = Modifier
                                    .size(if (isContinuous) 4.dp else 6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(primary.copy(alpha = if (isContinuous) 0.34f else 0.44f))
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val maxTravelPx = (trackWidthPx - size.width).coerceAtLeast(0f)
                                translationX = phaseState.value.coerceIn(0f, 1f) * maxTravelPx
                            }
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(primary)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        AppText(
                            text = if (isContinuous) "⇄ ±0.1x" else "⇄ 换挡",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = onPrimary,
                            maxLines = 1,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    scaleLabels.forEach { label ->
                        AppText(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText().copy(alpha = 0.78f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicLyricsStyleLivePreview(style: SettingsManager.MusicLyricsUiStyle) {
    val gate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val sweepState = remember { androidx.compose.animation.core.Animatable(0.55f) }
    LaunchedEffect(gate.active, style) {
        if (!gate.active) {
            sweepState.snapTo(0.65f)
            return@LaunchedEffect
        }
        while (true) {
            sweepState.snapTo(0f)
            sweepState.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(2200),
            )
            kotlinx.coroutines.delay(350)
        }
    }
    val isArc = style == SettingsManager.MusicLyricsUiStyle.ARC
    val isClassic = style == SettingsManager.MusicLyricsUiStyle.CLASSIC
    val previewBg = Color(0xFF151822)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(gate.modifier)
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(previewBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (isArc) {
                        rotationY = 14f
                        cameraDistance = 12f * density
                    }
                },
            horizontalAlignment = if (isClassic) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = "上一句歌词轻轻淡出",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.32f),
            )
            Box(
                modifier = Modifier.graphicsLayer {
                    if (!isClassic) {
                        translationY = -2.dp.toPx() * sweepState.value
                    }
                }
            ) {
                AppText(
                    text = when (style) {
                        SettingsManager.MusicLyricsUiStyle.CLASSIC -> "当前句居中清晰高亮"
                        SettingsManager.MusicLyricsUiStyle.IMMERSIVE -> "当前句大字逐词高亮上浮"
                        SettingsManager.MusicLyricsUiStyle.ARC -> "3D 弧面纵深 · 逐词高亮"
                    },
                    style = if (isClassic) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
                AppText(
                    text = when (style) {
                        SettingsManager.MusicLyricsUiStyle.CLASSIC -> "当前句居中清晰高亮"
                        SettingsManager.MusicLyricsUiStyle.IMMERSIVE -> "当前句大字逐词高亮上浮"
                        SettingsManager.MusicLyricsUiStyle.ARC -> "3D 弧面纵深 · 逐词高亮"
                    },
                    style = if (isClassic) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.55f + 0.45f * sweepState.value
                    },
                )
            }
            AppText(
                text = "下一句歌词即将唱起",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.32f),
            )
        }
    }
}

@Composable
private fun PortraitPlayerCollapseLivePreview(mode: com.android.purebilibili.core.store.PortraitPlayerCollapseMode) {
    val animatesCollapse = mode != com.android.purebilibili.core.store.PortraitPlayerCollapseMode.OFF
    val gate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val animateActive = gate.active && animatesCollapse
    val collapseState = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(animateActive, mode) {
        if (!animateActive) {
            collapseState.snapTo(if (animatesCollapse) 0.7f else 0f)
            return@LaunchedEffect
        }
        while (true) {
            collapseState.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(900),
            )
            kotlinx.coroutines.delay(550)
            collapseState.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(800),
            )
            kotlinx.coroutines.delay(550)
        }
    }
    val accent = AppContentColors.accent()
    val containerColor = AppSurfaceTokens.groupedListContainer()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(gate.modifier)
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 微型手机示意图：上方视频区随评论上滑平滑缩小
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 68.dp)
                .clip(AppShapes.container(ContainerLevel.Tag))
                .background(Color(0xFF1A1D26))
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .graphicsLayer {
                        val factor = 1f - 0.55f * collapseState.value
                        scaleY = factor
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.85f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -12.dp.toPx() * collapseState.value
                    },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AppText(
                text = "效果示意：${mode.label}",
                style = MaterialTheme.typography.labelLarge,
                color = AppContentColors.primaryText(),
            )
            AppText(
                text = when (mode) {
                    com.android.purebilibili.core.store.PortraitPlayerCollapseMode.OFF ->
                        "播放时视频区保持完整大小，不随评论滚动缩小"
                    com.android.purebilibili.core.store.PortraitPlayerCollapseMode.INTRO_ONLY ->
                        "看竖屏视频滑评论或简介时，上方画面自动缩小让出阅读空间"
                    com.android.purebilibili.core.store.PortraitPlayerCollapseMode.COMMENT_ONLY ->
                        "看横屏视频滑评论区时，上方画面自动缩小，看评论更舒展"
                    com.android.purebilibili.core.store.PortraitPlayerCollapseMode.BOTH ->
                        "横屏与竖屏视频在浏览下方内容时，均会自动缩小上方画面"
                    com.android.purebilibili.core.store.PortraitPlayerCollapseMode.PAUSED_ONLY ->
                        "播放中保持大屏不缩小；仅暂停后再上滑时自动收起画面"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
        }
    }
}
