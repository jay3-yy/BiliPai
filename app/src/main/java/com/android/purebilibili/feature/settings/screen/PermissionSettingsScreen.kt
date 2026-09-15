// 文件路径: feature/settings/PermissionSettingsScreen.kt
package com.android.purebilibili.feature.settings
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.adaptiveSquircleBackground
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.core.theme.iOSPink  // 存储权限图标色
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.theme.iOSTeal
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.cast.hasRawLocalNetworkAccess
import com.android.purebilibili.feature.cast.localNetworkRuntimePermissions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 *  权限管理页面
 * 显示应用所有权限的用途说明和当前状态
 */
/**
 *  权限管理页面内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSettingsScreen(
    onBack: () -> Unit
) {
    val screenTitle = stringResource(R.string.permission_management_title)
    val backLabel = stringResource(R.string.common_back)
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    EntranceGroup {
        SettingsPageScaffold(
            title = screenTitle,
            onBack = onBack,
            backContentDescription = backLabel,
            bottomContentPadding = bottomPadding,
            scrollHost = SettingsPageScrollHost.External,
        ) {
            PermissionSettingsContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSettingsContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 权限列表数据
    val permissions = remember {
        listOf(
            PermissionInfo(
                name = "网络访问",
                permission = Manifest.permission.INTERNET,
                description = "加载视频、图片和用户数据",
                iconResId = R.drawable.ms_wifi_24,
                iconTint = iOSBlue,
                isNormal = true,
                alwaysGranted = true
            ),
            PermissionInfo(
                name = "网络状态",
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                description = "检测网络连接状态，优化加载体验",
                iconResId = R.drawable.ms_bar_chart_24,
                iconTint = iOSGreen,
                isNormal = true,
                alwaysGranted = true
            ),
            PermissionInfo(
                name = "通知权限",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else {
                    "android.permission.POST_NOTIFICATIONS"
                },
                description = "显示媒体播放控制，以及已开启的消息、关注更新和开播提醒",
                iconResId = R.drawable.ms_notifications_24,
                iconTint = iOSOrange,
                isNormal = false,
                alwaysGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ),
            PermissionInfo(
                name = "前台服务",
                permission = Manifest.permission.FOREGROUND_SERVICE,
                description = "支持后台播放和可选的常驻消息检查",
                iconResId = R.drawable.ms_play_circle_24,
                iconTint = iOSPurple,
                isNormal = true,
                alwaysGranted = true
            ),
            PermissionInfo(
                name = "媒体播放服务",
                permission = "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                description = "允许应用在后台继续播放视频",
                iconResId = R.drawable.ms_audio_file_24,
                iconTint = iOSTeal,
                isNormal = true,
                alwaysGranted = true
            ),
            //  DLNA 投屏所需权限
            PermissionInfo(
                name = "本地网络（DLNA）",
                permission = localNetworkRuntimePermissions().firstOrNull()
                    ?: "android.permission.ACCESS_LOCAL_NETWORK",
                description = if (Build.VERSION.SDK_INT >= 37) {
                    "仅在搜索和连接局域网 DLNA 设备时使用；Google Cast 不需要此权限"
                } else {
                    "用于搜索和连接附近的 DLNA 投屏设备"
                },
                iconResId = R.drawable.ms_tv_24,
                iconTint = iOSBlue,
                isNormal = false,
                alwaysGranted = localNetworkRuntimePermissions().isEmpty(),
                customCheck = { permissionContext -> hasRawLocalNetworkAccess(permissionContext) }
            ),
             // 📁 存储写入（使用 MediaStore/SAF，不申请所有文件访问）
            PermissionInfo(
                name = "媒体文件写入",
                permission = "scoped_storage",
                description = "保存图片/截图时使用系统媒体库，下载导出使用系统文件夹授权",
                iconResId = R.drawable.ms_folder_shared_24,
                iconTint = iOSPink,
                isNormal = true,
                alwaysGranted = true
            ),

        )
    }
    
    // 检查权限状态
    var permissionStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    fun refreshPermissionStates() {
        permissionStates = permissions.associate { info ->
            info.permission to if (info.alwaysGranted) {
                true
            } else {
                info.customCheck?.invoke(context)
                    ?: (ContextCompat.checkSelfPermission(context, info.permission) == PackageManager.PERMISSION_GRANTED)
            }
        }
    }
    LaunchedEffect(permissions) { refreshPermissionStates() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
            Spacer(modifier = Modifier.height(LocalSettingsTopContentPadding.current))
            Box(modifier = Modifier.entrance()) {
                AppText(
                    text = "以下是应用所需的权限及其用途说明。普通权限在安装时自动授予，无需手动操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("需要授权的权限")
            }
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    permissions.filter { !it.isNormal }.forEachIndexed { index, info ->
                        if (index > 0) AppHorizontalDivider()
                        PermissionItem(
                            info = info,
                            isGranted = permissionStates[info.permission] ?: false,
                            onOpenSettings = { openAppSettings(context) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.entrance()) {
                AppPreferenceSectionTitle("自动授予的权限")
            }
            Box(modifier = Modifier.entrance()) {
                AppPreferenceGroup {
                    permissions.filter { it.isNormal }.forEachIndexed { index, info ->
                        if (index > 0) AppHorizontalDivider()
                        PermissionItem(
                            info = info,
                            isGranted = true,
                            onOpenSettings = null,
                        )
                    }
                }
            }

            Box(modifier = Modifier.entrance()) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    AppText(
                        text = "BiliPai 仅在必要功能的前提下申请部分敏感权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
}

private data class PermissionInfo(
    val name: String,
    val permission: String,
    val description: String,
    @DrawableRes val iconResId: Int,
    val iconTint: Color,
    val isNormal: Boolean,  // 是否是普通权限（自动授予）
    val alwaysGranted: Boolean = false,  // 是否总是被授予
    val customCheck: ((Context) -> Boolean)? = null // 自定义检查逻辑
)

/**
 * 单个权限项
 */
@Composable
private fun PermissionItem(
    info: PermissionInfo,
    isGranted: Boolean,
    onOpenSettings: (() -> Unit)?
) {
    val permissionIcon = rememberMaterialSymbol(info.iconResId)
    val visualSpec = rememberAdaptiveListVisualCapabilities().componentSpec
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(info.iconTint)
    val iconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    val grantedTint = rememberAdaptiveSemanticIconTint(iOSGreen)
    val deniedTint = rememberAdaptiveSemanticIconTint(com.android.purebilibili.core.theme.iOSRed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpenSettings != null) { onOpenSettings?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(visualSpec.iconContainerSizeDp.dp)
                .adaptiveSquircleBackground(
                    color = effectiveIconTint,
                    cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                permissionIcon,
                contentDescription = null,
                tint = iconContentColor,
                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // 名称和描述
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = info.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            AppText(
                text = info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 状态指示器
        if (isGranted) {
            AppIcon(
                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_check_circle_outline_24),
                contentDescription = "已授权",
                tint = grantedTint,
                modifier = Modifier.size(22.dp)
            )
        } else {
            // 未授权时显示红色的 X
            AppIcon(
                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_cancel_24),
                contentDescription = "未授权",
                tint = deniedTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 打开应用设置页面
 */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
