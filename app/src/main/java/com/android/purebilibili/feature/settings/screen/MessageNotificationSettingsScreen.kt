package com.android.purebilibili.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.ui.components.AppChoiceOption
import com.android.purebilibili.core.ui.components.AppPreference as SettingClickableItem
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSingleChoicePreference
import com.android.purebilibili.core.ui.components.AppSwitchPreference as SettingSwitchItem
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.message.notification.MessageNotificationMode
import com.android.purebilibili.feature.message.notification.MessageNotificationNotifier
import com.android.purebilibili.feature.message.notification.MessageNotificationSettings
import com.android.purebilibili.feature.message.notification.MessageNotificationSettingsStore
import com.android.purebilibili.feature.message.notification.MessageNotificationSync
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class NotificationPermissionRequest {
    MASTER_ENABLE,
    PERMISSION_ROW,
}

@Composable
fun MessageNotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settingsFlow = remember(context) { MessageNotificationSettingsStore.getSettings(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = MessageNotificationSettings())
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var notificationPermissionGranted by remember(context) {
        mutableStateOf(resolveNotificationPermissionGranted(context))
    }
    var notificationsCanPost by remember(context) { mutableStateOf(false) }
    var permissionRequestAttempted by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingPermissionRequest by rememberSaveable {
        mutableStateOf<NotificationPermissionRequest?>(null)
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshNotificationStatus(): Boolean {
        val permissionGranted = resolveNotificationPermissionGranted(context)
        val canPost = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                MessageNotificationNotifier.ensureChannels(context)
            }
            MessageNotificationNotifier.canPost(context)
        }.getOrDefault(false)
        notificationPermissionGranted = permissionGranted
        notificationsCanPost = canPost
        return permissionGranted && canPost
    }

    fun openNotificationSettings() {
        statusMessage = if (notificationsCanPost) null else "通知权限或通知渠道未开启，请在系统设置中允许通知"
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching {
            context.startActivity(notificationIntent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    },
                )
            }.onFailure {
                Toast.makeText(context, "无法打开系统通知设置", Toast.LENGTH_SHORT).show()
            }
        }
    }


    suspend fun persistAndSync(save: suspend () -> Unit) {
        try {
            statusMessage = null
            save()
            if (!MessageNotificationSync.sync(context)) {
                statusMessage = "设置已保存，但常驻后台未能启动，将使用系统周期检查"
                Toast.makeText(
                    context,
                    "常驻后台启动失败，已保留周期检查作为兜底",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() }
                ?: "设置保存失败，请稍后重试"
            statusMessage = message
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestAttempted = true
        val request = pendingPermissionRequest
        pendingPermissionRequest = null
        val usable = refreshNotificationStatus()
        if (request == NotificationPermissionRequest.MASTER_ENABLE) {
            if (!granted) {
                statusMessage = "未授予通知权限，无法开启后台通知；可点下方通知权限前往系统设置"
                Toast.makeText(
                    context,
                    "未授予通知权限，无法开启后台通知",
                    Toast.LENGTH_SHORT,
                ).show()
            } else if (usable) {
                scope.launch {
                    persistAndSync { MessageNotificationSettingsStore.setEnabled(context, true) }
                }
            } else {
                statusMessage = "通知权限已授予，但系统通知或通知渠道仍处于关闭状态"
                Toast.makeText(context, "请先在系统设置中开启通知", Toast.LENGTH_SHORT).show()
            }
        } else if (request == NotificationPermissionRequest.PERMISSION_ROW) {
            if (granted && usable) {
                scope.launch {
                    persistAndSync {}
                }
            } else {
                val message = if (!granted) {
                    "通知权限未开启，可再次点击此项前往系统设置"
                } else {
                    "通知权限已授予，但系统通知或通知渠道仍处于关闭状态"
                }
                statusMessage = message
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun requestNotificationPermission(request: NotificationPermissionRequest) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            refreshNotificationStatus()
            return
        }
        pendingPermissionRequest = request
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(context) {
        refreshNotificationStatus()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNotificationStatus()
                scope.launch { persistAndSync {} }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionValue = when {
        !notificationPermissionGranted && permissionRequestAttempted -> "未开启 · 前往系统设置"
        !notificationPermissionGranted -> "未开启"
        !notificationsCanPost -> "系统已关闭 · 前往系统设置"
        else -> "已开启"
    }
    val masterSubtitle = when {
        !notificationPermissionGranted -> "应用关闭后定期检查新消息；未登录时不检查。请先开启通知权限"
        !notificationsCanPost -> "应用关闭后定期检查新消息；系统通知或通知渠道已关闭"
        else -> "应用关闭后定期检查新消息；未登录时不检查"
    }

    SettingsPageScaffold(
        title = "消息通知",
        onBack = onBack,
        backContentDescription = "返回",
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
        externalContentHandlesTopPadding = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(LocalSettingsTopContentPadding.current))
            AppText(
                text = "后台消息通知会在应用不活跃时检查私信、互动消息、关注更新和开播提醒。检查频率与常驻后台可能增加耗电，系统仍可能终止后台任务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            statusMessage?.let { message ->
                AppText(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSOrange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            AppPreferenceSectionTitle("后台通知")
            SettingsCardGroup {
                SettingSwitchItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                    title = "后台消息通知",
                    subtitle = masterSubtitle,
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setEnabled(context, false)
                                }
                            }
                        } else if (!notificationPermissionGranted) {
                            if (permissionRequestAttempted) {
                                openNotificationSettings()
                            } else {
                                requestNotificationPermission(NotificationPermissionRequest.MASTER_ENABLE)
                            }
                        } else if (!notificationsCanPost) {
                            openNotificationSettings()
                        } else {
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setEnabled(context, true)
                                }
                            }
                        }
                    },
                    iconTint = iOSBlue,
                )
                AppPreferenceDivider()
                AppSingleChoicePreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                    title = "检查频率",
                    subtitle = "省电优先可减少耗电，更及时会增加检查频率",
                    selectedValue = settings.mode,
                    options = listOf(
                        AppChoiceOption(
                            value = MessageNotificationMode.POWER_SAVING,
                            label = "省电优先",
                            description = "系统空闲时批量检查，延迟较高、耗电低（默认）",
                        ),
                        AppChoiceOption(
                            value = MessageNotificationMode.MORE_TIMELY,
                            label = "更及时",
                            description = "检查更频繁，耗电略增",
                        ),
                    ),
                    onValueChange = { mode ->
                        scope.launch {
                            persistAndSync {
                                MessageNotificationSettingsStore.setMode(context, mode)
                            }
                        }
                    },
                    iconTint = iOSGreen,
                )
            }

            AppPreferenceSectionTitle("通知范围")
            SettingsCardGroup {
                SettingSwitchItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                    title = "消息中心",
                    subtitle = "私信、回复、@我、收到的赞和系统通知；仅在总开关开启时生效",
                    checked = settings.notifyMessageCenter,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            persistAndSync {
                                MessageNotificationSettingsStore.setMessageCenterEnabled(context, enabled)
                            }
                        }
                    },
                    iconTint = iOSPurple,
                )
                AppPreferenceDivider()
                SettingSwitchItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_FEED),
                    title = "关注 UP 更新",
                    subtitle = "关注 UP 主的视频和动态更新；仅在总开关开启时生效",
                    checked = settings.notifyDynamicUpdates,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            persistAndSync {
                                MessageNotificationSettingsStore.setDynamicUpdatesEnabled(context, enabled)
                            }
                        }
                    },
                    iconTint = iOSBlue,
                )
                AppPreferenceDivider()
                SettingSwitchItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.LIVE_SURFACE_TRANSITION),
                    title = "开播提醒",
                    subtitle = "关注主播开播提醒；仅在总开关开启时生效",
                    checked = settings.notifyLiveAlerts,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            persistAndSync {
                                MessageNotificationSettingsStore.setLiveAlertsEnabled(context, enabled)
                            }
                        }
                    },
                    iconTint = iOSOrange,
                )
            }

            AppPreferenceSectionTitle("后台运行")
            SettingsCardGroup {
                SettingSwitchItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.BATTERY_STATUS),
                    title = "常驻后台",
                    subtitle = if (settings.enabled && notificationsCanPost) {
                        "保持应用后台运行以更及时地收到通知；会增加耗电，系统仍可能终止"
                    } else {
                        "需要先开启后台消息通知和可用的通知权限；会增加耗电，系统仍可能终止"
                    },
                    checked = settings.residentEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setResidentEnabled(context, false)
                                }
                            }
                        } else if (!settings.enabled) {
                            statusMessage = "请先开启后台消息通知，再启用常驻后台"
                            Toast.makeText(context, "请先开启后台消息通知，再启用常驻后台", Toast.LENGTH_SHORT).show()
                        } else if (!notificationPermissionGranted || !notificationsCanPost) {
                            statusMessage = "请先在通知权限中开启通知，并确保通知渠道未被系统关闭"
                            Toast.makeText(
                                context,
                                "请先在通知权限中开启通知，并确保通知渠道未被系统关闭",
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (notificationPermissionGranted && !notificationsCanPost) {
                                openNotificationSettings()
                            }
                        } else {
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setResidentEnabled(context, true)
                                }
                            }
                        }
                    },
                    iconTint = iOSOrange,
                )

            }

            AppPreferenceSectionTitle("通知权限")
            SettingsCardGroup {
                SettingClickableItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PERMISSION),
                    title = "通知权限",
                    value = notificationPermissionValue,
                    subtitle = "点击检查权限；永久拒绝、系统总开关或通知渠道关闭时前往系统设置",
                    onClick = {
                        if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (permissionRequestAttempted) {
                                openNotificationSettings()
                            } else {
                                requestNotificationPermission(NotificationPermissionRequest.PERMISSION_ROW)
                            }
                        } else {
                            openNotificationSettings()
                        }
                    },
                    iconTint = iOSGreen,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun resolveNotificationPermissionGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}
