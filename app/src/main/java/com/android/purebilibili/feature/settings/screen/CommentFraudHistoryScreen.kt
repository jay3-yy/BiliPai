package com.android.purebilibili.feature.settings.screen

import com.android.purebilibili.core.util.toUserFriendlyMessage

import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.database.entity.CommentFraudRecord
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.feature.settings.SettingsIconRole
import com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon
import com.android.purebilibili.feature.settings.rememberSettingsVector
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentFraudHistoryScreen(
    onBack: () -> Unit,
    archiveMode: Boolean = false,
    onOpenSource: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordsFlow = remember {
        CommentFraudRepository.getAllRecordsFlow().map<List<CommentFraudRecord>, List<CommentFraudRecord>?> { it }
    }
    val loadedRecords by recordsFlow.collectAsStateWithLifecycle(initialValue = null)
    val allRecords = loadedRecords.orEmpty()
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    var showAllAccounts by androidx.compose.runtime.saveable.rememberSaveable(identity.mid, archiveMode) {
        mutableStateOf(!archiveMode)
    }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val ownerMid = if (showAllAccounts) null else identity.mid ?: 0L
    // 未归属记录只在“全部本地”中展示，不自动归给当前账号。
    val accountRecords = allRecords.filter { ownerMid == null || (ownerMid > 0L && it.uid == ownerMid) }
    val records = accountRecords.filter {
        query.isBlank() || it.message.contains(query, ignoreCase = true) ||
            it.source_id.orEmpty().contains(query, ignoreCase = true) || it.oid.toString().contains(query)
    }

    var showExportFormat by remember { mutableStateOf(false) }
    var compatibleExport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var recordToDeleteOnBili by remember { mutableStateOf<CommentFraudRecord?>(null) }
    var recheckingRpid by remember { mutableStateOf<Long?>(null) }

    // 导入选择器
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }
                        input.use {
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = it.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 8 * 1024 * 1024) { "备份文件不能超过 8 MB" }
                                output.write(buffer, 0, count)
                            }
                            output.toString("UTF-8")
                        }
                    }
                    if (!content.isNullOrBlank()) {
                        val result = CommentFraudRepository.importFromJson(content)
                        result.onSuccess { count ->
                            Toast.makeText(context, "已导入 $count 条记录", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "导入失败：${it.toUserFriendlyMessage("文件格式无效")}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导出选择器 (SAF 另存为 .json)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = CommentFraudRepository.exportToJson(ownerMid = ownerMid, compatibleFormat = compatibleExport)
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法写入文件" }
                            .bufferedWriter().use { it.write(json) }
                    }
                    Toast.makeText(context, "已导出备份文件", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败：${e.toUserFriendlyMessage("无法写入文件")}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    EntranceGroup {
        SettingsPageScaffold(
            title = if (archiveMode) "我的评论" else "评论检查记录",
            onBack = onBack,
            backContentDescription = "返回",
            bottomContentPadding = bottomPadding,
            scrollHost = com.android.purebilibili.feature.settings.SettingsPageScrollHost.External,
            actions = {
                AppIconButton(onClick = { importLauncher.launch("application/json") }) {
                    AppIcon(rememberSettingsVector(R.drawable.ph_file_arrow_down_24, R.drawable.ms_file_download_24), contentDescription = "导入备份", tint = AppContentColors.primaryText())
                }
                AppIconButton(enabled = loadedRecords != null, onClick = {
                    showExportFormat = true
                }) {
                    AppIcon(rememberSettingsVector(R.drawable.ph_file_arrow_up_24, R.drawable.ms_file_upload_24), contentDescription = "导出备份", tint = AppContentColors.primaryText())
                }
                if (allRecords.isNotEmpty() && !archiveMode) {
                    AppIconButton(onClick = { showClearDialog = true }) {
                        AppIcon(rememberSettingsVector(R.drawable.ph_broom_24, R.drawable.ms_delete_sweep_24), contentDescription = "清空全部", tint = AppContentColors.error())
                    }
                }
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                if (archiveMode) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        AppText("自动保存本机成功发送的评论；不补拉历史评论。", style = MaterialTheme.typography.bodySmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            AppFilterChip(
                                selected = !showAllAccounts,
                                onClick = { showAllAccounts = false },
                                label = { AppText("当前账号") },
                            )
                            AppFilterChip(
                                selected = showAllAccounts,
                                onClick = { showAllAccounts = true },
                                label = { AppText("全部本地") },
                            )
                        }
                        AppOutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { AppText("搜索评论内容或原内容 ID") }, singleLine = true,
                        )
                        AppText(if (loadedRecords == null) "正在读取评论…" else "${records.size} 条记录", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (loadedRecords == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppCircularProgressIndicator() }
                } else if (records.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(
                                rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_CHECK_HISTORY),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = AppContentColors.secondaryText().copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            AppText(
                                text = if (archiveMode) "暂无匹配的本地评论" else "暂无评论检查记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = AppContentColors.secondaryText()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AppText(
                                text = if (archiveMode) "可切换全部本地记录，或导入 JSON 备份" else "在此查看已发评论是否正常显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText()
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = settingsScrollContentPadding(extraHorizontal = 16.dp, extraVertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(records, key = { it.rpid }) { record ->
                            Box(modifier = Modifier.entrance()) {
                                CommentFraudItemCard(
                                    record = record,
                                    isRechecking = recheckingRpid == record.rpid,
                                    onRecheck = {
                                        scope.launch {
                                            recheckingRpid = record.rpid
                                            val res = CommentFraudRepository.recheckRecord(record)
                                            recheckingRpid = null
                                            res.onSuccess { status ->
                                                Toast.makeText(context, "检查完成：$status", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "检查失败：${it.toUserFriendlyMessage("请稍后重试")}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onCopyMessage = {
                                        copyText(context, record.message)
                                        Toast.makeText(context, "已复制评论内容", Toast.LENGTH_SHORT).show()
                                    },
                                    onCopyScheme = {
                                        val scheme = generateBiliUrlScheme(record)
                                        copyText(context, scheme)
                                        Toast.makeText(context, "已复制评论链接", Toast.LENGTH_SHORT).show()
                                    },
                                    onDeleteLocal = {
                                        scope.launch { CommentFraudRepository.deleteLocalRecord(record.rpid) }
                                    },
                                    onOpenSource = if (archiveMode && onOpenSource != null) {
                                        commentArchiveSourceUrl(record)?.let { url -> { onOpenSource(url) } }
                                    } else null,
                                    canManageRemote = identity.mid != null && record.uid == identity.mid,
                                    canRecheck = true,
                                    onDeleteBili = {
                                        recordToDeleteOnBili = record
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportFormat) {
        AppAlertDialog(
            onDismissRequest = { showExportFormat = false },
            title = { AppText("导出评论") },
            text = { AppText("原生备份保留评论检查状态；PiliPlus 兼容格式用于迁移评论；导入时已有的图片与装饰数据会保留，本机记录按现有字段导出。") },
            confirmButton = {
                AppTextButton(onClick = {
                    compatibleExport = false
                    showExportFormat = false
                    exportLauncher.launch("BiliPai_comments_${System.currentTimeMillis()}.json")
                }) { AppText("原生格式") }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    compatibleExport = true
                    showExportFormat = false
                    exportLauncher.launch("reply_${System.currentTimeMillis()}.json")
                }) { AppText("PiliPlus 兼容格式") }
            },
        )
    }

    if (showClearDialog) {
        AppAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { AppText("清空评论检查记录？", fontWeight = FontWeight.Bold) },
            text = { AppText("删除本地保存的全部评论检查记录，无法恢复。B 站上的评论会保留。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch { CommentFraudRepository.clearAllRecords() }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppContentColors.error())
                ) { AppText("确认清空") }
            },
            dismissButton = {
                AppTextButton(onClick = { showClearDialog = false }) { AppText("取消") }
            }
        )
    }

    recordToDeleteOnBili?.let { record ->
        AppAlertDialog(
            onDismissRequest = { recordToDeleteOnBili = null },
            title = { AppText("在 B 站永久删除此评论？", fontWeight = FontWeight.Bold, color = AppContentColors.error()) },
            text = { AppText("删除 B 站上的这条评论及本地记录，无法恢复。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch {
                            if (TokenManager.accountIdentity.value.mid != record.uid) {
                                Toast.makeText(context, "账号已切换，请重新打开评论记录", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val res = CommentFraudRepository.deleteBiliComment(record)
                            res.onSuccess {
                                Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "删除失败：${it.toUserFriendlyMessage("请稍后重试")}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        recordToDeleteOnBili = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppContentColors.error())
                ) { AppText("彻底删除") }
            },
            dismissButton = {
                AppTextButton(onClick = { recordToDeleteOnBili = null }) { AppText("取消") }
            }
        )
    }
}

/**
 * 终极手风琴式可展开全息卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentFraudItemCard(
    record: CommentFraudRecord,
    isRechecking: Boolean,
    onRecheck: () -> Unit,
    onCopyMessage: () -> Unit,
    onCopyScheme: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteBili: () -> Unit,
    onOpenSource: (() -> Unit)? = null,
    canManageRemote: Boolean = true,
    canRecheck: Boolean = true,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val postTimeStr = remember(record.post_time) {
        if (record.post_time > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.post_time))
        else "发评时间未记录"
    }

    val checkTimeStr = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }

    val status = record.fraudStatus
    val rememberedSecondaryTextColor = AppContentColors.secondaryText()
    val rememberedErrorColor = AppContentColors.error()
    val rememberedSuccessColor = AppContentColors.success()
    val rememberedWarningColor = AppContentColors.warning()
    val (statusLabel, statusColor) = remember(status, rememberedSecondaryTextColor, rememberedErrorColor, rememberedSuccessColor, rememberedWarningColor) {
        when (status) {
            CommentFraudStatus.NORMAL -> "评论正常" to rememberedSuccessColor
            CommentFraudStatus.SHADOW_BANNED -> "仅自己可见" to rememberedErrorColor
            CommentFraudStatus.DELETED -> "已被秒删" to rememberedSecondaryTextColor
            CommentFraudStatus.INVISIBLE -> "软屏蔽" to rememberedWarningColor
            CommentFraudStatus.UNDER_REVIEW -> "审核中" to rememberedWarningColor
            CommentFraudStatus.UNKNOWN -> "状态未知" to rememberedSecondaryTextColor
        }
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(), // 👈 手风琴平滑展开折叠动画
        shape = AppCardShape.Semantic(ContainerLevel.Card),
        colors = AppCardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // --- 顶部信息栏：发送时间 + 源ID/BV号 + 检测状态 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    AppText(
                        text = postTimeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                    AppText(
                        text = record.source_id ?: "OID: ${record.oid}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.link()
                    )
                }

                // 状态标签
                Box(
                    modifier = Modifier
                        .clip(AppShapes.container(ContainerLevel.Tag))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    AppText(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 评论正文内容 (点击复制/展开) ---
            AppText(
                text = record.message,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.primaryText(),
                modifier = Modifier.clickable { onCopyMessage() }
            )

            // --- 展开后的全息元数据区块 ---
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                AppHorizontalDivider()
                Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow(label = "当前状态", value = statusLabel, valueColor = statusColor) {
                        AppTextButton(
                            onClick = onRecheck,
                            enabled = !isRechecking && canManageRemote && canRecheck,
                            contentPadding = PaddingValues(horizontal = AppSpacingTokens.Small, vertical = 2.dp)
                        ) {
                            AppText(
                                if (isRechecking) "更新中…" else "重新检查",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    record.initial_status?.let {
                        val initLabel = CommentFraudRecord.parseStatus(it).name
                        DetailRow(label = "初始状态", value = initLabel)
                    }

                    DetailRow(label = "检查结果", value = record.fraudAssessment, valueColor = statusColor)

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "Source ID", value = record.source_id ?: "无")
                    DetailRow(label = "OID", value = "${record.oid}")
                    DetailRow(label = "评论区类型", value = record.typeName)

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "UID", value = if (record.uid > 0L) "${record.uid}" else "未记录")
                    DetailRow(label = "RPID", value = "${record.rpid}")

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "Root ID", value = if (record.root > 0L) "${record.root}" else "0（主评论）")
                    DetailRow(label = "Parent ID", value = if (record.parent > 0L) "${record.parent}" else "0")

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "发送日期", value = postTimeStr)
                    DetailRow(label = "检查日期", value = checkTimeStr)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 底部操作栏：复检、Scheme、移除、删评 + 【无字收起图标】 ---
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onOpenSource != null) {
                        AppTextButton(onClick = onOpenSource) { AppText("打开原内容") }
                    }
                    AppTextButton(
                        onClick = onRecheck,
                        enabled = !isRechecking && canManageRemote && canRecheck,
                    ) {
                        AppText(
                            if (isRechecking) "检查中…" else "重新检查",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    AppTextButton(onClick = onCopyScheme) {
                        AppText("复制链接", style = MaterialTheme.typography.labelMedium)
                    }

                    AppTextButton(onClick = onDeleteLocal) {
                        AppText("移除记录", style = MaterialTheme.typography.labelMedium)
                    }

                    AppTextButton(
                        onClick = onDeleteBili,
                        enabled = canManageRemote,
                        colors = ButtonDefaults.textButtonColors(contentColor = AppContentColors.error())
                    ) {
                        AppText("删除评论", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 右侧：展开/收起无字图标按钮
                AppIconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    AppIcon(
                        imageVector = if (isExpanded) rememberSettingsVector(R.drawable.ph_caret_up_24, R.drawable.ms_keyboard_arrow_up_24) else rememberSettingsVector(R.drawable.ph_caret_down_24, R.drawable.ms_keyboard_arrow_down_24),
                        contentDescription = if (isExpanded) "收起" else "展开详情",
                        tint = AppContentColors.secondaryText()
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = AppContentColors.primaryText(),
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            AppText(
                text = "$label：",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                fontWeight = FontWeight.Medium
            )
            AppText(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                fontWeight = FontWeight.Normal
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}

private fun generateBiliUrlScheme(record: CommentFraudRecord): String {
    val rootId = if (record.root > 0L) record.root else 0L
    return if (record.type == 1) {
        if (rootId > 0L) "bilibili://video/${record.oid}/?comment_root_id=${rootId}&comment_secondary_id=${record.rpid}"
        else "bilibili://video/${record.oid}/?comment_root_id=${record.rpid}"
    } else {
        if (rootId > 0L) "bilibili://comment/detail/${record.type}/${record.oid}/${rootId}?anchor=${record.rpid}"
        else "bilibili://comment/detail/${record.type}/${record.oid}/${record.rpid}"
    }
}
