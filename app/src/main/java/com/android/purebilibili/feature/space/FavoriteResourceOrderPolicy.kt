package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.FavoriteData

internal val FavoriteData.orderKey: String get() = "$id:$type"

internal fun moveFavoriteResource(items: List<FavoriteData>, from: String, to: String): List<FavoriteData> {
    val source = items.indexOfFirst { it.orderKey == from }
    val target = items.indexOfFirst { it.orderKey == to }
    if (source < 0 || target < 0 || source == target) return items
    return items.toMutableList().apply { add(target, removeAt(source)) }
}

/** 每个操作为“前一项ID:类型:移动项ID:类型”；0:0 表示移到开头，保留未加载内容。 */
internal fun favoriteResourceOrderPayload(original: List<FavoriteData>, draft: List<FavoriteData>): String {
    val originalKeys = original.map { it.orderKey }
    val targetKeys = draft.map { it.orderKey }
    require(originalKeys.size == originalKeys.distinct().size && targetKeys.size == targetKeys.distinct().size)
    require(originalKeys.toSet() == targetKeys.toSet()) { "收藏内容已变化，请重新读取" }
    require(draft.all { it.id > 0L && it.type > 0 }) { "收藏内容身份无效" }
    val working = originalKeys.toMutableList()
    return buildList {
        targetKeys.forEachIndexed { index, key ->
            if (working[index] != key) {
                add("${targetKeys.getOrNull(index - 1) ?: "0:0"}:$key")
                working.remove(key)
                working.add(index, key)
            }
        }
    }.joinToString(",")
}
