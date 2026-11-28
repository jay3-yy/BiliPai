package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.feature.video.viewmodel.*

/** Public counts and confirmed relationship fields survive the detail -> portrait handover. */
internal fun resolvePortraitEngagementSeed(
    bvid: String,
    aid: Long,
    ownerMid: Long,
    info: ViewInfo?,
    fallbackStat: Stat,
    success: VideoPlaybackUiState.Success?,
    knownRoot: VideoEngagementUiState,
): VideoEngagementSeed {
    val matchingSuccess = success?.takeIf { it.info.bvid == bvid && it.info.aid == aid }
    val stat = info?.stat ?: fallbackStat
    var seed = matchingSuccess?.toEngagementSeed() ?: VideoEngagementSeed(
        isLoggedIn = !TokenManager.sessDataCache.isNullOrEmpty(),
        isVip = TokenManager.isVipCache,
        likeCount = stat.like,
        totalCoinCount = stat.coin,
        favoriteCount = stat.favorite,
        isRepost = info?.isRepost ?: false,
    )
    val root = knownRoot.takeIf {
        it.subject?.bvid == bvid && it.subject.aid == aid && matchesVideoEngagementAccount(it.accountSession)
    }
    if (root != null) {
        seed = seed.copy(
            isLiked = if (root.likeStatusKnown) root.isLiked else seed.isLiked,
            likeStatusKnown = root.likeStatusKnown || seed.likeStatusKnown,
            likeCount = if (root.likeStatusKnown) root.likeCount else seed.likeCount,
            isFollowing = if (root.followStatusKnown && root.subject?.ownerMid == ownerMid) root.isFollowing else seed.isFollowing,
            followStatusKnown = seed.followStatusKnown || (root.followStatusKnown && root.subject?.ownerMid == ownerMid),
            relationshipStatusKnown = root.relationshipStatusKnown || seed.relationshipStatusKnown,
            isFavorited = if (root.relationshipStatusKnown) root.isFavorited else seed.isFavorited,
            favoriteCount = if (root.relationshipStatusKnown) root.favoriteCount else seed.favoriteCount,
            isDisliked = if (root.relationshipStatusKnown) root.isDisliked else seed.isDisliked,
            coinCount = if (root.relationshipStatusKnown) root.coinCount else seed.coinCount,
            totalCoinCount = if (root.relationshipStatusKnown) root.totalCoinCount else seed.totalCoinCount,
            followingMids = if (root.followStatusKnown) root.followingMids else seed.followingMids,
        )
    }
    if (!seed.likeStatusKnown) ActionRepository.cachedLikeStatus(aid)?.let {
        seed = seed.copy(isLiked = it, likeStatusKnown = true)
    }
    if (!seed.followStatusKnown && ownerMid > 0L) ActionRepository.cachedFollowStatus(ownerMid)?.let {
        seed = seed.copy(isFollowing = it, followStatusKnown = true)
    }
    return seed
}
