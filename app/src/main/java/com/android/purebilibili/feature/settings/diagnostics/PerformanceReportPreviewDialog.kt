package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.performance.PERFORMANCE_PREVIEW_TITLE
import com.android.purebilibili.core.performance.PerformanceReportPreview
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.resolveAppExpandedContentDialogLayoutPolicy
import java.text.DateFormat
import java.util.Date

/** 内容区滚动，标题与操作区由共用弹窗保留；同一布局适配玻璃、MD3 与 Miuix。 */
@Composable
internal fun PerformanceReportPreviewDialog(preview: PerformanceReportPreview, onDismiss: () -> Unit) {
    val copy = rememberClipboardCopyHandler()
    val text = remember(preview) {
        buildString {
            appendLine("版本：${preview.version}（${preview.buildType}）")
            appendLine("设备：${preview.device}")
            appendLine("开始时间：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(preview.startedWallMs))}")
            appendLine("采集时长：${preview.elapsedMs / 60_000} 分 ${preview.elapsedMs / 1_000 % 60} 秒（包含后台时间）")
            val state = when (preview.stopReason) {
                "user" -> "用户结束并保存"
                "time_limit" -> "达到采集时限"
                "checkpoint_unfinished" -> "检查点，采集尚未正常结束"
                "storage_error" -> "保存报告时遇到错误"
                else -> "未知结束状态"
            }
            appendLine("状态：$state")
            appendLine()
            append(preview.summary ?: "这份报告未包含摘要。")
        }
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(PERFORMANCE_PREVIEW_TITLE) },
        contentLayout = resolveAppExpandedContentDialogLayoutPolicy(),
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                AppText(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        },
        confirmButton = { AppDialogAction(onClick = onDismiss) { AppText("关闭") } },
        dismissButton = { AppDialogAction(onClick = { copy(text, "性能摘要") }) { AppText("复制摘要") } },
    )
}
