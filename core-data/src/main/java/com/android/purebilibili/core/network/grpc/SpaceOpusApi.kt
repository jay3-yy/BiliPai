package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.data.model.response.SpaceArticleCover
import com.android.purebilibili.data.model.response.SpaceArticleItem

data class SpaceOpusCollection(val id: Long, val title: String, val cover: String, val subtitle: String)
data class SpaceOpusPage(val items: List<SpaceArticleItem>, val next: String, val collections: List<SpaceOpusCollection>)

object SpaceOpusApi {
    fun load(mid: Long, next: String, filter: String): SpaceOpusPage = parseSpaceOpusReply(
        BiliGrpcClient.request("/bilibili.app.dynamic.v2.Opus/OpusSpaceFlow", buildSpaceOpusRequest(mid, next, filter)),
    )
}

internal fun buildSpaceOpusRequest(mid: Long, next: String, filter: String): ByteArray = ProtoWire.message(
    ProtoWire.int64(1, mid),
    ProtoWire.bytes(3, ProtoWire.message(ProtoWire.int32(1, 20), ProtoWire.string(2, next))),
    ProtoWire.string(4, filter),
)

internal fun parseSpaceOpusReply(bytes: ByteArray): SpaceOpusPage {
    val fields = ProtoWire.parseFields(bytes)
    val items = fields.messages(1).mapNotNull { item ->
        val content = item.message(4)
        if (content.isEmpty()) return@mapNotNull null
        val id = item.number(2)
        val paragraphText = content.message(5).message(3).messages(1).joinToString("") { node ->
            node.text(2).ifBlank { node.message(3).text(1) }
        }
        val description = item.message(3).messages(6).joinToString("") { it.text(1).ifBlank { it.text(10) } }
        val statistics = listOf(content.message(3), content.message(4)).mapNotNull statistic@ { stat ->
            val text = stat.text(2)
            if (text.isBlank()) return@statistic null
            text + when (stat.number(1)) {
                1L -> "播放"
                2L -> "弹幕"
                5L -> "阅读"
                6L -> "点赞"
                7L -> "评论"
                8L -> "收藏"
                9L -> "硬币"
                else -> ""
            }
        }
        val picture = content.message(1)
        val cover = picture.cover()
        val darkCover = content.message(7).cover() ?: picture.text(6).takeIf { it.isNotBlank() }
            ?.let { SpaceArticleCover(it, picture.number(2).toInt(), picture.number(3).toInt()) }
        SpaceArticleItem(id = id, title = paragraphText.ifBlank { description }, cover = cover,
            darkCover = darkCover, displayStats = statistics, jump_url = "https://www.bilibili.com/opus/$id")
    }
    val collections = fields.message(3).messages(2).map { entry ->
        val info = entry.message(1)
        SpaceOpusCollection(
            id = info.number(1),
            title = info.message(2).messages(1).joinToString("") { it.text(1) },
            cover = entry.text(2), subtitle = entry.text(4),
        )
    }
    return SpaceOpusPage(items, fields.message(2).text(1), collections)
}

private fun List<ProtoWire.Field>.number(number: Int): Long = firstOrNull { it.number == number }?.varint ?: 0L
private fun List<ProtoWire.Field>.text(number: Int): String =
    firstOrNull { it.number == number }?.let(ProtoWire::stringValue).orEmpty()
private fun List<ProtoWire.Field>.message(number: Int): List<ProtoWire.Field> =
    firstOrNull { it.number == number }?.let { ProtoWire.parseFields(it.bytes) }.orEmpty()
private fun List<ProtoWire.Field>.messages(number: Int): List<List<ProtoWire.Field>> =
    filter { it.number == number }.map { ProtoWire.parseFields(it.bytes) }
private fun List<ProtoWire.Field>.cover(): SpaceArticleCover? = text(1).takeIf { it.isNotBlank() }
    ?.let { SpaceArticleCover(it, number(2).toInt(), number(3).toInt()) }
