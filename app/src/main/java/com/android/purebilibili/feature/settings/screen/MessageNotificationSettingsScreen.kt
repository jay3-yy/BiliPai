package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors

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
                text = "离开应用后也检查新消息。更频繁检查和保持后台运行会增加耗电；系统可能中止检查。",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            statusMessage?.let { message ->
                AppText(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.warning(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            AppPreferenceSectionTitle("后台通知")
            SettingsCardGroup {
                SettingsItemAnchor("notification.enabled") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                        title = settingItemTitle("notification.enabled"),
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
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.mode") {
                    AppSingleChoicePreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                        title = settingItemTitle("notification.mode"),
                        subtitle = "省电优先可减少耗电，更及时会增加检查频率",
                        selectedValue = settings.mode,
                        options = listOf(
                            AppChoiceOption(
                                value = MessageNotificationMode.POWER_SAVING,
                                label = "省电优先",
                                description = "检查较少，通知可能延迟，耗电更低",
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
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
            }

            AppPreferenceSectionTitle("通知范围")
            SettingsCardGroup {
                SettingsItemAnchor("notification.notify_private_messages") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_MESSAGE),
                        title = settingItemTitle("notification.notify_private_messages"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "收到新私信时通知",
                        checked = settings.notifyPrivateMessages,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setPrivateMessagesEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.notify_replies") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_REPLY),
                        title = settingItemTitle("notification.notify_replies"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "有人回复视频或动态评论时通知",
                        checked = settings.notifyReplies,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setRepliesEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.notify_at_me") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_AT_ME),
                        title = settingItemTitle("notification.notify_at_me"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "有人在评论或动态中提到我时通知",
                        checked = settings.notifyAtMe,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setAtMeEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.notify_likes") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_LIKE),
                        title = settingItemTitle("notification.notify_likes"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "收到点赞、投币或收藏时通知",
                        checked = settings.notifyLikes,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setLikesEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.notify_system_notices") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_SYSTEM),
                        title = settingItemTitle("notification.notify_system_notices"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "收到 B 站系统消息时通知",
                        checked = settings.notifySystemNotices,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setSystemNoticesEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.notify_dynamic_updates") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_DYNAMIC_UP),
                        title = settingItemTitle("notification.notify_dynamic_updates"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "需 App 授权；UP 红点消失才清除通知，未知状态保留",
                        checked = settings.notifyDynamicUpdates,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setDynamicUpdatesEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
                AppPreferenceDivider()
                SettingsItemAnchor("notification.notify_live_alerts") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_LIVE),
                        title = settingItemTitle("notification.notify_live_alerts"),
                        subtitle = if (!settings.enabled) "先开启“后台消息通知”" else "关注的主播开播时通知",
                        checked = settings.notifyLiveAlerts,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                persistAndSync {
                                    MessageNotificationSettingsStore.setLiveAlertsEnabled(context, enabled)
                                }
                            }
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
            }

            AppPreferenceSectionTitle("后台运行")
            SettingsCardGroup {
                SettingsItemAnchor("notification.resident_enabled") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.BATTERY_STATUS),
                        title = settingItemTitle("notification.resident_enabled"),
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
                        iconTint = AppContentColors.secondaryText(),
                    )
                }

            }

            AppPreferenceSectionTitle("发送通知")
            SettingsCardGroup {
                SettingClickableItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PERMISSION),
                    title = "发送通知",
                    value = notificationPermissionValue,
                    subtitle = "点按检查授权；系统禁用通知时需前往设置",
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
                    iconTint = AppContentColors.secondaryText(),
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
