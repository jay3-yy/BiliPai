package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.AppIcons
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import io.github.alexzhirkevich.cupertino.CupertinoSwitchDefaults
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.core.ui.common.copyOnLongPress

// ═══════════════════════════════════════════════════
//  UI 组件 (Stateless Components)
// ═══════════════════════════════════════════════════

// ═══════════════════════════════════════════════════
//  UI 组件 (Stateless Components)
// ═══════════════════════════════════════════════════

// Delegated to core/ui/components/iOSListComponents.kt
import com.android.purebilibili.core.ui.components.IOSSectionTitle as SettingsSectionTitle
import com.android.purebilibili.core.ui.components.IOSGroup as SettingsGroup
import com.android.purebilibili.core.ui.components.IOSSwitchItem as SettingSwitchItem
import com.android.purebilibili.core.ui.components.IOSClickableItem as SettingClickableItem
import com.android.purebilibili.core.ui.components.IOSDivider as SettingsDivider



// ═══════════════════════════════════════════════════
//  业务板块 (Business Sections)
// ═══════════════════════════════════════════════════

@Composable
fun FollowAuthorSection(
    onTelegramClick: () -> Unit,
    onTwitterClick: () -> Unit
) {
    SettingsGroup {
        SettingClickableItem(
            iconPainter = painterResource(R.drawable.ic_telegram_mono),
            title = "Telegram 频道",
            value = "@BiliPai",
            onClick = onTelegramClick,
            iconTint = Color(0xFF0088CC),
            enableCopy = true
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = AppIcons.Twitter,
            title = "Twitter / X",
            value = "@YangY_0x00",
            onClick = onTwitterClick,
            iconTint = Color(0xFF1DA1F2),
            enableCopy = true
        )
    }
}

@Composable
fun GeneralSection(
    onAppearanceClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onBottomBarClick: () -> Unit
) {
    SettingsGroup {
        SettingClickableItem(
            icon = CupertinoIcons.Default.Paintpalette,
            title = "外观设置",
            value = "主题、图标、模糊效果",
            onClick = onAppearanceClick,
            iconTint = iOSPink
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.Play,
            title = "播放设置",
            value = "解码、手势、后台播放",
            onClick = onPlaybackClick,
            iconTint = iOSGreen
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.RectangleStack,
            title = "底栏设置",
            value = "自定义底栏项目",
            onClick = onBottomBarClick,
            iconTint = iOSBlue
        )
    }
}

@Composable
fun PrivacySection(
    privacyModeEnabled: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
    onPermissionClick: () -> Unit
) {
    SettingsGroup {
        SettingSwitchItem(
            icon = CupertinoIcons.Default.EyeSlash,
            title = "隐私无痕模式",
            subtitle = "启用后不记录播放历史和搜索历史",
            checked = privacyModeEnabled,
            onCheckedChange = onPrivacyModeChange,
            iconTint = iOSPurple
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.Lock,
            title = "权限管理",
            value = "查看应用权限",
            onClick = onPermissionClick,
            iconTint = iOSTeal
        )
    }
}

@Composable
fun DataStorageSection(
    customDownloadPath: String?,
    cacheSize: String,
    onDownloadPathClick: () -> Unit,
    onClearCacheClick: () -> Unit
) {
    SettingsGroup {
        SettingClickableItem(
            icon = CupertinoIcons.Default.FolderBadgePlus,
            title = "下载位置",
            value = if (customDownloadPath != null) "自定义" else "默认",
            onClick = onDownloadPathClick,
            iconTint = iOSBlue
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.Trash,
            title = "清除缓存",
            value = cacheSize,
            onClick = onClearCacheClick,
            iconTint = iOSPink
        )
    }
}

@Composable
fun DeveloperSection(
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    pluginCount: Int,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onPluginsClick: () -> Unit,
    onExportLogsClick: () -> Unit
) {
    SettingsGroup {
        SettingSwitchItem(
            icon = CupertinoIcons.Default.ExclamationmarkTriangle,
            title = "崩溃追踪",
            subtitle = "帮助开发者发现和修复问题",
            checked = crashTrackingEnabled,
            onCheckedChange = onCrashTrackingChange,
            iconTint = iOSTeal
        )
        SettingsDivider(startIndent = 66.dp)
        SettingSwitchItem(
            icon = CupertinoIcons.Default.ChartBar,
            title = "使用情况统计",
            subtitle = "帮助改进应用体验，不收集个人信息",
            checked = analyticsEnabled,
            onCheckedChange = onAnalyticsChange,
            iconTint = iOSBlue
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.PuzzlepieceExtension,
            title = "插件中心",
            value = "$pluginCount 个已启用",
            onClick = onPluginsClick,
            iconTint = iOSPurple
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.DocTextMagnifyingglass,
            title = "导出日志",
            value = "用于反馈问题",
            onClick = onExportLogsClick,
            iconTint = iOSTeal
        )
    }
}

@Composable
fun AboutSection(
    versionName: String,
    easterEggEnabled: Boolean,
    onLicenseClick: () -> Unit,
    onGithubClick: () -> Unit,
    onVersionClick: () -> Unit,
    onReplayOnboardingClick: () -> Unit,
    onEasterEggChange: (Boolean) -> Unit
) {
    SettingsGroup {
        SettingClickableItem(
            icon = CupertinoIcons.Default.DocText,
            title = "开源许可证",
            value = "License",
            onClick = onLicenseClick,
            iconTint = iOSOrange
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.Link,
            title = "开源主页",
            value = "GitHub",
            onClick = onGithubClick,
            iconTint = iOSPurple,
            enableCopy = true
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.InfoCircle,
            title = "版本",
            value = "v$versionName",
            onClick = onVersionClick,
            iconTint = iOSTeal,
            enableCopy = true
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = CupertinoIcons.Default.BookCircle,
            title = "重播新手引导",
            value = "了解应用功能",
            onClick = onReplayOnboardingClick,
            iconTint = iOSPink
        )
        SettingsDivider(startIndent = 66.dp)
        SettingSwitchItem(
            icon = CupertinoIcons.Default.Gift,
            title = "趣味彩蛋",
            subtitle = "刷新、点赞、投币、搜索时显示趣味提示",
            checked = easterEggEnabled,
            onCheckedChange = onEasterEggChange,
            iconTint = iOSYellow
        )
    }
}
