package com.android.purebilibili.feature.settings.webdav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceGroup
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.settings.backup.AppBackupScope
import com.android.purebilibili.feature.settings.backup.formatBackupSize
import com.android.purebilibili.feature.settings.backup.formatBackupTimestamp
import com.android.purebilibili.feature.settings.rememberOriginalThemeAwareSettingsIcon
import com.android.purebilibili.feature.settings.rememberSettingsVector
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.UploadCloud

private enum class WebDavEditMode {
    SERVER,
    ACCOUNT,
    REMOTE_DIR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavBackupScreen(
    onBack: () -> Unit,
    viewModel: WebDavBackupViewModel = viewModel(),
) {
    val screenTitle = stringResource(R.string.webdav_backup_title)
    val backLabel = stringResource(R.string.common_back)
    val refreshLabel = stringResource(R.string.common_refresh)
    val saveLabel = stringResource(R.string.common_save)
    val cancelLabel = stringResource(R.string.common_cancel)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("WebDavBackupScreen")
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showRestoreLatestConfirm by remember { mutableStateOf(false) }
    var showQrTransferDialog by remember { mutableStateOf(false) }
    var selectedRemoteEntry by remember { mutableStateOf<WebDavBackupEntry?>(null) }
    var editMode by remember { mutableStateOf(WebDavEditMode.SERVER) }

    var draftBaseUrl by remember { mutableStateOf("") }
    var draftUsername by remember { mutableStateOf("") }
    var draftPassword by remember { mutableStateOf("") }
    var draftRemoteDir by remember { mutableStateOf(DEFAULT_WEBDAV_REMOTE_DIR) }
    var draftEnabled by remember { mutableStateOf(false) }

    val exportLocalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            com.android.purebilibili.core.util.AnalyticsHelper.logBackupAction("local_zip", "export")
            viewModel.exportLocalBackup(uri)
        }
    }

    val importLocalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.inspectLocalBackup(uri)
        }
    }

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            draftBaseUrl = uiState.config.baseUrl
            draftUsername = uiState.config.username
            draftPassword = uiState.config.password
            draftRemoteDir = uiState.config.remoteDir
            draftEnabled = uiState.config.enabled
        }
    }

    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
        actions = {
            AppIconButton(onClick = { viewModel.refreshRemoteBackups() }) {
                AppIcon(
                    imageVector = rememberSettingsVector(R.drawable.ph_arrows_clockwise_24, R.drawable.ms_refresh_fill_24),
                    contentDescription = refreshLabel,
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = settingsScrollContentPadding(extraBottom = 24.dp),
            ) {
                item(key = "status", contentType = "status") {
                    AppPreferenceSectionTitle("状态")
                    AppPreferenceGroup {
                        val statusText = when {
                            uiState.isBusy -> uiState.statusMessage ?: "正在处理…"
                            uiState.restoreRequiresRestart -> uiState.statusMessage ?: "恢复完成，重启应用后生效"
                            !uiState.statusMessage.isNullOrBlank() -> uiState.statusMessage ?: ""
                            else -> "不包含账号 Cookie 与 WebDAV 密码，可放心备份"
                        }
                        AppPreference(
                            icon = if (uiState.restoreRequiresRestart) {
                                rememberSettingsVector(R.drawable.ph_warning_24, R.drawable.ms_warning_fill_24)
                            } else {
                                rememberSettingsVector(R.drawable.ph_info_24, R.drawable.ms_info_fill_24)
                            },
                            title = if (uiState.restoreRequiresRestart) "需要重启应用" else "执行状态",
                            value = statusText,
                            onClick = if (uiState.statusMessage != null) ({ viewModel.clearStatus() }) else null,
                            iconTint = if (uiState.restoreRequiresRestart) AppContentColors.warning() else AppContentColors.accent(),
                            showChevron = false,
                        )
                    }
                }

                item(key = "scopes", contentType = "scopes") {
                    AppPreferenceSectionTitle("备份范围")
                    AppPreferenceGroup {
                        AppSwitchPreference(
                            icon = rememberSettingsVector(R.drawable.ph_sliders_24, R.drawable.ms_tune_24),
                            title = AppBackupScope.SETTINGS.label,
                            subtitle = AppBackupScope.SETTINGS.description,
                            checked = AppBackupScope.SETTINGS in uiState.selectedScopes,
                            onCheckedChange = { viewModel.toggleScope(AppBackupScope.SETTINGS) },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppSwitchPreference(
                            icon = rememberSettingsVector(R.drawable.ph_puzzle_piece_24, R.drawable.ms_extension_fill_24),
                            title = AppBackupScope.PLUGINS.label,
                            subtitle = AppBackupScope.PLUGINS.description,
                            checked = AppBackupScope.PLUGINS in uiState.selectedScopes,
                            onCheckedChange = { viewModel.toggleScope(AppBackupScope.PLUGINS) },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppSwitchPreference(
                            icon = rememberSettingsVector(R.drawable.ph_bookmarks_24, R.drawable.ms_collections_bookmark_fill_24),
                            title = AppBackupScope.PERSONAL.label,
                            subtitle = AppBackupScope.PERSONAL.description,
                            checked = AppBackupScope.PERSONAL in uiState.selectedScopes,
                            onCheckedChange = { viewModel.toggleScope(AppBackupScope.PERSONAL) },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }
                }

                item(key = "local_backup", contentType = "local_backup") {
                    AppPreferenceSectionTitle("本地备份与恢复")
                    AppPreferenceGroup {
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_file_arrow_up_24, R.drawable.ms_upload_file_fill_24),
                            title = "导出本地备份包",
                            subtitle = "将所选范围打包为 ZIP 文件保存到本机",
                            onClick = {
                                exportLocalLauncher.launch(buildWebDavBackupFileName(System.currentTimeMillis()))
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_file_arrow_down_24, R.drawable.ms_file_download_24),
                            title = "从本地备份包恢复",
                            subtitle = "选择备份 ZIP 文件，预览内容后按所选范围恢复",
                            onClick = {
                                importLocalLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = Icons.Outlined.QrCode2,
                            title = "面对面扫码传输",
                            subtitle = "两台设备间加密同步设置、插件与完结纪念，不覆盖当前账号",
                            onClick = { showQrTransferDialog = true },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }
                }

                item(key = "webdav_config", contentType = "webdav_config") {
                    AppPreferenceSectionTitle("WebDAV 云端配置")
                    AppPreferenceGroup {
                        AppSwitchPreference(
                            icon = rememberSettingsVector(R.drawable.ph_cloud_24, R.drawable.ms_cloud_fill_24),
                            title = "每天自动备份",
                            subtitle = "每天自动上传完整快照，也可随时手动备份",
                            checked = uiState.config.enabled,
                            onCheckedChange = { viewModel.setEnabled(it) },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_hard_drives_24, R.drawable.ms_storage_fill_24),
                            title = "服务器",
                            value = uiState.config.baseUrl.ifBlank { "未配置" },
                            onClick = {
                                editMode = WebDavEditMode.SERVER
                                showEditDialog = true
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_user_24, R.drawable.ms_person_fill_24),
                            title = "用户名",
                            value = uiState.config.username.ifBlank { "未配置" },
                            onClick = {
                                editMode = WebDavEditMode.ACCOUNT
                                showEditDialog = true
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_folder_open_24, R.drawable.ms_drive_folder_upload_fill_24),
                            title = "云端文件夹",
                            value = uiState.config.remoteDir,
                            onClick = {
                                editMode = WebDavEditMode.REMOTE_DIR
                                showEditDialog = true
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_stack_24, R.drawable.ms_layers_24),
                            title = "自动保留份数",
                            value = if (uiState.config.maxRemoteBackups <= 0) {
                                "不限制"
                            } else {
                                "最近 ${uiState.config.maxRemoteBackups} 份"
                            },
                            onClick = { showRetentionDialog = true },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }
                }

                item(key = "webdav_actions", contentType = "webdav_actions") {
                    AppPreferenceSectionTitle("WebDAV 操作")
                    AppPreferenceGroup {
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_cloud_check_24) {
                                rememberOriginalThemeAwareSettingsIcon(R.drawable.ms_cloud_done_24, MiuixIcons.UploadCloud)
                            },
                            title = "测试连接",
                            subtitle = "检查账号和云端文件夹是否可用",
                            onClick = { viewModel.testConnection() },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_archive_24, R.drawable.ms_backup_fill_24),
                            title = "立即上传云端备份",
                            subtitle = "按上方勾选范围打包并上传至 WebDAV",
                            onClick = { viewModel.backupNow() },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_clock_clockwise_24) {
                                rememberOriginalThemeAwareSettingsIcon(R.drawable.ms_settings_backup_restore_24, MiuixIcons.Reset)
                            },
                            title = "恢复最新云端备份",
                            subtitle = "按上方勾选范围覆盖本地数据，建议先备份",
                            onClick = { showRestoreLatestConfirm = true },
                            iconTint = AppContentColors.secondaryText(),
                        )
                        AppPreferenceDivider(startIndent = 66.dp)
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_arrows_clockwise_24, R.drawable.ms_refresh_fill_24),
                            title = "刷新云端备份列表",
                            subtitle = "读取 WebDAV 目录中的历史快照",
                            onClick = { viewModel.refreshRemoteBackups() },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }
                }

                item(key = "remote_list", contentType = "remote_list") {
                    AppPreferenceSectionTitle("云端备份快照")
                    AppPreferenceGroup {
                        if (uiState.remoteBackups.isEmpty()) {
                            AppPreference(
                                icon = rememberSettingsVector(R.drawable.ph_package_24, R.drawable.ms_inventory2_fill_24),
                                title = "暂无云端备份",
                                value = "可先点击“立即上传云端备份”生成第一份快照",
                                onClick = null,
                                iconTint = AppContentColors.secondaryText(),
                                showChevron = false,
                            )
                        } else {
                            val visibleEntries = uiState.remoteBackups.take(12)
                            visibleEntries.forEachIndexed { index, entry ->
                                val timeLabel = formatBackupTimestamp(entry.lastModifiedEpochMs)
                                val sizeLabel = formatBackupSize(entry.sizeBytes)
                                AppPreference(
                                    icon = rememberSettingsVector(R.drawable.ph_folders_24, R.drawable.ms_folder_copy_fill_24),
                                    title = entry.fileName,
                                    subtitle = "$timeLabel · $sizeLabel · 点按恢复或删除",
                                    onClick = { selectedRemoteEntry = entry },
                                    iconTint = AppContentColors.secondaryText(),
                                )
                                if (index != visibleEntries.lastIndex) {
                                    AppPreferenceDivider(startIndent = 66.dp)
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isBusy) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveLoadingIndicator()
                }
            }
        }
    }

    if (showEditDialog) {
        val dialogTitle = when (editMode) {
            WebDavEditMode.SERVER -> "服务器地址"
            WebDavEditMode.ACCOUNT -> "账号信息"
            WebDavEditMode.REMOTE_DIR -> "云端文件夹"
        }
        AppAlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { AppText(dialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (editMode) {
                        WebDavEditMode.SERVER -> {
                            AppTextField(
                                value = draftBaseUrl,
                                onValueChange = { draftBaseUrl = it },
                                label = "服务器地址",
                                placeholder = "https://dav.example.com/remote.php/dav/files/<user>",
                                singleLine = true,
                            )
                        }

                        WebDavEditMode.ACCOUNT -> {
                            AppTextField(
                                value = draftUsername,
                                onValueChange = { draftUsername = it },
                                label = "用户名",
                                singleLine = true,
                            )
                            AppTextField(
                                value = draftPassword,
                                onValueChange = { draftPassword = it },
                                label = "密码",
                                singleLine = true,
                            )
                        }

                        WebDavEditMode.REMOTE_DIR -> {
                            AppTextField(
                                value = draftRemoteDir,
                                onValueChange = { draftRemoteDir = it },
                                label = "云端文件夹",
                                placeholder = "/BiliPai/backups",
                                singleLine = true,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText("启用自动备份", modifier = Modifier.weight(1f))
                                AppSwitch(
                                    checked = draftEnabled,
                                    onCheckedChange = { draftEnabled = it },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        viewModel.saveConfig(
                            WebDavBackupConfig(
                                enabled = draftEnabled,
                                baseUrl = draftBaseUrl,
                                username = draftUsername,
                                password = draftPassword,
                                remoteDir = draftRemoteDir,
                                maxRemoteBackups = uiState.config.maxRemoteBackups,
                            )
                        )
                        showEditDialog = false
                    },
                ) {
                    AppText(saveLabel)
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { showEditDialog = false }) {
                    AppText(cancelLabel)
                }
            },
        )
    }

    if (showQrTransferDialog) {
        com.android.purebilibili.feature.login.BiliPaiTransferDialog(
            onDismiss = { showQrTransferDialog = false },
            onContinueLogin = { showQrTransferDialog = false },
            defaultContentOnly = true,
        )
    }

    if (showRetentionDialog) {
        AppAlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { AppText("云端自动保留份数", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(
                        "上传新备份后自动清理超出上限的旧快照，避免网盘空间持续增长。",
                        color = AppContentColors.secondaryText(),
                    )
                    WEBDAV_RETENTION_PRESETS.forEach { preset ->
                        val selected = uiState.config.maxRemoteBackups == preset
                        val label = if (preset <= 0) "不限制（始终保留全部）" else "保留最近 $preset 份"
                        AppDialogAction(
                            onClick = {
                                viewModel.setMaxRemoteBackups(preset)
                                showRetentionDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText(
                                text = if (selected) "✓ $label" else label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AppContentColors.accent() else AppContentColors.primaryText(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogAction(onClick = { showRetentionDialog = false }) {
                    AppText(cancelLabel)
                }
            },
        )
    }

    if (showRestoreLatestConfirm) {
        val scopeSummary = uiState.selectedScopes.joinToString("、") { it.label }
        AppAlertDialog(
            onDismissRequest = { showRestoreLatestConfirm = false },
            title = { AppText("确认恢复最新云端备份", fontWeight = FontWeight.Bold) },
            text = {
                AppText("将覆盖本地「$scopeSummary」，不影响账号登录态与 WebDAV 密码。恢复完成后请重启应用以完全生效。")
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        showRestoreLatestConfirm = false
                        viewModel.restoreLatest()
                    },
                ) {
                    AppText("确认恢复")
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { showRestoreLatestConfirm = false }) {
                    AppText(cancelLabel)
                }
            },
        )
    }

    selectedRemoteEntry?.let { entry ->
        val scopeSummary = uiState.selectedScopes.joinToString("、") { it.label }
        AppAlertDialog(
            onDismissRequest = { selectedRemoteEntry = null },
            title = { AppText("管理云端快照", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(entry.fileName, fontWeight = FontWeight.Medium)
                    AppText(
                        "${formatBackupTimestamp(entry.lastModifiedEpochMs)} · ${formatBackupSize(entry.sizeBytes)}",
                        color = AppContentColors.secondaryText(),
                    )
                    AppText("恢复时将按当前勾选范围（$scopeSummary）覆盖本地数据。")
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        selectedRemoteEntry = null
                        viewModel.restoreRemoteBackup(entry)
                    },
                ) {
                    AppText("恢复此快照")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDialogAction(
                        onClick = {
                            selectedRemoteEntry = null
                            viewModel.deleteRemoteBackup(entry)
                        },
                    ) {
                        AppText("删除", color = MaterialTheme.colorScheme.error)
                    }
                    AppDialogAction(onClick = { selectedRemoteEntry = null }) {
                        AppText(cancelLabel)
                    }
                }
            },
        )
    }

    uiState.pendingLocalRestore?.let { session ->
        val inspection = session.inspection
        val detectedLabels = inspection.detectedScopes.joinToString("、") { it.label }.ifBlank { "未知" }
        val selectedLabels = uiState.selectedScopes.joinToString("、") { it.label }
        val versionSummary = inspection.manifest?.appVersion?.takeIf { it.isNotBlank() }?.let { "来源版本：$it" }
            ?: if (inspection.isLegacyV1) "格式：兼容旧版 v1 备份包" else "格式：BiliPai 备份包"
        AppAlertDialog(
            onDismissRequest = { viewModel.dismissLocalRestore() },
            title = { AppText("确认从本地备份恢复", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(versionSummary)
                    AppText("检测到 ${inspection.restorableEntryCount} 项数据：$detectedLabels")
                    AppText("本次将恢复当前勾选的「$selectedLabels」，完成后请重启应用生效。")
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = { viewModel.confirmLocalRestore() },
                ) {
                    AppText("确认恢复")
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { viewModel.dismissLocalRestore() }) {
                    AppText(cancelLabel)
                }
            },
        )
    }
}
