// 文件路径: feature/settings/PlaybackSettingsScreen.kt
package com.android.purebilibili.feature.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.store.BottomProgressBehavior
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSTeal
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.staggeredEntrance

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
    val state by viewModel.state.collectAsState()
    val screenTitle = stringResource(R.string.playback_settings_title)
    val backLabel = stringResource(R.string.common_back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
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
    val context = LocalContext.current
    val warningTint = rememberAdaptiveSemanticIconTint(iOSOrange)
    val windowSizeClass = LocalWindowSizeClass.current
    // val state by viewModel.state.collectAsState() // Moved to parameter
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isVisible by remember { mutableStateOf(false) }
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val effectiveMotionTier = remember(deviceUiProfile.motionTier) {
        resolveSettingsEntranceMotionTier(deviceUiProfile.motionTier)
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    var isStatsEnabled by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }
    var showPipPermissionDialog by remember { mutableStateOf(false) }
    
    // 获取动态圆角用于统一风格
    // 注意：这里需要导入 LocalCornerRadiusScale，如果该文件没有导入，可能需要添加。
    // 假设 iOSCornerRadius 和 LocalCornerRadiusScale 未在此文件导入，先使用硬编码或尝试导入
    // 为了稳妥，这里先检查导入。原文件没有导入这些。
    // 但为了保持原样，我先不做动态圆角修改，或者之后再做。
    
    val miniPlayerMode by com.android.purebilibili.core.store.SettingsManager
        .getMiniPlayerMode(context).collectAsState(
            initial = com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF
        )
    val stopPlaybackOnExit by com.android.purebilibili.core.store.SettingsManager
        .getStopPlaybackOnExit(context).collectAsState(initial = false)
    val backgroundPlaybackEnabled by com.android.purebilibili.core.store.SettingsManager
        .getBackgroundPlaybackEnabled(context).collectAsState(initial = true)
    val audioFocusEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAudioFocusEnabled(context).collectAsState(initial = true)
    val audioModeAutoPipEnabled by com.android.purebilibili.core.store.SettingsManager
        .getAudioModeAutoPipEnabled(context).collectAsState(initial = false)
    val playerDiagnosticLoggingEnabled by com.android.purebilibili.core.store.SettingsManager
        .getPlayerDiagnosticLoggingEnabled(context)
        .collectAsState(initial = DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED)
    val defaultPlaybackSpeed by com.android.purebilibili.core.store.SettingsManager
        .getDefaultPlaybackSpeed(context).collectAsState(initial = 1.0f)
    val rememberLastPlaybackSpeed by com.android.purebilibili.core.store.SettingsManager
        .getRememberLastPlaybackSpeed(context).collectAsState(initial = false)
    val videoCodecPreference by com.android.purebilibili.core.store.SettingsManager
        .getVideoCodec(context).collectAsState(initial = "hev1")
    val videoSecondCodecPreference by com.android.purebilibili.core.store.SettingsManager
        .getVideoSecondCodec(context).collectAsState(initial = "avc1")
    
    // ... [保留原有逻辑: checkPipPermission, gotoPipSettings] ...
    
    // 检查画中画权限
    fun checkPipPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                )
            }
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
            
            //  解码设置
            //  解码设置
            item {
                Box(modifier = Modifier.staggeredEntrance(0, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("解码")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(1, isVisible, motionTier = effectiveMotionTier)) {
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
                            icon = CupertinoIcons.Default.Cpu,
                            title = "启用硬件解码",
                            subtitle = "减少发热和耗电 (推荐开启)",
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
                Box(modifier = Modifier.staggeredEntrance(2, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("播放速度")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(3, isVisible, motionTier = effectiveMotionTier)) {
                    val scope = rememberCoroutineScope()
                    IOSGroup {
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.Clock,
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
                Box(modifier = Modifier.staggeredEntrance(4, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("小窗播放")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(5, isVisible, motionTier = effectiveMotionTier)) {
                    val scope = rememberCoroutineScope()
                    val pipNoDanmakuEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getPipNoDanmakuEnabled(context)
                        .collectAsState(initial = false)
                    val modeControlsEnabled = remember(stopPlaybackOnExit, backgroundPlaybackEnabled) {
                        !stopPlaybackOnExit && backgroundPlaybackEnabled
                    }
                    val audioModeAutoPipToggleEnabled = remember(miniPlayerMode, backgroundPlaybackEnabled) {
                        com.android.purebilibili.core.store.SettingsManager
                            .shouldEnableAudioModeAutoPipToggle(miniPlayerMode) && backgroundPlaybackEnabled
                    }
                    val miniPlayerOptions = listOf(
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.OFF, "默认"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY, "应用内小窗"),
                        PlaybackSegmentOption(com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP, "画中画")
                    )
                    
                    IOSGroup {
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.Pip,
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
                            icon = CupertinoIcons.Default.Play,
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
                            icon = CupertinoIcons.Default.SpeakerWave2,
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
                                if (mode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP &&
                                    !checkPipPermission()
                                ) {
                                    showPipPermissionDialog = true
                                }
                            }
                        )
                        
                        //  权限提示（仅当选择系统PiP且无权限时显示）
                        if (modeControlsEnabled &&
                            miniPlayerMode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP
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
                            icon = CupertinoIcons.Default.TextBubble,
                            title = "画中画不加载弹幕",
                            subtitle = if (!backgroundPlaybackEnabled) {
                                "开启后台播放后，系统画中画相关设置才会生效"
                            } else if (miniPlayerMode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP) {
                                if (pipNoDanmakuEnabled) "已开启：系统画中画中不显示弹幕" else "关闭后：系统画中画中也会显示弹幕"
                            } else {
                                "仅系统画中画模式下生效"
                            },
                            checked = pipNoDanmakuEnabled,
                            onCheckedChange = {
                                if (!backgroundPlaybackEnabled ||
                                    miniPlayerMode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP) {
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
                            icon = CupertinoIcons.Default.Headphones,
                            title = "听视频离开时自动进入画中画",
                            subtitle = if (audioModeAutoPipToggleEnabled) {
                                if (audioModeAutoPipEnabled) {
                                    "已开启：回到桌面或使用离开手势时会自动进入系统画中画"
                                } else {
                                    "关闭后仅保留听视频页内的画中画按钮"
                                }
                            } else {
                                "仅系统画中画模式下生效"
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
                Box(modifier = Modifier.staggeredEntrance(6, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("手势控制")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(7, isVisible, motionTier = effectiveMotionTier)) {
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
                Box(modifier = Modifier.staggeredEntrance(8, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("调试")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(9, isVisible, motionTier = effectiveMotionTier)) {
                    IOSGroup {
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.ChartBar,
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
                            icon = CupertinoIcons.Default.InfoCircle,
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
                    }
                }
            }
            
            //  交互设置
            item {
                Box(modifier = Modifier.staggeredEntrance(10, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("交互")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(11, isVisible, motionTier = effectiveMotionTier)) {
                    val scope = rememberCoroutineScope()
                    val swipeHidePlayerEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getSwipeHidePlayerEnabled(context).collectAsState(initial = false)
                    val portraitSwipeToFullscreenEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getPortraitSwipeToFullscreenEnabled(context).collectAsState(initial = true)
                    val centerSwipeToFullscreenEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getCenterSwipeToFullscreenEnabled(context).collectAsState(initial = true)
                    val slideVolumeBrightnessEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getSlideVolumeBrightnessEnabled(context).collectAsState(initial = true)
                    val setSystemBrightnessEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getSetSystemBrightnessEnabled(context).collectAsState(initial = false)
                    val fullscreenSwipeSeekEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getFullscreenSwipeSeekEnabled(context).collectAsState(initial = true)
                    val fullscreenSwipeSeekSeconds by com.android.purebilibili.core.store.SettingsManager
                        .getFullscreenSwipeSeekSeconds(context).collectAsState(initial = 15)
                    
                    //  [新增] 自动播放下一个
                    val autoPlayEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getAutoPlay(context).collectAsState(initial = true)
                    val externalPlaylistAutoContinueEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getExternalPlaylistAutoContinue(context).collectAsState(initial = true)
                    val resumePlaybackPromptEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getResumePlaybackPromptEnabled(context).collectAsState(initial = true)
                    val playbackCompletionBehavior by com.android.purebilibili.core.store.SettingsManager
                        .getPlaybackCompletionBehavior(context)
                        .collectAsState(initial = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC)
                    val subtitleFeatureEnabled = isSubtitleFeatureEnabledForUser()
                    val subtitleAutoPreference by com.android.purebilibili.core.store.SettingsManager
                        .getSubtitleAutoPreference(context)
                        .collectAsState(initial = SubtitleAutoPreference.OFF)
                    val videoAiSummaryEntryEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getVideoAiSummaryEntryEnabled(context)
                        .collectAsState(initial = true)
                    val subtitlePreferenceDescription = when (subtitleAutoPreference) {
                        SubtitleAutoPreference.OFF -> "默认关闭字幕"
                        SubtitleAutoPreference.ON -> "默认开启（优先当前可用轨道）"
                        SubtitleAutoPreference.WITHOUT_AI -> "仅自动启用非 AI 字幕"
                        SubtitleAutoPreference.AUTO -> "静音时可自动启用 AI 字幕"
                    }
                    
                    IOSGroup {
                        // --- Click to Play ---
                        val clickToPlayEnabled by com.android.purebilibili.core.store.SettingsManager
                            .getClickToPlay(context).collectAsState(initial = true)

                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.Play,
                            title = "点击视频直接播放",
                            subtitle = "进入视频详情页时自动开始播放",
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
                            icon = CupertinoIcons.Default.ArrowTriangle2Circlepath,
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
                            icon = CupertinoIcons.Default.ForwardEnd,
                            title = "自动播放下一个",
                            subtitle = "普通视频结束后自动播放推荐视频",
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
                            icon = CupertinoIcons.Default.ListBullet,
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
                                subtitle = "稍后再看推荐选择“顺序播放”",
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
                                text = "稍后再看推荐选择“顺序播放”即可连续播放下一条，不需要退出重选。",
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
                            icon = CupertinoIcons.Default.Sparkles,
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
                            icon = CupertinoIcons.Default.HandThumbsup,
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
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.HandDraw,  // 手势图标
                            title = "上滑隐藏播放器",
                            subtitle = "竖屏模式下拉评论区隐藏播放器",
                            checked = swipeHidePlayerEnabled,
                            onCheckedChange = { 
                                scope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setSwipeHidePlayerEnabled(context, it)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSBlue
                        )

                        IOSDivider()
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.ArrowLeftArrowRight,
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
                            icon = CupertinoIcons.Default.HandDraw,
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
                            icon = CupertinoIcons.Default.SpeakerWave2,
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
                            icon = CupertinoIcons.Default.SunMax,
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "横屏滑动快进/快退步长",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
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
                                    "左右滑动时每档跳转秒数：当前 ${fullscreenSwipeSeekSeconds} 秒"
                                } else {
                                    "已关闭固定步长（当前设定 ${fullscreenSwipeSeekSeconds} 秒，重新开启后生效）"
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
                        
                        // 🔄 [新增] 自动横竖屏切换
                        IOSDivider()
                        val autoRotateEnabled by com.android.purebilibili.core.store.SettingsManager
                            .getAutoRotateEnabled(context).collectAsState(initial = false)
                        val fullscreenGestureReverse by com.android.purebilibili.core.store.SettingsManager
                            .getFullscreenGestureReverse(context).collectAsState(initial = false)
                        val autoEnterFullscreen by com.android.purebilibili.core.store.SettingsManager
                            .getAutoEnterFullscreen(context).collectAsState(initial = false)
                        val autoExitFullscreen by com.android.purebilibili.core.store.SettingsManager
                            .getAutoExitFullscreen(context).collectAsState(initial = true)
                        val showFullscreenLockButton by com.android.purebilibili.core.store.SettingsManager
                            .getShowFullscreenLockButton(context).collectAsState(initial = true)
                        val showFullscreenScreenshotButton by com.android.purebilibili.core.store.SettingsManager
                            .getShowFullscreenScreenshotButton(context).collectAsState(initial = true)
                        val showFullscreenBatteryLevel by com.android.purebilibili.core.store.SettingsManager
                            .getShowFullscreenBatteryLevel(context).collectAsState(initial = true)
                        val showFullscreenTime by com.android.purebilibili.core.store.SettingsManager
                            .getShowFullscreenTime(context).collectAsState(initial = true)
                        val showFullscreenActionItems by com.android.purebilibili.core.store.SettingsManager
                            .getShowFullscreenActionItems(context).collectAsState(initial = true)
                        val showOnlineCount by com.android.purebilibili.core.store.SettingsManager
                            .getShowOnlineCount(context).collectAsState(initial = false)
                        val bottomProgressBehavior by com.android.purebilibili.core.store.SettingsManager
                            .getBottomProgressBehavior(context)
                            .collectAsState(initial = BottomProgressBehavior.ALWAYS_SHOW)
                        val isLargeScreenDevice = context.resources.configuration.smallestScreenWidthDp >= 600
                        val horizontalAdaptationEnabled by com.android.purebilibili.core.store.SettingsManager
                            .getHorizontalAdaptationEnabled(context)
                            .collectAsState(initial = isLargeScreenDevice)
                        val fullscreenMode by com.android.purebilibili.core.store.SettingsManager
                            .getFullscreenMode(context)
                            .collectAsState(initial = com.android.purebilibili.core.store.FullscreenMode.AUTO)
                        val fullscreenAspectRatio by com.android.purebilibili.core.store.SettingsManager
                            .getFullscreenAspectRatio(context)
                            .collectAsState(initial = FullscreenAspectRatio.FIT)
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
                            icon = CupertinoIcons.Default.ArrowTriangle2CirclepathCamera,  // 旋转图标
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
                            icon = CupertinoIcons.Default.ArrowLeftArrowRight,
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
                            icon = CupertinoIcons.Default.ArrowUpArrowDown,
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
                            icon = CupertinoIcons.Default.Play,
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
                            icon = CupertinoIcons.Default.ForwardEnd,
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
                            icon = CupertinoIcons.Default.Lock,
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
                            icon = CupertinoIcons.Default.Camera,
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
                            icon = CupertinoIcons.Default.Battery100,
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
                            icon = CupertinoIcons.Default.Clock,
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
                            icon = CupertinoIcons.Default.HandThumbsup,
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
                            icon = CupertinoIcons.Default.ChartBar,
                            title = "观看人数",
                            subtitle = if (showOnlineCount) {
                                "显示“xx人正在看”信息"
                            } else {
                                "关闭后隐藏观看人数展示"
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
                    }
                }
            }
            
            //  网络与画质
            item {
                Box(modifier = Modifier.staggeredEntrance(12, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("网络与画质")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(13, isVisible, motionTier = effectiveMotionTier)) {
                    val scope = rememberCoroutineScope()
                    val wifiQuality by com.android.purebilibili.core.store.SettingsManager
                        .getWifiQuality(context).collectAsState(initial = 80)
                    val mobileQuality by com.android.purebilibili.core.store.SettingsManager
                        .getMobileQuality(context).collectAsState(initial = 64)
                    val autoHighestQualityEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getAutoHighestQuality(context).collectAsState(initial = false)
                    val directedTrafficEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getBiliDirectedTrafficEnabled(context).collectAsState(initial = false)
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
                            icon = CupertinoIcons.Default.ChartBar,
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
                            icon = CupertinoIcons.Default.Sparkles,
                            title = "自动最高画质",
                            subtitle = if (autoHighestQualityEnabled) {
                                "已开启，始终请求账号与设备可用的最高画质"
                            } else {
                                "全局开关，开启后覆盖下方无线网络和流量默认画质"
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
                                "已被自动最高画质覆盖，当前仅保留你的无线网络偏好"
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
                            .getDataSaverMode(context).collectAsState(
                                initial = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
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
                                    "已被自动最高画质覆盖，当前仅保留你的流量偏好"
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
                Box(modifier = Modifier.staggeredEntrance(14, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("省流量")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(15, isVisible, motionTier = effectiveMotionTier)) {
                    val scope = rememberCoroutineScope()
                    val dataSaverMode by com.android.purebilibili.core.store.SettingsManager
                        .getDataSaverMode(context).collectAsState(
                            initial = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
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
                                "开启后将自动降低封面图质量、禁用预加载、限制视频最高480P",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
}
}
