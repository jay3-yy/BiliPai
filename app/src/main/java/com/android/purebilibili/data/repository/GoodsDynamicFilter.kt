package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.DynamicItem

internal fun isDynamicFromBlockedUser(item: DynamicItem, blockedMids: Set<Long>): Boolean =
    item.modules.module_author?.mid in blockedMids || item.orig?.let {
        isDynamicFromBlockedUser(it, blockedMids)
    } == true

internal fun isGoodsDynamic(item: DynamicItem): Boolean =
    item.modules.module_dynamic?.additional?.type == "ADDITIONAL_TYPE_GOODS" ||
        item.orig?.let(::isGoodsDynamic) == true

internal fun isGoodsSpaceDynamic(item: com.android.purebilibili.data.model.response.SpaceDynamicItem): Boolean =
    item.modules.module_dynamic?.additional?.type == "ADDITIONAL_TYPE_GOODS" ||
        item.orig?.let(::isGoodsSpaceDynamic) == true

internal suspend fun goodsDynamicFilterEnabled(): Boolean =
    com.android.purebilibili.core.network.NetworkModule.appContext?.let {
        com.android.purebilibili.core.store.ContentDisplaySettings.enabled(
            it, com.android.purebilibili.core.store.ContentDisplaySettings.BLOCK_GOODS_DYNAMIC
        )
    } ?: false

internal suspend fun filterGoodsSpaceDynamics(
    items: List<com.android.purebilibili.data.model.response.SpaceDynamicItem>,
): List<com.android.purebilibili.data.model.response.SpaceDynamicItem> {
    val blockGoods = goodsDynamicFilterEnabled()
    val blocked = com.android.purebilibili.core.network.NetworkModule.appContext?.let {
        com.android.purebilibili.core.store.ContentFilterRules.snapshot(it).blockedMids
    }.orEmpty()
    fun blockedUser(item: com.android.purebilibili.data.model.response.SpaceDynamicItem): Boolean =
        item.modules.module_author?.mid in blocked || item.orig?.let(::blockedUser) == true
    return items.filterNot { blockedUser(it) || (blockGoods && isGoodsSpaceDynamic(it)) }
}
