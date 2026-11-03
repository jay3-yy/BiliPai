package com.android.purebilibili.feature.bangumi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.navigation3.BiliPaiNavKey

internal data class BangumiCardTransition(val modifier: Modifier, val onClick: () -> Unit, val enabled: Boolean)

@Composable
internal fun rememberBangumiCardTransition(seasonId: Long, epId: Long = 0L, onClick: () -> Unit): BangumiCardTransition {
    val transition = com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(
        BiliPaiNavKey.BangumiPlayer(seasonId = seasonId, epId = epId), onClick,
    )
    return BangumiCardTransition(transition.modifier, transition.onClick, transition.enabled)
}

/** 剧集封面打开播放页，沿用视频卡片的来源快照、景深与返回落点。 */
@Composable
internal fun rememberBangumiEpisodeCardTransition(
    seasonId: Long,
    episode: BangumiEpisode,
    onClick: () -> Unit,
): BangumiCardTransition {
    val target = BiliPaiNavKey.BangumiPlayer(
        seasonId = seasonId,
        epId = episode.id,
        preferredAid = episode.aid,
        isCourse = episode.from == "pugv" || episode.playable || episode.episodeCanView,
    )
    val transition = com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(target, onClick)
    return BangumiCardTransition(transition.modifier, transition.onClick, transition.enabled)
}

/** 详情由导航容器承接，频道自身保留列表、选集与播放回调。 */
@Composable
internal fun BangumiCardTransitionHost(
    onBangumiClick: (Long) -> Unit,
    onBangumiEpisodeClick: (Long, Long) -> Unit,
    onPlayEpisode: ((Long, BangumiEpisode) -> Unit)?,
    onReviewsClick: (Long, String) -> Unit,
    onUserClick: (Long) -> Unit,
    content: @Composable ((Long) -> Unit, (Long, Long) -> Unit, Boolean) -> Unit,
) {
    content(onBangumiClick, onBangumiEpisodeClick, true)
}
