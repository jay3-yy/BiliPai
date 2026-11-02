package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicInteractionModule
import com.android.purebilibili.data.model.response.EmojiInfo
import com.android.purebilibili.data.model.response.RichTextNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppDynamicCommentPreview(
    val dynamicId: String,
    val commentOid: Long,
    val commentType: Int,
    val interaction: DynamicInteractionModule?,
)

/** Official summaries and their click metadata always come from the same DynSpace item. */
object DynamicCommentPreviewApi {
    private const val PATH = "/bilibili.app.dynamic.v2.Dynamic/DynSpace"

    suspend fun loadSpace(hostMid: Long, beforeDynamicId: String = ""): Map<String, AppDynamicCommentPreview> =
        withContext(Dispatchers.IO) {
            parseDynamicCommentPreviewReply(BiliGrpcClient.request(
                PATH,
                buildDynamicCommentPreviewRequest(hostMid, beforeDynamicId),
                profile = BiliGrpcProfile.DYNAMIC_PREVIEW_GUEST,
            ))
        }
}

internal fun buildDynamicCommentPreviewRequest(hostMid: Long, beforeDynamicId: String): ByteArray {
    require(hostMid > 0L)
    return ProtoWire.message(
        ProtoWire.int64(1, hostMid),
        ProtoWire.string(2, beforeDynamicId),
        ProtoWire.int32(4, 8),
        ProtoWire.int32(5, 1),
        ProtoWire.string(6, "space"),
    )
}

internal fun parseDynamicCommentPreviewReply(bytes: ByteArray): Map<String, AppDynamicCommentPreview> {
    val previews = LinkedHashMap<String, AppDynamicCommentPreview>()
    for (field in ProtoWire.parseFields(bytes)) {
        if (field.number == 1 && field.wireType == ProtoWire.WIRE_LENGTH_DELIMITED) {
            parseCommentPreviewCard(field.bytes, previews, 0)
        }
    }
    return previews
}

private fun parseCommentPreviewCard(
    bytes: ByteArray,
    previews: MutableMap<String, AppDynamicCommentPreview>,
    depth: Int,
) {
    // Match DynamicPersonalFeedApi's supported forwarding depth and actual module path.
    check(depth < 16) { "动态转发层级过深" }
    val fields = ProtoWire.parseFields(bytes)
    val extend = fields.previewMessage(4)
    val id = extend.previewText(1)
    if (id.isEmpty() || id.all { it == '0' } || id.any { it !in '0'..'9' }) return
    val reply = extend.previewMessage(18)
    var hasInteraction = false
    val comments = ArrayList<DynamicInteractionItem>()
    for (field in fields) {
        if (field.number != 3 || field.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
        for (module in ProtoWire.parseFields(field.bytes)) {
            if (module.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
            when (module.number) {
                12 -> {
                    hasInteraction = true
                    for (item in ProtoWire.parseFields(module.bytes)) {
                        if (item.number != 1 || item.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
                        parseCommentPreviewInteraction(item.bytes, id)?.let(comments::add)
                    }
                }
                5 -> for (media in ProtoWire.parseFields(module.bytes)) {
                    if (media.number != 6 || media.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
                    for (original in ProtoWire.parseFields(media.bytes)) {
                        if (original.number == 1 && original.wireType == ProtoWire.WIRE_LENGTH_DELIMITED) {
                            parseCommentPreviewCard(original.bytes, previews, depth + 1)
                        }
                    }
                }
            }
        }
    }
    // Even cards with no interaction are hits: callers must not retain old unidentified summaries.
    previews[id] = AppDynamicCommentPreview(
        dynamicId = id,
        commentOid = reply.previewInteger(4).takeIf { it > 0L } ?: 0L,
        commentType = reply.previewInteger(3).takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt() ?: 0,
        interaction = if (hasInteraction) DynamicInteractionModule(comments) else null,
    )
}

private fun parseCommentPreviewInteraction(bytes: ByteArray, cardId: String): DynamicInteractionItem? {
    val fields = ProtoWire.parseFields(bytes)
    // LocalIconType.Comment is proto3 zero; missing icon_type is the same enum value.
    // Like/Avatar/Cover are not Web's type=1 comments.
    if (fields.previewInteger(1) != 0L) return null
    val itemId = fields.previewText(4)
    if (itemId.isNotEmpty() && itemId != cardId) return null
    val nodes = ArrayList<RichTextNode>()
    val params = LinkedHashMap<String, String>()
    for (field in fields) {
        if (field.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
        when (field.number) {
            2 -> nodes += parseCommentPreviewDescription(field.bytes)
            12 -> {
                val entry = ProtoWire.parseFields(field.bytes)
                val key = entry.previewText(1)
                if (key.isNotEmpty()) params[key] = entry.previewText(2)
            }
        }
    }
    return DynamicInteractionItem(
        type = 1,
        desc = DynamicDesc(
            text = buildString { nodes.forEach { append(it.text.ifBlank { it.orig_text }) } },
            rich_text_nodes = nodes,
        ),
        uri = fields.previewText(3),
        extend_click_param = params,
    )
}

private fun parseCommentPreviewDescription(bytes: ByteArray): RichTextNode {
    val fields = ProtoWire.parseFields(bytes)
    val type = fields.previewInteger(2)
    val text = fields.previewText(1)
    val originalText = fields.previewText(10)
    return RichTextNode(
        type = commentPreviewDescriptionType(type),
        text = text,
        orig_text = originalText,
        emoji = if (type == 9L) EmojiInfo(
            icon_url = fields.previewText(6),
            text = originalText.ifBlank { text.ifBlank { fields.previewText(7) } },
        ) else null,
        jump_url = fields.previewText(3).takeIf { it.isNotEmpty() },
        // Description.rid is an opaque rich-text resource ID, never a comment floor ID.
        rid = fields.previewText(8).takeIf { it.isNotEmpty() },
    )
}

// APK 9.17.0 com.bapis.bilibili.app.dynamic.v2.DescType, not generic text-node/emoji enums.
// USER is the comment author; use the existing AT renderer for its URI/rid profile link.
private fun commentPreviewDescriptionType(type: Long): String = when (type) {
    2L, 10L -> "RICH_TEXT_NODE_TYPE_AT"
    3L -> "RICH_TEXT_NODE_TYPE_LOTTERY"
    4L -> "RICH_TEXT_NODE_TYPE_VOTE"
    5L -> "RICH_TEXT_NODE_TYPE_TOPIC"
    6L -> "RICH_TEXT_NODE_TYPE_GOODS"
    7L -> "RICH_TEXT_NODE_TYPE_BV"
    8L -> "RICH_TEXT_NODE_TYPE_AV"
    9L -> "RICH_TEXT_NODE_TYPE_EMOJI"
    11L -> "RICH_TEXT_NODE_TYPE_CV"
    12L -> "RICH_TEXT_NODE_TYPE_VC"
    13L -> "RICH_TEXT_NODE_TYPE_WEB"
    14L -> "RICH_TEXT_NODE_TYPE_TAOBAO"
    15L -> "RICH_TEXT_NODE_TYPE_MAIL"
    16L -> "RICH_TEXT_NODE_TYPE_OGV_SEASON"
    17L -> "RICH_TEXT_NODE_TYPE_OGV_EP"
    18L -> "RICH_TEXT_NODE_TYPE_SEARCH_WORD"
    else -> "RICH_TEXT_NODE_TYPE_TEXT"
}

private fun List<ProtoWire.Field>.previewText(number: Int): String =
    firstOrNull { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }
        ?.let(ProtoWire::stringValue).orEmpty()

private fun List<ProtoWire.Field>.previewInteger(number: Int): Long =
    firstOrNull { it.number == number && it.wireType == ProtoWire.WIRE_VARINT }?.varint ?: 0L

private fun List<ProtoWire.Field>.previewMessage(number: Int): List<ProtoWire.Field> =
    firstOrNull { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }
        ?.let { ProtoWire.parseFields(it.bytes) }.orEmpty()
