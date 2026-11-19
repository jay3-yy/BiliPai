package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.navigation3.BiliPaiNavKey

internal sealed interface MessageLinkNavigationAction {
    data class Video(val videoId: String) : MessageLinkNavigationAction
    data class VideoComment(
        val videoId: String,
        val rootReplyId: Long,
        val targetReplyId: Long = 0L,
        val cid: Long = 0L,
        val startPositionMs: Long? = null,
    ) : MessageLinkNavigationAction
    data class Dynamic(val dynamicId: String) : MessageLinkNavigationAction
    data class DynamicComment(
        val dynamicId: String,
        val rootReplyId: Long,
        val targetReplyId: Long = 0L,
        val openComments: Boolean = false,
    ) : MessageLinkNavigationAction
    data class CommentDetail(
        val oid: Long,
        val rootReplyId: Long,
        val targetReplyId: Long = 0L,
        val businessId: Int = 1,
        val enterUri: String = ""
    ) : MessageLinkNavigationAction
    data class Space(val mid: Long) : MessageLinkNavigationAction
    data class Live(val roomId: Long) : MessageLinkNavigationAction
    data class BangumiSeason(val seasonId: Long, val mediaId: Long = 0L) : MessageLinkNavigationAction
    data class BangumiEpisode(val epId: Long) : MessageLinkNavigationAction
    data class Music(val musicId: String) : MessageLinkNavigationAction
    data class Article(
        val articleId: Long,
        val rootReplyId: Long = 0L,
        val targetReplyId: Long = 0L,
    ) : MessageLinkNavigationAction
    data class ArticleCollection(val collectionId: Long) : MessageLinkNavigationAction
    data class Destination(val key: BiliPaiNavKey) : MessageLinkNavigationAction
    data class Web(val url: String) : MessageLinkNavigationAction
}

internal fun resolveMessageLinkNavigationAction(rawLink: String): MessageLinkNavigationAction {
    return when (val target = BilibiliNavigationTargetParser.parse(rawLink)) {
        is BilibiliNavigationTarget.CommentDetail -> MessageLinkNavigationAction.CommentDetail(
            oid = target.oid,
            rootReplyId = target.rootReplyId,
            targetReplyId = target.targetReplyId,
            businessId = target.businessId,
            enterUri = target.enterUri,
        )
        is BilibiliNavigationTarget.Video -> {
            val root = target.commentRootRpid
            val reply = target.commentTargetRpid
            val aid = target.videoId.removePrefix("av").removePrefix("AV").toLongOrNull()
            when {
                target.hasPlaybackLocation -> MessageLinkNavigationAction.VideoComment(
                    target.videoId, root, reply, target.cid, target.startPositionMs,
                )
                root > 0L && aid != null && aid > 0L -> MessageLinkNavigationAction.CommentDetail(
                    oid = aid,
                    rootReplyId = root,
                    targetReplyId = reply,
                    businessId = 1,
                    enterUri = "bilibili://video/$aid",
                )
                root > 0L || reply > 0L -> MessageLinkNavigationAction.VideoComment(target.videoId, root, reply)
                else -> MessageLinkNavigationAction.Video(target.videoId)
            }
        }
        is BilibiliNavigationTarget.Dynamic -> {
            val root = target.commentRootRpid
            val reply = target.commentTargetRpid
            val oid = target.dynamicId.toLongOrNull()
            when {
                target.openComments -> MessageLinkNavigationAction.DynamicComment(
                    target.dynamicId, root, reply, openComments = true,
                )
                root > 0L && oid != null && oid > 0L -> MessageLinkNavigationAction.CommentDetail(
                    oid = oid,
                    rootReplyId = root,
                    targetReplyId = reply,
                    businessId = 17,
                    enterUri = "bilibili://following/detail/$oid",
                )
                root > 0L || reply > 0L -> MessageLinkNavigationAction.DynamicComment(target.dynamicId, root, reply)
                else -> MessageLinkNavigationAction.Dynamic(target.dynamicId)
            }
        }
        is BilibiliNavigationTarget.Space -> MessageLinkNavigationAction.Space(target.mid)
        is BilibiliNavigationTarget.Search -> MessageLinkNavigationAction.Destination(BiliPaiNavKey.Search(target.keyword))
        is BilibiliNavigationTarget.Topic -> MessageLinkNavigationAction.Destination(BiliPaiNavKey.TopicDetail(target.topicId))
        is BilibiliNavigationTarget.FavoriteFolder -> MessageLinkNavigationAction.Destination(
            BiliPaiNavKey.SeasonSeriesDetail("favorite", target.mediaId, target.ownerMid)
        )
        is BilibiliNavigationTarget.Live -> MessageLinkNavigationAction.Live(target.roomId)
        is BilibiliNavigationTarget.BangumiSeason -> MessageLinkNavigationAction.BangumiSeason(
            seasonId = target.seasonId,
            mediaId = target.mediaId,
        )
        is BilibiliNavigationTarget.BangumiEpisode -> MessageLinkNavigationAction.BangumiEpisode(target.epId)
        is BilibiliNavigationTarget.Music -> MessageLinkNavigationAction.Music(target.musicId)
        is BilibiliNavigationTarget.ArticleCollection -> MessageLinkNavigationAction.ArticleCollection(target.collectionId)
        is BilibiliNavigationTarget.Article -> MessageLinkNavigationAction.Article(
            target.articleId, target.commentRootRpid, target.commentTargetRpid,
        )
        else -> {
            BilibiliNavigationTargetParser.unwrapBrowserUrl(rawLink)?.let {
                return resolveMessageLinkNavigationAction(it)
            }
            val trimmed = rawLink.trim()
            MessageLinkNavigationAction.Web(
                trimmed.takeIf {
                    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
                }.orEmpty()
            )
        }
    }
}
