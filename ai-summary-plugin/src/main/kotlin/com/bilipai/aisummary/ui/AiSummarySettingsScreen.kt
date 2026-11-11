package com.bilipai.aisummary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bilipai.aisummary.data.ChatCompletionsClient
import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SummaryDetailLevel
import com.bilipai.aisummary.model.SummaryStyle
import com.bilipai.aisummary.settings.ConfigStore
import com.bilipai.aisummary.settings.ProviderTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「AI 总结」设置页（可作为一个独立设置分组挂进 BiliPai 的设置容器）。
 * 模板只负责一键填值，任何字段都可手动覆盖；API Key 用密码框且默认隐藏。
 *
 * [scrollable] 为 true 时自带 `verticalScroll`（独立设置页用）。
 * 当本页被塞进宿主的 LazyColumn item（如 BiliPai 插件详情页）时，item 的高度约束是
 * Infinity，直接加 `verticalScroll` 会抛「infinite maximum height constraints」崩溃；
 * 因此改为用 [maxHeight] 给一个**有限**高度再滚动，既避免崩溃又保证内容完整可达。
 */
@Composable
fun AiSummarySettingsScreen(
    configStore: ConfigStore,
    chatClient: ChatCompletionsClient,
    onSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxHeight: Dp = 520.dp,
    /** 可选：提供后设置页会出现「测试 ASR 连接」按钮。 */
    asrTester: ((AiSummaryConfig) -> String)? = null
) {
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(configStore.load()) }
    var keyVisible by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var remoteModels by remember { mutableStateOf(emptyList<String>()) }
    var styleMenu by remember { mutableStateOf(false) }
    var detailMenu by remember { mutableStateOf(false) }
    var asrTestResult by remember { mutableStateOf("") }
    var asrTesting by remember { mutableStateOf(false) }

    fun update(block: (AiSummaryConfig) -> AiSummaryConfig) { cfg = block(cfg) }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (scrollable) {
                    Modifier.verticalScroll(scrollState)
                } else {
                    // 上层是 LazyColumn item（约束为 Infinity）：
                    // 先给有限高度，再在其内部滚动，避免无限约束崩溃。
                    Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(scrollState)
                }
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("模型服务", style = MaterialTheme.typography.titleMedium)

        // 模板
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("服务商模板", modifier = Modifier.weight(1f))
            TextButton(onClick = { templateMenu = true }) {
                Text(ProviderTemplates.displayNameFor(cfg))
            }
            DropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                ProviderTemplates.ALL.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.displayName) },
                        onClick = {
                            templateMenu = false
                            cfg = ProviderTemplates.applyTemplate(cfg, t.id)
                            remoteModels = emptyList()
                            testResult = ""
                        }
                    )
                }
            }
        }
        // 以实际 Base URL 判定：在某个模板上改成自建网关时，说明文字也跟着变，避免误导
        val template = ProviderTemplates.byId(ProviderTemplates.resolveEffectiveProviderId(cfg))
        if (template.note.isNotBlank()) {
            Text(template.note, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = cfg.baseUrl,
            onValueChange = { update { c -> c.copy(baseUrl = it) } },
            label = { Text("API Base URL（到 /v1）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("例：https://api.deepseek.com/v1") }
        )

        OutlinedTextField(
            value = cfg.apiKey,
            onValueChange = { update { c -> c.copy(apiKey = it) } },
            label = { Text("API Key（仅存本机，加密保存）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (keyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { keyVisible = !keyVisible }) { Text(if (keyVisible) "隐藏" else "显示") }
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cfg.model,
                onValueChange = { update { c -> c.copy(model = it) } },
                label = { Text("模型名") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            if (template.candidateModels.isNotEmpty() || remoteModels.isNotEmpty()) {
                TextButton(onClick = { modelMenu = true }) { Text("常用") }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    (remoteModels + template.candidateModels).distinct().forEach { m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = {
                            modelMenu = false
                            update { c -> c.copy(model = m) }
                        })
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    testing = true; testResult = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { chatClient.testConnection(cfg) }
                                .getOrElse { "测试失败：${it.message?.take(120)}" }
                        }
                        testing = false; testResult = r
                    }
                },
                enabled = !testing && cfg.isConfigured
            ) { Text(if (testing) "测试中…" else "测试连接") }
            TextButton(onClick = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { chatClient.listModels(cfg) }
                            .getOrElse { emptyList() }
                    }
                    remoteModels = r
                    testResult = if (r.isEmpty()) "该服务未返回模型列表（可用手动输入）"
                        else "拉取到 ${r.size} 个模型，点「常用」选择"
                    if (r.isNotEmpty()) modelMenu = true
                }
            }, enabled = cfg.isConfigured) { Text("拉取模型") }
            TextButton(onClick = {
                configStore.clearKey()
                cfg = cfg.copy(apiKey = "")
                testResult = "已清除 API Key"
            }) { Text("清除 Key") }
        }
        if (testResult.isNotBlank()) Text(testResult, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(6.dp))
        Text("总结参数", style = MaterialTheme.typography.titleMedium)

        NumberField("温度（0~1，越低越稳定）", cfg.temperature.toString()) { v ->
            v.toFloatOrNull()?.let { update { c -> c.copy(temperature = it.coerceIn(0f, 1f)) } }
        }
        NumberField("每段字幕字符预算", cfg.chunkCharBudget.toString()) { v ->
            v.toIntOrNull()?.let { update { c -> c.copy(chunkCharBudget = it.coerceIn(1000, 30000)) } }
        }
        NumberField("重叠字符数", cfg.overlapChars.toString()) { v ->
            v.toIntOrNull()?.let { raw ->
                update { c ->
                    // 与 SubtitleSegmenter 的约束联动：overlap 必须 < budget/2
                    val max = (c.chunkCharBudget / 2 - 1).coerceAtLeast(0)
                    c.copy(overlapChars = raw.coerceIn(0, max))
                }
            }
        }
        NumberField("最大并发（1~6）", cfg.maxConcurrency.toString()) { v ->
            v.toIntOrNull()?.let { update { c -> c.copy(maxConcurrency = it.coerceIn(1, 6)) } }
        }
        NumberField("单次超时（秒）", cfg.timeoutSeconds.toString()) { v ->
            v.toIntOrNull()?.let { update { c -> c.copy(timeoutSeconds = it.coerceIn(10, 600)) } }
        }

        Spacer(Modifier.height(6.dp))
        Spacer(Modifier.height(6.dp))
        Text("无字幕视频（ASR 兜底）", style = MaterialTheme.typography.titleMedium)
        Text(
            "视频没有 CC/AI 字幕时，下载音频用 ASR 转写后再总结。默认关闭——会消耗 ASR 额度与流量。",
            style = MaterialTheme.typography.bodySmall
        )
        SwitchRow("启用 ASR 兜底", cfg.asrEnabled) {
            update { c -> c.copy(asrEnabled = it) }
        }
        if (cfg.asrEnabled) {
            if (asrTester != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            asrTesting = true; asrTestResult = ""
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { asrTester(cfg) }
                                asrTesting = false; asrTestResult = r
                            }
                        },
                        enabled = !asrTesting && cfg.asrConfigured
                    ) { Text(if (asrTesting) "测试中…" else "测试 ASR 连接") }
                }
                if (asrTestResult.isNotBlank()) {
                    Text(asrTestResult, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = cfg.asrBaseUrl,
                onValueChange = { update { c -> c.copy(asrBaseUrl = it) } },
                label = { Text("ASR Base URL") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Groq: https://api.groq.com/openai/v1") }
            )
            OutlinedTextField(
                value = cfg.asrApiKey,
                onValueChange = { update { c -> c.copy(asrApiKey = it) } },
                label = { Text("ASR API Key（同样加密保存）") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (keyVisible) VisualTransformation.None
                    else PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = cfg.asrModel,
                onValueChange = { update { c -> c.copy(asrModel = it) } },
                label = { Text("ASR 模型") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Groq: whisper-large-v3-turbo / whisper-large-v3") }
            )
            OutlinedTextField(
                value = cfg.asrLanguage,
                onValueChange = { update { c -> c.copy(asrLanguage = it) } },
                label = { Text("转写语言（留空自动检测）") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("中文视频建议 zh") }
            )
            OutlinedTextField(
                value = cfg.asrProxy,
                onValueChange = { update { c -> c.copy(asrProxy = it) } },
                label = { Text("ASR 代理（host:port，可选）") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Groq 在部分地区直连会 403，填代理即可，例如 192.168.1.2:7890") }
            )
            NumberField("ASR 单次上限（MB）", (cfg.asrMaxBytes / 1024 / 1024).toString()) { v ->
                v.toLongOrNull()?.let { mb ->
                    update { c -> c.copy(asrMaxBytes = mb.coerceIn(1, 100) * 1024 * 1024) }
                }
            }
        }

        Text("行为", style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("默认风格", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { styleMenu = true }) { Text(cfg.style.displayName) }
            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                SummaryStyle.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.displayName) },
                        onClick = {
                            styleMenu = false
                            update { c -> c.copy(style = s) }
                        }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("总结详略", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { detailMenu = true }) { Text(cfg.detailLevel.displayName) }
            DropdownMenu(expanded = detailMenu, onDismissRequest = { detailMenu = false }) {
                SummaryDetailLevel.entries.forEach { d ->
                    DropdownMenuItem(
                        text = { Text("${d.displayName}：${d.description}") },
                        onClick = {
                            detailMenu = false
                            update { c -> c.copy(detailLevel = d) }
                        }
                    )
                }
            }
        }
        Text(
            "风格决定结构（快速总结 / 完整笔记 / 章节大纲），详略决定信息密度与篇幅：" +
                "精简只留结论、详细保留论证与数据。两者冲突时以详略为准。",
            style = MaterialTheme.typography.bodySmall
        )

        SwitchRow("优先使用 B 站官方总结（省额度）", cfg.preferOfficialSummary) {
            update { c -> c.copy(preferOfficialSummary = it) }
        }
        Text(
            "关闭（默认）：先用你的模型生成，官方只在无字幕/失败时兜底。" +
                "开启：先取官方总结（免费、秒出），没有才用你的模型；官方覆盖率有限且内容较浅。",
            style = MaterialTheme.typography.bodySmall
        )
        SwitchRow("无字幕时降级为 B 站官方 AI 总结", cfg.fallbackToOfficial) {
            update { c -> c.copy(fallbackToOfficial = it) }
        }
        SwitchRow("启用本地结果缓存（避免重复花钱）", cfg.cacheEnabled) {
            update { c -> c.copy(cacheEnabled = it) }
        }
        SwitchRow("把结果写入视频笔记草稿", cfg.saveAsNoteDraft) {
            update { c -> c.copy(saveAsNoteDraft = it) }
        }

        Button(
            onClick = { configStore.save(cfg); onSaved() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }

        Text(
            "提示：Key 用 Android Keystore 加密后只存在本机；日志与诊断信息不会包含它。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
