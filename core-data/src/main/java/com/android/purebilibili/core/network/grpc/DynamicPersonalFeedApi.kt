package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.ArticleMajor
import com.android.purebilibili.data.model.response.CommonMajor
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.DynamicTagModule
import com.android.purebilibili.data.model.response.LiveMajor
import com.android.purebilibili.data.model.response.LiveRcmdMajor
import com.android.purebilibili.data.model.response.MedialistMajor
import com.android.purebilibili.data.model.response.MusicMajor
import com.android.purebilibili.data.model.response.OpusMajor
import com.android.purebilibili.data.model.response.OpusSummary
import com.android.purebilibili.data.model.response.SubscriptionNewMajor
import com.android.purebilibili.data.model.response.UgcSeasonMajor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Notification metadata only; opening a card loads its full detail through the existing UI route. */
data class DynamicPersonalFeedPage(
    val items: List<DynamicItem> = emptyList(),
    val offset: String = "",
    val hasMore: Boolean = false,
)

object DynamicPersonalFeedApi {
    private const val PATH = "/bilibili.app.dynamic.v2.Dynamic/DynAllPersonal"

    suspend fun load(hostUid: Long, offset: String = "", page: Int = 1): DynamicPersonalFeedPage =
        withContext(Dispatchers.IO) {
            check(!TokenManager.accessTokenCache.isNullOrBlank()) { "动态预加载需要 App 登录授权，网页 Cookie 不能替代" }
            parseDynamicPersonalFeedReply(BiliGrpcClient.request(PATH, buildDynamicPersonalFeedRequest(hostUid, offset, page)))
        }
}

internal fun buildDynamicPersonalFeedRequest(hostUid: Long, offset: String, page: Int): ByteArray {
    require(hostUid > 0L && page > 0)
    return ProtoWire.message(
        ProtoWire.int64(1, hostUid),
        ProtoWire.string(2, offset),
        ProtoWire.int32(3, page),
        // Ordinary proto3 int32 defaults to zero: never omit this field, even on later pages.
        ProtoWire.int32(4, 1),
        ProtoWire.int32(6, 8),
    )
}

internal fun parseDynamicPersonalFeedReply(bytes: ByteArray): DynamicPersonalFeedPage {
    val items = ArrayList<DynamicItem>()
    var offset = ""
    var hasMore = false
    for (field in ProtoWire.parseFields(bytes)) {
        when (field.number) {
            1 -> if (field.wireType == ProtoWire.WIRE_LENGTH_DELIMITED) {
                parsePersonalDynamicItem(field.bytes)?.let(items::add)
            }
            2 -> offset = ProtoWire.stringValue(field)
            3 -> hasMore = field.varint != 0L
            // Field 4 is the token submitted by DynAllUpdOffset, not a committed read watermark.
            // Background polling must neither submit it nor use it to suppress or clear notices.
        }
    }
    check(!hasMore || offset.isNotBlank()) { "动态预加载分页游标为空" }
    return DynamicPersonalFeedPage(items, offset, hasMore)
}

private fun parsePersonalDynamicItem(bytes: ByteArray, depth: Int = 0): DynamicItem? {
    check(depth < 16) { "动态转发层级过深" }
    val fields = ProtoWire.parseFields(bytes)
    val extend = fields.message(4)
    val id = extend.text(1)
    if (id.isEmpty() || id.all { it == '0' } || id.any { it !in '0'..'9' }) return null
    val cardType = fields.integer(1).toInt()
    var author: DynamicAuthorModule? = null
    var desc: DynamicDesc? = null
    var major: DynamicMajor? = null
    var original: DynamicItem? = null
    var pinned = false
    var visible = cardType != 0 && cardType != 5 && cardType != 15 && cardType != 19
    for (field in fields) {
        if (field.number != 3 || field.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
        val module = ProtoWire.parseFields(field.bytes)
        for (part in module) {
            when (part.number) {
                2 -> {
                    val data = ProtoWire.parseFields(part.bytes)
                    val user = data.message(3)
                    author = DynamicAuthorModule(
                        mid = data.integer(1).takeIf { it > 0L } ?: user.integer(1),
                        name = user.text(2), face = user.text(3), pub_time = data.text(2),
                        // attend=0 is an incidental default outside the likes list, not "unfollowed".
                    )
                    pinned = pinned || data.integer(13) != 0L
                }
                4 -> {
                    val data = ProtoWire.parseFields(part.bytes)
                    desc = DynamicDesc(data.text(3).ifBlank { data.descriptions(1) })
                }
                5 -> {
                    val media = ProtoWire.parseFields(part.bytes)
                    for (entry in media) {
                        if (entry.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
                        val data = when (entry.number) {
                            2, 3, 6, 8, 9, 10, 11, 12, 15, 16, 17 -> ProtoWire.parseFields(entry.bytes)
                            else -> continue
                        }
                        when (entry.number) {
                            2 -> {
                                val archive = ArchiveMajor(
                                    aid = data.integer(7).takeIf { it > 0L }?.toString().orEmpty(),
                                    bvid = data.text(26), title = data.text(1), cover = data.text(2),
                                    jump_url = data.text(21).ifBlank { data.text(3) },
                                    epid = data.integer(16), season_id = data.integer(18),
                                )
                                major = if (data.integer(14) != 0L) {
                                    DynamicMajor(type = "MAJOR_TYPE_PGC", pgc = archive)
                                } else DynamicMajor(type = "MAJOR_TYPE_ARCHIVE", archive = archive)
                            }
                            3 -> major = DynamicMajor(type = "MAJOR_TYPE_PGC", pgc = ArchiveMajor(
                                aid = data.integer(10).takeIf { it > 0L }?.toString().orEmpty(),
                                title = data.text(1), cover = data.text(2),
                                jump_url = data.text(20).ifBlank { data.text(3) },
                                epid = data.integer(9), season_id = data.integer(8),
                            ))
                            6 -> data.firstOrNull { it.number == 1 }?.let {
                                original = parsePersonalDynamicItem(it.bytes, depth + 1)
                            }
                            8 -> major = DynamicMajor(type = "MAJOR_TYPE_ARTICLE", article = ArticleMajor(
                                id = data.integer(1), jump_url = data.text(2), title = data.text(3), desc = data.text(4),
                            ))
                            9 -> major = DynamicMajor(type = "MAJOR_TYPE_MUSIC", music = MusicMajor(
                                id = data.integer(1).toString(), jump_url = data.text(2), title = data.text(4), cover = data.text(5),
                            ))
                            10 -> major = DynamicMajor(type = "MAJOR_TYPE_COMMON", common = CommonMajor(
                                id = data.integer(1).toString(), jump_url = data.text(2), title = data.text(3),
                                desc = data.text(4), cover = data.text(5),
                            ))
                            11 -> major = DynamicMajor(type = "MAJOR_TYPE_LIVE", live = LiveMajor(
                                id = data.integer(1).toString(), jump_url = data.text(2), title = data.text(3),
                                cover = data.text(4), live_state = data.integer(7).toInt(),
                            ))
                            12 -> major = DynamicMajor(type = "MAJOR_TYPE_MEDIALIST", medialist = MedialistMajor(
                                id = data.integer(1).toString(), jump_url = data.text(2), title = data.text(3),
                                sub_title = data.text(4), cover = data.text(5),
                            ))
                            15 -> major = DynamicMajor(type = "MAJOR_TYPE_LIVE_RCMD", live_rcmd = LiveRcmdMajor(data.text(1)))
                            16 -> major = DynamicMajor(type = "MAJOR_TYPE_UGC_SEASON", ugc_season = UgcSeasonMajor(
                                aid = data.integer(11), id = data.integer(7), title = data.text(1), cover = data.text(2),
                                jump_url = data.text(15).ifBlank { data.text(3) },
                            ))
                            17 -> data.firstOrNull { it.number == 3 }?.let {
                                major = DynamicMajor(type = "MAJOR_TYPE_SUBSCRIPTION_NEW", subscription_new =
                                    SubscriptionNewMajor(LiveRcmdMajor(ProtoWire.parseFields(it.bytes).text(1))))
                            }
                        }
                    }
                }
                13 -> if (author == null) {
                    val data = ProtoWire.parseFields(part.bytes)
                    author = DynamicAuthorModule(mid = data.integer(3), name = extend.text(4),
                        face = data.text(6), pub_time = data.text(4))
                }
                14, 15, 16, 33 -> visible = false
                25 -> pinned = pinned || ProtoWire.parseFields(part.bytes).text(1) == "置顶"
                30 -> {
                    val data = ProtoWire.parseFields(part.bytes)
                    val title = data.firstOrNull { it.number == 1 }?.let { paragraphText(it.bytes) }.orEmpty()
                    val summary = data.firstOrNull { it.number == 2 }?.let { paragraphText(it.bytes) }.orEmpty()
                    major = DynamicMajor(type = "MAJOR_TYPE_OPUS", opus = OpusMajor(title = title.takeIf { it.isNotBlank() },
                        summary = OpusSummary(summary)))
                    if (desc?.text.isNullOrBlank() && summary.isNotBlank()) desc = DynamicDesc(summary)
                }
            }
        }
    }
    if (author == null && extend.integer(14) > 0L) author = DynamicAuthorModule(mid = extend.integer(14))
    if (desc?.text.isNullOrBlank()) extend.descriptions(7).takeIf { it.isNotBlank() }?.let { desc = DynamicDesc(it) }
    val originalId = extend.text(3)
    if (original == null && originalId.isNotEmpty() && originalId.any { it != '0' } && originalId.all { it in '0'..'9' }) {
        // Folded/deleted sources can expose only their real ID and Extend metadata.
        original = DynamicItem(id_str = originalId, type = personalDynamicType(extend.integer(8).toInt()),
            modules = DynamicModules(module_dynamic = DynamicContentModule(desc = DynamicDesc(extend.descriptions(6)))))
    }
    return DynamicItem(
        id_str = id, type = personalDynamicType(cardType), visible = visible,
        modules = DynamicModules(module_author = author, module_dynamic = DynamicContentModule(desc = desc, major = major),
            module_tag = if (pinned) DynamicTagModule("置顶") else null),
        orig = original,
    )
}

private fun paragraphText(bytes: ByteArray): String {
    val text = ProtoWire.parseFields(bytes).message(3)
    return buildString {
        for (field in text) {
            if (field.number != 1 || field.wireType != ProtoWire.WIRE_LENGTH_DELIMITED) continue
            val node = ProtoWire.parseFields(field.bytes)
            append(node.text(2).ifBlank { node.message(3).text(1) })
        }
    }
}

private fun List<ProtoWire.Field>.text(number: Int): String =
    firstOrNull { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }?.let(ProtoWire::stringValue).orEmpty()

private fun List<ProtoWire.Field>.integer(number: Int): Long =
    firstOrNull { it.number == number && it.wireType == ProtoWire.WIRE_VARINT }?.varint ?: 0L

private fun List<ProtoWire.Field>.message(number: Int): List<ProtoWire.Field> =
    firstOrNull { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }?.let { ProtoWire.parseFields(it.bytes) }.orEmpty()

private fun List<ProtoWire.Field>.descriptions(number: Int): String = buildString {
    for (field in this@descriptions) {
        if (field.number == number && field.wireType == ProtoWire.WIRE_LENGTH_DELIMITED) {
            val description = ProtoWire.parseFields(field.bytes)
            append(description.text(1).ifBlank { description.text(10) })
        }
    }
}

private fun personalDynamicType(type: Int): String = when (type) {
    1 -> "DYNAMIC_TYPE_FORWARD"
    2 -> "DYNAMIC_TYPE_AV"
    3 -> "DYNAMIC_TYPE_PGC"
    4 -> "DYNAMIC_TYPE_COURSES_SEASON"
    6 -> "DYNAMIC_TYPE_WORD"
    7 -> "DYNAMIC_TYPE_DRAW"
    8 -> "DYNAMIC_TYPE_ARTICLE"
    9 -> "DYNAMIC_TYPE_MUSIC"
    10 -> "DYNAMIC_TYPE_COMMON_SQUARE"
    11 -> "DYNAMIC_TYPE_COMMON_VERTICAL"
    12 -> "DYNAMIC_TYPE_LIVE"
    13 -> "DYNAMIC_TYPE_MEDIALIST"
    14 -> "DYNAMIC_TYPE_COURSES_SEASON"
    15 -> "DYNAMIC_TYPE_AD"
    18 -> "DYNAMIC_TYPE_LIVE_RCMD"
    19 -> "DYNAMIC_TYPE_BANNER"
    20 -> "DYNAMIC_TYPE_UGC_SEASON"
    21 -> "DYNAMIC_TYPE_SUBSCRIPTION_NEW"
    else -> type.toString()
}
