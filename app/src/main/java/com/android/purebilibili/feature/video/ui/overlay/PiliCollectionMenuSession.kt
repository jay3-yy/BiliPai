package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.util.HapticType

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.player.PiliPlayerMenuStore
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.feature.video.playback.next.PiliPlaybackOrder
import com.android.purebilibili.feature.video.ui.components.CollectionSortMode
import com.android.purebilibili.feature.video.ui.components.resolveCollectionSubscriptionId
import com.android.purebilibili.feature.video.ui.components.resolveCurrentUgcEpisodeAid
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PiliCollectionMenuSession(
    val isSubscribed: Boolean?,
    val onSubscriptionChange: ((Boolean) -> Unit)?,
    val isReversed: Boolean,
    val onReverse: (() -> Unit)?,
)

@Composable
fun rememberPiliCollectionMenuSession(
    ugcSeason: UgcSeason?, bvid: String, cid: Long,
    loadSubscription: Boolean = false, isParts: Boolean = false
): PiliCollectionMenuSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subscriptionHaptic = com.android.purebilibili.core.util.rememberVisibleHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    val seasonId = remember(ugcSeason) { ugcSeason?.let(::resolveCollectionSubscriptionId) ?: 0L }
    val orderSeasonId = if (isParts) 0L else seasonId
    val canLoadSubscription = loadSubscription && seasonId > 0 && bvid.isNotBlank()
    val aid = if (canLoadSubscription) {
        remember(ugcSeason, bvid, cid) {
            resolveCurrentUgcEpisodeAid(ugcSeason?.sections?.flatMap { it.episodes }.orEmpty(), bvid, cid)
        }
    } else 0L
    val cachedSubscribed = if (canLoadSubscription) {
        remember(context, seasonId) { SettingsManager.isCollectionSubscribed(context, seasonId) }
            .collectAsStateWithLifecycle(initialValue = false).value
    } else false
    var remoteSubscribed by remember(seasonId, bvid, cid) { mutableStateOf<Boolean?>(null) }
    var updating by remember(seasonId) { mutableStateOf(false) }
    val storedReversed by remember(context, orderSeasonId, bvid) {
        if (orderSeasonId > 0) SettingsManager.getCollectionSortMode(context, orderSeasonId).map { it == CollectionSortMode.DESCENDING }
        else PiliPlayerMenuStore.reversedPages(context, bvid)
    }.collectAsStateWithLifecycle(initialValue = false)
    var localReversed by remember(orderSeasonId, bvid) { mutableStateOf<Boolean?>(null) }
    val reversed = localReversed ?: storedReversed
    SideEffect { PiliPlaybackOrder.setReversed(orderSeasonId, bvid, reversed) }
    LaunchedEffect(storedReversed) { localReversed = null }
    LaunchedEffect(seasonId, bvid, aid, canLoadSubscription) {
        if (canLoadSubscription) {
            ActionRepository.checkCollectionSubscriptionStatus(bvid = bvid, aid = aid).onSuccess {
                remoteSubscribed = it
                SettingsManager.setCollectionSubscription(context, seasonId, it)
            }
        }
    }
    return PiliCollectionMenuSession(
        isSubscribed = if (seasonId > 0) remoteSubscribed ?: cachedSubscribed.takeIf { it } else null,
        onSubscriptionChange = if (canLoadSubscription && !updating) ({ target ->
            scope.launch {
                updating = true
                try {
                    ActionRepository.setCollectionSubscription(seasonId = seasonId, subscribe = target)
                        .onSuccess {
                            if (it != (remoteSubscribed ?: cachedSubscribed)) subscriptionHaptic(HapticType.LIGHT)
                            remoteSubscribed = it
                            SettingsManager.setCollectionSubscription(context, seasonId, it)
                            Toast.makeText(context, if (it) "已订阅合集" else "已取消订阅合集", Toast.LENGTH_SHORT).show()
                        }.onFailure { Toast.makeText(context, it.message ?: "合集订阅操作失败", Toast.LENGTH_LONG).show() }
                } finally { updating = false }
            }
        }) else null,
        isReversed = reversed,
        onReverse = if (bvid.isNotBlank()) ({
            val target = !reversed
            localReversed = target
            PiliPlaybackOrder.setReversed(orderSeasonId, bvid, target)
            scope.launch {
                if (orderSeasonId > 0) SettingsManager.setCollectionSortMode(context, orderSeasonId,
                    if (target) CollectionSortMode.DESCENDING else CollectionSortMode.ASCENDING)
                else PiliPlayerMenuStore.setReversedPages(context, bvid, target)
            }
        }) else null,
    )
}
