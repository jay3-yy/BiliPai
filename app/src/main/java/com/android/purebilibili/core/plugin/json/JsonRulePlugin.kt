// 文件路径: core/plugin/json/JsonRulePlugin.kt
package com.android.purebilibili.core.plugin.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * 🎯 JSON 规则插件数据模型
 */
@Serializable
data class JsonRulePlugin(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "Unknown",
    val type: String,  // "feed" | "danmaku"
    val iconUrl: String? = null,
    val rules: List<Rule>
)

/**
 *  条件表达式（支持 AND/OR 嵌套）
 * 
 * 使用自定义序列化器根据 JSON 结构自动判断类型
 */
@Serializable(with = ConditionSerializer::class)
sealed class Condition {
    /**
     * 简单条件：单个字段比较
     */
    data class Simple(
        val field: String,
        val op: String,
        val value: JsonElement
    ) : Condition()
    
    /**
     * AND 条件：所有子条件都必须满足
     */
    data class And(
        val conditions: List<Condition>
    ) : Condition()
    
    /**
     * OR 条件：任一子条件满足即可
     */
    data class Or(
        val conditions: List<Condition>
    ) : Condition()
}

/**
 * 🔧 Condition 自定义序列化器
 * 
 * 根据 JSON 结构自动判断条件类型：
 * - 包含 "and" 键 -> And
 * - 包含 "or" 键 -> Or
 * - 包含 "field" 键 -> Simple
 */
object ConditionSerializer : KSerializer<Condition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Condition")
    
    override fun deserialize(decoder: Decoder): Condition {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("只支持 JSON 解码")
        val jsonElement = jsonDecoder.decodeJsonElement()
        
        return parseCondition(jsonElement)
    }
    
    override fun serialize(encoder: Encoder, value: Condition) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("只支持 JSON 编码")
        
        val jsonElement = when (value) {
            is Condition.Simple -> buildJsonObject {
                put("field", value.field)
                put("op", value.op)
                put("value", value.value)
            }
            is Condition.And -> buildJsonObject {
                putJsonArray("and") {
                    value.conditions.forEach { condition ->
                        add(encodeConditionToJson(condition))
                    }
                }
            }
            is Condition.Or -> buildJsonObject {
                putJsonArray("or") {
                    value.conditions.forEach { condition ->
                        add(encodeConditionToJson(condition))
                    }
                }
            }
        }
        jsonEncoder.encodeJsonElement(jsonElement)
    }
    
    private fun parseCondition(element: JsonElement): Condition {
        if (element !is JsonObject) {
            throw IllegalArgumentException("条件必须是 JSON 对象")
        }
        
        return when {
            // AND 条件
            "and" in element -> {
                val conditions = element["and"]?.jsonArray?.map { parseCondition(it) }
                    ?: throw IllegalArgumentException("and 必须是数组")
                Condition.And(conditions)
            }
            // OR 条件
            "or" in element -> {
                val conditions = element["or"]?.jsonArray?.map { parseCondition(it) }
                    ?: throw IllegalArgumentException("or 必须是数组")
                Condition.Or(conditions)
            }
            // 简单条件
            "field" in element -> {
                val field = element["field"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("field 必须是字符串")
                val op = element["op"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("op 必须是字符串")
                val value = element["value"]
                    ?: throw IllegalArgumentException("value 不能为空")
                Condition.Simple(field, op, value)
            }
            else -> throw IllegalArgumentException("无法识别的条件格式: $element")
        }
    }
    
    private fun encodeConditionToJson(condition: Condition): JsonElement {
        return when (condition) {
            is Condition.Simple -> buildJsonObject {
                put("field", condition.field)
                put("op", condition.op)
                put("value", condition.value)
            }
            is Condition.And -> buildJsonObject {
                putJsonArray("and") {
                    condition.conditions.forEach { add(encodeConditionToJson(it)) }
                }
            }
            is Condition.Or -> buildJsonObject {
                putJsonArray("or") {
                    condition.conditions.forEach { add(encodeConditionToJson(it)) }
                }
            }
        }
    }
}

/**
 * 单条规则
 * 
 * 支持两种格式：
 * 1. 旧格式（向后兼容）：直接使用 field/op/value
 * 2. 新格式：使用 condition 复合条件
 */
@Serializable
data class Rule(
    // 旧格式字段（向后兼容）
    val field: String? = null,
    val op: String? = null,
    val value: JsonElement? = null,
    
    //  新格式：复合条件
    val condition: Condition? = null,
    
    val action: String,       // 动作: hide, highlight, fold, badge, rewriteTitle
    val style: HighlightStyle? = null,  // 仅 highlight 时使用
    val pattern: String? = null,        // rewriteTitle 匹配正则
    val replacement: String? = null,    // rewriteTitle 替换文本
    val text: String? = null,           // badge 标签文本
    val groupLabel: String? = null      // fold 分组说明
) {
    /**
     * 获取统一的条件对象（兼容新旧格式）
     */
    @kotlinx.serialization.Transient
    private val legacyCondition: Condition? = if (field != null && op != null && value != null) {
        Condition.Simple(field, op, value)
    } else null

    fun toCondition(): Condition? = condition ?: legacyCondition
}

/**
 * 高亮样式
 */
@Serializable
data class HighlightStyle(
    val color: String? = null,     // 十六进制颜色 "#FFD700"
    val bold: Boolean = false,
    val scale: Float = 1.0f
)

/**
 * 规则操作符
 */
object RuleOperator {
    const val EQ = "eq"              // 等于
    const val NE = "ne"              // 不等于
    const val LT = "lt"              // 小于
    const val LE = "le"              // 小于等于
    const val GT = "gt"              // 大于
    const val GE = "ge"              // 大于等于
    const val CONTAINS = "contains"  // 包含
    const val STARTS_WITH = "startsWith"
    const val ENDS_WITH = "endsWith"
    const val REGEX = "regex"        // 正则匹配
    const val IN = "in"              // 在列表中
}

/**
 * 规则动作
 */
object RuleAction {
    const val HIDE = "hide"
    const val HIGHLIGHT = "highlight"
    const val FOLD = "fold"
    const val BADGE = "badge"
    const val REWRITE_TITLE = "rewriteTitle"
}


