package com.android.purebilibili.feature.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.TextPrimary
import com.android.purebilibili.core.theme.BiliPink

const val GITHUB_URL = "https://github.com/jay3-yy/BiliPai/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // 获取 SharedPreferences
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    // --- 读取本地配置 (带默认值) ---
    var isAutoPlayEnabled by remember { mutableStateOf(prefs.getBoolean("auto_play", true)) }
    var isHdModeEnabled by remember { mutableStateOf(prefs.getBoolean("hd_mode", false)) }
    var isDarkModeEnabled by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    // 🔥 新增：详细统计信息开关
    var isStatsEnabled by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // --- 区域 1: 功能与体验 ---
            item {
                Text(
                    text = "功能与体验",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = TextPrimary
                )
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
            }

            // 1. 详细统计信息 (新增)
            item {
                SettingSwitchItem(
                    title = "显示详细统计信息",
                    subtitle = "在播放器显示真实分辨率 (Stats for Nerds)",
                    checked = isStatsEnabled,
                    onCheckedChange = {
                        isStatsEnabled = it
                        prefs.edit().putBoolean("show_stats", it).apply()
                    }
                )
            }

            item {
                SettingSwitchItem(
                    title = "视频自动播放",
                    subtitle = "在首页列表中自动播放视频",
                    checked = isAutoPlayEnabled,
                    onCheckedChange = {
                        isAutoPlayEnabled = it
                        prefs.edit().putBoolean("auto_play", it).apply()
                    }
                )
            }

            item {
                SettingSwitchItem(
                    title = "默认高清画质",
                    subtitle = "优先加载 1080P 或更高画质",
                    checked = isHdModeEnabled,
                    onCheckedChange = {
                        isHdModeEnabled = it
                        prefs.edit().putBoolean("hd_mode", it).apply()
                    }
                )
            }

            item {
                SettingSwitchItem(
                    title = "跟随系统深色模式",
                    subtitle = "根据系统设置自动切换主题",
                    checked = isDarkModeEnabled,
                    onCheckedChange = {
                        isDarkModeEnabled = it
                        prefs.edit().putBoolean("dark_mode", it).apply()
                    }
                )
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
            }

            // --- 区域 2: 关于应用 ---
            item {
                Text(
                    text = "关于应用",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = TextPrimary
                )
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
            }

            // 开源地址
            item {
                val hasUrl = GITHUB_URL.isNotBlank()
                SettingClickableItem(
                    title = "开源地址",
                    value = if (hasUrl) "GitHub" else "暂未配置",
                    onClick = if (hasUrl) { { uriHandler.openUri(GITHUB_URL) } } else null
                )
            }

            // 作者信息
            item {
                SettingClickableItem(
                    title = "作者",
                    value = "Jay3",
                    onClick = null
                )
            }

            // 版本号
            item {
                SettingClickableItem(
                    title = "应用版本",
                    value = "1.0.1 Beta", // 稍微更新一下版本号提示
                    onClick = null
                )
            }
        }
    }
}

@Composable
fun SettingSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BiliPink)
        )
    }
}

@Composable
fun SettingClickableItem(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
    }
}