package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class LiveMessageMedal(
    val name: String,
    val level: Int,
    val backgroundColor: Int,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val rawInfo: JsonObject? = null,
)

/** 新版点亮勋章优先；明确未点亮时不回退到旧版勋章。 */
fun parseLiveMessageMedal(modern: JsonObject?, legacy: JsonObject? = null): LiveMessageMedal? {
    val value = modern ?: legacy ?: return null
    if (modern != null && (modern["is_light"] as? JsonPrimitive)?.intOrNull != 1) return null
    return parseLiveMedal(value, modern = modern != null)
}

fun parseLiveRoomMedal(value: JsonObject): LiveMessageMedal? = parseLiveMedal(value, modern = true)

private fun parseLiveMedal(value: JsonObject, modern: Boolean): LiveMessageMedal? {
    fun text(key: String) = (value[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun number(key: String) = (value[key] as? JsonPrimitive)?.intOrNull
    val name = text("name").ifBlank { text("medal_name") }
    val level = number("level") ?: number("medal_level") ?: 0
    if (name.isBlank() || level <= 0) return null
    val background = if (modern) parseLiveMedalColor(text("v2_medal_color_start"), rgba = true)
        else parseLiveMedalColor(text("medal_color"))
    val foreground = parseLiveMedalColor(text("v2_medal_color_text")).takeIf { it != 0 } ?: 0xFFFFFFFF.toInt()
    return LiveMessageMedal(name, level, background, foreground, value)
}

fun parseLiveSuperChatMedal(data: JsonObject): LiveMessageMedal? = parseLiveMessageMedal(
    (data["uinfo"] as? JsonObject)?.get("medal") as? JsonObject,
    data["medal_info"] as? JsonObject,
)

private fun parseLiveMedalColor(raw: String, rgba: Boolean = false): Int {
    val value = if (raw.startsWith("#")) raw.drop(1).toLongOrNull(16) else raw.toLongOrNull()
    val color = value?.toInt() ?: return 0
    if (!raw.startsWith("#")) return color or 0xFF000000.toInt()
    return when (raw.length - 1) {
        6 -> color or 0xFF000000.toInt()
        8 -> if (rgba) (color ushr 8) or (color shl 24) else color
        else -> 0
    }
}
