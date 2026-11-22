package com.android.purebilibili.feature.share

import androidx.compose.runtime.compositionLocalOf
import com.android.purebilibili.data.model.response.DynamicItem
import java.net.URLEncoder

/** 评论所在内容的身份；列表与楼中楼沿用同一份来源数据。 */
internal data class ReplyShareContext(
    val url: String,
    val title: String = "",
    val cover: String = "",
    val page: Int = 1,
)
internal val LocalReplyShareContext = compositionLocalOf<ReplyShareContext?> { null }

internal fun videoReplyShareContext(
    bvid: String,
    title: String = "",
    cover: String = "",
    page: Int = 1,
): ReplyShareContext? {
    val trimmed = bvid.trim()
    if (trimmed.isEmpty()) return null
    return ReplyShareContext(
        url = "https://www.bilibili.com/video/$trimmed",
        title = title,
        cover = cover,
        page = page.coerceAtLeast(1),
    )
}

internal fun bangumiReplyShareContext(
    episodeId: Long,
    title: String = "",
    cover: String = "",
): ReplyShareContext? {
    if (episodeId <= 0L) return null
    return ReplyShareContext(
        url = "https://www.bilibili.com/bangumi/play/ep$episodeId",
        title = title,
        cover = cover,
    )
}

internal fun dynamicReplyShareContext(
    dynamicId: String,
    title: String = "动态",
    cover: String = "",
): ReplyShareContext? {
    val trimmed = dynamicId.trim()
    if (trimmed.isEmpty()) return null
    return ReplyShareContext(
        url = "https://t.bilibili.com/$trimmed",
        title = title,
        cover = cover,
    )
}

internal fun dynamicReplyShareContext(
    dynamicId: String,
    item: DynamicItem?,
): ReplyShareContext? {
    val major = item?.modules?.module_dynamic?.major
    val title = listOfNotNull(
        major?.opus?.title?.takeIf { it.isNotBlank() },
        major?.archive?.title?.takeIf { it.isNotBlank() },
        major?.pgc?.title?.takeIf { it.isNotBlank() },
        major?.article?.title?.takeIf { it.isNotBlank() },
        item?.modules?.module_author?.name?.takeIf { it.isNotBlank() },
    ).firstOrNull() ?: "动态"
    val cover = listOfNotNull(
        major?.opus?.pics?.firstOrNull()?.url?.takeIf { it.isNotBlank() },
        major?.draw?.items?.firstOrNull()?.src?.takeIf { it.isNotBlank() },
        major?.archive?.cover?.takeIf { it.isNotBlank() },
        major?.pgc?.cover?.takeIf { it.isNotBlank() },
        major?.article?.covers?.firstOrNull()?.takeIf { it.isNotBlank() },
    ).firstOrNull().orEmpty()
    return dynamicReplyShareContext(dynamicId = dynamicId, title = title, cover = cover)
}

internal fun articleReplyShareContext(
    articleId: Long,
    title: String = "",
    cover: String = "",
): ReplyShareContext? {
    if (articleId <= 0L) return null
    return ReplyShareContext(
        url = "https://www.bilibili.com/read/cv$articleId",
        title = title,
        cover = cover,
    )
}

internal fun bgmReplyShareContext(
    musicId: String,
    title: String = "",
    cover: String = "",
): ReplyShareContext? {
    val trimmed = musicId.trim()
    if (trimmed.isEmpty()) return null
    return ReplyShareContext(
        url = "https://music.bilibili.com/h5/music-detail?music_id=${URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")}",
        title = title,
        cover = cover,
    )
}

internal fun matchReplyShareContext(
    cid: Long,
    title: String = "",
    cover: String = "",
): ReplyShareContext? {
    if (cid <= 0L) return null
    return ReplyShareContext(
        url = "https://www.bilibili.com/h5/match/data/detail/$cid",
        title = title,
        cover = cover,
    )
}

internal fun commentDetailReplyShareContext(
    oid: Long,
    type: Int,
    enterUri: String = "",
): ReplyShareContext? {
    val resolvedUrl = enterUri.trim().ifBlank {
        when {
            oid <= 0L -> ""
            type == 1 -> "https://www.bilibili.com/video/av$oid"
            type == 11 || type == 17 -> "bilibili://following/detail/$oid"
            type == 12 -> "https://www.bilibili.com/read/cv$oid"
            type == 14 -> "https://www.bilibili.com/audio/au$oid"
            type == 27 -> "https://www.bilibili.com/h5/match/data/detail/$oid"
            else -> ""
        }
    }
    if (resolvedUrl.isBlank() && oid <= 0L) return null
    return ReplyShareContext(
        url = resolvedUrl,
        title = "",
    )
}
