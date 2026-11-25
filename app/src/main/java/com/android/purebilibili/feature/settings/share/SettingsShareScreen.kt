@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.settings.share

import androidx.compose.foundation.clickable

import com.android.purebilibili.core.ui.AppContentColors

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.R
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.settings.rememberOriginalThemeAwareSettingsIcon
import com.android.purebilibili.feature.settings.rememberSettingsVector
import com.android.purebilibili.feature.settings.settingsIconResource
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceGroup
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Tasks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsShareScreen(
    onBack: () -> Unit,
    viewModel: SettingsShareViewModel = viewModel()
) {
    val context = LocalContext.current
    val screenTitle = stringResource(R.string.settings_share_title)
    val backLabel = stringResource(R.string.common_back)
    val shareChooserTitle = stringResource(R.string.settings_share_chooser_title)
    val shareOpenFailed = stringResource(R.string.settings_share_open_failed)
    val importConfirmLabel = stringResource(R.string.settings_share_import_confirm)
    val viewSkippedLabel = stringResource(R.string.settings_share_view_skipped)
    val cancelLabel = stringResource(R.string.common_cancel)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadSavedProfiles()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImportPreview(uri)
        }
    }

    LaunchedEffect(uiState.pendingShareUri) {
        val shareUri = uiState.pendingShareUri ?: return@LaunchedEffect
        runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
        }.onFailure {
            Toast.makeText(
                context,
                it.message ?: shareOpenFailed,
                Toast.LENGTH_SHORT
            ).show()
        }
        viewModel.consumeShareUri(shareUri)
    }

    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = settingsScrollContentPadding(extraBottom = 24.dp),
            ) {
            item {
                AppPreferenceSectionTitle("当前状态")
                AppPreferenceGroup {
                    // 状态放 subtitle，避免右侧 value 窄列把长文案拆成「操作」单独一行。
                    AppPreference(
                        icon = if (uiState.isBusy) rememberSettingsVector(R.drawable.ph_dots_three_circle_24, R.drawable.ms_pending_fill_24) else rememberSettingsVector(R.drawable.ph_clock_afternoon_24, R.drawable.ms_history_fill_24),
                        title = if (uiState.isBusy) "正在处理" else "最近状态",
                        subtitle = uiState.statusMessage ?: "尚未执行导入导出操作",
                        onClick = if (uiState.statusMessage != null) ({ viewModel.clearStatus() }) else null,
                        iconTint = if (uiState.isBusy) AppContentColors.warning() else AppContentColors.success(),
                        showChevron = false
                    )
                }
            }

            item {
                AppPreferenceSectionTitle("说明")
                AppPreferenceGroup {
                    AppPreference(
                        icon = rememberSettingsVector(R.drawable.ph_list_checks_24) { rememberOriginalThemeAwareSettingsIcon(R.drawable.ms_task_alt_24, MiuixIcons.Tasks) },
                        title = "包含的设置",
                        subtitle = "外观、播放、手势、弹幕和导航等不含隐私的设置",
                        onClick = null,
                        iconTint = AppContentColors.secondaryText(),
                        showChevron = false
                    )
                    AppPreferenceDivider(startIndent = 66.dp)
                    AppPreference(
                        icon = rememberSettingsVector(R.drawable.ph_lock_24, R.drawable.ms_lock_fill_24),
                        title = "不包含的设置",
                        subtitle = "账号、保存路径、云备份账号、隐私和设备专属配置",
                        onClick = null,
                        iconTint = AppContentColors.secondaryText(),
                        showChevron = false
                    )
                }
            }

            item {
                AppPreferenceSectionTitle("导出选项")
                AppPreferenceGroup {
                    AppSwitchPreference(
                        title = "附带设备信息",
                        subtitle = "附带安卓版本、界面风格和屏幕信息，便于排查问题；导入时不会应用",
                        checked = uiState.includeDeviceDebug,
                        onCheckedChange = viewModel::setIncludeDeviceDebug,
                        icon = rememberSettingsVector(R.drawable.ph_flag_24) { rememberOriginalThemeAwareSettingsIcon(R.drawable.ms_report_24, MiuixIcons.Report) },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
            }

            item {
                AppPreferenceSectionTitle("操作")
                if (com.android.purebilibili.core.theme.LocalAppUiStyle.current == com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3) {
                    MaterialSettingsTransferActions(
                        enabled = !uiState.isBusy,
                        onExport = {
                            exportLauncher.launch(buildSettingsShareFileName(
                                appVersion = BuildConfig.VERSION_NAME,
                                epochMs = System.currentTimeMillis(),
                            ))
                        },
                        onShare = { viewModel.prepareShare() },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onSave = { profileName = ""; showSaveProfileDialog = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else                 AppPreferenceGroup {
                    AppPreference(
                        icon = rememberSettingsVector(R.drawable.ph_download_simple_24, R.drawable.ms_download_fill_24),
                        title = "导出到文件",
                        subtitle = if (uiState.includeDeviceDebug) {
                            "保存设置文件，并附带设备排查信息"
                        } else {
                            "把可分享设置保存为文件"
                        },
                        onClick = {
                            exportLauncher.launch(
                                buildSettingsShareFileName(
                                    appVersion = BuildConfig.VERSION_NAME,
                                    epochMs = System.currentTimeMillis()
                                )
                            )
                        },
                        iconTint = AppContentColors.secondaryText()
                    )
                    AppPreferenceDivider(startIndent = 66.dp)
                    AppPreference(
                        icon = rememberSettingsVector(R.drawable.ph_share_network_24, R.drawable.ms_share_fill_24),
                        title = "分享导出文件",
                        subtitle = "生成设置文件后打开系统分享面板",
                        onClick = { viewModel.prepareShare() },
                        iconTint = AppContentColors.secondaryText()
                    )
                    AppPreferenceDivider(startIndent = 66.dp)
                    AppPreference(
                        icon = rememberSettingsVector(R.drawable.ph_file_arrow_up_24, R.drawable.ms_upload_file_fill_24),
                        title = "从文件导入",
                        subtitle = "先查看会修改哪些内容，确认后再应用",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        iconTint = AppContentColors.secondaryText()
                    )
                }
            }

            item {
                AppPreferenceSectionTitle("本机保存的设置")
                AppPreferenceGroup {
                    if (com.android.purebilibili.core.theme.LocalAppUiStyle.current != com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3) {
                        AppPreference(
                            icon = rememberSettingsVector(R.drawable.ph_archive_24, R.drawable.ms_download_fill_24),
                            title = "保存当前配置",
                            subtitle = "自定义名称，保存后可一键恢复",
                            onClick = {
                                profileName = ""
                                showSaveProfileDialog = true
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }
                    if (com.android.purebilibili.core.theme.LocalAppUiStyle.current == com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3) {
                        if (uiState.savedProfiles.isEmpty()) {
                            com.android.purebilibili.core.ui.components.AppText(
                                "暂无保存的配置，可使用上方按钮保存。",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        uiState.savedProfiles.forEach { profile ->
                            androidx.compose.material3.ListItem(
                                headlineContent = { com.android.purebilibili.core.ui.components.AppText(profile.name) },
                                supportingContent = { com.android.purebilibili.core.ui.components.AppText("点击恢复此配置") },
                                modifier = Modifier
                                    .clickable(enabled = !uiState.isBusy) { viewModel.restoreSavedProfile(profile) },
                            )
                        }
                    } else {
                        uiState.savedProfiles.forEach { profile ->
                            AppPreferenceDivider(startIndent = 66.dp)
                            AppPreference(
                                icon = rememberSettingsVector(
                                    settingsIconResource(R.drawable.ph_arrow_u_down_left_24, R.drawable.ms_restore_24),
                                ),
                                title = profile.name,
                                subtitle = "点击立即恢复此配置",
                                onClick = { viewModel.restoreSavedProfile(profile) },
                                iconTint = AppContentColors.secondaryText(),
                            )
                        }
                    }
                }
            }

            item {
                AppPreferenceSectionTitle("文件格式")
                AppPreferenceGroup {
                    AppPreference(
                        icon = rememberSettingsVector(R.drawable.ph_brackets_curly_24, R.drawable.ms_data_object_fill_24),
                        title = "设置包（JSON）",
                        subtitle = "通用文本格式，可直接查看，也可在 BiliPai 中导入",
                        value = "格式版本 v$SETTINGS_SHARE_SCHEMA_VERSION",
                        onClick = null,
                        iconTint = AppContentColors.secondaryText(),
                        showChevron = false
                    )
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

    val pendingImportSession = uiState.pendingImportSession
    if (showSaveProfileDialog) {
        AppAlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { AppText("保存配置") },
            text = {
                AppTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = "配置名称",
                    placeholder = "例如：我的清爽布局",
                    singleLine = true,
                )
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        viewModel.saveCurrentProfile(profileName)
                        showSaveProfileDialog = false
                    },
                ) { AppText("保存") }
            },
            dismissButton = {
                AppDialogAction(onClick = { showSaveProfileDialog = false }) {
                    AppText(cancelLabel)
                }
            },
        )
    }
    if (pendingImportSession != null) {
        var showRawKeys by remember(pendingImportSession) { mutableStateOf(false) }
        AppAlertDialog(
            onDismissRequest = { viewModel.dismissImportPreview() },
            title = {
                AppText(
                    text = "导入设置",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(
                        text = buildImportPreviewSummary(pendingImportSession),
                        color = AppContentColors.secondaryText()
                    )
                    if (showRawKeys && pendingImportSession.preview.skippedKeys.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppText(
                            text = pendingImportSession.preview.skippedKeys.joinToString(separator = "\n"),
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogAction(onClick = { viewModel.confirmImport() }) {
                    AppText(importConfirmLabel)
                }
            },
            dismissButton = {
                AppDialogAction(
                    onClick = {
                        if (pendingImportSession.preview.skippedKeys.isNotEmpty() && !showRawKeys) {
                            showRawKeys = true
                        } else {
                            viewModel.dismissImportPreview()
                        }
                    }
                ) {
                    AppText(
                        if (pendingImportSession.preview.skippedKeys.isNotEmpty() && !showRawKeys) {
                            viewSkippedLabel
                        } else {
                            cancelLabel
                        }
                    )
                }
            }
        )
    }
}

private fun buildImportPreviewSummary(session: SettingsShareImportSession): String {
    val sectionSummary = session.preview.importableSections
        .joinToString(separator = " / ") { it.label }
        .ifBlank { "无可导入分类" }
    val skippedCount = session.preview.skippedKeys.size
    val skippedSummary = if (skippedCount > 0) {
        "将跳过 $skippedCount 项本机专属或未知配置"
    } else {
        "没有需要跳过的项目"
    }
    return buildString {
        appendLine("配置名：${session.profile.profileName}")
        appendLine("来源版本：${session.profile.appVersion}")
        appendLine("导出时间：${session.profile.exportedAtIso}")
        appendLine("可导入分类：$sectionSummary")
        append(skippedSummary)
    }.trim()
}
