package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppText

/** 继承各材质的原生字阶，长说明显式左对齐，不使用弹窗正文默认的居中排版。 */
@Composable
internal fun PerformanceCaptureConsentContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(
            text = "帮助开发者排查页面滑动和切换时的卡顿。",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        PerformanceConsentParagraph(
            title = "测试方式",
            body = "正常使用 3～5 分钟并复现问题，关闭开关即可保存报告。最长采集 15 分钟，后台自动暂停。",
            modifier = Modifier.fillMaxWidth(),
        )
        PerformanceConsentParagraph(
            title = "记录内容",
            body = "页面卡顿、渲染耗时，以及 CPU、内存、温控和设备信息。记录四类页面根节点提交、数据加载入口事件和播放器创建、绑定、释放耗时；不包含解码与缓冲专项数据。",
            modifier = Modifier.fillMaxWidth(),
        )
        PerformanceConsentParagraph(
            title = "隐私说明",
            body = "不记录账号、观看内容、搜索词或网络请求明细。报告保存在本机，仅在你主动分享时发送。",
            modifier = Modifier.fillMaxWidth(),
        )
        AppText(
            text = "重启后保持关闭；最新报告随采集更新，本机保留最近 ${com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_LIMIT} 次历史。",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun PerformanceConsentParagraph(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
        )
        AppText(text = body, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
