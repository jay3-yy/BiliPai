package com.android.purebilibili.core.plugin.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal class JsonRuleSourceException(message: String, val path: String) : IllegalArgumentException(message)

internal data class JsonSourceSpan(val start: Int, val end: Int)
internal data class JsonSourceDiagnostic(val message: String, val span: JsonSourceSpan?, val line: Int?, val column: Int?)
internal data class JsonSourceCompletion(val label: String, val span: JsonSourceSpan, val replacement: String)
internal data class JsonSourceEdit(val text: String, val cursor: Int)

private data class SourceToken(val start: Int, val end: Int, val text: String)

/** 只记录词法范围；语法正确性由正式 JSON 解析器判断。未闭合字符串也保留，供输入补全。 */
private fun sourceTokens(source: String): List<SourceToken> = buildList {
    var offset = 0
    while (offset < source.length) {
        if (source[offset].isWhitespace()) { offset++; continue }
        val start = offset
        when (source[offset++]) {
            '"' -> while (offset < source.length) {
                when (source[offset++]) {
                    '\\' -> if (offset < source.length) offset++
                    '"' -> break
                }
            }
            '{', '}', '[', ']', ':', ',' -> Unit
            else -> while (offset < source.length && !source[offset].isWhitespace() && source[offset] !in "{}[]:,\"") offset++
        }
        add(SourceToken(start, offset, source.substring(start, offset)))
    }
}

/** 用解析器的 JSON path 定位原文，而不是按字段文字搜索，避免重复字段指向第一条规则。 */
private fun sourceSpans(source: String): Map<String, JsonSourceSpan> {
    val tokens = sourceTokens(source)
    val spans = mutableMapOf<String, JsonSourceSpan>()
    var index = 0
    fun visit(path: String) {
        val token = tokens.getOrNull(index) ?: return
        spans[path] = JsonSourceSpan(token.start, if (token.text == "{" || token.text == "[") token.start + 1 else token.end)
        index++
        when (token.text) {
            "{" -> {
                while (index < tokens.size && tokens[index].text != "}") {
                    val key = tokens[index++]
                    if (!key.text.startsWith('"') || !key.text.endsWith('"')) return
                    if (tokens.getOrNull(index)?.text != ":") return
                    index++
                    visit("$path.${Json.decodeFromString<String>(key.text)}")
                    if (tokens.getOrNull(index)?.text != ",") break
                    index++
                }
                if (tokens.getOrNull(index)?.text == "}") index++
            }
            "[" -> {
                var item = 0
                while (index < tokens.size && tokens[index].text != "]") {
                    visit("$path[${item++}]")
                    if (tokens.getOrNull(index)?.text != ",") break
                    index++
                }
                if (tokens.getOrNull(index)?.text == "]") index++
            }
        }
    }
    visit("$")
    return spans
}

internal fun diagnoseJsonSource(source: String, error: Throwable): JsonSourceDiagnostic {
    val message = error.message.orEmpty()
    val path = (error as? JsonRuleSourceException)?.path
        ?: Regex("at path: (\\$[^\\s]*)").find(message)?.groupValues?.get(1)
    val offset = Regex("at offset (\\d+)").find(message)?.groupValues?.get(1)?.toInt()
    val span = if (offset != null) {
        val start = offset.coerceIn(0, source.length)
        JsonSourceSpan(start, (start + 1).coerceAtMost(source.length))
    } else if (path != null) {
        // 原文已通过语法解析时才建立路径索引；不对语法损坏文本再次解析属性名。
        val spans = sourceSpans(source)
        spans.entries.filter { (key, _) -> path == key || path.startsWith("$key.") || path.startsWith("$key[") }
            .maxByOrNull { it.key.length }?.value
    } else null
    val prefix = span?.let { source.substring(0, it.start) }
    return JsonSourceDiagnostic(
        message = message.substringBefore("\nJSON input:"),
        span = span,
        line = prefix?.let { it.count { char -> char == '\n' } + 1 },
        column = prefix?.let { it.length - it.lastIndexOf('\n') },
    )
}

internal val videoRuleFields = listOf(
    "title", "duration", "bvid", "tid", "tname", "owner.mid", "owner.name",
    "stat.view", "stat.like", "stat.reply", "stat.danmaku", "stat.coin", "stat.favorite",
)
internal val danmakuRuleFields = listOf("content", "userId", "type")
internal val jsonRuleOperators = listOf("eq", "ne", "lt", "le", "gt", "ge", "contains", "startsWith", "endsWith", "regex", "in")

/** 根据光标所在的字符串提供候选，只替换当前 token，不覆盖后面的规则或其他重复字段。 */
internal fun completeJsonSource(source: String, cursor: Int, pluginType: String): List<JsonSourceCompletion> {
    val tokens = sourceTokens(source)
    val index = tokens.indexOfFirst { it.text.startsWith('"') && cursor > it.start && cursor <= it.end }
    if (index < 0) return emptyList()
    val token = tokens[index]
    val closed = token.text.length > 1 && token.text.last() == '"' && run {
        var slashes = 0
        var at = token.text.lastIndex - 1
        while (at >= 0 && token.text[at--] == '\\') slashes++
        slashes % 2 == 0
    }
    if (closed && cursor == token.end) return emptyList()
    val property = if (tokens.getOrNull(index - 1)?.text == ":") tokens.getOrNull(index - 2)?.text else null
    var depth = 0
    var declaredType = pluginType
    tokens.forEachIndexed { tokenIndex, candidate ->
        when (candidate.text) {
            "{", "[" -> depth++
            "}", "]" -> depth--
            "\"type\"" -> if (depth == 1 && tokens.getOrNull(tokenIndex + 1)?.text == ":") {
                when (tokens.getOrNull(tokenIndex + 2)?.text) {
                    "\"feed\"" -> declaredType = "feed"
                    "\"danmaku\"" -> declaredType = "danmaku"
                }
            }
        }
    }
    val fields = if (declaredType == "danmaku") danmakuRuleFields else videoRuleFields
    val options = when (property) {
        "\"field\"" -> fields
        "\"op\"" -> jsonRuleOperators
        "\"action\"" -> if (declaredType == "danmaku") listOf("hide", "highlight") else listOf("hide")
        "\"type\"" -> listOf("feed", "danmaku")
        else -> if (tokens.getOrNull(index - 1)?.text in listOf("{", ",")) {
            listOf("id", "name", "description", "version", "author", "type", "rules", "condition", "and", "or", "field", "op", "value", "action", "style")
        } else emptyList()
    }
    val prefix = source.substring(token.start + 1, cursor)
    return options.filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }.map {
        JsonSourceCompletion(it, JsonSourceSpan(token.start, token.end), JsonPrimitive(it).toString())
    }
}

internal fun applyJsonCompletion(source: String, completion: JsonSourceCompletion): JsonSourceEdit {
    val replacement = completion.replacement
    return JsonSourceEdit(
        source.replaceRange(completion.span.start, completion.span.end, replacement),
        completion.span.start + replacement.length - 1,
    )
}
