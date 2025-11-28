// 文件路径: feature/settings/SettingsScreen.kt
package com.android.purebilibili.feature.settings

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 假设这些主题颜色是可用的
import com.android.purebilibili.core.theme.TextPrimary
import com.android.purebilibili.core.theme.BiliPink

// 🔥 修改：GitHub 地址留空，等待你创建仓库后填入
const val GITHUB_URL = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    // 本地状态用于功能开关
    var isAutoPlayEnabled by remember { mutableStateOf(true) }
    var isDarkModeEnabled by remember { mutableStateOf(false) }
    var isHdModeEnabled by remember { mutableStateOf(false) }

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
            // --- 区域 1: 功能开关 ---
            item {
                Text(
                    text = "功能与体验",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = TextPrimary
                )
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
            }

            item {
                SettingSwitchItem(
                    title = "视频自动播放",
                    subtitle = "在首页列表中自动播放视频",
                    checked = isAutoPlayEnabled,
                    onCheckedChange = { isAutoPlayEnabled = it }
                )
            }

            item {
                SettingSwitchItem(
                    title = "默认高清画质",
                    subtitle = "优先加载 1080P 或更高画质",
                    checked = isHdModeEnabled,
                    onCheckedChange = { isHdModeEnabled = it }
                )
            }

            item {
                SettingSwitchItem(
                    title = "跟随系统深色模式",
                    subtitle = "根据系统设置自动切换主题",
                    checked = isDarkModeEnabled,
                    onCheckedChange = { isDarkModeEnabled = it }
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

            // 开源地址 (逻辑升级)
            item {
                val hasUrl = GITHUB_URL.isNotBlank()
                SettingClickableItem(
                    title = "开源地址",
                    value = if (hasUrl) "GitHub" else "暂未配置",
                    // 如果没有 URL，onClick 为 null (不可点击)，否则跳转
                    onClick = if (hasUrl) { { uriHandler.openUri(GITHUB_URL) } } else null
                )
            }

            // 作者信息
            item {
                SettingClickableItem(
                    title = "作者",
                    value = "YangY", // 这里可以改成你的名字
                    onClick = null
                )
            }

            // 版本号
            item {
                SettingClickableItem(
                    title = "应用版本",
                    value = "1.0.0 Alpha",
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
            // 只有当 onClick 不为空时才显示箭头
            if (onClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
    }
}