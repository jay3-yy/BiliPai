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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSTeal
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSSystemGray
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
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
    // val state by viewModel.state.collectAsState() // Moved to parameter
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
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
            item { IOSSectionTitle("解码") }
            item {
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
                }
            }
            
            //  小窗播放
            item { IOSSectionTitle("小窗播放") }
            item {
                val scope = rememberCoroutineScope()
                
                // 🔄 [简化] 只有两种模式：默认 和 画中画
                val modeOptions = com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.entries
                var isExpanded by remember { mutableStateOf(false) }
                
                IOSGroup {
                    //  点击展开模式选择
                    IOSClickableItem(
                        icon = CupertinoIcons.Default.Pip,
                        title = "后台播放模式",
                        value = miniPlayerMode.label,
                        onClick = { isExpanded = !isExpanded },
                        iconTint = iOSTeal
                    )
                    
                    //  展开的模式选择列表（简化为2选项）
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isExpanded,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            modeOptions.forEach { mode ->
                                val isSelected = mode == miniPlayerMode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .clickable {
                                            scope.launch {
                                                com.android.purebilibili.core.store.SettingsManager
                                                    .setMiniPlayerMode(context, mode)
                                            }
                                            // 如果选择系统PiP，检查权限
                                            if (mode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP) {
                                                if (!checkPipPermission()) {
                                                    showPipPermissionDialog = true
                                                }
                                            }
                                            isExpanded = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            mode.label,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            mode.description,
                                            fontSize = 12.sp,
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
                    
                    //  权限提示（仅当选择系统PiP且无权限时显示）
                    if (miniPlayerMode == com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.SYSTEM_PIP 
                        && !checkPipPermission()) {
                        Divider()
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
                                tint = iOSOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "画中画权限未开启",
                                    fontSize = 14.sp,
                                    color = iOSOrange
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
                }
            }
            
            //  手势设置
            item { IOSSectionTitle("手势控制") }
            item {
                IOSGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                CupertinoIcons.Default.HandTap,
                                contentDescription = null,
                                tint = iOSOrange,
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
            
            //  调试选项
            item { IOSSectionTitle("调试") }
            item {
                IOSGroup {
                    IOSSwitchItem(
                        icon = CupertinoIcons.Default.ChartBar,
                        title = "详细统计信息",
                        subtitle = "显示 Codec、码率等 Geek 信息",
                        checked = isStatsEnabled,
                        onCheckedChange = {
                            isStatsEnabled = it
                            prefs.edit().putBoolean("show_stats", it).apply()
                        },
                        iconTint = iOSSystemGray
                    )
                }
            }
            
            //  交互设置
            item { IOSSectionTitle("交互") }
            item {
                val scope = rememberCoroutineScope()
                val swipeHidePlayerEnabled by com.android.purebilibili.core.store.SettingsManager
                    .getSwipeHidePlayerEnabled(context).collectAsState(initial = false)
                
                //  [新增] 自动播放下一个
                val autoPlayEnabled by com.android.purebilibili.core.store.SettingsManager
                    .getAutoPlay(context).collectAsState(initial = true)
                
                IOSGroup {
                    //  [新增] 自动播放下一个视频
                    IOSSwitchItem(
                        icon = CupertinoIcons.Default.ForwardEnd,
                        title = "自动播放下一个",
                        subtitle = "视频结束后自动播放推荐视频",
                        checked = autoPlayEnabled,
                        onCheckedChange = { 
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setAutoPlay(context, it)
                            }
                        },
                        iconTint = com.android.purebilibili.core.theme.iOSPurple
                    )
                    Divider()
                    IOSSwitchItem(
                        icon = CupertinoIcons.Default.HeartCircle,
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
                    Divider()
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
                }
            }
            
            //  网络与画质
            item { IOSSectionTitle("网络与画质") }
            item {
                val scope = rememberCoroutineScope()
                val wifiQuality by com.android.purebilibili.core.store.SettingsManager
                    .getWifiQuality(context).collectAsState(initial = 80)
                val mobileQuality by com.android.purebilibili.core.store.SettingsManager
                    .getMobileQuality(context).collectAsState(initial = 64)
                
                // 画质选项列表
                val qualityOptions = listOf(
                    116 to "1080P60",
                    80 to "1080P",
                    64 to "720P",
                    32 to "480P",
                    16 to "360P"
                )
                
                fun getQualityLabel(id: Int) = qualityOptions.find { it.first == id }?.second ?: "720P"
                
                IOSGroup {
                    // WiFi 画质选择
                    var wifiExpanded by remember { mutableStateOf(false) }
                    Column {
                        IOSClickableItem(
                            icon = CupertinoIcons.Default.Wifi,
                            title = "WiFi 默认画质",
                            value = getQualityLabel(wifiQuality),
                            onClick = { wifiExpanded = !wifiExpanded },
                            iconTint = com.android.purebilibili.core.theme.iOSBlue
                        )
                        
                        //  展开动画
                        androidx.compose.animation.AnimatedVisibility(
                            visible = wifiExpanded,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                qualityOptions.forEach { (id, label) ->
                                    val isSelected = id == wifiQuality
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable {
                                                scope.launch { 
                                                    com.android.purebilibili.core.store.SettingsManager
                                                        .setWifiQuality(context, id)
                                                }
                                                wifiExpanded = false
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Divider()
                    
                    // 流量画质选择
                    var mobileExpanded by remember { mutableStateOf(false) }
                    
                    // 📉 读取省流量模式，用于显示提示
                    val dataSaverModeForHint by com.android.purebilibili.core.store.SettingsManager
                        .getDataSaverMode(context).collectAsState(
                            initial = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
                        )
                    val isDataSaverActive = dataSaverModeForHint != com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF
                    // 📉 计算实际生效画质（省流量时限制最高480P）
                    val effectiveQuality = if (isDataSaverActive && mobileQuality > 32) 32 else mobileQuality
                    val effectiveQualityLabel = getQualityLabel(effectiveQuality)
                    
                    Column {
                        IOSClickableItem(
                            icon = CupertinoIcons.Default.ArrowDownCircle,
                            title = "流量 默认画质",
                            value = getQualityLabel(mobileQuality) + if (isDataSaverActive && mobileQuality > 32) " → $effectiveQualityLabel" else "",
                            onClick = { mobileExpanded = !mobileExpanded },
                            iconTint = iOSOrange
                        )
                        
                        // 📉 省流量限制提示
                        if (isDataSaverActive && mobileQuality > 32) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 56.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "省流量模式已限制为最高480P",
                                    fontSize = 11.sp,
                                    color = iOSGreen.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        //  展开动画
                        androidx.compose.animation.AnimatedVisibility(
                            visible = mobileExpanded,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                qualityOptions.forEach { (id, label) ->
                                    val isSelected = id == mobileQuality
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable {
                                                scope.launch { 
                                                    com.android.purebilibili.core.store.SettingsManager
                                                        .setMobileQuality(context, id)
                                                }
                                                mobileExpanded = false
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 📉 省流量模式
            item { IOSSectionTitle("省流量") }
            item {
                val scope = rememberCoroutineScope()
                val dataSaverMode by com.android.purebilibili.core.store.SettingsManager
                    .getDataSaverMode(context).collectAsState(
                        initial = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
                    )
                
                // 模式选项
                val modeOptions = com.android.purebilibili.core.store.SettingsManager.DataSaverMode.entries
                var isExpanded by remember { mutableStateOf(false) }
                
                IOSGroup {
                    //  点击展开模式选择
                    IOSClickableItem(
                        icon = CupertinoIcons.Default.Leaf,
                        title = "省流量模式",
                        value = dataSaverMode.label,
                        onClick = { isExpanded = !isExpanded },
                        iconTint = iOSGreen
                    )
                    
                    //  展开的模式选择列表
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isExpanded,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            modeOptions.forEach { mode ->
                                val isSelected = mode == dataSaverMode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .clickable {
                                            scope.launch {
                                                com.android.purebilibili.core.store.SettingsManager
                                                    .setDataSaverMode(context, mode)
                                            }
                                            isExpanded = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            mode.label,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            mode.description,
                                            fontSize = 12.sp,
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
                    
                    //  功能说明
                    Divider()
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
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
}
}
