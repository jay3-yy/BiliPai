// 文件路径: feature/plugin/SponsorFeedBadge.kt
package com.android.purebilibili.feature.plugin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.repository.SponsorBlockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.components.LocalListAuxiliaryWorkAllowed
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardAuxiliaryWorkAllowed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 列表恰饭角标文案；仅空降社区数据存在 sponsor（赞助/恰饭广告）类别片段时返回。 */
internal const val SPONSOR_FEED_BADGE_LABEL = "恰饭"

internal fun resolveSponsorFeedBadgeLabel(segments: List<SponsorSegment>): String? {
    return if (segments.any { it.category == SponsorCategory.SPONSOR }) {
        SPONSOR_FEED_BADGE_LABEL
    } else {
        null
    }
}

/**
 * 列表恰饭角标的进程内缓存：列表页同屏可能同时出现同一 bvid 的主卡与转发卡，
 * 且空结果（非恰饭）占绝大多数，需要比播放页片段缓存更长的 TTL 避免滚动往返反复请求。
 */
internal object SponsorFeedBadgeStore {
    private const val CACHE_LIMIT = 256
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private data class CachedFeedBadge(val label: String?, val expiresAtMs: Long)

    private val cache = LinkedHashMap<String, CachedFeedBadge>()
    // Optional badges are serialized. Waiting callers recheck the cache after acquiring
    // the permit; cancellation leaves no independent request or stale in-flight owner.
    private val requests = Mutex()

    private fun cachedBadge(bvid: String): CachedFeedBadge? = synchronized(cache) {
        cache[bvid]?.takeIf { it.expiresAtMs > android.os.SystemClock.elapsedRealtime() }?.also {
            cache.remove(bvid)
            cache[bvid] = it
        }
    }

    suspend fun badgeLabel(context: Context, bvid: String): String? {
        cachedBadge(bvid)?.let { return it.label }
        return requests.withLock {
            // Another visible copy may have completed while this caller queued.
            cachedBadge(bvid)?.let { return@withLock it.label }
            queryBadgeLabel(context, bvid)
        }
    }

    private suspend fun queryBadgeLabel(context: Context, bvid: String): String? {
        // 角标只关心是否存在恰饭片段，请求失败与无片段同为不显示，复用 getSegments 的兼容语义。
        val label = resolveSponsorFeedBadgeLabel(
            SponsorBlockRepository.getSegments(
                bvid = bvid,
                categories = listOf(SponsorCategory.SPONSOR),
                baseUrl = loadSponsorBlockConfig(context).serverBaseUrl,
            )
        )
        currentCoroutineContext().ensureActive()
        synchronized(cache) {
            cache.remove(bvid)
            cache[bvid] = CachedFeedBadge(label, android.os.SystemClock.elapsedRealtime() + CACHE_TTL_MS)
            while (cache.size > CACHE_LIMIT) {
                cache.remove(cache.keys.first())
            }
        }
        return label
    }
}

/**
 * 动态与 UP 主页列表卡的恰饭角标状态：未知与真实非恰饭都返回 null（不显示），
 * 状态随 bvid 重置；空降助手禁用或列表标记开关关闭时立即清空。
 */
@Composable
internal fun rememberSponsorFeedBadgeLabel(bvid: String): String? {
    val context = LocalContext.current
    val workAllowed = rememberVisualWorkActive() && LocalPageImageLoadingAllowed.current &&
        LocalListAuxiliaryWorkAllowed.current && LocalHomeCardAuxiliaryWorkAllowed.current
    val badgeGateFlow = remember(context) {
        combine(
            PluginManager.pluginsFlow.map { plugins ->
                plugins.any { it.plugin.id == BuiltinPluginIds.SPONSOR_BLOCK && it.enabled }
            },
            sponsorBlockFeedBadgeEnabledFlow(context),
        ) { pluginEnabled, feedBadgeEnabled -> pluginEnabled && feedBadgeEnabled }
            .distinctUntilChanged()
    }
    return key(bvid) {
        produceState<String?>(initialValue = null, bvid, workAllowed, badgeGateFlow) {
            if (!bvid.startsWith("BV")) return@produceState
            badgeGateFlow.collectLatest { gateEnabled ->
                if (!gateEnabled) {
                    value = null
                } else if (workAllowed) {
                    delay(180L)
                    value = SponsorFeedBadgeStore.badgeLabel(context, bvid)
                }
            }
        }.value
    }
}
