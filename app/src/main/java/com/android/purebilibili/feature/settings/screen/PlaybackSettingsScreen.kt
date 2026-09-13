// 文件路径: feature/settings/PlaybackSettingsScreen.kt
package com.android.purebilibili.feature.settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_ALPHA_MAX
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_ALPHA_MIN
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_SCALE_MAX
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_SCALE_MIN
import com.android.purebilibili.core.store.LONG_PRESS_SPEED_HINT_STEP
import com.android.purebilibili.core.store.player.DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.store.BottomProgressBehavior
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.store.PortraitPlayerCollapseMode
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.theme.iOSTeal
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.util.LocalWindowSizeClass
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val screenTitle = stringResource(R.string.playback_settings_title)
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
            PlaybackSettingsContent(viewModel = viewModel, state = state)
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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val warningTint = rememberAdaptiveSemanticIconTint(iOSOrange)
    val windowSizeClass = LocalWindowSizeClass.current
    // val state by viewModel.state.collectAsStateWithLifecycle() // Moved to parameter
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    LaunchedEffect(focusRequest?.token) {
        val request = focusRequest ?: return@LaunchedEffect
        val playbackFocusId = when (request.target) {
            SettingsSearchTarget.PLAYBACK -> request.focusId
            else -> resolveSettingsSceneDetailFocus(request.target)
                ?.takeIf { it.target == SettingsSearchTarget.PLAYBACK }
                ?.focusId
        } ?: return@LaunchedEffect
        val index = resolvePlaybackSettingsScrollIndex(playbackFocusId) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        SettingsSearchFocusController.clear(request.token)
    }


    var showPipPermissionDialog by remember { mutableStateOf(false) }
    val playbackInsightScope = rememberCoroutineScope()

    val miniPlayerMode by com.android.purebilibili.core.store.SettingsManager
        .getMiniPlayerMode(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF
        )
    val stopPlaybackOnExit by com.android.purebilibili.core.store.SettingsManager
        .getStopPlaybackOnExit(context).collectAsStateWithLifecycle(initialValue = false)
    val backgroundPlaybackEnabled by com.android.purebilibili.core.store.SettingsManager
        .getBackgroundPlaybackEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val audioFocusEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAudioFocusEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val audioModeAutoPipEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAudioModeAutoPipEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val audioNowPlayingBarEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAudioNowPlayingBarEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val playerDiagnosticLoggingEnabled by com.android.purebilibili.core.store.SettingsManager
        .getPlayerDiagnosticLoggingEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED)
    val playerInsightMode by SettingsManager
        .getPlayerInsightMode(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getPlayerInsightModeSync(context))
    val dashSegmentRequestsEnabled by com.android.purebilibili.core.store.SettingsManager
        .getDashSegmentRequestsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED)
    val qualitySwitchFailureDialogEnabled by SettingsManager
        .getQualitySwitchFailureDialogEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED)
    val qualitySwitchFailureDialogOnceEnabled by SettingsManager
        .getQualitySwitchFailureDialogOnceEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED)
    val defaultPlaybackSpeed by com.android.purebilibili.core.store.SettingsManager
        .getDefaultPlaybackSpeed(context).collectAsStateWithLifecycle(initialValue = 1.0f)
    val rememberLastPlaybackSpeed by com.android.purebilibili.core.store.SettingsManager
        .getRememberLastPlaybackSpeed(context).collectAsStateWithLifecycle(initialValue = false)
    val nativeMiuixPlayerPopups by PlayerSettingsStore
        .getNativeMiuixPlayerPopups(context).collectAsStateWithLifecycle(initialValue = true)
    val longPressSpeedHintHidden by SettingsManager
        .getLongPressSpeedHintHidden(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintHiddenSync(context)
        )
    val longPressSpeedHintScale by SettingsManager
        .getLongPressSpeedHintScale(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintScaleSync(context)
        )
    val longPressSpeedHintAlpha by SettingsManager
        .getLongPressSpeedHintAlpha(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintAlphaSync(context)
        )
    val videoCodecPreference by com.android.purebilibili.core.store.SettingsManager
        .getVideoCodec(context).collectAsStateWithLifecycle(initialValue = "hev1")
    val videoSecondCodecPreference by com.android.purebilibili.core.store.SettingsManager
        .getVideoSecondCodec(context).collectAsStateWithLifecycle(initialValue = "avc1")
    val playbackCdnPreferenceValue by SettingsManager
        .getPlaybackCdnPreference(context)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                context.applicationInfo.uid,
                context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
        return false
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
            title = { AppText("权限申请", color = MaterialTheme.colorScheme.onSurface) },
            text = { AppText("检测到未开启「画中画」权限。请在设置中开启该权限，否则无法使用小窗播放。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    AppText("暂不开启", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            //  解码设置
            //  解码设置
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("视频解码")
                }
            }
            item {
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
		                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.NATIVE_MIUIX_DIALOG),
                            title = "使用原生 Miuix 弹窗",
                            subtitle = "用于播放器、动态、用户空间等页面；关闭后使用 Material 3 弹窗",
                            checked = nativeMiuixPlayerPopups,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    PlayerSettingsStore.setNativeMiuixPlayerPopups(context, enabled)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSBlue,
                        )
                        AppPreferenceDivider()
		                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HARDWARE_DECODER),
                            title = "启用硬件解码",
                            subtitle = "推荐保持开启；只有遇到绿屏或无法播放时再尝试关闭，关闭后更耗电",
                            checked = state.hwDecode,
                            onCheckedChange = {
                                viewModel.toggleHwDecode(it)
                                //  [埋点] 设置变更追踪
                                com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange("hw_decode", it.toString())
                            },
                            iconTint = iOSGreen
                        )
                        AppPreferenceDivider()
                        SettingsSingleChoicePreference(
                            title = "优先使用：${resolveSelectionLabel(codecOptions, videoCodecPreference, fallbackLabel = "AVC")}",
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
                        AppPreferenceDivider()
                        SettingsSingleChoicePreference(
                            title = "无法播放时改用：${resolveSelectionLabel(codecOptions, videoSecondCodecPreference, fallbackLabel = "HEVC")}",
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

            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("播放速度")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    AppPreferenceGroup {
		                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.REMEMBER_PLAYBACK_SPEED),
                            title = "记忆上次播放速度",
                            subtitle = if (rememberLastPlaybackSpeed) {
                                "新视频将优先使用你最后一次手动设置的速度"
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
                            iconTint = com.android.purebilibili.core.theme.iOSBlue
                        )
                        AppPreferenceDivider()
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.LONG_PRESS_SPEED_HINT),
                            title = "隐藏长按倍速提示",
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
                            iconTint = com.android.purebilibili.core.theme.iOSBlue,
                        )
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
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "长按倍速浮层与提示文字的整体缩放",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${(hintScale * 100f).roundToInt()}%",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 13.sp,
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
                                        text = "倍速提示透明度",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "长按倍速浮层与全局提示的背景不透明度",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${(hintAlpha * 100f).roundToInt()}%",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 13.sp,
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
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DefaultPlaybackSpeedPreferenceControl(
                                currentSpeed = defaultPlaybackSpeed,
                                onSpeedChange = { speed ->
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setDefaultPlaybackSpeed(context, speed)
                                    }
                                },
                                title = "默认播放速度",
                                subtitle = "新视频默认使用此速度；开启“记忆上次速度”后以后者为准",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            //  小窗播放
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("小窗与后台")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val pipNoDanmakuEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getPipNoDanmakuEnabled(context)
                        .collectAsStateWithLifecycle(initialValue = false)
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
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP, "小窗+PiP")
                    )

                    AppPreferenceGroup {
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.STOP_ON_EXIT),
                            title = "离开播放页后停止",
                            subtitle = "开启后，返回其他页面时立即停止，也不会进入小窗或后台播放",
                            checked = stopPlaybackOnExit,
                            onCheckedChange = {
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setStopPlaybackOnExit(context, it)
                                }
                            },
                            iconTint = iOSOrange
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.BACKGROUND_PLAYBACK),
                            title = "后台播放",
                            subtitle = if (backgroundPlaybackEnabled) {
                                "已开启：离开应用或锁屏时仍可继续播放"
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
                            iconTint = iOSGreen
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_FOCUS),
                            title = "播放时暂停其他应用的声音",
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
                            iconTint = iOSTeal
                        )
                        AppPreferenceDivider()
                        SettingsSingleChoicePreference(
                            title = "离开播放页后的方式：${if (modeControlsEnabled) miniPlayerMode.label else "暂不生效"}",
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
                        AppPreferenceDivider()
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYLIST_AUTO_CONTINUE),
                            title = "视频小横条",
                            subtitle = if (audioNowPlayingBarEnabled) {
                                "进入视频详情后返回首页等页面时显示，即使未播放也保留当前视频入口"
                            } else {
                                "关闭后返回首页等页面时不显示视频小横条"
                            },
                            checked = audioNowPlayingBarEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setAudioNowPlayingBarEnabled(context, it)
                                }
                            },
                            iconTint = iOSOrange
                        )

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
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    AppText(
                                        "画中画权限未开启",
                                        fontSize = 14.sp,
                                        color = warningTint
                                    )
                                    AppText(
                                        "点击前往系统设置开启",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                AppIcon(
                                    com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_right_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PIP_DANMAKU),
                            title = "小窗/画中画不加载弹幕",
                            subtitle = if (!backgroundPlaybackEnabled) {
                                "开启后台播放后，小窗和画中画相关设置才会生效"
                            } else if (miniPlayerMode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF) {
                                if (pipNoDanmakuEnabled) "已开启：小窗/画中画中不显示弹幕" else "关闭后：小窗/画中画中也会显示弹幕"
                            } else {
                                "选择小窗或画中画模式后生效"
                            },
                            checked = pipNoDanmakuEnabled,
                            onCheckedChange = {
                                if (!pipDanmakuToggleEnabled) {
                                    return@AppSwitchPreference
                                }
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setPipNoDanmakuEnabled(context, it)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSPurple
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_MODE_PIP),
                            title = "听视频离开时自动进入画中画",
                            subtitle = if (audioModeAutoPipToggleEnabled) {
                                if (audioModeAutoPipEnabled) {
                                    "已开启：回到桌面或使用离开手势时会自动进入系统画中画"
                                } else {
                                    "关闭后仅保留听视频页内的画中画按钮"
                                }
                            } else {
                                "仅支持系统画中画的模式下生效"
                            },
                            checked = audioModeAutoPipEnabled,
                            onCheckedChange = {
                                if (!audioModeAutoPipToggleEnabled) {
                                    return@AppSwitchPreference
                                }
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setAudioModeAutoPipEnabled(context, it)
                                }
                            },
                            iconTint = iOSTeal
                        )
                    }
                }
            }

            //  手势设置
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("手势控制")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceGroup {
                        AppSliderDialogPreference(
                            title = "手势灵敏度",
                            subtitle = "调整快进、音量和亮度手势的响应速度",
                            value = state.gestureSensitivity,
                            onValueChange = viewModel::setGestureSensitivity,
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_gesture_24),
                            iconTint = warningTint,
                            valueFormatter = { value -> "${(value * 100).toInt()}%" },
                        )
                    }
                }
            }

            //  调试选项
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("诊断")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    AppPreferenceGroup {
	                        SettingsSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_STATS),
                            title = "屏幕显示播放状态",
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
                            iconTint = iOSSystemGray,
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_DIAGNOSTIC_LOGS),
                            title = "播放器诊断日志",
                            subtitle = "遇到黑屏、卡顿或无响应时记录排查信息；反馈问题后可关闭",
                            checked = playerDiagnosticLoggingEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setPlayerDiagnosticLoggingEnabled(context, it)
                                }
                            },
                            iconTint = iOSOrange
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.SEGMENT_LOADING_COMPATIBILITY),
                            title = "分段加载兼容模式（实验性）",
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
                            iconTint = iOSTeal
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.QUALITY_WARNING),
                            title = "画质降档诊断弹窗",
                            subtitle = "只在画质切换失败或权限异常时提示，不会因视频没有更高画质而打断播放",
                            checked = qualitySwitchFailureDialogEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SettingsManager.setQualitySwitchFailureDialogEnabled(context, enabled)
                                }
                            },
                            iconTint = iOSOrange
                        )
                        AppPreferenceDivider()
	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.QUALITY_WARNING_ONCE),
                            title = "降档弹窗仅提示一次",
                            subtitle = if (qualitySwitchFailureDialogEnabled) {
                                "首次弹出后不再重复打断播放；关闭本项会重置提示记录"
                            } else {
                                "开启画质降档诊断弹窗后生效"
                            },
                            checked = qualitySwitchFailureDialogOnceEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SettingsManager.setQualitySwitchFailureDialogOnceEnabled(context, enabled)
                                }
                            },
                            iconTint = iOSTeal
                        )
                    }
                }
            }

            //  网络与画质
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("网络与画质")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val wifiQuality by com.android.purebilibili.core.store.SettingsManager
                        .getWifiQuality(context).collectAsStateWithLifecycle(initialValue = 80)
                    val mobileQuality by com.android.purebilibili.core.store.SettingsManager
                        .getMobileQuality(context).collectAsStateWithLifecycle(initialValue = 64)
                    val defaultAudioQuality by PlayerSettingsStore
                        .getDefaultAudioQuality(context)
                        .collectAsStateWithLifecycle(
                            initialValue = DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
                        )
                    val autoHighestQualityEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getAutoHighestQuality(context).collectAsStateWithLifecycle(initialValue = false)
                    val directedTrafficEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getBiliDirectedTrafficEnabled(context).collectAsStateWithLifecycle(initialValue = false)
                    val isLoggedIn = com.android.purebilibili.data.repository.VideoRepository.isPlaybackLoggedIn()
                    val isVip = com.android.purebilibili.data.repository.VideoRepository.isPlaybackVip()

                    val qualityOptions = resolveDefaultPlaybackQualityOptions()
                    val audioQualityOptions = resolveDefaultAudioQualityOptions()
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
                        SettingsSingleChoicePreference(
                            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_router_24),
                            title = "CDN 设置：${playbackCdnPreference.displayName}",
                            subtitle = "新视频优先使用此线路；不可用时自动回退主/备用 URL",
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
                            iconTint = iOSTeal,
                        )

                        AppPreferenceDivider()

		                        AppSwitchPreference(
		                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DIRECTED_TRAFFIC),
                            title = "B站定向流量支持",
                            subtitle = if (directedTrafficEnabled) {
                                "移动数据下优先使用应用内播放链路（实验性）"
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
                            iconTint = iOSTeal
                        )

                        AppPreferenceDivider()

	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_HIGHEST_QUALITY),
                            title = "自动最高画质",
                            subtitle = if (autoHighestQualityEnabled) {
                                "每个视频都会自动选择当前账号和设备可播放的最高画质"
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
                            iconTint = iOSOrange
                        )

                        AppPreferenceDivider()

                        SettingsSingleChoicePreference(
                            title = "无线网络默认画质：${getQualityLabel(wifiQuality)}",
                            subtitle = if (autoHighestQualityEnabled) {
                                "已被自动最高画质覆盖；仅作为关闭自动最高后的无线网络偏好保留"
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

                        AppPreferenceDivider()

                        // 📉 读取省流量模式，用于显示提示
                        val dataSaverModeForHint by com.android.purebilibili.core.store.SettingsManager
                            .getDataSaverMode(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
                            )
                        val isDataSaverActive = dataSaverModeForHint != com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF
                        val effectiveQuality = resolveEffectiveMobileQuality(
                            rawMobileQuality = mobileQuality,
                            isDataSaverActive = isDataSaverActive
                        )
                        val effectiveQualityLabel = getQualityLabel(effectiveQuality)

                        SettingsSingleChoicePreference(
                            title = "移动网络默认画质：${getQualityLabel(mobileQuality)}",
                            subtitle = when {
                                autoHighestQualityEnabled ->
                                    "已被自动最高画质覆盖；仅作为关闭自动最高后的流量偏好保留"
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

                        AppPreferenceDivider()

                        SettingsSingleChoicePreference(
                            title = "默认音质：${getAudioQualityLabel(normalizedDefaultAudioQuality)}",
                            subtitle = if (
                                normalizedDefaultAudioQuality ==
                                DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
                            ) {
                                "新视频跟随播放器上次手动选择"
                            } else {
                                "具体默认音质优先于上次手动选择；当前视频仍可临时切换"
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

                        if (isDataSaverActive && mobileQuality > effectiveQuality) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(
                                    text = "省流量模式已限制为最高480P",
                                    fontSize = 11.sp,
                                    color = iOSGreen.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 📉 省流量模式
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("省流量")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val dataSaverMode by com.android.purebilibili.core.store.SettingsManager
                        .getDataSaverMode(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
                        )
                    val homeSettings by com.android.purebilibili.core.store.SettingsManager
                        .getHomeSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings()
                        )
                    val dataSaverModeOptions = listOf(
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF, "关闭"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY, "仅移动数据"),
                        AppSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.ALWAYS, "始终开启")
                    )

                    AppPreferenceGroup {
                        SettingsSingleChoicePreference(
                            title = "省流量模式：${dataSaverMode.label}",
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

                        AppPreferenceDivider()

	                        AppSwitchPreference(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.DATA_SAVER_COVER_QUALITY),
                            title = "省流量时降低首页封面清晰度",
                            subtitle = if (homeSettings.lowQualityHomeCoverInDataSaver) {
                                "开启后仅在省流量模式生效时加载低清晰度首页封面"
                            } else {
                                "默认始终加载高清首页封面"
                            },
                            checked = homeSettings.lowQualityHomeCoverInDataSaver,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setLowQualityHomeCoverInDataSaver(context, enabled)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSBlue
                        )

                        //  功能说明
                        AppPreferenceDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AppIcon(
                                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_info_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            AppText(
                                "省流量模式会禁用预加载、限制视频最高480P；首页封面是否降清晰度由上方开关决定。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            //  交互设置
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("互动与评论")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    PlaybackInteractionSettingsSection(
                        context = context,
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle("全屏与手势")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    PlaybackFullscreenGestureSettingsSection(context = context)
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
    viewModel: SettingsViewModel
) {
    val scope = rememberCoroutineScope()
    //  [新增] 自动播放下一个
    val autoPlayEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAutoPlay(context).collectAsStateWithLifecycle(initialValue = true)
    val externalPlaylistAutoContinueEnabled by com.android.purebilibili.core.store.SettingsManager
        .getExternalPlaylistAutoContinue(context).collectAsStateWithLifecycle(initialValue = true)
    val resumePlaybackPromptEnabled by com.android.purebilibili.core.store.SettingsManager
        .getResumePlaybackPromptEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val spacePlayedVideoLocatePromptEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSpacePlayedVideoLocatePromptEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val playbackCompletionBehavior by com.android.purebilibili.core.store.SettingsManager
        .getPlaybackCompletionBehavior(context)
        .collectAsStateWithLifecycle(initialValue = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC)
    val subtitleFeatureEnabled = isSubtitleFeatureEnabledForUser()
    val subtitleAutoPreference by com.android.purebilibili.core.store.SettingsManager
        .getSubtitleAutoPreference(context)
        .collectAsStateWithLifecycle(initialValue = SubtitleAutoPreference.OFF)
    val videoAiSummaryEntryEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoAiSummaryEntryEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val videoNoteEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoNoteEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val videoNoteDefaultCollapsed by com.android.purebilibili.core.store.SettingsManager
        .getVideoNoteDefaultCollapsed(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val videoInfoDefaultExpanded by com.android.purebilibili.core.store.SettingsManager
        .getVideoInfoDefaultExpanded(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val commentFraudDetectionEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCommentFraudDetectionEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val commentMemberDecorationsEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCommentMemberDecorationsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val subReplyLoadedCountEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSubReplyLoadedCountEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val imagePreviewLongPressSaveEnabled by com.android.purebilibili.core.store.SettingsManager
        .getImagePreviewLongPressSaveEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val imagePreview3dPageEnabled by com.android.purebilibili.core.store.SettingsManager
        .getImagePreview3dPageEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val commentCollapsedReplyPreviewLimit by com.android.purebilibili.core.store.SettingsManager
        .getCommentCollapsedReplyPreviewLimit(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager
                .DEFAULT_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT
        )
    val subtitlePreferenceDescription = when (subtitleAutoPreference) {
        SubtitleAutoPreference.OFF -> "默认关闭字幕"
        SubtitleAutoPreference.ON -> "默认开启（优先当前可用轨道）"
        SubtitleAutoPreference.WITHOUT_AI -> "仅自动启用非 AI 字幕"
        SubtitleAutoPreference.AUTO -> "静音时可自动启用 AI 字幕"
    }

    AppPreferenceGroup {
        // --- Click to Play ---
        val clickToPlayEnabled by com.android.purebilibili.core.store.SettingsManager
            .getClickToPlay(context).collectAsStateWithLifecycle(initialValue = true)

	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_PLAY_ON_OPEN),
            title = "进入视频自动播放",
            subtitle = if (clickToPlayEnabled) {
                "进入视频详情页时自动开始播放"
            } else {
                "关闭后进入视频详情页需手动播放"
            },
            checked = clickToPlayEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setClickToPlay(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.RESUME_PLAYBACK_PROMPT),
            title = "续播弹窗提示",
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
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.SPACE_PLAYED_VIDEO_LOCATE),
            title = "UP 主页看过视频定位提示",
            subtitle = if (spacePlayedVideoLocatePromptEnabled) {
                "每次从视频进入该 UP 主页时显示，可一键定位到对应投稿"
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
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
        //  [新增] 自动播放下一个视频
	        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_PLAY_NEXT),
            title = "自动播放下一个",
            subtitle = "分P/合集自动播放下一集，普通单视频播完暂停",
            checked = autoPlayEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setAutoPlay(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPurple
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYLIST_AUTO_CONTINUE),
            title = "列表/收藏夹连续播放",
            subtitle = "控制收藏夹、稍后再看、合集等列表播放完后是否继续下一条",
            checked = externalPlaylistAutoContinueEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setExternalPlaylistAutoContinue(context, it)
                }
            },
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
        val playbackOrderOptions = listOf(
            AppSegmentOption(PlaybackCompletionBehavior.STOP_AFTER_CURRENT, "暂停"),
            AppSegmentOption(PlaybackCompletionBehavior.PLAY_IN_ORDER, "顺序"),
            AppSegmentOption(PlaybackCompletionBehavior.REPEAT_ONE, "单循"),
            AppSegmentOption(PlaybackCompletionBehavior.LOOP_PLAYLIST, "列表循"),
            AppSegmentOption(PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC, "自动")
        )
        SettingsSingleChoicePreference(
            title = "视频播完后：${playbackCompletionBehavior.label}",
            subtitle = "“自动”会在单个视频结束后暂停，在分P或合集内继续下一集",
            options = playbackOrderOptions,
            selectedValue = playbackCompletionBehavior,
            onSelectionChange = { behavior ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setPlaybackCompletionBehavior(context, behavior)
                }
            }
        )
        if (subtitleFeatureEnabled) {
            AppPreferenceDivider()
            SettingsSingleChoicePreference(
                title = "自动启用字幕：${
                    when (subtitleAutoPreference) {
                        SubtitleAutoPreference.OFF -> "关闭"
                        SubtitleAutoPreference.ON -> "开启"
                        SubtitleAutoPreference.WITHOUT_AI -> "无 AI"
                        SubtitleAutoPreference.AUTO -> "自动"
                    }
                }",
                subtitle = subtitlePreferenceDescription,
                options = listOf(
                    AppSegmentOption(SubtitleAutoPreference.OFF, "关闭"),
                    AppSegmentOption(SubtitleAutoPreference.ON, "开启"),
                    AppSegmentOption(SubtitleAutoPreference.WITHOUT_AI, "无 AI"),
                    AppSegmentOption(SubtitleAutoPreference.AUTO, "自动")
                ),
                selectedValue = subtitleAutoPreference,
                onSelectionChange = { preference ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setSubtitleAutoPreference(context, preference)
                    }
                }
            )
            AppPreferenceDivider()
        }
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_DESCRIPTION),
            title = "默认展开视频简介",
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
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.AI_SUMMARY),
            title = "显示 AI 总结入口",
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
            iconTint = com.android.purebilibili.core.theme.iOSPurple
        )
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_NOTE),
            title = "显示视频笔记",
            subtitle = if (videoNoteEnabled) {
                "视频简介区展示笔记入口，并加载私有笔记和公开笔记"
            } else {
                "关闭后隐藏笔记入口，并跳过视频笔记接口"
            },
            checked = videoNoteEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setVideoNoteEnabled(context, it)
                }
            },
            iconTint = iOSTeal
        )
        if (videoNoteEnabled) {
            AppPreferenceDivider()
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.VIDEO_NOTE_COLLAPSE),
                title = "默认折叠视频笔记",
                subtitle = if (videoNoteDefaultCollapsed) {
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
                iconTint = com.android.purebilibili.core.theme.iOSBlue
            )
        }
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.LIKE_INTERACTION),
            title = "双击点赞",
            subtitle = "双击视频画面快捷点赞",
            checked = state.doubleTapLike,
            onCheckedChange = {
                viewModel.toggleDoubleTapLike(it)
                //  [埋点] 设置变更追踪
                com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange("double_tap_like", it.toString())
            },
            iconTint = com.android.purebilibili.core.theme.iOSPink
        )
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_SKIP_OP_ED),
            title = "自动跳过片头片尾",
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
            iconTint = com.android.purebilibili.core.theme.iOSOrange
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "评论回复预览：${commentCollapsedReplyPreviewLimit}条",
            subtitle = "收起楼中楼时保留的回复数量",
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
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.SUB_REPLY_LOADED_COUNT),
            title = "楼中楼已加载数量",
            subtitle = "在回复总数后显示当前已加载的条数",
            checked = subReplyLoadedCountEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setSubReplyLoadedCountEnabled(context, enabled)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_VISIBILITY_CHECK),
            title = "评论发送检测",
            subtitle = "发送成功后自动检查评论是否正常显示",
            checked = commentFraudDetectionEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setCommentFraudDetectionEnabled(context, enabled)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_DECORATION),
            title = "评论区个性装扮",
            subtitle = "显示粉丝牌、铭牌和装扮卡片；关闭后评论区更清爽",
            checked = commentMemberDecorationsEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setCommentMemberDecorationsEnabled(context, enabled)
                }
            },
            iconTint = iOSOrange
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.IMAGE_LONG_PRESS_ACTION),
            title = "图片长按操作",
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
            iconTint = com.android.purebilibili.core.theme.iOSGreen
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.IMAGE_3D_PAGE),
            title = "图片 3D 翻页",
            subtitle = if (imagePreview3dPageEnabled) {
                "普通图片浏览使用轻量透视翻页"
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
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
    }

}

@Composable
private fun PlaybackFullscreenGestureSettingsSection(
    context: Context
) {
    val scope = rememberCoroutineScope()
    val portraitPlayerCollapseMode by com.android.purebilibili.core.store.SettingsManager
        .getPortraitPlayerCollapseMode(context)
        .collectAsStateWithLifecycle(initialValue = PortraitPlayerCollapseMode.INTRO_ONLY)
    val videoDetailChromeScrollHideEnabled by SettingsManager
        .getVideoDetailChromeScrollHideEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val portraitSwipeToFullscreenEnabled by com.android.purebilibili.core.store.SettingsManager
        .getPortraitSwipeToFullscreenEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val directPortraitStoryEntry by com.android.purebilibili.core.store.SettingsManager
        .getAutoPortraitFullscreen(context).collectAsStateWithLifecycle(initialValue = false)
    val portraitOnlyVerticalRecommendations by com.android.purebilibili.core.store.SettingsManager
        .getPortraitOnlyVerticalRecommendations(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val launchToPortraitFeedOnStartup by com.android.purebilibili.core.store.SettingsManager
        .getLaunchToPortraitFeedOnStartup(context).collectAsStateWithLifecycle(initialValue = false)
    val centerSwipeToFullscreenEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCenterSwipeToFullscreenEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val slideVolumeBrightnessEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSlideVolumeBrightnessEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val setSystemBrightnessEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSetSystemBrightnessEnabled(context).collectAsStateWithLifecycle(initialValue = false)
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
    val inlineSwipeSeekSeconds by com.android.purebilibili.core.store.SettingsManager
        .getInlineSwipeSeekSeconds(context).collectAsStateWithLifecycle(initialValue = 30)
    val fullscreenSwipeSeekEnabled by com.android.purebilibili.core.store.SettingsManager
        .getFullscreenSwipeSeekEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val fullscreenSwipeSeekSeconds by com.android.purebilibili.core.store.SettingsManager
        .getFullscreenSwipeSeekSeconds(context).collectAsStateWithLifecycle(initialValue = 15)
    val doubleTapSeekEnabled by com.android.purebilibili.core.store.SettingsManager
        .getDoubleTapSeekEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val seekForwardSeconds by com.android.purebilibili.core.store.SettingsManager
        .getSeekForwardSeconds(context).collectAsStateWithLifecycle(initialValue = 10)
    val seekBackwardSeconds by com.android.purebilibili.core.store.SettingsManager
        .getSeekBackwardSeconds(context).collectAsStateWithLifecycle(initialValue = 10)
    val hideInteractiveCommandDanmaku by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuHideInteractiveCommands(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val danmakuCloudSyncEnabled by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuCloudSyncEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    if (showSystemBrightnessPermissionDialog) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showSystemBrightnessPermissionDialog = false },
            title = {
                AppText(
                    "允许调节系统亮度",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                AppText(
                    "开启后，播放器亮度手势会先调节当前窗口亮度，并同步系统亮度。Android 需要你在系统设置中单独允许 BiliPai 修改系统设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
    AppPreferenceGroup {
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
                        text = "双击跳转",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = if (doubleTapSeekEnabled) {
                            "双击右侧快进 ${seekForwardSeconds} 秒，双击左侧后退 ${seekBackwardSeconds} 秒"
                        } else {
                            "已关闭：双击画面只切换播放/暂停，不再快进或后退"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                SettingsSingleChoicePreference(
                    title = "快进秒数（双击右侧）：${seekForwardSeconds} 秒",
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
                AppPreferenceDivider()
                SettingsSingleChoicePreference(
                    title = "后退秒数（双击左侧）：${seekBackwardSeconds} 秒",
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
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTIVE_COMMANDS),
            title = "隐藏视频内互动提示",
            subtitle = if (hideInteractiveCommandDanmaku) {
                "已开启：不显示关注、一键三连、UP 提示和投票等视频内互动提示"
            } else {
                "关闭后：播放时仍显示关注、一键三连、UP 提示和投票等视频内互动提示"
            },
            checked = hideInteractiveCommandDanmaku,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setDanmakuHideInteractiveCommands(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPink
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.DANMAKU_CLOUD_SYNC),
            title = "同步弹幕设置到账号",
            subtitle = com.android.purebilibili.feature.video.danmaku
                .resolveDanmakuCloudSyncToggleSubtitle(danmakuCloudSyncEnabled),
            checked = danmakuCloudSyncEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setDanmakuCloudSyncEnabled(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPurple
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "评论上滑缩小播放器：${portraitPlayerCollapseMode.label}",
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

        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_HEADER_COLLAPSE),
            title = "详情页控件随滚动隐藏",
            subtitle = if (videoDetailChromeScrollHideEnabled) {
                "评论页下滑时隐藏顶部标签、发弹幕和排序控件，回到顶部后显示"
            } else {
                "关闭后详情页顶部控件跟随内容移动，不再自动折叠"
            },
            checked = videoDetailChromeScrollHideEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    SettingsManager.setVideoDetailChromeScrollHideEnabled(context, enabled)
                }
            },
            iconTint = iOSTeal,
        )

        val pauseOnPlayerCollapseEnabled by com.android.purebilibili.core.store.SettingsManager
            .getPauseOnPlayerCollapseEnabled(context)
            .collectAsStateWithLifecycle(initialValue = true)
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_COLLAPSE_PAUSE),
            title = "缩小后自动暂停",
            subtitle = if (pauseOnPlayerCollapseEnabled) {
                "上滑缩小播放器浏览相关推荐时自动暂停，展开后若为自动暂停则恢复播放"
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
            iconTint = iOSTeal
        )

        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_SWIPE_FULLSCREEN),
            title = "竖屏上滑进入全屏",
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
            iconTint = iOSTeal
        )

        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_STORY_ENTRY),
            title = "竖屏视频直达刷视频模式",
            subtitle = if (directPortraitStoryEntry) {
                "开启：任意入口点竖屏视频直接进竖滑全屏（可经卡片放大动画）；默认关闭时先进详情内联竖屏"
            } else {
                "关闭（默认）：竖屏视频先进详情页内联播放，可再点「竖屏」进刷视频"
            },
            checked = directPortraitStoryEntry,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setAutoPortraitFullscreen(context, it)
                }
            },
            iconTint = iOSTeal
        )

        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_STORY_ENTRY),
            title = "竖屏刷视频仅推荐真竖屏（Beta）",
            subtitle = if (portraitOnlyVerticalRecommendations) {
                "开启后过滤横屏视频，仅保留实际画面为竖屏的推荐"
            } else {
                "关闭后竖屏刷视频允许横竖屏混合推荐"
            },
            checked = portraitOnlyVerticalRecommendations,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setPortraitOnlyVerticalRecommendations(context, it)
                }
            },
            iconTint = iOSTeal
        )

        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.STARTUP_PORTRAIT_FEED),
            title = "启动时进入竖屏视频流",
            subtitle = if (launchToPortraitFeedOnStartup) {
                "打开应用后直接进入竖屏刷视频流（独立于「直达」开关）"
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
            iconTint = iOSTeal
        )

        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.CENTER_SWIPE_FULLSCREEN),
            title = "中部滑动切换全屏",
            subtitle = if (centerSwipeToFullscreenEnabled) {
                "开启后：播放器中部纵向滑动可切换进入/退出全屏（受手势反向影响）"
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
            iconTint = com.android.purebilibili.core.theme.iOSPurple
        )

        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.SLIDE_VOLUME_BRIGHTNESS),
            title = "左右侧滑动调节亮度/音量",
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
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.SYSTEM_BRIGHTNESS),
            title = "调节系统亮度",
            subtitle = when {
                !slideVolumeBrightnessEnabled -> "依赖“左右侧滑动调节亮度/音量”开关"
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
            iconTint = iOSOrange
        )

        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "非全屏滑动调进度范围",
            subtitle = "左右拖动约半屏达到 ${inlineSwipeSeekSeconds} 秒上限，数值越小越精确",
            options = listOf(
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

        AppPreferenceDivider()
        AppSwitchPreference(
            title = "横屏滑动调进度",
            subtitle = if (fullscreenSwipeSeekEnabled) {
                "已开启，当前范围 ${fullscreenSwipeSeekSeconds} 秒"
            } else {
                "已关闭，重新开启后继续使用 ${fullscreenSwipeSeekSeconds} 秒范围"
            },
            checked = fullscreenSwipeSeekEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setFullscreenSwipeSeekEnabled(context, it)
                }
            },
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "横屏滑动调进度范围",
            subtitle = "左右拖动约半屏达到的秒数上限，数值越小越精确",
            options = listOf(
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
        AppPreferenceDivider()
        val autoRotateEnabled by com.android.purebilibili.core.store.SettingsManager
            .getAutoRotateEnabled(context).collectAsStateWithLifecycle(initialValue = false)
        val fullscreenGestureReverse by com.android.purebilibili.core.store.SettingsManager
            .getFullscreenGestureReverse(context).collectAsStateWithLifecycle(initialValue = false)
        val autoEnterFullscreen by com.android.purebilibili.core.store.SettingsManager
            .getAutoEnterFullscreen(context).collectAsStateWithLifecycle(initialValue = false)
        val autoExitFullscreen by com.android.purebilibili.core.store.SettingsManager
            .getAutoExitFullscreen(context).collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenLockButton by com.android.purebilibili.core.store.SettingsManager
            .getShowFullscreenLockButton(context).collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenScreenshotButton by com.android.purebilibili.core.store.SettingsManager
            .getShowFullscreenScreenshotButton(context).collectAsStateWithLifecycle(initialValue = true)
        val appGestureScreenshotEnabled by SettingsManager
            .getAppGestureScreenshotEnabled(context).collectAsStateWithLifecycle(initialValue = false)
        val appScreenshotGestureMode by SettingsManager
            .getAppScreenshotGestureMode(context)
            .collectAsStateWithLifecycle(initialValue = AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS)
        val appScreenshotCaptureMode by SettingsManager
            .getAppScreenshotCaptureMode(context)
            .collectAsStateWithLifecycle(initialValue = AppScreenshotCaptureMode.FULL_WINDOW)
        val showFullscreenBatteryLevel by com.android.purebilibili.core.store.SettingsManager
            .getShowFullscreenBatteryLevel(context).collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenTime by com.android.purebilibili.core.store.SettingsManager
            .getShowFullscreenTime(context).collectAsStateWithLifecycle(initialValue = true)
        val showFullscreenActionItems by com.android.purebilibili.core.store.SettingsManager
            .getShowFullscreenActionItems(context).collectAsStateWithLifecycle(initialValue = true)
        val showOnlineCount by com.android.purebilibili.core.store.SettingsManager
            .getShowOnlineCount(context).collectAsStateWithLifecycle(initialValue = false)
        val bottomProgressBehavior by com.android.purebilibili.core.store.SettingsManager
            .getBottomProgressBehavior(context)
            .collectAsStateWithLifecycle(initialValue = BottomProgressBehavior.ALWAYS_HIDE)
        val progressPeakDanmakuEnabled by SettingsManager
            .getProgressPeakDanmakuEnabled(context)
            .collectAsStateWithLifecycle(initialValue = false)
        val playerControlVisibility by SettingsManager
            .getPlayerControlVisibilitySettings(context)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.PlayerControlVisibilitySettings()
            )
        val playerProgressPlacement by SettingsManager
            .getPlayerProgressPlacement(context)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.PlayerProgressPlacement.ABOVE_CONTROLS
            )
        val isLargeScreenDevice = context.resources.configuration.smallestScreenWidthDp >= 600
        val horizontalAdaptationEnabled by com.android.purebilibili.core.store.SettingsManager
            .getHorizontalAdaptationEnabled(context)
            .collectAsStateWithLifecycle(initialValue = isLargeScreenDevice)
        val immersiveVideoPageStatusBar by com.android.purebilibili.core.store.SettingsManager
            .getHideVideoPageStatusBar(context)
            .collectAsStateWithLifecycle(initialValue = false)
        val tabletCommentPanelWidthPreset by com.android.purebilibili.core.store.SettingsManager
            .getTabletCommentPanelWidthPreset(context)
            .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.TabletCommentPanelWidthPreset.STANDARD)
        val fullscreenMode by com.android.purebilibili.core.store.SettingsManager
            .getFullscreenMode(context)
            .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.FullscreenMode.AUTO)
        val fullscreenAspectRatio by com.android.purebilibili.core.store.SettingsManager
            .getFullscreenAspectRatio(context)
            .collectAsStateWithLifecycle(initialValue = FullscreenAspectRatio.FIT)
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
            "启用横屏布局和横屏逻辑（平板/折叠屏建议开启）"
        } else {
            "主要用于平板/折叠屏，当前设备触发场景可能较少"
        }

	        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_ORIENTATION),
            title = "自动横竖屏切换",
            subtitle = autoRotateSubtitle,
            checked = autoRotateEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setAutoRotateEnabled(context, it)
                }
            },
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.HORIZONTAL_ADAPTATION),
            title = "横屏适配",
            subtitle = horizontalAdaptationSubtitle,
            checked = horizontalAdaptationEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setHorizontalAdaptationEnabled(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.CAST_BUTTON),
            title = "显示投屏按钮",
            subtitle = "同时控制半屏、横屏全屏和竖屏全屏的投屏入口",
            checked = playerControlVisibility.showCastButton,
            onCheckedChange = {
                scope.launch {
                    SettingsManager.setShowPlayerCastButton(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.FOLLOW_BUTTON),
            title = "显示关注按钮",
            subtitle = "关闭后保留 UP 主头像、名称和主页入口",
            checked = playerControlVisibility.showFollowButton,
            onCheckedChange = {
                scope.launch {
                    SettingsManager.setShowVideoFollowButton(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPink
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "平板评论区宽度：${tabletCommentPanelWidthPreset.label}",
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
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "默认全屏方向：${fullscreenMode.label}",
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
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "固定全屏比例：${fullscreenAspectRatio.label}",
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
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_GESTURE_REVERSE),
            title = "全屏手势反向",
            subtitle = "默认上滑进全屏、下滑退全屏；开启后方向反转",
            checked = fullscreenGestureReverse,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setFullscreenGestureReverse(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPurple
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.IMMERSIVE_STATUS_BAR),
            title = "播放页沉浸状态栏",
            subtitle = if (immersiveVideoPageStatusBar) {
                "状态栏保留；播放器顶部使用实时模糊背景，系统图标清晰可见，底部手势条保持显示"
            } else {
                "状态栏保留；播放器顶部使用纯黑背景，系统图标清晰可见，底部手势条保持显示"
            },
            checked = immersiveVideoPageStatusBar,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setHideVideoPageStatusBar(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSTeal
        )
        AppPreferenceDivider()
        val portraitLetterboxAmbientHaze by com.android.purebilibili.core.store.SettingsManager
            .getPortraitLetterboxAmbientHaze(context)
            .collectAsStateWithLifecycle(initialValue = true)
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_AMBIENT_HAZE),
            title = "竖屏黑边动态模糊",
            subtitle = if (portraitLetterboxAmbientHaze) {
                "竖屏播放横屏视频时，上下黑边实时采样画面做毛玻璃模糊（默认开启）"
            } else {
                "竖屏播放横屏视频时，上下黑边保持纯黑"
            },
            checked = portraitLetterboxAmbientHaze,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setPortraitLetterboxAmbientHaze(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSTeal
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_ENTER_FULLSCREEN),
            title = "自动进入全屏",
            subtitle = "视频开始播放后自动切到全屏",
            checked = autoEnterFullscreen,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setAutoEnterFullscreen(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSGreen
        )
        AppPreferenceDivider()
        val autoExitFullscreenMode by com.android.purebilibili.core.store.SettingsManager
            .getAutoExitFullscreenMode(context)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS
            )
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_EXIT_FULLSCREEN),
            title = "自动退出全屏",
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
            iconTint = iOSOrange
        )
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
                        "当前P",
                    ),
                    AppSegmentOption(
                        com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS,
                        "全部完",
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
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_LOCK),
            title = "全屏显示锁定按钮",
            subtitle = "控制层中显示防误触锁定按钮",
            checked = showFullscreenLockButton,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setShowFullscreenLockButton(context, it)
                }
            },
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_SCREENSHOT),
            title = "全屏显示截图按钮",
            subtitle = "控制层中显示快速截图入口",
            checked = showFullscreenScreenshotButton,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setShowFullscreenScreenshotButton(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.CLEAN_SCREENSHOT),
            title = "应用内干净截图",
            subtitle = "在 BiliPai 前台通过应用内手势导出当前窗口 PNG",
            checked = appGestureScreenshotEnabled,
            onCheckedChange = {
                scope.launch {
                    SettingsManager.setAppGestureScreenshotEnabled(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPurple
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "截图触发方式：${appScreenshotGestureMode.label}",
            subtitle = appScreenshotGestureMode.description,
            options = resolveAppScreenshotGestureModeSegmentOptions(),
            selectedValue = appScreenshotGestureMode,
            onSelectionChange = { mode ->
                scope.launch {
                    SettingsManager.setAppScreenshotGestureMode(context, mode)
                }
            }
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "截图范围：${appScreenshotCaptureMode.label}",
            subtitle = appScreenshotCaptureMode.description,
            options = resolveAppScreenshotCaptureModeSegmentOptions(),
            selectedValue = appScreenshotCaptureMode,
            onSelectionChange = { mode ->
                scope.launch {
                    SettingsManager.setAppScreenshotCaptureMode(context, mode)
                }
            }
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.BATTERY_STATUS),
            title = "全屏显示电量",
            subtitle = "在横屏左上角展示电池图标和电量百分比",
            checked = showFullscreenBatteryLevel,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setShowFullscreenBatteryLevel(context, it)
                }
            },
            iconTint = iOSGreen
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.TIME_STATUS),
            title = "全屏显示时间",
            subtitle = "在横屏左上角单独展示当前时间",
            checked = showFullscreenTime,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setShowFullscreenTime(context, it)
                }
            },
            iconTint = iOSTeal
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_ACTIONS),
            title = "全屏显示互动按钮",
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
            iconTint = com.android.purebilibili.core.theme.iOSPink
        )
        AppPreferenceDivider()
	        AppSwitchPreference(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.ONLINE_COUNT),
            title = "卡片与视频页观看人数",
            subtitle = if (showOnlineCount) {
                "首页、搜索等视频卡片和视频页显示“xx人正在看”"
            } else {
                "关闭后隐藏卡片和视频页的观看人数展示"
            },
            checked = showOnlineCount,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setShowOnlineCount(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSBlue
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "底部进度条展示：${bottomProgressBehavior.label}",
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
        AppPreferenceDivider()
        AppSwitchPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESS_PEAK_DANMAKU),
            title = "进度条峰值弹幕",
            subtitle = if (progressPeakDanmakuEnabled) {
                "在进度条上显示弹幕热度峰值曲线"
            } else {
                "关闭后不显示弹幕热度峰值曲线"
            },
            checked = progressPeakDanmakuEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    SettingsManager.setProgressPeakDanmakuEnabled(context, enabled)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSPurple,
        )
        AppPreferenceDivider()
        SettingsSingleChoicePreference(
            title = "控制栏进度条位置：${playerProgressPlacement.label}",
            subtitle = "可将可拖动进度条放到控制按钮下方的视频最底部",
            options = listOf(
                AppSegmentOption(
                    com.android.purebilibili.core.store.PlayerProgressPlacement.ABOVE_CONTROLS,
                    "控制栏上方"
                ),
                AppSegmentOption(
                    com.android.purebilibili.core.store.PlayerProgressPlacement.BOTTOM_EDGE,
                    "视频最底部"
                )
            ),
            selectedValue = playerProgressPlacement,
            onSelectionChange = { placement ->
                scope.launch {
                    SettingsManager.setPlayerProgressPlacement(context, placement)
                }
            }
        )
    }

}
