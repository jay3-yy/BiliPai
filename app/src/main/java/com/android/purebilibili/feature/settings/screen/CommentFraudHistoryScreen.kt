package com.android.purebilibili.feature.settings.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentFraudHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by CommentFraudRepository.getAllRecordsFlow().collectAsState(initial = emptyList())
    
    var showClearDialog by remember { mutableStateOf(false) }
    var recordToDeleteOnBili by remember { mutableStateOf<CommentFraudRecord?>(null) }
    var recheckingRpid by remember { mutableStateOf<Long?>(null) }

    // 导入选择器
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        val result = CommentFraudRepository.importFromJson(content)
                        result.onSuccess { count ->
                            Toast.makeText(context, "✅ 成功导入 $count 条历史记录！", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "❌ 导入失败: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ 读取文件失败", Toast.LENGTH_SHORT).show()
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
                    val json = CommentFraudRepository.exportToJson()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    Toast.makeText(context, "✅ 成功导出 JSON 备份文件！", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ 导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    EntranceGroup {
        SettingsPageScaffold(
            title = "发评反诈历史",
            onBack = onBack,
            backContentDescription = "返回",
            bottomContentPadding = bottomPadding,
            scrollHost = com.android.purebilibili.feature.settings.SettingsPageScrollHost.External,
            actions = {
                AppIconButton(onClick = { importLauncher.launch("application/json") }) {
                    AppIcon(Icons.Outlined.FileDownload, contentDescription = "导入备份", tint = MaterialTheme.colorScheme.onSurface)
                }
                AppIconButton(onClick = {
                    val fileName = "biliSendCheck_backup_${System.currentTimeMillis()}.json"
                    exportLauncher.launch(fileName)
                }) {
                    AppIcon(Icons.Outlined.FileUpload, contentDescription = "导出备份", tint = MaterialTheme.colorScheme.onSurface)
                }
                if (records.isNotEmpty()) {
                    AppIconButton(onClick = { showClearDialog = true }) {
                        AppIcon(Icons.Outlined.DeleteSweep, contentDescription = "清空全部", tint = iOSRed)
                    }
                }
            }
        ) {
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AppText(
                            text = "暂无发评记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AppText(
                            text = "发出的评论将在此处沉淀并追踪全生命周期状态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records, key = { "${it.rpid}_${it.timestamp}_${it.hashCode()}" }) { record ->
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
                                            Toast.makeText(context, "复检完成: $status", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "复检失败: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onCopyMessage = {
                                    copyText(context, record.message)
                                    Toast.makeText(context, "✅ 文案已复制！", Toast.LENGTH_SHORT).show()
                                },
                                onCopyScheme = {
                                    val scheme = generateBiliUrlScheme(record)
                                    copyText(context, scheme)
                                    Toast.makeText(context, "✅ Scheme 已复制！", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteLocal = {
                                    scope.launch { CommentFraudRepository.deleteLocalRecord(record.rpid) }
                                },
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

    if (showClearDialog) {
        AppAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { AppText("清空所有反诈记录？", fontWeight = FontWeight.Bold) },
            text = { AppText("这将会删除本地保存的历史发评快照，此操作不可恢复。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch { CommentFraudRepository.clearAllRecords() }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
            title = { AppText("在 B 站永久删除此评论？", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { AppText("这将会调用 B 站官方接口彻底抹除这条评论，并同时清理本地记录。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch {
                            val res = CommentFraudRepository.deleteBiliComment(record)
                            res.onSuccess {
                                Toast.makeText(context, "✅ 删评成功！", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "❌ 删评失败: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        recordToDeleteOnBili = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
@Composable
private fun CommentFraudItemCard(
    record: CommentFraudRecord,
    isRechecking: Boolean,
    onRecheck: () -> Unit,
    onCopyMessage: () -> Unit,
    onCopyScheme: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteBili: () -> Unit
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
    val (statusLabel, statusColor) = remember(status) {
        when (status) {
            CommentFraudStatus.NORMAL -> "评论正常" to iOSGreen
            CommentFraudStatus.SHADOW_BANNED -> "仅自己可见" to iOSRed
            CommentFraudStatus.DELETED -> "已被秒删" to Color.DarkGray
            CommentFraudStatus.INVISIBLE -> "软屏蔽" to iOSOrange
            CommentFraudStatus.UNDER_REVIEW -> "审核中" to iOSOrange
            CommentFraudStatus.UNKNOWN -> "状态未知" to Color.Gray
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(), // 👈 手风琴平滑展开折叠动画
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = record.source_id ?: "OID: ${record.oid}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 状态标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    AppText(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 评论正文内容 (点击复制/展开) ---
            AppText(
                text = record.message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onCopyMessage() }
            )

            // --- 展开后的全息元数据区块 ---
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                AppHorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow(label = "当前状态", value = statusLabel, valueColor = statusColor) {
                        AppTextButton(
                            onClick = onRecheck,
                            enabled = !isRechecking,
                            colors = ButtonDefaults.textButtonColors(contentColor = iOSBlue),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            AppText(if (isRechecking) "更新中…" else "【更新状态】", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    record.initial_status?.let {
                        val initLabel = CommentFraudRecord.parseStatus(it).name
                        DetailRow(label = "初始状态", value = initLabel)
                    }

                    DetailRow(label = "风控定性", value = record.fraudAssessment, valueColor = statusColor)

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "评论区源 ID", value = record.source_id ?: "无")
                    DetailRow(label = "评论区 OID", value = "${record.oid}")
                    DetailRow(label = "评论区类型", value = record.typeName)

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "用户 UID", value = if (record.uid > 0L) "${record.uid}" else "未记录")
                    DetailRow(label = "评论 RPID", value = "${record.rpid}")

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "楼层 Root ID", value = if (record.root > 0L) "${record.root}" else "0 (根评论)")
                    DetailRow(label = "目标 Parent ID", value = if (record.parent > 0L) "${record.parent}" else "0")

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "发送日期", value = postTimeStr)
                    DetailRow(label = "检查日期", value = checkTimeStr)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 底部操作栏：复检、Scheme、移除、删评 + 【无字收起图标】 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧快捷动作按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(
                        onClick = onRecheck,
                        enabled = !isRechecking,
                        colors = ButtonDefaults.textButtonColors(contentColor = iOSBlue)
                    ) {
                        AppText(if (isRechecking) "复检中…" else "🔄 复检", fontSize = 12.sp)
                    }

                    AppTextButton(onClick = onCopyScheme) {
                        AppText("📱 Scheme", fontSize = 12.sp)
                    }

                    AppTextButton(onClick = onDeleteLocal) {
                        AppText("❌ 移除", fontSize = 12.sp)
                    }

                    AppTextButton(
                        onClick = onDeleteBili,
                        colors = ButtonDefaults.textButtonColors(contentColor = iOSRed)
                    ) {
                        AppText("🗑️ 删评", fontSize = 12.sp)
                    }
                }

                // 右侧：展开/收起无字图标按钮
                AppIconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    AppIcon(
                        imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开详情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
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
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            AppText(
                text = value,
                fontSize = 12.sp,
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
