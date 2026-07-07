// 文件路径: feature/settings/PlaybackSettingsScreen.kt
package com.android.purebilibili.feature.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.store.DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
import com.android.purebilibili.core.store.DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED
import com.android.purebilibili.core.store.DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED
import com.android.purebilibili.core.store.SettingsManager
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
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.feature.screenshot.AppScreenshotCaptureMode
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureMode
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = screenTitle,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                colors = settingsSubpageTopAppBarColors()
            )
        },
        containerColor = settingsSubpageContainerColor(),
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides state.isLiquidGlassEnabled) {
            Box(modifier = Modifier.padding(padding)) {
                PlaybackSettingsContent(viewModel = viewModel, state = state)
            }
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
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
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


    var isStatsEnabled by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }
    var showPipPermissionDialog by remember { mutableStateOf(false) }

    // 获取动态圆角用于统一风格
    // 注意：这里需要导入 LocalCornerRadiusScale，如果该文件没有导入，可能需要添加。
    // 假设 iOSCornerRadius 和 LocalCornerRadiusScale 未在此文件导入，先使用硬编码或尝试导入
    // 为了稳妥，这里先检查导入。原文件没有导入这些。
    // 但为了保持原样，我先不做动态圆角修改，或者之后再做。

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
    val playerDiagnosticLoggingEnabled by com.android.purebilibili.core.store.SettingsManager
        .getPlayerDiagnosticLoggingEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED)
    val dashSegmentRequestsEnabled by com.android.purebilibili.core.store.SettingsManager
        .getDashSegmentRequestsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
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
    val videoCodecPreference by com.android.purebilibili.core.store.SettingsManager
        .getVideoCodec(context).collectAsStateWithLifecycle(initialValue = "hev1")
    val videoSecondCodecPreference by com.android.purebilibili.core.store.SettingsManager
        .getVideoSecondCodec(context).collectAsStateWithLifecycle(initialValue = "avc1")

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
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { showPipPermissionDialog = false },
            title = { Text("权限申请", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("检测到未开启「画中画」权限。请在设置中开启该权限，否则无法使用小窗播放。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(
                    onClick = {
                        gotoPipSettings()
                        showPipPermissionDialog = false
                    }
                ) { Text("去设置") }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = { showPipPermissionDialog = false }) {
                    Text("暂不开启", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    EntranceGroup {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {

            //  解码设置
            //  解码设置
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("解码")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val codecOptions = listOf(
                        PlaybackSegmentOption("avc1", "AVC"),
                        PlaybackSegmentOption("hev1", "HEVC"),
                        PlaybackSegmentOption("av01", "AV1")
                    )
                    fun codecDescription(codec: String): String = when (codec) {
                        "avc1" -> "兼容性最佳"
                        "hev1" -> "推荐，画质与体积更平衡"
                        "av01" -> "高压缩，设备要求更高"
                        else -> "未知"
                    }
                    IOSGroup {
                        IOSSwitchItem(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HARDWARE_DECODER),
                            title = "启用硬件解码",
                            subtitle = "关闭后可尝试规避模拟器或特定设备绿屏，但可能更耗电/更卡顿",
                            checked = state.hwDecode,
                            onCheckedChange = {
                                viewModel.toggleHwDecode(it)
                                //  [埋点] 设置变更追踪
                                com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange("hw_decode", it.toString())
                            },
                            iconTint = iOSGreen
                        )
                        IOSDivider()
                        IOSSlidingSegmentedSetting(
                            title = "首选编码：${resolveSelectionLabel(codecOptions, videoCodecPreference, fallbackLabel = "AVC")}",
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
                        IOSDivider()
                        IOSSlidingSegmentedSetting(
                            title = "次选编码：${resolveSelectionLabel(codecOptions, videoSecondCodecPreference, fallbackLabel = "HEVC")}",
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
                    IOSSectionTitle("播放速度")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    IOSGroup {
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK_SPEED),
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
                        IOSDivider()
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
                                subtitle = "拖动滑杆自定义，常用档位可一键选择",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            //  小窗播放
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("小窗与后台")
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
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF, "默认"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY, "小窗"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP, "画中画"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP, "小窗+PiP")
                    )

                    IOSGroup {
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.STOP_ON_EXIT),
                            title = "离开播放页后停止",
                            subtitle = "不进入小窗/画中画，也不保留后台播放",
                            checked = stopPlaybackOnExit,
                            onCheckedChange = {
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setStopPlaybackOnExit(context, it)
                                }
                            },
                            iconTint = iOSOrange
                        )
                        IOSDivider()
	                        IOSSwitchItem(
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
                        IOSDivider()
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_FOCUS),
                            title = "占用音频焦点",
                            subtitle = if (audioFocusEnabled) {
                                "已开启：会优先接管系统媒体音频焦点"
                            } else {
                                "关闭后可以与其它 APP 同时播放"
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
                        IOSDivider()
                        IOSSlidingSegmentedSetting(
                            title = "后台播放模式：${if (modeControlsEnabled) miniPlayerMode.label else "已覆盖"}",
                            subtitle = if (stopPlaybackOnExit) {
                                "已由“离开播放页后停止”覆盖，后台模式暂不生效"
                            } else if (!backgroundPlaybackEnabled) {
                                "已关闭“后台播放”，后台模式暂不生效"
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

                        //  权限提示（仅当选择支持系统 PiP 的模式且无权限时显示）
                        if (modeControlsEnabled &&
                            miniPlayerMode.supportsSystemPip
                            && !checkPipPermission()) {
                            IOSDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPipPermissionDialog = true }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    CupertinoIcons.Default.ExclamationmarkTriangle,
                                    contentDescription = null,
                                    tint = warningTint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "画中画权限未开启",
                                        fontSize = 14.sp,
                                        color = warningTint
                                    )
                                    Text(
                                        "点击前往系统设置开启",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                Icon(
                                    CupertinoIcons.Default.ChevronForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IOSDivider()
	                        IOSSwitchItem(
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
                                    return@IOSSwitchItem
                                }
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setPipNoDanmakuEnabled(context, it)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSPurple
                        )
                        IOSDivider()
	                        IOSSwitchItem(
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
                                    return@IOSSwitchItem
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
                    IOSSectionTitle("手势控制")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    CupertinoIcons.Default.HandTap,
                                    contentDescription = null,
                                    tint = warningTint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "手势灵敏度",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "调整快进/音量/亮度手势响应速度",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${(state.gestureSensitivity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "较慢",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                //  iOS 风格滑块
                                io.github.alexzhirkevich.cupertino.CupertinoSlider(
                                    value = state.gestureSensitivity,
                                    onValueChange = { viewModel.setGestureSensitivity(it) },
                                    valueRange = 0.5f..2.0f,
                                    steps = 5,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text(
                                    "较快",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            //  调试选项
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("诊断")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSGroup {
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_STATS),
                            title = "详细统计信息",
                            subtitle = "显示编解码、码率等极客信息",
                            checked = isStatsEnabled,
                            onCheckedChange = {
                                isStatsEnabled = it
                                prefs.edit().putBoolean("show_stats", it).apply()
                            },
                            iconTint = iOSSystemGray
                        )
                        IOSDivider()
                        val scope = rememberCoroutineScope()
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_DIAGNOSTIC_LOGS),
                            title = "播放器诊断日志",
                            subtitle = "记录黑屏、卡顿、点击无响应等播放器诊断信息",
                            checked = playerDiagnosticLoggingEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setPlayerDiagnosticLoggingEnabled(context, it)
                                }
                            },
                            iconTint = iOSOrange
                        )
                        IOSDivider()
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_DIAGNOSTICS),
                            title = "DASH 分段请求",
                            subtitle = if (dashSegmentRequestsEnabled) {
                                "使用官方 DASH 播放路径请求初始化段和索引段；异常时可关闭回退旧路径"
                            } else {
                                "已关闭：使用旧的分离音视频合并播放路径"
                            },
                            checked = dashSegmentRequestsEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    SettingsManager.setDashSegmentRequestsEnabled(context, it)
                                }
                            },
                            iconTint = iOSTeal
                        )
                        IOSDivider()
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.QUALITY_WARNING_ONCE),
                            title = "画质降档诊断弹窗",
                            subtitle = "仅在明确切换失败、权限或接口异常时提示；视频本身无更高档不打断播放",
                            checked = qualitySwitchFailureDialogEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    SettingsManager.setQualitySwitchFailureDialogEnabled(context, enabled)
                                }
                            },
                            iconTint = iOSOrange
                        )
                        IOSDivider()
	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.QUALITY_WARNING),
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
                    IOSSectionTitle("网络与画质")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    val scope = rememberCoroutineScope()
                    val wifiQuality by com.android.purebilibili.core.store.SettingsManager
                        .getWifiQuality(context).collectAsStateWithLifecycle(initialValue = 80)
                    val mobileQuality by com.android.purebilibili.core.store.SettingsManager
                        .getMobileQuality(context).collectAsStateWithLifecycle(initialValue = 64)
                    val autoHighestQualityEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getAutoHighestQuality(context).collectAsStateWithLifecycle(initialValue = false)
                    val directedTrafficEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getBiliDirectedTrafficEnabled(context).collectAsStateWithLifecycle(initialValue = false)
                    val isLoggedIn = !TokenManager.sessDataCache.isNullOrEmpty() ||
                        !TokenManager.accessTokenCache.isNullOrEmpty()
                    val isVip = TokenManager.isVipCache

                    val qualityOptions = resolveDefaultPlaybackQualityOptions()

                    fun getQualityLabel(id: Int): String = resolveSelectionLabel(
                        options = qualityOptions,
                        selectedValue = id,
                        fallbackLabel = "720P"
                    )

                    IOSGroup {
	                        IOSSwitchItem(
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

                        IOSDivider()

	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_HIGHEST_QUALITY),
                            title = "自动最高画质",
                            subtitle = if (autoHighestQualityEnabled) {
                                "已开启，按每个视频实际最高可播档自动选择；没有 4K/HDR 时不会当作异常"
                            } else {
                                "全局开关，开启后覆盖下方默认画质；默认画质会作为关闭后的偏好保留"
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

                        IOSDivider()

                        IOSSlidingSegmentedSetting(
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

                        IOSDivider()

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

                        IOSSlidingSegmentedSetting(
                            title = "流量默认画质：${getQualityLabel(mobileQuality)}",
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

                        if (isDataSaverActive && mobileQuality > effectiveQuality) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
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
                    IOSSectionTitle("省流量")
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
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF, "关闭"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY, "仅移动数据"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.DataSaverMode.ALWAYS, "始终开启")
                    )

                    IOSGroup {
                        IOSSlidingSegmentedSetting(
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

                        IOSDivider()

	                        IOSSwitchItem(
	                            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_COVER_GLASS),
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
                        IOSDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                CupertinoIcons.Default.InfoCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
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
                    IOSSectionTitle("互动与评论")
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
                    IOSSectionTitle("全屏与手势")
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
    val imagePreviewLongPressSaveEnabled by com.android.purebilibili.core.store.SettingsManager
        .getImagePreviewLongPressSaveEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
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

    IOSGroup {
        // --- Click to Play ---
        val clickToPlayEnabled by com.android.purebilibili.core.store.SettingsManager
            .getClickToPlay(context).collectAsStateWithLifecycle(initialValue = true)

	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK_SPEED),
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
        IOSDivider()
        //  [新增] 自动播放下一个视频
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK),
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
        IOSDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val playbackOrderOptions = listOf(
                PlaybackSegmentOption(PlaybackCompletionBehavior.STOP_AFTER_CURRENT, "暂停"),
                PlaybackSegmentOption(PlaybackCompletionBehavior.PLAY_IN_ORDER, "顺序"),
                PlaybackSegmentOption(PlaybackCompletionBehavior.REPEAT_ONE, "单循"),
                PlaybackSegmentOption(PlaybackCompletionBehavior.LOOP_PLAYLIST, "列表循"),
                PlaybackSegmentOption(PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC, "自动")
            )
            IOSSlidingSegmentedSetting(
                title = "选择播放顺序：${playbackCompletionBehavior.label}",
                subtitle = "自动模式下普通单视频暂停，分P/合集继续下一集",
                options = playbackOrderOptions,
                selectedValue = playbackCompletionBehavior,
                onSelectionChange = { behavior ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setPlaybackCompletionBehavior(context, behavior)
                    }
                }
            )
            Text(
                text = "稍后再看、收藏夹等列表推荐选择“顺序播放”即可连续播放下一条；自动模式不会把普通单视频跳到推荐视频。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (subtitleFeatureEnabled) {
            IOSDivider()
            IOSSlidingSegmentedSetting(
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
                    PlaybackSegmentOption(SubtitleAutoPreference.OFF, "关闭"),
                    PlaybackSegmentOption(SubtitleAutoPreference.ON, "开启"),
                    PlaybackSegmentOption(SubtitleAutoPreference.WITHOUT_AI, "无 AI"),
                    PlaybackSegmentOption(SubtitleAutoPreference.AUTO, "自动")
                ),
                selectedValue = subtitleAutoPreference,
                onSelectionChange = { preference ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setSubtitleAutoPreference(context, preference)
                    }
                }
            )
            IOSDivider()
        }
	        IOSSwitchItem(
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
        IOSDivider()
        IOSSwitchItem(
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
        IOSDivider()
        IOSSwitchItem(
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
            IOSDivider()
            IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
            title = "评论回复预览：${commentCollapsedReplyPreviewLimit}条",
            subtitle = "收起楼中楼时保留的回复数量",
            options = listOf(
                PlaybackSegmentOption(3, "3条"),
                PlaybackSegmentOption(5, "5条"),
                PlaybackSegmentOption(8, "8条"),
                PlaybackSegmentOption(10, "10条")
            ),
            selectedValue = commentCollapsedReplyPreviewLimit,
            onSelectionChange = { limit ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setCommentCollapsedReplyPreviewLimit(context, limit)
                }
            }
        )
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTION_COMMENT),
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.DOWNLOAD_PATH),
            title = "图片长按保存",
            subtitle = if (imagePreviewLongPressSaveEnabled) {
                "查看图片时长按会直接保存到相册"
            } else {
                "关闭后长按图片不再自动保存，仍可点右上角下载"
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
    val portraitSwipeToFullscreenEnabled by com.android.purebilibili.core.store.SettingsManager
        .getPortraitSwipeToFullscreenEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val directPortraitStoryEntry by com.android.purebilibili.core.store.SettingsManager
        .getAutoPortraitFullscreen(context).collectAsStateWithLifecycle(initialValue = false)
    val launchToPortraitFeedOnStartup by com.android.purebilibili.core.store.SettingsManager
        .getLaunchToPortraitFeedOnStartup(context).collectAsStateWithLifecycle(initialValue = false)
    val centerSwipeToFullscreenEnabled by com.android.purebilibili.core.store.SettingsManager
        .getCenterSwipeToFullscreenEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val slideVolumeBrightnessEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSlideVolumeBrightnessEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val setSystemBrightnessEnabled by com.android.purebilibili.core.store.SettingsManager
        .getSetSystemBrightnessEnabled(context).collectAsStateWithLifecycle(initialValue = false)
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
    IOSGroup {
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
                    Text(
                        text = "双击跳转",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
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
                    PlaybackSegmentOption(5, "5秒"),
                    PlaybackSegmentOption(10, "10秒"),
                    PlaybackSegmentOption(15, "15秒"),
                    PlaybackSegmentOption(30, "30秒"),
                    PlaybackSegmentOption(60, "60秒")
                )
                IOSSlidingSegmentedSetting(
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
                IOSSlidingSegmentedSetting(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.PIP_DANMAKU),
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
        IOSDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IOSSlidingSegmentedSetting(
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
        }

        IOSDivider()
	        IOSSwitchItem(
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

        IOSDivider()
        IOSSwitchItem(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_SWIPE_FULLSCREEN),
            title = "竖屏视频直达刷视频模式",
            subtitle = if (directPortraitStoryEntry) {
                "任意入口点击竖屏视频将直接进入竖屏刷视频，跳过详情页"
            } else {
                "关闭后竖屏视频仍从详情页进入"
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

        IOSDivider()
        IOSSwitchItem(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PORTRAIT_SWIPE_FULLSCREEN),
            title = "启动时进入竖屏视频流",
            subtitle = if (launchToPortraitFeedOnStartup) {
                "打开应用后直接进入竖屏刷视频，类似短视频 App"
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

        IOSDivider()
	        IOSSwitchItem(
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

        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUDIO_FOCUS),
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.SYSTEM_BRIGHTNESS),
            title = "调节系统亮度",
            subtitle = if (slideVolumeBrightnessEnabled) {
                "开启后亮度手势会尝试同步系统亮度（需系统允许）"
            } else {
                "依赖“左右侧滑动调节亮度/音量”开关"
            },
            checked = setSystemBrightnessEnabled,
            onCheckedChange = {
                if (!slideVolumeBrightnessEnabled) return@IOSSwitchItem
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setSetSystemBrightnessEnabled(context, it)
                }
            },
            iconTint = iOSOrange
        )

        IOSDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "非全屏滑动调进度范围",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "左右拖动约半屏达到 ${inlineSwipeSeekSeconds} 秒上限，数值越小越精确",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val inlineSeekOptions = listOf(
                PlaybackSegmentOption(5, "5秒"),
                PlaybackSegmentOption(10, "10秒"),
                PlaybackSegmentOption(15, "15秒"),
                PlaybackSegmentOption(30, "30秒"),
                PlaybackSegmentOption(60, "60秒")
            )
            IOSSlidingSegmentedControl(
                options = inlineSeekOptions,
                selectedValue = inlineSwipeSeekSeconds,
                onSelectionChange = { seconds ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setInlineSwipeSeekSeconds(context, seconds)
                    }
                }
            )
        }

        IOSDivider()
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
                Text(
                    text = "横屏滑动调进度范围",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AppAdaptiveSwitch(
                    checked = fullscreenSwipeSeekEnabled,
                    onCheckedChange = {
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setFullscreenSwipeSeekEnabled(context, it)
                        }
                    }
                )
            }
            Text(
                text = if (fullscreenSwipeSeekEnabled) {
                    "左右拖动约半屏达到 ${fullscreenSwipeSeekSeconds} 秒上限，数值越小越精确"
                } else {
                    "已关闭横屏精细调进度（当前范围 ${fullscreenSwipeSeekSeconds} 秒，重新开启后生效）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val seekStepOptions = listOf(
                PlaybackSegmentOption(10, "10秒"),
                PlaybackSegmentOption(15, "15秒"),
                PlaybackSegmentOption(20, "20秒"),
                PlaybackSegmentOption(30, "30秒")
            )
            IOSSlidingSegmentedControl(
                options = seekStepOptions,
                selectedValue = fullscreenSwipeSeekSeconds,
                enabled = fullscreenSwipeSeekEnabled,
                onSelectionChange = { seconds ->
                    if (!fullscreenSwipeSeekEnabled) return@IOSSlidingSegmentedControl
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setFullscreenSwipeSeekSeconds(context, seconds)
                    }
                }
            )
        }
        IOSDivider()
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
        val hideVideoPageStatusBar by com.android.purebilibili.core.store.SettingsManager
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
        val fullscreenModeSubtitle = if (autoRotateEnabled) {
            "${fullscreenMode.description}；已开启自动横竖屏，日常会跟随设备方向自动进退全屏"
        } else {
            fullscreenMode.description
        }
        val horizontalAdaptationSubtitle = if (isLargeScreenDevice) {
            "启用横屏布局和横屏逻辑（平板/折叠屏建议开启）"
        } else {
            "主要用于平板/折叠屏，当前设备触发场景可能较少"
        }

	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_ORIENTATION),
            title = "自动横竖屏切换",
            subtitle = "跟随手机方向自动进入/退出全屏",
            checked = autoRotateEnabled,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setAutoRotateEnabled(context, it)
                }
            },
            iconTint = iOSTeal
        )
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
        IOSSwitchItem(
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
        IOSDivider()
        IOSSwitchItem(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.LIKE_INTERACTION),
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.HIDE_STATUS_BAR),
            title = "播放页隐藏状态栏",
            subtitle = if (hideVideoPageStatusBar) {
                "普通播放页隐藏顶部系统状态栏，底部手势条保持显示"
            } else {
                "关闭后播放页跟随系统状态栏显示"
            },
            checked = hideVideoPageStatusBar,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setHideVideoPageStatusBar(context, it)
                }
            },
            iconTint = com.android.purebilibili.core.theme.iOSTeal
        )
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
	            icon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_EXIT_FULLSCREEN),
            title = "自动退出全屏",
            subtitle = "视频结束播放后自动退出全屏",
            checked = autoExitFullscreen,
            onCheckedChange = {
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setAutoExitFullscreen(context, it)
                }
            },
            iconTint = iOSOrange
        )
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
	        IOSSwitchItem(
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
        IOSDivider()
        IOSSlidingSegmentedSetting(
            title = "底部进度条展示：${bottomProgressBehavior.label}",
            subtitle = bottomProgressBehavior.description,
            options = listOf(
                PlaybackSegmentOption(BottomProgressBehavior.ALWAYS_SHOW, "始终展示"),
                PlaybackSegmentOption(BottomProgressBehavior.ALWAYS_HIDE, "始终隐藏"),
                PlaybackSegmentOption(BottomProgressBehavior.ONLY_SHOW_FULLSCREEN, "仅全屏展示"),
                PlaybackSegmentOption(BottomProgressBehavior.ONLY_HIDE_FULLSCREEN, "仅全屏隐藏")
            ),
            selectedValue = bottomProgressBehavior,
            onSelectionChange = { behavior ->
                scope.launch {
                    com.android.purebilibili.core.store.SettingsManager
                        .setBottomProgressBehavior(context, behavior)
                }
            }
        )
        IOSDivider()
        IOSSlidingSegmentedSetting(
            title = "控制栏进度条位置：${playerProgressPlacement.label}",
            subtitle = "可将可拖动进度条放到控制按钮下方的视频最底部",
            options = listOf(
                PlaybackSegmentOption(
                    com.android.purebilibili.core.store.PlayerProgressPlacement.ABOVE_CONTROLS,
                    "控制栏上方"
                ),
                PlaybackSegmentOption(
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
