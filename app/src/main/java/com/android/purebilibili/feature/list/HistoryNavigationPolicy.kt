package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.search.ArticleNavigationTarget
import com.android.purebilibili.feature.search.resolveArticleNavigationTarget
import com.android.purebilibili.navigation3.BiliPaiNavKey

internal data class HistoryCardPresentation(
    val videoItem: VideoItem,
    val showUpBadge: Boolean?
)

internal enum class HistoryNavigationKind {
    VIDEO,
    PGC,
    CHEESE,
    LIVE,
    ARTICLE
}

internal fun resolveHistoryNavigationKind(
    historyItem: HistoryItem?
): HistoryNavigationKind {
    return when (historyItem?.business) {
        HistoryBusiness.PGC -> HistoryNavigationKind.PGC
        HistoryBusiness.CHEESE -> HistoryNavigationKind.CHEESE
        HistoryBusiness.LIVE -> HistoryNavigationKind.LIVE
        HistoryBusiness.ARTICLE -> HistoryNavigationKind.ARTICLE
        else -> HistoryNavigationKind.VIDEO
    }
}

internal fun resolveHistoryCardPresentation(
    historyItem: HistoryItem?
): HistoryCardPresentation? {
    historyItem ?: return null
    return when (historyItem.business) {
        HistoryBusiness.PGC -> {
            val video = historyItem.videoItem
            HistoryCardPresentation(
                videoItem = if (video.owner.name.isBlank()) {
                    video.copy(owner = video.owner.copy(name = "番剧"))
                } else {
                    video
                },
                showUpBadge = false
            )
        }
        else -> HistoryCardPresentation(
            videoItem = historyItem.videoItem,
            showUpBadge = null
        )
    }
}

/** 非普通视频使用整卡容器，目标与历史记录原有业务分发保持一致。 */
internal fun resolveHistoryCardContainerTarget(
    item: HistoryItem,
): BiliPaiNavKey? {
    val video = item.videoItem
    return when (item.business) {
        HistoryBusiness.PGC -> when {
            // 历史记录的 oid 可能是视频 aid，有分集 ID 时用它查询番剧。
            item.epid > 0L -> BiliPaiNavKey.BangumiPlayer(seasonId = 0L, epId = item.epid)
            item.seasonId > 0L -> BiliPaiNavKey.BangumiDetail(seasonId = item.seasonId)
            else -> null
        }
        HistoryBusiness.CHEESE -> if (item.seasonId > 0L || item.epid > 0L) {
            BiliPaiNavKey.BangumiPlayer(
                seasonId = item.seasonId,
                epId = item.epid,
                resumePositionMs = resolveHistoryResumePositionMs(item),
                isCourse = true,
            )
        } else null
        HistoryBusiness.LIVE -> item.roomId.takeIf { it > 0L }?.let { roomId ->
            BiliPaiNavKey.Live(
                roomId = roomId.toString(), title = video.title, uname = video.owner.name,
            )
        }
        HistoryBusiness.ARTICLE -> when (val target = resolveArticleNavigationTarget(video.id)) {
            is ArticleNavigationTarget.NativeArticle ->
                BiliPaiNavKey.ArticleDetail(target.articleId, video.title)
            is ArticleNavigationTarget.NativeDynamic ->
                BiliPaiNavKey.DynamicDetail(target.dynamicId)
            null -> null
        }
        // 普通视频保留卡片快照、播放位置与竖屏意图的统一视频导航入口。
        HistoryBusiness.ARCHIVE, HistoryBusiness.UNKNOWN -> null
    }
}
