package com.android.purebilibili.core.plugin.json

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.DanmakuItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** 编辑器只接受引擎实际支持的字段、操作与值，避免错误规则显示为测试通过。 */
internal fun validateEditableJsonRules(plugin: JsonRulePlugin) {
    val fields = if (plugin.type == "feed") videoRuleFields.toSet() else danmakuRuleFields.toSet()
    val numericFields = fields - setOf("title", "bvid", "tname", "owner.name", "content", "userId")
    val numericOps = setOf("lt", "le", "gt", "ge")
    val textOps = setOf("contains", "startsWith", "endsWith", "regex")
    fun checkValue(valid: Boolean, path: String, message: String) {
        if (!valid) throw JsonRuleSourceException(message, path)
    }
    fun validate(condition: Condition, path: String) {
        when (condition) {
            is Condition.And -> {
                checkValue(condition.conditions.isNotEmpty(), "$path.and", "AND 条件不能为空")
                condition.conditions.forEachIndexed { index, child -> validate(child, "$path.and[$index]") }
            }
            is Condition.Or -> {
                checkValue(condition.conditions.isNotEmpty(), "$path.or", "OR 条件不能为空")
                condition.conditions.forEachIndexed { index, child -> validate(child, "$path.or[$index]") }
            }
            is Condition.Simple -> {
                checkValue(condition.field in fields, "$path.field", "不支持的字段：${condition.field}")
                checkValue(condition.op in jsonRuleOperators, "$path.op", "不支持的操作：${condition.op}")
                if (condition.op in numericOps) {
                    checkValue(condition.field in numericFields, "$path.field", "${condition.field} 不支持数值比较")
                }
                val values = if (condition.op == "in") {
                    val array = condition.value as? JsonArray
                        ?: throw JsonRuleSourceException("in 的值必须是数组", "$path.value")
                    array.toList()
                } else listOf(condition.value)
                values.forEachIndexed { index, element ->
                    val valuePath = if (condition.op == "in") "$path.value[$index]" else "$path.value"
                    val value = element as? JsonPrimitive
                        ?: throw JsonRuleSourceException("条件值必须是文字或数字", valuePath)
                    checkValue(value != JsonNull, valuePath, "条件值不能为 null")
                    if (condition.op in textOps) {
                        checkValue(value.isString, valuePath, "文字匹配需要字符串值")
                    } else if (condition.op in numericOps) {
                        checkValue(!value.isString && value.doubleOrNull?.isFinite() == true, valuePath, "数值比较需要有效数字")
                    } else if (condition.field in numericFields) {
                        checkValue(!value.isString && value.longOrNull != null, valuePath, "${condition.field} 需要整数值")
                    } else checkValue(value.isString, valuePath, "${condition.field} 需要字符串值")
                }
                if (condition.op == "regex") {
                    try {
                        Regex(condition.value.jsonPrimitive.content)
                    } catch (error: java.util.regex.PatternSyntaxException) {
                        throw JsonRuleSourceException("正则表达式错误：${error.description}", "$path.value")
                    }
                }
            }
        }
    }
    plugin.rules.forEachIndexed { index, rule ->
        val path = "$.rules[$index]"
        checkValue(rule.action == "hide" || (plugin.type == "danmaku" && rule.action == "highlight"),
            "$path.action", "第 ${index + 1} 条规则不支持动作：${rule.action}")
        val condition = rule.toCondition() ?: throw JsonRuleSourceException("第 ${index + 1} 条规则缺少条件", path)
        validate(condition, if (rule.condition != null) "$path.condition" else path)
    }
}

@Serializable
private data class DanmakuTestSample(val content: String, val userId: String = "", val type: Int = 1)

internal data class JsonRuleTestResult(val label: String, val hidden: Boolean, val matchedRules: List<Int>, val highlighted: Boolean = false)

/** 使用正式规则引擎逐条评估，不发布过滤统计或修改插件开关。 */
internal fun previewJsonRules(plugin: JsonRulePlugin, samplesSource: String): List<JsonRuleTestResult> {
    val json = Json
    json.parseToJsonElement(samplesSource)
    return if (plugin.type == "feed") {
        json.decodeFromString<List<VideoItem>>(samplesSource).mapIndexed { index, video ->
            val matches = plugin.rules.mapIndexedNotNull { ruleIndex, rule ->
                if (!RuleEngine.shouldShowVideo(video, listOf(rule))) ruleIndex + 1 else null
            }
            JsonRuleTestResult("${index + 1}. ${video.owner.name} · ${video.title}", matches.isNotEmpty(), matches)
        }
    } else {
        json.decodeFromString<List<DanmakuTestSample>>(samplesSource).mapIndexed { index, data ->
            val danmaku = DanmakuItem(id = index.toLong(), content = data.content, timeMs = 0, userId = data.userId, type = data.type)
            val matches = plugin.rules.mapIndexedNotNull { ruleIndex, rule ->
                if (!RuleEngine.shouldShowDanmaku(danmaku, listOf(rule)) ||
                    RuleEngine.getDanmakuHighlightStyle(danmaku, listOf(rule)) != null) ruleIndex + 1 else null
            }
            JsonRuleTestResult("${index + 1}. ${data.content}", !RuleEngine.shouldShowDanmaku(danmaku, plugin.rules), matches,
                RuleEngine.getDanmakuHighlightStyle(danmaku, plugin.rules) != null)
        }
    }
}
