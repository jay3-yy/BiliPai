package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.repository.recordFilteredDynamic

data class DynamicFeedFilterState(
    val selectedFollowGroupTagId: Long? = null,
    val hideForward: Boolean = false,
    val hideLottery: Boolean = false,
    val hideGoods: Boolean = false,
    val hideReserve: Boolean = false,
    val hideArticle: Boolean = false,
    val hideLiveRcmd: Boolean = false,
) {
    val hasTypeFilter: Boolean
        get() = hideForward ||
            hideLottery ||
            hideGoods ||
            hideReserve ||
            hideArticle ||
            hideLiveRcmd

    val isActive: Boolean
        get() = selectedFollowGroupTagId != null || hasTypeFilter
}

internal fun filterDynamicFeedItems(
    items: List<DynamicItem>,
    filter: DynamicFeedFilterState,
    selectedGroupMemberMids: Set<Long>?,
): List<DynamicItem> {
    if (!filter.isActive || items.isEmpty()) return items
    return items.filter { item ->
        shouldKeepDynamicFeedItem(
            item = item,
            filter = filter,
            selectedGroupMemberMids = selectedGroupMemberMids,
        )
    }
}

internal fun resolveDynamicTypeFilterReason(
    item: DynamicItem,
    filter: DynamicFeedFilterState,
): ContentFilterReason? {
    if (filter.hideForward && item.type == "DYNAMIC_TYPE_FORWARD") return ContentFilterReason.DYNAMIC_FORWARD
    if (filter.hideArticle && (item.type == "DYNAMIC_TYPE_ARTICLE" || item.orig?.type == "DYNAMIC_TYPE_ARTICLE")) {
        return ContentFilterReason.DYNAMIC_ARTICLE
    }
    if (filter.hideLiveRcmd && (item.type == "DYNAMIC_TYPE_LIVE_RCMD" || item.orig?.type == "DYNAMIC_TYPE_LIVE_RCMD")) {
        return ContentFilterReason.DYNAMIC_LIVE
    }
    if (filter.hideGoods && (isGoodsDynamic(item) || item.orig?.let(::isGoodsDynamic) == true)) {
        return ContentFilterReason.GOODS_DYNAMIC
    }
    if (filter.hideReserve && (isReserveDynamic(item) || item.orig?.let(::isReserveDynamic) == true)) {
        return ContentFilterReason.DYNAMIC_RESERVE
    }
    if (filter.hideLottery && (isLotteryDynamic(item) || item.orig?.let(::isLotteryDynamic) == true)) {
        return ContentFilterReason.DYNAMIC_LOTTERY
    }
    return null
}

internal fun recordFilteredDynamicFeedTypeHits(
    items: List<DynamicItem>,
    filter: DynamicFeedFilterState,
) {
    if (!filter.hasTypeFilter || items.isEmpty()) return
    for (item in items) {
        val reason = resolveDynamicTypeFilterReason(item, filter) ?: continue
        recordFilteredDynamic(item, reason)
    }
}

internal fun shouldKeepDynamicFeedItem(
    item: DynamicItem,
    filter: DynamicFeedFilterState,
    selectedGroupMemberMids: Set<Long>?,
): Boolean {
    if (filter.selectedFollowGroupTagId != null && selectedGroupMemberMids != null) {
        val authorMid = item.modules.module_author?.mid ?: 0L
        if (authorMid !in selectedGroupMemberMids) return false
    }
    return resolveDynamicTypeFilterReason(item, filter) == null
}

internal fun extractDynamicFilterText(item: DynamicItem): String = buildString {
    appendDynamicOwnText(item)
    item.orig?.let { orig ->
        if (isNotEmpty()) append('\n')
        appendDynamicOwnText(orig)
    }
}

private fun isGoodsDynamic(item: DynamicItem): Boolean {
    val additional = item.modules.module_dynamic?.additional ?: return false
    return additional.goods != null || additional.type == "ADDITIONAL_TYPE_GOODS"
}

private fun isReserveDynamic(item: DynamicItem): Boolean {
    val additional = item.modules.module_dynamic?.additional ?: return false
    return additional.reserve != null || additional.type == "ADDITIONAL_TYPE_RESERVE"
}

private fun isLotteryDynamic(item: DynamicItem): Boolean {
    val additional = item.modules.module_dynamic?.additional
    if (additional?.type == "ADDITIONAL_TYPE_LOTTERY") return true
    val desc = item.modules.module_dynamic?.desc?.text.orEmpty()
    return desc.contains("互动抽奖")
}

private fun StringBuilder.appendDynamicOwnText(item: DynamicItem) {
    val dynamic = item.modules.module_dynamic ?: return
    dynamic.desc?.text?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
    dynamic.major?.archive?.title?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
    dynamic.major?.opus?.title?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
    dynamic.major?.opus?.summary?.text?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
    dynamic.major?.article?.title?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
}
