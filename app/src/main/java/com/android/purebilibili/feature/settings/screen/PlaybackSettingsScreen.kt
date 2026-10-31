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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlin.math.roundToInt
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
        val keys = when (page) {
            PlaybackSettingsPage.PLAYBACK -> playbackSettingsSectionKeys
            PlaybackSettingsPage.FULLSCREEN -> listOf(SettingsSearchFocusIds.PLAYBACK_FULLSCREEN)
            PlaybackSettingsPage.COMMENTS -> listOf(SettingsSearchFocusIds.PLAYBACK_COMMENTS)
            PlaybackSettingsPage.DECODER -> listOf(SettingsSearchFocusIds.PLAYBACK_DECODER)
            PlaybackSettingsPage.DIAGNOSTICS -> listOf(SettingsSearchFocusIds.PLAYBACK_DEBUG)
        }.flatMap { listOf(it + "_title", it) }
        val key = resolveSettingsItemScrollKey(request.settingId)
            ?: if (playbackFocusId == SettingsSearchFocusIds.PLAYBACK_GESTURE) SettingsSearchFocusIds.PLAYBACK_FULLSCREEN else playbackFocusId
        val index = keys.indexOf(key)
        if (index < 0) {
            SettingsSearchFocusController.clear(request.token)
            return@LaunchedEffect
        }
        if (request.settingId != null) listState.scrollToItem(index) else listState.animateScrollToItem(index)
        if (request.settingId == null) SettingsSearchFocusController.clear(request.token)
        else clearUnclaimedSettingsFocusRequest(request.token)
    }


    var showPipPermissionDialog by remember { mutableStateOf(false) }
    val playbackInsightScope = rememberCoroutineScope()

    val miniPlayerMode = playbackPreferences.miniPlayerMode
    val stopPlaybackOnExit = playbackPreferences.stopPlaybackOnExit
    val backgroundPlaybackEnabled = playbackPreferences.backgroundPlaybackEnabled
    val audioFocusEnabled = playbackPreferences.audioFocusEnabled
    val audioModeAutoPipEnabled = playbackPreferences.audioModeAutoPipEnabled
    val audioNowPlayingBarEnabled = playbackPreferences.audioNowPlayingBarEnabled
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
    val nativeMiuixPlayerPopups by remember(context) { PlayerSettingsStore.getNativeMiuixPlayerPopups(context) }.collectAsStateWithLifecycle(initialValue = true)
    val longPressSpeedHintHidden = playerConfiguration.longPressSpeedHintHidden
    val longPressSpeedHintScale = playerConfiguration.longPressSpeedHintScale
    val longPressSpeedHintAlpha = playerConfiguration.longPressSpeedHintAlpha
    val videoCodecPreference = playbackPreferences.videoCodecPreference
    val videoSecondCodecPreference = playbackPreferences.videoSecondCodecPreference
    val playbackCdnPreferenceValue by remember(context) { SettingsManager.getPlaybackCdnPreference(context) }
        .collectAsStateWithLifecycle(initialValue = PlaybackCdnPreference.BASE_URL.storageValue)
    val playbackCdnPreference = remember(playbackCdnPreferenceValue) {
        PlaybackCdnPreference.fromStorageValue(playbackCdnPreferenceValue)
    }
    val playbackCdnOptions = remember {
        PlaybackCdnPreference.entries.map { preference ->
            AppSegmentOption(preference, preference.displayName)
        }
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
                Box(modifier = Modifier.entrance()) {
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
                                SettingsItemAnchor("playback.native_miuix_player_popups") {
                                    AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.NATIVE_MIUIX_DIALOG),
                                title = playbackSettingTitle("playback.native_miuix_player_popups"),
                                subtitle = "播放器、动态和 UP 主页使用；关闭后用 Material 3",
                                checked = nativeMiuixPlayerPopups,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        PlayerSettingsStore.setNativeMiuixPlayerPopups(context, enabled)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText(),
                            )
                                }
                        AppPreferenceDivider()
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
                                        text = "倍速提示背景深浅",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AppContentColors.primaryText()
                                    )
                                    Text(
                                        text = "调高后，倍速提示和其他提示的背景更深",
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
            item(key = SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER) {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val pipNoDanmakuEnabled = playerConfiguration.pipNoDanmakuEnabled
                    val modeControlsEnabled = remember(stopPlaybackOnExit, backgroundPlaybackEnabled) {
                        !stopPlaybackOnExit && backgroundPlaybackEnabled
                    }
                    val audioModeAutoPipToggleEnabled = remember(miniPlayerMode, backgroundPlaybackEnabled) {
                        com.android.purebilibili.core.store.SettingsManager
                            .shouldEnableAudioModeAutoPipToggle(miniPlayerMode) && backgroundPlaybackEnabled
                    }
                    val pipDanmakuToggleEnabled = remember(miniPlayerMode, backgroundPlaybackEnabled) {
                        backgroundPlaybackEnabled &&
                            miniPlayerMode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF
                    }
                    val miniPlayerOptions = listOf(
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF, "默认"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY, "小窗"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP, "画中画"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP, "两种小窗")
                    )

                    AppPreferenceGroup {
                            SettingsItemAnchor("playback.stop_playback_on_exit") {
                                AppSwitchPreference(
                                    icon = rememberSettingsSemanticIcon(SettingsIconRole.STOP_ON_EXIT),
                                title = playbackSettingTitle("playback.stop_playback_on_exit"),
                                subtitle = "开启后，返回其他页面时立即停止，也不会进入小窗或后台播放",
                                checked = stopPlaybackOnExit,
                                onCheckedChange = {
                                    scope.launch {
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
                                onCheckedChange = {
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setBackgroundPlaybackEnabled(context, it)
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
                                    scope.launch {
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
                            title = settingItemTitle("playback.mini_player_mode"),
                            subtitle = if (stopPlaybackOnExit) {
                                "请先关闭“离开播放页后停止”"
                            } else if (!backgroundPlaybackEnabled) {
                                "请先开启“后台播放”"
                            } else {
                                miniPlayerMode.description
                            },
                            options = miniPlayerOptions,
                            selectedValue = miniPlayerMode,
                            enabled = modeControlsEnabled,
                            onSelectionChange = { mode ->
                                scope.launch {
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
                                    scope.launch {
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
                                    scope.launch {
                                        SettingsManager.setAudioNowPlayingBarOpensAudioMode(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("playback.audio_now_playing_bar_immersive_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_TITLE_AUTO_HIDE),
                                title = playbackSettingTitle("playback.audio_now_playing_bar_immersive_enabled"),
                                subtitle = if (audioNowPlayingBarImmersiveEnabled) {
                                    "播放中 5 秒不操作时隐藏；点底部横条恢复"
                                } else {
                                    "关闭后标题横条始终显示"
                                },
                                checked = audioNowPlayingBarImmersiveEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setAudioNowPlayingBarImmersiveEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
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
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setLoudnessNormalizationEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                        }
                        AppPreferenceDivider()
                        SettingsItemAnchor("playback.audio_spectrum_enabled") {
                            AppSwitchPreference(
                                icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_SPECTRUM),
                                title = playbackSettingTitle("playback.audio_spectrum_enabled"),
                                subtitle = "听视频时让封面背景光晕随声音变化；无需录音权限",
                                checked = audioSpectrumEnabled,
                                onCheckedChange = {
                                    scope.launch {
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
                                    scope.launch {
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
                            title = settingItemTitle("playback.music_lyrics_ui_style"),
                            subtitle = when (musicLyricsUiStyle) {
                                SettingsManager.MusicLyricsUiStyle.CLASSIC ->
                                    "经典全屏歌词；可在听视频右上角更多菜单临时切换"
                                SettingsManager.MusicLyricsUiStyle.IMMERSIVE ->
                                    "大字歌词，随播放逐字高亮"
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
                            ),
                            selectedValue = musicLyricsUiStyle,
                            onSelectionChange = { style ->
                                scope.launch {
                                    SettingsManager.setMusicLyricsUiStyle(context, style)
                                }
                            },
                            iconTint = AppContentColors.secondaryText()
                        )
}

                        //  权限提示（仅当选择支持系统 PiP 的模式且无权限时显示）
                        if (modeControlsEnabled &&
                            miniPlayerMode.supportsSystemPip
                            && !checkPipPermission()) {
                            AppPreferenceDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPipPermissionDialog = true }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_warning_24),
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
                                    com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_right_24),
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
                                    "开启后台播放后，小窗和画中画相关设置才会生效"
                                } else if (miniPlayerMode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF) {
                                    if (pipNoDanmakuEnabled) "小窗和画中画中不显示弹幕" else "小窗和画中画中也显示弹幕"
                                } else {
                                    "选择小窗或画中画模式后生效"
                                },
                                checked = pipNoDanmakuEnabled,
                                enabled = pipDanmakuToggleEnabled,
                                onCheckedChange = {
                                    if (!pipDanmakuToggleEnabled) {
                                        return@AppSwitchPreference
                                    }
                                    scope.launch {
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
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setAudioModeAutoPipEnabled(context, it)
                                    }
                                },
                                iconTint = AppContentColors.secondaryText()
                            )
                            }
                    }
                }
            }

            //  手势设置
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
SettingsSingleChoicePreference(
                            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_router_24),
                            title = settingItemTitle("playback.playback_cdn_preference"),
                            subtitle = "新视频优先使用此线路；不可用时换备用线路",
                            options = playbackCdnOptions,
                            selectedValue = playbackCdnPreference,
                            onSelectionChange = { preference ->
                                scope.launch {
                                    SettingsManager.setPlaybackCdnPreference(
                                        context,
                                        preference.storageValue,
                                    )
                                }
                            },
                            iconTint = AppContentColors.secondaryText(),
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
            if (page == PlaybackSettingsPage.PLAYBACK || page == PlaybackSettingsPage.FULLSCREEN) {
                item(key = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN + "_title") {
                    AppPreferenceSectionTitle("全屏与手势")
                }
                item(key = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN) {
                    PlaybackFullscreenGestureSettingsSection(
                        context = context,
                        state = state,
                        viewModel = viewModel,
                    )
                }
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
    val hideInteractiveCommandDanmaku by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDanmakuHideInteractiveCommands(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val danmakuCloudSyncEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDanmakuCloudSyncEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    //  [新增] 自动播放下一个
    val autoPlayEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getAutoPlay(context) }.collectAsStateWithLifecycle(initialValue = true)
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
        .collectAsStateWithLifecycle(initialValue = true)
    val commentAvatarPushEnabled by remember(context) { SettingsManager.getCommentAvatarPushEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val commentMemberDecorationsEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getCommentMemberDecorationsEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val detailedCommentTimeEnabled by remember(context) { SettingsManager.getDetailedCommentTimeEnabled(context) }
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
        SettingsItemAnchor("playback.next_watch_hint_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.NEXT_VIDEO_HINT),
                title = playbackSettingTitle("playback.next_watch_hint_enabled"),
                subtitle = "最后 5 秒提示下一集或相关推荐，关闭不影响自动续播",
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
        SettingsItemAnchor("playback.playback_completion_behavior") {
            SettingsSingleChoicePreference(
                title = settingItemTitle("playback.playback_completion_behavior"),
                subtitle = "分 P 与视频合集分别处理；外部列表内先续播当前视频分 P，再按列表切视频。“自动”受下方两个连播开关控制",
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
                subtitle = "“自动”策略下控制当前视频下一 P；非外部列表播放时，也控制视频合集续播",
                checked = autoPlayEnabled,
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
                subtitle = "“自动”策略下，当前视频最后一 P 播完后，控制稍后再看、收藏夹等队列是否播放下一视频",
                checked = externalPlaylistAutoContinueEnabled,
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
                    title = settingItemTitle("playback.subtitle_appearance"),
                    subtitle = "字号、颜色、描边与独立字体导入",
                    onClick = { showSubtitleAppearance = true },
                )
            }
            AppPreferenceDivider()
SettingsItemAnchor("playback.subtitle_auto_preference") {
            SettingsSingleChoicePreference(
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
        SettingsItemAnchor("playback.hide_interactive_command_danmaku") {
    AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTIVE_COMMANDS),
                title = playbackSettingTitle("playback.hide_interactive_command_danmaku"),
                subtitle = if (hideInteractiveCommandDanmaku) {
                    "隐藏画面内的关注、三连、UP 提示和投票"
                } else {
                    "显示画面内的关注、三连、UP 提示和投票"
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
        SettingsItemAnchor("playback.danmaku_cloud_sync_enabled") {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.DANMAKU_CLOUD_SYNC),
                title = playbackSettingTitle("playback.danmaku_cloud_sync_enabled"),
                subtitle = com.android.purebilibili.feature.video.danmaku
                    .resolveDanmakuCloudSyncToggleSubtitle(danmakuCloudSyncEnabled),
                checked = danmakuCloudSyncEnabled,
                onCheckedChange = {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setDanmakuCloudSyncEnabled(context, it)
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
                    "关闭后隐藏视频警告、争议信息及禁止转载声明"
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

        AppListItem(
            headlineContent = { AppText("评论 IP 属地") },
            supportingContent = {
                AppText("无需开启；B站返回属地时会在评论时间旁自动显示。部分评论没有属地数据。")
            },
            leadingContent = {
                SettingsFunctionIcon(
                    rememberMaterialSymbol(R.drawable.ms_info_24),
                    title = "评论 IP 属地",
                    contentDescription = null,
                )
            },
        )
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
                subtitle = "双击视频画面快捷点赞",
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
                subtitle = "发送成功后自动检查评论是否正常显示",
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
                    subtitle = "头像以作者行为原位，滑出顶部后吸附并随评论边界顶替，滑回即恢复；支持评论、回复与搜索结果",
                    checked = commentAvatarPushEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { SettingsManager.setCommentAvatarPushEnabled(context, enabled) }
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

@Composable
private fun PlaybackFullscreenGestureSettingsSection(
    context: Context,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val playerConfiguration = rememberSettingsPlayerConfiguration()
    val scope = rememberCoroutineScope()
    val portraitPlayerCollapseMode by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPortraitPlayerCollapseMode(context) }
        .collectAsStateWithLifecycle(initialValue = PortraitPlayerCollapseMode.INTRO_ONLY)
    val videoDetailChromeScrollHideEnabled by remember(context) { SettingsManager.getVideoDetailChromeScrollHideEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val portraitSwipeToFullscreenEnabled = playerConfiguration.portraitSwipeToFullscreenEnabled
    val directPortraitStoryEntry = playerConfiguration.directPortraitStoryEntry
    val portraitOnlyVerticalRecommendations by remember(context) { com.android.purebilibili.core.store.SettingsManager.getPortraitOnlyVerticalRecommendations(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val launchToPortraitFeedOnStartup = playerConfiguration.launchToPortraitFeedOnStartup
    val centerSwipeToFullscreenEnabled = playerConfiguration.centerSwipeToFullscreenEnabled
    val slideVolumeBrightnessEnabled = playerConfiguration.slideVolumeBrightnessEnabled
    val setSystemBrightnessEnabled = playerConfiguration.setSystemBrightnessEnabled
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
    val inlineSwipeSeekSeconds = playerConfiguration.inlineSwipeSeekSeconds
    val fullscreenSwipeSeekEnabled = playerConfiguration.fullscreenSwipeSeekEnabled
    val fullscreenSwipeSeekSeconds = playerConfiguration.fullscreenSwipeSeekSeconds
    val swipeSeekUseRelativeDuration = playerConfiguration.swipeSeekUseRelativeDuration
    val swipeSeekRelativePercent = playerConfiguration.swipeSeekRelativePercent
    val doubleTapSeekEnabled = playerConfiguration.doubleTapSeekEnabled
    val seekForwardSeconds = playerConfiguration.seekForwardSeconds
    val seekBackwardSeconds = playerConfiguration.seekBackwardSeconds
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
    AppPreferenceGroup {
        AppSliderDialogPreference(
            title = "手势灵敏度",
            subtitle = "调整快进、音量和亮度手势的响应速度",
            value = playerConfiguration.gestureSensitivity,
            onValueChange = viewModel::setGestureSensitivity,
            valueRange = 0.5f..2.0f,
            steps = 5,
            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_gesture_24),
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
            }
        }
        AppPreferenceDivider()
SettingsItemAnchor("playback.portrait_player_collapse_mode") {
        SettingsSingleChoicePreference(
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
                    setSystemBrightnessEnabled && canWriteSystemSettings ->
                        "亮度手势会同时修改当前画面和设备系统亮度"
                    else -> "关闭时只临时调整当前播放画面；开启需要系统授权"
                },
                checked = setSystemBrightnessEnabled && canWriteSystemSettings,
                enabled = slideVolumeBrightnessEnabled,
                onCheckedChange = { requestedEnabled ->
                    if (!slideVolumeBrightnessEnabled) return@AppSwitchPreference
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
                },
                iconTint = AppContentColors.secondaryText()
            )
            }

        AppPreferenceDivider()
        SettingsItemAnchor("playback.swipe_seek_use_relative_duration") {
            AppSwitchPreference(
                title = settingItemTitle("playback.swipe_seek_use_relative_duration"),
                subtitle = if (swipeSeekUseRelativeDuration) {
                    "按视频总时长比例快进快退，视频越长跳转越大；需要精确调整时建议关闭"
                } else {
                    "使用固定秒数，不受视频长短影响；需要精确调整时建议保持关闭"
                },
                checked = swipeSeekUseRelativeDuration,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SettingsManager.setSwipeSeekUseRelativeDuration(context, enabled)
                    }
                }
            )
        }
        AppPreferenceDivider()
        if (swipeSeekUseRelativeDuration) {
            SettingsItemAnchor("playback.swipe_seek_relative_percent") {
                SettingsSingleChoicePreference(
                    title = settingItemTitle("playback.swipe_seek_relative_percent"),
                    subtitle = "灵敏度为 1 时，横向拖过整个播放器宽度可调整视频总时长的所选比例；各播放场景共用，忽略固定秒数及横屏精细调节开关",
                    options = listOf(
                        AppSegmentOption(25, "25%"),
                        AppSegmentOption(50, "50%"),
                        AppSegmentOption(90, "90%"),
                        AppSegmentOption(100, "100%")
                    ),
                    selectedValue = swipeSeekRelativePercent,
                    onSelectionChange = { percent ->
                        scope.launch {
                            SettingsManager.setSwipeSeekRelativePercent(context, percent)
                        }
                    }
                )
            }
        } else {
SettingsItemAnchor("playback.inline_swipe_seek_seconds") {
        SettingsSingleChoicePreference(
            title = settingItemTitle("playback.inline_swipe_seek_seconds"),
            subtitle = if (inlineSwipeSeekSeconds == 0) "按拖动距离调整进度，不限制单次调整秒数" else "左右拖动约半屏达到 ${inlineSwipeSeekSeconds} 秒上限，数值越小越精确",
            options = listOf(
                AppSegmentOption(0, "不限制"),
                AppSegmentOption(5, "5秒"),
                AppSegmentOption(10, "10秒"),
                AppSegmentOption(15, "15秒"),
                AppSegmentOption(30, "30秒"),
                AppSegmentOption(60, "60秒"),
            ),
            selectedValue = inlineSwipeSeekSeconds,
            onSelectionChange = { seconds ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setInlineSwipeSeekSeconds(context, seconds)
                }
            },
        )
}

        AppPreferenceDivider()
        SettingsItemAnchor("playback.fullscreen_swipe_seek_enabled") {
            AppSwitchPreference(
                title = playbackSettingTitle("playback.fullscreen_swipe_seek_enabled"),
                subtitle = if (fullscreenSwipeSeekEnabled) {
                    if (fullscreenSwipeSeekSeconds == 0) "精细调节已开启，但当前仍为“不限制”；选择较小秒数可减少误跳" else "已开启，约半屏最多调整 ${fullscreenSwipeSeekSeconds} 秒"
                } else {
                    "精细调节已关闭，使用不限范围的拖动调节；需要精确调整时建议开启"
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
SettingsItemAnchor("playback.fullscreen_swipe_seek_seconds") {
        SettingsSingleChoicePreference(
            title = settingItemTitle("playback.fullscreen_swipe_seek_seconds"),
            subtitle = if (fullscreenSwipeSeekSeconds == 0) "按拖动距离调整进度，不限制单次调整秒数" else "左右拖动约半屏达到的秒数上限，数值越小越精确",
            options = listOf(
                AppSegmentOption(0, "不限制"),
                AppSegmentOption(10, "10秒"),
                AppSegmentOption(15, "15秒"),
                AppSegmentOption(20, "20秒"),
                AppSegmentOption(30, "30秒"),
            ),
            selectedValue = fullscreenSwipeSeekSeconds,
            enabled = fullscreenSwipeSeekEnabled,
            onSelectionChange = { seconds ->
                if (fullscreenSwipeSeekEnabled) {
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setFullscreenSwipeSeekSeconds(context, seconds)
                    }
                }
            },
        )
}
        }
        AppPreferenceDivider()
        val autoRotateEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getAutoRotateEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val landscapeHoldEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getLandscapeHoldEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val fullscreenGestureReverse = playerConfiguration.fullscreenGestureReverse
        val autoEnterFullscreen = playerConfiguration.autoEnterFullscreenEnabled
        val autoExitFullscreen = playerConfiguration.autoExitFullscreenEnabled
        val showFullscreenLockButton by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenLockButton(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenScreenshotButton by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenScreenshotButton(context) }.collectAsStateWithLifecycle(initialValue = true)
        val appGestureScreenshotEnabled by remember(context) { SettingsManager.getAppGestureScreenshotEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val appScreenshotGestureMode by remember(context) { SettingsManager.getAppScreenshotGestureMode(context) }
            .collectAsStateWithLifecycle(initialValue = AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS)
        val appScreenshotCaptureMode by remember(context) { SettingsManager.getAppScreenshotCaptureMode(context) }
            .collectAsStateWithLifecycle(initialValue = AppScreenshotCaptureMode.FULL_WINDOW)
        val showFullscreenBatteryLevel by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenBatteryLevel(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenTime by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenTime(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenActionItems by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowFullscreenActionItems(context) }.collectAsStateWithLifecycle(initialValue = true)
        val showOnlineCount by remember(context) { com.android.purebilibili.core.store.SettingsManager.getShowOnlineCount(context) }.collectAsStateWithLifecycle(initialValue = false)
        val chapterDisplayMode by remember(context) { SettingsManager.getChapterDisplayMode(context) }
            .collectAsStateWithLifecycle(initialValue = ChapterDisplayMode.WITH_CONTROLS)
        val bottomProgressBehavior by remember(context) { com.android.purebilibili.core.store.SettingsManager.getBottomProgressBehavior(context) }
            .collectAsStateWithLifecycle(initialValue = BottomProgressBehavior.ALWAYS_HIDE)
        val progressPeakDanmakuEnabled by remember(context) { SettingsManager.getProgressPeakDanmakuEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = false)
        val playerControlVisibility by remember(context) { SettingsManager.getPlayerControlVisibilitySettings(context) }
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.PlayerControlVisibilitySettings()
            )
        val windowSizeClass = LocalWindowSizeClass.current
        val displayContext = LocalAppWindowAdaptiveInfo.current.displayContext
        val isLargeScreenDevice = windowSizeClass.isTabletDevice ||
            displayContext.isKnownFoldableDevice
        val horizontalAdaptationEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getHorizontalAdaptationEnabled(context) }
            .collectAsStateWithLifecycle(initialValue = isLargeScreenDevice)
        val videoAmbientSettings by remember(context) { SettingsManager.getVideoAmbientSettings(context) }
            .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.feature.video.ambient.AmbientSettings())
        val immersiveVideoPageStatusBar by remember(context) { com.android.purebilibili.core.store.SettingsManager.getHideVideoPageStatusBar(context) }
            .collectAsStateWithLifecycle(initialValue = false)
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
        com.android.purebilibili.feature.settings.PlayerRotationSensitivityPreference()
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
        AppPreferenceDivider()
SettingsItemAnchor("playback.tablet_comment_panel_width_preset") {
        SettingsSingleChoicePreference(
            title = settingItemTitle("playback.tablet_comment_panel_width_preset"),
            subtitle = if (horizontalAdaptationEnabled) {
                "调整横屏适配下右侧评论/推荐栏宽度"
            } else {
                "开启横屏适配后生效"
            },
            options = resolveTabletCommentPanelWidthSegmentOptions(),
            selectedValue = tabletCommentPanelWidthPreset,
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
            title = settingItemTitle("playback.tablet_secondary_default_tab"),
            subtitle = "选择首次打开时，右栏显示推荐还是评论",
            options = resolveTabletSecondaryDefaultTabOptions(),
            selectedValue = tabletSecondaryDefaultTab,
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
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 180,
                    easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.Continuity,
                ),
            ) else androidx.compose.animation.EnterTransition.None,
            exit = if (animateAmbientOptions) androidx.compose.animation.shrinkVertically(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 140,
                    easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.Continuity,
                ),
            ) else androidx.compose.animation.ExitTransition.None,
        ) {
            Column {
            AppPreferenceDivider()
SettingsItemAnchor("playback.video_ambient_settings.strength") {
            SettingsSingleChoicePreference(
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
            title = settingItemTitle("playback.app_screenshot_gesture_mode"),
            subtitle = appScreenshotGestureMode.description,
            options = resolveAppScreenshotGestureModeSegmentOptions(),
            selectedValue = appScreenshotGestureMode,
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
            title = settingItemTitle("playback.app_screenshot_capture_mode"),
            subtitle = appScreenshotCaptureMode.description,
            options = resolveAppScreenshotCaptureModeSegmentOptions(),
            selectedValue = appScreenshotCaptureMode,
            onSelectionChange = { mode ->
                scope.launch {
                    SettingsManager.setAppScreenshotCaptureMode(context, mode)
                }
            }
        )
}
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
        AppPreferenceDivider()

        SettingsItemAnchor("playback.chapter_display_mode") {
            SettingsSingleChoicePreference(
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
