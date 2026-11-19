package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/** 消息内的表情资源优先于全局表情缓存；尺寸单位由显示场景决定。 */
@kotlinx.serialization.Serializable
data class LiveMessageEmote(
    val url: String,
    val width: Int = 24,
    val height: Int = 24,
    val official: Boolean = false,
    val unique: String = "",
    val rawInfo: JsonObject? = null,
)

fun parseLiveMessageEmote(value: JsonObject?): LiveMessageEmote? {
    fun text(key: String) = (value?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun number(key: String) = (value?.get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }?.roundToInt()
    val url = text("url").ifBlank { text("emoticon_url") }
    if (url.isBlank()) return null
    val width = (number("width") ?: 24).coerceIn(1, 1024)
    return LiveMessageEmote(url, width,
        (number("height") ?: width).coerceIn(1, 1024), text("emoticon_unique").startsWith("official_"), text("emoticon_unique"), value)
}

fun parseLiveMessageEmotes(value: JsonObject?): Map<String, LiveMessageEmote> =
    value.orEmpty().mapNotNull { (key, resource) ->
        if (key.isBlank()) null else parseLiveMessageEmote(resource as? JsonObject)?.let { key to it }
    }.toMap()

/** 最长标记优先，避免表情名重叠时先消费短标记。 */
fun compileLiveMessageEmotePattern(keys: Set<String>): Regex? {
    val valid = keys.filter { it.isNotEmpty() }.sortedByDescending { it.length }
    return if (valid.isEmpty()) null else Regex(valid.joinToString("|") { Regex.escape(it) })
}

fun splitLiveMessageEmotes(text: String, keys: Set<String>): List<Pair<String, Boolean>> {
    val pattern = compileLiveMessageEmotePattern(keys) ?: return listOf(text to false)
    return buildList {
        var offset = 0
        pattern.findAll(text).forEach { match ->
            if (match.range.first > offset) add(text.substring(offset, match.range.first) to false)
            add(match.value to true)
            offset = match.range.last + 1
        }
        if (offset < text.length) add(text.substring(offset) to false)
    }
}
