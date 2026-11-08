package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.json.*
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val sourceEditorJson = Json { prettyPrint = true; ignoreUnknownKeys = true }
private val newRuleSource = """
{
  "id": "filter_documentary_uploaders",
  "name": "过滤纪录片后缀UP主",
  "version": "1.0.0",
  "type": "feed",
  "rules": [
    {
      "field": "owner.name",
      "op": "regex",
      "value": "纪录片$",
      "action": "hide"
    }
  ]
}
""".trimIndent()

private val feedTestSamples = """
[
  {"title": "自然探索", "owner": {"name": "探索纪录片", "mid": 1}, "duration": 600, "stat": {"view": 1000}},
  {"title": "旅行日记", "owner": {"name": "小明", "mid": 2}, "duration": 120, "stat": {"view": 500}},
  {"title": "幕后故事", "owner": {"name": "纪录片爱好者", "mid": 3}, "duration": 300, "stat": {"view": 800}}
]
""".trimIndent()
private val danmakuTestSamples = """
[
  {"content": "测试弹幕", "userId": "123", "type": 1},
  {"content": "另一条弹幕", "userId": "456", "type": 5}
]
""".trimIndent()

private data class JsonRuleEditorEvaluation(
    val plugin: JsonRulePlugin?,
    val ruleError: JsonSourceDiagnostic?,
    val sampleError: JsonSourceDiagnostic?,
    val results: List<JsonRuleTestResult>,
)

@Composable
internal fun JsonRuleSourceEditorScreen(
    plugin: JsonRulePlugin?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var source by rememberSaveable(plugin?.id) {
        mutableStateOf(plugin?.let { sourceEditorJson.encodeToString(JsonRulePlugin.serializer(), it) } ?: newRuleSource)
    }
    var samples by rememberSaveable(plugin?.id) {
        mutableStateOf(if (plugin?.type == "danmaku") danmakuTestSamples else feedTestSamples)
    }
    var checkedSource by remember { mutableStateOf<String?>(null) }
    var checkedSamples by remember { mutableStateOf<String?>(null) }
    var parsedPlugin by remember { mutableStateOf<JsonRulePlugin?>(null) }
    var ruleError by remember { mutableStateOf<JsonSourceDiagnostic?>(null) }
    var sampleError by remember { mutableStateOf<JsonSourceDiagnostic?>(null) }
    var results by remember { mutableStateOf<List<JsonRuleTestResult>>(emptyList()) }
    var checking by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var savedSource by remember { mutableStateOf<String?>(null) }
    var installedId by rememberSaveable(plugin?.id) { mutableStateOf(plugin?.id) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(source, samples) {
        checking = true
        delay(250)
        val evaluated = withContext(Dispatchers.Default) {
            val parsed = runCatching { JsonPluginManager.parsePluginSource(source) }
            val tested = parsed.getOrNull()?.let { runCatching { previewJsonRules(it, samples) } }
            JsonRuleEditorEvaluation(
                plugin = parsed.getOrNull(),
                ruleError = parsed.exceptionOrNull()?.let { diagnoseJsonSource(source, it) },
                sampleError = tested?.exceptionOrNull()?.let { diagnoseJsonSource(samples, it) },
                results = tested?.getOrNull().orEmpty(),
            )
        }
        parsedPlugin = evaluated.plugin
        ruleError = evaluated.ruleError
        sampleError = evaluated.sampleError
        results = evaluated.results
        checkedSource = source
        checkedSamples = samples
        checking = false
    }

    SettingsPageScaffold(
        modifier = modifier,
        title = if (plugin == null) "编写 JSON 规则" else "编辑 JSON 规则",
        onBack = onBack,
        backContentDescription = "返回",
        bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        scrollHost = SettingsPageScrollHost.External,
        actions = {
            AppTextButton(
                enabled = !saving && checkedSource == source && parsedPlugin != null && savedSource != source,
                onClick = {
                    saving = true
                    saveError = null
                    val sourceToSave = source
                    scope.launch {
                        try {
                            val installed = JsonPluginManager.installFromSource(sourceToSave, installedId)
                            installedId = installed.id
                            savedSource = sourceToSave
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            saveError = e.message
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { AppText(if (saving) "保存中…" else if (installedId == null) "安装" else "保存") }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(settingsScrollContentPadding(extraHorizontal = 16.dp, extraVertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText("直接编写或粘贴完整 JSON。测试只预览当前草稿，安装或保存后才生效。", color = AppContentColors.secondaryText())
            AppText("视频规则用于首页视频流和视频搜索结果，并非全局屏蔽。", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
            JsonSourceCodeField(
                value = source,
                onValueChange = { if (!saving) { source = it; saveError = null } },
                label = "插件 JSON",
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 18,
                readOnly = saving,
                pluginType = parsedPlugin?.type ?: "feed",
                diagnostic = ruleError.takeIf { checkedSource == source },
            )
            if (checking || checkedSource != source || checkedSamples != samples) {
                AppText("正在检查规则与样例…")
            } else if (ruleError != null) {
                AppText("请先修正规则错误，再安装或保存。", color = AppContentColors.error())
            } else {
                parsedPlugin?.let {
                    AppText("${it.name} · ${it.rules.size} 条规则 · ${if (it.type == "feed") "视频过滤" else "弹幕规则"}")
                }
            }
            saveError?.let { AppText("保存失败：$it", color = AppContentColors.error()) }
            if (savedSource == source) AppText("已保存到插件中心，新插件默认启用；已有插件保留原开关状态。")
            AppText("实时测试", style = MaterialTheme.typography.titleMedium)
            AppText("样例为 JSON 数组，可添加多条。视频可填写 owner.name、title、duration、stat 等字段；弹幕可填写 content、userId、type。", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextButton(onClick = { samples = feedTestSamples }) { AppText("视频样例") }
                AppTextButton(onClick = { samples = danmakuTestSamples }) { AppText("弹幕样例") }
            }
            JsonSourceCodeField(
                value = samples,
                onValueChange = { samples = it },
                label = "测试样例 JSON",
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 12,
                diagnostic = sampleError.takeIf { checkedSamples == samples && checkedSource == source },
            )
            if (!checking && checkedSource == source && checkedSamples == samples && parsedPlugin != null) {
                if (sampleError == null) {
                    AppText("共 ${results.size} 条 · 隐藏 ${results.count { it.hidden }} 条 · 保留 ${results.count { !it.hidden }} 条")
                    results.forEach { result ->
                        AppSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppText(result.label)
                                AppText(
                                    if (result.hidden) "隐藏" else if (result.highlighted) "保留并高亮" else "保留",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                AppText(
                                    if (result.matchedRules.isEmpty()) "未命中规则" else "命中第 ${result.matchedRules.joinToString("、")} 条规则",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
