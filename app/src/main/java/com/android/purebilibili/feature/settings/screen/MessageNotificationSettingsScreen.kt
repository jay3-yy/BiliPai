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
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
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
import com.android.purebilibili.R
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
import com.android.purebilibili.core.push.CustomPushConfig
import com.android.purebilibili.core.push.PushChannelMode
import com.android.purebilibili.core.push.PushConfigStore
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
fun MessageNotificationSettingsScreen(
    onBack: () -> Unit,
    onCustomPushClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settingsFlow = remember(context) { MessageNotificationSettingsStore.getSettings(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = MessageNotificationSettings())
    val customPushFlow = remember(context) { PushConfigStore.observeConfig(context) }
    val customPushConfig by customPushFlow.collectAsStateWithLifecycle(initialValue = CustomPushConfig())
    val customPushCopy = remember { settingsDestinationCopy(SettingsSearchTarget.CUSTOM_PUSH) }
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
        statusMessage = if (notificationsCanPost) null else context.getString(R.string.notify_open_permission_message)
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
                Toast.makeText(context, context.getString(R.string.notify_open_settings_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }


    suspend fun persistAndSync(save: suspend () -> Unit) {
        try {
            statusMessage = null
            save()
            if (!MessageNotificationSync.sync(context)) {
                statusMessage = context.getString(R.string.notify_background_fallback_status)
                Toast.makeText(
                    context,
                    context.getString(R.string.notify_background_fallback_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.notify_save_failed)
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
                statusMessage = context.getString(R.string.notify_permission_denied_status)
                Toast.makeText(
                    context,
                    context.getString(R.string.notify_permission_denied),
                    Toast.LENGTH_SHORT,
                ).show()
            } else if (usable) {
                scope.launch {
                    persistAndSync { MessageNotificationSettingsStore.setEnabled(context, true) }
                }
            } else {
                statusMessage = context.getString(R.string.notify_permission_channels_off)
                Toast.makeText(context, context.getString(R.string.notify_enable_in_settings), Toast.LENGTH_SHORT).show()
            }
        } else if (request == NotificationPermissionRequest.PERMISSION_ROW) {
            if (granted && usable) {
                scope.launch {
                    persistAndSync {}
                }
            } else {
                val message = if (!granted) {
                    context.getString(R.string.notify_permission_not_enabled)
                } else {
                    context.getString(R.string.notify_permission_channels_off)
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
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("MessageNotificationSettingsScreen")
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
        !notificationPermissionGranted && permissionRequestAttempted -> context.getString(R.string.notify_permission_off_open_settings)
        !notificationPermissionGranted -> context.getString(R.string.notify_permission_off)
        !notificationsCanPost -> context.getString(R.string.notify_system_off_open_settings)
        else -> context.getString(R.string.notify_enabled)
    }
    val masterSubtitle = when {
        !notificationPermissionGranted -> context.getString(R.string.notify_master_subtitle_permission)
        !notificationsCanPost -> context.getString(R.string.notify_master_subtitle_channels)
        else -> context.getString(R.string.notify_master_subtitle)
    }

    SettingsPageScaffold(
        title = context.getString(R.string.notify_settings_title),
        onBack = onBack,
        backContentDescription = context.getString(R.string.notify_back),
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
        externalContentHandlesTopPadding = true,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(LocalSettingsTopContentPadding.current))
            AppText(
                text = context.getString(R.string.notify_intro),
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

            AppPreferenceSectionTitle(context.getString(R.string.notify_section_background))
            SettingsCardGroup {
                SettingsItemAnchor("notification.enabled") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                        title = context.getString(R.string.notify_item_enabled),
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
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_DELIVERY_MODE),
                        title = context.getString(R.string.notify_item_mode),
                        subtitle = if (!settings.enabled) {
                            context.getString(R.string.notify_enable_master_first)
                        } else {
                            context.getString(R.string.notify_mode_subtitle)
                        },
                        selectedValue = settings.mode,
                        enabled = settings.enabled,
                        options = listOf(
                            AppChoiceOption(
                                value = MessageNotificationMode.POWER_SAVING,
                                label = context.getString(R.string.notify_mode_saving),
                                description = context.getString(R.string.notify_mode_saving_desc),
                            ),
                            AppChoiceOption(
                                value = MessageNotificationMode.MORE_TIMELY,
                                label = context.getString(R.string.notify_mode_timely),
                                description = context.getString(R.string.notify_mode_timely_desc),
                            ),
                        ),
                        onValueChange = { mode ->
                            com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange(
                                "message_notification_mode",
                                mode.name.lowercase(),
                            )
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

            AppPreferenceSectionTitle(context.getString(R.string.notify_section_scope))
            SettingsCardGroup {
                SettingsItemAnchor("notification.notify_private_messages") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_SCOPE_MESSAGE),
                        title = context.getString(R.string.notify_item_private_messages),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_private_messages),
                        checked = settings.notifyPrivateMessages,
                        enabled = settings.enabled,
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
                        title = context.getString(R.string.notify_item_replies),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_replies),
                        checked = settings.notifyReplies,
                        enabled = settings.enabled,
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
                        title = context.getString(R.string.notify_item_at_me),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_at_me),
                        checked = settings.notifyAtMe,
                        enabled = settings.enabled,
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
                        title = context.getString(R.string.notify_item_likes),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_likes),
                        checked = settings.notifyLikes,
                        enabled = settings.enabled,
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
                        title = context.getString(R.string.notify_item_system),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_system),
                        checked = settings.notifySystemNotices,
                        enabled = settings.enabled,
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
                        title = context.getString(R.string.notify_item_dynamic),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_dynamic),
                        checked = settings.notifyDynamicUpdates,
                        enabled = settings.enabled,
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
                        title = context.getString(R.string.notify_item_live),
                        subtitle = if (!settings.enabled) context.getString(R.string.notify_enable_master_first) else context.getString(R.string.notify_live),
                        checked = settings.notifyLiveAlerts,
                        enabled = settings.enabled,
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

            AppPreferenceSectionTitle(context.getString(R.string.notify_section_resident))
            SettingsCardGroup {
                SettingsItemAnchor("notification.resident_enabled") {
                    SettingSwitchItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.BATTERY_STATUS),
                        title = context.getString(R.string.notify_item_resident),
                        subtitle = if (settings.enabled && notificationsCanPost) {
                            context.getString(R.string.notify_resident_enabled_desc)
                        } else {
                            context.getString(R.string.notify_resident_requires_desc)
                        },
                        checked = settings.residentEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                scope.launch {
                                    persistAndSync {
                                        MessageNotificationSettingsStore.setResidentEnabled(context, false)
                                    }
                                }
                            } else if (!settings.enabled) {
                                statusMessage = context.getString(R.string.notify_resident_first_status)
                                Toast.makeText(context, context.getString(R.string.notify_resident_first_status), Toast.LENGTH_SHORT).show()
                            } else if (!notificationPermissionGranted || !notificationsCanPost) {
                                statusMessage = context.getString(R.string.notify_permission_setup_status)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.notify_permission_setup_status),
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

            AppPreferenceSectionTitle("远程推送")
            SettingsCardGroup {
                SettingsItemAnchor("notification.custom_push") {
                    SettingClickableItem(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.CUSTOM_PUSH),
                        title = customPushCopy.title,
                        subtitle = customPushCopy.summary,
                        value = when (customPushConfig.mode) {
                            PushChannelMode.DISABLED -> "关闭"
                            PushChannelMode.UNIFIED_PUSH -> "UnifiedPush"
                            PushChannelMode.FCM_RELAY -> "FCM 中转"
                        },
                        onClick = onCustomPushClick,
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
            }

            AppPreferenceSectionTitle(context.getString(R.string.notify_section_send))
            SettingsCardGroup {
                SettingClickableItem(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PERMISSION),
                    title = context.getString(R.string.notify_section_send),
                    value = notificationPermissionValue,
                    subtitle = context.getString(R.string.notify_permission_subtitle),
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
