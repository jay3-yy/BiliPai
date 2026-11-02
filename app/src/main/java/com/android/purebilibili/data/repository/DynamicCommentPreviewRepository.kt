package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.AppDynamicCommentPreview
import com.android.purebilibili.core.network.grpc.DynamicCommentPreviewApi
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicBasic
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicInteractionModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.feature.dynamic.components.resolveDynamicCommentPreviewTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object DynamicCommentPreviewRepository {
    suspend fun enrich(items: List<DynamicItem>): List<DynamicItem> =
        enrichDynamicCommentPreviews(items, DynamicCommentPreviewApi::loadSpace)

    suspend fun enrichSpace(items: List<SpaceDynamicItem>, hostMid: Long): List<SpaceDynamicItem> {
        if (items.none(::spaceNeedsCommentPreview)) return items
        val previewItems = items.map { it.toPreviewItem(hostMid) }
        val enriched = enrich(previewItems)
        return items.mapChangedIndexed { index, item -> item.withPreviewItem(enriched[index]) }
    }
}

/** A public space cursor seeks to a requested card; no private-feed credentials or history scan. */
internal suspend fun enrichDynamicCommentPreviews(
    items: List<DynamicItem>,
    loadSpace: suspend (Long, String) -> Map<String, AppDynamicCommentPreview>,
): List<DynamicItem> = coroutineScope {
    val byAuthor = linkedMapOf<Long, MutableMap<String, Long>>()
    fun collect(item: DynamicItem) {
        if (item.needsCommentPreview()) {
            val id = item.id_str.toLongOrNull()?.takeIf { it > 0L }
            val author = item.modules.module_author?.mid?.takeIf { it > 0L }
            if (id != null && author != null) byAuthor.getOrPut(author, ::linkedMapOf)[item.id_str] = id
        }
        item.orig?.let(::collect)
    }
    items.forEach(::collect)
    val gate = Semaphore(4)
    val pages = byAuthor.map { (author, wanted) ->
        async {
            val remaining = wanted.toMutableMap()
            val found = mutableMapOf<String, AppDynamicCommentPreview>()
            while (remaining.isNotEmpty()) {
                val newest = remaining.maxBy { it.value }
                val cursor = if (newest.value == Long.MAX_VALUE) "" else (newest.value + 1L).toString()
                val response = try {
                    gate.withPermit { loadSpace(author, cursor) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Logger.w("DynamicPreview", "获取评论摘要失败: ${failure.javaClass.simpleName}")
                    break
                }
                for ((id, preview) in response) {
                    if (id in remaining && preview.dynamicId == id) {
                        found[id] = preview
                        remaining.remove(id)
                    }
                }
                // A deleted/private/missing card must not cause an unbounded cursor walk.
                remaining.remove(newest.key)
            }
            found
        }
    }.awaitAll()
    val previews = mutableMapOf<String, AppDynamicCommentPreview>()
    pages.forEach(previews::putAll)
    items.mapChangedIndexed { _, item -> mergeCommentPreview(item, previews) }
}

/** Also used when restoring old snapshots: unlocated Web previews are not rendered as located ones. */
internal fun retainLocatedDynamicCommentPreviews(items: List<DynamicItem>): List<DynamicItem> =
    items.mapChangedIndexed { _, item -> mergeCommentPreview(item, emptyMap()) }

internal fun restoreLocatedDynamicCommentPreviewMetadata(detail: DynamicItem, seed: DynamicItem?): DynamicItem {
    if (seed == null || seed.id_str != detail.id_str) return detail
    val original = detail.orig?.let { restoreLocatedDynamicCommentPreviewMetadata(it, seed.orig) }
    val base = if (original === detail.orig) detail else detail.copy(orig = original)
    val current = base.modules.module_interaction?.items.orEmpty()
    if (current.any { it.type == 1 && base.hasLocation(it) }) return base
    val comments = seed.modules.module_interaction?.items.orEmpty().filter { it.type == 1 && seed.hasLocation(it) }
    if (comments.isEmpty()) return base
    val hints = base.modules.module_interaction?.items ?: seed.modules.module_interaction?.items.orEmpty()
    return base.copy(
        basic = seed.basic ?: base.basic,
        modules = base.modules.copy(module_interaction = DynamicInteractionModule(replaceCommentHints(hints, comments))),
    )
}

private fun DynamicItem.needsCommentPreview(): Boolean =
    modules.module_interaction?.items.orEmpty().any { it.type == 1 && !hasLocation(it) }

private fun DynamicItem.hasLocation(preview: DynamicInteractionItem): Boolean {
    val target = resolveDynamicCommentPreviewTarget(this, preview) ?: return false
    return target.commentRootRpid > 0L || target.commentTargetRpid > 0L
}

private fun mergeCommentPreview(
    item: DynamicItem,
    previews: Map<String, AppDynamicCommentPreview>,
): DynamicItem {
    val original = item.orig?.let { mergeCommentPreview(it, previews) }
    val existing = item.modules.module_interaction ?: return if (original === item.orig) item else item.copy(orig = original)
    if (existing.items.none { it.type == 1 }) return if (original === item.orig) item else item.copy(orig = original)
    val preview = previews[item.id_str]?.takeIf { it.dynamicId == item.id_str }
    val subject = preview?.takeIf { it.commentOid > 0L && it.commentType > 0 }
    val basic = subject?.let {
        (item.basic ?: DynamicBasic()).copy(comment_id_str = it.commentOid.toString(), comment_type = it.commentType)
    } ?: item.basic
    val locatedItem = if (basic == item.basic) item else item.copy(basic = basic)
    val candidates = if (preview != null) {
        if (subject != null) preview.interaction?.items.orEmpty() else emptyList()
    } else existing.items
    val comments = candidates.filter { it.type == 1 && locatedItem.hasLocation(it) }
    val merged = replaceCommentHints(existing.items, comments)
    val retainedBasic = if (comments.isNotEmpty()) basic else item.basic
    if (merged == existing.items && original === item.orig && retainedBasic == item.basic) return item
    return item.copy(
        basic = retainedBasic,
        orig = original,
        modules = item.modules.copy(module_interaction = merged.takeIf { it.isNotEmpty() }?.let(::DynamicInteractionModule)),
    )
}

private fun replaceCommentHints(
    existing: List<DynamicInteractionItem>,
    comments: List<DynamicInteractionItem>,
): List<DynamicInteractionItem> = buildList {
    var inserted = false
    for (hint in existing) {
        if (hint.type == 1) {
            if (!inserted) {
                addAll(comments)
                inserted = true
            }
        } else add(hint)
    }
    if (!inserted) addAll(comments)
}

private fun spaceNeedsCommentPreview(item: SpaceDynamicItem): Boolean =
    item.modules.module_interaction?.items.orEmpty().any { it.type == 1 } || item.orig?.let(::spaceNeedsCommentPreview) == true

private fun SpaceDynamicItem.toPreviewItem(hostMid: Long): DynamicItem = DynamicItem(
    id_str = id_str,
    type = type,
    basic = basic,
    modules = DynamicModules(
        module_author = DynamicAuthorModule(mid = modules.module_author?.mid?.takeIf { it > 0L } ?: hostMid),
        module_interaction = modules.module_interaction,
    ),
    orig = orig?.toPreviewItem(0L),
)

private fun SpaceDynamicItem.withPreviewItem(item: DynamicItem): SpaceDynamicItem {
    val original = orig?.let { child -> item.orig?.let(child::withPreviewItem) ?: child }
    if (basic == item.basic && modules.module_interaction == item.modules.module_interaction && original === orig) return this
    return copy(
        basic = item.basic,
        orig = original,
        modules = modules.copy(module_interaction = item.modules.module_interaction),
    )
}

private inline fun <T> List<T>.mapChangedIndexed(transform: (Int, T) -> T): List<T> {
    var changed: MutableList<T>? = null
    forEachIndexed { index, value ->
        val updated = transform(index, value)
        if (updated !== value) {
            val destination = changed ?: toMutableList().also { changed = it }
            destination[index] = updated
        }
    }
    return changed ?: this
}
