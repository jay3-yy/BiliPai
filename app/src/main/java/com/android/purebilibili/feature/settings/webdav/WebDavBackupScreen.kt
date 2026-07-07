package com.android.purebilibili.feature.settings.webdav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPink
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.feature.settings.settingsSubpageContainerColor
import com.android.purebilibili.feature.settings.settingsSubpageTopAppBarColors
import com.android.purebilibili.core.ui.IOSAlertDialog
import com.android.purebilibili.core.ui.IOSDialogAction
import com.android.purebilibili.core.ui.components.IOSClickableItem
import com.android.purebilibili.core.ui.components.IOSDivider
import com.android.purebilibili.core.ui.components.IOSGroup
import com.android.purebilibili.core.ui.components.IOSSectionTitle
import com.android.purebilibili.core.ui.components.IOSAdaptiveTextField
import com.android.purebilibili.core.ui.components.IOSSwitchItem
import com.android.purebilibili.core.ui.rememberAppBackIcon

private enum class WebDavEditMode {
    SERVER,
    ACCOUNT,
    REMOTE_DIR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavBackupScreen(
    onBack: () -> Unit,
    viewModel: WebDavBackupViewModel = viewModel()
) {
    val screenTitle = stringResource(R.string.webdav_backup_title)
    val backLabel = stringResource(R.string.common_back)
    val refreshLabel = stringResource(R.string.common_refresh)
    val saveLabel = stringResource(R.string.common_save)
    val cancelLabel = stringResource(R.string.common_cancel)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(WebDavEditMode.SERVER) }

    var draftBaseUrl by remember { mutableStateOf("") }
    var draftUsername by remember { mutableStateOf("") }
    var draftPassword by remember { mutableStateOf("") }
    var draftRemoteDir by remember { mutableStateOf(DEFAULT_WEBDAV_REMOTE_DIR) }
    var draftEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            draftBaseUrl = uiState.config.baseUrl
            draftUsername = uiState.config.username
            draftPassword = uiState.config.password
            draftRemoteDir = uiState.config.remoteDir
            draftEnabled = uiState.config.enabled
        }
    }

    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = screenTitle,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshRemoteBackups() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = refreshLabel
                        )
                    }
                },
                colors = settingsSubpageTopAppBarColors()
            )
        },
        containerColor = settingsSubpageContainerColor(),
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
            item {
                IOSSectionTitle("连接状态")
                IOSGroup {
                    val statusText = when {
                        uiState.isBusy -> uiState.statusMessage ?: "正在处理..."
                        // 恢复会覆盖本地持久化文件，进程重启后才能稳定读取新内容。
                        uiState.restoreRequiresRestart -> uiState.statusMessage ?: "恢复完成，重启应用后生效"
                        !uiState.statusMessage.isNullOrBlank() -> uiState.statusMessage ?: ""
                        else -> "尚未执行操作"
                    }
                    IOSClickableItem(
                        icon = if (uiState.restoreRequiresRestart) Icons.Filled.Warning else Icons.Filled.Info,
                        title = if (uiState.restoreRequiresRestart) "需要重启应用" else "执行状态",
                        value = statusText,
                        onClick = if (uiState.statusMessage != null) ({ viewModel.clearStatus() }) else null,
                        iconTint = if (uiState.restoreRequiresRestart) iOSOrange else iOSBlue,
                        showChevron = false
                    )
                }
            }

            item {
                IOSSectionTitle("配置")
                IOSGroup {
                    // 配置项图标按“能力/服务器/账号/目录”映射，降低识别成本。
                    IOSSwitchItem(
                        icon = Icons.Filled.Cloud,
                        title = "启用 WebDAV 云备份",
                        subtitle = "开启后每天自动备份，同时保留手动备份能力",
                        checked = uiState.config.enabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        iconTint = iOSBlue
                    )
                    IOSDivider(startIndent = 66.dp)
                    IOSClickableItem(
                        icon = Icons.Filled.Storage,
                        title = "服务器",
                        value = uiState.config.baseUrl.ifBlank { "未配置" },
                        onClick = {
                            editMode = WebDavEditMode.SERVER
                            showEditDialog = true
                        },
                        iconTint = iOSBlue
                    )
                    IOSDivider(startIndent = 66.dp)
                    IOSClickableItem(
                        icon = Icons.Filled.Person,
                        title = "用户名",
                        value = uiState.config.username.ifBlank { "未配置" },
                        onClick = {
                            editMode = WebDavEditMode.ACCOUNT
                            showEditDialog = true
                        },
                        iconTint = iOSBlue
                    )
                    IOSDivider(startIndent = 66.dp)
                    IOSClickableItem(
                        icon = Icons.Filled.Folder,
                        title = "远端目录",
                        value = uiState.config.remoteDir,
                        onClick = {
                            editMode = WebDavEditMode.REMOTE_DIR
                            showEditDialog = true
                        },
                        iconTint = iOSBlue
                    )
                }
            }

            item {
                IOSSectionTitle("操作")
                IOSGroup {
                    IOSClickableItem(
                        icon = Icons.Filled.CheckCircle,
                        title = "测试连接",
                        subtitle = "验证账号与目录可用性",
                        onClick = { viewModel.testConnection() },
                        iconTint = iOSGreen
                    )
                    IOSDivider(startIndent = 66.dp)
                    IOSClickableItem(
                        icon = Icons.Filled.Backup,
                        title = "立即备份",
                        subtitle = "上传当前设置与插件配置",
                        onClick = { viewModel.backupNow() },
                        iconTint = iOSBlue
                    )
                    IOSDivider(startIndent = 66.dp)
                    IOSClickableItem(
                        icon = Icons.Filled.Restore,
                        title = "恢复最新备份",
                        subtitle = "会覆盖本地设置，建议先手动备份",
                        onClick = { showRestoreConfirm = true },
                        iconTint = iOSPink
                    )
                    IOSDivider(startIndent = 66.dp)
                    IOSClickableItem(
                        icon = Icons.Filled.Refresh,
                        title = "刷新远端列表",
                        subtitle = "读取 WebDAV 目录中的备份文件",
                        onClick = { viewModel.refreshRemoteBackups() },
                        iconTint = iOSOrange
                    )
                }
            }

            item {
                IOSSectionTitle("远端备份")
                IOSGroup {
                    if (uiState.remoteBackups.isEmpty()) {
                        IOSClickableItem(
                            icon = Icons.Filled.Folder,
                            title = "暂无备份",
                            value = "可先点击“立即备份”生成第一份快照",
                            onClick = null,
                            iconTint = iOSOrange,
                            showChevron = false
                        )
                    } else {
                        uiState.remoteBackups.take(10).forEachIndexed { index, entry ->
                            IOSClickableItem(
                                icon = Icons.Filled.Folder,
                                title = entry.fileName,
                                value = "${entry.sizeBytes} B",
                                onClick = null,
                                iconTint = iOSBlue,
                                showChevron = false
                            )
                            if (index != uiState.remoteBackups.take(10).lastIndex) {
                                IOSDivider(startIndent = 66.dp)
                            }
                        }
                    }
                }
            }
            }

            if (uiState.isBusy) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showEditDialog) {
        val dialogTitle = when (editMode) {
            WebDavEditMode.SERVER -> "服务器地址"
            WebDavEditMode.ACCOUNT -> "账号信息"
            WebDavEditMode.REMOTE_DIR -> "远端目录"
        }
        IOSAlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (editMode) {
                        WebDavEditMode.SERVER -> {
                            IOSAdaptiveTextField(
                                value = draftBaseUrl,
                                onValueChange = { draftBaseUrl = it },
                                label = "服务器地址",
                                placeholder = "https://dav.example.com/remote.php/dav/files/<user>",
                                singleLine = true
                            )
                        }

                        WebDavEditMode.ACCOUNT -> {
                            IOSAdaptiveTextField(
                                value = draftUsername,
                                onValueChange = { draftUsername = it },
                                label = "用户名",
                                singleLine = true
                            )
                            IOSAdaptiveTextField(
                                value = draftPassword,
                                onValueChange = { draftPassword = it },
                                label = "密码",
                                singleLine = true
                            )
                        }

                        WebDavEditMode.REMOTE_DIR -> {
                            IOSAdaptiveTextField(
                                value = draftRemoteDir,
                                onValueChange = { draftRemoteDir = it },
                                label = "远端目录",
                                placeholder = "/BiliPai/backups",
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("启用备份", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = draftEnabled,
                                    onCheckedChange = { draftEnabled = it }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                IOSDialogAction(onClick = {
                    viewModel.saveConfig(
                        WebDavBackupConfig(
                            baseUrl = draftBaseUrl,
                            username = draftUsername,
                            password = draftPassword,
                            remoteDir = draftRemoteDir,
                            enabled = draftEnabled
                        )
                    )
                    showEditDialog = false
                }) {
                    Text(saveLabel)
                }
            },
            dismissButton = {
                IOSDialogAction(onClick = { showEditDialog = false }) {
                    Text(cancelLabel)
                }
            }
        )
    }

    if (showRestoreConfirm) {
        IOSAlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复最新备份") },
            text = {
                Text("恢复会覆盖当前本地设置与插件配置。建议先执行一次“立即备份”。")
            },
            confirmButton = {
                IOSDialogAction(onClick = {
                    showRestoreConfirm = false
                    viewModel.restoreLatest()
                }) {
                    Text("继续恢复")
                }
            },
            dismissButton = {
                IOSDialogAction(onClick = { showRestoreConfirm = false }) {
                    Text(cancelLabel)
                }
            }
        )
    }
}
