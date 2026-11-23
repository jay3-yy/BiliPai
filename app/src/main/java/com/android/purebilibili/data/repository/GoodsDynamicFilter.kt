package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterHit
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.FilteredContentKind
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem

internal fun isDynamicFromBlockedUser(item: DynamicItem, blockedMids: Set<Long>): Boolean =
    item.modules.module_author?.mid in blockedMids || item.orig?.let {
        isDynamicFromBlockedUser(it, blockedMids)
    } == true

internal fun isGoodsDynamic(item: DynamicItem): Boolean =
    item.modules.module_dynamic?.additional?.type == "ADDITIONAL_TYPE_GOODS" ||
        item.orig?.let(::isGoodsDynamic) == true

internal fun isGoodsSpaceDynamic(item: SpaceDynamicItem): Boolean =
    item.modules.module_dynamic?.additional?.type == "ADDITIONAL_TYPE_GOODS" ||
        item.orig?.let(::isGoodsSpaceDynamic) == true

internal fun recordFilteredDynamic(
    item: DynamicItem,
    reason: ContentFilterReason,
    matchedRule: String = "",
) {
    val id = item.id_str.ifBlank { return }
    val author = item.modules.module_author?.name.orEmpty()
    val authorMid = item.modules.module_author?.mid ?: 0L
    val summary = buildDynamicHistorySummary(item)
    ContentFilterHistory.record(
        FilteredContentKind.DYNAMIC,
        ContentFilterHit(
            id = id,
            text = summary,
            author = author,
            reason = reason,
            authorMid = authorMid,
            matchedRule = matchedRule,
        ),
    )
}

internal fun recordFilteredSpaceDynamic(
    item: SpaceDynamicItem,
    reason: ContentFilterReason,
    matchedRule: String = "",
) {
    val id = item.id_str.ifBlank { return }
    val author = item.modules.module_author?.name.orEmpty()
    val authorMid = item.modules.module_author?.mid ?: 0L
    val summary = buildSpaceDynamicHistorySummary(item)
    ContentFilterHistory.record(
        FilteredContentKind.DYNAMIC,
        ContentFilterHit(
            id = id,
            text = summary,
            author = author,
            reason = reason,
            authorMid = authorMid,
            matchedRule = matchedRule,
        ),
    )
}

private fun buildDynamicHistorySummary(item: DynamicItem): String {
    val own = firstNonBlank(
        item.modules.module_dynamic?.desc?.text,
        item.modules.module_dynamic?.major?.archive?.title,
        item.modules.module_dynamic?.major?.opus?.title,
        item.modules.module_dynamic?.major?.opus?.summary?.text,
        item.modules.module_dynamic?.major?.article?.title,
    )
    val text = own.ifBlank { item.orig?.let(::buildDynamicHistorySummary).orEmpty() }.trim()
    return if (text.length > 160) text.take(160) + "…" else text
}

private fun buildSpaceDynamicHistorySummary(item: SpaceDynamicItem): String {
    val own = firstNonBlank(
        item.modules.module_dynamic?.desc?.text,
        item.modules.module_dynamic?.major?.archive?.title,
        item.modules.module_dynamic?.major?.opus?.title,
        item.modules.module_dynamic?.major?.opus?.summary?.text,
        item.modules.module_dynamic?.major?.article?.title,
    )
    val text = own.ifBlank { item.orig?.let(::buildSpaceDynamicHistorySummary).orEmpty() }.trim()
    return if (text.length > 160) text.take(160) + "…" else text
}

private fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

internal suspend fun goodsDynamicFilterEnabled(): Boolean =
    com.android.purebilibili.core.network.NetworkModule.appContext?.let {
        com.android.purebilibili.core.store.ContentDisplaySettings.enabled(
            it, com.android.purebilibili.core.store.ContentDisplaySettings.BLOCK_GOODS_DYNAMIC
        )
    } ?: false

internal suspend fun filterGoodsSpaceDynamics(
    items: List<SpaceDynamicItem>,
): List<SpaceDynamicItem> {
    val blockGoods = goodsDynamicFilterEnabled()
    val blocked = com.android.purebilibili.core.network.NetworkModule.appContext?.let {
        com.android.purebilibili.core.store.ContentFilterRules.snapshot(it).blockedMids
    }.orEmpty()
    fun blockedUser(item: SpaceDynamicItem): Boolean =
        item.modules.module_author?.mid in blocked || item.orig?.let(::blockedUser) == true
    return items.filterNot { item ->
        when {
            blockedUser(item) -> {
                recordFilteredSpaceDynamic(item, ContentFilterReason.BLOCKED_USER)
                true
            }
            blockGoods && isGoodsSpaceDynamic(item) -> {
                recordFilteredSpaceDynamic(item, ContentFilterReason.GOODS_DYNAMIC)
                true
            }
            else -> false
        }
    }
}
