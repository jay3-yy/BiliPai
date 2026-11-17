package com.android.purebilibili.feature.settings

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.performance.PERFORMANCE_CAPTURE_SETTING_ID
import com.android.purebilibili.core.performance.PERFORMANCE_CAPTURE_TITLE
import com.android.purebilibili.core.performance.PerformanceCapture
import com.android.purebilibili.core.performance.PERFORMANCE_PREVIEW_SETTING_ID
import com.android.purebilibili.core.performance.PERFORMANCE_PREVIEW_TITLE
import com.android.purebilibili.core.performance.PerformanceReportPreview
import com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_SETTING_ID
import com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_TITLE
import com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_LIMIT
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 复用偏好组件和自适应弹窗，玻璃、MD3、Miuix 使用相同功能与各自的容器样式。 */
@Composable
internal fun PerformanceCaptureSection(onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val windowOwner = LocalView.current.rootView
    val status by PerformanceCapture.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showConsent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasReport by remember { mutableStateOf<Boolean?>(null) }
    var preview by remember { mutableStateOf<PerformanceReportPreview?>(null) }
    LaunchedEffect(status.recording, busy) {
        hasReport = withContext(Dispatchers.IO) { PerformanceCapture.reportFile(context).exists() }
    }
    val changeCapture: (Boolean) -> Unit = { enabled ->
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    if (enabled) PerformanceCapture.start(context)
                    else {
                        PerformanceCapture.stop()
                        PerformanceCapture.checkpoint(context)
                    }
                    showConsent = false
                } catch (failure: java.io.IOException) {
                    val message = "保存报告失败：${failure.localizedMessage}"
                    error = message
                    showConsent = false
                    PerformanceCapture.fail(message)
                } finally {
                    busy = false
                }
            }
        }
    }

    SettingsCardGroup {
        SettingsItemAnchor(PERFORMANCE_CAPTURE_SETTING_ID) {
            AppSwitchPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PERFORMANCE_CAPTURE),
                title = PERFORMANCE_CAPTURE_TITLE,
                subtitle = if (busy) "正在处理报告…" else status.message,
                checked = status.recording,
                enabled = !busy,
                onCheckedChange = { enabled ->
                    if (enabled) { error = null; showConsent = true }
                    else changeCapture(false)
                },
            )
        }
        if (status.recording) {
            AppPreferenceDivider()
            AppPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PERFORMANCE_MARK),
                title = "标记刚才的问题",
                subtitle = "记录当前时间，并关联前 30 秒的采样结果",
                onClick = if (busy) null else { { PerformanceCapture.mark(windowOwner) } },
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor(PERFORMANCE_PREVIEW_SETTING_ID) {
            AppPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PERFORMANCE_PREVIEW),
                title = PERFORMANCE_PREVIEW_TITLE,
                subtitle = when {
                    status.recording -> "先关闭采集开关，再查看已保存报告"
                    hasReport == null -> "正在读取报告状态…"
                    hasReport == true -> "查看采集环境、卡顿页面、问题时段与标记回看，可复制摘要"
                    else -> "完成一次采集后可查看摘要"
                },
                onClick = if (busy || status.recording || hasReport != true) null else {
                    {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                preview = PerformanceCapture.readPreview(context)
                            } catch (failure: java.io.IOException) {
                                error = "读取报告失败：${failure.localizedMessage}"
                            } catch (failure: org.json.JSONException) {
                                error = "报告内容无法解析：${failure.localizedMessage}"
                            } catch (failure: IllegalStateException) {
                                error = failure.message
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor(PERFORMANCE_HISTORY_SETTING_ID) {
            AppPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PERFORMANCE_HISTORY),
                title = PERFORMANCE_HISTORY_TITLE,
                subtitle = if (status.recording) "结束采集后查看历史排行、趋势与对比" else "保留最近 $PERFORMANCE_HISTORY_LIMIT 次采集，按场景查看排行、坐标图与基准对比",
                onClick = if (busy || status.recording) null else { onOpenHistory },
            )
        }
        AppPreferenceDivider()
        AppPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PERFORMANCE_REPORT),
            title = "分享性能报告",
            subtitle = if (status.recording) "先关闭采集开关，再分享报告"
                else if (hasReport == null) "正在读取报告状态…"
                else if (hasReport == true) "报告包含卡顿页面排序、问题时段与人工标记回看摘要" else "开始采集后生成本地报告",
            onClick = if (busy || status.recording || hasReport != true) null else {
                {
                    busy = true
                    scope.launch {
                        try {
                            val file = PerformanceCapture.export(context)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "BiliPai 性能问题反馈")
                                putExtra(Intent.EXTRA_TEXT, "请补充问题现象、操作步骤和大致发生时间，并将附件发给开发者。")
                                clipData = ClipData.newRawUri("性能报告", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享性能报告"))
                        } catch (failure: java.io.IOException) {
                            error = "读取报告失败：${failure.localizedMessage}"
                        } catch (failure: org.json.JSONException) {
                            error = "报告内容无法解析：${failure.localizedMessage}"
                        } catch (failure: IllegalStateException) {
                            error = failure.message
                        } catch (failure: android.content.ActivityNotFoundException) {
                            error = "没有可接收报告的应用"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        )
    }

    preview?.let { snapshot -> PerformanceReportPreviewDialog(snapshot, onDismiss = { preview = null }) }
    if (showConsent) {
        AppAlertDialog(
            onDismissRequest = { if (!busy) showConsent = false },
            title = { AppText("开启性能采集？") },
            text = {
                PerformanceCaptureConsentContent(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                )
            },
            confirmButton = {
                AppDialogAction(onClick = { changeCapture(true) }) { AppText(if (busy) "正在准备…" else "开启采集") }
            },
            dismissButton = { AppDialogAction(onClick = { if (!busy) showConsent = false }) { AppText("取消") } },
        )
    }
    if (error != null) {
        AppAlertDialog(
            onDismissRequest = { error = null },
            title = { AppText("性能报告") },
            text = { AppText(error.orEmpty()) },
            confirmButton = { AppDialogAction(onClick = { error = null }) { AppText("知道了") } },
        )
    }
}
