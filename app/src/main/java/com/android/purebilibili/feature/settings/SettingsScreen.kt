// 文件路径: feature/settings/SettingsScreen.kt
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
// 🔥 导入新的枚举
import com.android.purebilibili.feature.settings.AppThemeMode

const val GITHUB_URL = "https://github.com/jay3-yy/BiliPai/"

enum class DisplayMode(val title: String, val value: Int) {
    Grid("双列网格 (默认)", 0),
    Card("单列大图 (沉浸)", 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // 1. 从 ViewModel 获取核心状态 (包含 themeMode 和 cacheSize)
    val state by viewModel.state.collectAsState()

    // 2. 其他 UI 状态 (SharedPreferences)
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var displayModeInt by remember { mutableIntStateOf(prefs.getInt("display_mode", 0)) }
    var isStatsEnabled by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }
    var isBgPlay by remember { mutableStateOf(prefs.getBoolean("bg_play", false)) }
    var danmakuScale by remember { mutableFloatStateOf(prefs.getFloat("danmaku_scale", 1.0f)) }
    var useDynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }

    // --- 弹窗状态 ---
    var showModeDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // --- 初始化逻辑 ---
    // 每次进入页面时刷新缓存大小
    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
    }

    fun saveMode(mode: Int) {
        displayModeInt = mode
        prefs.edit().putInt("display_mode", mode).apply()
        showModeDialog = false
    }

    // 1. 首页模式弹窗
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("选择首页展示方式", color = MaterialTheme.colorScheme.onSurface) },
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
                                colors = RadioButtonDefaults.colors(selectedColor = BiliPink, unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode.title, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModeDialog = false }) { Text("取消", color = BiliPink) } },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 2. 🔥 主题模式弹窗 (适配新逻辑)
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("外观设置", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (state.themeMode == mode),
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = BiliPink,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode.label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("取消", color = BiliPink)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 3. 🔥 缓存清理弹窗 (集成 CacheUtils)
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("清除缓存", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("确定要清除所有图片和视频缓存吗？", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        // 🔥 调用 ViewModel 执行清理
                        viewModel.clearCache()
                        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                        showCacheDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
                ) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = { showCacheDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                // 适配主题色
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        // 适配背景色
        containerColor = MaterialTheme.colorScheme.background
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
                    // 🔥 修改：改为可点击项，弹出选择框
                    SettingClickableItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "深色模式",
                        // 显示当前选中的模式名称
                        value = state.themeMode.label,
                        onClick = { showThemeDialog = true }
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
                        checked = state.hwDecode,
                        onCheckedChange = { viewModel.toggleHwDecode(it) }
                    )
                    Divider()
                    SettingSwitchItem(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "视频自动播放",
                        subtitle = "在列表静音播放预览",
                        checked = state.autoPlay,
                        onCheckedChange = { viewModel.toggleAutoPlay(it) }
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
                        // 🔥 使用动态计算的缓存大小
                        value = state.cacheSize,
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

// --- 组件封装 (核心修复：颜色适配) ---

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            // 适配卡片背景 (深灰/纯白)
            .background(MaterialTheme.colorScheme.surface),
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BiliPink,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
}

fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)