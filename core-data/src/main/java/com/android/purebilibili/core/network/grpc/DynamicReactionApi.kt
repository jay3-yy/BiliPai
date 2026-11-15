package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.data.model.response.DynamicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DynamicReactionUser(val mid: Long, val name: String, val face: String, val action: String)
data class DynamicReactionPage(val title: String, val users: List<DynamicReactionUser>, val offset: String, val hasMore: Boolean)

/** Field numbers match PiliPlus's bilibili.app.dynamic.v2 ReactionList models. */
object DynamicReactionApi {
    suspend fun load(item: DynamicItem, offset: String = ""): DynamicReactionPage = withContext(Dispatchers.IO) {
        // ReactionList uses Extend.dyn_type (source type bit values), not
        // DynamicType's display enum (where av=2, word=6, draw=7).
        val type = item.type.toLongOrNull() ?: when (item.type) {
            "DYNAMIC_TYPE_FORWARD" -> 1L
            "DYNAMIC_TYPE_DRAW" -> 2L
            "DYNAMIC_TYPE_WORD" -> 4L
            "DYNAMIC_TYPE_AV" -> 8L
            "DYNAMIC_TYPE_ARTICLE" -> 64L
            "DYNAMIC_TYPE_MUSIC" -> 256L
            "DYNAMIC_TYPE_PGC" -> 512L
            "DYNAMIC_TYPE_COMMON_SQUARE" -> 2048L
            "DYNAMIC_TYPE_COMMON_VERTICAL" -> 2049L
            "DYNAMIC_TYPE_LIVE" -> 4200L
            "DYNAMIC_TYPE_LIVE_RCMD" -> 4308L
            else -> error("暂不支持此动态类型：${item.type}")
        }
        val request = ProtoWire.message(
            ProtoWire.int64(1, item.id_str.toLong()),
            ProtoWire.int64(2, type),
            ProtoWire.int64(3, item.basic?.rid_str?.toLongOrNull() ?: 0L),
            ProtoWire.string(4, offset),
        )
        val fields = ProtoWire.parseFields(BiliGrpcClient.requestCancellable("/bilibili.app.dynamic.v2.Dynamic/ReactionList", request))
        fun text(number: Int) = fields.firstOrNull { it.number == number }?.let(ProtoWire::stringValue).orEmpty()
        val users = fields.filter { it.number == 2 }.mapNotNull { field ->
            val reaction = ProtoWire.parseFields(field.bytes)
            val user = reaction.firstOrNull { it.number == 1 }?.let { ProtoWire.parseFields(it.bytes) } ?: return@mapNotNull null
            val mid = user.firstOrNull { it.number == 1 }?.varint ?: 0L
            if (mid <= 0L) return@mapNotNull null
            DynamicReactionUser(
                mid,
                user.firstOrNull { it.number == 2 }?.let(ProtoWire::stringValue).orEmpty(),
                user.firstOrNull { it.number == 3 }?.let(ProtoWire::stringValue).orEmpty(),
                reaction.firstOrNull { it.number == 3 }?.let(ProtoWire::stringValue).orEmpty(),
            )
        }
        DynamicReactionPage(text(1), users, text(3), fields.firstOrNull { it.number == 4 }?.varint == 1L)
    }
}
