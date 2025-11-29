package com.android.purebilibili.feature.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 🔥 修复：使用 AutoMirrored
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer // 🔥 修复：添加缺失的导入
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.theme.TextPrimary

const val GITHUB_URL = "https://github.com/jay3-yy/BiliPai/"

enum class DisplayMode(val title: String, val value: Int) {
    Grid("双列网格 (默认)", 0),
    Card("单列大图 (沉浸)", 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(), // 🔥 注入 ViewModel
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // 🔥 1. 从 ViewModel 获取核心状态
    val state by viewModel.state.collectAsState()

    // 🔥 2. 其他 UI 状态仍暂时使用 SharedPreferences (因为 ViewModel 中还没加这些)
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var displayModeInt by remember { mutableIntStateOf(prefs.getInt("display_mode", 0)) }
    var isStatsEnabled by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }
    var isBgPlay by remember { mutableStateOf(prefs.getBoolean("bg_play", false)) }
    var danmakuScale by remember { mutableFloatStateOf(prefs.getFloat("danmaku_scale", 1.0f)) }
    var useDynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }

    // --- 弹窗逻辑 ---
    var showModeDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }

    fun saveMode(mode: Int) {
        displayModeInt = mode
        prefs.edit().putInt("display_mode", mode).apply()
        showModeDialog = false
    }

    // 模式选择弹窗
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("选择首页展示方式") },
            text = {
                Column {
                    DisplayMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveMode(mode.value) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (displayModeInt == mode.value),
                                onClick = { saveMode(mode.value) },
                                colors = RadioButtonDefaults.colors(selectedColor = BiliPink)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode.title)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModeDialog = false }) { Text("取消", color = BiliPink) } },
            containerColor = Color.White
        )
    }

    // 缓存清理弹窗
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有图片和视频缓存吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                        showCacheDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
                ) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = { showCacheDialog = false }) { Text("取消") } },
            containerColor = Color.White
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 🔥 修复：使用 AutoMirrored 图标消除警告
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // --- 区域 1: 首页与外观 ---
            item { SettingsSectionTitle("首页与外观") }
            item {
                SettingsGroup {
                    SettingClickableItem(
                        icon = Icons.Outlined.Dashboard,
                        title = "首页展示方式",
                        value = DisplayMode.entries.find { it.value == displayModeInt }?.title ?: "未知",
                        onClick = { showModeDialog = true }
                    )
                    Divider()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingSwitchItem(
                            icon = Icons.Outlined.Palette,
                            title = "动态取色 (Material You)",
                            subtitle = "跟随系统壁纸变换应用主题色",
                            checked = useDynamicColor,
                            onCheckedChange = {
                                useDynamicColor = it
                                prefs.edit().putBoolean("dynamic_color", it).apply()
                            }
                        )
                        Divider()
                    }
                    SettingSwitchItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "深色模式",
                        subtitle = "跟随系统或手动开启",
                        checked = state.darkMode, // 🔥 使用 ViewModel 状态
                        onCheckedChange = { viewModel.toggleDarkMode(it) } // 🔥 调用 ViewModel 方法
                    )
                }
            }

            // --- 区域 2: 播放与解码 ---
            item { SettingsSectionTitle("播放与解码") }
            item {
                SettingsGroup {
                    SettingSwitchItem(
                        icon = Icons.Outlined.Memory,
                        title = "启用硬件解码",
                        subtitle = "减少发热和耗电 (推荐开启)",
                        checked = state.hwDecode, // 🔥 使用 ViewModel 状态
                        onCheckedChange = { viewModel.toggleHwDecode(it) } // 🔥 调用 ViewModel 方法
                    )
                    Divider()
                    SettingSwitchItem(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "视频自动播放",
                        subtitle = "在列表静音播放预览",
                        checked = state.autoPlay, // 🔥 使用 ViewModel 状态
                        onCheckedChange = { viewModel.toggleAutoPlay(it) } // 🔥 调用 ViewModel 方法
                    )
                    Divider()
                    SettingSwitchItem(
                        icon = Icons.Outlined.PictureInPicture,
                        title = "后台/画中画播放",
                        subtitle = "应用切到后台时继续播放",
                        checked = isBgPlay,
                        onCheckedChange = {
                            isBgPlay = it
                            prefs.edit().putBoolean("bg_play", it).apply()
                        }
                    )
                    Divider()
                    SettingSwitchItem(
                        icon = Icons.Outlined.Info,
                        title = "详细统计信息",
                        subtitle = "显示 Codec、码率等 Geek 信息",
                        checked = isStatsEnabled,
                        onCheckedChange = {
                            isStatsEnabled = it
                            prefs.edit().putBoolean("show_stats", it).apply()
                        }
                    )
                }
            }

            // --- 区域 3: 弹幕设置 ---
            item { SettingsSectionTitle("弹幕设置") }
            item {
                SettingsGroup {
                    SettingClickableItem(
                        icon = Icons.Outlined.FormatSize,
                        title = "弹幕字号缩放",
                        value = "${(danmakuScale * 100).toInt()}%",
                        onClick = {
                            val newScale = if (danmakuScale >= 1.5f) 0.5f else danmakuScale + 0.25f
                            danmakuScale = newScale
                            prefs.edit().putFloat("danmaku_scale", newScale).apply()
                            Toast.makeText(context, "字号已调整", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // --- 区域 4: 高级选项 ---
            item { SettingsSectionTitle("高级选项") }
            item {
                SettingsGroup {
                    SettingClickableItem(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "清除缓存",
                        value = "128 MB",
                        onClick = { showCacheDialog = true }
                    )
                    Divider()
                    SettingClickableItem(
                        icon = Icons.Outlined.Code,
                        title = "开源主页",
                        value = "GitHub",
                        onClick = { uriHandler.openUri(GITHUB_URL) }
                    )
                    Divider()
                    SettingClickableItem(
                        icon = Icons.Outlined.Info,
                        title = "版本",
                        value = "v1.0.2 Beta",
                        onClick = null
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// --- 组件封装 ---

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Color.Gray,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        content = content
    )
}

@Composable
fun SettingSwitchItem(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BiliPink),
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
fun SettingClickableItem(
    icon: ImageVector? = null,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFF0F0F0)))
}

// 🔥 修复：这个 Modifier 扩展需要 'import androidx.compose.ui.graphics.graphicsLayer'
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)